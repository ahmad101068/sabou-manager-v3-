package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.InventoryItemMasterDraft
import ir.restaurant.management.domain.inventory.InventoryItemMasterRecord
import ir.restaurant.management.domain.inventory.InventoryItemSearch
import ir.restaurant.management.domain.inventory.InventoryItemType
import ir.restaurant.management.domain.inventory.InventoryLocationDraft
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.inventory.InventoryLocationSearch
import ir.restaurant.management.domain.inventory.InventoryLocationCode
import ir.restaurant.management.domain.inventory.InventoryLocationType
import ir.restaurant.management.domain.inventory.InventoryRepository
import ir.restaurant.management.domain.inventory.InventorySku
import ir.restaurant.management.domain.inventory.InventoryStorageCondition
import ir.restaurant.management.domain.inventory.ItemBarcode
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Inventory-owned application boundary for item and location master data. */
class LocalInventoryRepository(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
) : InventoryRepository {
    private val branchResolver = CanonicalBranchResolver(database)
    private val audit = LocalAuditEventWriter(database)
    private val dataScope = LocalDataScopeService(database, authorizer)

    override val locations: Flow<List<InventoryLocationRecord>> =
        dataScope.scopedLocations().map { rows -> rows.map(StorageLocationEntity::toRecord) }

    override suspend fun item(id: Long): InventoryItemMasterRecord {
        authorizer.require(Permission.INVENTORY_VIEW)
        return database.inventoryDao().byId(id)?.toMasterRecord()
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", id).asViolation()
    }

    override suspend fun searchItems(search: InventoryItemSearch): List<InventoryItemMasterRecord> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = search.validated()
        return database.inventoryDao().search(
            query = valid.query,
            category = valid.category,
            itemType = valid.itemType?.storedValue,
            supplierId = valid.preferredSupplierId,
            includeInactive = valid.includeInactive,
            limit = valid.limit,
            offset = valid.offset,
        ).map(InventoryItemEntity::toMasterRecord)
    }

    override suspend fun itemByBarcode(barcode: String): InventoryItemMasterRecord? {
        authorizer.require(Permission.INVENTORY_VIEW)
        val normalized = ItemBarcode.parse(barcode).value
        return database.inventoryDao().byPrimaryBarcode(normalized)?.toMasterRecord()
    }

    override suspend fun saveItem(id: Long?, draft: InventoryItemMasterDraft): Long {
        authorizer.require(Permission.INVENTORY_ITEM_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            valid.preferredSupplierId?.let { supplierId ->
                if (database.supplierDao().activeById(supplierId) == null) {
                    throw BusinessError.SupplierInactive(supplierId).asViolation()
                }
            }
            val requested = id?.let { itemId ->
                database.inventoryDao().byId(itemId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
            }
            val sameName = database.inventoryDao().byName(valid.name)
            val existing = requested ?: sameName?.takeUnless { it.isActive }
            if (sameName != null && sameName.id != existing?.id) {
                throw BusinessError.DuplicateDocument("INVENTORY_ITEM", valid.name).asViolation()
            }
            if (existing != null && existing.sku != valid.sku) {
                throw BusinessError.InvalidBusinessState("INVENTORY_ITEM", "STABLE_SKU_CHANGE").asViolation()
            }
            ensureItemKeysUnique(valid, existing?.id)
            val now = clock()
            val next = existing.toEntity(valid, now)
            val itemId = if (existing == null) {
                database.inventoryDao().insert(next)
            } else {
                check(database.inventoryDao().update(next) == 1) { "inventory_item_update_conflict" }
                existing.id
            }
            val action = when {
                existing == null -> "CREATE"
                !existing.isActive -> "REACTIVATE"
                else -> "UPDATE"
            }
            val correlationId = "inventory:item:$itemId:$now"
            audit.appendAuthorized(
                authorizer = authorizer,
                action = action,
                entityType = "INVENTORY_ITEM",
                entityId = itemId,
                description = "$action inventory item ${valid.sku}",
                occurredAtEpochMillis = now,
                reason = "INVENTORY_ITEM_MASTER_CHANGE",
                beforeSnapshot = existing?.masterSnapshot(),
                afterSnapshot = next.masterSnapshot(),
                correlationId = correlationId,
            )
            syncRecorder?.record("INVENTORY_ITEM", itemId, action, now)
            itemId
        }
    }

    override suspend fun deactivateItem(id: Long) {
        authorizer.require(Permission.INVENTORY_ITEM_MANAGE)
        database.withTransaction {
            val current = database.inventoryDao().activeById(id)
                ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", id).asViolation()
            if (
                current.stockMicros != 0L || current.inventoryValueRial != 0L ||
                database.inventoryBalanceDao().hasNonZeroState(id)
            ) {
                throw BusinessError.InvalidBusinessState("INVENTORY_ITEM", "NON_ZERO_BALANCE").asViolation()
            }
            val now = clock()
            if (database.inventoryDao().deactivate(id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_ITEM", id).asViolation()
            }
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "DEACTIVATE",
                entityType = "INVENTORY_ITEM",
                entityId = id,
                description = "Deactivate inventory item ${current.sku}",
                occurredAtEpochMillis = now,
                reason = "INVENTORY_ITEM_DEACTIVATED",
                beforeSnapshot = current.masterSnapshot(),
                afterSnapshot = current.copy(isActive = false, updatedAtEpochMillis = now).masterSnapshot(),
                correlationId = "inventory:item:$id:$now",
            )
            syncRecorder?.record("INVENTORY_ITEM", id, "DEACTIVATE", now)
        }
    }

    override suspend fun searchLocations(search: InventoryLocationSearch): List<InventoryLocationRecord> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = search.validated()
        val allowedIds = dataScope.activeLocations().map { it.id }.toSet()
        return database.inventoryLocationDao().search(
            query = valid.query,
            type = valid.type?.storedValue,
            includeInactive = false,
            limit = valid.limit,
            offset = valid.offset,
        ).filter { it.id in allowedIds }.map(StorageLocationEntity::toRecord)
    }

    override suspend fun saveLocation(id: Long?, draft: InventoryLocationDraft): Long {
        authorizer.require(Permission.INVENTORY_LOCATION_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            val branch = branchResolver.resolveOptional(valid.branchId, valid.branchName)
            val existing = id?.let { locationId ->
                database.inventoryLocationDao().byId(locationId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_LOCATION", locationId).asViolation()
            }
            if (existing != null && existing.code != valid.code) {
                throw BusinessError.InvalidBusinessState("INVENTORY_LOCATION", "STABLE_CODE_CHANGE").asViolation()
            }
            database.inventoryLocationDao().byCode(valid.code)?.takeIf { it.id != existing?.id }?.let {
                throw BusinessError.DuplicateDocument("INVENTORY_LOCATION", valid.code).asViolation()
            }
            database.inventoryLocationDao().byName(valid.name)?.takeIf { it.id != existing?.id }?.let {
                throw BusinessError.DuplicateDocument("INVENTORY_LOCATION", valid.name).asViolation()
            }
            val now = clock()
            val next = StorageLocationEntity(
                id = existing?.id ?: 0,
                code = valid.code,
                name = valid.name,
                branchName = branch?.name.orEmpty(),
                branchId = branch?.id,
                kind = valid.type.storedValue,
                isActive = true,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            )
            val locationId = if (existing == null) {
                database.inventoryLocationDao().insert(next)
            } else {
                check(database.inventoryLocationDao().update(next) == 1) { "inventory_location_update_conflict" }
                existing.id
            }
            val action = if (existing == null) "CREATE" else "UPDATE"
            audit.appendAuthorized(
                authorizer = authorizer,
                action = action,
                entityType = "INVENTORY_LOCATION",
                entityId = locationId,
                description = "$action inventory location ${valid.code}",
                occurredAtEpochMillis = now,
                reason = "INVENTORY_LOCATION_MASTER_CHANGE",
                beforeSnapshot = existing?.locationSnapshot(),
                afterSnapshot = next.locationSnapshot(),
                correlationId = "inventory:location:$locationId:$now",
            )
            syncRecorder?.record("INVENTORY_LOCATION", locationId, action, now)
            locationId
        }
    }

    override suspend fun defaultLocationId(): Long {
        authorizer.require(Permission.INVENTORY_VIEW)
        val scoped = dataScope.activeLocations()
        require(scoped.size == 1) { "انبار پیش‌فرض پنهان مجاز نیست؛ مکان باید صریح انتخاب شود." }
        return scoped.single().id
    }

    private suspend fun ensureItemKeysUnique(valid: InventoryItemMasterDraft, excludedId: Long?) {
        database.inventoryDao().bySku(valid.sku)?.takeIf { it.id != excludedId }?.let {
            throw BusinessError.DuplicateDocument("INVENTORY_SKU", valid.sku).asViolation()
        }
        valid.primaryBarcode?.let { barcode ->
            database.inventoryDao().byPrimaryBarcode(barcode)?.takeIf { it.id != excludedId }?.let {
                throw BusinessError.DuplicateDocument("INVENTORY_BARCODE", barcode).asViolation()
            }
        }
    }
}

