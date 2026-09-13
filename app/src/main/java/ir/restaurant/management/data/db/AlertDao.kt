package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM app_alerts WHERE status NOT IN ('RESOLVED','DISMISSED') ORDER BY CASE severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, COALESCE(dueEpochDay, 9223372036854775807), updatedAtEpochMillis DESC")
    fun observeVisible(): Flow<List<AppAlertEntity>>

    @Query("SELECT * FROM app_alerts WHERE id=:id LIMIT 1")
    suspend fun byId(id: Long): AppAlertEntity?

    @Query("""
        UPDATE app_alerts SET
            title=:title,
            message=:message,
            severity=:severity,
            dueEpochDay=:dueEpochDay,
            branchId=:branchId,
            status=CASE WHEN status='RESOLVED' THEN 'NEW' ELSE status END,
            isDismissed=CASE WHEN status='RESOLVED' THEN 0 ELSE isDismissed END,
            updatedAtEpochMillis=:now
        WHERE sourceType=:sourceType AND sourceId=:sourceId AND locationId=:locationId
    """)
    suspend fun updateGenerated(
        sourceType: String,
        sourceId: Long,
        title: String,
        message: String,
        severity: String,
        dueEpochDay: Long?,
        branchId: Long,
        locationId: Long,
        now: Long,
    ): Int

    @Query("""
        INSERT OR IGNORE INTO app_alerts(
            sourceType,sourceId,title,message,severity,dueEpochDay,isRead,isDismissed,
            createdAtEpochMillis,updatedAtEpochMillis,status,branchId,locationId,snoozedUntilEpochMillis
        ) VALUES(:sourceType,:sourceId,:title,:message,:severity,:dueEpochDay,0,0,:now,:now,'NEW',:branchId,:locationId,NULL)
    """)
    suspend fun insertGeneratedIfAbsent(
        sourceType: String,
        sourceId: Long,
        title: String,
        message: String,
        severity: String,
        dueEpochDay: Long?,
        branchId: Long,
        locationId: Long,
        now: Long,
    )

    @Query("SELECT sourceType, sourceId, locationId FROM app_alerts WHERE status!='RESOLVED'")
    suspend fun generatedKeys(): List<AlertKeyRow>

    @Query("UPDATE app_alerts SET status='RESOLVED', isDismissed=0, snoozedUntilEpochMillis=NULL, updatedAtEpochMillis=:now WHERE sourceType=:sourceType AND sourceId=:sourceId AND locationId=:locationId AND status!='RESOLVED'")
    suspend fun resolveGenerated(sourceType: String, sourceId: Long, locationId: Long, now: Long): Int

    @Query("UPDATE app_alerts SET isRead=1, status=CASE WHEN status='NEW' THEN 'READ' ELSE status END, updatedAtEpochMillis=:now WHERE id=:id")
    suspend fun markRead(id: Long, now: Long): Int

    @Query("UPDATE app_alerts SET status='ACTIONED', isRead=1, snoozedUntilEpochMillis=NULL, updatedAtEpochMillis=:now WHERE id=:id AND status NOT IN ('RESOLVED','DISMISSED')")
    suspend fun markActioned(id: Long, now: Long): Int

    @Query("UPDATE app_alerts SET status='RESOLVED', isRead=1, snoozedUntilEpochMillis=NULL, updatedAtEpochMillis=:now WHERE id=:id AND status!='DISMISSED'")
    suspend fun resolve(id: Long, now: Long): Int

    @Query("UPDATE app_alerts SET isDismissed=1, isRead=1, status='DISMISSED', snoozedUntilEpochMillis=NULL, updatedAtEpochMillis=:now WHERE id=:id AND status!='RESOLVED'")
    suspend fun dismiss(id: Long, now: Long): Int

    @Query("UPDATE app_alerts SET snoozedUntilEpochMillis=:untilEpochMillis, isRead=1, status=CASE WHEN status='NEW' THEN 'READ' ELSE status END, updatedAtEpochMillis=:now WHERE id=:id AND status NOT IN ('RESOLVED','DISMISSED')")
    suspend fun snooze(id: Long, untilEpochMillis: Long, now: Long): Int

    @Query("DELETE FROM app_alerts WHERE status='DISMISSED' AND sourceType IN (:sourceTypes)")
    suspend fun clearDismissedForTypes(sourceTypes: List<String>): Int

    @Query("""
        SELECT p.id AS sourceId,
               'بدهی سررسیدشده تأمین‌کننده' AS title,
               ('فاکتور ' || p.invoiceNo || ' · ' || sp.name || ' · مانده ' || (p.totalRial-p.paidRial) || ' ریال') AS message,
               'HIGH' AS severity,
               p.dueEpochDay AS dueEpochDay,
               COALESCE(p.branchId,0) AS branchId,
               COALESCE(p.locationId,0) AS locationId
        FROM purchases p
        INNER JOIN suppliers sp ON sp.id=p.supplierId
        WHERE p.paymentStatus IN ('UNPAID','PARTIAL') AND p.dueEpochDay<=:todayEpochDay
        ORDER BY p.dueEpochDay
    """)
    suspend fun overduePurchases(todayEpochDay: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT i.id AS sourceId,
               'هشدار کمبود موجودی' AS title,
               (i.name || ' · ' || l.name || ': موجودی قابل استفاده به حد هشدار رسیده است') AS message,
               'MEDIUM' AS severity,
               NULL AS dueEpochDay,
               COALESCE(l.branchId,0) AS branchId,
               b.locationId AS locationId
        FROM inventory_balances b
        INNER JOIN inventory_items i ON i.id=b.itemId
        INNER JOIN storage_locations l ON l.id=b.locationId
        WHERE i.isActive=1 AND i.alertEnabled=1 AND l.isActive=1
          AND (b.onHandMicros-b.reservedMicros-b.damagedMicros-b.quarantinedMicros
               - COALESCE((SELECT SUM(lot.quantityMicros) FROM inventory_lots lot
                           WHERE lot.itemId=b.itemId AND lot.locationId=b.locationId
                             AND lot.status='ACTIVE' AND lot.quantityMicros>0
                             AND lot.expiryEpochDay IS NOT NULL AND lot.expiryEpochDay<:todayEpochDay),0))<=i.alertThresholdMicros
        ORDER BY (b.onHandMicros-b.reservedMicros-b.damagedMicros-b.quarantinedMicros
                  - COALESCE((SELECT SUM(lot.quantityMicros) FROM inventory_lots lot
                              WHERE lot.itemId=b.itemId AND lot.locationId=b.locationId
                                AND lot.status='ACTIVE' AND lot.quantityMicros>0
                                AND lot.expiryEpochDay IS NOT NULL AND lot.expiryEpochDay<:todayEpochDay),0)),i.name,l.name
    """)
    suspend fun lowStock(todayEpochDay: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT l.id AS sourceId,
               'لات نزدیک انقضا' AS title,
               (i.name || ' · لات ' || l.lotCode) AS message,
               'MEDIUM' AS severity,
               l.expiryEpochDay AS dueEpochDay,
               COALESCE(sl.branchId,0) AS branchId,
               l.locationId AS locationId
        FROM inventory_lots l
        INNER JOIN inventory_items i ON i.id=l.itemId
        INNER JOIN storage_locations sl ON sl.id=l.locationId
        WHERE l.status='ACTIVE' AND l.quantityMicros>0 AND l.expiryEpochDay IS NOT NULL
          AND l.expiryEpochDay>=:today AND l.expiryEpochDay<=:horizon
        ORDER BY l.expiryEpochDay
    """)
    suspend fun expiringLots(today: Long, horizon: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT l.id AS sourceId,
               'لات منقضی شده' AS title,
               (i.name || ' · لات ' || l.lotCode) AS message,
               'HIGH' AS severity,
               l.expiryEpochDay AS dueEpochDay,
               COALESCE(sl.branchId,0) AS branchId,
               l.locationId AS locationId
        FROM inventory_lots l
        INNER JOIN inventory_items i ON i.id=l.itemId
        INNER JOIN storage_locations sl ON sl.id=l.locationId
        WHERE l.status='ACTIVE' AND l.quantityMicros>0 AND l.expiryEpochDay IS NOT NULL
          AND l.expiryEpochDay<:today
        ORDER BY l.expiryEpochDay
    """)
    suspend fun expiredLots(today: Long): List<GeneratedAlertRow>

    /** Canonical AR source: Phase-2/3 receivables, never re-derived from sales invoices. */
    @Query("""
        SELECT r.id AS sourceId,
               'مطالبه سررسیدشده مشتری' AS title,
               (c.name || ' · مانده سررسیدشده ' || r.outstandingAmountRial || ' ریال') AS message,
               'HIGH' AS severity,
               r.dueEpochDay AS dueEpochDay,
               r.branchId AS branchId,
               0 AS locationId
        FROM receivables r
        INNER JOIN customers c ON c.id=r.partyId
        WHERE r.status IN ('OPEN','PARTIALLY_PAID')
          AND r.outstandingAmountRial>0
          AND r.dueEpochDay IS NOT NULL AND r.dueEpochDay<=:today
        ORDER BY r.dueEpochDay,r.id
    """)
    suspend fun overdueReceivables(today: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT ec.id AS sourceId,
               'پایان قرارداد پرسنل' AS title,
               (e.name || ' · قرارداد ' || ec.contractNumber) AS message,
               CASE WHEN ec.effectiveToEpochDay<:today THEN 'HIGH' ELSE 'MEDIUM' END AS severity,
               ec.effectiveToEpochDay AS dueEpochDay,
               COALESCE(e.branchId,0) AS branchId,
               COALESCE(e.locationId,0) AS locationId
        FROM employment_contract_versions ec INNER JOIN employees e ON e.id=ec.employeeId
        WHERE ec.status='ACTIVE' AND ec.effectiveToEpochDay IS NOT NULL AND ec.effectiveToEpochDay<=:horizon
        ORDER BY ec.effectiveToEpochDay
    """)
    suspend fun expiringContracts(today: Long, horizon: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT ps.id AS sourceId,
               'حقوق پرداخت‌نشده' AS title,
               (ps.employeeNameSnapshot || ' · مانده ' || ps.remainingAmountRial || ' ریال') AS message,
               'HIGH' AS severity,
               NULL AS dueEpochDay,
               COALESCE(e.branchId,0) AS branchId,
               COALESCE(e.locationId,0) AS locationId
        FROM payroll_payslips ps
        INNER JOIN employees e ON e.id=ps.employeeId
        WHERE ps.remainingAmountRial>0 AND ps.status IN ('APPROVED','POSTED','PARTIALLY_PAID')
        ORDER BY ps.id DESC
    """)
    suspend fun unpaidPayroll(): List<GeneratedAlertRow>

    @Query("""
        SELECT fa.id AS sourceId,
               'سرویس دارایی سررسید شده' AS title,
               (fa.name || ' · ' || am.serviceType) AS message,
               CASE WHEN am.nextServiceEpochDay<:today THEN 'HIGH' ELSE 'MEDIUM' END AS severity,
               am.nextServiceEpochDay AS dueEpochDay,
               COALESCE(fa.branchId,0) AS branchId,
               0 AS locationId
        FROM asset_maintenance am INNER JOIN fixed_assets fa ON fa.id=am.assetId
        WHERE fa.status='ACTIVE' AND am.nextServiceEpochDay IS NOT NULL AND am.nextServiceEpochDay<=:horizon
          AND am.id=(SELECT MAX(am2.id) FROM asset_maintenance am2 WHERE am2.assetId=am.assetId)
        ORDER BY am.nextServiceEpochDay
    """)
    suspend fun dueAssetMaintenance(today: Long, horizon: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT ac.id AS sourceId,
               'مغایرت حضور و غیاب' AS title,
               (e.name || ' · درخواست اصلاح در انتظار بررسی') AS message,
               'MEDIUM' AS severity,
               ac.businessEpochDay AS dueEpochDay,
               COALESCE(e.branchId,0) AS branchId,
               COALESCE(e.locationId,0) AS locationId
        FROM attendance_corrections ac INNER JOIN employees e ON e.id=ac.employeeId
        WHERE ac.status IN ('PENDING','SUBMITTED')
        ORDER BY ac.businessEpochDay
    """)
    suspend fun attendanceAnomalies(): List<GeneratedAlertRow>

    @Query("""
        SELECT po.id AS sourceId,
               'تحویل خرید عقب‌افتاده' AS title,
               (po.orderNo || ' · ' || po.supplierNameSnapshot) AS message,
               'HIGH' AS severity,
               COALESCE(po.confirmedExpectedEpochDay,po.expectedEpochDay) AS dueEpochDay,
               COALESCE(po.branchId,0) AS branchId,
               COALESCE(po.destinationLocationId,0) AS locationId
        FROM purchase_orders po
        WHERE po.status NOT IN ('RECEIVED','CANCELLED','CLOSED')
          AND COALESCE(po.confirmedExpectedEpochDay,po.expectedEpochDay)<:today
        ORDER BY dueEpochDay
    """)
    suspend fun overdueDeliveries(today: Long): List<GeneratedAlertRow>

    @Query("""
        SELECT l.id AS sourceId,
               'مغایرت انبارگردانی' AS title,
               ('سند ' || s.documentNumber || ' · اختلاف ' || l.varianceQuantityMicros) AS message,
               'HIGH' AS severity,
               s.businessEpochDay AS dueEpochDay,
               COALESCE(sl.branchId,0) AS branchId,
               s.locationId AS locationId
        FROM inventory_count_lines l
        INNER JOIN inventory_count_sessions s ON s.id=l.sessionId
        INNER JOIN storage_locations sl ON sl.id=s.locationId
        WHERE l.varianceQuantityMicros IS NOT NULL AND l.varianceQuantityMicros!=0 AND s.status IN ('SUBMITTED','APPROVED')
        ORDER BY s.businessEpochDay DESC
    """)
    suspend fun inventoryDiscrepancies(): List<GeneratedAlertRow>
}

data class AlertKeyRow(val sourceType: String, val sourceId: Long, val locationId: Long)
