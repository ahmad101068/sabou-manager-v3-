package ir.restaurant.management.ui

import androidx.compose.runtime.Composable
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.operations.SupplierRecord

@Composable
internal fun WorkforceRoutes(
    screen: AppScreen,
    personnelState: PersonnelUiState,
    hrPayrollState: HrPayrollUiState,
    treasuryState: TreasuryUiState,
    personnel: PersonnelViewModel,
    performanceState: PerformanceUiState,
    performance: PerformanceViewModel,
    assetState: AssetUiState,
    assets: AssetViewModel,
    workforceControlState: ControlCenterUiState,
    workforceControl: ManagementControlViewModel,
    branches: List<BranchRecord>,
    suppliers: List<SupplierRecord>,
    requestedEmployeeProfileId: Long?,
    onProfileRequestConsumed: () -> Unit,
    navigateBack: () -> Unit,
) {
    when (screen) {
        AppScreen.PERSONNEL -> HrPayrollWorkspaceScreen(
            personnelState = personnelState,
            hrState = hrPayrollState,
            treasuryState = treasuryState,
            requestedProfileEmployeeId = requestedEmployeeProfileId,
            onProfileRequestConsumed = onProfileRequestConsumed,
            performanceState = performanceState,
            workforceState = workforceControlState,
            personnel = personnel,
            performance = performance,
            workforce = workforceControl,
            branches = branches,
            onBack = navigateBack,
        )

        AppScreen.ASSETS -> AssetScreen(
            state = assetState,
            branches = branches,
            suppliers = suppliers,
            onSave = assets::save,
            onRecognize = assets::recognize,
            onDispose = assets::dispose,
            onDepreciate = assets::depreciate,
            onReverseDepreciation = assets::reverseDepreciation,
            onTransfer = assets::transfer,
            onMaintenance = assets::maintenance,
            onImpair = assets::impair,
            onSell = assets::sell,
            onBack = navigateBack,
        )

        else -> error("workforce_route_group_mismatch:${screen.name}")
    }
}
