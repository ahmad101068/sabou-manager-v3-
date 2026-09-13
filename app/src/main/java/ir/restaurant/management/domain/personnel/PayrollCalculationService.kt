package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.FixedPointRounding
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

object PayrollCalculationService {
    fun calculate(command: PayrollCalculationCommand): PayrollCalculationResultV2 {
        val valid = command.validated()
        val input = valid.snapshot
        val components = buildList {
            val proratedBase = FixedPointRatio.multiplyDivide(
                value = input.baseSalaryRial,
                multiplier = input.eligiblePeriodMinutes.toLong(),
                divisor = input.standardPeriodMinutes.toLong(),
                rounding = FixedPointRounding.HALF_UP,
            )
            if (proratedBase > 0) {
                add(
                    component(
                        type = PayrollComponentType.BASE_SALARY,
                        description = "حقوق پایه دوره",
                        quantity = input.eligiblePeriodMinutes.toLong(),
                        rateRial = input.baseSalaryRial,
                        amountRial = proratedBase,
                        direction = PayrollComponentDirection.EARNING,
                        sourceType = PayrollComponentSourceType.CONTRACT,
                        sourceId = input.contractId,
                    ),
                )
            }
            if (input.overtimeMinutes > 0 && input.overtimeRateRialPerHour > 0) {
                val baseOvertime = FixedPointRatio.multiplyDivide(
                    value = input.overtimeRateRialPerHour,
                    multiplier = input.overtimeMinutes.toLong(),
                    divisor = 60,
                    rounding = FixedPointRounding.HALF_UP,
                )
                val overtime = FixedPointRatio.multiplyDivide(
                    value = baseOvertime,
                    multiplier = input.overtimeMultiplierBasisPoints.toLong(),
                    divisor = 10_000,
                    rounding = FixedPointRounding.HALF_UP,
                )
                if (overtime > 0) {
                    add(
                        component(
                            PayrollComponentType.OVERTIME,
                            "اضافه‌کاری ${input.overtimeMinutes} دقیقه",
                            input.overtimeMinutes.toLong(),
                            input.overtimeRateRialPerHour,
                            overtime,
                            PayrollComponentDirection.EARNING,
                            PayrollComponentSourceType.ATTENDANCE,
                            null,
                        ),
                    )
                }
            }
            addPremiumEarning(
                type = PayrollComponentType.NIGHT_DIFFERENTIAL,
                description = "فوق‌العاده شب‌کاری",
                minutes = input.nightMinutes,
                multiplierBasisPoints = input.nightMultiplierBasisPoints,
                input = input,
            )
            addPremiumEarning(
                type = PayrollComponentType.HOLIDAY_PREMIUM,
                description = "فوق‌العاده کار در تعطیل",
                minutes = input.holidayMinutes,
                multiplierBasisPoints = input.holidayMultiplierBasisPoints,
                input = input,
            )
            addMinuteDeduction(
                type = PayrollComponentType.ABSENCE_DEDUCTION,
                description = "کسر غیبت",
                minutes = input.absenceMinutes,
                input = input,
            )
            addMinuteDeduction(
                type = PayrollComponentType.LATE_DEDUCTION,
                description = "کسر تأخیر",
                minutes = input.lateMinutes,
                input = input,
            )
            addMinuteDeduction(
                type = PayrollComponentType.UNPAID_LEAVE_DEDUCTION,
                description = "کسر مرخصی بدون حقوق",
                minutes = input.unpaidLeaveMinutes,
                input = input,
                sourceType = PayrollComponentSourceType.LEAVE,
            )
            addAll(valid.approvedManualComponents.map { it.validated() })
            if (valid.approvedAdvanceDeductionRial > 0) {
                add(
                    component(
                        PayrollComponentType.ADVANCE_DEDUCTION,
                        "کسر مساعده تأییدشده",
                        null,
                        null,
                        valid.approvedAdvanceDeductionRial,
                        PayrollComponentDirection.DEDUCTION,
                        PayrollComponentSourceType.ADVANCE,
                        null,
                    ),
                )
            }
        }

        val earningsBeforeStatutory = sumDirection(components, PayrollComponentDirection.EARNING)
        val insurance = FixedPointRatio.multiplyDivide(
            earningsBeforeStatutory,
            input.insuranceBasisPoints.toLong(),
            10_000,
            FixedPointRounding.HALF_UP,
        )
        val tax = FixedPointRatio.multiplyDivide(
            earningsBeforeStatutory,
            input.taxBasisPoints.toLong(),
            10_000,
            FixedPointRounding.HALF_UP,
        )
        val finalComponents = buildList {
            addAll(components)
            if (insurance > 0) add(
                component(
                    PayrollComponentType.INSURANCE,
                    "بیمه سهم کارمند",
                    null,
                    null,
                    insurance,
                    PayrollComponentDirection.DEDUCTION,
                    PayrollComponentSourceType.POLICY,
                    input.payrollPolicyId,
                ),
            )
            if (tax > 0) add(
                component(
                    PayrollComponentType.TAX,
                    "مالیات حقوق",
                    null,
                    null,
                    tax,
                    PayrollComponentDirection.DEDUCTION,
                    PayrollComponentSourceType.POLICY,
                    input.payrollPolicyId,
                ),
            )
        }
        val gross = sumDirection(finalComponents, PayrollComponentDirection.EARNING)
        val deductions = sumDirection(finalComponents, PayrollComponentDirection.DEDUCTION)
        if (deductions > gross) {
            throw BusinessError.NegativeNetPay(null, SignedLongMath.subtract(gross, deductions)).asViolation()
        }
        val net = SignedLongMath.subtract(gross, deductions)
        MoneyRial.of(gross)
        MoneyRial.of(deductions)
        MoneyRial.of(net)
        return PayrollCalculationResultV2(
            snapshot = input,
            components = finalComponents,
            grossPayRial = gross,
            totalDeductionsRial = deductions,
            netPayRial = net,
            warnings = buildList {
                if (input.actualWorkMinutes == 0 && input.paidLeaveMinutes == 0) add("NO_RECORDED_WORK")
                if (input.overtimeMinutes > input.standardPeriodMinutes) add("EXCESSIVE_OVERTIME")
            },
        )
    }

