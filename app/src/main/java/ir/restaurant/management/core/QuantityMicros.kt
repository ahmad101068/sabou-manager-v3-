package ir.restaurant.management.core

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class QuantityMicros private constructor(val value: Long) : Comparable<QuantityMicros> {
    operator fun plus(other: QuantityMicros): QuantityMicros {
        require(other.value <= MAX_VALUE - value) {
            "جمع مقدار کالا از محدوده امن خارج می‌شود."
        }
        return QuantityMicros(value + other.value)
    }

    operator fun minus(other: QuantityMicros): QuantityMicros {
        require(value >= other.value) { "موجودی نمی‌تواند منفی شود." }
        return QuantityMicros(value - other.value)
    }

    override fun compareTo(other: QuantityMicros): Int = value.compareTo(other.value)

    companion object {
        const val SCALE: Long = 1_000_000L
        const val MAX_VALUE: Long = 1_000_000_000L * SCALE
        val ZERO: QuantityMicros = QuantityMicros(0)

        fun of(value: Long): QuantityMicros {
            require(value in 0..MAX_VALUE) { "مقدار کالا خارج از محدوده امن است." }
            return QuantityMicros(value)
        }

        fun positive(value: Long): QuantityMicros {
            require(value > 0) { "مقدار کالا باید بیشتر از صفر باشد." }
            return of(value)
        }

        fun parse(value: String): QuantityMicros {
            val scaled = value.trim()
                .toBigDecimal()
                .setScale(6, RoundingMode.UNNECESSARY)
                .multiply(BigDecimal.valueOf(SCALE))
                .toLongExactCompat()
            return of(scaled)
        }
    }
}
