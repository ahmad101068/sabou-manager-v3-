@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.restaurant.management.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.personnel.ApproveManualAdjustmentCommand
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.AttendanceCorrectionRecord
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendanceRecord
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.ClosePayrollPeriodCommand
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeContractRecord
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.EmploymentContractStatus
import ir.restaurant.management.domain.personnel.EmploymentStatus
import ir.restaurant.management.domain.personnel.ManualAdjustmentStatus
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentCommand
import ir.restaurant.management.domain.personnel.PayPayslipCommand
import ir.restaurant.management.domain.personnel.PayrollAdvanceDeductionRequest
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollBatchRecordV2
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollComponentDirection
import ir.restaurant.management.domain.personnel.PayrollComponentType
import ir.restaurant.management.domain.personnel.PayrollPaymentStatus
import ir.restaurant.management.domain.personnel.PayrollPayslipDetailV2
import ir.restaurant.management.domain.personnel.PayrollPayslipRecordV2
import ir.restaurant.management.domain.personnel.PayrollPayslipStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodRecordV2
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.personnel.ReopenPayrollPeriodCommand
import ir.restaurant.management.domain.personnel.ReversePayrollPaymentCommand
import ir.restaurant.management.domain.personnel.ReversePayslipCommandV2
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.treasury.TreasuryChannel

private enum class HrWorkspaceSection(val title: String) {
    PEOPLE("افراد"),
    ATTENDANCE("حضور"),
    SCHEDULING("شیفت و برنامه کاری"),
    LEAVE("مرخصی"),
    PAYROLL("حقوق"),
    ADVANCES("مساعده"),
    PERFORMANCE("عملکرد"),
}

