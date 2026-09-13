package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.FixedPointRounding
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AttendanceCorrectionEntity
import ir.restaurant.management.data.db.AttendanceEntity
import ir.restaurant.management.data.db.AttendanceEventEntity
import ir.restaurant.management.data.db.EmployeeAdvanceEntity
import ir.restaurant.management.data.db.EmployeeEntity
import ir.restaurant.management.data.db.EmploymentContractVersionEntity
import ir.restaurant.management.data.db.HrPayrollCommandReceiptEntity
import ir.restaurant.management.data.db.LeaveEntity
import ir.restaurant.management.data.db.PayrollAdvanceAllocationV2Entity
import ir.restaurant.management.data.db.PayrollApprovalEventEntity
import ir.restaurant.management.data.db.PayrollBatchEntity
import ir.restaurant.management.data.db.PayrollBatchDashboardRow
import ir.restaurant.management.data.db.EmployeeTimelineRow
import ir.restaurant.management.data.db.PayrollComponentEntity
import ir.restaurant.management.data.db.PayrollExceptionEntity
import ir.restaurant.management.data.db.PayrollManualAdjustmentEntity
import ir.restaurant.management.data.db.PayrollPaymentEntity
import ir.restaurant.management.data.db.PayrollPayslipEntity
import ir.restaurant.management.data.db.PayrollPeriodEntity
import ir.restaurant.management.data.db.PayrollPolicyEntity
import ir.restaurant.management.data.db.PayrollSnapshotEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.AccountingReversalCommand
import ir.restaurant.management.domain.accounting.JournalStatus
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.audit.AuditService
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.personnel.AdvanceDeductionAllocation
import ir.restaurant.management.domain.personnel.AdvanceDeductionAllocator
import ir.restaurant.management.domain.personnel.ApproveManualAdjustmentCommand
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceAggregationPolicy
import ir.restaurant.management.domain.personnel.AttendanceAnomaly
import ir.restaurant.management.domain.personnel.AttendanceCorrectionCodec
import ir.restaurant.management.domain.personnel.AttendanceEvent
import ir.restaurant.management.domain.personnel.AttendanceEventAggregator
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendanceSource
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.ClosePayrollPeriodCommand
import ir.restaurant.management.domain.personnel.DailyAttendanceStatus
import ir.restaurant.management.domain.personnel.EffectiveContractResolver
import ir.restaurant.management.domain.personnel.EmploymentContractStatus
import ir.restaurant.management.domain.personnel.EmploymentContractType
import ir.restaurant.management.domain.personnel.EmploymentContractVersion
import ir.restaurant.management.domain.personnel.EmploymentStatus
import ir.restaurant.management.domain.personnel.EmployeeTimelineItem
import ir.restaurant.management.domain.personnel.HrPayrollCommandService
import ir.restaurant.management.domain.personnel.LeaveType
import ir.restaurant.management.domain.personnel.ManualAdjustmentStatus
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentCommand
import ir.restaurant.management.domain.personnel.ManualPayrollAdjustmentRecordV2
import ir.restaurant.management.domain.personnel.OpenAdvanceBalance
import ir.restaurant.management.domain.personnel.PayPayslipCommand
import ir.restaurant.management.domain.personnel.PayrollAccountingPlanner
import ir.restaurant.management.domain.personnel.PayrollApprovalRecordV2
import ir.restaurant.management.domain.personnel.PayrollBatchCalculationOutcome
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollBatchRecordV2
import ir.restaurant.management.domain.personnel.PayrollBatchStateMachine
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollCalculationCommand
import ir.restaurant.management.domain.personnel.PayrollCalculationResultV2
import ir.restaurant.management.domain.personnel.PayrollCalculationService
import ir.restaurant.management.domain.personnel.PayrollComponentDirection
import ir.restaurant.management.domain.personnel.PayrollComponentDraftV2
import ir.restaurant.management.domain.personnel.PayrollComponentSourceType
import ir.restaurant.management.domain.personnel.PayrollComponentType
import ir.restaurant.management.domain.personnel.PayrollDocumentSource
import ir.restaurant.management.domain.personnel.PayrollExceptionRecord
import ir.restaurant.management.domain.personnel.PayrollInputSnapshot
import ir.restaurant.management.domain.personnel.PayrollPaymentRecordV2
import ir.restaurant.management.domain.personnel.PayrollPaymentLedger
import ir.restaurant.management.domain.personnel.PayrollPaymentLedgerEntry
import ir.restaurant.management.domain.personnel.PayrollPaymentStatus
import ir.restaurant.management.domain.personnel.PayrollPayslipDetailV2
import ir.restaurant.management.domain.personnel.PayrollPayslipRecordV2
import ir.restaurant.management.domain.personnel.PayrollPayslipStateMachine
import ir.restaurant.management.domain.personnel.PayrollPayslipStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodRecordV2
import ir.restaurant.management.domain.personnel.PayrollPeriodStateMachine
import ir.restaurant.management.domain.personnel.PayrollPeriodStatus
import ir.restaurant.management.domain.personnel.ReopenPayrollPeriodCommand
import ir.restaurant.management.domain.personnel.ReversePayrollPaymentCommand
import ir.restaurant.management.domain.personnel.ReversePayslipCommandV2
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.SegregationOfDuties
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Transactional HR/Payroll 2.0 application service. Mutable HR records are read only while a new
 * document is being calculated; every approved financial fact is then served from its snapshot and
 * append-only ledgers.
 */
