package ir.restaurant.management.domain.personnel

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class PayrollReversalDraftTest {
    @Test fun validatesDateAndNormalizesReason() {
        val valid = PayrollReversalDraft(4, 20, "  اصلاح مبلغ  ").validated(10)
        assertEquals("اصلاح مبلغ", valid.reason)
        assertFailsWith<IllegalArgumentException> { PayrollReversalDraft(4, 9, "اصلاح مبلغ").validated(10) }
    }
}
