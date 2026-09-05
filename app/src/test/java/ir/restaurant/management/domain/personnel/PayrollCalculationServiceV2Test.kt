package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class PayrollCalculationServiceV2Test {
    @Test
    fun calculatesFullComponentLedgerUsingHalfUpIntegerMoney() {
        val result = calculate(
            snapshot = snapshot(),
            manual = listOf(
                manual(PayrollComponentType.BONUS, PayrollComponentDirection.EARNING, 1_000_000),
                manual(PayrollComponentType.ALLOWANCE, PayrollComponentDirection.EARNING, 500_000),
            ),
            advance = 400_000,
        )

        assertEquals(19_200_000L, result.grossPayRial)
        assertEquals(5_289_000L, result.totalDeductionsRial)
        assertEquals(13_911_000L, result.netPayRial)
        assertEquals(
            setOf(
                PayrollComponentType.BASE_SALARY,
                PayrollComponentType.OVERTIME,
                PayrollComponentType.BONUS,
                PayrollComponentType.ALLOWANCE,
                PayrollComponentType.ABSENCE_DEDUCTION,
                PayrollComponentType.LATE_DEDUCTION,
                PayrollComponentType.UNPAID_LEAVE_DEDUCTION,
                PayrollComponentType.ADVANCE_DEDUCTION,
                PayrollComponentType.INSURANCE,
                PayrollComponentType.TAX,
            ),
            result.components.map { it.componentType }.toSet(),
        )
        assertTrue(result.components.all { it.amountRial > 0 })
    }

    @Test
    fun paidLeaveDoesNotCreateAbsenceOrUnpaidLeaveDeduction() {
        val result = calculate(
            snapshot = snapshot().copy(
                actualWorkMinutes = 0,
                overtimeMinutes = 0,
                absenceMinutes = 0,
                lateMinutes = 0,
                paidLeaveMinutes = 480,
                unpaidLeaveMinutes = 0,
                insuranceBasisPoints = 0,
                taxBasisPoints = 0,
            ),
        )

        assertFalse(result.components.any { it.componentType == PayrollComponentType.ABSENCE_DEDUCTION })
        assertFalse(result.components.any { it.componentType == PayrollComponentType.UNPAID_LEAVE_DEDUCTION })
        assertFalse("NO_RECORDED_WORK" in result.warnings)
    }

    @Test
    fun roundingIsExplicitHalfUpAndCalculationIsDeterministic() {
        val input = snapshot().copy(
            baseSalaryRial = 101,
            standardPeriodMinutes = 2,
            eligiblePeriodMinutes = 1,
            actualWorkMinutes = 1,
            overtimeMinutes = 0,
            absenceMinutes = 0,
            lateMinutes = 0,
            paidLeaveMinutes = 0,
            unpaidLeaveMinutes = 0,
            insuranceBasisPoints = 0,
            taxBasisPoints = 0,
        )

        val first = calculate(input)
        val replay = calculate(input)

        assertEquals(51L, first.grossPayRial)
        assertEquals(first, replay)
    }

    @Test
    fun approvedHistoricalResultDoesNotDependOnLaterSalaryOrPolicyChanges() {
        val approvedSnapshot = snapshot()
        val historical = calculate(approvedSnapshot)
        val changedLiveState = approvedSnapshot.copy(
            baseSalaryRial = 99_000_000,
            overtimeRateRialPerHour = 9_000_000,
            insuranceBasisPoints = 1_000,
            payrollPolicyVersion = approvedSnapshot.payrollPolicyVersion + 1,
        )
        val future = calculate(changedLiveState)

        assertEquals(30_000_000L, historical.snapshot.baseSalaryRial)
        assertEquals(7, historical.snapshot.payrollPolicyVersion)
        assertEquals(13_066_000L, historical.netPayRial)
        assertNotEquals(historical.netPayRial, future.netPayRial)
        assertEquals(13_066_000L, historical.netPayRial)
    }

    @Test
    fun rejectsNegativeNetAndSafeMoneyOverflow() {
        val negative = assertFailsWith<BusinessRuleViolation> {
            calculate(snapshot().copy(insuranceBasisPoints = 10_000, taxBasisPoints = 10_000))
        }
        assertIs<BusinessError.NegativeNetPay>(negative.error)

        assertFailsWith<IllegalArgumentException> {
            calculate(
                snapshot().copy(
                    baseSalaryRial = MoneyRial.MAX_VALUE,
                    eligiblePeriodMinutes = 14_400,
                    overtimeMinutes = 0,
                    absenceMinutes = 0,
                    lateMinutes = 0,
                    unpaidLeaveMinutes = 0,
                    insuranceBasisPoints = 0,
                    taxBasisPoints = 0,
                ),
                manual = listOf(manual(PayrollComponentType.BONUS, PayrollComponentDirection.EARNING, 1)),
            )
        }
    }

    @Test
    fun nightAndHolidayPremiumsUseVersionedPolicyWithoutDoubleCountingBaseSalary() {
        val result = calculate(
            snapshot().copy(
                eligiblePeriodMinutes = 14_400,
                actualWorkMinutes = 14_400,
                overtimeMinutes = 0,
                absenceMinutes = 0,
                lateMinutes = 0,
                paidLeaveMinutes = 0,
                unpaidLeaveMinutes = 0,
                insuranceBasisPoints = 0,
                taxBasisPoints = 0,
                nightMinutes = 600,
                holidayMinutes = 480,
                nightMultiplierBasisPoints = 12_000,
                holidayMultiplierBasisPoints = 15_000,
            ),
        )

        assertEquals(30_750_000L, result.grossPayRial)
        assertEquals(250_000L, result.components.single { it.componentType == PayrollComponentType.NIGHT_DIFFERENTIAL }.amountRial)
        assertEquals(500_000L, result.components.single { it.componentType == PayrollComponentType.HOLIDAY_PREMIUM }.amountRial)
        assertEquals(30_750_000L, result.netPayRial)

        val changedFuturePolicy = calculate(result.snapshot.copy(nightMultiplierBasisPoints = 20_000, holidayMultiplierBasisPoints = 20_000))
        assertNotEquals(result.grossPayRial, changedFuturePolicy.grossPayRial)
        assertEquals(30_750_000L, calculate(result.snapshot).grossPayRial)
    }

    @Test
    fun accountingAccrualIsBalancedAndUsesSemanticDestinations() {
        val result = calculate(snapshot())
        val lines = PayrollAccountingPlanner.accrualLines(result.components, result.netPayRial)
        val debit = lines.sumOf { it.debit.value }
        val credit = lines.sumOf { it.credit.value }

        assertEquals(debit, credit)
        assertEquals(result.grossPayRial, debit)
        assertTrue(lines.any { it.role.name == "PAYROLL_PAYABLE" && it.credit.value == result.netPayRial })
        assertTrue(lines.any { it.role.name == "INSURANCE_PAYABLE" })
        assertTrue(lines.any { it.role.name == "TAX_PAYABLE" })
    }

    private fun calculate(
        snapshot: PayrollInputSnapshot,
        manual: List<PayrollComponentDraftV2> = emptyList(),
        advance: Long = 0,
    ) = PayrollCalculationService.calculate(
        PayrollCalculationCommand(
            commandId = GlobalId.parse("123e4567-e89b-42d3-a456-426614174000"),
            batchId = 4,
            snapshot = snapshot,
            approvedManualComponents = manual,
            approvedAdvanceDeductionRial = advance,
        ),
    )

    private fun snapshot() = PayrollInputSnapshot(
        employeeId = 7,
        employeeCode = "EMP-000007",
        employeeDisplayName = "کارمند نمونه",
        contractId = 11,
        contractVersionNo = 2,
        baseSalaryRial = 30_000_000,
        standardPeriodMinutes = 14_400,
        eligiblePeriodMinutes = 7_200,
        actualWorkMinutes = 7_000,
        overtimeMinutes = 90,
        absenceMinutes = 480,
        lateMinutes = 60,
        paidLeaveMinutes = 480,
        unpaidLeaveMinutes = 240,
        payrollPolicyId = 5,
        payrollPolicyVersion = 7,
        overtimeRateRialPerHour = 1_200_000,
        overtimeMultiplierBasisPoints = 15_000,
        insuranceBasisPoints = 700,
        taxBasisPoints = 1_000,
        calculationVersion = "HR_PAYROLL_2_V1",
    )

    private fun manual(
        type: PayrollComponentType,
        direction: PayrollComponentDirection,
        amount: Long,
    ) = PayrollComponentDraftV2(
        componentType = type,
        description = "تعدیل تأییدشده",
        quantity = null,
        rateRial = null,
        amountRial = amount,
        direction = direction,
        sourceType = PayrollComponentSourceType.MANUAL_ADJUSTMENT,
        sourceId = 9,
        manualOverride = true,
        overrideReason = "مصوبه مدیر",
        createdByActorId = 3,
    )
}