class LocalHrPayrollService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val accountingPosting: AccountingPostingService,
    private val treasury: TreasuryService,
    private val audit: AuditService = LocalAuditEventWriter(database),
    private val clock: () -> Long = System::currentTimeMillis,
) : HrPayrollCommandService {
    private val hr = database.hrPayrollDao()
    private val personnel = database.personnelDao()
    private val paymentPosting = PayrollPaymentPostingService(database, authorizer, treasury, audit, clock)
    private val calculationPreparation = PayrollBatchPreparationService(database)
    private val branchResolver = CanonicalBranchResolver(database)
    private val scheduling = PersonnelSchedulingService(database, authorizer, LocalAuditEventWriter(database), clock)

    override val periods: Flow<List<PayrollPeriodRecordV2>> = authorizedFlow(Permission.PAYROLL_VIEW_ALL) {
        hr.observePayrollPeriods().map { rows -> rows.map(PayrollPeriodEntity::toRecord) }
    }

    override val batches: Flow<List<PayrollBatchRecordV2>> = authorizedFlow(Permission.PAYROLL_VIEW_ALL) {
        hr.observePayrollBatchDashboard().map { rows -> rows.map(PayrollBatchDashboardRow::toRecord) }
    }

    override fun employeePayslips(employeeId: Long, limit: Int, offset: Int): Flow<List<PayrollPayslipRecordV2>> {
        require(employeeId > 0 && limit in 1..200 && offset >= 0)
        return authorizedFlow(Permission.PAYROLL_VIEW_ALL) {
            hr.observeEmployeePayslipPage(employeeId, limit, offset).map { rows -> rows.map(PayrollPayslipEntity::toRecord) }
        }
    }

    override fun employeeTimeline(employeeId: Long, limit: Int, offset: Int): Flow<List<EmployeeTimelineItem>> {
        require(employeeId > 0 && limit in 1..200 && offset >= 0)
        return authorizedFlow(Permission.PAYROLL_VIEW_ALL) {
            hr.observeEmployeeTimeline(employeeId, limit, offset).map { rows -> rows.map(EmployeeTimelineRow::toDomain) }
        }
    }

    override suspend fun payslipDetail(payslipId: Long): PayrollPayslipDetailV2 {
        authorizer.require(Permission.PAYROLL_VIEW_ALL)
        return database.withTransaction {
            val payslip = hr.payrollPayslip(payslipId) ?: missing("PAYROLL_PAYSLIP", payslipId)
            val period = hr.payrollPeriod(payslip.periodId) ?: missing("PAYROLL_PERIOD", payslip.periodId)
            val batch = hr.payrollBatch(payslip.batchId) ?: missing("PAYROLL_BATCH", payslip.batchId)
            val snapshot = hr.payrollSnapshot(payslip.id)?.toDomainOrNull()
            val components = hr.payrollComponents(payslip.id).map(PayrollComponentEntity::toDraft)
            val payments = hr.payrollPayments(payslip.id).map(PayrollPaymentEntity::toRecord)
            val approvals = hr.payrollApprovalHistory(payslip.id, payslip.batchId).map {
                PayrollApprovalRecordV2(
                    id = it.id,
                    eventType = it.eventType,
                    fromStatus = it.fromStatus,
                    toStatus = it.toStatus,
                    actorId = it.actorId,
                    reason = it.reason,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                )
            }
            val allocations = hr.payslipAdvanceAllocations(payslip.id)
                .filter { it.status == "ALLOCATED" }
                .map { AdvanceDeductionAllocation(it.advanceId, it.amountRial) }
            PayrollPayslipDetailV2(
                payslip = payslip.toRecord(),
                period = period.toRecord(),
                batch = batch.toRecord(),
                snapshot = snapshot,
                components = components,
                payments = payments,
                approvalHistory = approvals,
                advanceAllocations = allocations,
                accrualJournalEntryId = payslip.accrualJournalEntryId,
                reversalJournalEntryId = payslip.reversalJournalEntryId,
            )
        }
    }

    override suspend fun manualAdjustments(periodId: Long): List<ManualPayrollAdjustmentRecordV2> {
        authorizer.require(Permission.PAYROLL_VIEW_ALL)
        require(periodId > 0)
        return hr.manualAdjustmentsForPeriod(periodId).map(PayrollManualAdjustmentEntity::toRecord)
    }

    override suspend fun openPeriod(draft: PayrollPeriodDraftV2): Long {
        val actor = authorizer.require(Permission.PAYROLL_CREATE)
        val valid = draft.validated()
        val key = receiptKey("OPEN_PERIOD", valid.commandId)
        val payload = hash("${valid.periodKey}|${valid.startEpochDay}|${valid.endEpochDay}|${valid.paymentDueEpochDay}")
        val correlation = correlation("payroll_period", valid.commandId)
        return database.withTransaction {
            replay(key, "OPEN_PERIOD", payload)?.let { return@withTransaction it.resultEntityId }
            hr.payrollPeriodByKey(valid.periodKey)?.let {
                throw BusinessError.DuplicateDocument("PAYROLL_PERIOD", valid.periodKey).asViolation()
            }
            val now = clock()
            val id = hr.insertPayrollPeriod(
                PayrollPeriodEntity(
                    periodKey = valid.periodKey,
                    startEpochDay = valid.startEpochDay,
                    endEpochDay = valid.endEpochDay,
                    paymentDueEpochDay = valid.paymentDueEpochDay,
                    status = PayrollPeriodStatus.OPEN.storedValue,
                    openedByActorId = actor.id,
                    openedAtEpochMillis = now,
                    closedAtEpochMillis = null,
                    reopenedAtEpochMillis = null,
                    rowVersion = 0,
                    source = PayrollDocumentSource.NATIVE.storedValue,
                ),
            )
            recordAudit(
                actor.id,
                actor.displayName,
                "OPEN",
                "PAYROLL_PERIOD",
                id,
                valid.startEpochDay,
                "ایجاد دوره حقوق ${valid.periodKey}",
                "ایجاد دوره حقوق",
                correlation.value,
                null,
                "status=OPEN;periodKey=${valid.periodKey}",
            )
            receipt(key, "OPEN_PERIOD", payload, "PAYROLL_PERIOD", id, "OPEN", actor.id, now, correlation.value)
            id
        }
    }

    override suspend fun closePeriod(command: ClosePayrollPeriodCommand) {
        val actor = authorizer.require(Permission.PAYROLL_APPROVE)
        val valid = command.validated()
        val key = receiptKey("CLOSE_PERIOD", valid.commandId)
        val payload = hash("${valid.periodId}|${valid.reason}")
        val correlation = correlation("payroll_period_close", valid.commandId)
        database.withTransaction {
            replay(key, "CLOSE_PERIOD", payload)?.let { return@withTransaction }
            val period = hr.payrollPeriod(valid.periodId) ?: missing("PAYROLL_PERIOD", valid.periodId)
            val from = PayrollPeriodStatus.fromStoredValue(period.status)
            val batches = hr.payrollBatchesForPeriod(period.id)
            val nonTerminal = batches.filter {
                PayrollBatchStatus.fromStoredValue(it.status) !in setOf(
                    PayrollBatchStatus.PAID,
                    PayrollBatchStatus.REVERSED,
                    PayrollBatchStatus.CANCELLED,
                    PayrollBatchStatus.LEGACY,
                )
            }
            if (nonTerminal.isNotEmpty()) {
                throw BusinessError.InvalidBusinessState("PAYROLL_PERIOD", "UNSETTLED_BATCHES").asViolation()
            }
            PayrollPeriodStateMachine.requireTransition(from, PayrollPeriodStatus.CLOSED)
            val now = clock()
            optimistic(
                hr.transitionPayrollPeriod(
                    period.id,
                    from.storedValue,
                    PayrollPeriodStatus.CLOSED.storedValue,
                    period.rowVersion,
                    now,
                    null,
                ),
                "PAYROLL_PERIOD",
                period.id,
            )
            recordAudit(actor.id, actor.displayName, "CLOSE", "PAYROLL_PERIOD", period.id, period.endEpochDay, valid.reason, valid.reason, correlation.value, "status=${from.storedValue}", "status=CLOSED")
            receipt(key, "CLOSE_PERIOD", payload, "PAYROLL_PERIOD", period.id, "CLOSED", actor.id, now, correlation.value)
        }
    }

    override suspend fun reopenPeriod(command: ReopenPayrollPeriodCommand) {
        val actor = authorizer.require(Permission.PAYROLL_APPROVE)
        val valid = command.validated()
        val key = receiptKey("REOPEN_PERIOD", valid.commandId)
        val payload = hash("${valid.periodId}|${valid.reason}")
        val correlation = correlation("payroll_period_reopen", valid.commandId)
        database.withTransaction {
            replay(key, "REOPEN_PERIOD", payload)?.let { return@withTransaction }
            val period = hr.payrollPeriod(valid.periodId) ?: missing("PAYROLL_PERIOD", valid.periodId)
            val from = PayrollPeriodStatus.fromStoredValue(period.status)
            PayrollPeriodStateMachine.requireTransition(from, PayrollPeriodStatus.REOPENED)
            val now = clock()
            optimistic(
                hr.transitionPayrollPeriod(
                    period.id,
                    from.storedValue,
                    PayrollPeriodStatus.REOPENED.storedValue,
                    period.rowVersion,
                    null,
                    now,
                ),
                "PAYROLL_PERIOD",
                period.id,
            )
            recordAudit(actor.id, actor.displayName, "REOPEN", "PAYROLL_PERIOD", period.id, period.endEpochDay, valid.reason, valid.reason, correlation.value, "status=CLOSED", "status=REOPENED")
            receipt(key, "REOPEN_PERIOD", payload, "PAYROLL_PERIOD", period.id, "REOPENED", actor.id, now, correlation.value)
        }
    }

    override suspend fun createBatch(draft: PayrollBatchDraftV2): Long {
        val actor = authorizer.require(Permission.PAYROLL_CREATE)
        val valid = draft.validated()
        val key = receiptKey("CREATE_BATCH", valid.commandId)
        val payload = hash("${valid.periodId}|${valid.scope}|${valid.branchId ?: valid.branchName}|${valid.department}|${valid.notes}")
        val correlation = correlation("payroll_batch", valid.commandId)
        return database.withTransaction {
            replay(key, "CREATE_BATCH", payload)?.let { return@withTransaction it.resultEntityId }
            val period = hr.payrollPeriod(valid.periodId) ?: missing("PAYROLL_PERIOD", valid.periodId)
            val periodStatus = PayrollPeriodStatus.fromStoredValue(period.status)
            if (periodStatus !in setOf(PayrollPeriodStatus.OPEN, PayrollPeriodStatus.REOPENED, PayrollPeriodStatus.CALCULATING)) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }
            val resolvedBranch = when (valid.scope) {
                "BRANCH" -> branchResolver.resolveRequired(valid.branchId, valid.branchName, "شعبه معتبر برای این لیست حقوق مشخص نشده است.")
                else -> null
            }
            val now = clock()
            val documentNumber = "PAY-${period.periodKey}-${valid.commandId.take(8).uppercase()}"
            val id = hr.insertPayrollBatch(
                PayrollBatchEntity(
                    documentNumber = documentNumber,
                    idempotencyKey = key,
                    periodId = period.id,
                    scope = valid.scope,
                    branchName = resolvedBranch?.name,
                    branchId = resolvedBranch?.id,
                    department = valid.department,
                    status = PayrollBatchStatus.DRAFT.storedValue,
                    createdByActorId = actor.id,
                    calculatedByActorId = null,
                    calculatedAtEpochMillis = null,
                    reviewedByActorId = null,
                    reviewedAtEpochMillis = null,
                    approvedByActorId = null,
                    approvedAtEpochMillis = null,
                    correlationId = correlation.value,
                    notes = valid.notes,
                    rowVersion = 0,
                    accrualJournalEntryId = null,
                    reversalJournalEntryId = null,
                    source = PayrollDocumentSource.NATIVE.storedValue,
                ),
            )
            recordAudit(actor.id, actor.displayName, "CREATE", "PAYROLL_BATCH", id, period.endEpochDay, valid.notes.ifBlank { "ایجاد دسته حقوق" }, "ایجاد دسته حقوق $documentNumber", correlation.value, null, "status=DRAFT;scope=${valid.scope}")
            receipt(key, "CREATE_BATCH", payload, "PAYROLL_BATCH", id, "DRAFT", actor.id, now, correlation.value)
            id
        }
    }

    override suspend fun calculateBatch(command: CalculatePayrollBatchCommand): PayrollBatchCalculationOutcome {
        val actor = authorizer.require(Permission.PAYROLL_CALCULATE)
        val valid = command.validated()
        val key = receiptKey("CALCULATE_BATCH", valid.commandId)
        val payload = hash(calculatePayload(valid))
        val correlation = correlation("payroll_calculate", valid.commandId)
        return database.withTransaction {
            replay(key, "CALCULATE_BATCH", payload)?.let {
                return@withTransaction PayrollBatchCalculationOutcome(
                    batchId = valid.batchId,
                    payslipIds = hr.batchPayslips(valid.batchId).map { row -> row.id },
                    exceptions = hr.unresolvedPayrollExceptions(valid.batchId).map(PayrollExceptionEntity::toRecord),
                    idempotentReplay = true,
                )
            }
            val batch = hr.payrollBatch(valid.batchId) ?: missing("PAYROLL_BATCH", valid.batchId)
            if (PayrollBatchStatus.fromStoredValue(batch.status) != PayrollBatchStatus.DRAFT) {
                throw BusinessError.PayrollAlreadyCalculated(batch.id).asViolation()
            }
            val period = hr.payrollPeriod(batch.periodId) ?: missing("PAYROLL_PERIOD", batch.periodId)
            val periodStatus = PayrollPeriodStatus.fromStoredValue(period.status)
            if (periodStatus !in setOf(PayrollPeriodStatus.OPEN, PayrollPeriodStatus.REOPENED, PayrollPeriodStatus.CALCULATING)) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }

            valid.employeeIds.distinct().forEach { employeeId ->
                scheduling.materializeRange(employeeId, period.startEpochDay, period.endEpochDay)
            }
            val preparation = calculationPreparation.prepare(valid, batch, period, actor.id)
            val now = clock()
            hr.clearUnresolvedPayrollExceptions(batch.id)
            if (preparation.exceptions.isNotEmpty()) {
                hr.insertPayrollExceptions(preparation.exceptions.map { it.toEntity(batch.id, now) })
            }
            if (preparation.exceptions.any { it.blocking }) {
                recordAudit(actor.id, actor.displayName, "CALCULATE_BLOCKED", "PAYROLL_BATCH", batch.id, period.endEpochDay, "وجود خطاهای مسدودکننده", "محاسبه دسته حقوق متوقف شد", correlation.value, "status=DRAFT", "exceptions=${preparation.exceptions.size}")
                receipt(key, "CALCULATE_BATCH", payload, "PAYROLL_BATCH", batch.id, "BLOCKED", actor.id, now, correlation.value)
                return@withTransaction PayrollBatchCalculationOutcome(batch.id, emptyList(), preparation.exceptions, false)
            }

            val payslipIds = preparation.payslips.map { prepared ->
                val payslipCorrelation = CorrelationId.parse("${correlation.value}:employee:${prepared.employee.id}")
                val payslipId = hr.insertPayrollPayslip(
                    PayrollPayslipEntity(
                        globalId = GlobalId.new().value,
                        batchId = batch.id,
                        periodId = period.id,
                        employeeId = prepared.employee.id,
                        employeeCodeSnapshot = requireNotNull(prepared.employee.employeeCode).trim(),
                        employeeNameSnapshot = prepared.employee.displayName.ifBlank { prepared.employee.name },
                        revisionNo = prepared.revisionNo,
                        replacesPayslipId = prepared.replacesPayslipId,
                        legacyPayrollRunId = null,
                        contractId = prepared.contract.id,
                        status = PayrollPayslipStatus.CALCULATED.storedValue,
                        grossPayRial = prepared.result.grossPayRial,
                        totalDeductionsRial = prepared.result.totalDeductionsRial,
                        netPayRial = prepared.result.netPayRial,
                        paidAmountRial = 0,
                        remainingAmountRial = prepared.result.netPayRial,
                        componentDetailComplete = true,
                        calculatedAtEpochMillis = now,
                        approvedAtEpochMillis = null,
                        paidAtEpochMillis = null,
                        correlationId = payslipCorrelation.value,
                        source = PayrollDocumentSource.NATIVE.storedValue,
                        rowVersion = 0,
                        accrualJournalEntryId = null,
                        reversalJournalEntryId = null,
                        reversalReason = null,
                        reversalEpochDay = null,
                        reversedAtEpochMillis = null,
                    ),
                )
                val snapshotHash = snapshotHash(
                    prepared.result.snapshot,
                    prepared.result.components,
                    prepared.traceParameters,
                    prepared.result.grossPayRial,
                    prepared.result.totalDeductionsRial,
                    prepared.result.netPayRial,
                )
                hr.insertPayrollSnapshot(prepared.toSnapshotEntity(payslipId, snapshotHash, now))
                hr.insertPayrollComponents(
                    prepared.result.components.map { component -> component.toEntity(payslipId, now) },
                )
                if (prepared.manualAdjustmentIds.isNotEmpty()) {
                    optimistic(
                        hr.consumeManualAdjustments(prepared.manualAdjustmentIds, payslipId)
                            .takeIf { it == prepared.manualAdjustmentIds.size }
                            ?: 0,
                        "PAYROLL_MANUAL_ADJUSTMENT",
                        payslipId,
                    )
                }
                payslipId
            }
            optimistic(
                hr.transitionPayrollBatch(
                    id = batch.id,
                    fromStatus = PayrollBatchStatus.DRAFT.storedValue,
                    toStatus = PayrollBatchStatus.CALCULATED.storedValue,
                    expectedVersion = batch.rowVersion,
                    calculatedBy = actor.id,
                    calculatedAt = now,
                ),
                "PAYROLL_BATCH",
                batch.id,
            )
            if (periodStatus in setOf(PayrollPeriodStatus.OPEN, PayrollPeriodStatus.REOPENED)) {
                PayrollPeriodStateMachine.requireTransition(periodStatus, PayrollPeriodStatus.CALCULATING)
                optimistic(
                    hr.transitionPayrollPeriod(
                        period.id,
                        periodStatus.storedValue,
                        PayrollPeriodStatus.CALCULATING.storedValue,
                        period.rowVersion,
                        null,
                        null,
                    ),
                    "PAYROLL_PERIOD",
                    period.id,
                )
            }
            hr.insertApprovalEvent(
                approvalEvent(batch.id, null, "CALCULATE", "DRAFT", "CALCULATED", actor.id, "محاسبه قطعی Snapshot", null, now, correlation.value),
            )
            recordAudit(actor.id, actor.displayName, "CALCULATE", "PAYROLL_BATCH", batch.id, period.endEpochDay, "محاسبه حقوق", "محاسبه ${payslipIds.size} فیش حقوق", correlation.value, "status=DRAFT", "status=CALCULATED;payslips=${payslipIds.size}")
            receipt(key, "CALCULATE_BATCH", payload, "PAYROLL_BATCH", batch.id, payslipIds.joinToString(","), actor.id, now, correlation.value)
            PayrollBatchCalculationOutcome(batch.id, payslipIds, preparation.exceptions, false)
        }
    }

    override suspend fun submitBatchForReview(command: ReviewPayrollBatchCommand) {
        val actor = authorizer.require(Permission.PAYROLL_REVIEW)
        val valid = command.validated()
        val key = receiptKey("REVIEW_BATCH", valid.commandId)
        val payload = hash("${valid.batchId}|${valid.note}")
        val correlation = correlation("payroll_review", valid.commandId)
        database.withTransaction {
            replay(key, "REVIEW_BATCH", payload)?.let { return@withTransaction }
            val batch = hr.payrollBatch(valid.batchId) ?: missing("PAYROLL_BATCH", valid.batchId)
            val from = PayrollBatchStatus.fromStoredValue(batch.status)
            PayrollBatchStateMachine.requireTransition(from, PayrollBatchStatus.UNDER_REVIEW)
            val payslips = hr.batchPayslips(batch.id)
            require(payslips.isNotEmpty()) { "payroll_batch_has_no_payslips" }
            val now = clock()
            payslips.forEach { payslip ->
                val payslipFrom = PayrollPayslipStatus.fromStoredValue(payslip.status)
                PayrollPayslipStateMachine.requireTransition(payslipFrom, PayrollPayslipStatus.UNDER_REVIEW)
                optimistic(
                    hr.transitionPayrollPayslip(payslip.id, payslipFrom.storedValue, PayrollPayslipStatus.UNDER_REVIEW.storedValue, payslip.rowVersion),
                    "PAYROLL_PAYSLIP",
                    payslip.id,
                )
            }
            optimistic(
                hr.transitionPayrollBatch(
                    batch.id,
                    from.storedValue,
                    PayrollBatchStatus.UNDER_REVIEW.storedValue,
                    batch.rowVersion,
                    reviewedBy = actor.id,
                    reviewedAt = now,
                ),
                "PAYROLL_BATCH",
                batch.id,
            )
            val period = hr.payrollPeriod(batch.periodId) ?: missing("PAYROLL_PERIOD", batch.periodId)
            val periodFrom = PayrollPeriodStatus.fromStoredValue(period.status)
            if (periodFrom == PayrollPeriodStatus.CALCULATING) {
                PayrollPeriodStateMachine.requireTransition(periodFrom, PayrollPeriodStatus.REVIEW)
                optimistic(hr.transitionPayrollPeriod(period.id, periodFrom.storedValue, PayrollPeriodStatus.REVIEW.storedValue, period.rowVersion, null, null), "PAYROLL_PERIOD", period.id)
            }
            hr.insertApprovalEvent(approvalEvent(batch.id, null, "REVIEW", from.storedValue, "UNDER_REVIEW", actor.id, valid.note, null, now, correlation.value))
            recordAudit(actor.id, actor.displayName, "REVIEW", "PAYROLL_BATCH", batch.id, period.endEpochDay, valid.note, "ارسال دسته حقوق برای تأیید", correlation.value, "status=${from.storedValue}", "status=UNDER_REVIEW")
            receipt(key, "REVIEW_BATCH", payload, "PAYROLL_BATCH", batch.id, "UNDER_REVIEW", actor.id, now, correlation.value)
        }
    }

    override suspend fun approveBatch(command: ApprovePayrollBatchCommand) {
        val actor = authorizer.require(Permission.PAYROLL_APPROVE)
        val valid = command.validated()
        val key = receiptKey("APPROVE_BATCH", valid.commandId)
        val payload = hash("${valid.batchId}|${valid.reason}")
        val correlation = correlation("payroll_approve", valid.commandId)
        database.withTransaction {
            replay(key, "APPROVE_BATCH", payload)?.let { return@withTransaction }
            val batch = hr.payrollBatch(valid.batchId) ?: missing("PAYROLL_BATCH", valid.batchId)
            val batchFrom = PayrollBatchStatus.fromStoredValue(batch.status)
            if (batchFrom in setOf(PayrollBatchStatus.APPROVED, PayrollBatchStatus.PAYMENT_PENDING, PayrollBatchStatus.PARTIALLY_PAID, PayrollBatchStatus.PAID)) {
                throw BusinessError.PayrollAlreadyApproved(batch.id).asViolation()
            }
            PayrollBatchStateMachine.requireTransition(batchFrom, PayrollBatchStatus.APPROVED)
            batch.createdByActorId?.let { SegregationOfDuties.requireDifferentActors("PAYROLL_CREATOR_APPROVAL", it, actor.id) }
            batch.calculatedByActorId?.let { SegregationOfDuties.requireDifferentActors("PAYROLL_CALCULATOR_APPROVAL", it, actor.id) }
            if (hr.unresolvedPayrollExceptions(batch.id).any { it.blocking }) {
                throw BusinessError.ApprovalRequired("PAYROLL_EXCEPTIONS", 1).asViolation()
            }
            val period = hr.payrollPeriod(batch.periodId) ?: missing("PAYROLL_PERIOD", batch.periodId)
            if (PayrollPeriodStatus.fromStoredValue(period.status) != PayrollPeriodStatus.REVIEW) {
                throw BusinessError.InvalidBusinessState("PAYROLL_PERIOD", period.status).asViolation()
            }
            val payslips = hr.batchPayslips(batch.id)
            require(payslips.isNotEmpty()) { "payroll_batch_has_no_payslips" }
            val snapshots = hr.payrollSnapshotsForBatch(batch.id).associateBy { it.payslipId }
            val componentsByPayslip = hr.payrollComponentsForBatch(batch.id).groupBy { it.payslipId }
            val includedEmployeeIds = payslips.map { it.employeeId }.toSet()
            val submittedAdjustments = hr.manualAdjustmentsForPeriod(period.id).filter {
                it.employeeId in includedEmployeeIds && it.status == ManualAdjustmentStatus.SUBMITTED.storedValue
            }
            if (submittedAdjustments.isNotEmpty()) {
                throw BusinessError.ApprovalRequired("MANUAL_ADJUSTMENT_NOT_APPROVED", 1).asViolation()
            }
            val unconsumedAdjustments = loadApprovedAdjustments(payslips.map { it.employeeId }, period.id)
            if (unconsumedAdjustments.isNotEmpty()) {
                throw BusinessError.ApprovalRequired("UNCONSUMED_MANUAL_ADJUSTMENT", 1).asViolation()
            }
            payslips.forEach { payslip ->
                require(PayrollPayslipStatus.fromStoredValue(payslip.status) == PayrollPayslipStatus.UNDER_REVIEW) {
                    "payslip_not_under_review:${payslip.id}"
                }
                val snapshot = snapshots[payslip.id] ?: throw BusinessError.InvalidBusinessState("PAYROLL_SNAPSHOT", "MISSING").asViolation()
                require(snapshot.detailComplete && payslip.componentDetailComplete) { "payroll_snapshot_incomplete:${payslip.id}" }
                val components = componentsByPayslip[payslip.id].orEmpty()
                require(components.isNotEmpty() || payslip.grossPayRial == 0L) { "payroll_components_missing:${payslip.id}" }
                val actualHash = snapshotHash(
                    snapshot.toDomainRequired(),
                    components.sortedBy(PayrollComponentEntity::id).map(PayrollComponentEntity::toDraft),
                    snapshot.calculationParameters,
                    snapshot.grossPayRial,
                    snapshot.totalDeductionsRial,
                    snapshot.netPayRial,
                )
                require(actualHash == snapshot.snapshotHash) { "payroll_snapshot_hash_mismatch:${payslip.id}" }
            }

            val now = clock()
            val journalIds = mutableListOf<Long>()
            payslips.forEach { original ->
                val components = componentsByPayslip.getValue(original.id).map(PayrollComponentEntity::toDraft)
                allocatePayslipAdvances(original, components, actor.id, now)
                val lines = PayrollAccountingPlanner.accrualLines(components, original.netPayRial)
                var expectedVersion = original.rowVersion
                if (lines.isNotEmpty()) {
                    val posting = accountingPosting.post(
                        AccountingPostingCommand(
                            entryNo = "PAY-${period.periodKey}-${original.employeeCodeSnapshot}-R${original.revisionNo}",
                            sourceType = "PAYROLL_ACCRUAL",
                            sourceId = original.id,
                            businessEpochDay = period.endEpochDay,
                            description = "شناسایی تعهد حقوق ${original.employeeNameSnapshot}",
                            lines = lines,
                            idempotencyKey = "PAYROLL_ACCRUAL:${original.globalId}",
                            correlationId = CorrelationId.parse(original.correlationId),
                            actorId = actor.id,
                            status = JournalStatus.POSTED,
                            accountingScope = if (batch.scope == "BRANCH") AccountingScope.BRANCH else AccountingScope.ORGANIZATION,
                            branchId = if (batch.scope == "BRANCH") {
                                branchResolver.requireActive(requireNotNull(batch.branchId) { "شعبه معتبر برای این لیست حقوق مشخص نشده است." }).id
                            } else null,
                        ),
                    )
                    optimistic(hr.attachPayslipAccrualJournal(original.id, posting.entryId, expectedVersion), "PAYROLL_PAYSLIP", original.id)
                    expectedVersion += 1
                    journalIds += posting.entryId
                }
                optimistic(
                    hr.transitionPayrollPayslip(
                        original.id,
                        PayrollPayslipStatus.UNDER_REVIEW.storedValue,
                        PayrollPayslipStatus.APPROVED.storedValue,
                        expectedVersion,
                        approvedAt = now,
                    ),
                    "PAYROLL_PAYSLIP",
                    original.id,
                )
                expectedVersion += 1
                val settlement = PayrollPaymentLedger.derive(original.netPayRial, emptyList())
                optimistic(
                    hr.transitionPayrollPayslip(
                        original.id,
                        PayrollPayslipStatus.APPROVED.storedValue,
                        PayrollPayslipStatus.PAYMENT_PENDING.storedValue,
                        expectedVersion,
                    ),
                    "PAYROLL_PAYSLIP",
                    original.id,
                )
                expectedVersion += 1
                if (settlement.status == PayrollPayslipStatus.PAID) {
                    optimistic(
                        hr.transitionPayrollPayslip(
                            original.id,
                            PayrollPayslipStatus.PAYMENT_PENDING.storedValue,
                            PayrollPayslipStatus.PAID.storedValue,
                            expectedVersion,
                            paidAt = now,
                        ),
                        "PAYROLL_PAYSLIP",
                        original.id,
                    )
                }
                val snapshotHash = snapshots.getValue(original.id).snapshotHash
                hr.insertApprovalEvent(approvalEvent(batch.id, original.id, "APPROVE", "UNDER_REVIEW", settlement.status.storedValue, actor.id, valid.reason, snapshotHash, now, original.correlationId))
            }
            optimistic(
                hr.transitionPayrollBatch(
                    batch.id,
                    batchFrom.storedValue,
                    PayrollBatchStatus.APPROVED.storedValue,
                    batch.rowVersion,
                    approvedBy = actor.id,
                    approvedAt = now,
                    accrualJournalId = journalIds.firstOrNull(),
                ),
                "PAYROLL_BATCH",
                batch.id,
            )
            val batchSettledWithoutPayment = payslips.all { it.netPayRial == 0L }
            optimistic(
                hr.transitionPayrollBatch(
                    batch.id,
                    PayrollBatchStatus.APPROVED.storedValue,
                    PayrollBatchStatus.PAYMENT_PENDING.storedValue,
                    batch.rowVersion + 1,
                ),
                "PAYROLL_BATCH",
                batch.id,
            )
            if (batchSettledWithoutPayment) {
                optimistic(
                    hr.transitionPayrollBatch(
                        batch.id,
                        PayrollBatchStatus.PAYMENT_PENDING.storedValue,
                        PayrollBatchStatus.PAID.storedValue,
                        batch.rowVersion + 2,
                    ),
                    "PAYROLL_BATCH",
                    batch.id,
                )
            }
            val siblingBatches = hr.payrollBatchesForPeriod(period.id)
            val allReadyForPayment = siblingBatches.all {
                it.id == batch.id || PayrollBatchStatus.fromStoredValue(it.status) in setOf(
                    PayrollBatchStatus.PAYMENT_PENDING,
                    PayrollBatchStatus.PARTIALLY_PAID,
                    PayrollBatchStatus.PAID,
                    PayrollBatchStatus.REVERSED,
                    PayrollBatchStatus.CANCELLED,
                    PayrollBatchStatus.LEGACY,
                )
            }
            if (allReadyForPayment) {
                PayrollPeriodStateMachine.requireTransition(PayrollPeriodStatus.REVIEW, PayrollPeriodStatus.APPROVED)
                optimistic(hr.transitionPayrollPeriod(period.id, "REVIEW", "APPROVED", period.rowVersion, null, null), "PAYROLL_PERIOD", period.id)
                PayrollPeriodStateMachine.requireTransition(PayrollPeriodStatus.APPROVED, PayrollPeriodStatus.PAYMENT)
                optimistic(hr.transitionPayrollPeriod(period.id, "APPROVED", "PAYMENT", period.rowVersion + 1, null, null), "PAYROLL_PERIOD", period.id)
            }
            val finalBatchStatus = if (batchSettledWithoutPayment) PayrollBatchStatus.PAID else PayrollBatchStatus.PAYMENT_PENDING
            hr.insertApprovalEvent(approvalEvent(batch.id, null, "FINAL_APPROVAL", batchFrom.storedValue, finalBatchStatus.storedValue, actor.id, valid.reason, null, now, correlation.value))
            recordAudit(actor.id, actor.displayName, "APPROVE", "PAYROLL_BATCH", batch.id, period.endEpochDay, valid.reason, "تأیید نهایی و ثبت تعهد حقوق", correlation.value, "status=${batchFrom.storedValue}", "status=${finalBatchStatus.storedValue};journals=${journalIds.size}")
            receipt(key, "APPROVE_BATCH", payload, "PAYROLL_BATCH", batch.id, finalBatchStatus.storedValue, actor.id, now, correlation.value)
        }
    }

    override suspend fun submitManualAdjustment(command: ManualPayrollAdjustmentCommand): Long {
        val actor = authorizer.require(Permission.PAYROLL_CREATE)
        val valid = command.validated()
        val key = receiptKey("MANUAL_ADJUSTMENT", valid.commandId)
        val payload = hash("${valid.employeeId}|${valid.periodId}|${valid.componentType}|${valid.direction}|${valid.amountRial}|${valid.reason}|${valid.attachmentMetadata}")
        val correlation = correlation("payroll_adjustment", valid.commandId)
        return database.withTransaction {
            replay(key, "MANUAL_ADJUSTMENT", payload)?.let { return@withTransaction it.resultEntityId }
            personnel.employeeById(valid.employeeId) ?: missing("EMPLOYEE", valid.employeeId)
            val period = hr.payrollPeriod(valid.periodId) ?: missing("PAYROLL_PERIOD", valid.periodId)
            if (PayrollPeriodStatus.fromStoredValue(period.status) !in setOf(PayrollPeriodStatus.OPEN, PayrollPeriodStatus.REOPENED, PayrollPeriodStatus.CALCULATING, PayrollPeriodStatus.REVIEW)) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }
            val now = clock()
            val id = hr.insertManualAdjustment(
                PayrollManualAdjustmentEntity(
                    globalId = valid.commandId,
                    idempotencyKey = key,
                    employeeId = valid.employeeId,
                    periodId = valid.periodId,
                    componentType = valid.componentType.storedValue,
                    direction = valid.direction.storedValue,
                    amountRial = valid.amountRial,
                    reason = valid.reason,
                    attachmentMetadata = valid.attachmentMetadata,
                    status = ManualAdjustmentStatus.SUBMITTED.storedValue,
                    createdByActorId = actor.id,
                    approvedByActorId = null,
                    createdAtEpochMillis = now,
                    approvedAtEpochMillis = null,
                    consumedByPayslipId = null,
                    correlationId = correlation.value,
                ),
            )
            recordAudit(actor.id, actor.displayName, "SUBMIT", "PAYROLL_ADJUSTMENT", id, period.endEpochDay, valid.reason, "ثبت تعدیل دستی حقوق", correlation.value, null, "status=SUBMITTED;type=${valid.componentType.storedValue}")
            receipt(key, "MANUAL_ADJUSTMENT", payload, "PAYROLL_ADJUSTMENT", id, "SUBMITTED", actor.id, now, correlation.value)
            id
        }
    }

    override suspend fun approveManualAdjustment(command: ApproveManualAdjustmentCommand) {
        val actor = authorizer.require(Permission.PAYROLL_REVIEW)
        val valid = command.validated()
        val key = receiptKey("APPROVE_ADJUSTMENT", valid.commandId)
        val payload = hash(valid.adjustmentId.toString())
        val correlation = correlation("payroll_adjustment_approve", valid.commandId)
        database.withTransaction {
            replay(key, "APPROVE_ADJUSTMENT", payload)?.let { return@withTransaction }
            val adjustment = hr.manualAdjustment(valid.adjustmentId) ?: missing("PAYROLL_ADJUSTMENT", valid.adjustmentId)
            SegregationOfDuties.requireDifferentActors("PAYROLL_MANUAL_ADJUSTMENT_APPROVAL", adjustment.createdByActorId, actor.id)
            val now = clock()
            optimistic(hr.approveManualAdjustment(adjustment.id, actor.id, now), "PAYROLL_ADJUSTMENT", adjustment.id)
            val period = hr.payrollPeriod(adjustment.periodId) ?: missing("PAYROLL_PERIOD", adjustment.periodId)
            recordAudit(actor.id, actor.displayName, "APPROVE", "PAYROLL_ADJUSTMENT", adjustment.id, period.endEpochDay, adjustment.reason, "تأیید تعدیل دستی حقوق", correlation.value, "status=${adjustment.status}", "status=APPROVED")
            receipt(key, "APPROVE_ADJUSTMENT", payload, "PAYROLL_ADJUSTMENT", adjustment.id, "APPROVED", actor.id, now, correlation.value)
        }
    }

    override suspend fun payPayslip(command: PayPayslipCommand): Long = paymentPosting.pay(command)

    override suspend fun reversePayment(command: ReversePayrollPaymentCommand): Long = paymentPosting.reverse(command)

    override suspend fun reversePayslip(command: ReversePayslipCommandV2) {
        val actor = authorizer.require(Permission.PAYROLL_REVERSE)
        val valid = command.validated()
        val key = receiptKey("REVERSE_PAYSLIP", valid.commandId)
        val payload = hash("${valid.payslipId}|${valid.reversalEpochDay}|${valid.reason}")
        val correlation = correlation("payroll_reverse", valid.commandId)
        database.withTransaction {
            replay(key, "REVERSE_PAYSLIP", payload)?.let { return@withTransaction }
            val payslip = hr.payrollPayslip(valid.payslipId) ?: missing("PAYROLL_PAYSLIP", valid.payslipId)
            val from = PayrollPayslipStatus.fromStoredValue(payslip.status)
            PayrollPayslipStateMachine.requireTransition(from, PayrollPayslipStatus.REVERSED)
            if (hr.payrollPayments(payslip.id).any { it.status == PayrollPaymentStatus.POSTED.storedValue && it.reversalOfPaymentId == null }) {
                throw BusinessError.ApprovalRequired("REVERSE_PAYROLL_PAYMENT_FIRST", 1).asViolation()
            }
            val period = hr.payrollPeriod(payslip.periodId) ?: missing("PAYROLL_PERIOD", payslip.periodId)
            if (PayrollPeriodStatus.fromStoredValue(period.status) == PayrollPeriodStatus.CLOSED) {
                throw BusinessError.PayrollPeriodClosed(period.id).asViolation()
            }
            val now = clock()
            var expectedVersion = payslip.rowVersion
            val originalJournal = payslip.accrualJournalEntryId
            var reversalJournalId: Long? = null
            if (originalJournal != null) {
                val result = accountingPosting.reverse(
                    AccountingReversalCommand(
                        originalEntryId = originalJournal,
                        entryNo = "REV-PAY-${payslip.globalId}",
                        sourceType = "PAYROLL_REVERSAL",
                        sourceId = payslip.id,
                        businessEpochDay = valid.reversalEpochDay,
                        reason = valid.reason,
                        idempotencyKey = "PAYROLL_REVERSAL:${valid.commandId}",
                        correlationId = correlation,
                        actorId = actor.id,
                    ),
                )
                reversalJournalId = result.entryId
                optimistic(hr.attachPayslipReversalJournal(payslip.id, result.entryId, expectedVersion), "PAYROLL_PAYSLIP", payslip.id)
                expectedVersion += 1
            } else if (payslip.grossPayRial > 0) {
                throw BusinessError.InvalidBusinessState("PAYROLL_PAYSLIP", "MISSING_ACCRUAL_JOURNAL").asViolation()
            }
            hr.payslipAdvanceAllocations(payslip.id).filter { it.status == "ALLOCATED" }.forEach { allocation ->
                optimistic(hr.reverseAdvanceAllocation(allocation.id, now, valid.reason), "PAYROLL_ADVANCE_ALLOCATION", allocation.id)
                optimistic(personnel.restoreAdvanceAllocation(allocation.advanceId, allocation.amountRial, now), "EMPLOYEE_ADVANCE", allocation.advanceId)
                recordAudit(actor.id, actor.displayName, "REVERSE", "PAYROLL_ADVANCE_ALLOCATION", allocation.id, valid.reversalEpochDay, valid.reason, "برگشت تخصیص مساعده", correlation.value, "status=ALLOCATED", "status=REVERSED")
            }
            optimistic(
                hr.transitionPayrollPayslip(
                    payslip.id,
                    from.storedValue,
                    PayrollPayslipStatus.REVERSED.storedValue,
                    expectedVersion,
                    reversalReason = valid.reason,
                    reversalEpochDay = valid.reversalEpochDay,
                    reversedAt = now,
                ),
                "PAYROLL_PAYSLIP",
                payslip.id,
            )
            hr.insertApprovalEvent(approvalEvent(payslip.batchId, payslip.id, "REVERSAL", from.storedValue, "REVERSED", actor.id, valid.reason, null, now, correlation.value))
            val batch = hr.payrollBatch(payslip.batchId) ?: missing("PAYROLL_BATCH", payslip.batchId)
            val activeAfter = hr.batchPayslips(batch.id).any { it.id != payslip.id && PayrollPayslipStatus.fromStoredValue(it.status) != PayrollPayslipStatus.REVERSED }
            if (!activeAfter) {
                val batchFrom = PayrollBatchStatus.fromStoredValue(batch.status)
                PayrollBatchStateMachine.requireTransition(batchFrom, PayrollBatchStatus.REVERSED)
                optimistic(hr.transitionPayrollBatch(batch.id, batchFrom.storedValue, "REVERSED", batch.rowVersion, reversalJournalId = reversalJournalId), "PAYROLL_BATCH", batch.id)
            }
            recordAudit(actor.id, actor.displayName, "REVERSE", "PAYROLL_PAYSLIP", payslip.id, valid.reversalEpochDay, valid.reason, "برگشت فیش حقوق", correlation.value, "status=${from.storedValue}", "status=REVERSED;revision=${payslip.revisionNo}")
            receipt(key, "REVERSE_PAYSLIP", payload, "PAYROLL_PAYSLIP", payslip.id, "REVERSED", actor.id, now, correlation.value)
        }
    }

    private suspend fun loadApprovedAdjustments(employeeIds: List<Long>, periodId: Long): List<PayrollManualAdjustmentEntity> =
        employeeIds.distinct().chunked(SQLITE_IN_CHUNK).flatMap {
            hr.approvedManualAdjustmentsForEmployees(it, periodId)
        }


    private suspend fun allocatePayslipAdvances(
        payslip: PayrollPayslipEntity,
        components: List<PayrollComponentDraftV2>,
        actorId: Long,
        now: Long,
    ) {
        val requested = components.filter { it.componentType == PayrollComponentType.ADVANCE_DEDUCTION }
            .fold(0L) { total, component -> SignedLongMath.add(total, component.amountRial) }
        if (requested == 0L) return
        val open = personnel.openAdvancesByEmployee(payslip.employeeId)
        val balances = open.map { advance ->
            OpenAdvanceBalance(advance.id, SignedLongMath.subtract(advance.amountRial, advance.settledAmountRial))
        }
        val available = balances.fold(0L) { total, item -> SignedLongMath.add(total, item.remainingRial) }
        if (requested > available) {
            throw BusinessError.AdvanceOverAllocation(open.firstOrNull()?.id ?: 0, requested, available).asViolation()
        }
        AdvanceDeductionAllocator.allocate(requested, balances).forEach { allocation ->
            optimistic(personnel.allocateAdvance(allocation.advanceId, allocation.amountRial, now), "EMPLOYEE_ADVANCE", allocation.advanceId)
            val allocationId = hr.insertAdvanceAllocation(
                PayrollAdvanceAllocationV2Entity(
                    idempotencyKey = "advance-allocation:${payslip.globalId}:${allocation.advanceId}",
                    payslipId = payslip.id,
                    advanceId = allocation.advanceId,
                    amountRial = allocation.amountRial,
                    status = "ALLOCATED",
                    createdByActorId = actorId,
                    createdAtEpochMillis = now,
                    reversedAtEpochMillis = null,
                    reversalReason = null,
                    correlationId = payslip.correlationId,
                ),
            )
            val actor = authorizer.actorIdentity()
            recordAudit(actorId, actor.displayName, "ALLOCATE", "PAYROLL_ADVANCE_ALLOCATION", allocationId, null, "تخصیص مساعده به فیش", "تخصیص مساعده", payslip.correlationId, null, "status=ALLOCATED;advanceId=${allocation.advanceId}")
        }
    }

    private fun PayrollExceptionRecord.toEntity(batchId: Long, now: Long) = PayrollExceptionEntity(
        batchId = batchId,
        payslipId = null,
        employeeId = employeeId,
        code = code,
        blocking = blocking,
        detail = detail.take(1_000),
        createdAtEpochMillis = now,
        resolvedAtEpochMillis = null,
        resolvedByActorId = null,
        resolutionNote = null,
    )

    private fun PreparedPayrollPayslip.toSnapshotEntity(payslipId: Long, snapshotHash: String, now: Long): PayrollSnapshotEntity {
        val input = result.snapshot
        return PayrollSnapshotEntity(
            payslipId = payslipId,
            employeeId = input.employeeId,
            employeeCode = input.employeeCode,
            employeeDisplayName = input.employeeDisplayName,
            contractId = contract.id,
            contractNumber = contract.contractNumber,
            contractVersionNo = contract.versionNo,
            baseSalaryRial = input.baseSalaryRial,
            standardPeriodMinutes = input.standardPeriodMinutes,
            eligiblePeriodMinutes = input.eligiblePeriodMinutes,
            actualWorkMinutes = input.actualWorkMinutes,
            overtimeMinutes = input.overtimeMinutes,
            absenceMinutes = input.absenceMinutes,
            lateMinutes = input.lateMinutes,
            paidLeaveMinutes = input.paidLeaveMinutes,
            unpaidLeaveMinutes = input.unpaidLeaveMinutes,
            payrollPolicyId = policy.id,
            payrollPolicyVersion = policy.versionNo,
            overtimeRateRialPerHour = input.overtimeRateRialPerHour,
            overtimeMultiplierBasisPoints = input.overtimeMultiplierBasisPoints,
            insuranceBasisPoints = input.insuranceBasisPoints,
            taxBasisPoints = input.taxBasisPoints,
            nightMinutes = input.nightMinutes,
            holidayMinutes = input.holidayMinutes,
            nightMultiplierBasisPoints = input.nightMultiplierBasisPoints,
            holidayMultiplierBasisPoints = input.holidayMultiplierBasisPoints,
            grossPayRial = result.grossPayRial,
            totalDeductionsRial = result.totalDeductionsRial,
            netPayRial = result.netPayRial,
            calculationVersion = input.calculationVersion,
            calculationParameters = traceParameters,
            snapshotHash = snapshotHash,
            capturedAtEpochMillis = now,
            detailComplete = true,
        )
    }

    private fun PayrollComponentDraftV2.toEntity(payslipId: Long, now: Long) = PayrollComponentEntity(
        payslipId = payslipId,
        componentType = componentType.storedValue,
        description = description,
        quantity = quantity,
        rateRial = rateRial,
        amountRial = amountRial,
        direction = direction.storedValue,
        sourceType = sourceType.storedValue,
        sourceId = sourceId,
        manualOverride = manualOverride,
        overrideReason = overrideReason,
        createdByActorId = createdByActorId,
        createdAtEpochMillis = now,
    )

    private fun snapshotHash(
        snapshot: PayrollInputSnapshot,
        components: List<PayrollComponentDraftV2>,
        traceParameters: String,
        grossPayRial: Long,
        totalDeductionsRial: Long,
        netPayRial: Long,
    ): String = hash(
        listOf(
            snapshot.employeeId,
            snapshot.employeeCode,
            snapshot.employeeDisplayName,
            snapshot.contractId,
            snapshot.contractVersionNo,
            snapshot.baseSalaryRial,
            snapshot.standardPeriodMinutes,
            snapshot.eligiblePeriodMinutes,
            snapshot.actualWorkMinutes,
            snapshot.overtimeMinutes,
            snapshot.absenceMinutes,
            snapshot.lateMinutes,
            snapshot.paidLeaveMinutes,
            snapshot.unpaidLeaveMinutes,
            snapshot.payrollPolicyId,
            snapshot.payrollPolicyVersion,
            snapshot.overtimeRateRialPerHour,
            snapshot.overtimeMultiplierBasisPoints,
            snapshot.insuranceBasisPoints,
            snapshot.taxBasisPoints,
            snapshot.nightMinutes,
            snapshot.holidayMinutes,
            snapshot.nightMultiplierBasisPoints,
            snapshot.holidayMultiplierBasisPoints,
            snapshot.calculationVersion,
            grossPayRial,
            totalDeductionsRial,
            netPayRial,
            traceParameters,
            components.joinToString("||") { component ->
                listOf(
                    component.componentType.storedValue,
                    component.description,
                    component.quantity,
                    component.rateRial,
                    component.amountRial,
                    component.direction.storedValue,
                    component.sourceType.storedValue,
                    component.sourceId,
                    component.manualOverride,
                    component.overrideReason,
                    component.createdByActorId,
                ).joinToString("|")
            },
        ).joinToString("\u001f"),
    )

    private fun calculatePayload(command: CalculatePayrollBatchCommand): String = listOf(
        command.batchId,
        command.employeeIds.joinToString(","),
        command.advanceDeductions.joinToString(",") { "${it.employeeId}:${it.amountRial}" },
        command.replacements.joinToString(",") { "${it.employeeId}:${it.replacesPayslipId}" },
    ).joinToString("|")

    private suspend fun recordAudit(
        actorId: Long,
        actorName: String,
        action: String,
        entityType: String,
        entityId: Long,
        businessEpochDay: Long?,
        reason: String,
        description: String,
        correlationId: String,
        before: String?,
        after: String?,
    ) {
        audit.record(
            AuditEventDraft(
                action = AuditAction.of(action),
                entityType = AuditEntityType.of(entityType),
                entityId = entityId,
                actorId = actorId,
                actorDisplayName = actorName,
                occurredAtEpochMillis = clock(),
                businessEpochDay = businessEpochDay,
                deviceId = "local-android",
                referenceType = entityType,
                referenceId = entityId,
                reason = reason,
                beforeSnapshot = before,
                afterSnapshot = after,
                correlationId = correlationId,
                description = description,
            ),
        )
    }

    private suspend fun replay(
        key: String,
        commandType: String,
        payloadHash: String,
    ): HrPayrollCommandReceiptEntity? = hr.commandReceipt(key)?.also { receipt ->
        if (receipt.commandType != commandType || receipt.payloadHash != payloadHash) {
            throw BusinessError.IdempotencyConflict(key).asViolation()
        }
    }

    private suspend fun receipt(
        key: String,
        commandType: String,
        payloadHash: String,
        resultEntityType: String,
        resultEntityId: Long,
        resultDetail: String,
        actorId: Long,
        now: Long,
        correlationId: String,
    ) {
        hr.insertCommandReceipt(
            HrPayrollCommandReceiptEntity(
                idempotencyKey = key,
                commandType = commandType,
                payloadHash = payloadHash,
                resultEntityType = resultEntityType,
                resultEntityId = resultEntityId,
                resultDetail = resultDetail.take(4_000),
                actorId = actorId,
                createdAtEpochMillis = now,
                correlationId = correlationId,
            ),
        )
    }

    private fun approvalEvent(
        batchId: Long,
        payslipId: Long?,
        eventType: String,
        fromStatus: String,
        toStatus: String,
        actorId: Long,
        reason: String,
        snapshotHash: String?,
        now: Long,
        correlationId: String,
    ) = PayrollApprovalEventEntity(
        batchId = batchId,
        payslipId = payslipId,
        eventType = eventType,
        fromStatus = fromStatus,
        toStatus = toStatus,
        actorId = actorId,
        reason = reason,
        snapshotHash = snapshotHash,
        createdAtEpochMillis = now,
        correlationId = correlationId,
    )

    private fun optimistic(result: Int, entityType: String, entityId: Long) {
        if (result != 1) throw BusinessError.ConcurrentModification(entityType, entityId).asViolation()
    }

    private fun missing(entityType: String, id: Long): Nothing =
        throw BusinessError.EntityNotFound(entityType, id).asViolation()

    private fun receiptKey(type: String, commandId: String): String = "HRPAY:$type:$commandId"

    private fun correlation(operation: String, commandId: String): CorrelationId =
        CorrelationId.forCommand(operation, GlobalId.parse(commandId))

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun exception(code: String, employeeId: Long?, blocking: Boolean, detail: String) =
        PayrollExceptionRecord(code, employeeId, blocking, detail)

    private fun <T> authorizedFlow(permission: Permission, upstream: () -> Flow<T>): Flow<T> = flow {
        authorizer.require(permission)
        emitAll(upstream())
    }

    private companion object {
        const val SQLITE_IN_CHUNK = 800
    }
}

