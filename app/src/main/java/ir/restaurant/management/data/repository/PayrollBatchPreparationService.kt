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
import ir.restaurant.management.data.db.PlannedShiftEntity
import ir.restaurant.management.data.db.ShiftTemplateEntity
import ir.restaurant.management.data.db.OvertimeApprovalEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
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
import ir.restaurant.management.domain.personnel.AttendanceCalculationEngine
import ir.restaurant.management.domain.personnel.AttendanceAnomaly
import ir.restaurant.management.domain.personnel.AttendanceCorrectionCodec
import ir.restaurant.management.domain.personnel.AttendanceEvent
import ir.restaurant.management.domain.personnel.AttendanceEventAggregator
import ir.restaurant.management.domain.personnel.AttendanceEventType
import ir.restaurant.management.domain.personnel.AttendanceSource
import ir.restaurant.management.domain.personnel.AttendanceSessionCalculator
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
 * Pure payroll batch preparation boundary. It loads immutable calculation inputs and derives
 * payslip snapshots/components/exceptions without posting journals or mutating payroll workflow.
 * The caller owns the surrounding Room transaction when persisting the returned preparation.
 */
internal class PayrollBatchPreparationService(private val database: AppDatabase) {
    private val hr = database.hrPayrollDao()
    private val personnel = database.personnelDao()
    private val management = database.managementControlDao()

    suspend fun prepare(
        command: CalculatePayrollBatchCommand,
        batch: PayrollBatchEntity,
        period: PayrollPeriodEntity,
        actorId: Long,
    ): PayrollBatchPreparation {
        val loaded = loadPayrollCalculationInputs(command.employeeIds, period)
        return prepareBatch(command, batch, period, loaded, actorId)
    }

    private suspend fun loadPayrollCalculationInputs(
        employeeIds: List<Long>,
        period: PayrollPeriodEntity,
    ): PayrollCalculationInputs {
        val employees = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap { personnel.employeesByIds(it) }
        val contracts = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            hr.contractVersionsForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val events = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            hr.attendanceEventsForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val corrections = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            hr.approvedAttendanceCorrectionsForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val legacyAttendance = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            personnel.attendanceForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val leaves = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            personnel.approvedLeavesForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val adjustments = loadApprovedAdjustments(employeeIds, period.id)
        val submittedAdjustments = employeeIds.distinct().chunked(SQLITE_IN_CHUNK).flatMap {
            hr.submittedManualAdjustmentsForEmployees(it, period.id)
        }
        val advances = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap { personnel.openAdvancesForEmployees(it) }
        val oldPayslips = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap { hr.payslipsForEmployeesPeriod(it, period.id) }
        val plannedShifts = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            management.plannedShiftsForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        val shiftTemplates = management.activeShiftTemplates()
        val overtimeApprovals = employeeIds.chunked(SQLITE_IN_CHUNK).flatMap {
            hr.approvedOvertimeForEmployeesInRange(it, period.startEpochDay, period.endEpochDay)
        }
        return PayrollCalculationInputs(
            employees = employees.associateBy { it.id },
            contracts = contracts.groupBy { it.employeeId },
            events = events.groupBy { it.employeeId to it.businessEpochDay },
            corrections = corrections.groupBy { it.employeeId to it.businessEpochDay }.mapValues { (_, rows) -> rows.first() },
            legacyAttendance = legacyAttendance.associateBy { it.employeeId to it.workEpochDay },
            leaves = leaves.groupBy { it.employeeId },
            policies = personnel.payrollPolicySnapshot(),
            adjustments = adjustments.groupBy { it.employeeId },
            submittedAdjustments = submittedAdjustments.groupBy { it.employeeId },
            advances = advances.groupBy { it.employeeId },
            oldPayslips = oldPayslips.groupBy { it.employeeId },
            plannedShifts = plannedShifts.groupBy { it.employeeId to it.epochDay }.mapValues { (_, rows) -> rows.first() },
            shiftTemplates = shiftTemplates.associateBy { it.id },
            overtimeApprovals = overtimeApprovals.associateBy { it.employeeId to it.businessEpochDay },
        )
    }

    private suspend fun loadApprovedAdjustments(employeeIds: List<Long>, periodId: Long): List<PayrollManualAdjustmentEntity> =
        employeeIds.distinct().chunked(SQLITE_IN_CHUNK).flatMap {
            hr.approvedManualAdjustmentsForEmployees(it, periodId)
        }

