package ir.restaurant.management.ui

import androidx.compose.runtime.Composable
import ir.restaurant.management.domain.branch.BranchRecord

@Composable
internal fun AccountingRoutes(
    screen: AppScreen,
    state: AccountingUiState,
    accounting: AccountingViewModel,
    sales: DailySalesViewModel,
    treasuryState: TreasuryUiState,
    treasury: TreasuryViewModel,
    crmState: CrmUiState,
    crm: CrmViewModel,
    branches: List<BranchRecord>,
    navigate: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    when (screen) {
        AppScreen.ACCOUNTING -> AccountingScreen(
            state = state,
            onSearch = accounting::searchJournals,
            onSelectJournal = accounting::selectJournal,
            onSelectLedger = accounting::selectLedger,
            onSaveAccount = accounting::saveAccount,
            onDeactivateAccount = accounting::deactivateAccount,
            onReverse = accounting::reverseManual,
            onSetProfitLossRange = { from, to ->
                accounting.setProfitLossRange(from, to)
                sales.setReportRange(from, to)
            },
            onAddJournal = { navigate(AppScreen.NEW_JOURNAL) },
            onOpenTreasury = { navigate(AppScreen.TREASURY) },
            onBack = navigateBack,
        )

        AppScreen.NEW_JOURNAL -> ManualJournalEntryScreen(
            state = state,
            onPost = accounting::postManual,
            onBack = navigateBack,
        )

        AppScreen.TREASURY -> TreasuryScreen(
            state = treasuryState,
            onReceipt = treasury::receipt,
            onPayment = treasury::payment,
            onSettlement = treasury::settlement,
            onTransfer = treasury::transfer,
            onReconcile = treasury::reconcile,
            onReverse = treasury::reverse,
            onBack = navigateBack,
        )

        AppScreen.CRM -> CrmScreen(
            state = crmState,
            branches = branches,
            onSelect = crm::select,
            onSaveCustomer = crm::saveCustomer,
            onPostOpening = crm::postOpeningBalance,
            onPostAdjustment = crm::postAdjustment,
            onRefreshAging = crm::refreshAging,
            onDetectDuplicates = crm::detectDuplicates,
            onMerge = crm::merge,
            onSelectReceivableBranch = crm::selectReceivableBranch,
            onCollectReceivable = crm::collectReceivable,
            onOpenSales = { navigate(AppScreen.SALES) },
            onOpenTreasury = { navigate(AppScreen.TREASURY) },
            onNavigateTopLevel = navigate,
            onBack = navigateBack,
        )

        else -> error("accounting_route_group_mismatch:${screen.name}")
    }
}
