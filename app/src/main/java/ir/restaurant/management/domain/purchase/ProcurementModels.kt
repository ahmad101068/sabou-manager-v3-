package ir.restaurant.management.domain.purchase

import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow

enum class RequisitionStatus { SUBMITTED, PENDING_SECOND_APPROVAL, APPROVED, REJECTED, CONVERTED }
enum class PurchaseOrderStatus { OPEN, PARTIALLY_RECEIVED, RECEIVED, CLOSED, CANCELLED }
enum class ThreeWayMatchStatus { MATCHED, PRICE_VARIANCE, QUANTITY_VARIANCE }
enum class PurchaseOrderDispatchChannel { PRINT, SHARE, OTHER }

data class PurchaseApprovalPlan(val requiredLevel: Int, val requiresOwnerAtFinalLevel: Boolean)

class PurchaseApprovalPolicy(
    val secondApprovalThresholdRial: Long = SECOND_APPROVAL_THRESHOLD_RIAL,
) {
    init {
        require(secondApprovalThresholdRial > 0) { "آستانه تأیید دوم باید مثبت باشد." }
    }

    fun plan(estimatedTotalRial: Long): PurchaseApprovalPlan {
        require(estimatedTotalRial >= 0) { "مبلغ درخواست نمی‌تواند منفی باشد." }
        val levels = if (estimatedTotalRial >= secondApprovalThresholdRial) 2 else 1
        return PurchaseApprovalPlan(levels, levels == 2)
    }

    companion object {
        const val SECOND_APPROVAL_THRESHOLD_RIAL = 500_000_000L
        private val default = PurchaseApprovalPolicy()

        fun plan(estimatedTotalRial: Long): PurchaseApprovalPlan = default.plan(estimatedTotalRial)
    }
}

data class RequisitionLineDraft(
    val itemId: Long,
    val quantityMicros: Long,
    val estimatedUnitCostRial: Long,
    val recommendedSupplierId: Long? = null,
    val supplierSku: String? = null,
    val recommendedLeadTimeDays: Int? = null,
    val note: String = "",
)

data class PurchaseRequisitionDraft(
    val department: String,
    val requiredEpochDay: Long,
    val note: String = "",
    val lines: List<RequisitionLineDraft>,
    val branchId: Long = 0L,
    val destinationLocationId: Long = 0L,
) {
    fun validated(): PurchaseRequisitionDraft {
        val normalizedDepartment = department.trim()
        require(branchId > 0 && destinationLocationId > 0) { "شعبه و انبار مقصد درخواست خرید الزامی است." }
        require(normalizedDepartment.length in 2..80) { "واحد درخواست‌کننده معتبر نیست." }
        require(note.trim().length <= 300) { "توضیحات درخواست بیش از حد طولانی است." }
        require(lines.isNotEmpty() && lines.size <= 100) { "درخواست خرید باید بین ۱ تا ۱۰۰ ردیف داشته باشد." }
        require(lines.map { it.itemId }.distinct().size == lines.size) { "یک کالا در درخواست خرید تکرار شده است." }
        require(lines.all { it.itemId > 0 && it.quantityMicros > 0 && it.estimatedUnitCostRial > 0 }) {
            "مقدار یا برآورد یکی از ردیف‌های درخواست معتبر نیست."
        }
        lines.forEach {
            require(it.note.trim().length <= 200) { "توضیحات یکی از ردیف‌ها بیش از حد طولانی است." }
            QuantityMicros.positive(it.quantityMicros)
            MoneyRial.of(it.estimatedUnitCostRial).times(QuantityMicros.of(it.quantityMicros))
            require(it.recommendedSupplierId == null || it.recommendedSupplierId > 0) { "تأمین‌کننده پیشنهادی معتبر نیست." }
            require((it.supplierSku?.trim()?.length ?: 0) <= 80) { "کد کالای تأمین‌کننده بیش از حد طولانی است." }
            require(it.recommendedLeadTimeDays == null || it.recommendedLeadTimeDays in 0..180) { "زمان تحویل پیشنهادی معتبر نیست." }
        }
        return copy(
            department = normalizedDepartment,
            note = note.trim(),
            lines = lines.map { it.copy(note = it.note.trim(), supplierSku = it.supplierSku?.trim()?.ifBlank { null }) },
        )
    }
}

