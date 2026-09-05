package ir.restaurant.management.data.repository

import ir.restaurant.management.core.BusinessCalendar

import ir.restaurant.management.domain.security.Permission

import androidx.room.withTransaction
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryCountEntity
import ir.restaurant.management.data.db.InventoryPeriodClosureEntity
import ir.restaurant.management.data.db.InventoryPeriodClosureLineEntity
import ir.restaurant.management.data.db.StockMovementEntity
import ir.restaurant.management.data.db.SupplierEntity
import ir.restaurant.management.data.db.SupplierMergeHistoryEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryCountRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodReopenDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodStatus
import ir.restaurant.management.domain.operations.InventoryPeriodClosureDetails
import ir.restaurant.management.domain.operations.InventoryPeriodClosureLineRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCalculator
import ir.restaurant.management.domain.operations.AuditLogRecord
import ir.restaurant.management.domain.operations.AuditLogQuery
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.StockMovementRecord
import ir.restaurant.management.domain.operations.InventoryUsageInsight
import ir.restaurant.management.domain.operations.OperationsRepository
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.operations.PurchaseSummary
import ir.restaurant.management.domain.operations.PurchaseDashboardSummary
import ir.restaurant.management.domain.operations.SupplierPriceInsight
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierMergeDraft
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.operations.SupplierPartyType
import ir.restaurant.management.domain.operations.WasteDraft
import ir.restaurant.management.domain.operations.WasteRecord
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventorySku
import ir.restaurant.management.domain.inventory.InventoryItemType
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.inventory.InventoryStorageCondition
import ir.restaurant.management.domain.inventory.InventoryItemMasterDraft
import ir.restaurant.management.domain.inventory.InventoryRepository
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.InventoryWasteService
import ir.restaurant.management.domain.inventory.WasteReason
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.common.asViolation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import ir.restaurant.management.domain.security.UserRole

class LocalOperationsRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
    private val sensitiveActionGate: SensitiveActionGate = SensitiveActionGate(),
    private val inventoryRepository: InventoryRepository = LocalInventoryRepository(
        database = database,
        authorizer = authorizer,
        clock = clock,
        syncRecorder = syncRecorder,
    ),
    private val inventoryWasteService: InventoryWasteService = LocalInventoryWasteService(
        database = database,
        authorizer = authorizer,
        clock = clock,
        syncRecorder = syncRecorder,
    ),
) : OperationsRepository {
    private val auditWriter = LocalAuditEventWriter(database)
    private val documentNumbers = LocalDocumentNumberAllocator(database, clock)
    private val dataScope = LocalDataScopeService(database, authorizer)
    override val recentStockMovements: Flow<List<StockMovementRecord>> = combine(
        database.stockMovementDao().observeRecent(),
        dataScope.scopedLocations(),
    ) { rows, locations ->
        val allowedLocationIds = locations.asSequence().filter { it.isActive }.map { it.id }.toSet()
        rows.filter { it.locationId != null && it.locationId in allowedLocationIds }.map { it.toRecord() }
    }

    override fun stockMovements(itemId: Long): Flow<List<StockMovementRecord>> {
        require(itemId > 0) { "کالای انتخاب‌شده معتبر نیست." }
        return combine(
            database.stockMovementDao().observeForItem(itemId),
            dataScope.scopedLocations(),
        ) { rows, locations ->
            val allowedLocationIds = locations.asSequence().filter { it.isActive }.map { it.id }.toSet()
            rows.filter { it.locationId != null && it.locationId in allowedLocationIds }.map { it.toRecord() }
        }
    }

    override val suppliers: Flow<List<SupplierRecord>> =
        database.supplierDao().observeActive().map { rows -> rows.map(SupplierEntity::toRecord) }

    override val inventoryLocations: Flow<List<InventoryLocationRecord>> = inventoryRepository.locations

    private val scopedInventoryItems: Flow<List<InventoryItemRecord>> = combine(
        database.inventoryDao().observeActive(),
        database.inventoryBalanceDao().observeAll(),
        database.inventoryLotDao().observeActiveStock(),
        dataScope.scopedLocations(),
    ) { items, balances, lots, locations ->
        val allowedLocationIds = locations.asSequence().filter { it.isActive }.map { it.id }.toSet()
        val balancesByItem = balances.asSequence()
            .filter { it.locationId in allowedLocationIds }
            .groupBy { it.itemId }
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toEpochDay()
        val expiredByItemAndLocation = lots.asSequence()
            .filter { row ->
                row.locationId in allowedLocationIds && row.quantityMicros > 0 && row.status == "ACTIVE" &&
                    row.expiryEpochDay?.let { it < today } == true
            }
            .groupBy { it.itemId to it.locationId }
            .mapValues { (_, rows) -> rows.fold(0L) { sum, row -> SignedLongMath.add(sum, row.quantityMicros) } }
        items.map { item ->
            val itemBalances = balancesByItem[item.id].orEmpty()
            val available = itemBalances.fold(0L) { sum, balance ->
                val expired = expiredByItemAndLocation[item.id to balance.locationId] ?: 0L
                val usable = balance.onHandMicros - balance.reservedMicros - balance.damagedMicros - balance.quarantinedMicros - expired
                SignedLongMath.add(sum, usable.coerceAtLeast(0L))
            }
            val scopedValue = itemBalances.fold(0L) { sum, balance -> SignedLongMath.add(sum, balance.inventoryValueRial) }
            item.toRecord().copy(stockMicros = available, inventoryValueRial = scopedValue)
        }
    }

    override val inventoryItems: Flow<List<InventoryItemRecord>> = scopedInventoryItems

    override val lowStockItems: Flow<List<InventoryItemRecord>> = scopedInventoryItems.map { rows ->
        rows.filter { it.alertEnabled && it.stockMicros <= it.alertThresholdMicros }
            .sortedWith(compareBy<InventoryItemRecord> { it.stockMicros }.thenBy { it.name })
    }

    override val inventoryCounts: Flow<List<InventoryCountRecord>> = combine(
        database.inventoryControlDao().observeRecentCounts(),
        dataScope.scopedLocations(),
    ) { rows, locations ->
        val allowedLocationIds = locations.asSequence().filter { it.isActive }.map { it.id }.toSet()
        rows.filter { it.locationId != null && it.locationId in allowedLocationIds }
            .map { InventoryCountRecord(it.id, it.itemId, it.previousQuantityMicros, it.countedQuantityMicros, it.previousValueRial, it.countedValueRial, it.countEpochDay, it.reason) }
    }

    override val inventoryPeriodClosures: Flow<List<InventoryPeriodClosureRecord>> =
        database.inventoryControlDao().observeClosures().map { rows ->
            rows.map { it.toRecord() }
        }

    override fun inventoryPeriodClosureDetails(closureId: Long): Flow<InventoryPeriodClosureDetails?> = combine(
        database.inventoryControlDao().observeClosure(closureId),
        database.inventoryControlDao().observeClosureLines(closureId),
    ) { closure, lines ->
        closure?.let {
            InventoryPeriodClosureDetails(
                closure = it.toRecord(),
                lines = lines.map { line ->
                    InventoryPeriodClosureLineRecord(
                        line.itemId, line.itemNameSnapshot, line.unitSnapshot,
                        line.openingQuantityMicros, line.openingValueRial,
                        line.netPurchaseQuantityMicros, line.netPurchaseValueRial,
                        line.recordedOutflowQuantityMicros, line.recordedOutflowValueRial,
                        line.adjustmentQuantityMicros, line.adjustmentValueRial,
                        line.expectedClosingQuantityMicros, line.expectedClosingValueRial,
                        line.countedClosingQuantityMicros, line.countedClosingValueRial,
                    )
                },
            )
        }
    }

    override fun auditLogs(query: AuditLogQuery): Flow<List<AuditLogRecord>> = flow {
        val actor = authorizer.require(Permission.AUDIT_VIEW)
        val canViewSensitive = authorizer.can(Permission.AUDIT_SENSITIVE_VIEW)
        val allowedBranches = if (actor.role == UserRole.OWNER) null else dataScope.activeBranches().map { it.id }.toSet()
        val zone = java.time.ZoneId.systemDefault()
        val fromMillis = query.fromEpochDay?.let { java.time.LocalDate.ofEpochDay(it).atStartOfDay(zone).toInstant().toEpochMilli() }
        val toExclusiveMillis = query.toEpochDay?.let { java.time.LocalDate.ofEpochDay(it + 1L).atStartOfDay(zone).toInstant().toEpochMilli() }
        emitAll(database.auditLogDao().observeFiltered(
            search = query.search.trim(),
            actor = query.actor.trim(),
            action = query.action.trim().uppercase(),
            entityType = query.entityType.trim().uppercase(),
            entityId = query.entityId,
            sourceReference = query.sourceReference.trim(),
            severity = query.severity.trim().uppercase(),
            fromMillis = fromMillis,
            toExclusiveMillis = toExclusiveMillis,
        ).map { rows ->
            rows.asSequence()
                .filter { row -> allowedBranches == null || row.actorBranchIdSnapshot == null || row.actorBranchIdSnapshot in allowedBranches }
                .map { row ->
                    val sensitive = row.entityType.startsWith("SECURITY") || row.entityType == "SENSITIVE_ACTION" ||
                        row.action.contains("RECOVERY") || row.action.contains("LOGIN")
                    AuditLogRecord(
                        id = row.id, action = row.action, entityType = row.entityType, entityId = row.entityId,
                        description = row.description, actor = row.actor, createdAtEpochMillis = row.createdAtEpochMillis,
                        actorId = row.actorId, referenceType = row.referenceType, referenceId = row.referenceId,
                        reason = if (sensitive && !canViewSensitive) "جزئیات حساس برای این نقش پنهان است" else row.reason,
                        beforeSnapshot = if (sensitive && !canViewSensitive) null else row.beforeSnapshot,
                        afterSnapshot = if (sensitive && !canViewSensitive) null else row.afterSnapshot,
                        correlationId = row.correlationId, actorRoleSnapshot = row.actorRoleSnapshot,
                        actorBranchIdSnapshot = row.actorBranchIdSnapshot,
                    )
                }.toList()
        })
    }

    override val usageInsights: Flow<List<InventoryUsageInsight>> =
        database.stockMovementDao().observeUsageSince(BusinessCalendar.epochDayAt(clock()) - 29L).map { rows ->
            rows.map { row ->
                InventoryUsageInsight(
                    itemId = row.itemId,
                    itemName = row.itemName,
                    unit = row.unit,
                    usageMicros30Days = row.usageMicros,
                    averageDailyUsageMicros = row.usageMicros / 30L,
                )
            }
        }

    override val supplierPriceInsights: Flow<List<SupplierPriceInsight>> =
        database.purchaseDao().observeSupplierPriceInsights().map { rows ->
            rows.map { row ->
                SupplierPriceInsight(
                    itemId = row.itemId,
                    itemName = row.itemName,
                    supplierName = row.supplierName,
                    latestUnitCostRial = row.latestUnitCostRial,
                    previousUnitCostRial = row.previousUnitCostRial,
                )
            }
        }

    override val wasteRecords: Flow<List<WasteRecord>> =
        database.stockMovementDao().observeWasteRecords().map { rows ->
            rows.map { row ->
                WasteRecord(
                    id = row.id,
                    itemId = row.itemId,
                    itemName = row.itemName,
                    unit = row.unit,
                    quantityMicros = row.quantityMicros,
                    valueRial = row.valueRial,
                    wasteEpochDay = row.wasteEpochDay,
                    reason = row.reason,
                )
            }
        }

    override fun purchases(query: String): Flow<List<PurchaseSummary>> =
        dataScope.scopedBranches().flatMapLatest { branches ->
            val branchIds = branches.map { it.id }
            if (branchIds.isEmpty()) flowOf(emptyList())
            else database.purchaseDao().observeSearchForBranches(query.trim(), branchIds).map { rows ->
                rows.map { row ->
                    PurchaseSummary(
                        id = row.purchaseId,
                        invoiceNo = row.invoiceNo,
                        supplierName = row.supplierName,
                        purchaseEpochDay = row.purchaseEpochDay,
                        dueEpochDay = row.dueEpochDay,
                        totalRial = row.totalRial,
                        paidRial = row.paidRial,
                        paymentStatus = PurchasePaymentStatus.fromStoredValue(row.paymentStatus),
                        paymentMethod = PurchasePaymentMethod.fromStored(row.paymentMethod),
                        reminderEnabled = row.reminderEnabled,
                        reminderEpochDay = row.reminderEpochDay,
                    )
                }
            }
        }

    override fun purchaseDashboardSummary(fromEpochDay: Long, toEpochDay: Long, todayEpochDay: Long): Flow<PurchaseDashboardSummary> {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay) { "بازه داشبورد خرید معتبر نیست." }
        return dataScope.scopedBranches().flatMapLatest { branches ->
            val branchIds = branches.map { it.id }
            if (branchIds.isEmpty()) flowOf(PurchaseDashboardSummary())
            else combine(
                database.purchaseDao().observeDashboardSummaryForBranches(fromEpochDay, toEpochDay, todayEpochDay, branchIds),
                database.phase3Dao().observePayablesRialForBranches(branchIds),
            ) { row, canonicalPayablesRial ->
                PurchaseDashboardSummary(
                    periodPurchaseRial = row.periodPurchaseRial,
                    openOrderCount = row.openOrderCount,
                    activeSupplierCount = row.activeSupplierCount,
                    supplierPayablesRial = canonicalPayablesRial,
                    pendingReceiptCount = row.pendingReceiptCount,
                    openRequisitionCount = row.openRequisitionCount,
                    pendingApprovalCount = row.pendingApprovalCount,
                    overdueOrderCount = row.overdueOrderCount,
                )
            }
        }
    }

    override suspend fun createSupplier(draft: SupplierDraft): Long {
        val actor = authorizer.require(Permission.SUPPLIERS)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            ensureSupplierDuplicatePolicy(null, valid)
            val normalizedName = supplierNameKey(valid.name)
            val previous = database.supplierDao().byNormalizedName(normalizedName)
            if (previous != null) {
                require(!previous.isActive) { "تأمین‌کننده‌ای با این مشخصات وجود دارد." }
                val after = previous.copy(
                    name = valid.name,
                    normalizedName = normalizedName,
                    partyType = valid.partyType.name,
                    legalId = valid.legalId,
                    economicCode = valid.economicCode,
                    bankIban = valid.bankIban,
                    contactName = valid.contactName,
                    phone = valid.phone,
                    address = valid.address,
                    paymentTermsDays = valid.paymentTermsDays,
                    notes = valid.notes,
                    isActive = true,
                    updatedAtEpochMillis = now,
                )
                check(database.supplierDao().update(after) == 1) { "فعال‌سازی دوباره تأمین‌کننده انجام نشد." }
                syncRecorder?.record("SUPPLIER", previous.id, "UPSERT", now)
                auditWriter.appendAuthorized(
                    authorizer, "REACTIVATE", "SUPPLIER", previous.id, "فعال‌سازی مجدد تأمین‌کننده ${valid.name}", now,
                    reason = "فعال‌سازی مجدد master تأمین‌کننده",
                    beforeSnapshot = "active=false;code=${previous.code}",
                    afterSnapshot = "active=true;code=${previous.code};legalId=${valid.legalId.orEmpty()}",
                    correlationId = "supplier:${previous.id}:reactivate:$now",
                )
                return@withTransaction previous.id
            }
            val code = documentNumbers.next(DocumentNumberType.SUPPLIER)
            val id = database.supplierDao().insert(
                SupplierEntity(
                    code = code,
                    name = valid.name,
                    normalizedName = normalizedName,
                    partyType = valid.partyType.name,
                    legalId = valid.legalId,
                    economicCode = valid.economicCode,
                    bankIban = valid.bankIban,
                    contactName = valid.contactName,
                    phone = valid.phone,
                    address = valid.address,
                    paymentTermsDays = valid.paymentTermsDays,
                    notes = valid.notes,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            syncRecorder?.record("SUPPLIER", id, "CREATE", now)
            auditWriter.appendAuthorized(
                authorizer, "CREATE", "SUPPLIER", id, "ایجاد تأمین‌کننده ${valid.name}", now,
                reason = "ایجاد master تأمین‌کننده",
                afterSnapshot = "code=$code;partyType=${valid.partyType.name};legalId=${valid.legalId.orEmpty()}",
                correlationId = "supplier:$id:create",
            )
            id
        }
    }

    override suspend fun updateSupplier(id: Long, draft: SupplierDraft) {
        authorizer.require(Permission.SUPPLIERS)
        val valid = draft.validated()
        val now = clock()
        database.withTransaction {
            val current = database.supplierDao().activeById(id) ?: error("تأمین‌کننده پیدا نشد.")
            ensureSupplierDuplicatePolicy(id, valid)
            val after = current.copy(
                name = valid.name,
                normalizedName = supplierNameKey(valid.name),
                partyType = valid.partyType.name,
                legalId = valid.legalId,
                economicCode = valid.economicCode,
                bankIban = valid.bankIban,
                contactName = valid.contactName,
                phone = valid.phone,
                address = valid.address,
                paymentTermsDays = valid.paymentTermsDays,
                notes = valid.notes,
                updatedAtEpochMillis = now,
            )
            check(database.supplierDao().update(after) == 1) { "ویرایش تأمین‌کننده انجام نشد." }
            syncRecorder?.record("SUPPLIER", id, "UPDATE", now)
            auditWriter.appendAuthorized(
                authorizer, "UPDATE", "SUPPLIER", id, "ویرایش تأمین‌کننده ${valid.name}", now,
                reason = "ویرایش master تأمین‌کننده",
                beforeSnapshot = "name=${current.name};legalId=${current.legalId.orEmpty()};iban=${current.bankIban.orEmpty()}",
                afterSnapshot = "name=${after.name};legalId=${after.legalId.orEmpty()};iban=${after.bankIban.orEmpty()}",
                correlationId = "supplier:$id:update:$now",
            )
        }
    }

    override suspend fun deactivateSupplier(id: Long) {
        authorizer.require(Permission.SUPPLIERS)
        database.withTransaction {
            val current = database.supplierDao().activeById(id) ?: error("تأمین‌کننده پیدا نشد.")
            val phase3 = database.phase3Dao()
            val blockers = buildList {
                if (phase3.supplierOpenOrders(id) > 0) add("سفارش خرید باز")
                if (phase3.supplierOpenPayables(id) > 0) add("حساب پرداختنی باز")
                if (phase3.supplierOpenCredits(id) > 0) add("اعتبار تأمین‌کننده باز")
                if (phase3.supplierPendingReceipts(id) > 0) add("دریافت کالای معلق")
            }
            require(blockers.isEmpty()) { "غیرفعال‌سازی تأمین‌کننده ممکن نیست: ${blockers.joinToString("، ")}" }
            val now = clock()
            check(database.supplierDao().deactivate(id, now) == 1) { "غیرفعال‌سازی تأمین‌کننده انجام نشد." }
            database.inventoryDao().clearSupplierReference(id, now)
            syncRecorder?.record("SUPPLIER", id, "DEACTIVATE", now)
            auditWriter.appendAuthorized(
                authorizer, "DEACTIVATE", "SUPPLIER", id, "غیرفعال‌سازی تأمین‌کننده ${current.name}", now,
                reason = "غیرفعال‌سازی پس از کنترل وابستگی‌ها",
                beforeSnapshot = "active=true;code=${current.code}",
                afterSnapshot = "active=false;code=${current.code}",
                correlationId = "supplier:$id:deactivate:$now",
            )
        }
    }

    override suspend fun mergeSupplier(draft: SupplierMergeDraft) {
        val actor = authorizer.require(Permission.SUPPLIERS)
        val valid = draft.validated()
        val now = clock()
        database.withTransaction {
            val source = database.supplierDao().activeById(valid.sourceSupplierId) ?: error("تأمین‌کننده مبدأ پیدا نشد.")
            val target = database.supplierDao().activeById(valid.targetSupplierId) ?: error("تأمین‌کننده مقصد پیدا نشد.")
            val phase3 = database.phase3Dao()
            val blockers = buildList {
                if (phase3.supplierOpenOrders(source.id) > 0) add("سفارش خرید باز")
                if (phase3.supplierOpenPayables(source.id) > 0) add("حساب پرداختنی باز")
                if (phase3.supplierOpenCredits(source.id) > 0) add("اعتبار باز")
                if (phase3.supplierPendingReceipts(source.id) > 0) add("دریافت معلق")
            }
            require(blockers.isEmpty()) {
                "برای حفظ تاریخچه مالی، تأمین‌کننده مبدأ تا بستن وابستگی‌های باز قابل ادغام نیست: ${blockers.joinToString("، ")}"
            }
            // Posted procurement/AP history is deliberately NOT rewritten. The merge is a master-data
            // alias decision for future operations and remains reconstructable through this immutable row.
            phase3.insertSupplierMerge(
                SupplierMergeHistoryEntity(
                    sourceSupplierId = source.id,
                    targetSupplierId = target.id,
                    mergedByActorId = actor.id,
                    reason = valid.reason,
                    createdAtEpochMillis = now,
                ),
            )
            check(database.supplierDao().deactivate(source.id, now) == 1) { "غیرفعال‌سازی تأمین‌کننده مبدأ انجام نشد." }
            database.inventoryDao().clearSupplierReference(source.id, now)
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "MERGE",
                entityType = "SUPPLIER",
                entityId = source.id,
                description = "ادغام کنترل‌شده ${source.name} در ${target.name}",
                occurredAtEpochMillis = now,
                reason = valid.reason,
                beforeSnapshot = "source=${source.id};target=${target.id};sourceActive=true",
                afterSnapshot = "source=${source.id};target=${target.id};sourceActive=false;postedHistoryRewritten=false",
                correlationId = "supplier_merge:${source.id}:${target.id}:$now",
                referenceType = "SUPPLIER",
                referenceId = target.id,
            )
            syncRecorder?.record("SUPPLIER", source.id, "MERGE", now, recordAudit = false)
        }
    }

    private suspend fun ensureSupplierDuplicatePolicy(currentId: Long?, draft: SupplierDraft) {
        val dao = database.supplierDao()
        fun conflict(row: SupplierEntity?): Boolean = row != null && row.id != currentId
        require(!conflict(dao.byNormalizedName(supplierNameKey(draft.name)))) { "نام تأمین‌کننده تکراری است." }
        draft.legalId?.let { require(!conflict(dao.byLegalId(it))) { "شناسه ملی/حقوقی قبلاً برای تأمین‌کننده دیگری ثبت شده است." } }
        draft.bankIban?.let { require(!conflict(dao.byBankIban(it))) { "شماره شبا قبلاً برای تأمین‌کننده دیگری ثبت شده است." } }
        draft.phone.takeIf { it.isNotBlank() }?.let { require(!conflict(dao.byPhone(it))) { "شماره تماس قبلاً برای تأمین‌کننده دیگری ثبت شده است." } }
    }

    override suspend fun createInventoryItem(draft: InventoryItemDraft): Long {
        val valid = draft.validated()
        val stableSku = database.inventoryDao().byName(valid.name)?.sku ?: InventorySku.generated().value
        return inventoryRepository.saveItem(null, valid.toMasterDraft(stableSku))
    }

    override suspend fun updateInventoryItem(id: Long, draft: InventoryItemDraft) {
        val current = database.inventoryDao().activeById(id)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", id).asViolation()
        val valid = draft.validated()
        inventoryRepository.saveItem(id, valid.toMasterDraft(current.sku))
    }

    override suspend fun deactivateInventoryItem(id: Long) = inventoryRepository.deactivateItem(id)

    override suspend fun postInventoryCount(draft: InventoryCountDraft): Long {
        val actor = authorizer.require(Permission.INVENTORY_ADJUST)
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(actor.id, SensitiveAction.ADJUST_INVENTORY, SensitiveActionContext.resource("INVENTORY_COUNT", "${valid.locationId}:${valid.itemId}:${valid.countEpochDay}", commandFingerprint = valid.commandId))
        return database.withTransaction {
            val idempotencyKey = "inventory_count:${valid.commandId}"
            val correlationId = idempotencyKey
            database.inventoryControlDao().countByIdempotencyKey(idempotencyKey)?.let { existing ->
                val samePayload = existing.itemId == valid.itemId &&
                    existing.countedQuantityMicros == valid.countedQuantityMicros &&
                    existing.countedValueRial == valid.countedValueRial &&
                    existing.countEpochDay == valid.countEpochDay &&
                    existing.reason == valid.reason &&
                    existing.actorId == actor.id
                if (!samePayload) throw BusinessError.IdempotencyConflict(idempotencyKey).asViolation()
                return@withTransaction existing.id
            }
            val current = database.inventoryDao().activeById(valid.itemId) ?: error("کالا پیدا نشد.")
            val now = clock()
            val locationId = valid.locationId
            LocalDataScopeService(database, authorizer).requireLocation(locationId)
            val countId = database.inventoryControlDao().insertCount(
                InventoryCountEntity(
                    itemId = current.id, previousQuantityMicros = database.inventoryBalanceDao().byKey(current.id, locationId)?.onHandMicros ?: 0L,
                    countedQuantityMicros = valid.countedQuantityMicros, previousValueRial = database.inventoryBalanceDao().byKey(current.id, locationId)?.inventoryValueRial ?: 0L,
                    countedValueRial = valid.countedValueRial, countEpochDay = valid.countEpochDay,
                    reason = valid.reason, createdAtEpochMillis = now,
                    globalId = valid.commandId,
                    idempotencyKey = idempotencyKey,
                    correlationId = correlationId,
                    actorId = actor.id,
                    deviceId = "local-android",
                    locationId = locationId,
                ),
            )
            if (
                current.stockMicros != valid.countedQuantityMicros ||
                current.inventoryValueRial != valid.countedValueRial
            ) {
                LocalInventoryCommandEngine(database, clock = { now }, authorizer = authorizer).adjustToCount(
                    itemId = current.id,
                    countedQuantityMicros = valid.countedQuantityMicros,
                    countedValueRial = valid.countedValueRial,
                    referenceId = countId,
                    movementEpochDay = valid.countEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.INVENTORY_COUNT,
                        referenceId = countId,
                        suffix = "adjust:${current.id}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.PHYSICAL_COUNT,
                        reason = valid.reason,
                        correlationId = correlationId,
                        locationId = locationId,
                    ),
                    notes = valid.reason,
                )
            }
            audit(
                action = "INVENTORY_COUNT",
                entityType = "INVENTORY_ITEM",
                entityId = current.id,
                description = "انبارگردانی ${current.name}: ${current.stockMicros} → ${valid.countedQuantityMicros}",
                now = now,
                businessEpochDay = valid.countEpochDay,
                reason = valid.reason,
                beforeSnapshot = "quantityMicros=${current.stockMicros};valueRial=${current.inventoryValueRial}",
                afterSnapshot = "quantityMicros=${valid.countedQuantityMicros};valueRial=${valid.countedValueRial}",
                correlationId = correlationId,
            )
            countId
        }
    }

    override suspend fun closeInventoryPeriod(draft: InventoryPeriodCloseDraft): Long {
        authorizer.require(Permission.INVENTORY)
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.CLOSE_INVENTORY_PERIOD, SensitiveActionContext.resource("INVENTORY_PERIOD", "${valid.fromEpochDay}:${valid.toEpochDay}"))
        return database.withTransaction {
            val control = database.inventoryControlDao()
            val previousClosure = control.closureByRange(valid.fromEpochDay, valid.toEpochDay)
            require(previousClosure?.status != "CLOSED") { "این دوره قبلاً بسته شده است." }
            require(!control.closureOverlaps(valid.fromEpochDay, valid.toEpochDay)) { "این بازه با دوره بسته‌شده دیگری هم‌پوشانی دارد." }
            control.lastClosedEpochDay()?.let { lastDay ->
                require(valid.fromEpochDay == lastDay + 1L) { "دوره جدید باید از روز بعد آخرین دوره بسته‌شده شروع شود." }
            }
            val items = database.inventoryDao().activeItems()
            require(items.isNotEmpty()) { "برای بستن دوره، کالای فعال وجود ندارد." }
            val latestCounts = control.countsOnDay(valid.toEpochDay)
                .groupBy { it.itemId }
                .mapValues { (_, rows) -> rows.maxBy { it.createdAtEpochMillis } }
            val missing = items.filter { latestCounts[it.id] == null }.map { it.name }
            require(missing.isEmpty()) { "ابتدا در تاریخ پایان دوره برای همه کالاها انبارگردانی ثبت کنید: ${missing.take(6).joinToString("، ")}" }
            val stale = items.filter { item ->
                val count = latestCounts.getValue(item.id)
                count.countedQuantityMicros != item.stockMicros || count.countedValueRial != item.inventoryValueRial
            }.map { it.name }
            require(stale.isEmpty()) { "پس از آخرین شمارش این کالاها گردش جدید ثبت شده؛ دوباره شمارش کنید: ${stale.take(6).joinToString("، ")}" }
            val totalsByItem = control.movementTotals(valid.fromEpochDay, valid.toEpochDay).associateBy { it.itemId }
            val calculations = items.associateWith { item ->
                val totals = totalsByItem[item.id]
                InventoryPeriodCalculator.calculate(
                    countedClosingQuantityMicros = item.stockMicros,
                    countedClosingValueRial = item.inventoryValueRial,
                    netMovementQuantityMicros = totals?.netQuantityMicros ?: 0,
                    netMovementValueRial = totals?.netValueRial ?: 0,
                    netPurchaseQuantityMicros = totals?.netPurchaseQuantityMicros ?: 0,
                    netPurchaseValueRial = totals?.netPurchaseValueRial ?: 0,
                    countAdjustmentQuantityMicros = totals?.countAdjustmentQuantityMicros ?: 0,
                    countAdjustmentValueRial = totals?.countAdjustmentValueRial ?: 0,
                )
            }
            fun total(selector: (ir.restaurant.management.domain.operations.InventoryPeriodLineCalculation) -> Long): Long =
                calculations.values.fold(0L) { sum, line -> SignedLongMath.add(sum, selector(line)) }
            val openingValue = total { it.openingValueRial }
            val purchases = total { it.netPurchaseValueRial }
            val outflow = total { it.recordedOutflowValueRial }
            val expected = total { it.expectedClosingValueRial }
            val counted = total { it.countedClosingValueRial }
            val variance = SignedLongMath.subtract(counted, expected)
            val now = clock()
            val refreshed = InventoryPeriodClosureEntity(
                id = previousClosure?.id ?: 0,
                fromEpochDay = valid.fromEpochDay, toEpochDay = valid.toEpochDay,
                openingValueRial = openingValue, netPurchaseValueRial = purchases,
                recordedOutflowValueRial = outflow, expectedClosingValueRial = expected,
                countedClosingValueRial = counted, varianceValueRial = variance,
                itemCount = items.size, status = "CLOSED", revisionNo = (previousClosure?.revisionNo ?: 0) + 1,
                closedBy = authorizer.actor(), note = valid.note,
                createdAtEpochMillis = now,
            )
            val closureId = if (previousClosure == null) {
                control.insertClosure(refreshed)
            } else {
                check(control.updateClosure(refreshed) == 1) { "بستن مجدد دوره انجام نشد." }
                control.deleteClosureLines(previousClosure.id)
                previousClosure.id
            }
            control.insertClosureLines(calculations.map { (item, line) ->
                InventoryPeriodClosureLineEntity(
                    closureId = closureId, itemId = item.id, itemNameSnapshot = item.name, unitSnapshot = item.unit,
                    openingQuantityMicros = line.openingQuantityMicros, openingValueRial = line.openingValueRial,
                    netPurchaseQuantityMicros = line.netPurchaseQuantityMicros, netPurchaseValueRial = line.netPurchaseValueRial,
                    recordedOutflowQuantityMicros = line.recordedOutflowQuantityMicros, recordedOutflowValueRial = line.recordedOutflowValueRial,
                    adjustmentQuantityMicros = line.adjustmentQuantityMicros, adjustmentValueRial = line.adjustmentValueRial,
                    expectedClosingQuantityMicros = line.expectedClosingQuantityMicros, expectedClosingValueRial = line.expectedClosingValueRial,
                    countedClosingQuantityMicros = line.countedClosingQuantityMicros, countedClosingValueRial = line.countedClosingValueRial,
                )
            })
            audit(
                action = "CLOSE",
                entityType = "INVENTORY_PERIOD",
                entityId = closureId,
                description = "بستن انبار ${valid.fromEpochDay} تا ${valid.toEpochDay}؛ مغایرت ارزش $variance ریال",
                now = now,
                businessEpochDay = valid.toEpochDay,
                reason = valid.note.ifBlank { "پایان دوره و تأیید شمارش‌ها" },
                afterSnapshot = "from=${valid.fromEpochDay};to=${valid.toEpochDay};varianceValueRial=$variance;status=CLOSED",
            )
            syncRecorder?.record("INVENTORY_PERIOD", closureId, "CLOSE", now)
            closureId
        }
    }

    override suspend fun reopenInventoryPeriod(draft: InventoryPeriodReopenDraft) {
        authorizer.requireOwner()
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.REOPEN_INVENTORY_PERIOD, SensitiveActionContext.resource("INVENTORY_PERIOD", valid.closureId))
        database.withTransaction {
            val control = database.inventoryControlDao()
            val closure = control.closureById(valid.closureId) ?: error("دوره انبار پیدا نشد.")
            require(InventoryPeriodStatus.fromStoredValue(closure.status) == InventoryPeriodStatus.CLOSED) { "این دوره در وضعیت بسته نیست." }
            require(control.latestClosedClosure()?.id == closure.id) { "فقط آخرین دوره بسته‌شده قابل بازگشایی است." }
            val actor = authorizer.actor()
            val now = clock()
            check(control.updateClosure(closure.copy(status = "REOPENED", reopenedBy = actor, reopenReason = valid.reason, reopenedAtEpochMillis = now)) == 1) {
                "بازگشایی دوره انبار انجام نشد."
            }
            audit(
                action = "REOPEN",
                entityType = "INVENTORY_PERIOD",
                entityId = closure.id,
                description = "بازگشایی کنترل‌شده انبار ${closure.fromEpochDay} تا ${closure.toEpochDay}: ${valid.reason}",
                now = now,
                businessEpochDay = closure.toEpochDay,
                reason = valid.reason,
                beforeSnapshot = "status=CLOSED",
                afterSnapshot = "status=REOPENED;reason=${valid.reason}",
            )
            syncRecorder?.record("INVENTORY_PERIOD", closure.id, "REOPEN", now)
        }
    }

    override suspend fun postWaste(draft: WasteDraft): Long {
        val actor = authorizer.require(Permission.INVENTORY_WASTE_CREATE)
        val valid = draft.validated()
        val locationId = database.inventoryLocationDao().defaultLocationId()
            ?: throw BusinessError.EntityNotFound("DEFAULT_STORAGE_LOCATION", null).asViolation()
        return inventoryWasteService.submitAndPost(
            CreateWasteCommand(
                itemId = valid.itemId,
                locationId = locationId,
                quantityMicros = valid.quantityMicros,
                reason = WasteReason.fromStoredInput(valid.reason),
                businessEpochDay = valid.wasteEpochDay,
                reasonDetail = valid.reason,
                notes = valid.notes,
                actorId = actor.id,
                commandId = valid.commandId,
                correlationId = "inventory_waste:${valid.commandId}",
            ),
        ).id
    }

    private fun InventoryPeriodClosureEntity.toRecord() = InventoryPeriodClosureRecord(
        id, fromEpochDay, toEpochDay, openingValueRial, netPurchaseValueRial,
        recordedOutflowValueRial, expectedClosingValueRial, countedClosingValueRial,
        varianceValueRial, itemCount, InventoryPeriodStatus.fromStoredValue(status), revisionNo, closedBy, note, reopenedBy, reopenReason,
    )

    private suspend fun log(action: String, entityType: String, entityId: Long?, description: String) =
        audit(action, entityType, entityId, description, clock())

    private suspend fun audit(
        action: String,
        entityType: String,
        entityId: Long?,
        description: String,
        now: Long,
        businessEpochDay: Long? = null,
        reason: String = description,
        beforeSnapshot: String? = null,
        afterSnapshot: String? = null,
        correlationId: String = "audit:$entityType:${entityId ?: 0}:$action:$now",
    ) {
        auditWriter.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = entityType,
            entityId = entityId,
            description = description,
            occurredAtEpochMillis = now,
            businessEpochDay = businessEpochDay,
            reason = reason,
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = afterSnapshot,
            correlationId = correlationId,
        )
    }

}

