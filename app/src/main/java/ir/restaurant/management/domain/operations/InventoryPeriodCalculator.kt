package ir.restaurant.management.domain.operations

import ir.restaurant.management.core.SignedLongMath

object InventoryPeriodCalculator {
    fun calculate(
        countedClosingQuantityMicros: Long,
        countedClosingValueRial: Long,
        netMovementQuantityMicros: Long,
        netMovementValueRial: Long,
        netPurchaseQuantityMicros: Long,
        netPurchaseValueRial: Long,
        countAdjustmentQuantityMicros: Long,
        countAdjustmentValueRial: Long,
    ): InventoryPeriodLineCalculation {
        require(countedClosingQuantityMicros >= 0 && countedClosingValueRial >= 0) { "موجودی پایان دوره معتبر نیست." }
        val openingQuantity = SignedLongMath.subtract(countedClosingQuantityMicros, netMovementQuantityMicros)
        val openingValue = SignedLongMath.subtract(countedClosingValueRial, netMovementValueRial)
        require(openingQuantity >= 0 && openingValue >= 0) { "مانده اول دوره از گردش انبار قابل بازسازی نیست." }
        val nonPurchaseNonCountQuantity = SignedLongMath.subtract(
            SignedLongMath.subtract(netMovementQuantityMicros, netPurchaseQuantityMicros),
            countAdjustmentQuantityMicros,
        )
        val nonPurchaseNonCountValue = SignedLongMath.subtract(
            SignedLongMath.subtract(netMovementValueRial, netPurchaseValueRial),
            countAdjustmentValueRial,
        )
        val outflowQuantity = -nonPurchaseNonCountQuantity
        val outflowValue = -nonPurchaseNonCountValue
        val expectedQuantity = SignedLongMath.subtract(countedClosingQuantityMicros, countAdjustmentQuantityMicros)
        val expectedValue = SignedLongMath.subtract(countedClosingValueRial, countAdjustmentValueRial)
        return InventoryPeriodLineCalculation(
            openingQuantity, openingValue, netPurchaseQuantityMicros, netPurchaseValueRial,
            outflowQuantity, outflowValue, countAdjustmentQuantityMicros, countAdjustmentValueRial,
            expectedQuantity, expectedValue, countedClosingQuantityMicros, countedClosingValueRial,
        )
    }
}
