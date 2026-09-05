package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AccountingPeriodLockEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.JournalEntryEntity
import ir.restaurant.management.data.db.clearAllTablesForFactoryReset
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.recipe.RecipeCostProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeVersionIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private var now = 1_000_000L
    private var businessDay = 20_000L

    @Before
    fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
            .save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        Unit
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appendsRevisionsAndSelectsFormulaByBusinessDate(): Unit = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "گوشت نسخه‌ای",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                stockMicros = 100_000_000,
                inventoryValueRial = 1_000_000,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val repository = LocalRecipeRepository(
            database = database,
            clock = { now },
            syncRecorder = SyncRecorder(database, "recipe-test-device"),
            authorizer = authorizer,
            epochDay = { businessDay },
        )

        val menuId = repository.saveMenuItem(
            id = null,
            name = "کباب نسخه‌ای",
            category = "غذای اصلی",
            salePriceRial = 5_000_000,
            ingredients = listOf(RecipeIngredientInput(itemId, 120_000)),
            costProfile = RecipeCostProfile(
                yieldMicros = 8_000_000,
                portionWeightMicros = 350_000,
                preparationWasteBasisPoints = 500,
                cookingWasteBasisPoints = 300,
                packagingCostRial = 20_000,
                directLaborCostRial = 40_000,
                allocatedOverheadRial = 15_000,
                note = "پروفایل مرجع",
            ),
        )
        val firstVersion = requireNotNull(database.recipeDao().effectiveVersion(menuId, businessDay))
        assertEquals(1, firstVersion.revisionNo)
        assertEquals(120_000L, database.recipeDao().versionIngredients(firstVersion.id).single().quantityMicrosPerUnit)
        assertEquals(8_000_000L, firstVersion.yieldMicros)
        assertEquals(350_000L, firstVersion.portionWeightMicros)
        assertEquals(500, firstVersion.preparationWasteBasisPoints)
        assertEquals(300, firstVersion.cookingWasteBasisPoints)
        assertEquals(20_000L, firstVersion.packagingCostRial)
        assertEquals(40_000L, firstVersion.directLaborCostRial)
        assertEquals(15_000L, firstVersion.allocatedOverheadRial)
        assertEquals("پروفایل مرجع", firstVersion.note)

        now += 10_000
        businessDay = 20_100L
        repository.saveMenuItem(
            id = menuId,
            name = "کباب نسخه‌ای",
            category = "غذای اصلی",
            salePriceRial = 5_500_000,
            ingredients = listOf(RecipeIngredientInput(itemId, 140_000)),
        )

        val historical = requireNotNull(database.recipeDao().effectiveVersion(menuId, 20_050L))
        val current = requireNotNull(database.recipeDao().effectiveVersion(menuId, 20_100L))
        assertEquals(1, historical.revisionNo)
        assertEquals(2, current.revisionNo)
        assertNotEquals(historical.id, current.id)
        assertEquals(120_000L, database.recipeDao().versionIngredients(historical.id).single().quantityMicrosPerUnit)
        assertEquals(140_000L, database.recipeDao().versionIngredients(current.id).single().quantityMicrosPerUnit)
        assertEquals(2L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='RECIPE_VERSION'"))

        assertThrows(Exception::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE recipe_versions SET revisionNo=9 WHERE id=${current.id}",
            )
        }
    }

    @Test
    fun factoryResetClearsProtectedHistoryAndRestoresGuards() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده ریست",
                category = "مواد اولیه",
                unit = "کیلوگرم",
                stockMicros = 1_000_000,
                inventoryValueRial = 100_000,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        LocalRecipeRepository(
            database = database,
            clock = { now },
            authorizer = authorizer,
            epochDay = { businessDay },
        ).saveMenuItem(
            id = null,
            name = "غذای ریست",
            category = "غذای اصلی",
            salePriceRial = 500_000,
            ingredients = listOf(RecipeIngredientInput(itemId, 100_000)),
        )
        database.accountingDao().insertEntry(
            JournalEntryEntity(
                entryNo = "RESET-1",
                entryEpochDay = businessDay,
                description = "سند تست ریست",
                sourceType = "TEST",
                sourceId = 1,
                createdAtEpochMillis = now,
            ),
        )
        database.managementControlDao().insertAccountingPeriodLock(
            AccountingPeriodLockEntity(
                fromEpochDay = businessDay,
                toEpochDay = businessDay,
                reason = "آزمون ریست",
                closedBy = "owner",
                closedAtEpochMillis = now,
            ),
        )

        database.clearAllTablesForFactoryReset()

        assertEquals(0L, scalar("SELECT COUNT(*) FROM recipe_versions"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries"))
        assertTrue(scalar("SELECT COUNT(*) FROM accounts WHERE isSystem=1 AND isActive=1") > 0L)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM storage_locations WHERE code='MAIN' AND name='انبار اصلی' AND kind='WAREHOUSE' AND isActive=1"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_recipe_versions_no_delete'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='prevent_closed_accounting_delete'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_audit_logs_no_delete'"))
    }

    private fun scalar(sql: String): Long =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
