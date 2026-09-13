package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.PlannedShiftEntity
import ir.restaurant.management.data.db.ShiftTemplateEntity
import ir.restaurant.management.data.db.WorkScheduleDayEntity
import ir.restaurant.management.data.db.WorkScheduleEntity
import ir.restaurant.management.domain.personnel.PlannedShiftDraft
import ir.restaurant.management.domain.personnel.PlannedShiftRecord
import ir.restaurant.management.domain.personnel.PlannedShiftStatus
import ir.restaurant.management.domain.personnel.PlannedShiftTime
import ir.restaurant.management.domain.personnel.PersonnelReferenceCode
import ir.restaurant.management.domain.personnel.EffectiveContractResolver
import ir.restaurant.management.domain.personnel.EmploymentContractStatus
import ir.restaurant.management.domain.personnel.EmploymentContractType
import ir.restaurant.management.domain.personnel.EmploymentContractVersion
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.domain.personnel.ShiftCategory
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.ShiftTemplateRecord
import ir.restaurant.management.domain.personnel.WorkScheduleDayRule
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkSchedulePatternType
import ir.restaurant.management.domain.personnel.WorkScheduleRecord
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.AuthorizationService
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Authoritative Personnel 2.1 scheduling boundary. */
internal class PersonnelSchedulingService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val auditWriter: LocalAuditEventWriter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.managementControlDao()
    private val hr get() = database.hrPayrollDao()
    private val personnel get() = database.personnelDao()
    private val branchResolver = CanonicalBranchResolver(database)

    val shiftTemplates: Flow<List<ShiftTemplateRecord>> = dao.observeShiftTemplates().map { rows -> rows.map { it.toRecord() } }
    val workSchedules: Flow<List<WorkScheduleRecord>> = dao.observeWorkSchedules().map { rows ->
        rows.map { row -> row.toRecord(dao.workScheduleDays(row.id).map { it.toRule() }) }
    }

    fun plannedShifts(employeeId: Long): Flow<List<PlannedShiftRecord>> =
        dao.observePlannedShifts(employeeId).map { rows -> rows.map { it.toRecord() } }

    suspend fun saveShiftTemplate(id: Long?, draft: ShiftTemplateDraft): Long {
        val actor = authorizer.require(Permission.SHIFT_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            valid.branchId?.let { branchResolver.requireActive(it) }
            val now = clock()
            val existing = id?.let { dao.shiftTemplate(it) }
            if (id != null) require(existing != null) { "شیفت پیدا نشد." }
            val resolvedCode = valid.code.ifBlank { existing?.code ?: PersonnelReferenceCode.newShiftCode() }
            val entity = ShiftTemplateEntity(
                id = id ?: 0,
                code = resolvedCode,
                name = valid.name,
                category = valid.category.storedValue,
                startMinute = valid.startMinute,
                endMinute = valid.endMinute,
                crossesMidnight = valid.crossesMidnight,
                plannedWorkMinutes = valid.plannedWorkMinutes,
                breakMinutes = valid.breakMinutes,
                graceInMinutes = valid.graceInMinutes,
                graceOutMinutes = valid.graceOutMinutes,
                overtimeEligible = valid.overtimeEligible,
                overtimeRequiresApproval = valid.overtimeRequiresApproval,
                nightShift = valid.nightShift,
                active = valid.active,
                branchId = valid.branchId,
                notes = valid.notes,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                createdBy = existing?.createdBy ?: actor.displayName,
                updatedBy = actor.displayName,
            )
            val savedId = if (id == null) dao.insertShiftTemplate(entity) else {
                check(dao.updateShiftTemplate(entity) == 1) { "به‌روزرسانی شیفت انجام نشد." }
                id
            }
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = if (id == null) "CREATE" else "UPDATE",
                entityType = "SHIFT_TEMPLATE",
                entityId = savedId,
                description = "مدیریت شیفت پرسنلی",
                occurredAtEpochMillis = now,
                reason = valid.notes.ifBlank { if (id == null) "ایجاد شیفت" else "ویرایش شیفت" },
                beforeSnapshot = existing?.auditSnapshot(),
                afterSnapshot = entity.auditSnapshot(),
                correlationId = "shift_template:${GlobalId.new().value}",
            )
            savedId
        }
    }

    suspend fun saveWorkSchedule(id: Long?, draft: WorkScheduleDraft): Long {
        val actor = authorizer.require(Permission.SHIFT_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            val branch = branchResolver.resolveOptional(valid.branchId, valid.branchName)
            valid.days.filterNot { it.isOffDay }.forEach { rule ->
                val shift = dao.shiftTemplate(requireNotNull(rule.shiftTemplateId)) ?: error("شیفت برنامه کاری پیدا نشد.")
                require(shift.active) { "شیفت غیرفعال را نمی‌توان در برنامه کاری جدید استفاده کرد." }
            }
            val now = clock()
            val existing = id?.let { dao.workSchedule(it) }
            if (id != null) require(existing != null) { "برنامه کاری پیدا نشد." }
            val resolvedCode = valid.code.ifBlank { existing?.code ?: PersonnelReferenceCode.newWorkScheduleCode() }
            val entity = WorkScheduleEntity(
                id = id ?: 0,
                code = resolvedCode,
                name = valid.name,
                patternType = valid.patternType.storedValue,
                cycleLengthDays = valid.cycleLengthDays,
                effectiveFromEpochDay = valid.effectiveFromEpochDay,
                effectiveToEpochDay = valid.effectiveToEpochDay,
                active = valid.active,
                branchName = branch?.name.orEmpty(),
                branchId = branch?.id,
                notes = valid.notes,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                createdBy = existing?.createdBy ?: actor.displayName,
                updatedBy = actor.displayName,
            )
            val savedId = if (id == null) dao.insertWorkSchedule(entity) else {
                check(dao.updateWorkSchedule(entity) == 1) { "به‌روزرسانی برنامه کاری انجام نشد." }
                id
            }
            dao.deleteWorkScheduleDays(savedId)
            dao.insertWorkScheduleDays(valid.days.map { rule ->
                WorkScheduleDayEntity(
                    scheduleId = savedId,
                    sequenceDay = rule.sequenceDay,
                    dayOfWeek = rule.dayOfWeek,
                    shiftTemplateId = rule.shiftTemplateId,
                    isOffDay = rule.isOffDay,
                    overrideStartMinute = rule.overrideStartMinute,
                    overrideEndMinute = rule.overrideEndMinute,
                    notes = rule.notes,
                )
            })
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = if (id == null) "CREATE" else "UPDATE",
                entityType = "WORK_SCHEDULE",
                entityId = savedId,
                description = "مدیریت برنامه کاری پرسنل",
                occurredAtEpochMillis = now,
                reason = valid.notes.ifBlank { if (id == null) "ایجاد برنامه کاری" else "ویرایش برنامه کاری" },
                beforeSnapshot = existing?.auditSnapshot(),
                afterSnapshot = entity.auditSnapshot(),
                correlationId = "work_schedule:${GlobalId.new().value}",
            )
            savedId
        }
    }

    suspend fun savePlannedShift(id: Long?, draft: PlannedShiftDraft): Long {
        val actor = authorizer.require(Permission.SHIFT_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val shift = dao.shiftTemplate(valid.shiftTemplateId) ?: error("شیفت پیدا نشد.")
            require(shift.active) { "شیفت غیرفعال است." }
            valid.scheduleId?.let { require(dao.workSchedule(it)?.active == true) { "برنامه کاری معتبر نیست." } }
            val startMinute = valid.overrideStartMinute ?: shift.startMinute
            val endMinute = valid.overrideEndMinute ?: shift.endMinute
            val startMillis = PlannedShiftTime.startEpochMillis(valid.businessEpochDay, startMinute)
            val endMillis = PlannedShiftTime.endEpochMillis(valid.businessEpochDay, startMinute, endMinute)
            val conflict = dao.plannedShiftsForEmployeeRange(valid.employeeId, valid.businessEpochDay - 1, valid.businessEpochDay + 1)
                .firstOrNull { row -> row.id != id && startMillis < row.plannedEndEpochMillis && endMillis > row.plannedStartEpochMillis }
            require(conflict == null) { "این پرسنل در بازه انتخاب‌شده شیفت هم‌پوشان دارد." }
            val now = clock()
            val existing = id?.let { dao.plannedShift(it) }
            val entity = PlannedShiftEntity(
                id = id ?: 0,
                employeeId = valid.employeeId,
                employeeName = employee.displayName.ifBlank { employee.name },
                role = employee.jobTitle,
                epochDay = valid.businessEpochDay,
                startMinute = startMinute,
                endMinute = endMinute,
                shiftTemplateId = shift.id,
                scheduleId = valid.scheduleId,
                plannedStartEpochMillis = startMillis,
                plannedEndEpochMillis = endMillis,
                breakMinutes = shift.breakMinutes,
                status = valid.status.storedValue,
                source = "MANUAL",
                overrideReason = valid.overrideReason,
                createdBy = existing?.createdBy ?: actor.displayName,
                updatedBy = actor.displayName,
                auditRef = "planned_shift:${valid.commandId}",
            )
            val savedId = if (id == null) dao.insertPlannedShift(entity) else {
                check(dao.updatePlannedShift(entity) == 1) { "به‌روزرسانی شیفت برنامه‌ریزی‌شده انجام نشد." }
                id
            }
            auditWriter.appendAuthorized(
                authorizer, if (id == null) "CREATE" else "UPDATE", "PLANNED_SHIFT", savedId,
                "شیفت برنامه‌ریزی‌شده پرسنل", now, valid.businessEpochDay,
                valid.overrideReason.ifBlank { "برنامه‌ریزی شیفت" }, existing?.auditSnapshot(), entity.auditSnapshot(),
                "planned_shift:${valid.commandId}",
            )
            savedId
        }
    }

    /** Materializes the contract schedule into the authoritative planned_shifts table when needed. */
    suspend fun resolveOrMaterializePlannedShift(employeeId: Long, businessEpochDay: Long): PlannedShiftEntity? {
        dao.plannedShiftForEmployeeDay(employeeId, businessEpochDay)?.let { return it }
        return database.withTransaction {
            dao.plannedShiftForEmployeeDay(employeeId, businessEpochDay)?.let { return@withTransaction it }
            val candidates = hr.effectiveContractCandidates(employeeId, businessEpochDay)
            val resolved = runCatching { EffectiveContractResolver.resolve(employeeId, businessEpochDay, candidates.map { it.toSchedulingDomain() }) }.getOrNull()
                ?: return@withTransaction null
            val contract = candidates.firstOrNull { it.id == resolved.id } ?: return@withTransaction null
            val employee = personnel.employeeById(employeeId) ?: return@withTransaction null
            val schedule = contract.workScheduleId?.let { dao.workSchedule(it) } ?: return@withTransaction null
            if (!schedule.active || businessEpochDay < schedule.effectiveFromEpochDay ||
                (schedule.effectiveToEpochDay != null && businessEpochDay > schedule.effectiveToEpochDay)
            ) return@withTransaction null
            val rule = resolveRule(schedule, businessEpochDay) ?: return@withTransaction null
            if (rule.isOffDay) return@withTransaction null
            val shiftId = rule.shiftTemplateId ?: contract.defaultShiftTemplateId ?: return@withTransaction null
            val shift = dao.shiftTemplate(shiftId)?.takeIf { it.active } ?: return@withTransaction null
            val startMinute = rule.overrideStartMinute ?: shift.startMinute
            val endMinute = rule.overrideEndMinute ?: shift.endMinute
            val actor = authorizer.actorIdentity()
            val entity = PlannedShiftEntity(
                employeeId = employeeId,
                employeeName = employee.displayName.ifBlank { employee.name },
                role = employee.jobTitle,
                epochDay = businessEpochDay,
                startMinute = startMinute,
                endMinute = endMinute,
                shiftTemplateId = shift.id,
                scheduleId = schedule.id,
                plannedStartEpochMillis = PlannedShiftTime.startEpochMillis(businessEpochDay, startMinute),
                plannedEndEpochMillis = PlannedShiftTime.endEpochMillis(businessEpochDay, startMinute, endMinute),
                breakMinutes = shift.breakMinutes,
                status = PlannedShiftStatus.PUBLISHED.storedValue,
                source = "SCHEDULE_AUTO",
                createdBy = actor.displayName,
                updatedBy = actor.displayName,
                auditRef = "schedule_materialize:$employeeId:$businessEpochDay",
            )
            val id = dao.insertPlannedShift(entity)
            auditWriter.appendAuthorized(
                authorizer, "MATERIALIZE", "PLANNED_SHIFT", id, "تولید شیفت از برنامه کاری قرارداد",
                clock(), businessEpochDay, "تولید خودکار Planned Shift از Work Schedule",
                null, entity.copy(id = id).auditSnapshot(), entity.auditRef, "WORK_SCHEDULE", schedule.id,
            )
            entity.copy(id = id)
        }
    }

    suspend fun materializeRange(employeeId: Long, fromEpochDay: Long, toEpochDay: Long): List<PlannedShiftEntity> {
        require(employeeId > 0 && fromEpochDay > 0 && toEpochDay >= fromEpochDay)
        val rows = mutableListOf<PlannedShiftEntity>()
        for (day in fromEpochDay..toEpochDay) {
            resolveOrMaterializePlannedShift(employeeId, day)?.let(rows::add)
        }
        return rows
    }

    private suspend fun resolveRule(schedule: WorkScheduleEntity, businessEpochDay: Long): WorkScheduleDayEntity? {
        val rules = dao.workScheduleDays(schedule.id)
        return if (WorkSchedulePatternType.fromStoredValue(schedule.patternType) == WorkSchedulePatternType.WEEKLY_FIXED) {
            val dow = LocalDate.ofEpochDay(businessEpochDay).dayOfWeek.value
            rules.firstOrNull { it.dayOfWeek == dow }
        } else {
            val sequence = Math.floorMod(businessEpochDay - schedule.effectiveFromEpochDay, schedule.cycleLengthDays.toLong()).toInt()
            rules.firstOrNull { it.sequenceDay == sequence }
        }
    }
}

