package ir.restaurant.management.ui

import ir.restaurant.management.domain.operations.AlertTarget
import ir.restaurant.management.domain.search.GlobalSearchTarget
import androidx.compose.runtime.Composable
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun ManagementRoutes(
    screen: AppScreen,
    organizationName: String,
    dashboardState: DashboardSnapshot,
    operationsState: OperationsUiState,
    operations: OperationsViewModel,
    inventory: InventoryWorkspaceViewModel,
    accountingState: AccountingUiState,
    accounting: AccountingViewModel,
    globalSearchState: GlobalSearchUiState,
    globalSearch: GlobalSearchViewModel,
    crm: CrmViewModel,
    salesState: DailySalesUiState,
    sales: DailySalesViewModel,
    personnelState: PersonnelUiState,
    assetState: AssetUiState,
    managementState: ControlCenterUiState,
    management: ManagementControlViewModel,
    workflowState: ManagementWorkflowUiState,
    workflowIssues: List<ir.restaurant.management.domain.control.ManagementIssueRecord>,
    workflowTasks: List<ir.restaurant.management.domain.control.ManagementTaskRecord>,
    checklistTemplates: List<ir.restaurant.management.domain.control.ChecklistTemplateRecord>,
    checklistRuns: List<ir.restaurant.management.domain.control.ChecklistRunRecord>,
    checklistRunItems: List<ir.restaurant.management.domain.control.ChecklistRunItemRecord>,
    checklistCurrentRunId: Long?,
    dailyBriefState: DailyBriefUiState,
    workflow: ManagementWorkflowViewModel,
    branches: List<ir.restaurant.management.domain.branch.BranchRecord>,
    alertState: AlertUiState,
    alerts: AlertViewModel,
    currentUser: AppUserRecord?,
    users: List<AppUserRecord>,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestEmployeeProfile: (Long?) -> Unit,
    navigate: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    when (screen) {
        AppScreen.AUDIT_LOG -> AuditLogScreen(
            state = operationsState,
            users = users,
            canSensitiveView = currentUser?.role?.allows(Permission.AUDIT_SENSITIVE_VIEW) == true,
            onSearch = operations::setAuditSearch,
            onActor = operations::setAuditActor,
            onAction = operations::setAuditAction,
            onEntity = operations::setAuditEntity,
            onEntityId = operations::setAuditEntityId,
            onSourceReference = operations::setAuditSourceReference,
            onSeverity = operations::setAuditSeverity,
            onDateRange = operations::setAuditDateRange,
            onClearFilters = operations::clearAuditFilters,
            onBack = navigateBack,
        )

        AppScreen.MANAGEMENT_CONTROL -> ManagementControlScreen(
            state = managementState,
            employees = personnelState.employees,
            onSetRange = management::setRange,
            onFollowUp = management::followUp,
            onOpenInventory = { navigate(AppScreen.INVENTORY) },
            onOpenWorkforce = { navigate(AppScreen.PERSONNEL) },
            onSaveBudget = management::saveBudget,
            onRecordSpend = management::recordSpend,
            onCloseAccountingPeriod = management::closeAccountingPeriod,
            onReopenAccountingPeriod = management::reopenAccountingPeriod,
            onReconcileSalesCash = management::reconcileSalesCash,
            onBack = navigateBack,
        )

        AppScreen.MANAGEMENT_ISSUES,
        AppScreen.MANAGEMENT_TASKS,
        AppScreen.CHECKLISTS,
        AppScreen.DAILY_BRIEF -> ManagementWorkflowRoute(
            screen = screen,
            state = workflowState,
            issues = workflowIssues,
            tasks = workflowTasks,
            templates = checklistTemplates,
            runs = checklistRuns,
            runItems = checklistRunItems,
            currentRunId = checklistCurrentRunId,
            dailyBrief = dailyBriefState,
            branches = branches,
            employees = personnelState.employees,
            currentUser = currentUser,
            workflow = workflow,
            navigateTopLevel = navigate,
            navigateBack = navigateBack,
        )

        AppScreen.ALERTS -> AlertCenterScreen(
            state = alertState,
            notificationPermissionGranted = notificationPermissionGranted,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRefresh = alerts::refresh,
            onRead = alerts::markRead,
            onActioned = alerts::markActioned,
            onResolve = alerts::resolve,
            onDismiss = alerts::dismiss,
            onSnooze = alerts::snoozeOneDay,
            onOpenSource = { target ->
                when (target) {
                    is AlertTarget.InventoryItem -> { inventory.focusItem(target.itemId); navigate(AppScreen.INVENTORY) }
                    is AlertTarget.InventoryLot -> { inventory.selectSection(InventoryWorkspaceSection.EXPIRY); navigate(AppScreen.INVENTORY) }
                    is AlertTarget.InventoryCount -> { inventory.selectSection(InventoryWorkspaceSection.COUNTS); navigate(AppScreen.INVENTORY_COUNT) }
                    is AlertTarget.Purchase -> { operations.selectPurchase(target.purchaseId); navigate(AppScreen.PURCHASES) }
                    is AlertTarget.PurchaseOrder -> navigate(AppScreen.PURCHASES)
                    is AlertTarget.Receivable -> navigate(AppScreen.CRM)
                    is AlertTarget.EmploymentContract -> navigate(AppScreen.PERSONNEL)
                    is AlertTarget.Payroll -> navigate(AppScreen.PERSONNEL)
                    is AlertTarget.AttendanceCorrection -> navigate(AppScreen.PERSONNEL)
                    is AlertTarget.Asset -> navigate(AppScreen.ASSETS)
                    is AlertTarget.SecurityEvent -> navigate(AppScreen.AUDIT_LOG)
                    AlertTarget.None -> Unit
                }
            },
            onClearDismissed = alerts::clearDismissed,
            onBack = navigateBack,
        )

        AppScreen.REPORTS -> ReportsCenterScreen(
            organizationName = organizationName,
            dashboard = dashboardState,
            sales = salesState,
            accounting = accountingState,
            operations = operationsState,
            personnel = personnelState,
            assets = assetState,
            onOpenSales = { navigate(AppScreen.SALES) },
            onOpenAccounting = { navigate(AppScreen.ACCOUNTING) },
            onOpenInventory = { navigate(AppScreen.INVENTORY) },
            onOpenStockMovements = { navigate(AppScreen.STOCK_MOVEMENTS) },
            onOpenPersonnel = { navigate(AppScreen.PERSONNEL) },
            onSetReportRange = { from, to ->
                sales.setReportRange(from, to)
                accounting.setProfitLossRange(from, to)
            },
            navigateTopLevel = navigate,
            onBack = navigateBack,
        )

        AppScreen.GLOBAL_SEARCH -> GlobalSearchScreen(
            currentUser = currentUser,
            state = globalSearchState,
            onSearch = globalSearch::search,
            onOpen = navigate,
            onOpenEntity = { result ->
                val target = when (val entity = result.target) {
                    is GlobalSearchTarget.InventoryItem -> { inventory.focusItem(entity.id); AppScreen.INVENTORY }
                    is GlobalSearchTarget.StockMovement -> { inventory.focusItem(entity.itemId, InventoryWorkspaceSection.MOVEMENTS); AppScreen.STOCK_MOVEMENTS }
                    is GlobalSearchTarget.Purchase -> { operations.selectPurchase(entity.id); AppScreen.PURCHASES }
                    is GlobalSearchTarget.Account -> { accounting.selectLedger(entity.code); AppScreen.ACCOUNTING }
                    is GlobalSearchTarget.Journal -> { accounting.selectJournal(entity.id); AppScreen.ACCOUNTING }
                    is GlobalSearchTarget.Employee -> { onRequestEmployeeProfile(entity.id); AppScreen.PERSONNEL }
                    is GlobalSearchTarget.Customer -> { crm.select(entity.id); AppScreen.CRM }
                }
                navigate(target)
            },
            onBack = navigateBack,
        )

        else -> error("management_route_group_mismatch:${screen.name}")
    }
}
