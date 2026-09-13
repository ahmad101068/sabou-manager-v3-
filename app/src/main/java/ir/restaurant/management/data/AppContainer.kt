package ir.restaurant.management.data

import ir.restaurant.management.application.sales.SalesHistoryUseCases
import ir.restaurant.management.application.crm.CrmUseCases
import ir.restaurant.management.application.treasury.TreasuryUseCases
import ir.restaurant.management.application.treasury.ReverseTreasuryTransactionUseCase
import ir.restaurant.management.application.assets.AssetUseCases
import ir.restaurant.management.application.recipe.RecipeUseCases
import ir.restaurant.management.application.accounting.AccountingUseCases
import ir.restaurant.management.application.procurement.ProcurementUseCases
import ir.restaurant.management.application.inventory.InventoryUseCases
import ir.restaurant.management.application.inventory.OperationsInventoryUseCases
import ir.restaurant.management.application.personnel.PersonnelUseCases
import ir.restaurant.management.application.personnel.AttendanceUseCases
import ir.restaurant.management.application.payroll.PayrollUseCases
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.OrganizationSettingsStore

import android.content.Context
import android.net.Uri
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.APP_DATABASE_SCHEMA_VERSION
import ir.restaurant.management.data.db.AccountSeedCallback
import ir.restaurant.management.data.db.clearAllTablesForFactoryReset
import ir.restaurant.management.data.repository.DashboardRepository
import ir.restaurant.management.data.repository.LocalAccountingRepository
import ir.restaurant.management.data.repository.LocalBranchRepository
import ir.restaurant.management.data.repository.LocalOperationsRepository
import ir.restaurant.management.data.repository.LocalInventoryRepository
import ir.restaurant.management.data.repository.LocalInventoryIntegrityService
import ir.restaurant.management.data.repository.LocalInventoryCommandEngine
import ir.restaurant.management.data.repository.LocalInventoryCountService
import ir.restaurant.management.data.repository.LocalInventoryLotService
import ir.restaurant.management.data.repository.LocalInventoryTransferService
import ir.restaurant.management.data.repository.LocalInventoryReplenishmentService
import ir.restaurant.management.data.repository.LocalInventoryReadService
import ir.restaurant.management.data.repository.LocalInventoryWasteService
import ir.restaurant.management.data.repository.LocalPurchaseRepository
import ir.restaurant.management.data.repository.LocalProcurementRepository
import ir.restaurant.management.data.repository.LocalPersonnelRepository
import ir.restaurant.management.data.repository.LocalHrPayrollService
import ir.restaurant.management.data.repository.LocalPerformanceRepository
import ir.restaurant.management.data.repository.LocalSyncRepository
import ir.restaurant.management.data.repository.SyncRecorder
import ir.restaurant.management.data.repository.LocalDailySalesRepository
import ir.restaurant.management.data.repository.LocalSalesHistoryRepository
import ir.restaurant.management.data.repository.LocalSecurityRepository
import ir.restaurant.management.data.repository.LocalAssetRepository
import ir.restaurant.management.data.repository.LocalRecipeRepository
import ir.restaurant.management.data.repository.LocalCustomerAccountService
import ir.restaurant.management.data.repository.LocalAlertRepository
import ir.restaurant.management.data.repository.LocalManagementControlRepository
import ir.restaurant.management.data.repository.LocalAccountingPostingEngine
import ir.restaurant.management.data.repository.LocalAuditEventWriter
import ir.restaurant.management.data.repository.AuditIntegrityVerifier
import ir.restaurant.management.data.repository.OperationalAlertWriter
import ir.restaurant.management.data.repository.LocalReceivableService
import ir.restaurant.management.data.repository.LocalManagementWorkflowService
import ir.restaurant.management.data.repository.LocalManagementWorkflowReadService
import ir.restaurant.management.data.repository.LocalDailyManagementBriefService
import ir.restaurant.management.data.repository.OverdueReceivableRule
import ir.restaurant.management.data.repository.FoodCostVarianceRule
import ir.restaurant.management.data.repository.CashVarianceRule
import ir.restaurant.management.data.repository.LowStockRule
import ir.restaurant.management.data.repository.WasteSpikeRule
import ir.restaurant.management.data.repository.PurchasePriceSpikeRule
import ir.restaurant.management.data.repository.CardSettlementVarianceRule
import ir.restaurant.management.data.repository.InventoryUsageVarianceRule
import ir.restaurant.management.data.repository.ManagementRuleEngine
import ir.restaurant.management.data.repository.ControlSettingsService
import ir.restaurant.management.data.repository.LocalCostControlReadService
import ir.restaurant.management.data.repository.LocalGlobalSearchRepository
import ir.restaurant.management.domain.control.CostControlReadService
import ir.restaurant.management.domain.search.GlobalSearchRepository
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.data.security.DatabaseKeyProvider
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.data.security.ForensicIntegrityLedger
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.StartupSessionBoundary
import ir.restaurant.management.domain.accounting.AccountingRepository
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.branch.BranchRepository
import ir.restaurant.management.domain.audit.AuditService
import ir.restaurant.management.domain.operations.OperationsRepository
import ir.restaurant.management.domain.inventory.InventoryRepository
import ir.restaurant.management.domain.inventory.InventoryIntegrityService
import ir.restaurant.management.domain.inventory.InventoryCommandService
import ir.restaurant.management.domain.inventory.InventoryCountService
import ir.restaurant.management.domain.inventory.InventoryLotService
import ir.restaurant.management.domain.inventory.InventoryTransferService
import ir.restaurant.management.domain.inventory.InventoryReplenishmentService
import ir.restaurant.management.domain.inventory.InventoryReadService
import ir.restaurant.management.domain.inventory.InventoryWasteService
import ir.restaurant.management.domain.purchase.PurchaseRepository
import ir.restaurant.management.domain.purchase.ProcurementRepository
import ir.restaurant.management.domain.personnel.PersonnelRepository
import ir.restaurant.management.domain.personnel.HrPayrollCommandService
import ir.restaurant.management.domain.sales.DailySalesRepository
import ir.restaurant.management.domain.sales.SalesHistoryRepository
import ir.restaurant.management.domain.operations.SecurityRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.recipe.RecipeRepository
import ir.restaurant.management.domain.assets.AssetRepository
import ir.restaurant.management.domain.operations.AlertRepository
import ir.restaurant.management.domain.operations.CloudSyncConfig
import ir.restaurant.management.domain.operations.SyncSafetyGate
import ir.restaurant.management.domain.control.ManagementControlRepository
import ir.restaurant.management.domain.receivables.ReceivableService
import ir.restaurant.management.domain.control.ManagementWorkflowService
import ir.restaurant.management.domain.control.ManagementWorkflowReadService
import ir.restaurant.management.domain.brief.DailyManagementBriefService
import ir.restaurant.management.domain.control.ManagementRuleContext
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.treasury.TreasuryAccountCatalog
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.crm.CustomerAccountService
import ir.restaurant.management.data.repository.HttpsSyncTransport
import ir.restaurant.management.data.repository.SyncCoordinator
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val organizationSettings = OrganizationSettingsStore(appContext)
    private val keyProvider = DatabaseKeyProvider(appContext)
    private val sensitiveActionGate = SensitiveActionGate()
    private val deviceId: String by lazy {
        val preferences = context.getSharedPreferences("sync_identity", Context.MODE_PRIVATE)
        preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also { generated ->
            check(preferences.edit().putString("device_id", generated).commit()) { "ذخیره شناسه دستگاه انجام نشد." }
        }
    }
    val backupManager = BackupManager(appContext, keyProvider) { deviceId }
    private val forensicLedger = ForensicIntegrityLedger(appContext)
    @Volatile private var startupRestoreMetadata: RestoreForensicMetadata? = null
    @Volatile private var startupRestoreResult: String? = null
    @Volatile private var startupDatabaseFailure: Throwable? = null

    private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val restoreMetadata = backupManager.restoreForensicMetadata()
        backupManager.applyPendingRestore()
        backupManager.preparePreMigrationRecovery(APP_DATABASE_SCHEMA_VERSION)
        var openedDatabase: AppDatabase? = null
        try {
            openedDatabase = openAndValidateDatabase()
            startupRestoreMetadata = restoreMetadata
            startupRestoreResult = restoreMetadata?.let { "COMPLETED" }
            backupManager.markRestoreValidated()
            backupManager.markDatabaseSchemaValidated(APP_DATABASE_SCHEMA_VERSION)
            checkNotNull(openedDatabase)
        } catch (error: Throwable) {
            startupDatabaseFailure = error
            runCatching {
                forensicLedger.append(
                    operationType = "DATABASE_INTEGRITY_FAILURE",
                    requestEpochMillis = System.currentTimeMillis(),
                    actorId = restoreMetadata?.actorId,
                    actor = restoreMetadata?.actor ?: "SYSTEM",
                    deviceId = deviceId,
                    sourceDbFingerprint = backupManager.currentDatabaseFingerprint(),
                    backupChecksum = restoreMetadata?.backupChecksum.orEmpty(),
                    schemaVersion = APP_DATABASE_SCHEMA_VERSION,
                    correlationId = restoreMetadata?.correlationId ?: "db-integrity:${System.nanoTime()}",
                    result = error.javaClass.simpleName,
                )
            }
            openedDatabase?.close()
            if (backupManager.rollbackLastRestore()) {
                val recoveredDatabase = openAndValidateDatabase()
                try {
                    startupRestoreMetadata = restoreMetadata
                    startupRestoreResult = restoreMetadata?.let { "ROLLED_BACK" } ?: "MIGRATION_ROLLBACK"
                    backupManager.markRestoreValidated()
                    backupManager.markDatabaseSchemaValidated(APP_DATABASE_SCHEMA_VERSION)
                    recoveredDatabase
                } catch (recoveryError: Throwable) {
                    recoveredDatabase.close()
                    recoveryError.addSuppressed(error)
                    throw recoveryError
                }
            } else {
                runCatching { backupManager.rollbackPreMigrationRecovery() }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private fun openAndValidateDatabase(): AppDatabase {
        val database = AppDatabase.create(appContext, keyProvider)
        try {
            val sqlite = database.openHelper.writableDatabase
            val cipherResults = sqlite.query("PRAGMA cipher_integrity_check").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            require(cipherResults.isEmpty() || cipherResults.all { it.equals("ok", ignoreCase = true) }) {
                "اعتبارسنجی پایگاه داده ناموفق بود."
            }
            StartupSessionBoundary.invalidatePersistedSession(sqlite)
            return database
        } catch (error: Throwable) {
            database.close()
            throw error
        }
    }
    internal val databaseForTesting: AppDatabase
        get() = database

    private val authorizer by lazy { SessionAuthorizer(database) }
    val authorizationService: AuthorizationService by lazy { authorizer }
    val auditService: AuditService by lazy { LocalAuditEventWriter(database) }
    val globalSearchRepository: GlobalSearchRepository by lazy { LocalGlobalSearchRepository(database, authorizer) }
    val branchRepository: BranchRepository by lazy { LocalBranchRepository(database, authorizer) }
    val accountingPostingService: AccountingPostingService by lazy {
        LocalAccountingPostingEngine(database)
    }
    val treasuryAccountCatalog: TreasuryAccountCatalog by lazy {
        DefaultTreasuryAccountCatalog()
    }
    val treasuryService: TreasuryService by lazy {
        LocalTreasuryServiceV2(
            database = database,
            accounting = accountingPostingService,
            authorizer = authorizer,
            accountCatalog = treasuryAccountCatalog,
        )
    }
    val treasuryLedgerReader: TreasuryLedgerReader by lazy { treasuryService as TreasuryLedgerReader }
    val treasuryUseCases: TreasuryUseCases by lazy { TreasuryUseCases(treasuryService, treasuryLedgerReader, treasuryAccountCatalog) }
    val reverseTreasuryTransactionUseCase: ReverseTreasuryTransactionUseCase by lazy { ReverseTreasuryTransactionUseCase(treasuryService, treasuryLedgerReader) }

    val hrPayrollService: HrPayrollCommandService by lazy {
        LocalHrPayrollService(
            database = database,
            authorizer = authorizer,
            accountingPosting = accountingPostingService,
            treasury = treasuryService,
            audit = auditService,
        )
    }
    val payrollUseCases: PayrollUseCases by lazy { PayrollUseCases(hrPayrollService) }

    /** Opens and validates encrypted Room plus both database and external audit-integrity chains. Call from IO. */
    fun initialize() {
        val external = forensicLedger.verify()
        require(external.valid) { "FORENSIC_LEDGER_INTEGRITY_FAILURE:${external.failure}" }
        database.openHelper.writableDatabase
        runBlocking {
            val integrity = AuditIntegrityVerifier(database).verify()
            require(integrity.valid) { "AUDIT_CHAIN_INTEGRITY_FAILURE:${integrity.failure}" }
            val previousAnchor = forensicLedger.latestAuditAnchorHash()
            val rollbackDetected = !previousAnchor.isNullOrBlank() && database.auditLogDao().countByEventHash(previousAnchor) == 0
            val restore = startupRestoreMetadata
            val alerts = OperationalAlertWriter(database)
            if (rollbackDetected) {
                forensicLedger.append(
                    operationType = if (restore != null) "RESTORE_AUDIT_ROLLBACK_DETECTED" else "AUDIT_ROLLBACK_DETECTED",
                    requestEpochMillis = restore?.requestEpochMillis ?: System.currentTimeMillis(),
                    actorId = restore?.actorId,
                    actor = restore?.actor ?: "SYSTEM",
                    deviceId = deviceId,
                    sourceDbFingerprint = restore?.sourceDbFingerprint.orEmpty(),
                    destinationDbFingerprint = backupManager.currentDatabaseFingerprint(),
                    backupChecksum = restore?.backupChecksum.orEmpty(),
                    auditTerminalHash = integrity.terminalHash,
                    schemaVersion = APP_DATABASE_SCHEMA_VERSION,
                    correlationId = restore?.correlationId ?: "audit-rollback:${System.nanoTime()}",
                    result = "DETECTED",
                )
                alerts.append(
                    sourceType = LocalAlertRepository.RESTORE_ANOMALY,
                    sourceId = System.currentTimeMillis(),
                    title = "ناهنجاری زنجیره حسابرسی",
                    message = "زنجیره حسابرسی جاری با آخرین لنگر مستقل تطابق ندارد.",
                )
                require(restore != null) { "UNAUTHORIZED_AUDIT_ROLLBACK_DETECTED" }
            }
            if (startupDatabaseFailure != null) {
                alerts.append(
                    sourceType = LocalAlertRepository.DATABASE_INTEGRITY_FAILURE,
                    sourceId = System.currentTimeMillis(),
                    title = "خطای یکپارچگی پایگاه داده",
                    message = "اعتبارسنجی پایگاه داده ناموفق شد و مسیر بازیابی ایمن اجرا شد.",
                )
            }
            if (restore != null) {
                val outcome = startupRestoreResult ?: "UNKNOWN"
                forensicLedger.append(
                    operationType = if (outcome == "ROLLED_BACK") "RESTORE_ROLLBACK" else "RESTORE_COMPLETED",
                    requestEpochMillis = restore.requestEpochMillis,
                    actorId = restore.actorId,
                    actor = restore.actor,
                    deviceId = deviceId,
                    sourceDbFingerprint = restore.sourceDbFingerprint,
                    destinationDbFingerprint = backupManager.currentDatabaseFingerprint(),
                    backupChecksum = restore.backupChecksum,
                    auditTerminalHash = integrity.terminalHash,
                    schemaVersion = APP_DATABASE_SCHEMA_VERSION,
                    correlationId = restore.correlationId,
                    result = outcome,
                )
                if (outcome == "ROLLED_BACK") {
                    alerts.append(
                        sourceType = LocalAlertRepository.RESTORE_ANOMALY,
                        sourceId = restore.requestEpochMillis,
                        title = "بازیابی پایگاه داده بازگردانده شد",
                        message = "اعتبارسنجی نسخه بازیابی‌شده ناموفق بود و پایگاه داده قبلی به‌صورت ایمن بازگردانده شد.",
                    )
                }
            }
            forensicLedger.append(
                operationType = "AUDIT_ANCHOR",
                requestEpochMillis = System.currentTimeMillis(),
                actorId = restore?.actorId,
                actor = restore?.actor ?: "SYSTEM",
                deviceId = deviceId,
                destinationDbFingerprint = backupManager.currentDatabaseFingerprint(),
                auditTerminalHash = integrity.terminalHash,
                schemaVersion = APP_DATABASE_SCHEMA_VERSION,
                correlationId = "audit-anchor:${System.nanoTime()}",
                result = "VERIFIED",
            )
        }
    }

    val purchaseRepository: PurchaseRepository by lazy {
        LocalPurchaseRepository(
            database = database, syncRecorder = syncRecorder, authorizer = authorizer,
            treasury = treasuryService, treasuryReader = treasuryLedgerReader,
        )
    }

    val procurementRepository: ProcurementRepository by lazy {
        LocalProcurementRepository(
            database,
            authorizer,
            syncRecorder = syncRecorder,
            treasury = treasuryService,
            inventoryReplenishment = inventoryReplenishmentService,
        )
    }
    val procurementUseCases: ProcurementUseCases by lazy { ProcurementUseCases(procurementRepository, purchaseRepository) }

    val receivableService: ReceivableService by lazy {
        LocalReceivableService(
            database = database,
            authorizer = authorizer,
            treasury = treasuryService,
            treasuryReader = treasuryLedgerReader,
        )
    }

    val dailySalesRepository: DailySalesRepository by lazy {
        LocalDailySalesRepository(
            database,
            authorizer,
            syncRecorder = syncRecorder,
            sensitiveActionGate = sensitiveActionGate,
            treasury = treasuryService,
            receivables = receivableService,
            treasuryReader = treasuryLedgerReader,
        )
    }
    val managementWorkflowService: ManagementWorkflowService by lazy { LocalManagementWorkflowService(database, authorizer) }
    val managementWorkflowReadService: ManagementWorkflowReadService by lazy { LocalManagementWorkflowReadService(database, authorizer) }
    val dailyManagementBriefService: DailyManagementBriefService by lazy { LocalDailyManagementBriefService(database, authorizer) }
    val controlSettingsService: ControlSettingsService by lazy { ControlSettingsService(database, authorizer) }
    val costControlReadService: CostControlReadService by lazy { LocalCostControlReadService(database, authorizer) }
    val managementRuleEngine: ManagementRuleEngine by lazy {
        ManagementRuleEngine(
            managementWorkflowService,
            listOf(OverdueReceivableRule(database), FoodCostVarianceRule(database), WasteSpikeRule(database), PurchasePriceSpikeRule(database), CashVarianceRule(database), CardSettlementVarianceRule(database), InventoryUsageVarianceRule(database), LowStockRule(database)),
        )
    }
    suspend fun refreshManagementRules(branchId: Long, fromEpochDay: Long, toEpochDay: Long): Int =
        managementRuleEngine.refresh(ManagementRuleContext(branchId, fromEpochDay, toEpochDay))

    val salesHistoryRepository: SalesHistoryRepository by lazy {
        LocalSalesHistoryRepository(
            database = database,
            authorizer = authorizer,
            syncRecorder = syncRecorder,
        )
    }

    val salesHistoryUseCases: SalesHistoryUseCases by lazy {
        SalesHistoryUseCases(salesHistoryRepository)
    }

    val customerAccountService: CustomerAccountService by lazy { LocalCustomerAccountService(database, authorizer) }
    val crmUseCases: CrmUseCases by lazy { CrmUseCases(customerAccountService) }

    val recipeRepository: RecipeRepository by lazy { LocalRecipeRepository(database, syncRecorder = syncRecorder, authorizer = authorizer) }
    val recipeUseCases: RecipeUseCases by lazy { RecipeUseCases(recipeRepository) }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(database)
    }

    val inventoryRepository: InventoryRepository by lazy {
        LocalInventoryRepository(
            database = database,
            authorizer = authorizer,
            syncRecorder = syncRecorder,
        )
    }

    val inventoryIntegrityService: InventoryIntegrityService by lazy {
        LocalInventoryIntegrityService(database, authorizer)
    }

    val inventoryCommandService: InventoryCommandService by lazy {
        LocalInventoryCommandEngine(database, authorizer = authorizer)
    }

    val inventoryCountService: InventoryCountService by lazy {
        LocalInventoryCountService(database, authorizer, syncRecorder = syncRecorder)
    }

    val inventoryLotService: InventoryLotService by lazy {
        LocalInventoryLotService(database, authorizer, syncRecorder = syncRecorder)
    }

    val inventoryWasteService: InventoryWasteService by lazy {
        LocalInventoryWasteService(
            database = database,
            authorizer = authorizer,
            accounting = accountingPostingService,
            syncRecorder = syncRecorder,
        )
    }

    val inventoryTransferService: InventoryTransferService by lazy {
        LocalInventoryTransferService(
            database = database,
            authorizer = authorizer,
            syncRecorder = syncRecorder,
        )
    }

    val inventoryReplenishmentService: InventoryReplenishmentService by lazy {
        LocalInventoryReplenishmentService(database, authorizer)
    }

    val inventoryReadService: InventoryReadService by lazy {
        LocalInventoryReadService(database, authorizer)
    }
    val inventoryUseCases: InventoryUseCases by lazy {
        InventoryUseCases(
            master = inventoryRepository,
            commands = inventoryCommandService,
            counts = inventoryCountService,
            lots = inventoryLotService,
            waste = inventoryWasteService,
            transfers = inventoryTransferService,
            reads = inventoryReadService,
            replenishment = inventoryReplenishmentService,
            integrity = inventoryIntegrityService,
        )
    }

    val operationsRepository: OperationsRepository by lazy {
        LocalOperationsRepository(
            database,
            syncRecorder = syncRecorder,
            authorizer = authorizer,
            sensitiveActionGate = sensitiveActionGate,
            inventoryRepository = inventoryRepository,
            inventoryWasteService = inventoryWasteService,
        )
    }

    val operationsInventoryUseCases: OperationsInventoryUseCases by lazy {
        OperationsInventoryUseCases(operationsRepository, securityRepository)
    }

    val securityRepository: SecurityRepository by lazy {
        LocalSecurityRepository(
            database,
            authorizer = authorizer,
            sensitiveActionGate = sensitiveActionGate,
            deviceIdProvider = { deviceId },
        )
    }

    val personnelRepository: PersonnelRepository by lazy {
        LocalPersonnelRepository(database, syncRecorder = syncRecorder, authorizer = authorizer, treasury = treasuryService)
    }
    val personnelUseCases: PersonnelUseCases by lazy { PersonnelUseCases(personnelRepository) }
    val attendanceUseCases: AttendanceUseCases by lazy { AttendanceUseCases(personnelRepository) }
    val performanceRepository by lazy { LocalPerformanceRepository(database, authorizer = authorizer, syncRecorder = syncRecorder) }
    val syncRepository by lazy { LocalSyncRepository(database) }
    private val syncPreferences by lazy { appContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE) }
    fun syncConfig(): CloudSyncConfig = CloudSyncConfig(
        endpoint = syncPreferences.getString("endpoint", "").orEmpty(),
        organizationId = syncPreferences.getString("organization_id", null)
            ?: syncPreferences.getString("tenant", "").orEmpty(),
        enabled = syncPreferences.getBoolean("enabled", false) && SyncSafetyGate.isProductionReady,
        accessToken = syncPreferences.getString("access_token_protected", null)?.let(keyProvider::unprotectSecret)
            ?: syncPreferences.getString("access_token", "").orEmpty(),
        refreshToken = syncPreferences.getString("refresh_token_protected", null)?.let(keyProvider::unprotectSecret)
            ?: syncPreferences.getString("refresh_token", "").orEmpty(),
        accessTokenExpiresAtEpochMillis = syncPreferences.getLong("access_expires_at", 0),
        deviceId = deviceId,
    )
    private fun persistSyncConfig(config: CloudSyncConfig) {
        check(
            syncPreferences.edit()
                .putString("endpoint", config.endpoint.trim())
                .putString("organization_id", config.organizationId.trim())
                .remove("tenant")
                .putBoolean("enabled", config.enabled)
                .putString("access_token_protected", keyProvider.protectSecret(config.accessToken))
                .putString("refresh_token_protected", keyProvider.protectSecret(config.refreshToken))
                .remove("access_token")
                .remove("refresh_token")
                .putLong("access_expires_at", config.accessTokenExpiresAtEpochMillis)
                .commit(),
        )
    }
    suspend fun saveSyncConfig(config: CloudSyncConfig) { authorizer.require(Permission.BACKUP); if(config.enabled) SyncSafetyGate.requireProductionReady(); val normalized=config.copy(deviceId=deviceId,enabled=false); persistSyncConfig(normalized) }
    suspend fun runSync(): ir.restaurant.management.data.repository.SyncRunResult { SyncSafetyGate.requireProductionReady(); var config=syncConfig(); if(config.enabled && config.accessTokenExpired){config=ir.restaurant.management.data.repository.SyncTokenRefresher().refresh(config);persistSyncConfig(config)}; return SyncCoordinator(syncRepository,HttpsSyncTransport(config)).runOnce() }
    suspend fun runSyncAuthorized() = authorizer.require(Permission.BACKUP).let { runSync() }
    suspend fun resolveSyncIssueAuthorized(changeId:String,keepLocal:Boolean){authorizer.require(Permission.BACKUP);syncRepository.resolveIssue(changeId,keepLocal)}
    val syncRecorder by lazy { SyncRecorder(database, deviceId) }
    val assetRepository: AssetRepository by lazy {
        LocalAssetRepository(database, syncRecorder = syncRecorder, authorizer = authorizer, treasury = treasuryService)
    }
    val assetUseCases: AssetUseCases by lazy { AssetUseCases(assetRepository) }
    val alertRepository: AlertRepository by lazy { LocalAlertRepository(database, authorizer) }
    val managementControlRepository: ManagementControlRepository by lazy {
        LocalManagementControlRepository(
            database,
            procurementRepository,
            authorizer,
            syncRecorder = syncRecorder,
            sensitiveActionGate = sensitiveActionGate,
            inventoryRepository = inventoryRepository,
            inventoryTransferService = inventoryTransferService,
        )
    }

    suspend fun createBackup(): String {
        authorizer.require(Permission.BACKUP)
        return withContext(Dispatchers.IO) {
            try {
                backupManager.create(database).also { backupManager.prune(BackupPolicyStore(appContext).load().maxFiles) }
            } catch (error: Throwable) {
                OperationalAlertWriter(database).append(
                    sourceType = LocalAlertRepository.BACKUP_FAILURE,
                    sourceId = System.currentTimeMillis(),
                    title = "ساخت پشتیبان ناموفق بود",
                    message = "ساخت یا اعتبارسنجی نسخه پشتیبان با خطا مواجه شد.",
                )
                throw error
            }
        }
    }
    internal suspend fun createAutomaticBackup(maxFiles: Int): String = withContext(Dispatchers.IO) {
        try {
            backupManager.create(database).also { backupManager.prune(maxFiles) }
        } catch (error: Throwable) {
            OperationalAlertWriter(database).append(
                sourceType = LocalAlertRepository.BACKUP_FAILURE,
                sourceId = System.currentTimeMillis(),
                title = "پشتیبان‌گیری خودکار ناموفق بود",
                message = "پشتیبان‌گیری خودکار یا اعتبارسنجی آن با خطا مواجه شد.",
            )
            throw error
        }
    }
    suspend fun factoryReset() {
        val actor = authorizer.require(Permission.MANAGE_USERS)
        sensitiveActionGate.requireAndConsume(actor.id, SensitiveAction.FACTORY_RESET, SensitiveActionContext.resource("DATABASE", "FACTORY_RESET"))
        withContext(Dispatchers.IO) {
            val requestAt = System.currentTimeMillis()
            val correlation = "factory-reset:${actor.id}:$requestAt"
            val audit = AuditIntegrityVerifier(database).verify()
            require(audit.valid) { "AUDIT_CHAIN_INTEGRITY_FAILURE:${audit.failure}" }
            val sourceFingerprint = backupManager.currentDatabaseFingerprint()
            forensicLedger.append(
                operationType = "FACTORY_RESET_REQUESTED", requestEpochMillis = requestAt, actorId = actor.id, actor = actor.displayName,
                deviceId = deviceId, sourceDbFingerprint = sourceFingerprint, auditTerminalHash = audit.terminalHash,
                schemaVersion = APP_DATABASE_SCHEMA_VERSION, correlationId = correlation, result = "REQUESTED",
            )
            database.clearAllTablesForFactoryReset()
            AccountSeedCallback.seedMissingAccounts(database.openHelper.writableDatabase)
            AccountSeedCallback.seedSystemLocations(database.openHelper.writableDatabase)
            backupManager.clearAll()
            listOf("cloud_sync", "sync_identity", "automatic_backup_policy").forEach { name ->
                check(appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) {
                    "پاک‌سازی تنظیمات $name انجام نشد."
                }
            }
            forensicLedger.append(
                operationType = "FACTORY_RESET_COMPLETED", requestEpochMillis = requestAt, actorId = actor.id, actor = actor.displayName,
                deviceId = deviceId, sourceDbFingerprint = sourceFingerprint, destinationDbFingerprint = backupManager.currentDatabaseFingerprint(),
                auditTerminalHash = audit.terminalHash, schemaVersion = APP_DATABASE_SCHEMA_VERSION, correlationId = correlation, result = "COMPLETED",
            )
        }
    }
    suspend fun listBackups(): List<String> {
        authorizer.require(Permission.BACKUP)
        return withContext(Dispatchers.IO) { backupManager.list() }
    }
    suspend fun describeBackups(): List<BackupDescriptor> {
        authorizer.require(Permission.BACKUP)
        return withContext(Dispatchers.IO) {
            val visibleNames = backupManager.list().toSet()
            backupManager.describe().filter { it.name in visibleNames }
        }
    }
    suspend fun deleteBackup(name: String) {
        authorizer.require(Permission.BACKUP)
        withContext(Dispatchers.IO) { backupManager.delete(name) }
    }
    suspend fun scheduleRestore(name: String) {
        val actor = authorizer.require(Permission.RESTORE)
        sensitiveActionGate.requireAndConsume(actor.id, SensitiveAction.RESTORE_BACKUP, SensitiveActionContext.resource("BACKUP", name))
        withContext(Dispatchers.IO) {
            require(backupManager.verify(name)) { "فایل پشتیبان معتبر نیست یا تغییر کرده است." }
            val requestAt = System.currentTimeMillis()
            val correlation = "restore:${actor.id}:$requestAt:${name.hashCode()}"
            val audit = AuditIntegrityVerifier(database).verify()
            require(audit.valid) { "AUDIT_CHAIN_INTEGRITY_FAILURE:${audit.failure}" }
            val metadata = RestoreForensicMetadata(
                requestEpochMillis = requestAt, actorId = actor.id, actor = actor.displayName, correlationId = correlation,
                sourceDbFingerprint = backupManager.currentDatabaseFingerprint(), backupChecksum = backupManager.backupFingerprint(name),
            )
            forensicLedger.append(
                operationType = "RESTORE_REQUESTED", requestEpochMillis = requestAt, actorId = actor.id, actor = actor.displayName,
                deviceId = deviceId, sourceDbFingerprint = metadata.sourceDbFingerprint, backupChecksum = metadata.backupChecksum,
                auditTerminalHash = audit.terminalHash, schemaVersion = APP_DATABASE_SCHEMA_VERSION, correlationId = correlation, result = "REQUESTED",
            )
            val recoveryName = backupManager.create(database)
            try {
                backupManager.scheduleRestore(name, recoveryName, metadata)
            } catch (error: Throwable) {
                forensicLedger.append(
                    operationType = "RESTORE_VALIDATION_FAILED", requestEpochMillis = requestAt, actorId = actor.id, actor = actor.displayName,
                    deviceId = deviceId, sourceDbFingerprint = metadata.sourceDbFingerprint, backupChecksum = metadata.backupChecksum,
                    auditTerminalHash = audit.terminalHash, schemaVersion = APP_DATABASE_SCHEMA_VERSION, correlationId = correlation,
                    result = error.javaClass.simpleName,
                )
                runCatching { backupManager.delete(recoveryName) }
                throw error
            }
        }
    }
    suspend fun exportBackup(name: String, password: CharArray, destination: Uri) {
        authorizer.require(Permission.BACKUP)
        withContext(Dispatchers.IO) {
            val output = requireNotNull(appContext.contentResolver.openOutputStream(destination, "w")) {
                "امکان نوشتن فایل مقصد وجود ندارد."
            }
            output.use { backupManager.exportPortable(name, password, it) }
        }
    }
    suspend fun importBackup(source: Uri, password: CharArray): String {
        authorizer.require(Permission.BACKUP)
        return withContext(Dispatchers.IO) {
            val input = requireNotNull(appContext.contentResolver.openInputStream(source)) {
                "امکان خواندن فایل انتخاب‌شده وجود ندارد."
            }
            backupManager.importPortable(input, password).also { backupManager.prune(BackupPolicyStore(appContext).load().maxFiles) }
        }
    }

    val accountingRepository: AccountingRepository by lazy {
        LocalAccountingRepository(database, syncRecorder = syncRecorder, authorizer = authorizer)
    }
    val accountingUseCases: AccountingUseCases by lazy { AccountingUseCases(accountingRepository) }
}
