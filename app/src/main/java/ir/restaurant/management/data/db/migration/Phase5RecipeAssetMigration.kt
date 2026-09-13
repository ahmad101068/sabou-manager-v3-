package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 5 — historical recipe substitutions and auditable, reversible depreciation detail. */
internal val MIGRATION_57_58 = object : Migration(57, 58) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_substitutions ADD COLUMN effectiveFromEpochDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE recipe_substitutions SET effectiveFromEpochDay=CAST(createdAtEpochMillis/86400000 AS INTEGER) WHERE effectiveFromEpochDay=0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_substitutions_effectiveFromEpochDay ON recipe_substitutions(effectiveFromEpochDay)")

        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN postingEpochDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN commandId TEXT")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN reversedAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN reversalEpochDay INTEGER")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN reversalReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE asset_depreciations ADD COLUMN reversalJournalEntryId INTEGER")
        db.execSQL("DROP INDEX IF EXISTS index_asset_depreciations_assetId_periodYear_periodMonth")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_depreciations_assetId_periodYear_periodMonth ON asset_depreciations(assetId,periodYear,periodMonth)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_depreciations_reversalJournalEntryId ON asset_depreciations(reversalJournalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asset_depreciations_commandId ON asset_depreciations(commandId)")
    }
}