data class PurchaseOrderDraft(
    val requisitionId: Long,
    val supplierId: Long,
    val orderEpochDay: Long,
    val expectedEpochDay: Long,
    val note: String = "",
) {
    fun validated(): PurchaseOrderDraft {
        require(requisitionId > 0 && supplierId > 0) { "درخواست خرید و تأمین‌کننده الزامی است." }
        require(expectedEpochDay >= orderEpochDay) { "تاریخ تحویل نمی‌تواند قبل از تاریخ سفارش باشد." }
        require(note.trim().length <= 300) { "توضیحات سفارش بیش از حد طولانی است." }
        return copy(note = note.trim())
    }
}

data class GoodsReceiptLineDraft(
    val purchaseOrderLineId: Long,
    val deliveredQtyMicros: Long,
    val acceptedQtyMicros: Long,
    val rejectionReason: String = "",
    val lotNumber: String? = null,
    val supplierLotNumber: String? = null,
    val productionEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val lotBarcode: String? = null,
)

data class GoodsReceiptDraft(
    val purchaseOrderId: Long,
    val receiptEpochDay: Long,
    val deliveryNoteNo: String,
    val note: String = "",
    val finalizeOrder: Boolean = false,
    val lines: List<GoodsReceiptLineDraft>,
    val destinationLocationId: Long = 0L,
) {
    fun validated(): GoodsReceiptDraft {
        require(purchaseOrderId > 0) { "سفارش خرید معتبر نیست." }
        require(destinationLocationId > 0) { "انبار مقصد رسید کالا باید صریح انتخاب شود." }
        require(deliveryNoteNo.trim().length in 1..80) { "شماره حواله تحویل الزامی است." }
        require(note.trim().length <= 300) { "توضیحات رسید بیش از حد طولانی است." }
        require(lines.isNotEmpty() && lines.size <= 100) { "حداقل یک ردیف تحویل لازم است." }
        require(lines.map { it.purchaseOrderLineId }.distinct().size == lines.size) { "یک ردیف سفارش در رسید تکرار شده است." }
        lines.forEach {
            require(it.purchaseOrderLineId > 0) { "ردیف سفارش معتبر نیست." }
            require(it.deliveredQtyMicros >= 0) { "مقدار تحویل نمی‌تواند منفی باشد." }
            require(it.acceptedQtyMicros in 0..it.deliveredQtyMicros) { "مقدار پذیرفته‌شده از مقدار تحویل بیشتر است." }
            QuantityMicros.of(it.deliveredQtyMicros)
            QuantityMicros.of(it.acceptedQtyMicros)
            require(it.deliveredQtyMicros == it.acceptedQtyMicros || it.rejectionReason.trim().length >= 3) {
                "برای کسری یا کالای ردشده دلیل ثبت کنید."
            }
            require(it.rejectionReason.trim().length <= 300) { "دلیل مغایرت بیش از حد طولانی است." }
            require(it.lotNumber == null || it.lotNumber.trim().length in 1..80) { "شماره لات معتبر نیست." }
            require(it.supplierLotNumber == null || it.supplierLotNumber.trim().length in 1..80) {
                "شماره لات تأمین‌کننده معتبر نیست."
            }
            require(it.productionEpochDay == null || it.productionEpochDay <= receiptEpochDay) {
                "تاریخ تولید نمی‌تواند پس از تاریخ دریافت باشد."
            }
            require(it.expiryEpochDay == null || it.expiryEpochDay >= (it.productionEpochDay ?: receiptEpochDay)) {
                "تاریخ انقضا نمی‌تواند پیش از تولید یا دریافت باشد."
            }
            require(it.lotBarcode == null || it.lotBarcode.trim().length in 4..80) { "بارکد لات معتبر نیست." }
        }
        require(lines.any { it.deliveredQtyMicros > 0 } || finalizeOrder) { "حداقل یک مقدار تحویلی لازم است." }
        return copy(
            deliveryNoteNo = deliveryNoteNo.trim(),
            note = note.trim(),
            lines = lines.map {
                it.copy(
                    rejectionReason = it.rejectionReason.trim(),
                    lotNumber = it.lotNumber?.trim()?.ifBlank { null },
                    supplierLotNumber = it.supplierLotNumber?.trim()?.ifBlank { null },
                    lotBarcode = it.lotBarcode?.trim()?.ifBlank { null },
                )
            },
        )
    }
}

