package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierPerformanceTest {
    @Test
    fun validatesAndNormalizesPurchaseReturn() {
        val result = PurchaseReturnDraft(
            purchaseOrderId = 10,
            returnEpochDay = 20_000,
            reason = "  ایراد کیفیت  ",
            lines = listOf(PurchaseReturnLineDraft(20, 1_500_000, "  بسته‌بندی آسیب‌دیده  ")),
        ).validated()

        assertEquals("ایراد کیفیت", result.reason)
        assertEquals("بسته‌بندی آسیب‌دیده", result.lines.single().reason)
    }

    @Test
    fun scorecardAssignsGlobalGradeBands() {
        val score = SupplierScorecard(1, "نمونه", 5, 9_500, 9_800, 100, 80, 0, 915)
        assertEquals("A", score.grade)
    }
}
