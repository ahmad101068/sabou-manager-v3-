package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employee_private_profiles",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["nationalId"], unique = true)],
)
data class EmployeePrivateProfileEntity(
    @PrimaryKey val employeeId: Long,
    val nationalId: String?,
    val insuranceNumber: String?,
    val bankName: String?,
    val bankAccountLast4: String?,
    val ibanLast4: String?,
    val accountHolder: String?,
    val emergencyContact: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedByActorId: Long?,
)

@Entity(
    tableName = "employment_assignments",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["employeeId", "effectiveFromEpochDay"]),
        Index(value = ["employeeId", "effectiveToEpochDay"]),
        Index("department"),
        Index("branchName"),
        Index("branchId"),
        Index("locationId"),
        Index("managerId"),
    ],
)
data class EmploymentAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val jobTitle: String,
    val department: String,
    val branchName: String,
    val branchId: Long? = null,
    val locationId: Long?,
    val managerId: Long?,
    val reason: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: Long?,
    val correlationId: String,
)

@Entity(
    tableName = "employment_contract_versions",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["contractNumber"], unique = true),
        Index(value = ["employeeId", "effectiveFromEpochDay"]),
        Index(value = ["employeeId", "effectiveToEpochDay"]),
        Index(value = ["employeeId", "status"]),
        Index("replacesContractId"),
        Index("payrollPolicyId"),
        Index("overtimePolicyId"),
        Index("workScheduleId"),
        Index("defaultShiftTemplateId"),
        Index("correlationId"),
    ],
)
data class EmploymentContractVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val contractNumber: String,
    val versionNo: Int,
    val replacesContractId: Long?,
    val contractType: String,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val baseSalaryRial: Long,
    val standardDailyMinutes: Int,
    val standardWeeklyMinutes: Int,
    val overtimePolicyId: Long?,
    val payrollPolicyId: Long?,
    val workScheduleId: Long?,
    val defaultShiftTemplateId: Long?,
    val jobTitleSnapshot: String,
    val departmentSnapshot: String,
    val branchSnapshot: String,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
    val createdByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val approvedByActorId: Long?,
    val correlationId: String,
    val source: String,
)

@Entity(
    tableName = "attendance_events",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId", "businessEpochDay", "timestampEpochMillis"]),
        Index(value = ["employeeId", "eventType", "businessEpochDay"]),
        Index("branchId"),
        Index("correlationId"),
    ],
)
data class AttendanceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val idempotencyKey: String,
    val employeeId: Long,
    val eventType: String,
    val businessEpochDay: Long,
    val timestampEpochMillis: Long,
    val minuteOfDay: Int,
    val source: String,
    val deviceId: String?,
    val locationId: Long?,
    val branchId: Long?,
    val createdByActorId: Long?,
    val reason: String?,
    val correlationId: String,
)

@Entity(
    tableName = "attendance_corrections",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId", "businessEpochDay"]),
        Index("status"),
        Index("correlationId"),
    ],
)
data class AttendanceCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val businessEpochDay: Long,
    val idempotencyKey: String,
    val beforeSnapshot: String,
    val afterSnapshot: String,
    val reason: String,
    val status: String,
    val requestedByActorId: Long,
    val approvedByActorId: Long?,
    val requestedAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val correlationId: String,
)

@Entity(
    tableName = "overtime_approvals",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["commandId"], unique = true),
        Index(value = ["employeeId", "businessEpochDay"], unique = true),
        Index("status"),
        Index("reviewedByActorId"),
        Index("correlationId"),
    ],
)
data class OvertimeApprovalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandId: String,
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
    val correlationId: String,
)

@Entity(
    tableName = "leave_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LeaveEntity::class,
            parentColumns = ["id"],
            childColumns = ["leaveId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId", "leaveType", "businessEpochDay"]),
        Index("leaveId"),
        Index("correlationId"),
    ],
)
data class LeaveLedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val idempotencyKey: String,
    val employeeId: Long,
    val leaveType: String,
    val entryType: String,
    val amountMicros: Long,
    val leaveId: Long?,
    val businessEpochDay: Long,
    val reason: String,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
    val correlationId: String,
)

@Entity(
    tableName = "payroll_periods",
    indices = [
        Index(value = ["periodKey"], unique = true),
        Index(value = ["startEpochDay", "endEpochDay"]),
        Index("status"),
    ],
)
data class PayrollPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodKey: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val paymentDueEpochDay: Long?,
    val status: String,
    val openedByActorId: Long?,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long?,
    val reopenedAtEpochMillis: Long?,
    val rowVersion: Long,
    val source: String,
)

