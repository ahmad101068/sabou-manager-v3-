package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.MoneyRial
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingPostingTest {
    @Test
    fun systemResolverOwnsConcreteChartCodes() {
        assertEquals("1101", SystemSemanticAccountResolver.codeFor(SemanticAccountRole.CASH))
        assertEquals("1301", SystemSemanticAccountResolver.codeFor(SemanticAccountRole.INVENTORY_ASSET))
        assertEquals("4101", SystemSemanticAccountResolver.codeFor(SemanticAccountRole.SALES_REVENUE))
        assertEquals("5101", SystemSemanticAccountResolver.codeFor(SemanticAccountRole.COGS))
    }

    @Test
    fun semanticJournalRequiresBalance() {
        SemanticJournalDraft(
            entryNo = "T-1",
            description = "test",
            entryEpochDay = 1,
            sourceType = "TEST",
            sourceId = 1,
            lines = listOf(
                SemanticJournalLine(SemanticAccountRole.CASH, debit = MoneyRial.of(100)),
                SemanticJournalLine(SemanticAccountRole.SALES_REVENUE, credit = MoneyRial.of(100)),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun semanticJournalRejectsUnbalancedLines() {
        SemanticJournalDraft(
            entryNo = "T-2",
            description = "test",
            entryEpochDay = 1,
            sourceType = "TEST",
            sourceId = 1,
            lines = listOf(
                SemanticJournalLine(SemanticAccountRole.CASH, debit = MoneyRial.of(100)),
                SemanticJournalLine(SemanticAccountRole.SALES_REVENUE, credit = MoneyRial.of(99)),
            ),
        )
    }
}
