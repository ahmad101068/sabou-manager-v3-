package ir.restaurant.management.ui

import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScreenAccessTest {
    @Test
    fun `cashier can open reports granted by its role without seeing accounting alerts`() {
        val cashier = AppUserRecord(
            id = 1,
            username = "cashier",
            displayName = "صندوقدار",
            role = UserRole.CASHIER,
            isActive = true,
            hasRecoveryCode = false,
        )

        assertTrue(canOpenScreen(cashier, AppScreen.REPORTS))
        assertFalse(canOpenScreen(cashier, AppScreen.ALERTS))
        assertFalse(canOpenScreen(cashier, AppScreen.ACCOUNTING))
    }

    @Test
    fun `manager can open management workflow surfaces`() {
        val manager = AppUserRecord(
            id = 2,
            username = "manager",
            displayName = "مدیر",
            role = UserRole.MANAGER,
            isActive = true,
            hasRecoveryCode = false,
        )
        assertTrue(canOpenScreen(manager, AppScreen.MANAGEMENT_ISSUES))
        assertTrue(canOpenScreen(manager, AppScreen.MANAGEMENT_TASKS))
        assertTrue(canOpenScreen(manager, AppScreen.CHECKLISTS))
        assertTrue(canOpenScreen(manager, AppScreen.DAILY_BRIEF))
    }

}
