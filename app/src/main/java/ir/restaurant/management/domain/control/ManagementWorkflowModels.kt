package ir.restaurant.management.domain.control

enum class ManagementIssueSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
enum class ManagementIssueStatus { NEW, ACKNOWLEDGED, ASSIGNED, IN_PROGRESS, WAITING_APPROVAL, RESOLVED, DISMISSED }
enum class ManagementIssueType { LOW_STOCK, INVENTORY_DISCREPANCY, ABNORMAL_INVENTORY_USAGE, WASTE_SPIKE, FOOD_COST_VARIANCE, PURCHASE_PRICE_SPIKE, PURCHASE_DELIVERY_OVERDUE, CASH_VARIANCE, CARD_SETTLEMENT_VARIANCE, OVERDUE_RECEIVABLE, UNALLOCATED_RECEIVABLE, CREDIT_LIMIT_EXCEEDED, BUDGET_OVERRUN, ATTENDANCE_ANOMALY, TASK_OVERDUE, CHECKLIST_FAILED }
enum class ManagementTaskPriority { LOW, NORMAL, HIGH, CRITICAL }
enum class ManagementTaskStatus { TODO, IN_PROGRESS, WAITING_APPROVAL, COMPLETED, REJECTED, CANCELLED }
enum class ChecklistType { OPENING, CLOSING, SHIFT, SAFETY, INVENTORY, CUSTOM }
enum class ChecklistStatus { NOT_STARTED, IN_PROGRESS, WAITING_APPROVAL, COMPLETED, FAILED }

object ManagementIssueLifecycle {
    private val allowed = mapOf(
        ManagementIssueStatus.NEW to setOf(ManagementIssueStatus.ACKNOWLEDGED, ManagementIssueStatus.ASSIGNED, ManagementIssueStatus.DISMISSED),
        ManagementIssueStatus.ACKNOWLEDGED to setOf(ManagementIssueStatus.ASSIGNED, ManagementIssueStatus.IN_PROGRESS, ManagementIssueStatus.DISMISSED),
        ManagementIssueStatus.ASSIGNED to setOf(ManagementIssueStatus.IN_PROGRESS, ManagementIssueStatus.DISMISSED),
        ManagementIssueStatus.IN_PROGRESS to setOf(ManagementIssueStatus.WAITING_APPROVAL, ManagementIssueStatus.RESOLVED, ManagementIssueStatus.DISMISSED),
        ManagementIssueStatus.WAITING_APPROVAL to setOf(ManagementIssueStatus.RESOLVED, ManagementIssueStatus.IN_PROGRESS, ManagementIssueStatus.DISMISSED),
        ManagementIssueStatus.RESOLVED to emptySet(),
        ManagementIssueStatus.DISMISSED to emptySet(),
    )
    fun requireTransition(from: ManagementIssueStatus, to: ManagementIssueStatus) {
        require(to in allowed.getValue(from)) { "گذار وضعیت مسئله مدیریتی مجاز نیست: $from → $to" }
    }
}

data class DetectedIssue(
    val branchId: Long,
    val type: ManagementIssueType,
    val severity: ManagementIssueSeverity,
    val title: String,
    val description: String,
    val financialImpactRial: Long? = null,
    val businessEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val businessPeriodKey: String,
) {
    val deduplicationKey: String get() = "$branchId|$type|$sourceType|$sourceId|$businessPeriodKey"
}

data class ManagementRuleContext(val branchId: Long, val fromEpochDay: Long, val toEpochDay: Long)
interface ManagementRule { suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> }

data class ManagementIssueAssignmentDraft(val issueId: Long, val assignedUserId: Long? = null, val assignedEmployeeId: Long? = null, val dueAtEpochMillis: Long? = null)
data class ManagementTaskDraft(
    val branchId: Long,
    val title: String,
    val description: String,
    val priority: ManagementTaskPriority,
    val assignedUserId: Long? = null,
    val assignedEmployeeId: Long? = null,
    val dueAtEpochMillis: Long? = null,
    val requiresApproval: Boolean = false,
    val requiresAttachment: Boolean = false,
    val sourceIssueId: Long? = null,
    val sourceType: String? = null,
    val sourceId: Long? = null,
    val note: String? = null,
)

data class ChecklistTemplateDraft(
    val branchId: Long?,
    val name: String,
    val type: ChecklistType,
    val items: List<ChecklistTemplateItemDraft>,
)
data class ChecklistTemplateItemDraft(val title: String, val description: String? = null, val required: Boolean = true, val requiresPhoto: Boolean = false, val requiresNoteOnFailure: Boolean = true)

interface ManagementWorkflowService {
    suspend fun recordDetectedIssues(issues: List<DetectedIssue>): Int
    suspend fun acknowledgeIssue(issueId: Long)
    suspend fun assignIssue(draft: ManagementIssueAssignmentDraft)
    suspend fun startIssue(issueId: Long)
    suspend fun resolveIssue(issueId: Long, resolutionNote: String)
    suspend fun dismissIssue(issueId: Long, resolutionNote: String)
    suspend fun createTask(draft: ManagementTaskDraft): Long
    suspend fun assignTask(taskId: Long, assignedUserId: Long? = null, assignedEmployeeId: Long? = null, dueAtEpochMillis: Long? = null)
    suspend fun startTask(taskId: Long)
    suspend fun completeTask(taskId: Long)
    suspend fun approveTask(taskId: Long)
    suspend fun rejectTask(taskId: Long)
    suspend fun cancelTask(taskId: Long)
    suspend fun attachToTask(taskId: Long, storageReference: String, mimeType: String? = null, originalName: String? = null): Long
    suspend fun createChecklistTemplate(draft: ChecklistTemplateDraft): Long
    suspend fun startChecklistRun(templateId: Long, branchId: Long, businessEpochDay: Long, assignedEmployeeId: Long? = null): Long
    suspend fun completeChecklistItem(runItemId: Long, passed: Boolean, note: String? = null, attachmentReference: String? = null)
    suspend fun completeChecklistRun(runId: Long)
    suspend fun approveChecklistRun(runId: Long)
}
