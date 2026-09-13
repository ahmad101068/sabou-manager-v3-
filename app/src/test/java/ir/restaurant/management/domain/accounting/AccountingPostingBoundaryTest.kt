package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.MoneyRial
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingPostingBoundaryTest {
    @Test
    fun commandNormalizesSourceAndRequiresBalancedFixedPointLines() {
        val command = AccountingPostingCommand(
            entryNo = " GR-10 ",
            sourceType = "goods receipt",
            sourceId = 10,
            businessEpochDay = 20_000,
            description = " دریافت کالا ",
            lines = listOf(
                SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, debit = MoneyRial.of(75_000)),
                SemanticJournalLine(SemanticAccountRole.GOODS_RECEIVED_NOT_INVOICED, credit = MoneyRial.of(75_000)),
            ),
            idempotencyKey = "GOODS_RECEIPT:10:post",
            correlationId = CorrelationId.parse("goods_receipt:10"),
            actorId = 7,
        ).validated()

        assertEquals("GOODS_RECEIPT", command.sourceType)
        assertEquals("GR-10", command.entryNo)
        assertEquals("دریافت کالا", command.description)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reversalRequiresARealBusinessReason() {
        AccountingReversalCommand(
            originalEntryId = 10,
            entryNo = "R-10",
            sourceType = "GOODS_RECEIPT_REVERSAL",
            sourceId = 10,
            businessEpochDay = 20_001,
            reason = "x",
            idempotencyKey = "GOODS_RECEIPT_REVERSAL:10:post",
            correlationId = CorrelationId.parse("goods_receipt_reversal:10"),
            actorId = 7,
        ).validated()
    }
}
