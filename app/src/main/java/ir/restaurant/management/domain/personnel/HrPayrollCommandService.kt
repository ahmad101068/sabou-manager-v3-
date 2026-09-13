package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryChannel
import kotlinx.coroutines.flow.Flow

data class PayrollPeriodDraftV2(
    val periodKey: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val paymentDueEpochDay: Long? = null,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PayrollPeriodDraftV2 {
        require(periodKey.trim().uppercase().matches(Regex("[A-Z0-9][A-Z0-9._/-]{2,39}"))) { "payroll_period_key_invalid" }
        require(startEpochDay > 0 && endEpochDay >= startEpochDay) { "payroll_period_range_invalid" }
        require(paymentDueEpochDay == null || paymentDueEpochDay >= endEpochDay) { "payroll_payment_due_date_invalid" }
        return copy(periodKey = periodKey.trim().uppercase(), commandId = GlobalId.parse(commandId).value)
    }
}

data class PayrollBatchDraftV2(
    val periodId: Long,
    val scope: String = "ALL",
    val branchName: String? = null,
    val department: String? = null,
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
    val branchId: Long? = null,
) {
    fun validated(): PayrollBatchDraftV2 {
        require(periodId > 0)
        val normalizedScope = scope.trim().uppercase()
        require(normalizedScope in setOf("ALL", "BRANCH", "DEPARTMENT", "SELECTED")) { "payroll_scope_invalid" }
        require(normalizedScope != "BRANCH" || (branchId != null && branchId > 0) || !branchName.isNullOrBlank()) { "payroll_branch_scope_missing" }
        require(branchId == null || branchId > 0) { "payroll_branch_id_invalid" }
        require(normalizedScope != "DEPARTMENT" || !department.isNullOrBlank()) { "payroll_department_scope_missing" }
        require(notes.length <= 500)
        return copy(
            scope = normalizedScope,
            branchName = branchName?.trim()?.takeIf { it.isNotEmpty() },
            department = department?.trim()?.takeIf { it.isNotEmpty() },
            notes = notes.trim(),
            commandId = GlobalId.parse(commandId).value,
        )
    }
}

