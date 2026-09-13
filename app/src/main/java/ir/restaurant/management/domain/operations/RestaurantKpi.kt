package ir.restaurant.management.domain.operations

import ir.restaurant.management.core.SignedLongMath
import java.math.BigInteger

data class RestaurantKpi(val salesRial: Long, val costRial: Long, val grossProfitRial: Long, val marginPercent: Long, val invoiceCount: Int)

object RestaurantKpiCalculator {
    fun calculate(salesRial: Long, costRial: Long, invoiceCount: Int): RestaurantKpi {
        require(salesRial >= 0 && costRial >= 0 && invoiceCount >= 0)
        val profit = SignedLongMath.subtract(salesRial, costRial)
        val margin = if (salesRial == 0L) 0 else BigInteger.valueOf(profit)
            .multiply(BigInteger.valueOf(100))
            .divide(BigInteger.valueOf(salesRial))
            .toLong()
        return RestaurantKpi(salesRial, costRial, profit, margin, invoiceCount)
    }
}
