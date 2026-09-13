package ir.restaurant.management.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration59To60Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrationPreservesDataBackfillsAuditIntegrityAndInitializesConcurrencyState() {
        helper.createDatabase(DB_NAME, 59).use { db ->
            db.execSQL(
                "INSERT INTO audit_logs(id,action,entityType,entityId,description,actor,createdAtEpochMillis,globalId,reason,correlationId) " +
                    "VALUES(901,'UPDATE','TEST',7,'legacy audit','legacy',1722384000000,'ph81:audit','legacy reason','ph81:audit:1')",
            )
            db.execSQL(
                "INSERT INTO app_users(id,username,displayName,pinHash,role,isActive,failedPinAttempts,lockUntilEpochMillis,createdAtEpochMillis,updatedAtEpochMillis) " +
                    "VALUES(902,'phase81-user','Phase 8.1 User','pin-hash','ADMIN',1,0,0,1,1)",
            )
            db.execSQL(
                "INSERT INTO sales_cash_reconciliations(id,businessEpochDay,branchId,revisionNo,expectedCashRial,expectedCardRial,expectedTransferRial,actualCashRial,actualCardRial,actualTransferRial,status,note,reconciledBy,createdAtEpochMillis) " +
                    "VALUES(903,20000,NULL,1,100,200,300,100,200,300,'FINAL','legacy-1','legacy',1)",
            )
            db.execSQL(
                "INSERT INTO sales_cash_reconciliations(id,businessEpochDay,branchId,revisionNo,expectedCashRial,expectedCardRial,expectedTransferRial,actualCashRial,actualCardRial,actualTransferRial,status,note,reconciledBy,createdAtEpochMillis) " +
                    "VALUES(904,20000,NULL,2,100,200,300,100,200,300,'FINAL','legacy-2','legacy',2)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 60, true, MIGRATION_59_60).use { db ->
            assertColumn(db, "audit_logs", "integritySequence")
            assertColumn(db, "audit_logs", "previousEventHash")
            assertColumn(db, "audit_logs", "eventHash")
            assertColumn(db, "app_users", "rowVersion")
            assertIndex(db, "audit_logs", "index_audit_logs_integritySequence", expectedUnique = true)

            db.query("SELECT integritySequence,previousEventHash,eventHash FROM audit_logs WHERE id=901").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("", cursor.getString(1))
                assertTrue(cursor.getString(2).isNotBlank())
            }
            db.query("SELECT rowVersion FROM app_users WHERE id=902").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
            db.query("SELECT nextValue FROM document_sequences WHERE sequenceKey='SALES_CASH_REVISION:20000'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3L, cursor.getLong(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    @Test
    fun migrationRegistryContainsForwardOnly59To60() {
        val registry = ALL_MIGRATIONS.toList()
        assertTrue(registry.any { it.startVersion == 59 && it.endVersion == 60 })
        assertFalse(registry.any { it.startVersion == 60 && it.endVersion == 59 })
        assertNotNull(registry.singleOrNull { it.startVersion == 59 && it.endVersion == 60 })
    }

    private fun assertColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, name: String) {
        db.query("PRAGMA table_info('$table')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == name) found = true
            assertTrue("missing column $table.$name", found)
        }
    }

    private fun assertIndex(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        name: String,
        expectedUnique: Boolean,
    ) {
        db.query("PRAGMA index_list('$table')").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val uniqueIndex = cursor.getColumnIndex("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == name) {
                    found = true
                    assertEquals(expectedUnique, cursor.getInt(uniqueIndex) == 1)
                }
            }
            assertTrue("missing index $name", found)
        }
    }

    private companion object {
        const val DB_NAME = "phase81-migration-59-60"
    }
}
