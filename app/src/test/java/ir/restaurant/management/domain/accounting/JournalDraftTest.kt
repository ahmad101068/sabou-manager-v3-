package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.MoneyRial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JournalDraftTest {
    @Test
    fun rejectsUnbalancedJournal() {
        assertThrows(IllegalArgumentException::class.java) {
            BalancedJournalDraft(
                description = "سند نامتوازن",
                entryEpochDay = 1,
                sourceType = "TEST",
                sourceId = 1,
                lines = listOf(
                    JournalLineDraft("1301", debit = MoneyRial.of(100)),
                    JournalLineDraft("2101", credit = MoneyRial.of(99)),
                ),
            )
        }
    }

    @Test
    fun acceptsBalancedMultiLineManualJournal() {
        val journal = ManualJournalDraft(
            description = "ثبت هزینه روزانه",
            entryEpochDay = 20_000,
            lines = listOf(
                JournalLineDraft("6105", debit = MoneyRial.of(700)),
                JournalLineDraft("6102", debit = MoneyRial.of(300)),
                JournalLineDraft("1101", credit = MoneyRial.of(1_000)),
            ),
        ).validated(sourceId = 12)

        assertEquals(3, journal.lines.size)
        assertEquals("MANUAL", journal.sourceType)
        assertEquals(12L, journal.sourceId)
    }

    @Test
    fun rejectsLineThatIsBothDebitAndCredit() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalLineDraft(
                accountCode = "1101",
                debit = MoneyRial.of(100),
                credit = MoneyRial.of(100),
            )
        }
    }
}
