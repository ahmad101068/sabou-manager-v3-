package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardAnalyticsDao {
    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(ds.grossSalesRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS grossSalesRial,
          COALESCE((SELECT SUM(ds.netSalesRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS netSalesRial,
          COALESCE((SELECT SUM(ds.discountRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS discountRial,
          COALESCE((SELECT SUM(ds.taxRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS taxRial,
          COALESCE((SELECT SUM(ds.serviceRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS serviceRial,
          COALESCE((SELECT SUM(ds.returnRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS salesReturnRial,
          COALESCE((SELECT SUM(ds.theoreticalCostRial) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS cogsRial,
          COALESCE((SELECT SUM(p.totalRial) FROM purchases p WHERE p.purchaseEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND p.paymentStatus!='REVERSED' AND (:branchId IS NULL OR p.branchId=:branchId)),0) AS purchaseRial,
          COALESCE((SELECT SUM(pr.supplierCreditValueRial) FROM purchase_returns pr LEFT JOIN purchases pp ON pp.id=pr.purchaseId WHERE pr.returnEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND (:branchId IS NULL OR pp.branchId=:branchId)),0) AS purchaseReturnRial,
          COALESCE((SELECT SUM(p.totalRial-p.paidRial) FROM purchases p WHERE p.paymentStatus IN ('UNPAID','PARTIAL') AND (:branchId IS NULL OR p.branchId=:branchId)),0) AS supplierPayablesRial,
          COALESCE((
            SELECT CASE
              WHEN :warehouseLocationId IS NULL AND :branchId IS NULL
                THEN (SELECT SUM(i.inventoryValueRial) FROM inventory_items i WHERE i.isActive=1)
              WHEN :warehouseLocationId IS NULL
                THEN (SELECT SUM(b.inventoryValueRial) FROM inventory_balances b INNER JOIN inventory_items i ON i.id=b.itemId INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE i.isActive=1 AND sl.branchId=:branchId)
              ELSE (SELECT SUM(b.inventoryValueRial) FROM inventory_balances b INNER JOIN inventory_items i ON i.id=b.itemId INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE i.isActive=1 AND b.locationId=:warehouseLocationId AND (:branchId IS NULL OR sl.branchId=:branchId))
            END
          ),0) AS inventoryValueRial,
          COALESCE((SELECT COUNT(*) FROM inventory_items i WHERE i.isActive=1 AND i.alertEnabled=1 AND
            ((:warehouseLocationId IS NULL AND :branchId IS NULL AND i.stockMicros<=i.alertThresholdMicros) OR
             (:warehouseLocationId IS NULL AND :branchId IS NOT NULL AND EXISTS(SELECT 1 FROM inventory_balances b INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE b.itemId=i.id AND sl.branchId=:branchId AND b.onHandMicros<=i.alertThresholdMicros)) OR
             (:warehouseLocationId IS NOT NULL AND EXISTS(SELECT 1 FROM inventory_balances b INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE b.itemId=i.id AND b.locationId=:warehouseLocationId AND (:branchId IS NULL OR sl.branchId=:branchId) AND b.onHandMicros<=i.alertThresholdMicros)))),0) AS lowStockCount,
          COALESCE((SELECT COUNT(*) FROM inventory_lots l INNER JOIN storage_locations sl ON sl.id=l.locationId WHERE l.status='ACTIVE' AND l.quantityMicros>0 AND l.expiryEpochDay BETWEEN :toEpochDay AND (:toEpochDay+30) AND (:warehouseLocationId IS NULL OR l.locationId=:warehouseLocationId) AND (:branchId IS NULL OR sl.branchId=:branchId)),0) AS expiringLotCount,
          COALESCE((SELECT SUM(w.valueRial) FROM inventory_waste_documents w INNER JOIN storage_locations sl ON sl.id=w.locationId WHERE w.status='POSTED' AND w.wasteEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND (:warehouseLocationId IS NULL OR w.locationId=:warehouseLocationId) AND (:branchId IS NULL OR sl.branchId=:branchId)),0) AS wasteRial,
          COALESCE((SELECT COUNT(*) FROM inventory_items i WHERE i.isActive=1 AND
            ((:warehouseLocationId IS NULL AND :branchId IS NULL AND i.stockMicros>0) OR
             (:warehouseLocationId IS NULL AND :branchId IS NOT NULL AND EXISTS(SELECT 1 FROM inventory_balances b INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE b.itemId=i.id AND sl.branchId=:branchId AND b.onHandMicros>0)) OR
             (:warehouseLocationId IS NOT NULL AND EXISTS(SELECT 1 FROM inventory_balances b INNER JOIN storage_locations sl ON sl.id=b.locationId WHERE b.itemId=i.id AND b.locationId=:warehouseLocationId AND (:branchId IS NULL OR sl.branchId=:branchId) AND b.onHandMicros>0))) AND
            NOT EXISTS(SELECT 1 FROM stock_movements sm LEFT JOIN storage_locations slm ON slm.id=sm.locationId WHERE sm.itemId=i.id AND sm.movementEpochDay>=(:toEpochDay-30) AND (:warehouseLocationId IS NULL OR sm.locationId=:warehouseLocationId) AND (:branchId IS NULL OR slm.branchId=:branchId))),0) AS slowStockCount,
          COALESCE((SELECT SUM(jl.debitRial-jl.creditRial) FROM journal_lines jl INNER JOIN journal_entries je ON je.id=jl.entryId WHERE je.status='POSTED' AND jl.accountCode='1101' AND (:branchId IS NULL OR (je.accountingScope='BRANCH' AND je.branchId=:branchId))),0) AS cashBalanceRial,
          COALESCE((SELECT SUM(jl.debitRial-jl.creditRial) FROM journal_lines jl INNER JOIN journal_entries je ON je.id=jl.entryId WHERE je.status='POSTED' AND jl.accountCode='1102' AND (:branchId IS NULL OR (je.accountingScope='BRANCH' AND je.branchId=:branchId))),0) AS bankBalanceRial,
          COALESCE((SELECT SUM(r.outstandingAmountRial) FROM receivables r WHERE r.status IN ('OPEN','PARTIALLY_PAID') AND (:branchId IS NULL OR r.branchId=:branchId)),0) AS customerReceivablesRial,
          COALESCE((SELECT COUNT(DISTINCT ae.employeeId) FROM attendance_events ae INNER JOIN employees e ON e.id=ae.employeeId WHERE ae.businessEpochDay=:toEpochDay AND ae.eventType IN ('CHECK_IN','IN') AND (:branchId IS NULL OR e.branchId=:branchId)),0) AS presentCount,
          COALESCE((SELECT COUNT(*) FROM planned_shifts ps INNER JOIN employees e ON e.id=ps.employeeId WHERE ps.epochDay=:toEpochDay AND (:branchId IS NULL OR e.branchId=:branchId) AND NOT EXISTS(SELECT 1 FROM attendance_events ae WHERE ae.employeeId=ps.employeeId AND ae.businessEpochDay=:toEpochDay AND ae.eventType IN ('CHECK_IN','IN'))),0) AS absentCount,
          COALESCE((SELECT COUNT(*) FROM attendance_corrections ac INNER JOIN employees e ON e.id=ac.employeeId WHERE ac.businessEpochDay=:toEpochDay AND ac.status='PENDING' AND (:branchId IS NULL OR e.branchId=:branchId)),0) AS attendanceAnomalyCount,
          COALESCE((SELECT SUM(ps.remainingAmountRial) FROM payroll_payslips ps INNER JOIN employees e ON e.id=ps.employeeId WHERE ps.remainingAmountRial>0 AND ps.status IN ('APPROVED','PAYMENT_PENDING','PARTIALLY_PAID') AND (:branchId IS NULL OR e.branchId=:branchId)),0) AS unpaidPayrollRial,
          COALESCE((SELECT SUM(MAX(0,fa.purchaseCostRial-fa.accumulatedDepreciationRial-fa.impairmentRial)) FROM fixed_assets fa WHERE fa.status='ACTIVE' AND (:branchId IS NULL OR fa.branchId=:branchId)),0) AS assetBookValueRial,
          COALESCE((SELECT SUM(ad.amountRial) FROM asset_depreciations ad INNER JOIN fixed_assets fa ON fa.id=ad.assetId WHERE ((ad.periodYear*12)+ad.periodMonth) >= 0 AND (:branchId IS NULL OR fa.branchId=:branchId)),0) AS accumulatedDepreciationRial,
          COALESCE((SELECT COUNT(*) FROM asset_maintenance am INNER JOIN fixed_assets fa ON fa.id=am.assetId WHERE fa.status='ACTIVE' AND (:branchId IS NULL OR fa.branchId=:branchId) AND am.nextServiceEpochDay IS NOT NULL AND am.nextServiceEpochDay<=:toEpochDay+30 AND am.id=(SELECT MAX(am2.id) FROM asset_maintenance am2 WHERE am2.assetId=am.assetId)),0) AS dueMaintenanceCount,
          COALESCE((SELECT COUNT(*) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS invoiceCount,
          COALESCE((SELECT COUNT(*) FROM daily_sales_summaries ds WHERE ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND ds.returnRial>0 AND (:branchId IS NULL OR ds.branchId=:branchId)),0) AS returnCount
        """,
    )
    fun observeRange(
        fromEpochDay: Long,
        toEpochDay: Long,
        branchId: Long?,
        warehouseLocationId: Long?,
    ): Flow<DashboardAnalyticsRow>

}

data class DashboardAnalyticsRow(
    val grossSalesRial: Long,
    val netSalesRial: Long,
    val discountRial: Long,
    val taxRial: Long,
    val serviceRial: Long,
    val salesReturnRial: Long,
    val cogsRial: Long,
    val purchaseRial: Long,
    val purchaseReturnRial: Long,
    val supplierPayablesRial: Long,
    val inventoryValueRial: Long,
    val lowStockCount: Long,
    val expiringLotCount: Long,
    val wasteRial: Long,
    val slowStockCount: Long,
    val cashBalanceRial: Long,
    val bankBalanceRial: Long,
    val customerReceivablesRial: Long,
    val presentCount: Long,
    val absentCount: Long,
    val attendanceAnomalyCount: Long,
    val unpaidPayrollRial: Long,
    val assetBookValueRial: Long,
    val accumulatedDepreciationRial: Long,
    val dueMaintenanceCount: Long,
    val invoiceCount: Long,
    val returnCount: Long,
)
