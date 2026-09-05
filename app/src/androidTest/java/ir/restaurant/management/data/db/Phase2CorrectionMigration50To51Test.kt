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
class Phase2CorrectionMigration50To51Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "phase2-correction-migration-50-51.db"

    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    @Test fun migratesGlobalThresholdScopeAndCollectionReversalColumnsWithoutDualGlobals() {
        open(50).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("""
                CREATE TABLE receivable_collections(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    globalId TEXT NOT NULL,
                    receivableId INTEGER NOT NULL,
                    amountRial INTEGER NOT NULL,
                    method TEXT NOT NULL,
                    cashboxId INTEGER,
                    bankAccountId INTEGER,
                    reference TEXT,
                    businessEpochDay INTEGER NOT NULL,
                    createdByUserId INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE management_rule_thresholds(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    branchId INTEGER,
                    `key` TEXT NOT NULL,
                    valueBasisPoints INTEGER,
                    valueRial INTEGER,
                    updatedByUserId INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO management_rule_thresholds(branchId,`key`,valueBasisPoints,updatedByUserId,updatedAtEpochMillis) VALUES(NULL,'FOOD_COST_VARIANCE_BP',500,1,100)")
            db.execSQL("INSERT INTO management_rule_thresholds(branchId,`key`,valueBasisPoints,updatedByUserId,updatedAtEpochMillis) VALUES(NULL,'FOOD_COST_VARIANCE_BP',600,1,200)")
            db.execSQL("INSERT INTO management_rule_thresholds(branchId,`key`,valueBasisPoints,updatedByUserId,updatedAtEpochMillis) VALUES(2,'FOOD_COST_VARIANCE_BP',800,1,150)")
        }

        open(51).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM management_rule_thresholds WHERE branchScopeId=0 AND `key`='FOOD_COST_VARIANCE_BP'"))
            assertEquals(600L, scalar(db, "SELECT valueBasisPoints FROM management_rule_thresholds WHERE branchScopeId=0 AND `key`='FOOD_COST_VARIANCE_BP'"))
            assertEquals(800L, scalar(db, "SELECT valueBasisPoints FROM management_rule_thresholds WHERE branchScopeId=2 AND `key`='FOOD_COST_VARIANCE_BP'"))
            assertThrows(Exception::class.java) {
                db.execSQL("INSERT INTO management_rule_thresholds(branchScopeId,`key`,valueBasisPoints,updatedByUserId,updatedAtEpochMillis) VALUES(0,'FOOD_COST_VARIANCE_BP',700,1,300)")
            }
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info(receivable_collections)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue("reversedAtEpochMillis" in columns)
            assertTrue("reversalReason" in columns)
            assertTrue("reversalJournalEntryId" in columns)
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 50 && newVersion == 51) MIGRATION_50_51.migrate(db)
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
}
