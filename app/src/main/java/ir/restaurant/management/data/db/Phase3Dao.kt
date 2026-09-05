package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface Phase3Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScopeProfile(entity: UserScopeProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun grantBranch(entity: UserBranchScopeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun grantWarehouse(entity: UserWarehouseScopeEntity): Long

    @Query("DELETE FROM user_branch_scopes WHERE userId=:userId")
    suspend fun clearBranches(userId: Long)

    @Query("DELETE FROM user_warehouse_scopes WHERE userId=:userId")
    suspend fun clearWarehouses(userId: Long)

    @Query("SELECT * FROM user_scope_profiles WHERE userId=:userId LIMIT 1")
    suspend fun scopeProfile(userId: Long): UserScopeProfileEntity?

    @Query("SELECT branchId FROM user_branch_scopes WHERE userId=:userId ORDER BY branchId")
    suspend fun branchIds(userId: Long): List<Long>

    @Query("SELECT locationId FROM user_warehouse_scopes WHERE userId=:userId ORDER BY locationId")
    suspend fun warehouseIds(userId: Long): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM user_branch_scopes WHERE userId=:userId AND branchId=:branchId)")
    suspend fun hasBranch(userId: Long, branchId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM user_warehouse_scopes WHERE userId=:userId AND locationId=:locationId)")
    suspend fun hasWarehouse(userId: Long, locationId: Long): Boolean

    @Query("SELECT COUNT(*) FROM user_warehouse_scopes WHERE userId=:userId")
    suspend fun warehouseGrantCount(userId: Long): Int

    @Query("""
        SELECT b.* FROM branches b
        INNER JOIN user_branch_scopes s ON s.branchId=b.id
        WHERE s.userId=:userId
        ORDER BY b.isActive DESC, b.name, b.id
    """)
    fun observeBranches(userId: Long): Flow<List<BranchEntity>>

    @Query("""
        SELECT b.* FROM branches b
        INNER JOIN user_branch_scopes s ON s.branchId=b.id
        WHERE s.userId=:userId AND b.isActive=1
        ORDER BY b.name, b.id
    """)
    suspend fun listActiveBranches(userId: Long): List<BranchEntity>

    @Query("""
        SELECT l.* FROM storage_locations l
        INNER JOIN user_branch_scopes bs ON bs.branchId=l.branchId AND bs.userId=:userId
        LEFT JOIN user_warehouse_scopes ws ON ws.locationId=l.id AND ws.userId=:userId
        WHERE ws.locationId IS NOT NULL
           OR (:warehouseScoped=0 AND NOT EXISTS(SELECT 1 FROM user_warehouse_scopes granted WHERE granted.userId=:userId))
        ORDER BY l.isActive DESC, l.name, l.id
    """)
    fun observeLocations(userId: Long, warehouseScoped: Boolean): Flow<List<StorageLocationEntity>>

    @Query("""
        SELECT l.* FROM storage_locations l
        INNER JOIN user_branch_scopes bs ON bs.branchId=l.branchId AND bs.userId=:userId
        LEFT JOIN user_warehouse_scopes ws ON ws.locationId=l.id AND ws.userId=:userId
        WHERE l.isActive=1
          AND (ws.locationId IS NOT NULL
               OR (:warehouseScoped=0 AND NOT EXISTS(SELECT 1 FROM user_warehouse_scopes granted WHERE granted.userId=:userId)))
        ORDER BY l.name, l.id
    """)
    suspend fun listActiveLocations(userId: Long, warehouseScoped: Boolean): List<StorageLocationEntity>

    @Query("SELECT COUNT(*) FROM inventory_balances WHERE locationId IN (SELECT id FROM storage_locations WHERE branchId=:branchId) AND (onHandMicros<>0 OR reservedMicros<>0 OR damagedMicros<>0 OR quarantinedMicros<>0 OR inTransitMicros<>0)")
    suspend fun branchStockDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM purchase_orders WHERE branchId=:branchId AND status NOT IN ('CLOSED','CANCELLED')")
    suspend fun branchOpenPurchaseDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM supplier_payables WHERE branchId=:branchId AND status IN ('OPEN','PARTIAL') AND originalRial>settledRial")
    suspend fun branchOpenPayableDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM receivables WHERE branchId=:branchId AND status NOT IN ('PAID','VOID','CLOSED')")
    suspend fun branchOpenReceivableDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM employees WHERE branchId=:branchId AND status='ACTIVE'")
    suspend fun branchEmployeeDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM payroll_batches WHERE branchId=:branchId AND status NOT IN ('PAID','REVERSED','CANCELLED','LEGACY')")
    suspend fun branchPayrollDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM fixed_assets WHERE branchId=:branchId AND status='ACTIVE'")
    suspend fun branchAssetDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM daily_sales_summaries WHERE branchId=:branchId AND status NOT IN ('POSTED','VOID')")
    suspend fun branchOpenSalesDependencies(branchId: Long): Int

    @Query("SELECT COUNT(*) FROM purchase_orders WHERE supplierId=:supplierId AND status NOT IN ('CLOSED','CANCELLED')")
    suspend fun supplierOpenOrders(supplierId: Long): Int

    @Query("SELECT COUNT(*) FROM supplier_payables WHERE supplierId=:supplierId AND status IN ('OPEN','PARTIAL') AND originalRial>settledRial")
    suspend fun supplierOpenPayables(supplierId: Long): Int

    @Query("SELECT COUNT(*) FROM supplier_credits WHERE supplierId=:supplierId AND status IN ('OPEN','PARTIAL') AND amountRial>appliedRial")
    suspend fun supplierOpenCredits(supplierId: Long): Int

    @Query("SELECT COUNT(*) FROM purchase_orders po WHERE po.supplierId=:supplierId AND EXISTS (SELECT 1 FROM purchase_order_lines pol WHERE pol.purchaseOrderId=po.id AND pol.receivedQtyMicros<pol.orderedQtyMicros) AND po.status NOT IN ('CLOSED','CANCELLED')")
    suspend fun supplierPendingReceipts(supplierId: Long): Int

    @Insert
    suspend fun insertSupplierMerge(entity: SupplierMergeHistoryEntity): Long

    @Insert
    suspend fun insertPayable(entity: SupplierPayableEntity): Long

    @Update
    suspend fun updatePayable(entity: SupplierPayableEntity): Int

    @Query("""
        UPDATE supplier_payables
        SET settledRial=:newSettledRial, status=:newStatus, updatedAtEpochMillis=:updatedAt
        WHERE id=:payableId AND settledRial=:expectedSettledRial
    """)
    suspend fun compareAndSetPayableSettlement(
        payableId: Long,
        expectedSettledRial: Long,
        newSettledRial: Long,
        newStatus: String,
        updatedAt: Long,
    ): Int

    @Query("SELECT * FROM supplier_payables WHERE id=:id LIMIT 1")
    suspend fun payableById(id: Long): SupplierPayableEntity?

    @Query("SELECT * FROM supplier_payables WHERE sourceType=:sourceType AND sourceId=:sourceId LIMIT 1")
    suspend fun payableBySource(sourceType: String, sourceId: Long): SupplierPayableEntity?

    @Query("SELECT * FROM supplier_payables WHERE idempotencyKey=:key LIMIT 1")
    suspend fun payableByIdempotencyKey(key: String): SupplierPayableEntity?

    @Insert
    suspend fun insertPayableLedger(entity: SupplierPayableLedgerEntity): Long

    @Query("SELECT * FROM supplier_payable_ledger WHERE commandId=:commandId LIMIT 1")
    suspend fun payableLedgerByCommand(commandId: String): SupplierPayableLedgerEntity?

    @Query("SELECT COALESCE(SUM(originalRial-settledRial),0) FROM supplier_payables WHERE status IN ('OPEN','PARTIAL') AND (:branchId IS NULL OR branchId=:branchId)")
    fun observePayablesRial(branchId: Long?): Flow<Long>

    @Query("SELECT COALESCE(SUM(originalRial-settledRial),0) FROM supplier_payables WHERE status IN ('OPEN','PARTIAL') AND branchId IN (:branchIds)")
    fun observePayablesRialForBranches(branchIds: List<Long>): Flow<Long>

    @Insert
    suspend fun insertInvoiceLineMatches(rows: List<ProcurementInvoiceLineMatchEntity>)

    @Query("SELECT * FROM procurement_invoice_line_matches WHERE invoiceLinkId=:invoiceLinkId ORDER BY id")
    suspend fun invoiceLineMatches(invoiceLinkId: Long): List<ProcurementInvoiceLineMatchEntity>

    @Query("SELECT * FROM procurement_invoice_line_matches ORDER BY purchaseOrderLineId, id")
    fun observeInvoiceLineMatches(): Flow<List<ProcurementInvoiceLineMatchEntity>>

    @Query("SELECT COALESCE(SUM(invoiceQtyMicros),0) FROM procurement_invoice_line_matches WHERE purchaseOrderLineId=:purchaseOrderLineId")
    suspend fun invoicedQuantityForOrderLine(purchaseOrderLineId: Long): Long

    @Query("""
        SELECT l.purchaseId AS purchaseId,
               m.purchaseOrderLineId AS purchaseOrderLineId,
               m.invoiceQtyMicros AS invoiceQtyMicros,
               m.invoiceUnitCostRial AS invoiceUnitCostRial,
               l.matchedAtEpochMillis AS matchedAtEpochMillis
        FROM procurement_invoice_line_matches m
        INNER JOIN procurement_invoice_links l ON l.id=m.invoiceLinkId
        WHERE m.purchaseOrderLineId=:purchaseOrderLineId
        ORDER BY l.matchedAtEpochMillis, l.id, m.id
    """)
    suspend fun returnInvoiceMatches(purchaseOrderLineId: Long): List<ProcurementReturnInvoiceMatchRow>

    @Query("""
        SELECT COALESCE(SUM(quantityMicros),0)
        FROM purchase_return_lines
        WHERE purchaseOrderLineId=:purchaseOrderLineId AND supplierCreditValueRial>0
    """)
    suspend fun priorInvoicedReturnQuantity(purchaseOrderLineId: Long): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceiptLotAllocation(entity: ProcurementReceiptLotAllocationEntity): Long

    @Query("""
        SELECT a.id AS allocationId, a.lotId AS lotId, a.goodsReceiptId AS goodsReceiptId,
               a.purchaseOrderLineId AS purchaseOrderLineId, a.receivedQuantityMicros AS receivedQuantityMicros,
               a.returnedQuantityMicros AS returnedQuantityMicros, l.quantityMicros AS currentLotQuantityMicros,
               l.unitCostRial AS lotUnitCostRial, l.receivedEpochDay AS receivedEpochDay, l.status AS status
        FROM procurement_receipt_lot_allocations a
        INNER JOIN inventory_lots l ON l.id=a.lotId
        WHERE a.purchaseOrderLineId=:purchaseOrderLineId
          AND a.receivedQuantityMicros>a.returnedQuantityMicros
          AND l.quantityMicros>0
        ORDER BY l.receivedEpochDay, a.goodsReceiptId, a.id
    """)
    suspend fun returnableProcurementLots(purchaseOrderLineId: Long): List<ReturnableProcurementLotRow>

    @Query("""
        UPDATE procurement_receipt_lot_allocations
        SET returnedQuantityMicros=returnedQuantityMicros+:quantityMicros
        WHERE id=:allocationId
          AND returnedQuantityMicros=:expectedReturnedMicros
          AND returnedQuantityMicros+:quantityMicros<=receivedQuantityMicros
    """)
    suspend fun addReturnedReceiptLotQuantity(allocationId: Long, expectedReturnedMicros: Long, quantityMicros: Long): Int
}