@Entity(
    tableName = "payroll_batches",
    foreignKeys = [
        ForeignKey(
            entity = PayrollPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["documentNumber"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["periodId", "status"]),
        Index("branchName"),
        Index("branchId"),
        Index("department"),
        Index("correlationId"),
    ],
)
data class PayrollBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentNumber: String,
    val idempotencyKey: String,
    val periodId: Long,
    val scope: String,
    val branchName: String?,
    val branchId: Long? = null,
    val department: String?,
    val status: String,
    val createdByActorId: Long?,
    val calculatedByActorId: Long?,
    val calculatedAtEpochMillis: Long?,
    val reviewedByActorId: Long?,
    val reviewedAtEpochMillis: Long?,
    val approvedByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val correlationId: String,
    val notes: String,
    val rowVersion: Long,
    val accrualJournalEntryId: Long?,
    val reversalJournalEntryId: Long?,
    val source: String,
)

@Entity(
    tableName = "payroll_payslips",
    foreignKeys = [
        ForeignKey(
            entity = PayrollBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PayrollPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["employeeId", "periodId", "revisionNo"], unique = true),
        Index(value = ["batchId", "status"]),
        Index(value = ["employeeId", "periodId"]),
        Index("periodId"),
        Index("replacesPayslipId"),
        Index("contractId"),
        Index("correlationId"),
    ],
)
data class PayrollPayslipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val batchId: Long,
    val periodId: Long,
    val employeeId: Long,
    val employeeCodeSnapshot: String,
    val employeeNameSnapshot: String,
    val revisionNo: Int,
    val replacesPayslipId: Long?,
    val legacyPayrollRunId: Long?,
    val contractId: Long?,
    val status: String,
    val grossPayRial: Long,
    val totalDeductionsRial: Long,
    val netPayRial: Long,
    val paidAmountRial: Long,
    val remainingAmountRial: Long,
    val componentDetailComplete: Boolean,
    val calculatedAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val paidAtEpochMillis: Long?,
    val correlationId: String,
    val source: String,
    val rowVersion: Long,
    val accrualJournalEntryId: Long?,
    val reversalJournalEntryId: Long?,
    val reversalReason: String?,
    val reversalEpochDay: Long?,
    val reversedAtEpochMillis: Long?,
)

