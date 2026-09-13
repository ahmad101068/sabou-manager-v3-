package ir.restaurant.management.domain.assets

import ir.restaurant.management.core.SignedLongMath

data class DepreciationCandidate(val assetId: Long, val purchaseCostRial: Long, val salvageValueRial: Long, val usefulLifeMonths: Int, val accumulatedRial: Long)
data class DepreciationProjection(val assetId: Long, val amountRial: Long, val closingAccumulatedRial: Long, val closingBookValueRial: Long)
object DepreciationRunCalculator {
    fun project(candidate: DepreciationCandidate): DepreciationProjection {
        require(candidate.purchaseCostRial >= 0 && candidate.salvageValueRial in 0..candidate.purchaseCostRial && candidate.usefulLifeMonths > 0)
        val depreciable = SignedLongMath.subtract(candidate.purchaseCostRial, candidate.salvageValueRial)
        require(candidate.accumulatedRial in 0..depreciable)
        val remaining = SignedLongMath.subtract(depreciable, candidate.accumulatedRial)
        val monthly = depreciable / candidate.usefulLifeMonths
        val amount = minOf(monthly, remaining)
        val closingAccumulated = SignedLongMath.add(candidate.accumulatedRial, amount)
        return DepreciationProjection(candidate.assetId, amount, closingAccumulated, SignedLongMath.subtract(candidate.purchaseCostRial, closingAccumulated))
    }
}
