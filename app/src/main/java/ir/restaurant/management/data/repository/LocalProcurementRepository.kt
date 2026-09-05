package ir.restaurant.management.data.repository

import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties
import androidx.room.withTransaction
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.GoodsReceiptEntity
import ir.restaurant.management.data.db.GoodsReceiptLineEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryReplenishmentPolicyEntity
import ir.restaurant.management.data.db.ProcurementDemandUsageRow
import ir.restaurant.management.data.db.ProcurementLatestCostRow
import ir.restaurant.management.data.db.ProcurementInvoiceLinkEntity
import ir.restaurant.management.data.db.PurchaseOrderEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.data.db.PurchaseReturnEntity
import ir.restaurant.management.data.db.PurchaseReturnLineEntity
import ir.restaurant.management.data.db.PurchaseRequisitionEntity
import ir.restaurant.management.data.db.PurchaseRequisitionLineEntity
import ir.restaurant.management.data.db.SupplierCreditRow
import ir.restaurant.management.data.db.SupplierItemOfferEntity
import ir.restaurant.management.data.db.SupplierItemOfferRow
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.inventory.InventoryReplenishmentRisk
import ir.restaurant.management.domain.inventory.InventoryReplenishmentService
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.ProcurementOverview
import ir.restaurant.management.domain.purchase.ProcurementRepository
import ir.restaurant.management.domain.purchase.PurchaseCalculator
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderLineRecord
import ir.restaurant.management.domain.purchase.PurchaseOrderRecord
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import ir.restaurant.management.domain.purchase.PurchaseOrderDispatchChannel
import ir.restaurant.management.domain.purchase.PurchaseOrderAcknowledgementDraft
import ir.restaurant.management.domain.purchase.PurchaseApprovalPolicy
import ir.restaurant.management.domain.purchase.PurchaseReturnDraft
import ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft
import ir.restaurant.management.domain.purchase.RequisitionRecord
import ir.restaurant.management.domain.purchase.RequisitionStatus
import ir.restaurant.management.domain.purchase.RequisitionLineDraft
import ir.restaurant.management.domain.purchase.ReplenishmentInput
import ir.restaurant.management.domain.purchase.ReplenishmentPlanner
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyRecord
import ir.restaurant.management.domain.purchase.ThreeWayMatchResult
import ir.restaurant.management.domain.purchase.ThreeWayMatchStatus
import ir.restaurant.management.domain.purchase.SupplierCreditRecord
import ir.restaurant.management.domain.purchase.SupplierOfferCandidate
import ir.restaurant.management.domain.purchase.SupplierOfferDraft
import ir.restaurant.management.domain.purchase.SupplierOfferRecord
import ir.restaurant.management.domain.purchase.SupplierSourcingAdvisor
import ir.restaurant.management.domain.purchase.SplitPurchaseOrdersDraft
import ir.restaurant.management.domain.purchase.SplitPurchaseOrdersResult
import ir.restaurant.management.domain.purchase.SupplierAssignedRequisitionLine
import ir.restaurant.management.domain.purchase.SupplierOrderSplitter
import ir.restaurant.management.domain.purchase.SupplierScorecard
import ir.restaurant.management.domain.security.UserRole
import java.math.BigInteger
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

private data class SupplierSignals(
    val receipts: List<GoodsReceiptEntity>,
    val receiptLines: List<GoodsReceiptLineEntity>,
    val returns: List<PurchaseReturnEntity>,
    val returnLines: List<PurchaseReturnLineEntity>,
    val invoiceLinks: List<ProcurementInvoiceLinkEntity>,
    val credits: List<SupplierCreditRow>,
)

private data class ReplenishmentSignals(
    val policies: List<InventoryReplenishmentPolicyEntity>,
    val inventory: List<InventoryItemEntity>,
    val usage: List<ProcurementDemandUsageRow>,
    val latestCosts: List<ProcurementLatestCostRow>,
    val offers: List<SupplierItemOfferRow>,
    val activeRequestedItemIds: Set<Long>,
)

private data class ProcurementCatalogSignals(
    val latestCosts: List<ProcurementLatestCostRow>,
    val offers: List<SupplierItemOfferRow>,
)

private fun toSupplierOfferRecord(row: SupplierItemOfferRow) = SupplierOfferRecord(
    id = row.id,
    supplierId = row.supplierId,
    supplierName = row.supplierName,
    itemId = row.itemId,
    itemName = row.itemName,
    supplierSku = row.supplierSku,
    unitCostRial = row.unitCostRial,
    minimumOrderMicros = row.minimumOrderMicros,
    orderMultipleMicros = row.orderMultipleMicros,
    leadTimeDays = row.leadTimeDays,
    validUntilEpochDay = row.validUntilEpochDay,
    isActive = row.isActive,
)

