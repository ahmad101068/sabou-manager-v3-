package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.treasury.TreasuryChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PayrollCalculatorTest {
    private fun draft() = PayrollDraft(
        employeeId = 1,
        periodYear = 1405,
        periodMonth = 4,
        overtimeRial = 2_000_000,
        bonusRial = 1_000_000,
        allowancesRial = 500_000,
        deductionsRial = 300_000,
        insuranceRial = 700_000,
        taxRial = 200_000,
        advanceDeductionRial = 400_000,
        paymentEpochDay = 20_000,
        paymentMethod = TreasuryChannel.BANK,
    )

    @Test fun calculatesGrossDeductionsAndNet() {
        val result = PayrollCalculator.calculate(20_000_000, draft())
        assertEquals(23_500_000, result.grossPayRial)
        assertEquals(1_600_000, result.totalDeductionsRial)
        assertEquals(21_900_000, result.netPayRial)
    }

    @Test fun rejectsNegativeNetPay() {
        val invalid = draft().copy(deductionsRial = 30_000_000)
        assertThrows(IllegalArgumentException::class.java) {
            PayrollCalculator.calculate(20_000_000, invalid)
        }
    }
}
