package ir.restaurant.management.core

import java.math.BigInteger

enum class FixedPointRounding { DOWN, HALF_UP }

/** Overflow-safe integer ratios used for money/quantity conversion. */
object FixedPointRatio {
    fun multiplyDivide(
        value: Long,
        multiplier: Long,
        divisor: Long,
        rounding: FixedPointRounding = FixedPointRounding.DOWN,
    ): Long {
        require(value >= 0 && multiplier >= 0) { "مقادیر نسبت نمی‌توانند منفی باشند." }
        require(divisor > 0) { "مخرج نسبت باید بیشتر از صفر باشد." }
        val numerator = BigInteger.valueOf(value).multiply(BigInteger.valueOf(multiplier))
        val denominator = BigInteger.valueOf(divisor)
        val (whole, remainder) = numerator.divideAndRemainder(denominator)
        val rounded = when (rounding) {
            FixedPointRounding.DOWN -> whole
            FixedPointRounding.HALF_UP -> if (remainder.shiftLeft(1) >= denominator) {
                whole + BigInteger.ONE
            } else {
                whole
            }
        }
        require(rounded <= BigInteger.valueOf(Long.MAX_VALUE)) {
            "نتیجه نسبت از محدوده امن خارج می‌شود."
        }
        return rounded.toLong()
    }

    fun unitCostRial(totalRial: Long, quantityMicros: Long): Long {
        MoneyRial.of(totalRial)
        QuantityMicros.positive(quantityMicros)
        return multiplyDivide(totalRial, QuantityMicros.SCALE, quantityMicros)
    }
}
