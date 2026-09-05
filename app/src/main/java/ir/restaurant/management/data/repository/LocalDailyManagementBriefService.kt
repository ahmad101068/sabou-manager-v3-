package ir.restaurant.management.data.repository

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.accounting.BranchProfitAndLoss
import ir.restaurant.management.domain.brief.DailyManagementBrief
import ir.restaurant.management.domain.brief.DailyManagementBriefService
import ir.restaurant.management.domain.control.ActualCostDataQuality
import ir.restaurant.management.domain.control.ConsumptionCostVariance
import ir.restaurant.management.domain.control.ManagementIssueType
import ir.restaurant.management.domain.sales.LiquiditySnapshot
import ir.restaurant.management.domain.sales.ProfitabilitySnapshot
import ir.restaurant.management.domain.security.Permission
import java.math.BigInteger

class LocalDailyManagementBriefService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
) : DailyManagementBriefService {
    override suspend fun compose(branchId: Long, businessEpochDay: Long): DailyManagementBrief {
        authorizer.require(Permission.DAILY_BRIEF_VIEW)
        require(branchId>0 && businessEpochDay>0)
        CanonicalBranchResolver(database).requireExisting(branchId)
        val dao=database.businessOperationsDao()
        val sales=dao.salesAggregate(branchId,businessEpochDay,businessEpochDay)
        val settlements=dao.settlementAggregate(branchId,businessEpochDay,businessEpochDay)
        val oldCollections=dao.receivableCollectionsTotal(branchId,businessEpochDay,businessEpochDay)
        val outstanding=dao.outstandingTotal(branchId)
        val newReceivables=SignedLongMath.add(settlements.personalCreditRial,settlements.corporateCreditRial)
        val rawPnl = database.accountingDao().branchProfitLoss(branchId, businessEpochDay, businessEpochDay)
        val canonicalPnl = BranchProfitAndLoss(
            branchId = branchId,
            fromEpochDay = businessEpochDay,
            toEpochDay = businessEpochDay,
            revenueRial = rawPnl.revenueRial,
            cogsRial = rawPnl.cogsRial,
            operatingExpensesExcludingPayrollRial = rawPnl.operatingExpensesExcludingPayrollRial,
            payrollRial = rawPnl.payrollRial,
            // Operational Daily Sales is an independent reconciliation source for branch revenue.
            // A mismatch means accounting attribution is incomplete and profit must stay unavailable.
            isRevenueComplete = rawPnl.unassignedRevenueLineCount == 0L && rawPnl.revenueRial == sales.revenueRial,
            isCogsComplete = rawPnl.unassignedCogsLineCount == 0L,
            isExpenseComplete = rawPnl.unassignedOperatingExpenseLineCount == 0L,
            isPayrollComplete = rawPnl.unassignedPayrollLineCount == 0L,
        )
        val profitability=ProfitabilitySnapshot(
            grossSalesRial=sales.grossSalesRial,
            discountRial=sales.discountRial,
            returnRial=sales.returnRial,
            netSalesRial=sales.netSalesRial,
            serviceRevenueRial=sales.serviceRevenueRial,
            taxPayableRial=sales.taxPayableRial,
            revenueRial=canonicalPnl.revenueRial,
            cogsRial=canonicalPnl.cogsRial.takeIf { canonicalPnl.isCogsComplete },
            grossProfitRial=canonicalPnl.grossProfitRial.takeIf { canonicalPnl.isRevenueComplete && canonicalPnl.isCogsComplete },
            operatingExpensesRial=canonicalPnl.operatingExpensesExcludingPayrollRial.takeIf { canonicalPnl.isExpenseComplete },
            payrollRial=canonicalPnl.payrollRial.takeIf { canonicalPnl.isPayrollComplete },
            estimatedOperatingProfitRial=canonicalPnl.estimatedOperatingProfitRial,
            isCogsComplete=canonicalPnl.isCogsComplete,
            isExpenseDataComplete=canonicalPnl.isExpenseComplete,
            isPayrollDataComplete=canonicalPnl.isPayrollComplete,
            isEstimatedProfitAvailable=canonicalPnl.isEstimatedOperatingProfitAvailable,
            unavailableReason=canonicalPnl.unavailableReason,
        )
        val liquidity=LiquiditySnapshot(settlements.cashRial,settlements.cardRial,settlements.transferRial,oldCollections,newReceivables,outstanding)
        val rawFood=dao.foodCostVariance(branchId,businessEpochDay,businessEpochDay)
        val foodCost = if (rawFood.actualEvidenceCount<=0) {
            ConsumptionCostVariance(branchId,businessEpochDay,businessEpochDay,rawFood.theoreticalCostRial,null,null,null,ActualCostDataQuality.ACTUAL_NOT_AVAILABLE,
                "شاهد مستقل و منتسب به همین شعبه برای ضایعات/شمارش/تعدیل actual وجود ندارد.")
        } else {
            val actual=SignedLongMath.subtract(SignedLongMath.add(SignedLongMath.add(rawFood.standardSalesLedgerCostRial,rawFood.wasteCostRial),rawFood.negativeAdjustmentCostRial),rawFood.positiveAdjustmentCostRial)
            val variance=SignedLongMath.subtract(actual,rawFood.theoreticalCostRial)
            val bp=rawFood.theoreticalCostRial.takeIf { it>0 }?.let { denominator ->
                val raw = BigInteger.valueOf(variance).multiply(BigInteger.valueOf(10_000L)).divide(BigInteger.valueOf(denominator))
                require(raw >= BigInteger.valueOf(Int.MIN_VALUE.toLong()) && raw <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) { "درصد انحراف Food Cost از محدوده امن خارج است." }
                raw.toInt()
            }
            ConsumptionCostVariance(branchId,businessEpochDay,businessEpochDay,rawFood.theoreticalCostRial,actual,variance,bp,ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
                "مصرف استاندارد فروش + ضایعات + تعدیل منفی - اصلاح مثبت؛ transfer حذف شده است.")
        }
        val critical=dao.criticalIssueCount(branchId); val open=dao.openIssueCount(branchId); val overdue=dao.overdueTaskCount(branchId,clock()); val failed=dao.failedChecklistCount(branchId,businessEpochDay,businessEpochDay)
        val importantIssues=dao.importantIssues(branchId,5)
        val events=buildList {
            importantIssues.forEach { issue -> add("${issue.title}${issue.financialImpactRial?.let { " · اثر مالی $it ریال" } ?: ""}") }
            if(size<5 && overdue>0) add("$overdue وظیفه سررسیدگذشته نیازمند پیگیری است.")
            if(size<5 && failed>0) add("$failed چک‌لیست ناموفق ثبت شده است.")
            if(size<5 && newReceivables>0) add("مطالبات جدید روز: $newReceivables ریال")
            if(size<5 && outstanding>0) add("مانده مطالبات: $outstanding ریال")
        }.take(5)
        val recommendations=importantIssues.mapNotNull { issue ->
            when(ManagementIssueType.entries.firstOrNull { it.name==issue.type }) {
                ManagementIssueType.LOW_STOCK -> "موجودی مرتبط قبل از شروع شیفت تأمین یا بازبینی شود."
                ManagementIssueType.FOOD_COST_VARIANCE, ManagementIssueType.ABNORMAL_INVENTORY_USAGE -> "مصرف واقعی با رسپی مؤثر و موجودی فیزیکی بررسی شود."
                ManagementIssueType.PURCHASE_PRICE_SPIKE -> "قیمت خرید با سوابق و تأمین‌کنندگان جایگزین مقایسه شود."
                ManagementIssueType.CASH_VARIANCE -> "تطبیق صندوق و اسناد دریافت/پرداخت بررسی شود."
                ManagementIssueType.CARD_SETTLEMENT_VARIANCE -> "مبالغ کارتخوان با تسویه بانکی و ثبت فروش تطبیق داده شود."
                ManagementIssueType.OVERDUE_RECEIVABLE -> "مطالبه سررسید گذشته با طرف‌حساب پیگیری شود."
                ManagementIssueType.WASTE_SPIKE -> "علت افزایش ضایعات و ثبت‌های انبار بررسی شود."
                ManagementIssueType.CHECKLIST_FAILED -> "آیتم الزامی ناموفق چک‌لیست اصلاح و مجدداً کنترل شود."
                else -> null
            }
        }.distinct().take(5)
        return DailyManagementBrief(
            businessEpochDay,branchId,profitability,liquidity,foodCost,
            null, // legacy waste source has no trustworthy branch id
            null, // legacy cash-reconciliation source has no trustworthy branch id
            critical,open,overdue,failed,events,recommendations,
        )
    }
}
