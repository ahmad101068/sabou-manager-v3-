package ir.restaurant.management.domain.control

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class EnterpriseControlDraftsTest {
    @Test fun `accounting period requires ordered dates and an auditable reason`() {
        val valid = AccountingPeriodDraft(100, 130, "  پایان ماه  ").validated()
        assertEquals("پایان ماه", valid.reason)
        assertFailsWith<IllegalArgumentException> { AccountingPeriodDraft(130, 100, "پایان ماه").validated() }
        assertFailsWith<IllegalArgumentException> { AccountingPeriodDraft(100, 130, "کم").validated() }
    }

    @Test fun `cash reconciliation rejects negative channels`() {
        assertFailsWith<IllegalArgumentException> {
            CashReconciliationDraft(100, -1, 0, 0).validated()
        }
    }
}
