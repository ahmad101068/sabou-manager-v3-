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
class SupplierSplitMigration27To28Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "supplier-split-migration-27-28.db"
    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test
    fun addsAssignmentsAndKeepsExistingRequestLine() {
        val helper = open(27)
        try {
            val db = helper.writableDatabase
            createSplitFixture(db)
            migrateSplitTo28(db)
            assertSplitRequestLine(db)
            assertSplitOrderLineColumn(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createSplitFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE purchase_requisition_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requisitionId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, requestedQtyMicros INTEGER NOT NULL, estimatedUnitCostRial INTEGER NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("INSERT INTO purchase_requisition_lines(requisitionId,itemId,itemNameSnapshot,requestedQtyMicros,estimatedUnitCostRial,note) VALUES(1,2,'قهوه',1000000,100000,'قدیمی')")
    }

    private fun migrateSplitTo28(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_27_28.migrate(db)
            db.version = 28
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertSplitRequestLine(db: SupportSQLiteDatabase) {
        assertEquals(28, db.version)
        val cursor = db.query("SELECT itemNameSnapshot, recommendedSupplierId, supplierSkuSnapshot, recommendedLeadTimeDays FROM purchase_requisition_lines")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("قهوه", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        } finally {
            cursor.close()
        }
    }

    private fun assertSplitOrderLineColumn(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT supplierSkuSnapshot FROM purchase_order_lines")
        try {
            assertEquals(1, cursor.columnCount)
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
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion == 27 && newVersion == 28) MIGRATION_27_28.migrate(db) }
        }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
    }
}
