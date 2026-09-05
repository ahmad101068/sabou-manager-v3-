package ir.restaurant.management.domain.personnel

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class AdvanceDeductionAllocatorTest {
    @Test
    fun allocatesOldestAdvanceFirstAcrossMultipleBalances() {
        val allocations = AdvanceDeductionAllocator.allocate(
            700,
            listOf(OpenAdvanceBalance(11, 400), OpenAdvanceBalance(12, 500)),
        )
        assertEquals(listOf(AdvanceDeductionAllocation(11, 400), AdvanceDeductionAllocation(12, 300)), allocations)
    }

    @Test
    fun rejectsDeductionAboveOutstandingBalance() {
        assertFailsWith<IllegalArgumentException> {
            AdvanceDeductionAllocator.allocate(901, listOf(OpenAdvanceBalance(11, 400), OpenAdvanceBalance(12, 500)))
        }
    }
}
