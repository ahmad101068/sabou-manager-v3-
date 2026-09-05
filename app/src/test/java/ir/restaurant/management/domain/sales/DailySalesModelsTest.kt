package ir.restaurant.management.domain.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DailySalesModelsTest {
    @Test
    fun reversalNormalizesReasonAndAcceptsSameOrLaterDay() {
        val result = DailySalesReversalDraft(7, 20001, "  اصلاح مبلغ صندوق  ").validated(20000)

        assertEquals("اصلاح مبلغ صندوق", result.reason)
        assertEquals(20001L, result.reversalEpochDay)
    }

    @Test
    fun reversalRejectsDateBeforeOriginalSale() {
        assertThrows(IllegalArgumentException::class.java) {
            DailySalesReversalDraft(7, 19999, "اصلاح مبلغ").validated(20000)
        }
    }
}
