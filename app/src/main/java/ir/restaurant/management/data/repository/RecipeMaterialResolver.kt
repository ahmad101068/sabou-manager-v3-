package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.core.toLongExactCompat
import java.math.BigInteger

internal data class ResolvedRecipeMaterial(
    val inventoryItemId: Long,
    val quantityMicros: Long,
)

/**
 * Expands one immutable recipe version into physical inventory requirements for a business date.
 * Child recipe VERSION ids are followed verbatim so later recipe edits cannot rewrite history.
 * Approved substitutions are effective only on/after their approval business day.
 */
internal class RecipeMaterialResolver(private val database: AppDatabase) {
    suspend fun resolve(
        recipeVersionId: Long,
        businessEpochDay: Long,
        outputQuantityMicros: Long,
    ): List<ResolvedRecipeMaterial> {
        require(recipeVersionId > 0 && businessEpochDay > 0 && outputQuantityMicros > 0)
        val totals = linkedMapOf<Long, Long>()
        expand(recipeVersionId, businessEpochDay, outputQuantityMicros, linkedSetOf(), totals)
        require(totals.isNotEmpty()) { "رسپی نهایی هیچ مصرف موجودی ندارد." }
        return totals.entries.sortedBy { it.key }.map { ResolvedRecipeMaterial(it.key, it.value) }
    }

    private suspend fun expand(
        versionId: Long,
        businessEpochDay: Long,
        multiplierMicros: Long,
        path: MutableSet<Long>,
        totals: MutableMap<Long, Long>,
    ) {
        require(path.add(versionId)) { "وابستگی حلقوی هنگام اجرای زیررسپی شناسایی شد." }
        database.recipeLifecycleDao().versionById(versionId) ?: error("نسخه رسپی پیدا نشد: $versionId")
        val substitutions = database.recipeLifecycleDao()
            .effectiveSubstitutions(versionId, businessEpochDay)
            .groupBy { it.originalInventoryItemId }
            .mapValues { (_, rows) -> rows.maxWith(compareBy({ it.effectiveFromEpochDay }, { it.id })) }

        database.recipeDao().versionIngredients(versionId).forEach { ingredient ->
            val baseQuantity = mulDiv(ingredient.quantityMicrosPerUnit, multiplierMicros, MICROS)
            require(baseQuantity > 0) { "مقدار ماده رسپی پس از توسعه نامعتبر است." }
            val substitution = substitutions[ingredient.inventoryItemId]
            val itemId = substitution?.substituteInventoryItemId ?: ingredient.inventoryItemId
            val quantity = substitution?.let { mulDiv(baseQuantity, it.ratioNumerator, it.ratioDenominator) } ?: baseQuantity
            require(quantity > 0) { "مقدار ماده جایگزین پس از تبدیل نامعتبر است." }
            totals[itemId] = Math.addExact(totals[itemId] ?: 0L, quantity)
        }

        database.recipeLifecycleDao().components(versionId).forEach { component ->
            val childMultiplier = mulDiv(multiplierMicros, component.quantityMicrosPerUnit, MICROS)
            require(childMultiplier > 0) { "مقدار زیررسپی پس از توسعه نامعتبر است." }
            expand(component.subRecipeVersionId, businessEpochDay, childMultiplier, path, totals)
        }
        path.remove(versionId)
    }

    private fun mulDiv(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).toLongExactCompat()
    }

    private companion object { const val MICROS = 1_000_000L }
}
