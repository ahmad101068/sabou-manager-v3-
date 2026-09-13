package ir.restaurant.management.ui

import ir.restaurant.management.core.BusinessCalendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.control.ChecklistRunItemRecord
import ir.restaurant.management.domain.control.ChecklistRunRecord
import ir.restaurant.management.domain.control.ChecklistStatus
import ir.restaurant.management.domain.control.ChecklistTemplateDraft
import ir.restaurant.management.domain.control.ChecklistTemplateItemDraft
import ir.restaurant.management.domain.control.ChecklistTemplateRecord
import ir.restaurant.management.domain.control.ChecklistType
import ir.restaurant.management.domain.control.ManagementIssueRecord
import ir.restaurant.management.domain.control.ManagementIssueAssignmentDraft
import ir.restaurant.management.domain.control.ManagementIssueSeverity
import ir.restaurant.management.domain.control.ManagementIssueStatus
import ir.restaurant.management.domain.control.ManagementTaskDraft
import ir.restaurant.management.domain.control.ManagementTaskPriority
import ir.restaurant.management.domain.control.ManagementTaskRecord
import ir.restaurant.management.domain.control.ManagementTaskStatus
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun ManagementWorkflowRoute(
    screen: AppScreen,
    state: ManagementWorkflowUiState,
    issues: List<ManagementIssueRecord>,
    tasks: List<ManagementTaskRecord>,
    templates: List<ChecklistTemplateRecord>,
    runs: List<ChecklistRunRecord>,
    runItems: List<ChecklistRunItemRecord>,
    currentRunId: Long?,
    dailyBrief: DailyBriefUiState,
    branches: List<BranchRecord>,
    employees: List<EmployeeRecord>,
    currentUser: AppUserRecord?,
    workflow: ManagementWorkflowViewModel,
    navigateTopLevel: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    val activeBranches = branches.filter { it.isActive }
    LaunchedEffect(activeBranches, state.selectedBranchId) {
        if (state.selectedBranchId != null && state.selectedBranchId !in activeBranches.map { it.id }) workflow.selectBranch(null)
    }
    when (screen) {
        AppScreen.MANAGEMENT_ISSUES -> ManagementIssuesScreen(state, issues, activeBranches, tasks, employees, currentUser, workflow, navigateTopLevel, navigateBack)
        AppScreen.MANAGEMENT_TASKS -> ManagementTasksScreen(state, tasks, activeBranches, employees, currentUser, workflow, navigateTopLevel, navigateBack)
        AppScreen.CHECKLISTS -> ChecklistCenterScreen(state, templates, runs, runItems, currentRunId, activeBranches, employees, currentUser, workflow, navigateTopLevel, navigateBack)
        AppScreen.DAILY_BRIEF -> DailyBriefScreen(state, dailyBrief, activeBranches, workflow, navigateTopLevel, navigateBack)
        else -> error("management_workflow_route_mismatch:${screen.name}")
    }
}

@Composable
private fun WorkflowScaffold(
    title: String,
    subtitle: String,
    state: ManagementWorkflowUiState,
    branches: List<BranchRecord>,
    onBranch: (Long?) -> Unit,
    onBack: () -> Unit,
    onNavigateTopLevel: (AppScreen) -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { ProfessionalTopBar(title, subtitle, onBack) },
        bottomBar = { ErpBottomNavigation(AppScreen.CONTROL_HUB, onNavigateTopLevel) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CanonicalBranchSelector(branches, state.selectedBranchId, onBranch, tag = "management_branch_selector")
            state.error?.let { MessageCard(it, true) }
            state.message?.let { MessageCard(it) }
            if (state.busy) CircularProgressIndicator()
            actions()
            content()
        }
    }
}

