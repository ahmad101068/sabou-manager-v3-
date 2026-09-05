package ir.restaurant.management.domain.personnel

/** Compatibility calculator for explicit test/input schedules. Production uses AttendanceCalculationEngine + PlannedShift. */
object AttendanceCalculator {
    data class Result(val workedMinutes: Int, val lateMinutes: Int, val overtimeMinutes: Int)

    fun calculate(draft: AttendanceDraft): Result {
        val valid = draft.validated()
        if (valid.status != "PRESENT") return Result(0, 0, 0)
        val checkIn = requireNotNull(valid.checkInMinute)
        val checkOut = requireNotNull(valid.checkOutMinute)
        val start = requireNotNull(valid.scheduledStartMinute) { "planned_shift_required" }
        val end = requireNotNull(valid.scheduledEndMinute) { "planned_shift_required" }
        val crosses = end <= start
        val normalizedOut = if (crosses && checkOut <= checkIn) checkOut + 1440 else checkOut
        val normalizedEnd = if (crosses) end + 1440 else end
        require(normalizedOut > checkIn) { "زمان خروج باید بعد از ورود باشد." }
        return Result(
            workedMinutes = normalizedOut - checkIn,
            lateMinutes = (checkIn - start).coerceAtLeast(0),
            overtimeMinutes = (normalizedOut - normalizedEnd).coerceAtLeast(0),
        )
    }
}
