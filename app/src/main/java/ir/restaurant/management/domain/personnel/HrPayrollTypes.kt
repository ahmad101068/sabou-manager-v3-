package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

enum class EmploymentStatus(val storedValue: String) {
    APPLICANT("APPLICANT"),
    ACTIVE("ACTIVE"),
    ON_LEAVE("ON_LEAVE"),
    SUSPENDED("SUSPENDED"),
    TERMINATED("TERMINATED"),
    ARCHIVED("ARCHIVED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): EmploymentStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() }
                ?: LEGACY_UNKNOWN
    }
}

object EmploymentStatusTransitionValidator {
    private val transitions = mapOf(
        EmploymentStatus.APPLICANT to setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ARCHIVED),
        EmploymentStatus.ACTIVE to setOf(
            EmploymentStatus.ON_LEAVE,
            EmploymentStatus.SUSPENDED,
            EmploymentStatus.TERMINATED,
            EmploymentStatus.ARCHIVED,
        ),
        EmploymentStatus.ON_LEAVE to setOf(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED),
        EmploymentStatus.SUSPENDED to setOf(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED),
        EmploymentStatus.TERMINATED to setOf(EmploymentStatus.ARCHIVED),
        EmploymentStatus.ARCHIVED to emptySet(),
        EmploymentStatus.LEGACY_UNKNOWN to emptySet(),
    )

    fun requireAllowed(
        from: EmploymentStatus,
        to: EmploymentStatus,
        terminationEpochDay: Long? = null,
    ) {
        if (from == to) return
        if (to !in transitions.getValue(from)) {
            throw BusinessError.InvalidStateTransition("EMPLOYEE", from.storedValue, to.storedValue).asViolation()
        }
        if (to == EmploymentStatus.TERMINATED && (terminationEpochDay == null || terminationEpochDay <= 0)) {
            throw BusinessError.InvalidInput("terminationEpochDay", "required_for_termination").asViolation()
        }
    }
}

