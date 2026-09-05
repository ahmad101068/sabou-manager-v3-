package ir.restaurant.management.application.treasury

import ir.restaurant.management.domain.treasury.TreasuryAccountCatalog
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryReversalContext
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.treasury.TreasuryService

class TreasuryUseCases(
    private val service: TreasuryService,
    private val reader: TreasuryLedgerReader,
    private val accounts: TreasuryAccountCatalog,
) {
    val recentTransactions get() = reader.recentTransactions
    fun activeAccounts() = accounts.activeAccounts()
    fun balance(accountId: TreasuryAccountId) = reader.observeBalance(accountId)
    suspend fun execute(command: TreasuryCommand) = service.execute(command)
    suspend fun reverse(command: TreasuryReversalCommand) = service.reverse(command)
}


/** Converts an append-only posted treasury row into a controlled compensating command. */
class ReverseTreasuryTransactionUseCase(
    private val service: TreasuryService,
    private val reader: TreasuryLedgerReader,
) {
    suspend operator fun invoke(
        transactionId: String,
        reason: String,
        businessEpochDay: Long = currentLocalEpochDay(),
    ) = invokeWithContext(
        context = requireNotNull(reader.reversalContext(transactionId.trim())) { "تراکنش خزانه پیدا نشد." },
        reason = reason,
        businessEpochDay = businessEpochDay,
    )

    private suspend fun invokeWithContext(
        context: TreasuryReversalContext,
        reason: String,
        businessEpochDay: Long,
    ): ir.restaurant.management.domain.treasury.TreasuryTransaction {
        require(context.status == "POSTED") { "فقط تراکنش Posted قابل برگشت است." }
        require(context.reversalOfTransactionId == null) { "تراکنش جبرانی دوباره قابل برگشت نیست." }
        val journalId = requireNotNull(context.journalEntryId) { "تراکنش بدون سند حسابداری قابل برگشت نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..500) { "دلیل برگشت باید بین ۳ تا ۵۰۰ نویسه باشد." }
        require(businessEpochDay > 0) { "تاریخ برگشت معتبر نیست." }
        val commandId = GlobalId.new()
        return service.reverse(
            TreasuryReversalCommand(
                commandId = commandId,
                originalTransactionId = context.transactionId,
                originalJournalEntryId = journalId,
                businessEpochDay = businessEpochDay,
                correlationId = CorrelationId.forCommand("treasury_reversal", commandId),
                sourceType = "TREASURY_REVERSAL",
                sourceId = context.sourceId,
                reason = normalizedReason,
                accountId = context.accountId,
                channel = context.channel,
                amount = MoneyRial.of(context.amountRial),
            ),
        )
    }
}
