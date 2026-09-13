package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.GoodsReceiptEntity
import ir.restaurant.management.data.db.GoodsReceiptLineEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.GoodsReceiptLineDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GoodsReceiptIdempotencyTest {
    @Test
    fun identicalNaturalKeyAndPayloadReturnOriginalReceipt() {
        assertEquals(
            RECEIPT_ID,
            GoodsReceiptIdempotency.existingReplayIdOrThrow(
                existing = receipt(),
                existingLines = listOf(receiptLine(rejected = 200_000)),
                currentOrderLines = listOf(orderLine(received = 700_000)),
                draft = draft(),
            ),
        )
    }

    @Test
    fun sameNaturalKeyWithDifferentPayloadIsAConflict() {
        try {
            GoodsReceiptIdempotency.existingReplayIdOrThrow(
                existing = receipt(),
                existingLines = listOf(receiptLine(rejected = 200_000)),
                currentOrderLines = listOf(orderLine(received = 700_000)),
                draft = draft().copy(note = "payload changed"),
            )
            fail("a natural-key collision must not be treated as a replay")
        } catch (error: BusinessRuleViolation) {
            assertTrue(error.error is BusinessError.IdempotencyConflict)
        }
    }

    @Test
    fun finalizeReplayReconstructsThePersistedUnreceivedQuantity() {
        val finalized = draft().copy(
            finalizeOrder = true,
            lines = listOf(
                GoodsReceiptLineDraft(
                    purchaseOrderLineId = ORDER_LINE_ID,
                    deliveredQtyMicros = 700_000,
                    acceptedQtyMicros = 700_000,
                    rejectionReason = "کسری تحویل نهایی",
                ),
            ),
        )
        assertEquals(
            RECEIPT_ID,
            GoodsReceiptIdempotency.existingReplayIdOrThrow(
                existing = receipt(),
                existingLines = listOf(
                    receiptLine(delivered = 700_000, accepted = 700_000, rejected = 300_000, reason = "کسری تحویل نهایی"),
                ),
                currentOrderLines = listOf(orderLine(received = 700_000)),
                draft = finalized,
            ),
        )
    }

    private fun draft() = GoodsReceiptDraft(
        purchaseOrderId = ORDER_ID,
        receiptEpochDay = 20_000,
        deliveryNoteNo = "DN-100",
        note = "sealed",
        lines = listOf(
            GoodsReceiptLineDraft(
                purchaseOrderLineId = ORDER_LINE_ID,
                deliveredQtyMicros = 900_000,
                acceptedQtyMicros = 700_000,
                rejectionReason = "آسیب بسته‌بندی",
            ),
        ),
        destinationLocationId = LOCATION_ID,
    ).validated()

    private fun receipt() = GoodsReceiptEntity(
        id = RECEIPT_ID,
        receiptNo = "GR-100",
        purchaseOrderId = ORDER_ID,
        receiptEpochDay = 20_000,
        deliveryNoteNo = "DN-100",
        receivedBy = "tester",
        note = "sealed",
        createdAtEpochMillis = 1_000,
    )

    private fun orderLine(received: Long) = PurchaseOrderLineEntity(
        id = ORDER_LINE_ID,
        purchaseOrderId = ORDER_ID,
        itemId = 3,
        itemNameSnapshot = "برنج",
        supplierSkuSnapshot = null,
        orderedQtyMicros = 1_000_000,
        unitCostRial = 500_000,
        receivedQtyMicros = received,
        rejectedQtyMicros = 300_000,
    )

    private fun receiptLine(
        delivered: Long = 900_000,
        accepted: Long = 700_000,
        rejected: Long,
        reason: String = "آسیب بسته‌بندی",
    ) = GoodsReceiptLineEntity(
        id = 1,
        goodsReceiptId = RECEIPT_ID,
        purchaseOrderLineId = ORDER_LINE_ID,
        itemId = 3,
        deliveredQtyMicros = delivered,
        acceptedQtyMicros = accepted,
        rejectedQtyMicros = rejected,
        rejectionReason = reason,
        acceptedValueRial = 350_000,
    )

    private companion object {
        const val ORDER_ID = 10L
        const val ORDER_LINE_ID = 20L
        const val RECEIPT_ID = 30L
        const val LOCATION_ID = 40L
    }
}
