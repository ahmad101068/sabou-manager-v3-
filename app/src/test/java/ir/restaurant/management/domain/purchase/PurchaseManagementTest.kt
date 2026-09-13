package ir.restaurant.management.domain.purchase

import ir.restaurant.management.core.MoneyRial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseManagementTest {
    @Test
    fun settlement_trimsAuditText() {
        val valid = PurchaseSettlementDraft(
            purchaseId = 12,
            settlementEpochDay = 100,
            amount = MoneyRial.of(250_000),
            paymentMethod = SettlementPaymentMethod.TRANSFER,
            referenceNo = "  TR-14  ",
            notes = "  پرداخت مرحله اول  ",
        ).validated()

        assertEquals("TR-14", valid.referenceNo)
        assertEquals("پرداخت مرحله اول", valid.notes)
    }

    @Test
    fun zeroSettlement_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseSettlementDraft(
                purchaseId = 12,
                settlementEpochDay = 100,
                amount = MoneyRial.ZERO,
                paymentMethod = SettlementPaymentMethod.CASH,
            ).validated()
        }
    }


    @Test
    fun settlementReversal_trimsReasonAndRejectsShortText() {
        val valid = PurchaseSettlementReversalDraft(
            purchaseId = 1,
            settlementJournalEntryId = 8,
            reversalEpochDay = 120,
            reason = "  ثبت دوباره پرداخت  ",
        ).validated()

        assertEquals("ثبت دوباره پرداخت", valid.reason)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(reason = "ن").validated()
        }
    }

    @Test
    fun partiallySettledPayable_exposesCorrectCapabilities() {
        val details = details(
            paidRial = 300,
            paymentStatus = PurchasePaymentStatus.PARTIAL,
            paymentMethod = PurchasePaymentMethod.PAYABLE,
        )

        assertEquals(700, details.outstandingRial)
        assertTrue(details.canSettle)
        assertFalse(details.canReverse)
    }

    @Test
    fun directPaidPurchase_canBeReversedButNotSettled() {
        val details = details(
            paidRial = 1_000,
            paymentStatus = PurchasePaymentStatus.PAID,
            paymentMethod = PurchasePaymentMethod.TRANSFER,
        )

        assertFalse(details.canSettle)
        assertTrue(details.canReverse)
    }

    private fun details(
        paidRial: Long,
        paymentStatus: PurchasePaymentStatus,
        paymentMethod: PurchasePaymentMethod,
    ) = PurchaseDetails(
        id = 1,
        invoiceNo = "P-1",
        supplierName = "نمونه",
        purchaseEpochDay = 1,
        dueEpochDay = 10,
        totalRial = 1_000,
        paidRial = paidRial,
        paymentStatus = paymentStatus,
        paymentMethod = paymentMethod,
        reminderEnabled = true,
        reminderEpochDay = 9,
        lines = emptyList(),
        settlements = emptyList(),
    )
}
