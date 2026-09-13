package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.CorrelationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttendanceSessionCalculatorTest {
    private val day = 20_000L
    private val dayStart = day * 86_400_000L

    @Test
    fun multiPunchDoesNotPayTheGapBetweenSessions() {
        val summary = AttendanceSessionCalculator.summarize(
            employeeId = 7,
            businessEpochDay = day,
            events = listOf(
                event(1, AttendanceEventType.CLOCK_IN, 8 * 60),
                event(2, AttendanceEventType.CLOCK_OUT, 12 * 60),
                event(3, AttendanceEventType.CLOCK_IN, 13 * 60),
                event(4, AttendanceEventType.CLOCK_OUT, 17 * 60),
            ),
            scheduledBreakMinutes = 60,
        )
        assertEquals(2, summary.sessionCount)
        assertEquals(480, summary.grossSessionMinutes)
        assertEquals(60, summary.breakMinutes)
        assertEquals(480, summary.workedMinutes)
        assertTrue(summary.anomalies.isEmpty())
    }

    @Test
    fun scheduledBreakIsAppliedOnlyToTheUncoveredBreakRemainder() {
        val summary = AttendanceSessionCalculator.summarize(
            employeeId = 7,
            businessEpochDay = day,
            events = listOf(
                event(1, AttendanceEventType.CLOCK_IN, 8 * 60),
                event(2, AttendanceEventType.BREAK_START, 12 * 60),
                event(3, AttendanceEventType.BREAK_END, 12 * 60 + 30),
                event(4, AttendanceEventType.CLOCK_OUT, 17 * 60),
            ),
            scheduledBreakMinutes = 60,
        )
        assertEquals(540, summary.grossSessionMinutes)
        assertEquals(30, summary.explicitBreakMinutes)
        assertEquals(60, summary.breakMinutes)
        assertEquals(480, summary.workedMinutes)
    }

    @Test
    fun unpairedPunchIsBlockingEvidenceInsteadOfInflatingWork() {
        val summary = AttendanceSessionCalculator.summarize(
            employeeId = 7,
            businessEpochDay = day,
            events = listOf(event(1, AttendanceEventType.CLOCK_IN, 8 * 60)),
            scheduledBreakMinutes = 60,
        )
        assertEquals(0, summary.workedMinutes)
        assertTrue(summary.anomalies.any { it.type == AttendanceAnomalyType.MISSING_CLOCK_OUT })
    }

    @Test
    fun overnightSessionUsesAbsoluteTimestamps() {
        val inEvent = event(1, AttendanceEventType.CLOCK_IN, 22 * 60)
        val outEvent = event(2, AttendanceEventType.CLOCK_OUT, 6 * 60, nextDay = true)
        val summary = AttendanceSessionCalculator.summarize(7, day, listOf(inEvent, outEvent), scheduledBreakMinutes = 60)
        assertEquals(420, summary.workedMinutes)
        assertTrue(summary.anomalies.isEmpty())
    }

    private fun event(id: Long, type: AttendanceEventType, minute: Int, nextDay: Boolean = false) = AttendanceEvent(
        id = id,
        employeeId = 7,
        eventType = type,
        businessEpochDay = day,
        timestampEpochMillis = dayStart + (if (nextDay) 86_400_000L else 0L) + minute * 60_000L,
        minuteOfDay = minute,
        source = AttendanceSource.DEVICE,
        deviceId = "test",
        locationId = 1,
        createdByActorId = 1,
        reason = null,
        correlationId = CorrelationId.parse("legacy:attendance:$id"),
    )
}
