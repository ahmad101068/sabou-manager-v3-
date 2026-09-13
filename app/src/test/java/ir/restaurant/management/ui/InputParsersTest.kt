package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.After
import org.junit.Test

class InputParsersTest {
    @After
    fun resetCurrencyUnit() {
        MoneyDisplayPreferences.unit = CurrencyUnit.RIAL
    }

    @Test
    fun persianMoneyInput_isNormalizedToRial() {
        assertEquals(12_345_678L, parseMoneyRial("۱۲٬۳۴۵٬۶۷۸").value)
    }

    @Test
    fun moneyInput_isGroupedWhileTypingAndStillParses() {
        assertEquals("12,345,678", formatMoneyInput("۱۲۳۴۵۶۷۸"))
        assertEquals(12_345_678L, parseMoneyInputOrZero("12,345,678"))
    }

    @Test
    fun moneyInput_rejectsDigitsBeyondSafeRialRangeWhileTyping() {
        MoneyDisplayPreferences.unit = CurrencyUnit.RIAL
        assertEquals("9,000,000,000,000,000", formatMoneyInput("90000000000000000"))
        assertEquals("999,999,999,999,999", formatMoneyInput("9999999999999999"))
    }

    @Test
    fun oversizedMoneyParse_returnsControlledValidationError() {
        assertThrows(IllegalArgumentException::class.java) {
            parseMoneyRial("999999999999999999999999")
        }
        assertNull(parseMoneyInputOrNull("999999999999999999999999"))
    }

    @Test
    fun treasuryRialInput_usesPersianDigitsAndArabicThousandsSeparator() {
        assertEquals("۰", formatRialMoneyInput("0"))
        assertEquals("۵", formatRialMoneyInput("5"))
        assertEquals("۵٬۰۰۰", formatRialMoneyInput("5000"))
        assertEquals("۵٬۰۰۰٬۰۰۰", formatRialMoneyInput("5000000"))
        assertEquals("۵٬۰۰۰٬۰۰۰", formatRialMoneyInput("۵۰۰۰۰۰۰"))
        assertEquals(5_000_000L, parseMoneyInputOrNull(formatRialMoneyInput("۵۰۰۰۰۰۰")))
    }

    @Test
    fun treasuryRialInput_backspaceAndOversizeRemainSafe() {
        assertEquals("۵۰۰٬۰۰۰", formatRialMoneyInput("۵٬۰۰۰٬۰۰۰".dropLast(1)))
        assertEquals("۵۰٬۰۰۰", formatRialMoneyInput("۵٬۰۰۰٬۰۰۰".dropLast(2)))
        assertNull(parseMoneyInputOrNull("۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹۹"))
        assertEquals("۹٬۰۰۰٬۰۰۰٬۰۰۰٬۰۰۰٬۰۰۰", formatRialMoneyInput("90000000000000000"))
    }

    @Test
    fun decimalQuantity_hasSixDigitPrecision() {
        assertEquals(1_250_000L, parseQuantity("۱٫۲۵").value)
    }

    @Test
    fun quantityWithMoreThanSixDecimals_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseQuantity("1.0000001")
        }
    }
}
