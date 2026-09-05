package ir.restaurant.management.core

import java.math.BigInteger

@JvmInline
value class MoneyRial private constructor(val value: Long) : Comparable<MoneyRial> {
    operator fun plus(other: MoneyRial): MoneyRial {
        require(other.value <= MAX_VALUE - value) {
            "جمع مبلغ‌ها از محدوده امن خارج می‌شود."
        }
        return MoneyRial(value + other.value)
    }

    operator fun minus(other: MoneyRial): MoneyRial {
        require(value >= other.value) {
            "نتیجه مبلغ نمی‌تواند منفی شود."
        }
        return MoneyRial(value - other.value)
    }

    fun times(quantity: QuantityMicros): MoneyRial {
        val numerator = BigInteger.valueOf(value)
            .multiply(BigInteger.valueOf(quantity.value))
        val scale = BigInteger.valueOf(QuantityMicros.SCALE)
        val (whole, remainder) = numerator.divideAndRemainder(scale)
        val rounded = if (remainder.shiftLeft(1) >= scale) whole + BigInteger.ONE else whole
        require(rounded <= BigInteger.valueOf(MAX_VALUE)) {
            "مبلغ ردیف خارج از محدوده امن است."
        }
        return of(rounded.toString().toLong())
    }

    override fun compareTo(other: MoneyRial): Int = value.compareTo(other.value)

    companion object {
        const val MAX_VALUE: Long = 9_000_000_000_000_000L
        val ZERO: MoneyRial = MoneyRial(0)

        fun of(value: Long): MoneyRial {
            require(value in 0..MAX_VALUE) { "مبلغ ریالی خارج از محدوده امن است." }
            return MoneyRial(value)
        }

        fun sum(values: Iterable<MoneyRial>): MoneyRial =
            values.fold(ZERO) { total, value -> total + value }
    }
}
