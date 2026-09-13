package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.accounting.AccountBalanceRecord
import ir.restaurant.management.domain.accounting.AccountDraft
import ir.restaurant.management.application.accounting.AccountingUseCases
import ir.restaurant.management.domain.accounting.JournalDetails
import ir.restaurant.management.domain.accounting.JournalSummary
import ir.restaurant.management.domain.accounting.LedgerRow
import ir.restaurant.management.domain.accounting.ManualJournalDraft
import ir.restaurant.management.domain.accounting.PostedJournal
import ir.restaurant.management.domain.accounting.ProfitLossSnapshot
import ir.restaurant.management.domain.accounting.TrialBalanceSnapshot
import ir.restaurant.management.domain.accounting.calculateProfitLoss
import ir.restaurant.management.domain.accounting.calculateTrialBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountingUiState(
    val accounts: List<AccountBalanceRecord> = emptyList(),
    val journals: List<JournalSummary> = emptyList(),
    val journalSearch: String = "",
    val selectedJournal: JournalDetails? = null,
    val selectedLedgerCode: String? = null,
    val ledgerRows: List<LedgerRow> = emptyList(),
    val trialBalance: TrialBalanceSnapshot = calculateTrialBalance(emptyList()),
    val profitLoss: ProfitLossSnapshot = calculateProfitLoss(emptyList()),
    val profitLossFromEpochDay: Long = currentEpochDay() - 29,
    val profitLossToEpochDay: Long = currentEpochDay(),
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AccountingViewModel(
    private val useCases: AccountingUseCases,
) : ViewModel() {
    private val journalSearch = MutableStateFlow("")
    private val selectedJournalId = MutableStateFlow<Long?>(null)
    private val selectedLedgerCode = MutableStateFlow<String?>(null)
    private val profitLossRange = MutableStateFlow((currentEpochDay() - 29) to currentEpochDay())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private data class ProfitLossPeriod(
        val snapshot: ProfitLossSnapshot,
        val range: Pair<Long, Long>,
    )

    private val profitLoss = profitLossRange.flatMapLatest { range ->
        useCases.profitLoss(range.first, range.second)
    }
    private val profitLossPeriod = combine(profitLoss, profitLossRange) { snapshot, range ->
        ProfitLossPeriod(snapshot, range)
    }

    private val content = combine(
        useCases.accounts,
        journalSearch.debounce(250).distinctUntilChanged().flatMapLatest(useCases::journals),
        selectedJournalId.flatMapLatest { entryId ->
            if (entryId == null) flowOf(null)
            else useCases.journalDetails(entryId)
        },
        selectedLedgerCode.flatMapLatest { accountCode ->
            if (accountCode == null) flowOf(emptyList())
            else useCases.ledger(accountCode)
        },
        profitLossPeriod,
    ) { accounts, journals, journalDetails, ledgerRows, currentProfitLoss ->
        AccountingUiState(
            accounts = accounts,
            journals = journals,
            journalSearch = journalSearch.value,
            selectedJournal = journalDetails,
            selectedLedgerCode = selectedLedgerCode.value,
            ledgerRows = ledgerRows,
            trialBalance = calculateTrialBalance(accounts),
            profitLoss = currentProfitLoss.snapshot,
            profitLossFromEpochDay = currentProfitLoss.range.first,
            profitLossToEpochDay = currentProfitLoss.range.second,
        )
    }

    val state: StateFlow<AccountingUiState> = combine(
        content,
        busy,
        message,
    ) { current, isBusy, currentMessage ->
        current.copy(busy = isBusy, message = currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountingUiState(),
    )

    fun searchJournals(value: String) {
        journalSearch.value = value
    }

    fun selectJournal(entryId: Long?) {
        selectedJournalId.value = entryId
    }

    fun selectLedger(accountCode: String?) {
        selectedLedgerCode.value = accountCode
    }

    fun setProfitLossRange(fromEpochDay: Long, toEpochDay: Long) {
        if (fromEpochDay > 0 && toEpochDay >= fromEpochDay) {
            profitLossRange.value = fromEpochDay to toEpochDay
        }
    }

    fun clearMessage() {
        message.value = null
    }

    fun saveAccount(code: String?, draft: AccountDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (code == null) "حساب جدید ثبت شد." else "حساب ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            if (code == null) useCases.createAccount(draft)
            else useCases.updateAccount(code, draft)
        }
    }

    fun deactivateAccount(code: String) {
        runAction("حساب غیرفعال شد.") {
            useCases.deactivateAccount(code)
        }
    }

    fun postManual(draft: ManualJournalDraft, onSuccess: (PostedJournal) -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val posted = useCases.postManual(draft)
                message.value = "سند ${posted.entryNo} با موفقیت ثبت شد."
                onSuccess(posted)
            } catch (error: Exception) {
                message.value = UiErrorHandler.message("AccountingViewModel.postManual", error)
            } finally {
                busy.value = false
            }
        }
    }

    fun reverseManual(entryId: Long, epochDay: Long, reason: String) {
        runAction(
            successMessage = "سند برگشت ثبت شد و اثر مالی سند قبلی خنثی شد.",
            onSuccess = { selectedJournalId.value = null },
        ) {
            useCases.reverseManual(entryId, epochDay, reason)
        }
    }

    private fun runAction(
        successMessage: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                block()
                message.value = successMessage
                onSuccess()
            } catch (error: Exception) {
                message.value = UiErrorHandler.message("AccountingViewModel", error)
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(useCases: AccountingUseCases): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountingViewModel(useCases) as T
            }
    }
}
