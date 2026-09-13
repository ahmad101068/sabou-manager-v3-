package ir.restaurant.management.ui

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErpDisplayFormattersTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun percentage_never_formats_nan_or_infinity() {
        assertNull(ErpDisplayFormatters.percentage(Double.NaN))
        assertNull(ErpDisplayFormatters.percentage(Double.POSITIVE_INFINITY))
        assertNull(ErpDisplayFormatters.percentage(null))
    }

    @Test
    fun activityDateTime_uses_today_for_today_only() {
        val now = utcMillis(2026, 8, 13, 12, 30)
        val today = epochDay(2026, 8, 13)
        assertEquals("امروز، ۱۰:۱۵", ErpDisplayFormatters.activityDateTime(today, utcMillis(2026, 8, 13, 10, 15), now, utc))
        assertEquals("دیروز، ۱۶:۴۵", ErpDisplayFormatters.activityDateTime(today - 1, utcMillis(2026, 8, 12, 16, 45), now, utc))
    }

    @Test
    fun fileSize_uses_human_persian_units() {
        assertEquals("۵۰۰ بایت", ErpDisplayFormatters.fileSize(500))
        assertEquals("۲ کیلوبایت", ErpDisplayFormatters.fileSize(2 * 1024L))
        assertEquals("۳ مگابایت", ErpDisplayFormatters.fileSize(3 * 1024L * 1024L))
    }

    @Test
    fun timestampDateTime_does_not_label_old_backup_as_today() {
        val now = utcMillis(2026, 8, 13, 12, 30)
        val old = utcMillis(2026, 8, 11, 14, 30)
        val text = ErpDisplayFormatters.timestampDateTime(old, now, utc)
        check(!text.startsWith("امروز"))
        check(!text.startsWith("دیروز"))
        check(text.endsWith("۱۴:۳۰"))
    }

    @Test
    fun activityDateTime_old_record_uses_persian_business_date_not_today() {
        val now = utcMillis(2026, 8, 13, 12, 30)
        val old = epochDay(2026, 8, 11)
        val text = ErpDisplayFormatters.activityDateTime(old, utcMillis(2026, 8, 11, 14, 30), now, utc)
        check(!text.startsWith("امروز"))
        check(!text.startsWith("دیروز"))
        check(text.endsWith("۱۴:۳۰"))
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        GregorianCalendar(utc).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun epochDay(year: Int, month: Int, day: Int): Long =
        GregorianCalendar(utc).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis / 86_400_000L
}
