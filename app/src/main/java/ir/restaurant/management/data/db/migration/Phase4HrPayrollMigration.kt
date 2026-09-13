package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 4 — HR attendance provenance and versioned payroll premium policy. */
internal val MIGRATION_56_57 = object : Migration(56, 57) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE attendance_events ADD COLUMN branchId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_events_branchId ON attendance_events(branchId)")

        db.execSQL("ALTER TABLE payroll_policies ADD COLUMN holidayMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")
        db.execSQL("ALTER TABLE payroll_policies ADD COLUMN nightMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")

        db.execSQL("ALTER TABLE payroll_snapshots ADD COLUMN nightMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_snapshots ADD COLUMN holidayMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_snapshots ADD COLUMN nightMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")
        db.execSQL("ALTER TABLE payroll_snapshots ADD COLUMN holidayMultiplierBasisPoints INTEGER NOT NULL DEFAULT 10000")

        installHrPayrollGuards(db)
    }
}
