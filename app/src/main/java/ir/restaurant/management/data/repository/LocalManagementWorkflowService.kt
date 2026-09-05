package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.ChecklistRunEntity
import ir.restaurant.management.data.db.ChecklistRunItemEntity
import ir.restaurant.management.data.db.ChecklistTemplateEntity
import ir.restaurant.management.data.db.ChecklistTemplateItemEntity
import ir.restaurant.management.data.db.ManagementIssueEntity
import ir.restaurant.management.data.db.ManagementTaskEntity
import ir.restaurant.management.data.db.TaskAttachmentEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.control.ChecklistStatus
import ir.restaurant.management.domain.control.ChecklistTemplateDraft
import ir.restaurant.management.domain.control.DetectedIssue
import ir.restaurant.management.domain.control.ManagementIssueAssignmentDraft
import ir.restaurant.management.domain.control.ManagementIssueLifecycle
import ir.restaurant.management.domain.control.ManagementIssueStatus
import ir.restaurant.management.domain.control.ManagementTaskDraft
import ir.restaurant.management.domain.control.ManagementTaskStatus
import ir.restaurant.management.domain.control.ManagementWorkflowService
import ir.restaurant.management.domain.security.Permission

class LocalManagementWorkflowService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
) : ManagementWorkflowService {
    private val audit = LocalAuditEventWriter(database)
    private val branchResolver = CanonicalBranchResolver(database)
    private val dataScope = LocalDataScopeService(database, authorizer)

    override suspend fun recordDetectedIssues(issues: List<DetectedIssue>): Int {
        authorizer.require(Permission.CONTROL_VIEW)
        val now=clock(); var inserted=0
        database.withTransaction {
            issues.forEach { issue ->
                require(issue.branchId>=0 && issue.businessEpochDay>0 && issue.sourceId>0) { "منبع مسئله مدیریتی معتبر نیست." }
                if (issue.branchId > 0) {
                    dataScope.requireBranch(issue.branchId, operational = false)
                } else {
                    require(issue.sourceType.startsWith("UNSCOPED_")) { "مسئله مدیریتی شعبه‌ای باید Branch canonical معتبر داشته باشد." }
                }
                val id=database.businessOperationsDao().insertIssue(
                    ManagementIssueEntity(
                        globalId=GlobalId.new().value, branchId=issue.branchId, type=issue.type.name, severity=issue.severity.name,
                        title=issue.title.trim(), description=issue.description.trim(), financialImpactRial=issue.financialImpactRial,
                        businessEpochDay=issue.businessEpochDay, detectedAtEpochMillis=now, sourceType=issue.sourceType, sourceId=issue.sourceId,
                        deduplicationKey=issue.deduplicationKey, status=ManagementIssueStatus.NEW.name, createdAtEpochMillis=now, updatedAtEpochMillis=now,
                    ),
                )
                if(id>0) { inserted++; audit.appendAuthorized(authorizer,"CREATE","MANAGEMENT_ISSUE",id,issue.title,now,issue.businessEpochDay,correlationId="issue:${issue.deduplicationKey}") }
            }
        }
        return inserted
    }

    override suspend fun acknowledgeIssue(issueId: Long) {
        authorizer.require(Permission.CONTROL_VIEW); val now=clock()
        database.withTransaction {
            val current=database.businessOperationsDao().issue(issueId) ?: error("مسئله پیدا نشد.")
            dataScope.requireBranch(current.branchId, operational = false)
            ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.valueOf(current.status),ManagementIssueStatus.ACKNOWLEDGED)
            check(database.businessOperationsDao().updateIssueStatus(issueId,ManagementIssueStatus.ACKNOWLEDGED.name,now)==1)
            audit.appendAuthorized(authorizer,"ACKNOWLEDGE","MANAGEMENT_ISSUE",issueId,"تأیید مشاهده مسئله مدیریتی",now,current.businessEpochDay,correlationId="issue_ack:$issueId:$now")
        }
    }

    override suspend fun assignIssue(draft: ManagementIssueAssignmentDraft) {
        authorizer.require(Permission.CONTROL_ASSIGN)
        require(draft.assignedUserId!=null || draft.assignedEmployeeId!=null) { "مسئله باید به کاربر یا پرسنل ارجاع شود." }
        val now=clock()
        database.withTransaction {
            val current=database.businessOperationsDao().issue(draft.issueId) ?: error("مسئله پیدا نشد.")
            dataScope.requireBranch(current.branchId, operational = false)
            val from=ManagementIssueStatus.valueOf(current.status)
            ManagementIssueLifecycle.requireTransition(from,ManagementIssueStatus.ASSIGNED)
            check(database.businessOperationsDao().updateIssueAssignment(current.id,"ASSIGNED",draft.assignedUserId,draft.assignedEmployeeId,draft.dueAtEpochMillis,now)==1)
            audit.appendAuthorized(authorizer,"ASSIGN","MANAGEMENT_ISSUE",current.id,"ارجاع مسئله مدیریتی",now,current.businessEpochDay,correlationId="issue_assign:${current.id}:$now")
        }
    }

    override suspend fun startIssue(issueId: Long) {
        authorizer.require(Permission.CONTROL_ASSIGN); val now=clock()
        database.withTransaction {
            val current=database.businessOperationsDao().issue(issueId) ?: error("مسئله پیدا نشد.")
            dataScope.requireBranch(current.branchId, operational = false)
            ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.valueOf(current.status),ManagementIssueStatus.IN_PROGRESS)
            check(database.businessOperationsDao().updateIssueStatus(issueId,ManagementIssueStatus.IN_PROGRESS.name,now)==1)
            audit.appendAuthorized(authorizer,"START","MANAGEMENT_ISSUE",issueId,"شروع پیگیری مسئله",now,current.businessEpochDay,correlationId="issue_start:$issueId:$now")
        }
    }

    override suspend fun resolveIssue(issueId: Long, resolutionNote: String) {
        val actor=authorizer.require(Permission.CONTROL_RESOLVE); val now=clock(); val note=resolutionNote.trim(); require(note.length>=3)
        database.withTransaction {
            val current=database.businessOperationsDao().issue(issueId) ?: error("مسئله پیدا نشد.")
            dataScope.requireBranch(current.branchId, operational = false)
            ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.valueOf(current.status),ManagementIssueStatus.RESOLVED)
            check(database.businessOperationsDao().resolveIssue(issueId,"RESOLVED",note,actor.id,now)==1)
            audit.appendAuthorized(authorizer,"RESOLVE","MANAGEMENT_ISSUE",issueId,note,now,current.businessEpochDay,correlationId="issue_resolve:$issueId:$now")
        }
    }

    override suspend fun dismissIssue(issueId: Long, resolutionNote: String) {
        val actor=authorizer.require(Permission.CONTROL_RESOLVE); val now=clock(); val note=resolutionNote.trim(); require(note.length>=3)
        database.withTransaction {
            val current=database.businessOperationsDao().issue(issueId) ?: error("مسئله پیدا نشد.")
            dataScope.requireBranch(current.branchId, operational = false)
            ManagementIssueLifecycle.requireTransition(ManagementIssueStatus.valueOf(current.status),ManagementIssueStatus.DISMISSED)
            check(database.businessOperationsDao().resolveIssue(issueId,ManagementIssueStatus.DISMISSED.name,note,actor.id,now)==1)
            audit.appendAuthorized(authorizer,"DISMISS","MANAGEMENT_ISSUE",issueId,note,now,current.businessEpochDay,correlationId="issue_dismiss:$issueId:$now")
        }
    }

    override suspend fun createTask(draft: ManagementTaskDraft): Long {
        val actor=authorizer.require(Permission.TASK_CREATE); val now=clock()
        require(draft.branchId>0 && draft.title.trim().length>=3)
        return database.withTransaction {
            dataScope.requireBranch(draft.branchId)
            val id=database.businessOperationsDao().insertTask(ManagementTaskEntity(
                globalId=GlobalId.new().value, branchId=draft.branchId, title=draft.title.trim(), description=draft.description.trim(),
                priority=draft.priority.name, status=ManagementTaskStatus.TODO.name, assignedUserId=draft.assignedUserId, assignedEmployeeId=draft.assignedEmployeeId,
                createdByUserId=actor.id, createdAtEpochMillis=now, dueAtEpochMillis=draft.dueAtEpochMillis, requiresApproval=draft.requiresApproval,
                requiresAttachment=draft.requiresAttachment, sourceIssueId=draft.sourceIssueId, sourceType=draft.sourceType, sourceId=draft.sourceId, note=draft.note?.trim()))
            audit.appendAuthorized(authorizer,"CREATE","MANAGEMENT_TASK",id,draft.title,now,correlationId="task:$id:create")
            id
        }
    }

    override suspend fun assignTask(taskId: Long, assignedUserId: Long?, assignedEmployeeId: Long?, dueAtEpochMillis: Long?) {
        authorizer.require(Permission.TASK_ASSIGN); require(assignedUserId!=null || assignedEmployeeId!=null) { "وظیفه باید به کاربر یا پرسنل ارجاع شود." }; val now=clock()
        database.withTransaction {
            val task=database.businessOperationsDao().task(taskId) ?: error("وظیفه پیدا نشد.")
            dataScope.requireBranch(task.branchId, operational = false)
            require(task.status !in setOf("COMPLETED","REJECTED","CANCELLED")) { "وظیفه نهایی‌شده قابل ارجاع نیست." }
            check(database.businessOperationsDao().updateTaskAssignment(taskId,assignedUserId,assignedEmployeeId,dueAtEpochMillis)==1)
            audit.appendAuthorized(authorizer,"ASSIGN","MANAGEMENT_TASK",taskId,"ارجاع وظیفه مدیریتی",now,correlationId="task:$taskId:assign:$now")
        }
    }

    override suspend fun startTask(taskId: Long) { transitionTask(taskId,Permission.TASK_COMPLETE,"IN_PROGRESS") }
    override suspend fun completeTask(taskId: Long) { transitionTask(taskId,Permission.TASK_COMPLETE,"COMPLETED") }
    override suspend fun approveTask(taskId: Long) { transitionTask(taskId,Permission.TASK_APPROVE,"COMPLETED",approve=true) }
    override suspend fun rejectTask(taskId: Long) { transitionTask(taskId,Permission.TASK_APPROVE,"REJECTED") }
    override suspend fun cancelTask(taskId: Long) { transitionTask(taskId,Permission.TASK_ASSIGN,"CANCELLED") }

    private suspend fun transitionTask(taskId:Long, permission:Permission, target:String, approve:Boolean=false) {
        val actor=authorizer.require(permission); val now=clock()
        database.withTransaction {
            val task=database.businessOperationsDao().task(taskId) ?: error("وظیفه پیدا نشد.")
            dataScope.requireBranch(task.branchId, operational = false)
            if(target in setOf("IN_PROGRESS","COMPLETED") && !approve && task.assignedUserId != null) {
                require(task.assignedUserId == actor.id) { "فقط کاربر ارجاع‌شده می‌تواند وظیفه را اجرا یا تکمیل کند." }
            }
            if(target in setOf("COMPLETED","REJECTED") && approve) {
                require(task.completedByUserId == null || task.completedByUserId != actor.id) { "ثبت‌کننده تکمیل نمی‌تواند همان وظیفه را تأیید کند." }
            }
            if(target=="REJECTED") {
                require(task.status=="WAITING_APPROVAL") { "فقط وظیفه منتظر تأیید قابل رد است." }
                require(task.completedByUserId == null || task.completedByUserId != actor.id) { "تکمیل‌کننده وظیفه نمی‌تواند همان وظیفه را رد کند." }
            }
            if(target=="IN_PROGRESS") require(task.status=="TODO") { "وظیفه قابل شروع نیست." }
            if(target=="CANCELLED") require(task.status in setOf("TODO","IN_PROGRESS","WAITING_APPROVAL")) { "وظیفه قابل لغو نیست." }
            if(target=="COMPLETED") {
                if(approve) require(task.status=="WAITING_APPROVAL") { "فقط وظیفه منتظر تأیید قابل تأیید است." }
                else require(task.status=="IN_PROGRESS") { "وظیفه باید قبل از تکمیل شروع شده باشد." }
                if(task.requiresAttachment) require(database.businessOperationsDao().attachmentCount(taskId)>0) { "این وظیفه نیازمند پیوست است." }
                if(task.requiresApproval && !approve) {
                    check(database.businessOperationsDao().updateTaskLifecycle(taskId,"WAITING_APPROVAL",task.startedAtEpochMillis ?: now,null,actor.id,null,null)==1)
                    audit.appendAuthorized(authorizer,"WAITING_APPROVAL","MANAGEMENT_TASK",taskId,"وظیفه برای تأیید ارسال شد",now,correlationId="task:$taskId:waiting_approval:$now")
                    return@withTransaction
                }
            }
            val completedBy = if(target=="COMPLETED" && !approve) actor.id else null
            check(database.businessOperationsDao().updateTaskLifecycle(
                taskId,target,task.startedAtEpochMillis ?: now,
                if(target in setOf("COMPLETED","REJECTED","CANCELLED")) now else null,
                completedBy,if(approve) actor.id else null,if(approve) now else null,
            )==1)
            audit.appendAuthorized(authorizer,if(approve) "APPROVE" else target,"MANAGEMENT_TASK",taskId,"تغییر وضعیت وظیفه به $target",now,correlationId="task:$taskId:$target:$now")
        }
    }

    override suspend fun attachToTask(taskId: Long, storageReference: String, mimeType: String?, originalName: String?): Long {
        val actor=authorizer.require(Permission.TASK_COMPLETE); require(storageReference.isNotBlank()); val now=clock()
        val task=database.businessOperationsDao().task(taskId) ?: error("وظیفه پیدا نشد.")
        dataScope.requireBranch(task.branchId, operational = false)
        if(task.assignedUserId != null) require(task.assignedUserId == actor.id) { "فقط کاربر ارجاع‌شده می‌تواند پیوست وظیفه را ثبت کند." }
        return database.businessOperationsDao().insertTaskAttachment(TaskAttachmentEntity(taskId=taskId,storageReference=storageReference.trim(),mimeType=mimeType?.trim(),originalName=originalName?.trim(),createdByUserId=actor.id,createdAtEpochMillis=now))
    }

    override suspend fun createChecklistTemplate(draft: ChecklistTemplateDraft): Long {
        val actor=authorizer.require(Permission.CHECKLIST_MANAGE); val now=clock(); require(draft.name.trim().length>=3 && draft.items.isNotEmpty())
        return database.withTransaction {
            draft.branchId?.let { dataScope.requireBranch(it) }
            val id=database.businessOperationsDao().insertChecklistTemplate(ChecklistTemplateEntity(branchId=draft.branchId,name=draft.name.trim(),type=draft.type.name,createdByUserId=actor.id,createdAtEpochMillis=now,updatedAtEpochMillis=now))
            database.businessOperationsDao().insertChecklistTemplateItems(draft.items.mapIndexed { index,item -> ChecklistTemplateItemEntity(templateId=id,title=item.title.trim(),description=item.description?.trim(),sortOrder=index,required=item.required,requiresPhoto=item.requiresPhoto,requiresNoteOnFailure=item.requiresNoteOnFailure) })
            audit.appendAuthorized(authorizer,"CREATE","CHECKLIST_TEMPLATE",id,draft.name,now,correlationId="checklist_template:$id")
            id
        }
    }

    override suspend fun startChecklistRun(templateId: Long, branchId: Long, businessEpochDay: Long, assignedEmployeeId: Long?): Long {
        authorizer.require(Permission.CHECKLIST_PERFORM); val now=clock(); require(templateId>0&&branchId>0&&businessEpochDay>0)
        return database.withTransaction {
            dataScope.requireBranch(branchId)
            val templateItems=database.businessOperationsDao().templateItems(templateId)
            require(templateItems.isNotEmpty()) { "قالب چک‌لیست آیتم فعال ندارد." }
            val runId=database.businessOperationsDao().insertChecklistRun(ChecklistRunEntity(templateId=templateId,branchId=branchId,businessEpochDay=businessEpochDay,assignedEmployeeId=assignedEmployeeId,status=ChecklistStatus.IN_PROGRESS.name,startedAtEpochMillis=now))
            database.businessOperationsDao().insertChecklistRunItems(templateItems.map { ChecklistRunItemEntity(runId=runId,templateItemId=it.id,status="NOT_STARTED") })
            audit.appendAuthorized(authorizer,"START","CHECKLIST_RUN",runId,"شروع اجرای چک‌لیست",now,businessEpochDay,correlationId="checklist_run:$runId:start")
            runId
        }
    }

    override suspend fun completeChecklistItem(runItemId: Long, passed: Boolean, note: String?, attachmentReference: String?) {
        val actor=authorizer.require(Permission.CHECKLIST_PERFORM); val now=clock()
        val normalizedNote=note?.trim()
        val runItem=database.businessOperationsDao().checklistRunItem(runItemId) ?: error("آیتم اجرای چک‌لیست پیدا نشد.")
        val run=database.businessOperationsDao().checklistRun(runItem.runId) ?: error("اجرای چک‌لیست پیدا نشد.")
        dataScope.requireBranch(run.branchId, operational = false)
        val templateItem=database.businessOperationsDao().checklistTemplateItem(runItem.templateItemId) ?: error("آیتم قالب چک‌لیست پیدا نشد.")
        if(templateItem.requiresPhoto) require(!attachmentReference.isNullOrBlank()) { "این آیتم نیازمند پیوست/عکس است." }
        if(!passed && templateItem.requiresNoteOnFailure) require(!normalizedNote.isNullOrBlank()) { "برای آیتم ناموفق ثبت توضیح الزامی است." }
        check(database.businessOperationsDao().completeChecklistItem(runItemId,if(passed) "COMPLETED" else "FAILED",normalizedNote,attachmentReference?.trim(),actor.id,now)==1)
        audit.appendAuthorized(authorizer,if(passed) "ITEM_COMPLETE" else "ITEM_FAILED","CHECKLIST_RUN_ITEM",runItemId,if(passed) "آیتم چک‌لیست تکمیل شد" else "آیتم چک‌لیست ناموفق شد",now,correlationId="checklist_item:$runItemId:$now")
    }

    override suspend fun completeChecklistRun(runId: Long) {
        val actor=authorizer.require(Permission.CHECKLIST_PERFORM); val now=clock()
        database.withTransaction {
            val items=database.businessOperationsDao().checklistRunItems(runId); require(items.isNotEmpty())
            require(items.none { it.status=="NOT_STARTED" || it.status=="IN_PROGRESS" }) { "همه آیتم‌های چک‌لیست باید تعیین تکلیف شوند." }
            val run=database.businessOperationsDao().checklistRun(runId) ?: error("اجرای چک‌لیست پیدا نشد.")
            dataScope.requireBranch(run.branchId, operational = false)
            val failed=items.any { it.status=="FAILED" }
            val requiredFailed=database.businessOperationsDao().requiredFailedItemCount(runId)>0
            val target=if(failed) ChecklistStatus.FAILED.name else ChecklistStatus.WAITING_APPROVAL.name
            check(database.businessOperationsDao().updateChecklistRun(runId,target,now,now,actor.id)==1)
            audit.appendAuthorized(authorizer,"COMPLETE","CHECKLIST_RUN",runId,"پایان اجرای چک‌لیست؛ وضعیت=$target",now,run.businessEpochDay,correlationId="checklist_run:$runId:complete")
            if(requiredFailed) {
                val detected=DetectedIssue(run.branchId,ir.restaurant.management.domain.control.ManagementIssueType.CHECKLIST_FAILED,ir.restaurant.management.domain.control.ManagementIssueSeverity.HIGH,"چک‌لیست الزامی ناموفق","حداقل یک آیتم الزامی چک‌لیست ناموفق ثبت شده است.",businessEpochDay=run.businessEpochDay,sourceType="CHECKLIST_RUN",sourceId=run.id,businessPeriodKey=run.businessEpochDay.toString())
                val issueId=database.businessOperationsDao().insertIssue(ManagementIssueEntity(globalId=GlobalId.new().value,branchId=detected.branchId,type=detected.type.name,severity=detected.severity.name,title=detected.title,description=detected.description,businessEpochDay=detected.businessEpochDay,detectedAtEpochMillis=now,sourceType=detected.sourceType,sourceId=detected.sourceId,deduplicationKey=detected.deduplicationKey,status=ManagementIssueStatus.NEW.name,createdAtEpochMillis=now,updatedAtEpochMillis=now))
                if(issueId>0) audit.appendAuthorized(authorizer,"CREATE","MANAGEMENT_ISSUE",issueId,detected.title,now,run.businessEpochDay,correlationId="issue:${detected.deduplicationKey}")
            }
        }
    }

    override suspend fun approveChecklistRun(runId: Long) {
        val actor=authorizer.require(Permission.CHECKLIST_APPROVE); val now=clock()
        database.withTransaction {
            val run=database.businessOperationsDao().checklistRun(runId) ?: error("اجرای چک‌لیست پیدا نشد.")
            dataScope.requireBranch(run.branchId, operational = false)
            require(run.status==ChecklistStatus.WAITING_APPROVAL.name) { "چک‌لیست در انتظار تأیید نیست." }
            require(run.completedByUserId == null || run.completedByUserId != actor.id) { "تکمیل‌کننده چک‌لیست نمی‌تواند همان اجرا را تأیید کند." }
            check(database.businessOperationsDao().approveChecklistRun(runId,actor.id,now)==1)
            audit.appendAuthorized(authorizer,"APPROVE","CHECKLIST_RUN",runId,"تأیید چک‌لیست",now,run.businessEpochDay,correlationId="checklist_run:$runId:approve")
        }
    }
}
