package ir.restaurant.management.ui

import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.personnel.PayrollStatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.restaurant.management.data.AppContainer
import ir.restaurant.management.ProtectedWorkScheduler
import ir.restaurant.management.data.BackupPolicy
import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.ui.theme.ManagementBrand
import ir.restaurant.management.organizationDisplayTitle
import kotlinx.coroutines.delay

@Composable
fun RestaurantManagementApp(
    container: AppContainer,
    organizationName: String,
    onOrganizationNameChange: (String) -> Unit,
    themePreference: ThemePreference,
    currencyUnit: CurrencyUnit,
    fontScalePreference: FontScalePreference,
    backupPolicy: BackupPolicy,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onCurrencyUnitChange: (CurrencyUnit) -> Unit,
    onFontScalePreferenceChange: (FontScalePreference) -> Unit,
    onBackupPolicyChange: (BackupPolicy) -> Unit,
    onAppPreferencesReset: () -> Unit,
) {
    val security: SecurityViewModel = viewModel(
        factory = SecurityViewModel.factory(container.securityRepository, container),
    )
    val securityState by security.state.collectAsStateWithLifecycle()
    AutoClearMessage(securityState.message, security::clearMessage)
    val activity = LocalActivity.current

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        val authenticatedUser = securityState.currentUser
        if (authenticatedUser == null) {
            SecurityScreen(
                state = securityState,
                onSave = security::save,
                onDeactivate = security::deactivate,
                onSwitch = security::switchUser,
                onSetRecoveryCode = security::setRecoveryCode,
                onRecoverPin = security::recoverPin,
                onLogout = security::logout,
                onBackup = security::createBackup,
                onBackupToDrive = security::backupToDrive,
                onExport = security::exportBackup,
                onImport = security::importBackup,
                onRestore = security::restore,
                onDeleteBackup = security::deleteBackup,
                canManageUsers = false,
                canBackup = false,
                onBack = { activity?.finish() },
            )
        } else {
            AuthenticatedSessionViewModelScope(sessionUserId = authenticatedUser.id) {
                AuthenticatedRestaurantManagementApp(
                container = container,
                organizationName = organizationName,
                onOrganizationNameChange = onOrganizationNameChange,
                themePreference = themePreference,
                currencyUnit = currencyUnit,
                fontScalePreference = fontScalePreference,
                backupPolicy = backupPolicy,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onThemePreferenceChange = onThemePreferenceChange,
                onCurrencyUnitChange = onCurrencyUnitChange,
                onFontScalePreferenceChange = onFontScalePreferenceChange,
                onBackupPolicyChange = onBackupPolicyChange,
                onAppPreferencesReset = onAppPreferencesReset,
                    security = security,
                    securityState = securityState,
                )
            }
        }
    }
}

private class AuthenticatedViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

