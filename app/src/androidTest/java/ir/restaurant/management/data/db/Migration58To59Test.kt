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
class Migration58To59Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrationPreservesRowsAndAddsDurableSecurityAlertAndMakerCheckerFields() {
        helper.createDatabase(DB_NAME, 58).use { db ->
            db.execSQL("INSERT INTO audit_logs(id,action,entityType,entityId,description,actor,createdAtEpochMillis,globalId,reason,correlationId) VALUES(901,'UPDATE','TEST',1,'legacy','legacy',1,'ph6:audit','legacy reason','ph6:audit:1')")
            db.execSQL("INSERT INTO app_alerts(id,sourceType,sourceId,title,message,severity,dueEpochDay,isRead,isDismissed,createdAtEpochMillis,updatedAtEpochMillis,status) VALUES(902,'LOW_STOCK',77,'legacy','legacy','MEDIUM',NULL,0,0,1,1,'NEW')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 59, true, MIGRATION_58_59).use { db ->
            db.query("SELECT actorRoleSnapshot,actorBranchIdSnapshot FROM audit_logs WHERE id=901").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("UNKNOWN", c.getString(0))
                assertTrue(c.isNull(1))
            }
            db.query("SELECT branchId,locationId,snoozedUntilEpochMillis FROM app_alerts WHERE id=902").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
                assertEquals(0L, c.getLong(1))
                assertTrue(c.isNull(2))
            }
            assertColumn(db, "management_tasks", "completedByUserId")
            assertColumn(db, "checklist_runs", "completedByUserId")
            assertIndex(db, "audit_logs", "index_audit_logs_actorBranchIdSnapshot", false)
            assertIndex(db, "app_alerts", "index_app_alerts_sourceType_sourceId_locationId", true)
            assertIndex(db, "app_alerts", "index_app_alerts_snoozedUntilEpochMillis", false)
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }

    @Test fun registryContainsForwardOnly58To59() {
        val registry = ALL_MIGRATIONS.toList()
        assertTrue(registry.any { it.startVersion == 58 && it.endVersion == 59 })
        assertFalse(registry.any { it.startVersion == 59 && it.endVersion == 58 })
    }

    private fun assertColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, name: String) {
        db.query("PRAGMA table_info('$table')").use { c ->
            val idx = c.getColumnIndex("name")
            var found = false
            while (c.moveToNext()) if (c.getString(idx) == name) found = true
            assertTrue("missing column $table.$name", found)
        }
    }

    private fun assertIndex(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, name: String, unique: Boolean) {
        db.query("PRAGMA index_list('$table')").use { c ->
            val ni=c.getColumnIndex("name"); val ui=c.getColumnIndex("unique"); var found=false
            while(c.moveToNext()) if(c.getString(ni)==name){ found=true; assertEquals(unique,c.getInt(ui)==1) }
            assertTrue("missing index $name",found)
        }
    }

    private companion object { const val DB_NAME = "phase6-migration-58-59" }
}
