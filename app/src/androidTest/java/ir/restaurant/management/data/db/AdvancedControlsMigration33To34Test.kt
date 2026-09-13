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
class AdvancedControlsMigration33To34Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "advanced-controls-migration-33-34.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun addsPayrollPolicyInventoryCloseAndClosedLedgerGuard() {
        open(33).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE payroll_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, movementEpochDay INTEGER NOT NULL)")
        }
        open(34).use { helper ->
            val db = helper.writableDatabase
            db.query("PRAGMA table_info('payroll_runs')").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertTrue(columns.containsAll(setOf("periodStartEpochDay", "periodEndEpochDay", "payrollPolicyId", "automaticOvertimeRial", "attendanceDeductionRial")))
            }
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('payroll_policies','inventory_period_closures','inventory_period_closure_lines')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
            db.execSQL("""INSERT INTO inventory_period_closures(fromEpochDay,toEpochDay,openingValueRial,netPurchaseValueRial,recordedOutflowValueRial,expectedClosingValueRial,countedClosingValueRial,varianceValueRial,itemCount,status,closedBy,note,createdAtEpochMillis)
                VALUES(10,20,0,0,0,0,0,0,0,'CLOSED','TEST','',0)""")
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO stock_movements(movementEpochDay) VALUES(15)")
            }
            db.execSQL("INSERT INTO stock_movements(movementEpochDay) VALUES(21)")
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 33 && newVersion == 34) MIGRATION_33_34.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
