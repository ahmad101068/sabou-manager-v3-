package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SupplierInvoiceNumberTest {
    @Test
    fun normalize_unifies_case_digits_scriptAndWhitespace() {
        assertEquals("INV-123کی", SupplierInvoiceNumber.normalize(" inv-۱۲٣ك ي "))
    }

    @Test
    fun normalize_removesWhitespaceButPreservesMeaningfulSeparators() {
        assertEquals("AB123", SupplierInvoiceNumber.normalize(" AB  123 "))
        assertNotEquals(SupplierInvoiceNumber.normalize("AB-123"), SupplierInvoiceNumber.normalize("AB/123"))
    }
}
