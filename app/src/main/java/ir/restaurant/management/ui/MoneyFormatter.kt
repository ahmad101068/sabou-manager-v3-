package ir.restaurant.management.ui

import java.text.NumberFormat
import java.math.BigInteger
import java.util.Locale

enum class CurrencyUnit { RIAL, TOMAN }

object MoneyDisplayPreferences {
    @Volatile var unit: CurrencyUnit = CurrencyUnit.RIAL
}

fun currencyUnitLabel(unit: CurrencyUnit = MoneyDisplayPreferences.unit): String =
    if (unit == CurrencyUnit.RIAL) "ریال" else "تومان"

fun formatMoney(rial: Long, unit: CurrencyUnit = MoneyDisplayPreferences.unit): String {
    val formatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR"))
    return when (unit) {
        CurrencyUnit.RIAL -> "${formatter.format(rial)} ریال"
        CurrencyUnit.TOMAN -> {
            val amount = BigInteger.valueOf(rial)
            val sign = if (amount.signum() < 0) "−" else ""
            val magnitude = amount.abs()
            val parts = magnitude.divideAndRemainder(BigInteger.TEN)
            if (parts[1] == BigInteger.ZERO) {
                "$sign${formatter.format(parts[0])} تومان"
            } else {
                "$sign${formatter.format(parts[0])}٫${formatter.format(parts[1])} تومان"
            }
        }
    }
}