class LocalProcurementRepository(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val todayEpochDay: () -> Long = ::currentLocalEpochDay,
    private val approvalPolicy: PurchaseApprovalPolicy = PurchaseApprovalPolicy(),
    private val accountingPosting: AccountingPostingService = LocalAccountingPostingEngine(database, clock = clock),
    private val treasury: TreasuryService = ir.restaurant.management.data.treasury.LocalTreasuryServiceV2(
        database = database, accounting = accountingPosting, authorizer = authorizer,
        accountCatalog = ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog(), clock = clock,
    ),
    private val inventoryReplenishment: InventoryReplenishmentService = LocalInventoryReplenishmentService(
        database,
        authorizer,
    ),
) : ProcurementRepository {
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val dataScope = LocalDataScopeService(database, authorizer)
    private val audit = LocalAuditEventWriter(database)
    private val receivingService = ProcurementReceivingService(
        database = database,
        authorizer = authorizer,
        accountingPosting = accountingPosting,
        clock = clock,
        syncRecorder = syncRecorder,
    )
    private val invoiceMatchingService = ProcurementInvoiceMatchingService(
        database = database,
        authorizer = authorizer,
        accountingPosting = accountingPosting,
        treasury = treasury,
        clock = clock,
        syncRecorder = syncRecorder,
    )
    private val sourcingService = ProcurementSourcingService(
        database = database,
        authorizer = authorizer,
        inventoryReplenishment = inventoryReplenishment,
        syncRecorder = syncRecorder,
        clock = clock,
        todayEpochDay = todayEpochDay,
    )

    private val baseOverview: Flow<ProcurementOverview> = combine(
        database.procurementDao().observeRequisitions(),
        database.procurementDao().observeRequisitionLines(),
        database.procurementDao().observeOrders(),
        database.procurementDao().observeOrderLines(),
        database.phase3Dao().observeInvoiceLineMatches(),
    ) { requisitions, requisitionLines, orders, lines, invoiceMatches ->
        val invoicedByOrderLine = invoiceMatches.groupBy { it.purchaseOrderLineId }
            .mapValues { (_, rows) -> safeSum(rows.map { it.invoiceQtyMicros }) }
        ProcurementOverview(
            requisitions = requisitions.map {
                RequisitionRecord(
                    id = it.id,
                    requestNo = it.requestNo,
                    department = it.department,
                    requiredEpochDay = it.requiredEpochDay,
                    status = RequisitionStatus.valueOf(it.status),
                    requestedBy = it.requestedBy,
                    approvedBy = it.approvedBy,
                    note = it.note,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    estimatedTotalRial = MoneyRial.sum(
                        requisitionLines.filter { line -> line.requisitionId == it.id }.map { line ->
                            MoneyRial.of(line.estimatedUnitCostRial).times(QuantityMicros.of(line.requestedQtyMicros))
                        },
                    ).value,
                    lineCount = it.lineCount,
                    supplierGroupCount = requisitionLines.filter { line -> line.requisitionId == it.id }.mapNotNull { line -> line.recommendedSupplierId }.distinct().size,
                    unassignedLineCount = requisitionLines.count { line -> line.requisitionId == it.id && line.recommendedSupplierId == null },
                    requiredApprovalLevel = it.requiredApprovalLevel,
                    completedApprovalLevel = it.completedApprovalLevel,
                    firstApprovedBy = it.firstApprovedBy,
                    secondApprovedBy = it.secondApprovedBy,
                    committedBudgetId = it.committedBudgetId,
                    committedBudgetRial = it.committedBudgetRial,
                    branchId = it.branchId ?: 0L,
                    destinationLocationId = it.destinationLocationId ?: 0L,
                )
            },
            orders = orders.map { order ->
                PurchaseOrderRecord(
                    id = order.id,
                    orderNo = order.orderNo,
                    supplierId = order.supplierId,
                    supplierName = order.supplierName,
                    requisitionId = order.requisitionId,
                    orderEpochDay = order.orderEpochDay,
                    expectedEpochDay = order.expectedEpochDay,
                    sentAtEpochMillis = order.sentAtEpochMillis,
                    sentBy = order.sentBy,
                    dispatchChannel = order.dispatchChannel?.let { PurchaseOrderDispatchChannel.valueOf(it) },
                    acknowledgedAtEpochMillis = order.acknowledgedAtEpochMillis,
                    supplierConfirmationNo = order.supplierConfirmationNo,
                    confirmedExpectedEpochDay = order.confirmedExpectedEpochDay,
                    status = PurchaseOrderStatus.valueOf(order.status),
                    orderedValueRial = MoneyRial.sum(
                        lines.filter { it.purchaseOrderId == order.id }.map {
                            MoneyRial.of(it.unitCostRial).times(QuantityMicros.of(it.orderedQtyMicros))
                        },
                    ).value,
                    acceptedValueRial = MoneyRial.sum(
                        lines.filter { it.purchaseOrderId == order.id }.map {
                            MoneyRial.of(it.unitCostRial).times(QuantityMicros.of(it.receivedQtyMicros - it.returnedQtyMicros))
                        },
                    ).value,
                    receiptCount = order.receiptCount,
                    invoiceNo = order.invoiceNo,
                    lines = lines.filter { it.purchaseOrderId == order.id }.map {
                        PurchaseOrderLineRecord(
                            id = it.id,
                            itemId = it.itemId,
                            itemName = it.itemNameSnapshot,
                            supplierSku = it.supplierSkuSnapshot,
                            orderedQtyMicros = it.orderedQtyMicros,
                            receivedQtyMicros = it.receivedQtyMicros,
                            rejectedQtyMicros = it.rejectedQtyMicros,
                            returnedQtyMicros = it.returnedQtyMicros,
                            unitCostRial = it.unitCostRial,
                            invoicedQtyMicros = invoicedByOrderLine[it.id] ?: 0L,
                        )
                    },
                    branchId = order.branchId ?: 0L,
                    destinationLocationId = order.destinationLocationId ?: 0L,
                )
            },
        )
    }

    private val scopedBaseOverview: Flow<ProcurementOverview> = combine(
        baseOverview,
        dataScope.scopedBranches(),
        database.securityDao().observeCurrentUser(),
    ) { base, branches, currentUser ->
        val branchIds = branches.map { it.id }.toSet()
        val isOwner = currentUser?.role?.let(UserRole::fromStoredValue) == UserRole.OWNER
        fun visible(branchId: Long): Boolean = branchId in branchIds || (isOwner && branchId == 0L)
        base.copy(
            requisitions = base.requisitions.filter { visible(it.branchId) },
            orders = base.orders.filter { visible(it.branchId) },
        )
    }

    private val financialSignals = combine(
        database.procurementDao().observeInvoiceLinks(),
        database.procurementDao().observeSupplierCredits(),
    ) { links, credits -> links to credits }

    private val supplierSignals = combine(
        database.procurementDao().observeGoodsReceipts(),
        database.procurementDao().observeGoodsReceiptLines(),
        database.procurementDao().observePurchaseReturns(),
        database.procurementDao().observePurchaseReturnLines(),
        financialSignals,
    ) { receipts, receiptLines, returns, returnLines, financial ->
        SupplierSignals(receipts, receiptLines, returns, returnLines, financial.first, financial.second)
    }

    private val performanceOverview: Flow<ProcurementOverview> = combine(scopedBaseOverview, supplierSignals) { base, signals ->
        base.copy(
            supplierScorecards = buildSupplierScorecards(base.orders, signals),
            supplierCredits = signals.credits.map {
                SupplierCreditRecord(
                    id = it.id,
                    creditNo = it.creditNo,
                    supplierId = it.supplierId,
                    supplierName = it.supplierName,
                    amountRial = it.amountRial,
                    appliedRial = it.appliedRial,
                    status = it.status,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                )
            },
        )
    }

    private val catalogSignals = combine(
        database.procurementDao().observeLatestPurchaseCosts(),
        database.procurementDao().observeSupplierItemOffers(),
    ) { costs, offers -> ProcurementCatalogSignals(costs, offers) }

    private val scopedReplenishmentInventory: Flow<List<InventoryItemEntity>> = combine(
        database.inventoryDao().observeActive(),
        database.inventoryBalanceDao().observeAll(),
        database.inventoryLotDao().observeActiveStock(),
        dataScope.scopedLocations(),
    ) { items, balances, lots, locations ->
        val allowedLocationIds = locations.asSequence().filter { it.isActive }.map { it.id }.toSet()
        val scopedBalances = balances.filter { it.locationId in allowedLocationIds }.groupBy { it.itemId }
        val today = todayEpochDay()
        val expiredByItemAndLocation = lots.asSequence()
            .filter { lot ->
                lot.locationId in allowedLocationIds && lot.quantityMicros > 0 && lot.status == "ACTIVE" &&
                    lot.expiryEpochDay?.let { it < today } == true
            }
            .groupBy { it.itemId to it.locationId }
            .mapValues { (_, rows) -> rows.fold(0L) { sum, row -> SignedLongMath.add(sum, row.quantityMicros) } }
        items.map { item ->
            val itemBalances = scopedBalances[item.id].orEmpty()
            val availableMicros = itemBalances.fold(0L) { sum, balance ->
                val expired = expiredByItemAndLocation[item.id to balance.locationId] ?: 0L
                val usable = SignedLongMath.subtract(
                    SignedLongMath.subtract(
                        SignedLongMath.subtract(
                            SignedLongMath.subtract(balance.onHandMicros, balance.reservedMicros),
                            balance.damagedMicros,
                        ),
                        balance.quarantinedMicros,
                    ),
                    expired,
                ).coerceAtLeast(0L)
                SignedLongMath.add(sum, usable)
            }
            val scopedValue = itemBalances.fold(0L) { sum, balance ->
                SignedLongMath.add(sum, balance.inventoryValueRial)
            }
            item.copy(stockMicros = availableMicros, inventoryValueRial = scopedValue)
        }
    }

    private val scopedDemandUsage: Flow<List<ProcurementDemandUsageRow>> =
        dataScope.scopedLocations().flatMapLatest { locations ->
            val ids = locations.asSequence().filter { it.isActive }.map { it.id }.toList()
            if (ids.isEmpty()) flowOf(emptyList())
            else database.procurementDao().observeDemandUsageForLocations(todayEpochDay() - 29, ids)
        }

    private val scopedActiveRequestedItemIds: Flow<List<Long>> =
        dataScope.scopedBranches().flatMapLatest { branches ->
            val ids = branches.asSequence().filter { it.isActive }.map { it.id }.toList()
            if (ids.isEmpty()) flowOf(emptyList())
            else database.procurementDao().observeActiveRequestedItemIdsForBranches(ids)
        }

    private val replenishmentSignals = combine(
        database.procurementDao().observeReplenishmentPolicies(),
        scopedReplenishmentInventory,
        scopedDemandUsage,
        catalogSignals,
        scopedActiveRequestedItemIds,
    ) { policies, inventory, usage, catalog, requested ->
        ReplenishmentSignals(policies, inventory, usage, catalog.latestCosts, catalog.offers, requested.toSet())
    }

    override val overview: Flow<ProcurementOverview> = combine(performanceOverview, replenishmentSignals) { base, signals ->
        val policies = signals.policies.map {
            ReplenishmentPolicyRecord(
                itemId = it.itemId,
                preferredSupplierId = it.preferredSupplierId,
                targetCoverDays = it.targetCoverDays,
                leadTimeDays = it.leadTimeDays,
                safetyStockMicros = it.safetyStockMicros,
                orderMultipleMicros = it.orderMultipleMicros,
                isEnabled = it.isEnabled,
            )
        }
        val usageByItem = signals.usage.associateBy { it.itemId }
        val latestCostByItem = signals.latestCosts.associateBy { it.itemId }
        val offerRecords = signals.offers.map(::toSupplierOfferRecord)
        val policyByItem = policies.associateBy { it.itemId }
        val suggestions = signals.inventory.mapNotNull { item ->
            val policy = policyByItem[item.id] ?: return@mapNotNull null
            val openOrderQty = safeSum(
                base.orders.filter { it.status in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED) }
                    .flatMap { it.lines }
                    .filter { it.itemId == item.id }
                    .map { it.remainingQtyMicros },
            )
            val latestCost = latestCostByItem[item.id]?.unitCostRial
                ?: base.orders.asSequence().flatMap { it.lines.asSequence() }
                    .firstOrNull { it.itemId == item.id }?.unitCostRial
                ?: 0
            val suggestion = ReplenishmentPlanner.suggest(
                ReplenishmentInput(
                    itemId = item.id,
                    itemName = item.name,
                    currentStockMicros = item.stockMicros,
                    openPurchaseOrderMicros = openOrderQty,
                    usage30DaysMicros = usageByItem[item.id]?.usageMicros ?: 0,
                    estimatedUnitCostRial = latestCost,
                    policy = policy,
                    hasPendingRequest = item.id in signals.activeRequestedItemIds,
                    preferredSupplierScore = policy.preferredSupplierId?.let { supplierId ->
                        base.supplierScorecards.firstOrNull { it.supplierId == supplierId }?.score
                    },
                ),
            ) ?: return@mapNotNull null
            val sourcing = SupplierSourcingAdvisor.choose(
                candidates = offerRecords.filter { it.itemId == item.id && it.isActive && (it.validUntilEpochDay == null || it.validUntilEpochDay >= todayEpochDay()) }.map { offer ->
                    SupplierOfferCandidate(offer, base.supplierScorecards.firstOrNull { it.supplierId == offer.supplierId }?.score)
                },
                requiredQuantityMicros = suggestion.suggestedOrderMicros,
                baselineUnitCostRial = suggestion.estimatedUnitCostRial,
                preferredSupplierId = policy.preferredSupplierId,
            )
            if (sourcing == null) suggestion else suggestion.copy(
                suggestedOrderMicros = sourcing.orderQuantityMicros,
                estimatedUnitCostRial = sourcing.offer.unitCostRial,
                estimatedOrderValueRial = sourcing.orderValueRial,
                recommendedSupplierId = sourcing.offer.supplierId,
                recommendedSupplierName = sourcing.offer.supplierName,
                recommendedSupplierSku = sourcing.offer.supplierSku,
                comparedOfferCount = sourcing.comparedOfferCount,
                recommendedLeadTimeDays = sourcing.offer.leadTimeDays,
                offerValidUntilEpochDay = sourcing.offer.validUntilEpochDay,
                estimatedSavingsRial = sourcing.estimatedSavingsRial,
            )
        }.sortedWith(
            compareBy<ir.restaurant.management.domain.purchase.ReplenishmentSuggestion> { it.risk.ordinal }
                .thenByDescending { it.estimatedOrderValueRial },
        )
        base.copy(replenishmentPolicies = policies, replenishmentSuggestions = suggestions, supplierOffers = offerRecords)
    }

    override suspend fun submitRequisition(draft: PurchaseRequisitionDraft): Long {
        val actor = authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        dataScope.requireLocation(valid.destinationLocationId, valid.branchId)
        val now = clock()
        val globalId = GlobalId.new().value
        return database.withTransaction {
            val id = database.procurementDao().insertRequisition(
                PurchaseRequisitionEntity(
                    requestNo = numbering.next(DocumentNumberType.PURCHASE_REQUISITION),
                    department = valid.department,
                    requiredEpochDay = valid.requiredEpochDay,
                    branchId = valid.branchId,
                    destinationLocationId = valid.destinationLocationId,
                    status = RequisitionStatus.SUBMITTED.name,
                    requestedBy = actor.displayName,
                    approvedBy = null,
                    note = valid.note,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    globalId = globalId,
                    requestedByActorId = actor.id,
                    correlationId = "purchase_requisition:$globalId",
                ),
            )
            database.procurementDao().insertRequisitionLines(valid.lines.map { line ->
                val item = database.inventoryDao().activeById(line.itemId)
                    ?: error("یکی از کالاهای درخواست فعال نیست.")
                PurchaseRequisitionLineEntity(
                    requisitionId = id,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    requestedQtyMicros = line.quantityMicros,
                    estimatedUnitCostRial = line.estimatedUnitCostRial,
                    recommendedSupplierId = line.recommendedSupplierId,
                    supplierSkuSnapshot = line.supplierSku,
                    recommendedLeadTimeDays = line.recommendedLeadTimeDays,
                    note = line.note,
                )
            })
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "SUBMIT",
                entityType = "PURCHASE_REQUISITION",
                entityId = id,
                description = "ثبت درخواست خرید ${valid.department}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.requiredEpochDay,
                reason = valid.note.ifBlank { "ثبت درخواست خرید" },
                afterSnapshot = "branchId=${valid.branchId};locationId=${valid.destinationLocationId};lines=${valid.lines.size}",
                correlationId = "purchase_requisition:$globalId",
            )
            syncRecorder?.record("PURCHASE_REQUISITION", id, "SUBMIT", now)
            id
        }
    }

    override suspend fun reviewRequisition(requisitionId: Long, approve: Boolean, note: String) {
        val actor = authorizer.require(Permission.PURCHASE_APPROVE)
        require(note.trim().length <= 300) { "توضیحات بررسی بیش از حد طولانی است." }
        val now = clock()
        database.withTransaction {
            val current = database.procurementDao().requisitionById(requisitionId)
                ?: error("درخواست خرید پیدا نشد.")
            dataScope.requireLocation(requireNotNull(current.destinationLocationId) { "درخواست خرید بدون انبار مقصد قابل بررسی نیست." }, requireNotNull(current.branchId) { "درخواست خرید بدون شعبه قابل بررسی نیست." })
            require(current.status in setOf(RequisitionStatus.SUBMITTED.name, RequisitionStatus.PENDING_SECOND_APPROVAL.name)) { "درخواست قبلاً بررسی شده است." }
            val lines = database.procurementDao().requisitionLines(current.id)
            val total = lines.fold(0L) { sum, line ->
                SignedLongMath.add(sum, MoneyRial.of(line.estimatedUnitCostRial).times(QuantityMicros.of(line.requestedQtyMicros)).value)
            }
            val requiredLevel = approvalPolicy.plan(total).requiredLevel
            SegregationOfDuties.requireDifferentHistoricalAware(
                operation = "PURCHASE_REQUISITION_REVIEW",
                creatorActorId = current.requestedByActorId,
                creatorDisplayName = current.requestedBy,
                approverActorId = actor.id,
                approverDisplayName = actor.displayName,
            )
            if (!approve) {
                val reviewNote = note.trim().ifBlank { current.note.ifBlank { "رد درخواست خرید" } }
                check(database.procurementDao().recordRequisitionApproval(current.id, current.status, RequisitionStatus.REJECTED.name, requiredLevel, current.completedApprovalLevel, actor.displayName, actor.id, reviewNote, now) == 1)
                audit.appendAuthorized(
                    authorizer = authorizer,
                    action = "REJECT",
                    entityType = "PURCHASE_REQUISITION",
                    entityId = current.id,
                    description = "رد درخواست خرید ${current.requestNo}",
                    occurredAtEpochMillis = now,
                    businessEpochDay = current.requiredEpochDay,
                    reason = reviewNote,
                    beforeSnapshot = "status=${current.status};level=${current.completedApprovalLevel}",
                    afterSnapshot = "status=${RequisitionStatus.REJECTED.name};level=${current.completedApprovalLevel}",
                    correlationId = current.correlationId.ifBlank { "purchase_requisition:${current.globalId}" },
                )
                syncRecorder?.record("PURCHASE_REQUISITION", current.id, "REJECT", now)
                return@withTransaction
            }
            val nextLevel = current.completedApprovalLevel + 1
            if (nextLevel == 2) authorizer.requireOwner()
            if (nextLevel == 2) {
                SegregationOfDuties.requireDifferentHistoricalAware(
                    operation = "PURCHASE_REQUISITION_SECOND_APPROVAL",
                    creatorActorId = current.firstApprovedByActorId,
                    creatorDisplayName = current.firstApprovedBy.orEmpty(),
                    approverActorId = actor.id,
                    approverDisplayName = actor.displayName,
                )
            }
            require(nextLevel <= requiredLevel) { "سطح تأیید این درخواست کامل شده است." }
            val finalApproval = nextLevel == requiredLevel
            val newStatus = if (finalApproval) RequisitionStatus.APPROVED.name else RequisitionStatus.PENDING_SECOND_APPROVAL.name
            val reviewNote = note.trim().ifBlank { current.note.ifBlank { "تأیید درخواست خرید" } }
            check(database.procurementDao().recordRequisitionApproval(current.id, current.status, newStatus, requiredLevel, nextLevel, actor.displayName, actor.id, reviewNote, now) == 1) { "درخواست هم‌زمان تغییر کرده است." }
            audit.appendAuthorized(
                authorizer = authorizer,
                action = if (finalApproval) "APPROVE" else "APPROVE_STAGE_${nextLevel}",
                entityType = "PURCHASE_REQUISITION",
                entityId = current.id,
                description = "تأیید درخواست خرید ${current.requestNo}",
                occurredAtEpochMillis = now,
                businessEpochDay = current.requiredEpochDay,
                reason = reviewNote,
                beforeSnapshot = "status=${current.status};level=${current.completedApprovalLevel}",
                afterSnapshot = "status=$newStatus;level=$nextLevel;requiredLevel=$requiredLevel",
                correlationId = current.correlationId.ifBlank { "purchase_requisition:${current.globalId}" },
            )
            if (finalApproval && total > 0) {
                val budget = database.managementControlDao().activePurchaseBudget(current.requiredEpochDay, current.department)
                    ?: error("برای این تاریخ و مرکز هزینه، بودجه خرید فعال تعریف نشده است.")
                val committed = database.managementControlDao().committedAmount(budget.id)
                val actual = database.managementControlDao().actualBudgetSpend(budget.id)
                require(SignedLongMath.add(SignedLongMath.add(actual, committed), total) <= budget.limitRial) { "بودجه قابل‌تعهد با احتساب مصرف واقعی کافی نیست." }
                database.managementControlDao().insertBudgetCommitment(ir.restaurant.management.data.db.BudgetCommitmentEntity(budgetId=budget.id,referenceType="PURCHASE_REQUISITION",referenceId=current.id,amountRial=total,actor=actor.displayName,createdAtEpochMillis=now,updatedAtEpochMillis=now))
                check(database.procurementDao().linkRequisitionBudget(current.id, budget.id, total) == 1)
            }
            syncRecorder?.record("PURCHASE_REQUISITION", current.id, if (finalApproval) "FINAL_APPROVE" else "LEVEL1_APPROVE", now)
        }
    }

    override suspend fun createOrder(draft: PurchaseOrderDraft): Long {
        authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        val now = clock()
        val actor = authorizer.actor()
        return database.withTransaction {
            val request = database.procurementDao().requisitionById(valid.requisitionId)
                ?: error("درخواست خرید پیدا نشد.")
            dataScope.requireLocation(requireNotNull(request.destinationLocationId) { "درخواست خرید بدون انبار مقصد قابل تبدیل نیست." }, requireNotNull(request.branchId) { "درخواست خرید بدون شعبه قابل تبدیل نیست." })
            require(request.status == RequisitionStatus.APPROVED.name) { "فقط درخواست تأییدشده قابل تبدیل به سفارش است." }
            val supplier = database.supplierDao().activeById(valid.supplierId)
                ?: error("تأمین‌کننده فعال پیدا نشد.")
            val requestLines = database.procurementDao().requisitionLines(request.id)
            require(requestLines.isNotEmpty()) { "ردیف‌های درخواست پیدا نشدند." }
            val assignedSuppliers = requestLines.mapNotNull { it.recommendedSupplierId }.distinct()
            require(assignedSuppliers.isEmpty() || (assignedSuppliers.size == 1 && assignedSuppliers.single() == supplier.id && requestLines.none { it.recommendedSupplierId == null })) {
                "این درخواست برای چند تأمین‌کننده تخصیص یافته است؛ از ساخت سفارش‌های تفکیک‌شده استفاده کنید."
            }
            val orderId = database.procurementDao().insertOrder(
                PurchaseOrderEntity(
                    orderNo = numbering.next(DocumentNumberType.PURCHASE_ORDER),
                    supplierId = supplier.id,
                    supplierNameSnapshot = supplier.name,
                    requisitionId = request.id,
                    branchId = request.branchId,
                    destinationLocationId = request.destinationLocationId,
                    orderEpochDay = valid.orderEpochDay,
                    expectedEpochDay = valid.expectedEpochDay,
                    sentAtEpochMillis = null,
                    sentBy = null,
                    dispatchChannel = null,
                    acknowledgedAtEpochMillis = null,
                    supplierConfirmationNo = null,
                    confirmedExpectedEpochDay = null,
                    status = PurchaseOrderStatus.OPEN.name,
                    note = valid.note,
                    createdBy = actor,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            database.procurementDao().insertOrderLines(requestLines.map {
                PurchaseOrderLineEntity(
                    purchaseOrderId = orderId,
                    itemId = it.itemId,
                    itemNameSnapshot = it.itemNameSnapshot,
                    supplierSkuSnapshot = it.supplierSkuSnapshot,
                    orderedQtyMicros = it.requestedQtyMicros,
                    unitCostRial = it.estimatedUnitCostRial,
                    receivedQtyMicros = 0,
                    rejectedQtyMicros = 0,
                )
            })
            check(database.procurementDao().transitionRequisition(
                id = request.id,
                expectedStatus = RequisitionStatus.APPROVED.name,
                newStatus = RequisitionStatus.CONVERTED.name,
                approvedBy = request.approvedBy,
                note = request.note,
                updatedAtEpochMillis = now,
            ) == 1) { "درخواست هم‌زمان تغییر کرده است." }
            syncRecorder?.record("PURCHASE_ORDER", orderId, "CREATE", now)
            orderId
        }
    }

    override suspend fun createSplitOrders(draft: SplitPurchaseOrdersDraft): SplitPurchaseOrdersResult {
        authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        val now = clock()
        val actor = authorizer.actor()
        return database.withTransaction {
            val request = database.procurementDao().requisitionById(valid.requisitionId)
                ?: error("درخواست خرید پیدا نشد.")
            dataScope.requireLocation(requireNotNull(request.destinationLocationId) { "درخواست خرید بدون انبار مقصد قابل تبدیل نیست." }, requireNotNull(request.branchId) { "درخواست خرید بدون شعبه قابل تبدیل نیست." })
            require(request.status == RequisitionStatus.APPROVED.name) { "فقط درخواست تأییدشده قابل تبدیل به سفارش است." }
            val requestLines = database.procurementDao().requisitionLines(request.id)
            require(requestLines.isNotEmpty()) { "ردیف‌های درخواست پیدا نشدند." }
            val linesById = requestLines.associateBy { it.id }
            val groups = SupplierOrderSplitter.split(
                lines = requestLines.map { SupplierAssignedRequisitionLine(it.id, it.recommendedSupplierId, it.recommendedLeadTimeDays) },
                orderEpochDay = valid.orderEpochDay,
                fallbackSupplierId = valid.fallbackSupplierId,
            )
            require(groups.size <= 100) { "تعداد گروه‌های تأمین‌کننده معتبر نیست." }
            val orderIds = groups.map { group ->
                val supplier = database.supplierDao().activeById(group.supplierId)
                    ?: error("یکی از تأمین‌کنندگان تخصیص‌یافته فعال نیست.")
                val orderId = database.procurementDao().insertOrder(
                    PurchaseOrderEntity(
                        orderNo = numbering.next(DocumentNumberType.PURCHASE_ORDER),
                        supplierId = supplier.id,
                        supplierNameSnapshot = supplier.name,
                        requisitionId = request.id,
                        branchId = request.branchId,
                        destinationLocationId = request.destinationLocationId,
                        orderEpochDay = valid.orderEpochDay,
                        expectedEpochDay = group.expectedEpochDay,
                        sentAtEpochMillis = null,
                        sentBy = null,
                        dispatchChannel = null,
                        acknowledgedAtEpochMillis = null,
                        supplierConfirmationNo = null,
                        confirmedExpectedEpochDay = null,
                        status = PurchaseOrderStatus.OPEN.name,
                        note = valid.note.ifBlank { "سفارش تفکیک‌شده خودکار از ${request.requestNo}" },
                        createdBy = actor,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                    ),
                )
                database.procurementDao().insertOrderLines(group.lineIds.map { lineId ->
                    val line = linesById.getValue(lineId)
                    PurchaseOrderLineEntity(
                        purchaseOrderId = orderId,
                        itemId = line.itemId,
                        itemNameSnapshot = line.itemNameSnapshot,
                        supplierSkuSnapshot = line.supplierSkuSnapshot,
                        orderedQtyMicros = line.requestedQtyMicros,
                        unitCostRial = line.estimatedUnitCostRial,
                        receivedQtyMicros = 0,
                        rejectedQtyMicros = 0,
                    )
                })
                syncRecorder?.record("PURCHASE_ORDER", orderId, "CREATE_SPLIT", now)
                orderId
            }
            check(database.procurementDao().transitionRequisition(
                id = request.id,
                expectedStatus = RequisitionStatus.APPROVED.name,
                newStatus = RequisitionStatus.CONVERTED.name,
                approvedBy = request.approvedBy,
                note = request.note,
                updatedAtEpochMillis = now,
            ) == 1) { "درخواست هم‌زمان تغییر کرده است." }
            SplitPurchaseOrdersResult(orderIds, groups.size, requestLines.size)
        }
    }

    override suspend fun markOrderSent(orderId: Long, channel: PurchaseOrderDispatchChannel) {
        authorizer.require(Permission.PURCHASES)
        require(orderId > 0) { "سفارش خرید معتبر نیست." }
        val now = clock()
        database.withTransaction {
            val order = database.procurementDao().orderById(orderId) ?: error("سفارش خرید پیدا نشد.")
            dataScope.requireLocation(requireNotNull(order.destinationLocationId) { "سفارش بدون انبار مقصد معتبر نیست." }, requireNotNull(order.branchId) { "سفارش بدون شعبه معتبر نیست." })
            require(order.status == PurchaseOrderStatus.OPEN.name) { "فقط سفارش باز قابل ارسال است." }
            require(order.sentAtEpochMillis == null) { "ارسال این سفارش قبلاً ثبت شده است." }
            check(database.procurementDao().markOrderSent(order.id, now, authorizer.actor(), channel.name) == 1) {
                "وضعیت ارسال سفارش هم‌زمان تغییر کرده است."
            }
            syncRecorder?.record("PURCHASE_ORDER", order.id, "DISPATCH_${channel.name}", now)
        }
    }

    override suspend fun acknowledgeOrder(draft: PurchaseOrderAcknowledgementDraft) {
        authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        val now = clock()
        database.withTransaction {
            val order = database.procurementDao().orderById(valid.purchaseOrderId) ?: error("سفارش خرید پیدا نشد.")
            dataScope.requireLocation(requireNotNull(order.destinationLocationId) { "سفارش بدون انبار مقصد معتبر نیست." }, requireNotNull(order.branchId) { "سفارش بدون شعبه معتبر نیست." })
            require(order.sentAtEpochMillis != null) { "ابتدا ارسال سفارش را ثبت کنید." }
            require(order.acknowledgedAtEpochMillis == null) { "تأیید تأمین‌کننده قبلاً ثبت شده است." }
            require(valid.confirmedExpectedEpochDay >= order.orderEpochDay) { "موعد قطعی نمی‌تواند قبل از تاریخ سفارش باشد." }
            val latestReceipt = database.procurementDao().latestReceiptEpochDay(order.id)
            require(latestReceipt == null || valid.confirmedExpectedEpochDay >= latestReceipt) { "موعد قطعی نمی‌تواند قبل از دریافت ثبت‌شده باشد." }
            check(database.procurementDao().acknowledgeOrder(order.id, now, valid.supplierConfirmationNo, valid.confirmedExpectedEpochDay) == 1) {
                "وضعیت تأیید سفارش هم‌زمان تغییر کرده است."
            }
            syncRecorder?.record("PURCHASE_ORDER", order.id, "SUPPLIER_ACKNOWLEDGE", now)
        }
    }

    override suspend fun postGoodsReceipt(draft: GoodsReceiptDraft): Long =
        receivingService.receive(draft)

    override suspend fun postPurchaseReturn(draft: PurchaseReturnDraft): Long =
        receivingService.returnToSupplier(draft)

    override suspend fun saveReplenishmentPolicy(draft: ReplenishmentPolicyDraft) =
        sourcingService.saveReplenishmentPolicy(draft)

    override suspend fun saveSupplierOffer(draft: SupplierOfferDraft) =
        sourcingService.saveSupplierOffer(draft)

    override suspend fun submitSuggestedRequisition(itemIds: List<Long>): Long =
        sourcingService.submitSuggestedRequisition(itemIds, ::submitRequisition)

    override suspend fun previewThreeWayMatch(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
    ): ThreeWayMatchResult = invoiceMatchingService.preview(purchaseOrderId, invoice)

    override suspend fun postMatchedInvoice(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
        approvePriceVariance: Boolean,
    ): PostedPurchase = invoiceMatchingService.post(purchaseOrderId, invoice, approvePriceVariance)

    private fun buildSupplierScorecards(
        orders: List<PurchaseOrderRecord>,
        signals: SupplierSignals,
    ): List<SupplierScorecard> {
        val receiptById = signals.receipts.associateBy { it.id }
        return orders.groupBy { it.supplierId }.map { (supplierId, supplierOrders) ->
            val orderIds = supplierOrders.map { it.id }.toSet()
            val completed = supplierOrders.filter { it.status in setOf(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.CLOSED) }
            val onTimeCount = completed.count { order ->
                signals.receipts.filter { it.purchaseOrderId == order.id }
                    .maxOfOrNull { it.receiptEpochDay }
                    ?.let { it <= order.expectedEpochDay } == true
            }
            val relevantReceiptLines = signals.receiptLines.filter { line ->
                receiptById[line.goodsReceiptId]?.purchaseOrderId?.let(orderIds::contains) == true
            }
            val acceptedQty = safeSum(relevantReceiptLines.map { it.acceptedQtyMicros })
            val evaluatedQty = safeSum(relevantReceiptLines.map { it.acceptedQtyMicros + it.rejectedQtyMicros })
            val returnIds = signals.returns.filter { it.supplierId == supplierId }.map { it.id }.toSet()
            val returnedQty = safeSum(signals.returnLines.filter { it.purchaseReturnId in returnIds }.map { it.quantityMicros })
            val relevantLinks = signals.invoiceLinks.filter { link -> link.purchaseOrderId in orderIds }
            val acceptedValue = safeSum(relevantLinks.map { it.acceptedValueRial })
            val absolutePriceVariance = safeSum(relevantLinks.map { abs(it.priceVarianceRial) })
            val onTime = ratioBasisPoints(onTimeCount.toLong(), completed.size.toLong())
            val acceptance = ratioBasisPoints(acceptedQty, evaluatedQty)
            val returns = ratioBasisPoints(returnedQty, acceptedQty)
            val priceVariance = ratioBasisPoints(absolutePriceVariance, acceptedValue)
            val score = if (completed.isEmpty()) 0 else {
                val deliveryScore = onTime * 350 / 10_000
                val qualityScore = acceptance * 300 / 10_000
                val returnScore = (10_000 - returns.coerceAtMost(10_000)) * 150 / 10_000
                val priceScore = (10_000 - priceVariance.coerceAtMost(10_000)) * 200 / 10_000
                (deliveryScore + qualityScore + returnScore + priceScore).toInt()
            }
            SupplierScorecard(
                supplierId = supplierId,
                supplierName = supplierOrders.first().supplierName,
                completedOrders = completed.size,
                onTimeBasisPoints = onTime,
                acceptanceBasisPoints = acceptance,
                returnBasisPoints = returns,
                priceVarianceBasisPoints = priceVariance,
                openCreditRial = safeSum(signals.credits.filter { it.supplierId == supplierId }.map { it.amountRial - it.appliedRial }),
                score = score,
            )
        }.filter { it.completedOrders > 0 }
            .sortedWith(compareByDescending<SupplierScorecard> { it.score }.thenBy { it.supplierName })
    }

    private fun ratioBasisPoints(numerator: Long, denominator: Long): Long {
        if (numerator <= 0 || denominator <= 0) return 0
        return BigInteger.valueOf(numerator)
            .multiply(BigInteger.valueOf(10_000))
            .divide(BigInteger.valueOf(denominator))
            .toLongExactCompat()
    }

    private fun safeSum(values: Iterable<Long>): Long = values.fold(BigInteger.ZERO) { total, value ->
        require(value >= 0) { "مقدار تجمیعی نمی‌تواند منفی باشد." }
        total + BigInteger.valueOf(value)
    }.also {
        require(it <= BigInteger.valueOf(Long.MAX_VALUE)) { "جمع شاخص عملکرد از محدوده امن خارج می‌شود." }
    }.toLongExactCompat()

}
