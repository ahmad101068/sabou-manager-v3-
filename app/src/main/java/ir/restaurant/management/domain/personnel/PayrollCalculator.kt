package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.SignedLongMath

/** Pure payroll calculation engine. It has no Android or database dependency and is unit-testable. */
object PayrollCalculator {
    fun calculate(baseSalaryRial: Long, draft: PayrollDraft): PayrollCalculation {
        require(baseSalaryRial >= 0) { "حقوق پایه نمی‌تواند منفی باشد." }
        val valid = draft.validated()
        val gross = listOf(baseSalaryRial, valid.overtimeRial, valid.bonusRial, valid.allowancesRial)
            .fold(0L, SignedLongMath::add)
        val totalDeductions = listOf(
            valid.deductionsRial,
            valid.insuranceRial,
            valid.taxRial,
            valid.advanceDeductionRial,
        ).fold(0L, SignedLongMath::add)
        val net = SignedLongMath.subtract(gross, totalDeductions)
        require(net >= 0) { "خالص پرداخت نمی‌تواند منفی باشد." }
        return PayrollCalculation(
            baseSalaryRial = baseSalaryRial,
            overtimeRial = valid.overtimeRial,
            bonusRial = valid.bonusRial,
            allowancesRial = valid.allowancesRial,
            grossPayRial = gross,
            deductionsRial = valid.deductionsRial,
            insuranceRial = valid.insuranceRial,
            taxRial = valid.taxRial,
            advanceDeductionRial = valid.advanceDeductionRial,
            totalDeductionsRial = totalDeductions,
            netPayRial = net,
        )
    }
}
