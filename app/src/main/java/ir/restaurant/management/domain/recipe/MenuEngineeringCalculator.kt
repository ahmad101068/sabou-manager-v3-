package ir.restaurant.management.domain.recipe

import ir.restaurant.management.core.SignedLongMath
import java.math.BigInteger

data class MenuPerformanceInput(val menuItemId: Long, val name: String, val unitsSold: Long, val salesRial: Long, val costRial: Long) {
    fun validated(): MenuPerformanceInput {
        require(menuItemId > 0 && name.isNotBlank()) { "محصول منو معتبر نیست." }
        require(unitsSold >= 0 && salesRial >= 0 && costRial >= 0) { "آمار منو نمی‌تواند منفی باشد." }
        return this
    }
}

enum class MenuQuadrant { STAR, PLOWHORSE, PUZZLE, DOG }

data class MenuPerformanceResult(val menuItemId: Long, val name: String, val unitsSold: Long, val grossProfitRial: Long, val marginRialPerUnit: Long, val quadrant: MenuQuadrant)

object MenuEngineeringCalculator {
    fun classify(items: List<MenuPerformanceInput>): List<MenuPerformanceResult> {
        require(items.isNotEmpty()) { "برای تحلیل منو داده‌ای وجود ندارد." }
        val valid = items.map { it.validated() }
        val totalUnits = valid.fold(0L) { total, item -> SignedLongMath.add(total, item.unitsSold) }
        val margins = valid.map { if (it.unitsSold == 0L) 0L else it.salesRial / it.unitsSold - it.costRial / it.unitsSold }
        val totalMargin = margins.fold(0L, SignedLongMath::add)
        val itemCount = BigInteger.valueOf(valid.size.toLong())
        return valid.mapIndexed { index, item ->
            val margin = margins[index]
            val popular = BigInteger.valueOf(item.unitsSold).multiply(itemCount) >= BigInteger.valueOf(totalUnits)
            val profitable = BigInteger.valueOf(margin).multiply(itemCount) >= BigInteger.valueOf(totalMargin)
            val quadrant = when {
                popular && profitable -> MenuQuadrant.STAR
                popular -> MenuQuadrant.PLOWHORSE
                profitable -> MenuQuadrant.PUZZLE
                else -> MenuQuadrant.DOG
            }
            MenuPerformanceResult(item.menuItemId, item.name, item.unitsSold, SignedLongMath.subtract(item.salesRial, item.costRial), margin, quadrant)
        }
    }
}
