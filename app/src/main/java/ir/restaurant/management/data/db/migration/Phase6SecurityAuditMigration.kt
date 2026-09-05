package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 6 — durable audit actor snapshots, scoped/snoozable alerts and management maker-checker evidence. */
internal val MIGRATION_58_59 = object : Migration(58, 59) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN actorRoleSnapshot TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN actorBranchIdSnapshot INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_actorBranchIdSnapshot ON audit_logs(actorBranchIdSnapshot)")

        db.execSQL("ALTER TABLE app_alerts ADD COLUMN branchId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_alerts ADD COLUMN locationId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_alerts ADD COLUMN snoozedUntilEpochMillis INTEGER")
        db.execSQL("DROP INDEX IF EXISTS index_app_alerts_sourceType_sourceId")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_app_alerts_sourceType_sourceId_locationId ON app_alerts(sourceType,sourceId,locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_branchId ON app_alerts(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_locationId ON app_alerts(locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_alerts_snoozedUntilEpochMillis ON app_alerts(snoozedUntilEpochMillis)")

        db.execSQL("ALTER TABLE management_tasks ADD COLUMN completedByUserId INTEGER")
        db.execSQL("ALTER TABLE checklist_runs ADD COLUMN completedByUserId INTEGER")
    }
}
