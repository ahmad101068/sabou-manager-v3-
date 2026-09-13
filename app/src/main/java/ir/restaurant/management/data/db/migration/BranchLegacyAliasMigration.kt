package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the stable branchId -> historical display-name compatibility boundary. */
internal val MIGRATION_54_55 = object : Migration(54, 55) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `branch_legacy_aliases` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `branchId` INTEGER NOT NULL,
                `aliasName` TEXT NOT NULL,
                `normalizedAlias` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                FOREIGN KEY(`branchId`) REFERENCES `branches`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_branch_legacy_aliases_branchId_normalizedAlias` ON `branch_legacy_aliases` (`branchId`, `normalizedAlias`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_branch_legacy_aliases_branchId` ON `branch_legacy_aliases` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_branch_legacy_aliases_normalizedAlias` ON `branch_legacy_aliases` (`normalizedAlias`)")

        // Current display names become aliases without rewriting any historical business row.
        db.execSQL(
            """
            INSERT OR IGNORE INTO branch_legacy_aliases(branchId, aliasName, normalizedAlias, createdAtEpochMillis)
            SELECT id, trim(name), lower(trim(name)), updatedAtEpochMillis
            FROM branches
            WHERE trim(name) <> ''
            """.trimIndent(),
        )

        // Branches created by v54 legacy-name canonicalization also retain the original normalized key.
        db.execSQL(
            """
            INSERT OR IGNORE INTO branch_legacy_aliases(branchId, aliasName, normalizedAlias, createdAtEpochMillis)
            SELECT id, substr(globalId, length('legacy:branch:name:') + 1),
                   lower(trim(substr(globalId, length('legacy:branch:name:') + 1))), updatedAtEpochMillis
            FROM branches
            WHERE globalId LIKE 'legacy:branch:name:%'
              AND trim(substr(globalId, length('legacy:branch:name:') + 1)) <> ''
            """.trimIndent(),
        )
    }
}