private fun ir.restaurant.management.data.db.EmploymentContractVersionEntity.toSchedulingDomain() = EmploymentContractVersion(
    id = id,
    employeeId = employeeId,
    contractNumber = contractNumber,
    versionNo = versionNo,
    replacesContractId = replacesContractId,
    contractType = runCatching { EmploymentContractType.valueOf(contractType) }.getOrDefault(EmploymentContractType.OTHER),
    effectiveFromEpochDay = effectiveFromEpochDay,
    effectiveToEpochDay = effectiveToEpochDay,
    baseSalary = MoneyRial.of(baseSalaryRial),
    standardDailyMinutes = standardDailyMinutes,
    standardWeeklyMinutes = standardWeeklyMinutes,
    overtimePolicyId = overtimePolicyId,
    payrollPolicyId = payrollPolicyId,
    workScheduleId = workScheduleId,
    defaultShiftTemplateId = defaultShiftTemplateId,
    jobTitleSnapshot = jobTitleSnapshot,
    departmentSnapshot = departmentSnapshot,
    branchSnapshot = branchSnapshot,
    status = EmploymentContractStatus.fromStoredValue(status),
    createdAtEpochMillis = createdAtEpochMillis,
    createdByActorId = createdByActorId,
    approvedAtEpochMillis = approvedAtEpochMillis,
    approvedByActorId = approvedByActorId,
)

