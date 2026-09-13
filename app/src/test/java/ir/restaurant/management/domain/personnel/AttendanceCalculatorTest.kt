package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceCalculatorTest {
    @Test fun calculatesLateOvertimeAndWorkedMinutes() {
        val result = AttendanceCalculator.calculate(AttendanceDraft(
            employeeId = 1, workEpochDay = 1, checkInMinute = 8 * 60 + 15, checkOutMinute = 17 * 60,
            scheduledStartMinute = 8 * 60, scheduledEndMinute = 16 * 60,
        ))
        assertEquals(525, result.workedMinutes)
        assertEquals(15, result.lateMinutes)
        assertEquals(60, result.overtimeMinutes)
    }

    @Test fun nonPresentStatusHasZeroMinutes() {
        val result = AttendanceCalculator.calculate(AttendanceDraft(employeeId = 1, workEpochDay = 1, status = "ABSENT"))
        assertEquals(0, result.workedMinutes)
        assertEquals(0, result.lateMinutes)
        assertEquals(0, result.overtimeMinutes)
    }
}