private fun StockMovementEntity.toRecord() = StockMovementRecord(
    id = id,
    itemId = itemId,
    movementType = InventoryMovementType.fromStoredValue(movementType),
    quantityDeltaMicros = quantityDeltaMicros,
    valueDeltaRial = valueDeltaRial,
    referenceType = InventoryReferenceType.fromStoredValue(referenceType),
    referenceId = referenceId,
    movementEpochDay = movementEpochDay,
    notes = notes,
)

private fun SupplierEntity.toRecord() = SupplierRecord(
    id = id,
    code = code,
    name = name,
    partyType = runCatching { SupplierPartyType.valueOf(partyType) }.getOrDefault(SupplierPartyType.COMPANY),
    legalId = legalId,
    economicCode = economicCode,
    bankIban = bankIban,
    contactName = contactName,
    phone = phone,
    address = address,
    paymentTermsDays = paymentTermsDays,
    notes = notes,
    isActive = isActive,
)

private fun supplierNameKey(value: String): String = value
    .trim()
    .lowercase()
    .replace('ي', 'ی')
    .replace('ك', 'ک')
    .replace(Regex("\\s+"), " ")

private fun InventoryItemDraft.toMasterDraft(fallbackSku: String) = InventoryItemMasterDraft(
    sku = sku.ifBlank { fallbackSku },
    name = name,
    category = category,
    itemType = itemType,
    baseUnit = unit,
    purchaseUnit = purchaseUnit,
    purchaseToBaseNumerator = purchaseToStockNumerator,
    purchaseToBaseDenominator = purchaseToStockDenominator,
    recipeUnit = recipeUnit,
    recipeToBaseNumerator = recipeToStockNumerator,
    recipeToBaseDenominator = recipeToStockDenominator,
    primaryBarcode = primaryBarcode,
    brand = brand,
    storageCondition = storageCondition,
    shelfLifeDays = shelfLifeDays,
    trackLot = trackLot,
    trackExpiry = trackExpiry,
    minimumStockMicros = minimumStockMicros.takeIf { it > 0 } ?: alertThresholdMicros,
    maximumStockMicros = maximumStockMicros,
    safetyStockMicros = safetyStockMicros,
    reorderPointMicros = reorderPointMicros.takeIf { it > 0 } ?: alertThresholdMicros,
    preferredSupplierId = supplierId,
    leadTimeDays = leadTimeDays,
)

