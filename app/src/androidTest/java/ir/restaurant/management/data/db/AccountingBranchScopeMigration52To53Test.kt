package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingBranchScopeMigration52To53Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "accounting-branch-scope-migration-52-53.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun backfillsOnlyDeterministicNumericRelationsAndLeavesUnknownLegacyUnassigned() {
        open(52).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                """CREATE TABLE journal_entries(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId INTEGER NOT NULL,
                    entryEpochDay INTEGER NOT NULL DEFAULT 1,
                    reversalOfEntryId INTEGER
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE daily_sales_summaries(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    branchId INTEGER NOT NULL,
                    isLegacyArchive INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE receivables(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    branchId INTEGER NOT NULL
                )""".trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE receivable_collections(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    receivableId INTEGER NOT NULL
                )""".trimIndent(),
            )

            db.execSQL("INSERT INTO daily_sales_summaries(id,branchId,isLegacyArchive) VALUES(10,3,0)")
            db.execSQL("INSERT INTO daily_sales_summaries(id,branchId,isLegacyArchive) VALUES(11,1,1)")
            db.execSQL("INSERT INTO receivables(id,branchId) VALUES(20,2)")
            db.execSQL("INSERT INTO receivable_collections(id,receivableId) VALUES(30,20)")

            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(1,'DAILY_SALES',10,NULL)")
            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(2,'DAILY_SALES',11,NULL)")
            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(3,'RECEIVABLE',20,NULL)")
            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(4,'RECEIVABLE_COLLECTION',30,NULL)")
            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(5,'UNKNOWN',999,NULL)")
            db.execSQL("INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(6,'CUSTOM_REVERSAL',1,1)")
        }

        open(53).use { helper ->
            val db = helper.writableDatabase
            assertScope(db, 1, "BRANCH", 3)
            // Phase-2 compatibility default branch 1 on archived legacy Daily Sales is not evidence.
            assertScope(db, 2, "UNASSIGNED_LEGACY", null)
            assertScope(db, 3, "BRANCH", 2)
            assertScope(db, 4, "BRANCH", 2)
            assertScope(db, 5, "UNASSIGNED_LEGACY", null)
            assertScope(db, 6, "BRANCH", 3)
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 52 && newVersion == 53) MIGRATION_52_53.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }

    private fun assertScope(db: SupportSQLiteDatabase, id: Long, scope: String, branchId: Long?) {
        db.query("SELECT accountingScope, branchId FROM journal_entries WHERE id=$id").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(scope, cursor.getString(0))
            if (branchId == null) assertEquals(true, cursor.isNull(1)) else assertEquals(branchId, cursor.getLong(1))
        }
    }
}
