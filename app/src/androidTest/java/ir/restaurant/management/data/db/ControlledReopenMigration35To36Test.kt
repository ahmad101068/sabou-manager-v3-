package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlledReopenMigration35To36Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "controlled-reopen-migration-35-36.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test fun addsReopenAndOutboxStateAndUnlocksOnlyReopenedSalesDay() {
        open(35).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE inventory_period_closures(id INTEGER PRIMARY KEY, status TEXT NOT NULL)")
            db.execSQL("CREATE TABLE daily_sales_summaries(id INTEGER PRIMARY KEY, businessEpochDay INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE daily_sales_menu_lines(id INTEGER PRIMARY KEY, summaryId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE stock_movements(id INTEGER PRIMARY KEY, referenceType TEXT NOT NULL, referenceId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sales_day_closures(businessEpochDay INTEGER PRIMARY KEY, summaryId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sync_changes(id INTEGER PRIMARY KEY, changeId TEXT NOT NULL)")
            db.execSQL("INSERT INTO daily_sales_summaries VALUES(1,100)")
            db.execSQL("INSERT INTO sales_day_closures VALUES(100,1)")
            db.execSQL("INSERT INTO sync_changes VALUES(1,'device:event:1')")
        }
        open(36).use { helper ->
            val db = helper.writableDatabase
            assertThrows(Exception::class.java) { db.execSQL("UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1") }
            db.execSQL("UPDATE sales_day_closures SET status='REOPENED' WHERE businessEpochDay=100")
            db.execSQL("UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1")
            db.query("SELECT idempotencyKey,attemptCount FROM sync_changes WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0) == "device:event:1" && cursor.getInt(1) == 0)
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 35 && newVersion == 36) MIGRATION_35_36.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
