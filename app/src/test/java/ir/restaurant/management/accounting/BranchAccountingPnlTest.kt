package ir.restaurant.management.accounting

import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.BranchProfitAndLoss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchAccountingPnlTest {
    @Test
    fun branch2_complete_pnl_uses_revenue_cogs_expense_and_payroll_once() {
        val pnl = BranchProfitAndLoss(
            branchId = 2,
            fromEpochDay = 20_000,
            toEpochDay = 20_000,
            revenueRial = 125_000_000,
            cogsRial = 48_000_000,
            operatingExpensesExcludingPayrollRial = 12_000_000,
            payrollRial = 9_000_000,
            isRevenueComplete = true,
            isCogsComplete = true,
            isExpenseComplete = true,
            isPayrollComplete = true,
        )

        assertEquals(77_000_000L, pnl.grossProfitRial)
        assertEquals(56_000_000L, pnl.estimatedOperatingProfitRial)
        assertTrue(pnl.isEstimatedOperatingProfitAvailable)
    }

    @Test
    fun missing_payroll_evidence_makes_estimated_profit_unavailable_instead_of_zero() {
        val pnl = BranchProfitAndLoss(
            branchId = 2,
            fromEpochDay = 20_000,
            toEpochDay = 20_000,
            revenueRial = 125_000_000,
            cogsRial = 48_000_000,
            operatingExpensesExcludingPayrollRial = 12_000_000,
            payrollRial = 0,
            isRevenueComplete = true,
            isCogsComplete = true,
            isExpenseComplete = true,
            isPayrollComplete = false,
        )

        assertFalse(pnl.isEstimatedOperatingProfitAvailable)
        assertNull(pnl.estimatedOperatingProfitRial)
        assertTrue(pnl.unavailableReason.orEmpty().contains("حقوق"))
    }

    @Test
    fun accounting_scope_distinguishes_branch_organization_and_unassigned_legacy() {
        AccountingScope.BRANCH.requireCompatible(2)
        AccountingScope.ORGANIZATION.requireCompatible(null)
        AccountingScope.UNASSIGNED_LEGACY.requireCompatible(null)

        assertTrue(runCatching { AccountingScope.BRANCH.requireCompatible(null) }.isFailure)
        assertTrue(runCatching { AccountingScope.ORGANIZATION.requireCompatible(2) }.isFailure)
        assertTrue(runCatching { AccountingScope.UNASSIGNED_LEGACY.requireCompatible(1) }.isFailure)
    }
}
