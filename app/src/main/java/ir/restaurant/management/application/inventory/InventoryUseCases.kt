package ir.restaurant.management.application.inventory

import ir.restaurant.management.domain.inventory.*

/**
 * Application boundary for Inventory 2.0.
 *
 * The UI never coordinates repositories/services directly. This layer owns command validation,
 * normalization and lifecycle orchestration while the underlying domain services own persistence,
 * permissions, transactions and audit.
 */
class InventoryUseCases(
    private val master: InventoryRepository,
    private val commands: InventoryCommandService,
    private val counts: InventoryCountService,
    private val lots: InventoryLotService,
    private val waste: InventoryWasteService,
    private val transfers: InventoryTransferService,
    private val replenishment: InventoryReplenishmentService,
    private val reads: InventoryReadService,
    private val integrity: InventoryIntegrityService,
) {
    val locations get() = master.locations

    suspend fun item(id: Long): InventoryItemMasterRecord {
        require(id > 0) { "شناسه کالا معتبر نیست." }
        return master.item(id)
    }

    suspend fun searchItems(search: InventoryItemSearch) = master.searchItems(search.validated())

    suspend fun itemByBarcode(barcode: String): InventoryItemMasterRecord? {
        val normalized = barcode.trim()
        require(normalized.length in 3..80) { "بارکد معتبر نیست." }
        return master.itemByBarcode(normalized)
    }

    suspend fun saveItem(id: Long?, draft: InventoryItemMasterDraft) = master.saveItem(id, draft.validated())

    suspend fun deactivateItem(id: Long) {
        require(id > 0) { "شناسه کالا معتبر نیست." }
        master.deactivateItem(id)
    }

    suspend fun searchLocations(search: InventoryLocationSearch) = master.searchLocations(search.validated())
    suspend fun saveLocation(id: Long?, draft: InventoryLocationDraft) = master.saveLocation(id, draft.validated())
    suspend fun defaultLocationId() = master.defaultLocationId()

    suspend fun receive(command: ReceiveInventoryCommand) = commands.receive(command)
    suspend fun issue(command: IssueInventoryCommand) = commands.issue(command)
    suspend fun adjust(command: AdjustInventoryCommand) = commands.adjust(command)
    suspend fun reverse(command: ReverseInventoryCommand) = commands.reverse(command)

    suspend fun searchCounts(query: InventoryCountSearch) = counts.search(query.validated())
    suspend fun createCount(command: CreateInventoryCountSessionCommand) = counts.create(command.validated())
    suspend fun openCount(command: InventoryCountActionCommand) = counts.open(command)
    suspend fun recordCount(command: RecordInventoryCountCommand) = counts.record(command)
    suspend fun submitCount(command: InventoryCountActionCommand) = counts.submit(command)
    suspend fun approveCount(command: InventoryCountActionCommand) = counts.approve(command)
    suspend fun cancelCount(command: InventoryCountActionCommand) = counts.cancel(command)
    suspend fun postCount(command: PostInventoryCountCommand) = counts.post(command)
    suspend fun countSession(id: Long): InventoryCountSession {
        require(id > 0) { "شناسه جلسه انبارگردانی معتبر نیست." }
        return counts.session(id)
    }
    suspend fun countLines(sessionId: Long, canReviewVariance: Boolean): List<InventoryCountLineView> {
        require(sessionId > 0) { "شناسه جلسه انبارگردانی معتبر نیست." }
        return counts.lines(sessionId, canReviewVariance)
    }

    suspend fun searchLots(query: InventoryLotSearch) = lots.search(query.validated())
    suspend fun registerLot(command: RegisterInventoryLotCommand) = lots.register(command)
    suspend fun changeLotStatus(command: ChangeInventoryLotStatusCommand) = lots.changeStatus(command)

    suspend fun searchWaste(query: InventoryWasteSearch) = waste.search(query.validated())
    suspend fun submitWaste(command: CreateWasteCommand) = waste.submit(command.validated())
    suspend fun submitAndPostWaste(command: CreateWasteCommand) = waste.submitAndPost(command.validated())
    suspend fun approveWaste(command: WasteActionCommand) = waste.approve(command)
    suspend fun postWaste(command: PostWasteCommand) = waste.post(command)

    suspend fun searchTransfers(query: InventoryTransferSearch) = transfers.search(query.validated())
    suspend fun createTransfer(command: CreateInventoryTransferCommand) = transfers.create(command.validated())
    suspend fun approveTransfer(command: TransferActionCommand) = transfers.approve(command.validated())
    suspend fun issueTransfer(command: TransferActionCommand) = transfers.issue(command.validated())
    suspend fun receiveTransfer(command: ReceiveInventoryTransferCommand) = transfers.receive(command.validated())
    suspend fun createAndCompleteTransfer(command: CreateInventoryTransferCommand) = transfers.createAndComplete(command.validated())

    suspend fun replenishmentRecommendations(query: InventoryReplenishmentQuery) = replenishment.recommendations(query.validated())

    suspend fun dashboard(
        asOfEpochDay: Long,
        expiryWindowDays: Int = 7,
        reportingWindowDays: Int = 30,
    ): InventoryDashboardSnapshot {
        require(asOfEpochDay > 0) { "تاریخ گزارش انبار معتبر نیست." }
        require(expiryWindowDays in 1..365 && reportingWindowDays in 1..365) { "بازه گزارش انبار معتبر نیست." }
        return reads.dashboard(asOfEpochDay, expiryWindowDays, reportingWindowDays)
    }

    suspend fun balances(query: InventoryBalanceQuery) = reads.balances(query.validated())
    suspend fun movements(query: InventoryMovementQuery) = reads.movements(query.validated())
    suspend fun verifyIntegrity() = integrity.verify()
}
