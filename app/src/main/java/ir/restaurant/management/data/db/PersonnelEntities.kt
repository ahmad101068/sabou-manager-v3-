package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import ir.restaurant.management.core.GlobalId

@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["nationalId"], unique = true),
        Index(value = ["employeeCode"], unique = true),
        Index("name"),
        Index("displayName"),
        Index("phone"),
        Index("jobTitle"),
        Index("branchName"),
        Index("branchId"),
        Index("status"),
        Index("department"),
        Index("locationId"),
        Index("managerId"),
        Index("terminationEpochDay"),
    ],
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val firstName: String = "",
    @ColumnInfo(defaultValue = "''") val lastName: String = "",
    @ColumnInfo(defaultValue = "''") val displayName: String = "",
    @ColumnInfo(defaultValue = "''") val fatherName: String = "",
    val employeeCode: String? = null,
    val jobTitle: String,
    @ColumnInfo(defaultValue = "''") val department: String = "",
    val branchName: String = "",
    val branchId: Long? = null,
    val locationId: Long? = null,
    val managerId: Long? = null,
    val phone: String = "",
    val email: String? = null,
    val nationalId: String? = null,
    val birthEpochDay: Long? = null,
    val hireEpochDay: Long? = null,
    val terminationEpochDay: Long? = null,
    val insuranceNumber: String? = null,
    val bankCard: String? = null,
    val address: String = "",
    val emergencyContact: String = "",
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val monthlySalaryRial: Long,
    val leaveBalanceMicros: Long,
    val status: String = "ACTIVE",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val createdByActorId: Long? = null,
    val updatedByActorId: Long? = null,
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["employeeId", "workEpochDay"], unique = true),
        Index("workEpochDay"),
    ],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val workEpochDay: Long,
    val status: String,
    val checkInMinute: Int?,
    val checkOutMinute: Int?,
    val lateMinutes: Int,
    @ColumnInfo(defaultValue = "0") val rawLateMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val payableLateMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val earlyLeaveMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val rawOvertimeMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val approvedOvertimeMinutes: Int = 0,
    val overtimeMinutes: Int,
    val notes: String,
)

@Entity(
    tableName = "leaves",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"), Index("startEpochDay"), Index("endEpochDay"), Index("status"),
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId", "status", "startEpochDay", "endEpochDay"]),
        Index("correlationId"),
    ],
)
data class LeaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = "",
    val idempotencyKey: String? = null,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val daysMicros: Long,
    val leaveType: String,
    val status: String,
    val notes: String,
    val requestedBy: String = "SYSTEM",
    val requestedByActorId: Long? = null,
    val reviewedBy: String? = null,
    val reviewedByActorId: Long? = null,
    val reviewNotes: String = "",
    val reviewedAtEpochMillis: Long? = null,
    val cancelledAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "",
)


@Entity(
    tableName = "payroll_policies",
    indices = [Index("effectiveFromEpochDay"), Index("effectiveToEpochDay")],
)
data class PayrollPolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(defaultValue = "1") val versionNo: Int = 1,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val overtimeHourlyRateRial: Long,
    val absenceDailyDeductionRial: Long,
    val lateMinuteDeductionRial: Long,
    @ColumnInfo(defaultValue = "10000") val overtimeMultiplierBasisPoints: Int = 10_000,
    @ColumnInfo(defaultValue = "0") val insuranceBasisPoints: Int = 0,
    @ColumnInfo(defaultValue = "0") val taxBasisPoints: Int = 0,
    @ColumnInfo(defaultValue = "10000") val holidayMultiplierBasisPoints: Int = 10_000,
    @ColumnInfo(defaultValue = "10000") val nightMultiplierBasisPoints: Int = 10_000,
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    val createdBy: String,
    val createdByActorId: Long? = null,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "",
)

