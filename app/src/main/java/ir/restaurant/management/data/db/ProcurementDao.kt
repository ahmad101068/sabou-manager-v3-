package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ProcurementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplierItemOffer(entity: SupplierItemOfferEntity): Long

    @Query("SELECT o.*, s.name AS supplierName, i.name AS itemName FROM supplier_item_offers o INNER JOIN suppliers s ON s.id = o.supplierId INNER JOIN inventory_items i ON i.id = o.itemId ORDER BY i.name, o.unitCostRial")
    fun observeSupplierItemOffers(): Flow<List<SupplierItemOfferRow>>

    @Query("SELECT * FROM supplier_item_offers WHERE itemId = :itemId AND isActive = 1 AND (validUntilEpochDay IS NULL OR validUntilEpochDay >= :todayEpochDay) ORDER BY unitCostRial")
    suspend fun validSupplierItemOffers(itemId: Long, todayEpochDay: Long): List<SupplierItemOfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReplenishmentPolicy(entity: InventoryReplenishmentPolicyEntity)

    @Query("SELECT * FROM inventory_replenishment_policies WHERE itemId = :itemId LIMIT 1")
    suspend fun replenishmentPolicy(itemId: Long): InventoryReplenishmentPolicyEntity?

    @Query("SELECT * FROM inventory_replenishment_policies ORDER BY itemId")
    fun observeReplenishmentPolicies(): Flow<List<InventoryReplenishmentPolicyEntity>>

    @Query(
        """
        SELECT itemId, MAX(COALESCE(SUM(-quantityDeltaMicros), 0), 0) AS usageMicros
        FROM stock_movements
        WHERE movementEpochDay >= :fromEpochDay
          AND movementType IN ('SALE_CONSUMPTION', 'DAILY_SALES_CONSUMPTION', 'DAILY_SALES_REVERSAL', 'WASTE')
        GROUP BY itemId
        """,
    )
    fun observeDemandUsage(fromEpochDay: Long): Flow<List<ProcurementDemandUsageRow>>

    @Query(
        """
        SELECT itemId, MAX(COALESCE(SUM(-quantityDeltaMicros), 0), 0) AS usageMicros
        FROM stock_movements
        WHERE movementEpochDay >= :fromEpochDay
          AND locationId IN (:locationIds)
          AND movementType IN ('SALE_CONSUMPTION', 'DAILY_SALES_CONSUMPTION', 'DAILY_SALES_REVERSAL', 'WASTE')
        GROUP BY itemId
        """,
    )
    fun observeDemandUsageForLocations(fromEpochDay: Long, locationIds: List<Long>): Flow<List<ProcurementDemandUsageRow>>

    @Query(
        """
        SELECT pl.itemId AS itemId, pl.unitCostRial AS unitCostRial
        FROM purchase_lines pl
        INNER JOIN purchases p ON p.id = pl.purchaseId
        WHERE p.paymentStatus != 'REVERSED'
          AND pl.id = (
              SELECT latestLine.id
              FROM purchase_lines latestLine
              INNER JOIN purchases latestPurchase ON latestPurchase.id = latestLine.purchaseId
              WHERE latestLine.itemId = pl.itemId AND latestPurchase.paymentStatus != 'REVERSED'
              ORDER BY latestPurchase.purchaseEpochDay DESC, latestLine.id DESC
              LIMIT 1
          )
        """,
    )
    fun observeLatestPurchaseCosts(): Flow<List<ProcurementLatestCostRow>>

    @Query(
        """
        SELECT MAX(COALESCE(SUM(-quantityDeltaMicros), 0), 0)
        FROM stock_movements
        WHERE itemId = :itemId
          AND movementEpochDay >= :fromEpochDay
          AND movementType IN ('SALE_CONSUMPTION', 'DAILY_SALES_CONSUMPTION', 'DAILY_SALES_REVERSAL', 'WASTE')
        """,
    )
    suspend fun demandUsageMicros(itemId: Long, fromEpochDay: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(pol.orderedQtyMicros - pol.receivedQtyMicros), 0)
        FROM purchase_order_lines pol
        INNER JOIN purchase_orders po ON po.id = pol.purchaseOrderId
        WHERE pol.itemId = :itemId
          AND po.status IN ('OPEN', 'PARTIALLY_RECEIVED')
        """,
    )
    suspend fun openPurchaseOrderQtyMicros(itemId: Long): Long

    @Query(
        """
        SELECT pl.unitCostRial
        FROM purchase_lines pl
        INNER JOIN purchases p ON p.id = pl.purchaseId
        WHERE pl.itemId = :itemId AND p.paymentStatus != 'REVERSED'
        ORDER BY p.purchaseEpochDay DESC, pl.id DESC
        LIMIT 1
        """,
    )
    suspend fun latestPurchaseUnitCostRial(itemId: Long): Long?

    @Query(
        """
        SELECT DISTINCT prl.itemId
        FROM purchase_requisition_lines prl
        INNER JOIN purchase_requisitions pr ON pr.id = prl.requisitionId
        WHERE pr.status IN ('SUBMITTED', 'PENDING_SECOND_APPROVAL', 'APPROVED')
        """,
    )
    fun observeActiveRequestedItemIds(): Flow<List<Long>>

    @Query(
        """
        SELECT DISTINCT line.itemId
        FROM purchase_requisition_lines line
        INNER JOIN purchase_requisitions requisition ON requisition.id = line.requisitionId
        WHERE requisition.status IN ('SUBMITTED','PENDING_SECOND_APPROVAL','APPROVED','ORDERED','PARTIALLY_ORDERED')
          AND requisition.branchId IN (:branchIds)
        ORDER BY line.itemId
        """,
    )
    fun observeActiveRequestedItemIdsForBranches(branchIds: List<Long>): Flow<List<Long>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM purchase_requisition_lines prl
            INNER JOIN purchase_requisitions pr ON pr.id = prl.requisitionId
            WHERE prl.itemId = :itemId AND pr.status IN ('SUBMITTED', 'PENDING_SECOND_APPROVAL', 'APPROVED')
        )
        """,
    )
    suspend fun activeRequestExistsForItem(itemId: Long): Boolean

    @Insert
    suspend fun insertRequisition(entity: PurchaseRequisitionEntity): Long

    @Insert
    suspend fun insertRequisitionLines(lines: List<PurchaseRequisitionLineEntity>)

    @Query("SELECT * FROM purchase_requisitions WHERE id = :id LIMIT 1")
    suspend fun requisitionById(id: Long): PurchaseRequisitionEntity?

    @Query("SELECT * FROM purchase_requisition_lines WHERE requisitionId = :requisitionId ORDER BY id")
    suspend fun requisitionLines(requisitionId: Long): List<PurchaseRequisitionLineEntity>

    @Query(
        """
        UPDATE purchase_requisitions
        SET status = :newStatus, approvedBy = :approvedBy, note = :note,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND status = :expectedStatus
        """,
    )
    suspend fun transitionRequisition(
        id: Long,
        expectedStatus: String,
        newStatus: String,
        approvedBy: String?,
        note: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE purchase_requisitions
        SET status = :newStatus,
            approvedBy = :approvedBy,
            approvedByActorId = :approvedByActorId,
            requiredApprovalLevel = :requiredLevel,
            completedApprovalLevel = :completedLevel,
            firstApprovedBy = CASE WHEN :completedLevel = 1 THEN :approvedBy ELSE firstApprovedBy END,
            firstApprovedByActorId = CASE WHEN :completedLevel = 1 THEN :approvedByActorId ELSE firstApprovedByActorId END,
            secondApprovedBy = CASE WHEN :completedLevel = 2 THEN :approvedBy ELSE secondApprovedBy END,
            secondApprovedByActorId = CASE WHEN :completedLevel = 2 THEN :approvedByActorId ELSE secondApprovedByActorId END,
            note = :note,
            updatedAtEpochMillis = :updatedAt
        WHERE id = :id AND status = :expectedStatus
        """,
    )
    suspend fun recordRequisitionApproval(
        id: Long,
        expectedStatus: String,
        newStatus: String,
        requiredLevel: Int,
        completedLevel: Int,
        approvedBy: String,
        approvedByActorId: Long,
        note: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE purchase_requisitions SET committedBudgetId=:budgetId,committedBudgetRial=:amountRial WHERE id=:id")
    suspend fun linkRequisitionBudget(id: Long, budgetId: Long, amountRial: Long): Int

    @Insert
    suspend fun insertOrder(entity: PurchaseOrderEntity): Long

    @Insert
    suspend fun insertOrderLines(lines: List<PurchaseOrderLineEntity>)

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun orderById(id: Long): PurchaseOrderEntity?

    @Query("SELECT * FROM purchase_order_lines WHERE purchaseOrderId = :purchaseOrderId ORDER BY id")
    suspend fun orderLines(purchaseOrderId: Long): List<PurchaseOrderLineEntity>

    @Query("SELECT * FROM procurement_invoice_links WHERE purchaseOrderId = :purchaseOrderId ORDER BY matchedAtEpochMillis, id")
    suspend fun invoiceLinksForOrder(purchaseOrderId: Long): List<ProcurementInvoiceLinkEntity>

    @Query("SELECT * FROM procurement_invoice_links WHERE purchaseOrderId = :purchaseOrderId ORDER BY matchedAtEpochMillis, id LIMIT 1")
    suspend fun invoiceLinkForOrder(purchaseOrderId: Long): ProcurementInvoiceLinkEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId AND itemId = :itemId LIMIT 1")
    suspend fun purchaseLineForItem(purchaseId: Long, itemId: Long): PurchaseLineEntity?

    @Insert
    suspend fun insertReceipt(entity: GoodsReceiptEntity): Long

    @Insert
    suspend fun insertReceiptLines(lines: List<GoodsReceiptLineEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM goods_receipts WHERE receiptNo = :receiptNo)")
    suspend fun receiptNoExists(receiptNo: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM goods_receipts WHERE purchaseOrderId = :purchaseOrderId AND deliveryNoteNo = :deliveryNoteNo)")
    suspend fun deliveryNoteExists(purchaseOrderId: Long, deliveryNoteNo: String): Boolean

    @Query("SELECT * FROM goods_receipts WHERE purchaseOrderId = :purchaseOrderId AND deliveryNoteNo = :deliveryNoteNo LIMIT 1")
    suspend fun receiptByDeliveryNote(purchaseOrderId: Long, deliveryNoteNo: String): GoodsReceiptEntity?

    @Query("SELECT * FROM goods_receipt_lines WHERE goodsReceiptId = :goodsReceiptId ORDER BY id")
    suspend fun receiptLines(goodsReceiptId: Long): List<GoodsReceiptLineEntity>

    @Query("SELECT MAX(receiptEpochDay) FROM goods_receipts WHERE purchaseOrderId = :purchaseOrderId")
    suspend fun latestReceiptEpochDay(purchaseOrderId: Long): Long?

    @Query(
        """
        UPDATE purchase_order_lines
        SET receivedQtyMicros = receivedQtyMicros + :acceptedQtyMicros,
            rejectedQtyMicros = rejectedQtyMicros + :rejectedQtyMicros
        WHERE id = :lineId
          AND purchaseOrderId = :purchaseOrderId
          AND receivedQtyMicros + :acceptedQtyMicros <= orderedQtyMicros
        """,
    )
    suspend fun addReceiptQuantities(
        lineId: Long,
        purchaseOrderId: Long,
        acceptedQtyMicros: Long,
        rejectedQtyMicros: Long,
    ): Int

    @Query(
        """
        UPDATE purchase_order_lines
        SET returnedQtyMicros = returnedQtyMicros + :quantityMicros
        WHERE id = :lineId
          AND purchaseOrderId = :purchaseOrderId
          AND returnedQtyMicros + :quantityMicros <= receivedQtyMicros
        """,
    )
    suspend fun addReturnedQuantity(
        lineId: Long,
        purchaseOrderId: Long,
        quantityMicros: Long,
    ): Int

    @Query("UPDATE purchase_orders SET status = :status, updatedAtEpochMillis = :updatedAt WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String, updatedAt: Long): Int

    @Query("UPDATE purchase_orders SET sentAtEpochMillis = :sentAt, sentBy = :sentBy, dispatchChannel = :channel, updatedAtEpochMillis = :sentAt WHERE id = :orderId AND status = 'OPEN' AND sentAtEpochMillis IS NULL")
    suspend fun markOrderSent(orderId: Long, sentAt: Long, sentBy: String, channel: String): Int

    @Query("UPDATE purchase_orders SET acknowledgedAtEpochMillis = :acknowledgedAt, supplierConfirmationNo = :confirmationNo, confirmedExpectedEpochDay = :confirmedExpectedDay, expectedEpochDay = :confirmedExpectedDay, updatedAtEpochMillis = :acknowledgedAt WHERE id = :orderId AND status IN ('OPEN', 'PARTIALLY_RECEIVED') AND sentAtEpochMillis IS NOT NULL AND acknowledgedAtEpochMillis IS NULL")
    suspend fun acknowledgeOrder(orderId: Long, acknowledgedAt: Long, confirmationNo: String, confirmedExpectedDay: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM procurement_invoice_links WHERE purchaseOrderId = :purchaseOrderId)")
    suspend fun orderHasInvoice(purchaseOrderId: Long): Boolean

    @Insert
    suspend fun insertInvoiceLink(entity: ProcurementInvoiceLinkEntity): Long

    @Insert
    suspend fun insertPurchaseReturn(entity: PurchaseReturnEntity): Long

    @Insert
    suspend fun insertPurchaseReturnLines(lines: List<PurchaseReturnLineEntity>)

    @Insert
    suspend fun insertSupplierCredit(entity: SupplierCreditEntity): Long

    @Query(
        """
        SELECT r.id, r.requestNo, r.department, r.requiredEpochDay, r.status,
               r.requestedBy, r.approvedBy, r.note, r.createdAtEpochMillis,
               r.requiredApprovalLevel,r.completedApprovalLevel,r.firstApprovedBy,r.secondApprovedBy,r.committedBudgetId,r.committedBudgetRial,
               r.branchId, r.destinationLocationId,
               COUNT(rl.id) AS lineCount
        FROM purchase_requisitions r
        LEFT JOIN purchase_requisition_lines rl ON rl.requisitionId = r.id
        GROUP BY r.id
        ORDER BY r.createdAtEpochMillis DESC
        """,
    )
    fun observeRequisitions(): Flow<List<ProcurementRequisitionRow>>

    @Query("SELECT * FROM purchase_requisition_lines ORDER BY requisitionId, id")
    fun observeRequisitionLines(): Flow<List<PurchaseRequisitionLineEntity>>

    @Query(
        """
        SELECT po.id, po.orderNo, po.supplierId, po.supplierNameSnapshot AS supplierName,
               po.requisitionId, po.branchId, po.destinationLocationId, po.orderEpochDay, po.expectedEpochDay,
               po.sentAtEpochMillis, po.sentBy, po.dispatchChannel,
               po.acknowledgedAtEpochMillis, po.supplierConfirmationNo, po.confirmedExpectedEpochDay,
               po.status,
               (SELECT COUNT(*) FROM goods_receipts gr WHERE gr.purchaseOrderId = po.id) AS receiptCount,
               (SELECT p.invoiceNo FROM procurement_invoice_links pil
                   INNER JOIN purchases p ON p.id = pil.purchaseId
                   WHERE pil.purchaseOrderId = po.id LIMIT 1) AS invoiceNo
        FROM purchase_orders po
        ORDER BY po.createdAtEpochMillis DESC
        """,
    )
    fun observeOrders(): Flow<List<ProcurementOrderRow>>

    @Query("SELECT * FROM purchase_order_lines ORDER BY purchaseOrderId, id")
    fun observeOrderLines(): Flow<List<PurchaseOrderLineEntity>>

    @Query("SELECT * FROM goods_receipts ORDER BY receiptEpochDay DESC, id DESC")
    fun observeGoodsReceipts(): Flow<List<GoodsReceiptEntity>>

    @Query("SELECT * FROM goods_receipt_lines ORDER BY goodsReceiptId, id")
    fun observeGoodsReceiptLines(): Flow<List<GoodsReceiptLineEntity>>

    @Query("SELECT * FROM purchase_returns ORDER BY returnEpochDay DESC, id DESC")
    fun observePurchaseReturns(): Flow<List<PurchaseReturnEntity>>

    @Query("SELECT * FROM purchase_return_lines ORDER BY purchaseReturnId, id")
    fun observePurchaseReturnLines(): Flow<List<PurchaseReturnLineEntity>>

    @Query("SELECT * FROM procurement_invoice_links ORDER BY matchedAtEpochMillis DESC")
    fun observeInvoiceLinks(): Flow<List<ProcurementInvoiceLinkEntity>>

    @Query(
        """
        SELECT sc.id, sc.creditNo, sc.supplierId, s.name AS supplierName,
               sc.amountRial, sc.appliedRial, sc.status, sc.createdAtEpochMillis
        FROM supplier_credits sc
        INNER JOIN suppliers s ON s.id = sc.supplierId
        ORDER BY sc.createdAtEpochMillis DESC
        """,
    )
    fun observeSupplierCredits(): Flow<List<SupplierCreditRow>>
}

data class ProcurementDemandUsageRow(
    val itemId: Long,
    val usageMicros: Long,
)

data class ProcurementLatestCostRow(
    val itemId: Long,
    val unitCostRial: Long,
)

data class SupplierItemOfferRow(
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
    val updatedBy: String,
    val updatedAtEpochMillis: Long,
)

data class SupplierCreditRow(
    val id: Long,
    val creditNo: String,
    val supplierId: Long,
    val supplierName: String,
    val amountRial: Long,
    val appliedRial: Long,
    val status: String,
    val createdAtEpochMillis: Long,
)

data class ProcurementRequisitionRow(
    val id: Long,
    val requestNo: String,
    val department: String,
    val requiredEpochDay: Long,
    val status: String,
    val requestedBy: String,
    val approvedBy: String?,
    val note: String,
    val createdAtEpochMillis: Long,
    val requiredApprovalLevel: Int,
    val completedApprovalLevel: Int,
    val firstApprovedBy: String?,
    val secondApprovedBy: String?,
    val committedBudgetId: Long?,
    val committedBudgetRial: Long,
    val branchId: Long?,
    val destinationLocationId: Long?,
    val lineCount: Int,
)

data class ProcurementOrderRow(
    val id: Long,
    val orderNo: String,
    val supplierId: Long,
    val supplierName: String,
    val requisitionId: Long,
    val branchId: Long?,
    val destinationLocationId: Long?,
    val orderEpochDay: Long,
    val expectedEpochDay: Long,
    val sentAtEpochMillis: Long?,
    val sentBy: String?,
    val dispatchChannel: String?,
    val acknowledgedAtEpochMillis: Long?,
    val supplierConfirmationNo: String?,
    val confirmedExpectedEpochDay: Long?,
    val status: String,
    val receiptCount: Int,
    val invoiceNo: String?,
)

data class SupplierPriceInsightRow(
    val itemId: Long,
    val itemName: String,
    val supplierName: String,
    val latestUnitCostRial: Long,
    val previousUnitCostRial: Long,
)

data class PurchaseListRow(
    val purchaseId: Long,
    val invoiceNo: String,
    val supplierName: String,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
)

data class PurchaseHeaderRow(
    val purchaseId: Long,
    val invoiceNo: String,
    val supplierName: String,
    val branchId: Long?,
    val locationId: Long?,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
)

data class PurchaseLineDetailRow(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)