data class RequisitionRecord(
    val id: Long,
    val requestNo: String,
    val department: String,
    val requiredEpochDay: Long,
    val status: RequisitionStatus,
    val requestedBy: String,
    val approvedBy: String?,
    val note: String = "",
    val createdAtEpochMillis: Long = 0L,
    val estimatedTotalRial: Long,
    val lineCount: Int,
    val supplierGroupCount: Int = 0,
    val unassignedLineCount: Int = 0,
    val requiredApprovalLevel: Int = 1,
    val completedApprovalLevel: Int = 0,
    val firstApprovedBy: String? = null,
    val secondApprovedBy: String? = null,
    val committedBudgetId: Long? = null,
    val committedBudgetRial: Long = 0,
    val branchId: Long = 0L,
    val destinationLocationId: Long = 0L,
)

data class SplitPurchaseOrdersDraft(
    val requisitionId: Long,
    val orderEpochDay: Long,
    val fallbackSupplierId: Long? = null,
    val note: String = "",
) {
    fun validated(): SplitPurchaseOrdersDraft {
        require(requisitionId > 0) { "درخواست خرید معتبر نیست." }
        require(fallbackSupplierId == null || fallbackSupplierId > 0) { "تأمین‌کننده جایگزین معتبر نیست." }
        require(note.trim().length <= 300) { "توضیحات سفارش بیش از حد طولانی است." }
        return copy(note = note.trim())
    }
}

data class SplitPurchaseOrdersResult(
    val orderIds: List<Long>,
    val supplierCount: Int,
    val lineCount: Int,
)

data class SupplierAssignedRequisitionLine(
    val lineId: Long,
    val recommendedSupplierId: Long?,
    val leadTimeDays: Int?,
)

data class SupplierOrderGroup(
    val supplierId: Long,
    val lineIds: List<Long>,
    val expectedEpochDay: Long,
)

object SupplierOrderSplitter {
    fun split(
        lines: List<SupplierAssignedRequisitionLine>,
        orderEpochDay: Long,
        fallbackSupplierId: Long?,
    ): List<SupplierOrderGroup> {
        require(lines.isNotEmpty()) { "ردیفی برای تفکیک سفارش وجود ندارد." }
        require(lines.map { it.lineId }.distinct().size == lines.size && lines.all { it.lineId > 0 }) { "شناسه ردیف‌های درخواست معتبر نیست." }
        require(lines.all { it.leadTimeDays == null || it.leadTimeDays in 0..180 }) { "زمان تحویل یکی از ردیف‌ها معتبر نیست." }
        require(lines.none { it.recommendedSupplierId == null } || fallbackSupplierId != null) { "تأمین‌کننده جایگزین الزامی است." }
        return lines.groupBy { it.recommendedSupplierId ?: requireNotNull(fallbackSupplierId) }
            .toSortedMap()
            .map { (supplierId, assigned) ->
                val maxLeadTime = assigned.maxOfOrNull { it.leadTimeDays ?: 0 } ?: 0
                require(orderEpochDay <= Long.MAX_VALUE - maxLeadTime) { "تاریخ تحویل از محدوده امن خارج می‌شود." }
                SupplierOrderGroup(
                    supplierId = supplierId,
                    lineIds = assigned.map { it.lineId },
                    expectedEpochDay = orderEpochDay + maxLeadTime.toLong(),
                )
            }
    }
}

data class PurchaseOrderLineRecord(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val supplierSku: String?,
    val orderedQtyMicros: Long,
    val receivedQtyMicros: Long,
    val rejectedQtyMicros: Long,
    val returnedQtyMicros: Long,
    val unitCostRial: Long,
    val invoicedQtyMicros: Long = 0L,
) {
    val remainingQtyMicros: Long get() = (orderedQtyMicros - receivedQtyMicros).coerceAtLeast(0)
    val netAcceptedQtyMicros: Long get() = (receivedQtyMicros - returnedQtyMicros).coerceAtLeast(0)
    val returnableQtyMicros: Long get() = netAcceptedQtyMicros
    val invoiceableQtyMicros: Long get() = (netAcceptedQtyMicros - invoicedQtyMicros).coerceAtLeast(0)
}

