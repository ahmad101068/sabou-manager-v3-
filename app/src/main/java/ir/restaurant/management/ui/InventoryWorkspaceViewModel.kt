package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.application.inventory.InventoryUseCases
import ir.restaurant.management.application.procurement.ProcurementUseCases
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.inventory.ChangeInventoryLotStatusCommand
import ir.restaurant.management.domain.inventory.CreateInventoryCountSessionCommand
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.CreateWasteCommand
import ir.restaurant.management.domain.inventory.InventoryBalanceQuery
import ir.restaurant.management.domain.inventory.InventoryBalanceView
import ir.restaurant.management.domain.inventory.InventoryCountActionCommand
import ir.restaurant.management.domain.inventory.InventoryCountLineView
import ir.restaurant.management.domain.inventory.InventoryCountScope
import ir.restaurant.management.domain.inventory.InventoryCountSearch
import ir.restaurant.management.domain.inventory.InventoryCountSession
import ir.restaurant.management.domain.inventory.InventoryDashboardSnapshot
import ir.restaurant.management.domain.inventory.InventoryItemMasterDraft
import ir.restaurant.management.domain.inventory.InventoryItemMasterRecord
import ir.restaurant.management.domain.inventory.InventoryItemSearch
import ir.restaurant.management.domain.inventory.InventoryLocationDraft
import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.inventory.InventoryLocationSearch
import ir.restaurant.management.domain.inventory.InventoryLot
import ir.restaurant.management.domain.inventory.InventoryLotDraft
import ir.restaurant.management.domain.inventory.InventoryLotSearch
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryMovementQuery
import ir.restaurant.management.domain.inventory.InventoryMovementView
import ir.restaurant.management.domain.inventory.InventoryReplenishmentQuery
import ir.restaurant.management.domain.inventory.InventoryReplenishmentRecommendation
import ir.restaurant.management.domain.inventory.InventoryStockStatus
import ir.restaurant.management.domain.inventory.InventoryTransferDocument
import ir.restaurant.management.domain.inventory.InventoryTransferSearch
import ir.restaurant.management.domain.inventory.PostInventoryCountCommand
import ir.restaurant.management.domain.inventory.PostWasteCommand
import ir.restaurant.management.domain.inventory.ReceiveInventoryTransferCommand
import ir.restaurant.management.domain.inventory.RecordInventoryCountCommand
import ir.restaurant.management.domain.inventory.RegisterInventoryLotCommand
import ir.restaurant.management.domain.inventory.TransferActionCommand
import ir.restaurant.management.domain.inventory.InventoryWasteDocument
import ir.restaurant.management.domain.inventory.InventoryWasteSearch
import ir.restaurant.management.domain.inventory.WasteActionCommand
import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class InventoryWorkspaceSection {
    OVERVIEW,
    ITEMS,
    EXPIRY,
    MOVEMENTS,
    COUNTS,
    WASTE,
    TRANSFERS,
    REPLENISHMENT,
    PERIODS,
}

enum class InventoryWorkspaceAction {
    CREATE_ITEM,
    CREATE_TRANSFER,
    CREATE_COUNT,
    CREATE_WASTE,
}

internal fun InventoryWorkspaceAction.section(): InventoryWorkspaceSection = when (this) {
    InventoryWorkspaceAction.CREATE_ITEM -> InventoryWorkspaceSection.ITEMS
    InventoryWorkspaceAction.CREATE_TRANSFER -> InventoryWorkspaceSection.TRANSFERS
    InventoryWorkspaceAction.CREATE_COUNT -> InventoryWorkspaceSection.COUNTS
    InventoryWorkspaceAction.CREATE_WASTE -> InventoryWorkspaceSection.WASTE
}

internal data class InventoryLoadPlan(
    val dashboard: Boolean = false,
    val items: Boolean = false,
    val balances: Boolean = false,
    val locations: Boolean = false,
    val lots: Boolean = false,
    val counts: Boolean = false,
    val waste: Boolean = false,
    val transfers: Boolean = false,
    val replenishment: Boolean = false,
    val movementsLimit: Int? = null,
)

