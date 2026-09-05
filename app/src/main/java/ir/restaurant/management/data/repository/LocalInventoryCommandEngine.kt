package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryLotConsumptionEntity
import ir.restaurant.management.data.db.StockMovementEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.common.businessRequire
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryCommandService
import ir.restaurant.management.domain.inventory.ReceiveInventoryCommand
import ir.restaurant.management.domain.inventory.IssueInventoryCommand
import ir.restaurant.management.domain.inventory.AdjustInventoryCommand
import ir.restaurant.management.domain.inventory.ReverseInventoryCommand
import ir.restaurant.management.domain.inventory.InventoryLedgerResult
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.FefoLotAllocator
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.LotAllocationCandidate
import ir.restaurant.management.domain.inventory.LotAllocationPurpose
import ir.restaurant.management.domain.inventory.LotAllocationRequest
import ir.restaurant.management.domain.inventory.WeightedAverageInventoryValuationService
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.data.security.AccessDeniedException

/**
 * Authoritative command boundary for inventory quantity/value mutations.
 *
 * Every command owns a Room transaction. Nested calls participate in the caller's transaction, so a
 * cross-domain workflow (document + ledger + accounting + audit) either commits in full or rolls back.
 * `inventory_items.stockMicros/inventoryValueRial` is a compare-and-set projection cache; immutable
 * `stock_movements` remains the auditable history.
 */
