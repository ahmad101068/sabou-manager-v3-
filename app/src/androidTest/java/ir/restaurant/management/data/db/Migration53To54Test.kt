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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration53To54Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "branch-canonicalization-migration-53-54.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun legacyPlaceholderDailySalesDoesNotCreateCanonicalBranch() {
        open(53).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES (1, 1, 1, 'legacy-only')",
            )
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=1"))
        }
    }

    @Test
    fun realNonLegacyDailySalesStillCreatesCanonicalBranchOne() {
        open(53).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES (1, 1, 0, 'real-branch-one')",
            )
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=1"))
        }
    }

    @Test
    fun mixedLegacyAndRealDailySalesCreateOnlyRealBranchEvidence() {
        open(53).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES " +
                    "(1, 1, 1, 'legacy-one'), (2, 2, 0, 'real-two')",
            )
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=1"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=2"))
        }
    }

    @Test
    fun sameBranchInLegacyAndRealDailySalesCreatesExactlyOneCanonicalBranch() {
        open(53).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES " +
                    "(1, 2, 1, 'legacy-two'), (2, 2, 0, 'real-two')",
            )
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=2"))
        }
    }

    @Test
    fun legacyDailySalesRowIsPreservedWithoutCreatingPlaceholderBranch() {
        open(53).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES (41, 1, 1, 'preserve-me')",
            )
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM daily_sales_summaries WHERE id=41"))
            assertEquals(1L, nullableLong(db, "SELECT branchId FROM daily_sales_summaries WHERE id=41"))
            assertEquals(1L, scalar(db, "SELECT isLegacyArchive FROM daily_sales_summaries WHERE id=41"))
            assertEquals("preserve-me", text(db, "SELECT note FROM daily_sales_summaries WHERE id=41"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=1"))
        }
    }

    @Test
    fun migration53To54PreservesDataAndBackfillsOnlyDeterministicLegacyKeys() {
        open(53).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES (1, 2, 0, 'operational')")
            db.execSQL("INSERT INTO journal_entries(id, branchId, accountingScope) VALUES (1, 2, 'BRANCH')")
            db.execSQL("INSERT INTO employees(id, branchName) VALUES (1, 'ونک'), (2, 'VANAK')")
            db.execSQL("INSERT INTO payroll_batches(id, branchName) VALUES (1, 'ونک')")
            db.execSQL("INSERT INTO storage_locations(id, branchName) VALUES (1, 'ونک'), (2, 'vanak')")
        }

        open(54).use { helper ->
            val db = helper.writableDatabase
            assertEquals(54, db.version)
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM branches WHERE id=2 AND globalId='legacy:branch:id:2'"))
            val employeeBranch = nullableLong(db, "SELECT branchId FROM employees WHERE id=1")
            assertEquals(employeeBranch, nullableLong(db, "SELECT branchId FROM payroll_batches WHERE id=1"))
            assertEquals(employeeBranch, nullableLong(db, "SELECT branchId FROM storage_locations WHERE id=1"))
            assertNull(nullableLong(db, "SELECT branchId FROM employees WHERE id=2"))
            assertNull(nullableLong(db, "SELECT branchId FROM storage_locations WHERE id=2"))
            assertEquals(2L, nullableLong(db, "SELECT branchId FROM journal_entries WHERE id=1"))
            assertEquals("BRANCH", text(db, "SELECT accountingScope FROM journal_entries WHERE id=1"))
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM employees"))
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM storage_locations"))
            assertFalse(hasColumnDefault(db, "daily_sales_summaries", "branchId"))
            listOf(
                "employees", "employment_assignments", "payroll_batches", "storage_locations",
                "purchases", "fixed_assets", "work_schedules", "sales_cash_reconciliations",
            ).forEach { table -> assertEquals(true, hasColumn(db, table, "branchId")) }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (version == 53) createVersion53Fixture(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 53 && newVersion == 54) MIGRATION_53_54.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }

    private fun createVersion53Fixture(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE daily_sales_summaries(" +
                "id INTEGER PRIMARY KEY NOT NULL, " +
                "branchId INTEGER NOT NULL DEFAULT 1, " +
                "isLegacyArchive INTEGER NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '')",
        )
        db.execSQL("CREATE TABLE journal_entries(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER, accountingScope TEXT NOT NULL DEFAULT 'UNASSIGNED_LEGACY')")
        db.execSQL("CREATE TABLE receivables(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE management_issues(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE management_tasks(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE checklist_templates(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER)")
        db.execSQL("CREATE TABLE checklist_runs(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE shift_templates(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER)")
        db.execSQL("CREATE TABLE employees(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE employment_assignments(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE payroll_batches(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT)")
        db.execSQL("CREATE TABLE storage_locations(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE purchases(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE fixed_assets(id INTEGER PRIMARY KEY NOT NULL, branch TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE work_schedules(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE sales_cash_reconciliations(id INTEGER PRIMARY KEY NOT NULL)")
    }

    private fun hasColumnDefault(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == column) return@use !cursor.isNull(4)
            }
            error("column_not_found:$table.$column")
        }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == column) return@use true
            false
        }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun nullableLong(db: SupportSQLiteDatabase, sql: String): Long? = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getLong(0)
    }

    private fun text(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }
}
