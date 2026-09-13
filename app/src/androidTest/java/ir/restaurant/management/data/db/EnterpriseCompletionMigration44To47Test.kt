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

/** Proves the real exported v44 schema upgrades non-destructively through Personnel 2.1 schema 48. */
@RunWith(AndroidJUnit4::class)
class EnterpriseCompletionMigration44To48Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemaAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val name = "enterprise-completion-44-48.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test
    fun realV44Seed_migratesThrough48_withIntegrityForeignKeysSchedulingAndAttendanceBackfill() {
        createRealV44Database()
        openCurrent().use { helper ->
            val db = helper.writableDatabase
            assertEquals(48, db.version)
            assertEquals(42L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='purchase_invoice'"))
            assertEquals(103L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='employee'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='treasury_transactions'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='customer_receivable_ledger'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_sales_invoices_branchName'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_purchases_branchName'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_storage_locations_branchName'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='shift_templates'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='work_schedules'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='work_schedule_days'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='overtime_approvals'"))
            assertEquals("", text(db, "SELECT branchName FROM purchases WHERE invoiceNo='PUR-00000041'"))
            db.query("PRAGMA table_info(sales_invoices)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "branchName") {
                        found = true
                        assertEquals(1, cursor.getInt(notNullIndex))
                        assertTrue(cursor.getString(defaultIndex).contains("''"))
                    }
                }
                assertTrue("sales_invoices.branchName must exist", found)
            }
            db.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    private fun createRealV44Database() {
        val root = schemaAssets
            .open("ir.restaurant.management.data.db.AppDatabase/44.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }
        val callback = object : SupportSQLiteOpenHelper.Callback(44) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = root.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val tableName = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (position in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(position).getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                    }
                }
                val setupQueries = root.getJSONArray("setupQueries")
                for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
                db.execSQL("INSERT INTO suppliers(id,name,contactName,phone,address,paymentTermsDays,notes,isActive,createdAtEpochMillis,updatedAtEpochMillis) VALUES(1,'Seed Supplier','','','',0,'',1,1,1)")
                db.execSQL("INSERT INTO purchases(invoiceNo,supplierId,purchaseEpochDay,dueEpochDay,totalRial,paidRial,paymentStatus,paymentMethod,reminderEnabled,reminderEpochDay,createdAtEpochMillis) VALUES('PUR-00000041',1,20000,20030,1000,0,'UNPAID',NULL,0,NULL,1)")
                db.execSQL("INSERT INTO employees(name,firstName,lastName,displayName,fatherName,employeeCode,jobTitle,department,branchName,locationId,managerId,phone,email,nationalId,birthEpochDay,hireEpochDay,terminationEpochDay,insuranceNumber,bankCard,address,emergencyContact,notes,monthlySalaryRial,leaveBalanceMicros,status,createdAtEpochMillis,updatedAtEpochMillis,createdByActorId,updatedByActorId) VALUES('Seed Employee','Seed','Employee','Seed Employee','','EMP-00000102','','','A',NULL,NULL,'',NULL,NULL,NULL,20000,NULL,NULL,NULL,'','','',0,0,'ACTIVE',1,1,NULL,NULL)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        ).use { it.writableDatabase }
    }

    private fun openCurrent(): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(48) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                var version = oldVersion
                if (version == 44) { MIGRATION_44_45.migrate(db); version = 45 }
                if (version == 45) { MIGRATION_45_46.migrate(db); version = 46 }
                if (version == 46) { MIGRATION_46_47.migrate(db); version = 47 }
                if (version == 47) { MIGRATION_47_48.migrate(db); version = 48 }
                check(version == newVersion) { "Unexpected migration chain $oldVersion->$newVersion ended at $version" }
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun text(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
}
