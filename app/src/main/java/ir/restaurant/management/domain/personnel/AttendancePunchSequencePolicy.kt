package ir.restaurant.management.domain.personnel

/**
 * Validates the next immutable clock event. CLOCK_OUT remains anchored to the business day of the
 * open CLOCK_IN, so overnight shifts do not split one work session across two payroll days.
 */
data class AttendancePunchDecision(val businessEpochDay: Long)

object AttendancePunchSequencePolicy {
    private const val MAX_OPEN_SESSION_MILLIS = 24L * 60L * 60L * 1000L

    fun decide(
        employeeId: Long,
        requestedType: AttendanceEventType,
        localEpochDay: Long,
        timestampEpochMillis: Long,
        latestClockEvent: AttendanceEvent?,
    ): AttendancePunchDecision {
        require(employeeId > 0 && localEpochDay > 0 && timestampEpochMillis > 0)
        require(requestedType in setOf(AttendanceEventType.CLOCK_IN, AttendanceEventType.CLOCK_OUT))
        latestClockEvent?.let { require(it.employeeId == employeeId) { "attendance_punch_employee_mismatch" } }
        return when (requestedType) {
            AttendanceEventType.CLOCK_IN -> {
                require(latestClockEvent?.eventType != AttendanceEventType.CLOCK_IN) { "attendance_duplicate_clock_in" }
                AttendancePunchDecision(localEpochDay)
            }
            AttendanceEventType.CLOCK_OUT -> {
                val open = requireNotNull(latestClockEvent?.takeIf { it.eventType == AttendanceEventType.CLOCK_IN }) {
                    "attendance_clock_out_without_open_clock_in"
                }
                require(timestampEpochMillis > open.timestampEpochMillis) { "attendance_clock_out_not_after_clock_in" }
                require(timestampEpochMillis - open.timestampEpochMillis <= MAX_OPEN_SESSION_MILLIS) {
                    "attendance_open_session_too_old_use_correction"
                }
                AttendancePunchDecision(open.businessEpochDay)
            }
            else -> error("attendance_punch_type_not_exhaustive")
        }
    }
}
