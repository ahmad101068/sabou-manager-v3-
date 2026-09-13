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
class Migration56To57Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun phase4MigrationBackfillsPremiumPolicyAndAddsAttendanceBranchProvenance() {
        helper.createDatabase(DB_NAME, 56).use { db ->
            db.execSQL(
                "INSERT INTO payroll_policies(id,title,versionNo,effectiveFromEpochDay,effectiveToEpochDay,overtimeHourlyRateRial,absenceDailyDeductionRial,lateMinuteDeductionRial,overtimeMultiplierBasisPoints,insuranceBasisPoints,taxBasisPoints,status,createdBy,createdByActorId,createdAtEpochMillis,correlationId) " +
                    "VALUES(4,'Legacy payroll',3,20000,NULL,1200000,0,0,15000,700,1000,'ACTIVE','manager',NULL,1,'legacy:policy')",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 57, true, MIGRATION_56_57).use { db ->
            db.query("SELECT holidayMultiplierBasisPoints,nightMultiplierBasisPoints FROM payroll_policies WHERE id=4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(10_000, cursor.getInt(0))
                assertEquals(10_000, cursor.getInt(1))
            }
            db.query("PRAGMA table_info(attendance_events)").use { cursor ->
                var branchFound = false
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) if (cursor.getString(nameIndex) == "branchId") branchFound = true
                assertTrue(branchFound)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_attendance_events_branchId'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            db.query("PRAGMA table_info(payroll_snapshots)").use { cursor ->
                val required = mutableSetOf("nightMinutes", "holidayMinutes", "nightMultiplierBasisPoints", "holidayMultiplierBasisPoints")
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) required.remove(cursor.getString(nameIndex))
                assertTrue(required.isEmpty())
            }
            db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name='trg_attendance_corrections_controlled_update'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val triggerSql = cursor.getString(0)
                assertTrue(triggerSql.contains("'APPROVED','REJECTED'"))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    @Test
    fun migrationRegistryContainsForwardOnly56To57() {
        val registry = ALL_MIGRATIONS.toList()
        assertTrue(registry.any { it.startVersion == 56 && it.endVersion == 57 })
        assertFalse(registry.any { it.startVersion == 57 && it.endVersion == 56 })
    }

    private companion object {
        const val DB_NAME = "phase4-migration-56-57"
    }
}
