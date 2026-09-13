package ir.restaurant.management.core

import java.math.BigInteger

object SignedLongMath {
    private val minimum = BigInteger.valueOf(Long.MIN_VALUE)
    private val maximum = BigInteger.valueOf(Long.MAX_VALUE)

    fun add(left: Long, right: Long): Long =
        checked(BigInteger.valueOf(left).add(BigInteger.valueOf(right)))

    fun subtract(left: Long, right: Long): Long =
        checked(BigInteger.valueOf(left).subtract(BigInteger.valueOf(right)))

    fun multiply(left: Long, right: Long): Long =
        checked(BigInteger.valueOf(left).multiply(BigInteger.valueOf(right)))

    private fun checked(value: BigInteger): Long {
        require(value in minimum..maximum) {
            "محاسبه مانده از محدوده امن خارج می‌شود."
        }
        return value.toLong()
    }
}
