package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

object PayrollPeriodStateMachine {
    private val transitions = mapOf(
        PayrollPeriodStatus.OPEN to setOf(PayrollPeriodStatus.CALCULATING, PayrollPeriodStatus.CLOSED),
        PayrollPeriodStatus.CALCULATING to setOf(PayrollPeriodStatus.REVIEW, PayrollPeriodStatus.OPEN),
        PayrollPeriodStatus.REVIEW to setOf(PayrollPeriodStatus.APPROVED, PayrollPeriodStatus.CALCULATING),
        PayrollPeriodStatus.APPROVED to setOf(PayrollPeriodStatus.PAYMENT),
        PayrollPeriodStatus.PAYMENT to setOf(PayrollPeriodStatus.CLOSED),
        PayrollPeriodStatus.CLOSED to setOf(PayrollPeriodStatus.REOPENED),
        PayrollPeriodStatus.REOPENED to setOf(PayrollPeriodStatus.CALCULATING, PayrollPeriodStatus.CLOSED),
        PayrollPeriodStatus.LEGACY to emptySet(),
        PayrollPeriodStatus.LEGACY_UNKNOWN to emptySet(),
    )

    fun requireTransition(from: PayrollPeriodStatus, to: PayrollPeriodStatus) {
        if (from == to) return
        if (to !in transitions.getValue(from)) {
            throw BusinessError.InvalidStateTransition("PAYROLL_PERIOD", from.storedValue, to.storedValue).asViolation()
        }
    }
}

object PayrollBatchStateMachine {
    private val transitions = mapOf(
        PayrollBatchStatus.DRAFT to setOf(PayrollBatchStatus.CALCULATED, PayrollBatchStatus.CANCELLED),
        PayrollBatchStatus.CALCULATED to setOf(PayrollBatchStatus.UNDER_REVIEW, PayrollBatchStatus.DRAFT),
        PayrollBatchStatus.UNDER_REVIEW to setOf(PayrollBatchStatus.APPROVED, PayrollBatchStatus.CALCULATED),
        PayrollBatchStatus.APPROVED to setOf(PayrollBatchStatus.PAYMENT_PENDING, PayrollBatchStatus.REVERSED),
        PayrollBatchStatus.PAYMENT_PENDING to setOf(
            PayrollBatchStatus.PARTIALLY_PAID,
            PayrollBatchStatus.PAID,
            PayrollBatchStatus.REVERSED,
        ),
        PayrollBatchStatus.PARTIALLY_PAID to setOf(
            PayrollBatchStatus.PAYMENT_PENDING,
            PayrollBatchStatus.PAID,
            PayrollBatchStatus.REVERSED,
        ),
        PayrollBatchStatus.PAID to setOf(
            PayrollBatchStatus.PAYMENT_PENDING,
            PayrollBatchStatus.PARTIALLY_PAID,
            PayrollBatchStatus.REVERSED,
        ),
        PayrollBatchStatus.REVERSED to emptySet(),
        PayrollBatchStatus.CANCELLED to emptySet(),
        PayrollBatchStatus.LEGACY to emptySet(),
        PayrollBatchStatus.LEGACY_UNKNOWN to emptySet(),
    )

    fun requireTransition(from: PayrollBatchStatus, to: PayrollBatchStatus) {
        if (from == to) return
        if (to !in transitions.getValue(from)) {
            throw BusinessError.InvalidStateTransition("PAYROLL_BATCH", from.storedValue, to.storedValue).asViolation()
        }
    }
}

object PayrollPayslipStateMachine {
    private val transitions = mapOf(
        PayrollPayslipStatus.DRAFT to setOf(PayrollPayslipStatus.CALCULATED, PayrollPayslipStatus.CANCELLED),
        PayrollPayslipStatus.CALCULATED to setOf(PayrollPayslipStatus.UNDER_REVIEW, PayrollPayslipStatus.DRAFT),
        PayrollPayslipStatus.UNDER_REVIEW to setOf(PayrollPayslipStatus.APPROVED, PayrollPayslipStatus.CALCULATED),
        PayrollPayslipStatus.APPROVED to setOf(PayrollPayslipStatus.PAYMENT_PENDING, PayrollPayslipStatus.REVERSED),
        PayrollPayslipStatus.PAYMENT_PENDING to setOf(
            PayrollPayslipStatus.PARTIALLY_PAID,
            PayrollPayslipStatus.PAID,
            PayrollPayslipStatus.REVERSED,
        ),
        PayrollPayslipStatus.PARTIALLY_PAID to setOf(
            PayrollPayslipStatus.PAYMENT_PENDING,
            PayrollPayslipStatus.PAID,
            PayrollPayslipStatus.REVERSED,
        ),
        PayrollPayslipStatus.PAID to setOf(
            PayrollPayslipStatus.PAYMENT_PENDING,
            PayrollPayslipStatus.PARTIALLY_PAID,
            PayrollPayslipStatus.REVERSED,
        ),
        PayrollPayslipStatus.REVERSED to emptySet(),
        PayrollPayslipStatus.CANCELLED to emptySet(),
        PayrollPayslipStatus.LEGACY to emptySet(),
        PayrollPayslipStatus.LEGACY_UNKNOWN to emptySet(),
    )

    fun requireTransition(from: PayrollPayslipStatus, to: PayrollPayslipStatus) {
        if (from == to) return
        if (to !in transitions.getValue(from)) {
            throw BusinessError.InvalidStateTransition("PAYROLL_PAYSLIP", from.storedValue, to.storedValue).asViolation()
        }
    }
}
