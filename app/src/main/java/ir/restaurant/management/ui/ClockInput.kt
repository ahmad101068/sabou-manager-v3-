package ir.restaurant.management.ui

internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    require(minuteOfDay in 0..1440) { "زمان معتبر نیست." }
    if (minuteOfDay == 1440) return "24:00"
    return "${(minuteOfDay / 60).toString().padStart(2, '0')}:${(minuteOfDay % 60).toString().padStart(2, '0')}"
}

internal fun normalizeClockInput(value: String): String {
    val digits = normalizeNumberInput(value).filter(Char::isDigit).take(4)
    return if (digits.length <= 2) digits else "${digits.take(2)}:${digits.drop(2)}"
}

internal fun parseClockMinute(value: String): Int {
    val parts = value.split(':')
    require(parts.size == 2 && parts.all { it.isNotBlank() }) { "ساعت را به شکل 08:30 وارد کنید." }
    val hour = parts[0].toIntOrNull() ?: error("ساعت ورود یا خروج معتبر نیست.")
    val minute = parts[1].toIntOrNull() ?: error("دقیقه ورود یا خروج معتبر نیست.")
    require((hour in 0..23 && minute in 0..59) || (hour == 24 && minute == 0)) {
        "ساعت باید بین 00:00 تا 24:00 باشد."
    }
    return hour * 60 + minute
}
