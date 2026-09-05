package ir.restaurant.management.domain.operations

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class InventoryIntelligenceModelsTest {
    @Test fun calculatesSupplierPriceChange() {
        val insight = SupplierPriceInsight(1, "گوشت", "تأمین‌کننده", 1_250_000, 1_000_000)
        assertEquals(25, insight.changePercent)
    }

    @Test fun validatesWasteInput() {
        val commandId = "123e4567-e89b-42d3-a456-426614174000"
        val value = WasteDraft(1, 500_000, 20_000, "فساد مواد", "کنترل دما", commandId).validated()
        assertEquals("فساد مواد", value.reason)
        assertEquals(commandId, value.commandId)
        assertFailsWith<IllegalArgumentException> { value.copy(quantityMicros = 0).validated() }
        assertFailsWith<IllegalArgumentException> { value.copy(commandId = "retry-key").validated() }
    }
}
