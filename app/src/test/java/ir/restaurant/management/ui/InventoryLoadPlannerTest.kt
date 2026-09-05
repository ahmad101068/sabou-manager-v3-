package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryLoadPlannerTest {
    @Test
    fun overview_loads_summary_and_only_five_recent_movements() {
        val plan = InventoryLoadPlanner.forSection(InventoryWorkspaceSection.OVERVIEW)
        assertTrue(plan.dashboard)
        assertTrue(plan.locations)
        assertTrue(plan.replenishment)
        assertEquals(5, plan.movementsLimit)
        assertFalse(plan.items)
        assertFalse(plan.lots)
        assertFalse(plan.counts)
        assertFalse(plan.waste)
        assertFalse(plan.transfers)
    }

    @Test
    fun transfers_load_only_transfer_dependencies() {
        val plan = InventoryLoadPlanner.forSection(InventoryWorkspaceSection.TRANSFERS)
        assertTrue(plan.items)
        assertTrue(plan.locations)
        assertTrue(plan.lots)
        assertTrue(plan.transfers)
        assertFalse(plan.dashboard)
        assertFalse(plan.counts)
        assertFalse(plan.waste)
        assertEquals(null, plan.movementsLimit)
    }

    @Test
    fun counts_do_not_load_unrelated_lists() {
        val plan = InventoryLoadPlanner.forSection(InventoryWorkspaceSection.COUNTS)
        assertTrue(plan.items)
        assertTrue(plan.locations)
        assertTrue(plan.counts)
        assertFalse(plan.lots)
        assertFalse(plan.transfers)
        assertFalse(plan.replenishment)
    }
}
