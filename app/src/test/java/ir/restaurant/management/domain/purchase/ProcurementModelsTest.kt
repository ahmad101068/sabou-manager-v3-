package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcurementModelsTest {
    @Test
    fun requisitionNormalizesDepartmentAndKeepsSafeQuantities() {
        val result = PurchaseRequisitionDraft(
            department = "  آشپزخانه  ",
            requiredEpochDay = 20_000,
            lines = listOf(RequisitionLineDraft(1, 2_500_000, 300_000)),
            branchId = 7,
            destinationLocationId = 11,
        ).validated()

        assertEquals("آشپزخانه", result.department)
        assertEquals(2_500_000, result.lines.single().quantityMicros)
    }

    @Test
    fun finalReceiptMayCloseAZeroDeliveryAsDocumentedShortage() {
        val result = GoodsReceiptDraft(
            purchaseOrderId = 2,
            receiptEpochDay = 20_001,
            deliveryNoteNo = "DN-1",
            finalizeOrder = true,
            lines = listOf(GoodsReceiptLineDraft(3, 0, 0, "عدم تحویل تأمین‌کننده")),
            destinationLocationId = 11,
        ).validated()

        assertTrue(result.finalizeOrder)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroEstimatedPriceCannotBypassApprovalOrBudget() {
        PurchaseRequisitionDraft(
            department = "آشپزخانه",
            requiredEpochDay = 20_000,
            lines = listOf(RequisitionLineDraft(1, 1_000_000, 0)),
        ).validated()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectedGoodsRequireReason() {
        GoodsReceiptDraft(
            purchaseOrderId = 2,
            receiptEpochDay = 20_001,
            deliveryNoteNo = "DN-1",
            lines = listOf(GoodsReceiptLineDraft(3, 1_000_000, 500_000)),
        ).validated()
    }
}