    private fun prepareBatch(
        command: CalculatePayrollBatchCommand,
        batch: PayrollBatchEntity,
        period: PayrollPeriodEntity,
        loaded: PayrollCalculationInputs,
        actorId: Long,
    ): PayrollBatchPreparation {
        val exceptions = mutableListOf<PayrollExceptionRecord>()
        val prepared = mutableListOf<PreparedPayrollPayslip>()
        command.employeeIds.forEach { employeeId ->
            val employee = loaded.employees[employeeId]
            if (employee == null) {
                exceptions += exception("EMPLOYEE_NOT_FOUND", employeeId, true, "employee_missing")
                return@forEach
            }
            val employeeExceptions = mutableListOf<PayrollExceptionRecord>()
            validateBatchScope(batch, employee, employeeExceptions)
            val status = EmploymentStatus.fromStoredValue(employee.status)
            if (status !in setOf(EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE, EmploymentStatus.SUSPENDED, EmploymentStatus.TERMINATED)) {
                employeeExceptions += exception("EMPLOYEE_NOT_ACTIVE", employee.id, true, status.storedValue)
            } else if (status == EmploymentStatus.SUSPENDED) {
                employeeExceptions += exception("EMPLOYEE_SUSPENDED", employee.id, false, "suspended_employee_included")
            }
            val hireDate = employee.hireEpochDay
            if (hireDate == null || hireDate <= 0) {
                employeeExceptions += exception("MISSING_HIRE_DATE", employee.id, true, "hire_date_required_for_proration")
            }
            val eligibleStart = maxOf(period.startEpochDay, hireDate ?: period.startEpochDay)
            val eligibleEnd = minOf(period.endEpochDay, employee.terminationEpochDay ?: period.endEpochDay)
            if (eligibleEnd < eligibleStart) {
                employeeExceptions += exception("OUTSIDE_EMPLOYMENT_PERIOD", employee.id, true, "employee_not_employed_in_period")
            }
            val code = employee.employeeCode?.trim()
            if (code.isNullOrEmpty()) employeeExceptions += exception("MISSING_EMPLOYEE_CODE", employee.id, true, "stable_employee_code_required")

            val contracts = loaded.contracts[employee.id].orEmpty()
            val contract = if (eligibleEnd >= eligibleStart) {
                resolvePeriodContract(employee.id, eligibleStart, eligibleEnd, contracts, employeeExceptions)
            } else {
                null
            }
            val policy = contract?.let { resolvePolicy(it, eligibleStart, eligibleEnd, loaded.policies, employeeExceptions) }
            val oldRows = loaded.oldPayslips[employee.id].orEmpty()
            val replacement = command.replacements.firstOrNull { it.employeeId == employee.id }
            val latest = oldRows.maxWithOrNull(compareBy<PayrollPayslipEntity> { it.revisionNo }.thenBy { it.id })
            if (replacement == null && latest != null) {
                employeeExceptions += exception("PAYSLIP_REVISION_LINK_REQUIRED", employee.id, true, "existing_payslip_id=${latest.id}")
            }
            if (replacement != null) {
                val replaced = oldRows.firstOrNull { it.id == replacement.replacesPayslipId }
                if (replaced == null || PayrollPayslipStatus.fromStoredValue(replaced.status) != PayrollPayslipStatus.REVERSED) {
                    employeeExceptions += exception("INVALID_REPLACED_PAYSLIP", employee.id, true, "replaced_payslip_must_be_reversed")
                }
            }
            val advanceRequest = command.advanceDeductions.firstOrNull { it.employeeId == employee.id }?.amountRial ?: 0L
            loaded.submittedAdjustments[employee.id].orEmpty().forEach { adjustment ->
                employeeExceptions += exception(
                    "MANUAL_ADJUSTMENT_NOT_APPROVED",
                    employee.id,
                    true,
                    "adjustmentId=${adjustment.id}",
                )
            }
            val availableAdvance = loaded.advances[employee.id].orEmpty().fold(0L) { total, advance ->
                SignedLongMath.add(total, SignedLongMath.subtract(advance.amountRial, advance.settledAmountRial))
            }
            if (advanceRequest > availableAdvance) {
                employeeExceptions += exception("ADVANCE_OVER_ALLOCATION", employee.id, true, "requested=$advanceRequest;outstanding=$availableAdvance")
            }
            if (employeeExceptions.any { it.blocking } || contract == null || policy == null || code == null) {
                exceptions += employeeExceptions
                return@forEach
            }

            val periodShifts = loaded.plannedShifts.values
                .filter { it.employeeId == employee.id && it.epochDay in period.startEpochDay..period.endEpochDay }
                .sortedBy { it.epochDay }
            val eligibleShifts = periodShifts.filter { it.epochDay in eligibleStart..eligibleEnd }
            if (contract.workScheduleId == null) {
                employeeExceptions += exception("NO_WORK_SCHEDULE", employee.id, true, "contractId=${contract.id}")
            }
            val plannedDays = eligibleShifts.map { it.epochDay }.toSet()
            val unplannedAttendanceDays = (loaded.events.keys.asSequence()
                .filter { it.first == employee.id && it.second in eligibleStart..eligibleEnd }
                .filter { (_, day) -> day !in plannedDays && loaded.events[employee.id to day].orEmpty().any { it.eventType in setOf("CLOCK_IN", "CLOCK_OUT") } }
                .map { it.second } + loaded.legacyAttendance.keys.asSequence()
                .filter { it.first == employee.id && it.second in eligibleStart..eligibleEnd && it.second !in plannedDays }
                .filter { loaded.legacyAttendance[it]?.status == "PRESENT" }
                .map { it.second }).distinct().sorted().toList()
            if (unplannedAttendanceDays.isNotEmpty()) {
                employeeExceptions += exception("NO_EFFECTIVE_SHIFT", employee.id, true, "businessDays=${unplannedAttendanceDays.joinToString(",")}")
            }
            val totals = PayrollAttendanceTotals()
            val traceEventIds = mutableListOf<Long>()
            val traceLegacyIds = mutableListOf<Long>()
            val traceCorrectionIds = mutableListOf<Long>()
            val traceLeaveIds = mutableSetOf<Long>()
            val tracePlannedShiftIds = mutableListOf<Long>()
            eligibleShifts.forEach { plannedShift ->
                val businessDay = plannedShift.epochDay
                val dayEvents = loaded.events[employee.id to businessDay].orEmpty()
                val correction = loaded.corrections[employee.id to businessDay]
                val legacy = loaded.legacyAttendance[employee.id to businessDay]
                val dayLeaves = loaded.leaves[employee.id].orEmpty().filter { businessDay in it.startEpochDay..it.endEpochDay }
                val shiftTemplate = plannedShift.shiftTemplateId?.let(loaded.shiftTemplates::get)
                val overtimeApproval = loaded.overtimeApprovals[employee.id to businessDay]
                val day = summarizeDay(
                    employee = employee,
                    contract = contract,
                    plannedShift = plannedShift,
                    shiftTemplate = shiftTemplate,
                    businessDay = businessDay,
                    events = dayEvents,
                    correction = correction,
                    legacy = legacy,
                    leaves = dayLeaves,
                    overtimeApproval = overtimeApproval,
                )
                totals.add(day)
                tracePlannedShiftIds += plannedShift.id
                traceEventIds += dayEvents.map { it.id }
                if (legacy != null) traceLegacyIds += legacy.id
                if (correction != null) traceCorrectionIds += correction.id
                traceLeaveIds += dayLeaves.map { it.id }
                employeeExceptions += day.exceptions
            }
            val standardPeriodMinutes = periodShifts.sumOf(::plannedWorkMinutes)
            val eligiblePeriodMinutes = eligibleShifts.sumOf(::plannedWorkMinutes)
            if (standardPeriodMinutes <= 0) {
                employeeExceptions += exception("INVALID_STANDARD_WORK_MINUTES", employee.id, true, "standard_period_minutes=$standardPeriodMinutes")
            }
            if (employeeExceptions.any { it.blocking }) {
                exceptions += employeeExceptions
                return@forEach
            }
            val adjustments = loaded.adjustments[employee.id].orEmpty()
            val manualComponents = adjustments.map { adjustment ->
                PayrollComponentDraftV2(
                    componentType = PayrollComponentType.fromStoredValue(adjustment.componentType),
                    description = adjustment.reason,
                    quantity = null,
                    rateRial = null,
                    amountRial = adjustment.amountRial,
                    direction = PayrollComponentDirection.valueOf(adjustment.direction),
                    sourceType = PayrollComponentSourceType.MANUAL_ADJUSTMENT,
                    sourceId = adjustment.id,
                    manualOverride = true,
                    overrideReason = adjustment.reason,
                    createdByActorId = adjustment.createdByActorId,
                ).validated()
            }
            val snapshot = PayrollInputSnapshot(
                employeeId = employee.id,
                employeeCode = code,
                employeeDisplayName = employee.displayName.ifBlank { employee.name },
                contractId = contract.id,
                contractVersionNo = contract.versionNo,
                baseSalaryRial = contract.baseSalaryRial,
                standardPeriodMinutes = standardPeriodMinutes,
                eligiblePeriodMinutes = eligiblePeriodMinutes,
                actualWorkMinutes = totals.workedMinutes,
                overtimeMinutes = totals.overtimeMinutes,
                absenceMinutes = totals.absenceMinutes,
                lateMinutes = totals.lateMinutes,
                paidLeaveMinutes = totals.paidLeaveMinutes,
                unpaidLeaveMinutes = totals.unpaidLeaveMinutes,
                payrollPolicyId = policy.id,
                payrollPolicyVersion = policy.versionNo,
                overtimeRateRialPerHour = policy.overtimeHourlyRateRial,
                overtimeMultiplierBasisPoints = policy.overtimeMultiplierBasisPoints,
                insuranceBasisPoints = policy.insuranceBasisPoints,
                taxBasisPoints = policy.taxBasisPoints,
                calculationVersion = CALCULATION_VERSION,
                nightMinutes = totals.nightMinutes,
                holidayMinutes = totals.holidayMinutes,
                nightMultiplierBasisPoints = policy.nightMultiplierBasisPoints,
                holidayMultiplierBasisPoints = policy.holidayMultiplierBasisPoints,
            ).validated()
            val result = try {
                PayrollCalculationService.calculate(
                    PayrollCalculationCommand(
                        commandId = GlobalId.parse(command.commandId),
                        batchId = batch.id,
                        snapshot = snapshot,
                        approvedManualComponents = manualComponents,
                        approvedAdvanceDeductionRial = advanceRequest,
                    ),
                )
            } catch (error: BusinessRuleViolation) {
                if (error.error is BusinessError.NegativeNetPay) {
                    employeeExceptions += exception("NEGATIVE_NET_PAY", employee.id, true, "deductions_exceed_earnings")
                    exceptions += employeeExceptions
                    return@forEach
                }
                throw error
            }
            employeeExceptions += result.warnings.map { warning -> exception(warning, employee.id, false, warning.lowercase()) }
            val trace = listOf(
                "plannedShiftIds=${tracePlannedShiftIds.distinct().sorted().joinToString(",")}",
                "attendanceEventIds=${traceEventIds.distinct().sorted().joinToString(",")}",
                "legacyAttendanceIds=${traceLegacyIds.distinct().sorted().joinToString(",")}",
                "attendanceCorrectionIds=${traceCorrectionIds.distinct().sorted().joinToString(",")}",
                "leaveIds=${traceLeaveIds.sorted().joinToString(",")}",
                "manualAdjustmentIds=${adjustments.map { it.id }.sorted().joinToString(",")}",
                "advanceRequestedRial=$advanceRequest",
                "rounding=HALF_UP",
            ).joinToString(";")
            prepared += PreparedPayrollPayslip(
                employee = employee,
                contract = contract,
                policy = policy,
                result = result,
                revisionNo = (latest?.revisionNo ?: 0) + 1,
                replacesPayslipId = replacement?.replacesPayslipId,
                manualAdjustmentIds = adjustments.map { it.id },
                traceParameters = trace,
            )
            exceptions += employeeExceptions
        }
        return PayrollBatchPreparation(prepared, exceptions.distinctBy { Triple(it.code, it.employeeId, it.detail) })
    }