@Composable
private fun ManagementIssuesScreen(
    state: ManagementWorkflowUiState,
    issues: List<ManagementIssueRecord>,
    branches: List<BranchRecord>,
    tasks: List<ManagementTaskRecord>,
    employees: List<EmployeeRecord>,
    currentUser: AppUserRecord?,
    workflow: ManagementWorkflowViewModel,
    navigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<ManagementIssueRecord?>(null) }
    WorkflowScaffold(
        "مسائل مدیریتی", "شواهد، اثر مالی و اقدام اصلاحی", state, branches, workflow::selectBranch, onBack, navigateTopLevel,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = workflow::refreshRules, enabled = state.selectedBranchId != null && currentUser?.role?.allows(Permission.CONTROL_VIEW) == true) { Text("ارزیابی ناهنجاری‌ها") }
                Text("باز: ${issues.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            }
        },
    ) {
        AdaptiveManagementList(
            rows = issues,
            columns = listOf(
                ManagementGridColumn("severity", "شدت", .7f, value = { severityTitle(it.severity) }),
                ManagementGridColumn("type", "نوع", 1.1f, value = { issueTypeTitle(it) }),
                ManagementGridColumn("title", "عنوان", 2f, value = { it.title }),
                ManagementGridColumn("source", "منبع", 1.1f, value = { "${it.sourceType} #${it.sourceId}" }),
                ManagementGridColumn("impact", "اثر مالی", 1f, { it.financialImpactRial?.let(::formatMoney) ?: "—" }, TextAlign.End),
                ManagementGridColumn("assignee", "مسئول", 1.1f, value = { issueAssignee(it, employees) }),
                ManagementGridColumn("due", "سررسید", 1f, value = { it.dueAtEpochMillis?.let(::formatEpochMillisShort) ?: "—" }),
                ManagementGridColumn("status", "وضعیت", 1f, value = { issueStatusTitle(it.status) }),
            ),
            key = { it.id },
            mobileTitle = { it.title },
            mobilePrimaryValue = { severityTitle(it.severity) },
            mobileSupporting = {
                listOf(
                    "نوع" to issueTypeTitle(it),
                    "منبع" to "${it.sourceType} #${it.sourceId}",
                    "اثر مالی" to (it.financialImpactRial?.let(::formatMoney) ?: "—"),
                    "مسئول" to issueAssignee(it, employees),
                    "سررسید" to (it.dueAtEpochMillis?.let(::formatEpochMillisShort) ?: "—"),
                )
            },
            mobileStatus = { issueStatusTitle(it.status) },
            rowState = { if (it.severity == ManagementIssueSeverity.CRITICAL) GridRowState.ERROR else if (it.severity == ManagementIssueSeverity.HIGH) GridRowState.WARNING else GridRowState.VIEW },
            emptyMessage = "مسئله مدیریتی بازی برای این شعبه وجود ندارد.",
            onRowClick = { selected = it },
        )
    }
    selected?.let { issue ->
        IssueDetailDialog(
            issue = issue,
            linkedTask = tasks.firstOrNull { it.sourceIssueId == issue.id },
            employees = employees,
            canAssign = currentUser?.role?.allows(Permission.CONTROL_ASSIGN) == true,
            canResolve = currentUser?.role?.allows(Permission.CONTROL_RESOLVE) == true,
            onDismiss = { selected = null },
            onAcknowledge = { workflow.acknowledgeIssue(issue.id) },
            onStart = { workflow.startIssue(issue.id) },
            onAssign = { employeeId, dueAtEpochMillis ->
                workflow.assignIssue(
                    ManagementIssueAssignmentDraft(
                        issueId = issue.id,
                        assignedEmployeeId = employeeId,
                        dueAtEpochMillis = dueAtEpochMillis,
                    ),
                )
            },
            onResolve = { note -> workflow.resolveIssue(issue.id, note); selected = null },
        )
    }
}

