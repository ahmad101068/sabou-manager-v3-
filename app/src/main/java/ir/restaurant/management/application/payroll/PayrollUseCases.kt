package ir.restaurant.management.application.payroll

import ir.restaurant.management.domain.personnel.*

class PayrollUseCases(
    private val service: HrPayrollCommandService,
) {
    val periods get() = service.periods
    val batches get() = service.batches
    fun employeePayslips(employeeId: Long, limit: Int = 100, offset: Int = 0) = service.employeePayslips(employeeId, limit, offset)
    fun employeeTimeline(employeeId: Long, limit: Int = 100, offset: Int = 0) = service.employeeTimeline(employeeId, limit, offset)
    suspend fun payslipDetail(id: Long) = service.payslipDetail(id)
    suspend fun manualAdjustments(periodId: Long) = service.manualAdjustments(periodId)
    suspend fun openPeriod(draft: PayrollPeriodDraftV2) = service.openPeriod(draft)
    suspend fun closePeriod(command: ClosePayrollPeriodCommand) = service.closePeriod(command)
    suspend fun reopenPeriod(command: ReopenPayrollPeriodCommand) = service.reopenPeriod(command)
    suspend fun createBatch(draft: PayrollBatchDraftV2) = service.createBatch(draft)
    suspend fun calculateBatch(command: CalculatePayrollBatchCommand) = service.calculateBatch(command)
    suspend fun submitBatch(command: ReviewPayrollBatchCommand) = service.submitBatchForReview(command)
    suspend fun approveBatch(command: ApprovePayrollBatchCommand) = service.approveBatch(command)
    suspend fun submitAdjustment(command: ManualPayrollAdjustmentCommand) = service.submitManualAdjustment(command)
    suspend fun approveAdjustment(command: ApproveManualAdjustmentCommand) = service.approveManualAdjustment(command)
    suspend fun pay(command: PayPayslipCommand) = service.payPayslip(command)
    suspend fun reversePayment(command: ReversePayrollPaymentCommand) = service.reversePayment(command)
    suspend fun reversePayslip(command: ReversePayslipCommandV2) = service.reversePayslip(command)

}
