package ir.restaurant.management.domain.sales

import ir.restaurant.management.core.SignedLongMath

enum class DailySalesStatus { DRAFT, CONFIRMED, POSTED, VOIDED }
enum class SalesSettlementType { CASH, CARD, BANK_TRANSFER, PERSONAL_CREDIT, CORPORATE_CREDIT }
enum class PartyFinancialType { PERSON, COMPANY }

object DailySalesLifecycle {
    private val allowed = mapOf(
        DailySalesStatus.DRAFT to setOf(DailySalesStatus.CONFIRMED, DailySalesStatus.VOIDED),
        DailySalesStatus.CONFIRMED to setOf(DailySalesStatus.DRAFT, DailySalesStatus.POSTED, DailySalesStatus.VOIDED),
        DailySalesStatus.POSTED to setOf(DailySalesStatus.VOIDED),
        DailySalesStatus.VOIDED to emptySet(),
    )

    fun requireTransition(from: DailySalesStatus, to: DailySalesStatus) {
        require(to in allowed.getValue(from)) { "گذار وضعیت فروش روزانه مجاز نیست: $from → $to" }
    }

    fun requireDirectEdit(status: DailySalesStatus) {
        require(status == DailySalesStatus.DRAFT) { "فقط فروش روزانه DRAFT قابل ویرایش مستقیم است." }
    }
}

data class DailySalesSettlementDraft(
    val type: SalesSettlementType,
    val amountRial: Long,
    val cashboxId: Long? = null,
    val bankAccountId: Long? = null,
    val cardTerminalId: Long? = null,
    val partyId: Long? = null,
    val dueEpochDay: Long? = null,
    val contractId: Long? = null,
    val referenceNumber: String? = null,
    val note: String? = null,
) {
    fun validated(): DailySalesSettlementDraft {
        require(amountRial >= 0) { "مبلغ تسویه نمی‌تواند منفی باشد." }
        require(dueEpochDay == null || dueEpochDay > 0) { "تاریخ سررسید معتبر نیست." }
        when (type) {
            SalesSettlementType.PERSONAL_CREDIT,
            SalesSettlementType.CORPORATE_CREDIT -> require(partyId != null && partyId > 0) { "فروش اعتباری نیازمند طرف‌حساب است." }
            else -> Unit
        }
        return copy(referenceNumber = referenceNumber?.trim()?.takeIf(String::isNotEmpty), note = note?.trim()?.takeIf(String::isNotEmpty))
    }
}

data class DailySalesPostingDraft(
    val branchId: Long,
    val businessEpochDay: Long,
    val grossSalesRial: Long,
    val discountRial: Long,
    val returnRial: Long,
    val settlements: List<DailySalesSettlementDraft>,
    val serviceRevenueRial: Long = 0L,
    val taxPayableRial: Long = 0L,
) {
    val netSalesRial: Long get() = SignedLongMath.subtract(SignedLongMath.subtract(grossSalesRial, discountRial), returnRial)
    val revenueRial: Long get() = SignedLongMath.add(netSalesRial, serviceRevenueRial)
    val amountToSettleRial: Long get() = SignedLongMath.add(revenueRial, taxPayableRial)
    val settlementTotalRial: Long get() = settlements.fold(0L) { total, row -> SignedLongMath.add(total, row.amountRial) }
    fun validated(): DailySalesPostingDraft {
        require(branchId > 0 && businessEpochDay > 0) { "شعبه و تاریخ فروش معتبر نیست." }
        require(listOf(grossSalesRial, discountRial, returnRial, serviceRevenueRial, taxPayableRial).all { it >= 0 }) { "مبالغ فروش نمی‌توانند منفی باشند." }
        require(discountRial <= grossSalesRial) { "تخفیف از فروش ناخالص بیشتر است." }
        require(returnRial <= SignedLongMath.subtract(grossSalesRial, discountRial)) { "برگشت فروش از مبلغ قابل برگشت بیشتر است." }
        settlements.forEach { it.validated() }
        require(settlementTotalRial == amountToSettleRial) { "جمع Settlementها باید دقیقاً برابر مبلغ قابل تسویه (Revenue + Tax) باشد." }
        return this
    }
}

data class LiquiditySnapshot(
    val cashReceivedRial: Long,
    val cardReceivedRial: Long,
    val transferReceivedRial: Long,
    val oldReceivableCollectionsRial: Long,
    val newReceivablesRial: Long,
    val outstandingReceivablesRial: Long,
)

data class ProfitabilitySnapshot(
    val grossSalesRial: Long,
    val discountRial: Long,
    val returnRial: Long,
    val netSalesRial: Long,
    val serviceRevenueRial: Long,
    val taxPayableRial: Long,
    val revenueRial: Long,
    val cogsRial: Long?,
    val grossProfitRial: Long?,
    val operatingExpensesRial: Long?,
    val payrollRial: Long?,
    val estimatedOperatingProfitRial: Long?,
    val isCogsComplete: Boolean = cogsRial != null,
    val isExpenseDataComplete: Boolean = operatingExpensesRial != null,
    val isPayrollDataComplete: Boolean = payrollRial != null,
    val isEstimatedProfitAvailable: Boolean = estimatedOperatingProfitRial != null,
    val unavailableReason: String? = null,
)
