package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.SignedLongMath

enum class LeaveLedgerEntryType { GRANT, USE, RESTORE, ADJUSTMENT }

data class LeaveLedgerEntry(
    val id: Long,
    val employeeId: Long,
    val leaveType: LeaveType,
    val entryType: LeaveLedgerEntryType,
    val amountMicros: Long,
    val leaveId: Long?,
)

data class PendingLeaveUsage(
    val employeeId: Long,
    val leaveType: LeaveType,
    val amountMicros: Long,
)

object LeaveBalanceCalculator {
    fun calculate(
        employeeId: Long,
        leaveType: LeaveType,
        ledger: List<LeaveLedgerEntry>,
        pending: List<PendingLeaveUsage>,
    ): LeaveBalance {
        require(employeeId > 0)
        val relevant = ledger.filter { it.employeeId == employeeId && it.leaveType == leaveType }
        val granted = relevant.filter { it.entryType in setOf(LeaveLedgerEntryType.GRANT, LeaveLedgerEntryType.ADJUSTMENT) }
            .fold(0L) { total, entry -> SignedLongMath.add(total, entry.amountMicros) }
        val used = relevant.filter { it.entryType == LeaveLedgerEntryType.USE }
            .fold(0L) { total, entry -> SignedLongMath.add(total, entry.amountMicros) }
        val restored = relevant.filter { it.entryType == LeaveLedgerEntryType.RESTORE }
            .fold(0L) { total, entry -> SignedLongMath.add(total, entry.amountMicros) }
        val pendingAmount = pending
            .filter { it.employeeId == employeeId && it.leaveType == leaveType }
            .fold(0L) { total, item -> SignedLongMath.add(total, item.amountMicros) }
        val consumed = SignedLongMath.subtract(used, restored)
        require(consumed >= 0) { "leave_restoration_exceeds_usage" }
        val remaining = SignedLongMath.subtract(SignedLongMath.subtract(granted, consumed), pendingAmount)
        return LeaveBalance(
            employeeId = employeeId,
            leaveType = leaveType,
            grantedMicros = granted,
            usedMicros = consumed,
            pendingMicros = pendingAmount,
            remainingMicros = remaining,
        )
    }
}

