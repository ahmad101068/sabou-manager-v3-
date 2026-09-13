package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryLotEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.ChangeInventoryLotStatusCommand
import ir.restaurant.management.domain.inventory.FefoLotAllocator
import ir.restaurant.management.domain.inventory.InventoryLotService
import ir.restaurant.management.domain.inventory.InventoryLot
import ir.restaurant.management.domain.inventory.InventoryLotSearch
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryLotTransitionPolicy
import ir.restaurant.management.domain.inventory.LotAllocationCandidate
import ir.restaurant.management.domain.inventory.LotAllocationRequest
import ir.restaurant.management.domain.inventory.LotAllocationResult
import ir.restaurant.management.domain.inventory.RegisterInventoryLotCommand
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission

/** Inventory-owned boundary for lot registration, lifecycle and deterministic FEFO suggestions. */
class LocalInventoryLotService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
) : InventoryLotService {
    private val audit = LocalAuditEventWriter(database)

    override suspend fun search(query: InventoryLotSearch): List<InventoryLot> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        return database.inventoryLotDao().search(
            itemId = valid.itemId,
            locationId = valid.locationId,
            status = valid.status?.storedValue,
            expiryFrom = valid.expiryFromEpochDay,
            expiryTo = valid.expiryToEpochDay,
            limit = valid.limit,
            offset = valid.offset,
        ).map { row ->
            InventoryLot(
                id = row.id,
                globalId = row.globalId,
                itemId = row.itemId,
                locationId = row.locationId,
                lotNumber = row.lotCode,
                supplierLotNumber = row.supplierLotNumber,
                receivedEpochDay = row.receivedEpochDay,
                productionEpochDay = row.productionEpochDay,
                expiryEpochDay = row.expiryEpochDay,
                initialQuantityMicros = row.initialQuantityMicros,
                remainingQuantityMicros = row.quantityMicros,
                unitCostRial = row.unitCostRial,
                status = InventoryLotStatus.fromStoredValue(row.status),
                barcode = row.barcode,
                sourceReceiptId = row.sourceReceiptId,
                correlationId = row.correlationId,
            )
        }
    }

    override suspend fun allocate(request: LotAllocationRequest): LotAllocationResult {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = request.validated()
        return allocateWithoutAuthorization(valid)
    }

    internal suspend fun allocateWithoutAuthorization(request: LotAllocationRequest): LotAllocationResult {
        val valid = request.validated()
        val candidates = database.inventoryLotDao().allocationCandidates(valid.itemId, valid.locationId).map { lot ->
            LotAllocationCandidate(
                lotId = lot.id,
                locationId = lot.locationId,
                receivedEpochDay = lot.receivedEpochDay,
                expiryEpochDay = lot.expiryEpochDay,
                availableQuantityMicros = lot.quantityMicros,
                unitCostRial = lot.unitCostRial,
                status = InventoryLotStatus.fromStoredValue(lot.status),
            )
        }
        return FefoLotAllocator.allocate(valid, candidates)
    }

    override suspend fun register(command: RegisterInventoryLotCommand): Long {
        val actor = authorizer.require(Permission.INVENTORY_LOT_MANAGE)
        if (actor.id != command.actorId) throw BusinessError.PermissionDenied(Permission.INVENTORY_LOT_MANAGE).asViolation()
        val reason = command.reason.trim()
        require(reason.length in 2..300) { "دلیل ثبت لات الزامی است." }
        return database.withTransaction {
            val item = database.inventoryDao().activeById(command.draft.itemId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", command.draft.itemId).asViolation()
            if (!item.trackLot) {
                throw BusinessError.InvalidBusinessState("INVENTORY_ITEM", "LOT_TRACKING_DISABLED").asViolation()
            }
            val valid = command.draft.validated(item.trackExpiry)
            database.inventoryLocationDao().activeById(valid.locationId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_LOCATION", valid.locationId).asViolation()
            if (database.inventoryLotDao().byNaturalKey(valid.itemId, valid.locationId, valid.lotNumber) != null) {
                throw BusinessError.DuplicateDocument("INVENTORY_LOT", valid.lotNumber).asViolation()
            }
            val balance = database.inventoryBalanceDao().byKey(valid.itemId, valid.locationId)
                ?: throw BusinessError.InvalidBusinessState("INVENTORY_BALANCE", "LOCATION_BALANCE_MISSING").asViolation()
            val allocated = database.inventoryLotDao().allocatedQuantityAtLocation(valid.itemId, valid.locationId)
            val unallocated = SignedLongMath.subtract(balance.onHandMicros, allocated).coerceAtLeast(0L)
            if (valid.quantityMicros > unallocated) {
                throw BusinessError.InsufficientStock(item.id, item.name, valid.quantityMicros, unallocated).asViolation()
            }
            val now = clock()
            val id = database.inventoryLotDao().insert(
                InventoryLotEntity(
                    globalId = GlobalId.new().value,
                    itemId = valid.itemId,
                    locationId = valid.locationId,
                    lotCode = valid.lotNumber,
                    supplierLotNumber = valid.supplierLotNumber,
                    receivedEpochDay = valid.receivedEpochDay,
                    productionEpochDay = valid.productionEpochDay,
                    expiryEpochDay = valid.expiryEpochDay,
                    quantityMicros = valid.quantityMicros,
                    initialQuantityMicros = valid.quantityMicros,
                    unitCostRial = valid.unitCostRial,
                    status = InventoryLotStatus.ACTIVE.storedValue,
                    barcode = valid.barcode,
                    sourceReceiptId = valid.sourceReceiptId,
                    correlationId = valid.correlationId,
                    createdByActorId = actor.id,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "CREATE",
                entityType = "INVENTORY_LOT",
                entityId = id,
                description = "Create inventory lot ${valid.lotNumber}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.receivedEpochDay,
                reason = reason,
                afterSnapshot = "status=ACTIVE;quantityMicros=${valid.quantityMicros};locationId=${valid.locationId}",
                correlationId = valid.correlationId,
                referenceType = valid.sourceReceiptId?.let { "GOODS_RECEIPT" } ?: "INVENTORY_LOT",
                referenceId = valid.sourceReceiptId ?: id,
            )
            syncRecorder?.record("INVENTORY_LOT", id, "CREATE", now)
            id
        }
    }

    override suspend fun changeStatus(command: ChangeInventoryLotStatusCommand) {
        val actor = authorizer.require(Permission.INVENTORY_LOT_MANAGE)
        if (actor.id != command.actorId) throw BusinessError.PermissionDenied(Permission.INVENTORY_LOT_MANAGE).asViolation()
        val reason = command.reason.trim()
        require(command.lotId > 0 && command.businessEpochDay > 0)
        require(reason.length in 2..300) { "دلیل تغییر وضعیت لات الزامی است." }
        val correlationId = CorrelationId.parse(command.correlationId).value
        database.withTransaction {
            val lot = database.inventoryLotDao().byId(command.lotId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_LOT", command.lotId).asViolation()
            val current = InventoryLotStatus.requireKnown(lot.status)
            if (current != command.expectedStatus) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_LOT", lot.id).asViolation()
            }
            InventoryLotTransitionPolicy.requireAllowed(current, command.nextStatus, lot.quantityMicros)
            if (
                command.nextStatus == InventoryLotStatus.EXPIRED &&
                (lot.expiryEpochDay == null || lot.expiryEpochDay >= command.businessEpochDay)
            ) {
                throw BusinessError.InvalidBusinessState("INVENTORY_LOT", "NOT_EXPIRED").asViolation()
            }
            if (
                command.nextStatus == InventoryLotStatus.ACTIVE &&
                lot.expiryEpochDay != null && lot.expiryEpochDay < command.businessEpochDay
            ) {
                throw BusinessError.LotExpired(lot.id, lot.expiryEpochDay).asViolation()
            }
            val balance = database.inventoryBalanceDao().byKey(lot.itemId, lot.locationId)
                ?: throw BusinessError.InvalidBusinessState("INVENTORY_BALANCE", "LOCATION_BALANCE_MISSING").asViolation()
            val unavailableDelta = when {
                !current.isUnavailable && command.nextStatus.isUnavailable -> lot.quantityMicros
                current.isUnavailable && !command.nextStatus.isUnavailable -> -lot.quantityMicros
                else -> 0L
            }
            val now = clock()
            if (unavailableDelta != 0L) {
                val nextUnavailable = SignedLongMath.add(balance.quarantinedMicros, unavailableDelta)
                if (
                    database.inventoryBalanceDao().compareAndSetQuarantined(
                        itemId = lot.itemId,
                        locationId = lot.locationId,
                        expectedQuarantinedMicros = balance.quarantinedMicros,
                        nextQuarantinedMicros = nextUnavailable,
                        updatedAtEpochMillis = now,
                    ) != 1
                ) {
                    throw BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", lot.itemId).asViolation()
                }
            }
            if (
                database.inventoryLotDao().compareAndSetStatus(
                    id = lot.id,
                    expectedStatus = current.storedValue,
                    nextStatus = command.nextStatus.storedValue,
                    expectedQuantityMicros = lot.quantityMicros,
                    updatedAtEpochMillis = now,
                ) != 1
            ) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_LOT", lot.id).asViolation()
            }
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "STATUS_CHANGE",
                entityType = "INVENTORY_LOT",
                entityId = lot.id,
                description = "Inventory lot status ${current.storedValue} -> ${command.nextStatus.storedValue}",
                occurredAtEpochMillis = now,
                businessEpochDay = command.businessEpochDay,
                reason = reason,
                beforeSnapshot = "status=${current.storedValue};quantityMicros=${lot.quantityMicros}",
                afterSnapshot = "status=${command.nextStatus.storedValue};quantityMicros=${lot.quantityMicros}",
                correlationId = correlationId,
            )
            syncRecorder?.record("INVENTORY_LOT", lot.id, "STATUS_CHANGE", now)
        }
    }
}
