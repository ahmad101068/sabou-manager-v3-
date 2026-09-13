package ir.restaurant.management.domain.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuEngineeringCalculatorTest {
    @Test
    fun classifiesPopularAndProfitableItemsAsStars() {
        val result = MenuEngineeringCalculator.classify(listOf(
            MenuPerformanceInput(1, "برگر ویژه", 20, 2_000_000, 700_000),
            MenuPerformanceInput(2, "سالاد", 2, 100_000, 80_000),
        ))
        assertEquals(MenuQuadrant.STAR, result.first().quadrant)
    }
}
