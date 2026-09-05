package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryReplenishmentInputRow
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.InventoryDemandUsagePolicy
import ir.restaurant.management.domain.inventory.InventoryReplenishmentCalculator
import ir.restaurant.management.domain.inventory.InventoryReplenishmentInput
import ir.restaurant.management.domain.inventory.InventoryReplenishmentPolicy
import ir.restaurant.management.domain.inventory.InventoryReplenishmentQuery
import ir.restaurant.management.domain.inventory.InventoryReplenishmentRecommendation
import ir.restaurant.management.domain.inventory.InventoryReplenishmentService
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission

/** Location-aware Inventory read boundary. Procurement consumes its recommendations; it never creates a PO. */
class LocalInventoryReplenishmentService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
) : InventoryReplenishmentService {
    override suspend fun recommendations(
        query: InventoryReplenishmentQuery,
    ): List<InventoryReplenishmentRecommendation> {
        authorizer.require(Permission.INVENTORY_VIEW)
        val valid = query.validated()
        valid.locationId?.let { locationId ->
            database.inventoryLocationDao().activeById(locationId)
                ?: throw BusinessError.InvalidLocation(locationId, "INACTIVE_OR_MISSING").asViolation()
        }
        val fromEpochDay = maxOf(1L, valid.asOfEpochDay - valid.usageWindowDays + 1L)
        return database.inventoryReplenishmentDao().inputs(
            itemId = null,
            locationId = valid.locationId,
            fromEpochDay = fromEpochDay,
            asOfEpochDay = valid.asOfEpochDay,
            includeWaste = if (valid.demandUsagePolicy == InventoryDemandUsagePolicy.INCLUDE_WASTE) 1 else 0,
            limit = valid.limit,
            offset = valid.offset,
        ).map { it.toRecommendation(valid.usageWindowDays) }
            .filter { !valid.actionableOnly || it.suggestedQuantityMicros > 0 }
            .sortedWith(
                compareBy<InventoryReplenishmentRecommendation> { it.risk.ordinal }
                    .thenBy { it.daysOfCoverBasisPoints ?: Long.MAX_VALUE }
                    .thenBy { it.itemName },
            )
    }

    override suspend fun recommendation(
        itemId: Long,
        locationId: Long?,
        asOfEpochDay: Long,
        demandUsagePolicy: InventoryDemandUsagePolicy,
    ): InventoryReplenishmentRecommendation? {
        authorizer.require(Permission.INVENTORY_VIEW)
        require(itemId > 0 && asOfEpochDay > 0)
        locationId?.let { id ->
            database.inventoryLocationDao().activeById(id)
                ?: throw BusinessError.InvalidLocation(id, "INACTIVE_OR_MISSING").asViolation()
        }
        val windowDays = DEFAULT_USAGE_WINDOW_DAYS
        return database.inventoryReplenishmentDao().inputs(
            itemId = itemId,
            locationId = locationId,
            fromEpochDay = maxOf(1L, asOfEpochDay - windowDays + 1L),
            asOfEpochDay = asOfEpochDay,
            includeWaste = if (demandUsagePolicy == InventoryDemandUsagePolicy.INCLUDE_WASTE) 1 else 0,
            limit = 1,
            offset = 0,
        ).singleOrNull()?.toRecommendation(windowDays)
    }

    private fun InventoryReplenishmentInputRow.toRecommendation(
        usageWindowDays: Int,
    ): InventoryReplenishmentRecommendation {
        val legacySafety = legacySafetyStockMicros ?: 0L
        val preferredId = preferredSupplierId.takeIf { preferredSupplierName != null }
        return InventoryReplenishmentCalculator.recommend(
            InventoryReplenishmentInput(
                itemId = itemId,
                itemName = itemName,
                unit = unit,
                locationId = locationId,
                locationName = locationName ?: "همه محل‌ها",
                onHandMicros = onHandMicros,
                reservedMicros = reservedMicros,
                damagedMicros = damagedMicros,
                quarantinedMicros = quarantinedMicros,
                inTransitMicros = inTransitMicros,
                onOrderMicros = onOrderMicros,
                usageMicros = usageMicros,
                usageWindowDays = usageWindowDays,
                estimatedUnitCostRial = estimatedUnitCostRial,
                preferredSupplierId = preferredId,
                preferredSupplierName = preferredSupplierName,
                hasPendingRequisition = hasPendingRequisition,
                policy = InventoryReplenishmentPolicy(
                    targetCoverDays = legacyTargetCoverDays
                        ?: InventoryReplenishmentPolicy.DEFAULT_TARGET_COVER_DAYS,
                    leadTimeDays = itemLeadTimeDays.takeIf { it > 0 } ?: legacyLeadTimeDays ?: 0,
                    safetyStockMicros = maxOf(safetyStockMicros, legacySafety),
                    minimumStockMicros = minimumStockMicros,
                    maximumStockMicros = maximumStockMicros,
                    configuredReorderPointMicros = configuredReorderPointMicros,
                    orderMultipleMicros = legacyOrderMultipleMicros
                        ?: InventoryReplenishmentPolicy.MINIMUM_ORDER_INCREMENT_MICROS,
                    isEnabled = legacyPolicyEnabled ?: alertEnabled,
                ),
            ),
        )
    }

    private companion object {
        const val DEFAULT_USAGE_WINDOW_DAYS = 30
    }
}
