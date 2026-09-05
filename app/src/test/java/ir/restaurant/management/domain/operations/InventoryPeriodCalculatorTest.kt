package ir.restaurant.management.domain.operations

import kotlin.test.assertEquals
import org.junit.Test

class InventoryPeriodCalculatorTest {
    @Test
    fun reconstructsOpeningAndSeparatesPhysicalCountVariance() {
        val result = InventoryPeriodCalculator.calculate(
            countedClosingQuantityMicros = 70,
            countedClosingValueRial = 700,
            netMovementQuantityMicros = -30,
            netMovementValueRial = -300,
            netPurchaseQuantityMicros = 50,
            netPurchaseValueRial = 500,
            countAdjustmentQuantityMicros = -5,
            countAdjustmentValueRial = -50,
        )
        assertEquals(100, result.openingQuantityMicros)
        assertEquals(1_000, result.openingValueRial)
        assertEquals(75, result.recordedOutflowQuantityMicros)
        assertEquals(750, result.recordedOutflowValueRial)
        assertEquals(75, result.expectedClosingQuantityMicros)
        assertEquals(750, result.expectedClosingValueRial)
        assertEquals(-50, result.adjustmentValueRial)
    }
}
