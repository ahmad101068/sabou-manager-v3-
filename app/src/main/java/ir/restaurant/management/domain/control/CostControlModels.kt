package ir.restaurant.management.domain.control

data class PurchasePriceInsight(
    val itemId: Long,
    val itemName: String,
    val currentPriceRial: Long,
    val previousPriceRial: Long?,
    val average30DayRial: Long?,
    val changeBasisPointsVs30Day: Int?,
)

enum class ActualCostDataQuality {
    ACTUAL_CONFIRMED,
    ACTUAL_LEDGER_ESTIMATE,
    ACTUAL_NOT_AVAILABLE,
}

data class ConsumptionCostVariance(
    val branchId: Long,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val theoreticalCostRial: Long,
    val actualLedgerCostRial: Long?,
    val varianceCostRial: Long?,
    val varianceBasisPoints: Int?,
    val actualDataQuality: ActualCostDataQuality,
    val actualDataReason: String? = null,
)

interface CostControlReadService {
    suspend fun purchasePriceInsights(branchId: Long, fromEpochDay: Long, toEpochDay: Long): List<PurchasePriceInsight>
    suspend fun consumptionVariance(branchId: Long, fromEpochDay: Long, toEpochDay: Long): ConsumptionCostVariance
}
