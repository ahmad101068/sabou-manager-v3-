package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.SignedLongMath

/** Converts approved attendance totals into deterministic payroll additions and deductions. */
object AttendancePayrollCalculator {
    fun calculate(summary: AttendanceSummary, policy: AttendancePayrollPolicy): AttendancePayrollAdjustment {
        val valid = policy.validated()
        val overtimeRial = SignedLongMath.multiply(summary.overtimeMinutes.toLong(), valid.overtimeHourlyRateRial) / 60L
        val absenceDeduction = SignedLongMath.multiply(summary.absentDays.toLong(), valid.absenceDailyDeductionRial)
        val lateDeduction = SignedLongMath.multiply(summary.lateMinutes.toLong(), valid.lateMinuteDeductionRial)
        val total = SignedLongMath.add(absenceDeduction, lateDeduction)
        return AttendancePayrollAdjustment(overtimeRial, absenceDeduction, lateDeduction, total)
    }
}
