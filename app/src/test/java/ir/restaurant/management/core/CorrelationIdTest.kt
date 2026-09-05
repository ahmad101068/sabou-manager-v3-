package ir.restaurant.management.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrelationIdTest {
    @Test
    fun commandCorrelationIsStableAndNormalized() {
        val commandId = GlobalId.parse("123e4567-e89b-42d3-a456-426614174000")
        val first = CorrelationId.forCommand("Goods_Receipt", commandId)
        val retry = CorrelationId.forCommand("goods_receipt", commandId)

        assertEquals(first, retry)
        assertEquals("goods_receipt:${commandId.value}", first.value)
    }

    @Test
    fun generatedCorrelationCarriesOperationPrefix() {
        assertTrue(CorrelationId.new("purchase").value.startsWith("purchase:"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOpaqueShortRandomStrings() {
        CorrelationId.parse("random")
    }
}
