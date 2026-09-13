package ir.restaurant.management.domain.personnel

import ir.restaurant.management.core.SignedLongMath

data class OpenAdvanceBalance(val advanceId: Long, val remainingRial: Long)
data class AdvanceDeductionAllocation(val advanceId: Long, val amountRial: Long)

object AdvanceDeductionAllocator {
    fun allocate(requestedRial: Long, advancesOldestFirst: List<OpenAdvanceBalance>): List<AdvanceDeductionAllocation> {
        require(requestedRial >= 0) { "کسر مساعده نمی‌تواند منفی باشد." }
        require(advancesOldestFirst.all { it.advanceId > 0 && it.remainingRial > 0 }) { "مانده مساعده معتبر نیست." }
        val available = advancesOldestFirst.fold(0L) { total, item -> SignedLongMath.add(total, item.remainingRial) }
        require(requestedRial <= available) { "کسر مساعده از مانده باز بیشتر است؛ مانده قابل کسر $available ریال است." }
        var remaining = requestedRial
        return buildList {
            for (advance in advancesOldestFirst) {
                if (remaining == 0L) break
                val allocated = minOf(remaining, advance.remainingRial)
                add(AdvanceDeductionAllocation(advance.advanceId, allocated))
                remaining = SignedLongMath.subtract(remaining, allocated)
            }
        }
    }
}
