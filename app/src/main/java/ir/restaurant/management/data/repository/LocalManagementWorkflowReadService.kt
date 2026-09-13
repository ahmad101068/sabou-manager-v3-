package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.control.ChecklistRunItemRecord
import ir.restaurant.management.domain.control.ChecklistRunRecord
import ir.restaurant.management.domain.control.ChecklistStatus
import ir.restaurant.management.domain.control.ChecklistTemplateRecord
import ir.restaurant.management.domain.control.ChecklistType
import ir.restaurant.management.domain.control.ManagementIssueRecord
import ir.restaurant.management.domain.control.ManagementIssueSeverity
import ir.restaurant.management.domain.control.ManagementIssueStatus
import ir.restaurant.management.domain.control.ManagementIssueType
import ir.restaurant.management.domain.control.ManagementTaskPriority
import ir.restaurant.management.domain.control.ManagementTaskRecord
import ir.restaurant.management.domain.control.ManagementTaskStatus
import ir.restaurant.management.domain.control.ManagementWorkflowReadService
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class LocalManagementWorkflowReadService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
) : ManagementWorkflowReadService {
    private val dataScope = LocalDataScopeService(database, authorizer)
    override fun observeOpenIssues(branchId: Long): Flow<List<ManagementIssueRecord>> = authorizedBranch(Permission.CONTROL_VIEW, branchId) {
        database.businessOperationsDao().observeOpenIssues(branchId).map { rows ->
            rows.map { row ->
                ManagementIssueRecord(
                    id = row.id,
                    branchId = row.branchId,
                    type = ManagementIssueType.valueOf(row.type),
                    severity = ManagementIssueSeverity.valueOf(row.severity),
                    title = row.title,
                    description = row.description,
                    financialImpactRial = row.financialImpactRial,
                    businessEpochDay = row.businessEpochDay,
                    sourceType = row.sourceType,
                    sourceId = row.sourceId,
                    status = ManagementIssueStatus.valueOf(row.status),
                    assignedUserId = row.assignedUserId,
                    assignedEmployeeId = row.assignedEmployeeId,
                    dueAtEpochMillis = row.dueAtEpochMillis,
                )
            }
        }
    }

    override fun observeOpenTasks(branchId: Long): Flow<List<ManagementTaskRecord>> = authorizedBranch(Permission.TASK_VIEW, branchId) {
        database.businessOperationsDao().observeOpenTasks(branchId).map { rows ->
            rows.map { row ->
                ManagementTaskRecord(
                    id = row.id,
                    branchId = row.branchId,
                    title = row.title,
                    description = row.description,
                    priority = ManagementTaskPriority.valueOf(row.priority),
                    status = ManagementTaskStatus.valueOf(row.status),
                    assignedUserId = row.assignedUserId,
                    assignedEmployeeId = row.assignedEmployeeId,
                    dueAtEpochMillis = row.dueAtEpochMillis,
                    requiresApproval = row.requiresApproval,
                    requiresAttachment = row.requiresAttachment,
                    sourceIssueId = row.sourceIssueId,
                )
            }
        }
    }

    override fun observeChecklistTemplates(branchId: Long): Flow<List<ChecklistTemplateRecord>> = authorizedBranch(Permission.CHECKLIST_VIEW, branchId) {
        database.businessOperationsDao().observeChecklistTemplates(branchId).map { rows ->
            rows.map { row -> ChecklistTemplateRecord(row.id, row.branchId, row.name, ChecklistType.valueOf(row.type), row.active) }
        }
    }

    override fun observeChecklistRuns(branchId: Long): Flow<List<ChecklistRunRecord>> = authorizedBranch(Permission.CHECKLIST_VIEW, branchId) {
        database.businessOperationsDao().observeChecklistRuns(branchId).map { rows ->
            rows.map { row -> ChecklistRunRecord(row.id, row.templateId, row.branchId, row.businessEpochDay, row.assignedEmployeeId, ChecklistStatus.valueOf(row.status)) }
        }
    }

    override suspend fun checklistRunItems(runId: Long): List<ChecklistRunItemRecord> {
        authorizer.require(Permission.CHECKLIST_VIEW)
        val dao = database.businessOperationsDao()
        val run = dao.checklistRun(runId) ?: error("اجرای چک‌لیست پیدا نشد.")
        dataScope.requireBranch(run.branchId, operational = false)
        return dao.checklistRunItems(runId).map { row ->
            val template = requireNotNull(dao.checklistTemplateItem(row.templateItemId))
            ChecklistRunItemRecord(
                id = row.id,
                runId = row.runId,
                templateItemId = row.templateItemId,
                title = template.title,
                description = template.description,
                required = template.required,
                requiresPhoto = template.requiresPhoto,
                requiresNoteOnFailure = template.requiresNoteOnFailure,
                status = row.status,
                note = row.note,
                attachmentReference = row.attachmentReference,
            )
        }
    }

    private fun <T> authorized(permission: Permission, source: () -> Flow<T>): Flow<T> = flow {
        authorizer.require(permission)
        emitAll(source())
    }

    private fun <T> authorizedBranch(permission: Permission, branchId: Long, source: () -> Flow<T>): Flow<T> = flow {
        authorizer.require(permission)
        dataScope.requireBranch(branchId, operational = false)
        emitAll(source())
    }
}
