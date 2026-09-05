package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.application.crm.CrmUseCases
import ir.restaurant.management.application.sales.SalesHistoryUseCases
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.brief.DailyManagementBriefService
import ir.restaurant.management.domain.crm.CustomerDuplicateCandidate
import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand
import ir.restaurant.management.domain.crm.CustomerReceivableAdjustmentCommand
import ir.restaurant.management.domain.crm.ReceivableAdjustmentDirection
import ir.restaurant.management.domain.crm.ReceivableAdjustmentEconomicNature
import ir.restaurant.management.domain.crm.ReceivableAging
import ir.restaurant.management.domain.crm.ReceivableLedgerRecord
import ir.restaurant.management.domain.receivables.ReceivableAging as BranchReceivableAging
import ir.restaurant.management.domain.receivables.ReceivableCollectionDraft
import ir.restaurant.management.domain.receivables.ReceivableRecord
import ir.restaurant.management.domain.receivables.ReceivableService
import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.sales.CustomerRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
data class CrmUiState(
    val customers: List<CustomerRecord> = emptyList(),
    val selectedCustomerId: Long? = null,
    val ledger: List<ReceivableLedgerRecord> = emptyList(),
    val aging: ReceivableAging? = null,
    val duplicateCandidates: List<CustomerDuplicateCandidate> = emptyList(),
    val selectedReceivableBranchId: Long? = null,
    val openReceivables: List<ReceivableRecord> = emptyList(),
    val branchReceivableAging: BranchReceivableAging? = null,
    val collectedTodayRial: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CrmViewModel(
    private val crm: CrmUseCases,
    private val salesHistory: SalesHistoryUseCases,
    private val receivables: ReceivableService,
    private val dailyBrief: DailyManagementBriefService,
) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    private val selectedReceivableBranch = MutableStateFlow<Long?>(null)
    private val receivableOverview = MutableStateFlow(ReceivableOverview())
    private val transient = MutableStateFlow(CrmUiState())
    private val ledger = selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else crm.ledger(id) }
    private val openReceivables = selectedReceivableBranch.flatMapLatest { branchId ->
        if (branchId == null) flowOf(emptyList()) else receivables.observeOpen(branchId)
    }

    private data class ReceivableOverview(
        val aging: BranchReceivableAging? = null,
        val collectedTodayRial: Long? = null,
    )

    private data class ReceivableContent(
        val branchId: Long?,
        val rows: List<ReceivableRecord>,
        val overview: ReceivableOverview,
    )

    private val receivableContent = combine(selectedReceivableBranch, openReceivables, receivableOverview) { branchId, rows, overview ->
        ReceivableContent(branchId, rows, overview)
    }

    val state: StateFlow<CrmUiState> = combine(salesHistory.customers, selected, ledger, transient, receivableContent) { customers, customerId, rows, local, branchContent ->
        local.copy(
            customers = customers,
            selectedCustomerId = customerId,
            ledger = rows,
            selectedReceivableBranchId = branchContent.branchId,
            openReceivables = branchContent.rows,
            branchReceivableAging = branchContent.overview.aging,
            collectedTodayRial = branchContent.overview.collectedTodayRial,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrmUiState())

    fun selectReceivableBranch(branchId: Long?) {
        selectedReceivableBranch.value = branchId
        receivableOverview.value = ReceivableOverview()
        if (branchId != null) refreshReceivableOverview(branchId)
    }

    fun collectReceivable(draft: ReceivableCollectionDraft) {
        command("وصول مطالبه ثبت شد؛ درآمد جدید ایجاد نشد.") {
            receivables.collect(draft)
            selectedReceivableBranch.value?.let { refreshReceivableOverviewAfterCommand(it) }
        }
    }

    fun refreshReceivableOverview(branchId: Long? = selectedReceivableBranch.value) {
        val id = branchId ?: return
        command(null) { refreshReceivableOverviewAfterCommand(id) }
    }

    private suspend fun refreshReceivableOverviewAfterCommand(branchId: Long) {
        val day = currentLocalEpochDay()
        val aging = runCatching { receivables.aging(branchId, day) }.getOrNull()
        val collections = runCatching { dailyBrief.compose(branchId, day).liquidity.oldReceivableCollectionsRial }.getOrNull()
        receivableOverview.value = ReceivableOverview(aging, collections)
    }

    fun select(customerId: Long?) {
        selected.value = customerId
        transient.update { it.copy(aging = null, duplicateCandidates = emptyList(), message = null, isError = false) }
        if (customerId != null) refreshAging(customerId)
    }

    fun saveCustomer(id: Long?, draft: CustomerDraft) {
        command("اطلاعات مشتری ذخیره شد.") {
            val savedId = salesHistory.saveCustomer(id, draft.validated())
            selected.value = savedId
        }
    }

    fun postOpeningBalance(
        amountRial: Long,
        direction: ReceivableAdjustmentDirection,
        businessEpochDay: Long,
        dueEpochDay: Long?,
        reason: String,
    ) {
        val customerId = selected.value ?: return
        command("مانده افتتاحیه با سند حسابداری ثبت شد.") {
            crm.postOpeningBalance(CustomerOpeningBalanceCommand(customerId, businessEpochDay, amountRial, direction, dueEpochDay, reason))
            refreshAgingAfterCommand(customerId)
        }
    }

    fun postAdjustment(
        amountRial: Long,
        direction: ReceivableAdjustmentDirection,
        economicNature: ReceivableAdjustmentEconomicNature,
        businessEpochDay: Long,
        dueEpochDay: Long?,
        reason: String,
    ) {
        val customerId = selected.value ?: return
        command("تعدیل حساب با سند حسابداری ثبت شد.") {
            crm.postAdjustment(CustomerReceivableAdjustmentCommand(customerId, businessEpochDay, amountRial, direction, economicNature, dueEpochDay, reason))
            refreshAgingAfterCommand(customerId)
        }
    }

    fun refreshAging(customerId: Long? = selected.value) {
        val id = customerId ?: return
        command(null) { refreshAgingAfterCommand(id) }
    }

    private suspend fun refreshAgingAfterCommand(customerId: Long) {
        val result = crm.aging(customerId, currentLocalEpochDay())
        transient.update { it.copy(aging = result) }
    }

    fun detectDuplicates(customerId: Long? = selected.value) {
        val id = customerId ?: return
        command(null) {
            val customer = state.value.customers.firstOrNull { it.id == id } ?: error("مشتری پیدا نشد.")
            val rows = crm.duplicateCandidates(id, customer.phone.ifBlank { customer.mobile }, customer.nationalId)
            transient.update { it.copy(duplicateCandidates = rows) }
        }
    }

    fun merge(targetCustomerId: Long, reason: String) {
        val sourceId = selected.value ?: return
        command("ادغام مشتری با انتقال تمام مراجع ثبت شد.") {
            crm.merge(sourceId, targetCustomerId, reason)
            selected.value = targetCustomerId
            transient.update { it.copy(duplicateCandidates = emptyList()) }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null, isError = false) }

    private fun command(success: String?, block: suspend () -> Unit) {
        if (transient.value.busy) return
        viewModelScope.launch {
            transient.update { it.copy(busy = true, message = null, isError = false) }
            try {
                block()
                transient.update { it.copy(busy = false, message = success) }
            } catch (e: Exception) {
                transient.update { it.copy(busy = false, message = e.message ?: "عملیات حساب مشتری انجام نشد.", isError = true) }
            }
        }
    }

    companion object {
        fun factory(
            crm: CrmUseCases,
            salesHistory: SalesHistoryUseCases,
            receivables: ReceivableService,
            dailyBrief: DailyManagementBriefService,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CrmViewModel::class.java))
                return CrmViewModel(crm, salesHistory, receivables, dailyBrief) as T
            }
        }
    }
}
