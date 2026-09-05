package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.inventory.InventoryLocationCode

@Entity(
    tableName = "shift_templates",
    indices = [
        Index(value = ["code"], unique = true),
        Index("category"),
        Index("active"),
        Index("branchId"),
    ],
)
data class ShiftTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val category: String,
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
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val createdBy: String,
    val updatedBy: String,
)

@Entity(
    tableName = "work_schedules",
    indices = [
        Index(value = ["code"], unique = true),
        Index("patternType"),
        Index("active"),
        Index("effectiveFromEpochDay"),
        Index("effectiveToEpochDay"),
        Index("branchId"),
    ],
)
data class WorkScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val patternType: String,
    val cycleLengthDays: Int,
    val effectiveFromEpochDay: Long,
    val effectiveToEpochDay: Long?,
    val active: Boolean,
    val branchName: String,
    val branchId: Long? = null,
    val notes: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val createdBy: String,
    val updatedBy: String,
)

@Entity(
    tableName = "work_schedule_days",
    foreignKeys = [
        ForeignKey(
            entity = WorkScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ShiftTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["shiftTemplateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["scheduleId", "sequenceDay"], unique = true),
        Index("shiftTemplateId"),
        Index("dayOfWeek"),
    ],
)
data class WorkScheduleDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val sequenceDay: Int,
    val dayOfWeek: Int?,
    val shiftTemplateId: Long?,
    val isOffDay: Boolean,
    val overrideStartMinute: Int?,
    val overrideEndMinute: Int?,
    val notes: String,
)

@Entity(
    tableName = "planned_shifts",
    indices = [
        Index("employeeId"),
        Index("epochDay"),
        Index("shiftTemplateId"),
        Index("scheduleId"),
        Index("status"),
        Index(value = ["employeeId", "epochDay", "plannedStartEpochMillis"]),
    ],
)
data class PlannedShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val employeeName: String,
    val role: String,
    val epochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val shiftTemplateId: Long? = null,
    val scheduleId: Long? = null,
    @ColumnInfo(defaultValue = "0") val plannedStartEpochMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val plannedEndEpochMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val breakMinutes: Int = 0,
    @ColumnInfo(defaultValue = "'PUBLISHED'") val status: String = "PUBLISHED",
    @ColumnInfo(defaultValue = "'LEGACY'") val source: String = "LEGACY",
    @ColumnInfo(defaultValue = "''") val overrideReason: String = "",
    @ColumnInfo(defaultValue = "'MIGRATION'") val createdBy: String = "MIGRATION",
    @ColumnInfo(defaultValue = "'MIGRATION'") val updatedBy: String = "MIGRATION",
    @ColumnInfo(defaultValue = "''") val auditRef: String = "",
)

@Entity(
    tableName = "purchase_order_follow_ups",
    foreignKeys = [ForeignKey(entity = PurchaseOrderEntity::class, parentColumns = ["id"], childColumns = ["purchaseOrderId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("purchaseOrderId"), Index("createdAtEpochMillis")],
)
data class PurchaseOrderFollowUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseOrderId: Long,
    val note: String,
    val actor: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "storage_locations",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["name"], unique = true),
        Index("kind"),
        Index("isActive"),
        Index("branchName"),
        Index("branchId"),
    ],
)
data class StorageLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val code: String = InventoryLocationCode.generated().value,
    val name: String,
    @ColumnInfo(defaultValue = "''") val branchName: String = "",
    val branchId: Long? = null,
    val kind: String,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Entity(
    tableName = "operating_budgets",
    indices = [Index(value = ["name", "fromEpochDay", "toEpochDay"], unique = true), Index("category"), Index("costCenter")],
)
data class OperatingBudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val costCenter: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val limitRial: Long,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "budget_spend_entries",
    foreignKeys = [ForeignKey(entity = OperatingBudgetEntity::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("budgetId"), Index("spendEpochDay")],
)
data class BudgetSpendEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetId: Long,
    val amountRial: Long,
    val spendEpochDay: Long,
    val reference: String,
    val actor: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "budget_commitments",
    foreignKeys = [ForeignKey(entity = OperatingBudgetEntity::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("budgetId"), Index(value = ["referenceType", "referenceId"], unique = true), Index("status")],
)
data class BudgetCommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val budgetId: Long,
    val referenceType: String,
    val referenceId: Long,
    val amountRial: Long,
    val status: String = "COMMITTED",
    val actor: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "accounting_period_locks", indices = [Index(value = ["fromEpochDay", "toEpochDay"], unique = true), Index("status")])
