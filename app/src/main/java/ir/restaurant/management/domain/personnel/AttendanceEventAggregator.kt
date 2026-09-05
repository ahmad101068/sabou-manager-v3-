package ir.restaurant.management.domain.personnel

import kotlin.math.max

data class AttendanceAggregationPolicy(
    val scheduledStartMinute: Int,
    val scheduledEndMinute: Int,
    val maximumWorkedMinutes: Int = 16 * 60,
) {
    fun validated(): AttendanceAggregationPolicy {
        require(scheduledStartMinute in 0..1439)
        require(scheduledEndMinute in 1..1440 && scheduledEndMinute > scheduledStartMinute)
        require(maximumWorkedMinutes in 1..1440)
        return this
    }
}

object AttendanceEventAggregator {
    fun summarize(
        employeeId: Long,
        businessEpochDay: Long,
        events: List<AttendanceEvent>,
        policy: AttendanceAggregationPolicy,
        employmentFromEpochDay: Long,
        employmentToEpochDay: Long?,
    ): DailyAttendanceSummaryV2 {
        require(employeeId > 0 && businessEpochDay > 0)
        val validPolicy = policy.validated()
        val dayEvents = events
            .filter { it.employeeId == employeeId && it.businessEpochDay == businessEpochDay }
            .sortedWith(compareBy<AttendanceEvent> { it.timestampEpochMillis }.thenBy { it.id })
        val anomalies = mutableListOf<AttendanceAnomaly>()
        if (businessEpochDay < employmentFromEpochDay ||
            (employmentToEpochDay != null && businessEpochDay > employmentToEpochDay)
        ) {
            anomalies += anomaly(
                AttendanceAnomalyType.OUTSIDE_EMPLOYMENT_PERIOD,
                employeeId,
                businessEpochDay,
                dayEvents,
                "attendance_outside_employment_period",
            )
        }

        var clockIn: AttendanceEvent? = null
        var breakStart: AttendanceEvent? = null
        var workedMinutes = 0
        var breakMinutes = 0
        val inMinutes = mutableListOf<Int>()
        val outMinutes = mutableListOf<Int>()

        dayEvents.forEach { event ->
            when (event.eventType) {
                AttendanceEventType.CLOCK_IN -> {
                    if (clockIn != null) {
                        anomalies += anomaly(
                            AttendanceAnomalyType.DUPLICATE_CLOCK_IN,
                            employeeId,
                            businessEpochDay,
                            listOfNotNull(clockIn, event),
                            "duplicate_clock_in",
                        )
                    } else {
                        clockIn = event
                        inMinutes += event.minuteOfDay
                    }
                }

                AttendanceEventType.CLOCK_OUT -> {
                    val open = clockIn
                    if (open == null) {
                        anomalies += anomaly(
                            AttendanceAnomalyType.DUPLICATE_CLOCK_OUT,
                            employeeId,
                            businessEpochDay,
                            listOf(event),
                            "clock_out_without_clock_in",
                        )
                    } else {
                        breakStart?.let { unfinishedBreak ->
                            anomalies += anomaly(
                                AttendanceAnomalyType.BREAK_NOT_CLOSED,
                                employeeId,
                                businessEpochDay,
                                listOf(unfinishedBreak, event),
                                "break_not_closed_before_clock_out",
                            )
                        }
                        val duration = event.minuteOfDay - open.minuteOfDay
                        if (duration < 0) {
                            anomalies += anomaly(
                                AttendanceAnomalyType.NEGATIVE_DURATION,
                                employeeId,
                                businessEpochDay,
                                listOf(open, event),
                                "clock_out_before_clock_in",
                            )
                        } else {
                            workedMinutes += duration
                            outMinutes += event.minuteOfDay
                        }
                        clockIn = null
                        breakStart = null
                    }
                }

                AttendanceEventType.BREAK_START -> {
                    if (clockIn == null || breakStart != null) {
                        anomalies += anomaly(
                            AttendanceAnomalyType.OVERLAPPING_SESSION,
                            employeeId,
                            businessEpochDay,
                            listOf(event),
                            "invalid_break_start",
                        )
                    } else {
                        breakStart = event
                    }
                }

                AttendanceEventType.BREAK_END -> {
                    val openBreak = breakStart
                    if (openBreak == null) {
                        anomalies += anomaly(
                            AttendanceAnomalyType.OVERLAPPING_SESSION,
                            employeeId,
                            businessEpochDay,
                            listOf(event),
                            "break_end_without_start",
                        )
                    } else {
                        val duration = event.minuteOfDay - openBreak.minuteOfDay
                        if (duration < 0) {
                            anomalies += anomaly(
                                AttendanceAnomalyType.NEGATIVE_DURATION,
                                employeeId,
                                businessEpochDay,
                                listOf(openBreak, event),
                                "break_end_before_start",
                            )
                        } else {
                            breakMinutes += duration
                        }
                        breakStart = null
                    }
                }

                AttendanceEventType.MANUAL_ADJUSTMENT -> Unit
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
        clockIn?.let {
            anomalies += anomaly(
                AttendanceAnomalyType.MISSING_CLOCK_OUT,
                employeeId,
                businessEpochDay,
                listOf(it),
                "missing_clock_out",
            )
        }
        breakStart?.let {
            anomalies += anomaly(
                AttendanceAnomalyType.BREAK_NOT_CLOSED,
                employeeId,
                businessEpochDay,
                listOf(it),
                "break_not_closed",
            )
        }

        val netWorked = max(0, workedMinutes - breakMinutes)
        if (netWorked > validPolicy.maximumWorkedMinutes) {
            anomalies += anomaly(
                AttendanceAnomalyType.EXCESSIVE_DURATION,
                employeeId,
                businessEpochDay,
                dayEvents,
                "worked_minutes_exceed_policy",
            )
        }
        val firstIn = inMinutes.minOrNull()
        val lastOut = outMinutes.maxOrNull()
        val late = firstIn?.let { max(0, it - validPolicy.scheduledStartMinute) } ?: 0
        val early = lastOut?.let { max(0, validPolicy.scheduledEndMinute - it) } ?: 0
        val scheduledMinutes = validPolicy.scheduledEndMinute - validPolicy.scheduledStartMinute
        val overtime = max(0, netWorked - scheduledMinutes)
        val absenceMark = dayEvents.any { it.eventType == AttendanceEventType.ABSENCE_MARK }
        val status = when {
            anomalies.isNotEmpty() -> DailyAttendanceStatus.ANOMALY
            absenceMark -> DailyAttendanceStatus.ABSENT
            firstIn != null && lastOut != null -> DailyAttendanceStatus.PRESENT
            dayEvents.isEmpty() -> DailyAttendanceStatus.ABSENT
            else -> DailyAttendanceStatus.INCOMPLETE
        }
        return DailyAttendanceSummaryV2(
            employeeId = employeeId,
            businessEpochDay = businessEpochDay,
            firstInMinute = firstIn,
            lastOutMinute = lastOut,
            workedMinutes = netWorked,
            breakMinutes = breakMinutes,
            lateMinutes = late,
            earlyLeaveMinutes = early,
            overtimeMinutes = overtime,
            absenceMinutes = if (status == DailyAttendanceStatus.ABSENT) scheduledMinutes else 0,
            paidLeaveMinutes = 0,
            unpaidLeaveMinutes = 0,
            status = status,
            anomalies = anomalies.distinctBy { it.type to it.eventIds },
            source = dayEvents.firstOrNull()?.source ?: AttendanceSource.SYSTEM,
        )
    }

    private fun anomaly(
        type: AttendanceAnomalyType,
        employeeId: Long,
        businessEpochDay: Long,
        events: List<AttendanceEvent>,
        detail: String,
    ) = AttendanceAnomaly(
        type = type,
        employeeId = employeeId,
        businessEpochDay = businessEpochDay,
        eventIds = events.map { it.id }.filter { it > 0 }.distinct().sorted(),
        detail = detail,
    )
}
