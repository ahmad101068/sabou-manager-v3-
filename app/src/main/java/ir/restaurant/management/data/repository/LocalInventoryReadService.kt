package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.domain.inventory.InventoryBalanceQuery
import ir.restaurant.management.domain.inventory.InventoryBalanceView
import ir.restaurant.management.domain.inventory.InventoryDashboardSnapshot
import ir.restaurant.management.domain.inventory.InventoryMovementQuery
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryMovementView
import ir.restaurant.management.domain.inventory.InventoryReadService
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission

/** Database-backed, bounded query boundary. It never mutates ledger or projection state. */
class LocalInventoryReadService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
) : InventoryReadService {
    override suspend fun dashboard(
        asOfEpochDay: Long,
        expiryWindowDays: Int,
        reportingWindowDays: Int,
    ): InventoryDashboardSnapshot {
        authorizer.require(Permission.INVENTORY_VIEW)
        require(asOfEpochDay > 0 && expiryWindowDays in 1..365 && reportingWindowDays in 1..366)
        val row = database.inventoryReadDao().dashboard(
            asOfEpochDay = asOfEpochDay,
            expiryToEpochDay = Math.addExact(asOfEpochDay, expiryWindowDays.toLong()),
            reportingFromEpochDay = Math.subtractExact(asOfEpochDay, reportingWindowDays.toLong() - 1L),
        )
        return InventoryDashboardSnapshot(
            totalInventoryValueRial = row.totalInventoryValueRial,
            activeItemCount = row.activeItemCount,
            lowStockItemCount = row.lowStockItemCount,
            outOfStockItemCount = row.outOfStockItemCount,
            expiringLotCount = row.expiringLotCount,
            expiredLotCount = row.expiredLotCount,
            quarantinedLotCount = row.quarantinedLotCount,
            wasteCostRial = row.wasteCostRial,
            inventoryVarianceRial = row.inventoryVarianceRial,
            pendingTransferCount = row.pendingTransferCount,
            pendingCountSessionCount = row.pendingCountSessionCount,
        )
    }

    override suspend fun balances(query: InventoryBalanceQuery): List<InventoryBalanceView> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        return database.inventoryReadDao().balances(
            query = valid.query,
            locationId = valid.locationId,
            stockStatus = valid.stockStatus.name,
            includeInactive = valid.includeInactive,
            limit = valid.limit,
            offset = valid.offset,
        ).map { row ->
            InventoryBalanceView(
                itemId = row.itemId,
                itemName = row.itemName,
                sku = row.sku,
                baseUnit = row.baseUnit,
                locationId = row.locationId,
                locationName = row.locationName,
                onHandMicros = row.onHandMicros,
                reservedMicros = row.reservedMicros,
                inTransitMicros = row.inTransitMicros,
                damagedMicros = row.damagedMicros,
                quarantinedMicros = row.quarantinedMicros,
                inventoryValueRial = row.inventoryValueRial,
                reorderPointMicros = row.reorderPointMicros,
            )
        }
    }

    override suspend fun movements(query: InventoryMovementQuery): List<InventoryMovementView> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        return database.inventoryReadDao().movements(
            itemId = valid.itemId,
            locationId = valid.locationId,
            movementType = valid.movementType?.storedValue,
            fromEpochDay = valid.fromEpochDay,
            toEpochDay = valid.toEpochDay,
            limit = valid.limit,
            offset = valid.offset,
        ).map { row ->
            InventoryMovementView(
                id = row.id,
                itemId = row.itemId,
                itemName = row.itemName,
                baseUnit = row.baseUnit,
                movementType = InventoryMovementType.fromStoredValue(row.movementType),
                quantityDeltaMicros = row.quantityDeltaMicros,
                valueDeltaRial = row.valueDeltaRial,
                unitCostRial = row.unitCostRial,
                businessEpochDay = row.businessEpochDay,
                createdAtEpochMillis = row.createdAtEpochMillis,
                locationId = row.locationId,
                locationName = row.locationName,
                lotNumbers = row.lotNumbers,
                sourceType = InventoryReferenceType.fromStoredValue(row.sourceType),
                sourceId = row.sourceId,
                actorId = row.actorId,
                correlationId = row.correlationId,
                reversalOfMovementId = row.reversalOfMovementId,
                reason = row.reason,
            )
        }
    }
}

