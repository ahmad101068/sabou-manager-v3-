package ir.restaurant.management.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.restaurant.management.data.security.DatabaseKeyProvider
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

internal const val APP_DATABASE_SCHEMA_VERSION = 60

@Database(
    entities = [
        BranchEntity::class,
        BranchLegacyAliasEntity::class,
        UserScopeProfileEntity::class,
        UserBranchScopeEntity::class,
        UserWarehouseScopeEntity::class,
        AccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        SupplierEntity::class,
        SupplierMergeHistoryEntity::class,
        SupplierPayableEntity::class,
        SupplierPayableLedgerEntity::class,
        InventoryItemEntity::class,
        InventoryBalanceEntity::class,
        PurchaseEntity::class,
        PurchaseLineEntity::class,
        StockMovementEntity::class,
        EmployeeEntity::class,
        AttendanceEntity::class,
        LeaveEntity::class,
        PayrollRunEntity::class,
        PayrollPolicyEntity::class,
        PayrollAdvanceAllocationEntity::class,
        EmployeeContractEntity::class,
        EmployeeAdvanceEntity::class,
        EmployeePrivateProfileEntity::class,
        HrDocumentEntity::class,
        EmploymentAssignmentEntity::class,
        EmploymentContractVersionEntity::class,
        AttendanceEventEntity::class,
        AttendanceCorrectionEntity::class,
        OvertimeApprovalEntity::class,
        LeaveLedgerEntryEntity::class,
        PayrollPeriodEntity::class,
        PayrollBatchEntity::class,
        PayrollPayslipEntity::class,
        PayrollSnapshotEntity::class,
        PayrollComponentEntity::class,
        PayrollManualAdjustmentEntity::class,
        PayrollApprovalEventEntity::class,
        PayrollPaymentEntity::class,
        PayrollAdvanceAllocationV2Entity::class,
        PayrollExceptionEntity::class,
        HrPayrollMigrationAnomalyEntity::class,
        HrPayrollCommandReceiptEntity::class,
        PerformanceGoalEntity::class,
        PerformanceReviewEntity::class,
        PerformanceScoreEntity::class,
        MenuItemEntity::class,
        RecipeIngredientEntity::class,
        RecipeVersionEntity::class,
        RecipeVersionIngredientEntity::class,
        InventoryCountEntity::class,
        InventoryCountSessionEntity::class,
        InventoryCountLineEntity::class,
        InventoryPeriodClosureEntity::class,
        InventoryPeriodClosureLineEntity::class,
        AuditLogEntity::class,
        AppUserEntity::class,
        AppSessionEntity::class,
        FixedAssetEntity::class,
        AssetDepreciationEntity::class,
        AppAlertEntity::class,
        SyncChangeEntity::class,
        ShiftTemplateEntity::class,
        WorkScheduleEntity::class,
        WorkScheduleDayEntity::class,
        PlannedShiftEntity::class,
        PurchaseRequisitionEntity::class,
        PurchaseRequisitionLineEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderLineEntity::class,
        GoodsReceiptEntity::class,
        GoodsReceiptLineEntity::class,
        ProcurementInvoiceLinkEntity::class,
        ProcurementInvoiceLineMatchEntity::class,
        ProcurementReceiptLotAllocationEntity::class,
        PurchaseReturnEntity::class,
        PurchaseReturnLineEntity::class,
        SupplierCreditEntity::class,
        InventoryReplenishmentPolicyEntity::class,
        SupplierItemOfferEntity::class,
        PurchaseOrderFollowUpEntity::class,
        StorageLocationEntity::class,
        InventoryLotEntity::class,
        StockTransferEntity::class,
        StockTransferLineEntity::class,
        OperatingBudgetEntity::class,
        BudgetSpendEntryEntity::class,
        BudgetCommitmentEntity::class,
        AccountingPeriodLockEntity::class,
        SalesCashReconciliationEntity::class,
        LaborPolicyEntity::class,
        WorkBreakEntity::class,
        EmployeeAvailabilityEntity::class,
        ShiftSwapRequestEntity::class,
        InventoryLotConsumptionEntity::class,
        DailySalesSummaryEntity::class,
        DailySalesMenuLineEntity::class,
        SalesDayClosureEntity::class,
        InventoryWasteDocumentEntity::class,
        DocumentSequenceEntity::class,
        CustomerEntity::class,
        SalesInvoiceEntity::class,
        SalesInvoiceLineEntity::class,
        SalesPaymentEntity::class,
        SalesConsumptionSnapshotEntity::class,
        SalesReturnEntity::class,
        SalesReturnLineEntity::class,
        InvoiceSalesDayClosureEntity::class,
        TreasuryTransactionEntity::class,
        TreasuryLedgerEntryEntity::class,
        TreasuryReconciliationEntity::class,
        RecipeComponentEntity::class,
        RecipeSubstitutionEntity::class,
        AssetLifecycleEventEntity::class,
        AssetMaintenanceEntity::class,
        CustomerReceivableLedgerEntity::class,
        CustomerMergeHistoryEntity::class,
        DailySalesSettlementEntity::class,
        ReceivableEntity::class,
        ReceivableCollectionEntity::class,
        ManagementIssueEntity::class,
        ManagementTaskEntity::class,
        TaskAttachmentEntity::class,
        ChecklistTemplateEntity::class,
        ChecklistTemplateItemEntity::class,
        ChecklistRunEntity::class,
        ChecklistRunItemEntity::class,
        ManagementRuleThresholdEntity::class,
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun branchDao(): BranchDao
    abstract fun phase3Dao(): Phase3Dao
    abstract fun supplierDao(): SupplierDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun inventoryLocationDao(): InventoryLocationDao
    abstract fun inventoryBalanceDao(): InventoryBalanceDao
    abstract fun inventoryLotDao(): InventoryLotDao
    abstract fun inventoryCountDao(): InventoryCountDao
    abstract fun inventoryTransferDao(): InventoryTransferDao
    abstract fun inventoryReplenishmentDao(): InventoryReplenishmentDao
    abstract fun inventoryReadDao(): InventoryReadDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun accountingDao(): AccountingDao
    abstract fun personnelDao(): PersonnelDao
    abstract fun hrPayrollDao(): HrPayrollDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun recipeDao(): RecipeDao
    abstract fun inventoryControlDao(): InventoryControlDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun securityDao(): SecurityDao
    abstract fun assetDao(): AssetDao
    abstract fun alertDao(): AlertDao
    abstract fun syncDao(): SyncDao
    abstract fun procurementDao(): ProcurementDao
    abstract fun managementControlDao(): ManagementControlDao
    abstract fun dailySalesDao(): DailySalesDao
    abstract fun documentSequenceDao(): DocumentSequenceDao
    abstract fun salesDao(): SalesDao
    abstract fun treasuryDao(): TreasuryDao
    abstract fun recipeLifecycleDao(): RecipeLifecycleDao
    abstract fun assetLifecycleDao(): AssetLifecycleDao
    abstract fun customerReceivableDao(): CustomerReceivableDao
    abstract fun dashboardAnalyticsDao(): DashboardAnalyticsDao
    abstract fun businessOperationsDao(): BusinessOperationsDao

    companion object {
        fun create(context: Context, keyProvider: DatabaseKeyProvider): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(keyProvider.getOrCreatePassphrase(), null, true)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "restaurant_management.db",
            )
                .openHelperFactory(factory)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(AccountSeedCallback)
                .build()
        }

        internal fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .addCallback(AccountSeedCallback)
                .build()

        /** Opens a copied backup with the production migrations and Room schema validator. */
        internal fun validateBackupCopy(context: Context, file: File, passphrase: ByteArray) {
            val expectedParent = context.getDatabasePath(file.name).parentFile?.canonicalFile
            require(file.parentFile?.canonicalFile == expectedParent) {
                "فایل اعتبارسنجی باید در پوشه پایگاه داده برنامه باشد."
            }
            System.loadLibrary("sqlcipher")
            val validationPassphrase = passphrase.copyOf()
            val database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                file.name,
            )
                .openHelperFactory(SupportOpenHelperFactory(validationPassphrase, null, true))
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(AccountSeedCallback)
                .build()
            try {
                val sqlite = database.openHelper.writableDatabase
                val cipherResults = sqlite.query("PRAGMA cipher_integrity_check").use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                require(cipherResults.isEmpty() || cipherResults.all { it.equals("ok", ignoreCase = true) }) {
                    "اعتبارسنجی رمزنگاری نسخه موقت ناموفق بود."
                }
                val integrityResults = sqlite.query("PRAGMA integrity_check").use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                require(integrityResults.size == 1 && integrityResults.single().equals("ok", ignoreCase = true)) {
                    "اعتبارسنجی SQLite نسخه موقت ناموفق بود."
                }
                DatabaseHealthValidator.validateForeignKeys(sqlite)
                DatabaseHealthValidator.validateStartup(sqlite)
                val schemaVersion = sqlite.query("PRAGMA user_version").use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
                require(schemaVersion == APP_DATABASE_SCHEMA_VERSION) {
                    "مهاجرت نسخه موقت تا ساختار جاری کامل نشد."
                }
            } finally {
                database.close()
                validationPassphrase.fill(0)
            }
        }
    }
}
