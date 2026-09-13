package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMetricsTest {
    @Test fun calculatesSuccessRate() {
        val m = SyncMetricsCalculator.from(SyncUploadResult(listOf("a", "b"), listOf("c"), emptyList()), 120)
        assertEquals(66, m.successRatePercent)
    }

    @Test fun countAdditionDoesNotOverflowInt() {
        val metrics = SyncMetrics(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 1)
        assertEquals(33, metrics.successRatePercent)
    }
}
