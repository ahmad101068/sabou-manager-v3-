package ir.restaurant.management.ui

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.DomainFailure
import ir.restaurant.management.domain.common.JournalInvalidReason
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFailureMappingTest {
    @Test
    fun typedAccountingFailureGetsActionablePersianTextOnlyAtPresentationBoundary() {
        val failure: DomainFailure = BusinessError.InvalidJournal(JournalInvalidReason.UNBALANCED)

        val message = UiErrorHandler.run { failure.toPersianMessage() }

        assertTrue(message.contains("سند حسابداری"))
        assertTrue(message.contains("بدهکار/بستانکار"))
    }
}