    private fun MutableList<PayrollComponentDraftV2>.addPremiumEarning(
        type: PayrollComponentType,
        description: String,
        minutes: Int,
        multiplierBasisPoints: Int,
        input: PayrollInputSnapshot,
    ) {
        if (minutes <= 0 || multiplierBasisPoints <= 10_000) return
        val baseForMinutes = FixedPointRatio.multiplyDivide(
            value = input.baseSalaryRial,
            multiplier = minutes.toLong(),
            divisor = input.standardPeriodMinutes.toLong(),
            rounding = FixedPointRounding.HALF_UP,
        )
        val premium = FixedPointRatio.multiplyDivide(
            value = baseForMinutes,
            multiplier = (multiplierBasisPoints - 10_000).toLong(),
            divisor = 10_000,
            rounding = FixedPointRounding.HALF_UP,
        )
        if (premium > 0) add(
            component(
                type,
                "$description $minutes دقیقه",
                minutes.toLong(),
                null,
                premium,
                PayrollComponentDirection.EARNING,
                PayrollComponentSourceType.ATTENDANCE,
                null,
            ),
        )
    }

    private fun MutableList<PayrollComponentDraftV2>.addMinuteDeduction(
        type: PayrollComponentType,
        description: String,
        minutes: Int,
        input: PayrollInputSnapshot,
        sourceType: PayrollComponentSourceType = PayrollComponentSourceType.ATTENDANCE,
    ) {
        if (minutes <= 0) return
        val amount = FixedPointRatio.multiplyDivide(
            value = input.baseSalaryRial,
            multiplier = minutes.toLong(),
            divisor = input.standardPeriodMinutes.toLong(),
            rounding = FixedPointRounding.HALF_UP,
        )
        if (amount > 0) {
            add(
                component(
                    type,
                    "$description ($minutes دقیقه)",
                    minutes.toLong(),
                    null,
                    amount,
                    PayrollComponentDirection.DEDUCTION,
                    sourceType,
                    null,
                ),
            )
        }
    }

    private fun component(
        type: PayrollComponentType,
        description: String,
        quantity: Long?,
        rateRial: Long?,
        amountRial: Long,
        direction: PayrollComponentDirection,
        sourceType: PayrollComponentSourceType,
        sourceId: Long?,
    ) = PayrollComponentDraftV2(
        componentType = type,
        description = description,
        quantity = quantity,
        rateRial = rateRial,
        amountRial = amountRial,
        direction = direction,
        sourceType = sourceType,
        sourceId = sourceId,
    ).validated()

    private fun sumDirection(
        components: List<PayrollComponentDraftV2>,
        direction: PayrollComponentDirection,
    ): Long = components.asSequence()
        .filter { it.direction == direction }
        .fold(0L) { total, component -> SignedLongMath.add(total, component.amountRial) }
}

