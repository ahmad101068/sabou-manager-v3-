package ir.restaurant.management.domain.brief

import ir.restaurant.management.domain.sales.LiquiditySnapshot
import ir.restaurant.management.domain.sales.ProfitabilitySnapshot
import ir.restaurant.management.domain.control.ConsumptionCostVariance

data class DailyManagementBrief(
    val businessEpochDay: Long,
    val branchId: Long,
    val profitability: ProfitabilitySnapshot,
    val liquidity: LiquiditySnapshot,
    val foodCost: ConsumptionCostVariance,
    val wasteCostRial: Long?,
    val cashVarianceRial: Long?,
    val criticalIssues: Int,
    val openIssues: Int,
    val overdueTasks: Int,
    val failedChecklists: Int,
    val importantEvents: List<String>,
    val recommendations: List<String>,
)

interface DailyManagementBriefService {
    suspend fun compose(branchId: Long, businessEpochDay: Long): DailyManagementBrief
}
