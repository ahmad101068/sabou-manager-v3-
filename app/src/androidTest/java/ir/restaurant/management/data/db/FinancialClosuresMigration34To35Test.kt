package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialClosuresMigration34To35Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "financial-closures-migration-34-35.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun addsPayrollRevisionsAndGuardsClosedSalesDays() {
        open(34).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE payroll_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, employeeId INTEGER NOT NULL, periodYear INTEGER NOT NULL, periodMonth INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_payroll_runs_employeeId_periodYear_periodMonth ON payroll_runs(employeeId,periodYear,periodMonth)")
            db.execSQL("CREATE TABLE daily_sales_summaries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, businessEpochDay INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE daily_sales_menu_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, summaryId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, referenceType TEXT NOT NULL, referenceId INTEGER NOT NULL)")
        }
        open(35).use { helper ->
            val db = helper.writableDatabase
            db.query("PRAGMA table_info('payroll_runs')").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertTrue(columns.containsAll(setOf("revisionNo", "reversalEpochDay", "reversalReason", "reversalJournalEntryId", "reversedBy")))
            }
            db.query("PRAGMA table_info('sales_day_closures')").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertFalse("status belongs to the version-36 controlled-reopen contract", columns.contains("status"))
            }
            db.execSQL("INSERT INTO daily_sales_summaries(id,businessEpochDay) VALUES(1,100)")
            db.execSQL("INSERT INTO daily_sales_menu_lines(id,summaryId) VALUES(1,1)")
            db.execSQL("INSERT INTO stock_movements(id,referenceType,referenceId) VALUES(1,'DAILY_SALES',1)")
            db.execSQL("""INSERT INTO sales_day_closures(businessEpochDay,summaryId,grossSalesRial,netSalesRial,theoreticalCostRial,cashRial,cardRial,transferRial,closedBy,note,createdAtEpochMillis)
                VALUES(100,1,1000,1000,400,100,900,0,'TEST','',0)""")
            assertThrows(Exception::class.java) { db.execSQL("UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1") }
            assertThrows(Exception::class.java) { db.execSQL("INSERT INTO daily_sales_summaries(businessEpochDay) VALUES(100)") }
            assertThrows(Exception::class.java) { db.execSQL("UPDATE daily_sales_menu_lines SET summaryId=1 WHERE id=1") }
            assertThrows(Exception::class.java) { db.execSQL("DELETE FROM stock_movements WHERE id=1") }
            db.execSQL("INSERT INTO daily_sales_summaries(businessEpochDay) VALUES(101)")
            db.query("SELECT COUNT(*) FROM sales_day_closures").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 34 && newVersion == 35) MIGRATION_34_35.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