@Composable
internal fun HrPayrollWorkspaceScreen(
    personnelState: PersonnelUiState,
    hrState: HrPayrollUiState,
    treasuryState: TreasuryUiState,
    requestedProfileEmployeeId: Long?,
    onProfileRequestConsumed: () -> Unit,
    performanceState: PerformanceUiState,
    workforceState: ControlCenterUiState,
    personnel: PersonnelViewModel,
    performance: PerformanceViewModel,
    workforce: ManagementControlViewModel,
    branches: List<BranchRecord>,
    onBack: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(HrWorkspaceSection.PEOPLE) }
    var editingEmployee by remember { mutableStateOf<EmployeeRecord?>(null) }
    var showEmployeeEditor by remember { mutableStateOf(false) }
    var detailEmployee by remember { mutableStateOf<EmployeeRecord?>(null) }
    var showAttendance by remember { mutableStateOf(false) }
    var correctionTarget by remember { mutableStateOf<AttendanceRecord?>(null) }
    var rejectCorrectionTarget by remember { mutableStateOf<AttendanceCorrectionRecord?>(null) }
    var showAttendanceSummary by remember { mutableStateOf(false) }
    var showShiftTemplate by remember { mutableStateOf(false) }
    var showWorkSchedule by remember { mutableStateOf(false) }
    var showPlannedShift by remember { mutableStateOf(false) }
    var overtimeTarget by remember { mutableStateOf<ir.restaurant.management.domain.personnel.OvertimeApprovalRecord?>(null) }
    var showLeave by remember { mutableStateOf(false) }
    var showAdvance by remember { mutableStateOf(false) }
    var contractRevisionBase by remember { mutableStateOf<EmployeeContractRecord?>(null) }
    var showContract by remember { mutableStateOf(false) }
    var showPayrollPolicy by remember { mutableStateOf(false) }
    var showPeriod by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var calculateBatch by remember { mutableStateOf<PayrollBatchRecordV2?>(null) }
    var showAdjustment by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<PayrollPayslipRecordV2?>(null) }
    var reversalTarget by remember { mutableStateOf<PayrollPayslipRecordV2?>(null) }
    var paymentReversalTarget by remember { mutableStateOf<Long?>(null) }
    var showLaborPolicy by remember { mutableStateOf(false) }
    var showAvailability by remember { mutableStateOf(false) }
    var showShiftSwap by remember { mutableStateOf(false) }
    var breakTarget by remember { mutableStateOf<ir.restaurant.management.domain.control.LaborShiftInput?>(null) }

    LaunchedEffect(requestedProfileEmployeeId, personnelState.employees) {
        personnelState.employees.firstOrNull { it.id == requestedProfileEmployeeId }?.let { employee ->
            section = HrWorkspaceSection.PEOPLE
            personnel.selectEmployee(employee.id)
            detailEmployee = employee
            onProfileRequestConsumed()
        }
    }

    Scaffold(
        topBar = {
            ProfessionalTopBar(
                "منابع انسانی و حقوق",
                "Employee 360، حضور، قرارداد و دفتر حقوق",
                onBack,
            )
        },
        floatingActionButton = {
            when (section) {
                HrWorkspaceSection.PEOPLE -> ExtendedFloatingActionButton(onClick = {
                    editingEmployee = null
                    showEmployeeEditor = true
                }) { Text("پرسنل جدید") }
                HrWorkspaceSection.ATTENDANCE -> ExtendedFloatingActionButton(onClick = { showAttendance = true }) { Text("ثبت حضور") }
                HrWorkspaceSection.SCHEDULING -> ExtendedFloatingActionButton(onClick = { showShiftTemplate = true }) { Text("شیفت جدید") }
                HrWorkspaceSection.LEAVE -> ExtendedFloatingActionButton(onClick = { showLeave = true }) { Text("درخواست مرخصی") }
                HrWorkspaceSection.PAYROLL -> ExtendedFloatingActionButton(onClick = { showPeriod = true }) { Text("دوره جدید") }
                HrWorkspaceSection.ADVANCES -> ExtendedFloatingActionButton(
                    onClick = { showAdvance = true },
                ) { Text("مساعده جدید") }
                HrWorkspaceSection.PERFORMANCE -> Unit
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HrWorkspaceNavigation(section) { section = it }
            personnelState.message?.let { MessageCard(it) }
            if (hrState.busy || workforceState.busy) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text("در حال اجرای عملیات کنترل‌شده…")
                }
            }
            Box(Modifier.weight(1f)) {
                when (section) {
                    HrWorkspaceSection.PEOPLE -> PeopleHome(
                        state = personnelState,
                        onOpen = { employee -> personnel.selectEmployee(employee.id); detailEmployee = employee },
                        onEdit = { employee -> editingEmployee = employee; showEmployeeEditor = true },
                        onNewEmployee = { editingEmployee = null; showEmployeeEditor = true },
                        onAttendance = { section = HrWorkspaceSection.ATTENDANCE },
                        onLeave = { section = HrWorkspaceSection.LEAVE },
                        onScheduling = { section = HrWorkspaceSection.SCHEDULING },
                    )
                    HrWorkspaceSection.ATTENDANCE -> AttendanceCenter(
                        state = personnelState,
                        onSummary = { showAttendanceSummary = true },
                        onCorrect = { correctionTarget = it },
                        onPunch = personnel::punchAttendance,
                        onApproveCorrection = personnel::approveAttendanceCorrection,
                        onRejectCorrection = { rejectCorrectionTarget = it },
                    )
                    HrWorkspaceSection.SCHEDULING -> PersonnelSchedulingCenter(
                        state = personnelState,
                        onNewShift = { showShiftTemplate = true },
                        onNewSchedule = { showWorkSchedule = true },
                        onPlanShift = { showPlannedShift = true },
                        onReviewOvertime = { overtimeTarget = it },
                    )
                    HrWorkspaceSection.LEAVE -> LeaveCenter(personnelState, personnel::reviewLeave, personnel::cancelLeave)
                    HrWorkspaceSection.PAYROLL -> PayrollCenter(
                        personnelState,
                        hrState,
                        onPolicy = { showPayrollPolicy = true },
                        onCreateBatch = { showBatch = true },
                        onCalculate = { calculateBatch = it },
                        onReview = { personnel.reviewPayrollBatch(ReviewPayrollBatchCommand(it.id, "بازبینی اجزای حقوق")) },
                        onApprove = { personnel.approvePayrollBatch(ApprovePayrollBatchCommand(it.id, "تأیید نهایی دسته حقوق")) },
                        onAdjustment = { showAdjustment = true },
                        onLoadAdjustments = personnel::loadManualAdjustments,
                        onApproveAdjustment = { personnel.approveManualAdjustment(ApproveManualAdjustmentCommand(it)) },
                        onOpenPayslip = personnel::loadPayslipDetail,
                        onSelectEmployee = personnel::selectEmployee,
                        onClosePeriod = { personnel.closePayrollPeriod(ClosePayrollPeriodCommand(it.id, "بستن کنترل‌شده دوره حقوق")) },
                        onReopenPeriod = { personnel.reopenPayrollPeriod(ReopenPayrollPeriodCommand(it.id, "بازگشایی برای Revision حقوق")) },
                    )
                    HrWorkspaceSection.ADVANCES -> AdvancesCenter(personnelState) { employee ->
                        personnel.selectEmployee(employee.id)
                        showAdvance = true
                    }
                    HrWorkspaceSection.PERFORMANCE -> WorkforcePerformanceCenter(
                        personnelState,
                        performanceState,
                        workforceState,
                        onPolicy = { showLaborPolicy = true },
                        onAvailability = { showAvailability = true },
                        onSwap = { showShiftSwap = true },
                        onBreak = { breakTarget = it },
                        onReviewSwap = workforce::reviewShiftSwap,
                    )
                }
            }
        }
    }

    if (showEmployeeEditor) EmployeeDialog(editingEmployee, branches, { showEmployeeEditor = false }) { draft ->
        personnel.saveEmployee(editingEmployee?.id, draft)
        showEmployeeEditor = false
    }
    if (showAttendance) AttendanceDialog(personnelState, { showAttendance = false }) { draft ->
        personnel.saveAttendance(null, draft)
        showAttendance = false
    }
    correctionTarget?.let { attendance ->
        AttendanceCorrectionDialog(attendance, { correctionTarget = null }) { draft ->
            personnel.saveAttendance(attendance.id, draft)
            correctionTarget = null
        }
    }
    rejectCorrectionTarget?.let { correction ->
        AttendanceCorrectionRejectDialog(
            correction = correction,
            onDismiss = { rejectCorrectionTarget = null },
            onReject = { reason ->
                personnel.rejectAttendanceCorrection(correction.id, reason)
                rejectCorrectionTarget = null
            },
        )
    }
    if (showAttendanceSummary) AttendanceSummaryDialog(personnelState, { showAttendanceSummary = false }, personnel::loadAttendanceSummary)
    if (showShiftTemplate) ShiftTemplateDialog({ showShiftTemplate = false }) { draft ->
        personnel.saveShiftTemplate(null, draft)
        showShiftTemplate = false
    }
    if (showWorkSchedule) WorkScheduleDialog(personnelState, { showWorkSchedule = false }) { draft ->
        personnel.saveWorkSchedule(null, draft)
        showWorkSchedule = false
    }
    if (showPlannedShift) PlannedShiftDialog(personnelState, { showPlannedShift = false }) { draft ->
        personnel.savePlannedShift(null, draft)
        showPlannedShift = false
    }
    overtimeTarget?.let { approval ->
        OvertimeReviewDialog(
            approval = approval,
            employeeName = personnelState.employees.firstOrNull { it.id == approval.employeeId }?.displayName ?: "پرسنل #${approval.employeeId}",
            onDismiss = { overtimeTarget = null },
        ) { command ->
            personnel.reviewOvertime(command)
            overtimeTarget = null
        }
    }
    if (showLeave) LeaveDialog(personnelState, { showLeave = false }) { draft ->
        personnel.requestLeave(draft)
        showLeave = false
    }
    if (showAdvance) AdvanceDialog(personnelState, { showAdvance = false }, personnel::settleAdvance) { draft ->
        personnel.postAdvance(draft)
        showAdvance = false
    }
    if (showContract) ContractVersionDialog(
        personnelState,
        contractRevisionBase,
        { showContract = false },
    ) { draft ->
        personnel.saveContract(contractRevisionBase?.id, draft)
        showContract = false
    }
    if (showPayrollPolicy) PayrollPolicyDialog({ showPayrollPolicy = false }) { draft ->
        personnel.savePayrollPolicy(draft)
        showPayrollPolicy = false
    }
    if (showPeriod) PayrollPeriodDialog({ showPeriod = false }) { draft ->
        personnel.openPayrollPeriod(draft)
        showPeriod = false
    }
    if (showBatch) PayrollBatchDialog(hrState.periods, { showBatch = false }) { draft ->
        personnel.createPayrollBatch(draft)
        showBatch = false
    }
    calculateBatch?.let { batch ->
        CalculateBatchDialog(batch, personnelState, { calculateBatch = null }) { command ->
            personnel.calculatePayrollBatch(command)
            calculateBatch = null
        }
    }
    if (showAdjustment) ManualAdjustmentDialog(personnelState, hrState.periods, { showAdjustment = false }) { command ->
        personnel.submitManualAdjustment(command)
        showAdjustment = false
    }
    detailEmployee?.let { employee ->
        Employee360Dialog(
            employee = employee,
            personnelState = personnelState,
            hrState = hrState,
            performanceState = performanceState,
            onDismiss = { detailEmployee = null; personnel.selectEmployee(null) },
            onEdit = { editingEmployee = employee; showEmployeeEditor = true },
            onNewContract = { base -> contractRevisionBase = base; showContract = true },
            onApproveContract = personnel::approveContract,
            onAdvance = { showAdvance = true },
            onOpenPayslip = personnel::loadPayslipDetail,
            onSaveDocument = personnel::saveDocument,
            onArchiveDocument = personnel::archiveDocument,
            onDeactivate = { personnel.deactivate(employee.id) },
        )
    }
    hrState.payslipDetail?.let { detail ->
        PayslipDetailDialog(
            detail,
            onDismiss = personnel::clearPayslipDetail,
            onPay = { paymentTarget = detail.payslip },
            onReverse = { reversalTarget = detail.payslip },
            onReversePayment = { paymentReversalTarget = it },
        )
    }
    paymentTarget?.let { payslip ->
        PayslipPaymentDialog(payslip, treasuryState.accounts, { paymentTarget = null }) { command ->
            personnel.payPayslip(command)
            paymentTarget = null
        }
    }
    reversalTarget?.let { payslip ->
        PayslipReversalDialog(payslip, { reversalTarget = null }) { command ->
            personnel.reversePayslipV2(command)
            reversalTarget = null
        }
    }
    paymentReversalTarget?.let { paymentId ->
        PaymentReversalDialog(paymentId, { paymentReversalTarget = null }) { command ->
            personnel.reversePayrollPayment(command)
            paymentReversalTarget = null
        }
    }
    if (showLaborPolicy) LaborPolicyDialog({ showLaborPolicy = false }) { policy ->
        workforce.saveLaborPolicy(policy) { showLaborPolicy = false }
    }
    if (showAvailability) AvailabilityDialog(personnelState.employees, { showAvailability = false }) { draft ->
        workforce.saveAvailability(draft) { showAvailability = false }
    }
    if (showShiftSwap && workforceState.snapshot != null) ShiftSwapDialog(
        workforceState.snapshot.plannedShifts,
        personnelState.employees,
        { showShiftSwap = false },
    ) { draft -> workforce.requestShiftSwap(draft) { showShiftSwap = false } }
    breakTarget?.let { shift ->
        WorkBreakDialog(shift, { breakTarget = null }) { start, end ->
            workforce.recordWorkBreak(shift.shiftId, start, end) { breakTarget = null }
        }
    }
}