data class PurchaseOrderRecord(
    val id: Long,
    val orderNo: String,
    val supplierId: Long,
    val supplierName: String,
    val requisitionId: Long,
    val orderEpochDay: Long,
    val expectedEpochDay: Long,
    val sentAtEpochMillis: Long?,
    val sentBy: String?,
    val dispatchChannel: PurchaseOrderDispatchChannel?,
    val acknowledgedAtEpochMillis: Long?,
    val supplierConfirmationNo: String?,
    val confirmedExpectedEpochDay: Long?,
    val status: PurchaseOrderStatus,
    val orderedValueRial: Long,
    val acceptedValueRial: Long,
    val receiptCount: Int,
    val invoiceNo: String?,
    val lines: List<PurchaseOrderLineRecord>,
    val branchId: Long = 0L,
    val destinationLocationId: Long = 0L,
)

data class PurchaseOrderAcknowledgementDraft(
    val purchaseOrderId: Long,
    val supplierConfirmationNo: String,
    val confirmedExpectedEpochDay: Long,
) {
    fun validated(): PurchaseOrderAcknowledgementDraft {
        require(purchaseOrderId > 0) { "سفارش خرید معتبر نیست." }
        require(supplierConfirmationNo.trim().length in 1..80) { "شماره تأیید تأمین‌کننده الزامی است." }
        return copy(supplierConfirmationNo = supplierConfirmationNo.trim())
    }
}

data class ProcurementOverview(
    val requisitions: List<RequisitionRecord> = emptyList(),
    val orders: List<PurchaseOrderRecord> = emptyList(),
    val supplierScorecards: List<SupplierScorecard> = emptyList(),
    val supplierCredits: List<SupplierCreditRecord> = emptyList(),
    val replenishmentPolicies: List<ReplenishmentPolicyRecord> = emptyList(),
    val replenishmentSuggestions: List<ReplenishmentSuggestion> = emptyList(),
    val supplierOffers: List<SupplierOfferRecord> = emptyList(),
) {
    val awaitingApproval: Int get() = requisitions.count { it.status == RequisitionStatus.SUBMITTED }
    val openOrders: Int get() = orders.count { it.status in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED) }
    val pendingInvoiceMatches: Int get() = orders.count { order ->
        order.status != PurchaseOrderStatus.CANCELLED && order.lines.any { it.invoiceableQtyMicros > 0 }
    }
}

data class ReplenishmentPolicyDraft(
    val itemId: Long,
    val preferredSupplierId: Long?,
    val targetCoverDays: Int,
    val leadTimeDays: Int,
    val safetyStockMicros: Long,
    val orderMultipleMicros: Long,
    val isEnabled: Boolean = true,
) {
    fun validated(): ReplenishmentPolicyDraft {
        require(itemId > 0) { "کالای سیاست تأمین معتبر نیست." }
        require(preferredSupplierId == null || preferredSupplierId > 0) { "تأمین‌کننده ترجیحی معتبر نیست." }
        require(targetCoverDays in 1..365) { "پوشش هدف باید بین ۱ تا ۳۶۵ روز باشد." }
        require(leadTimeDays in 0..180) { "زمان تأمین باید بین صفر تا ۱۸۰ روز باشد." }
        QuantityMicros.of(safetyStockMicros)
        QuantityMicros.positive(orderMultipleMicros)
        return this
    }
}

data class ReplenishmentPolicyRecord(
    val itemId: Long,
    val preferredSupplierId: Long?,
    val targetCoverDays: Int,
    val leadTimeDays: Int,
    val safetyStockMicros: Long,
    val orderMultipleMicros: Long,
    val isEnabled: Boolean,
)

enum class ReplenishmentRisk { CRITICAL, HIGH, MEDIUM }

