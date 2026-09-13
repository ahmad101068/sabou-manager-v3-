package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeVersionMigration38To39Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "recipe-version-migration-38-39.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun convertsLegacyRecipeAndMakesHistoryImmutable() {
        open(38).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                """CREATE TABLE menu_items(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    salePriceRial INTEGER NOT NULL,
                    isActive INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )""",
            )
            db.execSQL("CREATE TABLE inventory_items(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            db.execSQL(
                """CREATE TABLE recipe_ingredients(
                    menuItemId INTEGER NOT NULL,
                    inventoryItemId INTEGER NOT NULL,
                    quantityMicrosPerUnit INTEGER NOT NULL,
                    PRIMARY KEY(menuItemId, inventoryItemId)
                )""",
            )
            db.execSQL(
                """CREATE TABLE daily_sales_menu_lines(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    summaryId INTEGER NOT NULL,
                    menuItemId INTEGER,
                    menuItemNameSnapshot TEXT NOT NULL,
                    quantityMicros INTEGER NOT NULL,
                    grossSalesRial INTEGER NOT NULL,
                    theoreticalCostRial INTEGER NOT NULL
                )""",
            )
            db.execSQL("INSERT INTO menu_items VALUES(1,'کباب','غذای اصلی',5000000,1,1000,1000)")
            db.execSQL("INSERT INTO inventory_items VALUES(10,'گوشت')")
            db.execSQL("INSERT INTO recipe_ingredients VALUES(1,10,120000)")
            db.execSQL("INSERT INTO daily_sales_menu_lines VALUES(20,5,1,'کباب',1000000,5000000,1800000)")
        }

        open(39).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT id,menuItemId,revisionNo,effectiveFromEpochDay FROM recipe_versions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1L, cursor.getLong(3))
            }
            db.query("SELECT inventoryItemId,quantityMicrosPerUnit FROM recipe_version_ingredients").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10L, cursor.getLong(0))
                assertEquals(120000L, cursor.getLong(1))
            }
            db.query("SELECT recipeVersionId FROM daily_sales_menu_lines WHERE id=20").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(!cursor.isNull(0))
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE recipe_versions SET revisionNo=2 WHERE menuItemId=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM recipe_version_ingredients")
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 38 && newVersion == 39) MIGRATION_38_39.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }
}
