package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.JournalEntryEntity
import ir.restaurant.management.data.db.JournalLineEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingPostingResult
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingReversalCommand
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.accounting.JournalStatus
import ir.restaurant.management.domain.accounting.ResolvedJournalPosting
import ir.restaurant.management.domain.accounting.SemanticAccountResolver
import ir.restaurant.management.domain.accounting.SemanticJournalDraft
import ir.restaurant.management.domain.accounting.SystemSemanticAccountResolver
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.common.businessRequire

/**
 * Accounting-owned posting boundary. A journal is always created as DRAFT, receives all balanced
 * lines, and only then transitions to POSTED. The full sequence owns a Room transaction and is
 * idempotent by command key.
 */
class LocalAccountingPostingEngine(
    private val database: AppDatabase,
    private val resolver: SemanticAccountResolver = SystemSemanticAccountResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountingPostingService {
    override suspend fun post(command: AccountingPostingCommand): AccountingPostingResult {
        val valid = command.validated()
        val replay = database.accountingDao().entryByIdempotencyKey(valid.idempotencyKey) != null
        val entryId = post(
            draft = SemanticJournalDraft(
                entryNo = valid.entryNo,
                description = valid.description,
                entryEpochDay = valid.businessEpochDay,
                sourceType = valid.sourceType,
                sourceId = valid.sourceId,
                status = valid.status,
                lines = valid.lines,
                accountingScope = valid.accountingScope,
                branchId = valid.branchId,
            ),
            context = AccountingPostingContext(
                idempotencyKey = valid.idempotencyKey,
                correlationId = valid.correlationId.value,
                actorId = valid.actorId,
            ),
        )
        val entry = database.accountingDao().entryById(entryId)
            ?: throw BusinessError.InvalidJournal(
                ir.restaurant.management.domain.common.JournalInvalidReason.MISSING_LINES,
            ).asViolation()
        return AccountingPostingResult(entry.id, entry.entryNo, replay)
    }

    override suspend fun reverse(command: AccountingReversalCommand): AccountingPostingResult {
        val valid = command.validated()
        return accountingTransaction(valid.businessEpochDay) {
            val dao = database.accountingDao()
            val original = dao.entryById(valid.originalEntryId)
                ?: throw BusinessError.EntityNotFound("JOURNAL", valid.originalEntryId).asViolation()
            businessRequire(original.status == JournalStatus.POSTED.storedValue) {
                BusinessError.InvalidStateTransition("JOURNAL", original.status, "REVERSED")
            }
            businessRequire(valid.businessEpochDay >= original.entryEpochDay) {
                BusinessError.InvalidJournal(
                    ir.restaurant.management.domain.common.JournalInvalidReason.BUSINESS_DATE_MISMATCH,
                )
            }
            val originalLines = dao.linesByEntry(original.id)
            businessRequire(originalLines.size >= 2) {
                BusinessError.InvalidJournal(
                    ir.restaurant.management.domain.common.JournalInvalidReason.MISSING_LINES,
                )
            }
            val replay = dao.entryByIdempotencyKey(valid.idempotencyKey)
            if (replay == null) {
                dao.postedReversalOf(original.id)?.let {
                    throw BusinessError.DuplicatePosting(
                        sourceType = valid.sourceType,
                        sourceId = valid.sourceId,
                        idempotencyKey = valid.idempotencyKey,
                    ).asViolation()
                }
            }
            val posted = postBalanced(
                draft = BalancedJournalDraft(
                    entryEpochDay = valid.businessEpochDay,
                    description = "برگشت ${original.entryNo}: ${valid.reason}",
                    sourceType = valid.sourceType,
                    sourceId = valid.sourceId,
                    accountingScope = AccountingScope.fromStoredValue(original.accountingScope),
                    branchId = original.branchId,
                    lines = originalLines.map { line ->
                        JournalLineDraft(
                            accountCode = line.accountCode,
                            debit = ir.restaurant.management.core.MoneyRial.of(line.creditRial),
                            credit = ir.restaurant.management.core.MoneyRial.of(line.debitRial),
                            memo = valid.reason,
                        )
                    },
                ),
                context = AccountingPostingContext(
                    idempotencyKey = valid.idempotencyKey,
                    correlationId = valid.correlationId.value,
                    actorId = valid.actorId,
                    reversalOfEntryId = original.id,
                ),
                entryNoFactory = { valid.entryNo },
            )
            if (!posted.idempotentReplay) {
                auditJournal(
                    action = "REVERSE",
                    entryId = posted.entryId,
                    entryNo = posted.entryNo,
                    sourceType = valid.sourceType,
                    sourceId = valid.sourceId,
                    businessEpochDay = valid.businessEpochDay,
                    description = valid.reason,
                    context = AccountingPostingContext(
                        idempotencyKey = valid.idempotencyKey,
                        correlationId = valid.correlationId.value,
                        actorId = valid.actorId,
                        reversalOfEntryId = original.id,
                    ),
                    status = JournalStatus.POSTED,
                    occurredAt = clock(),
                )
            }
            AccountingPostingResult(posted.entryId, posted.entryNo, posted.idempotentReplay)
        }
    }

    suspend fun post(
        draft: SemanticJournalDraft,
        context: AccountingPostingContext,
    ): Long = accountingTransaction(draft.entryEpochDay) {
        val validContext = context.validated()
        businessRequire(draft.status != JournalStatus.LEGACY_UNKNOWN) {
            BusinessError.InvalidBusinessState("JOURNAL", draft.status.storedValue)
        }
        val dao = database.accountingDao()
        val scope = resolvePostingScope(dao, draft.accountingScope, draft.branchId, validContext.reversalOfEntryId)
        val resolved = draft.lines.map { line ->
            val code = resolver.codeFor(line.role)
            val account = dao.accountByCode(code)
                ?: throw BusinessError.EntityNotFound("ACCOUNT", null).asViolation()
            businessRequire(account.isActive) {
                BusinessError.InvalidBusinessState("ACCOUNT", "$code:INACTIVE")
            }
            JournalLineEntity(
                entryId = 0,
                accountCode = code,
                debitRial = line.debit.value,
                creditRial = line.credit.value,
                memo = line.memo.trim(),
            )
        }

        dao.entryByIdempotencyKey(validContext.idempotencyKey)?.let { existing ->
            val sameHeader = existing.entryNo == draft.entryNo &&
                existing.entryEpochDay == draft.entryEpochDay &&
                existing.description == draft.description &&
                existing.sourceType == draft.sourceType &&
                existing.sourceId == draft.sourceId &&
                existing.correlationId == validContext.correlationId &&
                existing.reversalOfEntryId == validContext.reversalOfEntryId &&
                existing.branchId == scope.second &&
                existing.accountingScope == scope.first.storedValue &&
                existing.status == draft.status.storedValue
            val existingLines = dao.linesByEntry(existing.id)
            val sameLines = existingLines.size == resolved.size && existingLines.zip(resolved).all { (left, right) ->
                left.accountCode == right.accountCode &&
                    left.debitRial == right.debitRial &&
                    left.creditRial == right.creditRial &&
                    left.memo == right.memo
            }
            if (!sameHeader || !sameLines) {
                throw BusinessError.IdempotencyConflict(validContext.idempotencyKey).asViolation()
            }
            return@accountingTransaction existing.id
        }

        val now = clock()
        val entryId = dao.insertEntry(
            JournalEntryEntity(
                entryNo = draft.entryNo,
                entryEpochDay = draft.entryEpochDay,
                description = draft.description,
                sourceType = draft.sourceType,
                sourceId = draft.sourceId,
                status = JournalStatus.DRAFT.storedValue,
                createdAtEpochMillis = now,
                globalId = GlobalId.new().value,
                idempotencyKey = validContext.idempotencyKey,
                correlationId = validContext.correlationId,
                reversalOfEntryId = validContext.reversalOfEntryId,
                branchId = scope.second,
                accountingScope = scope.first.storedValue,
            ),
        )
        dao.insertLines(resolved.map { it.copy(entryId = entryId) })
        if (draft.status == JournalStatus.POSTED) {
            businessRequire(dao.postDraftEntry(entryId, now, validContext.actorId) == 1) {
                BusinessError.ConcurrentModification("JOURNAL", entryId)
            }
        }
        auditJournal(
            action = if (draft.status == JournalStatus.POSTED) "POST" else "PREPARE",
            entryId = entryId,
            entryNo = draft.entryNo,
            sourceType = draft.sourceType,
            sourceId = draft.sourceId,
            businessEpochDay = draft.entryEpochDay,
            description = draft.description,
            context = validContext,
            status = draft.status,
            occurredAt = now,
        )
        entryId
    }

    /** Posts journals whose account codes are already resolved (manual entries and reversals). */
    suspend fun postBalanced(
        draft: BalancedJournalDraft,
        context: AccountingPostingContext,
        entryNoFactory: (Long) -> String,
        sourceIdFactory: ((Long) -> Long)? = null,
        status: JournalStatus = JournalStatus.POSTED,
    ): ResolvedJournalPosting = accountingTransaction(draft.entryEpochDay) {
        val validContext = context.validated()
        businessRequire(status != JournalStatus.LEGACY_UNKNOWN) {
            BusinessError.InvalidBusinessState("JOURNAL", status.storedValue)
        }
        val dao = database.accountingDao()
        val scope = resolvePostingScope(dao, draft.accountingScope, draft.branchId, validContext.reversalOfEntryId)
        val resolved = draft.lines.map { line ->
            val account = dao.accountByCode(line.accountCode)
                ?: throw BusinessError.EntityNotFound("ACCOUNT", null).asViolation()
            businessRequire(account.isActive) {
                BusinessError.InvalidBusinessState("ACCOUNT", "${line.accountCode}:INACTIVE")
            }
            JournalLineEntity(
                entryId = 0,
                accountCode = line.accountCode,
                debitRial = line.debit.value,
                creditRial = line.credit.value,
                memo = line.memo.trim(),
            )
        }

        dao.entryByIdempotencyKey(validContext.idempotencyKey)?.let { existing ->
            val expectedSourceId = sourceIdFactory?.invoke(existing.id) ?: draft.sourceId
            val expectedEntryNo = entryNoFactory(existing.id)
            val sameHeader = existing.entryNo == expectedEntryNo &&
                existing.entryEpochDay == draft.entryEpochDay &&
                existing.description == draft.description &&
                existing.sourceType == draft.sourceType &&
                existing.sourceId == expectedSourceId &&
                existing.correlationId == validContext.correlationId &&
                existing.reversalOfEntryId == validContext.reversalOfEntryId &&
                existing.branchId == scope.second &&
                existing.accountingScope == scope.first.storedValue &&
                existing.status == status.storedValue
            val existingLines = dao.linesByEntry(existing.id)
            val sameLines = existingLines.size == resolved.size && existingLines.zip(resolved).all { (left, right) ->
                left.accountCode == right.accountCode &&
                    left.debitRial == right.debitRial &&
                    left.creditRial == right.creditRial &&
                    left.memo == right.memo
            }
            if (!sameHeader || !sameLines) {
                throw BusinessError.IdempotencyConflict(validContext.idempotencyKey).asViolation()
            }
            return@accountingTransaction ResolvedJournalPosting(existing.id, existing.entryNo, idempotentReplay = true)
        }

        val now = clock()
        val entryId = dao.insertEntry(
            JournalEntryEntity(
                entryNo = "TMP-${GlobalId.new().value}",
                entryEpochDay = draft.entryEpochDay,
                description = draft.description,
                sourceType = draft.sourceType,
                sourceId = draft.sourceId,
                status = JournalStatus.DRAFT.storedValue,
                createdAtEpochMillis = now,
                globalId = GlobalId.new().value,
                idempotencyKey = validContext.idempotencyKey,
                correlationId = validContext.correlationId,
                reversalOfEntryId = validContext.reversalOfEntryId,
                branchId = scope.second,
                accountingScope = scope.first.storedValue,
            ),
        )
        val finalEntryNo = entryNoFactory(entryId)
        val finalSourceId = sourceIdFactory?.invoke(entryId) ?: draft.sourceId
        businessRequire(dao.finalizeEntryIdentity(entryId, finalEntryNo, finalSourceId) == 1) {
            BusinessError.ConcurrentModification("JOURNAL", entryId)
        }
        dao.insertLines(resolved.map { it.copy(entryId = entryId) })
        if (status == JournalStatus.POSTED) {
            businessRequire(dao.postDraftEntry(entryId, now, validContext.actorId) == 1) {
                BusinessError.ConcurrentModification("JOURNAL", entryId)
            }
        }
        auditJournal(
            action = if (status == JournalStatus.POSTED) "POST" else "PREPARE",
            entryId = entryId,
            entryNo = finalEntryNo,
            sourceType = draft.sourceType,
            sourceId = finalSourceId,
            businessEpochDay = draft.entryEpochDay,
            description = draft.description,
            context = validContext,
            status = status,
            occurredAt = now,
        )
        ResolvedJournalPosting(entryId, finalEntryNo, idempotentReplay = false)
    }

    /** Finalizes an already prepared draft (for example, payroll after independent approval). */
    suspend fun postExistingDraft(
        entryId: Long,
        businessEpochDay: Long,
        actorId: Long,
    ): Long = accountingTransaction(businessEpochDay) {
        businessRequire(entryId > 0 && actorId > 0 && businessEpochDay > 0) {
            BusinessError.InvalidInput("journalPosting", "مشخصات ثبت قطعی سند کامل نیست.")
        }
        val dao = database.accountingDao()
        val entry = dao.entryById(entryId)
            ?: throw BusinessError.EntityNotFound("JOURNAL", entryId).asViolation()
        businessRequire(entry.entryEpochDay == businessEpochDay) {
            BusinessError.InvalidBusinessState("JOURNAL", "BUSINESS_DATE_MISMATCH")
        }
        businessRequire(entry.status == JournalStatus.DRAFT.storedValue) {
            BusinessError.InvalidBusinessState("JOURNAL", entry.status)
        }
        val idempotencyKey = entry.idempotencyKey?.takeIf { it.isNotBlank() }
            ?: throw BusinessError.InvalidJournal(
                ir.restaurant.management.domain.common.JournalInvalidReason.MISSING_POSTING_CONTEXT,
            ).asViolation()
        val postingContext = AccountingPostingContext(
            idempotencyKey = idempotencyKey,
            correlationId = entry.correlationId,
            actorId = actorId,
            reversalOfEntryId = entry.reversalOfEntryId,
        ).validated()
        businessRequire(dao.postDraftEntry(entry.id, clock(), actorId) == 1) {
            BusinessError.ConcurrentModification("JOURNAL", entry.id)
        }
        auditJournal(
            action = "POST",
            entryId = entry.id,
            entryNo = entry.entryNo,
            sourceType = entry.sourceType,
            sourceId = entry.sourceId,
            businessEpochDay = entry.entryEpochDay,
            description = entry.description,
            context = postingContext,
            status = JournalStatus.POSTED,
            occurredAt = clock(),
        )
        entry.id
    }

    private suspend fun auditJournal(
        action: String,
        entryId: Long,
        entryNo: String,
        sourceType: String,
        sourceId: Long,
        businessEpochDay: Long,
        description: String,
        context: AccountingPostingContext,
        status: JournalStatus,
        occurredAt: Long,
    ) {
        val sessionActor = database.securityDao().currentUser()
        val actorName = sessionActor
            ?.takeIf { it.id == context.actorId }
            ?.let { it.displayName.ifBlank { it.username } }
            ?: "ACTOR-${context.actorId}"
        LocalAuditEventWriter(database).append(
            AuditEventDraft(
                action = AuditAction.of(action),
                entityType = AuditEntityType.of("JOURNAL"),
                entityId = entryId,
                actorId = context.actorId,
                actorDisplayName = actorName,
                occurredAtEpochMillis = occurredAt,
                businessEpochDay = businessEpochDay,
                deviceId = "local-android",
                referenceType = sourceType.takeIf { sourceId > 0 },
                referenceId = sourceId.takeIf { it > 0 },
                reason = description,
                beforeSnapshot = null,
                afterSnapshot = database.accountingDao().entryById(entryId)?.let { entry ->
                    "entryNo=$entryNo;status=${status.storedValue};sourceType=$sourceType;sourceId=$sourceId;scope=${entry.accountingScope};branchId=${entry.branchId ?: ""}"
                } ?: "entryNo=$entryNo;status=${status.storedValue};sourceType=$sourceType;sourceId=$sourceId",
                correlationId = context.correlationId,
                description = description,
            ),
        )
    }

    private suspend fun resolvePostingScope(
        dao: ir.restaurant.management.data.db.AccountingDao,
        requestedScope: AccountingScope,
        requestedBranchId: Long?,
        reversalOfEntryId: Long?,
    ): Pair<AccountingScope, Long?> {
        requestedScope.requireCompatible(requestedBranchId)
        if (reversalOfEntryId == null) {
            if (requestedScope == AccountingScope.BRANCH) {
                val branchId = requireNotNull(requestedBranchId)
                businessRequire(database.branchDao().activeById(branchId) != null) {
                    BusinessError.InvalidBusinessState("BRANCH", "$branchId:INACTIVE_OR_MISSING")
                }
            }
            return requestedScope to requestedBranchId
        }
        val original = dao.entryById(reversalOfEntryId)
            ?: throw BusinessError.EntityNotFound("JOURNAL", reversalOfEntryId).asViolation()
        val originalScope = AccountingScope.fromStoredValue(original.accountingScope)
        originalScope.requireCompatible(original.branchId)
        return originalScope to original.branchId
    }

    private suspend fun <T> accountingTransaction(
        businessEpochDay: Long,
        block: suspend () -> T,
    ): T = try {
        database.withTransaction { block() }
    } catch (error: Throwable) {
        throw mapDatabaseBusinessFailure(error, businessEpochDay)
    }
}
