package ir.restaurant.management.domain.purchase

import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseCalculatorTest {
    @Test
    fun calculatesInvoiceFromRoundedRialLines() {
        val draft = PurchaseDraft(
            invoiceNo = "P-100",
            supplierId = 1,
            purchaseEpochDay = 10,
            dueEpochDay = 22,
            paymentMethod = PurchasePaymentMethod.PAYABLE,
            reminderEnabled = true,
            reminderEpochDay = 20,
            lines = listOf(
                PurchaseLineDraft(
                    itemId = 1,
                    quantity = QuantityMicros.parse("0.333"),
                    unitCost = MoneyRial.of(101),
                ),
                PurchaseLineDraft(
                    itemId = 2,
                    quantity = QuantityMicros.parse("2.5"),
                    unitCost = MoneyRial.of(1_000),
                ),
            ),
        )

        val prepared = PurchaseCalculator.prepare(draft)

        assertEquals(34L, prepared.lines.first().total.value)
        assertEquals(2_534L, prepared.total.value)
    }

    @Test
    fun rejectsDuplicateItem() {
        val line = PurchaseLineDraft(1, QuantityMicros.positive(1), MoneyRial.of(1))
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseCalculator.prepare(
                PurchaseDraft(
                    invoiceNo = "P-1",
                    supplierId = 1,
                    purchaseEpochDay = 1,
                    dueEpochDay = 1,
                    paymentMethod = PurchasePaymentMethod.PAYABLE,
                    reminderEnabled = false,
                    reminderEpochDay = null,
                    lines = listOf(line, line),
                ),
            )
        }
    }

    @Test
    fun acceptsZeroCostInventoryWithoutCreatingFakeMoney() {
        val prepared = PurchaseCalculator.prepare(
            PurchaseDraft(
                invoiceNo = "FREE-1",
                supplierId = 1,
                purchaseEpochDay = 1,
                dueEpochDay = 1,
                paymentMethod = PurchasePaymentMethod.PAYABLE,
                reminderEnabled = false,
                reminderEpochDay = null,
                lines = listOf(
                    PurchaseLineDraft(
                        itemId = 1,
                        quantity = QuantityMicros.parse("2"),
                        unitCost = MoneyRial.ZERO,
                    ),
                ),
            ),
        )

        assertEquals(MoneyRial.ZERO, prepared.total)
    }

    @Test
    fun trimsInvoiceNumberBeforePosting() {
        val prepared = PurchaseCalculator.prepare(
            PurchaseDraft(
                invoiceNo = "  P-200  ",
                supplierId = 1,
                purchaseEpochDay = 1,
                dueEpochDay = 1,
                paymentMethod = PurchasePaymentMethod.PAYABLE,
                reminderEnabled = false,
                reminderEpochDay = null,
                lines = listOf(
                    PurchaseLineDraft(
                        itemId = 1,
                        quantity = QuantityMicros.positive(1),
                        unitCost = MoneyRial.of(10),
                    ),
                ),
            ),
        )

        assertEquals("P-200", prepared.draft.invoiceNo)
    }

    @Test
    fun rejectsZeroQuantityLine() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseCalculator.prepare(
                PurchaseDraft(
                    invoiceNo = "ZERO-QTY",
                    supplierId = 1,
                    purchaseEpochDay = 1,
                    dueEpochDay = 1,
                    paymentMethod = PurchasePaymentMethod.PAYABLE,
                    reminderEnabled = false,
                    reminderEpochDay = null,
                    lines = listOf(
                        PurchaseLineDraft(
                            itemId = 1,
                            quantity = QuantityMicros.ZERO,
                            unitCost = MoneyRial.of(100_000),
                        ),
                    ),
                ),
            )
        }
    }
}
