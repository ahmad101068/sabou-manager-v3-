package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial

data class EmployeePrivateProfile(
    val employeeId: Long,
    val nationalId: String?,
    val insuranceNumber: String?,
    val bankName: String?,
    val bankAccountLast4: String?,
    val ibanLast4: String?,
    val accountHolder: String?,
    val emergencyContact: String,
) {
    fun maskedPaymentSummary(): String = when {
        !ibanLast4.isNullOrBlank() -> "IR••••$ibanLast4"
        !bankAccountLast4.isNullOrBlank() -> "••••$bankAccountLast4"
        else -> "تعریف نشده"
    }
}

data class EmploymentAssignment(
    val id: Long,
    val employeeId: Long,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val jobTitle: String,
    val department: String,
    val branchName: String,
    val locationId: Long?,
    val managerId: Long?,
    val createdByActorId: Long?,
    val branchId: Long? = null,
)

data class EmploymentContractVersion(
    val id: Long,
    val employeeId: Long,
    val contractNumber: String,
    val versionNo: Int,
    val replacesContractId: Long?,
    val contractType: EmploymentContractType,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val baseSalary: MoneyRial,
    val standardDailyMinutes: Int,
    val standardWeeklyMinutes: Int,
    val overtimePolicyId: Long?,
    val payrollPolicyId: Long?,
    val workScheduleId: Long? = null,
    val defaultShiftTemplateId: Long? = null,
    val jobTitleSnapshot: String,
    val departmentSnapshot: String,
    val branchSnapshot: String,
    val status: EmploymentContractStatus,
    val createdAtEpochMillis: Long,
    val createdByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val approvedByActorId: Long?,
)

data class AttendanceEvent(
    val id: Long,
    val employeeId: Long,
    val eventType: AttendanceEventType,
    val businessEpochDay: Long,
    val timestampEpochMillis: Long,
    val minuteOfDay: Int,
    val source: AttendanceSource,
    val deviceId: String?,
    val locationId: Long?,
    val createdByActorId: Long?,
    val reason: String?,
    val correlationId: CorrelationId,
    val branchId: Long? = null,
)

data class AttendanceCorrectionRecord(
    val id: Long,
    val employeeId: Long,
    val businessEpochDay: Long,
    val reason: String,
    val status: String,
    val requestedByActorId: Long,
    val reviewedByActorId: Long?,
    val requestedAtEpochMillis: Long,
    val reviewedAtEpochMillis: Long?,
    val correlationId: String,
)

data class AttendanceAnomaly(
    val type: AttendanceAnomalyType,
    val employeeId: Long,
    val businessEpochDay: Long,
    val eventIds: List<Long>,
    val detail: String,
)

data class DailyAttendanceSummaryV2(
    val employeeId: Long,
    val businessEpochDay: Long,
    val firstInMinute: Int?,
    val lastOutMinute: Int?,
    val workedMinutes: Int,
    val breakMinutes: Int,
    val lateMinutes: Int,
    val earlyLeaveMinutes: Int,
    val overtimeMinutes: Int,
    val absenceMinutes: Int,
    val paidLeaveMinutes: Int,
    val unpaidLeaveMinutes: Int,
    val status: DailyAttendanceStatus,
    val anomalies: List<AttendanceAnomaly>,
    val source: AttendanceSource,
)

data class LeaveBalance(
    val employeeId: Long,
    val leaveType: LeaveType,
    val grantedMicros: Long,
    val usedMicros: Long,
    val pendingMicros: Long,
    val remainingMicros: Long,
)

data class PayrollPeriodRecordV2(
    val id: Long,
    val periodKey: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val paymentDueEpochDay: Long?,
    val status: PayrollPeriodStatus,
)

data class PayrollBatchRecordV2(
    val id: Long,
    val documentNumber: String,
    val periodId: Long,
    val status: PayrollBatchStatus,
    val branchName: String?,
    val department: String?,
    val createdByActorId: Long?,
    val calculatedByActorId: Long?,
    val calculatedAtEpochMillis: Long?,
    val reviewedByActorId: Long?,
    val approvedByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val correlationId: CorrelationId,
    val source: PayrollDocumentSource,
    val employeesIncluded: Int = 0,
    val grossPayrollRial: Long = 0,
    val deductionsRial: Long = 0,
    val netPayrollRial: Long = 0,
    val paidRial: Long = 0,
    val remainingRial: Long = 0,
    val exceptionCount: Int = 0,
    val branchId: Long? = null,
)

data class PayrollPayslipRecordV2(
    val id: Long,
    val batchId: Long,
    val periodId: Long,
    val employeeId: Long,
    val employeeCodeSnapshot: String,
    val employeeNameSnapshot: String,
    val revisionNo: Int,
    val replacesPayslipId: Long?,
    val contractId: Long?,
    val status: PayrollPayslipStatus,
    val grossPay: MoneyRial,
    val totalDeductions: MoneyRial,
    val netPay: MoneyRial,
    val paidAmount: MoneyRial,
    val remainingAmount: MoneyRial,
    val componentDetailComplete: Boolean,
    val source: PayrollDocumentSource,
    val calculatedAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val paidAtEpochMillis: Long?,
    val accrualJournalEntryId: Long?,
    val reversalJournalEntryId: Long?,
    val reversalEpochDay: Long?,
    val correlationId: CorrelationId,
)

