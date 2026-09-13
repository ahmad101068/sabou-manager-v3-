package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.BusinessCalendar

import ir.restaurant.management.core.GlobalId

enum class ShiftCategory(val storedValue: String, val faLabel: String) {
    MORNING("MORNING", "صبح"),
    MID("MID", "میانی"),
    EVENING("EVENING", "عصر"),
    NIGHT("NIGHT", "شب"),
    FULL_NIGHT("FULL_NIGHT", "شب کامل"),
    SPLIT("SPLIT", "شکسته"),
    FLEXIBLE("FLEXIBLE", "شناور"),
    ROTATING("ROTATING", "چرخشی"),
    CUSTOM("CUSTOM", "سفارشی");

    companion object {
        fun fromStoredValue(value: String): ShiftCategory = entries.firstOrNull {
            it.storedValue == value.trim().uppercase()
        } ?: CUSTOM
    }
}

enum class WorkSchedulePatternType(val storedValue: String) {
    WEEKLY_FIXED("WEEKLY_FIXED"),
    ROTATING("ROTATING"),
    SPLIT("SPLIT"),
    CUSTOM("CUSTOM");

    companion object {
        fun fromStoredValue(value: String): WorkSchedulePatternType = entries.firstOrNull {
            it.storedValue == value.trim().uppercase()
        } ?: CUSTOM
    }
}

enum class PlannedShiftStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    LOCKED("LOCKED"),
    CANCELLED("CANCELLED");

    companion object {
        fun fromStoredValue(value: String): PlannedShiftStatus = entries.firstOrNull {
            it.storedValue == value.trim().uppercase()
        } ?: DRAFT
    }
}

data class ShiftTemplateRecord(
    val id: Long,
    val code: String,
    val name: String,
    val category: ShiftCategory,
    val startMinute: Int,
    val endMinute: Int,
    val crossesMidnight: Boolean,
    val plannedWorkMinutes: Int,
    val breakMinutes: Int,
    val graceInMinutes: Int,
    val graceOutMinutes: Int,
    val overtimeEligible: Boolean,
    val overtimeRequiresApproval: Boolean,
    val nightShift: Boolean,
    val active: Boolean,
    val branchId: Long?,
    val notes: String,
)

data class ShiftTemplateDraft(
    val code: String,
    val name: String,
    val category: ShiftCategory,
    val startMinute: Int,
    val endMinute: Int,
    val breakMinutes: Int = 0,
    val graceInMinutes: Int = 0,
    val graceOutMinutes: Int = 0,
    val overtimeEligible: Boolean = true,
    val overtimeRequiresApproval: Boolean = true,
    val nightShift: Boolean = category in setOf(ShiftCategory.NIGHT, ShiftCategory.FULL_NIGHT),
    val active: Boolean = true,
    val branchId: Long? = null,
    val notes: String = "",
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute
    val grossMinutes: Int get() = if (crossesMidnight) 1440 - startMinute + endMinute else endMinute - startMinute
    val plannedWorkMinutes: Int get() = grossMinutes - breakMinutes

    fun validated(): ShiftTemplateDraft {
        val normalizedCode = code.trim().uppercase().replace(' ', '_')
        if (normalizedCode.isNotEmpty()) {
            require(normalizedCode.matches(Regex("[A-Z][A-Z0-9_-]{1,39}"))) { "کد شیفت معتبر نیست." }
        }
        require(name.trim().length in 2..80) { "نام شیفت معتبر نیست." }
        require(startMinute in 0..1439 && endMinute in 0..1439) { "زمان شیفت معتبر نیست." }
        require(startMinute != endMinute) { "شیفت نمی‌تواند طول صفر داشته باشد." }
        require(grossMinutes in 30..(24 * 60)) { "طول شیفت معتبر نیست." }
        require(breakMinutes in 0 until grossMinutes) { "زمان استراحت معتبر نیست." }
        require(plannedWorkMinutes in 1..(24 * 60)) { "زمان کار برنامه‌ریزی‌شده معتبر نیست." }
        require(graceInMinutes in 0..180 && graceOutMinutes in 0..180) { "بازه ارفاق معتبر نیست." }
        require(branchId == null || branchId > 0) { "شناسه شعبه معتبر نیست." }
        require(notes.length <= 500) { "یادداشت شیفت طولانی است." }
        return copy(code = normalizedCode, name = name.trim(), notes = notes.trim())
    }
}

