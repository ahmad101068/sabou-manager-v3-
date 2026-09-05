package ir.restaurant.management.ui

import androidx.compose.runtime.Composable
import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.operations.AppUserRecord

@Composable
internal fun OperationsRoutes(
    screen: AppScreen,
    operationsState: OperationsUiState,
    operations: OperationsViewModel,
    inventoryState: InventoryWorkspaceUiState,
    inventory: InventoryWorkspaceViewModel,
    salesState: DailySalesUiState,
    sales: DailySalesViewModel,
    recipeState: RecipeUiState,
    recipes: RecipeViewModel,
    accounting: AccountingViewModel,
    branches: List<BranchRecord>,
    currentUser: AppUserRecord?,
    navigate: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    when (screen) {
        AppScreen.PURCHASES -> PurchasesScreen(
            state = operationsState,
            branches = branches,
            currentUser = currentUser,
            onSearch = operations::searchPurchases,
            onRefresh = operations::refresh,
            onPurchaseToday = operations::purchaseToday,
            onPurchaseWeek = operations::purchaseWeek,
            onPurchaseMonth = operations::purchaseMonth,
            onLoadPurchasePriceControl = operations::loadPurchasePriceControl,
            onRequestProcurementAction = operations::requestProcurementAction,
            onNavigate = navigate,
            onSelect = operations::selectPurchase,
            onSettle = operations::settlePurchase,
            onReverseSettlement = operations::reversePurchaseSettlement,
            onReverse = operations::reversePurchase,
            onSubmitRequisition = operations::submitRequisition,
            onReviewRequisition = operations::reviewRequisition,
            onCreateOrder = operations::createPurchaseOrder,
            onCreateSplitOrders = operations::createSplitPurchaseOrders,
            onMarkOrderSent = operations::markPurchaseOrderSent,
            onAcknowledgeOrder = operations::acknowledgePurchaseOrder,
            onReceiveGoods = operations::postGoodsReceipt,
            onReturnGoods = operations::postPurchaseReturn,
            onSaveReplenishmentPolicy = operations::saveReplenishmentPolicy,
            onSaveSupplierOffer = operations::saveSupplierOffer,
            onSubmitSuggestedRequisition = operations::submitSuggestedRequisition,
            onMatchInvoice = operations::postMatchedInvoice,
            onConsumeProcurementLaunchAction = operations::consumeProcurementAction,
            onAdd = { navigate(AppScreen.NEW_PURCHASE) },
            onBack = {
                operations.selectPurchase(null)
                navigateBack()
            },
        )

        AppScreen.NEW_PURCHASE -> PurchaseEntryScreen(
            state = operationsState,
            branches = branches,
            locations = inventoryState.locations,
            onPost = operations::postPurchase,
            onBack = navigateBack,
        )

        AppScreen.SUPPLIERS -> SuppliersScreen(
            state = operationsState,
            onSave = operations::saveSupplier,
            onRefresh = operations::refresh,
            onDeactivate = operations::deactivateSupplier,
            onMerge = operations::mergeSupplier,
            onOpenPurchases = { navigate(AppScreen.PURCHASES) },
            onNewPurchase = { navigate(AppScreen.NEW_PURCHASE) },
            onBack = navigateBack,
        )

        AppScreen.INVENTORY,
        AppScreen.INVENTORY_COUNT,
        AppScreen.INVENTORY_TRANSFER,
        AppScreen.INVENTORY_WASTE,
        AppScreen.STOCK_MOVEMENTS,
        -> InventoryRoutes(
            screen = screen,
            state = inventoryState,
            inventory = inventory,
            operationsState = operationsState,
            operations = operations,
            branches = branches,
            navigate = navigate,
            navigateBack = navigateBack,
        )

        AppScreen.SALES -> DailySalesScreen(
            state = salesState,
            onSearch = sales::search,
            onSaveDraft = sales::saveDraft,
            onConfirm = sales::confirm,
            onPostConfirmed = sales::postConfirmed,
            onReverse = sales::reverse,
            onCloseDay = sales::closeDay,
            onReopenDay = sales::reopenDay,
            onSetReportRange = { from, to ->
                sales.setReportRange(from, to)
                accounting.setProfitLossRange(from, to)
            },
            onNavigateTopLevel = navigate,
            onBack = navigateBack,
        )

        AppScreen.RECIPES -> RecipeScreen(
            state = recipeState,
            onSelect = recipes::select,
            onSave = recipes::save,
            onCreateDraft = recipes::createDraftFrom,
            onCopyVersion = recipes::copyVersion,
            onLoadDraft = recipes::loadDraft,
            onEditDraft = recipes::editDraft,
            onCloseDraftEditor = recipes::closeDraftEditor,
            onActivate = recipes::activate,
            onRetire = recipes::retire,
            onSubstitution = recipes::approveSubstitution,
            onBack = {
                recipes.select(null)
                navigateBack()
            },
        )



        else -> error("operations_route_group_mismatch:${screen.name}")
    }
}
