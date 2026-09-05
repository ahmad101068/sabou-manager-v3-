package ir.restaurant.management.domain.personnel

import kotlin.math.max

/**
 * Canonical pairing for attendance punch events. Unlike first-in/last-out arithmetic, this sums
 * only closed work sessions so gaps between punches never become payable work by accident.
 */
data class AttendanceSessionSummary(
    val firstIn: AttendanceEvent?,
    val lastOut: AttendanceEvent?,
    val grossSessionMinutes: Int,
    val explicitBreakMinutes: Int,
    val breakMinutes: Int,
    val workedMinutes: Int,
    val sessionCount: Int,
    val anomalies: List<AttendanceAnomaly>,
)

object AttendanceSessionCalculator {
    fun summarize(
        employeeId: Long,
        businessEpochDay: Long,
        events: List<AttendanceEvent>,
        scheduledBreakMinutes: Int,
        maximumWorkedMinutes: Int = 16 * 60,
    ): AttendanceSessionSummary {
        require(employeeId > 0 && businessEpochDay > 0)
        require(scheduledBreakMinutes in 0..(24 * 60))
        require(maximumWorkedMinutes in 1..(24 * 60))

        val dayEvents = events
            .filter { it.employeeId == employeeId && it.businessEpochDay == businessEpochDay }
            .sortedWith(compareBy<AttendanceEvent> { it.timestampEpochMillis }.thenBy { it.id })
        val anomalies = mutableListOf<AttendanceAnomaly>()
        var openSession: AttendanceEvent? = null
        var openBreak: AttendanceEvent? = null
        var grossSessionMinutes = 0
        var explicitBreakMinutes = 0
        var sessionCount = 0
        val clockIns = mutableListOf<AttendanceEvent>()
        val clockOuts = mutableListOf<AttendanceEvent>()

        dayEvents.forEach { event ->
            when (event.eventType) {
                AttendanceEventType.CLOCK_IN -> {
                    if (openSession != null) {
                        anomalies += anomaly(AttendanceAnomalyType.DUPLICATE_CLOCK_IN, employeeId, businessEpochDay, listOfNotNull(openSession, event), "duplicate_clock_in")
                    } else {
                        openSession = event
                        clockIns += event
                    }
                }

                AttendanceEventType.CLOCK_OUT -> {
                    val start = openSession
                    if (start == null) {
                        anomalies += anomaly(AttendanceAnomalyType.DUPLICATE_CLOCK_OUT, employeeId, businessEpochDay, listOf(event), "clock_out_without_clock_in")
                    } else {
                        if (openBreak != null) {
                            anomalies += anomaly(AttendanceAnomalyType.BREAK_NOT_CLOSED, employeeId, businessEpochDay, listOfNotNull(openBreak, event), "break_not_closed_before_clock_out")
                            openBreak = null
                        }
                        val minutes = durationMinutes(start, event)
                        if (minutes <= 0) {
                            anomalies += anomaly(AttendanceAnomalyType.NEGATIVE_DURATION, employeeId, businessEpochDay, listOf(start, event), "clock_out_not_after_clock_in")
                        } else {
                            grossSessionMinutes = Math.addExact(grossSessionMinutes, minutes)
                            sessionCount += 1
                            clockOuts += event
                        }
                        openSession = null
                    }
                }

                AttendanceEventType.BREAK_START -> {
                    if (openSession == null || openBreak != null) {
                        anomalies += anomaly(AttendanceAnomalyType.OVERLAPPING_SESSION, employeeId, businessEpochDay, listOf(event), "invalid_break_start")
                    } else {
                        openBreak = event
                    }
                }

                AttendanceEventType.BREAK_END -> {
                    val start = openBreak
                    if (start == null) {
                        anomalies += anomaly(AttendanceAnomalyType.OVERLAPPING_SESSION, employeeId, businessEpochDay, listOf(event), "break_end_without_start")
                    } else {
                        val minutes = durationMinutes(start, event)
                        if (minutes <= 0) {
                            anomalies += anomaly(AttendanceAnomalyType.NEGATIVE_DURATION, employeeId, businessEpochDay, listOf(start, event), "break_end_not_after_start")
                        } else {
                            explicitBreakMinutes = Math.addExact(explicitBreakMinutes, minutes)
                        }
                        openBreak = null
                    }
                }

                AttendanceEventType.MANUAL_ADJUSTMENT,
                AttendanceEventType.ABSENCE_MARK -> Unit

                AttendanceEventType.LEGACY_UNKNOWN -> anomalies += anomaly(
                    AttendanceAnomalyType.OVERLAPPING_SESSION,
                    employeeId,
                    businessEpochDay,
                    listOf(event),
                    "unknown_attendance_event",
                )
            }
        }

        openSession?.let {
            anomalies += anomaly(AttendanceAnomalyType.MISSING_CLOCK_OUT, employeeId, businessEpochDay, listOf(it), "missing_clock_out")
        }
        openBreak?.let {
            anomalies += anomaly(AttendanceAnomalyType.BREAK_NOT_CLOSED, employeeId, businessEpochDay, listOf(it), "break_not_closed")
        }

        val firstIn = clockIns.minByOrNull { it.timestampEpochMillis }
        val lastOut = clockOuts.maxByOrNull { it.timestampEpochMillis }
        val spanMinutes = if (firstIn != null && lastOut != null && lastOut.timestampEpochMillis > firstIn.timestampEpochMillis) {
            ((lastOut.timestampEpochMillis - firstIn.timestampEpochMillis) / 60_000L).toInt()
        } else 0
        val implicitGapMinutes = max(0, spanMinutes - grossSessionMinutes)
        val breakMinutes = max(scheduledBreakMinutes, implicitGapMinutes + explicitBreakMinutes)
        val workedMinutes = max(0, spanMinutes - breakMinutes)
        if (workedMinutes > maximumWorkedMinutes) {
            anomalies += anomaly(
                AttendanceAnomalyType.EXCESSIVE_DURATION,
                employeeId,
                businessEpochDay,
                dayEvents,
                "worked_minutes_exceed_policy",
            )
        }

        return AttendanceSessionSummary(
            firstIn = firstIn,
            lastOut = lastOut,
            grossSessionMinutes = grossSessionMinutes,
            explicitBreakMinutes = explicitBreakMinutes,
            breakMinutes = breakMinutes,
            workedMinutes = workedMinutes,
            sessionCount = sessionCount,
            anomalies = anomalies.distinctBy { it.type to it.eventIds },
        )
    }

    private fun durationMinutes(start: AttendanceEvent, end: AttendanceEvent): Int {
        val delta = end.timestampEpochMillis - start.timestampEpochMillis
        if (delta <= 0) return 0
        val minutes = delta / 60_000L
        return if (minutes > Int.MAX_VALUE) Int.MAX_VALUE else minutes.toInt()
    }

    private fun anomaly(
        type: AttendanceAnomalyType,
        employeeId: Long,
        businessEpochDay: Long,
        events: List<AttendanceEvent>,
        detail: String,
    ) = AttendanceAnomaly(type, employeeId, businessEpochDay, events.map { it.id }, detail)
}