data class WorkScheduleDayRule(
    val sequenceDay: Int,
    val dayOfWeek: Int?,
    val shiftTemplateId: Long?,
    val isOffDay: Boolean,
    val overrideStartMinute: Int? = null,
    val overrideEndMinute: Int? = null,
    val notes: String = "",
) {
    fun validated(cycleLengthDays: Int): WorkScheduleDayRule {
        require(sequenceDay in 0 until cycleLengthDays) { "روز الگو معتبر نیست." }
        require(dayOfWeek == null || dayOfWeek in 1..7) { "روز هفته معتبر نیست." }
        require(isOffDay || (shiftTemplateId != null && shiftTemplateId > 0)) { "برای روز کاری باید شیفت انتخاب شود." }
        require(!isOffDay || shiftTemplateId == null) { "روز استراحت نباید شیفت داشته باشد." }
        require((overrideStartMinute == null) == (overrideEndMinute == null)) { "شروع و پایان override باید همزمان تعیین شوند." }
        overrideStartMinute?.let { require(it in 0..1439) { "شروع override معتبر نیست." } }
        overrideEndMinute?.let { require(it in 0..1439) { "پایان override معتبر نیست." } }
        require(notes.length <= 300)
        return copy(notes = notes.trim())
    }
}

data class WorkScheduleRecord(
    val id: Long,
    val code: String,
    val name: String,
    val patternType: WorkSchedulePatternType,
    val cycleLengthDays: Int,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val active: Boolean,
    val branchName: String,
    val branchId: Long?,
    val notes: String,
    val days: List<WorkScheduleDayRule> = emptyList(),
)

data class WorkScheduleDraft(
    val code: String,
    val name: String,
    val patternType: WorkSchedulePatternType,
    val cycleLengthDays: Int,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long? = null,
    val active: Boolean = true,
    val branchName: String = "",
    val branchId: Long? = null,
    val notes: String = "",
    val days: List<WorkScheduleDayRule>,
) {
    fun validated(): WorkScheduleDraft {
        val normalizedCode = code.trim().uppercase().replace(' ', '_')
        if (normalizedCode.isNotEmpty()) {
            require(normalizedCode.matches(Regex("[A-Z][A-Z0-9_-]{1,39}"))) { "کد برنامه کاری معتبر نیست." }
        }
        require(name.trim().length in 2..100) { "نام برنامه کاری معتبر نیست." }
        require(cycleLengthDays in 1..56) { "طول چرخه برنامه کاری معتبر نیست." }
        require(effectiveFromEpochDay > 0) { "تاریخ شروع برنامه کاری معتبر نیست." }
        require(effectiveToEpochDay == null || effectiveToEpochDay >= effectiveFromEpochDay) { "تاریخ پایان برنامه کاری معتبر نیست." }
        require(branchId == null || branchId > 0) { "شناسه شعبه معتبر نیست." }
        require(days.isNotEmpty()) { "برنامه کاری باید حداقل یک روز تعریف‌شده داشته باشد." }
        val validDays = days.map { it.validated(cycleLengthDays) }
        require(validDays.map { it.sequenceDay }.distinct().size == validDays.size) { "روز تکراری در الگوی کاری وجود دارد." }
        if (patternType == WorkSchedulePatternType.WEEKLY_FIXED) {
            require(cycleLengthDays == 7) { "برنامه هفتگی باید چرخه ۷ روزه داشته باشد." }
            require(validDays.mapNotNull { it.dayOfWeek }.distinct().size == validDays.size) { "روز هفته تکراری است." }
        }
        return copy(code = normalizedCode, name = name.trim(), branchName = branchName.trim(), notes = notes.trim(), days = validDays)
    }
}

data class PlannedShiftRecord(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val role: String,
    val businessEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val shiftTemplateId: Long?,
    val scheduleId: Long?,
    val plannedStartEpochMillis: Long,
    val plannedEndEpochMillis: Long,
    val breakMinutes: Int,
    val status: PlannedShiftStatus,
    val source: String,
    val overrideReason: String,
    val auditRef: String,
) {
    val crossesMidnight: Boolean get() = plannedEndEpochMillis > plannedStartEpochMillis + (24L * 60L * 60L * 1000L - 1L) || endMinute <= startMinute
    val plannedDurationMinutes: Int get() = ((plannedEndEpochMillis - plannedStartEpochMillis) / 60_000L).toInt().coerceAtLeast(0)
}

