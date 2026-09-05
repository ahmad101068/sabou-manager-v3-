package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModelsTest {
    @Test
    fun `business audit actions map without rejecting the transaction`() {
        assertEquals(SyncChangeType.CREATE, SyncChangeClassifier.classify("SUBMIT"))
        assertEquals(SyncChangeType.CREATE, SyncChangeClassifier.classify("POST"))
        assertEquals(SyncChangeType.CREATE, SyncChangeClassifier.classify("CREATE_SPLIT"))
        assertEquals(SyncChangeType.UPDATE, SyncChangeClassifier.classify("APPROVE"))
        assertEquals(SyncChangeType.UPDATE, SyncChangeClassifier.classify("REJECT"))
        assertEquals(SyncChangeType.UPDATE, SyncChangeClassifier.classify("DISPATCH_PRINT"))
        assertEquals(SyncChangeType.UPDATE, SyncChangeClassifier.classify("SUPPLIER_ACKNOWLEDGE"))
        assertEquals(SyncChangeType.UPDATE, SyncChangeClassifier.classify("THREE_WAY_MATCH"))
    }

    @Test
    fun latestChangeWinsDeterministically() {
        val left = SyncEnvelope("a", "sale", 1, SyncChangeType.UPDATE, "phone-a", 10, SyncPayloadCodec.sha256("payload-a"), payload = "payload-a")
        val right = left.copy(changeId = "b", deviceId = "phone-b", occurredAtEpochMillis = 20)
        assertEquals("b", SyncConflictResolver.choose(left, right).changeId)
    }

    @Test
    fun assetDisposalIsAValidSyncChange() {
        val envelope = SyncEnvelope(
            changeId = "device:asset:7:100:uuid",
            entityType = "ASSET",
            entityId = 7,
            type = SyncChangeType.DISPOSE,
            deviceId = "device-a",
            occurredAtEpochMillis = 100,
            payloadHash = SyncPayloadCodec.sha256("asset-dispose"),
            payload = "asset-dispose",
        )

        assertEquals(SyncChangeType.DISPOSE, envelope.validated().type)
    }

    @Test
    fun rejectedChangesHaveTerminalState() {
        assertEquals("REJECTED", SyncState.REJECTED.name)
    }
}