    private fun resolvePeriodContract(
        employeeId: Long,
        eligibleStart: Long,
        eligibleEnd: Long,
        entities: List<EmploymentContractVersionEntity>,
        exceptions: MutableList<PayrollExceptionRecord>,
    ): EmploymentContractVersionEntity? {
        val domains = entities.map(EmploymentContractVersionEntity::toDomain)
        val intersecting = domains.filter { contract ->
            contract.status in setOf(
                EmploymentContractStatus.APPROVED,
                EmploymentContractStatus.ACTIVE,
                EmploymentContractStatus.SUPERSEDED,
                EmploymentContractStatus.LEGACY,
            ) &&
                contract.effectiveFromEpochDay <= eligibleEnd &&
                (contract.effectiveToEpochDay ?: Long.MAX_VALUE) >= eligibleStart
        }
        val conflicting = intersecting.flatMapIndexed { index, left ->
            intersecting.drop(index + 1).filter { right ->
                !versionRelated(left, right, domains) &&
                maxOf(left.effectiveFromEpochDay, right.effectiveFromEpochDay, eligibleStart) <=
                    minOf(left.effectiveToEpochDay ?: Long.MAX_VALUE, right.effectiveToEpochDay ?: Long.MAX_VALUE, eligibleEnd)
            }.flatMap { right -> listOf(left.id, right.id) }
        }.distinct().sorted()
        if (conflicting.isNotEmpty()) {
            exceptions += exception("CONFLICTING_CONTRACTS", employeeId, true, "contractIds=${conflicting.joinToString(",")}")
            return null
        }
        return try {
            val start = EffectiveContractResolver.resolve(employeeId, eligibleStart, domains)
            val end = EffectiveContractResolver.resolve(employeeId, eligibleEnd, domains)
            if (start.id != end.id) {
                exceptions += exception("CONTRACT_CHANGE_WITHIN_PERIOD", employeeId, true, "start=${start.id};end=${end.id}")
                null
            } else {
                entities.first { it.id == start.id }
            }
        } catch (error: BusinessRuleViolation) {
            when (val failure = error.error) {
                is BusinessError.NoEffectiveContract -> exceptions += exception("NO_EFFECTIVE_CONTRACT", employeeId, true, "businessDay=${failure.businessEpochDay}")
                is BusinessError.ConflictingContracts -> exceptions += exception("CONFLICTING_CONTRACTS", employeeId, true, "contractIds=${failure.contractIds.joinToString(",")}")
                else -> throw error
            }
            null
        }
    }

