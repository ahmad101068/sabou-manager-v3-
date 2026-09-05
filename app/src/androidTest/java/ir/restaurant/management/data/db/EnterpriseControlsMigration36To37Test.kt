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
class EnterpriseControlsMigration36To37Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "enterprise-controls-migration-36-37.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test fun addsApprovalControlsAndGuardsClosedAccountingPeriods() {
        open(36).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE purchase_requisitions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE payroll_runs(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE operating_budgets(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entryEpochDay INTEGER NOT NULL)")
        }

        open(37).use { helper ->
            val db = helper.writableDatabase
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(purchase_requisitions)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue("requiredApprovalLevel" in columns)
            assertTrue("committedBudgetRial" in columns)

            db.execSQL("INSERT INTO accounting_period_locks(fromEpochDay,toEpochDay,status,reason,closedBy,closedAtEpochMillis) VALUES(100,200,'CLOSED','month end','owner',1)")
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO accounting_period_locks(fromEpochDay,toEpochDay,status,reason,closedBy,closedAtEpochMillis) VALUES(150,250,'CLOSED','overlap','owner',2)")
            }
            assertThrows(Exception::class.java) { db.execSQL("INSERT INTO journal_entries(entryEpochDay) VALUES(150)") }
            db.execSQL("UPDATE accounting_period_locks SET status='REOPENED' WHERE fromEpochDay=100")
            db.execSQL("INSERT INTO journal_entries(entryEpochDay) VALUES(150)")
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 36 && newVersion == 37) MIGRATION_36_37.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