internal object InventoryLoadPlanner {
    fun forSection(section: InventoryWorkspaceSection): InventoryLoadPlan = when (section) {
        InventoryWorkspaceSection.OVERVIEW -> InventoryLoadPlan(
            dashboard = true,
            locations = true,
            replenishment = true,
            movementsLimit = 5,
        )
        InventoryWorkspaceSection.ITEMS -> InventoryLoadPlan(items = true, balances = true, locations = true)
        InventoryWorkspaceSection.EXPIRY -> InventoryLoadPlan(items = true, locations = true, lots = true)
        InventoryWorkspaceSection.MOVEMENTS -> InventoryLoadPlan(locations = true, movementsLimit = 100)
        InventoryWorkspaceSection.COUNTS -> InventoryLoadPlan(items = true, locations = true, counts = true)
        InventoryWorkspaceSection.WASTE -> InventoryLoadPlan(items = true, locations = true, lots = true, waste = true)
        InventoryWorkspaceSection.TRANSFERS -> InventoryLoadPlan(items = true, locations = true, lots = true, transfers = true)
        InventoryWorkspaceSection.REPLENISHMENT -> InventoryLoadPlan(locations = true, replenishment = true)
        InventoryWorkspaceSection.PERIODS -> InventoryLoadPlan()
    }
}

data class InventoryWorkspaceUiState(
    val section: InventoryWorkspaceSection = InventoryWorkspaceSection.OVERVIEW,
    val currentUser: AppUserRecord? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val dashboard: InventoryDashboardSnapshot? = null,
    val items: List<InventoryItemMasterRecord> = emptyList(),
    val balances: List<InventoryBalanceView> = emptyList(),
    val locations: List<InventoryLocationRecord> = emptyList(),
    val lots: List<InventoryLot> = emptyList(),
    val countSessions: List<InventoryCountSession> = emptyList(),
    val selectedCountSession: InventoryCountSession? = null,
    val selectedCountLines: List<InventoryCountLineView> = emptyList(),
    val wasteDocuments: List<InventoryWasteDocument> = emptyList(),
    val transfers: List<InventoryTransferDocument> = emptyList(),
    val replenishment: List<InventoryReplenishmentRecommendation> = emptyList(),
    val movements: List<InventoryMovementView> = emptyList(),
    val query: String = "",
    val locationId: Long? = null,
    val stockStatus: InventoryStockStatus = InventoryStockStatus.ALL,
    val focusedItemId: Long? = null,
    val pendingAction: InventoryWorkspaceAction? = null,
)

