package ir.restaurant.management.data.repository

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.ReceivableEntity
import java.util.ArrayDeque

/**
 * Canonical receivable allocation/read model.
 *
 * Phase-2 receivable master rows are settled only by ledger entries carrying the
 * explicit `RECEIVABLE:<id>` allocation. Legacy ledger rows without an explicit
 * allocation are aged with the historical FIFO policy and are intentionally
 * branch-unassigned because the legacy schema has no trustworthy branch id.
 */
internal data class CanonicalOpenReceivableLot(
    val stableKey: String,
    val partyId: Long,
    val branchId: Long?,
    val receivableId: Long?,
    val sourceLedgerId: Long?,
    val dueEpochDay: Long,
    val outstandingRial: Long,
)

internal object CanonicalReceivableAllocator {
    fun explicitLots(
        masters: List<ReceivableEntity>,
        ledger: List<CustomerReceivableLedgerEntity>,
    ): List<CanonicalOpenReceivableLot> {
        if (masters.isEmpty()) return emptyList()
        val balanceByAllocation = ledger
            .asSequence()
            .filter { parseReceivableReference(it.reference) != null }
            .groupBy { it.customerId to it.reference }
            .mapValues { (_, rows) ->
                rows.fold(0L) { total, row ->
                    SignedLongMath.add(total, SignedLongMath.subtract(row.debitRial, row.creditRial))
                }
            }

        return masters.mapNotNull { master ->
            val reference = receivableReference(master.id)
            val ledgerBalance = balanceByAllocation[master.partyId to reference] ?: 0L
            check(ledgerBalance == master.outstandingAmountRial) {
                "مانده Master و Ledger دریافتنی ${master.id} ناسازگار است."
            }
            ledgerBalance.takeIf { it > 0 }?.let {
                CanonicalOpenReceivableLot(
                    stableKey = "RECEIVABLE:${master.id}",
                    partyId = master.partyId,
                    branchId = master.branchId,
                    receivableId = master.id,
                    sourceLedgerId = null,
                    dueEpochDay = master.dueEpochDay ?: master.issueEpochDay,
                    outstandingRial = it,
                )
            }
        }
    }

    fun legacyLots(entries: List<CustomerReceivableLedgerEntity>): List<CanonicalOpenReceivableLot> {
        data class OpenDebit(
            val customerId: Long,
            val sourceLedgerId: Long,
            val dueEpochDay: Long,
            var remainingRial: Long,
        )

        val result = mutableListOf<CanonicalOpenReceivableLot>()
        entries
            .asSequence()
            .filter { parseReceivableReference(it.reference) == null }
            .groupBy { it.customerId }
            .forEach { (customerId, customerRows) ->
                val open = ArrayDeque<OpenDebit>()
                customerRows
                    .sortedWith(compareBy<CustomerReceivableLedgerEntity> { it.businessEpochDay }.thenBy { it.id })
                    .forEach { row ->
                        require(row.debitRial >= 0 && row.creditRial >= 0) { "گردش دریافتنی مبلغ منفی دارد." }
                        if (row.debitRial > 0) {
                            open.addLast(
                                OpenDebit(
                                    customerId = customerId,
                                    sourceLedgerId = row.id,
                                    dueEpochDay = row.dueEpochDay ?: row.businessEpochDay,
                                    remainingRial = row.debitRial,
                                ),
                            )
                        }
                        var credit = row.creditRial
                        while (credit > 0 && open.isNotEmpty()) {
                            val first = open.first()
                            val applied = minOf(credit, first.remainingRial)
                            first.remainingRial = SignedLongMath.subtract(first.remainingRial, applied)
                            credit = SignedLongMath.subtract(credit, applied)
                            if (first.remainingRial == 0L) open.removeFirst()
                        }
                    }
                open.forEach { lot ->
                    if (lot.remainingRial > 0) {
                        result += CanonicalOpenReceivableLot(
                            stableKey = "LEGACY_LEDGER:${lot.sourceLedgerId}",
                            partyId = lot.customerId,
                            branchId = null,
                            receivableId = null,
                            sourceLedgerId = lot.sourceLedgerId,
                            dueEpochDay = lot.dueEpochDay,
                            outstandingRial = lot.remainingRial,
                        )
                    }
                }
            }
        return result
    }

    fun receivableReference(receivableId: Long): String = "RECEIVABLE:$receivableId"

    fun parseReceivableReference(reference: String): Long? {
        if (!reference.startsWith("RECEIVABLE:")) return null
        return reference.substringAfter(':').toLongOrNull()?.takeIf { it > 0 }
    }
}

internal class CanonicalReceivableReadModel(private val database: AppDatabase) {
    suspend fun openLotsForBranch(branchId: Long): List<CanonicalOpenReceivableLot> {
        require(branchId > 0) { "شناسه شعبه معتبر نیست." }
        val ledger = database.customerReceivableDao().allLedger()
        return CanonicalReceivableAllocator.explicitLots(database.businessOperationsDao().openReceivables(branchId), ledger)
    }

    suspend fun openLotsForParty(partyId: Long): List<CanonicalOpenReceivableLot> {
        require(partyId > 0) { "طرف‌حساب معتبر نیست." }
        val partyIds = database.customerReceivableDao().mergedCustomerIds(partyId).distinct()
        val ledger = database.customerReceivableDao().ledgerForCustomers(partyIds)
        val explicit = CanonicalReceivableAllocator.explicitLots(database.businessOperationsDao().openReceivablesForParties(partyIds), ledger)
        val legacy = CanonicalReceivableAllocator.legacyLots(ledger)
        return explicit + legacy
    }

    suspend fun unassignedLegacyLots(): List<CanonicalOpenReceivableLot> =
        CanonicalReceivableAllocator.legacyLots(database.customerReceivableDao().allLedger())

    suspend fun overdueLotsForRule(branchId: Long, todayEpochDay: Long): List<CanonicalOpenReceivableLot> {
        require(branchId > 0 && todayEpochDay > 0)
        return (openLotsForBranch(branchId) + unassignedLegacyLots())
            .filter { it.outstandingRial > 0 && it.dueEpochDay < todayEpochDay }
    }
}
