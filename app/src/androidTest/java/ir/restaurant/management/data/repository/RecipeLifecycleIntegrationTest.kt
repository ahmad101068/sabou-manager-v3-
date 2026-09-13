package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.recipe.RecipeComponentInput
import ir.restaurant.management.domain.recipe.RecipeDraftInput
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.recipe.RecipeLifecycleState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeLifecycleIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var repository: LocalRecipeRepository
    private var now = 4_000_000L
    private val today = 22_000L
    private var ingredientId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer).save(
            null,
            UserDraft("owner-recipe-cycle", "مالک رسپی", "123456", UserRole.OWNER, "87654321"),
        )
        ingredientId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده پایه چرخه",
                category = "مواد اولیه",
                unit = "گرم",
                stockMicros = 100 * QuantityMicros.SCALE,
                inventoryValueRial = 5_000_000L,
                alertEnabled = false,
                createdAtEpochMillis = ++now,
                updatedAtEpochMillis = ++now,
            ),
        )
        repository = LocalRecipeRepository(database, authorizer = authorizer, clock = { ++now }, epochDay = { today })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun directCircularDependency_AtoBtoA_isRejectedAndDraftInsertRollsBack() = runBlocking {
        val menuA = createMenu("A")
        val menuB = createMenu("B")
        val activeA = activeVersion(menuA)

        val bDraft = repository.createDraft(componentDraft(menuB, activeA))
        repository.activate(bDraft, today)
        val activeB = activeVersion(menuB)
        val revisionsBefore = scalar("SELECT COUNT(*) FROM recipe_versions WHERE menuItemId=$menuA")

        try {
            repository.createDraft(componentDraft(menuA, activeB))
            fail("A → B → A باید قبل از Commit رد شود")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("حلقوی"))
        }

        assertEquals(revisionsBefore, scalar("SELECT COUNT(*) FROM recipe_versions WHERE menuItemId=$menuA"))
        assertEquals(RecipeLifecycleState.ACTIVE.name, requireNotNull(database.recipeLifecycleDao().versionById(activeA)).status)
    }

    @Test
    fun indirectCircularDependency_AtoBtoCtoA_isRejectedWithoutPartialVersion() = runBlocking {
        val menuA = createMenu("A3")
        val menuB = createMenu("B3")
        val menuC = createMenu("C3")
        val activeA = activeVersion(menuA)

        val cDraft = repository.createDraft(componentDraft(menuC, activeA))
        repository.activate(cDraft, today)
        val activeC = activeVersion(menuC)

        val bDraft = repository.createDraft(componentDraft(menuB, activeC))
        repository.activate(bDraft, today)
        val activeB = activeVersion(menuB)
        val revisionsBefore = scalar("SELECT COUNT(*) FROM recipe_versions WHERE menuItemId=$menuA")
        val componentsBefore = scalar("SELECT COUNT(*) FROM recipe_components")

        try {
            repository.createDraft(componentDraft(menuA, activeB))
            fail("A → B → C → A باید قبل از Commit رد شود")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("حلقوی"))
        }

        assertEquals(revisionsBefore, scalar("SELECT COUNT(*) FROM recipe_versions WHERE menuItemId=$menuA"))
        assertEquals(componentsBefore, scalar("SELECT COUNT(*) FROM recipe_components"))
    }

    @Test
    fun activeVersion_isImmutable_editRequiresDraft_andActivationRetiresPreviousVersion() = runBlocking {
        val menuId = createMenu("IMMUTABLE")
        val original = activeVersion(menuId)

        try {
            repository.editDraft(original, ingredientDraft(menuId))
            fail("نسخه Active نباید مستقیم ویرایش شود")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("پیش‌نویس"))
        }

        val draft = repository.copyVersion(original)
        repository.editDraft(draft, ingredientDraft(menuId, quantity = 2 * QuantityMicros.SCALE))
        repository.activate(draft, today)

        assertEquals(RecipeLifecycleState.RETIRED.name, requireNotNull(database.recipeLifecycleDao().versionById(original)).status)
        assertEquals(RecipeLifecycleState.ACTIVE.name, requireNotNull(database.recipeLifecycleDao().versionById(draft)).status)
        assertEquals(QuantityMicros.SCALE, database.recipeDao().versionIngredients(original).single().quantityMicrosPerUnit)
        assertEquals(2 * QuantityMicros.SCALE, database.recipeDao().versionIngredients(draft).single().quantityMicrosPerUnit)
    }

    private suspend fun createMenu(suffix: String): Long = repository.saveMenuItem(
        id = null,
        name = "رسپی $suffix",
        category = "TEST",
        salePriceRial = 500_000L,
        ingredients = listOf(RecipeIngredientInput(ingredientId, QuantityMicros.SCALE)),
    )

    private suspend fun activeVersion(menuItemId: Long): Long =
        requireNotNull(database.recipeDao().effectiveVersion(menuItemId, today)).id

    private fun componentDraft(menuItemId: Long, childVersionId: Long) = RecipeDraftInput(
        menuItemId = menuItemId,
        ingredients = emptyList(),
        components = listOf(RecipeComponentInput(childVersionId, QuantityMicros.SCALE)),
        note = "آزمون زیررسپی",
    )

    private fun ingredientDraft(menuItemId: Long, quantity: Long = QuantityMicros.SCALE) = RecipeDraftInput(
        menuItemId = menuItemId,
        ingredients = listOf(RecipeIngredientInput(ingredientId, quantity)),
        note = "آزمون نسخه immutable",
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
