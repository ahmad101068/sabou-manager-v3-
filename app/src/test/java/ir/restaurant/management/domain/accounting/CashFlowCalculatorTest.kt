package ir.restaurant.management.domain.accounting

import org.junit.Assert.assertEquals
import org.junit.Test

class CashFlowCalculatorTest {
    @Test
    fun forecastsOrderedEventsAndDeficit() {
        val result = CashFlowCalculator.forecast(100, listOf(CashFlowEvent(3, 50, "دریافت", true), CashFlowEvent(2, 200, "پرداخت", false)))
        assertEquals(-50, result.endBalanceRial)
        assertEquals(listOf(2L), result.deficitDays)
    }
}