private fun InventoryItemEntity?.toEntity(valid: InventoryItemMasterDraft, now: Long): InventoryItemEntity =
    InventoryItemEntity(
        id = this?.id ?: 0,
        name = valid.name,
        category = valid.category,
        unit = valid.baseUnit,
        sku = valid.sku,
        itemType = valid.itemType.storedValue,
        purchaseUnit = valid.purchaseUnit,
        purchaseToStockNumerator = valid.purchaseToBaseNumerator,
        purchaseToStockDenominator = valid.purchaseToBaseDenominator,
        recipeUnit = valid.recipeUnit,
        recipeToStockNumerator = valid.recipeToBaseNumerator,
        recipeToStockDenominator = valid.recipeToBaseDenominator,
        stockMicros = this?.stockMicros ?: 0,
        inventoryValueRial = this?.inventoryValueRial ?: 0,
        alertEnabled = valid.reorderPointMicros > 0 || valid.minimumStockMicros > 0,
        alertThresholdMicros = valid.reorderPointMicros.takeIf { it > 0 } ?: valid.minimumStockMicros,
        supplierId = valid.preferredSupplierId,
        primaryBarcode = valid.primaryBarcode,
        brand = valid.brand,
        storageCondition = valid.storageCondition.storedValue,
        shelfLifeDays = valid.shelfLifeDays,
        trackLot = valid.trackLot,
        trackExpiry = valid.trackExpiry,
        minimumStockMicros = valid.minimumStockMicros,
        maximumStockMicros = valid.maximumStockMicros,
        safetyStockMicros = valid.safetyStockMicros,
        reorderPointMicros = valid.reorderPointMicros,
        leadTimeDays = valid.leadTimeDays,
        isActive = valid.active,
        createdAtEpochMillis = this?.createdAtEpochMillis ?: now,
        updatedAtEpochMillis = now,
    )

