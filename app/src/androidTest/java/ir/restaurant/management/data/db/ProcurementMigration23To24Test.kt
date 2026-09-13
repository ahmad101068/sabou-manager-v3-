package ir.restaurant.management.data.db

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
        createVersionOneDatabase().use { helper ->
            val db = helper.writableDatabase
            migrateProductionChain(db, fromVersion = 1, toVersion = 23)
            assertEquals(23, db.version)

            android.util.Log.i("ProcurementMigration", "before supplier seed")
            db.execSQL(
                """INSERT INTO suppliers(
                    name,contactName,phone,address,paymentTermsDays,notes,isActive,
                    createdAtEpochMillis,updatedAtEpochMillis
                ) VALUES ('تأمین نمونه','','','',0,'',1,1000,1000)""".trimIndent(),
            )

            android.util.Log.i("ProcurementMigration", "after supplier seed")
            migrateProductionChain(db, fromVersion = 23, toVersion = 24)
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

    private fun createVersionOneDatabase(): SupportSQLiteOpenHelper {
        val root = schemaAssets
            .open("ir.restaurant.management.data.db.AppDatabase/1.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = root.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val tableName = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (position in 0 until indices.length()) {
                        db.execSQL(
                            indices.getJSONObject(position)
                                .getString("createSql")
                                .replace("${'$'}{TABLE_NAME}", tableName),
                        )
                    }
                }
                val setupQueries = root.getJSONArray("setupQueries")
                for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }

    private fun migrateProductionChain(
        db: SupportSQLiteDatabase,
        fromVersion: Int,
        toVersion: Int,
    ) {
        android.util.Log.i("ProcurementMigration", "chain-start $fromVersion→$toVersion")
        db.beginTransaction()
        try {
            var current = fromVersion
            while (current < toVersion) {
                val next = current + 1
                val migration = ALL_MIGRATIONS.singleOrNull {
                    it.startVersion == current && it.endVersion == next
                } ?: error("Missing production migration ${'$'}current→${'$'}next")
                android.util.Log.i("ProcurementMigration", "before $current→$next")
                migration.migrate(db)
                db.version = next
                android.util.Log.i("ProcurementMigration", "after $current→$next")
                current = next
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        android.util.Log.i("ProcurementMigration", "chain-end $fromVersion→$toVersion")
    }
}
