package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.branch.BranchRepository
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.domain.recipe.RecipeRepository
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.inventory.InventoryRepository
import ir.restaurant.management.domain.sales.CustomerRecord
import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesDraft
import ir.restaurant.management.domain.sales.DailySalesItem
import ir.restaurant.management.domain.sales.DailySalesReport
import ir.restaurant.management.domain.sales.DailySalesRepository
import ir.restaurant.management.domain.sales.DailySalesReversalDraft
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.DailySalesStatus
import ir.restaurant.management.domain.sales.SalesDayClosureDraft
import ir.restaurant.management.domain.sales.SalesDayReopenDraft
import ir.restaurant.management.domain.sales.SalesHistoryRepository
import ir.restaurant.management.domain.sales.SalesSettlementType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DailySalesUiState(
    val sales: List<DailySalesItem> = emptyList(),
    val branches: List<BranchRecord> = emptyList(),
    val locations: List<InventoryLocationRecord> = emptyList(),
    val menuItems: List<MenuItem> = emptyList(),
    val customers: List<CustomerRecord> = emptyList(),
    val report: DailySalesReport? = null,
    val reportFromEpochDay: Long = currentEpochDay() - 29,
    val reportToEpochDay: Long = currentEpochDay(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val activeSalesRial: Long get() = report?.salesRial ?: 0
    val grossProfitRial: Long get() = report?.grossProfitRial ?: 0
    val receivablesRial: Long get() = sales.asSequence()
        .filter { it.status == DailySalesStatus.POSTED && !it.isReversed }
        .flatMap { it.settlements.asSequence() }
        .filter { it.type == SalesSettlementType.PERSONAL_CREDIT || it.type == SalesSettlementType.CORPORATE_CREDIT }
        .sumOf { it.amountRial }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DailySalesViewModel(
    private val repository: DailySalesRepository,
    branchRepository: BranchRepository,
    inventoryRepository: InventoryRepository,
    recipeRepository: RecipeRepository,
    salesHistoryRepository: SalesHistoryRepository,
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val range = MutableStateFlow((currentEpochDay() - 29) to currentEpochDay())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val commandError = MutableStateFlow<String?>(null)
    private val sales = query.debounce(250).distinctUntilChanged().flatMapLatest(repository::observe)
    private val report = range.flatMapLatest { repository.observeReport(it.first, it.second) }

    private data class Content(
        val sales: List<DailySalesItem>,
        val branches: List<BranchRecord>,
        val locations: List<InventoryLocationRecord>,
        val menuItems: List<MenuItem>,
        val customers: List<CustomerRecord>,
        val report: DailySalesReport,
        val range: Pair<Long, Long>,
    )

    private val salesContent = combine(
        sales, recipeRepository.observeMenuItems(), salesHistoryRepository.customers, report, range,
    ) { saleRows, menuRows, customerRows, currentReport, currentRange ->
        Quintuple(saleRows, menuRows, customerRows.filter { it.isActive }, currentReport, currentRange)
    }

    private val branchLocationContent = combine(branchRepository.activeBranches, inventoryRepository.locations) { branches, locations -> branches to locations.filter { it.active } }

    private val content = combine(salesContent, branchLocationContent) { base, scope ->
        Content(base.first, scope.first, scope.second, base.second, base.third, base.fourth, base.fifth)
    }

    val state: StateFlow<DailySalesUiState> = combine(content, busy, message, commandError) { base, isBusy, currentMessage, currentError ->
        DailySalesUiState(
            sales = base.sales,
            branches = base.branches,
            locations = base.locations,
            menuItems = base.menuItems,
            customers = base.customers,
            report = base.report,
            reportFromEpochDay = base.range.first,
            reportToEpochDay = base.range.second,
            busy = isBusy,
            message = currentMessage,
            error = currentError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailySalesUiState())

    fun search(value: String) { query.value = value }
    fun setReportRange(from: Long, to: Long) { if (from > 0 && to >= from) range.value = from to to }
    fun clearMessage() { message.value = null }

    fun saveDraft(
        summaryId: Long?,
        branchId: Long,
        locationId: Long,
        epochDay: Long,
        grossSalesRial: Long,
        discountRial: Long,
        returnRial: Long,
        serviceRial: Long,
        taxRial: Long,
        notes: String,
        lines: List<DailyMenuSaleDraft>,
        settlements: List<DailySalesSettlementDraft>,
        done: () -> Unit = {},
    ) = runCommand("DailySalesViewModel.saveDraft", done) {
        val draft = DailySalesDraft(
            businessEpochDay = epochDay,
            discountRial = discountRial,
            serviceRial = serviceRial,
            taxRial = taxRial,
            cashRial = 0,
            cardRial = 0,
            transferRial = 0,
            notes = notes,
            lines = lines,
            branchId = branchId,
            locationId = locationId,
            returnRial = returnRial,
            settlements = settlements,
            grossSalesRial = grossSalesRial,
        )
        if (summaryId == null) repository.createDraft(draft) else repository.updateDraft(summaryId, draft)
        message.value = if (summaryId == null) "پیش‌نویس فروش روزانه ایجاد شد." else "پیش‌نویس فروش روزانه ویرایش شد."
    }

    fun confirm(summaryId: Long, done: () -> Unit = {}) = runCommand("DailySalesViewModel.confirm", done) {
        repository.confirm(summaryId)
        message.value = "فروش روزانه تأیید شد و آماده ثبت نهایی است."
    }

    fun postConfirmed(summaryId: Long, done: () -> Unit = {}) = runCommand("DailySalesViewModel.postConfirmed", done) {
        repository.postConfirmed(summaryId)
        message.value = "فروش روزانه نهایی شد؛ موجودی، حسابداری و مطالبات به‌صورت اتمیک ثبت شدند."
    }

    fun reverse(summaryId: Long, reversalEpochDay: Long, reason: String, done: () -> Unit = {}) =
        runCommand("DailySalesViewModel.reverse", done) {
            repository.reverse(DailySalesReversalDraft(summaryId, reversalEpochDay, reason))
            message.value = "فروش و آثار مالی مرتبط برگشت داده شدند."
        }

    fun closeDay(branchId: Long, businessEpochDay: Long, note: String, pin: String, done: () -> Unit = {}) =
        runCommand("DailySalesViewModel.closeDay", done) {
            securityRepository.authorizeSensitiveAction(SensitiveAction.CLOSE_SALES_DAY, pin, SensitiveActionContext.resource("SALES_DAY", "$branchId:$businessEpochDay", branchId))
            repository.closeDay(SalesDayClosureDraft(branchId, businessEpochDay, note))
            message.value = "روز فروش بسته و امضا شد."
        }

    fun reopenDay(branchId: Long, businessEpochDay: Long, reason: String, pin: String, done: () -> Unit = {}) =
        runCommand("DailySalesViewModel.reopenDay", done) {
            securityRepository.authorizeSensitiveAction(SensitiveAction.REOPEN_SALES_DAY, pin, SensitiveActionContext.resource("SALES_DAY", "$branchId:$businessEpochDay", branchId))
            repository.reopenDay(SalesDayReopenDraft(branchId, businessEpochDay, reason))
            message.value = "روز فروش با مجوز مالک بازگشایی شد."
        }

    private fun runCommand(source: String, done: () -> Unit, action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            commandError.value = null
            try {
                action()
                done()
            } catch (error: Exception) {
                commandError.value = UiErrorHandler.message(source, error)
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: DailySalesRepository,
            branchRepository: BranchRepository,
            inventoryRepository: InventoryRepository,
            recipeRepository: RecipeRepository,
            salesHistoryRepository: SalesHistoryRepository,
            securityRepository: SecurityRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DailySalesViewModel(repository, branchRepository, inventoryRepository, recipeRepository, salesHistoryRepository, securityRepository) as T
        }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
)
