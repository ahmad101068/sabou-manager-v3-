package ir.restaurant.management.application.procurement

import ir.restaurant.management.domain.purchase.*

/** Application boundary for procurement commands and workflow policy. */
class ProcurementUseCases(
    private val repository: ProcurementRepository,
    private val purchases: PurchaseRepository? = null,
) {
    val overview get() = repository.overview

    suspend fun submitRequisition(draft: PurchaseRequisitionDraft) = repository.submitRequisition(draft.validated())

    suspend fun reviewRequisition(id: Long, approve: Boolean, note: String = "") {
        require(id > 0) { "شناسه درخواست خرید معتبر نیست." }
        val normalizedNote = note.trim()
        require(normalizedNote.length <= 300) { "یادداشت بررسی بیش از حد طولانی است." }
        if (!approve) require(normalizedNote.length >= 3) { "برای رد درخواست خرید دلیل ثبت کنید." }
        repository.reviewRequisition(id, approve, normalizedNote)
    }

    suspend fun createOrder(draft: PurchaseOrderDraft) = repository.createOrder(draft.validated())
    suspend fun createSplitOrders(draft: SplitPurchaseOrdersDraft) = repository.createSplitOrders(draft.validated())

    suspend fun markOrderSent(id: Long, channel: PurchaseOrderDispatchChannel) {
        require(id > 0) { "شناسه سفارش خرید معتبر نیست." }
        repository.markOrderSent(id, channel)
    }

    suspend fun acknowledge(draft: PurchaseOrderAcknowledgementDraft) = repository.acknowledgeOrder(draft.validated())
    suspend fun receive(draft: GoodsReceiptDraft) = repository.postGoodsReceipt(draft.validated())
    suspend fun returnGoods(draft: PurchaseReturnDraft) = repository.postPurchaseReturn(draft.validated())
    suspend fun saveReplenishmentPolicy(draft: ReplenishmentPolicyDraft) = repository.saveReplenishmentPolicy(draft.validated())
    suspend fun saveSupplierOffer(draft: SupplierOfferDraft) = repository.saveSupplierOffer(draft.validated())

    suspend fun submitSuggestedRequisition(itemIds: List<Long>) {
        val normalized = itemIds.filter { it > 0 }.distinct().take(100)
        require(normalized.isNotEmpty()) { "حداقل یک کالای معتبر برای درخواست پیشنهادی لازم است." }
        repository.submitSuggestedRequisition(normalized)
    }

    suspend fun previewThreeWayMatch(orderId: Long, invoice: PurchaseDraft): ThreeWayMatchResult {
        require(orderId > 0) { "شناسه سفارش خرید معتبر نیست." }
        return repository.previewThreeWayMatch(orderId, invoice)
    }

    suspend fun postMatchedInvoice(orderId: Long, invoice: PurchaseDraft, approvePriceVariance: Boolean) : PostedPurchase {
        require(orderId > 0) { "شناسه سفارش خرید معتبر نیست." }
        return repository.postMatchedInvoice(orderId, invoice, approvePriceVariance)
    }

    suspend fun postPurchase(draft: PurchaseDraft): PostedPurchase = purchaseBoundary().post(draft)

    suspend fun settlePurchase(draft: PurchaseSettlementDraft): PostedPurchaseSettlement =
        purchaseBoundary().settle(draft.validated())

    suspend fun reverseSettlement(draft: PurchaseSettlementReversalDraft) =
        purchaseBoundary().reverseSettlement(draft.validated())

    suspend fun reversePurchase(draft: PurchaseReversalDraft) =
        purchaseBoundary().reverse(draft.validated())

    private fun purchaseBoundary(): PurchaseRepository =
        requireNotNull(purchases) { "Purchase application boundary is not configured." }
}
