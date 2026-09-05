package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import java.math.BigInteger

enum class InventoryDemandUsagePolicy {
    EXCLUDE_WASTE,
    INCLUDE_WASTE,
}

enum class InventoryReplenishmentRisk {
    OUT_OF_STOCK,
    BELOW_SAFETY_STOCK,
    BELOW_REORDER_POINT,
    LEAD_TIME_RISK,
    NO_USAGE_HISTORY,
    HEALTHY,
    DISABLED,
}

enum class InventoryReplenishmentReason {
    OUT_OF_STOCK,
    SAFETY_STOCK_BREACH,
    REORDER_POINT_REACHED,
    LEAD_TIME_COVERAGE,
    MINIMUM_STOCK,
    NO_USAGE_HISTORY,
    POLICY_DISABLED,
    NO_ACTION_REQUIRED,
}

data class InventoryReplenishmentPolicy(
    val targetCoverDays: Int,
    val leadTimeDays: Int,
    val safetyStockMicros: Long,
    val minimumStockMicros: Long,
    val maximumStockMicros: Long,
    val configuredReorderPointMicros: Long,
    val orderMultipleMicros: Long,
    val isEnabled: Boolean = true,
) {
    fun validated(): InventoryReplenishmentPolicy {
        require(targetCoverDays in 1..365)
        require(leadTimeDays in 0..365)
        listOf(
            safetyStockMicros,
            minimumStockMicros,
            maximumStockMicros,
            configuredReorderPointMicros,
        ).forEach(QuantityMicros::of)
        QuantityMicros.positive(orderMultipleMicros)
        require(maximumStockMicros == 0L || minimumStockMicros <= maximumStockMicros)
        require(maximumStockMicros == 0L || configuredReorderPointMicros <= maximumStockMicros)
        return this
    }

    companion object {
        const val DEFAULT_TARGET_COVER_DAYS = 7
        const val MINIMUM_ORDER_INCREMENT_MICROS = 1L
    }
}

data class InventoryReplenishmentInput(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val locationId: Long?,
    val locationName: String,
    val onHandMicros: Long,
    val reservedMicros: Long,
    val damagedMicros: Long,
    val quarantinedMicros: Long,
    val inTransitMicros: Long,
    val onOrderMicros: Long,
    val usageMicros: Long,
    val usageWindowDays: Int,
    val estimatedUnitCostRial: Long,
    val preferredSupplierId: Long?,
    val preferredSupplierName: String?,
    val hasPendingRequisition: Boolean,
    val policy: InventoryReplenishmentPolicy,
) {
    fun validated(): InventoryReplenishmentInput {
        require(itemId > 0 && itemName.isNotBlank() && unit.isNotBlank())
        require(locationId == null || locationId > 0)
        require(locationName.isNotBlank())
        listOf(
            onHandMicros,
            reservedMicros,
            damagedMicros,
            quarantinedMicros,
            inTransitMicros,
            onOrderMicros,
            usageMicros,
        ).forEach(QuantityMicros::of)
        require(
            reservedMicros.toBigInteger()
                .add(damagedMicros.toBigInteger())
                .add(quarantinedMicros.toBigInteger()) <= onHandMicros.toBigInteger(),
        )
        require(usageWindowDays in 1..365)
        MoneyRial.of(estimatedUnitCostRial)
        require(preferredSupplierId == null || preferredSupplierId > 0)
        policy.validated()
        return this
    }
}

data class InventoryReplenishmentRecommendation(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val locationId: Long?,
    val locationName: String,
    val onHandMicros: Long,
    val availableMicros: Long,
    val inTransitMicros: Long,
    val onOrderMicros: Long,
    val averageDailyUsageMicros: Long,
    val leadTimeDays: Int,
    val leadTimeDemandMicros: Long,
    val reorderPointMicros: Long,
    val targetStockMicros: Long,
    val projectedAtDeliveryMicros: Long,
    val suggestedQuantityMicros: Long,
    val daysOfCoverBasisPoints: Long?,
    val estimatedUnitCostRial: Long,
    val estimatedOrderValueRial: Long,
    val preferredSupplierId: Long?,
    val preferredSupplierName: String?,
    val hasPendingRequisition: Boolean,
    val risk: InventoryReplenishmentRisk,
    val reason: InventoryReplenishmentReason,
) {
    val isActionable: Boolean get() = suggestedQuantityMicros > 0 && !hasPendingRequisition
}

