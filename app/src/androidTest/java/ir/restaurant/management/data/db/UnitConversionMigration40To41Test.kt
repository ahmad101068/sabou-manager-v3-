package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnitConversionMigration40To41Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "unit-conversion-40-41.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun legacyUnitBecomesOneToOnePurchaseStockAndRecipeUnits() {
        open(40).use { helper ->
            helper.writableDatabase.execSQL(
                """CREATE TABLE inventory_items(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL,
                    stockMicros INTEGER NOT NULL, inventoryValueRial INTEGER NOT NULL,
                    alertEnabled INTEGER NOT NULL, alertThresholdMicros INTEGER NOT NULL,
                    supplierId INTEGER, isActive INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)""",
            )
            helper.writableDatabase.execSQL(
                "INSERT INTO inventory_items VALUES(1,'شیر','مواد اولیه','لیتر',2000000,100000,1,0,NULL,1,1,1)",
            )
        }
        open(41).use { helper ->
            helper.writableDatabase.query(
                "SELECT unit,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,recipeUnit,recipeToStockNumerator,recipeToStockDenominator FROM inventory_items WHERE id=1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("لیتر", cursor.getString(0))
                assertEquals("لیتر", cursor.getString(1))
                assertEquals(1L, cursor.getLong(2))
                assertEquals(1L, cursor.getLong(3))
                assertEquals("لیتر", cursor.getString(4))
                assertEquals(1L, cursor.getLong(5))
                assertEquals(1L, cursor.getLong(6))
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 40 && newVersion == 41) MIGRATION_40_41.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