@Composable
private fun IssueDetailDialog(
    issue: ManagementIssueRecord,
    linkedTask: ManagementTaskRecord?,
    employees: List<EmployeeRecord>,
    canAssign: Boolean,
    canResolve: Boolean,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onStart: () -> Unit,
    onAssign: (Long, Long?) -> Unit,
    onResolve: (String) -> Unit,
) {
    var resolution by remember(issue.id) { mutableStateOf("") }
    var showAssignment by remember(issue.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(issue.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("چرا شناسایی شد", issue.description)
                DetailLine("منبع شواهد", "${issue.sourceType} #${issue.sourceId}")
                DetailLine("اثر مالی", issue.financialImpactRial?.let(::formatMoney) ?: "نامشخص")
                DetailLine("مسئول", issueAssignee(issue, employees))
                DetailLine("سررسید", issue.dueAtEpochMillis?.let(::formatEpochMillisShort) ?: "تعیین نشده")
                DetailLine("اقدام پیشنهادی", issueRecommendation(issue))
                DetailLine("وظیفه مرتبط", linkedTask?.let { "${it.title} · ${taskStatusTitle(it.status)}" } ?: "ایجاد نشده")
                DetailLine("وضعیت فعلی", issueStatusTitle(issue.status))
                Text("تاریخچه کامل تغییرات از Audit Log قابل ممیزی است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canResolve) OutlinedTextField(resolution, { resolution = it.take(500) }, label = { Text("یادداشت حل مسئله") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canAssign && issue.status == ManagementIssueStatus.NEW) TextButton(onClick = onAcknowledge) { Text("مشاهده شد") }
                if (canAssign && issue.status in setOf(ManagementIssueStatus.NEW, ManagementIssueStatus.ACKNOWLEDGED)) {
                    TextButton(onClick = { showAssignment = true }) { Text("ارجاع") }
                }
                if (canAssign && issue.status in setOf(ManagementIssueStatus.ACKNOWLEDGED, ManagementIssueStatus.ASSIGNED)) TextButton(onClick = onStart) { Text("شروع رسیدگی") }
                if (canResolve && issue.status == ManagementIssueStatus.IN_PROGRESS) Button(onClick = { if (resolution.isNotBlank()) onResolve(resolution) }) { Text("حل") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
    if (showAssignment) {
        IssueAssignmentDialog(
            employees = employees,
            onDismiss = { showAssignment = false },
            onAssign = { employeeId, dueAt ->
                onAssign(employeeId, dueAt)
                showAssignment = false
            },
        )
    }
}

@Composable
private fun IssueAssignmentDialog(
    employees: List<EmployeeRecord>,
    onDismiss: () -> Unit,
    onAssign: (Long, Long?) -> Unit,
) {
    var employeeId by remember(employees) { mutableLongStateOf(employees.firstOrNull()?.id ?: 0L) }
    var dueDay by remember { mutableStateOf<Long?>(currentEpochDay() + 1L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ارجاع مسئله") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField(
                    "مسئول",
                    employees.firstOrNull { it.id == employeeId }?.name,
                    employees.map { it.id to it.name },
                ) { employeeId = it }
                OptionalPersianDateField("سررسید", dueDay, onSelected = { dueDay = it })
            }
        },
        confirmButton = {
            Button(
                enabled = employeeId > 0L,
                onClick = {
                    onAssign(employeeId, dueDay?.let(BusinessCalendar::startOfDayEpochMillis))
                },
            ) { Text("ثبت ارجاع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ManagementTasksScreen(
    state: ManagementWorkflowUiState,
    tasks: List<ManagementTaskRecord>,
    branches: List<BranchRecord>,
    employees: List<EmployeeRecord>,
    currentUser: AppUserRecord?,
    workflow: ManagementWorkflowViewModel,
    navigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ManagementTaskRecord?>(null) }
    WorkflowScaffold(
        "وظایف مدیریتی", "اقدام، مسئول، سررسید و چرخه تأیید", state, branches, workflow::selectBranch, onBack, navigateTopLevel,
        actions = {
            if (currentUser?.role?.allows(Permission.TASK_CREATE) == true) Button(onClick = { showCreate = true }, enabled = state.selectedBranchId != null) { Text("ایجاد وظیفه") }
        },
    ) {
        AdaptiveManagementList(
            rows = tasks,
            columns = listOf(
                ManagementGridColumn("priority", "اولویت", .8f, value = { priorityTitle(it.priority) }),
                ManagementGridColumn("title", "عنوان", 2f, value = { it.title }),
                ManagementGridColumn("assignee", "مسئول", 1.2f, value = { taskAssignee(it, employees) }),
                ManagementGridColumn("due", "سررسید", 1f, value = { it.dueAtEpochMillis?.let(::formatEpochMillisShort) ?: "—" }),
                ManagementGridColumn("status", "وضعیت", 1.1f, value = { taskStatusTitle(it.status) }),
            ),
            key = { it.id },
            mobileTitle = { it.title },
            mobilePrimaryValue = { priorityTitle(it.priority) },
            mobileSupporting = { listOf("مسئول" to taskAssignee(it, employees), "سررسید" to (it.dueAtEpochMillis?.let(::formatEpochMillisShort) ?: "—")) },
            mobileStatus = { taskStatusTitle(it.status) },
            rowState = { if (it.priority == ManagementTaskPriority.CRITICAL) GridRowState.ERROR else if (isOverdue(it)) GridRowState.WARNING else GridRowState.VIEW },
            emptyMessage = "وظیفه بازی برای این شعبه وجود ندارد.",
            onRowClick = { selected = it },
        )
    }
    if (showCreate && state.selectedBranchId != null) TaskCreateDialog(state.selectedBranchId, employees, { showCreate = false }) { workflow.createTask(it); showCreate = false }
    selected?.let { task -> TaskLifecycleDialog(task, currentUser, employees, { selected = null }, workflow) }
}

@Composable
private fun TaskCreateDialog(branchId: Long, employees: List<EmployeeRecord>, onDismiss: () -> Unit, onSave: (ManagementTaskDraft) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(ManagementTaskPriority.NORMAL) }
    var employeeId by remember { mutableStateOf<Long?>(null) }
    var dueDay by remember { mutableLongStateOf(currentEpochDay() + 1) }
    var requiresApproval by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وظیفه جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(600) }, label = { Text("شرح اقدام") }, modifier = Modifier.fillMaxWidth())
                SelectionField("اولویت", priorityTitle(priority), ManagementTaskPriority.entries.mapIndexed { i, p -> i.toLong() to priorityTitle(p) }) { priority = ManagementTaskPriority.entries[it.toInt()] }
                SelectionField("مسئول", employeeId?.let { id -> employees.firstOrNull { it.id == id }?.name } ?: "بدون مسئول", listOf(0L to "بدون مسئول") + employees.map { it.id to it.name }) { employeeId = it.takeIf { id -> id > 0 } }
                PersianDateField("سررسید", dueDay) { dueDay = it }
                SelectionField("تأیید مدیر", if (requiresApproval) "لازم است" else "لازم نیست", listOf(0L to "لازم نیست", 1L to "لازم است")) { requiresApproval = it == 1L }
            }
        },
        confirmButton = { Button(onClick = { if (title.isNotBlank()) onSave(ManagementTaskDraft(branchId, title.trim(), description.trim(), priority, assignedEmployeeId = employeeId, dueAtEpochMillis = BusinessCalendar.startOfDayEpochMillis(dueDay), requiresApproval = requiresApproval)) }) { Text("ایجاد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun TaskLifecycleDialog(task: ManagementTaskRecord, currentUser: AppUserRecord?, employees: List<EmployeeRecord>, onDismiss: () -> Unit, workflow: ManagementWorkflowViewModel) {
    val canComplete = currentUser?.role?.allows(Permission.TASK_COMPLETE) == true
    val canApprove = currentUser?.role?.allows(Permission.TASK_APPROVE) == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(task.description); DetailLine("مسئول", taskAssignee(task, employees)); DetailLine("اولویت", priorityTitle(task.priority)); DetailLine("وضعیت", taskStatusTitle(task.status)); DetailLine("نیازمند تأیید", if (task.requiresApproval) "بله" else "خیر") } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canComplete && task.status == ManagementTaskStatus.TODO) TextButton(onClick = { workflow.startTask(task.id); onDismiss() }) { Text("شروع") }
                if (canComplete && task.status == ManagementTaskStatus.IN_PROGRESS) Button(onClick = { workflow.completeTask(task.id); onDismiss() }) { Text("تکمیل") }
                if (canApprove && task.status == ManagementTaskStatus.WAITING_APPROVAL) {
                    TextButton(onClick = { workflow.rejectTask(task.id); onDismiss() }) { Text("رد") }
                    Button(onClick = { workflow.approveTask(task.id); onDismiss() }) { Text("تأیید") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun ChecklistCenterScreen(
    state: ManagementWorkflowUiState,
    templates: List<ChecklistTemplateRecord>,
    runs: List<ChecklistRunRecord>,
    runItems: List<ChecklistRunItemRecord>,
    currentRunId: Long?,
    branches: List<BranchRecord>,
    employees: List<EmployeeRecord>,
    currentUser: AppUserRecord?,
    workflow: ManagementWorkflowViewModel,
    navigateTopLevel: (AppScreen) -> Unit,
    onBack: () -> Unit,
) {
    var showTemplate by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    WorkflowScaffold(
        "چک‌لیست‌ها", "قالب، اجرا، Pass/Fail و تأیید", state, branches, workflow::selectBranch, onBack, navigateTopLevel,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentUser?.role?.allows(Permission.CHECKLIST_MANAGE) == true) OutlinedButton(onClick = { showTemplate = true }) { Text("قالب جدید") }
                if (currentUser?.role?.allows(Permission.CHECKLIST_PERFORM) == true) Button(onClick = { showStart = true }, enabled = templates.isNotEmpty()) { Text("شروع اجرا") }
            }
        },
    ) {
        AdaptiveManagementList(
            rows = runs,
            columns = listOf(
                ManagementGridColumn("date", "تاریخ", 1f, value = { epochDayToPersian(it.businessEpochDay).display() }),
                ManagementGridColumn("template", "قالب", 1.8f, value = { run -> templates.firstOrNull { it.id == run.templateId }?.name ?: "#${run.templateId}" }),
                ManagementGridColumn("assignee", "مسئول", 1.2f, value = { run -> run.assignedEmployeeId?.let { id -> employees.firstOrNull { it.id == id }?.name } ?: "—" }),
                ManagementGridColumn("status", "وضعیت", 1.1f, value = { checklistStatusTitle(it.status) }),
            ),
            key = { it.id },
            mobileTitle = { run -> templates.firstOrNull { it.id == run.templateId }?.name ?: "چک‌لیست #${run.id}" },
            mobilePrimaryValue = { checklistStatusTitle(it.status) },
            mobileSupporting = { run -> listOf("تاریخ" to epochDayToPersian(run.businessEpochDay).display(), "مسئول" to (run.assignedEmployeeId?.let { id -> employees.firstOrNull { it.id == id }?.name } ?: "—")) },
            mobileStatus = { checklistStatusTitle(it.status) },
            rowState = { if (it.status == ChecklistStatus.FAILED) GridRowState.ERROR else if (it.status == ChecklistStatus.WAITING_APPROVAL) GridRowState.WARNING else GridRowState.VIEW },
            emptyMessage = "اجرای چک‌لیستی برای این شعبه ثبت نشده است.",
            onRowClick = { workflow.openChecklistRun(it.id) },
        )
    }
    if (showTemplate) ChecklistTemplateDialog(state.selectedBranchId, { showTemplate = false }) { workflow.createChecklistTemplate(it); showTemplate = false }
    if (showStart) ChecklistStartDialog(templates, employees, { showStart = false }) { templateId, employeeId -> workflow.startChecklistRun(templateId, employeeId); showStart = false }
    currentRunId?.let { runId ->
        val run = runs.firstOrNull { it.id == runId }
        ChecklistRunDialog(runId, run, runItems, currentUser, { workflow.closeChecklistRun() }, workflow)
    }
}

@Composable
private fun ChecklistTemplateDialog(branchId: Long?, onDismiss: () -> Unit, onSave: (ChecklistTemplateDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ChecklistType.CUSTOM) }
    var items by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قالب چک‌لیست") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it.take(120) }, label = { Text("نام قالب") }); SelectionField("نوع", type.name, ChecklistType.entries.mapIndexed { i, v -> i.toLong() to v.name }) { type = ChecklistType.entries[it.toInt()] }; OutlinedTextField(items, { items = it.take(1200) }, label = { Text("آیتم‌ها؛ هر خط یک مورد الزامی") }, minLines = 5, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { val parsed = items.lines().map(String::trim).filter(String::isNotBlank).map { ChecklistTemplateItemDraft(it, required = true) }; if (name.isNotBlank() && parsed.isNotEmpty()) onSave(ChecklistTemplateDraft(branchId, name.trim(), type, parsed)) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ChecklistStartDialog(templates: List<ChecklistTemplateRecord>, employees: List<EmployeeRecord>, onDismiss: () -> Unit, onStart: (Long, Long?) -> Unit) {
    var templateId by remember { mutableLongStateOf(templates.firstOrNull()?.id ?: 0L) }
    var employeeId by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("شروع چک‌لیست") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SelectionField("قالب", templates.firstOrNull { it.id == templateId }?.name, templates.map { it.id to it.name }) { templateId = it }; SelectionField("مسئول", employeeId?.let { id -> employees.firstOrNull { it.id == id }?.name } ?: "بدون مسئول", listOf(0L to "بدون مسئول") + employees.map { it.id to it.name }) { employeeId = it.takeIf { id -> id > 0 } } } },
        confirmButton = { Button(onClick = { if (templateId > 0) onStart(templateId, employeeId) }) { Text("شروع") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ChecklistRunDialog(runId: Long, run: ChecklistRunRecord?, items: List<ChecklistRunItemRecord>, currentUser: AppUserRecord?, onDismiss: () -> Unit, workflow: ManagementWorkflowViewModel) {
    var itemTarget by remember { mutableStateOf<ChecklistRunItemRecord?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اجرای چک‌لیست #$runId") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                run?.let { DetailLine("وضعیت", checklistStatusTitle(it.status)) }
                items.forEach { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), onClick = { if (currentUser?.role?.allows(Permission.CHECKLIST_PERFORM) == true && item.status == "NOT_STARTED") itemTarget = item }) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) { Text(item.title, fontWeight = FontWeight.Bold); Text(if (item.required) "الزامی · ${item.status}" else item.status, style = MaterialTheme.typography.bodySmall); item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (run?.status == ChecklistStatus.IN_PROGRESS && currentUser?.role?.allows(Permission.CHECKLIST_PERFORM) == true) Button(onClick = { workflow.completeChecklistRun(runId) }) { Text("پایان اجرا") }
                if (run?.status == ChecklistStatus.WAITING_APPROVAL && currentUser?.role?.allows(Permission.CHECKLIST_APPROVE) == true) Button(onClick = { workflow.approveChecklistRun(runId) }) { Text("تأیید") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
    itemTarget?.let { item -> ChecklistItemDialog(item, { itemTarget = null }) { passed, note, attachment -> workflow.completeChecklistItem(item.id, passed, note, attachment); itemTarget = null } }
}

@Composable
private fun ChecklistItemDialog(item: ChecklistRunItemRecord, onDismiss: () -> Unit, onSave: (Boolean, String?, String?) -> Unit) {
    var passed by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SelectionField("نتیجه", if (passed) "قبول" else "رد", listOf(1L to "قبول", 0L to "رد")) { passed = it == 1L }; OutlinedTextField(note, { note = it.take(500) }, label = { Text(if (!passed && item.requiresNoteOnFailure) "یادداشت علت (الزامی)" else "یادداشت") }, modifier = Modifier.fillMaxWidth()); if (item.requiresPhoto) OutlinedTextField(attachment, { attachment = it.take(500) }, label = { Text("مرجع پیوست/عکس (الزامی)") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { val noteOk = passed || !item.requiresNoteOnFailure || note.isNotBlank(); val attachmentOk = !item.requiresPhoto || attachment.isNotBlank(); if (noteOk && attachmentOk) onSave(passed, note.ifBlank { null }, attachment.ifBlank { null }) }) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun DailyBriefScreen(state: ManagementWorkflowUiState, briefState: DailyBriefUiState, branches: List<BranchRecord>, workflow: ManagementWorkflowViewModel, navigateTopLevel: (AppScreen) -> Unit, onBack: () -> Unit) {
    WorkflowScaffold(
        "گزارش روزانه مدیریت", "فروش، نقدینگی، هزینه مواد غذایی و سیگنال‌های کنترلی", state, branches, workflow::selectBranch, onBack, navigateTopLevel,
        actions = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PersianDateField("تاریخ", state.businessEpochDay, workflow::setDay); OutlinedButton(onClick = workflow::refreshBrief) { Text("بازخوانی") } } },
    ) {
        when {
            briefState.loading -> CircularProgressIndicator()
            briefState.error != null -> MessageCard(briefState.error, true)
            briefState.brief == null -> HubEmptyStateForWorkflow("برای نمایش گزارش، شعبه را انتخاب کنید.")
            briefState.readModel == null -> HubEmptyStateForWorkflow("داده کافی موجود نیست.")
            else -> {
                val brief = briefState.brief
                val readModel = briefState.readModel
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("daily_management_brief"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BriefMetricCard("درآمد", formatMoney(readModel.revenueRial))
                    BriefMetricCard("وصول مطالبات", formatMoney(readModel.collectionsRial))
                    BriefMetricCard("مطالبات جدید", formatMoney(readModel.newReceivablesRial))
                    BriefMetricCard("مانده مطالبات", formatMoney(readModel.outstandingReceivablesRial))
                    BriefMetricCard("بهای تمام‌شده کالای فروش‌رفته", readModel.cogsRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("سود ناخالص", readModel.grossProfitRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("درصد هزینه مواد غذایی", readModel.foodCostBasisPoints?.let(::formatPercentBasisPoints) ?: unavailableMetricValue())
                    BriefMetricCard("هزینه‌های عملیاتی", readModel.operatingExpensesRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("حقوق و دستمزد", readModel.payrollRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("سود عملیاتی برآوردی", readModel.estimatedOperatingProfitRial?.let(::formatMoney) ?: unavailableMetricValue(readModel.unavailableReason))
                    BriefMetricCard("هزینه ضایعات", readModel.wasteCostRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("مغایرت نقدی", readModel.cashVarianceRial?.let(::formatMoney) ?: unavailableMetricValue())
                    BriefMetricCard("مسائل بحرانی", toPersianDigits(readModel.criticalIssues.toString()))
                    BriefMetricCard("مسائل باز", toPersianDigits(readModel.openIssues.toString()))
                    BriefMetricCard("وظایف معوق", toPersianDigits(readModel.overdueTasks.toString()))
                    BriefMetricCard("موارد ناموفق چک‌لیست", toPersianDigits(readModel.failedChecklists.toString()))
                    if (brief.importantEvents.isNotEmpty()) BriefListCard("رویدادهای مهم", brief.importantEvents)
                    if (brief.recommendations.isNotEmpty()) BriefListCard("اقدام‌های پیشنهادی", brief.recommendations)
                }
            }
        }
    }
}

@Composable private fun DetailLine(label: String, value: String) { Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) } }
@Composable private fun BriefMetricCard(label: String, value: String) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(value, fontWeight = FontWeight.ExtraBold) } } }
@Composable private fun BriefListCard(title: String, items: List<String>) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontWeight = FontWeight.Black); items.forEach { Text("• $it") } } } }
@Composable private fun HubEmptyStateForWorkflow(message: String) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Text(message, Modifier.fillMaxWidth().padding(16.dp)) } }

