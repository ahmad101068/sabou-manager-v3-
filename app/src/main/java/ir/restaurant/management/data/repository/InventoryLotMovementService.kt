package ir.restaurant.management.data.repository

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryLotConsumptionEntity
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.common.businessRequire
import ir.restaurant.management.domain.inventory.FefoLotAllocator
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.LotAllocationCandidate
import ir.restaurant.management.domain.inventory.LotAllocationPurpose
import ir.restaurant.management.domain.inventory.LotAllocationRequest

internal data class PlannedLotIssue(
    val lotId: Long,
    val quantityMicros: Long,
    val expectedQuantityMicros: Long,
    val unitCostRial: Long,
    val status: InventoryLotStatus,
)

internal data class LotIssuePlan(
    val allocations: List<PlannedLotIssue>,
    val unavailableQuantityMicros: Long,
) {
    companion object {
        val EMPTY = LotIssuePlan(emptyList(), 0L)
    }
}

/**
 * Exclusive boundary for lot allocation and lot quantity mutations used by the inventory ledger.
 * The caller owns the surrounding Room transaction; compare-and-set quantity updates keep FEFO
 * allocation safe against concurrent inventory commands.
 */
internal class InventoryLotMovementService(private val database: AppDatabase) {
    suspend fun planIssue(
        item: InventoryItemEntity,
        locationId: Long,
        quantityMicros: Long,
        movementEpochDay: Long,
        movementType: InventoryMovementType,
        lotPolicy: LocalInventoryCommandEngine.LotIssuePolicy,
        balance: InventoryBalanceEntity,
        requestedLotId: Long? = null,
    ): LotIssuePlan {
        if (!item.trackLot || lotPolicy == LocalInventoryCommandEngine.LotIssuePolicy.NONE || quantityMicros == 0L) {
            return LotIssuePlan.EMPTY
        }
        val lotDao = database.inventoryLotDao()
        val lotQuantity = lotDao.allocatedQuantityAtLocation(item.id, locationId)
        val unallocatedStock = SignedLongMath.subtract(balance.onHandMicros, lotQuantity).coerceAtLeast(0L)
        val requiredFromLots = when (lotPolicy) {
            LocalInventoryCommandEngine.LotIssuePolicy.NONE -> 0L
            LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALL -> quantityMicros
            LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALLOCATED_ONLY ->
                SignedLongMath.subtract(quantityMicros, unallocatedStock).coerceAtLeast(0L)
        }
        if (requiredFromLots == 0L) return LotIssuePlan.EMPTY

        val entities = if (requestedLotId == null) {
            lotDao.allocationCandidates(item.id, locationId)
        } else {
            val requested = lotDao.byId(requestedLotId)
                ?: throw BusinessError.InvalidLot(requestedLotId, "LOT_NOT_FOUND").asViolation()
            businessRequire(requested.itemId == item.id && requested.locationId == locationId) {
                BusinessError.InvalidLot(requestedLotId, "LOT_ITEM_LOCATION_MISMATCH")
            }
            listOf(requested)
        }
        val purpose = when (movementType) {
            InventoryMovementType.WASTE,
            InventoryMovementType.INVENTORY_COUNT,
            InventoryMovementType.COUNT_VARIANCE,
            -> LotAllocationPurpose.DISPOSAL

            InventoryMovementType.PURCHASE_RETURN,
            InventoryMovementType.PURCHASE_REVERSAL,
            -> LotAllocationPurpose.SUPPLIER_RETURN

            else -> LotAllocationPurpose.NORMAL_CONSUMPTION
        }
        val result = FefoLotAllocator.allocate(
            LotAllocationRequest(
                itemId = item.id,
                locationId = locationId,
                requiredQuantityMicros = requiredFromLots,
                businessEpochDay = movementEpochDay,
                trackExpiry = item.trackExpiry,
                purpose = purpose,
            ),
            entities.map { lot ->
                LotAllocationCandidate(
                    lotId = lot.id,
                    locationId = lot.locationId,
                    receivedEpochDay = lot.receivedEpochDay,
                    expiryEpochDay = lot.expiryEpochDay,
                    availableQuantityMicros = lot.quantityMicros,
                    unitCostRial = lot.unitCostRial,
                    status = InventoryLotStatus.fromStoredValue(lot.status),
                )
            },
        )
        businessRequire(result.isComplete) {
            BusinessError.InsufficientStock(item.id, item.name, requiredFromLots, result.allocatedQuantityMicros)
        }
        val byId = entities.associateBy { it.id }
        val planned = result.allocations.map { allocation ->
            val lot = requireNotNull(byId[allocation.lotId])
            PlannedLotIssue(
                lotId = lot.id,
                quantityMicros = allocation.quantityMicros,
                expectedQuantityMicros = lot.quantityMicros,
                unitCostRial = lot.unitCostRial,
                status = InventoryLotStatus.fromStoredValue(lot.status),
            )
        }
        val unavailable = planned.filter { it.status.isUnavailable }.fold(0L) { total, lot ->
            SignedLongMath.add(total, lot.quantityMicros)
        }
        return LotIssuePlan(planned, unavailable)
    }

