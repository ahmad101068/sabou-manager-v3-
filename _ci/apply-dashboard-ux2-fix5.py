#!/usr/bin/env python3
from pathlib import Path

root = Path.cwd()
enterprise = root / "app/src/main/java/ir/sabou/inventory/data/db/migration/EnterpriseMigrations.kt"
procurement_test = root / "app/src/androidTest/java/ir/sabou/inventory/data/db/ProcurementMigration23To24Test.kt"

# Android 6 SQLite does not rewrite a child FK that points at the temporary parent
# name after ALTER TABLE ... RENAME. Build the staging child against the stable final
# parent name instead. At copy time the legacy stock_transfers table still owns that
# name; after it is dropped, stock_transfers_v43 is renamed into that same stable name.
text = enterprise.read_text(encoding="utf-8")
old_fk = "FOREIGN KEY(transferId) REFERENCES stock_transfers_v43(id) ON UPDATE NO ACTION ON DELETE RESTRICT,"
new_fk = "FOREIGN KEY(transferId) REFERENCES stock_transfers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,"
count = text.count(old_fk)
if count != 1:
    raise SystemExit(f"Expected exactly one stock_transfers_v43 FK, found {count}")
text = text.replace(old_fk, new_fk, 1)
if "REFERENCES stock_transfers_v43(id)" in text:
    raise SystemExit("Temporary stock transfer parent FK still present")
enterprise.write_text(text, encoding="utf-8")

# The old standalone procurement migration test fabricated a tiny v23 schema. That
# is not a valid representation of a production v23 database and crashes the API23
# platform SQLite process. Rebuild the real v1 schema, run the production migration
# chain to v23, add a real legacy supplier row, then exercise only v23 -> v24.
procurement_test.write_text(r'''package ir.sabou.inventory.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcurementMigration23To24Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemaAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val databaseName = "procurement-migration-23-24.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun addsProcurementWorkflowAndKeepsLegacySupplier() {
        createVersionOneDatabase()

        open(version = 23).use { helper ->
            val db = helper.writableDatabase
            assertEquals(23, db.version)
            db.execSQL(
                """INSERT INTO suppliers(
                    name,contactName,phone,address,paymentTermsDays,notes,isActive,
                    createdAtEpochMillis,updatedAtEpochMillis
                ) VALUES ('تأمین نمونه','','','',0,'',1,1000,1000)""".trimIndent(),
            )
        }

        open(version = 24).use { helper ->
            val db = helper.writableDatabase
            assertEquals(24, db.version)
            db.query("SELECT name FROM suppliers WHERE name='تأمین نمونه'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("تأمین نمونه", cursor.getString(0))
            }
            listOf(
                "purchase_requisitions",
                "purchase_requisition_lines",
                "purchase_orders",
                "purchase_order_lines",
                "goods_receipts",
                "goods_receipt_lines",
                "procurement_invoice_links",
            ).forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue(cursor.moveToFirst()) }
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("v23→v24 must not introduce broken foreign keys", 0, cursor.count)
            }
        }
    }

    private fun createVersionOneDatabase() {
        val root = schemaAssets
            .open("ir.sabou.inventory.data.db.AppDatabase/1.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = root.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val tableName = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (position in 0 until indices.length()) {
                        db.execSQL(
                            indices.getJSONObject(position)
                                .getString("createSql")
                                .replace("${TABLE_NAME}", tableName),
                        )
                    }
                }
                val setupQueries = root.getJSONArray("setupQueries")
                for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        ).use { helper -> helper.writableDatabase }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                var current = oldVersion
                while (current < newVersion) {
                    val migration = ALL_MIGRATIONS.singleOrNull {
                        it.startVersion == current && it.endVersion == current + 1
                    } ?: error("Missing production migration ${current}→${current + 1}")
                    migration.migrate(db)
                    current += 1
                }
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }
}
''', encoding="utf-8")

print("DASHBOARD_UX2_MIGRATION_CHAIN_FIX=PASS stock_transfer_fk=1 procurement_seed=real_v1_to_v23")