data class AccountingPeriodLockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val status: String = "CLOSED",
    val reason: String,
    val closedBy: String,
    val closedAtEpochMillis: Long,
    val reopenedBy: String? = null,
    val reopenedAtEpochMillis: Long? = null,
)

@Entity(tableName = "sales_cash_reconciliations", indices = [Index(value = ["businessEpochDay", "revisionNo"], unique = true), Index("status"), Index("branchId")])
data class SalesCashReconciliationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessEpochDay: Long,
    val branchId: Long? = null,
    val revisionNo: Int,
    val expectedCashRial: Long,
    val expectedCardRial: Long,
    val expectedTransferRial: Long,
    val actualCashRial: Long,
    val actualCardRial: Long,
    val actualTransferRial: Long,
    val status: String,
    val note: String,
    val reconciledBy: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "labor_policy")
data class LaborPolicyEntity(
    @PrimaryKey val singletonId: Int = 1,
    val maxWeeklyMinutes: Int,
    val maxShiftMinutes: Int,
    val minimumRestMinutes: Int,
    val breakRequiredAfterMinutes: Int,
    val minimumBreakMinutes: Int,
    val updatedBy: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "work_breaks",
    foreignKeys = [ForeignKey(entity = PlannedShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("shiftId")],
)
data class WorkBreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val startMinute: Int,
    val endMinute: Int,
    val recordedBy: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "employee_availability",
    foreignKeys = [ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["employeeId", "dayOfWeek"], unique = true), Index("dayOfWeek")],
)
data class EmployeeAvailabilityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val dayOfWeek: Int,
    val fromMinute: Int,
    val toMinute: Int,
    val isAvailable: Boolean,
    val updatedBy: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "shift_swap_requests",
    foreignKeys = [
        ForeignKey(entity = PlannedShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["requesterEmployeeId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("shiftId"), Index("requesterEmployeeId"), Index("targetEmployeeId"), Index("status")],
)
data class ShiftSwapRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val requesterEmployeeId: Long,
    val targetEmployeeId: Long?,
    val status: String,
    val note: String,
    val reviewedBy: String?,
    val createdAtEpochMillis: Long,
    val reviewedAtEpochMillis: Long?,
)

data class BudgetWithSpendRow(
    val id: Long,
    val name: String,
    val category: String,
    val costCenter: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val limitRial: Long,
    val manualSpendRial: Long,
    val automaticSpendRial: Long,
    val committedRial: Long,
)

data class KpiTraceRow(val id: Long, val entryNo: String, val entryEpochDay: Long, val description: String, val sourceType: String, val sourceId: Long, val debitRial: Long, val creditRial: Long)

data class FoodCostRow(
    val salesRial: Long,
    val theoreticalCostRial: Long,
    val standardSalesLedgerCostRial: Long,
    val wasteCostRial: Long,
    val negativeAdjustmentCostRial: Long,
    val positiveAdjustmentCostRial: Long,
    val actualEvidenceCount: Long,
)
data class ShiftBreakRow(val shiftId: Long, val employeeId: Long, val employeeName: String, val epochDay: Long, val startMinute: Int, val endMinute: Int, val breakMinutes: Int)
data class EmployeeAvailabilityRow(val id: Long, val employeeId: Long, val employeeName: String, val dayOfWeek: Int, val fromMinute: Int, val toMinute: Int, val isAvailable: Boolean)
data class ShiftSwapRow(val id: Long, val shiftId: Long, val requesterEmployeeId: Long, val requesterName: String, val targetEmployeeId: Long?, val targetName: String?, val status: String, val note: String)
