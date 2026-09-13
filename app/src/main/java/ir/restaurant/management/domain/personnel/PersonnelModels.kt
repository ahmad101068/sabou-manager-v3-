package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.GlobalId
import kotlinx.coroutines.flow.Flow
import ir.restaurant.management.domain.treasury.TreasuryChannel

data class EmployeeRecord(
    val id: Long,
    val name: String,
    val fatherName: String,
    val jobTitle: String,
    val phone: String,
    val monthlySalaryRial: Long,
    val isActive: Boolean,
    val nationalId: String? = null,
    val birthEpochDay: Long? = null,
    val hireEpochDay: Long? = null,
    val employeeCode: String? = null,
    val branchName: String = "",
    val insuranceNumber: String? = null,
    val bankCard: String? = null,
    val address: String = "",
    val emergencyContact: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = name,
    val email: String? = null,
    val department: String = "",
    val locationId: Long? = null,
    val managerId: Long? = null,
    val employmentStatus: EmploymentStatus = if (isActive) EmploymentStatus.ACTIVE else EmploymentStatus.ARCHIVED,
    val terminationEpochDay: Long? = null,
    val notes: String = "",
    val maskedBankAccount: String = "",
    val contractStatus: EmploymentContractStatus? = null,
    val branchId: Long? = null,
)

data class EmployeeDraft(
    val name: String,
    val fatherName: String,
    val jobTitle: String,
    val phone: String,
    val monthlySalaryRial: Long,
    val nationalId: String = "",
    val birthEpochDay: Long? = null,
    val hireEpochDay: Long? = null,
    val employeeCode: String = "",
    val branchName: String = "",
    val insuranceNumber: String = "",
    val bankCard: String = "",
    val address: String = "",
    val emergencyContact: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String = "",
    val email: String = "",
    val department: String = "",
    val locationId: Long? = null,
    val managerId: Long? = null,
    val employmentStatus: EmploymentStatus = EmploymentStatus.ACTIVE,
    val terminationEpochDay: Long? = null,
    val notes: String = "",
    val assignmentEffectiveEpochDay: Long? = null,
    val branchId: Long? = null,
) {
    fun validated(): EmployeeDraft {
        require(name.trim().length >= 2) { "نام پرسنل معتبر نیست." }
        require(jobTitle.trim().isNotEmpty()) { "عنوان شغلی الزامی است." }
        require(monthlySalaryRial >= 0) { "حقوق پایه نمی‌تواند منفی باشد." }
        require(nationalId.isBlank() || IranianNationalIdValidator.isValid(nationalId)) { "کد ملی معتبر نیست." }
        require(birthEpochDay == null || birthEpochDay > 0) { "تاریخ تولد معتبر نیست." }
        require(hireEpochDay == null || hireEpochDay > 0) { "تاریخ استخدام معتبر نیست." }
        require(bankCard.isBlank() || (bankCard.length == 16 && bankCard.all(Char::isDigit))) { "شماره کارت باید ۱۶ رقم باشد." }
        require(email.isBlank() || email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "ایمیل معتبر نیست." }
        require(locationId == null || locationId > 0) { "محل خدمت معتبر نیست." }
        require(managerId == null || managerId > 0) { "مدیر مستقیم معتبر نیست." }
        require(branchId == null || branchId > 0) { "شعبه معتبر نیست." }
        require(assignmentEffectiveEpochDay == null || assignmentEffectiveEpochDay > 0) { "تاریخ اثر تغییر شغلی معتبر نیست." }
        require(employmentStatus != EmploymentStatus.LEGACY_UNKNOWN) { "وضعیت استخدام معتبر نیست." }
        require(
            employmentStatus != EmploymentStatus.TERMINATED ||
                (terminationEpochDay != null && terminationEpochDay > 0),
        ) { "تاریخ خاتمه همکاری الزامی است." }
        return copy(
            name = name.trim(), fatherName = fatherName.trim(), jobTitle = jobTitle.trim(), phone = phone.trim(), nationalId = nationalId.trim(),
            employeeCode = employeeCode.trim(), branchName = branchName.trim(), insuranceNumber = insuranceNumber.trim(), bankCard = bankCard.trim(),
            address = address.trim(), emergencyContact = emergencyContact.trim(), firstName = firstName.trim(), lastName = lastName.trim(),
            displayName = displayName.trim().ifBlank { name.trim() }, email = email.trim(), department = department.trim(), notes = notes.trim(),
        )
    }
}


