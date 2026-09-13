package ir.restaurant.management.core

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BigIntegerCompatTest {
    @Test fun `converts values inside Long range exactly`() {
        assertEquals(Long.MIN_VALUE, BigInteger.valueOf(Long.MIN_VALUE).toLongExactCompat())
        assertEquals(0L, BigInteger.ZERO.toLongExactCompat())
        assertEquals(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE).toLongExactCompat())
    }

    @Test fun `big decimal conversion stays exact`() {
        assertEquals(42L, BigDecimal("42.000").toLongExactCompat())
        assertThrows(ArithmeticException::class.java) { BigDecimal("42.1").toLongExactCompat() }
    }

    @Test fun `rejects values above Long range`() {
        val value = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        assertThrows(ArithmeticException::class.java) { value.toLongExactCompat() }
    }

    @Test fun `rejects values below Long range`() {
        val value = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        assertThrows(ArithmeticException::class.java) { value.toLongExactCompat() }
    }
}
