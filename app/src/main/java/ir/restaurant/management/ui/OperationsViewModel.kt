package ir.restaurant.management.ui

import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.StockMovementRecord
import ir.restaurant.management.domain.operations.InventoryCountDraft
import ir.restaurant.management.domain.operations.InventoryCountRecord
import ir.restaurant.management.domain.operations.InventoryPeriodCloseDraft
import ir.restaurant.management.domain.operations.InventoryPeriodClosureRecord
import ir.restaurant.management.domain.operations.InventoryPeriodClosureDetails
import ir.restaurant.management.domain.operations.InventoryUsageInsight
import ir.restaurant.management.domain.operations.AuditLogRecord
import ir.restaurant.management.domain.operations.AuditLogQuery
import ir.restaurant.management.domain.operations.OperationsRepository
import ir.restaurant.management.domain.operations.PurchaseSummary
import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.operations.SupplierMergeDraft
import ir.restaurant.management.domain.operations.SupplierRecord
import ir.restaurant.management.domain.operations.SupplierPriceInsight
import ir.restaurant.management.domain.operations.WasteDraft
import ir.restaurant.management.domain.operations.WasteRecord
import ir.restaurant.management.domain.control.CostControlReadService
import ir.restaurant.management.domain.control.PurchasePriceInsight
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PurchaseDetails
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseRepository
import ir.restaurant.management.domain.purchase.PurchaseReversalDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementReversalDraft
import ir.restaurant.management.domain.purchase.ProcurementOverview
import ir.restaurant.management.application.procurement.ProcurementUseCases
import ir.restaurant.management.application.inventory.OperationsInventoryUseCases
import ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDraft
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.PurchaseReturnDraft
import ir.restaurant.management.domain.purchase.ReplenishmentPolicyDraft
import ir.restaurant.management.domain.purchase.SupplierOfferDraft
import ir.restaurant.management.domain.purchase.SplitPurchaseOrdersDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderAcknowledgementDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDispatchChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProcurementLaunchAction {
    REQUISITION,
    PURCHASE_ORDER,
    GOODS_RECEIPT,
    PURCHASE_RETURN,
}

data class OperationsUiState(
    val suppliers: List<SupplierRecord> = emptyList(),
    val inventoryLocations: List<InventoryLocationRecord> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val lowStockItems: List<InventoryItemRecord> = emptyList(),
    val inventoryCounts: List<InventoryCountRecord> = emptyList(),
    val inventoryPeriodClosures: List<InventoryPeriodClosureRecord> = emptyList(),
    val selectedInventoryClosureDetails: InventoryPeriodClosureDetails? = null,
    val selectedInventoryItemId: Long? = null,
    val selectedStockMovements: List<StockMovementRecord> = emptyList(),
    val recentStockMovements: List<StockMovementRecord> = emptyList(),
    val auditLogs: List<AuditLogRecord> = emptyList(),
    val auditQuery: AuditLogQuery = AuditLogQuery(),
    val usageInsights: List<InventoryUsageInsight> = emptyList(),
    val supplierPriceInsights: List<SupplierPriceInsight> = emptyList(),
    val purchasePriceControlInsights: List<PurchasePriceInsight> = emptyList(),
    val wasteRecords: List<WasteRecord> = emptyList(),
    val purchases: List<PurchaseSummary> = emptyList(),
    val settlementAlerts: List<PurchaseSummary> = emptyList(),
    val procurement: ProcurementOverview = ProcurementOverview(),
    val purchaseDashboard: PurchaseDashboardUi = PurchaseDashboardUi(),
    val selectedPurchase: PurchaseDetails? = null,
    val purchaseSearch: String = "",
    val procurementLaunchAction: ProcurementLaunchAction? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
)

