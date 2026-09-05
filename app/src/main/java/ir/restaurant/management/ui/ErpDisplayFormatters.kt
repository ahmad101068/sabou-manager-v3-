package ir.restaurant.management.ui

import java.text.NumberFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/** Presentation-only formatters shared by ERP dashboards. No business calculation belongs here. */
object ErpDisplayFormatters {
    private val faLocale: Locale = Locale.forLanguageTag("fa-IR")
    private val persianMonths = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun money(rial: Long, unit: CurrencyUnit = MoneyDisplayPreferences.unit): String =
        formatMoney(rial, unit)

    fun integer(value: Long): String = NumberFormat.getIntegerInstance(faLocale).format(value)

    fun integer(value: Int): String = integer(value.toLong())

    fun percentage(value: Double?): String? {
        if (value == null || !value.isFinite()) return null
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        val formatter = NumberFormat.getNumberInstance(faLocale).apply {
            minimumFractionDigits = if (rounded % 1.0 == 0.0) 0 else 1
            maximumFractionDigits = 1
        }
        val sign = if (rounded > 0) "+" else ""
        return "$sign${formatter.format(rounded)}٪"
    }


    fun fileSize(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        val value: Double
        val unit: String
        when {
            safe >= 1024L * 1024L * 1024L -> { value = safe / (1024.0 * 1024.0 * 1024.0); unit = "گیگابایت" }
            safe >= 1024L * 1024L -> { value = safe / (1024.0 * 1024.0); unit = "مگابایت" }
            safe >= 1024L -> { value = safe / 1024.0; unit = "کیلوبایت" }
            else -> return "${integer(safe)} بایت"
        }
        val formatter = NumberFormat.getNumberInstance(faLocale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
        return "${formatter.format(value)} $unit"
    }

    fun timestampDateTime(
        epochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String = activityDateTime(
        businessEpochDay = localEpochDay(epochMillis, timeZone),
        createdAtEpochMillis = epochMillis,
        nowEpochMillis = nowEpochMillis,
        timeZone = timeZone,
    )

    /**
     * Human relative date/time for persisted timestamps.
     * The business epoch day is accepted separately because ERP records may be backdated while
     * createdAt remains today. In that case the business date wins for the day label.
     */
    fun activityDateTime(
        businessEpochDay: Long,
        createdAtEpochMillis: Long?,
        nowEpochMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val todayEpochDay = localEpochDay(nowEpochMillis, timeZone)
        val prefix = when (businessEpochDay) {
            todayEpochDay -> "امروز"
            todayEpochDay - 1 -> "دیروز"
            else -> {
                val persian = epochDayToPersian(businessEpochDay)
                "${toPersianDigits(persian.day.toString())} ${persianMonths[persian.month - 1]}"
            }
        }
        val clock = createdAtEpochMillis?.let { timestamp ->
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
            "%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        }?.let(::toPersianDigits)
        return if (clock == null) prefix else "$prefix، $clock"
    }

    private fun localEpochDay(epochMillis: Long, timeZone: TimeZone): Long {
        val local = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
        val utcMidnight = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }
        return utcMidnight.timeInMillis / 86_400_000L
    }
}
