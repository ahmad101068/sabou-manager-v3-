package ir.restaurant.management.domain.personnel

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class LeaveBalanceV2Test {
    @Test
    fun separatesGrantedUsedPendingAndRemainingWithoutFakeBalance() {
        val result = LeaveBalanceCalculator.calculate(
            employeeId = 7,
            leaveType = LeaveType.ANNUAL,
            ledger = listOf(
                entry(1, LeaveLedgerEntryType.GRANT, 10_000_000),
                entry(2, LeaveLedgerEntryType.USE, 3_000_000),
                entry(3, LeaveLedgerEntryType.RESTORE, 1_000_000),
            ),
            pending = listOf(PendingLeaveUsage(7, LeaveType.ANNUAL, 2_000_000)),
        )

        assertEquals(10_000_000L, result.grantedMicros)
        assertEquals(2_000_000L, result.usedMicros)
        assertEquals(2_000_000L, result.pendingMicros)
        assertEquals(6_000_000L, result.remainingMicros)
    }

    @Test
    fun rejectsRestorationBeyondRecordedUsage() {
        assertFailsWith<IllegalArgumentException> {
            LeaveBalanceCalculator.calculate(
                7,
                LeaveType.ANNUAL,
                listOf(entry(1, LeaveLedgerEntryType.RESTORE, 1_000_000)),
                emptyList(),
            )
        }
    }

    private fun entry(id: Long, type: LeaveLedgerEntryType, amount: Long) = LeaveLedgerEntry(
        id = id,
        employeeId = 7,
        leaveType = LeaveType.ANNUAL,
        entryType = type,
        amountMicros = amount,
        leaveId = null,
    )
}