private data class InventoryCoreContent(
    val suppliers: List<SupplierRecord>,
    val locations: List<InventoryLocationRecord>,
    val items: List<InventoryItemRecord>,
    val lowStock: List<InventoryItemRecord>,
    val counts: List<InventoryCountRecord>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OperationsViewModel(
    private val operationsRepository: OperationsRepository,
    private val purchaseRepository: PurchaseRepository,
    private val procurementUseCases: ProcurementUseCases,
    private val inventoryOperations: OperationsInventoryUseCases,
    private val costControlReadService: CostControlReadService,
) : ViewModel() {
    private val purchaseSearch = MutableStateFlow("")
    private val purchasePeriod = MutableStateFlow(DashboardPeriod.TODAY)
    private val busy = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val refreshRevision = MutableStateFlow(0L)
    private val selectedPurchaseId = MutableStateFlow<Long?>(null)
    private val selectedInventoryClosureId = MutableStateFlow<Long?>(null)
    private val selectedInventoryItemId = MutableStateFlow<Long?>(null)
    private val procurementLaunchAction = MutableStateFlow<ProcurementLaunchAction?>(null)
    private val purchasePriceControlInsights = MutableStateFlow<List<PurchasePriceInsight>>(emptyList())
    private val auditQuery = MutableStateFlow(AuditLogQuery())
    private val workspaceActive = MutableStateFlow(false)
    private val todayEpochDay = currentEpochDay()

    private val auditContent = auditQuery
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { query -> operationsRepository.auditLogs(query).map { logs -> query to logs } }

    private val inventoryCoreContent = combine(
        operationsRepository.suppliers,
        operationsRepository.inventoryLocations,
        operationsRepository.inventoryItems,
        operationsRepository.lowStockItems,
        operationsRepository.inventoryCounts,
    ) { suppliers, locations, items, lowStock, counts ->
        InventoryCoreContent(
            suppliers = suppliers,
            locations = locations,
            items = items,
            lowStock = lowStock,
            counts = counts,
        )
    }

    private val inventoryContent = combine(inventoryCoreContent, auditContent) { inventory, audit ->
        OperationsUiState(
            suppliers = inventory.suppliers,
            inventoryLocations = inventory.locations,
            inventoryItems = inventory.items,
            lowStockItems = inventory.lowStock,
            inventoryCounts = inventory.counts,
            auditQuery = audit.first,
            auditLogs = audit.second,
        )
    }

    private val intelligentInventoryContent = combine(
        inventoryContent,
        operationsRepository.usageInsights,
        operationsRepository.supplierPriceInsights,
        operationsRepository.wasteRecords,
        operationsRepository.inventoryPeriodClosures,
    ) { base, usage, prices, wastes, closures ->
        base.copy(
            usageInsights = usage,
            supplierPriceInsights = prices,
            wasteRecords = wastes,
            inventoryPeriodClosures = closures,
        )
    }

    private val inventoryWithMovements = combine(intelligentInventoryContent, operationsRepository.recentStockMovements) { base, movements ->
        base.copy(recentStockMovements = movements)
    }

    private val content = combine(
        inventoryWithMovements,
        purchaseSearch.debounce(250).distinctUntilChanged().flatMapLatest(operationsRepository::purchases),
        operationsRepository.purchases(""),
    ) { base, purchases, allPurchases ->
        base.copy(
            purchases = purchases,
            settlementAlerts = allPurchases.filter { it.reminderIsDue(todayEpochDay) },
            purchaseSearch = purchaseSearch.value,
        )
    }

    private val selectedPurchase = selectedPurchaseId.flatMapLatest { purchaseId ->
        if (purchaseId == null) flowOf(null) else purchaseRepository.details(purchaseId)
    }

    private val selectedInventoryClosure = selectedInventoryClosureId.flatMapLatest { closureId ->
        if (closureId == null) flowOf(null) else operationsRepository.inventoryPeriodClosureDetails(closureId)
    }
    private val selectedStockMovements = selectedInventoryItemId.flatMapLatest { itemId ->
        if (itemId == null) flowOf(emptyList()) else operationsRepository.stockMovements(itemId)
    }

    private data class SelectedContent(
        val base: OperationsUiState,
        val purchase: PurchaseDetails?,
        val inventoryClosure: InventoryPeriodClosureDetails?,
        val itemId: Long?,
        val movements: List<StockMovementRecord>,
    )

    private val activeContent = combine(workspaceActive, refreshRevision) { active, revision -> active to revision }
        .flatMapLatest { (active, _) -> if (active) content else flowOf(OperationsUiState()) }

    private val purchaseDashboard = purchasePeriod.flatMapLatest { period ->
        val range = DashboardPeriodRanges.currentRange(todayEpochDay, period, 0L to 0L)
        operationsRepository.purchaseDashboardSummary(range.fromEpochDay, range.toEpochDay, todayEpochDay)
            .map { PurchaseDashboardPresenter.present(period, it) }
            .onStart { emit(PurchaseDashboardPresenter.loading(period)) }
            .catch { emit(PurchaseDashboardPresenter.error(period)) }
    }

    private data class ProcurementDashboardBundle(
        val procurement: ProcurementOverview,
        val dashboard: PurchaseDashboardUi,
        val launchAction: ProcurementLaunchAction?,
        val priceInsights: List<PurchasePriceInsight>,
    )

    private val procurementDashboardBundle = combine(
        combine(workspaceActive, refreshRevision) { active, revision -> active to revision }.flatMapLatest { (active, _) -> if (active) procurementUseCases.overview else flowOf(ProcurementOverview()) },
        workspaceActive.flatMapLatest { active -> if (active) purchaseDashboard else flowOf(PurchaseDashboardPresenter.loading(purchasePeriod.value)) },
        procurementLaunchAction,
        purchasePriceControlInsights,
    ) { procurement, dashboard, launchAction, priceInsights ->
        ProcurementDashboardBundle(procurement, dashboard, launchAction, priceInsights)
    }

    private val selectedContent = combine(activeContent, selectedPurchase, selectedInventoryClosure, selectedInventoryItemId, selectedStockMovements) { base, purchase, closure, itemId, movements ->
        SelectedContent(base, purchase, closure, itemId, movements)
    }

    private data class OperationFlags(val busy: Boolean, val refreshing: Boolean, val message: String?)
    private val operationFlags = combine(busy, refreshing, message) { isBusy, isRefreshing, text -> OperationFlags(isBusy, isRefreshing, text) }

    val state: StateFlow<OperationsUiState> = combine(
        selectedContent,
        operationFlags,
        procurementDashboardBundle,
    ) { selected, flags, bundle ->
        selected.base.copy(
            busy = flags.busy,
            refreshing = flags.refreshing,
            message = flags.message,
            selectedPurchase = selected.purchase,
            selectedInventoryClosureDetails = selected.inventoryClosure,
            selectedInventoryItemId = selected.itemId,
            selectedStockMovements = selected.movements,
            procurement = bundle.procurement,
            purchaseDashboard = bundle.dashboard,
            procurementLaunchAction = bundle.launchAction,
            purchasePriceControlInsights = bundle.priceInsights,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OperationsUiState(),
    )

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            runCatching {
                refreshRevision.value = refreshRevision.value + 1L
                // Force real Room/procurement reads; this is not a decorative delay.
                operationsRepository.suppliers.first()
                operationsRepository.inventoryItems.first()
                procurementUseCases.overview.first()
            }.onSuccess {
                message.value = "اطلاعات از پایگاه داده تازه‌سازی شد."
            }.onFailure { failure ->
                message.value = UiErrorHandler.message("OperationsRefresh", failure)
            }
            refreshing.value = false
        }
    }

    fun searchPurchases(value: String) {
        purchaseSearch.value = value
    }

    fun purchaseToday() { purchasePeriod.value = DashboardPeriod.TODAY }
    fun purchaseWeek() { purchasePeriod.value = DashboardPeriod.WEEK }
    fun purchaseMonth() { purchasePeriod.value = DashboardPeriod.MONTH }

    fun loadPurchasePriceControl(branchId: Long, fromEpochDay: Long = todayEpochDay - 29, toEpochDay: Long = todayEpochDay) {
        viewModelScope.launch {
            purchasePriceControlInsights.value = runCatching {
                costControlReadService.purchasePriceInsights(branchId, fromEpochDay, toEpochDay)
            }.getOrElse { emptyList() }
        }
    }

    fun selectPurchase(purchaseId: Long?) {
        selectedPurchaseId.value = purchaseId
    }

    fun requestProcurementAction(action: ProcurementLaunchAction) {
        procurementLaunchAction.value = action
    }

    fun consumeProcurementAction(action: ProcurementLaunchAction) {
        if (procurementLaunchAction.value == action) procurementLaunchAction.value = null
    }

    fun selectInventoryClosure(closureId: Long?) {
        selectedInventoryClosureId.value = closureId
    }

    fun selectInventoryItem(itemId: Long?) {
        selectedInventoryItemId.value = itemId
    }

    fun clearMessage() {
        message.value = null
    }

    fun setAuditSearch(value: String) { auditQuery.value = auditQuery.value.copy(search = value.take(120)) }
    fun setAuditActor(value: String) { auditQuery.value = auditQuery.value.copy(actor = value.take(80)) }
    fun setAuditAction(value: String) { auditQuery.value = auditQuery.value.copy(action = value.take(40)) }
    fun setAuditEntity(value: String) { auditQuery.value = auditQuery.value.copy(entityType = value.take(60)) }
    fun setAuditEntityId(value: String) { auditQuery.value = auditQuery.value.copy(entityId = value.filter(Char::isDigit).take(18).toLongOrNull()) }
    fun setAuditSourceReference(value: String) { auditQuery.value = auditQuery.value.copy(sourceReference = value.take(100)) }
    fun setAuditSeverity(value: String) {
        require(value.isBlank() || value in setOf("INFO", "NOTICE", "WARNING", "CRITICAL"))
        auditQuery.value = auditQuery.value.copy(severity = value)
    }
    fun setWorkspaceActive(active: Boolean) { workspaceActive.value = active }
    fun setAuditDateRange(fromEpochDay: Long?, toEpochDay: Long?) {
        require(fromEpochDay == null || fromEpochDay > 0)
        require(toEpochDay == null || toEpochDay > 0)
        require(fromEpochDay == null || toEpochDay == null || toEpochDay >= fromEpochDay)
        auditQuery.value = auditQuery.value.copy(fromEpochDay = fromEpochDay, toEpochDay = toEpochDay)
    }
    fun clearAuditFilters() { auditQuery.value = AuditLogQuery() }

    fun saveSupplier(id: Long?, draft: SupplierDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (id == null) "تأمین‌کننده ثبت شد." else "تأمین‌کننده ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            inventoryOperations.saveSupplier(id, draft)
        }
    }

    fun deactivateSupplier(id: Long) {
        runAction("تأمین‌کننده غیرفعال شد.") {
            inventoryOperations.deactivateSupplier(id)
        }
    }

    fun mergeSupplier(draft: SupplierMergeDraft, onSuccess: () -> Unit = {}) {
        runAction("تأمین‌کننده تکراری به‌صورت کنترل‌شده ادغام شد.", onSuccess) {
            inventoryOperations.mergeSupplier(draft)
        }
    }

    fun saveInventoryItem(id: Long?, draft: InventoryItemDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (id == null) "کالا ثبت شد." else "کالا ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            inventoryOperations.saveInventoryItem(id, draft)
        }
    }

    fun postInventoryCount(draft: InventoryCountDraft, pin: String, onSuccess: () -> Unit = {}) {
        runAction("انبارگردانی ثبت و موجودی اصلاح شد.", onSuccess) {
            inventoryOperations.postInventoryCount(draft, pin)
        }
    }

    fun closeInventoryPeriod(draft: InventoryPeriodCloseDraft, pin: String, onSuccess: () -> Unit = {}) {
        runAction("دوره انبار بسته و دفتر گردش آن قفل شد.", onSuccess) {
            inventoryOperations.closeInventoryPeriod(draft, pin)
        }
    }

    fun reopenInventoryPeriod(closureId: Long, reason: String, pin: String, onSuccess: () -> Unit = {}) {
        runAction("دوره انبار بازگشایی شد؛ پس از اصلاح، آن را دوباره ببندید.", onSuccess) {
            inventoryOperations.reopenInventoryPeriod(closureId, reason, pin)
        }
    }

    fun postWaste(draft: WasteDraft, onSuccess: () -> Unit = {}) {
        runAction("ضایعات ثبت شد و انبار و حسابداری به‌روزرسانی شدند.", onSuccess) {
            inventoryOperations.postWaste(draft)
        }
    }

    fun deactivateInventoryItem(id: Long) {
        runAction("کالا غیرفعال شد.") {
            inventoryOperations.deactivateInventoryItem(id)
        }
    }

    fun postPurchase(draft: PurchaseDraft, onSuccess: (PostedPurchase) -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val result = procurementUseCases.postPurchase(draft)
                message.value = "فاکتور خرید ثبت شد و موجودی و حسابداری به‌روزرسانی شدند."
                onSuccess(result)
            } catch (error: Exception) {
                message.value = UiErrorHandler.message("OperationsViewModel.postPurchase", error)
            } finally {
                busy.value = false
            }
        }
    }

    fun submitRequisition(draft: PurchaseRequisitionDraft, onSuccess: () -> Unit = {}) {
        runAction("درخواست خرید برای تأیید ارسال شد.", onSuccess) {
            procurementUseCases.submitRequisition(draft)
        }
    }

    fun reviewRequisition(
        requisitionId: Long,
        approve: Boolean,
        note: String = "",
        onSuccess: () -> Unit = {},
    ) {
        runAction(if (approve) "درخواست خرید تأیید شد." else "درخواست خرید رد شد.", onSuccess) {
            procurementUseCases.reviewRequisition(requisitionId, approve, note)
        }
    }

    fun createPurchaseOrder(draft: PurchaseOrderDraft, onSuccess: () -> Unit = {}) {
        runAction("سفارش خرید از درخواست تأییدشده ساخته شد.", onSuccess) {
            procurementUseCases.createOrder(draft)
        }
    }

    fun createSplitPurchaseOrders(draft: SplitPurchaseOrdersDraft, onSuccess: () -> Unit = {}) {
        runAction("سفارش‌های خرید به تفکیک تأمین‌کننده ایجاد شدند.", onSuccess) {
            procurementUseCases.createSplitOrders(draft)
        }
    }

    fun markPurchaseOrderSent(orderId: Long, channel: PurchaseOrderDispatchChannel) {
        runAction("ارسال سفارش خرید ثبت شد.") { procurementUseCases.markOrderSent(orderId, channel) }
    }

    fun acknowledgePurchaseOrder(draft: PurchaseOrderAcknowledgementDraft, onSuccess: () -> Unit = {}) {
        runAction("تأیید تأمین‌کننده و موعد قطعی تحویل ثبت شد.", onSuccess) {
            procurementUseCases.acknowledge(draft)
        }
    }

    fun postGoodsReceipt(draft: GoodsReceiptDraft, onSuccess: () -> Unit = {}) {
        runAction("رسید کالا ثبت شد؛ فقط مقدار پذیرفته‌شده به موجودی اضافه شد.", onSuccess) {
            procurementUseCases.receive(draft)
        }
    }

    fun postPurchaseReturn(draft: PurchaseReturnDraft, onSuccess: () -> Unit = {}) {
        runAction("مرجوعی خرید ثبت شد؛ موجودی، بدهی/اعتبار تأمین‌کننده و حسابداری اصلاح شدند.", onSuccess) {
            procurementUseCases.returnGoods(draft)
        }
    }

    fun saveReplenishmentPolicy(draft: ReplenishmentPolicyDraft, onSuccess: () -> Unit = {}) {
        runAction("سیاست تأمین کالا ذخیره و پیشنهاد سفارش به‌روزرسانی شد.", onSuccess) {
            procurementUseCases.saveReplenishmentPolicy(draft)
        }
    }

    fun saveSupplierOffer(draft: SupplierOfferDraft, onSuccess: () -> Unit = {}) {
        runAction("پیشنهاد تأمین‌کننده ذخیره و مقایسه قیمت به‌روزرسانی شد.", onSuccess) {
            procurementUseCases.saveSupplierOffer(draft)
        }
    }

    fun submitSuggestedRequisition(itemIds: List<Long>, onSuccess: () -> Unit = {}) {
        runAction("پیشنهادهای تأمین به درخواست خرید قابل تأیید تبدیل شدند.", onSuccess) {
            procurementUseCases.submitSuggestedRequisition(itemIds)
        }
    }

    fun postMatchedInvoice(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
        approvePriceVariance: Boolean,
        onSuccess: () -> Unit = {},
    ) {
        runAction("فاکتور با سفارش و رسید تطبیق و ثبت مالی شد.", onSuccess) {
            procurementUseCases.postMatchedInvoice(purchaseOrderId, invoice, approvePriceVariance)
        }
    }

    fun settlePurchase(draft: PurchaseSettlementDraft, onSuccess: () -> Unit = {}) {
        runAction("تسویه ثبت شد و مانده تأمین‌کننده و سند حسابداری به‌روزرسانی شدند.", onSuccess) {
            procurementUseCases.settlePurchase(draft)
        }
    }

    fun reversePurchaseSettlement(draft: PurchaseSettlementReversalDraft, onSuccess: () -> Unit = {}) {
        runAction("تسویه برگشت خورد و مانده فاکتور با سند معکوس اصلاح شد.", onSuccess) {
            procurementUseCases.reverseSettlement(draft)
        }
    }

    fun reversePurchase(draft: PurchaseReversalDraft, onSuccess: () -> Unit = {}) {
        runAction("فاکتور برگشت خورد و موجودی و حسابداری با سند معکوس اصلاح شدند.", onSuccess) {
            procurementUseCases.reversePurchase(draft)
        }
    }

    private fun runAction(
        successMessage: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                block()
                message.value = successMessage
                onSuccess()
            } catch (error: Exception) {
                message.value = UiErrorHandler.message("OperationsViewModel", error)
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(
            operationsRepository: OperationsRepository,
            purchaseRepository: PurchaseRepository,
            procurementUseCases: ProcurementUseCases,
            inventoryOperations: OperationsInventoryUseCases,
            costControlReadService: CostControlReadService,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OperationsViewModel(operationsRepository, purchaseRepository, procurementUseCases, inventoryOperations, costControlReadService) as T
        }
    }
}
