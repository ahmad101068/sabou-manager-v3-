package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun rial_is_persian_grouped_and_explicitly_labeled() {
        assertEquals("۵٬۰۰۰٬۰۰۰ ریال", formatMoney(5_000_000L, CurrencyUnit.RIAL))
        assertEquals("۰ ریال", formatMoney(0L, CurrencyUnit.RIAL))
    }

    @Test
    fun percent_basis_points_uses_persian_digits_and_percent_sign() {
        assertEquals("۳۵.۲۵٪", formatPercentBasisPoints(3_525L))
        assertEquals("۰.۰۰٪", formatPercentBasisPoints(0L))
    }
}