    suspend fun applyIssue(allocations: List<PlannedLotIssue>, movementId: Long, now: Long) {
        allocations.forEach { allocation ->
            check(
                database.inventoryLotDao().compareAndSetQuantity(
                    id = allocation.lotId,
                    expectedQuantityMicros = allocation.expectedQuantityMicros,
                    expectedStatus = allocation.status.storedValue,
                    nextQuantityMicros = SignedLongMath.subtract(
                        allocation.expectedQuantityMicros,
                        allocation.quantityMicros,
                    ),
                    updatedAtEpochMillis = now,
                ) == 1,
            ) { "کاهش موجودی لات انجام نشد." }
            database.inventoryLotDao().insertConsumption(
                InventoryLotConsumptionEntity(
                    stockMovementId = movementId,
                    lotId = allocation.lotId,
                    quantityMicros = allocation.quantityMicros,
                    unitCostRial = allocation.unitCostRial,
                    lotStatusSnapshot = allocation.status.storedValue,
                ),
            )
        }
    }

    suspend fun receive(
        item: InventoryItemEntity,
        locationId: Long,
        quantityMicros: Long,
        unitCostRial: Long,
        receivedEpochDay: Long,
        referenceType: InventoryReferenceType,
        referenceId: Long,
        context: InventoryCommandContext,
        lot: InventoryReceiptLot,
        now: Long,
    ) {
        val dao = database.inventoryLotDao()
        val existing = dao.byNaturalKey(item.id, locationId, lot.lotNumber)
        if (existing == null) {
            dao.insert(
                ir.restaurant.management.data.db.InventoryLotEntity(
                    globalId = GlobalId.new().value,
                    itemId = item.id,
                    locationId = locationId,
                    lotCode = lot.lotNumber,
                    supplierLotNumber = lot.supplierLotNumber,
                    receivedEpochDay = receivedEpochDay,
                    productionEpochDay = lot.productionEpochDay,
                    expiryEpochDay = lot.expiryEpochDay,
                    quantityMicros = quantityMicros,
                    initialQuantityMicros = quantityMicros,
                    unitCostRial = unitCostRial,
                    status = InventoryLotStatus.ACTIVE.storedValue,
                    barcode = lot.barcode,
                    sourceReceiptId = referenceId.takeIf { referenceType == InventoryReferenceType.GOODS_RECEIPT },
                    correlationId = context.correlationId,
                    createdByActorId = context.actorId,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            return
        }
        val status = InventoryLotStatus.requireKnown(existing.status)
        val sameIdentity = existing.supplierLotNumber == lot.supplierLotNumber &&
            existing.productionEpochDay == lot.productionEpochDay &&
            existing.expiryEpochDay == lot.expiryEpochDay &&
            existing.barcode == lot.barcode &&
            existing.unitCostRial == unitCostRial
        businessRequire(sameIdentity && status in setOf(InventoryLotStatus.ACTIVE, InventoryLotStatus.DEPLETED)) {
            BusinessError.InvalidLot(existing.id, "LOT_IDENTITY_CONFLICT")
        }
        businessRequire(
            dao.addTransferredQuantity(
                id = existing.id,
                expectedQuantityMicros = existing.quantityMicros,
                quantityMicros = quantityMicros,
                updatedAtEpochMillis = now,
            ) == 1,
        ) { BusinessError.ConcurrencyConflict("INVENTORY_LOT", existing.id) }
    }
}
