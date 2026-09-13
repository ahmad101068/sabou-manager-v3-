package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Phase 2 business-core storage. Historical cash/card/transfer columns remain compatibility snapshots only. */
internal val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `customers` ADD COLUMN `partyType` TEXT NOT NULL DEFAULT 'PERSON'")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `globalId` TEXT NOT NULL DEFAULT 'legacy:daily_sales:0'")
        db.execSQL("UPDATE daily_sales_summaries SET globalId='legacy:daily_sales:'||id")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `branchId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `returnRial` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `createdByUserId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'POSTED'")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `updatedByUserId` INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE `daily_sales_summaries` ADD COLUMN `updatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE daily_sales_summaries SET updatedAtEpochMillis=createdAtEpochMillis WHERE updatedAtEpochMillis=0")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_summaries_branchId` ON `daily_sales_summaries` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_summaries_branchId_businessEpochDay` ON `daily_sales_summaries` (`branchId`,`businessEpochDay`)")
        // Rebuild menu lines once so Phase 2 can preserve an unknown line amount as NULL.
        db.execSQL("CREATE TABLE `daily_sales_menu_lines_p2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `summaryId` INTEGER NOT NULL, `menuItemId` INTEGER, `recipeVersionId` INTEGER, `menuItemNameSnapshot` TEXT NOT NULL, `quantityMicros` INTEGER NOT NULL, `grossSalesRial` INTEGER, `theoreticalCostRial` INTEGER NOT NULL, `foodCostSnapshotRial` INTEGER, `packagingCostSnapshotRial` INTEGER, `directLaborCostSnapshotRial` INTEGER, `allocatedOverheadSnapshotRial` INTEGER, FOREIGN KEY(`summaryId`) REFERENCES `daily_sales_summaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT INTO `daily_sales_menu_lines_p2` SELECT `id`,`summaryId`,`menuItemId`,`recipeVersionId`,`menuItemNameSnapshot`,`quantityMicros`,`grossSalesRial`,`theoreticalCostRial`,`foodCostSnapshotRial`,`packagingCostSnapshotRial`,`directLaborCostSnapshotRial`,`allocatedOverheadSnapshotRial` FROM `daily_sales_menu_lines`")
        db.execSQL("DROP TABLE `daily_sales_menu_lines`")
        db.execSQL("ALTER TABLE `daily_sales_menu_lines_p2` RENAME TO `daily_sales_menu_lines`")
        db.execSQL("CREATE INDEX `index_daily_sales_menu_lines_summaryId` ON `daily_sales_menu_lines` (`summaryId`)")
        db.execSQL("CREATE INDEX `index_daily_sales_menu_lines_menuItemId` ON `daily_sales_menu_lines` (`menuItemId`)")
        db.execSQL("CREATE INDEX `index_daily_sales_menu_lines_recipeVersionId` ON `daily_sales_menu_lines` (`recipeVersionId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_sales_settlements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `dailySalesId` INTEGER NOT NULL, `type` TEXT NOT NULL, `amountRial` INTEGER NOT NULL, `cashboxId` INTEGER, `bankAccountId` INTEGER, `cardTerminalId` INTEGER, `partyId` INTEGER, `dueEpochDay` INTEGER, `contractId` INTEGER, `referenceNumber` TEXT, `note` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`dailySalesId`) REFERENCES `daily_sales_summaries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`partyId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_settlements_dailySalesId` ON `daily_sales_settlements` (`dailySalesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_settlements_type` ON `daily_sales_settlements` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_settlements_partyId` ON `daily_sales_settlements` (`partyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_sales_settlements_dueEpochDay` ON `daily_sales_settlements` (`dueEpochDay`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_sales_settlements_globalId` ON `daily_sales_settlements` (`globalId`)")
        db.execSQL("INSERT INTO daily_sales_settlements(globalId,dailySalesId,type,amountRial,createdAtEpochMillis,updatedAtEpochMillis) SELECT 'm49-cash-'||id,id,'CASH',cashRial,createdAtEpochMillis,createdAtEpochMillis FROM daily_sales_summaries WHERE cashRial>0")
        db.execSQL("INSERT INTO daily_sales_settlements(globalId,dailySalesId,type,amountRial,createdAtEpochMillis,updatedAtEpochMillis) SELECT 'm49-card-'||id,id,'CARD',cardRial,createdAtEpochMillis,createdAtEpochMillis FROM daily_sales_summaries WHERE cardRial>0")
        db.execSQL("INSERT INTO daily_sales_settlements(globalId,dailySalesId,type,amountRial,createdAtEpochMillis,updatedAtEpochMillis) SELECT 'm49-transfer-'||id,id,'BANK_TRANSFER',transferRial,createdAtEpochMillis,createdAtEpochMillis FROM daily_sales_summaries WHERE transferRial>0")

        db.execSQL("CREATE TABLE IF NOT EXISTS `receivables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `branchId` INTEGER NOT NULL, `partyId` INTEGER NOT NULL, `type` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `originalAmountRial` INTEGER NOT NULL, `paidAmountRial` INTEGER NOT NULL, `outstandingAmountRial` INTEGER NOT NULL, `issueEpochDay` INTEGER NOT NULL, `dueEpochDay` INTEGER, `status` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`partyId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_receivables_globalId` ON `receivables` (`globalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivables_branchId` ON `receivables` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivables_partyId` ON `receivables` (`partyId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivables_dueEpochDay` ON `receivables` (`dueEpochDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivables_status` ON `receivables` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivables_sourceType_sourceId` ON `receivables` (`sourceType`,`sourceId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `receivable_collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `receivableId` INTEGER NOT NULL, `amountRial` INTEGER NOT NULL, `method` TEXT NOT NULL, `cashboxId` INTEGER, `bankAccountId` INTEGER, `reference` TEXT, `businessEpochDay` INTEGER NOT NULL, `createdByUserId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`receivableId`) REFERENCES `receivables`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivable_collections_receivableId` ON `receivable_collections` (`receivableId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_receivable_collections_businessEpochDay` ON `receivable_collections` (`businessEpochDay`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_receivable_collections_globalId` ON `receivable_collections` (`globalId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `management_issues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `branchId` INTEGER NOT NULL, `type` TEXT NOT NULL, `severity` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `financialImpactRial` INTEGER, `businessEpochDay` INTEGER NOT NULL, `detectedAtEpochMillis` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `sourceId` INTEGER NOT NULL, `deduplicationKey` TEXT NOT NULL, `status` TEXT NOT NULL, `assignedUserId` INTEGER, `assignedEmployeeId` INTEGER, `dueAtEpochMillis` INTEGER, `resolutionNote` TEXT, `resolvedByUserId` INTEGER, `resolvedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_management_issues_globalId` ON `management_issues` (`globalId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_management_issues_deduplicationKey` ON `management_issues` (`deduplicationKey`)")
        listOf("branchId","type","status","assignedEmployeeId").forEach { db.execSQL("CREATE INDEX IF NOT EXISTS `index_management_issues_${it}` ON `management_issues` (`${it}`)") }
        db.execSQL("CREATE TABLE IF NOT EXISTS `management_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `globalId` TEXT NOT NULL, `branchId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `assignedUserId` INTEGER, `assignedEmployeeId` INTEGER, `createdByUserId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `dueAtEpochMillis` INTEGER, `startedAtEpochMillis` INTEGER, `completedAtEpochMillis` INTEGER, `requiresApproval` INTEGER NOT NULL, `approvedByUserId` INTEGER, `approvedAtEpochMillis` INTEGER, `requiresAttachment` INTEGER NOT NULL, `sourceIssueId` INTEGER, `sourceType` TEXT, `sourceId` INTEGER, `note` TEXT, FOREIGN KEY(`sourceIssueId`) REFERENCES `management_issues`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
        listOf("branchId","status","assignedEmployeeId","sourceIssueId").forEach { db.execSQL("CREATE INDEX IF NOT EXISTS `index_management_tasks_${it}` ON `management_tasks` (`${it}`)") }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_management_tasks_globalId` ON `management_tasks` (`globalId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `task_attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `storageReference` TEXT NOT NULL, `mimeType` TEXT, `originalName` TEXT, `createdByUserId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, FOREIGN KEY(`taskId`) REFERENCES `management_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_attachments_taskId` ON `task_attachments` (`taskId`)")

        db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` INTEGER, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdByUserId` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_templates_branchId` ON `checklist_templates` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_templates_type` ON `checklist_templates` (`type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_templates_active` ON `checklist_templates` (`active`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_template_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `sortOrder` INTEGER NOT NULL, `required` INTEGER NOT NULL, `requiresPhoto` INTEGER NOT NULL, `requiresNoteOnFailure` INTEGER NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_template_items_templateId` ON `checklist_template_items` (`templateId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_runs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `branchId` INTEGER NOT NULL, `businessEpochDay` INTEGER NOT NULL, `assignedEmployeeId` INTEGER, `status` TEXT NOT NULL, `startedAtEpochMillis` INTEGER, `completedAtEpochMillis` INTEGER, `approvedByUserId` INTEGER, `approvedAtEpochMillis` INTEGER, FOREIGN KEY(`templateId`) REFERENCES `checklist_templates`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_runs_templateId` ON `checklist_runs` (`templateId`)")
        listOf("branchId","businessEpochDay","assignedEmployeeId","status").forEach { db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_runs_${it}` ON `checklist_runs` (`${it}`)") }
        db.execSQL("CREATE TABLE IF NOT EXISTS `checklist_run_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` INTEGER NOT NULL, `templateItemId` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT, `attachmentReference` TEXT, `completedByUserId` INTEGER, `completedAtEpochMillis` INTEGER, FOREIGN KEY(`runId`) REFERENCES `checklist_runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`templateItemId`) REFERENCES `checklist_template_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_run_items_runId` ON `checklist_run_items` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_run_items_templateItemId` ON `checklist_run_items` (`templateItemId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `management_rule_thresholds` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `branchId` INTEGER, `key` TEXT NOT NULL, `valueBasisPoints` INTEGER, `valueRial` INTEGER, `updatedByUserId` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_management_rule_thresholds_branchId_key` ON `management_rule_thresholds` (`branchId`,`key`)")
    }
}


/** Phase 2 correction: deterministic threshold scope + reversible receivable collections. */
internal val MIGRATION_50_51 = object : androidx.room.migration.Migration(50, 51) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `receivable_collections` ADD COLUMN `reversedAtEpochMillis` INTEGER")
        db.execSQL("ALTER TABLE `receivable_collections` ADD COLUMN `reversalReason` TEXT")
        db.execSQL("ALTER TABLE `receivable_collections` ADD COLUMN `reversalJournalEntryId` INTEGER")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `management_rule_thresholds_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `branchScopeId` INTEGER NOT NULL,
                `key` TEXT NOT NULL,
                `valueBasisPoints` INTEGER,
                `valueRial` INTEGER,
                `updatedByUserId` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
        """.trimIndent())
        // Keep one deterministic winner per (scope,key).  NULL globals become scope 0.
        db.execSQL("""
            INSERT INTO `management_rule_thresholds_new`
                (`id`,`branchScopeId`,`key`,`valueBasisPoints`,`valueRial`,`updatedByUserId`,`updatedAtEpochMillis`)
            SELECT t.`id`, COALESCE(t.`branchId`,0), t.`key`, t.`valueBasisPoints`, t.`valueRial`, t.`updatedByUserId`, t.`updatedAtEpochMillis`
            FROM `management_rule_thresholds` t
            WHERE t.`id` = (
                SELECT t2.`id` FROM `management_rule_thresholds` t2
                WHERE COALESCE(t2.`branchId`,0)=COALESCE(t.`branchId`,0) AND t2.`key`=t.`key`
                ORDER BY t2.`updatedAtEpochMillis` DESC, t2.`id` DESC LIMIT 1
            )
        """.trimIndent())
        db.execSQL("DROP TABLE `management_rule_thresholds`")
        db.execSQL("ALTER TABLE `management_rule_thresholds_new` RENAME TO `management_rule_thresholds`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_management_rule_thresholds_branchScopeId_key` ON `management_rule_thresholds` (`branchScopeId`,`key`)")
    }
}


