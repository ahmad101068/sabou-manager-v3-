package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertEquals

class RestaurantKpiTest {
    @Test
    fun marginCalculationDoesNotOverflowLong() {
        val result = RestaurantKpiCalculator.calculate(Long.MAX_VALUE, 1, 1)
        assertEquals(99, result.marginPercent)
        assertEquals(Long.MAX_VALUE - 1, result.grossProfitRial)
    }

    @Test
    fun lossProducesNegativeMargin() {
        val result = RestaurantKpiCalculator.calculate(100, 150, 1)
        assertEquals(-50, result.marginPercent)
        assertEquals(-50, result.grossProfitRial)
    }
}