@Composable
private fun AuthenticatedSessionViewModelScope(
    sessionUserId: Long,
    content: @Composable () -> Unit,
) {
    val owner = remember(sessionUserId) { AuthenticatedViewModelStoreOwner() }
    val context = LocalContext.current.applicationContext
    DisposableEffect(owner, context) {
        ProtectedWorkScheduler.enable(context)
        onDispose {
            ProtectedWorkScheduler.disable(context)
            owner.viewModelStore.clear()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

@Composable
private fun AuthenticatedRestaurantManagementApp(
    container: AppContainer,
    organizationName: String,
    onOrganizationNameChange: (String) -> Unit,
    themePreference: ThemePreference,
    currencyUnit: CurrencyUnit,
    fontScalePreference: FontScalePreference,
    backupPolicy: BackupPolicy,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onCurrencyUnitChange: (CurrencyUnit) -> Unit,
    onFontScalePreferenceChange: (FontScalePreference) -> Unit,
    onBackupPolicyChange: (BackupPolicy) -> Unit,
    onAppPreferencesReset: () -> Unit,
    security: SecurityViewModel,
    securityState: SecurityUiState,
) {
    val dashboard: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(
            container.dashboardRepository,
            container.dailyManagementBriefService,
        ),
    )
    val operations: OperationsViewModel = viewModel(
        factory = OperationsViewModel.factory(
            container.operationsRepository,
            container.purchaseRepository,
            container.procurementUseCases,
            container.operationsInventoryUseCases,
            container.costControlReadService,
        ),
    )
    val inventory: InventoryWorkspaceViewModel = viewModel(
        factory = InventoryWorkspaceViewModel.factory(
            container.inventoryUseCases,
            container.procurementUseCases,
            container.securityRepository,
        ),
    )
    val accounting: AccountingViewModel = viewModel(
        factory = AccountingViewModel.factory(container.accountingUseCases),
    )
    val globalSearch: GlobalSearchViewModel = viewModel(
        factory = GlobalSearchViewModel.factory(container.globalSearchRepository),
    )
    val sales: DailySalesViewModel = viewModel(
        factory = DailySalesViewModel.factory(
            container.dailySalesRepository,
            container.branchRepository,
            container.inventoryRepository,
            container.recipeRepository,
            container.salesHistoryRepository,
            container.securityRepository,
        ),
    )
    val treasury: TreasuryViewModel = viewModel(
        factory = TreasuryViewModel.factory(container.treasuryUseCases, container.reverseTreasuryTransactionUseCase),
    )
    val crm: CrmViewModel = viewModel(
        factory = CrmViewModel.factory(
            container.crmUseCases,
            container.salesHistoryUseCases,
            container.receivableService,
            container.dailyManagementBriefService,
        ),
    )
    val recipes: RecipeViewModel = viewModel(
        factory = RecipeViewModel.factory(container.recipeUseCases, container.operationsRepository),
    )
    val personnel: PersonnelViewModel = viewModel(
        factory = PersonnelViewModel.factory(container.personnelUseCases, container.attendanceUseCases, container.payrollUseCases),
    )
    val performance: PerformanceViewModel = viewModel(factory = PerformanceViewModel.factory(container.performanceRepository))
    val sync: SyncViewModel = viewModel(factory = SyncViewModel.factory(container.syncRepository, container))
    val branches: BranchManagementViewModel = viewModel(
        factory = BranchManagementViewModel.factory(container.branchRepository),
    )
    val managementControl: ManagementControlViewModel = viewModel(
        factory = ManagementControlViewModel.factory(
            container.managementControlRepository,
            container.securityRepository,
        ),
    )
    val managementWorkflow: ManagementWorkflowViewModel = viewModel(
        factory = ManagementWorkflowViewModel.factory(
            container.managementWorkflowReadService,
            container.managementWorkflowService,
            container.dailyManagementBriefService,
            container::refreshManagementRules,
        ),
    )
    val assets: AssetViewModel = viewModel(factory = AssetViewModel.factory(container.assetUseCases))
    val alerts: AlertViewModel = viewModel(factory = AlertViewModel.factory(container.alertRepository))
    val dashboardState by dashboard.state.collectAsStateWithLifecycle()
    val dashboardHomeState by dashboard.homeState.collectAsStateWithLifecycle()
    val dashboardManagementOverview by dashboard.managementOverview.collectAsStateWithLifecycle()
    val operationsState by operations.state.collectAsStateWithLifecycle()
    val inventoryState by inventory.state.collectAsStateWithLifecycle()
    val accountingState by accounting.state.collectAsStateWithLifecycle()
    val globalSearchState by globalSearch.state.collectAsStateWithLifecycle()
    val salesState by sales.state.collectAsStateWithLifecycle()
    val treasuryState by treasury.state.collectAsStateWithLifecycle()
    val crmState by crm.state.collectAsStateWithLifecycle()
    val recipeState by recipes.state.collectAsStateWithLifecycle()
    val canObserveAlerts = securityState.currentUser?.role?.let { role ->
        listOf(
            Permission.INVENTORY_VIEW, Permission.PURCHASES, Permission.RECEIVABLE_VIEW,
            Permission.PERSONNEL_VIEW, Permission.PAYROLL_VIEW_ALL, Permission.ASSETS,
            Permission.AUDIT_VIEW, Permission.BACKUP, Permission.RESTORE,
        ).any(role::allows)
    } == true
    val alertState = if (canObserveAlerts) {
        alerts.state.collectAsStateWithLifecycle().value
    } else {
        AlertUiState()
    }
    val personnelState by personnel.state.collectAsStateWithLifecycle()
    val canObservePayroll = securityState.currentUser?.role?.allows(Permission.PAYROLL_VIEW_ALL) == true
    val hrPayrollState = if (canObservePayroll) {
        personnel.hrState.collectAsStateWithLifecycle().value
    } else {
        HrPayrollUiState()
    }
    val performanceState by performance.state.collectAsStateWithLifecycle()
    val syncState by sync.state.collectAsStateWithLifecycle()
    val managementControlState by managementControl.state.collectAsStateWithLifecycle()
    val managementWorkflowState by managementWorkflow.state.collectAsStateWithLifecycle()
    val managementIssues by managementWorkflow.issues.collectAsStateWithLifecycle()
    val managementTasks by managementWorkflow.tasks.collectAsStateWithLifecycle()
    val checklistTemplates by managementWorkflow.templates.collectAsStateWithLifecycle()
    val checklistRuns by managementWorkflow.runs.collectAsStateWithLifecycle()
    val checklistRunItems by managementWorkflow.checklistRunItems.collectAsStateWithLifecycle()
    val checklistCurrentRunId by managementWorkflow.currentRunId.collectAsStateWithLifecycle()
    val dailyBriefState by managementWorkflow.dailyBrief.collectAsStateWithLifecycle()
    val branchManagementState by branches.state.collectAsStateWithLifecycle()
    val assetState by assets.state.collectAsStateWithLifecycle()
    LaunchedEffect(securityState.currentUser, organizationName) {
        dashboard.setContext(securityState.currentUser, organizationName)
    }
    LaunchedEffect(canObserveAlerts) {
        if (canObserveAlerts) alerts.refresh()
    }
    AutoClearMessage(operationsState.message, operations::clearMessage)
    AutoClearMessage(inventoryState.message, inventory::clearMessage)
    AutoClearMessage(accountingState.message, accounting::clearMessage)
    AutoClearMessage(salesState.message, sales::clearMessage)
    AutoClearMessage(treasuryState.message, treasury::clearMessage)
    AutoClearMessage(crmState.message, crm::clearMessage)
    AutoClearMessage(recipeState.message, recipes::clearMessage)
    AutoClearMessage(alertState.message, alerts::clearMessage)
    AutoClearMessage(personnelState.message, personnel::clearMessage)
    AutoClearMessage(performanceState.message, performance::clearMessage)
    AutoClearMessage(syncState.message, sync::clearMessage)
    AutoClearMessage(managementControlState.message, managementControl::clearMessage)
    AutoClearMessage(managementWorkflowState.message ?: managementWorkflowState.error, managementWorkflow::clearMessage)
    AutoClearMessage(branchManagementState.message, branches::clearMessage)
    AutoClearMessage(assetState.message, assets::clearMessage)
    var routeStack by rememberSaveable { mutableStateOf(listOf(AppScreen.DASHBOARD.name)) }
    var requestedEmployeeProfileId by rememberSaveable { mutableStateOf<Long?>(null) }
    val screen = AppScreen.valueOf(routeStack.last())
    SensitiveScreenPrivacyEffect(screen)
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    LaunchedEffect(screen) {
        operations.setWorkspaceActive(
            screen.group == AppRouteGroup.OPERATIONS || screen in setOf(
                AppScreen.AUDIT_LOG, AppScreen.REPORTS, AppScreen.GLOBAL_SEARCH,
            )
        )
    }

    fun navigate(target: AppScreen) {
        if (target == screen || !canOpenScreen(securityState.currentUser, target)) return
        val topLevel = target in setOf(
            AppScreen.DASHBOARD,
            AppScreen.CONTROL_HUB,
            AppScreen.OPERATIONS_HUB,
            AppScreen.FINANCE_HUB,
            AppScreen.MORE,
        )
        routeStack = if (topLevel) listOf(target.name) else routeStack + target.name
    }

    fun navigateBack() {
        if (routeStack.size > 1) {
            routeStack = routeStack.dropLast(1)
        } else {
            showExitConfirmation = true
        }
    }

    BackHandler {
        when {
            screen == AppScreen.DASHBOARD -> showExitConfirmation = true
            screen in setOf(AppScreen.CONTROL_HUB, AppScreen.OPERATIONS_HUB, AppScreen.FINANCE_HUB, AppScreen.MORE) ->
                routeStack = listOf(AppScreen.DASHBOARD.name)
            routeStack.size > 1 -> navigateBack()
            else -> routeStack = listOf(AppScreen.DASHBOARD.name)
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("خروج از برنامه") },
            text = { Text("آیا می‌خواهید از برنامه خارج شوید؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        activity?.finish()
                    },
                ) {
                    Text("خروج")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("انصراف")
                }
            },
        )
    }

    LaunchedEffect(securityState.currentUser, securityState.users.size) {
        if (securityState.currentUser == null) {
            if (screen != AppScreen.SECURITY || routeStack.size != 1) {
                routeStack = listOf(AppScreen.SECURITY.name)
            }
        } else if (!canOpenScreen(securityState.currentUser, screen)) {
            routeStack = listOf(AppScreen.DASHBOARD.name)
        } else if (screen == AppScreen.SECURITY && routeStack.size == 1) {
            routeStack = listOf(AppScreen.DASHBOARD.name)
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        ErpResponsiveNavigationFrame(screen, { navigate(it) }) {
            ResponsiveContentSurface {
                when (screen.group) {
            AppRouteGroup.HOME -> DashboardScreen(
                state = dashboardState,
                home = dashboardHomeState,
                managementOverview = dashboardManagementOverview,
                onSearch = { navigate(AppScreen.GLOBAL_SEARCH) },
                onSettings = { navigate(AppScreen.SETTINGS) },
                onToday = dashboard::today,
                onWeek = dashboard::week,
                onMonth = dashboard::month,
                onCustomRange = dashboard::custom,
                onBranchSelected = dashboard::selectBranch,
                onWarehouse = dashboard::warehouse,
                onOpen = { navigate(it) },
            )

            AppRouteGroup.HUB -> NavigationHubRoutes(
                screen = screen,
                currentUser = securityState.currentUser,
                navigate = { navigate(it) },
            )

            AppRouteGroup.OPERATIONS -> OperationsRoutes(
                screen = screen,
                operationsState = operationsState,
                operations = operations,
                inventoryState = inventoryState,
                inventory = inventory,
                salesState = salesState,
                sales = sales,
                recipeState = recipeState,
                recipes = recipes,
                accounting = accounting,
                branches = branchManagementState.branches,
                currentUser = securityState.currentUser,
                navigate = { navigate(it) },
                navigateBack = { navigateBack() },
            )

            AppRouteGroup.ACCOUNTING -> AccountingRoutes(
                screen = screen,
                state = accountingState,
                accounting = accounting,
                sales = sales,
                treasuryState = treasuryState,
                treasury = treasury,
                crmState = crmState,
                crm = crm,
                branches = branchManagementState.branches,
                navigate = { navigate(it) },
                navigateBack = { navigateBack() },
            )

            AppRouteGroup.WORKFORCE -> WorkforceRoutes(
                screen = screen,
                personnelState = personnelState,
                hrPayrollState = hrPayrollState,
                treasuryState = treasuryState,
                personnel = personnel,
                performanceState = performanceState,
                performance = performance,
                assetState = assetState,
                assets = assets,
                workforceControlState = managementControlState,
                workforceControl = managementControl,
                branches = branchManagementState.branches,
                suppliers = operationsState.suppliers,
                requestedEmployeeProfileId = requestedEmployeeProfileId,
                onProfileRequestConsumed = { requestedEmployeeProfileId = null },
                navigateBack = { navigateBack() },
            )

            AppRouteGroup.MANAGEMENT -> ManagementRoutes(
                screen = screen,
                organizationName = organizationName,
                dashboardState = dashboardState,
                operationsState = operationsState,
                operations = operations,
                inventory = inventory,
                accountingState = accountingState,
                accounting = accounting,
                globalSearchState = globalSearchState,
                globalSearch = globalSearch,
                crm = crm,
                salesState = salesState,
                sales = sales,
                personnelState = personnelState,
                assetState = assetState,
                managementState = managementControlState,
                management = managementControl,
                workflowState = managementWorkflowState,
                workflowIssues = managementIssues,
                workflowTasks = managementTasks,
                checklistTemplates = checklistTemplates,
                checklistRuns = checklistRuns,
                checklistRunItems = checklistRunItems,
                checklistCurrentRunId = checklistCurrentRunId,
                dailyBriefState = dailyBriefState,
                workflow = managementWorkflow,
                branches = branchManagementState.branches,
                alertState = alertState,
                alerts = alerts,
                currentUser = securityState.currentUser,
                users = securityState.users,
                notificationPermissionGranted = notificationPermissionGranted,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestEmployeeProfile = { requestedEmployeeProfileId = it },
                navigate = { navigate(it) },
                navigateBack = { navigateBack() },
            )

            AppRouteGroup.ADMIN -> AdminRoutes(
                screen = screen,
                securityState = securityState,
                security = security,
                branchState = branchManagementState,
                branches = branches,
                syncState = syncState,
                sync = sync,
                organizationName = organizationName,
                onOrganizationNameChange = onOrganizationNameChange,
                themePreference = themePreference,
                currencyUnit = currencyUnit,
                fontScalePreference = fontScalePreference,
                backupPolicy = backupPolicy,
                onThemePreferenceChange = onThemePreferenceChange,
                onCurrencyUnitChange = onCurrencyUnitChange,
                onFontScalePreferenceChange = onFontScalePreferenceChange,
                onBackupPolicyChange = onBackupPolicyChange,
                onAppPreferencesReset = onAppPreferencesReset,
                navigate = { navigate(it) },
                navigateBack = { navigateBack() },
            )
                }
            }
        }
    }

}

@Composable
private fun AutoClearMessage(message: String?, onClear: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500L)
            onClear()
        }
    }
}

@Composable
private fun SensitiveScreenPrivacyEffect(screen: AppScreen) {
    val activity = LocalActivity.current
    val sensitive = screen in setOf(
        AppScreen.PERSONNEL, AppScreen.TREASURY, AppScreen.ACCOUNTING, AppScreen.NEW_JOURNAL,
        AppScreen.SECURITY, AppScreen.AUDIT_LOG,
    )
    DisposableEffect(activity, sensitive) {
        val window = activity?.window
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        val wasSecure = window?.attributes?.flags?.and(flag) != 0
        if (sensitive && !wasSecure) window?.addFlags(flag)
        onDispose {
            if (sensitive && !wasSecure) window?.clearFlags(flag)
        }
    }
}

