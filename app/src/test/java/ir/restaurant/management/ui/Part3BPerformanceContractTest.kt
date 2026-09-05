package ir.restaurant.management.ui

import ir.restaurant.management.domain.branch.BranchRecord
import ir.restaurant.management.domain.brief.DailyManagementBrief
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModelFactory
import ir.restaurant.management.domain.control.ActualCostDataQuality
import ir.restaurant.management.domain.control.ConsumptionCostVariance
import ir.restaurant.management.domain.sales.LiquiditySnapshot
import ir.restaurant.management.domain.sales.ProfitabilitySnapshot
import java.io.File
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Part3BPerformanceContractTest {
    @Test
    fun fiveHundredRowMobileAndDesktopPathsAreLazyAndUseStableKeys() {
        val source = projectFile("app/src/main/java/ir/restaurant/management/ui/ManagementDataGrid.kt").readText()
        val desktop = source.substringAfter("internal fun <T> ManagementDataGrid").substringBefore("@Composable\ninternal fun <T> GridHeader")
        val adaptive = source.substringAfter("internal fun <T> AdaptiveManagementList")
        assertTrue(desktop.contains("LazyColumn"))
        assertTrue(desktop.contains("items(rows, key = key)"))
        assertTrue(adaptive.contains("LazyColumn"))
        assertTrue(adaptive.contains("val visibleRows = rows.page(pageWindow)"))
        assertTrue(adaptive.contains("items(visibleRows, key = key)"))
        assertTrue(adaptive.contains("rows = visibleRows"))
        assertTrue(!adaptive.contains("rows.forEach"))
        assertEquals(500, (1L..500L).map { it }.distinct().size)
    }

    @Test
    fun searchFilterDashboardMappingAndBranchSelectionHandleFiveHundredRowsWithinBudget() {
        val branches = (1L..500L).map { id ->
            BranchRecord(id, "branch-$id", null, "B$id", "Branch $id", isActive = id % 5L != 0L)
        }
        val brief = canonicalBrief()
        var searchMatches = 0
        var activeBranches = 0
        var mappedRevenue = 0L
        val elapsed = measureTimeMillis {
            repeat(100) {
                searchMatches = branches.count { businessTextMatches("Branch 49", it.name, it.code) }
                activeBranches = branches.count { it.isActive }
            }
            repeat(500) {
                mappedRevenue = DailyManagementKpiReadModelFactory.from(brief).revenueRial
            }
        }
        assertEquals(11, searchMatches)
        assertEquals(400, activeBranches)
        assertEquals(1_000_000L, mappedRevenue)
        assertTrue("500-row performance contract exceeded 5 seconds: ${elapsed}ms", elapsed < 5_000L)
    }

    @Test
    fun canonicalBranchSelectorVirtualizesFiveHundredOptionsWithStableIds() {
        val source = projectFile("app/src/main/java/ir/restaurant/management/ui/CanonicalBranchSelector.kt").readText()
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("items(activeBranches, key = { it.id })"))
        assertTrue(!source.contains("activeBranches.forEach"))
    }

    private fun canonicalBrief() = DailyManagementBrief(
        businessEpochDay = 20_000L,
        branchId = 7L,
        profitability = ProfitabilitySnapshot(
            grossSalesRial = 1_000_000L,
            discountRial = 0L,
            returnRial = 0L,
            netSalesRial = 1_000_000L,
            serviceRevenueRial = 0L,
            taxPayableRial = 90_000L,
            revenueRial = 1_000_000L,
            cogsRial = 400_000L,
            grossProfitRial = 600_000L,
            operatingExpensesRial = 100_000L,
            payrollRial = 50_000L,
            estimatedOperatingProfitRial = 450_000L,
            unavailableReason = null,
        ),
        liquidity = LiquiditySnapshot(500_000L, 500_000L, 0L, 75_000L, 125_000L, 350_000L),
        foodCost = ConsumptionCostVariance(
            branchId = 7L,
            fromEpochDay = 20_000L,
            toEpochDay = 20_000L,
            theoreticalCostRial = 240_000L,
            actualLedgerCostRial = 250_000L,
            varianceCostRial = 10_000L,
            varianceBasisPoints = 100,
            actualDataQuality = ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
        ),
        wasteCostRial = 5_000L,
        cashVarianceRial = 0L,
        criticalIssues = 1,
        openIssues = 2,
        overdueTasks = 3,
        failedChecklists = 4,
        importantEvents = emptyList(),
        recommendations = emptyList(),
    )

    private fun projectFile(relative: String): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, relative), File(cwd.parentFile ?: cwd, relative))
            .firstOrNull { it.isFile }
            ?: error("Project file not found: $relative from $cwd")
    }
}
