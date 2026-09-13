package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.MenuItemEntity
import ir.restaurant.management.data.db.RecipeComponentEntity
import ir.restaurant.management.data.db.RecipeSubstitutionEntity
import ir.restaurant.management.data.db.RecipeVersionEntity
import ir.restaurant.management.data.db.RecipeVersionIngredientEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeMaterialResolverIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var resolver: RecipeMaterialResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        resolver = RecipeMaterialResolver(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun expandsImmutableNestedRecipeAndAppliesSubstitutionOnlyFromEffectiveBusinessDay() = runBlocking {
        val originalId = inventory("ماده A")
        val parentDirectId = inventory("ماده B")
        val substituteId = inventory("ماده C")
        val childMenu = menu("زیررسپی")
        val parentMenu = menu("رسپی مادر")
        val childVersion = version(childMenu, 1)
        val parentVersion = version(parentMenu, 1)

        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(childVersion, originalId, 200_000L)),
        )
        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(parentVersion, parentDirectId, 100_000L)),
        )
        database.recipeLifecycleDao().insertComponents(
            listOf(RecipeComponentEntity(recipeVersionId = parentVersion, subRecipeVersionId = childVersion, quantityMicrosPerUnit = 500_000L)),
        )
        database.recipeLifecycleDao().insertSubstitution(
            RecipeSubstitutionEntity(
                recipeVersionId = childVersion,
                originalInventoryItemId = originalId,
                substituteInventoryItemId = substituteId,
                ratioNumerator = 2,
                ratioDenominator = 1,
                reason = "جایگزینی کنترل‌شده",
                approvedByActorId = 1,
                createdAtEpochMillis = 1,
                effectiveFromEpochDay = 20_100L,
            ),
        )

        val historical = resolver.resolve(parentVersion, businessEpochDay = 20_050L, outputQuantityMicros = 2_000_000L).associateBy { it.inventoryItemId }
        assertEquals(200_000L, historical.getValue(parentDirectId).quantityMicros)
        assertEquals(200_000L, historical.getValue(originalId).quantityMicros)
        assertTrue(substituteId !in historical)

        val current = resolver.resolve(parentVersion, businessEpochDay = 20_100L, outputQuantityMicros = 2_000_000L).associateBy { it.inventoryItemId }
        assertEquals(200_000L, current.getValue(parentDirectId).quantityMicros)
        assertEquals(400_000L, current.getValue(substituteId).quantityMicros)
        assertTrue(originalId !in current)
    }

    @Test
    fun runtimeCycleCannotSilentlyConsumeInventory() = runBlocking {
        val materialId = inventory("ماده حلقه")
        val firstMenu = menu("رسپی اول")
        val secondMenu = menu("رسپی دوم")
        val first = version(firstMenu, 1)
        val second = version(secondMenu, 1)
        database.recipeDao().insertVersionIngredients(listOf(RecipeVersionIngredientEntity(first, materialId, 100_000L)))
        database.recipeLifecycleDao().insertComponents(
            listOf(
                RecipeComponentEntity(recipeVersionId = first, subRecipeVersionId = second, quantityMicrosPerUnit = 1_000_000L),
                RecipeComponentEntity(recipeVersionId = second, subRecipeVersionId = first, quantityMicrosPerUnit = 1_000_000L),
            ),
        )

        val failure = runCatching { resolver.resolve(first, 20_100L, 1_000_000L) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("حلقوی"))
    }

    private suspend fun inventory(name: String): Long = database.inventoryDao().insert(
        InventoryItemEntity(name = name, category = "مواد", unit = "گرم", createdAtEpochMillis = 1, updatedAtEpochMillis = 1),
    )

    private suspend fun menu(name: String): Long = database.recipeDao().insertMenuItem(
        MenuItemEntity(name = name, createdAtEpochMillis = 1, updatedAtEpochMillis = 1),
    )

    private suspend fun version(menuItemId: Long, revision: Int): Long = database.recipeDao().insertVersion(
        RecipeVersionEntity(
            menuItemId = menuItemId,
            revisionNo = revision,
            effectiveFromEpochDay = 20_000L,
            createdBy = "test",
            createdAtEpochMillis = 1,
            status = "ACTIVE",
        ),
    )
}