data class EmployeeContractRecord(
    val id: Long,
    val employeeId: Long,
    val contractType: String,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val baseSalaryRial: Long,
    val dailyWorkMinutes: Int,
    val weeklyWorkDays: Int,
    val status: String,
    val notes: String,
    val contractNumber: String = "",
    val versionNo: Int = 1,
    val replacesContractId: Long? = null,
    val payrollPolicyId: Long? = null,
    val workScheduleId: Long? = null,
    val defaultShiftTemplateId: Long? = null,
    val jobTitleSnapshot: String = "",
    val departmentSnapshot: String = "",
    val branchSnapshot: String = "",
    val typedStatus: EmploymentContractStatus = EmploymentContractStatus.LEGACY_UNKNOWN,
)

data class EmployeeContractDraft(
    val employeeId: Long,
    val contractType: String,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val baseSalaryRial: Long,
    val dailyWorkMinutes: Int = 480,
    val weeklyWorkDays: Int = 6,
    val notes: String = "",
    val contractNumber: String = "",
    val payrollPolicyId: Long? = null,
    val overtimePolicyId: Long? = null,
    val workScheduleId: Long? = null,
    val defaultShiftTemplateId: Long? = null,
    val jobTitleSnapshot: String = "",
    val departmentSnapshot: String = "",
    val branchSnapshot: String = "",
    val correctionReason: String = "",
) {
    fun validated(): EmployeeContractDraft {
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(contractType.trim().isNotEmpty()) { "نوع قرارداد الزامی است." }
        require(startEpochDay > 0) { "تاریخ شروع قرارداد معتبر نیست." }
        require(endEpochDay == null || endEpochDay >= startEpochDay) { "تاریخ پایان قرارداد معتبر نیست." }
        require(baseSalaryRial >= 0) { "حقوق پایه نمی‌تواند منفی باشد." }
        require(dailyWorkMinutes in 1..1440) { "ساعت کار روزانه معتبر نیست." }
        require(weeklyWorkDays in 1..7) { "تعداد روز کاری هفتگی معتبر نیست." }
        require(contractNumber.length <= 80) { "شماره قرارداد بیش از حد طولانی است." }
        require(payrollPolicyId == null || payrollPolicyId > 0) { "سیاست حقوق معتبر نیست." }
        require(overtimePolicyId == null || overtimePolicyId > 0) { "سیاست اضافه‌کاری معتبر نیست." }
        require(workScheduleId == null || workScheduleId > 0) { "برنامه کاری معتبر نیست." }
        require(defaultShiftTemplateId == null || defaultShiftTemplateId > 0) { "شیفت پیش‌فرض معتبر نیست." }
        require(correctionReason.length <= 500) { "دلیل اصلاح قرارداد بیش از حد طولانی است." }
        return copy(
            contractType = contractType.trim(), notes = notes.trim(), contractNumber = contractNumber.trim(),
            jobTitleSnapshot = jobTitleSnapshot.trim(), departmentSnapshot = departmentSnapshot.trim(),
            branchSnapshot = branchSnapshot.trim(), correctionReason = correctionReason.trim(),
        )
    }
}

data class EmployeeAdvanceRecord(
    val id: Long,
    val employeeId: Long,
    val amountRial: Long,
    val settledAmountRial: Long,
    val remainingAmountRial: Long,
    val advanceEpochDay: Long,
    val paymentMethod: TreasuryChannel,
    val journalEntryId: Long,
    val status: String,
    val notes: String,
)

data class EmployeeAdvanceDraft(
    val employeeId: Long,
    val amountRial: Long,
    val advanceEpochDay: Long,
    val paymentMethod: TreasuryChannel,
    val notes: String = "",
) {
    fun validated(): EmployeeAdvanceDraft {
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(amountRial > 0) { "مبلغ مساعده باید بیشتر از صفر باشد." }
        require(advanceEpochDay > 0) { "تاریخ مساعده معتبر نیست." }
        require(paymentMethod in setOf(TreasuryChannel.CASH, TreasuryChannel.BANK)) {
            "personnel_payment_channel_unsupported"
        }
        return copy(notes = notes.trim())
    }
}


