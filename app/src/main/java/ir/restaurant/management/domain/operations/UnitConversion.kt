package ir.restaurant.management.domain.operations

import ir.restaurant.management.core.toLongExactCompat
import java.math.BigInteger

/**
 * Exact rational conversion between purchase/recipe units and the inventory stock unit.
 * A factor N/D means: 1 source unit = N/D stock units.
 * Quantities are expressed in micros (1 unit = 1_000_000 micros).
 */
data class UnitConversionFactor(
    val numerator: Long,
    val denominator: Long,
) {
    init {
        require(numerator > 0) { "صورت ضریب تبدیل باید بیشتر از صفر باشد." }
        require(denominator > 0) { "مخرج ضریب تبدیل باید بیشتر از صفر باشد." }
    }

    fun toStockMicros(sourceMicros: Long): Long = scale(sourceMicros, numerator, denominator)

    fun fromStockMicros(stockMicros: Long): Long = scale(stockMicros, denominator, numerator)

    private fun scale(value: Long, multiply: Long, divide: Long): Long {
        require(value >= 0) { "مقدار تبدیل نمی‌تواند منفی باشد." }
        val product = BigInteger.valueOf(value).multiply(BigInteger.valueOf(multiply))
        val divisor = BigInteger.valueOf(divide)
        val result = product.divideAndRemainder(divisor)
        require(result[1] == BigInteger.ZERO) {
            "تبدیل واحد برای دقت فعلی QuantityMicros دقیق نیست."
        }
        return result[0].toLongExactCompat()
    }
}

data class InventoryUnitDefinition(
    val stockUnit: String,
    val purchaseUnit: String,
    val purchaseToStock: UnitConversionFactor,
    val recipeUnit: String,
    val recipeToStock: UnitConversionFactor,
) {
    init {
        require(stockUnit.isNotBlank()) { "واحد انبار الزامی است." }
        require(purchaseUnit.isNotBlank()) { "واحد خرید الزامی است." }
        require(recipeUnit.isNotBlank()) { "واحد رسپی الزامی است." }
    }
}
