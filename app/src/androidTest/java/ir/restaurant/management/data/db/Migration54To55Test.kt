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
class Migration54To55Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-54-55.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationCreatesStableAliasesWithoutRewritingHistoricalRows() {
        open(54).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO branches(id, globalId, organizationId, code, name, isActive, createdAtEpochMillis, updatedAtEpochMillis) " +
                    "VALUES(7,'legacy:branch:name:vanak',NULL,'VNK','ونک',1,1,2)",
            )
            db.execSQL(
                "INSERT INTO sales_invoices(id, invoiceNo, commandId, businessEpochDay, branchName, grossRial) " +
                    "VALUES(99,'LEG-1','LEG-CMD-1',24000,'ونک',100)",
            )
        }

        open(55).use { helper ->
            val db = helper.writableDatabase
            assertEquals(55, db.version)
            db.query(
                "SELECT branchId, aliasName, normalizedAlias FROM branch_legacy_aliases " +
                    "WHERE branchId=7 ORDER BY normalizedAlias",
            ).use { cursor ->
                val rows = mutableListOf<Triple<Long, String, String>>()
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                }
                assertEquals(
                    listOf(
                        Triple(7L, "vanak", "vanak"),
                        Triple(7L, "ونک", "ونک"),
                    ),
                    rows,
                )
            }
            db.query("SELECT branchName, grossRial FROM sales_invoices WHERE id=99").use { cursor ->
                cursor.moveToFirst()
                assertEquals("ونک", cursor.getString(0))
                assertEquals(100L, cursor.getLong(1))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (version == 54) createVersion54Fixture(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 54 && newVersion == 55) MIGRATION_54_55.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }

    private fun createVersion54Fixture(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE branches(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                globalId TEXT NOT NULL,
                organizationId INTEGER,
                code TEXT,
                name TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE sales_invoices(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceNo TEXT NOT NULL DEFAULT '',
                commandId TEXT NOT NULL DEFAULT '',
                businessEpochDay INTEGER NOT NULL DEFAULT 0,
                branchName TEXT NOT NULL DEFAULT '',
                grossRial INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}
