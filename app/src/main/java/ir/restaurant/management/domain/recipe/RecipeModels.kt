package ir.restaurant.management.domain.recipe

import kotlinx.coroutines.flow.Flow

data class MenuItem(
    val id: Long,
    val name: String,
    val category: String,
    val salePriceRial: Long,
    val ingredientCount: Int = 0,
    val recipeRevisionNo: Int = 0,
    val recipeEffectiveFromEpochDay: Long = 0,
    val costProfile: RecipeCostProfile = RecipeCostProfile(),
)

data class RecipeCostProfile(
    val yieldMicros: Long = 1_000_000L,
    val portionWeightMicros: Long = 0,
    val preparationWasteBasisPoints: Int = 0,
    val cookingWasteBasisPoints: Int = 0,
    val packagingCostRial: Long = 0,
    val directLaborCostRial: Long = 0,
    val allocatedOverheadRial: Long = 0,
    val note: String = "",
) {
    fun validated(): RecipeCostProfile {
        require(yieldMicros > 0) { "بازده تولید باید بیشتر از صفر باشد." }
        require(portionWeightMicros >= 0) { "وزن استاندارد هر پرس نامعتبر است." }
        require(preparationWasteBasisPoints in 0..FullCostCalculator.MAX_WASTE_BASIS_POINTS) { "ضایعات آماده‌سازی نامعتبر است." }
        require(cookingWasteBasisPoints in 0..FullCostCalculator.MAX_WASTE_BASIS_POINTS) { "ضایعات پخت نامعتبر است." }
        require(listOf(packagingCostRial, directLaborCostRial, allocatedOverheadRial).all { it >= 0 }) { "اجزای بهای کامل نمی‌توانند منفی باشند." }
        return copy(note = note.trim())
    }
}

data class RecipeIngredientInput(
    val inventoryItemId: Long,
    val quantityMicrosPerUnit: Long,
)

enum class RecipeLifecycleState { DRAFT, ACTIVE, RETIRED }

data class RecipeComponentInput(
    val subRecipeVersionId: Long,
    val quantityMicrosPerUnit: Long,
) {
    fun validated(): RecipeComponentInput {
        require(subRecipeVersionId > 0) { "نسخه زیررسپی معتبر نیست." }
        require(quantityMicrosPerUnit > 0) { "مقدار زیررسپی باید بیشتر از صفر باشد." }
        return this
    }
}

data class RecipeDraftInput(
    val menuItemId: Long,
    val ingredients: List<RecipeIngredientInput>,
    val components: List<RecipeComponentInput> = emptyList(),
    val costProfile: RecipeCostProfile = RecipeCostProfile(),
    val note: String = "",
)

data class RecipeSubstitutionDraft(
    val recipeVersionId: Long,
    val originalInventoryItemId: Long,
    val substituteInventoryItemId: Long,
    val ratioNumerator: Long = 1,
    val ratioDenominator: Long = 1,
    val reason: String,
)


data class RecipeVersionOption(
    val versionId: Long,
    val menuItemId: Long,
    val menuItemName: String,
    val revisionNo: Int,
)

data class RecipeVersionDetails(
    val revision: RecipeRevision,
    val draft: RecipeDraftInput,
)

data class RecipeIngredientItem(
    val inventoryItemId: Long,
    val inventoryName: String,
    val unit: String,
    val quantityMicrosPerUnit: Long,
)

data class RecipeRevision(
    val id: Long,
    val revisionNo: Int,
    val effectiveFromEpochDay: Long,
    val costProfile: RecipeCostProfile,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val state: RecipeLifecycleState = RecipeLifecycleState.ACTIVE,
    val parentVersionId: Long? = null,
)

interface RecipeRepository {
    suspend fun saveMenuItem(
        id: Long?,
        name: String,
        category: String,
        salePriceRial: Long,
        ingredients: List<RecipeIngredientInput>,
        costProfile: RecipeCostProfile = RecipeCostProfile(),
    ): Long

    suspend fun createDraft(input: RecipeDraftInput, parentVersionId: Long? = null): Long
    suspend fun copyVersion(versionId: Long): Long
    suspend fun editDraft(versionId: Long, input: RecipeDraftInput)
    suspend fun activate(versionId: Long, effectiveFromEpochDay: Long): Long
    suspend fun retire(versionId: Long, reason: String)
    suspend fun approveSubstitution(draft: RecipeSubstitutionDraft): Long
    suspend fun versionDetails(versionId: Long): RecipeVersionDetails
    fun observeActiveVersionOptions(): Flow<List<RecipeVersionOption>>
    fun observeMenuItems(): Flow<List<MenuItem>>
    fun observeIngredients(menuItemId: Long): Flow<List<RecipeIngredientItem>>
    fun observeRevisions(menuItemId: Long): Flow<List<RecipeRevision>>
}