data class ClosePayrollPeriodCommand(
    val periodId: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ClosePayrollPeriodCommand {
        require(periodId > 0)
        require(reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class ReopenPayrollPeriodCommand(
    val periodId: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ReopenPayrollPeriodCommand {
        require(periodId > 0)
        require(reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class PayrollAdvanceDeductionRequest(
    val employeeId: Long,
    val amountRial: Long,
) {
    fun validated(): PayrollAdvanceDeductionRequest {
        require(employeeId > 0)
        MoneyRial.of(amountRial)
        return this
    }
}

data class PayslipReplacementRequest(
    val employeeId: Long,
    val replacesPayslipId: Long,
) {
    init {
        require(employeeId > 0 && replacesPayslipId > 0)
    }
}

data class CalculatePayrollBatchCommand(
    val batchId: Long,
    val employeeIds: List<Long>,
    val advanceDeductions: List<PayrollAdvanceDeductionRequest> = emptyList(),
    val replacements: List<PayslipReplacementRequest> = emptyList(),
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): CalculatePayrollBatchCommand {
        require(batchId > 0)
        require(employeeIds.isNotEmpty() && employeeIds.all { it > 0 })
        require(employeeIds.distinct().size == employeeIds.size) { "payroll_employee_duplicate" }
        val advances = advanceDeductions.map { it.validated() }
        require(advances.map { it.employeeId }.distinct().size == advances.size) { "payroll_advance_request_duplicate" }
        require(advances.all { it.employeeId in employeeIds }) { "payroll_advance_employee_outside_batch" }
        require(replacements.map { it.employeeId }.distinct().size == replacements.size) { "payroll_replacement_duplicate" }
        require(replacements.all { it.employeeId in employeeIds }) { "payroll_replacement_employee_outside_batch" }
        return copy(
            employeeIds = employeeIds.sorted(),
            advanceDeductions = advances.sortedBy { it.employeeId },
            replacements = replacements.sortedBy { it.employeeId },
            commandId = GlobalId.parse(commandId).value,
        )
    }
}

data class PayrollBatchCalculationOutcome(
    val batchId: Long,
    val payslipIds: List<Long>,
    val exceptions: List<PayrollExceptionRecord>,
    val idempotentReplay: Boolean,
) {
    val hasBlockingExceptions: Boolean get() = exceptions.any { it.blocking }
}

data class ReviewPayrollBatchCommand(
    val batchId: Long,
    val note: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ReviewPayrollBatchCommand {
        require(batchId > 0 && note.trim().length in 3..500)
        return copy(note = note.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class ApprovePayrollBatchCommand(
    val batchId: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ApprovePayrollBatchCommand {
        require(batchId > 0 && reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class PayPayslipCommand(
    val payslipId: Long,
    val amountRial: Long,
    val treasuryAccountId: String,
    val channel: TreasuryChannel,
    val paymentEpochDay: Long,
    val paymentReference: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PayPayslipCommand {
        require(payslipId > 0 && paymentEpochDay > 0)
        require(amountRial > 0)
        MoneyRial.of(amountRial)
        TreasuryAccountId.parse(treasuryAccountId)
        require(channel in setOf(TreasuryChannel.CASH, TreasuryChannel.BANK)) { "payroll_payment_channel_invalid" }
        require(paymentReference.trim().length in 2..120)
        return copy(paymentReference = paymentReference.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class ReversePayrollPaymentCommand(
    val paymentId: Long,
    val reversalEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ReversePayrollPaymentCommand {
        require(paymentId > 0 && reversalEpochDay > 0)
        require(reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class ReversePayslipCommandV2(
    val payslipId: Long,
    val reversalEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ReversePayslipCommandV2 {
        require(payslipId > 0 && reversalEpochDay > 0)
        require(reason.trim().length in 3..500)
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class ManualPayrollAdjustmentCommand(
    val employeeId: Long,
    val periodId: Long,
    val componentType: PayrollComponentType,
    val direction: PayrollComponentDirection,
    val amountRial: Long,
    val reason: String,
    val attachmentMetadata: String? = null,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ManualPayrollAdjustmentCommand {
        require(employeeId > 0 && periodId > 0)
        require(componentType !in setOf(PayrollComponentType.LEGACY_TOTAL, PayrollComponentType.LEGACY_UNKNOWN))
        require(amountRial > 0)
        MoneyRial.of(amountRial)
        val earningTypes = setOf(
            PayrollComponentType.BONUS,
            PayrollComponentType.ALLOWANCE,
            PayrollComponentType.COMMISSION,
            PayrollComponentType.OTHER_EARNING,
        )
        val deductionTypes = setOf(
            PayrollComponentType.INSURANCE,
            PayrollComponentType.TAX,
            PayrollComponentType.ABSENCE_DEDUCTION,
            PayrollComponentType.LATE_DEDUCTION,
            PayrollComponentType.UNPAID_LEAVE_DEDUCTION,
            PayrollComponentType.ADVANCE_DEDUCTION,
            PayrollComponentType.LOAN_DEDUCTION,
            PayrollComponentType.OTHER_DEDUCTION,
        )
        require(
            (direction == PayrollComponentDirection.EARNING && componentType in earningTypes) ||
                (direction == PayrollComponentDirection.DEDUCTION && componentType in deductionTypes),
        ) { "payroll_component_direction_mismatch" }
        require(reason.trim().length in 3..500)
        require(attachmentMetadata == null || attachmentMetadata.length <= 500)
        return copy(
            reason = reason.trim(),
            attachmentMetadata = attachmentMetadata?.trim(),
            commandId = GlobalId.parse(commandId).value,
        )
    }
}

data class ApproveManualAdjustmentCommand(
    val adjustmentId: Long,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ApproveManualAdjustmentCommand {
        require(adjustmentId > 0)
        return copy(commandId = GlobalId.parse(commandId).value)
    }
}

data class PayrollPaymentRecordV2(
    val id: Long,
    val payslipId: Long,
    val amountRial: Long,
    val treasuryAccountId: String,
    val paymentEpochDay: Long,
    val paymentReference: String,
    val status: PayrollPaymentStatus,
    val journalEntryId: Long?,
    val reversalOfPaymentId: Long?,
    val correlationId: String,
)

data class PayrollApprovalRecordV2(
    val id: Long,
    val eventType: String,
    val fromStatus: String,
    val toStatus: String,
    val actorId: Long,
    val reason: String,
    val createdAtEpochMillis: Long,
)

data class ManualPayrollAdjustmentRecordV2(
    val id: Long,
    val employeeId: Long,
    val periodId: Long,
    val componentType: PayrollComponentType,
    val direction: PayrollComponentDirection,
    val amountRial: Long,
    val reason: String,
    val status: ManualAdjustmentStatus,
    val createdByActorId: Long,
    val approvedByActorId: Long?,
)

data class PayrollPayslipDetailV2(
    val payslip: PayrollPayslipRecordV2,
    val period: PayrollPeriodRecordV2,
    val batch: PayrollBatchRecordV2,
    val snapshot: PayrollInputSnapshot?,
    val components: List<PayrollComponentDraftV2>,
    val payments: List<PayrollPaymentRecordV2>,
    val approvalHistory: List<PayrollApprovalRecordV2>,
    val advanceAllocations: List<AdvanceDeductionAllocation>,
    val accrualJournalEntryId: Long?,
    val reversalJournalEntryId: Long?,
)

interface HrPayrollCommandService {
    val periods: Flow<List<PayrollPeriodRecordV2>>
    val batches: Flow<List<PayrollBatchRecordV2>>

    fun employeePayslips(
        employeeId: Long,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<PayrollPayslipRecordV2>>
    fun employeeTimeline(
        employeeId: Long,
        limit: Int = 100,
        offset: Int = 0,
    ): Flow<List<EmployeeTimelineItem>>
    suspend fun payslipDetail(payslipId: Long): PayrollPayslipDetailV2
    suspend fun manualAdjustments(periodId: Long): List<ManualPayrollAdjustmentRecordV2>
    suspend fun openPeriod(draft: PayrollPeriodDraftV2): Long
    suspend fun closePeriod(command: ClosePayrollPeriodCommand)
    suspend fun reopenPeriod(command: ReopenPayrollPeriodCommand)
    suspend fun createBatch(draft: PayrollBatchDraftV2): Long
    suspend fun calculateBatch(command: CalculatePayrollBatchCommand): PayrollBatchCalculationOutcome
    suspend fun submitBatchForReview(command: ReviewPayrollBatchCommand)
    suspend fun approveBatch(command: ApprovePayrollBatchCommand)
    suspend fun submitManualAdjustment(command: ManualPayrollAdjustmentCommand): Long
    suspend fun approveManualAdjustment(command: ApproveManualAdjustmentCommand)
    suspend fun payPayslip(command: PayPayslipCommand): Long
    suspend fun reversePayment(command: ReversePayrollPaymentCommand): Long
    suspend fun reversePayslip(command: ReversePayslipCommandV2)
}