private fun InventoryItemEntity.toMasterRecord() = InventoryItemMasterRecord(
    id = id,
    sku = InventorySku.parse(sku),
    name = name,
    category = category,
    itemType = InventoryItemType.fromStoredValue(itemType),
    baseUnit = unit,
    purchaseUnit = purchaseUnit,
    purchaseToBaseNumerator = purchaseToStockNumerator,
    purchaseToBaseDenominator = purchaseToStockDenominator,
    recipeUnit = recipeUnit,
    recipeToBaseNumerator = recipeToStockNumerator,
    recipeToBaseDenominator = recipeToStockDenominator,
    primaryBarcode = primaryBarcode?.let { ItemBarcode.parse(it) },
    brand = brand,
    storageCondition = InventoryStorageCondition.fromStoredValue(storageCondition),
    shelfLifeDays = shelfLifeDays,
    trackLot = trackLot,
    trackExpiry = trackExpiry,
    minimumStockMicros = minimumStockMicros,
    maximumStockMicros = maximumStockMicros,
    safetyStockMicros = safetyStockMicros,
    reorderPointMicros = reorderPointMicros,
    preferredSupplierId = supplierId,
    leadTimeDays = leadTimeDays,
    active = isActive,
    onHandMicros = stockMicros,
    inventoryValueRial = inventoryValueRial,
)

private fun StorageLocationEntity.toRecord() = InventoryLocationRecord(
    id = id,
    code = InventoryLocationCode.parse(code),
    name = name,
    type = InventoryLocationType.fromStoredValue(kind),
    active = isActive,
    branchName = branchName,
    branchId = branchId,
)

private fun InventoryItemEntity.masterSnapshot(): String =
    "sku=$sku;name=${name.take(120)};type=$itemType;active=$isActive;lot=$trackLot;expiry=$trackExpiry"

private fun StorageLocationEntity.locationSnapshot(): String =
    "code=$code;name=${name.take(80)};type=$kind;active=$isActive;branchId=${branchId ?: ""}"
