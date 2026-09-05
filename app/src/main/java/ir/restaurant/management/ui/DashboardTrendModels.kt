package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import java.time.LocalDate
import kotlin.math.abs

enum class TrendDirection { UP, DOWN, SAME, NOT_AVAILABLE }

data class MetricTrend(
    val currentValue: Long,
    val previousValue: Long,
    val percentage: Double?,
    val direction: TrendDirection,
)

internal object DashboardTrendCalculator {
    fun calculate(currentValue: Long, previousValue: Long): MetricTrend {
        if (previousValue == 0L) {
            return when {
                currentValue == 0L -> MetricTrend(currentValue, previousValue, 0.0, TrendDirection.SAME)
                currentValue > 0L -> MetricTrend(currentValue, previousValue, null, TrendDirection.UP)
                else -> MetricTrend(currentValue, previousValue, null, TrendDirection.DOWN)
            }
        }

        val percentage = ((currentValue.toDouble() - previousValue.toDouble()) / previousValue.toDouble()) * 100.0
        val safePercentage = percentage.takeIf { it.isFinite() }
        val direction = when {
            safePercentage == null -> TrendDirection.NOT_AVAILABLE
            safePercentage > 0.0 -> TrendDirection.UP
            safePercentage < 0.0 -> TrendDirection.DOWN
            else -> TrendDirection.SAME
        }
        return MetricTrend(currentValue, previousValue, safePercentage, direction)
    }
}

data class DashboardPeriodLabels(
    val salesTitle: String,
    val invoicesTitle: String,
    val expensesTitle: String,
    val overviewTitle: String,
    val comparisonSuffix: String,
)

internal object DashboardPeriodLabelProvider {
    fun labels(period: DashboardPeriod): DashboardPeriodLabels = when (period) {
        DashboardPeriod.TODAY -> DashboardPeriodLabels(
            salesTitle = "فروش امروز",
            invoicesTitle = "فاکتورهای امروز",
            expensesTitle = "هزینه امروز",
            overviewTitle = "نمای کلی امروز",
            comparisonSuffix = "نسبت به دیروز",
        )
        DashboardPeriod.WEEK -> DashboardPeriodLabels(
            salesTitle = "فروش این هفته",
            invoicesTitle = "فاکتورهای این هفته",
            expensesTitle = "هزینه این هفته",
            overviewTitle = "نمای کلی این هفته",
            comparisonSuffix = "نسبت به هفته قبل",
        )
        DashboardPeriod.MONTH -> DashboardPeriodLabels(
            salesTitle = "فروش این ماه",
            invoicesTitle = "فاکتورهای این ماه",
            expensesTitle = "هزینه این ماه",
            overviewTitle = "نمای کلی این ماه",
            comparisonSuffix = "نسبت به ماه قبل",
        )
        DashboardPeriod.CUSTOM -> DashboardPeriodLabels(
            salesTitle = "فروش بازه",
            invoicesTitle = "فاکتورهای بازه",
            expensesTitle = "هزینه بازه",
            overviewTitle = "نمای کلی بازه",
            comparisonSuffix = "نسبت به بازه قبل",
        )
    }
}

internal data class DashboardEpochRange(val fromEpochDay: Long, val toEpochDay: Long) {
    init {
        require(fromEpochDay > 0L && toEpochDay >= fromEpochDay)
    }

    val dayCount: Long get() = toEpochDay - fromEpochDay + 1L
}

internal object DashboardPeriodRanges {
    fun currentRange(todayEpochDay: Long, period: DashboardPeriod, customRange: Pair<Long, Long>): DashboardEpochRange {
        require(todayEpochDay > 0L)
        val today = LocalDate.ofEpochDay(todayEpochDay)
        return when (period) {
            DashboardPeriod.TODAY -> DashboardEpochRange(todayEpochDay, todayEpochDay)
            DashboardPeriod.WEEK -> DashboardEpochRange(
                fromEpochDay = today.minusDays(today.dayOfWeek.value.toLong() - 1L).toEpochDay(),
                toEpochDay = todayEpochDay,
            )
            DashboardPeriod.MONTH -> DashboardEpochRange(
                fromEpochDay = today.withDayOfMonth(1).toEpochDay(),
                toEpochDay = todayEpochDay,
            )
            DashboardPeriod.CUSTOM -> DashboardEpochRange(customRange.first, customRange.second)
        }
    }

