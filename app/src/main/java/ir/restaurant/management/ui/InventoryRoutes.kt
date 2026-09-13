package ir.restaurant.management.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ir.restaurant.management.domain.branch.BranchRecord

/** Inventory-owned route group. RestaurantManagementApp remains a composition root, not an inventory router. */
@Composable
internal fun InventoryRoutes(
    screen: AppScreen,
    state: InventoryWorkspaceUiState,
    inventory: InventoryWorkspaceViewModel,
    operationsState: OperationsUiState,
    operations: OperationsViewModel,
    branches: List<BranchRecord>,
    navigate: (AppScreen) -> Unit,
    navigateBack: () -> Unit,
) {
    LaunchedEffect(screen) {
        val target = screen.inventoryWorkspaceSection()
        if (target != null && state.section != target) {
            inventory.selectSection(target)
        }
    }
    InventoryWorkspaceScreen(
        state = state,
        viewModel = inventory,
        operationsState = operationsState,
        operations = operations,
        branches = branches,
        onBack = navigateBack,
        onNavigate = navigate,
    )
}


internal fun AppScreen.inventoryWorkspaceSection(): InventoryWorkspaceSection? = when (this) {
    AppScreen.INVENTORY -> InventoryWorkspaceSection.OVERVIEW
    AppScreen.INVENTORY_COUNT -> InventoryWorkspaceSection.COUNTS
    AppScreen.INVENTORY_TRANSFER -> InventoryWorkspaceSection.TRANSFERS
    AppScreen.INVENTORY_WASTE -> InventoryWorkspaceSection.WASTE
    AppScreen.STOCK_MOVEMENTS -> InventoryWorkspaceSection.MOVEMENTS
    else -> null
}
