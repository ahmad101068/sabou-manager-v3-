package ir.restaurant.management.domain.personnel

/** Single source of truth for shift-aware attendance calculations. */
object AttendanceCalculationEngine {
    data class ShiftInput(
        val plannedStartEpochMillis: Long,
        val plannedEndEpochMillis: Long,
        val breakMinutes: Int,
        val graceInMinutes: Int,
        val graceOutMinutes: Int,
        val overtimeEligible: Boolean,
        val overtimeRequiresApproval: Boolean,
    ) {
        fun validated(): ShiftInput {
            require(plannedStartEpochMillis > 0 && plannedEndEpochMillis > plannedStartEpochMillis)
            require(breakMinutes >= 0 && graceInMinutes >= 0 && graceOutMinutes >= 0)
            return this
        }
    }

    data class Result(
        val workedMinutes: Int,
        val rawLateMinutes: Int,
        val payableLateMinutes: Int,
        val rawEarlyLeaveMinutes: Int,
        val payableEarlyLeaveMinutes: Int,
        val rawOvertimeMinutes: Int,
        val approvedOvertimeMinutes: Int,
        val payrollOvertimeMinutes: Int,
        val actualCheckInEpochMillis: Long,
        val actualCheckOutEpochMillis: Long,
    )

    fun calculate(
        businessEpochDay: Long,
        checkInMinute: Int,
        checkOutMinute: Int,
        shift: ShiftInput,
        approvedOvertimeMinutes: Int? = null,
    ): Result {
        val valid = shift.validated()
        require(businessEpochDay > 0)
        require(checkInMinute in 0..1439 && checkOutMinute in 0..1439)
        val dayStart = Math.multiplyExact(businessEpochDay, 86_400_000L)
        val checkIn = Math.addExact(dayStart, checkInMinute * 60_000L)
        val shiftCrossesMidnight = valid.plannedEndEpochMillis >= dayStart + 86_400_000L
        val checkOutDayStart = if (shiftCrossesMidnight && checkOutMinute <= checkInMinute) {
            Math.addExact(dayStart, 86_400_000L)
        } else dayStart
        val checkOut = Math.addExact(checkOutDayStart, checkOutMinute * 60_000L)
        require(checkOut > checkIn) { "زمان خروج باید بعد از ورود واقعی باشد." }
        val grossWorked = ((checkOut - checkIn) / 60_000L).toInt()
        require(grossWorked in 1..(24 * 60)) { "مدت حضور خارج از محدوده مجاز است." }
        val worked = (grossWorked - valid.breakMinutes).coerceAtLeast(0)
        val rawLate = ((checkIn - valid.plannedStartEpochMillis) / 60_000L).toInt().coerceAtLeast(0)
        val payableLate = (rawLate - valid.graceInMinutes).coerceAtLeast(0)
        val rawEarly = ((valid.plannedEndEpochMillis - checkOut) / 60_000L).toInt().coerceAtLeast(0)
        val payableEarly = (rawEarly - valid.graceOutMinutes).coerceAtLeast(0)
        val rawOvertime = if (valid.overtimeEligible) {
            ((checkOut - valid.plannedEndEpochMillis) / 60_000L).toInt().coerceAtLeast(0)
        } else 0
        val approved = when {
            !valid.overtimeRequiresApproval -> rawOvertime
            approvedOvertimeMinutes == null -> 0
            else -> approvedOvertimeMinutes.coerceIn(0, rawOvertime)
        }
        return Result(
            workedMinutes = worked,
            rawLateMinutes = rawLate,
            payableLateMinutes = payableLate,
            rawEarlyLeaveMinutes = rawEarly,
            payableEarlyLeaveMinutes = payableEarly,
            rawOvertimeMinutes = rawOvertime,
            approvedOvertimeMinutes = approved,
            payrollOvertimeMinutes = approved,
            actualCheckInEpochMillis = checkIn,
            actualCheckOutEpochMillis = checkOut,
        )
    }
}