private fun PayrollPeriodEntity.toRecord() = PayrollPeriodRecordV2(
    id = id,
    periodKey = periodKey,
    startEpochDay = startEpochDay,
    endEpochDay = endEpochDay,
    paymentDueEpochDay = paymentDueEpochDay,
    status = PayrollPeriodStatus.fromStoredValue(status),
)

private fun PayrollBatchEntity.toRecord() = PayrollBatchRecordV2(
    id = id,
    documentNumber = documentNumber,
    periodId = periodId,
    status = PayrollBatchStatus.fromStoredValue(status),
    branchName = branchName,
    branchId = branchId,
    department = department,
    createdByActorId = createdByActorId,
    calculatedByActorId = calculatedByActorId,
    calculatedAtEpochMillis = calculatedAtEpochMillis,
    reviewedByActorId = reviewedByActorId,
    approvedByActorId = approvedByActorId,
    approvedAtEpochMillis = approvedAtEpochMillis,
    correlationId = CorrelationId.parse(correlationId),
    source = PayrollDocumentSource.entries.firstOrNull { it.storedValue == source } ?: PayrollDocumentSource.LEGACY_MIGRATION,
)

private fun PayrollBatchDashboardRow.toRecord() = batch.toRecord().copy(
    employeesIncluded = employeesIncluded,
    grossPayrollRial = grossPayrollRial,
    deductionsRial = deductionsRial,
    netPayrollRial = netPayrollRial,
    paidRial = paidRial,
    remainingRial = remainingRial,
    exceptionCount = exceptionCount,
)

