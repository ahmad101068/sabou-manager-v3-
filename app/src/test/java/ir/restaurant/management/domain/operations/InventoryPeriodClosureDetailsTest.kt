package ir.restaurant.management.domain.operations

import kotlin.test.assertEquals
import org.junit.Test

class InventoryPeriodClosureDetailsTest {
    @Test fun derivesQuantityAndValueVarianceFromSnapshot() {
        val line = InventoryPeriodClosureLineRecord(
            1, "برنج", "کیلو", 100, 1_000, 20, 200, 30, 300,
            -5, -50, 90, 900, 85, 850,
        )
        assertEquals(-5, line.varianceQuantityMicros)
        assertEquals(-50, line.varianceValueRial)
    }
}
