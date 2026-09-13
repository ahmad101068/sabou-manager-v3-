package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.InventoryWasteDocumentEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryWasteDocument
import ir.restaurant.management.domain.inventory.InventoryWasteSearch
import ir.restaurant.management.domain.inventory.InventoryWasteService
import ir.restaurant.management.domain.inventory.PostWasteCommand
import ir.restaurant.management.domain.inventory.WasteActionCommand
import ir.restaurant.management.domain.inventory.WasteApprovalPolicy
import ir.restaurant.management.domain.inventory.WasteReason
import ir.restaurant.management.domain.inventory.WasteStatus
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties

/** Inventory-owned waste document boundary; posting reuses the existing ledger/accounting engines. */
class LocalInventoryWasteService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val accounting: AccountingPostingService = LocalAccountingPostingEngine(database),
    private val approvalPolicy: WasteApprovalPolicy = WasteApprovalPolicy.NO_APPROVAL_REQUIRED,
    private val enforceSeparationOfDuties: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
) : InventoryWasteService {
    private val audit = LocalAuditEventWriter(database)
    private val inventoryCommands = LocalInventoryCommandEngine(database, clock, authorizer)
    private val branchResolver = CanonicalBranchResolver(database)

    override suspend fun search(query: InventoryWasteSearch): List<InventoryWasteDocument> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        return database.inventoryControlDao().searchWasteDocuments(
            status = valid.status?.storedValue,
            locationId = valid.locationId,
            fromEpochDay = valid.fromEpochDay,
            toEpochDay = valid.toEpochDay,
            limit = valid.limit,
            offset = valid.offset,
        ).map { it.toDomain() }
    }

    override suspend fun submit(command: CreateWasteCommand): InventoryWasteDocument {
        val actor = authorizer.require(Permission.INVENTORY_WASTE_CREATE)
        val valid = command.validated()
        requireActor(valid.actorId, actor.id, Permission.INVENTORY_WASTE_CREATE)
        val key = "inventory_waste:${valid.commandId}"
        val reasonDetail = valid.reasonDetail.ifBlank { valid.reason.storedValue }
        return database.withTransaction {
            database.inventoryControlDao().wasteDocumentByIdempotencyKey(key)?.let { existing ->
                val matches = existing.itemId == valid.itemId && existing.locationId == valid.locationId &&
                    existing.lotId == valid.lotId && existing.quantityMicros == valid.quantityMicros &&
                    existing.reasonCode == valid.reason.storedValue && existing.reason == reasonDetail &&
                    existing.notes == valid.notes && existing.wasteEpochDay == valid.businessEpochDay &&
                    existing.actorId == actor.id && existing.correlationId == valid.correlationId
                if (!matches) throw BusinessError.IdempotencyConflict(key).asViolation()
                return@withTransaction existing.toDomain()
            }
            val item = database.inventoryDao().activeById(valid.itemId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", valid.itemId).asViolation()
            database.inventoryLocationDao().activeById(valid.locationId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_LOCATION", valid.locationId).asViolation()
            val balance = resolveBalance(item.id, item.stockMicros, item.inventoryValueRial, valid.locationId)
            val disposable = SignedLongMath.subtract(balance.onHandMicros, balance.reservedMicros)
            if (disposable < valid.quantityMicros) {
                throw BusinessError.InsufficientStock(
                    item.id,
                    item.name,
                    valid.quantityMicros,
                    disposable,
                ).asViolation()
            }
            val lot = valid.lotId?.let { lotId ->
                if (!item.trackLot) throw BusinessError.InvalidLot(lotId, "LOT_TRACKING_DISABLED").asViolation()
                database.inventoryLotDao().byId(lotId)
                    ?: throw BusinessError.InvalidLot(lotId, "LOT_NOT_FOUND").asViolation()
            }
            if (lot != null) {
                if (lot.itemId != item.id || lot.locationId != valid.locationId) {
                    throw BusinessError.InvalidLot(lot.id, "LOT_ITEM_LOCATION_MISMATCH").asViolation()
                }
                val status = InventoryLotStatus.requireKnown(lot.status)
                if (status == InventoryLotStatus.DEPLETED || lot.quantityMicros < valid.quantityMicros) {
                    throw BusinessError.InsufficientStock(item.id, item.name, valid.quantityMicros, lot.quantityMicros)
                        .asViolation()
                }
                if (
                    valid.reason == WasteReason.EXPIRED &&
                    status != InventoryLotStatus.EXPIRED &&
                    (lot.expiryEpochDay == null || lot.expiryEpochDay >= valid.businessEpochDay)
                ) {
                    throw BusinessError.InvalidLot(lot.id, "LOT_NOT_EXPIRED").asViolation()
                }
            } else if (valid.reason == WasteReason.EXPIRED) {
                throw BusinessError.InvalidLot(null, "EXPIRED_WASTE_REQUIRES_LOT").asViolation()
            }
            // Lot/expiry identifies the physical stock consumed; carrying value remains location weighted-average.
            val totalCost = ir.restaurant.management.domain.inventory.WeightedAverageInventoryValuationService.issueValue(
                balance.onHandMicros,
                balance.inventoryValueRial,
                valid.quantityMicros,
            )
            if (totalCost > balance.inventoryValueRial) {
                throw BusinessError.InsufficientInventoryValue(
                    item.id,
                    item.name,
                    totalCost,
                    balance.inventoryValueRial,
                ).asViolation()
            }
            val unitCost = if (totalCost == 0L) {
                0L
            } else {
                FixedPointRatio.multiplyDivide(totalCost, QuantityMicros.SCALE, valid.quantityMicros)
            }
            val now = clock()
            val token = valid.commandId.replace("-", "").uppercase().take(12)
            val status = if (approvalPolicy.requiresApproval(totalCost)) {
                WasteStatus.PENDING_APPROVAL
            } else {
                WasteStatus.APPROVED
            }
            val id = database.inventoryControlDao().insertWasteDocument(
                InventoryWasteDocumentEntity(
                    globalId = valid.commandId,
                    documentNumber = "WD-${valid.businessEpochDay}-$token",
                    idempotencyKey = key,
                    correlationId = valid.correlationId,
                    itemId = item.id,
                    locationId = valid.locationId,
                    lotId = lot?.id,
                    quantityMicros = valid.quantityMicros,
                    unitCostRial = unitCost,
                    valueRial = totalCost,
                    stockQuantitySnapshotMicros = balance.onHandMicros,
                    stockValueSnapshotRial = balance.inventoryValueRial,
                    lotQuantitySnapshotMicros = lot?.quantityMicros,
                    wasteEpochDay = valid.businessEpochDay,
                    reasonCode = valid.reason.storedValue,
                    reason = reasonDetail,
                    notes = valid.notes,
                    status = status.storedValue,
                    actorId = actor.id,
                    deviceId = "local-android",
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            audit(
                id,
                "CREATE",
                valid.businessEpochDay,
                reasonDetail,
                valid.correlationId,
                now,
                "itemId=${item.id};locationId=${valid.locationId};lotId=${lot?.id};quantityMicros=${valid.quantityMicros};valueRial=$totalCost;status=${status.storedValue}",
            )
            syncRecorder?.record("INVENTORY_WASTE", id, "CREATE", now)
            requireDocument(id).toDomain()
        }
    }

    override suspend fun approve(command: WasteActionCommand): InventoryWasteDocument {
        val actor = authorizer.require(Permission.INVENTORY_WASTE_APPROVE)
        requireActor(command.actorId, actor.id, Permission.INVENTORY_WASTE_APPROVE)
        val reason = requiredReason(command.reason)
        return database.withTransaction {
            val document = requireDocument(command.wasteId)
            if (document.status != WasteStatus.PENDING_APPROVAL.storedValue) {
                throw BusinessError.InvalidStateTransition(
                    "INVENTORY_WASTE",
                    document.status,
                    WasteStatus.APPROVED.storedValue,
                ).asViolation()
            }
            if (enforceSeparationOfDuties) {
                SegregationOfDuties.requireDifferentActors("INVENTORY_WASTE_APPROVAL", document.actorId, actor.id)
            }
            val now = clock()
            if (database.inventoryControlDao().approveWaste(document.id, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_WASTE", document.id).asViolation()
            }
            audit(document.id, "APPROVE", document.wasteEpochDay, reason, document.correlationId, now)
            syncRecorder?.record("INVENTORY_WASTE", document.id, "APPROVE", now)
            requireDocument(document.id).toDomain()
        }
    }

    /** Compatibility/application shortcut; the outer transaction keeps create + posting indivisible. */
    override suspend fun submitAndPost(command: CreateWasteCommand): InventoryWasteDocument =
        database.withTransaction {
            val document = submit(command)
            if (document.status !in setOf(WasteStatus.APPROVED, WasteStatus.POSTED)) {
                throw BusinessError.ApprovalRequired("INVENTORY_WASTE", 1).asViolation()
            }
            post(
                PostWasteCommand(
                    wasteId = document.id,
                    actorId = command.actorId,
                    commandId = command.commandId,
                ),
            )
        }

    override suspend fun post(command: PostWasteCommand): InventoryWasteDocument {
        val actor = authorizer.require(Permission.INVENTORY_WASTE_CREATE)
        requireActor(command.actorId, actor.id, Permission.INVENTORY_WASTE_CREATE)
        val commandId = GlobalId.parse(command.commandId).value
        return database.withTransaction {
            database.inventoryControlDao().wasteDocumentByPostCommand(commandId)?.let { replay ->
                if (replay.id != command.wasteId) throw BusinessError.IdempotencyConflict(commandId).asViolation()
                return@withTransaction replay.toDomain()
            }
            val document = requireDocument(command.wasteId)
            if (document.status == WasteStatus.POSTED.storedValue) {
                throw BusinessError.WasteAlreadyPosted(document.id).asViolation()
            }
            if (document.status != WasteStatus.APPROVED.storedValue) {
                throw BusinessError.WasteNotApproved(document.id).asViolation()
            }
            if (actor.id != document.actorId && !authorizer.can(Permission.INVENTORY_WASTE_APPROVE)) {
                throw BusinessError.PermissionDenied(Permission.INVENTORY_WASTE_CREATE).asViolation()
            }
            val balance = database.inventoryBalanceDao().byKey(document.itemId, document.locationId)
                ?: throw BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", document.itemId).asViolation()
            if (
                balance.onHandMicros != document.stockQuantitySnapshotMicros ||
                balance.inventoryValueRial != document.stockValueSnapshotRial
            ) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_WASTE_SNAPSHOT", document.id).asViolation()
            }
            document.lotId?.let { lotId ->
                val lot = database.inventoryLotDao().byId(lotId)
                    ?: throw BusinessError.InvalidLot(lotId, "LOT_NOT_FOUND").asViolation()
                if (lot.quantityMicros != document.lotQuantitySnapshotMicros) {
                    throw BusinessError.ConcurrencyConflict("INVENTORY_LOT", lotId).asViolation()
                }
            }
            val now = clock()
            val detail = document.reason.ifBlank { document.reasonCode }
            inventoryCommands.issue(
                itemId = document.itemId,
                quantityMicros = document.quantityMicros,
                valueRial = document.valueRial,
                movementType = InventoryMovementType.WASTE,
                referenceType = InventoryReferenceType.WASTE,
                referenceId = document.id,
                movementEpochDay = document.wasteEpochDay,
                context = InventoryCommandContext.local(
                    referenceType = InventoryReferenceType.WASTE,
                    referenceId = document.id,
                    suffix = "post:${document.id}",
                    actorId = actor.id,
                    reasonCode = InventoryReasonCode.WASTE,
                    reason = detail,
                    correlationId = document.correlationId,
                    locationId = document.locationId,
                ),
                notes = document.notes.ifBlank { detail },
                lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALL,
                requestedLotId = document.lotId,
            )
            if (document.valueRial > 0L) {
                val location = database.inventoryLocationDao().byId(document.locationId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_LOCATION", document.locationId).asViolation()
                val branchId = location.branchId
                if (branchId != null) branchResolver.requireActive(branchId)
                accounting.post(
                    AccountingPostingCommand(
                        entryNo = "ض-${document.documentNumber}",
                        sourceType = "WASTE",
                        sourceId = document.id,
                        businessEpochDay = document.wasteEpochDay,
                        description = "ضایعات ${document.documentNumber}: $detail",
                        accountingScope = if (branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION,
                        branchId = branchId,
                        lines = listOf(
                            SemanticJournalLine(
                                SemanticAccountRole.INVENTORY_WASTE_EXPENSE,
                                debit = MoneyRial.of(document.valueRial),
                            ),
                            SemanticJournalLine(
                                SemanticAccountRole.INVENTORY_ASSET,
                                credit = MoneyRial.of(document.valueRial),
                            ),
                        ),
                        idempotencyKey = "WASTE:${document.id}:POST",
                        correlationId = CorrelationId.parse(document.correlationId),
                        actorId = actor.id,
                    ),
                )
            }
            if (database.inventoryControlDao().markWastePosted(document.id, commandId, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_WASTE", document.id).asViolation()
            }
            audit(
                document.id,
                "POST",
                document.wasteEpochDay,
                detail,
                document.correlationId,
                now,
                "status=POSTED;quantityMicros=${document.quantityMicros};valueRial=${document.valueRial}",
            )
            syncRecorder?.record("INVENTORY_WASTE", document.id, "POST", now)
            requireDocument(document.id).toDomain()
        }
    }

    override suspend fun document(id: Long): InventoryWasteDocument {
        authorizer.require(Permission.INVENTORY_VIEW)
        return requireDocument(id).toDomain()
    }

    private suspend fun resolveBalance(
        itemId: Long,
        aggregateQuantityMicros: Long,
        aggregateValueRial: Long,
        locationId: Long,
    ): InventoryBalanceEntity {
        database.inventoryBalanceDao().byKey(itemId, locationId)?.let { return it }
        val defaultLocationId = database.inventoryLocationDao().defaultLocationId()
        val isOnlyProjection = database.inventoryBalanceDao().countForItem(itemId) == 0
        database.inventoryBalanceDao().initialize(
            InventoryBalanceEntity(
                itemId = itemId,
                locationId = locationId,
                onHandMicros = if (isOnlyProjection && locationId == defaultLocationId) aggregateQuantityMicros else 0,
                inventoryValueRial = if (isOnlyProjection && locationId == defaultLocationId) aggregateValueRial else 0,
                updatedAtEpochMillis = clock(),
            ),
        )
        return database.inventoryBalanceDao().byKey(itemId, locationId)
            ?: throw BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", itemId).asViolation()
    }

    private suspend fun requireDocument(id: Long): InventoryWasteDocumentEntity =
        database.inventoryControlDao().wasteDocument(id)
            ?: throw BusinessError.EntityNotFound("INVENTORY_WASTE", id).asViolation()

    private fun requireActor(requested: Long, authorized: Long, permission: Permission) {
        if (requested != authorized) throw BusinessError.PermissionDenied(permission).asViolation()
    }

    private fun requiredReason(reason: String): String = reason.trim().also {
        if (it.length !in 3..300) throw BusinessError.InvalidInput("reason", "دلیل تأیید ضایعات الزامی است.").asViolation()
    }

    private suspend fun audit(
        id: Long,
        action: String,
        businessEpochDay: Long,
        reason: String,
        correlationId: String,
        now: Long,
        afterSnapshot: String? = null,
    ) {
        audit.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = "INVENTORY_WASTE",
            entityId = id,
            description = "$action سند ضایعات $id",
            occurredAtEpochMillis = now,
            businessEpochDay = businessEpochDay,
            reason = reason,
            afterSnapshot = afterSnapshot,
            correlationId = correlationId,
        )
    }

    private fun InventoryWasteDocumentEntity.toDomain() = InventoryWasteDocument(
        id = id,
        globalId = globalId,
        documentNumber = documentNumber,
        itemId = itemId,
        locationId = locationId,
        lotId = lotId,
        quantityMicros = quantityMicros,
        unitCostRial = unitCostRial,
        totalCostRial = valueRial,
        reason = WasteReason.fromStoredValue(reasonCode),
        reasonDetail = reason,
        businessEpochDay = wasteEpochDay,
        createdByActorId = actorId,
        approvedByActorId = approvedByActorId,
        postedByActorId = postedByActorId,
        status = WasteStatus.fromStoredValue(status),
        correlationId = correlationId,
    )
}
