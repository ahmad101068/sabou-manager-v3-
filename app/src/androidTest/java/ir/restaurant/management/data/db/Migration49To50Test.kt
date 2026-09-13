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
class Migration49To50Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-49-50.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun preservesHistoricalDailySalesAndBuildsCanonicalSettlementRows() {
        open(49).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY NOT NULL)")
            db.execSQL("CREATE TABLE daily_sales_summaries(id INTEGER PRIMARY KEY NOT NULL, businessEpochDay INTEGER NOT NULL, cashRial INTEGER NOT NULL, cardRial INTEGER NOT NULL, transferRial INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE daily_sales_menu_lines(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, summaryId INTEGER NOT NULL, menuItemId INTEGER, recipeVersionId INTEGER, menuItemNameSnapshot TEXT NOT NULL, quantityMicros INTEGER NOT NULL, grossSalesRial INTEGER, theoreticalCostRial INTEGER NOT NULL, foodCostSnapshotRial INTEGER, packagingCostSnapshotRial INTEGER, directLaborCostSnapshotRial INTEGER, allocatedOverheadSnapshotRial INTEGER)")
            db.execSQL("INSERT INTO customers(id) VALUES(7)")
            db.execSQL("INSERT INTO daily_sales_summaries VALUES(1,20000,300,400,500,1234)")
            db.execSQL("INSERT INTO daily_sales_menu_lines(id,summaryId,menuItemId,recipeVersionId,menuItemNameSnapshot,quantityMicros,grossSalesRial,theoreticalCostRial) VALUES(9,1,NULL,NULL,'legacy item',2000000,NULL,170)")
        }

        open(50).use { helper ->
            val db = helper.writableDatabase
            assertEquals("PERSON", text(db, "SELECT partyType FROM customers WHERE id=7"))
            assertEquals("legacy:daily_sales:1", text(db, "SELECT globalId FROM daily_sales_summaries WHERE id=1"))
            assertEquals(1L, scalar(db, "SELECT branchId FROM daily_sales_summaries WHERE id=1"))
            assertEquals(3L, scalar(db, "SELECT COUNT(*) FROM daily_sales_settlements WHERE dailySalesId=1"))
            assertEquals(1_200L, scalar(db, "SELECT SUM(amountRial) FROM daily_sales_settlements WHERE dailySalesId=1"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM daily_sales_settlements WHERE globalId='m49-cash-1' AND type='CASH' AND amountRial=300"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM daily_sales_settlements WHERE globalId='m49-card-1' AND type='CARD' AND amountRial=400"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM daily_sales_settlements WHERE globalId='m49-transfer-1' AND type='BANK_TRANSFER' AND amountRial=500"))
            db.query("SELECT grossSalesRial FROM daily_sales_menu_lines WHERE id=9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='receivables'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='management_issues'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='checklist_runs'"))
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 49 && newVersion == 50) MIGRATION_49_50.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun text(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }
}