data class ReplenishmentInput(
    val itemId: Long,
    val itemName: String,
    val currentStockMicros: Long,
    val openPurchaseOrderMicros: Long,
    val usage30DaysMicros: Long,
    val estimatedUnitCostRial: Long,
    val policy: ReplenishmentPolicyRecord,
    val hasPendingRequest: Boolean,
    val preferredSupplierScore: Int?,
)

data class ReplenishmentSuggestion(
    val itemId: Long,
    val itemName: String,
    val preferredSupplierId: Long?,
    val preferredSupplierScore: Int?,
    val averageDailyUsageMicros: Long,
    val currentStockMicros: Long,
    val openPurchaseOrderMicros: Long,
    val projectedAtDeliveryMicros: Long,
    val suggestedOrderMicros: Long,
    val estimatedUnitCostRial: Long,
    val estimatedOrderValueRial: Long,
    val daysOfCoverBasisPoints: Long,
    val risk: ReplenishmentRisk,
    val blockedByPendingRequest: Boolean,
    val recommendedSupplierId: Long? = null,
    val recommendedSupplierName: String? = null,
    val recommendedSupplierSku: String? = null,
    val comparedOfferCount: Int = 0,
    val recommendedLeadTimeDays: Int? = null,
    val offerValidUntilEpochDay: Long? = null,
    val estimatedSavingsRial: Long = 0,
)

data class SupplierOfferDraft(
    val supplierId: Long,
    val itemId: Long,
    val supplierSku: String,
    val unitCostRial: Long,
    val minimumOrderMicros: Long,
    val orderMultipleMicros: Long,
    val leadTimeDays: Int,
    val validUntilEpochDay: Long?,
    val isActive: Boolean = true,
) {
    fun validated(): SupplierOfferDraft {
        require(supplierId > 0 && itemId > 0) { "کالا و تأمین‌کننده پیشنهاد قیمت معتبر نیستند." }
        require(supplierSku.trim().length <= 80) { "کد کالای تأمین‌کننده بیش از حد طولانی است." }
        require(unitCostRial > 0) { "قیمت واحد باید بیشتر از صفر باشد." }
        MoneyRial.of(unitCostRial)
        QuantityMicros.of(minimumOrderMicros)
        QuantityMicros.positive(orderMultipleMicros)
        require(leadTimeDays in 0..180) { "زمان تحویل باید بین صفر تا ۱۸۰ روز باشد." }
        return copy(supplierSku = supplierSku.trim())
    }
}

data class SupplierOfferRecord(
    val id: Long,
    val supplierId: Long,
    val supplierName: String,
    val itemId: Long,
    val itemName: String,
    val supplierSku: String,
    val unitCostRial: Long,
    val minimumOrderMicros: Long,
    val orderMultipleMicros: Long,
    val leadTimeDays: Int,
    val validUntilEpochDay: Long?,
    val isActive: Boolean,
)

data class SupplierOfferCandidate(
    val offer: SupplierOfferRecord,
    val supplierScore: Int?,
)

data class SupplierSourcingDecision(
    val offer: SupplierOfferRecord,
    val orderQuantityMicros: Long,
    val orderValueRial: Long,
    val comparedOfferCount: Int,
    val estimatedSavingsRial: Long,
)

