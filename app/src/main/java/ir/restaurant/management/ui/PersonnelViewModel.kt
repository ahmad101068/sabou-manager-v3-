package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.application.payroll.PayrollUseCases
import ir.restaurant.management.application.personnel.AttendanceUseCases
import ir.restaurant.management.application.personnel.PersonnelUseCases
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.EmployeeAdvanceDraft
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.EmployeeTimelineItem
import ir.restaurant.management.domain.personnel.EmployeeAuditRecord
import ir.restaurant.management.domain.personnel.HrDocumentRecord
import ir.restaurant.management.domain.personnel.HrDocumentDraft
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.AttendancePunchDraft
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendanceCorrectionRecord
import ir.restaurant.management.domain.personnel.AttendanceRecord
import ir.restaurant.management.domain.personnel.AttendanceSummary
import ir.restaurant.management.domain.personnel.LeaveDraft
import ir.restaurant.management.domain.personnel.LeaveRecord
import ir.restaurant.management.domain.personnel.LeaveReviewDraft
import ir.restaurant.management.domain.personnel.LeaveGrantDraft
import ir.restaurant.management.domain.personnel.PayrollCalculation
import ir.restaurant.management.domain.personnel.PayrollRecord
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.PayrollPolicyRecord
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.ShiftTemplateRecord
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkScheduleRecord
import ir.restaurant.management.domain.personnel.PlannedShiftDraft
import ir.restaurant.management.domain.personnel.PlannedShiftRecord
import ir.restaurant.management.domain.personnel.OvertimeApprovalRecord
import ir.restaurant.management.domain.personnel.OvertimeReviewCommand
import ir.restaurant.management.domain.personnel.PayrollReadinessResult
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.personnel.ApproveManualAdjustmentCommand
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.ClosePayrollPeriodCommand
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentCommand
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentRecordV2
import ir.restaurant.management.domain.personnel.PayPayslipCommand
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollBatchRecordV2
import ir.restaurant.management.domain.personnel.PayrollExceptionRecord
import ir.restaurant.management.domain.personnel.PayrollPayslipDetailV2
import ir.restaurant.management.domain.personnel.PayrollPayslipRecordV2
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodRecordV2
import ir.restaurant.management.domain.personnel.ReopenPayrollPeriodCommand
import ir.restaurant.management.domain.personnel.ReversePayrollPaymentCommand
import ir.restaurant.management.domain.personnel.ReversePayslipCommandV2
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.treasury.TreasuryChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class PersonnelUiState(
    val employees: List<EmployeeRecord> = emptyList(),
    val payrolls: List<PayrollRecord> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val pendingAttendanceCorrections: List<AttendanceCorrectionRecord> = emptyList(),
    val leaves: List<LeaveRecord> = emptyList(),
    val pendingLeaves: List<LeaveRecord> = emptyList(),
    val payrollPreview: PayrollCalculation? = null,
    val attendanceSummary: AttendanceSummary? = null,
    val contracts: List<ir.restaurant.management.domain.personnel.EmployeeContractRecord> = emptyList(),
    val advances: List<ir.restaurant.management.domain.personnel.EmployeeAdvanceRecord> = emptyList(),
    val openAdvances: List<ir.restaurant.management.domain.personnel.EmployeeAdvanceRecord> = emptyList(),
    val payrollPolicies: List<PayrollPolicyRecord> = emptyList(),
    val shiftTemplates: List<ShiftTemplateRecord> = emptyList(),
    val workSchedules: List<WorkScheduleRecord> = emptyList(),
    val plannedShifts: List<PlannedShiftRecord> = emptyList(),
    val pendingOvertimeApprovals: List<OvertimeApprovalRecord> = emptyList(),
    val payrollReadiness: PayrollReadinessResult? = null,
    val auditTimeline: List<EmployeeAuditRecord> = emptyList(),
    val documents: List<HrDocumentRecord> = emptyList(),
    val selectedEmployeeId: Long? = null,
    val message: String? = null,
)

