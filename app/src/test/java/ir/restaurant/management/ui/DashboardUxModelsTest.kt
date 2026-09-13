package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardSnapshot
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.security.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUxModelsTest {
    private fun user(role: UserRole) = AppUserRecord(1, "u", "کاربر تست", role, true, false)
    private val snapshot = DashboardSnapshot(
        fromEpochDay = 20_000,
        toEpochDay = 20_000,
        postedInvoiceSalesRial = 10_000_000,
        postedInvoiceGrossProfitRial = 3_000_000,
        postedInvoiceCount = 12,
        cashBalanceRial = 4_000_000,
        bankBalanceRial = 8_000_000,
        lowStockCount = 5,
        expiringLotCount = 2,
        slowStockCount = 3,
        wasteRial = 500_000,
        attendanceAnomalyCount = 4,
        unpaidPayrollRial = 20_000_000,
        dueMaintenanceCount = 1,
    )

    @Test
    fun `owner home exposes at most four role aware kpis`() {
        val state = DashboardUxComposer.compose(snapshot, user(UserRole.OWNER), "مدیریت رستوران")
        assertEquals(4, state.kpis.size)
        assertTrue(state.kpis.any { it.id == "sales" })
        assertTrue(state.kpis.any { it.id == "gross_profit" })
        assertTrue(state.kpis.any { it.id == "liquidity" })
    }

    @Test
    fun `inventory user never receives accounting kpi`() {
        val inventoryUser = user(UserRole.INVENTORY)
        val kpis = DashboardUxComposer.resolveKpis(snapshot, inventoryUser)
        assertFalse(kpis.any { it.id == "liquidity" || it.id == "payables" || it.id == "receivables" })
        assertTrue(kpis.any { it.id == "low_stock" })

        val state = DashboardUxComposer.compose(snapshot, inventoryUser, "مدیریت رستوران")
        assertFalse(state.performanceText.contains("فروش"))
    }

    @Test
    fun `cashier quick actions are permission filtered and bounded`() {
        val actions = DashboardUxComposer.resolveQuickActions(user(UserRole.CASHIER))
        assertTrue(actions.size <= 6)
        assertTrue(actions.any { it.id == "sale" })
        assertTrue(actions.any { it.id == "more" })
        assertFalse(actions.any { it.id == "purchase" || it.id == "treasury" || it.id == "personnel" })
    }

    @Test
    fun `home attention never contains ordinary audit activity`() {
        val alerts = DashboardUxComposer.resolveAlerts(snapshot, user(UserRole.OWNER))
        assertTrue(alerts.isNotEmpty())
        assertFalse(alerts.any { it.id.contains("audit", ignoreCase = true) })
    }

    @Test
    fun `restricted historical role gets no unauthorized financial metrics`() {
        val state = DashboardUxComposer.compose(snapshot, user(UserRole.RESTRICTED), "مدیریت رستوران")
        assertTrue(state.kpis.isEmpty())
        assertEquals(1, state.quickActions.size)
        assertEquals("more", state.quickActions.single().id)
    }
}
