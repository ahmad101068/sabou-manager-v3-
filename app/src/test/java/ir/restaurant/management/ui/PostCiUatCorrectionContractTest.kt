package ir.restaurant.management.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostCiUatCorrectionContractTest {
    @Test
    fun purchaseUseCaseDelegatesBlankInternalNumberToTransactionalRepositoryAllocator() {
        val useCase = projectFile("app/src/main/java/ir/restaurant/management/application/procurement/ProcurementUseCases.kt").readText()
        val repository = projectFile("app/src/main/java/ir/restaurant/management/data/repository/LocalPurchaseRepository.kt").readText()
        assertTrue(useCase.contains("purchaseBoundary().post(draft)"))
        assertFalse(useCase.contains("PurchaseCalculator.prepare(draft).draft"))
        assertTrue(repository.contains("numbering.next(DocumentNumberType.PURCHASE)"))
        assertTrue(repository.contains("database.withTransaction"))
    }

    @Test
    fun homeUsesExplicitBranchAndCanonicalSevenDayRevenueSeries_withoutFakeTrend() {
        val viewModel = projectFile("app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt").readText()
        val screen = projectFile("app/src/main/java/ir/restaurant/management/ui/DashboardScreen.kt").readText()
        assertFalse(viewModel.contains("singleOrNull { it.isActive }?.id"))
        assertTrue(viewModel.contains("(6L downTo 0L)"))
        assertTrue(viewModel.contains("dailyBriefService.compose"))
        assertTrue(screen.contains("HomeRevenueTrendCard"))
        assertFalse(screen.substringAfter("private fun SalesHeroCard").substringBefore("private fun HomeManagementOverview").contains("TinyTrendChart("))
    }

    @Test
    fun procurementRejectRequiresDialogReasonAndApprovalRespectsCanonicalPermission() {
        val source = projectFile("app/src/main/java/ir/restaurant/management/ui/ProcurementControlUi.kt").readText()
        assertTrue(source.contains("procurement_rejection_dialog"))
        assertTrue(source.contains("rejectionReason.trim()"))
        assertTrue(source.contains("Permission.PURCHASE_APPROVE"))
        assertTrue(source.contains("UserRole.OWNER"))
        assertFalse(source.contains("onReview(request.id, false)"))
    }

    private fun projectFile(relative: String): File {
        val cwd = File(System.getProperty("user.dir"))
        return listOf(File(cwd, relative), File(cwd.parentFile ?: cwd, relative))
            .firstOrNull { it.isFile }
            ?: error("Project file not found: $relative from $cwd")
    }
}
