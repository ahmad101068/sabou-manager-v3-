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
class SupplierCatalogMigration26To27Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "supplier-catalog-migration-26-27.db"
    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test
    fun addsCatalogAndKeepsExistingData() {
        val helper = open(26)
        try {
            val db = helper.writableDatabase
            createCatalogFixture(db)
            migrateCatalogTo27(db)
            assertCatalogVersionAndMarker(db)
            assertCatalogColumns(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createCatalogFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO marker VALUES (1, 'محفوظ')")
    }

    private fun migrateCatalogTo27(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_26_27.migrate(db)
            db.version = 27
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertCatalogVersionAndMarker(db: SupportSQLiteDatabase) {
        assertEquals(27, db.version)
        val cursor = db.query("SELECT value FROM marker")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("محفوظ", cursor.getString(0))
        } finally {
            cursor.close()
        }
    }

    private fun assertCatalogColumns(db: SupportSQLiteDatabase) {
        val expected = mutableSetOf(
            "supplierId",
            "itemId",
            "unitCostRial",
            "minimumOrderMicros",
            "orderMultipleMicros",
            "leadTimeDays",
            "validUntilEpochDay",
        )
        val cursor = db.query("PRAGMA table_info(supplier_item_offers)")
        try {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) expected.remove(cursor.getString(nameIndex))
        } finally {
            cursor.close()
        }
        assertTrue("missing supplier catalog columns: $expected", expected.isEmpty())
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA foreign_key_check")
        try {
            assertEquals(0, cursor.count)
        } finally {
            cursor.close()
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion == 26 && newVersion == 27) MIGRATION_26_27.migrate(db) }
        }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
    }
}
