package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.Test

class PayrollLifecycleV2Test {
    @Test
    fun batchLifecycleSeparatesCalculationApprovalPaymentAndReversal() {
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.DRAFT, PayrollBatchStatus.CALCULATED)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.CALCULATED, PayrollBatchStatus.UNDER_REVIEW)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.UNDER_REVIEW, PayrollBatchStatus.APPROVED)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.APPROVED, PayrollBatchStatus.PAYMENT_PENDING)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.PAYMENT_PENDING, PayrollBatchStatus.PARTIALLY_PAID)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.PARTIALLY_PAID, PayrollBatchStatus.PAID)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.PAID, PayrollBatchStatus.PARTIALLY_PAID)
        PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.PARTIALLY_PAID, PayrollBatchStatus.REVERSED)
    }

    @Test
    fun directApprovalOrMutationAfterReversalIsRejected() {
        val directApproval = assertFailsWith<BusinessRuleViolation> {
            PayrollBatchStateMachine.requireTransition(PayrollBatchStatus.DRAFT, PayrollBatchStatus.APPROVED)
        }
        assertIs<BusinessError.InvalidStateTransition>(directApproval.error)

        val immutable = assertFailsWith<BusinessRuleViolation> {
            PayrollPayslipStateMachine.requireTransition(PayrollPayslipStatus.REVERSED, PayrollPayslipStatus.CALCULATED)
        }
        assertIs<BusinessError.InvalidStateTransition>(immutable.error)
    }

    @Test
    fun closedPeriodRequiresExplicitReopen() {
        PayrollPeriodStateMachine.requireTransition(PayrollPeriodStatus.CLOSED, PayrollPeriodStatus.REOPENED)
        val invalid = assertFailsWith<BusinessRuleViolation> {
            PayrollPeriodStateMachine.requireTransition(PayrollPeriodStatus.CLOSED, PayrollPeriodStatus.CALCULATING)
        }
        assertIs<BusinessError.InvalidStateTransition>(invalid.error)
    }
}
