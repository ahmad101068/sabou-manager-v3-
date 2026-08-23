package ir.restaurant.management.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Canonical restaurant business calendar. Business dates are Tehran-local calendar dates. */
object BusinessCalendar {
    val zoneId: ZoneId = ZoneId.of("Asia/Tehran")

    fun epochDayAt(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toEpochDay()

    fun startOfDayEpochMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun epochMillisAtMinute(epochDay: Long, minuteOfDay: Int): Long {
        require(minuteOfDay in 0..1439) { "دقیقه روز معتبر نیست." }
        return LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(zoneId)
            .plusMinutes(minuteOfDay.toLong())
            .toInstant()
            .toEpochMilli()
    }
}
