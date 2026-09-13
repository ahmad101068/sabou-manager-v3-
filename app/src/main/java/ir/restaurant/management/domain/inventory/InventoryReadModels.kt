package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.SignedLongMath

/** Read-side stock classification. It is deliberately not persisted as a second source of truth. */
enum class InventoryStockStatus {
    ALL,
    HEALTHY,
    LOW,
    OUT_OF_STOCK,
}

data class InventoryDashboardSnapshot(
    val totalInventoryValueRial: Long,
    val activeItemCount: Int,
    val lowStockItemCount: Int,
    val outOfStockItemCount: Int,
    val expiringLotCount: Int,
    val expiredLotCount: Int,
    val quarantinedLotCount: Int,
    val wasteCostRial: Long,
    val inventoryVarianceRial: Long,
    val pendingTransferCount: Int,
    val pendingCountSessionCount: Int,
)

data class InventoryBalanceView(
    val itemId: Long,
    val itemName: String,
    val sku: String,
    val baseUnit: String,
    val locationId: Long,
    val locationName: String,
    val onHandMicros: Long,
    val reservedMicros: Long,
    val inTransitMicros: Long,
    val damagedMicros: Long,
    val quarantinedMicros: Long,
    val inventoryValueRial: Long,
    val reorderPointMicros: Long,
) {
    val availableMicros: Long
        get() = SignedLongMath.subtract(
            onHandMicros,
            SignedLongMath.add(reservedMicros, SignedLongMath.add(damagedMicros, quarantinedMicros)),
        )
}

data class InventoryBalanceQuery(
    val query: String = "",
    val locationId: Long? = null,
    val stockStatus: InventoryStockStatus = InventoryStockStatus.ALL,
    val includeInactive: Boolean = false,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryBalanceQuery {
        require(locationId == null || locationId > 0)
        require(limit in 1..200 && offset >= 0)
        return copy(query = query.trim().take(80))
    }
}

data class InventoryMovementView(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val baseUnit: String,
    val movementType: InventoryMovementType,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val unitCostRial: Long,
    val businessEpochDay: Long,
    val createdAtEpochMillis: Long,
    val locationId: Long?,
    val locationName: String?,
    val lotNumbers: String?,
    val sourceType: InventoryReferenceType,
    val sourceId: Long,
    val actorId: Long?,
    val correlationId: String,
    val reversalOfMovementId: Long?,
    val reason: String,
)

data class InventoryMovementQuery(
    val itemId: Long? = null,
    val locationId: Long? = null,
    val movementType: InventoryMovementType? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryMovementQuery {
        require(itemId == null || itemId > 0)
        require(locationId == null || locationId > 0)
        require(fromEpochDay == null || fromEpochDay > 0)
        require(toEpochDay == null || toEpochDay > 0)
        require(fromEpochDay == null || toEpochDay == null || fromEpochDay <= toEpochDay)
        require(limit in 1..200 && offset >= 0)
        return this
    }
}

/** Bounded read model over the immutable ledger and its rebuildable projections. */
interface InventoryReadService {
    suspend fun dashboard(
        asOfEpochDay: Long,
        expiryWindowDays: Int = 7,
        reportingWindowDays: Int = 30,
    ): InventoryDashboardSnapshot

    suspend fun balances(query: InventoryBalanceQuery): List<InventoryBalanceView>
    suspend fun movements(query: InventoryMovementQuery): List<InventoryMovementView>
}
