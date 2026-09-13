package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityFormatterTest {
    @Test fun formatsSignedInventoryMovementsCorrectly() {
        assertEquals("2", formatQuantity(2_000_000))
        assertEquals("-0.1", formatQuantity(-100_000))
        assertEquals("-1.25", formatQuantity(-1_250_000))
    }

    @Test fun formatsLongMinimumWithoutOverflow() {
        assertEquals("-9223372036854.775808", formatQuantity(Long.MIN_VALUE))
    }
}
