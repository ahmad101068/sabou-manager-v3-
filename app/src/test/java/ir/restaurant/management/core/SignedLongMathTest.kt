package ir.restaurant.management.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SignedLongMathTest {
    @Test
    fun signedAdditionSupportsDebitAndCreditBalances() {
        assertEquals(-250L, SignedLongMath.add(750, -1_000))
    }

    @Test
    fun subtractionRejectsOverflow() {
        assertThrows(IllegalArgumentException::class.java) {
            SignedLongMath.subtract(Long.MAX_VALUE, -1)
        }
    }
}