    private fun versionRelated(
        left: EmploymentContractVersion,
        right: EmploymentContractVersion,
        contracts: List<EmploymentContractVersion>,
    ): Boolean {
        val byId = contracts.associateBy { it.id }
        fun replaces(descendant: EmploymentContractVersion, ancestorId: Long): Boolean {
            val visited = mutableSetOf<Long>()
            var predecessorId = descendant.replacesContractId
            while (predecessorId != null && visited.add(predecessorId)) {
                if (predecessorId == ancestorId) return true
                predecessorId = byId[predecessorId]?.replacesContractId
            }
            return false
        }
        return replaces(left, right.id) || replaces(right, left.id)
    }

    private fun resolvePolicy(
        contract: EmploymentContractVersionEntity,
        from: Long,
        to: Long,
        policies: List<PayrollPolicyEntity>,
        exceptions: MutableList<PayrollExceptionRecord>,
    ): PayrollPolicyEntity? {
        val candidates = if (contract.payrollPolicyId != null) {
            policies.filter { it.id == contract.payrollPolicyId }
        } else {
            policies.filter {
                it.effectiveFromEpochDay <= from && (it.effectiveToEpochDay ?: Long.MAX_VALUE) >= to && it.status == "ACTIVE"
            }
        }
        val policy = candidates.singleOrNull()
        if (policy == null || policy.effectiveFromEpochDay > from || (policy.effectiveToEpochDay ?: Long.MAX_VALUE) < to || policy.status != "ACTIVE") {
            exceptions += exception("MISSING_OR_CONFLICTING_PAYROLL_POLICY", contract.employeeId, true, "contractId=${contract.id};policyId=${contract.payrollPolicyId}")
            return null
        }
        return policy
    }

