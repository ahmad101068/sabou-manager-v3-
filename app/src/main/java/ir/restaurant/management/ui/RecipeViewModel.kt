package ir.restaurant.management.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.restaurant.management.domain.operations.InventoryItemRecord
import ir.restaurant.management.domain.operations.OperationsRepository
import ir.restaurant.management.domain.recipe.MenuItem
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.recipe.RecipeCostProfile
import ir.restaurant.management.domain.recipe.RecipeIngredientItem
import ir.restaurant.management.application.recipe.RecipeUseCases
import ir.restaurant.management.domain.recipe.RecipeRevision
import ir.restaurant.management.domain.recipe.RecipeDraftInput
import ir.restaurant.management.domain.recipe.RecipeSubstitutionDraft
import ir.restaurant.management.domain.recipe.RecipeVersionDetails
import ir.restaurant.management.domain.recipe.RecipeVersionOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecipeUiState(
    val menuItems: List<MenuItem> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val selectedMenuItemId: Long? = null,
    val ingredients: List<RecipeIngredientItem> = emptyList(),
    val revisions: List<RecipeRevision> = emptyList(),
    val activeVersions: List<RecipeVersionOption> = emptyList(),
    val editingDraft: RecipeVersionDetails? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModel(
    private val useCases: RecipeUseCases,
    operationsRepository: OperationsRepository,
) : ViewModel() {
    private val selectedId = MutableStateFlow<Long?>(null)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val editingDraft = MutableStateFlow<RecipeVersionDetails?>(null)
    private val ingredients = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else useCases.ingredients(id)
    }
    private val revisions = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else useCases.revisions(id)
    }

    private data class RecipeBaseState(
        val menuItems: List<MenuItem>,
        val inventoryItems: List<InventoryItemRecord>,
        val selectedMenuItemId: Long?,
        val ingredients: List<RecipeIngredientItem>,
        val revisions: List<RecipeRevision>,
    )

    private val baseState = combine(
        useCases.menuItems,
        operationsRepository.inventoryItems,
        selectedId,
        ingredients,
        revisions,
    ) { menu, inventory, selected, recipe, history ->
        RecipeBaseState(menu, inventory, selected, recipe, history)
    }

    val state: StateFlow<RecipeUiState> = combine(
        baseState, useCases.activeVersionOptions(), editingDraft, busy, message,
    ) { base, activeVersions, draft, isBusy, msg ->
        RecipeUiState(
            menuItems = base.menuItems,
            inventoryItems = base.inventoryItems,
            selectedMenuItemId = base.selectedMenuItemId,
            ingredients = base.ingredients,
            revisions = base.revisions,
            activeVersions = activeVersions,
            editingDraft = draft,
            busy = isBusy,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState())

    fun select(id: Long?) { selectedId.value = id }
    fun clearMessage() { message.value = null }

    fun save(
        id: Long?,
        name: String,
        category: String,
        salePriceRial: Long,
        ingredients: List<RecipeIngredientInput>,
        costProfile: RecipeCostProfile,
        done: () -> Unit = {},
    ) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val savedId = useCases.saveCompatible(id, name, category, salePriceRial, ingredients, costProfile)
                selectedId.value = savedId
                message.value = "محصول و رسپی ذخیره شد."
                done()
            } catch (e: Exception) {
                message.value = e.message ?: "ذخیره رسپی انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }


    fun createDraftFrom(versionId: Long) = operation("پیش‌نویس جدید از نسخه فعال ایجاد شد.") {
        val details = useCases.versionDetails(versionId)
        useCases.createDraft(details.draft, versionId)
    }

    fun copyVersion(versionId: Long) = operation("کپی نسخه به‌صورت پیش‌نویس ایجاد شد.") {
        useCases.copyVersion(versionId)
    }

    fun loadDraft(versionId: Long) = operation(null) {
        val details = useCases.versionDetails(versionId)
        require(details.revision.state == ir.restaurant.management.domain.recipe.RecipeLifecycleState.DRAFT) { "فقط پیش‌نویس قابل ویرایش است." }
        editingDraft.value = details
    }

    fun closeDraftEditor() { editingDraft.value = null }

    fun editDraft(versionId: Long, input: RecipeDraftInput, done: () -> Unit = {}) = operation("پیش‌نویس رسپی ذخیره شد.", done) {
        useCases.editDraft(versionId, input)
        editingDraft.value = useCases.versionDetails(versionId)
    }

    fun activate(versionId: Long, effectiveFromEpochDay: Long) = operation("نسخه رسپی فعال شد.") {
        useCases.activate(versionId, effectiveFromEpochDay)
        editingDraft.value = null
    }

    fun retire(versionId: Long, reason: String) = operation("نسخه فعال بازنشسته شد.") {
        useCases.retire(versionId, reason)
    }

    fun approveSubstitution(draft: RecipeSubstitutionDraft) = operation("جایگزینی ماده با Audit ثبت شد.") {
        useCases.approveSubstitution(draft)
    }

    private fun operation(success: String?, done: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                block()
                message.value = success
                done()
            } catch (e: Exception) {
                message.value = e.message ?: "عملیات رسپی انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(useCases: RecipeUseCases, operationsRepository: OperationsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RecipeViewModel(useCases, operationsRepository) as T
            }
    }
}