data class HrPayrollUiState(
    val periods: List<PayrollPeriodRecordV2> = emptyList(),
    val batches: List<PayrollBatchRecordV2> = emptyList(),
    val employeePayslips: List<PayrollPayslipRecordV2> = emptyList(),
    val payslipDetail: PayrollPayslipDetailV2? = null,
    val calculationExceptions: List<PayrollExceptionRecord> = emptyList(),
    val manualAdjustments: List<ManualPayrollAdjustmentRecordV2> = emptyList(),
    val employeeTimeline: List<EmployeeTimelineItem> = emptyList(),
    val busy: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PersonnelViewModel(
    private val personnel: PersonnelUseCases,
    private val attendanceUseCases: AttendanceUseCases,
    private val payroll: PayrollUseCases,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val payrollPreview = MutableStateFlow<PayrollCalculation?>(null)
    private val attendanceSummary = MutableStateFlow<AttendanceSummary?>(null)
    private val selectedEmployeeId = MutableStateFlow<Long?>(null)
    private val payslipDetail = MutableStateFlow<PayrollPayslipDetailV2?>(null)
    private val calculationExceptions = MutableStateFlow<List<PayrollExceptionRecord>>(emptyList())
    private val manualAdjustments = MutableStateFlow<List<ManualPayrollAdjustmentRecordV2>>(emptyList())
    private val payrollReadiness = MutableStateFlow<PayrollReadinessResult?>(null)
    private val hrBusy = MutableStateFlow(false)
    private val contracts = selectedEmployeeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else personnel.contracts(id) }
    private val advances = selectedEmployeeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else personnel.advances(id) }
    private val plannedShifts = selectedEmployeeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else personnel.plannedShifts(id) }
    private val auditTimeline = selectedEmployeeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else personnel.auditTimeline(id) }
    private val documents = selectedEmployeeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else personnel.documents(id) }
    private val attendanceReview = combine(attendanceUseCases.attendance, attendanceUseCases.pendingCorrections) { attendance, corrections -> attendance to corrections }
    private val coreState = combine(personnel.employees, personnel.payrolls, attendanceReview, attendanceUseCases.leaves, attendanceUseCases.pendingLeaves) { e, p, attendanceState, l, pending -> arrayOf(e, p, attendanceState, l, pending) }
    private data class EmployeeDetails(
        val contracts: List<ir.restaurant.management.domain.personnel.EmployeeContractRecord>,
        val advances: List<ir.restaurant.management.domain.personnel.EmployeeAdvanceRecord>,
        val openAdvances: List<ir.restaurant.management.domain.personnel.EmployeeAdvanceRecord>,
        val payrollPolicies: List<PayrollPolicyRecord>,
        val employeeId: Long?,
    )
    private data class SchedulingDetails(
        val shiftTemplates: List<ShiftTemplateRecord>,
        val workSchedules: List<WorkScheduleRecord>,
        val plannedShifts: List<PlannedShiftRecord>,
        val pendingOvertime: List<OvertimeApprovalRecord>,
    )
    private data class Transient(
        val preview: PayrollCalculation?,
        val summary: AttendanceSummary?,
        val details: EmployeeDetails,
        val scheduling: SchedulingDetails,
        val readiness: PayrollReadinessResult?,
        val auditTimeline: List<EmployeeAuditRecord>,
        val documents: List<HrDocumentRecord>,
        val message: String?,
    )
    private val employeeDetails = combine(contracts, advances, personnel.openAdvances, personnel.payrollPolicies, selectedEmployeeId) { c, a, open, policies, id -> EmployeeDetails(c, a, open, policies, id) }
    private val schedulingDetails = combine(personnel.shiftTemplates, personnel.workSchedules, plannedShifts, personnel.pendingOvertimeApprovals) { shifts, schedules, planned, overtime ->
        SchedulingDetails(shifts, schedules, planned, overtime)
    }
    private data class ReadinessAuditMessage(
        val readiness: PayrollReadinessResult?,
        val auditTimeline: List<EmployeeAuditRecord>,
        val documents: List<HrDocumentRecord>,
        val message: String?,
    )
    private val auditDocs = combine(auditTimeline, documents) { audit, docs -> audit to docs }
    private val readinessAuditMessage = combine(payrollReadiness, auditDocs, message) { readiness, ad, m ->
        ReadinessAuditMessage(readiness, ad.first, ad.second, m)
    }
    private val transientState = combine(payrollPreview, attendanceSummary, employeeDetails, schedulingDetails, readinessAuditMessage) { preview, summary, details, scheduling, ram ->
        Transient(preview, summary, details, scheduling, ram.readiness, ram.auditTimeline, ram.documents, ram.message)
    }
    val state: StateFlow<PersonnelUiState> = combine(coreState, transientState) { core, transient ->
        @Suppress("UNCHECKED_CAST")
        PersonnelUiState(
            employees = core[0] as List<EmployeeRecord>, payrolls = core[1] as List<PayrollRecord>,
            attendance = (core[2] as Pair<List<AttendanceRecord>, List<AttendanceCorrectionRecord>>).first,
            pendingAttendanceCorrections = (core[2] as Pair<List<AttendanceRecord>, List<AttendanceCorrectionRecord>>).second,
            leaves = core[3] as List<LeaveRecord>, pendingLeaves = core[4] as List<LeaveRecord>, payrollPreview = transient.preview,
            attendanceSummary = transient.summary, contracts = transient.details.contracts, advances = transient.details.advances,
            openAdvances = transient.details.openAdvances, payrollPolicies = transient.details.payrollPolicies,
            shiftTemplates = transient.scheduling.shiftTemplates, workSchedules = transient.scheduling.workSchedules,
            plannedShifts = transient.scheduling.plannedShifts, pendingOvertimeApprovals = transient.scheduling.pendingOvertime,
            payrollReadiness = transient.readiness, auditTimeline = transient.auditTimeline, documents = transient.documents,
            selectedEmployeeId = transient.details.employeeId, message = transient.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonnelUiState())

    private val employeePayslips = selectedEmployeeId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else payroll.employeePayslips(id)
    }
    private val employeeTimeline = selectedEmployeeId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else payroll.employeeTimeline(id)
    }
    private data class HrCore(
        val periods: List<PayrollPeriodRecordV2>,
        val batches: List<PayrollBatchRecordV2>,
        val payslips: List<PayrollPayslipRecordV2>,
        val timeline: List<EmployeeTimelineItem>,
    )
    val hrState: StateFlow<HrPayrollUiState> = combine(
        combine(payroll.periods, payroll.batches, employeePayslips, employeeTimeline) { periods, batches, payslips, timeline ->
            HrCore(periods, batches, payslips, timeline)
        },
        payslipDetail,
        calculationExceptions,
        manualAdjustments,
        hrBusy,
    ) { core, detail, exceptions, adjustments, busy ->
        HrPayrollUiState(
            periods = core.periods,
            batches = core.batches,
            employeePayslips = core.payslips,
            payslipDetail = detail,
            calculationExceptions = exceptions,
            manualAdjustments = adjustments,
            employeeTimeline = core.timeline,
            busy = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HrPayrollUiState())

    fun saveEmployee(id: Long?, draft: EmployeeDraft) = launch("پرسنل ذخیره شد.") { personnel.saveEmployee(id, draft) }
    fun saveDocument(draft: HrDocumentDraft) = launch("سند منابع انسانی ذخیره شد.") { personnel.saveDocument(draft) }
    fun archiveDocument(documentId: Long) = launch("سند بایگانی شد.") { personnel.archiveDocument(documentId) }
    fun deactivate(id: Long) = launch("پرسنل غیرفعال شد.") { personnel.deactivateEmployee(id) }
    fun saveAttendance(id: Long?, draft: AttendanceDraft) = launch("حضور و غیاب ذخیره شد.") { attendanceUseCases.save(id, draft) }
    fun punchAttendance(employeeId: Long, eventType: AttendanceEventType) = launch(
        if (eventType == AttendanceEventType.CLOCK_IN) "ورود با زمان واقعی ثبت شد." else "خروج با زمان واقعی ثبت شد.",
    ) { attendanceUseCases.punch(AttendancePunchDraft(employeeId = employeeId, eventType = eventType)) }
    fun requestLeave(draft: LeaveDraft, requestedBy: String = "SYSTEM") = launch("درخواست مرخصی ثبت شد.") { attendanceUseCases.requestLeave(draft, requestedBy) }
    fun reviewLeave(draft: LeaveReviewDraft) = launch("نتیجه بررسی مرخصی ثبت شد.") { attendanceUseCases.reviewLeave(draft) }
    fun cancelLeave(id: Long) = launch("درخواست مرخصی لغو شد.") { attendanceUseCases.cancelLeave(id) }
    fun approveLeave(draft: LeaveDraft) = launch("مرخصی تأیید شد.") { attendanceUseCases.approveLeave(draft) }
    fun loadAttendanceSummary(employeeId: Long, startEpochDay: Long, endEpochDay: Long) = viewModelScope.launch {
        runCatching { attendanceUseCases.summary(employeeId, startEpochDay, endEpochDay) }
            .onSuccess { attendanceSummary.value = it }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.attendance", it) }
    }
    fun saveContract(id: Long?, draft: EmployeeContractDraft) = launch("قرارداد ذخیره شد.") { personnel.saveContract(id, draft) }
    fun saveShiftTemplate(id: Long?, draft: ShiftTemplateDraft) = launch("شیفت ذخیره شد.") { personnel.saveShiftTemplate(id, draft) }
    fun saveWorkSchedule(id: Long?, draft: WorkScheduleDraft) = launch("برنامه کاری ذخیره شد.") { personnel.saveWorkSchedule(id, draft) }
    fun savePlannedShift(id: Long?, draft: PlannedShiftDraft) = launch("شیفت برنامه‌ریزی‌شده ذخیره شد.") { personnel.savePlannedShift(id, draft) }
    fun reviewOvertime(command: OvertimeReviewCommand) = launch("نتیجه اضافه‌کار ثبت شد.") { attendanceUseCases.reviewOvertime(command) }
    fun approveContract(id: Long) = launch("قرارداد تأیید شد.") { personnel.approveContract(id) }
    fun approveAttendanceCorrection(id: Long) = launch("اصلاح حضور و غیاب تأیید شد.") { attendanceUseCases.approveCorrection(id) }
    fun rejectAttendanceCorrection(id: Long, reason: String) = launch("اصلاح حضور و غیاب رد شد.") { attendanceUseCases.rejectCorrection(id, reason) }
    fun grantLeave(draft: LeaveGrantDraft) = launch("سهمیه مرخصی در دفتر مرخصی ثبت شد.") { attendanceUseCases.grantLeave(draft) }
    fun postAdvance(draft: EmployeeAdvanceDraft) = launch("مساعده ثبت و سند حسابداری صادر شد.") { personnel.postAdvance(draft) }
    fun settleAdvance(
        id: Long,
        amountRial: Long,
        paymentMethod: TreasuryChannel,
        settlementEpochDay: Long,
    ) = launch("تسویه مساعده و سند دریافت ثبت شد.") {
        personnel.settleAdvance(id, amountRial, paymentMethod, settlementEpochDay)
    }
    fun savePayrollPolicy(draft: PayrollPolicyDraft) = launch("سیاست نسخه‌دار حقوق ذخیره شد.") { personnel.savePayrollPolicy(draft) }
    fun openPayrollPeriod(draft: PayrollPeriodDraftV2) = launchHr("دوره حقوق باز شد.") { payroll.openPeriod(draft) }
    fun closePayrollPeriod(command: ClosePayrollPeriodCommand) = launchHr("دوره حقوق بسته شد.") { payroll.closePeriod(command) }
    fun reopenPayrollPeriod(command: ReopenPayrollPeriodCommand) = launchHr("دوره حقوق برای اصلاح بازگشایی شد.") { payroll.reopenPeriod(command) }
    fun createPayrollBatch(draft: PayrollBatchDraftV2) = launchHr("دسته حقوق ایجاد شد.") { payroll.createBatch(draft) }
    fun calculatePayrollBatch(command: CalculatePayrollBatchCommand) = viewModelScope.launch {
        hrBusy.value = true
        runCatching { payroll.calculateBatch(command) }
            .onSuccess { outcome ->
                calculationExceptions.value = outcome.exceptions
                message.value = if (outcome.hasBlockingExceptions) {
                    "محاسبه به‌دلیل ${outcome.exceptions.count { it.blocking }} استثنای مسدودکننده متوقف شد."
                } else {
                    "${outcome.payslipIds.size} فیش حقوق با تصویر ثابت محاسبه شد."
                }
            }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.payrollV2.calculate", it) }
        hrBusy.value = false
    }
    fun reviewPayrollBatch(command: ReviewPayrollBatchCommand) = launchHr("دسته حقوق برای بازبینی ثبت شد.") { payroll.submitBatch(command) }
    fun approvePayrollBatch(command: ApprovePayrollBatchCommand) = launchHr("تعهد حقوق تأیید و اسناد تعهد ثبت شد.") { payroll.approveBatch(command) }
    fun submitManualAdjustment(command: ManualPayrollAdjustmentCommand) = launchHr("تعدیل حقوق برای تأیید ارسال شد.") { payroll.submitAdjustment(command) }
    fun approveManualAdjustment(command: ApproveManualAdjustmentCommand) = launchHr("تعدیل حقوق تأیید شد.") { payroll.approveAdjustment(command) }
    fun loadManualAdjustments(periodId: Long) = viewModelScope.launch {
        runCatching { payroll.manualAdjustments(periodId) }
            .onSuccess { manualAdjustments.value = it }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.payrollV2.adjustments", it) }
    }
    fun payPayslip(command: PayPayslipCommand) = launchHr("پرداخت حقوق در دفتر پرداخت ثبت شد.") { payroll.pay(command) }
    fun reversePayrollPayment(command: ReversePayrollPaymentCommand) = launchHr("پرداخت با تراکنش جبرانی برگشت خورد.") { payroll.reversePayment(command) }
    fun reversePayslipV2(command: ReversePayslipCommandV2) = launchHr("فیش حقوق برگشت خورد؛ اصلاح فقط با Revision جدید ممکن است.") { payroll.reversePayslip(command) }
    fun loadPayslipDetail(id: Long) = viewModelScope.launch {
        hrBusy.value = true
        runCatching { payroll.payslipDetail(id) }
            .onSuccess { payslipDetail.value = it }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.payrollV2.detail", it) }
        hrBusy.value = false
    }
    fun clearPayslipDetail() { payslipDetail.value = null }
    fun clearMessage() { message.value = null }
    fun selectEmployee(id: Long?) {
        selectedEmployeeId.value = id
        attendanceSummary.value = null
        payslipDetail.value = null
        payrollReadiness.value = null
        if (id != null) refreshPayrollReadiness(id)
    }
    fun refreshPayrollReadiness(employeeId: Long, businessEpochDay: Long = currentLocalEpochDay()) = viewModelScope.launch {
        runCatching { personnel.payrollReadiness(employeeId, businessEpochDay) }
            .onSuccess { payrollReadiness.value = it }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.readiness", it) }
    }
    private fun launch(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onSuccess { message.value = success }.onFailure { message.value = UiErrorHandler.message("PersonnelViewModel", it) }
    }
    private fun launchHr(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        hrBusy.value = true
        runCatching { block() }
            .onSuccess { message.value = success }
            .onFailure { message.value = UiErrorHandler.message("PersonnelViewModel.payrollV2", it) }
        hrBusy.value = false
    }
    companion object {
        fun factory(
            personnel: PersonnelUseCases,
            attendance: AttendanceUseCases,
            payroll: PayrollUseCases,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PersonnelViewModel(personnel, attendance, payroll) as T
        }
    }
}
