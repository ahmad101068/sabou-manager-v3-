package ir.restaurant.management.domain.operations

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.inventory.InventoryReplenishmentCalculator
import ir.restaurant.management.domain.inventory.InventoryReplenishmentInput
import ir.restaurant.management.domain.inventory.InventoryReplenishmentPolicy

data class ReorderPolicy(
    val leadTimeDays: Int = 2,
    val safetyStockDays: Int = 1,
    val reviewHorizonDays: Int = 7,
) {
    fun validated(): ReorderPolicy {
        require(leadTimeDays in 0..365) { "زمان تأمین معتبر نیست." }
        require(safetyStockDays in 0..365) { "ذخیره ایمن معتبر نیست." }
        require(reviewHorizonDays in 1..365) { "افق سفارش معتبر نیست." }
        return this
    }
}

data class ReorderInput(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val currentStockMicros: Long,
    val onOrderMicros: Long = 0,
    val averageDailyUsageMicros: Long,
    val minimumStockMicros: Long = 0,
    val policy: ReorderPolicy = ReorderPolicy(),
) {
    fun validated(): ReorderInput {
        require(itemId > 0 && itemName.isNotBlank() && unit.isNotBlank()) { "کالای سفارش معتبر نیست." }
        require(currentStockMicros >= 0 && onOrderMicros >= 0) { "موجودی نمی‌تواند منفی باشد." }
        require(averageDailyUsageMicros >= 0 && minimumStockMicros >= 0) { "مصرف یا حداقل موجودی معتبر نیست." }
        policy.validated()
        return this
    }
}

data class ReorderRecommendation(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val projectedNeedMicros: Long,
    val availableMicros: Long,
    val recommendedOrderMicros: Long,
    val daysUntilStockout: Int?,
    val urgency: ReorderUrgency,
)

enum class ReorderUrgency { NONE, PLAN, SOON, CRITICAL }

object SmartReorderCalculator {
    fun recommend(input: ReorderInput): ReorderRecommendation {
        val value = input.validated()
        val safetyStock = SignedLongMath.multiply(
            value.averageDailyUsageMicros,
            value.policy.safetyStockDays.toLong(),
        )
        val inventoryRecommendation = InventoryReplenishmentCalculator.recommend(
            InventoryReplenishmentInput(
                itemId = value.itemId,
                itemName = value.itemName,
                unit = value.unit,
                locationId = null,
                locationName = "ALL_LOCATIONS",
                onHandMicros = value.currentStockMicros,
                reservedMicros = 0,
                damagedMicros = 0,
                quarantinedMicros = 0,
                inTransitMicros = 0,
                onOrderMicros = value.onOrderMicros,
                usageMicros = value.averageDailyUsageMicros,
                usageWindowDays = 1,
                estimatedUnitCostRial = 0,
                preferredSupplierId = null,
                preferredSupplierName = null,
                hasPendingRequisition = false,
                policy = InventoryReplenishmentPolicy(
                    targetCoverDays = value.policy.reviewHorizonDays,
                    leadTimeDays = value.policy.leadTimeDays,
                    safetyStockMicros = safetyStock,
                    minimumStockMicros = value.minimumStockMicros,
                    maximumStockMicros = 0,
                    configuredReorderPointMicros = 0,
                    orderMultipleMicros = InventoryReplenishmentPolicy.MINIMUM_ORDER_INCREMENT_MICROS,
                ),
            ),
        )
        val projectedNeed = inventoryRecommendation.targetStockMicros
        val available = SignedLongMath.add(value.currentStockMicros, value.onOrderMicros)
        val order = inventoryRecommendation.suggestedQuantityMicros
        val daysUntilStockout = if (value.averageDailyUsageMicros > 0) (value.currentStockMicros / value.averageDailyUsageMicros).toInt() else null
        val urgency = when {
            order == 0L -> ReorderUrgency.NONE
            value.currentStockMicros <= value.minimumStockMicros -> ReorderUrgency.CRITICAL
            daysUntilStockout != null && daysUntilStockout <= value.policy.leadTimeDays -> ReorderUrgency.SOON
            else -> ReorderUrgency.PLAN
        }
        return ReorderRecommendation(value.itemId, value.itemName, value.unit, projectedNeed, available, order, daysUntilStockout, urgency)
    }
}
