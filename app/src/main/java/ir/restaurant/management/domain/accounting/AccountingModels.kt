package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.SignedLongMath
import kotlinx.coroutines.flow.Flow

enum class AccountType(
    val storedValue: String,
    val title: String,
) {
    ASSET("ASSET", "دارایی"),
    LIABILITY("LIABILITY", "بدهی"),
    EQUITY("EQUITY", "سرمایه"),
    REVENUE("REVENUE", "درآمد"),
    EXPENSE("EXPENSE", "هزینه");

    companion object {
        fun fromStored(value: String): AccountType =
            entries.firstOrNull { it.storedValue == value }
                ?: error("نوع حساب ناشناخته است.")
    }
}

data class AccountDraft(
    val code: String,
    val name: String,
    val type: AccountType,
) {
    fun validated(): AccountDraft {
        val normalizedCode = code.trim()
        val normalizedName = name.trim()
        require(normalizedCode.matches(Regex("[1-9][0-9]{3}"))) {
            "کد حساب باید یک عدد چهاررقمی باشد."
        }
        require(normalizedName.length in 2..120) {
            "نام حساب باید بین ۲ تا ۱۲۰ نویسه باشد."
        }
        return copy(code = normalizedCode, name = normalizedName)
    }
}

data class AccountBalanceRecord(
    val code: String,
    val name: String,
    val type: AccountType,
    val isSystem: Boolean,
    val debitTurnoverRial: Long,
    val creditTurnoverRial: Long,
) {
    val debitBalanceRial: Long
        get() = if (debitTurnoverRial >= creditTurnoverRial) {
            SignedLongMath.subtract(debitTurnoverRial, creditTurnoverRial)
        } else {
            0
        }

    val creditBalanceRial: Long
        get() = if (creditTurnoverRial >= debitTurnoverRial) {
            SignedLongMath.subtract(creditTurnoverRial, debitTurnoverRial)
        } else {
            0
        }
}

data class JournalSummary(
    val id: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val totalDebitRial: Long,
    val totalCreditRial: Long,
    val isReversed: Boolean,
)

data class JournalDetailLine(
    val id: Long,
    val accountCode: String,
    val accountName: String,
    val debitRial: Long,
    val creditRial: Long,
    val memo: String,
)

data class JournalDetails(
    val id: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val sourceId: Long,
    val totalDebitRial: Long,
    val totalCreditRial: Long,
    val isReversed: Boolean,
    val lines: List<JournalDetailLine>,
) {
    val canReverse: Boolean
        get() = sourceType == "MANUAL" && !isReversed
}

data class LedgerRow(
    val lineId: Long,
    val entryId: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val debitRial: Long,
    val creditRial: Long,
    val balanceAfterRial: Long,
)

data class TrialBalanceSnapshot(
    val accounts: List<AccountBalanceRecord>,
    val totalDebitTurnoverRial: Long,
    val totalCreditTurnoverRial: Long,
    val totalDebitBalanceRial: Long,
    val totalCreditBalanceRial: Long,
) {
    val isBalanced: Boolean
        get() = totalDebitTurnoverRial == totalCreditTurnoverRial &&
            totalDebitBalanceRial == totalCreditBalanceRial
}

data class ProfitLossSnapshot(
    val revenueRial: Long,
    val expenseRial: Long,
    val netProfitRial: Long,
)

data class PostedJournal(
    val id: Long,
    val entryNo: String,
)

interface AccountingRepository {
    val accounts: Flow<List<AccountBalanceRecord>>

    fun profitLoss(fromEpochDay: Long, toEpochDay: Long): Flow<ProfitLossSnapshot>
    fun journals(query: String): Flow<List<JournalSummary>>
    fun journalDetails(entryId: Long): Flow<JournalDetails?>
    fun ledger(accountCode: String): Flow<List<LedgerRow>>

    suspend fun createAccount(draft: AccountDraft)
    suspend fun updateAccount(code: String, draft: AccountDraft)
    suspend fun deactivateAccount(code: String)
    suspend fun postManual(draft: ManualJournalDraft): PostedJournal
    suspend fun reverseManual(
        entryId: Long,
        reversalEpochDay: Long,
        reason: String,
    ): PostedJournal
}

fun calculateTrialBalance(accounts: List<AccountBalanceRecord>): TrialBalanceSnapshot =
    TrialBalanceSnapshot(
        accounts = accounts,
        totalDebitTurnoverRial = exactSum(accounts.map { it.debitTurnoverRial }),
        totalCreditTurnoverRial = exactSum(accounts.map { it.creditTurnoverRial }),
        totalDebitBalanceRial = exactSum(accounts.map { it.debitBalanceRial }),
        totalCreditBalanceRial = exactSum(accounts.map { it.creditBalanceRial }),
    )

fun calculateProfitLoss(accounts: List<AccountBalanceRecord>): ProfitLossSnapshot {
    val revenue = exactSum(
        accounts
            .filter { it.type == AccountType.REVENUE }
            .map { SignedLongMath.subtract(it.creditTurnoverRial, it.debitTurnoverRial) },
    )
    val expense = exactSum(
        accounts
            .filter { it.type == AccountType.EXPENSE }
            .map { SignedLongMath.subtract(it.debitTurnoverRial, it.creditTurnoverRial) },
    )
    return ProfitLossSnapshot(
        revenueRial = revenue,
        expenseRial = expense,
        netProfitRial = SignedLongMath.subtract(revenue, expense),
    )
}

private fun exactSum(values: Iterable<Long>): Long =
    values.fold(0L, SignedLongMath::add)
