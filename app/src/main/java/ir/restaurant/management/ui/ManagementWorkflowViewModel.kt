package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.brief.DailyManagementBrief
import ir.restaurant.management.domain.brief.DailyManagementBriefService
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModel
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModelFactory
import ir.restaurant.management.domain.control.ChecklistRunItemRecord
import ir.restaurant.management.domain.control.ChecklistRunRecord
import ir.restaurant.management.domain.control.ChecklistTemplateDraft
import ir.restaurant.management.domain.control.ChecklistTemplateRecord
import ir.restaurant.management.domain.control.ManagementIssueAssignmentDraft
import ir.restaurant.management.domain.control.ManagementIssueRecord
import ir.restaurant.management.domain.control.ManagementTaskDraft
import ir.restaurant.management.domain.control.ManagementTaskRecord
import ir.restaurant.management.domain.control.ManagementWorkflowReadService
import ir.restaurant.management.domain.control.ManagementWorkflowService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementWorkflowViewModel(
    private val readService: ManagementWorkflowReadService,
    private val workflowService: ManagementWorkflowService,
    private val briefService: DailyManagementBriefService,
    private val refreshRules: suspend (Long, Long, Long) -> Int,
) : ViewModel() {
    private val selectedBranchId = MutableStateFlow<Long?>(null)
    private val selectedDay = MutableStateFlow(currentEpochDay())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val selectedRunId = MutableStateFlow<Long?>(null)
    private val runItems = MutableStateFlow<List<ChecklistRunItemRecord>>(emptyList())

    private fun <T> branchFlow(source: (Long) -> Flow<List<T>>): Flow<List<T>> =
        selectedBranchId.flatMapLatest { branchId -> branchId?.let(source) ?: flowOf(emptyList()) }

    val issues: StateFlow<List<ManagementIssueRecord>> = branchFlow(readService::observeOpenIssues)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks: StateFlow<List<ManagementTaskRecord>> = branchFlow(readService::observeOpenTasks)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates: StateFlow<List<ChecklistTemplateRecord>> = branchFlow(readService::observeChecklistTemplates)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val runs: StateFlow<List<ChecklistRunRecord>> = branchFlow(readService::observeChecklistRuns)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ManagementWorkflowUiState> = combine(
        selectedBranchId, selectedDay, busy, message, error,
    ) { branchId, day, isBusy, currentMessage, currentError ->
        ManagementWorkflowUiState(branchId, day, isBusy, currentMessage, currentError)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManagementWorkflowUiState())

    val checklistRunItems: StateFlow<List<ChecklistRunItemRecord>> = runItems
    val currentRunId: StateFlow<Long?> = selectedRunId

    private val briefRevision = MutableStateFlow(0L)
    val dailyBrief: StateFlow<DailyBriefUiState> = combine(selectedBranchId, selectedDay, briefRevision) { branchId, day, revision -> Triple(branchId, day, revision) }
        .flatMapLatest { (branchId, day, _) ->
            if (branchId == null) flowOf(DailyBriefUiState())
            else kotlinx.coroutines.flow.flow {
                emit(DailyBriefUiState(loading = true))
                emit(runCatching { briefService.compose(branchId, day) }
                    .fold(
                            onSuccess = {
                                DailyBriefUiState(
                                    brief = it,
                                    readModel = DailyManagementKpiReadModelFactory.from(it),
                                )
                            },
                        onFailure = { DailyBriefUiState(error = UiErrorHandler.message("DailyManagementBrief", it)) },
                    ))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyBriefUiState())

    fun selectBranch(branchId: Long?) { if (branchId == null || branchId > 0) selectedBranchId.value = branchId }
    fun setDay(epochDay: Long) { if (epochDay > 0) selectedDay.value = epochDay }
    fun clearMessage() { message.value = null; error.value = null }
    fun refreshBrief() { briefRevision.value += 1 }

    fun refreshRules() = run("قواعد مدیریتی ارزیابی شدند.") {
        val branchId = requireNotNull(selectedBranchId.value) { "ابتدا شعبه را انتخاب کنید." }
        refreshRules(branchId, selectedDay.value, selectedDay.value)
    }

    fun acknowledgeIssue(id: Long) = run("مسئله مشاهده شد.") { workflowService.acknowledgeIssue(id) }
    fun startIssue(id: Long) = run("رسیدگی به مسئله شروع شد.") { workflowService.startIssue(id) }
    fun assignIssue(draft: ManagementIssueAssignmentDraft) = run("مسئله ارجاع شد.") { workflowService.assignIssue(draft) }
    fun resolveIssue(id: Long, note: String) = run("مسئله حل شد.") { workflowService.resolveIssue(id, note) }
    fun dismissIssue(id: Long, note: String) = run("مسئله مختومه شد.") { workflowService.dismissIssue(id, note) }

    fun createTask(draft: ManagementTaskDraft) = run("وظیفه ایجاد شد.") { workflowService.createTask(draft) }
    fun startTask(id: Long) = run("وظیفه شروع شد.") { workflowService.startTask(id) }
    fun completeTask(id: Long) = run("وضعیت وظیفه به‌روزرسانی شد.") { workflowService.completeTask(id) }
    fun approveTask(id: Long) = run("وظیفه تأیید شد.") { workflowService.approveTask(id) }
    fun rejectTask(id: Long) = run("وظیفه رد شد.") { workflowService.rejectTask(id) }
    fun cancelTask(id: Long) = run("وظیفه لغو شد.") { workflowService.cancelTask(id) }

    fun createChecklistTemplate(draft: ChecklistTemplateDraft) = run("قالب چک‌لیست ایجاد شد.") { workflowService.createChecklistTemplate(draft) }
    fun startChecklistRun(templateId: Long, assignedEmployeeId: Long?) = run("اجرای چک‌لیست شروع شد.") {
        val branchId = requireNotNull(selectedBranchId.value) { "ابتدا شعبه را انتخاب کنید." }
        val id = workflowService.startChecklistRun(templateId, branchId, selectedDay.value, assignedEmployeeId)
        openChecklistRun(id)
    }
    fun openChecklistRun(runId: Long) {
        selectedRunId.value = runId
        viewModelScope.launch { loadRunItems(runId) }
    }
    fun closeChecklistRun() { selectedRunId.value = null; runItems.value = emptyList() }
    fun completeChecklistItem(id: Long, passed: Boolean, note: String?, attachment: String?) = run("آیتم چک‌لیست ثبت شد.") {
        workflowService.completeChecklistItem(id, passed, note, attachment)
        selectedRunId.value?.let { loadRunItems(it) }
    }
    fun completeChecklistRun(id: Long) = run("اجرای چک‌لیست تکمیل شد.") { workflowService.completeChecklistRun(id); loadRunItems(id) }
    fun approveChecklistRun(id: Long) = run("چک‌لیست تأیید شد.") { workflowService.approveChecklistRun(id); loadRunItems(id) }

    private suspend fun loadRunItems(runId: Long) { runItems.value = readService.checklistRunItems(runId) }

    private fun run(success: String, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true; message.value = null; error.value = null
            try { block(); message.value = success; briefRevision.value += 1 }
            catch (t: Throwable) { error.value = UiErrorHandler.message("ManagementWorkflowViewModel", t) }
            finally { busy.value = false }
        }
    }

    companion object {
        fun factory(
            readService: ManagementWorkflowReadService,
            workflowService: ManagementWorkflowService,
            briefService: DailyManagementBriefService,
            refreshRules: suspend (Long, Long, Long) -> Int,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ManagementWorkflowViewModel(readService, workflowService, briefService, refreshRules) as T
        }
    }
}

data class ManagementWorkflowUiState(
    val selectedBranchId: Long? = null,
    val businessEpochDay: Long = currentEpochDay(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class DailyBriefUiState(
    val loading: Boolean = false,
    val brief: DailyManagementBrief? = null,
    val readModel: DailyManagementKpiReadModel? = null,
    val error: String? = null,
)
