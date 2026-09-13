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
class ReplenishmentMigration25To26Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "replenishment-migration-25-26.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun addsReplenishmentPoliciesAndKeepsExistingData() {
        val helper = open(25)
        try {
            val db = helper.writableDatabase
            createVersion25Fixture(db)
            migrateTo26(db)
            assertVersionAndLegacyData(db)
            assertReplenishmentColumns(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createVersion25Fixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE legacy_marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO legacy_marker(id, value) VALUES (1, 'محفوظ')")
    }

    private fun migrateTo26(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_25_26.migrate(db)
            db.version = 26
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertVersionAndLegacyData(db: SupportSQLiteDatabase) {
        assertEquals(26, db.version)
        val cursor = db.query("SELECT value FROM legacy_marker WHERE id = 1")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("محفوظ", cursor.getString(0))
        } finally { cursor.close() }
    }

    private fun assertReplenishmentColumns(db: SupportSQLiteDatabase) {
        val expected = mutableSetOf("itemId", "preferredSupplierId", "targetCoverDays", "leadTimeDays", "safetyStockMicros", "orderMultipleMicros", "isEnabled")
        val cursor = db.query("PRAGMA table_info(inventory_replenishment_policies)")
        try {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) expected.remove(cursor.getString(nameIndex))
        } finally { cursor.close() }
        assertTrue("missing replenishment columns: $expected", expected.isEmpty())
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA foreign_key_check")
        try { assertEquals(0, cursor.count) } finally { cursor.close() }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 25 && newVersion == 26) MIGRATION_25_26.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
