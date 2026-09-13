package ir.restaurant.management.ui

import ir.restaurant.management.data.repository.DashboardPeriod
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTrendModelsTest {
    @Test
    fun `positive growth is calculated from current and previous values`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = 120L, previousValue = 100L)

        assertEquals(TrendDirection.UP, trend.direction)
        assertEquals(20.0, trend.percentage!!, 0.0001)
    }

    @Test
    fun `decrease is calculated from current and previous values`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = 80L, previousValue = 100L)

        assertEquals(TrendDirection.DOWN, trend.direction)
        assertEquals(-20.0, trend.percentage!!, 0.0001)
    }

    @Test
    fun `previous zero with positive current becomes activity start without percentage`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = 50L, previousValue = 0L)

        assertEquals(TrendDirection.UP, trend.direction)
        assertNull(trend.percentage)
        assertEquals("شروع فعالیت", DashboardPresentationFormatter.trendText(trend, "نسبت به دیروز"))
    }

    @Test
    fun `current zero against positive previous is minus one hundred percent`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = 0L, previousValue = 50L)

        assertEquals(TrendDirection.DOWN, trend.direction)
        assertEquals(-100.0, trend.percentage!!, 0.0001)
    }

    @Test
    fun `both zero is stable zero percent and never nan or infinity`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = 0L, previousValue = 0L)
        val text = DashboardPresentationFormatter.trendText(trend, "نسبت به دیروز")

        assertEquals(TrendDirection.SAME, trend.direction)
        assertEquals(0.0, trend.percentage!!, 0.0001)
        assertFalse(text.contains("NaN", ignoreCase = true))
        assertFalse(text.contains("Infinity", ignoreCase = true))
    }

    @Test
    fun `direction follows the calculated percentage sign for negative baselines`() {
        val trend = DashboardTrendCalculator.calculate(currentValue = -50L, previousValue = -100L)

        assertEquals(-50.0, trend.percentage!!, 0.0001)
        assertEquals(TrendDirection.DOWN, trend.direction)
    }

    @Test
    fun `week comparison uses equivalent weekdays from previous week`() {
        val today = LocalDate.of(2026, 8, 12).toEpochDay() // Wednesday
        val current = DashboardPeriodRanges.currentRange(today, DashboardPeriod.WEEK, 0L to 0L)
        val previous = DashboardPeriodRanges.previousRange(current, DashboardPeriod.WEEK)

        assertEquals(LocalDate.of(2026, 8, 10).toEpochDay(), current.fromEpochDay)
        assertEquals(LocalDate.of(2026, 8, 12).toEpochDay(), current.toEpochDay)
        assertEquals(LocalDate.of(2026, 8, 3).toEpochDay(), previous.fromEpochDay)
        assertEquals(LocalDate.of(2026, 8, 5).toEpochDay(), previous.toEpochDay)
        assertEquals(current.dayCount, previous.dayCount)
    }

    @Test
    fun `month comparison clamps to shorter previous month`() {
        val today = LocalDate.of(2026, 3, 31).toEpochDay()
        val current = DashboardPeriodRanges.currentRange(today, DashboardPeriod.MONTH, 0L to 0L)
        val previous = DashboardPeriodRanges.previousRange(current, DashboardPeriod.MONTH)

        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), current.fromEpochDay)
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), current.toEpochDay)
        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), previous.fromEpochDay)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), previous.toEpochDay)
    }

    @Test
    fun `period labels change all today week and month titles centrally`() {
        val today = DashboardPeriodLabelProvider.labels(DashboardPeriod.TODAY)
        val week = DashboardPeriodLabelProvider.labels(DashboardPeriod.WEEK)
        val month = DashboardPeriodLabelProvider.labels(DashboardPeriod.MONTH)

        assertEquals("فروش امروز", today.salesTitle)
        assertEquals("فاکتورهای امروز", today.invoicesTitle)
        assertEquals("هزینه امروز", today.expensesTitle)
        assertEquals("نسبت به دیروز", today.comparisonSuffix)

        assertEquals("فروش این هفته", week.salesTitle)
        assertEquals("فاکتورهای این هفته", week.invoicesTitle)
        assertEquals("هزینه این هفته", week.expensesTitle)
        assertEquals("نسبت به هفته قبل", week.comparisonSuffix)

        assertEquals("فروش این ماه", month.salesTitle)
        assertEquals("فاکتورهای این ماه", month.invoicesTitle)
        assertEquals("هزینه این ماه", month.expensesTitle)
        assertEquals("نسبت به ماه قبل", month.comparisonSuffix)
    }

    @Test
    fun `performance text follows real sales trend`() {
        val growth = DashboardTrendCalculator.calculate(120L, 100L)
        val stable = DashboardTrendCalculator.calculate(100L, 100L)
        val decline = DashboardTrendCalculator.calculate(80L, 100L)

        assertEquals("عملکرد این دوره رو به رشد است", DashboardPerformanceTextResolver.resolve(growth, true))
        assertEquals("عملکرد این دوره پایدار است", DashboardPerformanceTextResolver.resolve(stable, true))
        assertEquals("فروش نسبت به دوره قبل کاهش داشته", DashboardPerformanceTextResolver.resolve(decline, true))
    }
}