@Entity(
    tableName = "payroll_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PayrollPayslipEntity::class,
            parentColumns = ["id"],
            childColumns = ["payslipId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("contractId"), Index("payrollPolicyId")],
)
data class PayrollSnapshotEntity(
    @PrimaryKey val payslipId: Long,
    val employeeId: Long,
    val employeeCode: String,
    val employeeDisplayName: String,
    val contractId: Long?,
    val contractNumber: String?,
    val contractVersionNo: Int?,
    val baseSalaryRial: Long?,
    val standardPeriodMinutes: Int?,
    val eligiblePeriodMinutes: Int?,
    val actualWorkMinutes: Int?,
    val overtimeMinutes: Int?,
    val absenceMinutes: Int?,
    val lateMinutes: Int?,
    val paidLeaveMinutes: Int?,
    val unpaidLeaveMinutes: Int?,
    val payrollPolicyId: Long?,
    val payrollPolicyVersion: Int?,
    val overtimeRateRialPerHour: Long?,
    val overtimeMultiplierBasisPoints: Int?,
    val insuranceBasisPoints: Int?,
    val taxBasisPoints: Int?,
    @ColumnInfo(defaultValue = "0") val nightMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val holidayMinutes: Int = 0,
    @ColumnInfo(defaultValue = "10000") val nightMultiplierBasisPoints: Int = 10_000,
    @ColumnInfo(defaultValue = "10000") val holidayMultiplierBasisPoints: Int = 10_000,
    val grossPayRial: Long,
    val totalDeductionsRial: Long,
    val netPayRial: Long,
    val calculationVersion: String?,
    val calculationParameters: String,
    val snapshotHash: String,
    val capturedAtEpochMillis: Long,
    val detailComplete: Boolean,
)

@Entity(
    tableName = "payroll_components",
    foreignKeys = [
        ForeignKey(
            entity = PayrollPayslipEntity::class,
            parentColumns = ["id"],
            childColumns = ["payslipId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["payslipId", "direction"]),
        Index(value = ["sourceType", "sourceId"]),
        Index("componentType"),
    ],
)
data class PayrollComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payslipId: Long,
    val componentType: String,
    val description: String,
    val quantity: Long?,
    val rateRial: Long?,
    val amountRial: Long,
    val direction: String,
    val sourceType: String,
    val sourceId: Long?,
    val manualOverride: Boolean,
    val overrideReason: String?,
    val createdByActorId: Long?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "payroll_manual_adjustments",
    foreignKeys = [
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PayrollPeriodEntity::class, parentColumns = ["id"], childColumns = ["periodId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["employeeId", "periodId", "status"]),
        Index("periodId"),
        Index("consumedByPayslipId"),
        Index("correlationId"),
    ],
)
data class PayrollManualAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val idempotencyKey: String,
    val employeeId: Long,
    val periodId: Long,
    val componentType: String,
    val direction: String,
    val amountRial: Long,
    val reason: String,
    val attachmentMetadata: String?,
    val status: String,
    val createdByActorId: Long,
    val approvedByActorId: Long?,
    val createdAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val consumedByPayslipId: Long?,
    val correlationId: String,
)

@Entity(
    tableName = "payroll_approval_events",
    foreignKeys = [
        ForeignKey(entity = PayrollBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PayrollPayslipEntity::class, parentColumns = ["id"], childColumns = ["payslipId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["batchId", "createdAtEpochMillis"]),
        Index(value = ["payslipId", "createdAtEpochMillis"]),
        Index("correlationId"),
    ],
)
data class PayrollApprovalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val payslipId: Long?,
    val eventType: String,
    val fromStatus: String,
    val toStatus: String,
    val actorId: Long,
    val reason: String,
    val snapshotHash: String?,
    val createdAtEpochMillis: Long,
    val correlationId: String,
)

@Entity(
    tableName = "payroll_payments",
    foreignKeys = [
        ForeignKey(entity = PayrollPayslipEntity::class, parentColumns = ["id"], childColumns = ["payslipId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["payslipId", "status", "paymentEpochDay"]),
        Index(value = ["reversalOfPaymentId"], unique = true),
        Index("treasuryTransactionId"),
        Index("correlationId"),
    ],
)
data class PayrollPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val idempotencyKey: String,
    val payslipId: Long,
    val amountRial: Long,
    val treasuryAccountId: String,
    val channel: String,
    val paymentEpochDay: Long,
    val paymentReference: String,
    val status: String,
    val treasuryTransactionId: String,
    val journalEntryId: Long?,
    val reversalOfPaymentId: Long?,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
    val reversedAtEpochMillis: Long?,
    val reversalReason: String?,
    val correlationId: String,
)

@Entity(
    tableName = "payroll_advance_allocations_v2",
    foreignKeys = [
        ForeignKey(entity = PayrollPayslipEntity::class, parentColumns = ["id"], childColumns = ["payslipId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeAdvanceEntity::class, parentColumns = ["id"], childColumns = ["advanceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["payslipId", "advanceId"], unique = true),
        Index(value = ["advanceId", "status"]),
        Index("correlationId"),
    ],
)
data class PayrollAdvanceAllocationV2Entity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,
    val payslipId: Long,
    val advanceId: Long,
    val amountRial: Long,
    val status: String,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
    val reversedAtEpochMillis: Long?,
    val reversalReason: String?,
    val correlationId: String,
)

@Entity(
    tableName = "payroll_exceptions",
    foreignKeys = [
        ForeignKey(entity = PayrollBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PayrollPayslipEntity::class, parentColumns = ["id"], childColumns = ["payslipId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["batchId", "resolvedAtEpochMillis"]),
        Index(value = ["employeeId", "code"]),
        Index("payslipId"),
    ],
)
data class PayrollExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val payslipId: Long?,
    val employeeId: Long?,
    val code: String,
    val blocking: Boolean,
    val detail: String,
    val createdAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long?,
    val resolvedByActorId: Long?,
    val resolutionNote: String?,
)

@Entity(
    tableName = "hr_payroll_migration_anomalies",
    indices = [Index(value = ["entityType", "entityId"]), Index("code")],
)
data class HrPayrollMigrationAnomalyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val code: String,
    val detail: String,
    val detectedAtEpochMillis: Long,
)

@Entity(
    tableName = "hr_payroll_command_receipts",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["commandType", "resultEntityType", "resultEntityId"]),
        Index("correlationId"),
    ],
)
data class HrPayrollCommandReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idempotencyKey: String,
    val commandType: String,
    val payloadHash: String,
    val resultEntityType: String,
    val resultEntityId: Long,
    val resultDetail: String,
    val actorId: Long,
    val createdAtEpochMillis: Long,
    val correlationId: String,
)
