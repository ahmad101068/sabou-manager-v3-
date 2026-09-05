package ir.restaurant.management.domain.control

import kotlinx.coroutines.flow.Flow

data class ManagementIssueRecord(
    val id: Long,
    val branchId: Long,
    val type: ManagementIssueType,
    val severity: ManagementIssueSeverity,
    val title: String,
    val description: String,
    val financialImpactRial: Long?,
    val businessEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val status: ManagementIssueStatus,
    val assignedUserId: Long?,
    val assignedEmployeeId: Long?,
    val dueAtEpochMillis: Long?,
)

data class ManagementTaskRecord(
    val id: Long,
    val branchId: Long,
    val title: String,
    val description: String,
    val priority: ManagementTaskPriority,
    val status: ManagementTaskStatus,
    val assignedUserId: Long?,
    val assignedEmployeeId: Long?,
    val dueAtEpochMillis: Long?,
    val requiresApproval: Boolean,
    val requiresAttachment: Boolean,
    val sourceIssueId: Long?,
)

data class ChecklistTemplateRecord(
    val id: Long,
    val branchId: Long?,
    val name: String,
    val type: ChecklistType,
    val active: Boolean,
)

data class ChecklistRunRecord(
    val id: Long,
    val templateId: Long,
    val branchId: Long,
    val businessEpochDay: Long,
    val assignedEmployeeId: Long?,
    val status: ChecklistStatus,
)

data class ChecklistRunItemRecord(
    val id: Long,
    val runId: Long,
    val templateItemId: Long,
    val title: String,
    val description: String?,
    val required: Boolean,
    val requiresPhoto: Boolean,
    val requiresNoteOnFailure: Boolean,
    val status: String,
    val note: String?,
    val attachmentReference: String?,
)

interface ManagementWorkflowReadService {
    fun observeOpenIssues(branchId: Long): Flow<List<ManagementIssueRecord>>
    fun observeOpenTasks(branchId: Long): Flow<List<ManagementTaskRecord>>
    fun observeChecklistTemplates(branchId: Long): Flow<List<ChecklistTemplateRecord>>
    fun observeChecklistRuns(branchId: Long): Flow<List<ChecklistRunRecord>>
    suspend fun checklistRunItems(runId: Long): List<ChecklistRunItemRecord>
}
