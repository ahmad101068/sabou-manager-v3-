package ir.restaurant.management.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration55To56Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun phase3MigrationPreservesLegacyTruthAndCreatesScopeApAndIntegrityGuards() {
        helper.createDatabase(DB_NAME, 55).use { db ->
            db.execSQL(
                "INSERT INTO branches(id,globalId,organizationId,code,name,isActive,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(7,'branch:7',NULL,'B7','شعبه هفت',1,1,2)",
            )
            db.execSQL(
                "INSERT INTO app_users(id,username,displayName,pinHash,recoveryCodeHash,role,isActive,failedPinAttempts,lockUntilEpochMillis,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(11, 'manager', 'مدیر', 'x', 'phase3-migration-fixture-recovery-hash', 'MANAGER', 1, 0, 0, 1, 2)",
            )
            db.execSQL(
                "INSERT INTO suppliers(id,name,contactName,phone,address,paymentTermsDays,notes,isActive,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(3,'تأمین نمونه','','09120000000','',30,'',1,1,2)",
            )
            db.execSQL(
                "INSERT INTO purchases(id,invoiceNo,supplierId,purchaseEpochDay,branchName,branchId,dueEpochDay,totalRial,paidRial,paymentStatus,paymentMethod,reminderEnabled,reminderEpochDay,createdAtEpochMillis) " +
                    "VALUES(5,'INV-55',3,20000,'شعبه هفت',7,20030,1000000,250000,'PARTIAL',NULL,0,NULL,10)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 56, true, MIGRATION_55_56).use { db ->
            db.query("SELECT status FROM branches WHERE id=7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ACTIVE", cursor.getString(0))
            }
            db.query("SELECT code,normalizedName FROM suppliers WHERE id=3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LEGACY-SUP-00000003", cursor.getString(0))
                assertTrue(cursor.getString(1).isNotBlank())
            }
            db.query("SELECT normalizedInvoiceNo FROM purchases WHERE id=5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("INV-55", cursor.getString(0))
            }
            db.query("SELECT originalRial,settledRial,status FROM supplier_payables WHERE sourceType='PURCHASE' AND sourceId=5").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1_000_000L, cursor.getLong(0))
                assertEquals(250_000L, cursor.getLong(1))
                assertEquals("PARTIAL", cursor.getString(2))
            }
            db.query("SELECT branchId FROM user_branch_scopes WHERE userId=11").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name='phase3_purchase_requisitions_scope_insert'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_purchases_supplierId_normalizedInvoiceNo'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }

            var rejected = false
            try {
                db.execSQL(
                    "INSERT INTO purchase_requisitions(id,requestNo,department,requiredEpochDay,status,requestedBy,requiredApprovalLevel,completedApprovalLevel,note,createdAtEpochMillis,updatedAtEpochMillis,globalId,correlationId,branchId,destinationLocationId) " +
                        "VALUES(99,'PR-X','آشپزخانه',20010,'SUBMITTED','manager',1,0,'',1,1,'pr:x','corr:x',7,NULL)",
                )
            } catch (_: Throwable) {
                rejected = true
            }
            assertTrue("operational write without explicit location must fail closed", rejected)
        }
    }

    @Test
    fun noDestructiveFallbackMarkerExistsInMigrationContract() {
        val registry = ALL_MIGRATIONS.toList()
        assertTrue(registry.any { it.startVersion == 55 && it.endVersion == 56 })
        assertFalse(registry.any { it.startVersion == 56 && it.endVersion == 55 })
    }

    private companion object {
        const val DB_NAME = "phase3-migration-55-56"
    }
}