data class AttendanceRecord(
    val id: Long,
    val employeeId: Long,
    val workEpochDay: Long,
    val status: String,
    val checkInMinute: Int?,
    val checkOutMinute: Int?,
    val workedMinutes: Int,
    val lateMinutes: Int,
    val rawLateMinutes: Int = lateMinutes,
    val payableLateMinutes: Int = lateMinutes,
    val overtimeMinutes: Int,
    val earlyLeaveMinutes: Int = 0,
    val rawOvertimeMinutes: Int = 0,
    val approvedOvertimeMinutes: Int = 0,
    val notes: String,
)

data class AttendanceDraft(
    val employeeId: Long,
    val workEpochDay: Long,
    val status: String = "PRESENT",
    val checkInMinute: Int? = null,
    val checkOutMinute: Int? = null,
    val scheduledStartMinute: Int? = null,
    val scheduledEndMinute: Int? = null,
    val notes: String = "",
    val source: AttendanceSource = AttendanceSource.MANUAL,
    val commandId: String = GlobalId.new().value,
    val correctionReason: String = "",
    val requiresApproval: Boolean = false,
) {
    fun validated(): AttendanceDraft {
        val normalizedCommandId = GlobalId.parse(commandId).value
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(workEpochDay > 0) { "تاریخ حضور معتبر نیست." }
        require(status in setOf("PRESENT", "ABSENT", "LEAVE", "MISSION", "HOLIDAY", "OFF_DAY")) { "وضعیت حضور معتبر نیست." }
        require((scheduledStartMinute == null) == (scheduledEndMinute == null)) { "شروع و پایان شیفت باید همزمان تعیین شوند." }
        scheduledStartMinute?.let { require(it in 0..1439) { "ساعت شروع شیفت معتبر نیست." } }
        scheduledEndMinute?.let { require(it in 0..1439 && it != scheduledStartMinute) { "ساعت پایان شیفت معتبر نیست." } }
        checkInMinute?.let { require(it in 0..1439) { "زمان ورود معتبر نیست." } }
        checkOutMinute?.let { require(it in 0..1439) { "زمان خروج معتبر نیست." } }
        if (status == "PRESENT") {
            require(checkInMinute != null && checkOutMinute != null) { "برای حضور، زمان ورود و خروج الزامی است." }
        }
        require(source !in setOf(AttendanceSource.LEGACY, AttendanceSource.LEGACY_UNKNOWN)) { "منبع حضور معتبر نیست." }
        require(correctionReason.length <= 500) { "دلیل اصلاح بیش از حد طولانی است." }
        return copy(notes = notes.trim(), commandId = normalizedCommandId, correctionReason = correctionReason.trim())
    }
}

data class AttendancePunchDraft(
    val employeeId: Long,
    val eventType: AttendanceEventType,
    val source: AttendanceSource = AttendanceSource.MANUAL,
    val reason: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): AttendancePunchDraft {
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(eventType in setOf(AttendanceEventType.CLOCK_IN, AttendanceEventType.CLOCK_OUT)) { "نوع پانچ معتبر نیست." }
        require(source !in setOf(AttendanceSource.LEGACY, AttendanceSource.LEGACY_UNKNOWN)) { "منبع پانچ معتبر نیست." }
        require(reason.trim().length <= 500) { "توضیح پانچ بیش از حد طولانی است." }
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class AttendanceSummary(
    val employeeId: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val presentDays: Int,
    val absentDays: Int,
    val leaveDays: Int,
    val missionDays: Int,
    val workedMinutes: Int,
    val lateMinutes: Int,
    val overtimeMinutes: Int,
)

data class LeaveDraft(
    val employeeId: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val leaveType: String,
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): LeaveDraft {
        val normalizedCommandId = GlobalId.parse(commandId).value
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(startEpochDay > 0 && endEpochDay >= startEpochDay) { "بازه مرخصی معتبر نیست." }
        require(LeaveType.fromStoredValue(leaveType) != LeaveType.LEGACY_UNKNOWN) { "نوع مرخصی معتبر نیست." }
        return copy(leaveType = LeaveType.fromStoredValue(leaveType).storedValue, notes = notes.trim(), commandId = normalizedCommandId)
    }
}


