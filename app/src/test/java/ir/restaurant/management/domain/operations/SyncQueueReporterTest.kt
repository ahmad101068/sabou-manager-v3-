package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncQueueReporterTest {
    @Test fun summarizesQueue() {
        val base = SyncEnvelope("a", "SALE", 1, SyncChangeType.CREATE, "d", 10, SyncPayloadCodec.sha256("payload-a"), payload = "payload-a")
        val report = SyncQueueReporter.summarize(listOf(base, base.copy(changeId = "b", state = SyncState.SYNCED), base.copy(changeId = "c", state = SyncState.CONFLICT)))
        assertEquals(3, report.total); assertEquals(1, report.pending); assertEquals(10, report.oldestPendingAt)
    }
}
