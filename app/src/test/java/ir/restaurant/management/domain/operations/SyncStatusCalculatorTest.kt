package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusCalculatorTest {
    @Test fun conflictsTakePriorityOverPending() {
        val base = SyncEnvelope("a", "sale", 1, SyncChangeType.UPDATE, "phone", 10, SyncPayloadCodec.sha256("payload-a"), payload = "payload-a")
        val result = SyncStatusCalculator.summarize(listOf(base, base.copy(changeId = "b", state = SyncState.CONFLICT, occurredAtEpochMillis = 20)))
        assertEquals(SyncHealth.NEEDS_ATTENTION, result.health)
        assertEquals(20L, result.lastChangeEpochMillis)
    }
}
