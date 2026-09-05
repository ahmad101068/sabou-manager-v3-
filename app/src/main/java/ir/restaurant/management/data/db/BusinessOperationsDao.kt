package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessOperationsDao {
    @Insert suspend fun insertSettlement(row: DailySalesSettlementEntity): Long
    @Insert suspend fun insertSettlements(rows: List<DailySalesSettlementEntity>): List<Long>
    @Query("SELECT * FROM daily_sales_settlements WHERE dailySalesId=:dailySalesId ORDER BY id") suspend fun settlements(dailySalesId: Long): List<DailySalesSettlementEntity>
    @Query("SELECT * FROM daily_sales_settlements ORDER BY dailySalesId,id") fun observeAllSettlements(): Flow<List<DailySalesSettlementEntity>>
    @Query("SELECT COALESCE(SUM(amountRial),0) FROM daily_sales_settlements WHERE dailySalesId=:dailySalesId") suspend fun settlementTotal(dailySalesId: Long): Long
    @Query("DELETE FROM daily_sales_settlements WHERE dailySalesId=:dailySalesId") suspend fun deleteSettlements(dailySalesId: Long): Int

    @Insert suspend fun insertReceivable(row: ReceivableEntity): Long
    @Query("SELECT * FROM receivables WHERE id=:id") suspend fun receivable(id: Long): ReceivableEntity?
    @Query("SELECT * FROM receivables WHERE globalId=:globalId LIMIT 1") suspend fun receivableByGlobalId(globalId: String): ReceivableEntity?
    @Query("SELECT * FROM receivables WHERE branchId=:branchId AND status IN ('OPEN','PARTIALLY_PAID') ORDER BY dueEpochDay, id") fun observeOpenReceivables(branchId: Long): Flow<List<ReceivableEntity>>
    @Query("SELECT * FROM receivables WHERE branchId=:branchId AND status IN ('OPEN','PARTIALLY_PAID') ORDER BY dueEpochDay, id") suspend fun openReceivables(branchId: Long): List<ReceivableEntity>
    @Query("SELECT * FROM receivables WHERE partyId=:partyId AND status IN ('OPEN','PARTIALLY_PAID') ORDER BY dueEpochDay, id") suspend fun openReceivablesForParty(partyId: Long): List<ReceivableEntity>
    @Query("SELECT * FROM receivables WHERE partyId IN (:partyIds) AND status IN ('OPEN','PARTIALLY_PAID') ORDER BY dueEpochDay, id") suspend fun openReceivablesForParties(partyIds: List<Long>): List<ReceivableEntity>
    @Query("UPDATE receivables SET paidAmountRial=:paid, outstandingAmountRial=:outstanding, status=:status, updatedAtEpochMillis=:updatedAt WHERE id=:id AND paidAmountRial=:expectedPaid AND outstandingAmountRial=:expectedOutstanding")
    suspend fun updateReceivableBalance(id: Long, expectedPaid: Long, expectedOutstanding: Long, paid: Long, outstanding: Long, status: String, updatedAt: Long): Int
    @Query("UPDATE receivables SET paidAmountRial=0, outstandingAmountRial=0, status='VOIDED', updatedAtEpochMillis=:updatedAt WHERE id=:id AND status!='VOIDED' AND paidAmountRial=:expectedPaid AND outstandingAmountRial=:expectedOutstanding")
    suspend fun voidReceivable(id: Long, expectedPaid: Long, expectedOutstanding: Long, updatedAt: Long): Int
    @Query("SELECT * FROM receivables WHERE sourceType=:sourceType AND sourceId=:sourceId ORDER BY id") suspend fun receivablesBySource(sourceType: String, sourceId: Long): List<ReceivableEntity>
    @Query("UPDATE receivables SET partyId=:targetPartyId WHERE partyId=:sourcePartyId") suspend fun moveReceivables(sourcePartyId: Long, targetPartyId: Long): Int
    @Insert suspend fun insertCollection(row: ReceivableCollectionEntity): Long
    @Query("SELECT * FROM receivable_collections WHERE id=:id") suspend fun collection(id: Long): ReceivableCollectionEntity?
    @Query("SELECT * FROM receivable_collections WHERE globalId=:globalId LIMIT 1") suspend fun collectionByGlobalId(globalId: String): ReceivableCollectionEntity?
    @Query("SELECT * FROM receivable_collections WHERE receivableId=:receivableId AND reversedAtEpochMillis IS NULL ORDER BY id") suspend fun activeCollections(receivableId: Long): List<ReceivableCollectionEntity>
    @Query("SELECT COUNT(*) FROM receivable_collections WHERE receivableId=:receivableId AND reversedAtEpochMillis IS NULL") suspend fun activeCollectionCount(receivableId: Long): Int
    @Query("UPDATE receivable_collections SET reversedAtEpochMillis=:reversedAt,reversalReason=:reason,reversalJournalEntryId=:journalEntryId WHERE id=:id AND reversedAtEpochMillis IS NULL") suspend fun markCollectionReversed(id: Long, reversedAt: Long, reason: String, journalEntryId: Long): Int
    @Query("SELECT COALESCE(SUM(outstandingAmountRial),0) FROM receivables WHERE branchId=:branchId AND partyId=:partyId AND status IN ('OPEN','PARTIALLY_PAID')") suspend fun partyOutstanding(branchId: Long, partyId: Long): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIssue(row: ManagementIssueEntity): Long
    @Query("SELECT * FROM management_issues WHERE branchId=:branchId AND status NOT IN ('RESOLVED','DISMISSED') ORDER BY severity, detectedAtEpochMillis DESC") fun observeOpenIssues(branchId: Long): Flow<List<ManagementIssueEntity>>
    @Query("SELECT * FROM management_issues WHERE id=:id") suspend fun issue(id: Long): ManagementIssueEntity?
    @Query("UPDATE management_issues SET status=:status, assignedUserId=:assignedUserId, assignedEmployeeId=:assignedEmployeeId, dueAtEpochMillis=:dueAt, updatedAtEpochMillis=:updatedAt WHERE id=:id") suspend fun updateIssueAssignment(id: Long, status: String, assignedUserId: Long?, assignedEmployeeId: Long?, dueAt: Long?, updatedAt: Long): Int
    @Query("UPDATE management_issues SET status=:status, resolutionNote=:note, resolvedByUserId=:actorId, resolvedAtEpochMillis=:resolvedAt, updatedAtEpochMillis=:resolvedAt WHERE id=:id") suspend fun resolveIssue(id: Long, status: String, note: String?, actorId: Long, resolvedAt: Long): Int


    @Query("UPDATE management_issues SET status=:status, updatedAtEpochMillis=:updatedAt WHERE id=:id") suspend fun updateIssueStatus(id: Long, status: String, updatedAt: Long): Int

    @Insert suspend fun insertTask(row: ManagementTaskEntity): Long
    @Query("SELECT * FROM management_tasks WHERE id=:id") suspend fun task(id: Long): ManagementTaskEntity?
    @Query("SELECT * FROM management_tasks WHERE branchId=:branchId AND status NOT IN ('COMPLETED','CANCELLED') ORDER BY dueAtEpochMillis, id") fun observeOpenTasks(branchId: Long): Flow<List<ManagementTaskEntity>>
    @Query("UPDATE management_tasks SET status=:status, startedAtEpochMillis=COALESCE(startedAtEpochMillis,:startedAt), completedAtEpochMillis=:completedAt, completedByUserId=CASE WHEN :completedBy IS NULL THEN completedByUserId ELSE :completedBy END, approvedByUserId=:approvedBy, approvedAtEpochMillis=:approvedAt WHERE id=:id") suspend fun updateTaskLifecycle(id: Long, status: String, startedAt: Long?, completedAt: Long?, completedBy: Long?, approvedBy: Long?, approvedAt: Long?): Int
    @Query("UPDATE management_tasks SET assignedUserId=:assignedUserId, assignedEmployeeId=:assignedEmployeeId, dueAtEpochMillis=:dueAt WHERE id=:id") suspend fun updateTaskAssignment(id: Long, assignedUserId: Long?, assignedEmployeeId: Long?, dueAt: Long?): Int
    @Insert suspend fun insertTaskAttachment(row: TaskAttachmentEntity): Long
    @Query("SELECT COUNT(*) FROM task_attachments WHERE taskId=:taskId") suspend fun attachmentCount(taskId: Long): Int

    @Insert suspend fun insertChecklistTemplate(row: ChecklistTemplateEntity): Long
    @Insert suspend fun insertChecklistTemplateItems(rows: List<ChecklistTemplateItemEntity>): List<Long>
    @Query("SELECT * FROM checklist_template_items WHERE templateId=:templateId ORDER BY sortOrder,id") suspend fun templateItems(templateId: Long): List<ChecklistTemplateItemEntity>
    @Insert suspend fun insertChecklistRun(row: ChecklistRunEntity): Long
    @Insert suspend fun insertChecklistRunItems(rows: List<ChecklistRunItemEntity>): List<Long>
    @Query("SELECT * FROM checklist_runs WHERE id=:runId") suspend fun checklistRun(runId: Long): ChecklistRunEntity?
    @Query("SELECT * FROM checklist_run_items WHERE id=:id") suspend fun checklistRunItem(id: Long): ChecklistRunItemEntity?
    @Query("SELECT * FROM checklist_template_items WHERE id=:id") suspend fun checklistTemplateItem(id: Long): ChecklistTemplateItemEntity?
    @Query("SELECT * FROM checklist_run_items WHERE runId=:runId ORDER BY id") suspend fun checklistRunItems(runId: Long): List<ChecklistRunItemEntity>
    @Query("SELECT COUNT(*) FROM checklist_run_items ri JOIN checklist_template_items ti ON ti.id=ri.templateItemId WHERE ri.runId=:runId AND ti.required=1 AND ri.status='FAILED'") suspend fun requiredFailedItemCount(runId: Long): Int
    @Query("UPDATE checklist_run_items SET status=:status,note=:note,attachmentReference=:attachmentReference,completedByUserId=:actorId,completedAtEpochMillis=:completedAt WHERE id=:id") suspend fun completeChecklistItem(id: Long, status: String, note: String?, attachmentReference: String?, actorId: Long, completedAt: Long): Int
    @Query("UPDATE checklist_runs SET status=:status,startedAtEpochMillis=COALESCE(startedAtEpochMillis,:now),completedAtEpochMillis=:completedAt,completedByUserId=CASE WHEN :completedBy IS NULL THEN completedByUserId ELSE :completedBy END WHERE id=:id") suspend fun updateChecklistRun(id: Long, status: String, now: Long, completedAt: Long?, completedBy: Long?): Int
    @Query("UPDATE checklist_runs SET status='COMPLETED', approvedByUserId=:actorId, approvedAtEpochMillis=:approvedAt WHERE id=:id AND status='WAITING_APPROVAL'") suspend fun approveChecklistRun(id: Long, actorId: Long, approvedAt: Long): Int
    @Query("SELECT * FROM checklist_templates WHERE active=1 AND (branchId IS NULL OR branchId=:branchId) ORDER BY type,name,id") fun observeChecklistTemplates(branchId: Long): Flow<List<ChecklistTemplateEntity>>
    @Query("SELECT * FROM checklist_runs WHERE branchId=:branchId ORDER BY businessEpochDay DESC,id DESC") fun observeChecklistRuns(branchId: Long): Flow<List<ChecklistRunEntity>>

    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertThreshold(row: ManagementRuleThresholdEntity): Long
    @Query("SELECT * FROM management_rule_thresholds WHERE branchScopeId IN (0,:branchId) ORDER BY CASE WHEN branchScopeId=:branchId THEN 0 ELSE 1 END, updatedAtEpochMillis DESC, id DESC") suspend fun thresholds(branchId: Long): List<ManagementRuleThresholdEntity>
    @Query("SELECT * FROM management_rule_thresholds WHERE branchScopeId=:branchScopeId AND `key`=:key LIMIT 1") suspend fun thresholdExact(branchScopeId: Long, key: String): ManagementRuleThresholdEntity?

    @Query("""
        SELECT COALESCE(SUM(grossSalesRial),0) grossSalesRial,
               COALESCE(SUM(discountRial),0) discountRial,
               COALESCE(SUM(returnRial),0) returnRial,
               COALESCE(SUM(netSalesRial),0) netSalesRial,
               COALESCE(SUM(serviceRial),0) serviceRevenueRial,
               COALESCE(SUM(taxRial),0) taxPayableRial,
               COALESCE(SUM(netSalesRial + serviceRial),0) revenueRial,
               COALESCE(SUM((SELECT COALESCE(SUM(jl.debitRial-jl.creditRial),0) FROM journal_lines jl WHERE jl.entryId=daily_sales_summaries.costJournalEntryId AND jl.accountCode='5101')),0) cogsRial
        FROM daily_sales_summaries
        WHERE branchId=:branchId AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND status='POSTED' AND reversedAtEpochDay IS NULL
    """) suspend fun salesAggregate(branchId:Long, fromEpochDay:Long, toEpochDay:Long): SalesAggregateRow

    @Query("""
        SELECT
          COALESCE(SUM(CASE WHEN s.type='CASH' THEN s.amountRial ELSE 0 END),0) cashRial,
          COALESCE(SUM(CASE WHEN s.type='CARD' THEN s.amountRial ELSE 0 END),0) cardRial,
          COALESCE(SUM(CASE WHEN s.type='BANK_TRANSFER' THEN s.amountRial ELSE 0 END),0) transferRial,
          COALESCE(SUM(CASE WHEN s.type='PERSONAL_CREDIT' THEN s.amountRial ELSE 0 END),0) personalCreditRial,
          COALESCE(SUM(CASE WHEN s.type='CORPORATE_CREDIT' THEN s.amountRial ELSE 0 END),0) corporateCreditRial
        FROM daily_sales_settlements s JOIN daily_sales_summaries d ON d.id=s.dailySalesId
        WHERE d.branchId=:branchId AND d.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND d.status='POSTED' AND d.reversedAtEpochDay IS NULL
    """) suspend fun settlementAggregate(branchId:Long, fromEpochDay:Long, toEpochDay:Long): SettlementAggregateRow

    @Query("""SELECT COALESCE(SUM(c.amountRial),0) FROM receivable_collections c JOIN receivables r ON r.id=c.receivableId WHERE r.branchId=:branchId AND c.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND c.reversedAtEpochMillis IS NULL""") suspend fun receivableCollectionsTotal(branchId:Long,fromEpochDay:Long,toEpochDay:Long):Long
    @Query("SELECT COALESCE(SUM(outstandingAmountRial),0) FROM receivables WHERE branchId=:branchId AND status IN ('OPEN','PARTIALLY_PAID')") suspend fun outstandingTotal(branchId:Long):Long
    @Query("SELECT COUNT(*) FROM management_issues WHERE branchId=:branchId AND severity='CRITICAL' AND status NOT IN ('RESOLVED','DISMISSED')") suspend fun criticalIssueCount(branchId:Long):Int
    @Query("SELECT COUNT(*) FROM management_issues WHERE branchId=:branchId AND status NOT IN ('RESOLVED','DISMISSED')") suspend fun openIssueCount(branchId:Long):Int
    @Query("SELECT * FROM management_issues WHERE branchId=:branchId AND status NOT IN ('RESOLVED','DISMISSED') ORDER BY CASE severity WHEN 'CRITICAL' THEN 5 WHEN 'HIGH' THEN 4 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 2 ELSE 1 END DESC, COALESCE(financialImpactRial,0) DESC, dueAtEpochMillis ASC LIMIT :limit") suspend fun importantIssues(branchId:Long, limit:Int):List<ManagementIssueEntity>
    @Query("SELECT COUNT(*) FROM management_tasks WHERE branchId=:branchId AND dueAtEpochMillis IS NOT NULL AND dueAtEpochMillis<:now AND status NOT IN ('COMPLETED','CANCELLED')") suspend fun overdueTaskCount(branchId:Long,now:Long):Int
    @Query("SELECT COUNT(*) FROM checklist_runs WHERE branchId=:branchId AND businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND status='FAILED'") suspend fun failedChecklistCount(branchId:Long,fromEpochDay:Long,toEpochDay:Long):Int
    @Query("SELECT COALESCE(SUM(-valueDeltaRial),0) FROM stock_movements WHERE movementType='WASTE' AND valueDeltaRial<0 AND movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun legacyWasteCost(fromEpochDay:Long,toEpochDay:Long):Long
    @Query("SELECT COALESCE(SUM(actualCashRial-expectedCashRial),0) FROM sales_cash_reconciliations WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun legacyCashVariance(fromEpochDay:Long,toEpochDay:Long):Long
    @Query("""
        SELECT COALESCE(SUM(l.debitRial-l.creditRial),0)
        FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId JOIN accounts a ON a.code=l.accountCode
        WHERE e.status='POSTED' AND e.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND a.type='EXPENSE' AND l.accountCode NOT IN ('5101','6101','6113','6114','6115')
    """) suspend fun organizationOperatingExpenseTotal(fromEpochDay:Long,toEpochDay:Long):Long
    @Query("""
        SELECT COALESCE(SUM(l.debitRial-l.creditRial),0)
        FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId
        WHERE e.status='POSTED' AND e.entryEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND l.accountCode IN ('6101','6113','6114','6115')
    """) suspend fun organizationPayrollExpenseTotal(fromEpochDay:Long,toEpochDay:Long):Long

    @Query("SELECT * FROM receivables WHERE branchId=:branchId AND status IN ('OPEN','PARTIALLY_PAID') AND dueEpochDay IS NOT NULL AND dueEpochDay<:todayEpochDay ORDER BY dueEpochDay") suspend fun overdueReceivables(branchId:Long,todayEpochDay:Long):List<ReceivableEntity>
    @Query("""
        SELECT
          COALESCE((SELECT SUM(d.theoreticalCostRial) FROM daily_sales_summaries d WHERE d.branchId=:branchId AND d.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND d.status='POSTED' AND d.reversedAtEpochDay IS NULL),0) theoreticalCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm JOIN daily_sales_summaries ds ON ds.id=sm.referenceId WHERE sm.referenceType='DAILY_SALES' AND sm.movementType='DAILY_SALES_CONSUMPTION' AND sm.valueDeltaRial<0 AND ds.branchId=:branchId AND ds.businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND ds.status='POSTED' AND ds.reversedAtEpochDay IS NULL),0) standardSalesLedgerCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm JOIN storage_locations sl ON sl.id=sm.locationId WHERE sm.movementType='WASTE' AND sm.valueDeltaRial<0 AND sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sl.branchId=:branchId),0) wasteCostRial,
          COALESCE((SELECT -SUM(sm.valueDeltaRial) FROM stock_movements sm JOIN storage_locations sl ON sl.id=sm.locationId WHERE sm.movementType IN ('INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.valueDeltaRial<0 AND sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sl.branchId=:branchId),0) negativeAdjustmentCostRial,
          COALESCE((SELECT SUM(sm.valueDeltaRial) FROM stock_movements sm JOIN storage_locations sl ON sl.id=sm.locationId WHERE sm.movementType IN ('INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.valueDeltaRial>0 AND sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sl.branchId=:branchId),0) positiveAdjustmentCostRial,
          COALESCE((SELECT COUNT(*) FROM stock_movements sm JOIN storage_locations sl ON sl.id=sm.locationId WHERE sm.movementType IN ('WASTE','INVENTORY_COUNT','COUNT_VARIANCE','INVENTORY_ADJUSTMENT') AND sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay AND sm.valueDeltaRial!=0 AND sl.branchId=:branchId),0) actualEvidenceCount
    """) suspend fun foodCostVariance(branchId:Long,fromEpochDay:Long,toEpochDay:Long):FoodCostVarianceRow
    @Query("SELECT id,name,stockMicros,reorderPointMicros FROM inventory_items WHERE isActive=1 AND alertEnabled=1 AND reorderPointMicros>0 AND stockMicros<=reorderPointMicros ORDER BY name") suspend fun lowStockRows():List<LowStockRow>
    @Query("SELECT COALESCE(SUM(actualCashRial-expectedCashRial),0) FROM sales_cash_reconciliations WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun cashVariance(fromEpochDay:Long,toEpochDay:Long):Long

    @Query("SELECT COALESCE(SUM(-sm.valueDeltaRial),0) FROM stock_movements sm JOIN daily_sales_summaries ds ON sm.referenceType='DAILY_SALES' AND ds.id=sm.referenceId WHERE ds.branchId=:branchId AND sm.movementType='WASTE' AND sm.valueDeltaRial<0 AND sm.movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun branchWasteCost(branchId:Long,fromEpochDay:Long,toEpochDay:Long):Long
    @Query("SELECT COALESCE(SUM(-valueDeltaRial),0) FROM stock_movements WHERE movementType='WASTE' AND valueDeltaRial<0 AND movementEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun wasteCost(fromEpochDay:Long,toEpochDay:Long):Long
    @Query("SELECT COALESCE(SUM(actualCardRial-expectedCardRial),0) FROM sales_cash_reconciliations WHERE businessEpochDay BETWEEN :fromEpochDay AND :toEpochDay") suspend fun cardVariance(fromEpochDay:Long,toEpochDay:Long):Long
    @Query("""
      SELECT pl.itemId itemId, pl.itemNameSnapshot itemName, pl.unitCostRial currentPriceRial,
             COALESCE((SELECT pl2.unitCostRial FROM purchase_lines pl2 JOIN purchases p2 ON p2.id=pl2.purchaseId WHERE pl2.itemId=pl.itemId AND p2.branchId=:branchId AND p2.purchaseEpochDay < p.purchaseEpochDay AND p2.paymentStatus!='REVERSED' ORDER BY p2.purchaseEpochDay DESC,p2.id DESC LIMIT 1),0) previousPriceRial,
             COALESCE((SELECT CAST(AVG(pl2.unitCostRial) AS INTEGER) FROM purchase_lines pl2 JOIN purchases p2 ON p2.id=pl2.purchaseId WHERE pl2.itemId=pl.itemId AND p2.branchId=:branchId AND p2.purchaseEpochDay BETWEEN p.purchaseEpochDay-30 AND p.purchaseEpochDay-1 AND p2.paymentStatus!='REVERSED'),0) average30DayRial
      FROM purchase_lines pl JOIN purchases p ON p.id=pl.purchaseId
      WHERE p.paymentStatus!='REVERSED' AND p.branchId=:branchId AND p.purchaseEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        AND p.id=(SELECT p3.id FROM purchases p3 JOIN purchase_lines pl3 ON pl3.purchaseId=p3.id WHERE pl3.itemId=pl.itemId AND p3.branchId=:branchId AND p3.paymentStatus!='REVERSED' ORDER BY p3.purchaseEpochDay DESC,p3.id DESC LIMIT 1)
    """) suspend fun purchasePriceSpikeRows(branchId:Long,fromEpochDay:Long,toEpochDay:Long):List<PurchasePriceSpikeRow>
}

data class SalesAggregateRow(
    val grossSalesRial: Long,
    val discountRial: Long,
    val returnRial: Long,
    val netSalesRial: Long,
    val serviceRevenueRial: Long,
    val taxPayableRial: Long,
    val revenueRial: Long,
    val cogsRial: Long,
)
data class SettlementAggregateRow(
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val personalCreditRial: Long,
    val corporateCreditRial: Long,
)

data class FoodCostVarianceRow(
    val theoreticalCostRial: Long,
    val standardSalesLedgerCostRial: Long,
    val wasteCostRial: Long,
    val negativeAdjustmentCostRial: Long,
    val positiveAdjustmentCostRial: Long,
    val actualEvidenceCount: Long,
)
data class LowStockRow(val id: Long, val name: String, val stockMicros: Long, val reorderPointMicros: Long)

data class PurchasePriceSpikeRow(val itemId:Long,val itemName:String,val currentPriceRial:Long,val previousPriceRial:Long,val average30DayRial:Long)
