package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.SignedLongMath

/**
 * Canonical branch P&L read model backed by the existing accounting journal.
 * Organization-wide and unassigned-legacy journals are never allocated to a branch implicitly.
 */
data class BranchProfitAndLoss(
    val branchId: Long,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val revenueRial: Long,
    val cogsRial: Long,
    val operatingExpensesExcludingPayrollRial: Long,
    val payrollRial: Long,
    val isRevenueComplete: Boolean,
    val isCogsComplete: Boolean,
    val isExpenseComplete: Boolean,
    val isPayrollComplete: Boolean,
) {
    init {
        require(branchId > 0) { "branch_pnl_branch_invalid" }
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay) { "branch_pnl_period_invalid" }
    }

    val grossProfitRial: Long = SignedLongMath.subtract(revenueRial, cogsRial)
    val isEstimatedOperatingProfitAvailable: Boolean =
        isRevenueComplete && isCogsComplete && isExpenseComplete && isPayrollComplete

    val estimatedOperatingProfitRial: Long? = if (isEstimatedOperatingProfitAvailable) {
        SignedLongMath.subtract(
            SignedLongMath.subtract(grossProfitRial, operatingExpensesExcludingPayrollRial),
            payrollRial,
        )
    } else {
        null
    }

    val unavailableReason: String? = buildList {
        if (!isRevenueComplete) add("درآمد شعبه با اسناد عملیاتی/حسابداری قابل تطبیق کامل نیست")
        if (!isCogsComplete) add("اسناد COGS تاریخی بدون شعبه قطعی وجود دارد")
        if (!isExpenseComplete) add("هزینه عملیاتی تاریخی بدون شعبه قطعی وجود دارد")
        if (!isPayrollComplete) add("حقوق تاریخی بدون شعبه قطعی وجود دارد")
    }.takeIf { it.isNotEmpty() }?.joinToString("؛ ")
}