    private fun summarizeDay(
        employee: EmployeeEntity,
        contract: EmploymentContractVersionEntity,
        plannedShift: PlannedShiftEntity,
        shiftTemplate: ShiftTemplateEntity?,
        businessDay: Long,
        events: List<AttendanceEventEntity>,
        correction: AttendanceCorrectionEntity?,
        legacy: AttendanceEntity?,
        leaves: List<LeaveEntity>,
        overtimeApproval: OvertimeApprovalEntity?,
    ): PayrollDayTotals {
        val exceptions = mutableListOf<PayrollExceptionRecord>()
        val scheduledMinutes = plannedWorkMinutes(plannedShift)
        if (scheduledMinutes <= 0) {
            return PayrollDayTotals(exceptions = listOf(exception("INVALID_PLANNED_SHIFT", employee.id, true, "plannedShiftId=${plannedShift.id}")))
        }
        if (leaves.size > 1) {
            exceptions += exception("OVERLAPPING_APPROVED_LEAVE", employee.id, true, "businessDay=$businessDay;leaveIds=${leaves.map { it.id }}")
        }
        val leave = leaves.firstOrNull()
        if (leave != null) {
            val type = LeaveType.fromStoredValue(leave.leaveType)
            val rangeDays = Math.addExact(Math.subtractExact(leave.endEpochDay, leave.startEpochDay), 1L)
            val dailyLeaveMicros = leave.daysMicros / rangeDays
            val leaveMinutes = FixedPointRatio.multiplyDivide(
                value = scheduledMinutes.toLong(),
                multiplier = dailyLeaveMicros.coerceIn(0L, LEAVE_DAY_MICROS),
                divisor = LEAVE_DAY_MICROS,
                rounding = FixedPointRounding.HALF_UP,
            ).toInt()
            val hasWorkedInput = events.any { it.eventType in setOf("CLOCK_IN", "CLOCK_OUT") } || legacy?.status == "PRESENT"
            if (hasWorkedInput && leaveMinutes < scheduledMinutes) {
                val attendancePart = summarizeDay(
                    employee, contract, plannedShift, shiftTemplate, businessDay, events, correction, legacy,
                    emptyList(), overtimeApproval,
                )
                return attendancePart.copy(
                    absenceMinutes = (attendancePart.absenceMinutes - leaveMinutes).coerceAtLeast(0),
                    paidLeaveMinutes = leaveMinutes.takeIf { type.paid } ?: 0,
                    unpaidLeaveMinutes = leaveMinutes.takeIf { !type.paid } ?: 0,
                    exceptions = exceptions + attendancePart.exceptions,
                )
            }
            if (hasWorkedInput) {
                exceptions += exception("LEAVE_ATTENDANCE_CONFLICT", employee.id, true, "businessDay=$businessDay;leaveId=${leave.id}")
            }
            return PayrollDayTotals(
                paidLeaveMinutes = leaveMinutes.takeIf { type.paid } ?: 0,
                unpaidLeaveMinutes = leaveMinutes.takeIf { !type.paid } ?: 0,
                absenceMinutes = (scheduledMinutes - leaveMinutes).coerceAtLeast(0),
                exceptions = exceptions,
            )
        }
        if (correction != null) {
            val snapshot = AttendanceCorrectionCodec.decode(correction.afterSnapshot)
            return PayrollDayTotals(
                workedMinutes = snapshot.workedMinutes,
                overtimeMinutes = snapshot.overtimeMinutes,
                absenceMinutes = Math.addExact(snapshot.absenceMinutes, snapshot.earlyLeaveMinutes),
                lateMinutes = snapshot.lateMinutes,
                paidLeaveMinutes = scheduledMinutes.takeIf { snapshot.status == DailyAttendanceStatus.PAID_LEAVE } ?: 0,
                unpaidLeaveMinutes = scheduledMinutes.takeIf { snapshot.status == DailyAttendanceStatus.UNPAID_LEAVE } ?: 0,
                nightMinutes = if (shiftTemplate?.nightShift == true) snapshot.workedMinutes else 0,
                holidayMinutes = if (snapshot.status == DailyAttendanceStatus.HOLIDAY) snapshot.workedMinutes else 0,
                exceptions = exceptions,
            )
        }
        val sessionSummary = AttendanceSessionCalculator.summarize(
            employeeId = employee.id,
            businessEpochDay = businessDay,
            events = events.map { it.toDomain() },
            scheduledBreakMinutes = plannedShift.breakMinutes,
        )
        if (sessionSummary.anomalies.isNotEmpty()) {
            exceptions += sessionSummary.anomalies.map { it.toException(employee.id, businessDay) }
            return PayrollDayTotals(absenceMinutes = scheduledMinutes, exceptions = exceptions)
        }
        val firstIn = sessionSummary.firstIn
        val lastOut = sessionSummary.lastOut
        if (firstIn != null || lastOut != null) {
            if (firstIn == null || lastOut == null) {
                exceptions += exception(
                    if (firstIn == null) "ATTENDANCE_MISSING_CLOCK_IN" else "ATTENDANCE_MISSING_CLOCK_OUT",
                    employee.id, true, "businessDay=$businessDay;plannedShiftId=${plannedShift.id}",
                )
                return PayrollDayTotals(absenceMinutes = scheduledMinutes, exceptions = exceptions)
            }
            val template = shiftTemplate
            if (template == null) {
                exceptions += exception("NO_EFFECTIVE_SHIFT", employee.id, true, "businessDay=$businessDay;plannedShiftId=${plannedShift.id}")
                return PayrollDayTotals(absenceMinutes = scheduledMinutes, exceptions = exceptions)
            }
            val calc = AttendanceCalculationEngine.calculate(
                businessEpochDay = businessDay,
                checkInMinute = firstIn.minuteOfDay,
                checkOutMinute = lastOut.minuteOfDay,
                shift = AttendanceCalculationEngine.ShiftInput(
                    plannedStartEpochMillis = plannedShift.plannedStartEpochMillis,
                    plannedEndEpochMillis = plannedShift.plannedEndEpochMillis,
                    breakMinutes = 0, // worked minutes are canonicalized from paired sessions below
                    graceInMinutes = template.graceInMinutes,
                    graceOutMinutes = template.graceOutMinutes,
                    overtimeEligible = template.overtimeEligible,
                    overtimeRequiresApproval = template.overtimeRequiresApproval,
                ),
                approvedOvertimeMinutes = overtimeApproval?.approvedMinutes,
            )
            if (template.overtimeRequiresApproval && calc.rawOvertimeMinutes > 0 && overtimeApproval == null) {
                exceptions += exception("OVERTIME_APPROVAL_REQUIRED", employee.id, true, "businessDay=$businessDay;raw=${calc.rawOvertimeMinutes}")
            }
            return PayrollDayTotals(
                workedMinutes = sessionSummary.workedMinutes,
                overtimeMinutes = calc.payrollOvertimeMinutes,
                absenceMinutes = (scheduledMinutes - sessionSummary.workedMinutes).coerceAtLeast(0),
                lateMinutes = calc.payableLateMinutes,
                nightMinutes = if (template.nightShift) sessionSummary.workedMinutes else 0,
                exceptions = exceptions,
            )
        }
        if (legacy != null) {
            return when (legacy.status) {
                "PRESENT" -> PayrollDayTotals(
                    workedMinutes = if (legacy.checkInMinute != null && legacy.checkOutMinute != null) {
                        if (legacy.checkOutMinute >= legacy.checkInMinute) {
                            (legacy.checkOutMinute - legacy.checkInMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                        } else {
                            (1440 - legacy.checkInMinute + legacy.checkOutMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                        }
                    } else 0,
                    overtimeMinutes = legacy.approvedOvertimeMinutes,
                    absenceMinutes = legacy.earlyLeaveMinutes,
                    lateMinutes = legacy.payableLateMinutes,
                    nightMinutes = if (shiftTemplate?.nightShift == true && legacy.checkInMinute != null && legacy.checkOutMinute != null) {
                        if (legacy.checkOutMinute >= legacy.checkInMinute) (legacy.checkOutMinute - legacy.checkInMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                        else (1440 - legacy.checkInMinute + legacy.checkOutMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                    } else 0,
                    exceptions = exceptions,
                )
                "ABSENT" -> PayrollDayTotals(absenceMinutes = scheduledMinutes, exceptions = exceptions)
                "LEAVE" -> PayrollDayTotals(exceptions = exceptions + exception("LEGACY_LEAVE_PAY_STATUS_UNKNOWN", employee.id, false, "businessDay=$businessDay"))
                "HOLIDAY" -> {
                    val worked = if (legacy.checkInMinute != null && legacy.checkOutMinute != null) {
                        if (legacy.checkOutMinute >= legacy.checkInMinute) (legacy.checkOutMinute - legacy.checkInMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                        else (1440 - legacy.checkInMinute + legacy.checkOutMinute - plannedShift.breakMinutes).coerceAtLeast(0)
                    } else 0
                    PayrollDayTotals(
                        workedMinutes = worked,
                        holidayMinutes = worked,
                        nightMinutes = if (shiftTemplate?.nightShift == true) worked else 0,
                        exceptions = exceptions,
                    )
                }
                "MISSION", "OFF_DAY" -> PayrollDayTotals(exceptions = exceptions)
                else -> PayrollDayTotals(
                    absenceMinutes = scheduledMinutes,
                    exceptions = exceptions + exception("UNKNOWN_LEGACY_ATTENDANCE", employee.id, true, "businessDay=$businessDay;status=${legacy.status}"),
                )
            }
        }
        return PayrollDayTotals(absenceMinutes = scheduledMinutes, exceptions = exceptions)
    }

    private fun plannedWorkMinutes(shift: PlannedShiftEntity): Int =
        (((shift.plannedEndEpochMillis - shift.plannedStartEpochMillis) / 60_000L).toInt() - shift.breakMinutes).coerceAtLeast(0)

    private fun validateBatchScope(
        batch: PayrollBatchEntity,
        employee: EmployeeEntity,
        exceptions: MutableList<PayrollExceptionRecord>,
    ) {
        when (batch.scope) {
            "ALL", "SELECTED" -> Unit
            "BRANCH" -> if (batch.branchId == null || employee.branchId != batch.branchId) exceptions += exception("BATCH_SCOPE_MISMATCH", employee.id, true, "branchId=${employee.branchId ?: "UNASSIGNED"}")
            "DEPARTMENT" -> if (employee.department != batch.department) exceptions += exception("BATCH_SCOPE_MISMATCH", employee.id, true, "department=${employee.department}")
            else -> exceptions += exception("UNKNOWN_BATCH_SCOPE", employee.id, true, batch.scope)
        }
    }


    private fun exception(code: String, employeeId: Long?, blocking: Boolean, detail: String) =
        PayrollExceptionRecord(code, employeeId, blocking, detail)

    private companion object {
        const val SQLITE_IN_CHUNK = 800
        const val CALCULATION_VERSION = "HRPAY-2.0.0"
        const val MAXIMUM_DAILY_WORK_MINUTES = 16 * 60
        const val LEAVE_DAY_MICROS = 1_000_000L
    }
}

internal data class PayrollCalculationInputs(
    val employees: Map<Long, EmployeeEntity>,
    val contracts: Map<Long, List<EmploymentContractVersionEntity>>,
    val events: Map<Pair<Long, Long>, List<AttendanceEventEntity>>,
    val corrections: Map<Pair<Long, Long>, AttendanceCorrectionEntity>,
    val legacyAttendance: Map<Pair<Long, Long>, AttendanceEntity>,
    val leaves: Map<Long, List<LeaveEntity>>,
    val policies: List<PayrollPolicyEntity>,
    val adjustments: Map<Long, List<PayrollManualAdjustmentEntity>>,
    val submittedAdjustments: Map<Long, List<PayrollManualAdjustmentEntity>>,
    val advances: Map<Long, List<EmployeeAdvanceEntity>>,
    val oldPayslips: Map<Long, List<PayrollPayslipEntity>>,
    val plannedShifts: Map<Pair<Long, Long>, PlannedShiftEntity>,
    val shiftTemplates: Map<Long, ShiftTemplateEntity>,
    val overtimeApprovals: Map<Pair<Long, Long>, OvertimeApprovalEntity>,
)

internal data class PreparedPayrollPayslip(
    val employee: EmployeeEntity,
    val contract: EmploymentContractVersionEntity,
    val policy: PayrollPolicyEntity,
    val result: PayrollCalculationResultV2,
    val revisionNo: Int,
    val replacesPayslipId: Long?,
    val manualAdjustmentIds: List<Long>,
    val traceParameters: String,
)

internal data class PayrollBatchPreparation(
    val payslips: List<PreparedPayrollPayslip>,
    val exceptions: List<PayrollExceptionRecord>,
)

private data class PayrollDayTotals(
    val workedMinutes: Int = 0,
    val overtimeMinutes: Int = 0,
    val absenceMinutes: Int = 0,
    val lateMinutes: Int = 0,
    val paidLeaveMinutes: Int = 0,
    val unpaidLeaveMinutes: Int = 0,
    val nightMinutes: Int = 0,
    val holidayMinutes: Int = 0,
    val exceptions: List<PayrollExceptionRecord> = emptyList(),
)

private class PayrollAttendanceTotals {
    var workedMinutes: Int = 0
        private set
    var overtimeMinutes: Int = 0
        private set
    var absenceMinutes: Int = 0
        private set
    var lateMinutes: Int = 0
        private set
    var paidLeaveMinutes: Int = 0
        private set
    var unpaidLeaveMinutes: Int = 0
        private set
    var nightMinutes: Int = 0
        private set
    var holidayMinutes: Int = 0
        private set

    fun add(day: PayrollDayTotals) {
        workedMinutes = Math.addExact(workedMinutes, day.workedMinutes)
        overtimeMinutes = Math.addExact(overtimeMinutes, day.overtimeMinutes)
        absenceMinutes = Math.addExact(absenceMinutes, day.absenceMinutes)
        lateMinutes = Math.addExact(lateMinutes, day.lateMinutes)
        paidLeaveMinutes = Math.addExact(paidLeaveMinutes, day.paidLeaveMinutes)
        unpaidLeaveMinutes = Math.addExact(unpaidLeaveMinutes, day.unpaidLeaveMinutes)
        nightMinutes = Math.addExact(nightMinutes, day.nightMinutes)
        holidayMinutes = Math.addExact(holidayMinutes, day.holidayMinutes)
    }
}

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
    jobTitleSnapshot = jobTitleSnapshot,
    departmentSnapshot = departmentSnapshot,
    branchSnapshot = branchSnapshot,
    status = EmploymentContractStatus.fromStoredValue(status),
    createdAtEpochMillis = createdAtEpochMillis,
    createdByActorId = createdByActorId,
    approvedAtEpochMillis = approvedAtEpochMillis,
    approvedByActorId = approvedByActorId,
)

private fun AttendanceEventEntity.toDomain() = AttendanceEvent(
    id = id,
    employeeId = employeeId,
    eventType = AttendanceEventType.fromStoredValue(eventType),
    businessEpochDay = businessEpochDay,
    timestampEpochMillis = timestampEpochMillis,
    minuteOfDay = minuteOfDay,
    source = AttendanceSource.fromStoredValue(source),
    deviceId = deviceId,
    locationId = locationId,
    createdByActorId = createdByActorId,
    reason = reason,
    correlationId = CorrelationId.parse(correlationId),
    branchId = branchId,
)

private fun AttendanceAnomaly.toException(employeeId: Long, businessDay: Long) = PayrollExceptionRecord(
    code = "ATTENDANCE_${type.name}",
    employeeId = employeeId,
    blocking = true,
    detail = "businessDay=$businessDay;eventIds=${eventIds.joinToString(",")};detail=$detail",
)
