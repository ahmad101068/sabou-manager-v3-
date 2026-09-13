package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_sales_settlements",
    foreignKeys = [
        ForeignKey(entity = DailySalesSummaryEntity::class, parentColumns = ["id"], childColumns = ["dailySalesId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["partyId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("dailySalesId"), Index("type"), Index("partyId"), Index("dueEpochDay"), Index(value=["globalId"], unique=true)],
)
data class DailySalesSettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val dailySalesId: Long,
    val type: String,
    val amountRial: Long,
    val cashboxId: Long? = null,
    val bankAccountId: Long? = null,
    val cardTerminalId: Long? = null,
    val partyId: Long? = null,
    val dueEpochDay: Long? = null,
    val contractId: Long? = null,
    val referenceNumber: String? = null,
    val note: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "receivables", foreignKeys=[ForeignKey(entity=CustomerEntity::class,parentColumns=["id"],childColumns=["partyId"],onDelete=ForeignKey.RESTRICT)], indices = [Index("branchId"), Index("partyId"), Index("dueEpochDay"), Index("status"), Index(value=["sourceType","sourceId"]), Index(value=["globalId"], unique=true)])
data class ReceivableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val branchId: Long,
    val partyId: Long,
    val type: String,
    val sourceType: String,
    val sourceId: Long,
    val originalAmountRial: Long,
    val paidAmountRial: Long = 0,
    val outstandingAmountRial: Long,
    val issueEpochDay: Long,
    val dueEpochDay: Long? = null,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "receivable_collections",
    foreignKeys = [ForeignKey(entity = ReceivableEntity::class, parentColumns=["id"], childColumns=["receivableId"], onDelete=ForeignKey.RESTRICT)],
    indices = [Index("receivableId"), Index("businessEpochDay"), Index(value=["globalId"], unique=true)],
)
data class ReceivableCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val receivableId: Long,
    val amountRial: Long,
    val method: String,
    val cashboxId: Long? = null,
    val bankAccountId: Long? = null,
    val reference: String? = null,
    val businessEpochDay: Long,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long,
    val reversedAtEpochMillis: Long? = null,
    val reversalReason: String? = null,
    val reversalJournalEntryId: Long? = null,
)

@Entity(tableName="management_issues", indices=[Index("branchId"),Index("type"),Index("status"),Index("assignedEmployeeId"),Index(value=["globalId"], unique=true),Index(value=["deduplicationKey"], unique=true)])
data class ManagementIssueEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val globalId: String,
    val branchId: Long,
    val type: String,
    val severity: String,
    val title: String,
    val description: String,
    val financialImpactRial: Long? = null,
    val businessEpochDay: Long,
    val detectedAtEpochMillis: Long,
    val sourceType: String,
    val sourceId: Long,
    val deduplicationKey: String,
    val status: String,
    val assignedUserId: Long? = null,
    val assignedEmployeeId: Long? = null,
    val dueAtEpochMillis: Long? = null,
    val resolutionNote: String? = null,
    val resolvedByUserId: Long? = null,
    val resolvedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName="management_tasks", foreignKeys=[ForeignKey(entity=ManagementIssueEntity::class,parentColumns=["id"],childColumns=["sourceIssueId"],onDelete=ForeignKey.SET_NULL)], indices=[Index("branchId"),Index("status"),Index("assignedEmployeeId"),Index("sourceIssueId"),Index(value=["globalId"], unique=true)])
data class ManagementTaskEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val globalId: String,
    val branchId: Long,
    val title: String,
    val description: String,
    val priority: String,
    val status: String,
    val assignedUserId: Long? = null,
    val assignedEmployeeId: Long? = null,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long,
    val dueAtEpochMillis: Long? = null,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val completedByUserId: Long? = null,
    val requiresApproval: Boolean = false,
    val approvedByUserId: Long? = null,
    val approvedAtEpochMillis: Long? = null,
    val requiresAttachment: Boolean = false,
    val sourceIssueId: Long? = null,
    val sourceType: String? = null,
    val sourceId: Long? = null,
    val note: String? = null,
)

@Entity(tableName="task_attachments", foreignKeys=[ForeignKey(entity=ManagementTaskEntity::class,parentColumns=["id"],childColumns=["taskId"],onDelete=ForeignKey.CASCADE)], indices=[Index("taskId")])
data class TaskAttachmentEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val taskId: Long,
    val storageReference: String,
    val mimeType: String? = null,
    val originalName: String? = null,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(tableName="checklist_templates", indices=[Index("branchId"),Index("type"),Index("active")])
data class ChecklistTemplateEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val branchId: Long? = null,
    val name: String,
    val type: String,
    val active: Boolean = true,
    val createdByUserId: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName="checklist_template_items", foreignKeys=[ForeignKey(entity=ChecklistTemplateEntity::class,parentColumns=["id"],childColumns=["templateId"],onDelete=ForeignKey.CASCADE)], indices=[Index("templateId")])
data class ChecklistTemplateItemEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val templateId: Long,
    val title: String,
    val description: String? = null,
    val sortOrder: Int,
    val required: Boolean,
    val requiresPhoto: Boolean,
    val requiresNoteOnFailure: Boolean,
)

@Entity(tableName="checklist_runs", foreignKeys=[ForeignKey(entity=ChecklistTemplateEntity::class,parentColumns=["id"],childColumns=["templateId"],onDelete=ForeignKey.RESTRICT)], indices=[Index("templateId"),Index("branchId"),Index("businessEpochDay"),Index("assignedEmployeeId"),Index("status")])
data class ChecklistRunEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val templateId: Long,
    val branchId: Long,
    val businessEpochDay: Long,
    val assignedEmployeeId: Long? = null,
    val status: String,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val completedByUserId: Long? = null,
    val approvedByUserId: Long? = null,
    val approvedAtEpochMillis: Long? = null,
)

@Entity(tableName="checklist_run_items", foreignKeys=[
    ForeignKey(entity=ChecklistRunEntity::class,parentColumns=["id"],childColumns=["runId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ChecklistTemplateItemEntity::class,parentColumns=["id"],childColumns=["templateItemId"],onDelete=ForeignKey.RESTRICT),
], indices=[Index("runId"),Index("templateItemId")])
data class ChecklistRunItemEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val runId: Long,
    val templateItemId: Long,
    val status: String,
    val note: String? = null,
    val attachmentReference: String? = null,
    val completedByUserId: Long? = null,
    val completedAtEpochMillis: Long? = null,
)

@Entity(tableName="management_rule_thresholds", indices=[Index(value=["branchScopeId","key"], unique=true)])
data class ManagementRuleThresholdEntity(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    /** 0 = GLOBAL, positive values are real branch ids. */
    val branchScopeId: Long = 0L,
    val key: String,
    val valueBasisPoints: Int? = null,
    val valueRial: Long? = null,
    val updatedByUserId: Long,
    val updatedAtEpochMillis: Long,
)
