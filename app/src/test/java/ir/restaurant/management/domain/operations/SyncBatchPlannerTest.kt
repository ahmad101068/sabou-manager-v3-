package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncBatchPlannerTest {
    @Test fun ordersByTimeAndLimitsBatch() {
        val a = SyncEnvelope("b", "SALE", 2, SyncChangeType.CREATE, "d", 20, SyncPayloadCodec.sha256("payload-b"), payload = "payload-b")
        val b = SyncEnvelope("a", "SALE", 1, SyncChangeType.CREATE, "d", 10, SyncPayloadCodec.sha256("payload-a"), payload = "payload-a")
        val batch = SyncBatchPlanner.plan(listOf(a, b), 1)
        assertEquals("a", batch.changes.single().changeId)
        assertTrue(batch.hasMore)
    }
}