private fun InventoryItemEntity.toRecord() = InventoryItemRecord(
    id = id,
    name = name,
    category = category,
    unit = unit,
    purchaseUnit = purchaseUnit,
    purchaseToStockNumerator = purchaseToStockNumerator,
    purchaseToStockDenominator = purchaseToStockDenominator,
    recipeUnit = recipeUnit,
    recipeToStockNumerator = recipeToStockNumerator,
    recipeToStockDenominator = recipeToStockDenominator,
    stockMicros = stockMicros,
    inventoryValueRial = inventoryValueRial,
    alertEnabled = alertEnabled,
    alertThresholdMicros = alertThresholdMicros,
    supplierId = supplierId,
    sku = sku,
    itemType = InventoryItemType.fromStoredValue(itemType),
    primaryBarcode = primaryBarcode,
    brand = brand,
    storageCondition = InventoryStorageCondition.fromStoredValue(storageCondition),
    shelfLifeDays = shelfLifeDays,
    trackLot = trackLot,
    trackExpiry = trackExpiry,
    minimumStockMicros = minimumStockMicros,
    maximumStockMicros = maximumStockMicros,
    safetyStockMicros = safetyStockMicros,
    reorderPointMicros = reorderPointMicros,
    leadTimeDays = leadTimeDays,
)
