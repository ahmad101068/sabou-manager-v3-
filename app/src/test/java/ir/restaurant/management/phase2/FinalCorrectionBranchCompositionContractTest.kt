package ir.restaurant.management.phase2

import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesDraft
import ir.restaurant.management.domain.sales.DailySalesPostingDraft
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.SalesSettlementType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun projectFile(relative: String): File {
    val cwd = File(System.getProperty("user.dir"))
    val candidates = listOf(File(cwd, relative), File(cwd.parentFile ?: cwd, relative))
    return candidates.firstOrNull { it.isFile } ?: error("Project file not found: $relative from $cwd")
}

class MultiBranchDailySalesTest {
    @Test
    fun branchIdentityAndRevenueStayIndependent() {
        val branch1 = DailySalesPostingDraft(1, 20_200, 50_000_000, 0, 0, listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 50_000_000))).validated()
        val branch2 = DailySalesPostingDraft(2, 20_200, 80_000_000, 0, 0, listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 80_000_000))).validated()
        assertEquals(1L, branch1.branchId)
        assertEquals(50_000_000, branch1.revenueRial)
        assertEquals(2L, branch2.branchId)
        assertEquals(80_000_000, branch2.revenueRial)

        // Storage identity must not make businessEpochDay globally unique across branches.
        val source = projectFile("app/src/main/java/ir/restaurant/management/data/db/DailySalesEntities.kt").readText()
        val closureEntity = source.substringAfter("data class SalesDayClosureEntity")
        assertTrue(closureEntity.contains("@PrimaryKey val summaryId"))
        assertFalse(closureEntity.contains("@PrimaryKey val businessEpochDay"))
        val declaration = source.substringAfter("tableName = \"sales_day_closures\"").substringBefore("data class SalesDayClosureEntity")
        assertTrue(declaration.contains("Index(\"businessEpochDay\")"))
    }
}

class MultiBranchManagementRuleTest {
    @Test
    fun phase2RulesHaveNoBranchOneGateAndUseContextBranchForScopedRules() {
        val source = projectFile("app/src/main/java/ir/restaurant/management/data/repository/ManagementRules.kt").readText()
        assertFalse(source.contains("branchId != 1L"))
        assertFalse(source.contains("branchId == 1L"))
        assertTrue(source.contains("foodCostVariance(context.branchId"))
        assertTrue(source.contains("branchWasteCost(context.branchId"))
        assertTrue(source.contains("overdueLotsForRule(context.branchId"))
    }
}

class SalesCompositionWithoutLineAmountsTest {
    @Test
    fun headerGrossAndQuantityAreValidWithoutFabricatedLineRevenue() {
        val draft = DailySalesDraft(
            businessEpochDay = 20_300,
            discountRial = 0,
            serviceRial = 0,
            taxRial = 0,
            cashRial = 125_000_000,
            cardRial = 0,
            transferRial = 0,
            lines = listOf(
                DailyMenuSaleDraft(1, 73_000_000, null),
                DailyMenuSaleDraft(2, 91_000_000, null),
                DailyMenuSaleDraft(3, 34_000_000, null),
            ),
            branchId = 2,
            grossSalesRial = 125_000_000,
        )
        assertEquals(125_000_000, draft.grossSalesRial)
        assertTrue(draft.lines.all { it.quantityMicros > 0 })
        assertTrue(draft.lines.all { it.grossSalesRial == null })
        assertNull(draft.lines.first().grossSalesRial)
    }
}

class LegacyOverdueReceivableRuleTest {
    @Test
    fun ruleUsesCanonicalReadModelInsteadOfDirectReceivablesQuery() {
        val source = projectFile("app/src/main/java/ir/restaurant/management/data/repository/ManagementRules.kt").readText()
        val rule = source.substringAfter("class OverdueReceivableRule").substringBefore("class FoodCostVarianceRule")
        assertTrue(rule.contains("CanonicalReceivableReadModel"))
        assertTrue(rule.contains("overdueLotsForRule"))
        assertFalse(rule.contains("openReceivables("))
        assertFalse(rule.contains("overdueReceivables("))
    }
}

class RoomSchemaEvidenceVerifierTest {
    @Test
    fun verifierCannotReportPassForMissingCurrentSchema() {
        val source = projectFile("scripts/verify-code-quality.py").readText()
        assertTrue(source.contains("APP_DATABASE_SCHEMA_VERSION"))
        assertTrue(source.contains("current_room_version in exported_versions"))
        assertTrue(source.contains("ROOM_SCHEMA_EVIDENCE=PENDING"))
        assertFalse(source.contains("schema_versions.issuperset"))
    }
}
