package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalGuardsMigration37To38Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "journal-guards-migration-37-38.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun protectsLineShapeAndClosedPeriods() {
        open(37).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY, entryEpochDay INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE journal_lines(id INTEGER PRIMARY KEY, entryId INTEGER NOT NULL, debitRial INTEGER NOT NULL, creditRial INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE accounting_period_locks(id INTEGER PRIMARY KEY, fromEpochDay INTEGER NOT NULL, toEpochDay INTEGER NOT NULL, status TEXT NOT NULL)")
            db.execSQL("INSERT INTO journal_entries VALUES(1,150)")
            db.execSQL("INSERT INTO journal_entries VALUES(2,300)")
            db.execSQL("INSERT INTO journal_lines VALUES(1,1,1000,0)")
        }

        open(38).use { helper ->
            val db = helper.writableDatabase
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO journal_lines VALUES(2,2,1000,1000)")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO journal_lines VALUES(2,2,-1,0)")
            }

            db.execSQL("INSERT INTO accounting_period_locks VALUES(1,100,200,'CLOSED')")
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO journal_lines VALUES(2,1,0,1000)")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE journal_lines SET debitRial=900 WHERE id=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM journal_lines WHERE id=1")
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 37 && newVersion == 38) MIGRATION_37_38.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }
}
