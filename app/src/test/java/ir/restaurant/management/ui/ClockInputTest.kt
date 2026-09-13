package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockInputTest {
    @Test
    fun clockTextConvertsToMinuteOfDay() {
        assertEquals(510, parseClockMinute("08:30"))
        assertEquals("16:05", formatMinuteOfDay(965))
        assertEquals(1440, parseClockMinute("24:00"))
    }

    @Test
    fun typedDigitsReceiveClockSeparator() {
        assertEquals("08:30", normalizeClockInput("0830"))
        assertEquals("12:45", normalizeClockInput("۱۲۴۵"))
    }
}
