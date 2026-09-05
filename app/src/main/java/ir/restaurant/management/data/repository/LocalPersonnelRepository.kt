package ir.restaurant.management.data.repository

import ir.restaurant.management.core.BusinessCalendar

import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties

import androidx.room.withTransaction
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.EmployeeEntity
import ir.restaurant.management.data.db.EmployeePrivateProfileEntity
import ir.restaurant.management.data.db.EmploymentAssignmentEntity
import ir.restaurant.management.data.db.EmploymentContractVersionEntity
import ir.restaurant.management.data.db.AttendanceEventEntity
import ir.restaurant.management.data.db.AttendanceCorrectionEntity
import ir.restaurant.management.data.db.LeaveLedgerEntryEntity
import ir.restaurant.management.data.db.EmployeeAdvanceEntity
import ir.restaurant.management.data.db.EmployeeContractEntity
import ir.restaurant.management.data.db.AttendanceEntity
import ir.restaurant.management.data.db.LeaveEntity
import ir.restaurant.management.data.db.PayrollRunEntity
import ir.restaurant.management.data.db.PayrollAdvanceAllocationEntity
import ir.restaurant.management.data.db.PayrollPolicyEntity
import ir.restaurant.management.data.db.OvertimeApprovalEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.treasury.StoredTreasuryChannelMapper
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.EmployeeAdvanceDraft
import ir.restaurant.management.domain.personnel.EmployeeAdvanceRecord
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeContractRecord
import ir.restaurant.management.domain.personnel.EmployeeRecord
import ir.restaurant.management.domain.personnel.EmployeePrivateProfile
import ir.restaurant.management.domain.personnel.EmploymentAssignment
import ir.restaurant.management.domain.personnel.EmploymentContractVersion
import ir.restaurant.management.domain.personnel.EmploymentContractType
import ir.restaurant.management.domain.personnel.EmploymentContractStatus
import ir.restaurant.management.domain.personnel.EmploymentStatus
import ir.restaurant.management.domain.personnel.EmploymentStatusTransitionValidator
import ir.restaurant.management.domain.personnel.EffectiveContractResolver
import ir.restaurant.management.domain.personnel.AttendanceEvent
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendanceSource
import ir.restaurant.management.domain.personnel.AttendanceEventAggregator
import ir.restaurant.management.domain.personnel.AttendanceAggregationPolicy
import ir.restaurant.management.domain.personnel.AttendanceCorrectionCodec
import ir.restaurant.management.domain.personnel.AttendanceCorrectionSnapshot
import ir.restaurant.management.domain.personnel.DailyAttendanceStatus
import ir.restaurant.management.domain.personnel.DailyAttendanceSummaryV2
import ir.restaurant.management.domain.personnel.LeaveType
import ir.restaurant.management.domain.personnel.LeaveStatus
import ir.restaurant.management.domain.personnel.LeaveGrantDraft
import ir.restaurant.management.domain.personnel.LeaveBalance
import ir.restaurant.management.domain.personnel.LeaveBalanceCalculator
import ir.restaurant.management.domain.personnel.LeaveLedgerEntry
import ir.restaurant.management.domain.personnel.LeaveLedgerEntryType
import ir.restaurant.management.domain.personnel.PendingLeaveUsage
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.AttendancePunchDraft
import ir.restaurant.management.domain.personnel.AttendanceRecord
import ir.restaurant.management.domain.personnel.AttendanceSummary
import ir.restaurant.management.domain.personnel.AttendanceCalculator
import ir.restaurant.management.domain.personnel.AttendancePayrollCalculator
import ir.restaurant.management.domain.personnel.AttendancePayrollPolicy
import ir.restaurant.management.domain.personnel.AttendancePayrollAdjustment
import ir.restaurant.management.domain.personnel.LeaveDraft
import ir.restaurant.management.domain.personnel.LeaveRecord
import ir.restaurant.management.domain.personnel.LeaveReviewDraft
import ir.restaurant.management.domain.personnel.PayrollCalculator
import ir.restaurant.management.domain.personnel.PayrollRecord
import ir.restaurant.management.domain.personnel.PayrollStatus
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.PayrollPolicyRecord
import ir.restaurant.management.domain.personnel.AdvanceDeductionAllocator
import ir.restaurant.management.domain.personnel.OpenAdvanceBalance
import ir.restaurant.management.domain.personnel.PersonnelRepository
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.ShiftTemplateRecord
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkScheduleRecord
import ir.restaurant.management.domain.personnel.PlannedShiftDraft
import ir.restaurant.management.domain.personnel.PlannedShiftRecord
import ir.restaurant.management.domain.personnel.OvertimeApprovalRecord
import ir.restaurant.management.domain.personnel.OvertimeReviewCommand
import ir.restaurant.management.domain.personnel.EmployeeAuditRecord
import ir.restaurant.management.domain.personnel.HrDocumentDraft
import ir.restaurant.management.domain.personnel.HrDocumentRecord
import ir.restaurant.management.domain.personnel.HrDocumentType
import ir.restaurant.management.domain.personnel.PayrollReadinessIssue
import ir.restaurant.management.domain.personnel.PayrollReadinessResult
import ir.restaurant.management.domain.personnel.PayrollReadinessStatus
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalDraft
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.accounting.JournalStatus
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

class LocalPersonnelRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
    private val treasury: TreasuryService = ir.restaurant.management.data.treasury.LocalTreasuryServiceV2(
        database = database, accounting = LocalAccountingPostingEngine(database, clock = clock), authorizer = authorizer,
        accountCatalog = ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog(), clock = clock,
    ),
) : PersonnelRepository {
    private val personnel get() = database.personnelDao()
    private val hr get() = database.hrPayrollDao()
    private val accounting get() = database.accountingDao()
    private val accountingPosting = LocalAccountingPostingEngine(database, clock = clock)
    private val auditWriter = LocalAuditEventWriter(database)
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val attendanceService = PersonnelAttendanceService(database, authorizer, auditWriter, clock)
    private val schedulingService = PersonnelSchedulingService(database, authorizer, auditWriter, clock)
    private val branchResolver = CanonicalBranchResolver(database)

    override val shiftTemplates: Flow<List<ShiftTemplateRecord>> = schedulingService.shiftTemplates
    override val workSchedules: Flow<List<WorkScheduleRecord>> = schedulingService.workSchedules
    override val pendingOvertimeApprovals: Flow<List<OvertimeApprovalRecord>> = hr.observePendingOvertimeApprovals().map { rows -> rows.map { it.toRecord() } }
    override val pendingAttendanceCorrections = attendanceService.pendingCorrections()

    override val employees: Flow<List<EmployeeRecord>> = combine(
        personnel.observeEmployees(),
        hr.observePrivateProfiles(),
        hr.observeLatestContractStatuses(),
    ) { rows, privateProfiles, contractStatuses ->
        val privateByEmployee = privateProfiles.associateBy { it.employeeId }
        val contractStatusByEmployee = contractStatuses.associate { row ->
            row.employeeId to EmploymentContractStatus.fromStoredValue(row.status)
        }
        rows.map {
            val privateProfile = privateByEmployee[it.id]
            EmployeeRecord(
                id = it.id, name = it.name, fatherName = it.fatherName, jobTitle = it.jobTitle, phone = it.phone,
                monthlySalaryRial = it.monthlySalaryRial, isActive = EmploymentStatus.fromStoredValue(it.status) == EmploymentStatus.ACTIVE, nationalId = null,
                birthEpochDay = it.birthEpochDay, hireEpochDay = it.hireEpochDay, employeeCode = it.employeeCode,
                branchName = it.branchName, insuranceNumber = null, bankCard = null,
                address = it.address, emergencyContact = it.emergencyContact,
                firstName = it.firstName, lastName = it.lastName, displayName = it.displayName.ifBlank { it.name },
                email = it.email, department = it.department, locationId = it.locationId, managerId = it.managerId,
                employmentStatus = EmploymentStatus.fromStoredValue(it.status), terminationEpochDay = it.terminationEpochDay,
                notes = it.notes,
                maskedBankAccount = (privateProfile?.bankAccountLast4 ?: privateProfile?.ibanLast4 ?: it.bankCard).maskedLastFour(),
                contractStatus = contractStatusByEmployee[it.id],
                branchId = it.branchId,
            )
        }
    }

    override val payrolls: Flow<List<PayrollRecord>> = personnel.observePayrolls().map { rows ->
        rows.map {
            PayrollRecord(
                it.id,
                it.employeeId,
                it.employeeName,
                it.periodYear,
                it.periodMonth,
                it.revisionNo,
                it.grossPayRial,
                it.netPayRial,
                it.paymentEpochDay,
                StoredTreasuryChannelMapper.fromPersonnelStoredValue(it.paymentMethod),
                PayrollStatus.fromStoredValue(it.status),
                it.reversalEpochDay,
                it.reversalReason,
            )
        }
    }

    override val attendance: Flow<List<AttendanceRecord>> = personnel.observeAttendance().map { rows ->
        rows.map { it.toRecord() }
    }

    override val leaves: Flow<List<LeaveRecord>> = personnel.observeLeaves().map { rows -> rows.map { it.toRecord() } }
    override val pendingLeaves: Flow<List<LeaveRecord>> = personnel.observePendingLeaves().map { rows -> rows.map { it.toRecord() } }
    override val payrollPolicies: Flow<List<PayrollPolicyRecord>> = personnel.observePayrollPolicies().map { rows ->
        rows.map { PayrollPolicyRecord(
            it.id, it.title, it.effectiveFromEpochDay, it.effectiveToEpochDay, it.overtimeHourlyRateRial,
            it.absenceDailyDeductionRial, it.lateMinuteDeductionRial, it.createdBy, it.versionNo,
            it.overtimeMultiplierBasisPoints, it.insuranceBasisPoints, it.taxBasisPoints,
            it.holidayMultiplierBasisPoints, it.nightMultiplierBasisPoints,
        ) }
    }

    override suspend fun savePayrollPolicy(draft: PayrollPolicyDraft): Long {
        val actor = authorizer.require(Permission.PAYROLL_CREATE)
        val valid = draft.validated()
        return database.withTransaction {
            val previous = personnel.openPayrollPolicyBefore(valid.effectiveFromEpochDay)
            previous?.let { row ->
                check(personnel.closeOpenPayrollPolicy(row.id, valid.effectiveFromEpochDay - 1L) == 1) {
                    "بستن نسخه قبلی سیاست حقوق انجام نشد."
                }
            }
            require(!personnel.payrollPolicyOverlaps(valid.effectiveFromEpochDay, valid.effectiveToEpochDay ?: Long.MAX_VALUE)) {
                "بازه این سیاست با سیاست حقوق دیگری هم‌پوشانی دارد."
            }
            val now = clock()
            personnel.insertPayrollPolicy(PayrollPolicyEntity(
                title = valid.title, effectiveFromEpochDay = valid.effectiveFromEpochDay,
                effectiveToEpochDay = valid.effectiveToEpochDay, overtimeHourlyRateRial = valid.overtimeHourlyRateRial,
                absenceDailyDeductionRial = valid.absenceDailyDeductionRial, lateMinuteDeductionRial = valid.lateMinuteDeductionRial,
                versionNo = (previous?.versionNo ?: 0) + 1, overtimeMultiplierBasisPoints = valid.overtimeMultiplierBasisPoints,
                insuranceBasisPoints = valid.insuranceBasisPoints, taxBasisPoints = valid.taxBasisPoints,
                holidayMultiplierBasisPoints = valid.holidayMultiplierBasisPoints,
                nightMultiplierBasisPoints = valid.nightMultiplierBasisPoints,
                createdBy = actor.displayName, createdByActorId = actor.id, createdAtEpochMillis = now,
                correlationId = "payroll_policy:${GlobalId.new().value}",
            )).also { syncRecorder?.record("PAYROLL_POLICY", it, "CREATE", now) }
        }
    }

    override suspend fun saveEmployee(id: Long?, draft: EmployeeDraft): Long {
        val actor = authorizer.require(Permission.PERSONNEL_EDIT)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            val resolvedBranch = branchResolver.resolveOptional(valid.branchId, valid.branchName)
            val canonicalBranchName = resolvedBranch?.name.orEmpty()
            if (id == null) {
                if (valid.nationalId.isNotBlank() || valid.insuranceNumber.isNotBlank() || valid.bankCard.isNotBlank()) {
                    authorizer.require(Permission.EMPLOYEE_SENSITIVE_EDIT)
                }
                val employeeCode = valid.employeeCode.ifBlank { newEmployeeCode() }
                val employeeId = personnel.insertEmployee(EmployeeEntity(name = valid.name, firstName = valid.firstName, lastName = valid.lastName,
                    displayName = valid.displayName.ifBlank { valid.name }, fatherName = valid.fatherName, employeeCode = employeeCode,
                    jobTitle = valid.jobTitle, department = valid.department.ifBlank { "UNASSIGNED" }, branchName = canonicalBranchName, branchId = resolvedBranch?.id,
                    locationId = valid.locationId, managerId = valid.managerId, phone = valid.phone, email = valid.email.takeIf { it.isNotBlank() },
                    nationalId = null, birthEpochDay = valid.birthEpochDay, hireEpochDay = valid.hireEpochDay,
                    terminationEpochDay = valid.terminationEpochDay, insuranceNumber = null,
                    bankCard = null, address = valid.address, emergencyContact = valid.emergencyContact,
                    notes = valid.notes, monthlySalaryRial = valid.monthlySalaryRial, leaveBalanceMicros = 0,
                    status = valid.employmentStatus.storedValue, createdAtEpochMillis = now, updatedAtEpochMillis = now,
                    createdByActorId = actor.id, updatedByActorId = actor.id))
                savePrivateProfile(employeeId, valid, now, actor.id, null)
                valid.hireEpochDay?.let { hireDay ->
                    hr.insertAssignment(
                        EmploymentAssignmentEntity(
                            employeeId = employeeId, effectiveFromEpochDay = hireDay, effectiveToEpochDay = null,
                            jobTitle = valid.jobTitle, department = valid.department.ifBlank { "UNASSIGNED" }, branchName = canonicalBranchName, branchId = resolvedBranch?.id,
                            locationId = valid.locationId, managerId = valid.managerId, reason = "EMPLOYEE_CREATED",
                            createdAtEpochMillis = now, createdByActorId = actor.id,
                            correlationId = "employee:$employeeId:create",
                        ),
                    )
                }
                auditWriter.appendAuthorized(
                    authorizer, "CREATE", "EMPLOYEE", employeeId, "ایجاد پروفایل پرسنل ${valid.displayName.ifBlank { valid.name }}",
                    now, valid.hireEpochDay, "ایجاد پرسنل", null,
                    "employeeCode=$employeeCode;status=${valid.employmentStatus.storedValue}", "employee:$employeeId:create",
                )
                syncRecorder?.record("EMPLOYEE", employeeId, "CREATE", now)
                employeeId
            } else {
                val current = personnel.employeeById(id) ?: error("پرسنل پیدا نشد.")
                val fromStatus = EmploymentStatus.fromStoredValue(current.status)
                EmploymentStatusTransitionValidator.requireAllowed(fromStatus, valid.employmentStatus, valid.terminationEpochDay)
                val employeeCode = current.employeeCode ?: valid.employeeCode.ifBlank { newEmployeeCode() }
                require(valid.employeeCode.isBlank() || valid.employeeCode == employeeCode) { "کد پرسنلی پس از ایجاد قابل تغییر نیست." }
                val privateChanged = valid.nationalId.isNotBlank() || valid.insuranceNumber.isNotBlank() || valid.bankCard.isNotBlank()
                if (privateChanged) authorizer.require(Permission.EMPLOYEE_SENSITIVE_EDIT)
                val assignmentChanged = current.jobTitle != valid.jobTitle || current.department != valid.department.ifBlank { "UNASSIGNED" } ||
                    current.branchId != resolvedBranch?.id || current.locationId != valid.locationId || current.managerId != valid.managerId
                if (assignmentChanged) {
                    val effectiveDay = requireNotNull(valid.assignmentEffectiveEpochDay) { "تاریخ اثر تغییر شغلی الزامی است." }
                    hr.openAssignment(id)?.let { assignment ->
                        require(effectiveDay > assignment.effectiveFromEpochDay) { "تاریخ اثر باید بعد از شروع انتساب فعلی باشد." }
                        check(hr.closeAssignment(assignment.id, effectiveDay - 1) == 1) { "بستن انتساب قبلی انجام نشد." }
                    }
                    hr.insertAssignment(
                        EmploymentAssignmentEntity(
                            employeeId = id, effectiveFromEpochDay = effectiveDay, effectiveToEpochDay = null,
                            jobTitle = valid.jobTitle, department = valid.department.ifBlank { "UNASSIGNED" }, branchName = canonicalBranchName, branchId = resolvedBranch?.id,
                            locationId = valid.locationId, managerId = valid.managerId, reason = "EMPLOYMENT_CHANGE",
                            createdAtEpochMillis = now, createdByActorId = actor.id,
                            correlationId = "employee:$id:assignment:$effectiveDay",
                        ),
                    )
                }
                check(personnel.updateEmployee(current.copy(name=valid.name, firstName=valid.firstName, lastName=valid.lastName,
                    displayName=valid.displayName.ifBlank { valid.name }, fatherName=valid.fatherName, employeeCode=employeeCode,
                    jobTitle=valid.jobTitle, department=valid.department.ifBlank { "UNASSIGNED" }, branchName=canonicalBranchName, branchId=resolvedBranch?.id,
                    locationId=valid.locationId, managerId=valid.managerId, phone=valid.phone, email=valid.email.takeIf { it.isNotBlank() },
                    nationalId=null, birthEpochDay=valid.birthEpochDay, hireEpochDay=valid.hireEpochDay,
                    terminationEpochDay=valid.terminationEpochDay, insuranceNumber=null,
                    bankCard=null, address=valid.address, emergencyContact=valid.emergencyContact,
                    notes=valid.notes, monthlySalaryRial=valid.monthlySalaryRial, status=valid.employmentStatus.storedValue,
                    updatedAtEpochMillis=now, updatedByActorId=actor.id)) == 1)
                savePrivateProfile(id, valid, now, actor.id, current)
                auditWriter.appendAuthorized(
                    authorizer, "UPDATE", "EMPLOYEE", id, "ویرایش پروفایل پرسنل ${valid.displayName.ifBlank { valid.name }}",
                    now, valid.assignmentEffectiveEpochDay, "ویرایش پرسنل",
                    "employeeCode=${current.employeeCode};status=${current.status};jobTitle=${current.jobTitle}",
                    "employeeCode=$employeeCode;status=${valid.employmentStatus.storedValue};jobTitle=${valid.jobTitle}",
                    "employee:$id:update:$now",
                )
                syncRecorder?.record("EMPLOYEE", id, "UPDATE", now)
                id
            }
        }
    }

    override suspend fun transitionEmploymentStatus(id: Long, to: EmploymentStatus, terminationEpochDay: Long?) {
        val actor = authorizer.require(Permission.PERSONNEL_EDIT)
        database.withTransaction {
            val current = personnel.employeeById(id) ?: error("پرسنل پیدا نشد.")
            val from = EmploymentStatus.fromStoredValue(current.status)
            EmploymentStatusTransitionValidator.requireAllowed(from, to, terminationEpochDay)
            if (from == to) return@withTransaction
            val now = clock()
            check(personnel.transitionEmployeeStatus(id, from.storedValue, to.storedValue, terminationEpochDay, now, actor.id) == 1) {
                "تغییر وضعیت پرسنل به علت تغییر هم‌زمان انجام نشد."
            }
            auditWriter.appendAuthorized(
                authorizer, "STATUS_CHANGE", "EMPLOYEE", id, "تغییر وضعیت استخدام",
                now, terminationEpochDay ?: BusinessCalendar.epochDayAt(now), "تغییر کنترل‌شده وضعیت", "status=${from.storedValue}",
                "status=${to.storedValue};terminationEpochDay=$terminationEpochDay", "employee:$id:status:$now",
            )
        }
    }

    override suspend fun privateProfile(employeeId: Long): EmployeePrivateProfile? {
        authorizer.require(Permission.PERSONNEL_CONFIDENTIAL_VIEW)
        return hr.privateProfile(employeeId)?.toDomain()
    }

    override fun assignments(employeeId: Long): Flow<List<EmploymentAssignment>> =
        hr.observeAssignments(employeeId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun deactivateEmployee(id: Long) {
        transitionEmploymentStatus(id, EmploymentStatus.ARCHIVED)
        syncRecorder?.record("EMPLOYEE", id, "ARCHIVE", clock())
    }

    override fun contracts(employeeId: Long): Flow<List<EmployeeContractRecord>> =
        hr.observeContractVersions(employeeId).map { rows ->
            rows.map { it.toRecord() }
        }

    override fun auditTimeline(employeeId: Long): Flow<List<EmployeeAuditRecord>> = flow {
        authorizer.require(Permission.PERSONNEL_VIEW)
        emitAll(database.auditLogDao().observeEmployeeTimeline(employeeId).map { rows ->
            rows.map { row ->
                EmployeeAuditRecord(
                    id = row.id,
                    occurredAtEpochMillis = row.createdAtEpochMillis,
                    businessEpochDay = row.businessEpochDay,
                    actor = row.actor,
                    action = row.action,
                    entityType = row.entityType,
                    entityId = row.entityId,
                    reason = row.reason,
                    beforeSnapshot = row.beforeSnapshot,
                    afterSnapshot = row.afterSnapshot,
                    correlationId = row.correlationId,
                )
            }
        })
    }

    override fun documents(employeeId: Long): Flow<List<HrDocumentRecord>> = flow {
        authorizer.require(Permission.HR_DOCUMENT_VIEW)
        emitAll(hr.observeHrDocuments(employeeId).map { rows ->
            rows.map { row ->
                HrDocumentRecord(
                    id = row.id,
                    employeeId = row.employeeId,
                    documentType = HrDocumentType.valueOf(row.documentType),
                    displayName = row.displayName,
                    contentUri = row.contentUri,
                    mimeType = row.mimeType,
                    issueEpochDay = row.issueEpochDay,
                    expiryEpochDay = row.expiryEpochDay,
                    notes = row.notes,
                )
            }
        })
    }

    override suspend fun saveDocument(draft: HrDocumentDraft): Long {
        val actor = authorizer.require(Permission.HR_DOCUMENT_MANAGE)
        val valid = draft.validated()
        return database.withTransaction {
            personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val now = clock()
            val correlation = "hr_document:${valid.employeeId}:${GlobalId.new().value}"
            val id = hr.insertHrDocument(
                ir.restaurant.management.data.db.HrDocumentEntity(
                    employeeId = valid.employeeId,
                    documentType = valid.documentType.storedValue,
                    displayName = valid.displayName,
                    contentUri = valid.contentUri,
                    mimeType = valid.mimeType,
                    issueEpochDay = valid.issueEpochDay,
                    expiryEpochDay = valid.expiryEpochDay,
                    status = "ACTIVE",
                    notes = valid.notes,
                    createdAtEpochMillis = now,
                    createdByActorId = actor.id,
                    correlationId = correlation,
                ),
            )
            auditWriter.appendAuthorized(
                authorizer, "CREATE", "HR_DOCUMENT", id, "ثبت سند منابع انسانی ${valid.displayName}",
                now, valid.issueEpochDay, valid.notes.ifBlank { "افزودن سند منابع انسانی" }, null,
                "employeeId=${valid.employeeId};type=${valid.documentType.storedValue};mime=${valid.mimeType}", correlation,
                "EMPLOYEE", valid.employeeId,
            )
            id
        }
    }

    override suspend fun archiveDocument(documentId: Long) {
        val actor = authorizer.require(Permission.HR_DOCUMENT_MANAGE)
        database.withTransaction {
            check(hr.archiveHrDocument(documentId) == 1) { "سند پیدا نشد یا قبلاً بایگانی شده است." }
            auditWriter.appendAuthorized(
                authorizer, "ARCHIVE", "HR_DOCUMENT", documentId, "بایگانی سند منابع انسانی",
                clock(), null, "بایگانی کنترل‌شده سند", "status=ACTIVE", "status=ARCHIVED",
                "hr_document:$documentId:archive:${actor.id}",
            )
        }
    }

    override fun advances(employeeId: Long): Flow<List<EmployeeAdvanceRecord>> =
        personnel.observeAdvances(employeeId).map { rows -> rows.map { it.toRecord() } }

    override val openAdvances: Flow<List<EmployeeAdvanceRecord>> =
        personnel.observeOpenAdvances().map { rows -> rows.map { it.toRecord() } }

    override suspend fun saveContract(id: Long?, draft: EmployeeContractDraft): Long {
        val actor = authorizer.require(Permission.CONTRACT_CREATE)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val employeeStatus = EmploymentStatus.fromStoredValue(employee.status)
            require(employeeStatus in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE)) { "برای این وضعیت استخدام نمی‌توان قرارداد ثبت کرد." }
            val previous = id?.let { hr.contractVersion(it) ?: error("قرارداد پیدا نشد.") }
            require(previous == null || previous.employeeId == valid.employeeId) { "پرسنل قرارداد قابل تغییر نیست." }
            require(previous == null || previous.status !in setOf(
                EmploymentContractStatus.SUPERSEDED.storedValue,
                EmploymentContractStatus.CANCELLED.storedValue,
            )) { "نسخه منقضی یا جانشین‌شده مبنای اصلاح جدید نیست." }
            if (previous != null) require(valid.correctionReason.length >= 3) { "دلیل اصلاح نسخه قرارداد الزامی است." }
            val versionNo = (previous?.versionNo ?: 0) + 1
            val contractNumber = when {
                valid.contractNumber.isNotBlank() -> valid.contractNumber
                previous != null -> "${previous.contractNumber}-R$versionNo"
                else -> "CTR-${employee.employeeCode ?: employee.id}-${GlobalId.new().value.take(8).uppercase()}"
            }
            require(hr.contractByNumber(contractNumber) == null) { "شماره قرارداد تکراری است." }
            val conflicts = hr.overlappingContracts(
                valid.employeeId, valid.startEpochDay, valid.endEpochDay ?: Long.MAX_VALUE, previous?.id ?: 0,
            )
            require(conflicts.isEmpty()) { "بازه قرارداد با قرارداد دیگری هم‌پوشانی دارد." }
            val type = valid.contractType.toContractType()
            valid.workScheduleId?.let { scheduleId ->
                val schedule = database.managementControlDao().workSchedule(scheduleId) ?: error("برنامه کاری پیدا نشد.")
                require(schedule.active) { "برنامه کاری غیرفعال است." }
                require(valid.startEpochDay >= schedule.effectiveFromEpochDay) { "شروع قرارداد قبل از شروع برنامه کاری است." }
                require(schedule.effectiveToEpochDay == null || valid.startEpochDay <= schedule.effectiveToEpochDay) { "برنامه کاری در شروع قرارداد منقضی است." }
            }
            valid.defaultShiftTemplateId?.let { shiftId ->
                val shift = database.managementControlDao().shiftTemplate(shiftId) ?: error("شیفت پیش‌فرض پیدا نشد.")
                require(shift.active) { "شیفت پیش‌فرض غیرفعال است." }
            }
            val contractId = hr.insertContractVersion(
                EmploymentContractVersionEntity(
                    employeeId = valid.employeeId, contractNumber = contractNumber, versionNo = versionNo,
                    replacesContractId = previous?.id, contractType = type.storedValue,
                    effectiveFromEpochDay = valid.startEpochDay, effectiveToEpochDay = valid.endEpochDay,
                    baseSalaryRial = valid.baseSalaryRial, standardDailyMinutes = valid.dailyWorkMinutes,
                    standardWeeklyMinutes = Math.multiplyExact(valid.dailyWorkMinutes, valid.weeklyWorkDays),
                    overtimePolicyId = valid.overtimePolicyId, payrollPolicyId = valid.payrollPolicyId,
                    workScheduleId = valid.workScheduleId, defaultShiftTemplateId = valid.defaultShiftTemplateId,
                    jobTitleSnapshot = valid.jobTitleSnapshot.ifBlank { employee.jobTitle },
                    departmentSnapshot = valid.departmentSnapshot.ifBlank { employee.department },
                    branchSnapshot = valid.branchSnapshot.ifBlank { employee.branchName },
                    status = EmploymentContractStatus.PENDING_APPROVAL.storedValue,
                    notes = listOf(valid.notes, valid.correctionReason.takeIf { it.isNotBlank() }?.let { "اصلاح: $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" | "),
                    createdAtEpochMillis = now, createdByActorId = actor.id, approvedAtEpochMillis = null,
                    approvedByActorId = null, correlationId = "contract:${valid.employeeId}:$contractNumber",
                    source = "NATIVE",
                ),
            )
            auditWriter.appendAuthorized(
                authorizer, if (previous == null) "CREATE" else "REVISE", "EMPLOYMENT_CONTRACT", contractId,
                if (previous == null) "ایجاد قرارداد $contractNumber" else "نسخه جدید قرارداد $contractNumber",
                now, valid.startEpochDay, valid.correctionReason.ifBlank { "ایجاد قرارداد" },
                previous?.let { "contractId=${it.id};version=${it.versionNo};status=${it.status}" },
                "contractId=$contractId;version=$versionNo;status=PENDING_APPROVAL", "contract:${valid.employeeId}:$contractNumber",
            )
            contractId
        }
    }

    override suspend fun saveShiftTemplate(id: Long?, draft: ShiftTemplateDraft): Long = schedulingService.saveShiftTemplate(id, draft)
    override suspend fun saveWorkSchedule(id: Long?, draft: WorkScheduleDraft): Long = schedulingService.saveWorkSchedule(id, draft)
    override fun plannedShifts(employeeId: Long): Flow<List<PlannedShiftRecord>> = schedulingService.plannedShifts(employeeId)
    override suspend fun savePlannedShift(id: Long?, draft: PlannedShiftDraft): Long = schedulingService.savePlannedShift(id, draft)

    override suspend fun approveContract(id: Long) {
        val actor = authorizer.require(Permission.CONTRACT_APPROVE)
        database.withTransaction {
            val contract = hr.contractVersion(id) ?: error("قرارداد پیدا نشد.")
            if (contract.status == EmploymentContractStatus.APPROVED.storedValue) return@withTransaction
            require(contract.status == EmploymentContractStatus.PENDING_APPROVAL.storedValue) { "قرارداد در انتظار تأیید نیست." }
            contract.createdByActorId?.let {
                SegregationOfDuties.requireDifferentActors("CONTRACT_APPROVAL", it, actor.id)
            }
            val conflicts = hr.overlappingContracts(
                contract.employeeId, contract.effectiveFromEpochDay,
                contract.effectiveToEpochDay ?: Long.MAX_VALUE, contract.id,
            ).filter { it.id != contract.replacesContractId }
            require(conflicts.isEmpty()) { "قرارداد با نسخه فعال دیگری هم‌پوشانی دارد." }
            require(contract.workScheduleId != null) { "برای تأیید قرارداد، برنامه کاری معتبر الزامی است." }
            val schedule = database.managementControlDao().workSchedule(contract.workScheduleId)
                ?: error("برنامه کاری قرارداد پیدا نشد.")
            require(schedule.active) { "برنامه کاری قرارداد غیرفعال است." }
            require(contract.payrollPolicyId != null) { "برای تأیید قرارداد، سیاست حقوق و دستمزد الزامی است." }
            val policy = personnel.payrollPolicyById(contract.payrollPolicyId)
                ?: error("سیاست حقوق قرارداد پیدا نشد.")
            require(policy.status == "ACTIVE") { "سیاست حقوق قرارداد فعال نیست." }
            val now = clock()
            contract.replacesContractId?.let { predecessorId ->
                check(hr.markContractSuperseded(predecessorId) == 1) { "نسخه قبلی قرارداد قابل جانشینی نیست." }
            }
            check(hr.approveContract(id, now, actor.id) == 1) { "تأیید قرارداد انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, "APPROVE", "EMPLOYMENT_CONTRACT", id, "تأیید قرارداد ${contract.contractNumber}",
                now, contract.effectiveFromEpochDay, "تأیید قرارداد توسط actor مستقل",
                "status=${contract.status};createdBy=${contract.createdByActorId}",
                "status=APPROVED;approvedBy=${actor.id}", contract.correlationId,
            )
        }
    }

    override suspend fun effectiveContract(employeeId: Long, businessEpochDay: Long): EmploymentContractVersion =
        EffectiveContractResolver.resolve(
            employeeId,
            businessEpochDay,
            hr.effectiveContractCandidates(employeeId, businessEpochDay).map { it.toDomain() },
        )

    override suspend fun payrollReadiness(employeeId: Long, businessEpochDay: Long): PayrollReadinessResult {
        authorizer.require(Permission.PERSONNEL_VIEW)
        require(employeeId > 0 && businessEpochDay > 0)
        val employee = personnel.employeeById(employeeId) ?: error("پرسنل پیدا نشد.")
        val issues = mutableListOf<PayrollReadinessIssue>()
        val status = EmploymentStatus.fromStoredValue(employee.status)
        if (status !in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE)) {
            issues += PayrollReadinessIssue("EMPLOYEE_NOT_ACTIVE", "وضعیت پرسنل برای محاسبه حقوق فعال نیست.", true, "اصلاح وضعیت استخدام")
        }
        if (employee.hireEpochDay == null || employee.hireEpochDay <= 0) {
            issues += PayrollReadinessIssue("MISSING_HIRE_DATE", "تاریخ استخدام ثبت نشده است.", true, "ویرایش پرسنل")
        }
        val candidates = hr.effectiveContractCandidates(employeeId, businessEpochDay)
        val contract = try {
            val resolved = EffectiveContractResolver.resolve(employeeId, businessEpochDay, candidates.map { it.toDomain() })
            candidates.firstOrNull { it.id == resolved.id }
        } catch (error: ir.restaurant.management.domain.common.BusinessRuleViolation) {
            when (error.error) {
                is BusinessError.NoEffectiveContract -> issues += PayrollReadinessIssue("NO_EFFECTIVE_CONTRACT", "برای این تاریخ قرارداد فعال و تأییدشده یافت نشد.", true, "ثبت/اصلاح قرارداد")
                is BusinessError.ConflictingContracts -> issues += PayrollReadinessIssue("CONFLICTING_CONTRACTS", "قراردادهای مؤثر با یکدیگر تعارض دارند.", true, "رفع تعارض قرارداد")
                else -> throw error
            }
            null
        }
        if (contract != null) {
            if (contract.status !in setOf("APPROVED", "ACTIVE", "SUPERSEDED", "LEGACY")) {
                issues += PayrollReadinessIssue("CONTRACT_NOT_APPROVED", "قرارداد هنوز تأیید یا فعال نشده است.", true, "تأیید قرارداد")
            }
            val scheduleId = contract.workScheduleId
            if (scheduleId == null) {
                issues += PayrollReadinessIssue("NO_WORK_SCHEDULE", "برنامه کاری به قرارداد متصل نشده است.", true, "تنظیم برنامه کاری")
            } else {
                val schedule = database.managementControlDao().workSchedule(scheduleId)
                if (schedule == null || !schedule.active || businessEpochDay < schedule.effectiveFromEpochDay ||
                    (schedule.effectiveToEpochDay != null && businessEpochDay > schedule.effectiveToEpochDay)
                ) {
                    issues += PayrollReadinessIssue("INVALID_WORK_SCHEDULE", "برنامه کاری قرارداد در این تاریخ معتبر نیست.", true, "اصلاح برنامه کاری")
                } else if (database.managementControlDao().workScheduleDays(scheduleId).isEmpty()) {
                    issues += PayrollReadinessIssue("EMPTY_WORK_SCHEDULE", "الگوی روزهای برنامه کاری تعریف نشده است.", true, "تکمیل برنامه کاری")
                }
            }
            val policyId = contract.payrollPolicyId
            val policy = policyId?.let { personnel.payrollPolicyById(it) }
            if (policy == null || policy.status != "ACTIVE" || businessEpochDay < policy.effectiveFromEpochDay ||
                (policy.effectiveToEpochDay != null && businessEpochDay > policy.effectiveToEpochDay)
            ) {
                issues += PayrollReadinessIssue("MISSING_PAYROLL_POLICY", "سیاست حقوق مؤثر برای قرارداد موجود نیست.", true, "تنظیم سیاست حقوق")
            }
        }
        val privateProfile = hr.privateProfile(employeeId)
        val hasPaymentDestination = !privateProfile?.bankAccountLast4.isNullOrBlank() ||
            !privateProfile?.ibanLast4.isNullOrBlank() || !employee.bankCard.isNullOrBlank()
        if (!hasPaymentDestination) {
            issues += PayrollReadinessIssue("MISSING_PAYMENT_DESTINATION", "اطلاعات مقصد پرداخت حقوق کامل نیست.", true, "تکمیل اطلاعات بانکی")
        }
        if (employee.nationalId.isNullOrBlank() && privateProfile?.nationalId.isNullOrBlank()) {
            issues += PayrollReadinessIssue("MISSING_CONFIDENTIAL_PROFILE", "اطلاعات محرمانه پایه پرسنل کامل نیست.", false, "تکمیل اطلاعات محرمانه")
        }
        if (personnel.attendanceDayLocked(employeeId, businessEpochDay)) {
            issues += PayrollReadinessIssue("ATTENDANCE_LOCKED", "حضور و غیاب این تاریخ قفل است؛ اصلاح فقط از مسیر Revision ممکن است.", false, "مشاهده قفل حضور")
        }
        val resultStatus = when {
            issues.any { it.blocking } -> PayrollReadinessStatus.BLOCKED
            issues.isNotEmpty() -> PayrollReadinessStatus.WARNING
            else -> PayrollReadinessStatus.READY
        }
        return PayrollReadinessResult(employeeId, businessEpochDay, resultStatus, issues)
    }

    override suspend fun saveAttendance(id: Long?, draft: AttendanceDraft): Long = attendanceService.save(id, draft)
    override suspend fun recordAttendancePunch(draft: AttendancePunchDraft): Long = attendanceService.recordPunch(draft)
    override fun attendanceEvents(employeeId: Long, limit: Int) = attendanceService.events(employeeId, limit)
    override suspend fun reviewOvertime(command: OvertimeReviewCommand) = attendanceService.reviewOvertime(command)

    override suspend fun attendanceSummaryV2(employeeId: Long, businessEpochDay: Long): DailyAttendanceSummaryV2 =
        attendanceService.dailySummary(employeeId, businessEpochDay)

    override suspend fun approveAttendanceCorrection(correctionId: Long) =
        attendanceService.approveCorrection(correctionId)

    override suspend fun rejectAttendanceCorrection(correctionId: Long, reason: String) =
        attendanceService.rejectCorrection(correctionId, reason)

    override suspend fun attendanceSummary(employeeId: Long, startEpochDay: Long, endEpochDay: Long): AttendanceSummary =
        attendanceService.summary(employeeId, startEpochDay, endEpochDay)

    override suspend fun attendancePayrollAdjustment(
        employeeId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
        policy: AttendancePayrollPolicy,
    ): AttendancePayrollAdjustment = attendanceService.payrollAdjustment(employeeId, startEpochDay, endEpochDay, policy)

    override suspend fun requestLeave(draft: LeaveDraft, requestedBy: String): Long {
        val actor = authorizer.require(Permission.PERSONNEL)
        val valid = draft.validated()
        require(requestedBy.trim().isNotEmpty()) { "ثبت‌کننده درخواست مشخص نیست." }
        return database.withTransaction {
            val idempotencyKey = "leave_request:${valid.commandId}"
            personnel.leaveByIdempotencyKey(idempotencyKey)?.let { existing ->
                if (
                    existing.employeeId != valid.employeeId || existing.startEpochDay != valid.startEpochDay ||
                    existing.endEpochDay != valid.endEpochDay || existing.leaveType != valid.leaveType
                ) {
                    throw BusinessError.IdempotencyConflict(idempotencyKey).asViolation()
                }
                return@withTransaction existing.id
            }
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val employeeStatus = EmploymentStatus.fromStoredValue(employee.status)
            require(employeeStatus in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE)) { "پرسنل در وضعیت قابل مرخصی نیست." }
            require(!personnel.hasLeaveOverlap(valid.employeeId, valid.startEpochDay, valid.endEpochDay)) { "این درخواست با مرخصی دیگری هم‌پوشانی دارد." }
            val now = clock()
            val correlation = CorrelationId.forCommand("leave_request", GlobalId.parse(valid.commandId)).value
            val leaveId = personnel.insertLeave(LeaveEntity(
                employeeId = valid.employeeId, globalId = valid.commandId, idempotencyKey = idempotencyKey,
                startEpochDay = valid.startEpochDay, endEpochDay = valid.endEpochDay,
                daysMicros = SignedLongMath.multiply(
                    SignedLongMath.add(SignedLongMath.subtract(valid.endEpochDay, valid.startEpochDay), 1L),
                    1_000_000L,
                ),
                leaveType = valid.leaveType, status = LeaveStatus.SUBMITTED.storedValue, notes = valid.notes,
                requestedBy = actor.displayName, requestedByActorId = actor.id,
                createdAtEpochMillis = now, updatedAtEpochMillis = now, correlationId = correlation,
            ))
            auditWriter.appendAuthorized(
                authorizer, "SUBMIT", "LEAVE", leaveId, "ثبت درخواست مرخصی",
                now, valid.startEpochDay, valid.notes.ifBlank { "درخواست مرخصی" }, null,
                "status=SUBMITTED;type=${valid.leaveType};from=${valid.startEpochDay};to=${valid.endEpochDay}", correlation,
            )
            leaveId
        }
    }

    override suspend fun grantLeave(draft: LeaveGrantDraft): Long {
        val actor = authorizer.require(Permission.LEAVE_APPROVE)
        val valid = draft.validated()
        return database.withTransaction {
            val key = "leave_grant:${valid.commandId}"
            hr.leaveLedgerByIdempotencyKey(key)?.let { existing ->
                if (
                    existing.employeeId != valid.employeeId || existing.leaveType != valid.leaveType.storedValue ||
                    existing.amountMicros != valid.amountMicros
                ) throw BusinessError.IdempotencyConflict(key).asViolation()
                return@withTransaction existing.id
            }
            personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            val now = clock()
            val correlation = CorrelationId.forCommand("leave_grant", GlobalId.parse(valid.commandId)).value
            val id = hr.insertLeaveLedgerEntry(
                LeaveLedgerEntryEntity(
                    globalId = valid.commandId, idempotencyKey = key, employeeId = valid.employeeId,
                    leaveType = valid.leaveType.storedValue, entryType = LeaveLedgerEntryType.GRANT.name,
                    amountMicros = valid.amountMicros, leaveId = null, businessEpochDay = valid.businessEpochDay,
                    reason = valid.reason, createdByActorId = actor.id, createdAtEpochMillis = now,
                    correlationId = correlation,
                ),
            )
            auditWriter.appendAuthorized(
                authorizer, "GRANT", "LEAVE_BALANCE", id, "اعطای سهمیه مرخصی",
                now, valid.businessEpochDay, valid.reason, null,
                "employeeId=${valid.employeeId};type=${valid.leaveType.storedValue};amountMicros=${valid.amountMicros}", correlation,
            )
            id
        }
    }

    override suspend fun leaveBalance(employeeId: Long, leaveType: LeaveType): LeaveBalance {
        authorizer.require(Permission.PERSONNEL_VIEW)
        val ledger = hr.leaveLedger(employeeId, leaveType.storedValue).map { row ->
            LeaveLedgerEntry(
                id = row.id, employeeId = row.employeeId, leaveType = LeaveType.fromStoredValue(row.leaveType),
                entryType = LeaveLedgerEntryType.valueOf(row.entryType), amountMicros = row.amountMicros,
                leaveId = row.leaveId,
            )
        }
        val pending = personnel.pendingLeavesForBalance(employeeId, leaveType.storedValue).map { row ->
            PendingLeaveUsage(row.employeeId, leaveType, row.daysMicros)
        }
        return LeaveBalanceCalculator.calculate(employeeId, leaveType, ledger, pending)
    }

    override suspend fun reviewLeave(draft: LeaveReviewDraft) {
        val actor = authorizer.require(Permission.LEAVE_APPROVE)
        val valid = draft.validated()
        database.withTransaction {
            val current = personnel.leaveById(valid.leaveId) ?: error("درخواست مرخصی پیدا نشد.")
            require(LeaveStatus.fromStoredValue(current.status) == LeaveStatus.SUBMITTED) { "فقط درخواست در انتظار قابل بررسی است." }
            current.requestedByActorId?.let {
                SegregationOfDuties.requireDifferentActors("LEAVE_APPROVAL", it, actor.id)
            }
            val now = clock()
            if (valid.decision == "APPROVE") {
                require(!personnel.attendanceRangeLocked(current.employeeId, current.startEpochDay, current.endEpochDay)) {
                    "بخشی از بازه مرخصی در دوره حقوق نهایی‌شده قرار دارد و قابل تغییر نیست."
                }
                require(!personnel.hasAttendanceConflict(current.employeeId, current.startEpochDay, current.endEpochDay)) { "در بازه مرخصی، رکورد حضور متعارض وجود دارد." }
                val leaveType = LeaveType.fromStoredValue(current.leaveType)
                if (leaveType in setOf(LeaveType.PAID, LeaveType.ANNUAL, LeaveType.HOURLY)) {
                    val balance = leaveBalance(current.employeeId, leaveType)
                    require(balance.remainingMicros >= 0) { "مانده مرخصی برای تأیید کافی نیست." }
                }
                for (day in current.startEpochDay..current.endEpochDay) {
                    val existing = personnel.attendanceByEmployeeDay(current.employeeId, day)
                    if (existing == null) {
                        personnel.insertAttendance(AttendanceEntity(employeeId=current.employeeId, workEpochDay=day, status="LEAVE", checkInMinute=null, checkOutMinute=null, lateMinutes=0, overtimeMinutes=0, notes="ثبت خودکار از گردش کار مرخصی"))
                    }
                }
                val leaveLedgerKey = "leave_use:${current.id}"
                if (hr.leaveLedgerByIdempotencyKey(leaveLedgerKey) == null) {
                    hr.insertLeaveLedgerEntry(
                        LeaveLedgerEntryEntity(
                            globalId = GlobalId.new().value, idempotencyKey = leaveLedgerKey,
                            employeeId = current.employeeId, leaveType = current.leaveType,
                            entryType = LeaveLedgerEntryType.USE.name, amountMicros = current.daysMicros,
                            leaveId = current.id, businessEpochDay = current.startEpochDay,
                            reason = valid.notes.ifBlank { "تأیید مرخصی" }, createdByActorId = actor.id,
                            createdAtEpochMillis = now, correlationId = current.correlationId,
                        ),
                    )
                }
            }
            check(personnel.updateLeave(current.copy(
                status = if (valid.decision == "APPROVE") LeaveStatus.APPROVED.storedValue else LeaveStatus.REJECTED.storedValue,
                reviewedBy = actor.displayName, reviewedByActorId = actor.id,
                reviewNotes = valid.notes, reviewedAtEpochMillis = now, updatedAtEpochMillis = now,
            )) == 1) { "ثبت نتیجه بررسی مرخصی انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, if (valid.decision == "APPROVE") "APPROVE" else "REJECT", "LEAVE", current.id,
                if (valid.decision == "APPROVE") "تأیید درخواست مرخصی" else "رد درخواست مرخصی",
                now, current.startEpochDay, valid.notes.ifBlank { "بررسی مرخصی" },
                "status=${current.status};requestedByActorId=${current.requestedByActorId}",
                "status=${if (valid.decision == "APPROVE") "APPROVED" else "REJECTED"};reviewedByActorId=${actor.id}",
                current.correlationId,
            )
        }
    }

    override suspend fun cancelLeave(id: Long) {
        authorizer.require(Permission.PERSONNEL)
        database.withTransaction {
            val current = personnel.leaveById(id) ?: error("درخواست مرخصی پیدا نشد.")
            require(LeaveStatus.fromStoredValue(current.status) == LeaveStatus.SUBMITTED) { "فقط درخواست در انتظار قابل لغو است." }
            val now = clock()
            check(personnel.updateLeave(current.copy(status=LeaveStatus.CANCELLED.storedValue, cancelledAtEpochMillis=now, updatedAtEpochMillis=now)) == 1) { "لغو درخواست انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer, "CANCEL", "LEAVE", id, "لغو درخواست مرخصی", now,
                current.startEpochDay, "لغو درخواست", "status=${current.status}", "status=CANCELLED", current.correlationId,
            )
        }
    }

    override suspend fun approveLeave(draft: LeaveDraft): Long {
        throw BusinessError.ApprovalRequired("LEAVE_APPROVAL", 1).asViolation()
    }

    override suspend fun postAdvance(draft: EmployeeAdvanceDraft): Long {
        val actor = authorizer.require(Permission.ADVANCE_CREATE)
        val valid = draft.validated()
        return database.withTransaction {
            val employee = personnel.employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
            require(employee.status == "ACTIVE") { "برای پرسنل غیرفعال نمی‌توان مساعده ثبت کرد." }
            val branchId = employee.branchId?.also { branchResolver.requireActive(it) }
            val now = clock()
            val commandId = GlobalId.new()
            val entryId = requireNotNull(
                treasury.execute(
                    TreasuryCommand.Payment(
                        commandId = commandId, businessEpochDay = valid.advanceEpochDay,
                        correlationId = CorrelationId.forCommand("employee_advance", commandId),
                        businessIntent = TreasuryBusinessIntent.EMPLOYEE_ADVANCE_DISBURSEMENT, sourceId = employee.id,
                        reason = valid.notes.ifBlank { "مساعده پرسنل: ${employee.name}" },
                        accountingScope = if (branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION, branchId = branchId,
                        accountId = valid.paymentMethod.toPersonnelTreasuryAccountId(), channel = valid.paymentMethod,
                        amount = MoneyRial.of(valid.amountRial),
                    ),
                ).journalEntryId,
            ) { "سند خزانه مساعده ایجاد نشد." }
            personnel.insertAdvance(
                EmployeeAdvanceEntity(
                    employeeId = employee.id,
                    amountRial = valid.amountRial,
                    advanceEpochDay = valid.advanceEpochDay,
                    paymentMethod = StoredTreasuryChannelMapper.toPersonnelStoredValue(valid.paymentMethod),
                    journalEntryId = entryId,
                    notes = valid.notes,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    override suspend fun settleAdvance(
        id: Long,
        amountRial: Long,
        paymentMethod: TreasuryChannel,
        settlementEpochDay: Long,
    ) {
        val actor = authorizer.require(Permission.ADVANCE_SETTLE)
        require(amountRial > 0) { "مبلغ تسویه باید بیشتر از صفر باشد." }
        require(paymentMethod in setOf(TreasuryChannel.CASH, TreasuryChannel.BANK)) {
            "advance_settlement_channel_unsupported"
        }
        require(settlementEpochDay > 0) { "تاریخ تسویه مساعده معتبر نیست." }
        database.withTransaction {
            val current = personnel.advanceById(id) ?: error("مساعده پیدا نشد.")
            require(current.status == "OPEN") { "این مساعده قبلاً تسویه شده است." }
            val employee = personnel.employeeById(current.employeeId) ?: error("پرسنل پیدا نشد.")
            val newSettled = SignedLongMath.add(current.settledAmountRial, amountRial)
            require(newSettled <= current.amountRial) { "مبلغ تسویه بیشتر از مانده مساعده است." }
            val branchId = employee.branchId?.also { branchResolver.requireActive(it) }
            val now = clock()
            val commandId = GlobalId.new()
            requireNotNull(
                treasury.execute(
                    TreasuryCommand.Receipt(
                        commandId = commandId, businessEpochDay = settlementEpochDay,
                        correlationId = CorrelationId.forCommand("employee_advance_settlement", commandId),
                        businessIntent = TreasuryBusinessIntent.EMPLOYEE_ADVANCE_REPAYMENT, sourceId = current.id,
                        reason = "بازپرداخت مساعده: ${employee.name}",
                        accountingScope = if (branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION, branchId = branchId,
                        accountId = paymentMethod.toPersonnelTreasuryAccountId(), channel = paymentMethod,
                        amount = MoneyRial.of(amountRial),
                    ),
                ).journalEntryId,
            ) { "سند خزانه بازپرداخت مساعده ایجاد نشد." }
            check(
                personnel.updateAdvance(
                    current.copy(
                        settledAmountRial = newSettled,
                        status = if (newSettled == current.amountRial) "SETTLED" else "OPEN",
                        updatedAtEpochMillis = now,
                    ),
                ) == 1,
            ) { "تسویه مساعده انجام نشد." }
        }
    }

    private suspend fun savePrivateProfile(
        employeeId: Long,
        draft: EmployeeDraft,
        now: Long,
        actorId: Long,
        legacyCurrent: EmployeeEntity?,
    ) {
        val current = hr.privateProfile(employeeId)
        hr.upsertPrivateProfile(
            EmployeePrivateProfileEntity(
                employeeId = employeeId,
                nationalId = draft.nationalId.takeIf { it.isNotBlank() } ?: current?.nationalId ?: legacyCurrent?.nationalId,
                insuranceNumber = draft.insuranceNumber.takeIf { it.isNotBlank() } ?: current?.insuranceNumber ?: legacyCurrent?.insuranceNumber,
                bankName = current?.bankName,
                bankAccountLast4 = draft.bankCard.takeIf { it.isNotBlank() }?.takeLast(4)
                    ?: current?.bankAccountLast4
                    ?: legacyCurrent?.bankCard?.takeLast(4),
                ibanLast4 = current?.ibanLast4,
                accountHolder = current?.accountHolder,
                emergencyContact = draft.emergencyContact,
                createdAtEpochMillis = current?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                updatedByActorId = actorId,
            ),
        )
    }

    /**
     * Human-readable identity allocated inside the employee creation transaction. The unique
     * database index remains the final concurrency safeguard; scanning forward also avoids a
     * collision with an administrator-supplied code in the reserved legacy format.
     */
    private suspend fun newEmployeeCode(): String = numbering.next(DocumentNumberType.EMPLOYEE)


}

private fun EmployeeContractEntity.toRecord() = EmployeeContractRecord(
    id = id,
    employeeId = employeeId,
    contractType = contractType,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    baseSalaryRial = baseSalaryRial,
    dailyWorkMinutes = dailyWorkMinutes,
    weeklyWorkDays = weeklyWorkDays,
    status = status,
    notes = notes,
)

private fun EmploymentContractVersionEntity.toRecord() = EmployeeContractRecord(
    id = id,
    employeeId = employeeId,
    contractType = contractType,
    startEpochDay = effectiveFromEpochDay,
    endEpochDay = effectiveToEpochDay,
    baseSalaryRial = baseSalaryRial,
    dailyWorkMinutes = standardDailyMinutes,
    weeklyWorkDays = if (standardDailyMinutes > 0) standardWeeklyMinutes / standardDailyMinutes else 0,
    status = status,
    notes = notes,
    contractNumber = contractNumber,
    versionNo = versionNo,
    replacesContractId = replacesContractId,
    payrollPolicyId = payrollPolicyId,
    workScheduleId = workScheduleId,
    defaultShiftTemplateId = defaultShiftTemplateId,
    jobTitleSnapshot = jobTitleSnapshot,
    departmentSnapshot = departmentSnapshot,
    branchSnapshot = branchSnapshot,
    typedStatus = EmploymentContractStatus.fromStoredValue(status),
)

private fun EmploymentContractVersionEntity.toDomain() = EmploymentContractVersion(
    id = id,
    employeeId = employeeId,
    contractNumber = contractNumber,
    versionNo = versionNo,
    replacesContractId = replacesContractId,
    contractType = EmploymentContractType.fromStoredValue(contractType),
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

private fun EmploymentAssignmentEntity.toDomain() = EmploymentAssignment(
    id = id,
    employeeId = employeeId,
    effectiveFromEpochDay = effectiveFromEpochDay,
    effectiveToEpochDay = effectiveToEpochDay,
    jobTitle = jobTitle,
    department = department,
    branchName = branchName,
    locationId = locationId,
    branchId = branchId,
    managerId = managerId,
    createdByActorId = createdByActorId,
)

private fun EmployeePrivateProfileEntity.toDomain() = EmployeePrivateProfile(
    employeeId = employeeId,
    nationalId = nationalId,
    insuranceNumber = insuranceNumber,
    bankName = bankName,
    bankAccountLast4 = bankAccountLast4,
    ibanLast4 = ibanLast4,
    accountHolder = accountHolder,
    emergencyContact = emergencyContact,
)

private fun String.toContractType(): EmploymentContractType = when (trim().uppercase()) {
    "PERMANENT", "FULL_TIME", "دائم" -> EmploymentContractType.PERMANENT
    "FIXED_TERM", "TEMPORARY", "موقت" -> EmploymentContractType.FIXED_TERM
    "PART_TIME", "پاره‌وقت" -> EmploymentContractType.PART_TIME
    "HOURLY", "ساعتی" -> EmploymentContractType.HOURLY
    "PROBATION", "آزمایشی" -> EmploymentContractType.PROBATION
    "OTHER", "سایر" -> EmploymentContractType.OTHER
    else -> EmploymentContractType.OTHER
}

private fun String?.maskedLastFour(): String =
    this?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }?.let { "••••$it" }.orEmpty()

private fun EmployeeAdvanceEntity.toRecord(): EmployeeAdvanceRecord {
    val remaining = SignedLongMath.subtract(amountRial, settledAmountRial)
    return EmployeeAdvanceRecord(
        id = id,
        employeeId = employeeId,
        amountRial = amountRial,
        settledAmountRial = settledAmountRial,
        remainingAmountRial = remaining,
        advanceEpochDay = advanceEpochDay,
        paymentMethod = StoredTreasuryChannelMapper.fromPersonnelStoredValue(paymentMethod),
        journalEntryId = journalEntryId,
        status = status,
        notes = notes,
    )
}


private fun OvertimeApprovalEntity.toRecord() = OvertimeApprovalRecord(
    id = id,
    employeeId = employeeId,
    businessEpochDay = businessEpochDay,
    rawMinutes = rawMinutes,
    approvedMinutes = approvedMinutes,
    rejectedMinutes = rejectedMinutes,
    status = status,
    reason = reason,
    requestedByActorId = requestedByActorId,
    reviewedByActorId = reviewedByActorId,
    requestedAtEpochMillis = requestedAtEpochMillis,
    reviewedAtEpochMillis = reviewedAtEpochMillis,
)

private fun AttendanceEntity.toRecord() = AttendanceRecord(
    id = id, employeeId = employeeId, workEpochDay = workEpochDay, status = status,
    checkInMinute = checkInMinute, checkOutMinute = checkOutMinute,
    workedMinutes = if (checkInMinute != null && checkOutMinute != null) ((if (checkOutMinute <= checkInMinute) checkOutMinute + 1440 else checkOutMinute) - checkInMinute).coerceAtLeast(0) else 0,
    lateMinutes = lateMinutes,
    rawLateMinutes = rawLateMinutes,
    payableLateMinutes = payableLateMinutes,
    earlyLeaveMinutes = earlyLeaveMinutes,
    rawOvertimeMinutes = rawOvertimeMinutes,
    approvedOvertimeMinutes = approvedOvertimeMinutes,
    overtimeMinutes = overtimeMinutes,
    notes = notes,
)

private fun TreasuryChannel.toPersonnelTreasuryAccountId(): TreasuryAccountId = when (this) {
    TreasuryChannel.CASH -> TreasuryAccountId.parse("cash_main")
    TreasuryChannel.BANK -> TreasuryAccountId.parse("bank_main")
    TreasuryChannel.CARD -> TreasuryAccountId.parse("card_terminal")
    TreasuryChannel.TRANSFER -> TreasuryAccountId.parse("bank_main")
}


private fun LeaveEntity.toRecord() = LeaveRecord(
    id=id, employeeId=employeeId, startEpochDay=startEpochDay, endEpochDay=endEpochDay, daysMicros=daysMicros,
    leaveType=leaveType, status=status, notes=notes, requestedBy=requestedBy, reviewedBy=reviewedBy,
    reviewNotes=reviewNotes, reviewedAtEpochMillis=reviewedAtEpochMillis,
    typedStatus=LeaveStatus.fromStoredValue(status), typedLeaveType=LeaveType.fromStoredValue(leaveType),
    correlationId=correlationId,
)
