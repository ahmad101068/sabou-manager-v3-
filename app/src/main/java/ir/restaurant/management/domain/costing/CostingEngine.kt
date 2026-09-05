package ir.restaurant.management.domain.costing

import ir.restaurant.management.core.SignedLongMath

data class StandardCostBreakdown(val foodRial: Long, val packagingRial: Long, val laborRial: Long, val overheadRial: Long) {
    init { require(listOf(foodRial, packagingRial, laborRial, overheadRial).all { it >= 0 }) }
    val fullCostRial: Long get() = listOf(foodRial, packagingRial, laborRial, overheadRial).fold(0L, SignedLongMath::add)
}
data class ActualCostBreakdown(val foodRial: Long, val laborRial: Long, val overheadRial: Long) {
    init { require(listOf(foodRial, laborRial, overheadRial).all { it >= 0 }) }
}
data class CostVariance(val foodRial: Long, val laborRial: Long, val overheadRial: Long)
object CostingEngine {
    fun variance(standard: StandardCostBreakdown, actual: ActualCostBreakdown) = CostVariance(
        SignedLongMath.subtract(actual.foodRial, standard.foodRial),
        SignedLongMath.subtract(actual.laborRial, standard.laborRial),
        SignedLongMath.subtract(actual.overheadRial, standard.overheadRial),
    )
}
