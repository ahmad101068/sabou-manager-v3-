package ir.restaurant.management.domain.brief

import java.math.BigInteger

/**
 * Small canonical read model shared by Home and Daily Brief.
 *
 * Every monetary fact comes from [DailyManagementBrief]. The only derived value is the
 * presentation-ready Food Cost ratio, calculated from the canonical actual-ledger cost and
 * canonical P&L revenue. A nullable value means that the underlying business fact is unavailable;
 * zero remains a real value.
 */
data class DailyManagementKpiReadModel(
    val businessEpochDay: Long,
    val branchId: Long,
    val revenueRial: Long,
    val cogsRial: Long?,
    val grossProfitRial: Long?,
    val foodCostBasisPoints: Long?,
    val operatingExpensesRial: Long?,
    val payrollRial: Long?,
    val estimatedOperatingProfitRial: Long?,
    val newReceivablesRial: Long,
    val collectionsRial: Long,
    val outstandingReceivablesRial: Long,
    val wasteCostRial: Long?,
    val cashVarianceRial: Long?,
    val criticalIssues: Int,
    val openIssues: Int,
    val overdueTasks: Int,
    val failedChecklists: Int,
    val unavailableReason: String?,
)

object DailyManagementKpiReadModelFactory {
    fun from(brief: DailyManagementBrief): DailyManagementKpiReadModel {
        val foodCostBasisPoints = brief.foodCost.actualLedgerCostRial
            ?.takeIf { brief.profitability.revenueRial > 0L }
            ?.let { actualCost -> ratioBasisPoints(actualCost, brief.profitability.revenueRial) }
        return DailyManagementKpiReadModel(
            businessEpochDay = brief.businessEpochDay,
            branchId = brief.branchId,
            revenueRial = brief.profitability.revenueRial,
            cogsRial = brief.profitability.cogsRial,
            grossProfitRial = brief.profitability.grossProfitRial,
            foodCostBasisPoints = foodCostBasisPoints,
            operatingExpensesRial = brief.profitability.operatingExpensesRial,
            payrollRial = brief.profitability.payrollRial,
            estimatedOperatingProfitRial = brief.profitability.estimatedOperatingProfitRial,
            newReceivablesRial = brief.liquidity.newReceivablesRial,
            collectionsRial = brief.liquidity.oldReceivableCollectionsRial,
            outstandingReceivablesRial = brief.liquidity.outstandingReceivablesRial,
            wasteCostRial = brief.wasteCostRial,
            cashVarianceRial = brief.cashVarianceRial,
            criticalIssues = brief.criticalIssues,
            openIssues = brief.openIssues,
            overdueTasks = brief.overdueTasks,
            failedChecklists = brief.failedChecklists,
            unavailableReason = brief.profitability.unavailableReason,
        )
    }

    private fun ratioBasisPoints(numerator: Long, denominator: Long): Long {
        require(numerator >= 0L && denominator > 0L)
        val ratio = BigInteger.valueOf(numerator)
            .multiply(BigInteger.valueOf(10_000L))
            .divide(BigInteger.valueOf(denominator))
        return ratio.coerceIn(BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }
}