data class PlannedShiftDraft(
    val employeeId: Long,
    val businessEpochDay: Long,
    val shiftTemplateId: Long,
    val scheduleId: Long? = null,
    val overrideStartMinute: Int? = null,
    val overrideEndMinute: Int? = null,
    val overrideReason: String = "",
    val status: PlannedShiftStatus = PlannedShiftStatus.DRAFT,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PlannedShiftDraft {
        require(employeeId > 0 && businessEpochDay > 0 && shiftTemplateId > 0) { "اطلاعات شیفت برنامه‌ریزی‌شده ناقص است." }
        require(scheduleId == null || scheduleId > 0)
        require((overrideStartMinute == null) == (overrideEndMinute == null))
        overrideStartMinute?.let { require(it in 0..1439) }
        overrideEndMinute?.let { require(it in 0..1439) }
        require(overrideReason.length <= 500)
        return copy(overrideReason = overrideReason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

object PlannedShiftTime {
    fun startEpochMillis(businessEpochDay: Long, startMinute: Int): Long =
        BusinessCalendar.epochMillisAtMinute(businessEpochDay, startMinute)

    fun endEpochMillis(businessEpochDay: Long, startMinute: Int, endMinute: Int): Long {
        val endDay = if (endMinute <= startMinute) Math.addExact(businessEpochDay, 1L) else businessEpochDay
        return BusinessCalendar.epochMillisAtMinute(endDay, endMinute)
    }
}

data class OvertimeApprovalRecord(
    val id: Long,
    val employeeId: Long,
    val businessEpochDay: Long,
    val rawMinutes: Int,
    val approvedMinutes: Int,
    val rejectedMinutes: Int,
    val status: String,
    val reason: String,
    val requestedByActorId: Long?,
    val reviewedByActorId: Long?,
    val requestedAtEpochMillis: Long,
    val reviewedAtEpochMillis: Long?,
)

data class OvertimeReviewCommand(
    val approvalId: Long,
    val approvedMinutes: Int,
    val reject: Boolean = false,
    val reason: String,
) {
    fun validated(rawMinutes: Int): OvertimeReviewCommand {
        require(approvalId > 0)
        require(rawMinutes >= 0)
        require(approvedMinutes in 0..rawMinutes) { "دقیقه اضافه‌کار تأییدشده معتبر نیست." }
        if (reject) require(approvedMinutes == 0) { "در رد اضافه‌کار، دقیقه تأییدشده باید صفر باشد." }
        require(reason.trim().length in 3..500) { "دلیل بررسی اضافه‌کار الزامی است." }
        return copy(reason = reason.trim())
    }
}

enum class PayrollReadinessStatus(val storedValue: String, val persianLabel: String) {
    READY("READY", "آماده محاسبه حقوق"),
    WARNING("WARNING", "نیازمند بررسی"),
    BLOCKED("BLOCKED", "نیازمند تکمیل");
}

data class PayrollReadinessIssue(
    val code: String,
    val message: String,
    val blocking: Boolean,
    val action: String,
)

data class PayrollReadinessResult(
    val employeeId: Long,
    val businessEpochDay: Long,
    val status: PayrollReadinessStatus,
    val issues: List<PayrollReadinessIssue>,
) {
    val ready: Boolean get() = status == PayrollReadinessStatus.READY
}

data class EmployeeAuditRecord(
    val id: Long,
    val occurredAtEpochMillis: Long,
    val businessEpochDay: Long?,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val reason: String,
    val beforeSnapshot: String?,
    val afterSnapshot: String?,
    val correlationId: String,
)

enum class HrDocumentType(val storedValue: String, val persianLabel: String) {
    CONTRACT("CONTRACT", "قرارداد"),
    ID_DOCUMENT("ID_DOCUMENT", "مدرک هویتی"),
    CERTIFICATE("CERTIFICATE", "گواهی"),
    MEDICAL("MEDICAL", "مدرک پزشکی"),
    HR_ATTACHMENT("HR_ATTACHMENT", "پیوست منابع انسانی");
}

data class HrDocumentRecord(
    val id: Long,
    val employeeId: Long,
    val documentType: HrDocumentType,
    val displayName: String,
    val contentUri: String,
    val mimeType: String,
    val issueEpochDay: Long?,
    val expiryEpochDay: Long?,
    val notes: String,
)

data class HrDocumentDraft(
    val employeeId: Long,
    val documentType: HrDocumentType,
    val displayName: String,
    val contentUri: String,
    val mimeType: String,
    val issueEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val notes: String = "",
) {
    fun validated(): HrDocumentDraft {
        require(employeeId > 0)
        require(displayName.trim().isNotBlank()) { "نام سند الزامی است." }
        require(contentUri.startsWith("content://")) { "آدرس سند معتبر نیست." }
        require(mimeType.isNotBlank()) { "نوع فایل سند مشخص نیست." }
        require(issueEpochDay == null || issueEpochDay > 0)
        require(expiryEpochDay == null || expiryEpochDay > 0)
        require(issueEpochDay == null || expiryEpochDay == null || expiryEpochDay >= issueEpochDay) { "تاریخ انقضا قبل از تاریخ صدور است." }
        return copy(displayName = displayName.trim(), mimeType = mimeType.trim(), notes = notes.trim())
    }
}
