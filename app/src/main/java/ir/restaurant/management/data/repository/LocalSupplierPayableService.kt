package ir.restaurant.management.data.repository

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.SupplierPayableEntity
import ir.restaurant.management.data.db.SupplierPayableLedgerEntity

/** Canonical AP subledger boundary. Callers own the surrounding Room transaction. */
internal class LocalSupplierPayableService(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun ensureOrigin(
        sourceType: String,
        sourceId: Long,
        sourceDocumentNo: String,
        supplierId: Long,
        branchId: Long?,
        issueEpochDay: Long,
        dueEpochDay: Long,
        originalRial: Long,
        actorId: Long,
        correlationId: String,
        originJournalEntryId: Long?,
    ): SupplierPayableEntity {
        require(sourceId > 0 && supplierId > 0)
        require(issueEpochDay > 0 && dueEpochDay >= issueEpochDay)
        require(originalRial >= 0)
        val normalizedType = sourceType.trim().uppercase()
        require(normalizedType.matches(Regex("[A-Z][A-Z0-9_]{1,63}")))
        val key = "AP:$normalizedType:$sourceId"
        database.phase3Dao().payableBySource(normalizedType, sourceId)?.let { existing ->
            require(
                existing.supplierId == supplierId && existing.branchId == branchId &&
                    existing.originalRial == originalRial && existing.sourceDocumentNo == sourceDocumentNo,
            ) { "ap_origin_idempotency_conflict" }
            return existing
        }
        val now = clock()
        val id = database.phase3Dao().insertPayable(
            SupplierPayableEntity(
                globalId = GlobalId.new().value,
                supplierId = supplierId,
                branchId = branchId,
                sourceType = normalizedType,
                sourceId = sourceId,
                sourceDocumentNo = sourceDocumentNo,
                issueEpochDay = issueEpochDay,
                dueEpochDay = dueEpochDay,
                originalRial = originalRial,
                settledRial = 0,
                status = if (originalRial == 0L) "SETTLED" else "OPEN",
                idempotencyKey = key,
                correlationId = correlationId,
                createdByActorId = actorId,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        if (originalRial > 0) {
            database.phase3Dao().insertPayableLedger(
                SupplierPayableLedgerEntity(
                    payableId = id,
                    supplierId = supplierId,
                    branchId = branchId,
                    businessEpochDay = issueEpochDay,
                    entryType = "ORIGIN",
                    amountDeltaRial = originalRial,
                    treasuryTransactionId = null,
                    journalEntryId = originJournalEntryId,
                    commandId = "$key:ORIGIN",
                    correlationId = correlationId,
                    reason = "ایجاد بدهی تأمین‌کننده از $normalizedType/$sourceDocumentNo",
                    actorId = actorId,
                    createdAtEpochMillis = now,
                ),
            )
        }
        return requireNotNull(database.phase3Dao().payableById(id))
    }

    suspend fun settle(
        sourceType: String,
        sourceId: Long,
        amountRial: Long,
        businessEpochDay: Long,
        commandId: String,
        correlationId: String,
        treasuryTransactionId: String,
        journalEntryId: Long,
        actorId: Long,
        reason: String,
    ): SupplierPayableEntity {
        val normalizedCommand = GlobalId.parse(commandId).value
        database.phase3Dao().payableLedgerByCommand(normalizedCommand)?.let { replay ->
            require(replay.entryType == "SETTLEMENT" && replay.amountDeltaRial == -amountRial) {
                "ap_settlement_idempotency_conflict"
            }
            return requireNotNull(database.phase3Dao().payableById(replay.payableId))
        }
        require(amountRial > 0)
        val payable = database.phase3Dao().payableBySource(sourceType.trim().uppercase(), sourceId)
            ?: error("حساب پرداختنی مرجع پیدا نشد.")
        val remaining = payable.originalRial - payable.settledRial
        require(amountRial <= remaining) { "مبلغ تسویه از مانده حساب پرداختنی بیشتر است." }
        val newSettled = Math.addExact(payable.settledRial, amountRial)
        val newStatus = if (newSettled == payable.originalRial) "SETTLED" else "PARTIAL"
        val now = clock()
        check(database.phase3Dao().compareAndSetPayableSettlement(payable.id, payable.settledRial, newSettled, newStatus, now) == 1) {
            "مانده حساب پرداختنی هم‌زمان تغییر کرده است؛ دوباره تلاش کنید."
        }
        database.phase3Dao().insertPayableLedger(
            SupplierPayableLedgerEntity(
                payableId = payable.id,
                supplierId = payable.supplierId,
                branchId = payable.branchId,
                businessEpochDay = businessEpochDay,
                entryType = "SETTLEMENT",
                amountDeltaRial = -amountRial,
                treasuryTransactionId = treasuryTransactionId,
                journalEntryId = journalEntryId,
                commandId = normalizedCommand,
                correlationId = correlationId,
                reason = reason.trim(),
                actorId = actorId,
                createdAtEpochMillis = now,
            ),
        )
        return requireNotNull(database.phase3Dao().payableById(payable.id))
    }

    suspend fun reverseSettlement(
        sourceType: String,
        sourceId: Long,
        amountRial: Long,
        businessEpochDay: Long,
        commandId: String,
        correlationId: String,
        treasuryTransactionId: String,
        journalEntryId: Long,
        actorId: Long,
        reason: String,
    ): SupplierPayableEntity {
        val normalizedCommand = GlobalId.parse(commandId).value
        database.phase3Dao().payableLedgerByCommand(normalizedCommand)?.let { replay ->
            require(replay.entryType == "SETTLEMENT_REVERSAL" && replay.amountDeltaRial == amountRial) {
                "ap_settlement_reversal_idempotency_conflict"
            }
            return requireNotNull(database.phase3Dao().payableById(replay.payableId))
        }
        require(amountRial > 0)
        val payable = database.phase3Dao().payableBySource(sourceType.trim().uppercase(), sourceId)
            ?: error("حساب پرداختنی مرجع پیدا نشد.")
        require(amountRial <= payable.settledRial) { "مبلغ برگشت از تسویه ثبت‌شده بیشتر است." }
        val newSettled = payable.settledRial - amountRial
        val newStatus = if (newSettled == 0L) "OPEN" else "PARTIAL"
        val now = clock()
        check(database.phase3Dao().compareAndSetPayableSettlement(payable.id, payable.settledRial, newSettled, newStatus, now) == 1) {
            "مانده حساب پرداختنی هم‌زمان تغییر کرده است؛ دوباره تلاش کنید."
        }
        database.phase3Dao().insertPayableLedger(
            SupplierPayableLedgerEntity(
                payableId = payable.id,
                supplierId = payable.supplierId,
                branchId = payable.branchId,
                businessEpochDay = businessEpochDay,
                entryType = "SETTLEMENT_REVERSAL",
                amountDeltaRial = amountRial,
                treasuryTransactionId = treasuryTransactionId,
                journalEntryId = journalEntryId,
                commandId = normalizedCommand,
                correlationId = correlationId,
                reason = reason.trim(),
                actorId = actorId,
                createdAtEpochMillis = now,
            ),
        )
        return requireNotNull(database.phase3Dao().payableById(payable.id))
    }
    /**
     * Applies a supplier credit note against an existing payable without moving cash.
     * `settledRial` represents extinguished liability (cash settlement or approved credit);
     * the ledger entry type preserves the economic reason.
     */
    suspend fun applyCredit(
        sourceType: String,
        sourceId: Long,
        amountRial: Long,
        businessEpochDay: Long,
        commandId: String,
        correlationId: String,
        journalEntryId: Long?,
        actorId: Long,
        reason: String,
    ): SupplierPayableEntity {
        val normalizedCommand = GlobalId.parse(commandId).value
        database.phase3Dao().payableLedgerByCommand(normalizedCommand)?.let { replay ->
            require(replay.entryType == "CREDIT" && replay.amountDeltaRial == -amountRial) {
                "ap_credit_idempotency_conflict"
            }
            return requireNotNull(database.phase3Dao().payableById(replay.payableId))
        }
        require(amountRial > 0)
        val payable = database.phase3Dao().payableBySource(sourceType.trim().uppercase(), sourceId)
            ?: error("حساب پرداختنی مرجع پیدا نشد.")
        val remaining = payable.originalRial - payable.settledRial
        require(amountRial <= remaining) { "اعتبار تأمین‌کننده از مانده حساب پرداختنی بیشتر است." }
        val newSettled = Math.addExact(payable.settledRial, amountRial)
        val newStatus = if (newSettled == payable.originalRial) "SETTLED" else "PARTIAL"
        val now = clock()
        check(database.phase3Dao().compareAndSetPayableSettlement(payable.id, payable.settledRial, newSettled, newStatus, now) == 1) {
            "مانده حساب پرداختنی هم‌زمان تغییر کرده است؛ دوباره تلاش کنید."
        }
        database.phase3Dao().insertPayableLedger(
            SupplierPayableLedgerEntity(
                payableId = payable.id,
                supplierId = payable.supplierId,
                branchId = payable.branchId,
                businessEpochDay = businessEpochDay,
                entryType = "CREDIT",
                amountDeltaRial = -amountRial,
                treasuryTransactionId = null,
                journalEntryId = journalEntryId,
                commandId = normalizedCommand,
                correlationId = correlationId,
                reason = reason.trim(),
                actorId = actorId,
                createdAtEpochMillis = now,
            ),
        )
        return requireNotNull(database.phase3Dao().payableById(payable.id))
    }

    suspend fun voidOrigin(
        sourceType: String,
        sourceId: Long,
        businessEpochDay: Long,
        commandId: String,
        correlationId: String,
        journalEntryId: Long?,
        actorId: Long,
        reason: String,
    ): SupplierPayableEntity {
        val normalizedCommand = GlobalId.parse(commandId).value
        database.phase3Dao().payableLedgerByCommand(normalizedCommand)?.let { replay ->
            require(replay.entryType == "ORIGIN_REVERSAL") { "ap_origin_reversal_idempotency_conflict" }
            return requireNotNull(database.phase3Dao().payableById(replay.payableId))
        }
        val payable = database.phase3Dao().payableBySource(sourceType.trim().uppercase(), sourceId)
            ?: error("حساب پرداختنی مرجع پیدا نشد.")
        require(payable.status != "VOID") { "حساب پرداختنی قبلاً باطل شده است." }
        require(payable.settledRial == 0L) { "قبل از ابطال بدهی، تمام تسویه‌های آن باید برگشت داده شوند." }
        val now = clock()
        check(database.phase3Dao().updatePayable(payable.copy(status = "VOID", updatedAtEpochMillis = now)) == 1) {
            "ابطال حساب پرداختنی انجام نشد."
        }
        if (payable.originalRial > 0) {
            database.phase3Dao().insertPayableLedger(
                SupplierPayableLedgerEntity(
                    payableId = payable.id,
                    supplierId = payable.supplierId,
                    branchId = payable.branchId,
                    businessEpochDay = businessEpochDay,
                    entryType = "ORIGIN_REVERSAL",
                    amountDeltaRial = -payable.originalRial,
                    treasuryTransactionId = null,
                    journalEntryId = journalEntryId,
                    commandId = normalizedCommand,
                    correlationId = correlationId,
                    reason = reason.trim(),
                    actorId = actorId,
                    createdAtEpochMillis = now,
                ),
            )
        }
        return requireNotNull(database.phase3Dao().payableById(payable.id))
    }

}