class LocalInventoryCommandEngine(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val authorizer: AuthorizationService,
) : InventoryCommandService {
    enum class LotIssuePolicy { NONE, FEFO_ALL, FEFO_ALLOCATED_ONLY }
    private val lotMovements = InventoryLotMovementService(database)
    private val dataScope = LocalDataScopeService(database, authorizer)

    override suspend fun receive(command: ReceiveInventoryCommand): InventoryLedgerResult = receive(
        itemId = command.itemId,
        quantityMicros = command.quantityMicros,
        valueRial = command.valueRial,
        movementType = command.movementType,
        referenceType = command.referenceType,
        referenceId = command.referenceId,
        movementEpochDay = command.businessEpochDay,
        context = command.context,
        notes = command.notes,
        lot = command.lot,
        enforceLotPolicy = true,
    )

    override suspend fun issue(command: IssueInventoryCommand): InventoryLedgerResult = issue(
        itemId = command.itemId,
        quantityMicros = command.quantityMicros,
        valueRial = command.valueRial,
        movementType = command.movementType,
        referenceType = command.referenceType,
        referenceId = command.referenceId,
        movementEpochDay = command.businessEpochDay,
        context = command.context,
        notes = command.notes,
        lotPolicy = if (command.allocateTrackedLots) LotIssuePolicy.FEFO_ALL else LotIssuePolicy.NONE,
        requestedLotId = command.lotId,
    )

    override suspend fun adjust(command: AdjustInventoryCommand): InventoryLedgerResult = adjustToCount(
        itemId = command.itemId,
        countedQuantityMicros = command.countedQuantityMicros,
        countedValueRial = command.countedValueRial,
        referenceId = command.referenceId,
        movementEpochDay = command.businessEpochDay,
        context = command.context,
        notes = command.notes,
    )

    override suspend fun reverse(command: ReverseInventoryCommand): InventoryLedgerResult {
        requireActorMatch(command.context, InventoryReferenceType.LEGACY_UNKNOWN, Permission.INVENTORY)
        val original = database.stockMovementDao().byId(command.originalMovementId)
            ?: throw BusinessError.EntityNotFound("STOCK_MOVEMENT", command.originalMovementId).asViolation()
        return restoreIssuedMovement(
            movement = original,
            reversalMovementType = command.reversalMovementType,
            reversalEpochDay = command.businessEpochDay,
            context = command.context,
            notes = command.notes,
        )
    }

    suspend fun receive(
        itemId: Long,
        quantityMicros: Long,
        valueRial: Long,
        movementType: InventoryMovementType,
        referenceType: InventoryReferenceType,
        referenceId: Long,
        movementEpochDay: Long,
        context: InventoryCommandContext,
        notes: String? = null,
        lot: InventoryReceiptLot? = null,
        @Suppress("UNUSED_PARAMETER") enforceLotPolicy: Boolean = false,
    ): InventoryLedgerResult = authorizedInventoryTransaction(
        movementEpochDay, context, referenceType, permissionsFor(referenceType, movementType),
    ) {
        requireCommandIdentity(movementType, referenceType, referenceId)
        businessRequire(quantityMicros > 0) {
            BusinessError.InvalidInput("quantityMicros", "مقدار ورود موجودی باید مثبت باشد.")
        }
        businessRequire(valueRial >= 0) {
            BusinessError.InvalidInput("valueRial", "ارزش ورود موجودی نمی‌تواند منفی باشد.")
        }
        val resolved = resolveContext(context)
        val unitCost = unitCost(valueRial, quantityMicros)
        val expected = movement(
            itemId = itemId,
            movementType = movementType,
            quantityDeltaMicros = quantityMicros,
            valueDeltaRial = valueRial,
            referenceType = referenceType,
            referenceId = referenceId,
            movementEpochDay = movementEpochDay,
            context = resolved,
            unitCostRial = unitCost,
            notes = notes,
        )
        replayOrConflict(expected)?.let { return@authorizedInventoryTransaction it }

        val item = database.inventoryDao().activeById(itemId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
        val validLot = when {
            item.trackLot && lot != null -> lot.validated(movementEpochDay, item.trackExpiry)
            item.trackLot -> throw BusinessError.InvalidLot(null, "LOT_REQUIRED").asViolation()
            lot != null -> throw BusinessError.InvalidBusinessState("INVENTORY_ITEM", "LOT_TRACKING_DISABLED").asViolation()
            else -> null
        }
        val locationId = requireNotNull(resolved.locationId)
        val balance = balanceForCommand(item, locationId)
        val nextStock = (QuantityMicros.of(item.stockMicros) + QuantityMicros.of(quantityMicros)).value
        val nextValue = (MoneyRial.of(item.inventoryValueRial) + MoneyRial.of(valueRial)).value
        val nextLocationStock = (QuantityMicros.of(balance.onHandMicros) + QuantityMicros.of(quantityMicros)).value
        val nextLocationValue = (MoneyRial.of(balance.inventoryValueRial) + MoneyRial.of(valueRial)).value
        val now = clock()
        val result = insert(expected.copy(createdAtEpochMillis = now))
        businessRequire(
            database.inventoryDao().compareAndSetValuation(
                itemId = item.id,
                expectedStockMicros = item.stockMicros,
                expectedInventoryValueRial = item.inventoryValueRial,
                nextStockMicros = nextStock,
                nextInventoryValueRial = nextValue,
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id) }
        updateBalance(
            balance = balance,
            nextOnHandMicros = nextLocationStock,
            nextInventoryValueRial = nextLocationValue,
            now = now,
        )
        validLot?.let { receivedLot ->
            lotMovements.receive(
                item = item,
                locationId = locationId,
                quantityMicros = quantityMicros,
                unitCostRial = unitCost,
                receivedEpochDay = movementEpochDay,
                referenceType = referenceType,
                referenceId = referenceId,
                context = resolved,
                lot = receivedLot,
                now = now,
            )
        }
        result
    }

    suspend fun issue(
        itemId: Long,
        quantityMicros: Long,
        valueRial: Long,
        movementType: InventoryMovementType,
        referenceType: InventoryReferenceType,
        referenceId: Long,
        movementEpochDay: Long,
        context: InventoryCommandContext,
        notes: String? = null,
        lotPolicy: LotIssuePolicy = LotIssuePolicy.FEFO_ALLOCATED_ONLY,
        requestedLotId: Long? = null,
    ): InventoryLedgerResult = authorizedInventoryTransaction(
        movementEpochDay, context, referenceType, permissionsFor(referenceType, movementType),
    ) {
        requireCommandIdentity(movementType, referenceType, referenceId)
        businessRequire(quantityMicros > 0) {
            BusinessError.InvalidInput("quantityMicros", "مقدار خروج موجودی باید مثبت باشد.")
        }
        businessRequire(valueRial >= 0) {
            BusinessError.InvalidInput("valueRial", "ارزش خروج موجودی نمی‌تواند منفی باشد.")
        }
        val resolved = resolveContext(context)
        val unitCost = unitCost(valueRial, quantityMicros)
        val expected = movement(
            itemId = itemId,
            movementType = movementType,
            quantityDeltaMicros = SignedLongMath.subtract(0L, quantityMicros),
            valueDeltaRial = SignedLongMath.subtract(0L, valueRial),
            referenceType = referenceType,
            referenceId = referenceId,
            movementEpochDay = movementEpochDay,
            context = resolved,
            unitCostRial = unitCost,
            notes = notes,
        )
        replayOrConflict(expected)?.let { return@authorizedInventoryTransaction it }

        val item = database.inventoryDao().activeById(itemId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
        val locationId = requireNotNull(resolved.locationId)
        val balance = balanceForCommand(item, locationId)
        val available = if (
            movementType in setOf(
                InventoryMovementType.WASTE,
                InventoryMovementType.INVENTORY_COUNT,
                InventoryMovementType.COUNT_VARIANCE,
            )
        ) {
            SignedLongMath.subtract(balance.onHandMicros, balance.reservedMicros)
        } else {
            val expiredMicros = if (item.trackLot) {
                database.inventoryLotDao().expiredAvailableQuantity(item.id, locationId, movementEpochDay)
            } else 0L
            SignedLongMath.subtract(availableMicros(balance), expiredMicros)
        }
        businessRequire(available >= quantityMicros) {
            BusinessError.InsufficientStock(item.id, item.name, quantityMicros, available)
        }
        val lotPlan = lotMovements.planIssue(
            item = item,
            locationId = locationId,
            quantityMicros = quantityMicros,
            movementEpochDay = movementEpochDay,
            movementType = movementType,
            lotPolicy = lotPolicy,
            balance = balance,
            requestedLotId = requestedLotId,
        )
        businessRequire(balance.inventoryValueRial >= valueRial) {
            BusinessError.InsufficientInventoryValue(item.id, item.name, valueRial, balance.inventoryValueRial)
        }
        businessRequire(item.stockMicros >= quantityMicros && item.inventoryValueRial >= valueRial) {
            BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id)
        }
        val now = clock()
        val result = insert(expected.copy(createdAtEpochMillis = now))
        businessRequire(
            database.inventoryDao().compareAndSetValuation(
                itemId = item.id,
                expectedStockMicros = item.stockMicros,
                expectedInventoryValueRial = item.inventoryValueRial,
                nextStockMicros = SignedLongMath.subtract(item.stockMicros, quantityMicros),
                nextInventoryValueRial = SignedLongMath.subtract(item.inventoryValueRial, valueRial),
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id) }
        updateBalance(
            balance = balance,
            nextOnHandMicros = SignedLongMath.subtract(balance.onHandMicros, quantityMicros),
            nextInventoryValueRial = SignedLongMath.subtract(balance.inventoryValueRial, valueRial),
            nextQuarantinedMicros = SignedLongMath.subtract(
                balance.quarantinedMicros,
                lotPlan.unavailableQuantityMicros,
            ),
            now = now,
        )
        lotMovements.applyIssue(lotPlan.allocations, result.movementId, now)
        result
    }

    suspend fun adjustToCount(
        itemId: Long,
        countedQuantityMicros: Long,
        countedValueRial: Long,
        referenceId: Long,
        movementEpochDay: Long,
        context: InventoryCommandContext,
        notes: String? = null,
    ): InventoryLedgerResult = authorizedInventoryTransaction(
        movementEpochDay, context, InventoryReferenceType.INVENTORY_COUNT, setOf(Permission.INVENTORY_COUNT_POST),
    ) {
        businessRequire(countedQuantityMicros >= 0) {
            BusinessError.InvalidInput("countedQuantityMicros", "موجودی شمارش‌شده نمی‌تواند منفی باشد.")
        }
        businessRequire(countedValueRial >= 0) {
            BusinessError.InvalidInput("countedValueRial", "ارزش شمارش‌شده نمی‌تواند منفی باشد.")
        }
        businessRequire(referenceId > 0) {
            BusinessError.InvalidInput("referenceId", "شناسه سند شمارش معتبر نیست.")
        }
        val resolved = resolveContext(context)
        val item = database.inventoryDao().activeById(itemId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
        val locationId = requireNotNull(resolved.locationId)
        val balance = balanceForCommand(item, locationId)
        val quantityDelta = SignedLongMath.subtract(countedQuantityMicros, balance.onHandMicros)
        val valueDelta = SignedLongMath.subtract(countedValueRial, balance.inventoryValueRial)
        businessRequire(quantityDelta != 0L || valueDelta != 0L) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "NO_VARIANCE")
        }
        val unitCost = if (quantityDelta == 0L) 0L else unitCost(absExact(valueDelta), absExact(quantityDelta))
        val expected = movement(
            itemId = item.id,
            movementType = InventoryMovementType.INVENTORY_COUNT,
            quantityDeltaMicros = quantityDelta,
            valueDeltaRial = valueDelta,
            referenceType = InventoryReferenceType.INVENTORY_COUNT,
            referenceId = referenceId,
            movementEpochDay = movementEpochDay,
            context = resolved,
            unitCostRial = unitCost,
            notes = notes,
        )
        replayOrConflict(expected)?.let { return@authorizedInventoryTransaction it }

        val now = clock()
        val nextAggregateStock = SignedLongMath.add(item.stockMicros, quantityDelta)
        val nextAggregateValue = SignedLongMath.add(item.inventoryValueRial, valueDelta)
        businessRequire(nextAggregateStock >= 0 && nextAggregateValue >= 0) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "NEGATIVE_AGGREGATE")
        }
        val lotPlan = if (quantityDelta < 0L) {
            lotMovements.planIssue(
                item = item,
                locationId = locationId,
                quantityMicros = absExact(quantityDelta),
                movementEpochDay = movementEpochDay,
                movementType = InventoryMovementType.INVENTORY_COUNT,
                lotPolicy = LotIssuePolicy.FEFO_ALLOCATED_ONLY,
                balance = balance,
            )
        } else {
            LotIssuePlan.EMPTY
        }
        val result = insert(expected.copy(createdAtEpochMillis = now))
        businessRequire(
            database.inventoryDao().compareAndSetValuation(
                itemId = item.id,
                expectedStockMicros = item.stockMicros,
                expectedInventoryValueRial = item.inventoryValueRial,
                nextStockMicros = nextAggregateStock,
                nextInventoryValueRial = nextAggregateValue,
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id) }
        updateBalance(
            balance = balance,
            nextOnHandMicros = countedQuantityMicros,
            nextInventoryValueRial = countedValueRial,
            nextQuarantinedMicros = SignedLongMath.subtract(
                balance.quarantinedMicros,
                lotPlan.unavailableQuantityMicros,
            ),
            now = now,
        )
        lotMovements.applyIssue(lotPlan.allocations, result.movementId, now)
        result
    }

    /**
     * Posts an approved physical count for a lot-controlled item. Every currently countable lot at
     * the location must be represented, so a positive variance can never create anonymous stock.
     * The single inventory movement carries the aggregate quantity/value variance while the count
     * document retains the per-lot evidence.
     */
    suspend fun adjustLotControlledCount(
        itemId: Long,
        countedLotQuantities: Map<Long, Long>,
        countedValueRial: Long,
        referenceId: Long,
        movementEpochDay: Long,
        context: InventoryCommandContext,
        notes: String? = null,
    ): InventoryLedgerResult = authorizedInventoryTransaction(
        movementEpochDay, context, InventoryReferenceType.INVENTORY_COUNT, setOf(Permission.INVENTORY_COUNT_POST),
    ) {
        businessRequire(referenceId > 0) {
            BusinessError.InvalidInput("referenceId", "شناسه سند شمارش معتبر نیست.")
        }
        businessRequire(countedValueRial >= 0) {
            BusinessError.InvalidInput("countedValueRial", "ارزش شمارش‌شده نمی‌تواند منفی باشد.")
        }
        businessRequire(countedLotQuantities.values.all { it >= 0L }) {
            BusinessError.InvalidInput("countedLotQuantities", "مقدار شمارش لات نمی‌تواند منفی باشد.")
        }
        val resolved = resolveContext(context)
        val locationId = requireNotNull(resolved.locationId)
        val item = database.inventoryDao().activeById(itemId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", itemId).asViolation()
        businessRequire(item.trackLot) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "LOT_CONTROL_NOT_ENABLED")
        }
        val balance = balanceForCommand(item, locationId)
        val currentLots = database.inventoryLotDao().countableAtLocation(item.id, locationId)
        val currentLotIds = currentLots.map { it.id }.toSet()
        businessRequire(countedLotQuantities.keys == currentLotIds) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "ALL_EXISTING_LOTS_MUST_BE_COUNTED")
        }
        val currentLotQuantity = currentLots.fold(0L) { sum, lot -> SignedLongMath.add(sum, lot.quantityMicros) }
        businessRequire(currentLotQuantity == balance.onHandMicros) {
            BusinessError.InvalidBusinessState("INVENTORY_LOT", "LOT_BALANCE_MISMATCH")
        }
        val countedQuantityMicros = countedLotQuantities.values.fold(0L) { sum, quantity ->
            SignedLongMath.add(sum, quantity)
        }
        val quantityDelta = SignedLongMath.subtract(countedQuantityMicros, balance.onHandMicros)
        val valueDelta = SignedLongMath.subtract(countedValueRial, balance.inventoryValueRial)
        businessRequire(quantityDelta != 0L || valueDelta != 0L) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "NO_VARIANCE")
        }
        val expected = movement(
            itemId = item.id,
            movementType = InventoryMovementType.INVENTORY_COUNT,
            quantityDeltaMicros = quantityDelta,
            valueDeltaRial = valueDelta,
            referenceType = InventoryReferenceType.INVENTORY_COUNT,
            referenceId = referenceId,
            movementEpochDay = movementEpochDay,
            context = resolved,
            unitCostRial = if (quantityDelta == 0L) 0L else unitCost(absExact(valueDelta), absExact(quantityDelta)),
            notes = notes,
        )
        replayOrConflict(expected)?.let { return@authorizedInventoryTransaction it }
        val nextAggregateStock = SignedLongMath.add(item.stockMicros, quantityDelta)
        val nextAggregateValue = SignedLongMath.add(item.inventoryValueRial, valueDelta)
        businessRequire(nextAggregateStock >= 0L && nextAggregateValue >= 0L) {
            BusinessError.InvalidBusinessState("INVENTORY_COUNT", "NEGATIVE_AGGREGATE")
        }
        val now = clock()
        val result = insert(expected.copy(createdAtEpochMillis = now))
        businessRequire(
            database.inventoryDao().compareAndSetValuation(
                itemId = item.id,
                expectedStockMicros = item.stockMicros,
                expectedInventoryValueRial = item.inventoryValueRial,
                nextStockMicros = nextAggregateStock,
                nextInventoryValueRial = nextAggregateValue,
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id) }
        businessRequire(
            countedQuantityMicros >= SignedLongMath.add(
                SignedLongMath.add(balance.reservedMicros, balance.damagedMicros),
                balance.quarantinedMicros,
            ),
        ) { BusinessError.InvalidBusinessState("INVENTORY_COUNT", "COUNT_BELOW_RESERVED_OR_UNAVAILABLE") }
        updateBalance(
            balance = balance,
            nextOnHandMicros = countedQuantityMicros,
            nextInventoryValueRial = countedValueRial,
            nextQuarantinedMicros = balance.quarantinedMicros,
            now = now,
        )
        currentLots.forEach { lot ->
            val nextQuantity = countedLotQuantities.getValue(lot.id)
            businessRequire(
                database.inventoryLotDao().compareAndSetCountQuantity(
                    id = lot.id,
                    expectedQuantityMicros = lot.quantityMicros,
                    nextQuantityMicros = nextQuantity,
                    updatedAtEpochMillis = now,
                ) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_LOT", lot.id) }
        }
        result
    }

    suspend fun issueTransferDocument(
        transferId: Long,
        businessEpochDay: Long,
        context: InventoryCommandContext,
    ) = authorizedInventoryTransaction(
        businessEpochDay, context, InventoryReferenceType.STOCK_TRANSFER, setOf(Permission.INVENTORY_TRANSFER_ISSUE),
    ) {
        val transferDao = database.inventoryTransferDao()
        val transfer = transferDao.transfer(transferId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_TRANSFER", transferId).asViolation()
        businessRequire(transfer.status == "APPROVED") {
            BusinessError.TransferNotApproved(transfer.id)
        }
        val resolved = resolveContext(context, locationOverride = transfer.sourceLocationId)
        val lines = transferDao.lines(transfer.id)
        businessRequire(lines.isNotEmpty()) {
            BusinessError.InvalidBusinessState("INVENTORY_TRANSFER", "NO_LINES")
        }
        val now = clock()
        lines.forEach { line ->
            val item = database.inventoryDao().activeById(line.itemId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", line.itemId).asViolation()
            val quantity = line.requestedQuantityMicros
            val sourceBalance = balanceForCommand(item, transfer.sourceLocationId)
            val destinationBalance = balanceForCommand(item, transfer.destinationLocationId)
            val sourceLot = line.lotId?.let { lotId ->
                database.inventoryLotDao().byId(lotId)
                    ?: throw BusinessError.InvalidLot(lotId, "LOT_NOT_FOUND").asViolation()
            }
            if (item.trackLot && sourceLot == null) {
                throw BusinessError.InvalidLot(null, "LOT_REQUIRED").asViolation()
            }
            if (sourceLot != null) {
                businessRequire(
                    sourceLot.itemId == item.id && sourceLot.locationId == transfer.sourceLocationId &&
                        InventoryLotStatus.requireKnown(sourceLot.status) == InventoryLotStatus.ACTIVE,
                ) { BusinessError.InvalidLot(sourceLot.id, "LOT_NOT_TRANSFERABLE") }
                if (sourceLot.expiryEpochDay != null && sourceLot.expiryEpochDay < businessEpochDay) {
                    throw BusinessError.LotExpired(sourceLot.id, sourceLot.expiryEpochDay).asViolation()
                }
                businessRequire(sourceLot.quantityMicros >= quantity) {
                    BusinessError.InsufficientStock(item.id, item.name, quantity, sourceLot.quantityMicros)
                }
            }
            // Lot identity constrains physical traceability; carrying value always follows source-location weighted-average.
            val value = WeightedAverageInventoryValuationService.issueValue(
                sourceBalance.onHandMicros,
                sourceBalance.inventoryValueRial,
                quantity,
            )
            val unitCost = unitCost(value, quantity)
            businessRequire(availableMicros(sourceBalance) >= quantity) {
                BusinessError.InsufficientStock(item.id, item.name, quantity, availableMicros(sourceBalance))
            }
            businessRequire(sourceBalance.inventoryValueRial >= value) {
                BusinessError.InsufficientInventoryValue(item.id, item.name, value, sourceBalance.inventoryValueRial)
            }
            val lineContext = resolved.copy(
                idempotencyKey = "${resolved.idempotencyKey}:line:${line.id}:out",
                locationId = transfer.sourceLocationId,
            ).validated()
            val movementId = insert(
                movement(
                    itemId = item.id,
                    movementType = InventoryMovementType.TRANSFER_OUT,
                    quantityDeltaMicros = SignedLongMath.subtract(0L, quantity),
                    valueDeltaRial = SignedLongMath.subtract(0L, value),
                    referenceType = InventoryReferenceType.STOCK_TRANSFER,
                    referenceId = transfer.id,
                    movementEpochDay = businessEpochDay,
                    context = lineContext,
                    unitCostRial = unitCost,
                    notes = transfer.note,
                ).copy(createdAtEpochMillis = now),
            ).movementId
            updateBalance(
                balance = sourceBalance,
                nextOnHandMicros = SignedLongMath.subtract(sourceBalance.onHandMicros, quantity),
                nextInventoryValueRial = SignedLongMath.subtract(sourceBalance.inventoryValueRial, value),
                now = now,
            )
            businessRequire(
                database.inventoryBalanceDao().compareAndSetInTransit(
                    itemId = item.id,
                    locationId = transfer.destinationLocationId,
                    expectedInTransitMicros = destinationBalance.inTransitMicros,
                    nextInTransitMicros = SignedLongMath.add(destinationBalance.inTransitMicros, quantity),
                    updatedAtEpochMillis = now,
                ) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", item.id) }
            if (sourceLot != null) {
                lotMovements.applyIssue(
                    listOf(
                        PlannedLotIssue(
                            lotId = sourceLot.id,
                            quantityMicros = quantity,
                            expectedQuantityMicros = sourceLot.quantityMicros,
                            unitCostRial = sourceLot.unitCostRial,
                            status = InventoryLotStatus.ACTIVE,
                        ),
                    ),
                    movementId,
                    now,
                )
            }
            businessRequire(
                transferDao.markLineIssued(transfer.id, line.id, quantity, unitCost, value, now) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER_LINE", line.id) }
        }
    }

    suspend fun receiveTransferDocument(
        transferId: Long,
        receivedQuantityByLineId: Map<Long, Long>,
        businessEpochDay: Long,
        context: InventoryCommandContext,
    ) = authorizedInventoryTransaction(
        businessEpochDay, context, InventoryReferenceType.STOCK_TRANSFER, setOf(Permission.INVENTORY_TRANSFER_RECEIVE),
    ) {
        val transferDao = database.inventoryTransferDao()
        val transfer = transferDao.transfer(transferId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_TRANSFER", transferId).asViolation()
        businessRequire(transfer.status == "IN_TRANSIT") {
            BusinessError.InvalidBusinessState("INVENTORY_TRANSFER", transfer.status)
        }
        val resolved = resolveContext(context, locationOverride = transfer.destinationLocationId)
        val lines = transferDao.lines(transfer.id)
        val now = clock()
        lines.forEach { line ->
            val issued = line.issuedQuantityMicros
                ?: throw BusinessError.InvalidBusinessState("INVENTORY_TRANSFER_LINE", "NOT_ISSUED").asViolation()
            val received = receivedQuantityByLineId[line.id]
                ?: throw BusinessError.InvalidInput("receivedQuantityByLineId", "مقدار دریافت همه ردیف‌ها الزامی است.").asViolation()
            if (received != issued) {
                throw BusinessError.TransferVarianceRequiresApproval(transfer.id, line.id, issued, received).asViolation()
            }
            val value = requireNotNull(line.valueRial)
            val unitCost = requireNotNull(line.unitCostRial)
            val item = database.inventoryDao().activeById(line.itemId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", line.itemId).asViolation()
            val destinationBalance = balanceForCommand(item, transfer.destinationLocationId)
            businessRequire(destinationBalance.inTransitMicros >= received) {
                BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER_IN_TRANSIT", line.id)
            }
            val lineContext = resolved.copy(
                idempotencyKey = "${resolved.idempotencyKey}:line:${line.id}:in",
                locationId = transfer.destinationLocationId,
            ).validated()
            insert(
                movement(
                    itemId = item.id,
                    movementType = InventoryMovementType.TRANSFER_IN,
                    quantityDeltaMicros = received,
                    valueDeltaRial = value,
                    referenceType = InventoryReferenceType.STOCK_TRANSFER,
                    referenceId = transfer.id,
                    movementEpochDay = businessEpochDay,
                    context = lineContext,
                    unitCostRial = unitCost,
                    notes = transfer.note,
                ).copy(createdAtEpochMillis = now),
            )
            businessRequire(
                database.inventoryBalanceDao().compareAndSetTransferReceipt(
                    itemId = item.id,
                    locationId = transfer.destinationLocationId,
                    expectedOnHandMicros = destinationBalance.onHandMicros,
                    expectedInventoryValueRial = destinationBalance.inventoryValueRial,
                    expectedInTransitMicros = destinationBalance.inTransitMicros,
                    nextOnHandMicros = SignedLongMath.add(destinationBalance.onHandMicros, received),
                    nextInventoryValueRial = SignedLongMath.add(destinationBalance.inventoryValueRial, value),
                    nextInTransitMicros = SignedLongMath.subtract(destinationBalance.inTransitMicros, received),
                    updatedAtEpochMillis = now,
                ) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", item.id) }
            line.lotId?.let { sourceLotId ->
                val sourceLot = database.inventoryLotDao().byId(sourceLotId)
                    ?: throw BusinessError.InvalidLot(sourceLotId, "LOT_NOT_FOUND").asViolation()
                val destinationLot = database.inventoryLotDao().byNaturalKey(
                    item.id,
                    transfer.destinationLocationId,
                    sourceLot.lotCode,
                )
                if (destinationLot == null) {
                    database.inventoryLotDao().insert(
                        sourceLot.copy(
                            id = 0,
                            globalId = GlobalId.new().value,
                            locationId = transfer.destinationLocationId,
                            quantityMicros = received,
                            initialQuantityMicros = received,
                            status = InventoryLotStatus.ACTIVE.storedValue,
                            correlationId = transfer.correlationId,
                            createdByActorId = resolved.actorId,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                } else {
                    businessRequire(
                        database.inventoryLotDao().addTransferredQuantity(
                            id = destinationLot.id,
                            expectedQuantityMicros = destinationLot.quantityMicros,
                            quantityMicros = received,
                            updatedAtEpochMillis = now,
                        ) == 1,
                    ) { BusinessError.ConcurrencyConflict("INVENTORY_LOT", destinationLot.id) }
                }
            }
            businessRequire(
                transferDao.markLineReceived(transfer.id, line.id, received, 0, now) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_TRANSFER_LINE", line.id) }
        }
    }

    suspend fun restoreIssuedMovement(
        movement: StockMovementEntity,
        reversalMovementType: InventoryMovementType,
        reversalEpochDay: Long,
        context: InventoryCommandContext,
        notes: String? = null,
    ): InventoryLedgerResult = authorizedInventoryTransaction(
        reversalEpochDay,
        context,
        InventoryReferenceType.fromStoredValue(movement.referenceType),
        setOf(permissionForReversal(movement)),
    ) {
        businessRequire(movement.quantityDeltaMicros < 0 && movement.valueDeltaRial <= 0) {
            BusinessError.InvalidBusinessState("STOCK_MOVEMENT", movement.movementType)
        }
        businessRequire(
            reversalMovementType !in setOf(
                InventoryMovementType.LEGACY_UNKNOWN,
                InventoryMovementType.LEGACY_SALE_CONSUMPTION,
            ),
        ) {
            BusinessError.InvalidInput("reversalMovementType", "نوع گردش برگشت معتبر نیست.")
        }
        val resolved = resolveContext(context, locationOverride = movement.locationId)
        val restoredQuantity = absExact(movement.quantityDeltaMicros)
        val restoredValue = absExact(movement.valueDeltaRial)
        val expected = movement(
            itemId = movement.itemId,
            movementType = reversalMovementType,
            quantityDeltaMicros = restoredQuantity,
            valueDeltaRial = restoredValue,
            referenceType = InventoryReferenceType.fromStoredValue(movement.referenceType),
            referenceId = movement.referenceId,
            movementEpochDay = reversalEpochDay,
            context = resolved,
            unitCostRial = movement.unitCostRial,
            notes = notes,
            reversalOfMovementId = movement.id,
        )
        replayOrConflict(expected)?.let { return@authorizedInventoryTransaction it }
        database.stockMovementDao().reversalOf(movement.id)?.let {
            throw BusinessError.IdempotencyConflict(resolved.idempotencyKey).asViolation()
        }

        val item = database.inventoryDao().byId(movement.itemId)
            ?: throw BusinessError.EntityNotFound("INVENTORY_ITEM", movement.itemId).asViolation()
        val balance = balanceForCommand(item, requireNotNull(resolved.locationId))
        val lotConsumptions = database.inventoryLotDao().consumptions(movement.id)
        val unavailableRestore = lotConsumptions.fold(0L) { total, consumption ->
            val remaining = SignedLongMath.subtract(consumption.quantityMicros, consumption.reversedQuantityMicros)
            val status = InventoryLotStatus.requireKnown(consumption.lotStatusSnapshot)
            if (status.isUnavailable) SignedLongMath.add(total, remaining) else total
        }
        val now = clock()
        val result = insert(expected.copy(createdAtEpochMillis = now))
        businessRequire(
            database.inventoryDao().compareAndRestoreValuation(
                itemId = item.id,
                expectedStockMicros = item.stockMicros,
                expectedInventoryValueRial = item.inventoryValueRial,
                nextStockMicros = SignedLongMath.add(item.stockMicros, restoredQuantity),
                nextInventoryValueRial = SignedLongMath.add(item.inventoryValueRial, restoredValue),
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrentModification("INVENTORY_ITEM", item.id) }
        updateBalance(
            balance = balance,
            nextOnHandMicros = SignedLongMath.add(balance.onHandMicros, restoredQuantity),
            nextInventoryValueRial = SignedLongMath.add(balance.inventoryValueRial, restoredValue),
            nextQuarantinedMicros = SignedLongMath.add(balance.quarantinedMicros, unavailableRestore),
            now = now,
        )

        lotConsumptions.forEach { consumption ->
            val restoreToLot = SignedLongMath.subtract(consumption.quantityMicros, consumption.reversedQuantityMicros)
            if (restoreToLot > 0) {
                val lot = database.inventoryLotDao().byId(consumption.lotId)
                    ?: throw BusinessError.EntityNotFound("INVENTORY_LOT", consumption.lotId).asViolation()
                check(
                    database.inventoryLotDao().restoreConsumedQuantity(
                        id = lot.id,
                        expectedQuantityMicros = lot.quantityMicros,
                        nextQuantityMicros = SignedLongMath.add(lot.quantityMicros, restoreToLot),
                        nextStatus = InventoryLotStatus.requireKnown(consumption.lotStatusSnapshot).storedValue,
                        updatedAtEpochMillis = now,
                    ) == 1,
                ) { "بازگردانی موجودی لات انجام نشد." }
                check(database.inventoryLotDao().markConsumptionReversed(consumption.id) == 1) {
                    "ثبت بازگشت مصرف لات انجام نشد."
                }
            }
        }
        result
    }

    /**
     * Restores only the lot-allocation projection for a partial commercial return. The caller must
     * create the corresponding positive stock movement via [receive] in the same Room transaction.
     */
    private suspend fun restoreTrackedLotQuantity(
        originalMovementId: Long,
        quantityMicros: Long,
    ) {
        businessRequire(quantityMicros > 0) {
            BusinessError.InvalidInput("quantityMicros", "مقدار برگشت لات باید مثبت باشد.")
        }
        val movement = database.stockMovementDao().byId(originalMovementId)
            ?: throw BusinessError.EntityNotFound("STOCK_MOVEMENT", originalMovementId).asViolation()
        businessRequire(movement.quantityDeltaMicros < 0) {
            BusinessError.InvalidBusinessState("STOCK_MOVEMENT", movement.movementType)
        }
        val consumptions = database.inventoryLotDao().consumptions(originalMovementId)
        if (consumptions.isEmpty()) return
        val available = consumptions.fold(0L) { total, row ->
            SignedLongMath.add(total, SignedLongMath.subtract(row.quantityMicros, row.reversedQuantityMicros))
        }
        businessRequire(quantityMicros <= available) {
            BusinessError.InvalidInput("quantityMicros", "مقدار برگشت از مصرف لات بیشتر است.")
        }
        var remaining = quantityMicros
        val now = clock()
        consumptions.forEach { consumption ->
            if (remaining <= 0) return@forEach
            val availableInLot = SignedLongMath.subtract(consumption.quantityMicros, consumption.reversedQuantityMicros)
            val restore = minOf(remaining, availableInLot)
            if (restore <= 0) return@forEach
            val lot = database.inventoryLotDao().byId(consumption.lotId)
                ?: throw BusinessError.EntityNotFound("INVENTORY_LOT", consumption.lotId).asViolation()
            val nextLotQuantity = SignedLongMath.add(lot.quantityMicros, restore)
            businessRequire(
                database.inventoryLotDao().restoreConsumedQuantity(
                    id = lot.id,
                    expectedQuantityMicros = lot.quantityMicros,
                    nextQuantityMicros = nextLotQuantity,
                    nextStatus = InventoryLotStatus.requireKnown(consumption.lotStatusSnapshot).storedValue,
                    updatedAtEpochMillis = now,
                ) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_LOT", lot.id) }
            businessRequire(
                database.inventoryLotDao().advanceConsumptionReversal(
                    id = consumption.id,
                    expectedReversedQuantityMicros = consumption.reversedQuantityMicros,
                    nextReversedQuantityMicros = SignedLongMath.add(consumption.reversedQuantityMicros, restore),
                ) == 1,
            ) { BusinessError.ConcurrencyConflict("INVENTORY_LOT_CONSUMPTION", consumption.id) }
            remaining = SignedLongMath.subtract(remaining, restore)
        }
        check(remaining == 0L) { "بازگردانی لات فروش کامل نشد." }
    }

    private suspend fun <T> authorizedInventoryTransaction(
        movementEpochDay: Long,
        context: InventoryCommandContext,
        referenceType: InventoryReferenceType,
        permissions: Set<Permission>,
        block: suspend () -> T,
    ): T {
        authorizeCommand(context, referenceType, permissions)
        return inventoryTransaction(movementEpochDay, block)
    }

    private suspend fun authorizeCommand(
        context: InventoryCommandContext,
        referenceType: InventoryReferenceType,
        permissions: Set<Permission>,
    ) {
        require(permissions.isNotEmpty()) { "inventory_command_permission_missing" }
        permissions.forEach { authorizer.require(it) }
        requireActorMatch(context, referenceType, permissions.first())
    }

    private suspend fun requireActorMatch(
        context: InventoryCommandContext,
        referenceType: InventoryReferenceType,
        permission: Permission,
    ) {
        val valid = context.validated()
        val actor = authorizer.actorIdentity()
        if (actor.id != valid.actorId) {
            LocalAuditEventWriter(database).appendAuthorized(
                authorizer = authorizer,
                action = "ACCESS_DENIED",
                entityType = "INVENTORY_COMMAND",
                entityId = null,
                description = "رد عملیات موجودی به علت عدم تطابق actor",
                occurredAtEpochMillis = clock(),
                reason = "ACTOR_MISMATCH:session=${actor.id};context=${valid.actorId};source=${referenceType.storedValue}",
                correlationId = valid.correlationId,
                referenceType = null,
                referenceId = null,
                deviceId = valid.deviceId,
            )
            throw AccessDeniedException(permission)
        }
    }

    private fun permissionsFor(
        referenceType: InventoryReferenceType,
        movementType: InventoryMovementType,
    ): Set<Permission> = buildSet {
        add(referencePermission(referenceType, movementType))
        add(movementPermission(movementType))
    }

    private fun referencePermission(
        referenceType: InventoryReferenceType,
        movementType: InventoryMovementType,
    ): Permission = when (referenceType) {
        InventoryReferenceType.PURCHASE,
        InventoryReferenceType.GOODS_RECEIPT,
        InventoryReferenceType.PURCHASE_RETURN -> Permission.PURCHASES
        InventoryReferenceType.DAILY_SALES -> if (movementType == InventoryMovementType.DAILY_SALES_REVERSAL) {
            Permission.DAILY_SALES_VOID
        } else {
            Permission.DAILY_SALES_POST
        }
        InventoryReferenceType.SALES_INVOICE -> Permission.DAILY_SALES_POST
        InventoryReferenceType.SALES_RETURN,
        InventoryReferenceType.SALES_VOID -> Permission.DAILY_SALES_VOID
        InventoryReferenceType.INVENTORY_COUNT -> Permission.INVENTORY_COUNT_POST
        InventoryReferenceType.WASTE -> Permission.INVENTORY_WASTE_CREATE
        InventoryReferenceType.STOCK_TRANSFER -> if (movementType == InventoryMovementType.TRANSFER_IN) {
            Permission.INVENTORY_TRANSFER_RECEIVE
        } else {
            Permission.INVENTORY_TRANSFER_ISSUE
        }
        InventoryReferenceType.INVENTORY_ADJUSTMENT -> Permission.INVENTORY_ADJUST
        InventoryReferenceType.RECIPE -> Permission.RECIPES
        InventoryReferenceType.PRODUCTION -> Permission.INVENTORY_ADJUST
        InventoryReferenceType.MIGRATION -> Permission.RESTORE
        InventoryReferenceType.LEGACY_UNKNOWN -> Permission.INVENTORY_ADJUST
    }

    private fun movementPermission(movementType: InventoryMovementType): Permission = when (movementType) {
        InventoryMovementType.PURCHASE,
        InventoryMovementType.PURCHASE_REVERSAL,
        InventoryMovementType.GOODS_RECEIPT,
        InventoryMovementType.PURCHASE_RETURN -> Permission.PURCHASES
        InventoryMovementType.DAILY_SALES_CONSUMPTION,
        InventoryMovementType.SALES_INVOICE_CONSUMPTION -> Permission.DAILY_SALES_POST
        InventoryMovementType.DAILY_SALES_REVERSAL,
        InventoryMovementType.SALES_RETURN,
        InventoryMovementType.SALES_VOID -> Permission.DAILY_SALES_VOID
        InventoryMovementType.RECIPE_CONSUMPTION -> Permission.RECIPES
        InventoryMovementType.PRODUCTION_OUTPUT,
        InventoryMovementType.INVENTORY_ADJUSTMENT -> Permission.INVENTORY_ADJUST
        InventoryMovementType.INVENTORY_COUNT,
        InventoryMovementType.COUNT_VARIANCE -> Permission.INVENTORY_COUNT_POST
        InventoryMovementType.WASTE -> Permission.INVENTORY_WASTE_CREATE
        InventoryMovementType.TRANSFER_IN -> Permission.INVENTORY_TRANSFER_RECEIVE
        InventoryMovementType.TRANSFER_OUT -> Permission.INVENTORY_TRANSFER_ISSUE
        InventoryMovementType.OPENING_BALANCE -> Permission.RESTORE
        InventoryMovementType.REVERSAL -> Permission.INVENTORY_ADJUST
        InventoryMovementType.LEGACY_SALE_CONSUMPTION,
        InventoryMovementType.LEGACY_UNKNOWN -> Permission.INVENTORY_ADJUST
    }

    private fun permissionForReversal(movement: StockMovementEntity): Permission = when (
        InventoryReferenceType.fromStoredValue(movement.referenceType)
    ) {
        InventoryReferenceType.DAILY_SALES,
        InventoryReferenceType.SALES_INVOICE,
        InventoryReferenceType.SALES_RETURN,
        InventoryReferenceType.SALES_VOID -> Permission.DAILY_SALES_VOID
        InventoryReferenceType.PURCHASE,
        InventoryReferenceType.GOODS_RECEIPT,
        InventoryReferenceType.PURCHASE_RETURN -> Permission.PURCHASES
        InventoryReferenceType.INVENTORY_COUNT -> Permission.INVENTORY_COUNT_POST
        InventoryReferenceType.WASTE -> Permission.INVENTORY_WASTE_CREATE
        InventoryReferenceType.STOCK_TRANSFER -> if (
            InventoryMovementType.fromStoredValue(movement.movementType) == InventoryMovementType.TRANSFER_IN
        ) {
            Permission.INVENTORY_TRANSFER_RECEIVE
        } else {
            Permission.INVENTORY_TRANSFER_ISSUE
        }
        InventoryReferenceType.INVENTORY_ADJUSTMENT -> Permission.INVENTORY_ADJUST
        InventoryReferenceType.RECIPE -> Permission.RECIPES
        InventoryReferenceType.PRODUCTION -> Permission.INVENTORY_ADJUST
        InventoryReferenceType.MIGRATION -> Permission.RESTORE
        InventoryReferenceType.LEGACY_UNKNOWN -> Permission.INVENTORY_ADJUST
    }

    private suspend fun resolveContext(
        context: InventoryCommandContext,
        locationOverride: Long? = null,
    ): InventoryCommandContext {
        val valid = context.validated()
        val locationId = locationOverride ?: valid.locationId
            ?: throw BusinessError.InvalidInput("locationId", "انبار/مکان نگهداری باید صریح انتخاب شود.").asViolation()
        dataScope.requireLocation(locationId)
        return valid.copy(locationId = locationId)
    }

    private suspend fun balanceForCommand(
        item: InventoryItemEntity,
        locationId: Long,
    ): InventoryBalanceEntity {
        val dao = database.inventoryBalanceDao()
        dao.byKey(item.id, locationId)?.let { return it }
        val now = clock()
        val firstLocationProjection = dao.countForItem(item.id) == 0
        dao.initialize(
            InventoryBalanceEntity(
                itemId = item.id,
                locationId = locationId,
                // Legacy aggregate inventory can only be assigned when the caller supplied this location explicitly.
                onHandMicros = if (firstLocationProjection) item.stockMicros else 0L,
                inventoryValueRial = if (firstLocationProjection) item.inventoryValueRial else 0L,
                updatedAtEpochMillis = now,
            ),
        )
        return dao.byKey(item.id, locationId)
            ?: throw BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", item.id).asViolation()
    }

    private suspend fun updateBalance(
        balance: InventoryBalanceEntity,
        nextOnHandMicros: Long,
        nextInventoryValueRial: Long,
        nextQuarantinedMicros: Long = balance.quarantinedMicros,
        now: Long,
    ) {
        businessRequire(
            database.inventoryBalanceDao().compareAndSetOnHand(
                itemId = balance.itemId,
                locationId = balance.locationId,
                expectedOnHandMicros = balance.onHandMicros,
                expectedInventoryValueRial = balance.inventoryValueRial,
                expectedQuarantinedMicros = balance.quarantinedMicros,
                nextOnHandMicros = nextOnHandMicros,
                nextInventoryValueRial = nextInventoryValueRial,
                nextQuarantinedMicros = nextQuarantinedMicros,
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrencyConflict("INVENTORY_BALANCE", balance.itemId) }
    }

    private fun availableMicros(balance: InventoryBalanceEntity): Long = SignedLongMath.subtract(
        SignedLongMath.subtract(
            SignedLongMath.subtract(balance.onHandMicros, balance.reservedMicros),
            balance.damagedMicros,
        ),
        balance.quarantinedMicros,
    )

    private fun requireCommandIdentity(
        movementType: InventoryMovementType,
        referenceType: InventoryReferenceType,
        referenceId: Long,
    ) {
        businessRequire(
            movementType !in setOf(
                InventoryMovementType.LEGACY_UNKNOWN,
                InventoryMovementType.LEGACY_SALE_CONSUMPTION,
            ),
        ) {
            BusinessError.InvalidInput("movementType", "نوع گردش موجودی معتبر نیست.")
        }
        businessRequire(referenceType != InventoryReferenceType.LEGACY_UNKNOWN) {
            BusinessError.InvalidInput("referenceType", "نوع مرجع موجودی معتبر نیست.")
        }
        businessRequire(referenceId > 0) {
            BusinessError.InvalidInput("referenceId", "شناسه مرجع موجودی معتبر نیست.")
        }
    }

    private fun movement(
        itemId: Long,
        movementType: InventoryMovementType,
        quantityDeltaMicros: Long,
        valueDeltaRial: Long,
        referenceType: InventoryReferenceType,
        referenceId: Long,
        movementEpochDay: Long,
        context: InventoryCommandContext,
        unitCostRial: Long,
        notes: String?,
        reversalOfMovementId: Long? = null,
    ): StockMovementEntity = StockMovementEntity(
        itemId = itemId,
        movementType = movementType.storedValue,
        quantityDeltaMicros = quantityDeltaMicros,
        valueDeltaRial = valueDeltaRial,
        referenceType = referenceType.storedValue,
        referenceId = referenceId,
        movementEpochDay = movementEpochDay,
        notes = notes?.trim().orEmpty().ifBlank { context.reason },
        createdAtEpochMillis = 0L,
        globalId = GlobalId.new().value,
        idempotencyKey = context.idempotencyKey,
        correlationId = context.correlationId,
        actorId = context.actorId,
        deviceId = context.deviceId,
        locationId = requireNotNull(context.locationId),
        unitCostRial = unitCostRial,
        reasonCode = context.reasonCode.storedValue,
        reversalOfMovementId = reversalOfMovementId,
    )

    private suspend fun replayOrConflict(expected: StockMovementEntity): InventoryLedgerResult? {
        val existing = database.stockMovementDao().byIdempotencyKey(expected.idempotencyKey) ?: return null
        val samePayload = existing.itemId == expected.itemId &&
            existing.movementType == expected.movementType &&
            existing.quantityDeltaMicros == expected.quantityDeltaMicros &&
            existing.valueDeltaRial == expected.valueDeltaRial &&
            existing.referenceType == expected.referenceType &&
            existing.referenceId == expected.referenceId &&
            existing.movementEpochDay == expected.movementEpochDay &&
            existing.notes == expected.notes &&
            existing.correlationId == expected.correlationId &&
            existing.actorId == expected.actorId &&
            existing.deviceId == expected.deviceId &&
            existing.locationId == expected.locationId &&
            existing.unitCostRial == expected.unitCostRial &&
            existing.reasonCode == expected.reasonCode &&
            existing.reversalOfMovementId == expected.reversalOfMovementId
        if (!samePayload) throw BusinessError.IdempotencyConflict(expected.idempotencyKey).asViolation()
        return InventoryLedgerResult(existing.id, existing.globalId, idempotentReplay = true)
    }

    private suspend fun insert(entity: StockMovementEntity): InventoryLedgerResult {
        val movementId = database.stockMovementDao().insert(entity)
        return InventoryLedgerResult(movementId, entity.globalId, idempotentReplay = false)
    }

    private fun unitCost(valueRial: Long, quantityMicros: Long): Long =
        if (valueRial == 0L) 0L else FixedPointRatio.unitCostRial(valueRial, quantityMicros)

    private suspend fun <T> inventoryTransaction(
        businessEpochDay: Long,
        block: suspend () -> T,
    ): T = try {
        database.withTransaction { block() }
    } catch (error: Throwable) {
        throw mapDatabaseBusinessFailure(error, businessEpochDay)
    }

    private fun absExact(value: Long): Long {
        require(value != Long.MIN_VALUE) { "مقدار از محدوده امن خارج است." }
        return kotlin.math.abs(value)
    }

    private fun InventoryMovementType.canDisposeExpiredLots(): Boolean = this in setOf(
        InventoryMovementType.PURCHASE_REVERSAL,
        InventoryMovementType.PURCHASE_RETURN,
        InventoryMovementType.INVENTORY_COUNT,
        InventoryMovementType.WASTE,
    )
}
