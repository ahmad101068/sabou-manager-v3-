package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.CorrelationId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AttendanceEventAggregatorV2Test {
    @Test
    fun aggregatesClockSessionsBreakLateAndOvertime() {
        val result = summarize(
            event(1, AttendanceEventType.CLOCK_IN, 8 * 60 + 15),
            event(2, AttendanceEventType.BREAK_START, 12 * 60),
            event(3, AttendanceEventType.BREAK_END, 12 * 60 + 30),
            event(4, AttendanceEventType.CLOCK_OUT, 17 * 60 + 15),
        )

        assertEquals(8 * 60 + 15, result.firstInMinute)
        assertEquals(17 * 60 + 15, result.lastOutMinute)
        assertEquals(510, result.workedMinutes)
        assertEquals(30, result.breakMinutes)
        assertEquals(15, result.lateMinutes)
        assertEquals(30, result.overtimeMinutes)
        assertEquals(DailyAttendanceStatus.PRESENT, result.status)
        assertTrue(result.anomalies.isEmpty())
    }

    @Test
    fun detectsMissingOutDuplicateInAndUnclosedBreak() {
        val missing = summarize(event(1, AttendanceEventType.CLOCK_IN, 480))
        assertTrue(missing.anomalies.any { it.type == AttendanceAnomalyType.MISSING_CLOCK_OUT })

        val duplicate = summarize(
            event(1, AttendanceEventType.CLOCK_IN, 480),
            event(2, AttendanceEventType.CLOCK_IN, 481),
            event(3, AttendanceEventType.CLOCK_OUT, 960),
        )
        assertTrue(duplicate.anomalies.any { it.type == AttendanceAnomalyType.DUPLICATE_CLOCK_IN })

        val unclosedBreak = summarize(
            event(1, AttendanceEventType.CLOCK_IN, 480),
            event(2, AttendanceEventType.BREAK_START, 720),
            event(3, AttendanceEventType.CLOCK_OUT, 960),
        )
        assertTrue(unclosedBreak.anomalies.any { it.type == AttendanceAnomalyType.BREAK_NOT_CLOSED })
    }

    @Test
    fun detectsNegativeDurationExcessiveDurationAndOutsideEmployment() {
        val negativeIn = event(1, AttendanceEventType.CLOCK_IN, 600)
        val negative = summarize(
            negativeIn,
            event(2, AttendanceEventType.CLOCK_OUT, 500).copy(
                timestampEpochMillis = negativeIn.timestampEpochMillis + 1,
            ),
        )
        assertTrue(negative.anomalies.any { it.type == AttendanceAnomalyType.NEGATIVE_DURATION })

        val excessive = summarize(
            event(1, AttendanceEventType.CLOCK_IN, 60),
            event(2, AttendanceEventType.CLOCK_OUT, 1_000),
        )
        assertTrue(excessive.anomalies.any { it.type == AttendanceAnomalyType.EXCESSIVE_DURATION })

        val outside = AttendanceEventAggregator.summarize(
            employeeId = 7,
            businessEpochDay = DAY,
            events = listOf(event(1, AttendanceEventType.CLOCK_IN, 480)),
            policy = AttendanceAggregationPolicy(480, 960),
            employmentFromEpochDay = DAY + 1,
            employmentToEpochDay = null,
        )
        assertTrue(outside.anomalies.any { it.type == AttendanceAnomalyType.OUTSIDE_EMPLOYMENT_PERIOD })
    }

    private fun summarize(vararg events: AttendanceEvent) = AttendanceEventAggregator.summarize(
        employeeId = 7,
        businessEpochDay = DAY,
        events = events.toList(),
        policy = AttendanceAggregationPolicy(480, 960, maximumWorkedMinutes = 900),
        employmentFromEpochDay = DAY - 100,
        employmentToEpochDay = null,
    )

    private fun event(id: Long, type: AttendanceEventType, minute: Int) = AttendanceEvent(
        id = id,
        employeeId = 7,
        eventType = type,
        businessEpochDay = DAY,
        timestampEpochMillis = minute * 60_000L + id,
        minuteOfDay = minute,
        source = AttendanceSource.DEVICE,
        deviceId = "device-1",
        locationId = 1,
        createdByActorId = null,
        reason = null,
        correlationId = CorrelationId.parse("attendance:test:$id"),
    )

    private companion object { const val DAY = 20_000L }
}