/** Final Phase 2 correction: repair persisted Net Sales semantics and make sales-day closure branch-safe. */
internal val MIGRATION_51_52 = object : androidx.room.migration.Migration(51, 52) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Through schema 51, Daily Sales stored Amount To Settle (Net + Service + Tax) in netSalesRial.
        // Rewrite only rows matching that historical formula; already-correct rows remain untouched.
        db.execSQL(
            """
            UPDATE `daily_sales_summaries`
            SET `netSalesRial` = `grossSalesRial` - `discountRial` - `returnRial`
            WHERE `netSalesRial` = (`grossSalesRial` - `discountRial` - `returnRial` + `serviceRial` + `taxRial`)
              AND (`serviceRial` != 0 OR `taxRial` != 0)
            """.trimIndent(),
        )
        // Closure rows are snapshots of the summary and must follow the repaired Net Sales meaning.
        db.execSQL(
            """
            UPDATE `sales_day_closures`
            SET `netSalesRial` = (
                SELECT s.`netSalesRial` FROM `daily_sales_summaries` s WHERE s.`id` = `sales_day_closures`.`summaryId`
            )
            WHERE EXISTS (SELECT 1 FROM `daily_sales_summaries` s WHERE s.`id` = `sales_day_closures`.`summaryId`)
            """.trimIndent(),
        )

        // Through schema 51, businessEpochDay was the primary key, which silently made
        // a second branch unable to close the same calendar/business day.  A closure is
        // owned by one Daily Sales summary, so summaryId is the stable per-branch identity.
        db.execSQL(
            """
            CREATE TABLE `sales_day_closures_p2_final` (
                `businessEpochDay` INTEGER NOT NULL,
                `summaryId` INTEGER NOT NULL,
                `grossSalesRial` INTEGER NOT NULL,
                `netSalesRial` INTEGER NOT NULL,
                `theoreticalCostRial` INTEGER NOT NULL,
                `cashRial` INTEGER NOT NULL,
                `cardRial` INTEGER NOT NULL,
                `transferRial` INTEGER NOT NULL,
                `status` TEXT NOT NULL DEFAULT 'CLOSED',
                `revisionNo` INTEGER NOT NULL DEFAULT 1,
                `closedBy` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `reopenedBy` TEXT,
                `reopenReason` TEXT NOT NULL DEFAULT '',
                `reopenedAtEpochMillis` INTEGER,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`summaryId`),
                FOREIGN KEY(`summaryId`) REFERENCES `daily_sales_summaries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `sales_day_closures_p2_final` (
                `businessEpochDay`,`summaryId`,`grossSalesRial`,`netSalesRial`,`theoreticalCostRial`,
                `cashRial`,`cardRial`,`transferRial`,`status`,`revisionNo`,`closedBy`,`note`,
                `reopenedBy`,`reopenReason`,`reopenedAtEpochMillis`,`createdAtEpochMillis`
            )
            SELECT
                `businessEpochDay`,`summaryId`,`grossSalesRial`,`netSalesRial`,`theoreticalCostRial`,
                `cashRial`,`cardRial`,`transferRial`,`status`,`revisionNo`,`closedBy`,`note`,
                `reopenedBy`,`reopenReason`,`reopenedAtEpochMillis`,`createdAtEpochMillis`
            FROM `sales_day_closures`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `sales_day_closures`")
        db.execSQL("ALTER TABLE `sales_day_closures_p2_final` RENAME TO `sales_day_closures`")
        db.execSQL("CREATE INDEX `index_sales_day_closures_businessEpochDay` ON `sales_day_closures` (`businessEpochDay`)")
        db.execSQL("CREATE INDEX `index_sales_day_closures_createdAtEpochMillis` ON `sales_day_closures` (`createdAtEpochMillis`)")
    }
}