/** Coordinates Inventory 2.0 workflows; Compose only renders this state and emits intents. */
class InventoryWorkspaceViewModel(
    private val inventory: InventoryUseCases,
    private val procurement: ProcurementUseCases,
    private val security: SecurityRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InventoryWorkspaceUiState())
    val state: StateFlow<InventoryWorkspaceUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            security.currentUser.collect { user ->
                mutableState.update { it.copy(currentUser = user) }
                if (user != null) refresh()
            }
        }
    }

    fun selectSection(section: InventoryWorkspaceSection) {
        mutableState.update { it.copy(section = section, message = null) }
        // Operational lists are snapshots, not live Flows. Refresh when entering a section
        // so data created by another workflow/process is visible without requiring a manual tap.
        refresh()
    }


    fun launchAction(action: InventoryWorkspaceAction) {
        mutableState.update {
            it.copy(
                section = action.section(),
                pendingAction = action,
                message = null,
            )
        }
        refresh()
    }

    fun consumeAction(action: InventoryWorkspaceAction) {
        mutableState.update { current ->
            if (current.pendingAction == action) current.copy(pendingAction = null) else current
        }
    }

    fun openItems(
        query: String = mutableState.value.query,
        stockStatus: InventoryStockStatus = mutableState.value.stockStatus,
        locationId: Long? = mutableState.value.locationId,
    ) {
        mutableState.update {
            it.copy(
                section = InventoryWorkspaceSection.ITEMS,
                query = query.take(80),
                stockStatus = stockStatus,
                locationId = locationId,
                focusedItemId = null,
                message = null,
            )
        }
        refresh()
    }
    fun setQuery(value: String) {
        mutableState.update { it.copy(query = value.take(80), focusedItemId = null) }
    }

    fun setLocation(locationId: Long?) {
        mutableState.update { it.copy(locationId = locationId) }
        refresh()
    }

    fun setStockStatus(status: InventoryStockStatus) {
        mutableState.update { it.copy(stockStatus = status) }
        refresh()
    }

    fun search() = refresh()

    fun focusItem(itemId: Long, section: InventoryWorkspaceSection = InventoryWorkspaceSection.ITEMS) {
        viewModelScope.launch {
            runCatching { inventory.item(itemId) }
                .onSuccess { item ->
                    mutableState.update { it.copy(query = item.sku.value, focusedItemId = item.id, section = section) }
                    refresh()
                }
                .onFailure { failure ->
                    mutableState.update { it.copy(message = UiErrorHandler.message("InventoryItemDrillDown", failure)) }
                }
        }
    }

    fun refresh() {
        if (mutableState.value.currentUser == null) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, message = null) }
            runCatching {
                val current = mutableState.value
                val today = currentEpochDay()
                val locationId = current.locationId
                val plan = InventoryLoadPlanner.forSection(current.section)
                InventoryWorkspacePatch(
                    dashboard = if (plan.dashboard) inventory.dashboard(today, expiryWindowDays = 60) else null,
                    items = if (plan.items) inventory.searchItems(InventoryItemSearch(query = current.query, limit = PAGE_SIZE)) else null,
                    balances = if (plan.balances) inventory.balances(
                        InventoryBalanceQuery(
                            query = current.query,
                            locationId = locationId,
                            stockStatus = current.stockStatus,
                            limit = PAGE_SIZE,
                        ),
                    ) else null,
                    locations = if (plan.locations) inventory.searchLocations(InventoryLocationSearch(limit = PAGE_SIZE)) else null,
                    lots = if (plan.lots) inventory.searchLots(InventoryLotSearch(locationId = locationId, limit = PAGE_SIZE)) else null,
                    countSessions = if (plan.counts) inventory.searchCounts(InventoryCountSearch(locationId = locationId, limit = PAGE_SIZE)) else null,
                    wastes = if (plan.waste) inventory.searchWaste(
                        InventoryWasteSearch(
                            locationId = locationId,
                            fromEpochDay = (today - 365).coerceAtLeast(1),
                            toEpochDay = today,
                            limit = PAGE_SIZE,
                        ),
                    ) else null,
                    transfers = if (plan.transfers) inventory.searchTransfers(InventoryTransferSearch(locationId = locationId, limit = PAGE_SIZE)) else null,
                    replenishment = if (plan.replenishment) inventory.replenishmentRecommendations(
                        InventoryReplenishmentQuery(
                            locationId = locationId,
                            asOfEpochDay = today,
                            actionableOnly = current.section == InventoryWorkspaceSection.OVERVIEW,
                            limit = if (current.section == InventoryWorkspaceSection.OVERVIEW) OVERVIEW_REPLENISHMENT_LIMIT else PAGE_SIZE,
                        ),
                    ) else null,
                    movements = plan.movementsLimit?.let { limit ->
                        inventory.movements(
                            InventoryMovementQuery(
                                itemId = current.focusedItemId,
                                locationId = locationId,
                                limit = limit,
                            ),
                        )
                    },
                )
            }.onSuccess { data ->
                mutableState.update { current ->
                    current.copy(
                        loading = false,
                        dashboard = data.dashboard ?: current.dashboard,
                        items = data.items ?: current.items,
                        balances = data.balances ?: current.balances,
                        locations = data.locations ?: current.locations,
                        lots = data.lots ?: current.lots,
                        countSessions = data.countSessions ?: current.countSessions,
                        wasteDocuments = data.wastes ?: current.wasteDocuments,
                        transfers = data.transfers ?: current.transfers,
                        replenishment = data.replenishment ?: current.replenishment,
                        movements = data.movements ?: current.movements,
                    )
                }
            }.onFailure { failure ->
                mutableState.update {
                    it.copy(loading = false, message = UiErrorHandler.message("InventoryWorkspaceViewModel", failure))
                }
            }
        }
    }

    fun saveItem(id: Long?, draft: InventoryItemMasterDraft, done: () -> Unit = {}) =
        runAction("اطلاعات کالای انبار ذخیره شد.", done) { inventory.saveItem(id, draft) }

    fun deactivateItem(id: Long) = runAction("کالا غیرفعال شد.") { inventory.deactivateItem(id) }

    fun saveLocation(id: Long?, draft: InventoryLocationDraft, done: () -> Unit = {}) =
        runAction("محل نگهداری ذخیره شد.", done) { inventory.saveLocation(id, draft) }

    fun registerLot(draft: InventoryLotDraft, reason: String, done: () -> Unit = {}) =
        runActorAction("لات کالا ثبت شد.", done) { actorId ->
            inventory.registerLot(RegisterInventoryLotCommand(draft, actorId, reason))
        }

    fun changeLotStatus(
        lot: InventoryLot,
        nextStatus: InventoryLotStatus,
        reason: String,
    ) = runActorAction("وضعیت لات به‌روزرسانی شد.") { actorId ->
        inventory.changeLotStatus(
            ChangeInventoryLotStatusCommand(
                lotId = lot.id,
                expectedStatus = lot.status,
                nextStatus = nextStatus,
                businessEpochDay = currentEpochDay(),
                actorId = actorId,
                reason = reason,
            ),
        )
    }

    fun createCount(
        locationId: Long,
        itemIds: Set<Long>,
        blindCount: Boolean,
        notes: String,
        done: () -> Unit = {},
    ) = runAction("جلسه انبارگردانی ایجاد شد.", done) {
        inventory.createCount(
            CreateInventoryCountSessionCommand(
                locationId = locationId,
                scope = if (itemIds.isEmpty()) InventoryCountScope.ALL_LOCATION else InventoryCountScope.ITEM_SELECTION,
                itemIds = itemIds,
                blindCount = blindCount,
                businessEpochDay = currentEpochDay(),
                notes = notes,
            ),
        )
    }

    fun selectCount(session: InventoryCountSession?, canReviewVariance: Boolean) {
        mutableState.update { it.copy(selectedCountSession = session, selectedCountLines = emptyList()) }
        if (session != null) viewModelScope.launch {
            runCatching { inventory.countLines(session.id, canReviewVariance) }
                .onSuccess { lines -> mutableState.update { it.copy(selectedCountLines = lines) } }
                .onFailure { failure ->
                    mutableState.update { it.copy(message = UiErrorHandler.message("InventoryCountCenter", failure)) }
                }
        }
    }

    fun openCount(sessionId: Long) = countAction(sessionId, "جلسه شمارش باز شد.") { command -> inventory.openCount(command) }

    fun recordCount(lineId: Long, quantityMicros: Long, unitCostRial: Long?, reason: String) {
        val session = mutableState.value.selectedCountSession ?: return
        runActorAction("مقدار شمارش ثبت شد.") { actorId ->
            inventory.recordCount(
                RecordInventoryCountCommand(session.id, lineId, quantityMicros, unitCostRial, reason, actorId),
            )
            val canReview = mutableState.value.currentUser?.role?.allows(
                ir.restaurant.management.domain.security.Permission.INVENTORY_COUNT_APPROVE,
            ) == true
            val updatedSession = inventory.countSession(session.id)
            val updatedLines = inventory.countLines(session.id, canReview)
            mutableState.update {
                it.copy(selectedCountSession = updatedSession, selectedCountLines = updatedLines)
            }
        }
    }

    fun submitCount(sessionId: Long) = countAction(sessionId, "جلسه برای بررسی ارسال شد.") { inventory.submitCount(it) }
    fun approveCount(sessionId: Long) = countAction(sessionId, "انبارگردانی تأیید شد.") { inventory.approveCount(it) }
    fun cancelCount(sessionId: Long) = countAction(sessionId, "جلسه انبارگردانی لغو شد.") { inventory.cancelCount(it) }
    fun postCount(sessionId: Long) = runActorAction("مغایرت انبارگردانی به دفترکل ثبت شد.") { actorId ->
        inventory.postCount(PostInventoryCountCommand(sessionId, actorId))
    }

    fun submitWaste(command: CreateWasteCommand, done: () -> Unit = {}) =
        runAction("سند ضایعات برای ثبت آماده شد.", done) { inventory.submitWaste(command) }

    fun approveWaste(id: Long, reason: String) = runActorAction("سند ضایعات تأیید شد.") { actorId ->
        inventory.approveWaste(WasteActionCommand(id, actorId, reason))
    }

    fun postWaste(id: Long) = runActorAction("ضایعات به دفترکل موجودی ثبت شد.") { actorId ->
        inventory.postWaste(PostWasteCommand(id, actorId))
    }

    fun createTransfer(command: CreateInventoryTransferCommand, done: () -> Unit = {}) =
        runAction("درخواست انتقال ایجاد شد.", done) { inventory.createTransfer(command) }

    fun approveTransfer(id: Long, reason: String) = transferAction(id, "انتقال تأیید شد.", reason) {
        inventory.approveTransfer(it)
    }

    fun issueTransfer(id: Long, reason: String) = transferAction(id, "کالا از مبدأ صادر و در راه ثبت شد.", reason) {
        inventory.issueTransfer(it)
    }

    fun receiveTransfer(document: InventoryTransferDocument, reason: String) =
        runActorAction("انتقال در مقصد دریافت شد.") { actorId ->
            inventory.receiveTransfer(
                ReceiveInventoryTransferCommand(
                    transferId = document.id,
                    actorId = actorId,
                    businessEpochDay = currentEpochDay(),
                    receivedQuantityByLineId = document.lines.associate { line ->
                        line.id to requireNotNull(line.issuedQuantityMicros)
                    },
                    reason = reason,
                ),
            )
        }

    fun submitReplenishment(itemIds: List<Long>) = runAction("پیشنهادها به درخواست خرید ارسال شدند.") {
        procurement.submitSuggestedRequisition(itemIds.distinct().take(100))
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun countAction(
        sessionId: Long,
        success: String,
        block: suspend (InventoryCountActionCommand) -> Unit,
    ) = runActorAction(success) { actorId ->
        block(InventoryCountActionCommand(sessionId, actorId, "تغییر وضعیت کنترل‌شده انبارگردانی"))
        if (mutableState.value.selectedCountSession?.id == sessionId) {
            val canReview = mutableState.value.currentUser?.role?.allows(
                ir.restaurant.management.domain.security.Permission.INVENTORY_COUNT_APPROVE,
            ) == true
            val updatedSession = inventory.countSession(sessionId)
            val updatedLines = inventory.countLines(sessionId, canReview)
            mutableState.update {
                it.copy(
                    selectedCountSession = updatedSession,
                    selectedCountLines = updatedLines,
                )
            }
        }
    }

    private fun transferAction(
        transferId: Long,
        success: String,
        reason: String,
        block: suspend (TransferActionCommand) -> InventoryTransferDocument,
    ) = runActorAction(success) { actorId ->
        block(TransferActionCommand(transferId, actorId, currentEpochDay(), reason, GlobalId.new().value))
    }

    private fun runActorAction(
        success: String,
        done: () -> Unit = {},
        block: suspend (Long) -> Unit,
    ) {
        val actorId = mutableState.value.currentUser?.id ?: return
        runAction(success, done) { block(actorId) }
    }

    private fun runAction(success: String, done: () -> Unit = {}, block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onSuccess {
                    mutableState.update { state -> state.copy(busy = false, message = success) }
                    done()
                    refresh()
                }
                .onFailure { failure ->
                    mutableState.update {
                        it.copy(busy = false, message = UiErrorHandler.message("InventoryWorkspaceViewModel", failure))
                    }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val OVERVIEW_REPLENISHMENT_LIMIT = 20

        fun factory(
            inventory: InventoryUseCases,
            procurement: ProcurementUseCases,
            security: SecurityRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = InventoryWorkspaceViewModel(
                inventory,
                procurement,
                security,
            ) as T
        }
    }
}

private data class InventoryWorkspacePatch(
    val dashboard: InventoryDashboardSnapshot? = null,
    val items: List<InventoryItemMasterRecord>? = null,
    val balances: List<InventoryBalanceView>? = null,
    val locations: List<InventoryLocationRecord>? = null,
    val lots: List<InventoryLot>? = null,
    val countSessions: List<InventoryCountSession>? = null,
    val wastes: List<InventoryWasteDocument>? = null,
    val transfers: List<InventoryTransferDocument>? = null,
    val replenishment: List<InventoryReplenishmentRecommendation>? = null,
    val movements: List<InventoryMovementView>? = null,
)
