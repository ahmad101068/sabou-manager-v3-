package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendancePayrollCalculatorTest {
    @Test fun convertsAttendanceToPayrollAdjustments() {
        val summary = AttendanceSummary(1, 1, 30, 20, 2, 1, 0, 9600, 30, 120)
        val result = AttendancePayrollCalculator.calculate(summary, AttendancePayrollPolicy(600_000, 2_000_000, 10_000))
        assertEquals(1_200_000, result.overtimeRial)
        assertEquals(4_000_000, result.absenceDeductionRial)
        assertEquals(300_000, result.lateDeductionRial)
        assertEquals(4_300_000, result.totalAttendanceDeductionRial)
    }
}