private fun ShiftTemplateEntity.toRecord() = ShiftTemplateRecord(
    id, code, name, ShiftCategory.fromStoredValue(category), startMinute, endMinute, crossesMidnight,
    plannedWorkMinutes, breakMinutes, graceInMinutes, graceOutMinutes, overtimeEligible,
    overtimeRequiresApproval, nightShift, active, branchId, notes,
)

private fun WorkScheduleEntity.toRecord(days: List<WorkScheduleDayRule>) = WorkScheduleRecord(
    id, code, name, WorkSchedulePatternType.fromStoredValue(patternType), cycleLengthDays,
    effectiveFromEpochDay, effectiveToEpochDay, active, branchName, branchId, notes, days,
)

private fun WorkScheduleDayEntity.toRule() = WorkScheduleDayRule(
    sequenceDay, dayOfWeek, shiftTemplateId, isOffDay, overrideStartMinute, overrideEndMinute, notes,
)

private fun PlannedShiftEntity.toRecord() = PlannedShiftRecord(
    id, employeeId, employeeName, role, epochDay, startMinute, endMinute, shiftTemplateId, scheduleId,
    plannedStartEpochMillis, plannedEndEpochMillis, breakMinutes, PlannedShiftStatus.fromStoredValue(status),
    source, overrideReason, auditRef,
)

private fun ShiftTemplateEntity.auditSnapshot() =
    "code=$code|name=$name|category=$category|start=$startMinute|end=$endMinute|cross=$crossesMidnight|break=$breakMinutes|graceIn=$graceInMinutes|graceOut=$graceOutMinutes|approval=$overtimeRequiresApproval|active=$active"

private fun WorkScheduleEntity.auditSnapshot() =
    "code=$code|name=$name|pattern=$patternType|cycle=$cycleLengthDays|from=$effectiveFromEpochDay|to=${effectiveToEpochDay ?: ""}|active=$active"

private fun PlannedShiftEntity.auditSnapshot() =
    "employee=$employeeId|day=$epochDay|shift=${shiftTemplateId ?: ""}|schedule=${scheduleId ?: ""}|start=$plannedStartEpochMillis|end=$plannedEndEpochMillis|status=$status|source=$source"
