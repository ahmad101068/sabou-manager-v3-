package ir.restaurant.management.domain.operations

import ir.restaurant.management.domain.sales.SalesDayReopenDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlledReopenDraftTest {
    @Test fun inventoryReopenRequiresAuditableReason() {
        assertEquals("اصلاح شمارش نهایی", InventoryPeriodReopenDraft(7, "  اصلاح شمارش نهایی  ").validated().reason)
        assertFailsWith<IllegalArgumentException> { InventoryPeriodReopenDraft(7, "کم").validated() }
    }

    @Test fun salesReopenRequiresValidDayAndReason() {
        assertEquals("اصلاح مغایرت کارتخوان", SalesDayReopenDraft(1, 20_000, " اصلاح مغایرت کارتخوان ").validated().reason)
        assertFailsWith<IllegalArgumentException> { SalesDayReopenDraft(1, 0, "دلیل معتبر").validated() }
    }
}
