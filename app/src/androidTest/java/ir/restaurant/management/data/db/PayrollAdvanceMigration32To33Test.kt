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
class PayrollAdvanceMigration32To33Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "payroll-advance-migration-32-33.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test
    fun addsAuditableAdvanceAllocationsWithoutChangingHistoricalPayroll() {
        open(32).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE payroll_runs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, deductionsRial INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE employee_advances (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("INSERT INTO payroll_runs(id,deductionsRial) VALUES(1,250000)")
        }
        open(33).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT deductionsRial, advanceDeductionRial FROM payroll_runs WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(250000L, cursor.getLong(0))
                assertEquals(0L, cursor.getLong(1))
            }
            db.query("PRAGMA table_info('payroll_advance_allocations')").use { cursor ->
                val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertEquals(setOf("payrollId", "advanceId", "amountRial", "createdAtEpochMillis"), names)
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 32 && newVersion == 33) MIGRATION_32_33.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
