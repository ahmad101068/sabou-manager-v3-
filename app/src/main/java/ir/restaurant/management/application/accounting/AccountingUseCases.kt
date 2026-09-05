package ir.restaurant.management.application.accounting

import ir.restaurant.management.domain.accounting.*

/** Application boundary for accounting reads and controlled journal commands. */
class AccountingUseCases(private val repository: AccountingRepository) {
    val accounts get() = repository.accounts

    fun profitLoss(fromEpochDay: Long, toEpochDay: Long) = repository.profitLoss(
        fromEpochDay = fromEpochDay.also { require(it > 0) { "تاریخ شروع گزارش معتبر نیست." } },
        toEpochDay = toEpochDay.also { require(it >= fromEpochDay) { "بازه گزارش مالی معتبر نیست." } },
    )

    fun journals(query: String) = repository.journals(query.trim().take(120))

    fun journalDetails(entryId: Long) = repository.journalDetails(
        entryId.also { require(it > 0) { "شناسه سند معتبر نیست." } },
    )

    fun ledger(accountCode: String) = repository.ledger(
        accountCode.trim().also { require(it.matches(Regex("[1-9][0-9]{3}"))) { "کد حساب معتبر نیست." } },
    )

    suspend fun createAccount(draft: AccountDraft) = repository.createAccount(draft.validated())
    suspend fun updateAccount(code: String, draft: AccountDraft) = repository.updateAccount(code.trim(), draft.validated())

    suspend fun deactivateAccount(code: String) {
        val normalized = code.trim()
        require(normalized.matches(Regex("[1-9][0-9]{3}"))) { "کد حساب معتبر نیست." }
        repository.deactivateAccount(normalized)
    }

    suspend fun postManual(draft: ManualJournalDraft): PostedJournal {
        draft.validated()
        return repository.postManual(draft)
    }

    suspend fun reverseManual(entryId: Long, reversalEpochDay: Long, reason: String): PostedJournal {
        require(entryId > 0 && reversalEpochDay > 0) { "سند یا تاریخ برگشت معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل برگشت سند باید بین ۳ تا ۳۰۰ نویسه باشد." }
        return repository.reverseManual(entryId, reversalEpochDay, normalizedReason)
    }
}
