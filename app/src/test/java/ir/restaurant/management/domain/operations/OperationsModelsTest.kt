package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus

class OperationsModelsTest {
    @Test
    fun supplierDraft_trimsStoredText() {
        val valid = SupplierDraft(
            name = "  لبنیات نمونه  ",
            phone = " 07100000000 ",
            paymentTermsDays = 12,
        ).validated()

        assertEquals("لبنیات نمونه", valid.name)
        assertEquals("07100000000", valid.phone)
        assertEquals(12, valid.paymentTermsDays)
    }

    @Test
    fun negativeInventoryThreshold_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryItemDraft(
                name = "برنج ایرانی",
                category = "خشکبار",
                unit = "کیلو",
                alertEnabled = true,
                alertThresholdMicros = -1,
                supplierId = null,
            ).validated()
        }
    }

    @Test
    fun settlementReminder_onlyBecomesDueForOpenPurchase() {
        val purchase = PurchaseSummary(
            id = 1,
            invoiceNo = "P-1",
            supplierName = "نمونه",
            purchaseEpochDay = 1,
            dueEpochDay = 10,
            totalRial = 1_000,
            paidRial = 200,
            paymentStatus = PurchasePaymentStatus.PARTIAL,
            paymentMethod = PurchasePaymentMethod.PAYABLE,
            reminderEnabled = true,
            reminderEpochDay = 8,
        )

        assertEquals(true, purchase.reminderIsDue(8))
        assertEquals(false, purchase.reminderIsDue(7))
        assertEquals(800, purchase.outstandingRial)
    }

    @Test
    fun inventoryCountCommandId_isStableAndValidated() {
        val commandId = "123e4567-e89b-42d3-a456-426614174000"
        val valid = InventoryCountDraft(1, 900_000, 90_000, 20_000, "شمارش فیزیکی", commandId, locationId = 1).validated()

        assertEquals(commandId, valid.commandId)
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(commandId = "not-a-global-id").validated()
        }
    }
}
