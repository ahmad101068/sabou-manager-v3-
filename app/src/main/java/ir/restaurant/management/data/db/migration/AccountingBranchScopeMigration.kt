package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the branch dimension to the existing accounting journal. Historical rows are never assigned
 * to a branch by a textual/default heuristic. Only deterministic numeric source relations are used.
 */
internal val MIGRATION_52_53 = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `branchId` INTEGER")
        db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `accountingScope` TEXT NOT NULL DEFAULT 'UNASSIGNED_LEGACY'")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_entries_branchId` ON `journal_entries` (`branchId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_journal_entries_accountingScope_branchId_entryEpochDay` " +
                "ON `journal_entries` (`accountingScope`,`branchId`,`entryEpochDay`)",
        )

        // Daily Sales created by the branch-aware workflow is deterministic. Converted legacy
        // archives are intentionally excluded because their Phase-2 branchId=1 was only a migration
        // compatibility default, not historical evidence of the real branch.
        db.execSQL(
            """UPDATE journal_entries
               SET branchId = (
                       SELECT ds.branchId FROM daily_sales_summaries ds
                       WHERE ds.id = journal_entries.sourceId AND ds.isLegacyArchive = 0
                   ),
                   accountingScope = 'BRANCH'
               WHERE sourceType IN ('DAILY_SALES','DAILY_SALES_COGS','DAILY_SALES_REVERSAL','DAILY_SALES_COGS_REVERSAL')
                 AND EXISTS (
                       SELECT 1 FROM daily_sales_summaries ds
                       WHERE ds.id = journal_entries.sourceId
                         AND ds.isLegacyArchive = 0
                         AND ds.branchId > 0
                   )""".trimIndent(),
        )

        // Receivable masters were introduced with a mandatory numeric branch id, so this relation is
        // deterministic for both the document and its explicit reversal source.
        db.execSQL(
            """UPDATE journal_entries
               SET branchId = (SELECT r.branchId FROM receivables r WHERE r.id = journal_entries.sourceId),
                   accountingScope = 'BRANCH'
               WHERE sourceType IN ('RECEIVABLE','RECEIVABLE_REVERSAL')
                 AND EXISTS (
                       SELECT 1 FROM receivables r
                       WHERE r.id = journal_entries.sourceId AND r.branchId > 0
                   )""".trimIndent(),
        )

        // A collection belongs to exactly one receivable; collection reversals use the same
        // collection id. This is a deterministic FK chain: Collection -> Receivable -> branchId.
        db.execSQL(
            """UPDATE journal_entries
               SET branchId = (
                       SELECT r.branchId
                       FROM receivable_collections c
                       JOIN receivables r ON r.id = c.receivableId
                       WHERE c.id = journal_entries.sourceId
                   ),
                   accountingScope = 'BRANCH'
               WHERE sourceType IN ('RECEIVABLE_COLLECTION','RECEIVABLE_COLLECTION_REVERSAL')
                 AND EXISTS (
                       SELECT 1
                       FROM receivable_collections c
                       JOIN receivables r ON r.id = c.receivableId
                       WHERE c.id = journal_entries.sourceId AND r.branchId > 0
                   )""".trimIndent(),
        )

        // Reversal rows also preserve the exact scope of their original journal even when their
        // source type is a legacy subsystem-specific name.
        db.execSQL(
            """UPDATE journal_entries
               SET branchId = (
                       SELECT original.branchId FROM journal_entries original
                       WHERE original.id = journal_entries.reversalOfEntryId
                   ),
                   accountingScope = (
                       SELECT original.accountingScope FROM journal_entries original
                       WHERE original.id = journal_entries.reversalOfEntryId
                   )
               WHERE reversalOfEntryId IS NOT NULL
                 AND EXISTS (
                       SELECT 1 FROM journal_entries original
                       WHERE original.id = journal_entries.reversalOfEntryId
                         AND original.accountingScope IN ('BRANCH','ORGANIZATION')
                   )""".trimIndent(),
        )

        // Safety invariant: BRANCH must have a positive branch id; the other scopes must not.
        // Any row not deterministically attributed remains UNASSIGNED_LEGACY with branchId NULL.
        db.execSQL(
            """UPDATE journal_entries
               SET accountingScope = 'UNASSIGNED_LEGACY', branchId = NULL
               WHERE (accountingScope = 'BRANCH' AND (branchId IS NULL OR branchId <= 0))
                  OR (accountingScope IN ('ORGANIZATION','UNASSIGNED_LEGACY') AND branchId IS NOT NULL)""".trimIndent(),
        )
    }
}
