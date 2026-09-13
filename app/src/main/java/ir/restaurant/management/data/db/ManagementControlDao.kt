package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagementControlDao {
    @Insert suspend fun insertFollowUp(entity: PurchaseOrderFollowUpEntity): Long

    @Query("SELECT * FROM purchase_orders WHERE id = :id LIMIT 1")
    suspend fun purchaseOrder(id: Long): PurchaseOrderEntity?
    @Query("SELECT * FROM shift_templates ORDER BY active DESC, category, name")
    fun observeShiftTemplates(): Flow<List<ShiftTemplateEntity>>
    @Query("SELECT * FROM shift_templates WHERE id = :id LIMIT 1")
    suspend fun shiftTemplate(id: Long): ShiftTemplateEntity?
    @Query("SELECT * FROM shift_templates WHERE active = 1 ORDER BY category, name")
    suspend fun activeShiftTemplates(): List<ShiftTemplateEntity>
    @Insert suspend fun insertShiftTemplate(entity: ShiftTemplateEntity): Long
    @Update suspend fun updateShiftTemplate(entity: ShiftTemplateEntity): Int

    @Query("SELECT * FROM work_schedules ORDER BY active DESC, name")
    fun observeWorkSchedules(): Flow<List<WorkScheduleEntity>>
    @Query("SELECT * FROM work_schedules WHERE id = :id LIMIT 1")
    suspend fun workSchedule(id: Long): WorkScheduleEntity?
    @Query("SELECT * FROM work_schedule_days WHERE scheduleId = :scheduleId ORDER BY sequenceDay")
    suspend fun workScheduleDays(scheduleId: Long): List<WorkScheduleDayEntity>
    @Insert suspend fun insertWorkSchedule(entity: WorkScheduleEntity): Long
    @Update suspend fun updateWorkSchedule(entity: WorkScheduleEntity): Int
    @Query("DELETE FROM work_schedule_days WHERE scheduleId = :scheduleId")
    suspend fun deleteWorkScheduleDays(scheduleId: Long): Int
    @Insert suspend fun insertWorkScheduleDays(entities: List<WorkScheduleDayEntity>): List<Long>

    @Query("SELECT * FROM planned_shifts WHERE id = :id LIMIT 1") suspend fun plannedShift(id: Long): PlannedShiftEntity?
    @Query("SELECT * FROM planned_shifts WHERE employeeId = :employeeId AND epochDay = :businessEpochDay AND status IN ('PUBLISHED','LOCKED') ORDER BY plannedStartEpochMillis LIMIT 1")
    suspend fun plannedShiftForEmployeeDay(employeeId: Long, businessEpochDay: Long): PlannedShiftEntity?
    @Query("SELECT * FROM planned_shifts WHERE employeeId IN (:employeeIds) AND epochDay BETWEEN :fromEpochDay AND :toEpochDay AND status IN ('PUBLISHED','LOCKED') ORDER BY employeeId, epochDay, plannedStartEpochMillis")
    suspend fun plannedShiftsForEmployeesInRange(employeeIds: List<Long>, fromEpochDay: Long, toEpochDay: Long): List<PlannedShiftEntity>
    @Query("SELECT * FROM planned_shifts WHERE employeeId = :employeeId AND epochDay = :businessEpochDay AND status != 'CANCELLED' ORDER BY plannedStartEpochMillis")
    suspend fun plannedShiftsForEmployeeDay(employeeId: Long, businessEpochDay: Long): List<PlannedShiftEntity>
    @Query("SELECT * FROM planned_shifts WHERE employeeId = :employeeId AND epochDay BETWEEN :fromEpochDay AND :toEpochDay AND status != 'CANCELLED' ORDER BY epochDay, plannedStartEpochMillis")
    suspend fun plannedShiftsForEmployeeRange(employeeId: Long, fromEpochDay: Long, toEpochDay: Long): List<PlannedShiftEntity>
    @Query("SELECT * FROM planned_shifts WHERE employeeId = :employeeId ORDER BY epochDay DESC, plannedStartEpochMillis DESC")
    fun observePlannedShifts(employeeId: Long): Flow<List<PlannedShiftEntity>>
    @Insert suspend fun insertPlannedShift(entity: PlannedShiftEntity): Long
    @Update suspend fun updatePlannedShift(entity: PlannedShiftEntity): Int
    @Query("UPDATE planned_shifts SET status = :status, updatedBy = :actor, overrideReason = :reason WHERE id = :id AND status != 'CANCELLED'")
    suspend fun transitionPlannedShift(id: Long, status: String, actor: String, reason: String): Int
    @Query("UPDATE planned_shifts SET employeeId = :employeeId, employeeName = :employeeName, updatedBy = :employeeName WHERE id = :shiftId") suspend fun reassignShift(shiftId: Long, employeeId: Long, employeeName: String): Int

    @Insert suspend fun insertLocation(entity: StorageLocationEntity): Long
    @Query("SELECT * FROM storage_locations WHERE id = :id AND isActive = 1 LIMIT 1") suspend fun activeLocation(id: Long): StorageLocationEntity?
    @Query("SELECT id FROM storage_locations WHERE isActive = 1 ORDER BY CASE WHEN code = 'MAIN' THEN 0 ELSE 1 END, id LIMIT 1") suspend fun defaultLocationId(): Long?
    @Query("SELECT * FROM storage_locations ORDER BY isActive DESC, name") fun observeLocations(): Flow<List<StorageLocationEntity>>

    @Insert suspend fun insertBudget(entity: OperatingBudgetEntity): Long
    @Update suspend fun updateBudget(entity: OperatingBudgetEntity): Int
    @Query("SELECT * FROM operating_budgets WHERE id = :id LIMIT 1") suspend fun budget(id: Long): OperatingBudgetEntity?
    @Query("SELECT * FROM operating_budgets WHERE category = 'PURCHASE' AND :epochDay BETWEEN fromEpochDay AND toEpochDay AND (costCenter = :costCenter OR costCenter = 'کل مجموعه') ORDER BY CASE WHEN costCenter = :costCenter THEN 0 ELSE 1 END, toEpochDay LIMIT 1") suspend fun activePurchaseBudget(epochDay: Long, costCenter: String): OperatingBudgetEntity?
    @Query("SELECT COALESCE(SUM(amountRial),0) FROM budget_commitments WHERE budgetId = :budgetId AND status = 'COMMITTED'") suspend fun committedAmount(budgetId: Long): Long
    @Query("""
        SELECT COALESCE((SELECT SUM(e.amountRial) FROM budget_spend_entries e WHERE e.budgetId = b.id), 0) +
               CASE b.category
                 WHEN 'PURCHASE' THEN COALESCE((SELECT SUM(p.totalRial) FROM purchases p WHERE p.paymentStatus != 'REVERSED' AND p.purchaseEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 WHEN 'LABOR' THEN COALESCE((SELECT SUM(pr.netPayRial) FROM payroll_runs pr WHERE pr.status != 'REVERSED' AND pr.paymentEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 WHEN 'WASTE' THEN COALESCE((SELECT SUM(-sm.valueDeltaRial) FROM stock_movements sm WHERE sm.movementType = 'WASTE' AND sm.valueDeltaRial < 0 AND sm.movementEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 ELSE 0
               END
        FROM operating_budgets b WHERE b.id = :budgetId
    """) suspend fun actualBudgetSpend(budgetId: Long): Long
    @Insert suspend fun insertBudgetCommitment(entity: BudgetCommitmentEntity): Long
    @Query("UPDATE budget_commitments SET status = :status, updatedAtEpochMillis = :now WHERE referenceType = :referenceType AND referenceId = :referenceId AND status = 'COMMITTED'") suspend fun transitionCommitment(referenceType: String, referenceId: Long, status: String, now: Long): Int
    @Query("""
        SELECT b.id, b.name, b.category, b.costCenter, b.fromEpochDay, b.toEpochDay, b.limitRial,
               COALESCE(SUM(e.amountRial), 0) AS manualSpendRial,
               CASE b.category
                 WHEN 'PURCHASE' THEN COALESCE((SELECT SUM(p.totalRial) FROM purchases p WHERE p.paymentStatus != 'REVERSED' AND p.purchaseEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 WHEN 'LABOR' THEN COALESCE((SELECT SUM(pr.netPayRial) FROM payroll_runs pr WHERE pr.status != 'REVERSED' AND pr.paymentEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 WHEN 'WASTE' THEN COALESCE((SELECT SUM(-sm.valueDeltaRial) FROM stock_movements sm WHERE sm.movementType = 'WASTE' AND sm.valueDeltaRial < 0 AND sm.movementEpochDay BETWEEN b.fromEpochDay AND b.toEpochDay), 0)
                 ELSE 0
               END AS automaticSpendRial,
               COALESCE((SELECT SUM(c.amountRial) FROM budget_commitments c WHERE c.budgetId = b.id AND c.status = 'COMMITTED'),0) AS committedRial
        FROM operating_budgets b
        LEFT JOIN budget_spend_entries e ON e.budgetId = b.id
        GROUP BY b.id
        ORDER BY b.toEpochDay DESC, b.name
    """) fun observeBudgets(): Flow<List<BudgetWithSpendRow>>
    @Insert suspend fun insertBudgetSpend(entity: BudgetSpendEntryEntity): Long

    @Query("SELECT * FROM accounting_period_locks ORDER BY toEpochDay DESC, id DESC") fun observeAccountingPeriodLocks(): Flow<List<AccountingPeriodLockEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM accounting_period_locks WHERE status='CLOSED' AND fromEpochDay <= :toEpochDay AND toEpochDay >= :fromEpochDay)") suspend fun accountingPeriodOverlaps(fromEpochDay: Long, toEpochDay: Long): Boolean
    @Insert suspend fun insertAccountingPeriodLock(entity: AccountingPeriodLockEntity): Long
    @Query("UPDATE accounting_period_locks SET status='REOPENED', reopenedBy=:actor, reopenedAtEpochMillis=:now WHERE id=:id AND status='CLOSED'") suspend fun reopenAccountingPeriod(id: Long, actor: String, now: Long): Int
    @Query("SELECT * FROM accounting_period_locks WHERE id=:id LIMIT 1") suspend fun accountingPeriodLock(id: Long): AccountingPeriodLockEntity?

    @Query("SELECT * FROM sales_cash_reconciliations ORDER BY businessEpochDay DESC, revisionNo DESC LIMIT 100") fun observeCashReconciliations(): Flow<List<SalesCashReconciliationEntity>>
    @Insert suspend fun insertCashReconciliation(entity: SalesCashReconciliationEntity): Long

    @Query("""SELECT je.id,je.entryNo,je.entryEpochDay,je.description,je.sourceType,je.sourceId,COALESCE(SUM(jl.debitRial),0) AS debitRial,COALESCE(SUM(jl.creditRial),0) AS creditRial FROM journal_entries je LEFT JOIN journal_lines jl ON jl.entryId=je.id WHERE je.status='POSTED' AND je.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay GROUP BY je.id ORDER BY je.entryEpochDay DESC,je.id DESC LIMIT 100""") fun observeKpiTrace(fromEpochDay: Long, toEpochDay: Long): Flow<List<KpiTraceRow>>

    @Query("""
        SELECT
          COALESCE((SELECT SUM(s.netSalesRial) FROM daily_sales_summaries s WHERE s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND s.status='POSTED' AND s.reversedAtEpochDay IS NULL), 0) AS salesRial,
          COALESCE((SELECT SUM(s.theoreticalCostRial) FROM daily_sales_summaries s WHERE s.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND s.status='POSTED' AND s.reversedAtEpochDay IS NULL), 0) AS theoreticalCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm JOIN daily_sales_summaries ds ON ds.id=sm.referenceId WHERE sm.referenceType='DAILY_SALES' AND sm.movementType='DAILY_SALES_CONSUMPTION' AND sm.valueDeltaRial<0 AND ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND ds.reversedAtEpochDay IS NULL),0) AS standardSalesLedgerCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm WHERE sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sm.movementType='WASTE' AND sm.valueDeltaRial<0),0) AS wasteCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm WHERE sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sm.movementType IN ('INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.valueDeltaRial<0),0) AS negativeAdjustmentCostRial,
          COALESCE((SELECT SUM(sm.valueDeltaRial) FROM stock_movements sm WHERE sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sm.movementType IN ('INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.valueDeltaRial>0),0) AS positiveAdjustmentCostRial,
          COALESCE((SELECT COUNT(*) FROM stock_movements sm WHERE sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sm.movementType IN ('WASTE','INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.valueDeltaRial!=0),0) AS actualEvidenceCount
    """) fun observeFoodCost(fromEpochDay: Long, toEpochDay: Long): Flow<FoodCostRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveLaborPolicy(entity: LaborPolicyEntity)
    @Query("SELECT * FROM labor_policy WHERE singletonId = 1") fun observeLaborPolicy(): Flow<LaborPolicyEntity?>
    @Query("""
        SELECT p.id AS shiftId, p.employeeId, p.employeeName, p.epochDay, p.startMinute, p.endMinute,
               COALESCE(SUM(w.endMinute - w.startMinute), 0) AS breakMinutes
        FROM planned_shifts p
        LEFT JOIN work_breaks w ON w.shiftId = p.id
        GROUP BY p.id
        ORDER BY p.epochDay, p.startMinute
    """) fun observeShiftBreaks(): Flow<List<ShiftBreakRow>>
    @Insert suspend fun insertWorkBreak(entity: WorkBreakEntity): Long
    @Query("SELECT * FROM work_breaks WHERE shiftId = :shiftId") suspend fun workBreaks(shiftId: Long): List<WorkBreakEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAvailability(entity: EmployeeAvailabilityEntity): Long
    @Query("SELECT * FROM employee_availability WHERE employeeId = :employeeId AND dayOfWeek = :dayOfWeek LIMIT 1") suspend fun availability(employeeId: Long, dayOfWeek: Int): EmployeeAvailabilityEntity?
    @Query("""SELECT a.id, a.employeeId, e.name AS employeeName, a.dayOfWeek, a.fromMinute, a.toMinute, a.isAvailable FROM employee_availability a INNER JOIN employees e ON e.id = a.employeeId ORDER BY e.name, a.dayOfWeek""") fun observeAvailabilities(): Flow<List<EmployeeAvailabilityRow>>
    @Insert suspend fun insertShiftSwap(entity: ShiftSwapRequestEntity): Long
    @Query("SELECT * FROM shift_swap_requests WHERE id = :id LIMIT 1") suspend fun shiftSwap(id: Long): ShiftSwapRequestEntity?
    @Query("UPDATE shift_swap_requests SET status = :status, reviewedBy = :reviewedBy, reviewedAtEpochMillis = :reviewedAt WHERE id = :id AND status = 'PENDING'") suspend fun reviewShiftSwap(id: Long, status: String, reviewedBy: String, reviewedAt: Long): Int
    @Query("""SELECT r.id, r.shiftId, r.requesterEmployeeId, requester.name AS requesterName, r.targetEmployeeId, target.name AS targetName, r.status, r.note FROM shift_swap_requests r INNER JOIN employees requester ON requester.id = r.requesterEmployeeId LEFT JOIN employees target ON target.id = r.targetEmployeeId ORDER BY CASE r.status WHEN 'PENDING' THEN 0 ELSE 1 END, r.createdAtEpochMillis DESC""") fun observeShiftSwaps(): Flow<List<ShiftSwapRow>>
}
