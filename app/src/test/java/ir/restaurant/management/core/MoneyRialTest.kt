package ir.restaurant.management.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyRialTest {
    @Test
    fun fractionalQuantityRoundsOnceToNearestRial() {
        val unitPrice = MoneyRial.of(101)
        val quantity = QuantityMicros.parse("0.333")

        assertEquals(34L, unitPrice.times(quantity).value)
    }

    @Test
    fun oneHundredFractionalLinesStayExact() {
        val line = MoneyRial.of(101).times(QuantityMicros.parse("0.333"))

        assertEquals(3_400L, MoneyRial.sum(List(100) { line }).value)
    }

    @Test
    fun multiplicationRejectsOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyRial.of(MoneyRial.MAX_VALUE)
                .times(QuantityMicros.of(QuantityMicros.MAX_VALUE))
        }
    }
}