object SupplierSourcingAdvisor {
    fun choose(
        candidates: List<SupplierOfferCandidate>,
        requiredQuantityMicros: Long,
        baselineUnitCostRial: Long,
        preferredSupplierId: Long?,
    ): SupplierSourcingDecision? {
        val required = QuantityMicros.positive(requiredQuantityMicros).value
        val evaluated = candidates.filter { it.offer.isActive }.map { candidate ->
            val offer = candidate.offer
            val minimum = maxOf(required, offer.minimumOrderMicros)
            val multiple = offer.orderMultipleMicros
            val rounded = BigInteger.valueOf(minimum).add(BigInteger.valueOf(multiple)).subtract(BigInteger.ONE)
                .divide(BigInteger.valueOf(multiple)).multiply(BigInteger.valueOf(multiple))
            require(rounded <= BigInteger.valueOf(QuantityMicros.MAX_VALUE)) { "مقدار سفارش پیشنهادی از محدوده امن خارج می‌شود." }
            val quantity = rounded.toLongExactCompat()
            val total = MoneyRial.of(offer.unitCostRial).times(QuantityMicros.of(quantity)).value
            Triple(candidate, quantity, total)
        }
        val selected = evaluated.minWithOrNull(
            compareBy<Triple<SupplierOfferCandidate, Long, Long>> { it.third }
                .thenBy { it.first.offer.leadTimeDays }
                .thenByDescending { it.first.supplierScore ?: 0 }
                .thenByDescending { if (it.first.offer.supplierId == preferredSupplierId) 1 else 0 },
        ) ?: return null
        val baseline = if (baselineUnitCostRial <= 0) 0L else MoneyRial.of(baselineUnitCostRial)
            .times(QuantityMicros.of(selected.second)).value
        return SupplierSourcingDecision(
            offer = selected.first.offer,
            orderQuantityMicros = selected.second,
            orderValueRial = selected.third,
            comparedOfferCount = evaluated.size,
            estimatedSavingsRial = (baseline - selected.third).coerceAtLeast(0),
        )
    }
}

object ReplenishmentPlanner {
    fun suggest(input: ReplenishmentInput): ReplenishmentSuggestion? {
        if (!input.policy.isEnabled || input.usage30DaysMicros <= 0) return null
        val inventoryRecommendation = ir.restaurant.management.domain.inventory.InventoryReplenishmentCalculator.recommend(
            ir.restaurant.management.domain.inventory.InventoryReplenishmentInput(
                itemId = input.itemId,
                itemName = input.itemName,
                unit = "base-unit",
                locationId = null,
                locationName = "ALL_LOCATIONS",
                onHandMicros = input.currentStockMicros,
                reservedMicros = 0,
                damagedMicros = 0,
                quarantinedMicros = 0,
                inTransitMicros = 0,
                onOrderMicros = input.openPurchaseOrderMicros,
                usageMicros = input.usage30DaysMicros,
                usageWindowDays = 30,
                estimatedUnitCostRial = input.estimatedUnitCostRial,
                preferredSupplierId = input.policy.preferredSupplierId,
                preferredSupplierName = null,
                hasPendingRequisition = input.hasPendingRequest,
                policy = ir.restaurant.management.domain.inventory.InventoryReplenishmentPolicy(
                    targetCoverDays = input.policy.targetCoverDays,
                    leadTimeDays = input.policy.leadTimeDays,
                    safetyStockMicros = input.policy.safetyStockMicros,
                    minimumStockMicros = 0,
                    maximumStockMicros = 0,
                    configuredReorderPointMicros = 0,
                    orderMultipleMicros = input.policy.orderMultipleMicros,
                    isEnabled = input.policy.isEnabled,
                ),
            ),
        )
        if (inventoryRecommendation.suggestedQuantityMicros == 0L) return null
        val daily = inventoryRecommendation.averageDailyUsageMicros
        val suggested = inventoryRecommendation.suggestedQuantityMicros
        val safeProjected = inventoryRecommendation.projectedAtDeliveryMicros
        val coverBasisPoints = requireNotNull(inventoryRecommendation.daysOfCoverBasisPoints)
        val risk = when {
            safeProjected <= input.policy.safetyStockMicros -> ReplenishmentRisk.CRITICAL
            coverBasisPoints < (input.policy.leadTimeDays + 3L) * 10_000L -> ReplenishmentRisk.HIGH
            else -> ReplenishmentRisk.MEDIUM
        }
        return ReplenishmentSuggestion(
            itemId = input.itemId,
            itemName = input.itemName,
            preferredSupplierId = input.policy.preferredSupplierId,
            preferredSupplierScore = input.preferredSupplierScore,
            averageDailyUsageMicros = daily,
            currentStockMicros = input.currentStockMicros,
            openPurchaseOrderMicros = input.openPurchaseOrderMicros,
            projectedAtDeliveryMicros = safeProjected,
            suggestedOrderMicros = suggested,
            estimatedUnitCostRial = input.estimatedUnitCostRial,
            estimatedOrderValueRial = inventoryRecommendation.estimatedOrderValueRial,
            daysOfCoverBasisPoints = coverBasisPoints,
            risk = risk,
            blockedByPendingRequest = input.hasPendingRequest,
        )
    }

}

