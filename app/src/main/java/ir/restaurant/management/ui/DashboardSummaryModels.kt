package ir.restaurant.management.ui

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.accounting.AccountType
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.recipe.RecipeLifecycleState
import ir.restaurant.management.domain.treasury.TreasuryTransactionKind

private fun Iterable<Long>.safeMoneySum(): Long = fold(0L, SignedLongMath::add)

data class AccountingDashboardSummary(
    val cashAndBankRial: Long,
    val receivablesRial: Long,
    val payablesRial: Long,
    val netProfitRial: Long,
    val activeJournalCount: Int,
    val reversedJournalCount: Int,
    val isBalanced: Boolean,
)

internal fun accountingDashboardSummary(state: AccountingUiState): AccountingDashboardSummary {
    val cashAndBank = state.accounts
        .filter { it.code.startsWith("11") || it.code.startsWith("12") }
        .map { SignedLongMath.subtract(it.debitBalanceRial, it.creditBalanceRial) }
        .safeMoneySum()
    val receivables = state.accounts
        .filter { it.code.startsWith("13") }
        .map { SignedLongMath.subtract(it.debitBalanceRial, it.creditBalanceRial) }
        .safeMoneySum()
    val payables = state.accounts
        .filter { it.type == AccountType.LIABILITY }
        .map { SignedLongMath.subtract(it.creditBalanceRial, it.debitBalanceRial) }
        .safeMoneySum()
    return AccountingDashboardSummary(
        cashAndBankRial = cashAndBank,
        receivablesRial = receivables,
        payablesRial = payables,
        netProfitRial = state.profitLoss.netProfitRial,
        activeJournalCount = state.journals.count { !it.isReversed },
        reversedJournalCount = state.journals.count { it.isReversed },
        isBalanced = state.trialBalance.isBalanced,
    )
}

data class TreasuryDashboardSummary(
    val totalBalanceRial: Long,
    val activeAccountCount: Int,
    val recentReceiptRial: Long,
    val recentPaymentRial: Long,
    val postedTransactionCount: Int,
)

internal fun treasuryDashboardSummary(state: TreasuryUiState): TreasuryDashboardSummary = TreasuryDashboardSummary(
    totalBalanceRial = state.balances.values.safeMoneySum(),
    activeAccountCount = state.accounts.count { it.isActive },
    recentReceiptRial = state.transactions.filter { it.kind == TreasuryTransactionKind.RECEIPT }.map { it.amountRial }.safeMoneySum(),
    recentPaymentRial = state.transactions.filter { it.kind == TreasuryTransactionKind.PAYMENT }.map { it.amountRial }.safeMoneySum(),
    postedTransactionCount = state.transactions.count { it.status == "POSTED" },
)

data class CrmDashboardSummary(
    val activeCustomers: Int,
    val debtorCustomers: Int,
    val onHoldCustomers: Int,
    val totalReceivableRial: Long,
    val nearCreditLimitCustomers: Int,
)

internal fun crmDashboardSummary(state: CrmUiState): CrmDashboardSummary = CrmDashboardSummary(
    activeCustomers = state.customers.count { it.isActive && it.status == "ACTIVE" },
    debtorCustomers = state.customers.count { it.outstandingRial > 0L },
    onHoldCustomers = state.customers.count { it.status == "ON_HOLD" },
    totalReceivableRial = state.customers.map { it.outstandingRial.coerceAtLeast(0L) }.safeMoneySum(),
    nearCreditLimitCustomers = state.customers.count {
        it.creditLimitRial > 0L && it.outstandingRial > 0L &&
            it.outstandingRial.toDouble() / it.creditLimitRial.toDouble() >= 0.80
    },
)

internal fun customerMatches(customer: ir.restaurant.management.domain.sales.CustomerRecord, query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isBlank()) return true
    return listOf(customer.name, customer.customerCode, customer.phone, customer.mobile, customer.nationalId, customer.branch)
        .any { it.contains(normalized, ignoreCase = true) }
}

data class SupplierDashboardSummary(
    val activeSuppliers: Int,
    val averagePaymentTermsDays: Int,
    val openPurchaseCount: Int,
    val payableRial: Long,
    val dueSettlementCount: Int,
)

internal fun supplierDashboardSummary(state: OperationsUiState): SupplierDashboardSummary = SupplierDashboardSummary(
    activeSuppliers = state.suppliers.size,
    averagePaymentTermsDays = if (state.suppliers.isEmpty()) 0 else state.suppliers.map { it.paymentTermsDays }.average().toInt(),
    openPurchaseCount = state.purchases.count { !it.isPaid },
    payableRial = state.purchases.map { it.outstandingRial.coerceAtLeast(0L) }.safeMoneySum(),
    dueSettlementCount = state.settlementAlerts.size,
)

data class PersonnelDashboardSummary(
    val activeEmployees: Int,
    val presentToday: Int,
    val absentToday: Int,
    val onLeaveToday: Int,
    val pendingLeaveCount: Int,
    val lateTodayCount: Int,
)

