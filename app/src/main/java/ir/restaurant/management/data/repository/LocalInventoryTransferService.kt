package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.StockTransferEntity
import ir.restaurant.management.data.db.StockTransferLineEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryTransferDocument
import ir.restaurant.management.domain.inventory.InventoryTransferLine
import ir.restaurant.management.domain.inventory.InventoryTransferService
import ir.restaurant.management.domain.inventory.InventoryTransferSearch
import ir.restaurant.management.domain.inventory.InventoryTransferStatus
import ir.restaurant.management.domain.inventory.InventoryTransferTransitionPolicy
import ir.restaurant.management.domain.inventory.ReceiveInventoryTransferCommand
import ir.restaurant.management.domain.inventory.TransferActionCommand
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties

/** Inventory-owned document boundary for request, issue, in-transit custody and receipt. */
class LocalInventoryTransferService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val enforceApprovalSeparationOfDuties: Boolean = false,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
) : InventoryTransferService {
    private val audit = LocalAuditEventWriter(database)
    private val inventoryCommands = LocalInventoryCommandEngine(database, clock, authorizer)
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val dataScope = LocalDataScopeService(database, authorizer)

    override suspend fun search(query: InventoryTransferSearch): List<InventoryTransferDocument> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        valid.locationId?.let { dataScope.requireLocation(it) }
        val allowed = dataScope.activeLocations().map { it.id }.toSet()
        if (allowed.isEmpty()) return emptyList()
        return database.inventoryTransferDao().search(
            status = valid.status?.storedValue,
            locationId = valid.locationId,
            limit = valid.limit,
            offset = valid.offset,
        ).filter { it.sourceLocationId in allowed || it.destinationLocationId in allowed }
            .map { transfer -> transfer.toDomain(database.inventoryTransferDao().lines(transfer.id)) }
    }

    override suspend fun create(command: CreateInventoryTransferCommand): InventoryTransferDocument {
        val actor = authorizer.require(Permission.INVENTORY_TRANSFER_CREATE)
        val valid = command.validated()
        requireActor(valid.actorId, actor.id, Permission.INVENTORY_TRANSFER_CREATE)
        val idempotencyKey = "inventory_transfer:${valid.commandId}"
        return database.withTransaction {
            database.inventoryTransferDao().byIdempotencyKey(idempotencyKey)?.let { existing ->
                return@withTransaction requireCreateReplay(existing, valid)
            }
            dataScope.requireLocation(valid.sourceLocationId)
            dataScope.requireLocation(valid.destinationLocationId)

            val preparedLines = valid.lines.map { requested ->
                val item = database.inventoryDao().activeById(requested.itemId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", requested.itemId).asViolation()
                val lot = requested.lotId?.let { lotId ->
                    database.inventoryLotDao().byId(lotId)
                        ?: throw BusinessError.InvalidLot(lotId, "LOT_NOT_FOUND").asViolation()
                }
                if (item.trackLot && lot == null) {
                    throw BusinessError.InvalidLot(null, "LOT_REQUIRED").asViolation()
                }
                if (!item.trackLot && lot != null) {
                    throw BusinessError.InvalidLot(lot.id, "LOT_TRACKING_DISABLED").asViolation()
                }
                if (lot != null) {
                    val lotStatus = InventoryLotStatus.requireKnown(lot.status)
                    if (lot.itemId != item.id || lot.locationId != valid.sourceLocationId) {
                        throw BusinessError.InvalidLot(lot.id, "LOT_ITEM_LOCATION_MISMATCH").asViolation()
                    }
                    if (lotStatus != InventoryLotStatus.ACTIVE) {
                        throw BusinessError.InvalidLot(lot.id, "LOT_NOT_TRANSFERABLE").asViolation()
                    }
                }
                PreparedLine(
                    itemId = item.id,
                    lotId = lot?.id,
                    lotCode = lot?.lotCode.orEmpty(),
                    quantityMicros = requested.requestedQuantityMicros,
                )
            }
            val now = clock()
            val transferId = database.inventoryTransferDao().insertTransfer(
                StockTransferEntity(
                    transferNo = numbering.next(DocumentNumberType.INVENTORY_TRANSFER),
                    sourceLocationId = valid.sourceLocationId,
                    destinationLocationId = valid.destinationLocationId,
                    transferEpochDay = valid.businessEpochDay,
                    note = valid.notes,
                    globalId = GlobalId.new().value,
                    idempotencyKey = idempotencyKey,
                    correlationId = valid.correlationId,
                    status = InventoryTransferStatus.REQUESTED.storedValue,
                    requestedByActorId = actor.id,
                    actorDisplayNameSnapshot = actor.displayName,
                    requestedAtEpochMillis = now,
                    deviceId = "local-android",
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            database.inventoryTransferDao().insertLines(
                preparedLines.map { line ->
                    StockTransferLineEntity(
                        transferId = transferId,
                        itemId = line.itemId,
                        lotId = line.lotId,
                        lotKey = line.lotId ?: 0L,
                        lotCodeSnapshot = line.lotCode,
                        requestedQuantityMicros = line.quantityMicros,
                        updatedAtEpochMillis = now,
                    )
                },
            )
            auditEvent(
                transferId = transferId,
                action = "REQUEST",
                businessEpochDay = valid.businessEpochDay,
                reason = valid.notes.ifBlank { "درخواست انتقال بین محل‌های انبار" },
                correlationId = valid.correlationId,
                now = now,
            )
            syncRecorder?.record("INVENTORY_TRANSFER", transferId, "REQUEST", now, recordAudit = false)
            requireDocument(transferId)
        }
    }

    override suspend fun approve(command: TransferActionCommand): InventoryTransferDocument {
        val actor = authorizer.require(Permission.INVENTORY_TRANSFER_ISSUE)
        val valid = command.validated()
        requireActor(valid.actorId, actor.id, Permission.INVENTORY_TRANSFER_ISSUE)
        return database.withTransaction {
            val transfer = requireTransfer(valid.transferId)
            dataScope.requireLocation(transfer.sourceLocationId)
            val status = InventoryTransferStatus.fromStoredValue(transfer.status)
            if (status == InventoryTransferStatus.APPROVED) return@withTransaction requireDocument(transfer.id)
            InventoryTransferTransitionPolicy.requireAllowed(status, InventoryTransferStatus.APPROVED)
            if (enforceApprovalSeparationOfDuties) {
                SegregationOfDuties.requireDifferentActors(
                    "INVENTORY_TRANSFER_APPROVAL",
                    transfer.requestedByActorId,
                    actor.id,
                )
            }
            val now = clock()
            if (database.inventoryTransferDao().approve(transfer.id, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER", transfer.id).asViolation()
            }
            auditEvent(transfer.id, "APPROVE", transfer.transferEpochDay, valid.reason, transfer.correlationId, now)
            syncRecorder?.record("INVENTORY_TRANSFER", transfer.id, "APPROVE", now, recordAudit = false)
            requireDocument(transfer.id)
        }
    }

    override suspend fun issue(command: TransferActionCommand): InventoryTransferDocument {
        val actor = authorizer.require(Permission.INVENTORY_TRANSFER_ISSUE)
        val valid = command.validated()
        requireActor(valid.actorId, actor.id, Permission.INVENTORY_TRANSFER_ISSUE)
        return database.withTransaction {
            database.inventoryTransferDao().byIssueCommand(valid.commandId)?.let { replay ->
                if (replay.id != valid.transferId) throw BusinessError.IdempotencyConflict(valid.commandId).asViolation()
                return@withTransaction requireDocument(replay.id)
            }
            val transfer = requireTransfer(valid.transferId)
            dataScope.requireLocation(transfer.sourceLocationId)
            val status = InventoryTransferStatus.fromStoredValue(transfer.status)
            if (status in setOf(InventoryTransferStatus.IN_TRANSIT, InventoryTransferStatus.COMPLETED)) {
                throw BusinessError.TransferAlreadyIssued(transfer.id).asViolation()
            }
            if (status != InventoryTransferStatus.APPROVED) {
                throw BusinessError.TransferNotApproved(transfer.id).asViolation()
            }
            InventoryTransferTransitionPolicy.requireAllowed(status, InventoryTransferStatus.IN_TRANSIT)
            inventoryCommands.issueTransferDocument(
                transferId = transfer.id,
                businessEpochDay = valid.businessEpochDay,
                context = commandContext(
                    transfer = transfer,
                    actorId = actor.id,
                    commandId = valid.commandId,
                    suffix = "issue",
                    reason = valid.reason,
                    locationId = transfer.sourceLocationId,
                ),
            )
            val now = clock()
            if (database.inventoryTransferDao().markIssued(transfer.id, valid.commandId, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER", transfer.id).asViolation()
            }
            auditEvent(transfer.id, "ISSUE", valid.businessEpochDay, valid.reason, transfer.correlationId, now)
            syncRecorder?.record("INVENTORY_TRANSFER", transfer.id, "ISSUE", now, recordAudit = false)
            requireDocument(transfer.id)
        }
    }

    override suspend fun receive(command: ReceiveInventoryTransferCommand): InventoryTransferDocument {
        val actor = authorizer.require(Permission.INVENTORY_TRANSFER_RECEIVE)
        val valid = command.validated()
        requireActor(valid.actorId, actor.id, Permission.INVENTORY_TRANSFER_RECEIVE)
        return database.withTransaction {
            database.inventoryTransferDao().byReceiveCommand(valid.commandId)?.let { replay ->
                if (replay.id != valid.transferId) throw BusinessError.IdempotencyConflict(valid.commandId).asViolation()
                return@withTransaction requireDocument(replay.id)
            }
            val transfer = requireTransfer(valid.transferId)
            dataScope.requireLocation(transfer.destinationLocationId)
            val status = InventoryTransferStatus.fromStoredValue(transfer.status)
            if (status == InventoryTransferStatus.COMPLETED) {
                throw BusinessError.TransferAlreadyReceived(transfer.id).asViolation()
            }
            if (status != InventoryTransferStatus.IN_TRANSIT) {
                throw BusinessError.InvalidStateTransition(
                    "INVENTORY_TRANSFER",
                    status.storedValue,
                    InventoryTransferStatus.COMPLETED.storedValue,
                ).asViolation()
            }
            InventoryTransferTransitionPolicy.requireAllowed(status, InventoryTransferStatus.COMPLETED)
            val expectedLineIds = database.inventoryTransferDao().lines(transfer.id).map { it.id }.toSet()
            if (valid.receivedQuantityByLineId.keys != expectedLineIds) {
                throw BusinessError.InvalidInput(
                    "receivedQuantityByLineId",
                    "مقادیر دریافت باید دقیقاً برای ردیف‌های همین انتقال باشند.",
                ).asViolation()
            }
            inventoryCommands.receiveTransferDocument(
                transferId = transfer.id,
                receivedQuantityByLineId = valid.receivedQuantityByLineId,
                businessEpochDay = valid.businessEpochDay,
                context = commandContext(
                    transfer = transfer,
                    actorId = actor.id,
                    commandId = valid.commandId,
                    suffix = "receive",
                    reason = valid.reason,
                    locationId = transfer.destinationLocationId,
                ),
            )
            val now = clock()
            if (database.inventoryTransferDao().markReceived(transfer.id, valid.commandId, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER", transfer.id).asViolation()
            }
            auditEvent(transfer.id, "RECEIVE", valid.businessEpochDay, valid.reason, transfer.correlationId, now)
            syncRecorder?.record("INVENTORY_TRANSFER", transfer.id, "RECEIVE", now, recordAudit = false)
            requireDocument(transfer.id)
        }
    }

    override suspend fun createAndComplete(command: CreateInventoryTransferCommand): InventoryTransferDocument =
        database.withTransaction {
            val reason = command.notes.trim().ifBlank { "انتقال فوری بین محل‌های انبار" }
            var current = create(command)
            if (current.status == InventoryTransferStatus.COMPLETED) return@withTransaction current
            if (current.status == InventoryTransferStatus.REQUESTED) {
                current = approve(
                    TransferActionCommand(current.id, command.actorId, command.businessEpochDay, reason),
                )
            }
            if (current.status == InventoryTransferStatus.APPROVED) {
                current = issue(
                    TransferActionCommand(current.id, command.actorId, command.businessEpochDay, reason),
                )
            }
            if (current.status != InventoryTransferStatus.IN_TRANSIT) {
                throw BusinessError.InvalidBusinessState("INVENTORY_TRANSFER", current.status.storedValue).asViolation()
            }
            receive(
                ReceiveInventoryTransferCommand(
                    transferId = current.id,
                    actorId = command.actorId,
                    businessEpochDay = command.businessEpochDay,
                    receivedQuantityByLineId = current.lines.associate { line ->
                        line.id to requireNotNull(line.issuedQuantityMicros)
                    },
                    reason = reason,
                ),
            )
        }

    override suspend fun document(id: Long): InventoryTransferDocument {
        authorizer.require(Permission.INVENTORY_VIEW)
        val transfer = requireTransfer(id)
        val allowed = dataScope.activeLocations().map { it.id }.toSet()
        if (transfer.sourceLocationId !in allowed && transfer.destinationLocationId !in allowed) {
            throw BusinessError.PermissionDenied(Permission.INVENTORY_VIEW).asViolation()
        }
        return transfer.toDomain(database.inventoryTransferDao().lines(id))
    }

    private suspend fun requireCreateReplay(
        existing: StockTransferEntity,
        command: CreateInventoryTransferCommand,
    ): InventoryTransferDocument {
        val lines = database.inventoryTransferDao().lines(existing.id)
        val requested = command.lines.sortedWith(compareBy({ it.itemId }, { it.lotId ?: 0L }))
        val persisted = lines.sortedWith(compareBy({ it.itemId }, { it.lotKey }))
        val matches = existing.sourceLocationId == command.sourceLocationId &&
            existing.destinationLocationId == command.destinationLocationId &&
            existing.transferEpochDay == command.businessEpochDay && existing.note == command.notes &&
            existing.requestedByActorId == command.actorId && existing.correlationId == command.correlationId &&
            requested.size == persisted.size && requested.indices.all { index ->
                val expected = requested[index]
                val actual = persisted[index]
                expected.itemId == actual.itemId && expected.lotId == actual.lotId &&
                    expected.requestedQuantityMicros == actual.requestedQuantityMicros
            }
        if (!matches) throw BusinessError.IdempotencyConflict(existing.idempotencyKey).asViolation()
        return existing.toDomain(lines)
    }

    private suspend fun requireTransfer(id: Long): StockTransferEntity =
        database.inventoryTransferDao().transfer(id)
            ?: throw BusinessError.EntityNotFound("INVENTORY_TRANSFER", id).asViolation()

    private suspend fun requireDocument(id: Long): InventoryTransferDocument {
        val transfer = requireTransfer(id)
        return transfer.toDomain(database.inventoryTransferDao().lines(id))
    }

    private fun commandContext(
        transfer: StockTransferEntity,
        actorId: Long,
        commandId: String,
        suffix: String,
        reason: String,
        locationId: Long,
    ): InventoryCommandContext = InventoryCommandContext.local(
        referenceType = InventoryReferenceType.STOCK_TRANSFER,
        referenceId = transfer.id,
        suffix = "$suffix:$commandId",
        actorId = actorId,
        reasonCode = InventoryReasonCode.STOCK_TRANSFER,
        reason = reason,
        correlationId = transfer.correlationId,
        deviceId = transfer.deviceId,
        locationId = locationId,
    )

    private suspend fun auditEvent(
        transferId: Long,
        action: String,
        businessEpochDay: Long,
        reason: String,
        correlationId: String,
        now: Long,
    ) {
        audit.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = "INVENTORY_TRANSFER",
            entityId = transferId,
            description = "عملیات انتقال بین محل‌های انبار",
            occurredAtEpochMillis = now,
            businessEpochDay = businessEpochDay,
            reason = reason,
            correlationId = correlationId,
        )
    }

    private fun requireActor(requested: Long, authorized: Long, permission: Permission) {
        if (requested != authorized) throw BusinessError.PermissionDenied(permission).asViolation()
    }


    private fun StockTransferEntity.toDomain(lines: List<StockTransferLineEntity>) = InventoryTransferDocument(
        id = id,
        globalId = globalId,
        documentNumber = transferNo,
        sourceLocationId = sourceLocationId,
        destinationLocationId = destinationLocationId,
        businessEpochDay = transferEpochDay,
        status = InventoryTransferStatus.fromStoredValue(status),
        requestedByActorId = requestedByActorId,
        approvedByActorId = approvedByActorId,
        issuedByActorId = issuedByActorId,
        receivedByActorId = receivedByActorId,
        notes = note,
        correlationId = correlationId,
        lines = lines.map { line ->
            InventoryTransferLine(
                id = line.id,
                itemId = line.itemId,
                lotId = line.lotId,
                lotCode = line.lotCodeSnapshot,
                requestedQuantityMicros = line.requestedQuantityMicros,
                issuedQuantityMicros = line.issuedQuantityMicros,
                receivedQuantityMicros = line.receivedQuantityMicros,
                varianceQuantityMicros = line.varianceQuantityMicros,
                unitCostRial = line.unitCostRial,
                valueRial = line.valueRial,
            )
        },
    )

    private data class PreparedLine(
        val itemId: Long,
        val lotId: Long?,
        val lotCode: String,
        val quantityMicros: Long,
    )
}
