package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.control.BudgetDraft
import ir.restaurant.management.domain.control.AvailabilityDraft
import ir.restaurant.management.domain.control.LaborPolicy
import ir.restaurant.management.domain.control.LotRegistrationDraft
import ir.restaurant.management.domain.control.LotTransferDraft
import ir.restaurant.management.domain.control.ManagementControlRepository
import ir.restaurant.management.domain.control.ManagementControlSnapshot
import ir.restaurant.management.domain.control.ShiftSwapDraft
import ir.restaurant.management.domain.control.AccountingPeriodDraft
import ir.restaurant.management.domain.control.CashReconciliationDraft
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ControlCenterUiState(
    val snapshot: ManagementControlSnapshot? = null,
    val fromEpochDay: Long = currentEpochDay() - 29,
    val toEpochDay: Long = currentEpochDay(),
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementControlViewModel(
    private val repository: ManagementControlRepository,
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    private val range = MutableStateFlow((currentEpochDay() - 29) to currentEpochDay())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val snapshot = range.flatMapLatest { repository.observeSnapshot(it.first, it.second) }

    val state: StateFlow<ControlCenterUiState> = combine(snapshot, range, busy, message) { value, selectedRange, isBusy, currentMessage ->
        ControlCenterUiState(value, selectedRange.first, selectedRange.second, isBusy, currentMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlCenterUiState())

    fun setRange(fromEpochDay: Long, toEpochDay: Long) {
        if (fromEpochDay <= toEpochDay) range.value = fromEpochDay to toEpochDay
    }
    fun clearMessage() { message.value = null }
    fun followUp(orderId: Long, note: String, done: () -> Unit = {}) = run("پیگیری سفارش ثبت شد.", done) { repository.recordPurchaseOrderFollowUp(orderId, note) }
    fun createLocation(name: String, kind: String, done: () -> Unit = {}) = run("محل نگهداری ثبت شد.", done) { repository.createLocation(name, kind) }
    fun registerLot(draft: LotRegistrationDraft, done: () -> Unit = {}) = run("بچ کالا ثبت شد.", done) { repository.registerLot(draft) }
    fun transferLot(draft: LotTransferDraft, done: () -> Unit = {}) = run("انتقال بین محل‌ها ثبت شد.", done) { repository.transferLot(draft) }
    fun saveBudget(id: Long?, draft: BudgetDraft, done: () -> Unit = {}) = run("بودجه ذخیره شد.", done) { repository.saveBudget(id, draft) }
    fun recordSpend(budgetId: Long, amountRial: Long, epochDay: Long, reference: String, done: () -> Unit = {}) = run("هزینه بودجه ثبت شد.", done) { repository.recordBudgetSpend(budgetId, amountRial, epochDay, reference) }
    fun saveLaborPolicy(policy: LaborPolicy, done: () -> Unit = {}) = run("سیاست نیروی انسانی ذخیره شد.", done) { repository.saveLaborPolicy(policy) }
    fun saveAvailability(draft: AvailabilityDraft, done: () -> Unit = {}) = run("الگوی دسترس‌پذیری ذخیره شد.", done) { repository.saveAvailability(draft) }
    fun requestShiftSwap(draft: ShiftSwapDraft, done: () -> Unit = {}) = run("درخواست جابه‌جایی شیفت ثبت شد.", done) { repository.requestShiftSwap(draft) }
    fun reviewShiftSwap(requestId: Long, approve: Boolean) = run(if (approve) "جابه‌جایی شیفت تأیید شد." else "درخواست جابه‌جایی رد شد.", {}) { repository.reviewShiftSwap(requestId, approve) }
    fun recordWorkBreak(shiftId: Long, startMinute: Int, endMinute: Int, done: () -> Unit = {}) = run("استراحت شیفت ثبت شد.", done) { repository.recordWorkBreak(shiftId, startMinute, endMinute) }
    fun closeAccountingPeriod(draft: AccountingPeriodDraft, pin: String, done: () -> Unit = {}) =
        run("دوره مالی بسته و اسناد آن قفل شد.", done) {
            securityRepository.authorizeSensitiveAction(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, pin, SensitiveActionContext.resource("ACCOUNTING_PERIOD", "${draft.fromEpochDay}:${draft.toEpochDay}"))
            repository.closeAccountingPeriod(draft)
        }
    fun reopenAccountingPeriod(id: Long, pin: String, done: () -> Unit = {}) = run("دوره مالی توسط مالک بازگشایی شد.", done) {
        securityRepository.authorizeSensitiveAction(SensitiveAction.REOPEN_ACCOUNTING_PERIOD, pin, SensitiveActionContext.resource("ACCOUNTING_PERIOD", id))
        repository.reopenAccountingPeriod(id)
    }
    fun reconcileSalesCash(draft:CashReconciliationDraft,done:()->Unit={})=run("تطبیق صندوق ثبت شد.",done){repository.reconcileSalesCash(draft)}

    private fun run(success: String, done: () -> Unit, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try { block(); message.value = success; done() }
            catch (error: Exception) { message.value = UiErrorHandler.message("ManagementControlViewModel", error) }
            finally { busy.value = false }
        }
    }

    companion object {
        fun factory(
            repository: ManagementControlRepository,
            securityRepository: SecurityRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ManagementControlViewModel(repository, securityRepository) as T
        }
    }
}