private fun PayrollPayslipEntity.toRecord() = PayrollPayslipRecordV2(
    id = id,
    batchId = batchId,
    periodId = periodId,
    employeeId = employeeId,
    employeeCodeSnapshot = employeeCodeSnapshot,
    employeeNameSnapshot = employeeNameSnapshot,
    revisionNo = revisionNo,
    replacesPayslipId = replacesPayslipId,
    contractId = contractId,
    status = PayrollPayslipStatus.fromStoredValue(status),
    grossPay = MoneyRial.of(grossPayRial),
    totalDeductions = MoneyRial.of(totalDeductionsRial),
    netPay = MoneyRial.of(netPayRial),
    paidAmount = MoneyRial.of(paidAmountRial),
    remainingAmount = MoneyRial.of(remainingAmountRial),
    componentDetailComplete = componentDetailComplete,
    source = PayrollDocumentSource.entries.firstOrNull { it.storedValue == source } ?: PayrollDocumentSource.LEGACY_MIGRATION,
    calculatedAtEpochMillis = calculatedAtEpochMillis,
    approvedAtEpochMillis = approvedAtEpochMillis,
    paidAtEpochMillis = paidAtEpochMillis,
    accrualJournalEntryId = accrualJournalEntryId,
    reversalJournalEntryId = reversalJournalEntryId,
    reversalEpochDay = reversalEpochDay,
    correlationId = CorrelationId.parse(correlationId),
)

