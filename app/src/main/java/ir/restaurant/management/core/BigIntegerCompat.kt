package ir.restaurant.management.core

import java.math.BigDecimal
import java.math.BigInteger

private val LONG_MIN_BIG_INTEGER: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX_BIG_INTEGER: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

/** API-23 compatible exact BigInteger-to-Long conversion. */
fun BigInteger.toLongExactCompat(): Long {
    if (this < LONG_MIN_BIG_INTEGER || this > LONG_MAX_BIG_INTEGER) {
        throw ArithmeticException("BigInteger value is outside Long range: $this")
    }
    return toLong()
}

/** API-23 compatible exact BigDecimal-to-Long conversion; fractional values are rejected. */
fun BigDecimal.toLongExactCompat(): Long = toBigIntegerExact().toLongExactCompat()
