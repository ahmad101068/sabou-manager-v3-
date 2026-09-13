package ir.restaurant.management.domain.brief

import ir.restaurant.management.domain.control.ActualCostDataQuality
import ir.restaurant.management.domain.control.ConsumptionCostVariance
import ir.restaurant.management.domain.sales.LiquiditySnapshot
import ir.restaurant.management.domain.sales.ProfitabilitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyManagementKpiReadModelTest {
    @Test fun `factory preserves canonical financial facts and distinguishes zero from unavailable`() {
        val model = DailyManagementKpiReadModelFactory.from(
            brief(
                revenueRial = 1_000_000L,
                cogsRial = 400_000L,
                grossProfitRial = 600_000L,
                operatingExpensesRial = null,
                payrollRial = 0L,
                estimatedOperatingProfitRial = null,
                actualFoodCostRial = 250_000L,
            ),
        )

        assertEquals(1_000_000L, model.revenueRial)
        assertEquals(400_000L, model.cogsRial)
        assertEquals(600_000L, model.grossProfitRial)
        assertEquals(2_500L, model.foodCostBasisPoints)
        assertNull(model.operatingExpensesRial)
        assertEquals(0L, model.payrollRial)
        assertNull(model.estimatedOperatingProfitRial)
        assertEquals(75_000L, model.collectionsRial)
        assertEquals(125_000L, model.newReceivablesRial)
    }

    @Test fun `food cost stays unavailable without canonical actual cost`() {
        val model = DailyManagementKpiReadModelFactory.from(
            brief(
                revenueRial = 1_000_000L,
                cogsRial = null,
                grossProfitRial = null,
                operatingExpensesRial = null,
                payrollRial = null,
                estimatedOperatingProfitRial = null,
                actualFoodCostRial = null,
            ),
        )

        assertNull(model.foodCostBasisPoints)
        assertEquals(0L, model.cashVarianceRial)
    }

    private fun brief(
        revenueRial: Long,
        cogsRial: Long?,
        grossProfitRial: Long?,
        operatingExpensesRial: Long?,
        payrollRial: Long?,
        estimatedOperatingProfitRial: Long?,
        actualFoodCostRial: Long?,
    ) = DailyManagementBrief(
        businessEpochDay = 20_000L,
        branchId = 7L,
        profitability = ProfitabilitySnapshot(
            grossSalesRial = revenueRial,
            discountRial = 0L,
            returnRial = 0L,
            netSalesRial = revenueRial,
            serviceRevenueRial = 0L,
            taxPayableRial = 90_000L,
            revenueRial = revenueRial,
            cogsRial = cogsRial,
            grossProfitRial = grossProfitRial,
            operatingExpensesRial = operatingExpensesRial,
            payrollRial = payrollRial,
            estimatedOperatingProfitRial = estimatedOperatingProfitRial,
            unavailableReason = "داده ناقص",
        ),
        liquidity = LiquiditySnapshot(
            cashReceivedRial = 500_000L,
            cardReceivedRial = 500_000L,
            transferReceivedRial = 0L,
            oldReceivableCollectionsRial = 75_000L,
            newReceivablesRial = 125_000L,
            outstandingReceivablesRial = 350_000L,
        ),
        foodCost = ConsumptionCostVariance(
            branchId = 7L,
            fromEpochDay = 20_000L,
            toEpochDay = 20_000L,
            theoreticalCostRial = 240_000L,
            actualLedgerCostRial = actualFoodCostRial,
            varianceCostRial = null,
            varianceBasisPoints = null,
            actualDataQuality = if (actualFoodCostRial == null) ActualCostDataQuality.ACTUAL_NOT_AVAILABLE else ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
        ),
        wasteCostRial = null,
        cashVarianceRial = 0L,
        criticalIssues = 1,
        openIssues = 2,
        overdueTasks = 3,
        failedChecklists = 4,
        importantEvents = emptyList(),
        recommendations = emptyList(),
    )
}