private fun PayrollSnapshotEntity.toDomainOrNull(): PayrollInputSnapshot? {
    if (!detailComplete) return null
    return toDomainRequired()
}

private fun PayrollSnapshotEntity.toDomainRequired() = PayrollInputSnapshot(
    employeeId = employeeId,
    employeeCode = employeeCode,
    employeeDisplayName = employeeDisplayName,
    contractId = requireNotNull(contractId),
    contractVersionNo = requireNotNull(contractVersionNo),
    baseSalaryRial = requireNotNull(baseSalaryRial),
    standardPeriodMinutes = requireNotNull(standardPeriodMinutes),
    eligiblePeriodMinutes = requireNotNull(eligiblePeriodMinutes),
    actualWorkMinutes = requireNotNull(actualWorkMinutes),
    overtimeMinutes = requireNotNull(overtimeMinutes),
    absenceMinutes = requireNotNull(absenceMinutes),
    lateMinutes = requireNotNull(lateMinutes),
    paidLeaveMinutes = requireNotNull(paidLeaveMinutes),
    unpaidLeaveMinutes = requireNotNull(unpaidLeaveMinutes),
    payrollPolicyId = requireNotNull(payrollPolicyId),
    payrollPolicyVersion = requireNotNull(payrollPolicyVersion),
    overtimeRateRialPerHour = requireNotNull(overtimeRateRialPerHour),
    overtimeMultiplierBasisPoints = requireNotNull(overtimeMultiplierBasisPoints),
    insuranceBasisPoints = requireNotNull(insuranceBasisPoints),
    taxBasisPoints = requireNotNull(taxBasisPoints),
    calculationVersion = requireNotNull(calculationVersion),
    nightMinutes = nightMinutes,
    holidayMinutes = holidayMinutes,
    nightMultiplierBasisPoints = nightMultiplierBasisPoints,
    holidayMultiplierBasisPoints = holidayMultiplierBasisPoints,
).validated()