@Entity(
    tableName = "payroll_runs",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["employeeId", "periodYear", "periodMonth", "revisionNo"], unique = true),
        Index("paymentEpochDay"),
        Index("journalEntryId"),
        Index("payrollPolicyId"),
        Index(value = ["globalId"], unique = true),
        Index("status"),
        Index("createdByActorId"),
        Index("approvedByActorId"),
        Index("correlationId"),
    ],
)
data class PayrollRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val periodYear: Int,
    val periodMonth: Int,
    @ColumnInfo(defaultValue = "1") val revisionNo: Int = 1,
    val baseSalaryRial: Long,
    val overtimeRial: Long,
    val bonusRial: Long,
    val deductionsRial: Long,
    @ColumnInfo(defaultValue = "0") val advanceDeductionRial: Long = 0,
    @ColumnInfo(defaultValue = "0") val periodStartEpochDay: Long = 0,
    @ColumnInfo(defaultValue = "0") val periodEndEpochDay: Long = 0,
    val payrollPolicyId: Long? = null,
    @ColumnInfo(defaultValue = "0") val automaticOvertimeRial: Long = 0,
    @ColumnInfo(defaultValue = "0") val attendanceDeductionRial: Long = 0,
    val insuranceRial: Long,
    val taxRial: Long,
    val netPayRial: Long,
    val paymentEpochDay: Long,
    val paymentMethod: String,
    val journalEntryId: Long,
    val status: String = "PENDING_APPROVAL",
    val approvedBy: String? = null,
    val approvedAtEpochMillis: Long? = null,
    val reversalEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val reversalReason: String = "",
    val reversalJournalEntryId: Long? = null,
    val reversedBy: String? = null,
    val notes: String = "",
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val createdByActorId: Long? = null,
    val approvedByActorId: Long? = null,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "",
)

@Entity(
    tableName = "payroll_advance_allocations",
    primaryKeys = ["payrollId", "advanceId"],
    foreignKeys = [
        ForeignKey(entity = PayrollRunEntity::class, parentColumns = ["id"], childColumns = ["payrollId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EmployeeAdvanceEntity::class, parentColumns = ["id"], childColumns = ["advanceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("advanceId")],
)
data class PayrollAdvanceAllocationEntity(
    val payrollId: Long,
    val advanceId: Long,
    val amountRial: Long,
    val createdAtEpochMillis: Long,
)


@Entity(
    tableName = "employee_contracts",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index("startEpochDay"),
        Index("endEpochDay"),
        Index("status"),
    ],
)
data class EmployeeContractEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val contractType: String,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val baseSalaryRial: Long,
    val dailyWorkMinutes: Int,
    val weeklyWorkDays: Int,
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "employee_advances",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index(value = ["employeeId", "status"]),
        Index("advanceEpochDay"),
        Index("journalEntryId"),
        Index("status"),
    ],
)
data class EmployeeAdvanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val amountRial: Long,
    val advanceEpochDay: Long,
    val paymentMethod: String,
    val journalEntryId: Long,
    val settledAmountRial: Long = 0,
    val status: String = "OPEN",
    val notes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "performance_goals",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index("periodStartEpochDay"),
        Index("periodEndEpochDay"),
        Index("status"),
    ],
)
data class PerformanceGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val title: String,
    val description: String = "",
    val weightPercent: Int,
    @ColumnInfo(name = "targetValue") val legacyTargetValue: Double?,
    val targetValueMicros: Long? = null,
    val unit: String = "",
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val status: String = "ACTIVE",
    val createdBy: String = "SYSTEM",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "performance_reviews",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("employeeId"),
        Index("periodStartEpochDay"),
        Index("periodEndEpochDay"),
        Index("status"),
    ],
)
data class PerformanceReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val reviewerName: String,
    val finalScoreBasisPoints: Int = 0,
    val status: String = "DRAFT",
    val managerComment: String = "",
    val employeeComment: String = "",
    val submittedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "performance_scores",
    foreignKeys = [
        ForeignKey(
            entity = PerformanceReviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["reviewId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PerformanceGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["reviewId", "goalId"], unique = true),
        Index("goalId"),
    ],
)
data class PerformanceScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reviewId: Long,
    val goalId: Long,
    @ColumnInfo(name = "achievedValue") val legacyAchievedValue: Double?,
    val achievedValueMicros: Long? = null,
    val scoreBasisPoints: Int,
    val weightedScoreBasisPoints: Int,
    val notes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
