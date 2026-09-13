package ir.restaurant.management.domain.personnel

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.Test

class PayrollPaymentLedgerV2Test {
    @Test
    fun derivesFullAndZeroNetSettlement() {
        val full = PayrollPaymentLedger.derive(200_000_000, listOf(posted(1, 200_000_000)))
        assertEquals(200_000_000L, full.paidAmountRial)
        assertEquals(0L, full.remainingAmountRial)
        assertEquals(PayrollPayslipStatus.PAID, full.status)

        val zero = PayrollPaymentLedger.derive(0, emptyList())
        assertEquals(PayrollPayslipStatus.PAID, zero.status)
    }

    @Test
    fun derivesMultiplePartialPayments() {
        val projection = PayrollPaymentLedger.derive(
            200_000_000,
            listOf(posted(1, 120_000_000), posted(2, 30_000_000)),
        )
        assertEquals(150_000_000L, projection.paidAmountRial)
        assertEquals(50_000_000L, projection.remainingAmountRial)
        assertEquals(PayrollPayslipStatus.PARTIALLY_PAID, projection.status)
    }

    @Test
    fun compensatingReversalRestoresRemainingAmount() {
        val projection = PayrollPaymentLedger.derive(
            200_000_000,
            listOf(
                PayrollPaymentLedgerEntry(1, 120_000_000, PayrollPaymentStatus.REVERSED, null),
                PayrollPaymentLedgerEntry(2, 120_000_000, PayrollPaymentStatus.REVERSED, 1),
            ),
        )
        assertEquals(0L, projection.paidAmountRial)
        assertEquals(200_000_000L, projection.remainingAmountRial)
        assertEquals(PayrollPayslipStatus.PAYMENT_PENDING, projection.status)
    }

    @Test
    fun rejectsOverpayment() {
        val failure = assertFailsWith<BusinessRuleViolation> {
            PayrollPaymentLedger.derive(200_000_000, listOf(posted(1, 200_000_001)))
        }
        assertIs<BusinessError.InvalidInput>(failure.error)
    }

    @Test
    fun rejectsDuplicateOrOrphanReversal() {
        assertFailsWith<IllegalStateException> {
            PayrollPaymentLedger.derive(
                200_000_000,
                listOf(PayrollPaymentLedgerEntry(2, 20_000_000, PayrollPaymentStatus.REVERSED, 1)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PayrollPaymentLedger.derive(
                200_000_000,
                listOf(
                    PayrollPaymentLedgerEntry(1, 20_000_000, PayrollPaymentStatus.REVERSED, null),
                    PayrollPaymentLedgerEntry(2, 20_000_000, PayrollPaymentStatus.REVERSED, 1),
                    PayrollPaymentLedgerEntry(3, 20_000_000, PayrollPaymentStatus.REVERSED, 1),
                ),
            )
        }
    }

    private fun posted(id: Long, amountRial: Long) = PayrollPaymentLedgerEntry(
        id = id,
        amountRial = amountRial,
        status = PayrollPaymentStatus.POSTED,
        reversalOfPaymentId = null,
    )
}
