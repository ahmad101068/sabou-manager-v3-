package ir.restaurant.management.domain.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullCostCalculatorTest {
    @Test fun `calculates complete explainable breakdown`() {
        val result = FullCostCalculator.calculate(
            FullCostCalculator.Input(
                rawIngredientCostRial = 100_000,
                yieldMicros = 2_000_000,
                preparationWasteBasisPoints = 500,
                cookingWasteBasisPoints = 1_000,
                packagingCostRial = 10_000,
                directLaborCostRial = 20_000,
                allocatedOverheadRial = 5_000,
                salePriceRial = 250_000,
            ),
        )
        assertEquals(115_500, result.foodCostRial)
        assertEquals(15_500, result.wasteImpactRial)
        assertEquals(150_500, result.fullCostRial)
        assertEquals(134_500, result.foodMarginRial)
        assertEquals(99_500, result.fullMarginRial)
        assertEquals(4_620, result.foodCostBasisPoints)
        assertEquals(6_020, result.fullCostBasisPoints)
    }

    @Test fun `zero optional costs and zero price are safe`() {
        val result = FullCostCalculator.calculate(FullCostCalculator.Input(25, 1_000_000))
        assertEquals(25, result.foodCostRial)
        assertEquals(25, result.fullCostRial)
        assertNull(result.foodCostBasisPoints)
        assertNull(result.fullCostBasisPoints)
    }

    @Test fun `production yield metadata does not double scale per-unit ingredient cost`() {
        val onePortion = FullCostCalculator.calculate(FullCostCalculator.Input(80_000, 1_000_000))
        val batchYield = FullCostCalculator.calculate(FullCostCalculator.Input(80_000, 12_000_000))
        assertEquals(onePortion.foodCostRial, batchYield.foodCostRial)
    }

    @Test fun `aggregated multiple ingredient cost remains exact`() {
        val result = FullCostCalculator.calculate(FullCostCalculator.Input(30_000 + 45_000 + 12_500, 1_000_000))
        assertEquals(87_500, result.rawIngredientCostRial)
        assertEquals(87_500, result.fullCostRial)
    }

    @Test fun `high valid waste is deterministic`() {
        val result = FullCostCalculator.calculate(FullCostCalculator.Input(10_000, 1, 9_999, 9_999))
        assertEquals(39_996, result.foodCostRial)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid yield`() { FullCostCalculator.calculate(FullCostCalculator.Input(1, 0)) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid waste`() { FullCostCalculator.calculate(FullCostCalculator.Input(1, 1, 10_000)) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects negative input`() { FullCostCalculator.calculate(FullCostCalculator.Input(-1, 1)) }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects overflow`() { FullCostCalculator.calculate(FullCostCalculator.Input(Long.MAX_VALUE, 1, 1)) }
}
