package ir.restaurant.management.application.recipe

import ir.restaurant.management.domain.recipe.*

class RecipeUseCases(private val repository: RecipeRepository) {
    val menuItems get() = repository.observeMenuItems()
    fun ingredients(menuItemId: Long) = repository.observeIngredients(menuItemId)
    fun revisions(menuItemId: Long) = repository.observeRevisions(menuItemId)
    suspend fun saveCompatible(id: Long?, name: String, category: String, salePriceRial: Long, ingredients: List<RecipeIngredientInput>, profile: RecipeCostProfile) =
        repository.saveMenuItem(id, name, category, salePriceRial, ingredients, profile)
    suspend fun createDraft(input: RecipeDraftInput, parentVersionId: Long? = null) = repository.createDraft(input, parentVersionId)
    suspend fun copyVersion(versionId: Long) = repository.copyVersion(versionId)
    suspend fun editDraft(versionId: Long, input: RecipeDraftInput) = repository.editDraft(versionId, input)
    suspend fun activate(versionId: Long, effectiveFromEpochDay: Long) = repository.activate(versionId, effectiveFromEpochDay)
    suspend fun retire(versionId: Long, reason: String) = repository.retire(versionId, reason)
    suspend fun approveSubstitution(draft: RecipeSubstitutionDraft) = repository.approveSubstitution(draft)
    suspend fun versionDetails(versionId: Long) = repository.versionDetails(versionId)
    fun activeVersionOptions() = repository.observeActiveVersionOptions()
}
