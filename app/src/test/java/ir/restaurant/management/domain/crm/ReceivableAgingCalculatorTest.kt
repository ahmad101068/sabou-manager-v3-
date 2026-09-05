package ir.restaurant.management.domain.crm

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceivableAgingCalculatorTest {
    @Test
    fun fifoCreditSettlesOldestDueLots_beforeCurrentReceivable() {
        val today = 1_000L
        val result = ReceivableAgingCalculator.calculate(
            listOf(
                ledger(1, 800, 850, debit = 10_000),
                ledger(2, 900, 920, debit = 20_000),
                ledger(3, 960, 970, debit = 30_000),
                ledger(4, 1_000, 1_010, debit = 40_000),
                ledger(5, 1_000, null, credit = 25_000),
            ),
            today,
        )
        assertEquals(0L, result.over90Rial)
        assertEquals(5_000L, result.days61To90Rial)
        assertEquals(30_000L, result.days1To30Rial)
        assertEquals(40_000L, result.currentRial)
        assertEquals(75_000L, result.totalRial)
    }

    @Test
    fun overpaymentDoesNotCreateNegativeAging() {
        val result = ReceivableAgingCalculator.calculate(
            listOf(ledger(1, 900, 900, debit = 10_000), ledger(2, 950, null, credit = 15_000)),
            1_000,
        )
        assertEquals(0L, result.totalRial)
    }

    private fun ledger(
        id: Long,
        day: Long,
        due: Long?,
        debit: Long = 0,
        credit: Long = 0,
    ) = ReceivableLedgerRecord(id, 1, day, "TEST", debit, credit, "TEST", id, "", due)
}