data class PurchaseReturnLineDraft(
    val purchaseOrderLineId: Long,
    val quantityMicros: Long,
    val reason: String,
)

data class PurchaseReturnDraft(
    val purchaseOrderId: Long,
    val returnEpochDay: Long,
    val reason: String,
    val lines: List<PurchaseReturnLineDraft>,
) {
    fun validated(): PurchaseReturnDraft {
        require(purchaseOrderId > 0) { "سفارش خرید معتبر نیست." }
        require(reason.trim().length in 3..300) { "دلیل مرجوعی باید بین ۳ تا ۳۰۰ نویسه باشد." }
        require(lines.isNotEmpty() && lines.size <= 100) { "حداقل یک ردیف مرجوعی لازم است." }
        require(lines.map { it.purchaseOrderLineId }.distinct().size == lines.size) { "یک ردیف سفارش در مرجوعی تکرار شده است." }
        lines.forEach {
            require(it.purchaseOrderLineId > 0) { "ردیف سفارش معتبر نیست." }
            QuantityMicros.positive(it.quantityMicros)
            require(it.reason.trim().length in 3..300) { "دلیل هر ردیف مرجوعی الزامی است." }
        }
        return copy(
            reason = reason.trim(),
            lines = lines.map { it.copy(reason = it.reason.trim()) },
        )
    }
}

data class SupplierCreditRecord(
    val id: Long,
    val creditNo: String,
    val supplierId: Long,
    val supplierName: String,
    val amountRial: Long,
    val appliedRial: Long,
    val status: String,
    val createdAtEpochMillis: Long,
) {
    val remainingRial: Long get() = amountRial - appliedRial
}

data class SupplierScorecard(
    val supplierId: Long,
    val supplierName: String,
    val completedOrders: Int,
    /** 10,000 means 100%. */
    val onTimeBasisPoints: Long,
    /** Accepted quantity divided by delivered quantity; 10,000 means 100%. */
    val acceptanceBasisPoints: Long,
    val returnBasisPoints: Long,
    val priceVarianceBasisPoints: Long,
    val openCreditRial: Long,
    /** 0..1000, higher is better. */
    val score: Int,
) {
    val grade: String get() = when {
        score >= 900 -> "A"
        score >= 800 -> "B"
        score >= 700 -> "C"
        score >= 600 -> "D"
        else -> "E"
    }
}

data class ThreeWayMatchResult(
    val status: ThreeWayMatchStatus,
    val acceptedValueRial: Long,
    val invoiceValueRial: Long,
    val priceVarianceRial: Long,
    /** Absolute variance in basis points; 500 means five percent. */
    val priceVarianceBasisPoints: Long,
)

interface ProcurementRepository {
    val overview: Flow<ProcurementOverview>
    suspend fun submitRequisition(draft: PurchaseRequisitionDraft): Long
    suspend fun reviewRequisition(requisitionId: Long, approve: Boolean, note: String = "")
    suspend fun createOrder(draft: PurchaseOrderDraft): Long
    suspend fun createSplitOrders(draft: SplitPurchaseOrdersDraft): SplitPurchaseOrdersResult
    suspend fun markOrderSent(orderId: Long, channel: PurchaseOrderDispatchChannel)
    suspend fun acknowledgeOrder(draft: PurchaseOrderAcknowledgementDraft)
    suspend fun postGoodsReceipt(draft: GoodsReceiptDraft): Long
    suspend fun postPurchaseReturn(draft: PurchaseReturnDraft): Long
    suspend fun saveReplenishmentPolicy(draft: ReplenishmentPolicyDraft)
    suspend fun saveSupplierOffer(draft: SupplierOfferDraft)
    suspend fun submitSuggestedRequisition(itemIds: List<Long>): Long
    suspend fun previewThreeWayMatch(purchaseOrderId: Long, invoice: PurchaseDraft): ThreeWayMatchResult
    suspend fun postMatchedInvoice(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
        approvePriceVariance: Boolean,
    ): PostedPurchase
}
