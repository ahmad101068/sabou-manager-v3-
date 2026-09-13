package ir.restaurant.management.domain.recipe

import ir.restaurant.management.core.SignedLongMath
import java.math.BigInteger

/**
 * Deterministic management-cost calculation for one sellable unit.
 *
 * Recipe ingredient quantities are already stored as actual input per sold unit. Yield therefore
 * remains production metadata and MUST NOT rescale inventory consumption. Preparation/cooking
 * waste rates are management uplifts over raw ingredient cost; they never create stock movements
 * or accounting COGS by themselves.
 */
object FullCostCalculator {
    const val BASIS_POINT_SCALE = 10_000
    const val MAX_WASTE_BASIS_POINTS = 9_999

    data class Input(
        val rawIngredientCostRial: Long,
        val yieldMicros: Long,
        val preparationWasteBasisPoints: Int = 0,
        val cookingWasteBasisPoints: Int = 0,
        val packagingCostRial: Long = 0,
        val directLaborCostRial: Long = 0,
        val allocatedOverheadRial: Long = 0,
        val salePriceRial: Long = 0,
    )

    data class Result(
        val rawIngredientCostRial: Long,
        val wasteImpactRial: Long,
        val foodCostRial: Long,
        val packagingCostRial: Long,
        val directLaborCostRial: Long,
        val allocatedOverheadRial: Long,
        val fullCostRial: Long,
        val foodMarginRial: Long,
        val fullMarginRial: Long,
        val foodCostBasisPoints: Int?,
        val fullCostBasisPoints: Int?,
    )

    fun calculate(input: Input): Result {
        require(input.yieldMicros > 0) { "بازده تولید باید بیشتر از صفر باشد." }
        require(input.preparationWasteBasisPoints in 0..MAX_WASTE_BASIS_POINTS) { "ضایعات آماده‌سازی نامعتبر است." }
        require(input.cookingWasteBasisPoints in 0..MAX_WASTE_BASIS_POINTS) { "ضایعات پخت نامعتبر است." }
        require(
            listOf(
                input.rawIngredientCostRial,
                input.packagingCostRial,
                input.directLaborCostRial,
                input.allocatedOverheadRial,
                input.salePriceRial,
            ).all { it >= 0 },
        ) { "مبالغ پروفایل هزینه نمی‌توانند منفی باشند." }

        val afterPreparation = uplift(input.rawIngredientCostRial, input.preparationWasteBasisPoints)
        val foodCost = uplift(afterPreparation, input.cookingWasteBasisPoints)
        val wasteImpact = SignedLongMath.subtract(foodCost, input.rawIngredientCostRial)
        val fullCost = listOf(
            foodCost,
            input.packagingCostRial,
            input.directLaborCostRial,
            input.allocatedOverheadRial,
        ).fold(0L, SignedLongMath::add)
        return Result(
            rawIngredientCostRial = input.rawIngredientCostRial,
            wasteImpactRial = wasteImpact,
            foodCostRial = foodCost,
            packagingCostRial = input.packagingCostRial,
            directLaborCostRial = input.directLaborCostRial,
            allocatedOverheadRial = input.allocatedOverheadRial,
            fullCostRial = fullCost,
            foodMarginRial = SignedLongMath.subtract(input.salePriceRial, foodCost),
            fullMarginRial = SignedLongMath.subtract(input.salePriceRial, fullCost),
            foodCostBasisPoints = ratioBasisPoints(foodCost, input.salePriceRial),
            fullCostBasisPoints = ratioBasisPoints(fullCost, input.salePriceRial),
        )
    }

    private fun uplift(value: Long, basisPoints: Int): Long = checked(
        BigInteger.valueOf(value).multiply(BigInteger.valueOf((BASIS_POINT_SCALE + basisPoints).toLong()))
            .divide(BigInteger.valueOf(BASIS_POINT_SCALE.toLong())),
    )

    private fun ratioBasisPoints(value: Long, base: Long): Int? {
        if (base <= 0) return null
        val ratio = BigInteger.valueOf(value).multiply(BigInteger.valueOf(BASIS_POINT_SCALE.toLong()))
            .divide(BigInteger.valueOf(base))
        require(ratio <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) { "درصد هزینه از محدوده امن خارج است." }
        return ratio.toInt()
    }

    private fun checked(value: BigInteger): Long {
        require(value <= BigInteger.valueOf(Long.MAX_VALUE)) { "محاسبه بهای کامل از محدوده امن خارج می‌شود." }
        return value.toLong()
    }
}
