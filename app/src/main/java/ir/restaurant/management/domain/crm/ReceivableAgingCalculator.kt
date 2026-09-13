package ir.restaurant.management.domain.crm

import ir.restaurant.management.core.SignedLongMath

/**
 * Deterministic FIFO aging shared by CRM and the alert engine.
 * Debits open receivable lots at their due date; credits settle the oldest open lot first.
 */
object ReceivableAgingCalculator {
    fun calculate(entries: List<ReceivableLedgerRecord>, todayEpochDay: Long): ReceivableAging {
        require(todayEpochDay > 0) { "تاریخ مبنای Aging معتبر نیست." }
        data class OpenDebit(val dueEpochDay: Long, var remainingRial: Long)
        val open = ArrayDeque<OpenDebit>()
        entries.sortedWith(compareBy<ReceivableLedgerRecord> { it.businessEpochDay }.thenBy { it.id }).forEach { row ->
            require(row.debitRial >= 0 && row.creditRial >= 0) { "گردش دریافتنی مبلغ منفی دارد." }
            if (row.debitRial > 0) {
                open.addLast(OpenDebit(row.dueEpochDay ?: row.businessEpochDay, row.debitRial))
            }
            var credit = row.creditRial
            while (credit > 0 && open.isNotEmpty()) {
                val first = open.first()
                val applied = minOf(credit, first.remainingRial)
                first.remainingRial = Math.subtractExact(first.remainingRial, applied)
                credit = Math.subtractExact(credit, applied)
                if (first.remainingRial == 0L) open.removeFirst()
            }
            // A credit balance is valid (advance/overpayment) but it is not an overdue receivable.
        }

        var current = 0L
        var days1To30 = 0L
        var days31To60 = 0L
        var days61To90 = 0L
        var over90 = 0L
        open.forEach { lot ->
            if (lot.remainingRial <= 0L) return@forEach
            val overdueDays = Math.subtractExact(todayEpochDay, lot.dueEpochDay)
            when {
                overdueDays <= 0 -> current = SignedLongMath.add(current, lot.remainingRial)
                overdueDays <= 30 -> days1To30 = SignedLongMath.add(days1To30, lot.remainingRial)
                overdueDays <= 60 -> days31To60 = SignedLongMath.add(days31To60, lot.remainingRial)
                overdueDays <= 90 -> days61To90 = SignedLongMath.add(days61To90, lot.remainingRial)
                else -> over90 = SignedLongMath.add(over90, lot.remainingRial)
            }
        }
        return ReceivableAging(current, days1To30, days31To60, days61To90, over90)
    }
}