data class PayrollComponentDraftV2(
    val componentType: PayrollComponentType,
    val description: String,
    val quantity: Long?,
    val rateRial: Long?,
    val amountRial: Long,
    val direction: PayrollComponentDirection,
    val sourceType: PayrollComponentSourceType,
    val sourceId: Long?,
    val manualOverride: Boolean = false,
    val overrideReason: String? = null,
    val createdByActorId: Long? = null,
) {
    fun validated(): PayrollComponentDraftV2 {
        require(componentType != PayrollComponentType.LEGACY_UNKNOWN) { "payroll_component_type_unknown" }
        require(description.trim().length in 2..240) { "payroll_component_description_invalid" }
        require(quantity == null || quantity >= 0) { "payroll_component_quantity_negative" }
        require(rateRial == null || rateRial >= 0) { "payroll_component_rate_negative" }
        MoneyRial.of(amountRial)
        require(amountRial > 0) { "payroll_component_amount_not_positive" }
        require(!manualOverride || !overrideReason.isNullOrBlank()) { "payroll_manual_override_reason_required" }
        return copy(description = description.trim(), overrideReason = overrideReason?.trim())
    }
}

data class PayrollInputSnapshot(
    val employeeId: Long,
    val employeeCode: String,
    val employeeDisplayName: String,
    val contractId: Long,
    val contractVersionNo: Int,
    val baseSalaryRial: Long,
    val standardPeriodMinutes: Int,
    val eligiblePeriodMinutes: Int,
    val actualWorkMinutes: Int,
    val overtimeMinutes: Int,
    val absenceMinutes: Int,
    val lateMinutes: Int,
    val paidLeaveMinutes: Int,
    val unpaidLeaveMinutes: Int,
    val payrollPolicyId: Long,
    val payrollPolicyVersion: Int,
    val overtimeRateRialPerHour: Long,
    val overtimeMultiplierBasisPoints: Int,
    val insuranceBasisPoints: Int,
    val taxBasisPoints: Int,
    val calculationVersion: String,
    val nightMinutes: Int = 0,
    val holidayMinutes: Int = 0,
    val nightMultiplierBasisPoints: Int = 10_000,
    val holidayMultiplierBasisPoints: Int = 10_000,
) {
    fun validated(): PayrollInputSnapshot {
        require(employeeId > 0 && contractId > 0 && payrollPolicyId > 0)
        require(employeeCode.isNotBlank() && employeeDisplayName.isNotBlank())
        MoneyRial.of(baseSalaryRial)
        require(standardPeriodMinutes > 0)
        require(eligiblePeriodMinutes in 0..standardPeriodMinutes)
        require(listOf(actualWorkMinutes, overtimeMinutes, absenceMinutes, lateMinutes, paidLeaveMinutes, unpaidLeaveMinutes, nightMinutes, holidayMinutes).all { it >= 0 })
        require(nightMinutes <= actualWorkMinutes) { "payroll_night_minutes_exceed_work" }
        require(holidayMinutes <= actualWorkMinutes) { "payroll_holiday_minutes_exceed_work" }
        MoneyRial.of(overtimeRateRialPerHour)
        require(overtimeMultiplierBasisPoints in 0..100_000)
        require(nightMultiplierBasisPoints in 10_000..100_000) { "payroll_night_multiplier_invalid" }
        require(holidayMultiplierBasisPoints in 10_000..100_000) { "payroll_holiday_multiplier_invalid" }
        require(insuranceBasisPoints in 0..10_000 && taxBasisPoints in 0..10_000)
        require(calculationVersion.matches(Regex("[A-Za-z0-9._-]{1,40}")))
        return this
    }
}

data class PayrollCalculationResultV2(
    val snapshot: PayrollInputSnapshot,
    val components: List<PayrollComponentDraftV2>,
    val grossPayRial: Long,
    val totalDeductionsRial: Long,
    val netPayRial: Long,
    val warnings: List<String>,
)

data class PayrollCalculationCommand(
    val commandId: GlobalId,
    val batchId: Long,
    val snapshot: PayrollInputSnapshot,
    val approvedManualComponents: List<PayrollComponentDraftV2>,
    val approvedAdvanceDeductionRial: Long,
) {
    fun validated(): PayrollCalculationCommand {
        require(batchId > 0)
        snapshot.validated()
        approvedManualComponents.forEach { it.validated() }
        MoneyRial.of(approvedAdvanceDeductionRial)
        return this
    }
}

data class PayrollExceptionRecord(
    val code: String,
    val employeeId: Long?,
    val blocking: Boolean,
    val detail: String,
)

data class EmployeeTimelineItem(
    val stableKey: String,
    val employeeId: Long,
    val businessEpochDay: Long,
    val occurredAtEpochMillis: Long?,
    val eventType: String,
    val title: String,
    val referenceType: String,
    val referenceId: Long,
)
