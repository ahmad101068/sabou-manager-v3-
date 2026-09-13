package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine

/**
 * Converts an immutable payslip component ledger to semantic accrual lines. Concrete chart codes
 * stay owned by accounting. Deductions are always positive and their destination is explicit.
 */
object PayrollAccountingPlanner {
    fun accrualLines(
        components: List<PayrollComponentDraftV2>,
        netPayRial: Long,
    ): List<SemanticJournalLine> {
        MoneyRial.of(netPayRial)
        val debits = linkedMapOf<SemanticAccountRole, Long>()
        val credits = linkedMapOf<SemanticAccountRole, Long>()
        components.forEach { component ->
            val valid = component.validated()
            when (valid.direction) {
                PayrollComponentDirection.EARNING -> debits.add(
                    earningExpenseRole(valid.componentType),
                    valid.amountRial,
                )

                PayrollComponentDirection.DEDUCTION -> credits.add(
                    deductionDestinationRole(valid.componentType),
                    valid.amountRial,
                )
            }
        }
        if (netPayRial > 0) credits.add(SemanticAccountRole.PAYROLL_PAYABLE, netPayRial)
        if (debits.isEmpty() && credits.isEmpty()) return emptyList()
        val debitTotal = debits.values.fold(0L) { total, amount -> SignedLongMath.add(total, amount) }
        val creditTotal = credits.values.fold(0L) { total, amount -> SignedLongMath.add(total, amount) }
        require(debitTotal == creditTotal) { "payroll_accrual_unbalanced" }
        return buildList {
            debits.forEach { (role, amount) ->
                add(
                    SemanticJournalLine(
                        role = role,
                        debit = MoneyRial.of(amount),
                        memo = "شناسایی هزینه حقوق",
                    ),
                )
            }
            credits.forEach { (role, amount) ->
                add(
                    SemanticJournalLine(
                        role = role,
                        credit = MoneyRial.of(amount),
                        memo = "تعهد یا کسر حقوق",
                    ),
                )
            }
        }
    }

    private fun earningExpenseRole(type: PayrollComponentType): SemanticAccountRole = when (type) {
        PayrollComponentType.OVERTIME,
        PayrollComponentType.NIGHT_DIFFERENTIAL,
        PayrollComponentType.HOLIDAY_PREMIUM -> SemanticAccountRole.OVERTIME_EXPENSE
        PayrollComponentType.BONUS -> SemanticAccountRole.BONUS_EXPENSE
        PayrollComponentType.ALLOWANCE -> SemanticAccountRole.ALLOWANCE_EXPENSE
        PayrollComponentType.BASE_SALARY,
        PayrollComponentType.COMMISSION,
        PayrollComponentType.OTHER_EARNING,
        PayrollComponentType.LEGACY_TOTAL -> SemanticAccountRole.SALARY_EXPENSE

        PayrollComponentType.INSURANCE,
        PayrollComponentType.TAX,
        PayrollComponentType.ABSENCE_DEDUCTION,
        PayrollComponentType.LATE_DEDUCTION,
        PayrollComponentType.UNPAID_LEAVE_DEDUCTION,
        PayrollComponentType.ADVANCE_DEDUCTION,
        PayrollComponentType.LOAN_DEDUCTION,
        PayrollComponentType.OTHER_DEDUCTION,
        PayrollComponentType.LEGACY_UNKNOWN -> error("deduction_component_used_as_earning:${type.storedValue}")
    }

    private fun deductionDestinationRole(type: PayrollComponentType): SemanticAccountRole = when (type) {
        PayrollComponentType.INSURANCE -> SemanticAccountRole.INSURANCE_PAYABLE
        PayrollComponentType.TAX -> SemanticAccountRole.TAX_PAYABLE
        PayrollComponentType.ADVANCE_DEDUCTION,
        PayrollComponentType.LOAN_DEDUCTION -> SemanticAccountRole.EMPLOYEE_ADVANCE_RECEIVABLE

        PayrollComponentType.ABSENCE_DEDUCTION,
        PayrollComponentType.LATE_DEDUCTION,
        PayrollComponentType.UNPAID_LEAVE_DEDUCTION,
        PayrollComponentType.OTHER_DEDUCTION -> SemanticAccountRole.SALARY_EXPENSE

        PayrollComponentType.BASE_SALARY,
        PayrollComponentType.OVERTIME,
        PayrollComponentType.NIGHT_DIFFERENTIAL,
        PayrollComponentType.HOLIDAY_PREMIUM,
        PayrollComponentType.BONUS,
        PayrollComponentType.ALLOWANCE,
        PayrollComponentType.COMMISSION,
        PayrollComponentType.OTHER_EARNING,
        PayrollComponentType.LEGACY_TOTAL,
        PayrollComponentType.LEGACY_UNKNOWN -> error("earning_component_used_as_deduction:${type.storedValue}")
    }

    private fun MutableMap<SemanticAccountRole, Long>.add(role: SemanticAccountRole, amount: Long) {
        this[role] = SignedLongMath.add(getOrDefault(role, 0L), amount)
    }
}
