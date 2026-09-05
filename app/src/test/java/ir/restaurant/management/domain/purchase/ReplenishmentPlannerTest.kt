package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplenishmentPlannerTest {
    @Test
    fun roundsRequiredQuantityToOrderMultiple() {
        val result = ReplenishmentPlanner.suggest(
            ReplenishmentInput(
                itemId = 7,
                itemName = "دانه قهوه",
                currentStockMicros = 2_000_000,
                openPurchaseOrderMicros = 0,
                usage30DaysMicros = 30_000_000,
                estimatedUnitCostRial = 600_000,
                policy = ReplenishmentPolicyRecord(7, 3, 14, 3, 2_000_000, 5_000_000, true),
                hasPendingRequest = false,
                preferredSupplierScore = 910,
            ),
        )

        requireNotNull(result)
        assertEquals(1_000_000, result.averageDailyUsageMicros)
        assertEquals(20_000_000, result.suggestedOrderMicros)
        assertEquals(ReplenishmentRisk.CRITICAL, result.risk)
        assertEquals(12_000_000, result.estimatedOrderValueRial)
    }

    @Test
    fun keepsPendingRequestGuardInSuggestion() {
        val result = ReplenishmentPlanner.suggest(
            ReplenishmentInput(
                itemId = 8,
                itemName = "شیر",
                currentStockMicros = 1_000_000,
                openPurchaseOrderMicros = 0,
                usage30DaysMicros = 30_000_000,
                estimatedUnitCostRial = 100_000,
                policy = ReplenishmentPolicyRecord(8, null, 7, 1, 0, 1_000_000, true),
                hasPendingRequest = true,
                preferredSupplierScore = null,
            ),
        )

        assertTrue(requireNotNull(result).blockedByPendingRequest)
    }
}