data class LeaveRecord(
    val id: Long,
    val employeeId: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val daysMicros: Long,
    val leaveType: String,
    val status: String,
    val notes: String,
    val requestedBy: String,
    val reviewedBy: String?,
    val reviewNotes: String,
    val reviewedAtEpochMillis: Long?,
    val typedStatus: LeaveStatus = LeaveStatus.fromStoredValue(status),
    val typedLeaveType: LeaveType = LeaveType.fromStoredValue(leaveType),
    val correlationId: String = "",
)

data class LeaveGrantDraft(
    val employeeId: Long,
    val leaveType: LeaveType,
    val amountMicros: Long,
    val businessEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): LeaveGrantDraft {
        require(employeeId > 0 && businessEpochDay > 0)
        require(leaveType != LeaveType.LEGACY_UNKNOWN)
        require(amountMicros > 0)
        require(reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class LeaveReviewDraft(
    val leaveId: Long,
    val decision: String,
    val reviewer: String,
    val notes: String = "",
) {
    fun validated(): LeaveReviewDraft {
        require(leaveId > 0) { "درخواست مرخصی معتبر نیست." }
        require(decision in setOf("APPROVE", "REJECT")) { "تصمیم بررسی معتبر نیست." }
        require(reviewer.trim().isNotEmpty()) { "نام تأییدکننده الزامی است." }
        return copy(reviewer = reviewer.trim(), notes = notes.trim())
    }
}

data class AttendancePayrollPolicy(
    val overtimeHourlyRateRial: Long,
    val absenceDailyDeductionRial: Long,
    val lateMinuteDeductionRial: Long = 0,
) {
    fun validated(): AttendancePayrollPolicy {
        require(overtimeHourlyRateRial >= 0) { "نرخ اضافه‌کاری معتبر نیست." }
        require(absenceDailyDeductionRial >= 0) { "نرخ کسر غیبت معتبر نیست." }
        require(lateMinuteDeductionRial >= 0) { "نرخ کسر تأخیر معتبر نیست." }
        return this
    }
}

data class PayrollPolicyRecord(
    val id: Long,
    val title: String,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val overtimeHourlyRateRial: Long,
    val absenceDailyDeductionRial: Long,
    val lateMinuteDeductionRial: Long,
    val createdBy: String,
    val versionNo: Int = 1,
    val overtimeMultiplierBasisPoints: Int = 10_000,
    val insuranceBasisPoints: Int = 0,
    val taxBasisPoints: Int = 0,
    val holidayMultiplierBasisPoints: Int = 10_000,
    val nightMultiplierBasisPoints: Int = 10_000,
)

data class PayrollPolicyDraft(
    val title: String,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long? = null,
    val overtimeHourlyRateRial: Long,
    val absenceDailyDeductionRial: Long,
    val lateMinuteDeductionRial: Long = 0,
    val overtimeMultiplierBasisPoints: Int = 10_000,
    val insuranceBasisPoints: Int = 0,
    val taxBasisPoints: Int = 0,
    val holidayMultiplierBasisPoints: Int = 10_000,
    val nightMultiplierBasisPoints: Int = 10_000,
) {
    fun validated(): PayrollPolicyDraft {
        require(title.trim().length >= 2) { "عنوان سیاست حقوق معتبر نیست." }
        require(effectiveFromEpochDay > 0) { "تاریخ شروع سیاست معتبر نیست." }
        require(effectiveToEpochDay == null || effectiveToEpochDay >= effectiveFromEpochDay) { "بازه سیاست معتبر نیست." }
        AttendancePayrollPolicy(overtimeHourlyRateRial, absenceDailyDeductionRial, lateMinuteDeductionRial).validated()
        require(overtimeMultiplierBasisPoints in 0..100_000) { "ضریب اضافه‌کاری معتبر نیست." }
        require(insuranceBasisPoints in 0..10_000) { "نرخ بیمه معتبر نیست." }
        require(taxBasisPoints in 0..10_000) { "نرخ مالیات معتبر نیست." }
        require(holidayMultiplierBasisPoints in 10_000..100_000) { "ضریب کار در تعطیل معتبر نیست." }
        require(nightMultiplierBasisPoints in 10_000..100_000) { "ضریب شب‌کاری معتبر نیست." }
        return copy(title = title.trim())
    }
}

data class AttendancePayrollAdjustment(
    val overtimeRial: Long,
    val absenceDeductionRial: Long,
    val lateDeductionRial: Long,
    val totalAttendanceDeductionRial: Long,
)

data class PayrollDraft(
    val employeeId: Long,
    val periodYear: Int,
    val periodMonth: Int,
    val overtimeRial: Long = 0,
    val bonusRial: Long = 0,
    val allowancesRial: Long = 0,
    val deductionsRial: Long = 0,
    val insuranceRial: Long = 0,
    val taxRial: Long = 0,
    val advanceDeductionRial: Long = 0,
    val periodStartEpochDay: Long = 0,
    val periodEndEpochDay: Long = 0,
    val paymentEpochDay: Long,
    val paymentMethod: TreasuryChannel,
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PayrollDraft {
        val normalizedCommandId = GlobalId.parse(commandId).value
        require(employeeId > 0) { "پرسنل انتخاب نشده است." }
        require(periodYear in 1300..1600) { "سال دوره معتبر نیست." }
        require(periodMonth in 1..12) { "ماه دوره معتبر نیست." }
        require(listOf(overtimeRial, bonusRial, allowancesRial, deductionsRial, insuranceRial, taxRial, advanceDeductionRial).all { it >= 0 }) {
            "مبالغ حقوق نمی‌توانند منفی باشند."
        }
        require(paymentEpochDay > 0) { "تاریخ پرداخت معتبر نیست." }
        require(paymentMethod in setOf(TreasuryChannel.CASH, TreasuryChannel.BANK)) {
            "payroll_payment_channel_unsupported"
        }
        require(
            (periodStartEpochDay == 0L && periodEndEpochDay == 0L) ||
                (periodStartEpochDay > 0L && periodEndEpochDay >= periodStartEpochDay),
        ) { "بازه حضور دوره حقوق معتبر نیست." }
        return copy(notes = notes.trim(), commandId = normalizedCommandId)
    }
}

data class PayrollCalculation(
    val baseSalaryRial: Long,
    val overtimeRial: Long,
    val bonusRial: Long,
    val allowancesRial: Long,
    val grossPayRial: Long,
    val deductionsRial: Long,
    val insuranceRial: Long,
    val taxRial: Long,
    val advanceDeductionRial: Long,
    val totalDeductionsRial: Long,
    val netPayRial: Long,
    val automaticOvertimeRial: Long = 0,
    val attendanceDeductionRial: Long = 0,
    val payrollPolicyId: Long? = null,
)

enum class PayrollStatus(val storedValue: String) {
    PENDING_APPROVAL("PENDING_APPROVAL"),
    PAID("PAID"),
    REVERSED("REVERSED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class PayrollRecord(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val periodYear: Int,
    val periodMonth: Int,
    val revisionNo: Int,
    val grossPayRial: Long,
    val netPayRial: Long,
    val paymentEpochDay: Long,
    val paymentMethod: TreasuryChannel,
    val status: PayrollStatus,
    val reversalEpochDay: Long? = null,
    val reversalReason: String = "",
)

data class PayrollReversalDraft(
    val payrollId: Long,
    val reversalEpochDay: Long,
    val reason: String,
) {
    fun validated(paymentEpochDay: Long): PayrollReversalDraft {
        val normalized = reason.trim()
        require(payrollId > 0) { "فیش حقوق معتبر نیست." }
        require(reversalEpochDay >= paymentEpochDay) { "تاریخ ابطال نمی‌تواند قبل از تاریخ پرداخت باشد." }
        require(normalized.length in 3..200) { "دلیل ابطال باید بین ۳ تا ۲۰۰ نویسه باشد." }
        return copy(reason = normalized)
    }
}

interface PersonnelRepository {
    val employees: Flow<List<EmployeeRecord>>
    val payrolls: Flow<List<PayrollRecord>>
    val attendance: Flow<List<AttendanceRecord>>
    val leaves: Flow<List<LeaveRecord>>
    val pendingLeaves: Flow<List<LeaveRecord>>
    val payrollPolicies: Flow<List<PayrollPolicyRecord>>
    val shiftTemplates: Flow<List<ShiftTemplateRecord>>
    val workSchedules: Flow<List<WorkScheduleRecord>>
    val pendingOvertimeApprovals: Flow<List<OvertimeApprovalRecord>>
    val pendingAttendanceCorrections: Flow<List<AttendanceCorrectionRecord>>
    suspend fun saveEmployee(id: Long?, draft: EmployeeDraft): Long
    suspend fun transitionEmploymentStatus(id: Long, to: EmploymentStatus, terminationEpochDay: Long? = null)
    suspend fun privateProfile(employeeId: Long): EmployeePrivateProfile?
    fun assignments(employeeId: Long): Flow<List<EmploymentAssignment>>
    suspend fun deactivateEmployee(id: Long)
    fun contracts(employeeId: Long): Flow<List<EmployeeContractRecord>>
    fun advances(employeeId: Long): Flow<List<EmployeeAdvanceRecord>>
    fun auditTimeline(employeeId: Long): Flow<List<EmployeeAuditRecord>>
    fun documents(employeeId: Long): Flow<List<HrDocumentRecord>>
    suspend fun saveDocument(draft: HrDocumentDraft): Long
    suspend fun archiveDocument(documentId: Long)
    val openAdvances: Flow<List<EmployeeAdvanceRecord>>
    suspend fun saveContract(id: Long?, draft: EmployeeContractDraft): Long
    suspend fun saveShiftTemplate(id: Long?, draft: ShiftTemplateDraft): Long
    suspend fun saveWorkSchedule(id: Long?, draft: WorkScheduleDraft): Long
    fun plannedShifts(employeeId: Long): Flow<List<PlannedShiftRecord>>
    suspend fun savePlannedShift(id: Long?, draft: PlannedShiftDraft): Long
    suspend fun approveContract(id: Long)
    suspend fun effectiveContract(employeeId: Long, businessEpochDay: Long): EmploymentContractVersion
    suspend fun payrollReadiness(employeeId: Long, businessEpochDay: Long): PayrollReadinessResult
    suspend fun saveAttendance(id: Long?, draft: AttendanceDraft): Long
    suspend fun recordAttendancePunch(draft: AttendancePunchDraft): Long
    fun attendanceEvents(employeeId: Long, limit: Int = 50): Flow<List<AttendanceEvent>>
    suspend fun attendanceSummaryV2(employeeId: Long, businessEpochDay: Long): DailyAttendanceSummaryV2
    suspend fun approveAttendanceCorrection(correctionId: Long)
    suspend fun rejectAttendanceCorrection(correctionId: Long, reason: String)
    suspend fun reviewOvertime(command: OvertimeReviewCommand)
    suspend fun attendanceSummary(employeeId: Long, startEpochDay: Long, endEpochDay: Long): AttendanceSummary
    suspend fun requestLeave(draft: LeaveDraft, requestedBy: String = "SYSTEM"): Long
    suspend fun grantLeave(draft: LeaveGrantDraft): Long
    suspend fun leaveBalance(employeeId: Long, leaveType: LeaveType): LeaveBalance
    suspend fun reviewLeave(draft: LeaveReviewDraft)
    suspend fun cancelLeave(id: Long)
    suspend fun approveLeave(draft: LeaveDraft): Long
    suspend fun postAdvance(draft: EmployeeAdvanceDraft): Long
    suspend fun settleAdvance(
        id: Long,
        amountRial: Long,
        paymentMethod: TreasuryChannel,
        settlementEpochDay: Long,
    )
    suspend fun savePayrollPolicy(draft: PayrollPolicyDraft): Long
    suspend fun attendancePayrollAdjustment(employeeId: Long, startEpochDay: Long, endEpochDay: Long, policy: AttendancePayrollPolicy): AttendancePayrollAdjustment
}