@Composable
private fun HrWorkspaceNavigation(selected: HrWorkspaceSection, onSelect: (HrWorkspaceSection) -> Unit) {
    Surface(tonalElevation = 2.dp) {
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("hr_workspace_navigation"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HrWorkspaceSection.entries, key = { it.name }) { item ->
                FilterChip(
                    selected = item == selected,
                    onClick = { onSelect(item) },
                    label = { Text(item.title) },
                    modifier = Modifier.testTag("hr_section_${item.name}"),
                )
            }
        }
    }
}

@Composable
private fun PeopleHome(
    state: PersonnelUiState,
    onOpen: (EmployeeRecord) -> Unit,
    onEdit: (EmployeeRecord) -> Unit,
    onNewEmployee: () -> Unit,
    onAttendance: () -> Unit,
    onLeave: () -> Unit,
    onScheduling: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("ACTIVE") }
    val departments = state.employees.map { it.department }.filter { it.isNotBlank() }.distinct().sorted()
    val jobs = state.employees.map { it.jobTitle }.filter { it.isNotBlank() }.distinct().sorted()
    val contractStatuses = state.employees.mapNotNull { it.contractStatus }.distinct().sortedBy { it.storedValue }
    var department by rememberSaveable { mutableStateOf<String?>(null) }
    var job by rememberSaveable { mutableStateOf<String?>(null) }
    var contractStatus by rememberSaveable { mutableStateOf<EmploymentContractStatus?>(null) }
    val rows = state.employees.filter { employee ->
        val searchable = listOf(employee.name, employee.displayName, employee.employeeCode, employee.phone, employee.jobTitle, employee.department)
            .joinToString(" ").lowercase()
        (query.isBlank() || query.trim().lowercase() in searchable) &&
            (status == "ALL" || employee.employmentStatus.storedValue == status) &&
            (department == null || employee.department == department) &&
            (job == null || employee.jobTitle == job) &&
            (contractStatus == null || employee.contractStatus == contractStatus)
    }
    val summary = personnelDashboardSummary(state, currentLocalEpochDay())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ErpDashboardHero(
                eyebrow = "پرسنل فعال",
                value = "${ErpDisplayFormatters.integer(summary.activeEmployees)} نفر",
                caption = "وضعیت امروز از حضور و مرخصی ثبت‌شده محاسبه می‌شود",
                metrics = listOf(
                    ErpKpiItem("حاضر", ErpDisplayFormatters.integer(summary.presentToday)),
                    ErpKpiItem("غایب", ErpDisplayFormatters.integer(summary.absentToday)),
                    ErpKpiItem("مرخصی", ErpDisplayFormatters.integer(summary.onLeaveToday)),
                ),
            )
        }
        item {
            SectionHeading("عملیات سریع", "دسترسی مستقیم به جریان‌های واقعی منابع انسانی")
            ErpQuickActionsGrid(
                listOf(
                    ErpActionItem("پرسنل جدید", Icons.Outlined.Badge, ErpPalette.IndigoSoft, ErpPalette.Indigo, onClick = onNewEmployee),
                    ErpActionItem("حضور و غیاب", Icons.Outlined.EventAvailable, ErpPalette.TealSoft, ErpPalette.Teal, onClick = onAttendance),
                    ErpActionItem("مرخصی", Icons.Outlined.CalendarMonth, ErpPalette.GreenSoft, ErpPalette.Green, onClick = onLeave),
                    ErpActionItem("شیفت", Icons.Outlined.Schedule, ErpPalette.AmberSoft, ErpPalette.Amber, onClick = onScheduling),
                ),
            )
        }
        if (summary.pendingLeaveCount > 0 || summary.lateTodayCount > 0) {
            item {
                SectionHeading("نیازمند توجه", "موارد ثبت‌شده‌ای که نیاز به بررسی دارند")
                if (summary.pendingLeaveCount > 0) ErpAttentionRow("مرخصی نیازمند بررسی", "${ErpDisplayFormatters.integer(summary.pendingLeaveCount)} درخواست در انتظار تصمیم است", onClick = onLeave)
                if (summary.lateTodayCount > 0) ErpAttentionRow("تأخیر امروز", "${ErpDisplayFormatters.integer(summary.lateTodayCount)} رکورد حضور دارای تأخیر است", onClick = onAttendance)
            }
        }
        item { SectionHeading("پرسنل", "جست‌وجو با نام، کد، تلفن، شغل، دپارتمان و وضعیت") }
        item { OutlinedTextField(query, { query = it }, label = { Text("نام، کد پرسنلی، تلفن یا شغل") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("ACTIVE" to "فعال", "TERMINATED" to "خاتمه‌یافته", "ON_LEAVE" to "مرخصی", "ALL" to "همه")) { item ->
                    FilterChip(status == item.first, { status = item.first }, { Text(item.second) })
                }
            }
        }
        if (departments.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(department == null, { department = null }, { Text("همه دپارتمان‌ها") }) }
                items(departments) { value -> FilterChip(department == value, { department = value }, { Text(value) }) }
            }
        }
        if (jobs.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(job == null, { job = null }, { Text("همه سمت‌ها") }) }
                items(jobs) { value -> FilterChip(job == value, { job = value }, { Text(value) }) }
            }
        }
        if (contractStatuses.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(contractStatus == null, { contractStatus = null }, { Text("همه وضعیت‌های قرارداد") }) }
                items(contractStatuses) { value ->
                    FilterChip(contractStatus == value, { contractStatus = value }, { Text(value.storedValue) })
                }
            }
        }
        if (rows.isEmpty()) item { EmptyStatePanel("پرسنلی یافت نشد", "فیلترها را تغییر دهید یا پروفایل جدید بسازید.") }
        items(rows, key = { it.id }) { employee ->
            Card(onClick = { onOpen(employee) }) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(employee.displayName, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${employee.employeeCode ?: "—"} · ${employee.jobTitle}", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(employee.employmentStatus.storedValue)
                    }
                    CompactInfoRow("دپارتمان / شعبه", "${employee.department.ifBlank { "—" }} / ${employee.branchName.ifBlank { "—" }}")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onEdit(employee) }) { Text("ویرایش") }
                        TextButton(onClick = { onOpen(employee) }) { Text("پرونده کامل") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCenter(
    state: PersonnelUiState,
    onSummary: () -> Unit,
    onCorrect: (AttendanceRecord) -> Unit,
    onPunch: (Long, AttendanceEventType) -> Unit,
    onApproveCorrection: (Long) -> Unit,
    onRejectCorrection: (AttendanceCorrectionRecord) -> Unit,
) {
    val activeEmployees = state.employees.filter { it.isActive }
    var punchEmployeeId by remember(activeEmployees) { mutableLongStateOf(activeEmployees.firstOrNull()?.id ?: 0L) }
    var punchEmployeeExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeading("حضور و غیاب", "رویدادهای تغییرناپذیر، نمای روزانه و اصلاحات با تأیید دو مرحله‌ای")
                TextButton(onClick = onSummary) { Text("گزارش بازه") }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پانچ واقعی", fontWeight = FontWeight.Bold)
                    Text("زمان ورود/خروج از ساعت سیستم ثبت می‌شود؛ برای اصلاح تاریخی از مسیر اصلاح روزانه استفاده کنید.", style = MaterialTheme.typography.bodySmall)
                    Box {
                        OutlinedButton(
                            onClick = { punchEmployeeExpanded = true },
                            modifier = Modifier.fillMaxWidth().testTag("attendance_punch_employee"),
                        ) { Text(activeEmployees.firstOrNull { it.id == punchEmployeeId }?.displayName ?: "انتخاب پرسنل") }
                        DropdownMenu(expanded = punchEmployeeExpanded, onDismissRequest = { punchEmployeeExpanded = false }) {
                            activeEmployees.forEach { employee ->
                                DropdownMenuItem(
                                    text = { Text(employee.displayName) },
                                    onClick = { punchEmployeeId = employee.id; punchEmployeeExpanded = false },
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (punchEmployeeId > 0) onPunch(punchEmployeeId, AttendanceEventType.CLOCK_IN) },
                            enabled = punchEmployeeId > 0,
                            modifier = Modifier.weight(1f).testTag("attendance_punch_in"),
                        ) { Text("ثبت ورود همین الان") }
                        OutlinedButton(
                            onClick = { if (punchEmployeeId > 0) onPunch(punchEmployeeId, AttendanceEventType.CLOCK_OUT) },
                            enabled = punchEmployeeId > 0,
                            modifier = Modifier.weight(1f).testTag("attendance_punch_out"),
                        ) { Text("ثبت خروج همین الان") }
                    }
                }
            }
        }
        if (state.pendingAttendanceCorrections.isNotEmpty()) {
            item { SectionHeading("اصلاحات حضور و غیاب", "اصلاح‌های منتظر بررسی؛ درخواست‌کننده نمی‌تواند اصلاح خودش را تأیید یا رد کند") }
            items(state.pendingAttendanceCorrections, key = { "correction_${it.id}" }) { correction ->
                val employee = state.employees.firstOrNull { it.id == correction.employeeId }
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(employee?.displayName ?: "پرسنل #${correction.employeeId}", fontWeight = FontWeight.Bold)
                            StatusPill(correction.status)
                        }
                        CompactInfoRow("تاریخ کسب‌وکار", epochDayToPersian(correction.businessEpochDay).display())
                        CompactInfoRow("دلیل", correction.reason)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRejectCorrection(correction) },
                                modifier = Modifier.weight(1f).testTag("attendance_correction_reject_${correction.id}"),
                            ) { Text("رد") }
                            Button(
                                onClick = { onApproveCorrection(correction.id) },
                                modifier = Modifier.weight(1f).testTag("attendance_correction_approve_${correction.id}"),
                            ) { Text("تأیید") }
                        }
                    }
                }
            }
        }
        if (state.attendance.isEmpty()) item { EmptyStatePanel("رکورد حضور وجود ندارد", "ورود/خروج واقعی یا اصلاح روزانه را ثبت کنید.") }
        items(state.attendance, key = { it.id }) { row ->
            val employee = state.employees.firstOrNull { it.id == row.employeeId }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(employee?.displayName ?: "پرسنل", fontWeight = FontWeight.Bold)
                        StatusPill(row.status)
                    }
                    CompactInfoRow("تاریخ کسب‌وکار", epochDayToPersian(row.workEpochDay).display())
                    CompactInfoRow("ورود / خروج", "${row.checkInMinute?.let(::formatMinuteOfDay) ?: "—"} / ${row.checkOutMinute?.let(::formatMinuteOfDay) ?: "—"}")
                    CompactInfoRow("کارکرد / تأخیر / اضافه‌کاری", "${row.workedMinutes} / ${row.lateMinutes} / ${row.overtimeMinutes} دقیقه")
                    OutlinedButton(onClick = { onCorrect(row) }, modifier = Modifier.fillMaxWidth()) { Text("درخواست اصلاح با ثبت قبل/بعد و دلیل") }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCorrectionRejectDialog(
    correction: AttendanceCorrectionRecord,
    onDismiss: () -> Unit,
    onReject: (String) -> Unit,
) {
    var reason by remember(correction.id) { mutableStateOf("") }
    var error by remember(correction.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رد اصلاح حضور") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رد باید دلیل قابل ممیزی داشته باشد.")
                error?.let { MessageCard(it, isError = true) }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("دلیل رد") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val normalized = reason.trim()
                if (normalized.length < 3) error = "دلیل رد حداقل ۳ نویسه باشد." else onReject(normalized)
            }) { Text("ثبت رد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun LeaveCenter(
    state: PersonnelUiState,
    onReview: (ir.restaurant.management.domain.personnel.LeaveReviewDraft) -> Unit,
    onCancel: (Long) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeading("Leave", "DRAFT / SUBMITTED / APPROVED / REJECTED / CANCELLED / TAKEN") }
        if (state.leaves.isEmpty()) item { EmptyStatePanel("درخواست مرخصی وجود ندارد", "مرخصی تأییدشده مستقیماً ورودی Payroll است.") }
        items(state.leaves, key = { it.id }) { leave ->
            val employee = state.employees.firstOrNull { it.id == leave.employeeId }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(employee?.displayName ?: "پرسنل", fontWeight = FontWeight.Bold)
                        StatusPill(leave.typedStatus.storedValue)
                    }
                    CompactInfoRow("نوع", leave.typedLeaveType.storedValue)
                    CompactInfoRow("بازه", "${epochDayToPersian(leave.startEpochDay).display()} تا ${epochDayToPersian(leave.endEpochDay).display()}")
                    if (leave.typedStatus == ir.restaurant.management.domain.personnel.LeaveStatus.SUBMITTED) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onReview(ir.restaurant.management.domain.personnel.LeaveReviewDraft(leave.id, "REJECT", "مدیر", "رد درخواست")) }, modifier = Modifier.weight(1f)) { Text("رد") }
                            Button(onClick = { onReview(ir.restaurant.management.domain.personnel.LeaveReviewDraft(leave.id, "APPROVE", "مدیر", "تأیید درخواست")) }, modifier = Modifier.weight(1f)) { Text("تأیید") }
                        }
                    }
                    if (leave.typedStatus in setOf(ir.restaurant.management.domain.personnel.LeaveStatus.DRAFT, ir.restaurant.management.domain.personnel.LeaveStatus.SUBMITTED)) {
                        TextButton(onClick = { onCancel(leave.id) }) { Text("لغو") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayrollCenter(
    personnelState: PersonnelUiState,
    hrState: HrPayrollUiState,
    onPolicy: () -> Unit,
    onCreateBatch: () -> Unit,
    onCalculate: (PayrollBatchRecordV2) -> Unit,
    onReview: (PayrollBatchRecordV2) -> Unit,
    onApprove: (PayrollBatchRecordV2) -> Unit,
    onAdjustment: () -> Unit,
    onLoadAdjustments: (Long) -> Unit,
    onApproveAdjustment: (Long) -> Unit,
    onOpenPayslip: (Long) -> Unit,
    onSelectEmployee: (Long?) -> Unit,
    onClosePeriod: (PayrollPeriodRecordV2) -> Unit,
    onReopenPeriod: (PayrollPeriodRecordV2) -> Unit,
) {
    val current = hrState.periods.firstOrNull { it.status !in setOf(PayrollPeriodStatus.CLOSED, PayrollPeriodStatus.LEGACY) }
    val summary = payrollDashboardSummary(hrState)
    LaunchedEffect(current?.id) { current?.let { onLoadAdjustments(it.id) } }
    LazyColumn(
        Modifier.fillMaxSize().testTag("payroll_center_list"),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ErpDashboardHero(
                eyebrow = summary.activePeriodKey?.let { "دوره حقوق $it" } ?: "حقوق و دستمزد",
                value = ErpDisplayFormatters.money(summary.netRial),
                caption = payrollStatusTitle(summary.activePeriodStatus),
                metrics = listOf(
                    ErpKpiItem("پرسنل", ErpDisplayFormatters.integer(summary.employeeCount)),
                    ErpKpiItem("کسورات", ErpDisplayFormatters.money(summary.deductionsRial)),
                    ErpKpiItem("مانده پرداخت", ErpDisplayFormatters.money(summary.remainingRial)),
                ),
            )
        }
        item {
            Text(
                "آماده‌سازی ← محاسبه ← بازبینی ← تأیید ← پرداخت ← بستن دوره",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPolicy, modifier = Modifier.weight(1f)) { Text("سیاست حقوق") }
                OutlinedButton(onClick = onAdjustment, enabled = current != null, modifier = Modifier.weight(1f)) { Text("تعدیل دستی") }
                Button(onClick = onCreateBatch, enabled = current != null, modifier = Modifier.weight(1f)) { Text("دسته حقوق جدید") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تاریخچه حقوق کارمند", fontWeight = FontWeight.Bold)
                LazyRow(
                    modifier = Modifier.testTag("payroll_employee_row"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        FilterChip(
                            selected = personnelState.selectedEmployeeId == null,
                            onClick = { onSelectEmployee(null) },
                            label = { Text("انتخاب نشده") },
                        )
                    }
                    items(personnelState.employees, key = { "payroll-employee-${it.id}" }) { employee ->
                        FilterChip(
                            selected = personnelState.selectedEmployeeId == employee.id,
                            onClick = { onSelectEmployee(employee.id) },
                            label = { Text("${employee.employeeCode ?: "—"} · ${employee.displayName}") },
                            modifier = Modifier.testTag("payroll_employee_${employee.id}"),
                        )
                    }
                }
            }
        }
        current?.let { period ->
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("دوره جاری ${period.periodKey}", fontWeight = FontWeight.Black)
                            StatusPill(period.status.storedValue)
                        }
                        CompactInfoRow("بازه", "${epochDayToPersian(period.startEpochDay).display()} تا ${epochDayToPersian(period.endEpochDay).display()}")
                        if (period.status == PayrollPeriodStatus.PAYMENT) OutlinedButton(onClick = { onClosePeriod(period) }, modifier = Modifier.fillMaxWidth()) { Text("بستن کنترل‌شده دوره") }
                    }
                }
            }
        }
        hrState.periods.firstOrNull { it.status == PayrollPeriodStatus.CLOSED }?.let { closed ->
            item { OutlinedButton(onClick = { onReopenPeriod(closed) }, modifier = Modifier.fillMaxWidth()) { Text("بازگشایی ${closed.periodKey} برای Revision") } }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("در انتظار تأیید", hrState.batches.count { it.status == PayrollBatchStatus.UNDER_REVIEW }.toString(), Modifier.weight(1f))
                MetricTile("در انتظار پرداخت", hrState.batches.count { it.status == PayrollBatchStatus.PAYMENT_PENDING }.toString(), Modifier.weight(1f))
                MetricTile("نیمه‌پرداخت", hrState.batches.count { it.status == PayrollBatchStatus.PARTIALLY_PAID }.toString(), Modifier.weight(1f))
            }
        }
        if (hrState.calculationExceptions.isNotEmpty()) {
            item { SectionHeading("موارد نیازمند اصلاح", "محاسبه تا رفع موارد مسدودکننده ادامه پیدا نمی‌کند") }
            items(hrState.calculationExceptions, key = { "${it.employeeId}-${it.code}-${it.detail}" }) { item ->
                MessageCard("${if (item.blocking) "مسدودکننده" else "هشدار"}: ${item.code} · ${item.detail}", item.blocking)
            }
        }
        if (hrState.manualAdjustments.isNotEmpty()) {
            item { SectionHeading("تعدیلات حقوق", "ثبت‌کننده و تأییدکننده باید متفاوت باشند") }
            items(hrState.manualAdjustments, key = { "adjustment-${it.id}" }) { adjustment ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${adjustment.componentType.storedValue} · ${formatMoney(adjustment.amountRial)}", fontWeight = FontWeight.Bold)
                        Text(adjustment.reason, style = MaterialTheme.typography.bodySmall)
                        StatusPill(adjustment.status.storedValue)
                        if (adjustment.status == ManualAdjustmentStatus.SUBMITTED) {
                            OutlinedButton(onClick = { onApproveAdjustment(adjustment.id) }, modifier = Modifier.fillMaxWidth()) { Text("تأیید توسط کاربر مستقل") }
                        }
                    }
                }
            }
        }
        item { SectionHeading("دسته‌های حقوق", "محاسبه، تأیید و پرداخت مراحل مستقل و قابل پیگیری هستند") }
        if (hrState.batches.isEmpty()) item { EmptyStatePanel("دسته حقوقی وجود ندارد", "ابتدا دوره و سپس Batch بسازید.") }
        items(hrState.batches, key = { "payroll-batch-${it.id}" }) { batch ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(batch.documentNumber, fontWeight = FontWeight.Black)
                        StatusPill(batch.status.storedValue)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("ناخالص", formatMoney(batch.grossPayrollRial), Modifier.weight(1f))
                        MetricTile("خالص", formatMoney(batch.netPayrollRial), Modifier.weight(1f))
                    }
                    CompactInfoRow("پرسنل / استثنا", "${batch.employeesIncluded} / ${batch.exceptionCount}", batch.exceptionCount > 0)
                    CompactInfoRow("پرداخت / مانده", "${formatMoney(batch.paidRial)} / ${formatMoney(batch.remainingRial)}", batch.remainingRial > 0)
                    when (batch.status) {
                        PayrollBatchStatus.DRAFT -> Button(onClick = { onCalculate(batch) }, modifier = Modifier.fillMaxWidth().testTag("payroll_calculate_${batch.id}")) { Text("محاسبه حقوق") }
                        PayrollBatchStatus.CALCULATED -> Button(onClick = { onReview(batch) }, modifier = Modifier.fillMaxWidth().testTag("payroll_review_${batch.id}")) { Text("ارسال برای بازبینی") }
                        PayrollBatchStatus.UNDER_REVIEW -> Button(onClick = { onApprove(batch) }, modifier = Modifier.fillMaxWidth().testTag("payroll_approve_${batch.id}")) { Text("تأیید نهایی") }
                        else -> Unit
                    }
                }
            }
        }
        if (hrState.employeePayslips.isNotEmpty()) {
            item { SectionHeading("تاریخچه حقوق کارمند منتخب", "ماه‌به‌ماه، Revision و مانده پرداخت") }
            items(hrState.employeePayslips, key = { "payroll-payslip-${it.id}" }) { payslip ->
                PayslipHistoryRow(payslip, hrState.periods.firstOrNull { it.id == payslip.periodId }, onOpenPayslip)
            }
        } else if (personnelState.selectedEmployeeId != null) {
            item { EmptyStatePanel("فیشی برای کارمند منتخب نیست", "سوابق قدیمی نیز بدون ساخت جزئیات غیرواقعی نمایش داده می‌شوند.") }
        }
    }
}

