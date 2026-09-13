package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartReorderCalculatorTest {
    @Test
    fun recommendsLeadTimeSafetyAndReviewCoverage() {
        val result = SmartReorderCalculator.recommend(
            ReorderInput(1, "برنج", "کیلو", currentStockMicros = 2_000_000, averageDailyUsageMicros = 1_000_000, policy = ReorderPolicy(2, 1, 7)),
        )
        assertEquals(10_000_000L, result.projectedNeedMicros)
        assertEquals(8_000_000L, result.recommendedOrderMicros)
        assertEquals(ReorderUrgency.SOON, result.urgency)
    }

    @Test
    fun onOrderReducesRecommendation() {
        val result = SmartReorderCalculator.recommend(
            ReorderInput(2, "روغن", "لیتر", currentStockMicros = 1_000_000, onOrderMicros = 4_000_000, averageDailyUsageMicros = 1_000_000, policy = ReorderPolicy(1, 0, 2)),
        )
        assertEquals(0L, result.recommendedOrderMicros)
    }
}
