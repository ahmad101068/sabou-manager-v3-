package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.application.treasury.TreasuryUseCases
import ir.restaurant.management.application.treasury.ReverseTreasuryTransactionUseCase
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.treasury.TreasuryAccount
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryLedgerRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreasuryUiState(
    val accounts: List<TreasuryAccount> = emptyList(),
    val transactions: List<TreasuryLedgerRecord> = emptyList(),
    val balances: Map<String, Long> = emptyMap(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class TreasuryViewModel(
    private val useCases: TreasuryUseCases,
    private val reverseTransaction: ReverseTreasuryTransactionUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(TreasuryUiState(accounts = useCases.activeAccounts()))
    val state: StateFlow<TreasuryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            useCases.recentTransactions.collect { rows -> _state.update { it.copy(transactions = rows) } }
        }
        val accounts = useCases.activeAccounts()
        val balanceFlow = if (accounts.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(accounts.map { account -> useCases.balance(account.id) }) { balances ->
                accounts.mapIndexed { index, account -> account.id.value to balances[index] }.toMap()
            }
        }
        viewModelScope.launch {
            balanceFlow.collect { values -> _state.update { it.copy(balances = values) } }
        }
    }

    fun receipt(accountId: String, amountRial: Long, sourceType: String, sourceId: Long, reason: String) = execute { account ->
        val id = GlobalId.new()
        TreasuryCommand.Receipt(
            commandId = id,
            businessEpochDay = currentLocalEpochDay(),
            correlationId = CorrelationId.forCommand("treasury_receipt", id),
            businessIntent = TreasuryBusinessIntent.fromExternalSource(sourceType, TreasuryDirection.RECEIPT),
            sourceId = sourceId,
            reason = reason.trim(),
            accountingScope = AccountingScope.ORGANIZATION,
            branchId = null,
            accountId = account.id,
            channel = account.channel,
            amount = MoneyRial.of(amountRial),
        )
    }(accountId)

    fun payment(accountId: String, amountRial: Long, sourceType: String, sourceId: Long, reason: String) = execute { account ->
        val id = GlobalId.new()
        TreasuryCommand.Payment(
            commandId = id,
            businessEpochDay = currentLocalEpochDay(),
            correlationId = CorrelationId.forCommand("treasury_payment", id),
            businessIntent = TreasuryBusinessIntent.fromExternalSource(sourceType, TreasuryDirection.PAYMENT),
            sourceId = sourceId,
            reason = reason.trim(),
            accountingScope = AccountingScope.ORGANIZATION,
            branchId = null,
            accountId = account.id,
            channel = account.channel,
            amount = MoneyRial.of(amountRial),
        )
    }(accountId)

    fun settlement(accountId: String, direction: TreasuryDirection, amountRial: Long, sourceType: String, sourceId: Long, reason: String) = execute { account ->
        val id = GlobalId.new()
        TreasuryCommand.Settlement(
            commandId = id,
            businessEpochDay = currentLocalEpochDay(),
            correlationId = CorrelationId.forCommand("treasury_settlement", id),
            businessIntent = when (direction) {
                TreasuryDirection.RECEIPT -> TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION
                TreasuryDirection.PAYMENT -> TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT
            },
            sourceId = sourceId,
            reason = reason.trim(),
            accountingScope = AccountingScope.ORGANIZATION,
            branchId = null,
            accountId = account.id,
            direction = direction,
            channel = account.channel,
            amount = MoneyRial.of(amountRial),
        )
    }(accountId)

    fun transfer(fromAccountId: String, toAccountId: String, amountRial: Long, reason: String) {
        runCommand {
            val from = account(fromAccountId)
            val to = account(toAccountId)
            val id = GlobalId.new()
            TreasuryCommand.InternalTransfer(
                commandId = id, businessEpochDay = currentLocalEpochDay(),
                correlationId = CorrelationId.forCommand("treasury_transfer", id),
                sourceId = 1L, reason = reason.trim(),
                accountingScope = AccountingScope.ORGANIZATION, branchId = null,
                fromAccountId = from.id, toAccountId = to.id, amount = MoneyRial.of(amountRial),
            )
        }
    }

    fun reconcile(accountId: String, expectedRial: Long, actualRial: Long, reason: String) = execute { account ->
        val id = GlobalId.new()
        TreasuryCommand.Reconciliation(
            commandId = id, businessEpochDay = currentLocalEpochDay(),
            correlationId = CorrelationId.forCommand("treasury_reconcile", id),
            sourceId = 1L, reason = reason.trim(),
            accountingScope = AccountingScope.ORGANIZATION, branchId = null,
            accountId = account.id, expected = MoneyRial.of(expectedRial), actual = MoneyRial.of(actualRial),
        )
    }(accountId)

    fun reverse(transactionId: String, reason: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, isError = false) }
            runCatching { reverseTransaction(transactionId, reason) }
                .onSuccess { result ->
                    _state.update { it.copy(isBusy = false, message = "تراکنش خزانه با سند جبرانی برگشت خورد · ${result.amount.value} ریال") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, message = UiErrorHandler.message("TreasuryViewModel.reverse", error), isError = true) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, isError = false) }

    private fun execute(factory: (TreasuryAccount) -> TreasuryCommand): (String) -> Unit = { accountId ->
        runCommand { factory(account(accountId)) }
    }

    private fun runCommand(factory: () -> TreasuryCommand) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, isError = false) }
            runCatching { useCases.execute(factory()) }
                .onSuccess { result -> _state.update { it.copy(isBusy = false, message = "تراکنش خزانه ثبت شد · ${result.amount.value} ریال") } }
                .onFailure { error -> _state.update { it.copy(isBusy = false, message = error.message ?: "ثبت تراکنش خزانه انجام نشد.", isError = true) } }
        }
    }

    private fun account(raw: String): TreasuryAccount {
        val id = TreasuryAccountId.parse(raw)
        return _state.value.accounts.firstOrNull { it.id == id } ?: error("حساب خزانه فعال پیدا نشد.")
    }


    companion object {
        fun factory(
            useCases: TreasuryUseCases,
            reverseTransaction: ReverseTreasuryTransactionUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TreasuryViewModel::class.java))
                return TreasuryViewModel(useCases, reverseTransaction) as T
            }
        }
    }
}
