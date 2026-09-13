package ir.restaurant.management.ui

import androidx.compose.runtime.Composable
import ir.restaurant.management.data.BackupPolicy
import ir.restaurant.management.domain.security.Permission

@Composable
internal fun AdminRoutes(
    screen: AppScreen,
    securityState: SecurityUiState,
    security: SecurityViewModel,
    branchState: BranchManagementUiState,
    branches: BranchManagementViewModel,
    syncState: SyncUiState,
    sync: SyncViewModel,
    organizationName: String,
    onOrganizationNameChange: (String) -> Unit,
    themePreference: ThemePreference,
    currencyUnit: CurrencyUnit,
    fontScalePreference: FontScalePreference,
    backupPolicy: BackupPolicy,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    onCurrencyUnitChange: (CurrencyUnit) -> Unit,
    onFontScalePreferenceChange: (FontScalePreference) -> Unit,
    onBackupPolicyChange: (BackupPolicy) -> Unit,
    onAppPreferencesReset: () -> Unit,
    navigate: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    when (screen) {
        AppScreen.SECURITY -> SecurityScreen(
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
            canManageUsers = securityState.currentUser?.role?.allows(Permission.MANAGE_USERS) == true,
            canBackup = securityState.currentUser?.role?.allows(Permission.BACKUP) == true,
            onBack = navigateBack,
        )

        AppScreen.BRANCHES -> BranchManagementScreen(
            state = branchState,
            onCreate = branches::create,
            onRename = branches::rename,
            onSetActive = branches::setActive,
            onNavigateTopLevel = navigate,
            onBack = navigateBack,
        )

        AppScreen.SETTINGS -> SettingsScreen(
            organizationName = organizationName,
            onOrganizationNameChange = onOrganizationNameChange,
            themePreference = themePreference,
            currencyUnit = currencyUnit,
            fontScalePreference = fontScalePreference,
            backupPolicy = backupPolicy,
            syncState = syncState,
            onSaveSync = sync::save,
            onRunSync = sync::runNow,
            onRequeueSync = sync::requeueDeadLetters,
            onResolveSyncIssue = sync::resolveIssue,
            onThemePreferenceChange = onThemePreferenceChange,
            onCurrencyUnitChange = onCurrencyUnitChange,
            onFontScalePreferenceChange = onFontScalePreferenceChange,
            onBackupPolicyChange = onBackupPolicyChange,
            onFactoryReset = { pin -> security.factoryReset(pin, onAppPreferencesReset) },
            sensitiveActionBusy = securityState.busy,
            sensitiveActionMessage = securityState.message,
            onOpenScreen = navigate,
            showSensitiveSettings = securityState.currentUser?.role?.allows(Permission.BACKUP) == true,
            canFactoryReset = securityState.currentUser?.role?.allows(Permission.MANAGE_USERS) == true,
            canAuditView = securityState.currentUser?.role?.allows(Permission.AUDIT_VIEW) == true || securityState.currentUser?.role?.allows(Permission.AUDIT) == true,
            onBack = navigateBack,
        )

        else -> error("admin_route_group_mismatch:${screen.name}")
    }
}
