package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceCalculationEngineTest {
    private val day = 20_000L
    private val dayMillis = 86_400_000L
    private val minuteMillis = 60_000L

    private fun epoch(dayOffset: Long, minute: Int): Long =
        (day + dayOffset) * dayMillis + minute * minuteMillis

    @Test
    fun `night shift crosses midnight and keeps business day`() {
        val result = AttendanceCalculationEngine.calculate(
            businessEpochDay = day,
            checkInMinute = 18 * 60,
            checkOutMinute = 2 * 60,
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = epoch(0, 18 * 60),
                plannedEndEpochMillis = epoch(1, 2 * 60),
                breakMinutes = 30,
                graceInMinutes = 0,
                graceOutMinutes = 0,
                overtimeEligible = true,
                overtimeRequiresApproval = false,
            ),
        )

        assertEquals(450, result.workedMinutes)
        assertEquals(epoch(1, 2 * 60), result.actualCheckOutEpochMillis)
        assertEquals(0, result.rawLateMinutes)
        assertEquals(0, result.rawEarlyLeaveMinutes)
    }

    @Test
    fun `late raw and payable honor grace independently`() {
        val result = AttendanceCalculationEngine.calculate(
            businessEpochDay = day,
            checkInMinute = 18 * 60 + 17,
            checkOutMinute = 2 * 60,
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = epoch(0, 18 * 60),
                plannedEndEpochMillis = epoch(1, 2 * 60),
                breakMinutes = 0,
                graceInMinutes = 10,
                graceOutMinutes = 0,
                overtimeEligible = true,
                overtimeRequiresApproval = true,
            ),
        )

        assertEquals(17, result.rawLateMinutes)
        assertEquals(7, result.payableLateMinutes)
    }

    @Test
    fun `early leave is independent and grace aware`() {
        val result = AttendanceCalculationEngine.calculate(
            businessEpochDay = day,
            checkInMinute = 18 * 60,
            checkOutMinute = 1 * 60 + 30,
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = epoch(0, 18 * 60),
                plannedEndEpochMillis = epoch(1, 2 * 60),
                breakMinutes = 0,
                graceInMinutes = 0,
                graceOutMinutes = 5,
                overtimeEligible = true,
                overtimeRequiresApproval = true,
            ),
        )

        assertEquals(30, result.rawEarlyLeaveMinutes)
        assertEquals(25, result.payableEarlyLeaveMinutes)
        assertEquals(0, result.rawOvertimeMinutes)
    }

    @Test
    fun `payroll receives only approved overtime when approval is required`() {
        val result = AttendanceCalculationEngine.calculate(
            businessEpochDay = day,
            checkInMinute = 18 * 60,
            checkOutMinute = 3 * 60 + 32,
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = epoch(0, 18 * 60),
                plannedEndEpochMillis = epoch(1, 2 * 60),
                breakMinutes = 0,
                graceInMinutes = 0,
                graceOutMinutes = 0,
                overtimeEligible = true,
                overtimeRequiresApproval = true,
            ),
            approvedOvertimeMinutes = 60,
        )

        assertEquals(92, result.rawOvertimeMinutes)
        assertEquals(60, result.approvedOvertimeMinutes)
        assertEquals(60, result.payrollOvertimeMinutes)
    }

    @Test
    fun `unapproved overtime is zero for payroll`() {
        val result = AttendanceCalculationEngine.calculate(
            businessEpochDay = day,
            checkInMinute = 18 * 60,
            checkOutMinute = 3 * 60,
            shift = AttendanceCalculationEngine.ShiftInput(
                plannedStartEpochMillis = epoch(0, 18 * 60),
                plannedEndEpochMillis = epoch(1, 2 * 60),
                breakMinutes = 0,
                graceInMinutes = 0,
                graceOutMinutes = 0,
                overtimeEligible = true,
                overtimeRequiresApproval = true,
            ),
        )

        assertEquals(60, result.rawOvertimeMinutes)
        assertEquals(0, result.approvedOvertimeMinutes)
        assertEquals(0, result.payrollOvertimeMinutes)
    }
}