    fun previousRange(current: DashboardEpochRange, period: DashboardPeriod): DashboardEpochRange = when (period) {
        DashboardPeriod.TODAY -> DashboardEpochRange(current.fromEpochDay - 1L, current.toEpochDay - 1L)
        DashboardPeriod.WEEK -> DashboardEpochRange(current.fromEpochDay - 7L, current.toEpochDay - 7L)
        DashboardPeriod.MONTH -> previousMonthComparableRange(current)
        DashboardPeriod.CUSTOM -> {
            val previousTo = current.fromEpochDay - 1L
            DashboardEpochRange(previousTo - current.dayCount + 1L, previousTo)
        }
    }

    private fun previousMonthComparableRange(current: DashboardEpochRange): DashboardEpochRange {
        val currentFrom = LocalDate.ofEpochDay(current.fromEpochDay)
        val currentTo = LocalDate.ofEpochDay(current.toEpochDay)
        val previousMonthStart = currentFrom.minusMonths(1L).withDayOfMonth(1)
        val elapsedDayIndex = currentTo.toEpochDay() - currentFrom.toEpochDay()
        val previousMonthEnd = previousMonthStart.withDayOfMonth(previousMonthStart.lengthOfMonth())
        val comparableEnd = previousMonthStart.plusDays(elapsedDayIndex).let { candidate ->
            if (candidate.isAfter(previousMonthEnd)) previousMonthEnd else candidate
        }
        return DashboardEpochRange(previousMonthStart.toEpochDay(), comparableEnd.toEpochDay())
    }
}

internal object DashboardPresentationFormatter {
    fun integer(value: Long): String = ErpDisplayFormatters.integer(value)

    fun compactToman(rial: Long): String = ErpDisplayFormatters.integer(rial / 10L)

    fun trendText(trend: MetricTrend, comparisonSuffix: String): String = when {
        trend.direction == TrendDirection.NOT_AVAILABLE -> "مقایسه در دسترس نیست"
        trend.previousValue == 0L && trend.currentValue > 0L -> "شروع فعالیت"
        trend.previousValue == 0L && trend.currentValue < 0L -> "مقایسه در دسترس نیست"
        trend.percentage == null || !trend.percentage.isFinite() -> "مقایسه در دسترس نیست"
        else -> {
            val signed = ErpDisplayFormatters.percentage(trend.percentage) ?: return "مقایسه در دسترس نیست"
            "$signed $comparisonSuffix"
        }
    }
}

internal object DashboardPerformanceTextResolver {
    fun resolve(salesTrend: MetricTrend, hasCurrentActivity: Boolean): String {
        if (!hasCurrentActivity) return "هنوز داده‌ای برای این بازه ثبت نشده"
        if (salesTrend.currentValue == 0L && salesTrend.previousValue == 0L) return "برای این بازه فروش ثبت نشده"
        if (salesTrend.previousValue == 0L && salesTrend.currentValue > 0L) return "فعالیت فروش در این دوره آغاز شده است"
        val percentage = salesTrend.percentage
        if (percentage == null || !percentage.isFinite()) return "برای مقایسه فروش داده کافی نیست"
        return when {
            abs(percentage) < 1.0 -> "عملکرد این دوره پایدار است"
            salesTrend.direction == TrendDirection.UP -> "عملکرد این دوره رو به رشد است"
            salesTrend.direction == TrendDirection.DOWN -> "فروش نسبت به دوره قبل کاهش داشته"
            else -> "عملکرد این دوره پایدار است"
        }
    }
}
