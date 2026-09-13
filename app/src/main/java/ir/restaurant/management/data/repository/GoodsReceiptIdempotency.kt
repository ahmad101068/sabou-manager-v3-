package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.GoodsReceiptEntity
import ir.restaurant.management.data.db.GoodsReceiptLineEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft

/**
 * Persistence-boundary equivalence check for the natural goods-receipt command key.
 * A matching key is a replay only when every business-relevant field is identical.
 */
internal object GoodsReceiptIdempotency {
    fun existingReplayIdOrThrow(
        existing: GoodsReceiptEntity?,
        existingLines: List<GoodsReceiptLineEntity>,
        currentOrderLines: List<PurchaseOrderLineEntity>,
        draft: GoodsReceiptDraft,
    ): Long? {
        if (existing == null) return null

        val commandKey = "GOODS_RECEIPT:${draft.purchaseOrderId}:${draft.deliveryNoteNo}"
        val persistedByOrderLine = existingLines.associateBy { it.purchaseOrderLineId }
        val currentById = currentOrderLines.associateBy { it.id }
        val sameHeader = existing.purchaseOrderId == draft.purchaseOrderId &&
            existing.deliveryNoteNo == draft.deliveryNoteNo &&
            existing.receiptEpochDay == draft.receiptEpochDay &&
            existing.note == draft.note
        val sameLines = persistedByOrderLine.size == draft.lines.size && draft.lines.all { requested ->
            val persisted = persistedByOrderLine[requested.purchaseOrderLineId] ?: return@all false
            val orderLine = currentById[requested.purchaseOrderLineId] ?: return@all false
            val expectedRejected = if (draft.finalizeOrder) {
                // Finalization records every still-unreceived unit as rejected. receivedQtyMicros is
                // cumulative and is not reduced by a later supplier return.
                orderLine.orderedQtyMicros - orderLine.receivedQtyMicros
            } else {
                requested.deliveredQtyMicros - requested.acceptedQtyMicros
            }
            persisted.itemId == orderLine.itemId &&
                persisted.deliveredQtyMicros == requested.deliveredQtyMicros &&
                persisted.acceptedQtyMicros == requested.acceptedQtyMicros &&
                persisted.rejectedQtyMicros == expectedRejected &&
                persisted.rejectionReason == requested.rejectionReason &&
                persisted.lotNumber == requested.lotNumber &&
                persisted.supplierLotNumber == requested.supplierLotNumber &&
                persisted.productionEpochDay == requested.productionEpochDay &&
                persisted.expiryEpochDay == requested.expiryEpochDay &&
                persisted.lotBarcode == requested.lotBarcode
        }

        if (!sameHeader || !sameLines) {
            throw BusinessError.IdempotencyConflict(commandKey).asViolation()
        }
        return existing.id
    }
}
