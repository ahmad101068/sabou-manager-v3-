package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission

import androidx.room.withTransaction
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AccountEntity
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.accounting.AccountBalanceRecord
import ir.restaurant.management.domain.accounting.AccountDraft
import ir.restaurant.management.domain.accounting.AccountType
import ir.restaurant.management.domain.accounting.AccountingRepository
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.JournalDetailLine
import ir.restaurant.management.domain.accounting.JournalDetails
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.accounting.JournalSummary
import ir.restaurant.management.domain.accounting.LedgerRow
import ir.restaurant.management.domain.accounting.ManualJournalDraft
import ir.restaurant.management.domain.accounting.PostedJournal
import ir.restaurant.management.domain.accounting.ProfitLossSnapshot
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalAccountingRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
) : AccountingRepository {
    private val posting = LocalAccountingPostingEngine(database, clock = clock)
    private val auditWriter = LocalAuditEventWriter(database)
    private val dao
        get() = database.accountingDao()

    override val accounts: Flow<List<AccountBalanceRecord>> =
        dao.observeAccountBalances().map { rows ->
            rows.map { row ->
                AccountBalanceRecord(
                    code = row.code,
                    name = row.name,
                    type = AccountType.fromStored(row.type),
                    isSystem = row.isSystem,
                    debitTurnoverRial = row.debitTurnoverRial,
                    creditTurnoverRial = row.creditTurnoverRial,
                )
            }
        }

    override fun profitLoss(fromEpochDay: Long, toEpochDay: Long): Flow<ProfitLossSnapshot> {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay) { "بازه سود و زیان معتبر نیست." }
        return dao.observeProfitLoss(fromEpochDay, toEpochDay).map { row ->
            ProfitLossSnapshot(
                revenueRial = row.revenueRial,
                expenseRial = row.expenseRial,
                netProfitRial = SignedLongMath.subtract(row.revenueRial, row.expenseRial),
            )
        }
    }

    override fun journals(query: String): Flow<List<JournalSummary>> =
        dao.observeJournals(query.trim()).map { rows ->
            rows.map { row ->
                JournalSummary(
                    id = row.id,
                    entryNo = row.entryNo,
                    entryEpochDay = row.entryEpochDay,
                    description = row.description,
                    sourceType = row.sourceType,
                    totalDebitRial = row.totalDebitRial,
                    totalCreditRial = row.totalCreditRial,
                    isReversed = row.isReversed,
                )
            }
        }

    override fun journalDetails(entryId: Long): Flow<JournalDetails?> =
        dao.observeJournalDetails(entryId).map { rows ->
            val first = rows.firstOrNull() ?: return@map null
            val lines = rows.map { row ->
                JournalDetailLine(
                    id = row.lineId,
                    accountCode = row.accountCode,
                    accountName = row.accountName,
                    debitRial = row.debitRial,
                    creditRial = row.creditRial,
                    memo = row.memo,
                )
            }
            JournalDetails(
                id = first.entryId,
                entryNo = first.entryNo,
                entryEpochDay = first.entryEpochDay,
                description = first.entryDescription,
                sourceType = first.sourceType,
                sourceId = first.sourceId,
                totalDebitRial = exactSum(lines.map { it.debitRial }),
                totalCreditRial = exactSum(lines.map { it.creditRial }),
                isReversed = first.isReversed,
                lines = lines,
            )
        }

    override fun ledger(accountCode: String): Flow<List<LedgerRow>> =
        dao.observeLedger(accountCode).map { rows ->
            var runningBalance = 0L
            rows.map { row ->
                runningBalance = SignedLongMath.add(
                    runningBalance,
                    SignedLongMath.subtract(row.debitRial, row.creditRial),
                )
                LedgerRow(
                    lineId = row.lineId,
                    entryId = row.entryId,
                    entryNo = row.entryNo,
                    entryEpochDay = row.entryEpochDay,
                    description = row.description,
                    debitRial = row.debitRial,
                    creditRial = row.creditRial,
                    balanceAfterRial = runningBalance,
                )
            }
        }

    override suspend fun createAccount(draft: AccountDraft) {
        authorizer.require(Permission.ACCOUNTING)
        val valid = draft.validated()
        database.withTransaction {
            val previous = dao.accountByCode(valid.code)
            if (previous == null) {
                dao.insertAccount(
                    AccountEntity(
                        code = valid.code,
                        name = valid.name,
                        type = valid.type.storedValue,
                        isSystem = false,
                    ),
                )
                return@withTransaction
            }

            require(!previous.isActive) { "حسابی با این کد وجود دارد." }
            require(!previous.isSystem) { "حساب سیستمی قابل جایگزینی نیست." }
            if (dao.accountUsageCount(valid.code) > 0) {
                require(previous.type == valid.type.storedValue) {
                    "نوع حساب دارای گردش قابل تغییر نیست."
                }
            }
            check(
                dao.updateAccount(
                    previous.copy(
                        name = valid.name,
                        type = valid.type.storedValue,
                        isActive = true,
                    ),
                ) == 1,
            ) { "فعال‌سازی حساب انجام نشد." }
        }
    }

    override suspend fun updateAccount(code: String, draft: AccountDraft) {
        authorizer.require(Permission.ACCOUNTING)
        val valid = draft.validated()
        require(valid.code == code) { "کد حساب پس از ثبت قابل تغییر نیست." }
        database.withTransaction {
            val current = dao.accountByCode(code)
                ?: error("حساب پیدا نشد.")
            require(current.isActive) { "حساب غیرفعال است." }
            require(!current.isSystem) { "حساب‌های سیستمی قابل ویرایش نیستند." }
            if (current.type != valid.type.storedValue) {
                require(dao.accountUsageCount(code) == 0L) {
                    "نوع حساب دارای گردش قابل تغییر نیست."
                }
            }
            check(
                dao.updateAccount(
                    current.copy(
                        name = valid.name,
                        type = valid.type.storedValue,
                    ),
                ) == 1,
            ) { "ویرایش حساب انجام نشد." }
        }
    }

    override suspend fun deactivateAccount(code: String) {
        authorizer.require(Permission.ACCOUNTING)
        database.withTransaction {
            val current = dao.accountByCode(code)
                ?: error("حساب پیدا نشد.")
            require(current.isActive) { "حساب قبلاً غیرفعال شده است." }
            require(!current.isSystem) { "حساب‌های سیستمی قابل غیرفعال‌کردن نیستند." }
            require(dao.accountBalanceRial(code) == 0L) {
                "حساب دارای مانده را نمی‌توان غیرفعال کرد."
            }
            check(dao.updateAccount(current.copy(isActive = false)) == 1) {
                "غیرفعال‌کردن حساب انجام نشد."
            }
        }
    }

    override suspend fun postManual(draft: ManualJournalDraft): PostedJournal {
        val actor = authorizer.require(Permission.ACCOUNTING)
        val valid = draft.validated()
        val commandId = GlobalId.parse(draft.commandId).value
        return database.withTransaction {
            val posted = posting.postBalanced(
                draft = valid,
                context = AccountingPostingContext.local(
                    sourceType = "MANUAL",
                    sourceId = 0,
                    suffix = "command:$commandId",
                    actorId = actor.id,
                    correlationId = "manual_journal:$commandId",
                ),
                entryNoFactory = { id -> "س-$id" },
                sourceIdFactory = { id -> id },
            )
            if (!posted.idempotentReplay) {
                syncRecorder?.record("JOURNAL", posted.entryId, "CREATE", clock())
                audit("CREATE", posted.entryId, "ثبت سند دستی ${posted.entryNo}؛ تاریخ=${valid.entryEpochDay}؛ شرح=${valid.description}", clock())
            }
            PostedJournal(posted.entryId, posted.entryNo)
        }
    }

    override suspend fun reverseManual(
        entryId: Long,
        reversalEpochDay: Long,
        reason: String,
    ): PostedJournal {
        val actor = authorizer.require(Permission.JOURNAL_REVERSE)
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..200) {
            "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد."
        }
        return database.withTransaction {
            val original = dao.entryById(entryId)
                ?: error("سند پیدا نشد.")
            require(original.status == "POSTED") { "سند ثبت‌شده نیست." }
            require(original.sourceType == "MANUAL") {
                "سند خودکار باید از ماژول مبدأ اصلاح شود."
            }
            require(reversalEpochDay >= original.entryEpochDay) {
                "تاریخ برگشت نمی‌تواند قبل از تاریخ سند باشد."
            }
            val expectedDescription = "برگشت ${original.entryNo}: $normalizedReason"
            dao.entryBySource("REVERSAL", original.id)?.let { existing ->
                if (
                    existing.entryEpochDay != reversalEpochDay ||
                    existing.description != expectedDescription
                ) {
                    throw BusinessError.IdempotencyConflict(
                        "REVERSAL:${original.id}:reverse:${original.id}",
                    ).asViolation()
                }
                return@withTransaction PostedJournal(existing.id, existing.entryNo)
            }
            val originalLines = dao.linesByEntry(entryId)
            require(originalLines.size >= 2) { "آرتیکل‌های سند کامل نیستند." }
            val reversal = BalancedJournalDraft(
                description = expectedDescription,
                entryEpochDay = reversalEpochDay,
                sourceType = "REVERSAL",
                sourceId = original.id,
                accountingScope = AccountingScope.fromStoredValue(original.accountingScope),
                branchId = original.branchId,
                lines = originalLines.map { line ->
                    JournalLineDraft(
                        accountCode = line.accountCode,
                        debit = MoneyRial.of(line.creditRial),
                        credit = MoneyRial.of(line.debitRial),
                        memo = normalizedReason,
                    )
                },
            )

            val posted = posting.postBalanced(
                draft = reversal,
                context = AccountingPostingContext.local(
                    sourceType = "REVERSAL",
                    sourceId = original.id,
                    suffix = "reverse:${original.id}",
                    actorId = actor.id,
                    correlationId = "journal_reversal:${original.id}",
                    reversalOfEntryId = original.id,
                ),
                entryNoFactory = { id -> "ب-$id" },
            )
            if (!posted.idempotentReplay) {
                syncRecorder?.record("JOURNAL", posted.entryId, "REVERSAL", clock())
                audit("REVERSE", posted.entryId, "برگشت سند ${original.entryNo} با سند ${posted.entryNo}؛ دلیل=$normalizedReason", clock())
            }
            PostedJournal(posted.entryId, posted.entryNo)
        }
    }

    private suspend fun audit(action: String, entityId: Long, description: String, now: Long) {
        auditWriter.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = "JOURNAL",
            entityId = entityId,
            description = description,
            occurredAtEpochMillis = now,
            correlationId = "journal:$entityId:$action:$now",
        )
    }

}

private fun exactSum(values: Iterable<Long>): Long =
    values.fold(0L, SignedLongMath::add)
