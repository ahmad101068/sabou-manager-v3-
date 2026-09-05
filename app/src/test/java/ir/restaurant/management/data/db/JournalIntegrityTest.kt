package ir.restaurant.management.data.db

import kotlin.test.Test
import kotlin.test.assertFailsWith

class JournalIntegrityTest {
    @Test
    fun acceptsBalancedOneSidedLines() {
        JournalIntegrity.requireBalanced(
            listOf(
                line(debit = 1_000),
                line(credit = 1_000),
            ),
        )
    }

    @Test
    fun rejectsUnbalancedJournal() {
        assertFailsWith<IllegalArgumentException> {
            JournalIntegrity.requireBalanced(
                listOf(
                    line(debit = 1_000),
                    line(credit = 900),
                ),
            )
        }
    }

    @Test
    fun rejectsTwoSidedOrZeroLine() {
        assertFailsWith<IllegalArgumentException> {
            JournalIntegrity.requireBalanced(
                listOf(
                    line(debit = 1_000, credit = 1_000),
                    line(debit = 0, credit = 0),
                ),
            )
        }
    }

    @Test
    fun rejectsOverflow() {
        assertFailsWith<IllegalArgumentException> {
            JournalIntegrity.requireBalanced(
                listOf(
                    line(debit = Long.MAX_VALUE),
                    line(debit = 1),
                    line(credit = Long.MAX_VALUE),
                ),
            )
        }
    }

    private fun line(debit: Long = 0, credit: Long = 0) = JournalLineEntity(
        entryId = 1,
        accountCode = "1101",
        debitRial = debit,
        creditRial = credit,
    )
}
