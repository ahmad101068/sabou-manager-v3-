package ir.restaurant.management.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianDateTest {
    @Test
    fun knownGregorianDate_convertsToPersian() {
        val date = epochDayToPersian(LocalDate.of(2026, 7, 29).toEpochDay())
        assertEquals(PersianDate(1405, 5, 7), date)
    }

    @Test
    fun persianNewYear_roundTrips() {
        val persian = PersianDate(1403, 1, 1)
        assertEquals(LocalDate.of(2024, 3, 20).toEpochDay(), persian.toEpochDay())
        assertEquals(persian, epochDayToPersian(persian.toEpochDay()))
    }

    @Test
    fun leapYear_hasThirtyDaysInEsfand() {
        assertTrue(isPersianLeapYear(1403))
        assertFalse(isPersianLeapYear(1404))
        assertEquals(30, daysInPersianMonth(1403, 12))
        assertEquals(29, daysInPersianMonth(1404, 12))
    }
}
