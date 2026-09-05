package ir.restaurant.management.ui

import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
private val arabicDigits = "٠١٢٣٤٥٦٧٨٩"

fun normalizeNumberInput(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when {
            character in persianDigits -> append(persianDigits.indexOf(character))
            character in arabicDigits -> append(arabicDigits.indexOf(character))
            character == '٬' || character == ',' || character.isWhitespace() -> Unit
            character == '٫' -> append('.')
            else -> append(character)
        }
    }
}

fun parseMoneyRial(value: String): MoneyRial {
    val normalized = normalizeNumberInput(value)
    require(normalized.matches(Regex("""\d+"""))) {
        "مبلغ را به‌صورت عدد صحیح وارد کنید."
    }
    val entered = normalized.toLongOrNull()
        ?: throw IllegalArgumentException("مبلغ از محدوده مجاز بزرگ‌تر است.")
    val rial = if (MoneyDisplayPreferences.unit == CurrencyUnit.TOMAN) {
        SignedLongMath.multiply(entered, 10L)
    } else {
        entered
    }
    return MoneyRial.of(rial)
}

fun formatMoneyInput(value: String): String {
    val digits = normalizeNumberInput(value).filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    val normalized = digits.trimStart('0').ifEmpty { "0" }
    val maximum = BigInteger.valueOf(MoneyRial.MAX_VALUE).let {
        if (MoneyDisplayPreferences.unit == CurrencyUnit.TOMAN) it.divide(BigInteger.TEN) else it
    }
    var bounded = normalized.take(maximum.toString().length)
    while (bounded.length > 1 && BigInteger(bounded) > maximum) {
        bounded = bounded.dropLast(1)
    }
    return bounded.reversed().chunked(3).joinToString(",").reversed()
}


/** Canonical editable Rial rendering: Persian digits with the Persian thousands separator. */
fun formatRialMoneyInput(value: String): String = formatMoneyInput(value)
    .replace(',', '٬')
    .map { character -> if (character in '0'..'9') persianDigits[character - '0'] else character }
    .joinToString("")

fun formatRialMoneyInputFromRial(rial: Long): String {
    val displayValue = if (MoneyDisplayPreferences.unit == CurrencyUnit.TOMAN) rial / 10L else rial
    return formatRialMoneyInput(displayValue.toString())
}

fun parseMoneyInputOrZero(value: String): Long =
    if (value.isBlank()) 0L else parseMoneyRial(value).value

fun parseMoneyInputOrNull(value: String): Long? =
    runCatching { parseMoneyInputOrZero(value) }.getOrNull()

fun formatMoneyInputFromRial(rial: Long): String {
    val displayValue = if (MoneyDisplayPreferences.unit == CurrencyUnit.TOMAN) rial / 10L else rial
    return formatMoneyInput(displayValue.toString())
}

fun parseQuantity(value: String): QuantityMicros {
    val normalized = normalizeNumberInput(value)
    require(normalized.matches(Regex("""\d+(\.\d{1,6})?"""))) {
        "مقدار کالا معتبر نیست."
    }
    val micros = BigDecimal(normalized)
        .movePointRight(6)
        .setScale(0, RoundingMode.UNNECESSARY)
        .toLongExactCompat()
    return QuantityMicros.of(micros)
}

fun formatQuantity(micros: Long): String {
    val absolute = BigInteger.valueOf(micros).abs()
    val parts = absolute.divideAndRemainder(BigInteger.valueOf(QuantityMicros.SCALE))
    val fraction = parts[1].toString().padStart(6, '0').trimEnd('0')
    val magnitude = if (fraction.isEmpty()) parts[0].toString() else "${parts[0]}.$fraction"
    return if (micros < 0) "-$magnitude" else magnitude
}