enum class EmploymentContractType(val storedValue: String) {
    PERMANENT("PERMANENT"),
    FIXED_TERM("FIXED_TERM"),
    PART_TIME("PART_TIME"),
    HOURLY("HOURLY"),
    PROBATION("PROBATION"),
    OTHER("OTHER"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): EmploymentContractType =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class EmploymentContractStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    SUPERSEDED("SUPERSEDED"),
    CANCELLED("CANCELLED"),
    LEGACY("LEGACY"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): EmploymentContractStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class AttendanceEventType(val storedValue: String) {
    CLOCK_IN("CLOCK_IN"),
    CLOCK_OUT("CLOCK_OUT"),
    BREAK_START("BREAK_START"),
    BREAK_END("BREAK_END"),
    MANUAL_ADJUSTMENT("MANUAL_ADJUSTMENT"),
    ABSENCE_MARK("ABSENCE_MARK"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): AttendanceEventType =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class AttendanceSource(val storedValue: String) {
    MANUAL("MANUAL"),
    DEVICE("DEVICE"),
    IMPORT("IMPORT"),
    SYSTEM("SYSTEM"),
    SHIFT("SHIFT"),
    LEGACY("LEGACY"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): AttendanceSource =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class DailyAttendanceStatus(val storedValue: String) {
    PRESENT("PRESENT"),
    ABSENT("ABSENT"),
    PAID_LEAVE("PAID_LEAVE"),
    UNPAID_LEAVE("UNPAID_LEAVE"),
    MISSION("MISSION"),
    HOLIDAY("HOLIDAY"),
    INCOMPLETE("INCOMPLETE"),
    ANOMALY("ANOMALY"),
}

enum class AttendanceAnomalyType {
    MISSING_CLOCK_OUT,
    DUPLICATE_CLOCK_IN,
    DUPLICATE_CLOCK_OUT,
    BREAK_NOT_CLOSED,
    NEGATIVE_DURATION,
    OVERLAPPING_SESSION,
    EXCESSIVE_DURATION,
    OUTSIDE_EMPLOYMENT_PERIOD,
}

enum class LeaveType(val storedValue: String, val paid: Boolean) {
    PAID("PAID", true),
    UNPAID("UNPAID", false),
    SICK("SICK", true),
    ANNUAL("ANNUAL", true),
    HOURLY("HOURLY", true),
    OTHER("OTHER", false),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", false);

    companion object {
        fun fromStoredValue(value: String): LeaveType =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class LeaveStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED"),
    TAKEN("TAKEN"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): LeaveStatus = when (value.trim().uppercase()) {
            "PENDING" -> SUBMITTED
            else -> entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
        }
    }
}

enum class PayrollPeriodStatus(val storedValue: String) {
    OPEN("OPEN"),
    CALCULATING("CALCULATING"),
    REVIEW("REVIEW"),
    APPROVED("APPROVED"),
    PAYMENT("PAYMENT"),
    CLOSED("CLOSED"),
    REOPENED("REOPENED"),
    LEGACY("LEGACY"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollPeriodStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class PayrollBatchStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    CALCULATED("CALCULATED"),
    UNDER_REVIEW("UNDER_REVIEW"),
    APPROVED("APPROVED"),
    PAYMENT_PENDING("PAYMENT_PENDING"),
    PARTIALLY_PAID("PARTIALLY_PAID"),
    PAID("PAID"),
    REVERSED("REVERSED"),
    CANCELLED("CANCELLED"),
    LEGACY("LEGACY"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollBatchStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class PayrollPayslipStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    CALCULATED("CALCULATED"),
    UNDER_REVIEW("UNDER_REVIEW"),
    APPROVED("APPROVED"),
    PAYMENT_PENDING("PAYMENT_PENDING"),
    PARTIALLY_PAID("PARTIALLY_PAID"),
    PAID("PAID"),
    REVERSED("REVERSED"),
    CANCELLED("CANCELLED"),
    LEGACY("LEGACY"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollPayslipStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class PayrollComponentType(val storedValue: String) {
    BASE_SALARY("BASE_SALARY"),
    OVERTIME("OVERTIME"),
    NIGHT_DIFFERENTIAL("NIGHT_DIFFERENTIAL"),
    HOLIDAY_PREMIUM("HOLIDAY_PREMIUM"),
    BONUS("BONUS"),
    ALLOWANCE("ALLOWANCE"),
    COMMISSION("COMMISSION"),
    INSURANCE("INSURANCE"),
    TAX("TAX"),
    ABSENCE_DEDUCTION("ABSENCE_DEDUCTION"),
    LATE_DEDUCTION("LATE_DEDUCTION"),
    UNPAID_LEAVE_DEDUCTION("UNPAID_LEAVE_DEDUCTION"),
    ADVANCE_DEDUCTION("ADVANCE_DEDUCTION"),
    LOAN_DEDUCTION("LOAN_DEDUCTION"),
    OTHER_EARNING("OTHER_EARNING"),
    OTHER_DEDUCTION("OTHER_DEDUCTION"),
    LEGACY_TOTAL("LEGACY_TOTAL"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollComponentType =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class PayrollComponentDirection(val storedValue: String) {
    EARNING("EARNING"),
    DEDUCTION("DEDUCTION"),
}

enum class PayrollComponentSourceType(val storedValue: String) {
    CONTRACT("CONTRACT"),
    ATTENDANCE("ATTENDANCE"),
    LEAVE("LEAVE"),
    POLICY("POLICY"),
    ADVANCE("ADVANCE"),
    MANUAL_ADJUSTMENT("MANUAL_ADJUSTMENT"),
    LEGACY_MIGRATION("LEGACY_MIGRATION"),
    SYSTEM("SYSTEM"),
}

enum class PayrollPaymentStatus(val storedValue: String) {
    POSTED("POSTED"),
    REVERSED("REVERSED"),
    FAILED("FAILED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PayrollPaymentStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

enum class PayrollDocumentSource(val storedValue: String) {
    NATIVE("NATIVE"),
    LEGACY_MIGRATION("LEGACY_MIGRATION"),
}

enum class ManualAdjustmentStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    CONSUMED("CONSUMED"),
}
