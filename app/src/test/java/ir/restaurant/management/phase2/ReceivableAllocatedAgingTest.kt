package ir.restaurant.management.phase2

import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.ReceivableEntity
import ir.restaurant.management.data.repository.CanonicalReceivableAllocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceivableAllocatedAgingTest {
    private fun master(id: Long, amount: Long, due: Long) = ReceivableEntity(
        id = id,
        globalId = "r-$id",
        branchId = 2,
        partyId = 10,
        type = "PERSONAL",
        sourceType = "DAILY_SALES",
        sourceId = 100 + id,
        originalAmountRial = amount,
        paidAmountRial = 0,
        outstandingAmountRial = amount,
        issueEpochDay = 100,
        dueEpochDay = due,
        status = "OPEN",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
    )

    private fun ledger(
        id: Long,
        debit: Long,
        credit: Long,
        reference: String,
        due: Long,
    ) = CustomerReceivableLedgerEntity(
        id = id,
        customerId = 10,
        businessEpochDay = 100 + id,
        entryType = if (debit > 0) "CREDIT_SALE" else "COLLECTION",
        debitRial = debit,
        creditRial = credit,
        sourceType = "TEST",
        sourceId = id,
        reference = reference,
        dueEpochDay = due,
        actorId = 1,
        createdAtEpochMillis = id,
    )

    @Test
    fun collectionOnFutureReceivableDoesNotReduceOlderOverdueReceivable() {
        val a = master(1, 8_000_000, 90)
        val b = master(2, 4_000_000, 120).copy(paidAmountRial = 4_000_000, outstandingAmountRial = 0, status = "PAID")
        val entries = listOf(
            ledger(1, 8_000_000, 0, "RECEIVABLE:1", 90),
            ledger(2, 4_000_000, 0, "RECEIVABLE:2", 120),
            ledger(3, 0, 4_000_000, "RECEIVABLE:2", 120),
        )

        val lots = CanonicalReceivableAllocator.explicitLots(listOf(a, b), entries)
        assertEquals(1, lots.size)
        assertEquals(1L, lots.single().receivableId)
        assertEquals(8_000_000, lots.single().outstandingRial)
        assertEquals(90L, lots.single().dueEpochDay)
    }

    @Test
    fun legacyFallbackIsOnlyForEntriesWithoutExplicitAllocation() {
        val entries = listOf(
            ledger(10, 8_000_000, 0, "", 90),
            ledger(11, 4_000_000, 0, "RECEIVABLE:2", 120),
            ledger(12, 0, 4_000_000, "RECEIVABLE:2", 120),
        )
        val legacy = CanonicalReceivableAllocator.legacyLots(entries)
        assertEquals(1, legacy.size)
        assertEquals(8_000_000, legacy.single().outstandingRial)
        assertEquals("LEGACY_LEDGER:10", legacy.single().stableKey)
        assertNull(legacy.single().branchId)
    }
}
