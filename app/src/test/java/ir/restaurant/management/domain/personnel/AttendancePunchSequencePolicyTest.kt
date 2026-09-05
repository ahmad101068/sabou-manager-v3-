package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.CorrelationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttendancePunchSequencePolicyTest {
    private val day = 20_000L
    private val start = day * 86_400_000L

    @Test
    fun clockInStartsCurrentBusinessDayWhenNoSessionIsOpen() {
        val decision = AttendancePunchSequencePolicy.decide(
            employeeId = 7,
            requestedType = AttendanceEventType.CLOCK_IN,
            localEpochDay = day,
            timestampEpochMillis = start + 8 * 60 * 60_000L,
            latestClockEvent = null,
        )
        assertEquals(day, decision.businessEpochDay)
    }

    @Test
    fun duplicateClockInIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AttendancePunchSequencePolicy.decide(
                employeeId = 7,
                requestedType = AttendanceEventType.CLOCK_IN,
                localEpochDay = day,
                timestampEpochMillis = start + 9 * 60 * 60_000L,
                latestClockEvent = event(AttendanceEventType.CLOCK_IN, start + 8 * 60 * 60_000L, day),
            )
        }
    }

    @Test
    fun clockOutWithoutOpenClockInIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AttendancePunchSequencePolicy.decide(
                employeeId = 7,
                requestedType = AttendanceEventType.CLOCK_OUT,
                localEpochDay = day,
                timestampEpochMillis = start + 10 * 60 * 60_000L,
                latestClockEvent = null,
            )
        }
    }

    @Test
    fun overnightClockOutStaysOnOpenClockInBusinessDay() {
        val inTime = start + 22 * 60 * 60_000L
        val outTime = start + 86_400_000L + 6 * 60 * 60_000L
        val decision = AttendancePunchSequencePolicy.decide(
            employeeId = 7,
            requestedType = AttendanceEventType.CLOCK_OUT,
            localEpochDay = day + 1,
            timestampEpochMillis = outTime,
            latestClockEvent = event(AttendanceEventType.CLOCK_IN, inTime, day),
        )
        assertEquals(day, decision.businessEpochDay)
    }

    @Test
    fun staleOpenSessionMustUseCorrectionFlow() {
        assertFailsWith<IllegalArgumentException> {
            AttendancePunchSequencePolicy.decide(
                employeeId = 7,
                requestedType = AttendanceEventType.CLOCK_OUT,
                localEpochDay = day + 2,
                timestampEpochMillis = start + 25 * 60 * 60_000L,
                latestClockEvent = event(AttendanceEventType.CLOCK_IN, start, day),
            )
        }
    }

    private fun event(type: AttendanceEventType, timestamp: Long, businessDay: Long) = AttendanceEvent(
        id = 1,
        employeeId = 7,
        eventType = type,
        businessEpochDay = businessDay,
        timestampEpochMillis = timestamp,
        minuteOfDay = 0,
        source = AttendanceSource.DEVICE,
        deviceId = "test",
        locationId = 1,
        createdByActorId = 1,
        reason = null,
        correlationId = CorrelationId.parse("test:attendance:punch"),
    )
}