private fun PayrollComponentEntity.toDraft() = PayrollComponentDraftV2(
    componentType = PayrollComponentType.fromStoredValue(componentType),
    description = description,
    quantity = quantity,
    rateRial = rateRial,
    amountRial = amountRial,
    direction = PayrollComponentDirection.valueOf(direction),
    sourceType = PayrollComponentSourceType.valueOf(sourceType),
    sourceId = sourceId,
    manualOverride = manualOverride,
    overrideReason = overrideReason,
    createdByActorId = createdByActorId,
)

private fun PayrollPaymentEntity.toRecord() = PayrollPaymentRecordV2(
    id = id,
    payslipId = payslipId,
    amountRial = amountRial,
    treasuryAccountId = treasuryAccountId,
    paymentEpochDay = paymentEpochDay,
    paymentReference = paymentReference,
    status = PayrollPaymentStatus.fromStoredValue(status),
    journalEntryId = journalEntryId,
    reversalOfPaymentId = reversalOfPaymentId,
    correlationId = correlationId,
)

private fun PayrollManualAdjustmentEntity.toRecord() = ManualPayrollAdjustmentRecordV2(
    id = id,
    employeeId = employeeId,
    periodId = periodId,
    componentType = PayrollComponentType.fromStoredValue(componentType),
    direction = PayrollComponentDirection.valueOf(direction),
    amountRial = amountRial,
    reason = reason,
    status = ManualAdjustmentStatus.entries.firstOrNull { it.storedValue == status }
        ?: ManualAdjustmentStatus.REJECTED,
    createdByActorId = createdByActorId,
    approvedByActorId = approvedByActorId,
)

private fun PayrollExceptionEntity.toRecord() = PayrollExceptionRecord(code, employeeId, blocking, detail)

private fun EmployeeTimelineRow.toDomain() = EmployeeTimelineItem(
    stableKey = stableKey,
    employeeId = employeeId,
    businessEpochDay = businessEpochDay,
    occurredAtEpochMillis = occurredAtEpochMillis,
    eventType = eventType,
    title = title,
    referenceType = referenceType,
    referenceId = referenceId,
)