internal fun personnelDashboardSummary(state: PersonnelUiState, todayEpochDay: Long): PersonnelDashboardSummary {
    val todayAttendance = state.attendance.filter { it.workEpochDay == todayEpochDay }
    val approvedLeaveEmployeeIds = state.leaves
        .filter { it.status == "APPROVED" && todayEpochDay in it.startEpochDay..it.endEpochDay }
        .mapTo(mutableSetOf()) { it.employeeId }
    return PersonnelDashboardSummary(
        activeEmployees = state.employees.count { it.isActive },
        presentToday = todayAttendance.count { it.status == "PRESENT" || it.checkInMinute != null },
        absentToday = todayAttendance.count { it.status == "ABSENT" },
        onLeaveToday = approvedLeaveEmployeeIds.size,
        pendingLeaveCount = state.pendingLeaves.size,
        lateTodayCount = todayAttendance.count { it.lateMinutes > 0 },
    )
}

data class PayrollDashboardSummary(
    val activePeriodKey: String?,
    val activePeriodStatus: PayrollPeriodStatus?,
    val employeeCount: Int,
    val grossRial: Long,
    val deductionsRial: Long,
    val netRial: Long,
    val paidRial: Long,
    val remainingRial: Long,
    val reviewCount: Int,
    val exceptionCount: Int,
)

internal fun payrollDashboardSummary(state: HrPayrollUiState): PayrollDashboardSummary {
    val period = state.periods.firstOrNull { it.status !in setOf(PayrollPeriodStatus.CLOSED, PayrollPeriodStatus.LEGACY, PayrollPeriodStatus.LEGACY_UNKNOWN) }
    val batches = if (period == null) emptyList() else state.batches.filter {
        it.periodId == period.id && it.status !in setOf(PayrollBatchStatus.CANCELLED, PayrollBatchStatus.REVERSED, PayrollBatchStatus.LEGACY, PayrollBatchStatus.LEGACY_UNKNOWN)
    }
    return PayrollDashboardSummary(
        activePeriodKey = period?.periodKey,
        activePeriodStatus = period?.status,
        employeeCount = batches.sumOf { it.employeesIncluded },
        grossRial = batches.map { it.grossPayrollRial }.safeMoneySum(),
        deductionsRial = batches.map { it.deductionsRial }.safeMoneySum(),
        netRial = batches.map { it.netPayrollRial }.safeMoneySum(),
        paidRial = batches.map { it.paidRial }.safeMoneySum(),
        remainingRial = batches.map { it.remainingRial }.safeMoneySum(),
        reviewCount = batches.count { it.status == PayrollBatchStatus.UNDER_REVIEW },
        exceptionCount = batches.sumOf { it.exceptionCount },
    )
}

internal fun payrollStatusTitle(status: PayrollPeriodStatus?): String = when (status) {
    PayrollPeriodStatus.OPEN -> "آماده‌سازی"
    PayrollPeriodStatus.CALCULATING -> "در حال محاسبه"
    PayrollPeriodStatus.REVIEW -> "بازبینی"
    PayrollPeriodStatus.APPROVED -> "تأییدشده"
    PayrollPeriodStatus.PAYMENT -> "در حال پرداخت"
    PayrollPeriodStatus.CLOSED -> "بسته‌شده"
    PayrollPeriodStatus.REOPENED -> "بازگشایی‌شده"
    PayrollPeriodStatus.LEGACY, PayrollPeriodStatus.LEGACY_UNKNOWN -> "قدیمی"
    null -> "دوره فعالی وجود ندارد"
}

data class RecipeDashboardSummary(
    val menuItems: Int,
    val activeRecipes: Int,
    val missingRecipeCount: Int,
    val configuredWasteCount: Int,
    val draftRevisionCount: Int,
)

internal fun recipeDashboardSummary(state: RecipeUiState): RecipeDashboardSummary = RecipeDashboardSummary(
    menuItems = state.menuItems.size,
    activeRecipes = state.activeVersions.map { it.menuItemId }.distinct().size,
    missingRecipeCount = state.menuItems.count { it.ingredientCount == 0 },
    configuredWasteCount = state.menuItems.count {
        it.costProfile.preparationWasteBasisPoints > 0 || it.costProfile.cookingWasteBasisPoints > 0
    },
    draftRevisionCount = state.revisions.count { it.state == RecipeLifecycleState.DRAFT },
)

data class AssetDashboardSummary(
    val totalPurchaseValueRial: Long,
    val totalBookValueRial: Long,
    val accumulatedDepreciationRial: Long,
    val activeAssetCount: Int,
    val disposedAssetCount: Int,
    val unrecognizedAssetCount: Int,
)

internal fun assetDashboardSummary(state: AssetUiState): AssetDashboardSummary = AssetDashboardSummary(
    totalPurchaseValueRial = state.assets.map { it.purchaseCostRial }.safeMoneySum(),
    totalBookValueRial = state.assets.map { it.bookValueRial }.safeMoneySum(),
    accumulatedDepreciationRial = state.assets.map { it.accumulatedDepreciationRial }.safeMoneySum(),
    activeAssetCount = state.assets.count { it.isActive },
    disposedAssetCount = state.assets.count { !it.isActive },
    unrecognizedAssetCount = state.assets.count { !it.isAccountingRecognized },
)
