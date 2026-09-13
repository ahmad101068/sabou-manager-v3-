package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveErpLayoutTest {
    @Test fun `window thresholds keep mobile tablet desktop distinct`() {
        assertEquals(ErpWindowClass.COMPACT, classifyErpWindow(599))
        assertEquals(ErpWindowClass.MEDIUM, classifyErpWindow(600))
        assertEquals(ErpWindowClass.MEDIUM, classifyErpWindow(839))
        assertEquals(ErpWindowClass.EXPANDED, classifyErpWindow(840))
    }

    @Test fun `management workflows keep control active on every form factor`() {
        listOf(
            AppScreen.MANAGEMENT_ISSUES,
            AppScreen.MANAGEMENT_TASKS,
            AppScreen.CHECKLISTS,
            AppScreen.DAILY_BRIEF,
        ).forEach { screen ->
            assertEquals(AppScreen.CONTROL_HUB, screen.topLevelDestination())
        }
    }
}
