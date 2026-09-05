package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface PurchaseDao {
    @Query("SELECT EXISTS(SELECT 1 FROM purchases WHERE invoiceNo = :invoiceNo)")
    suspend fun invoiceExists(invoiceNo: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM purchases WHERE supplierId=:supplierId AND normalizedInvoiceNo=:normalizedInvoiceNo)")
    suspend fun supplierInvoiceExists(supplierId: Long, normalizedInvoiceNo: String): Boolean

    @Query("SELECT * FROM purchases WHERE commandId=:commandId LIMIT 1")
    suspend fun byCommandId(commandId: String): PurchaseEntity?

    @Insert
    suspend fun insert(entity: PurchaseEntity): Long

    @Insert
    suspend fun insertLines(lines: List<PurchaseLineEntity>): List<Long>

    @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
    suspend fun byId(purchaseId: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY id")
    suspend fun linesByPurchase(purchaseId: Long): List<PurchaseLineEntity>

    @Query(
        """
        SELECT
            p.id AS purchaseId,
            p.invoiceNo AS invoiceNo,
            s.name AS supplierName,
            p.branchId AS branchId,
            p.locationId AS locationId,
            p.purchaseEpochDay AS purchaseEpochDay,
            p.dueEpochDay AS dueEpochDay,
            p.totalRial AS totalRial,
            p.paidRial AS paidRial,
            p.paymentStatus AS paymentStatus,
            p.paymentMethod AS paymentMethod,
            p.reminderEnabled AS reminderEnabled,
            p.reminderEpochDay AS reminderEpochDay
        FROM purchases p
        INNER JOIN suppliers s ON s.id = p.supplierId
        WHERE p.id = :purchaseId
        LIMIT 1
        """,
    )
    fun observeHeader(purchaseId: Long): Flow<PurchaseHeaderRow?>

    @Query(
        """
        SELECT
            pl.itemId AS itemId,
            pl.itemNameSnapshot AS itemName,
            i.unit AS unit,
            pl.quantityMicros AS quantityMicros,
            pl.unitCostRial AS unitCostRial,
            pl.lineTotalRial AS lineTotalRial
        FROM purchase_lines pl
        INNER JOIN inventory_items i ON i.id = pl.itemId
        WHERE pl.purchaseId = :purchaseId
        ORDER BY pl.id
        """,
    )
    fun observeDetailLines(purchaseId: Long): Flow<List<PurchaseLineDetailRow>>

    @Query(
        """
        UPDATE purchases
        SET paidRial = :newPaidRial,
            paymentStatus = :paymentStatus,
            reminderEnabled = :reminderEnabled,
            reminderEpochDay = :reminderEpochDay
        WHERE id = :purchaseId
          AND paidRial = :expectedPaidRial
          AND paymentStatus IN ('UNPAID', 'PARTIAL')
        """,
    )
    suspend fun updateSettlementState(
        purchaseId: Long,
        expectedPaidRial: Long,
        newPaidRial: Long,
        paymentStatus: String,
        reminderEnabled: Boolean,
        reminderEpochDay: Long?,
    ): Int

    @Query(
        """
        UPDATE purchases
        SET paymentStatus = 'REVERSED',
            reminderEnabled = 0,
            reminderEpochDay = NULL
        WHERE id = :purchaseId
          AND paymentStatus != 'REVERSED'
        """,
    )
    suspend fun markReversed(purchaseId: Long): Int

    @Query("SELECT * FROM purchases ORDER BY purchaseEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query(
        """
        SELECT i.id AS itemId,
               i.name AS itemName,
               COALESCE((
                   SELECT s.name
                   FROM purchase_lines latestLine
                   INNER JOIN purchases latestPurchase ON latestPurchase.id = latestLine.purchaseId
                   INNER JOIN suppliers s ON s.id = latestPurchase.supplierId
                   WHERE latestLine.itemId = i.id AND latestPurchase.paymentStatus != 'REVERSED'
                   ORDER BY latestPurchase.purchaseEpochDay DESC, latestLine.id DESC
                   LIMIT 1
               ), '') AS supplierName,
               COALESCE((
                   SELECT latestLine.unitCostRial
                   FROM purchase_lines latestLine
                   INNER JOIN purchases latestPurchase ON latestPurchase.id = latestLine.purchaseId
                   WHERE latestLine.itemId = i.id AND latestPurchase.paymentStatus != 'REVERSED'
                   ORDER BY latestPurchase.purchaseEpochDay DESC, latestLine.id DESC
                   LIMIT 1
               ), 0) AS latestUnitCostRial,
               COALESCE((
                   SELECT previousLine.unitCostRial
                   FROM purchase_lines previousLine
                   INNER JOIN purchases previousPurchase ON previousPurchase.id = previousLine.purchaseId
                   WHERE previousLine.itemId = i.id AND previousPurchase.paymentStatus != 'REVERSED'
                   ORDER BY previousPurchase.purchaseEpochDay DESC, previousLine.id DESC
                   LIMIT 1 OFFSET 1
               ), 0) AS previousUnitCostRial
        FROM inventory_items i
        WHERE i.isActive = 1
        ORDER BY i.name
        """,
    )
    fun observeSupplierPriceInsights(): Flow<List<SupplierPriceInsightRow>>

    @Query(
        """
        SELECT
            p.id AS purchaseId,
            p.invoiceNo AS invoiceNo,
            s.name AS supplierName,
            p.purchaseEpochDay AS purchaseEpochDay,
            p.dueEpochDay AS dueEpochDay,
            p.totalRial AS totalRial,
            p.paidRial AS paidRial,
            p.paymentStatus AS paymentStatus,
            p.paymentMethod AS paymentMethod,
            p.reminderEnabled AS reminderEnabled,
            p.reminderEpochDay AS reminderEpochDay
        FROM purchases p
        INNER JOIN suppliers s ON s.id = p.supplierId
        WHERE :query = ''
           OR p.invoiceNo LIKE '%' || :query || '%'
           OR s.name LIKE '%' || :query || '%'
        ORDER BY p.purchaseEpochDay DESC, p.id DESC
        LIMIT 100
        """,
    )
    fun observeSearch(query: String): Flow<List<PurchaseListRow>>

    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(totalRial) FROM purchases
            WHERE purchaseEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND paymentStatus!='REVERSED'), 0) AS periodPurchaseRial,
          (SELECT COUNT(*) FROM purchase_orders WHERE status IN ('OPEN','PARTIALLY_RECEIVED')) AS openOrderCount,
          (SELECT COUNT(*) FROM suppliers WHERE isActive=1) AS activeSupplierCount,
          COALESCE((SELECT SUM(totalRial-paidRial) FROM purchases WHERE paymentStatus IN ('UNPAID','PARTIAL')), 0) AS supplierPayablesRial,
          (SELECT COUNT(*) FROM purchase_orders WHERE status IN ('OPEN','PARTIALLY_RECEIVED')) AS pendingReceiptCount,
          (SELECT COUNT(*) FROM purchase_requisitions WHERE status IN ('SUBMITTED','PENDING_SECOND_APPROVAL','APPROVED')) AS openRequisitionCount,
          (SELECT COUNT(*) FROM purchase_requisitions WHERE status IN ('SUBMITTED','PENDING_SECOND_APPROVAL')) AS pendingApprovalCount,
          (SELECT COUNT(*) FROM purchase_orders WHERE status IN ('OPEN','PARTIALLY_RECEIVED') AND expectedEpochDay < :todayEpochDay) AS overdueOrderCount
        """,
    )
    fun observeDashboardSummary(fromEpochDay: Long, toEpochDay: Long, todayEpochDay: Long): Flow<PurchaseDashboardSummaryRow>


    @Query(
        """
        SELECT
            p.id AS purchaseId,
            p.invoiceNo AS invoiceNo,
            s.name AS supplierName,
            p.purchaseEpochDay AS purchaseEpochDay,
            p.dueEpochDay AS dueEpochDay,
            p.totalRial AS totalRial,
            p.paidRial AS paidRial,
            p.paymentStatus AS paymentStatus,
            p.paymentMethod AS paymentMethod,
            p.reminderEnabled AS reminderEnabled,
            p.reminderEpochDay AS reminderEpochDay
        FROM purchases p
        INNER JOIN suppliers s ON s.id = p.supplierId
        WHERE p.branchId IN (:branchIds)
          AND (:query = ''
           OR p.invoiceNo LIKE '%' || :query || '%'
           OR s.name LIKE '%' || :query || '%')
        ORDER BY p.purchaseEpochDay DESC, p.id DESC
        LIMIT 100
        """,
    )
    fun observeSearchForBranches(query: String, branchIds: List<Long>): Flow<List<PurchaseListRow>>

    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(totalRial) FROM purchases
            WHERE branchId IN (:branchIds) AND purchaseEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND paymentStatus!='REVERSED'), 0) AS periodPurchaseRial,
          (SELECT COUNT(*) FROM purchase_orders WHERE branchId IN (:branchIds) AND status IN ('OPEN','PARTIALLY_RECEIVED')) AS openOrderCount,
          (SELECT COUNT(*) FROM suppliers WHERE isActive=1) AS activeSupplierCount,
          COALESCE((SELECT SUM(totalRial-paidRial) FROM purchases WHERE branchId IN (:branchIds) AND paymentStatus IN ('UNPAID','PARTIAL')), 0) AS supplierPayablesRial,
          (SELECT COUNT(*) FROM purchase_orders WHERE branchId IN (:branchIds) AND status IN ('OPEN','PARTIALLY_RECEIVED')) AS pendingReceiptCount,
          (SELECT COUNT(*) FROM purchase_requisitions WHERE branchId IN (:branchIds) AND status IN ('SUBMITTED','PENDING_SECOND_APPROVAL','APPROVED')) AS openRequisitionCount,
          (SELECT COUNT(*) FROM purchase_requisitions WHERE branchId IN (:branchIds) AND status IN ('SUBMITTED','PENDING_SECOND_APPROVAL')) AS pendingApprovalCount,
          (SELECT COUNT(*) FROM purchase_orders WHERE branchId IN (:branchIds) AND status IN ('OPEN','PARTIALLY_RECEIVED') AND expectedEpochDay < :todayEpochDay) AS overdueOrderCount
        """,
    )
    fun observeDashboardSummaryForBranches(
        fromEpochDay: Long,
        toEpochDay: Long,
        todayEpochDay: Long,
        branchIds: List<Long>,
    ): Flow<PurchaseDashboardSummaryRow>

    @Query(
        """
        SELECT COALESCE(SUM(totalRial - paidRial), 0)
        FROM purchases
        WHERE paymentStatus IN ('UNPAID', 'PARTIAL')
        """,
    )
    fun observePayablesRial(): Flow<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM procurement_invoice_links WHERE purchaseId = :purchaseId)")
    suspend fun isProcurementInvoice(purchaseId: Long): Boolean
}


data class PurchaseDashboardSummaryRow(
    val periodPurchaseRial: Long,
    val openOrderCount: Int,
    val activeSupplierCount: Int,
    val supplierPayablesRial: Long,
    val pendingReceiptCount: Int,
    val openRequisitionCount: Int,
    val pendingApprovalCount: Int,
    val overdueOrderCount: Int,
)
