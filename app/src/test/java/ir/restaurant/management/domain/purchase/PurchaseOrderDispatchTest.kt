package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseOrderDispatchTest {
    @Test fun normalizesSupplierConfirmationNumber() {
        val result = PurchaseOrderAcknowledgementDraft(12, "  CONF-77  ", 25_010).validated()
        assertEquals("CONF-77", result.supplierConfirmationNo)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingSupplierConfirmationNumber() {
        PurchaseOrderAcknowledgementDraft(12, " ", 25_010).validated()
    }
}