@Composable
internal fun PayslipHistoryRow(
    payslip: PayrollPayslipRecordV2,
    period: PayrollPeriodRecordV2?,
    onOpen: (Long) -> Unit,
) {
    Card(onClick = { onOpen(payslip.id) }, modifier = Modifier.testTag("payroll_payslip_${payslip.id}")) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${period?.periodKey ?: "دوره #${payslip.periodId}"} · نسخه ${payslip.revisionNo}", fontWeight = FontWeight.Bold)
                StatusPill(payslip.status.storedValue)
            }
            CompactInfoRow("ناخالص / کسورات / خالص", "${formatMoney(payslip.grossPay.value)} / ${formatMoney(payslip.totalDeductions.value)} / ${formatMoney(payslip.netPay.value)}")
            CompactInfoRow("پرداخت / مانده", "${formatMoney(payslip.paidAmount.value)} / ${formatMoney(payslip.remainingAmount.value)}", payslip.remainingAmount.value > 0)
            if (!payslip.componentDetailComplete) Text("داده تاریخی: جزئیات مؤلفه‌ها در منبع قبلی موجود نبوده و ساخته نشده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AdvancesCenter(state: PersonnelUiState, onOpen: (EmployeeRecord) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeading("مساعده‌ها", "اصل، مانده و تخصیص کسر حقوق به‌صورت مستقل") }
        if (state.openAdvances.isEmpty()) item { EmptyStatePanel("مساعده باز وجود ندارد", "کسر هر Payroll از دفتر تخصیص و مانده کنترل می‌شود.") }
        items(state.employees, key = { "advance-employee-${it.id}" }) { employee ->
            val advances = state.openAdvances.filter { it.employeeId == employee.id }
            if (advances.isNotEmpty()) Card(onClick = { onOpen(employee) }) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(employee.displayName, fontWeight = FontWeight.Bold)
                    CompactInfoRow("تعداد مساعده باز", advances.size.toString())
                    CompactInfoRow("مانده کل", formatMoney(advances.sumOf { it.remainingAmountRial }))
                }
            }
        }
    }
}

