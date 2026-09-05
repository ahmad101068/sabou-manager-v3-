package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryCountEntity
import ir.restaurant.management.data.db.InventoryCountLineEntity
import ir.restaurant.management.data.db.InventoryCountSessionEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.JournalStatus
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.CreateInventoryCountSessionCommand
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryCountActionCommand
import ir.restaurant.management.domain.inventory.InventoryCountLine
import ir.restaurant.management.domain.inventory.InventoryCountLineStatus
import ir.restaurant.management.domain.inventory.InventoryCountLineView
import ir.restaurant.management.domain.inventory.InventoryCountScope
import ir.restaurant.management.domain.inventory.InventoryCountSearch
import ir.restaurant.management.domain.inventory.InventoryCountService
import ir.restaurant.management.domain.inventory.InventoryCountSession
import ir.restaurant.management.domain.inventory.InventoryCountStatus
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryRecountPolicy
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.PostInventoryCountCommand
import ir.restaurant.management.domain.inventory.RecordInventoryCountCommand
import ir.restaurant.management.domain.inventory.toView
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties

/** Document workflow for blind count, recount, approval and atomic variance posting. */
class LocalInventoryCountService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val recountPolicy: InventoryRecountPolicy = InventoryRecountPolicy.DEFAULT,
    private val enforceSeparationOfDuties: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val accountingPosting: AccountingPostingService = LocalAccountingPostingEngine(database, clock = clock),
) : InventoryCountService {
    private val audit = LocalAuditEventWriter(database)
    private val dataScope = LocalDataScopeService(database, authorizer)

    override suspend fun search(query: InventoryCountSearch): List<InventoryCountSession> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        valid.locationId?.let { dataScope.requireLocation(it) }
        val allowedLocationIds = dataScope.activeLocations().map { it.id }.toSet()
        if (allowedLocationIds.isEmpty()) return emptyList()
        return database.inventoryCountDao().searchSessions(
            status = valid.status?.storedValue,
            locationId = valid.locationId,
            limit = valid.limit,
            offset = valid.offset,
        ).filter { it.locationId in allowedLocationIds }.map { it.toDomain() }
    }

    override suspend fun create(command: CreateInventoryCountSessionCommand): Long {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_CREATE)
        val valid = command.validated()
        val key = "inventory_count_session:${valid.commandId}"
        return database.withTransaction {
            database.inventoryCountDao().byIdempotencyKey(key)?.let { existing ->
                val matches = existing.locationId == valid.locationId &&
                    existing.scope == valid.scope.storedValue && existing.blindCount == valid.blindCount &&
                    existing.assignedToActorId == valid.assignedToActorId &&
                    existing.businessEpochDay == valid.businessEpochDay && existing.notes == valid.notes &&
                    existing.correlationId == valid.correlationId && existing.createdByActorId == actor.id
                if (!matches) throw BusinessError.IdempotencyConflict(key).asViolation()
                return@withTransaction existing.id
            }
            dataScope.requireLocation(valid.locationId)
            valid.assignedToActorId?.let { assignedId ->
                val assigned = database.securityDao().byId(assignedId)
                if (assigned == null || !assigned.isActive) {
                    throw BusinessError.EntityNotFound("APP_USER", assignedId).asViolation()
                }
            }
            val allRows = database.inventoryCountDao().locationSnapshot(valid.locationId, 10_000, 0)
            val rows = when (valid.scope) {
                InventoryCountScope.ALL_LOCATION -> allRows
                InventoryCountScope.ITEM_SELECTION -> allRows.filter { it.itemId in valid.itemIds }
                InventoryCountScope.LEGACY_UNKNOWN -> emptyList()
            }
            if (rows.isEmpty()) throw BusinessError.InvalidBusinessState("INVENTORY_COUNT", "EMPTY_SCOPE").asViolation()
            if (valid.scope == InventoryCountScope.ITEM_SELECTION && rows.map { it.itemId }.toSet() != valid.itemIds) {
                throw BusinessError.EntityNotFound("INVENTORY_ITEM", null).asViolation()
            }
            val lotsByItem = database.inventoryCountDao().lotSnapshot(valid.locationId).groupBy { it.itemId }
            val now = clock()
            val token = valid.commandId.filter { it.isLetterOrDigit() }.uppercase().take(12)
            val documentNumber = "IC-${valid.businessEpochDay}-$token"
            val sessionId = database.inventoryCountDao().insertSession(
                InventoryCountSessionEntity(
                    globalId = valid.commandId,
                    documentNumber = documentNumber,
                    idempotencyKey = key,
                    locationId = valid.locationId,
                    scope = valid.scope.storedValue,
                    blindCount = valid.blindCount,
                    createdByActorId = actor.id,
                    assignedToActorId = valid.assignedToActorId,
                    status = InventoryCountStatus.DRAFT.storedValue,
                    snapshotEpochMillis = now,
                    businessEpochDay = valid.businessEpochDay,
                    startedAtEpochMillis = null,
                    submittedAtEpochMillis = null,
                    approvedByActorId = null,
                    approvedAtEpochMillis = null,
                    postedByActorId = null,
                    postedAtEpochMillis = null,
                    cancelledAtEpochMillis = null,
                    notes = valid.notes,
                    correlationId = valid.correlationId,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            database.inventoryCountDao().insertLines(
                rows.flatMap { row ->
                    if (!row.trackLot) {
                        listOf(
                            InventoryCountLineEntity(
                                sessionId = sessionId, itemId = row.itemId, lotId = null, lotKey = 0,
                                systemQuantitySnapshotMicros = row.quantityMicros, systemValueSnapshotRial = row.valueRial,
                                firstCountQuantityMicros = null, secondCountQuantityMicros = null,
                                finalCountQuantityMicros = null, finalCountValueRial = null,
                                varianceQuantityMicros = null, varianceValueRial = null,
                                status = InventoryCountLineStatus.PENDING.storedValue, reason = "",
                                countedByActorId = null, countedAtEpochMillis = null, updatedAtEpochMillis = now,
                            ),
                        )
                    } else {
                        val lots = lotsByItem[row.itemId].orEmpty()
                        val lotQuantity = lots.fold(0L) { sum, lot -> SignedLongMath.add(sum, lot.quantityMicros) }
                        if (lotQuantity != row.quantityMicros) {
                            throw BusinessError.InvalidBusinessState("INVENTORY_LOT", "LOT_BALANCE_MISMATCH").asViolation()
                        }
                        if (lots.isEmpty()) {
                            listOf(
                                InventoryCountLineEntity(
                                    sessionId = sessionId, itemId = row.itemId, lotId = null, lotKey = 0,
                                    systemQuantitySnapshotMicros = 0, systemValueSnapshotRial = 0,
                                    firstCountQuantityMicros = null, secondCountQuantityMicros = null,
                                    finalCountQuantityMicros = null, finalCountValueRial = null,
                                    varianceQuantityMicros = null, varianceValueRial = null,
                                    status = InventoryCountLineStatus.PENDING.storedValue, reason = "",
                                    countedByActorId = null, countedAtEpochMillis = null, updatedAtEpochMillis = now,
                                ),
                            )
                        } else {
                            var allocatedValue = 0L
                            lots.mapIndexed { index, lot ->
                                val value = if (index == lots.lastIndex) {
                                    SignedLongMath.subtract(row.valueRial, allocatedValue)
                                } else if (row.quantityMicros == 0L) {
                                    0L
                                } else {
                                    FixedPointRatio.multiplyDivide(row.valueRial, lot.quantityMicros, row.quantityMicros)
                                }
                                allocatedValue = SignedLongMath.add(allocatedValue, value)
                                InventoryCountLineEntity(
                                    sessionId = sessionId, itemId = row.itemId, lotId = lot.lotId, lotKey = lot.lotId,
                                    systemQuantitySnapshotMicros = lot.quantityMicros, systemValueSnapshotRial = value,
                                    firstCountQuantityMicros = null, secondCountQuantityMicros = null,
                                    finalCountQuantityMicros = null, finalCountValueRial = null,
                                    varianceQuantityMicros = null, varianceValueRial = null,
                                    status = InventoryCountLineStatus.PENDING.storedValue, reason = "",
                                    countedByActorId = null, countedAtEpochMillis = null, updatedAtEpochMillis = now,
                                )
                            }
                        }
                    }
                },
            )
            auditEvent(sessionId, "CREATE", actor.id, valid.businessEpochDay, "ایجاد جلسه انبارگردانی", valid.notes.ifBlank { "COUNT_CREATED" }, valid.correlationId, now)
            syncRecorder?.record("INVENTORY_COUNT_SESSION", sessionId, "CREATE", now)
            sessionId
        }
    }

    override suspend fun open(command: InventoryCountActionCommand) {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_CREATE)
        requireActor(command.actorId, actor.id)
        val reason = requiredReason(command.reason)
        database.withTransaction {
            val session = requireScopedSession(command.sessionId)
            requireAssignedOrCreator(session, actor.id)
            if (database.inventoryCountDao().open(session.id, clock()) != 1) {
                throw BusinessError.InvalidStateTransition("INVENTORY_COUNT_SESSION", session.status, "OPEN").asViolation()
            }
            auditEvent(session.id, "OPEN", actor.id, session.businessEpochDay, "بازکردن جلسه انبارگردانی", reason, session.correlationId, clock())
        }
    }

    override suspend fun record(command: RecordInventoryCountCommand) {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_PERFORM)
        requireActor(command.actorId, actor.id)
        QuantityMicros.of(command.countedQuantityMicros)
        command.unitCostOverrideRial?.let { MoneyRial.of(it) }
        val reason = command.reason.trim()
        database.withTransaction {
            var session = requireScopedSession(command.sessionId)
            requireAssigned(session, actor.id)
            if (session.status in setOf(InventoryCountStatus.OPEN.storedValue, InventoryCountStatus.RECOUNT_REQUIRED.storedValue)) {
                if (database.inventoryCountDao().markCounting(session.id, clock()) != 1) {
                    throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_SESSION", session.id).asViolation()
                }
                session = requireSession(session.id)
            }
            if (session.status != InventoryCountStatus.COUNTING.storedValue) {
                throw BusinessError.InvalidBusinessState("INVENTORY_COUNT_SESSION", session.status).asViolation()
            }
            val line = database.inventoryCountDao().line(session.id, command.lineId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_COUNT_LINE", command.lineId).asViolation()
            val expectedStatus = InventoryCountLineStatus.fromStoredValue(line.status)
            if (expectedStatus !in setOf(InventoryCountLineStatus.PENDING, InventoryCountLineStatus.RECOUNT_REQUIRED)) {
                throw BusinessError.InvalidBusinessState("INVENTORY_COUNT_LINE", line.status).asViolation()
            }
            val countedValue = countedValue(line, command.countedQuantityMicros, command.unitCostOverrideRial)
            val varianceQuantity = SignedLongMath.subtract(command.countedQuantityMicros, line.systemQuantitySnapshotMicros)
            val varianceValue = SignedLongMath.subtract(countedValue, line.systemValueSnapshotRial)
            if ((varianceQuantity != 0L || varianceValue != 0L) && reason.length < 3) {
                throw BusinessError.InvalidInput("reason", "برای مغایرت شمارش دلیل ثبت کنید.").asViolation()
            }
            val needsRecount = expectedStatus == InventoryCountLineStatus.PENDING && recountPolicy.requiresRecount(
                line.systemQuantitySnapshotMicros,
                command.countedQuantityMicros,
                line.systemValueSnapshotRial,
                countedValue,
            )
            val nextStatus = if (needsRecount) {
                InventoryCountLineStatus.RECOUNT_REQUIRED
            } else {
                InventoryCountLineStatus.FINALIZED
            }
            val now = clock()
            val updated = database.inventoryCountDao().recordLine(
                sessionId = session.id,
                id = line.id,
                expectedStatus = expectedStatus.storedValue,
                firstCountQuantityMicros = line.firstCountQuantityMicros ?: command.countedQuantityMicros,
                secondCountQuantityMicros = command.countedQuantityMicros.takeIf {
                    expectedStatus == InventoryCountLineStatus.RECOUNT_REQUIRED
                },
                finalCountQuantityMicros = command.countedQuantityMicros.takeIf { !needsRecount },
                finalCountValueRial = countedValue.takeIf { !needsRecount },
                varianceQuantityMicros = varianceQuantity,
                varianceValueRial = varianceValue,
                nextStatus = nextStatus.storedValue,
                reason = reason,
                actorId = actor.id,
                now = now,
            )
            if (updated != 1) throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_LINE", line.id).asViolation()
            auditEvent(session.id, "COUNT_LINE", actor.id, session.businessEpochDay, "ثبت شمارش ردیف ${line.id}", reason.ifBlank { "NO_VARIANCE" }, session.correlationId, now)
        }
    }

    override suspend fun submit(command: InventoryCountActionCommand) {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_PERFORM)
        requireActor(command.actorId, actor.id)
        val reason = requiredReason(command.reason)
        database.withTransaction {
            val session = requireScopedSession(command.sessionId)
            requireAssigned(session, actor.id)
            if (session.status != InventoryCountStatus.COUNTING.storedValue) {
                throw BusinessError.InvalidBusinessState("INVENTORY_COUNT_SESSION", session.status).asViolation()
            }
            if (database.inventoryCountDao().pendingLineCount(session.id) != 0) {
                throw BusinessError.InvalidBusinessState("INVENTORY_COUNT_SESSION", "LINES_PENDING").asViolation()
            }
            val next = if (database.inventoryCountDao().recountLineCount(session.id) > 0) {
                InventoryCountStatus.RECOUNT_REQUIRED
            } else {
                InventoryCountStatus.PENDING_APPROVAL
            }
            val now = clock()
            if (database.inventoryCountDao().submit(session.id, next.storedValue, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_SESSION", session.id).asViolation()
            }
            auditEvent(session.id, "SUBMIT", actor.id, session.businessEpochDay, "ارسال انبارگردانی", reason, session.correlationId, now)
        }
    }

    override suspend fun approve(command: InventoryCountActionCommand) {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_APPROVE)
        requireActor(command.actorId, actor.id)
        val reason = requiredReason(command.reason)
        database.withTransaction {
            val session = requireScopedSession(command.sessionId)
            if (session.status != InventoryCountStatus.PENDING_APPROVAL.storedValue) {
                throw BusinessError.CountNotApproved(session.id).asViolation()
            }
            if (enforceSeparationOfDuties) {
                SegregationOfDuties.requireDifferentActors("INVENTORY_COUNT_APPROVAL", session.createdByActorId, actor.id)
                database.inventoryCountDao().lines(session.id).mapNotNull { it.countedByActorId }.distinct().forEach { counterId ->
                    SegregationOfDuties.requireDifferentActors("INVENTORY_COUNT_APPROVAL", counterId, actor.id)
                }
            }
            val now = clock()
            if (database.inventoryCountDao().approve(session.id, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_SESSION", session.id).asViolation()
            }
            auditEvent(session.id, "APPROVE", actor.id, session.businessEpochDay, "تأیید انبارگردانی", reason, session.correlationId, now)
        }
    }

    override suspend fun cancel(command: InventoryCountActionCommand) {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_CREATE)
        requireActor(command.actorId, actor.id)
        val reason = requiredReason(command.reason)
        database.withTransaction {
            val session = requireScopedSession(command.sessionId)
            requireAssignedOrCreator(session, actor.id)
            val now = clock()
            if (database.inventoryCountDao().cancel(session.id, now) != 1) {
                throw BusinessError.InvalidStateTransition(
                    "INVENTORY_COUNT_SESSION",
                    session.status,
                    InventoryCountStatus.CANCELLED.storedValue,
                ).asViolation()
            }
            auditEvent(
                session.id,
                "CANCEL",
                actor.id,
                session.businessEpochDay,
                "لغو جلسه انبارگردانی",
                reason,
                session.correlationId,
                now,
            )
        }
    }

    override suspend fun post(command: PostInventoryCountCommand): InventoryCountSession {
        val actor = authorizer.require(Permission.INVENTORY_COUNT_POST)
        requireActor(command.actorId, actor.id)
        val commandId = GlobalId.parse(command.commandId).value
        return database.withTransaction {
            database.inventoryCountDao().byPostCommandId(commandId)?.let { replay ->
                if (replay.id != command.sessionId) throw BusinessError.IdempotencyConflict(commandId).asViolation()
                dataScope.requireLocation(replay.locationId)
                return@withTransaction replay.toDomain()
            }
            val session = requireSession(command.sessionId)
            val location = dataScope.requireLocation(session.locationId)
            if (session.status == InventoryCountStatus.POSTED.storedValue) {
                throw BusinessError.CountAlreadyPosted(session.id).asViolation()
            }
            if (session.status != InventoryCountStatus.APPROVED.storedValue) {
                throw BusinessError.CountNotApproved(session.id).asViolation()
            }
            if (database.inventoryControlDao().closureOverlaps(session.businessEpochDay, session.businessEpochDay)) {
                throw BusinessError.ClosedInventoryPeriod(session.businessEpochDay).asViolation()
            }
            val lines = database.inventoryCountDao().lines(session.id)
            if (lines.isEmpty() || lines.any { it.status != InventoryCountLineStatus.FINALIZED.storedValue }) {
                throw BusinessError.InvalidBusinessState("INVENTORY_COUNT_SESSION", "LINES_NOT_FINALIZED").asViolation()
            }
            val now = clock()
            val inventoryCommands = LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer)
            var totalValueDelta = 0L
            lines.groupBy { it.itemId }.forEach { (itemId, itemLines) ->
                val item = database.inventoryDao().activeById(itemId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
                val balance = database.inventoryBalanceDao().byKey(itemId, session.locationId)
                    ?: throw BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", itemId).asViolation()
                val systemQuantity = itemLines.fold(0L) { sum, line ->
                    SignedLongMath.add(sum, line.systemQuantitySnapshotMicros)
                }
                val systemValue = itemLines.fold(0L) { sum, line ->
                    SignedLongMath.add(sum, line.systemValueSnapshotRial)
                }
                if (balance.onHandMicros != systemQuantity || balance.inventoryValueRial != systemValue) {
                    throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_SNAPSHOT", itemId).asViolation()
                }
                val finalQuantity = itemLines.fold(0L) { sum, line ->
                    SignedLongMath.add(sum, requireNotNull(line.finalCountQuantityMicros))
                }
                val finalValue = itemLines.fold(0L) { sum, line ->
                    SignedLongMath.add(sum, requireNotNull(line.finalCountValueRial))
                }
                val quantityDelta = SignedLongMath.subtract(finalQuantity, systemQuantity)
                val valueDelta = SignedLongMath.subtract(finalValue, systemValue)
                totalValueDelta = SignedLongMath.add(totalValueDelta, valueDelta)

                val countId = database.inventoryControlDao().insertCount(
                    InventoryCountEntity(
                        itemId = itemId,
                        previousQuantityMicros = systemQuantity,
                        countedQuantityMicros = finalQuantity,
                        previousValueRial = systemValue,
                        countedValueRial = finalValue,
                        countEpochDay = session.businessEpochDay,
                        reason = itemLines.map { it.reason }.filter { it.isNotBlank() }.distinct().joinToString(" | ")
                            .ifBlank { "انبارگردانی ${session.documentNumber}" }.take(300),
                        createdAtEpochMillis = now,
                        globalId = GlobalId.new().value,
                        idempotencyKey = "inventory_count_session:${session.id}:item:$itemId",
                        correlationId = session.correlationId,
                        actorId = actor.id,
                        deviceId = "local-android",
                        locationId = session.locationId,
                    ),
                )
                if (quantityDelta != 0L || valueDelta != 0L) {
                    val context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.INVENTORY_COUNT,
                        referenceId = countId,
                        suffix = "session:${session.id}:item:$itemId",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.COUNT_VARIANCE,
                        reason = "مغایرت ${session.documentNumber}",
                        correlationId = session.correlationId,
                        locationId = session.locationId,
                    )
                    if (item.trackLot) {
                        val lotLines = itemLines.filter { it.lotId != null }
                        val anonymousLine = itemLines.firstOrNull { it.lotId == null }
                        if (anonymousLine != null && requireNotNull(anonymousLine.finalCountQuantityMicros) != 0L) {
                            throw BusinessError.InvalidBusinessState(
                                "INVENTORY_COUNT",
                                "LOT_ID_REQUIRED_FOR_POSITIVE_VARIANCE",
                            ).asViolation()
                        }
                        inventoryCommands.adjustLotControlledCount(
                            itemId = itemId,
                            countedLotQuantities = lotLines.associate { requireNotNull(it.lotId) to requireNotNull(it.finalCountQuantityMicros) },
                            countedValueRial = finalValue,
                            referenceId = countId,
                            movementEpochDay = session.businessEpochDay,
                            context = context,
                            notes = "انبارگردانی لات‌محور ${session.documentNumber}",
                        )
                    } else {
                        require(itemLines.size == 1 && itemLines.single().lotId == null) {
                            "non_lot_count_must_have_single_line"
                        }
                        inventoryCommands.adjustToCount(
                            itemId = itemId,
                            countedQuantityMicros = finalQuantity,
                            countedValueRial = finalValue,
                            referenceId = countId,
                            movementEpochDay = session.businessEpochDay,
                            context = context,
                            notes = itemLines.single().reason,
                        )
                    }
                }
            }

            if (totalValueDelta != 0L) {
                val variance = MoneyRial.of(kotlin.math.abs(totalValueDelta))
                val linesForJournal = if (totalValueDelta > 0L) {
                    listOf(
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, debit = variance),
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_COUNT_GAIN, credit = variance),
                    )
                } else {
                    listOf(
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_COUNT_LOSS, debit = variance),
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, credit = variance),
                    )
                }
                accountingPosting.post(
                    AccountingPostingCommand(
                        entryNo = "IC-${session.id}",
                        sourceType = "INVENTORY_COUNT_SESSION",
                        sourceId = session.id,
                        businessEpochDay = session.businessEpochDay,
                        description = "مغایرت انبارگردانی ${session.documentNumber}",
                        lines = linesForJournal,
                        idempotencyKey = "INVENTORY_COUNT_SESSION:${session.id}:VARIANCE",
                        correlationId = CorrelationId.parse(session.correlationId),
                        actorId = actor.id,
                        status = JournalStatus.POSTED,
                        accountingScope = AccountingScope.BRANCH,
                        branchId = requireNotNull(location.branchId),
                    ),
                )
            }
            if (database.inventoryCountDao().markPosted(session.id, commandId, actor.id, now) != 1) {
                throw BusinessError.ConcurrencyConflict("INVENTORY_COUNT_SESSION", session.id).asViolation()
            }
            auditEvent(
                session.id, "POST", actor.id, session.businessEpochDay, "ثبت نهایی انبارگردانی",
                "COUNT_VARIANCE_POSTED", session.correlationId, now,
            )
            syncRecorder?.record("INVENTORY_COUNT_SESSION", session.id, "POST", now)
            requireSession(session.id).toDomain()
        }
    }

    override suspend fun session(id: Long): InventoryCountSession {
        authorizer.require(Permission.INVENTORY_VIEW)
        return requireScopedSession(id).toDomain()
    }

    override suspend fun lines(sessionId: Long, canReviewVariance: Boolean): List<InventoryCountLineView> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val session = requireScopedSession(sessionId)
        val canReview = canReviewVariance && authorizer.can(Permission.INVENTORY_COUNT_APPROVE)
        return database.inventoryCountDao().lines(session.id).map { it.toDomain().toView(session.blindCount, canReview) }
    }

    private fun countedValue(
        line: InventoryCountLineEntity,
        countedQuantityMicros: Long,
        unitCostOverrideRial: Long?,
    ): Long = when {
        countedQuantityMicros == 0L -> 0L
        line.systemQuantitySnapshotMicros > 0L -> FixedPointRatio.multiplyDivide(
            line.systemValueSnapshotRial,
            countedQuantityMicros,
            line.systemQuantitySnapshotMicros,
        )
        unitCostOverrideRial != null -> MoneyRial.of(unitCostOverrideRial)
            .times(QuantityMicros.positive(countedQuantityMicros)).value
        else -> throw BusinessError.CountUnitCostRequired(line.id).asViolation()
    }

    private suspend fun requireScopedSession(id: Long): InventoryCountSessionEntity =
        requireSession(id).also { dataScope.requireLocation(it.locationId) }

    private suspend fun requireSession(id: Long): InventoryCountSessionEntity =
        database.inventoryCountDao().session(id)
            ?: throw BusinessError.EntityNotFound("INVENTORY_COUNT_SESSION", id).asViolation()

    private fun requireActor(requested: Long, authorized: Long) {
        if (requested != authorized) throw BusinessError.PermissionDenied(Permission.INVENTORY_VIEW).asViolation()
    }

    private fun requireAssigned(session: InventoryCountSessionEntity, actorId: Long) {
        if (session.assignedToActorId != null && session.assignedToActorId != actorId) {
            throw BusinessError.PermissionDenied(Permission.INVENTORY_COUNT_PERFORM).asViolation()
        }
    }

    private fun requireAssignedOrCreator(session: InventoryCountSessionEntity, actorId: Long) {
        if (actorId != session.createdByActorId && actorId != session.assignedToActorId) {
            throw BusinessError.PermissionDenied(Permission.INVENTORY_COUNT_CREATE).asViolation()
        }
    }

    private fun requiredReason(reason: String): String = reason.trim().also {
        require(it.length in 3..300) { "دلیل عملیات انبارگردانی الزامی است." }
    }

    private suspend fun auditEvent(
        sessionId: Long,
        action: String,
        actorId: Long,
        businessEpochDay: Long,
        description: String,
        reason: String,
        correlationId: String,
        now: Long,
    ) {
        val currentActor = authorizer.actorIdentity()
        requireActor(actorId, currentActor.id)
        audit.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = "INVENTORY_COUNT_SESSION",
            entityId = sessionId,
            description = description,
            occurredAtEpochMillis = now,
            businessEpochDay = businessEpochDay,
            reason = reason,
            correlationId = correlationId,
        )
    }

    private fun InventoryCountSessionEntity.toDomain() = InventoryCountSession(
        id = id,
        globalId = globalId,
        documentNumber = documentNumber,
        locationId = locationId,
        scope = InventoryCountScope.entries.firstOrNull { candidate -> candidate.storedValue == this.scope }
            ?: InventoryCountScope.LEGACY_UNKNOWN,
        blindCount = blindCount,
        createdByActorId = createdByActorId,
        assignedToActorId = assignedToActorId,
        status = InventoryCountStatus.fromStoredValue(status),
        snapshotEpochMillis = snapshotEpochMillis,
        businessEpochDay = businessEpochDay,
        submittedAtEpochMillis = submittedAtEpochMillis,
        approvedByActorId = approvedByActorId,
        approvedAtEpochMillis = approvedAtEpochMillis,
        postedAtEpochMillis = postedAtEpochMillis,
        notes = notes,
        correlationId = correlationId,
    )

    private fun InventoryCountLineEntity.toDomain() = InventoryCountLine(
        id = id,
        sessionId = sessionId,
        itemId = itemId,
        lotId = lotId,
        systemQuantitySnapshotMicros = systemQuantitySnapshotMicros,
        systemValueSnapshotRial = systemValueSnapshotRial,
        firstCountQuantityMicros = firstCountQuantityMicros,
        secondCountQuantityMicros = secondCountQuantityMicros,
        finalCountQuantityMicros = finalCountQuantityMicros,
        finalCountValueRial = finalCountValueRial,
        varianceQuantityMicros = varianceQuantityMicros,
        varianceValueRial = varianceValueRial,
        status = InventoryCountLineStatus.fromStoredValue(status),
        reason = reason,
    )
}