private fun severityTitle(value: ManagementIssueSeverity) = when (value) { ManagementIssueSeverity.CRITICAL -> "بحرانی"; ManagementIssueSeverity.HIGH -> "بالا"; ManagementIssueSeverity.MEDIUM -> "متوسط"; ManagementIssueSeverity.LOW -> "کم"; ManagementIssueSeverity.INFO -> "اطلاع" }
private fun issueStatusTitle(value: ManagementIssueStatus) = when (value) { ManagementIssueStatus.NEW -> "جدید"; ManagementIssueStatus.ACKNOWLEDGED -> "مشاهده‌شده"; ManagementIssueStatus.ASSIGNED -> "ارجاع‌شده"; ManagementIssueStatus.IN_PROGRESS -> "در حال رسیدگی"; ManagementIssueStatus.WAITING_APPROVAL -> "در انتظار تأیید"; ManagementIssueStatus.RESOLVED -> "حل‌شده"; ManagementIssueStatus.DISMISSED -> "مختومه" }
private fun issueTypeTitle(issue: ManagementIssueRecord) = when (issue.type) {
    ir.restaurant.management.domain.control.ManagementIssueType.LOW_STOCK -> "کمبود موجودی"
    ir.restaurant.management.domain.control.ManagementIssueType.INVENTORY_DISCREPANCY -> "مغایرت موجودی"
    ir.restaurant.management.domain.control.ManagementIssueType.ABNORMAL_INVENTORY_USAGE -> "مصرف غیرعادی موجودی"
    ir.restaurant.management.domain.control.ManagementIssueType.WASTE_SPIKE -> "افزایش غیرعادی ضایعات"
    ir.restaurant.management.domain.control.ManagementIssueType.FOOD_COST_VARIANCE -> "مغایرت هزینه مواد غذایی"
    ir.restaurant.management.domain.control.ManagementIssueType.PURCHASE_PRICE_SPIKE -> "افزایش غیرعادی قیمت خرید"
    ir.restaurant.management.domain.control.ManagementIssueType.PURCHASE_DELIVERY_OVERDUE -> "تأخیر در تحویل خرید"
    ir.restaurant.management.domain.control.ManagementIssueType.CASH_VARIANCE -> "مغایرت صندوق"
    ir.restaurant.management.domain.control.ManagementIssueType.CARD_SETTLEMENT_VARIANCE -> "مغایرت تسویه کارتخوان"
    ir.restaurant.management.domain.control.ManagementIssueType.OVERDUE_RECEIVABLE -> "مطالبه سررسیدگذشته"
    ir.restaurant.management.domain.control.ManagementIssueType.UNALLOCATED_RECEIVABLE -> "دریافت تخصیص‌نیافته"
    ir.restaurant.management.domain.control.ManagementIssueType.CREDIT_LIMIT_EXCEEDED -> "عبور از سقف اعتبار"
    ir.restaurant.management.domain.control.ManagementIssueType.BUDGET_OVERRUN -> "عبور از بودجه"
    ir.restaurant.management.domain.control.ManagementIssueType.ATTENDANCE_ANOMALY -> "ناهنجاری حضور و غیاب"
    ir.restaurant.management.domain.control.ManagementIssueType.TASK_OVERDUE -> "وظیفه معوق"
    ir.restaurant.management.domain.control.ManagementIssueType.CHECKLIST_FAILED -> "چک‌لیست ناموفق"
}
private fun issueRecommendation(issue: ManagementIssueRecord) = when (issue.type) {
    ir.restaurant.management.domain.control.ManagementIssueType.LOW_STOCK -> "موجودی و نقطه سفارش بازبینی و اقدام تأمین ثبت شود."
    ir.restaurant.management.domain.control.ManagementIssueType.WASTE_SPIKE -> "علت ضایعات با ثبت انبار و شیفت مربوط تطبیق داده شود."
    ir.restaurant.management.domain.control.ManagementIssueType.FOOD_COST_VARIANCE, ir.restaurant.management.domain.control.ManagementIssueType.ABNORMAL_INVENTORY_USAGE -> "مصرف واقعی، رسپی و شمارش فیزیکی بررسی شود."
    ir.restaurant.management.domain.control.ManagementIssueType.CASH_VARIANCE, ir.restaurant.management.domain.control.ManagementIssueType.CARD_SETTLEMENT_VARIANCE -> "اسناد فروش و تسویه صندوق/کارتخوان تطبیق داده شود."
    ir.restaurant.management.domain.control.ManagementIssueType.OVERDUE_RECEIVABLE -> "مطالبه سررسید گذشته با طرف‌حساب پیگیری شود."
    ir.restaurant.management.domain.control.ManagementIssueType.PURCHASE_PRICE_SPIKE -> "قیمت خرید با سوابق و تأمین‌کنندگان جایگزین مقایسه شود."
    ir.restaurant.management.domain.control.ManagementIssueType.TASK_OVERDUE -> "مسئول و سررسید وظیفه بازبینی شود."
    ir.restaurant.management.domain.control.ManagementIssueType.CHECKLIST_FAILED -> "آیتم ناموفق اصلاح و اجرای کنترل مجدد ثبت شود."
    else -> "شواهد منبع بررسی و اقدام اصلاحی متناسب ثبت شود."
}
private fun issueAssignee(issue: ManagementIssueRecord, employees: List<EmployeeRecord>): String =
    issue.assignedEmployeeId?.let { id -> employees.firstOrNull { it.id == id }?.name ?: "پرسنل #$id" }
        ?: issue.assignedUserId?.let { "کاربر #$it" }
        ?: "بدون مسئول"

