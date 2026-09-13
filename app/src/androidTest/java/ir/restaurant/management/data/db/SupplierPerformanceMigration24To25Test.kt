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
class SupplierPerformanceMigration24To25Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "supplier-performance-migration-24-25.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun addsReturnsAndPreservesExistingOrderLine() {
        val helper = open(24)
        try {
            val db = helper.writableDatabase
            createPerformanceFixture(db)
            migratePerformanceTo25(db)
            assertPerformanceVersionAndOrderLine(db)
            assertPerformanceTables(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createPerformanceFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE purchase_orders (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("INSERT INTO purchase_orders(id) VALUES(1)")
        db.execSQL("INSERT INTO suppliers(id) VALUES(1)")
        db.execSQL("INSERT INTO inventory_items(id) VALUES(2)")
        db.execSQL("CREATE TABLE purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseOrderId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, orderedQtyMicros INTEGER NOT NULL, unitCostRial INTEGER NOT NULL, receivedQtyMicros INTEGER NOT NULL, rejectedQtyMicros INTEGER NOT NULL)")
        db.execSQL("INSERT INTO purchase_order_lines(purchaseOrderId,itemId,itemNameSnapshot,orderedQtyMicros,unitCostRial,receivedQtyMicros,rejectedQtyMicros) VALUES (1,2,'قهوه',1000000,500000,1000000,0)")
    }

    private fun migratePerformanceTo25(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_24_25.migrate(db)
            db.version = 25
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertPerformanceVersionAndOrderLine(db: SupportSQLiteDatabase) {
        assertEquals(25, db.version)
        val cursor = db.query("SELECT itemNameSnapshot,returnedQtyMicros FROM purchase_order_lines")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("قهوه", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
        } finally {
            cursor.close()
        }
    }

    private fun assertPerformanceTables(db: SupportSQLiteDatabase) {
        assertTableReadable(db, "purchase_returns")
        assertTableReadable(db, "purchase_return_lines")
        assertTableReadable(db, "supplier_credits")
    }

    private fun assertTableReadable(db: SupportSQLiteDatabase, table: String) {
        val cursor = db.query("SELECT COUNT(*) FROM $table")
        try {
            assertTrue(cursor.moveToFirst())
        } finally {
            cursor.close()
        }
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
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 24 && newVersion == 25) MIGRATION_24_25.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
