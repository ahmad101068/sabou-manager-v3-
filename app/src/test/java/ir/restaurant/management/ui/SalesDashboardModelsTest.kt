package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import ir.restaurant.management.domain.sales.SalesDashboardSummary
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SalesDashboardModelsTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun weekUsesCurrentAndPreviousComparableRanges() {
        val today = LocalDate.of(2026, 8, 13).toEpochDay()
        val current = SalesDashboardRangeResolver.current(today, DashboardPeriod.WEEK, utc)
        val previous = SalesDashboardRangeResolver.previous(current, DashboardPeriod.WEEK, utc)
        assertEquals(current.business.fromEpochDay - 7, previous.business.fromEpochDay)
        assertEquals(current.business.toEpochDay - 7, previous.business.toEpochDay)
    }

    @Test
    fun monthUsesComparableElapsedDaysFromPreviousMonth() {
        val today = LocalDate.of(2026, 8, 13).toEpochDay()
        val current = SalesDashboardRangeResolver.current(today, DashboardPeriod.MONTH, utc)
        val previous = SalesDashboardRangeResolver.previous(current, DashboardPeriod.MONTH, utc)
        assertEquals(LocalDate.of(2026, 7, 1).toEpochDay(), previous.business.fromEpochDay)
        assertEquals(LocalDate.of(2026, 7, 13).toEpochDay(), previous.business.toEpochDay)
    }

    @Test
    fun presenterUsesRealTrendAndPeriodLabels() {
        val ui = SalesDashboardPresenter.present(
            DashboardPeriod.WEEK,
            SalesDashboardSummary(netSalesRial = 1500, invoiceNetRial = 1500, invoiceCount = 3),
            SalesDashboardSummary(netSalesRial = 1000, invoiceNetRial = 1000, invoiceCount = 2),
        )
        assertEquals("فروش این هفته", ui.salesTitle)
        assertEquals("نسبت به هفته قبل", ui.comparisonLabel)
        assertEquals(TrendDirection.UP, ui.salesTrend.direction)
        assertEquals(50.0, ui.salesTrend.percentage)
        assertTrue(ui.salesTrendText.contains("۵۰"))
    }

    @Test
    fun zeroBaselineNeverProducesInfinityOrNan() {
        val ui = SalesDashboardPresenter.present(
            DashboardPeriod.MONTH,
            SalesDashboardSummary(netSalesRial = 1000, invoiceNetRial = 1000, invoiceCount = 1),
            SalesDashboardSummary(netSalesRial = 0),
        )
        assertEquals(null, ui.salesTrend.percentage)
        assertEquals("شروع فعالیت", ui.salesTrendText)
        assertFalse(ui.salesTrendText.contains("Infinity", ignoreCase = true))
        assertFalse(ui.salesTrendText.contains("NaN", ignoreCase = true))
    }

    @Test
    fun noPeriodOrOperationalDataIsEmpty() {
        val ui = SalesDashboardPresenter.present(DashboardPeriod.TODAY, SalesDashboardSummary(), SalesDashboardSummary())
        assertEquals(SalesDashboardLoadStatus.EMPTY, ui.status)
        assertEquals("هنوز داده‌ای برای این بازه ثبت نشده", ui.message)
    }
}