private fun unavailableMetricValue(reason: String? = null): String =
    reason?.takeIf(String::isNotBlank)?.let { "— · $it" } ?: "— · داده کافی موجود نیست"
private fun priorityTitle(value: ManagementTaskPriority) = when (value) { ManagementTaskPriority.LOW -> "کم"; ManagementTaskPriority.NORMAL -> "عادی"; ManagementTaskPriority.HIGH -> "بالا"; ManagementTaskPriority.CRITICAL -> "بحرانی" }
private fun taskStatusTitle(value: ManagementTaskStatus) = when (value) { ManagementTaskStatus.TODO -> "برای انجام"; ManagementTaskStatus.IN_PROGRESS -> "در حال انجام"; ManagementTaskStatus.WAITING_APPROVAL -> "در انتظار تأیید"; ManagementTaskStatus.COMPLETED -> "تکمیل"; ManagementTaskStatus.REJECTED -> "رد"; ManagementTaskStatus.CANCELLED -> "لغو" }
private fun checklistStatusTitle(value: ChecklistStatus) = when (value) { ChecklistStatus.NOT_STARTED -> "شروع نشده"; ChecklistStatus.IN_PROGRESS -> "در حال اجرا"; ChecklistStatus.WAITING_APPROVAL -> "در انتظار تأیید"; ChecklistStatus.COMPLETED -> "تکمیل"; ChecklistStatus.FAILED -> "ناموفق" }
private fun taskAssignee(task: ManagementTaskRecord, employees: List<EmployeeRecord>): String = task.assignedEmployeeId?.let { id -> employees.firstOrNull { it.id == id }?.name } ?: task.assignedUserId?.let { "کاربر #$it" } ?: "بدون مسئول"
private fun isOverdue(task: ManagementTaskRecord): Boolean = task.dueAtEpochMillis?.let { it < System.currentTimeMillis() } == true && task.status !in setOf(ManagementTaskStatus.COMPLETED, ManagementTaskStatus.CANCELLED)
private fun formatEpochMillisShort(value: Long): String = epochDayToPersian(BusinessCalendar.epochDayAt(value)).display()