@Composable
private fun WorkforcePerformanceCenter(
    personnelState: PersonnelUiState,
    performanceState: PerformanceUiState,
    workforceState: ControlCenterUiState,
    onPolicy: () -> Unit,
    onAvailability: () -> Unit,
    onSwap: () -> Unit,
    onBreak: (ir.restaurant.management.domain.control.LaborShiftInput) -> Unit,
    onReviewSwap: (Long, Boolean) -> Unit,
) {
    val snapshot = workforceState.snapshot
    val laborAlerts = snapshot?.laborAlerts.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeading("Performance", "هدف‌ها و ارزیابی‌های دوره‌ای") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("هدف فعال", performanceState.goals.count { it.status == "ACTIVE" }.toString(), Modifier.weight(1f))
                MetricTile("ارزیابی", performanceState.reviews.size.toString(), Modifier.weight(1f))
                MetricTile("پرسنل", personnelState.employees.size.toString(), Modifier.weight(1f))
            }
        }
        item { SectionHeading("مدیریت نیروی انسانی", "شیفت، دسترس‌پذیری، تعویض و استراحت از همین بخش نیروی انسانی مدیریت می‌شوند") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAvailability, modifier = Modifier.weight(1f)) { Text("دسترس‌پذیری") }
                OutlinedButton(onClick = onSwap, modifier = Modifier.weight(1f)) { Text("تعویض شیفت") }
                OutlinedButton(onClick = onPolicy, modifier = Modifier.weight(1f)) { Text("سیاست کار") }
            }
        }
        if (laborAlerts.isEmpty()) item { EmptyStatePanel("مغایرت کاری وجود ندارد", "کنترل ساعات و استراحت از همین مرکز انجام می‌شود.") }
        else items(laborAlerts, key = { "${it.shiftId}-${it.message}" }) { alert ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(alert.employeeName, fontWeight = FontWeight.Bold)
                    Text(alert.message)
                    snapshot?.plannedShifts?.firstOrNull { it.shiftId == alert.shiftId }?.let { shift ->
                        OutlinedButton(onClick = { onBreak(shift) }, modifier = Modifier.fillMaxWidth()) { Text("ثبت استراحت شیفت") }
                    }
                }
            }
        }
        snapshot?.shiftSwaps?.let { swaps ->
            items(swaps, key = { "swap-${it.id}" }) { swap ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("تعویض شیفت · ${swap.requesterName}", fontWeight = FontWeight.Bold)
                        Text("${swap.note} · ${swap.status}")
                        if (swap.status == "PENDING") Row(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onReviewSwap(swap.id, false) }) { Text("رد") }
                            TextButton(onClick = { onReviewSwap(swap.id, true) }, enabled = swap.targetEmployeeId != null) { Text("تأیید") }
                        }
                    }
                }
            }
        }
    }
}