object InventoryReplenishmentCalculator {
    fun recommend(input: InventoryReplenishmentInput): InventoryReplenishmentRecommendation {
        val value = input.validated()
        val daily = if (value.usageMicros == 0L) BigInteger.ZERO else ceilDiv(
            value.usageMicros.toBigInteger(),
            value.usageWindowDays.toBigInteger(),
        )
        val available = value.onHandMicros.toBigInteger()
            .subtract(value.reservedMicros.toBigInteger())
            .subtract(value.damagedMicros.toBigInteger())
            .subtract(value.quarantinedMicros.toBigInteger())
        val supplyPosition = available
            .add(value.inTransitMicros.toBigInteger())
            .add(value.onOrderMicros.toBigInteger())
        val leadTimeDemand = daily.multiply(value.policy.leadTimeDays.toBigInteger())
        val computedReorderPoint = leadTimeDemand.add(value.policy.safetyStockMicros.toBigInteger())
        val reorderPoint = maxOf(
            computedReorderPoint,
            value.policy.minimumStockMicros.toBigInteger(),
            value.policy.configuredReorderPointMicros.toBigInteger(),
        )
        val demandTarget = daily
            .multiply((value.policy.leadTimeDays + value.policy.targetCoverDays).toBigInteger())
            .add(value.policy.safetyStockMicros.toBigInteger())
        val targetStock = maxOf(
            demandTarget,
            reorderPoint,
            value.policy.maximumStockMicros.takeIf { it > 0 }?.toBigInteger() ?: BigInteger.ZERO,
        )
        val projectedAtDelivery = supplyPosition.subtract(leadTimeDemand).max(BigInteger.ZERO)
        val reorderTriggered = value.policy.isEnabled && supplyPosition <= reorderPoint
        val rawSuggested = if (reorderTriggered) targetStock.subtract(supplyPosition).max(BigInteger.ZERO) else BigInteger.ZERO
        val multiple = value.policy.orderMultipleMicros.toBigInteger()
        val suggested = if (rawSuggested == BigInteger.ZERO) BigInteger.ZERO else ceilDiv(rawSuggested, multiple).multiply(multiple)
        require(suggested <= QuantityMicros.MAX_VALUE.toBigInteger()) {
            "مقدار پیشنهاد تأمین از محدوده امن خارج می‌شود."
        }
        val daysOfCover = if (daily == BigInteger.ZERO) null else available
            .multiply(10_000L.toBigInteger())
            .divide(daily)
            .min(Long.MAX_VALUE.toBigInteger())
            .toLongExactCompat()
        val risk = when {
            !value.policy.isEnabled -> InventoryReplenishmentRisk.DISABLED
            available == BigInteger.ZERO -> InventoryReplenishmentRisk.OUT_OF_STOCK
            daily == BigInteger.ZERO -> InventoryReplenishmentRisk.NO_USAGE_HISTORY
            available <= value.policy.safetyStockMicros.toBigInteger() -> InventoryReplenishmentRisk.BELOW_SAFETY_STOCK
            supplyPosition <= reorderPoint -> InventoryReplenishmentRisk.BELOW_REORDER_POINT
            available <= leadTimeDemand -> InventoryReplenishmentRisk.LEAD_TIME_RISK
            else -> InventoryReplenishmentRisk.HEALTHY
        }
        val reason = when {
            !value.policy.isEnabled -> InventoryReplenishmentReason.POLICY_DISABLED
            available == BigInteger.ZERO -> InventoryReplenishmentReason.OUT_OF_STOCK
            available <= value.policy.safetyStockMicros.toBigInteger() -> InventoryReplenishmentReason.SAFETY_STOCK_BREACH
            supplyPosition <= reorderPoint -> when {
                supplyPosition <= value.policy.minimumStockMicros.toBigInteger() -> InventoryReplenishmentReason.MINIMUM_STOCK
                available <= leadTimeDemand -> InventoryReplenishmentReason.LEAD_TIME_COVERAGE
                else -> InventoryReplenishmentReason.REORDER_POINT_REACHED
            }
            daily == BigInteger.ZERO -> InventoryReplenishmentReason.NO_USAGE_HISTORY
            else -> InventoryReplenishmentReason.NO_ACTION_REQUIRED
        }
        val suggestedLong = suggested.toLongExactCompat()
        val orderValue = if (suggestedLong == 0L || value.estimatedUnitCostRial == 0L) 0L else MoneyRial
            .of(value.estimatedUnitCostRial)
            .times(QuantityMicros.of(suggestedLong))
            .value
        return InventoryReplenishmentRecommendation(
            itemId = value.itemId,
            itemName = value.itemName,
            unit = value.unit,
            locationId = value.locationId,
            locationName = value.locationName,
            onHandMicros = value.onHandMicros,
            availableMicros = available.toLongExactCompat(),
            inTransitMicros = value.inTransitMicros,
            onOrderMicros = value.onOrderMicros,
            averageDailyUsageMicros = daily.toLongExactCompat(),
            leadTimeDays = value.policy.leadTimeDays,
            leadTimeDemandMicros = leadTimeDemand.toLongExactCompat(),
            reorderPointMicros = reorderPoint.toLongExactCompat(),
            targetStockMicros = targetStock.toLongExactCompat(),
            projectedAtDeliveryMicros = projectedAtDelivery.min(QuantityMicros.MAX_VALUE.toBigInteger()).toLongExactCompat(),
            suggestedQuantityMicros = suggestedLong,
            daysOfCoverBasisPoints = daysOfCover,
            estimatedUnitCostRial = value.estimatedUnitCostRial,
            estimatedOrderValueRial = orderValue,
            preferredSupplierId = value.preferredSupplierId,
            preferredSupplierName = value.preferredSupplierName,
            hasPendingRequisition = value.hasPendingRequisition,
            risk = risk,
            reason = reason,
        )
    }

    private fun ceilDiv(value: BigInteger, divisor: BigInteger): BigInteger =
        value.add(divisor).subtract(BigInteger.ONE).divide(divisor)
}

data class InventoryReplenishmentQuery(
    val locationId: Long? = null,
    val asOfEpochDay: Long,
    val usageWindowDays: Int = 30,
    val demandUsagePolicy: InventoryDemandUsagePolicy = InventoryDemandUsagePolicy.EXCLUDE_WASTE,
    val actionableOnly: Boolean = true,
    val limit: Int = 200,
    val offset: Int = 0,
) {
    fun validated(): InventoryReplenishmentQuery {
        require(locationId == null || locationId > 0)
        require(asOfEpochDay > 0 && usageWindowDays in 1..365)
        require(limit in 1..500 && offset >= 0)
        return this
    }
}

interface InventoryReplenishmentService {
    suspend fun recommendations(query: InventoryReplenishmentQuery): List<InventoryReplenishmentRecommendation>
    suspend fun recommendation(
        itemId: Long,
        locationId: Long?,
        asOfEpochDay: Long,
        demandUsagePolicy: InventoryDemandUsagePolicy = InventoryDemandUsagePolicy.EXCLUDE_WASTE,
    ): InventoryReplenishmentRecommendation?
}
