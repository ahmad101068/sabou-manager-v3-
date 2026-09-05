package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.domain.sales.SalesDashboardSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SalesDashboardLoadStatus { LOADING, LOADED, EMPTY, ERROR }

data class SalesDashboardUi(
    val status: SalesDashboardLoadStatus = SalesDashboardLoadStatus.LOADING,
    val period: DashboardPeriod = DashboardPeriod.TODAY,
    val salesTitle: String = "فروش امروز",
    val comparisonLabel: String = "نسبت به دیروز",
    val salesRial: Long = 0L,
    val salesDisplay: String = "",
    val salesTrend: MetricTrend = MetricTrend(0, 0, 0.0, TrendDirection.SAME),
    val salesTrendText: String = "",
    val performanceText: String = "",
    val invoiceCount: Int = 0,
    val invoiceCountDisplay: String = "",
    val averageInvoiceRial: Long = 0L,
    val averageInvoiceDisplay: String = "",
    val newCustomerCount: Int = 0,
    val newCustomerCountDisplay: String = "",
    val receivablesRial: Long = 0L,
    val receivablesDisplay: String = "",
    val returnsRial: Long = 0L,
    val returnsDisplay: String = "",
    val message: String? = null,
)

internal object SalesDashboardPresenter {
    fun loading(period: DashboardPeriod): SalesDashboardUi = base(period).copy(
        status = SalesDashboardLoadStatus.LOADING,
        message = "در حال دریافت خلاصه فروش…",
    )

    fun error(period: DashboardPeriod): SalesDashboardUi = base(period).copy(
        status = SalesDashboardLoadStatus.ERROR,
        message = "دریافت خلاصه فروش انجام نشد. دوباره تلاش کنید.",
    )

    fun present(
        period: DashboardPeriod,
        current: SalesDashboardSummary,
        previous: SalesDashboardSummary,
    ): SalesDashboardUi {
        val labels = DashboardPeriodLabelProvider.labels(period)
        val trend = DashboardTrendCalculator.calculate(current.netSalesRial, previous.netSalesRial)
        val hasAnyOperationalData = current.hasPeriodActivity || current.customerReceivablesRial != 0L
        val status = if (hasAnyOperationalData) SalesDashboardLoadStatus.LOADED else SalesDashboardLoadStatus.EMPTY
        return SalesDashboardUi(
            status = status,
            period = period,
            salesTitle = labels.salesTitle,
            comparisonLabel = labels.comparisonSuffix,
            salesRial = current.netSalesRial,
            salesDisplay = ErpDisplayFormatters.money(current.netSalesRial),
            salesTrend = trend,
            salesTrendText = DashboardPresentationFormatter.trendText(trend, labels.comparisonSuffix),
            performanceText = DashboardPerformanceTextResolver.resolve(trend, current.hasPeriodActivity),
            invoiceCount = current.invoiceCount,
            invoiceCountDisplay = ErpDisplayFormatters.integer(current.invoiceCount),
            averageInvoiceRial = current.averageInvoiceRial,
            averageInvoiceDisplay = ErpDisplayFormatters.money(current.averageInvoiceRial),
            newCustomerCount = current.newCustomerCount,
            newCustomerCountDisplay = ErpDisplayFormatters.integer(current.newCustomerCount),
            receivablesRial = current.customerReceivablesRial,
            receivablesDisplay = ErpDisplayFormatters.money(current.customerReceivablesRial),
            returnsRial = current.returnRial,
            returnsDisplay = ErpDisplayFormatters.money(current.returnRial),
            message = if (status == SalesDashboardLoadStatus.EMPTY) "هنوز داده‌ای برای این بازه ثبت نشده" else null,
        )
    }

    private fun base(period: DashboardPeriod): SalesDashboardUi {
        val labels = DashboardPeriodLabelProvider.labels(period)
        return SalesDashboardUi(period = period, salesTitle = labels.salesTitle, comparisonLabel = labels.comparisonSuffix)
    }
}

internal data class SalesDashboardQueryRange(
    val business: DashboardEpochRange,
    val createdFromEpochMillis: Long,
    val createdToEpochMillisExclusive: Long,
)

internal object SalesDashboardRangeResolver {
    fun current(todayEpochDay: Long, period: DashboardPeriod, zoneId: ZoneId = ZoneId.systemDefault()): SalesDashboardQueryRange {
        require(period != DashboardPeriod.CUSTOM) { "داشبورد فروش فقط بازه‌های امروز، هفته و ماه را پشتیبانی می‌کند." }
        val range = DashboardPeriodRanges.currentRange(todayEpochDay, period, 0L to 0L)
        return range.toQueryRange(zoneId)
    }

    fun previous(current: SalesDashboardQueryRange, period: DashboardPeriod, zoneId: ZoneId = ZoneId.systemDefault()): SalesDashboardQueryRange {
        require(period != DashboardPeriod.CUSTOM) { "داشبورد فروش فقط بازه‌های امروز، هفته و ماه را پشتیبانی می‌کند." }
        return DashboardPeriodRanges.previousRange(current.business, period).toQueryRange(zoneId)
    }

    private fun DashboardEpochRange.toQueryRange(zoneId: ZoneId): SalesDashboardQueryRange {
        val fromDate = LocalDate.ofEpochDay(fromEpochDay)
        val toDateExclusive = LocalDate.ofEpochDay(toEpochDay).plusDays(1)
        return SalesDashboardQueryRange(
            business = this,
            createdFromEpochMillis = fromDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            createdToEpochMillisExclusive = toDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        )
    }
}

internal fun localEpochDayAt(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().toEpochDay()
