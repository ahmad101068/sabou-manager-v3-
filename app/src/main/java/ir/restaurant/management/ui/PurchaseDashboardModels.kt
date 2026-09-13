package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.domain.operations.PurchaseDashboardSummary

enum class PurchaseDashboardLoadStatus { LOADING, LOADED, EMPTY, ERROR }

data class PurchaseDashboardUi(
    val status: PurchaseDashboardLoadStatus = PurchaseDashboardLoadStatus.LOADING,
    val period: DashboardPeriod = DashboardPeriod.TODAY,
    val purchaseTitle: String = "خرید امروز",
    val periodPurchaseRial: Long = 0L,
    val periodPurchaseDisplay: String = "",
    val openOrderCount: Int = 0,
    val openOrderDisplay: String = "",
    val activeSupplierCount: Int = 0,
    val activeSupplierDisplay: String = "",
    val supplierPayablesRial: Long = 0L,
    val supplierPayablesDisplay: String = "",
    val pendingReceiptCount: Int = 0,
    val pendingReceiptDisplay: String = "",
    val openRequisitionCount: Int = 0,
    val openRequisitionDisplay: String = "",
    val pendingApprovalCount: Int = 0,
    val overdueOrderCount: Int = 0,
    val message: String? = null,
)

internal object PurchaseDashboardPresenter {
    fun loading(period: DashboardPeriod) = base(period).copy(
        status = PurchaseDashboardLoadStatus.LOADING,
        message = "در حال دریافت خلاصه خرید…",
    )

    fun error(period: DashboardPeriod) = base(period).copy(
        status = PurchaseDashboardLoadStatus.ERROR,
        message = "دریافت خلاصه خرید انجام نشد. دوباره تلاش کنید.",
    )

    fun present(period: DashboardPeriod, summary: PurchaseDashboardSummary): PurchaseDashboardUi {
        val hasAny = summary.periodPurchaseRial != 0L || summary.openOrderCount > 0 || summary.supplierPayablesRial != 0L ||
            summary.pendingReceiptCount > 0 || summary.openRequisitionCount > 0
        return PurchaseDashboardUi(
            status = if (hasAny) PurchaseDashboardLoadStatus.LOADED else PurchaseDashboardLoadStatus.EMPTY,
            period = period,
            purchaseTitle = purchaseTitle(period),
            periodPurchaseRial = summary.periodPurchaseRial,
            periodPurchaseDisplay = ErpDisplayFormatters.money(summary.periodPurchaseRial),
            openOrderCount = summary.openOrderCount,
            openOrderDisplay = ErpDisplayFormatters.integer(summary.openOrderCount),
            activeSupplierCount = summary.activeSupplierCount,
            activeSupplierDisplay = ErpDisplayFormatters.integer(summary.activeSupplierCount),
            supplierPayablesRial = summary.supplierPayablesRial,
            supplierPayablesDisplay = ErpDisplayFormatters.money(summary.supplierPayablesRial),
            pendingReceiptCount = summary.pendingReceiptCount,
            pendingReceiptDisplay = ErpDisplayFormatters.integer(summary.pendingReceiptCount),
            openRequisitionCount = summary.openRequisitionCount,
            openRequisitionDisplay = ErpDisplayFormatters.integer(summary.openRequisitionCount),
            pendingApprovalCount = summary.pendingApprovalCount,
            overdueOrderCount = summary.overdueOrderCount,
            message = if (hasAny) null else "هنوز داده‌ای برای این بازه ثبت نشده",
        )
    }

    fun purchaseTitle(period: DashboardPeriod): String = when (period) {
        DashboardPeriod.TODAY -> "خرید امروز"
        DashboardPeriod.WEEK -> "خرید این هفته"
        DashboardPeriod.MONTH -> "خرید این ماه"
        DashboardPeriod.CUSTOM -> "خرید بازه"
    }

    private fun base(period: DashboardPeriod) = PurchaseDashboardUi(period = period, purchaseTitle = purchaseTitle(period))
}
