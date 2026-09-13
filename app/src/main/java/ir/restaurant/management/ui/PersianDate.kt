package ir.restaurant.management.ui

import ir.restaurant.management.core.currentLocalEpochDay
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

data class PersianDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in 1200..1600)
        require(month in 1..12)
        require(day in 1..daysInPersianMonth(year, month))
    }

    fun display(): String = "%04d/%02d/%02d".format(year, month, day)
}

private val utc = TimeZone.getTimeZone("UTC")
private const val MILLIS_PER_DAY = 86_400_000L

/** روز تقویمی جاری دستگاه؛ مستقل از اختلاف منطقه زمانی با UTC. */
fun currentEpochDay(): Long {
    return currentLocalEpochDay()
}

fun epochDayToPersian(epochDay: Long): PersianDate {
    require(epochDay in Long.MIN_VALUE / MILLIS_PER_DAY..Long.MAX_VALUE / MILLIS_PER_DAY) {
        "تاریخ خارج از محدوده امن است."
    }
    val calendar = GregorianCalendar(utc).apply {
        timeInMillis = epochDay * MILLIS_PER_DAY
    }
    return gregorianToPersian(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}

fun PersianDate.toEpochDay(): Long {
    val (year, month, day) = persianToGregorian(this.year, this.month, this.day)
    val calendar = GregorianCalendar(utc).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }
    return floorDiv(calendar.timeInMillis, MILLIS_PER_DAY)
}

fun daysInPersianMonth(year: Int, month: Int): Int = when {
    month in 1..6 -> 31
    month in 7..11 -> 30
    month == 12 && isPersianLeapYear(year) -> 30
    month == 12 -> 29
    else -> error("ماه شمسی معتبر نیست.")
}

fun isPersianLeapYear(year: Int): Boolean {
    val start = PersianDateUnchecked(year, 1, 1).toGregorianUnchecked()
    val next = PersianDateUnchecked(year + 1, 1, 1).toGregorianUnchecked()
    return gregorianEpochDay(next.first, next.second, next.third) -
        gregorianEpochDay(start.first, start.second, start.third) == 366L
}

private fun gregorianToPersian(gy: Int, gm: Int, gd: Int): PersianDate {
    val monthOffsets = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val adjustedYear = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy +
        (adjustedYear + 3) / 4 -
        (adjustedYear + 99) / 100 +
        (adjustedYear + 399) / 400 +
        gd + monthOffsets[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm: Int
    val jd: Int
    if (days < 186) {
        jm = 1 + days / 31
        jd = 1 + days % 31
    } else {
        jm = 7 + (days - 186) / 30
        jd = 1 + (days - 186) % 30
    }
    return PersianDate(jy, jm, jd)
}

private fun persianToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> =
    PersianDateUnchecked(jy, jm, jd).toGregorianUnchecked()

private data class PersianDateUnchecked(val year: Int, val month: Int, val day: Int) {
    fun toGregorianUnchecked(): Triple<Int, Int, Int> {
        val jy = year + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33 + 3) / 4) + day
        days += if (month < 7) (month - 1) * 31 else (month - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val monthDays = intArrayOf(
            0,
            31,
            if (isGregorianLeapYear(gy)) 29 else 28,
            31,
            30,
            31,
            30,
            31,
            31,
            30,
            31,
            30,
            31,
        )
        var gm = 1
        while (gm <= 12 && gd > monthDays[gm]) {
            gd -= monthDays[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }
}

private fun isGregorianLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun gregorianEpochDay(year: Int, month: Int, day: Int): Long {
    val calendar = GregorianCalendar(utc).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
    }
    return floorDiv(calendar.timeInMillis, MILLIS_PER_DAY)
}

private fun floorDiv(value: Long, divisor: Long): Long {
    var quotient = value / divisor
    if (value < 0 && value % divisor != 0L) quotient--
    return quotient
}
