package ir.restaurant.management.domain.operations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPayloadCodecTest {
    @Test
    fun canonicalPayloadIsIndependentOfMapOrder() {
        val first = SyncPayloadCodec.canonicalize(linkedMapOf("z" to "آخر", "a" to "first"))
        val second = SyncPayloadCodec.canonicalize(linkedMapOf("a" to "first", "z" to "آخر"))
        assertEquals(first, second)
        assertEquals(SyncPayloadCodec.sha256(first), SyncPayloadCodec.sha256(second))
    }

    @Test
    fun hashVerificationRejectsTampering() {
        val payload = SyncPayloadCodec.canonicalize(mapOf("entityId" to "7"))
        val hash = SyncPayloadCodec.sha256(payload)
        assertTrue(SyncPayloadCodec.verify(payload, hash))
        assertFalse(SyncPayloadCodec.verify("${payload}x", hash))
    }
}
