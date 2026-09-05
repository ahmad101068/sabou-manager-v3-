package ir.restaurant.management.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InventoryRouteMappingTest {
    @Test
    fun `operations inventory cards route to independent workspaces`() {
        assertEquals(InventoryWorkspaceSection.OVERVIEW, AppScreen.INVENTORY.inventoryWorkspaceSection())
        assertEquals(InventoryWorkspaceSection.COUNTS, AppScreen.INVENTORY_COUNT.inventoryWorkspaceSection())
        assertEquals(InventoryWorkspaceSection.TRANSFERS, AppScreen.INVENTORY_TRANSFER.inventoryWorkspaceSection())
        assertEquals(InventoryWorkspaceSection.WASTE, AppScreen.INVENTORY_WASTE.inventoryWorkspaceSection())
        assertEquals(InventoryWorkspaceSection.MOVEMENTS, AppScreen.STOCK_MOVEMENTS.inventoryWorkspaceSection())
        assertNull(AppScreen.PURCHASES.inventoryWorkspaceSection())
    }
}
