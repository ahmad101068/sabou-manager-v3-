package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Non-destructive Phase 2 branch canonicalization appended to the historical migration chain.
 * Version 53 remains the only supported pre-port baseline for this migration.
 */
internal val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `branches` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `globalId` TEXT NOT NULL,
                `organizationId` INTEGER,
                `code` TEXT,
                `name` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_branches_globalId` ON `branches` (`globalId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_branches_organizationId_code` ON `branches` (`organizationId`, `code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_branches_name` ON `branches` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_branches_isActive` ON `branches` (`isActive`)")

        // Preserve every already-numeric branch identity before touching legacy text fields.
        val numericSources = listOf(
            "SELECT DISTINCT branchId AS id FROM daily_sales_summaries WHERE branchId > 0 AND isLegacyArchive = 0",
            "SELECT branchId AS id FROM journal_entries WHERE branchId IS NOT NULL AND branchId > 0",
            "SELECT branchId AS id FROM receivables WHERE branchId > 0",
            "SELECT branchId AS id FROM management_issues WHERE branchId > 0",
            "SELECT branchId AS id FROM management_tasks WHERE branchId > 0",
            "SELECT branchId AS id FROM checklist_templates WHERE branchId IS NOT NULL AND branchId > 0",
            "SELECT branchId AS id FROM checklist_runs WHERE branchId > 0",
            "SELECT branchId AS id FROM shift_templates WHERE branchId IS NOT NULL AND branchId > 0",
        ).joinToString(" UNION ")
        db.execSQL(
            """
            INSERT OR IGNORE INTO branches(id, globalId, organizationId, code, name, isActive, createdAtEpochMillis, updatedAtEpochMillis)
            SELECT id, 'legacy:branch:id:' || id, NULL, NULL, 'Legacy Branch #' || id, 1, 0, 0
            FROM ($numericSources)
            """.trimIndent(),
        )

        removeLegacyDailySalesBranchDefault(db)

        addNullableBranchId(db, "employees")
        addNullableBranchId(db, "employment_assignments")
        addNullableBranchId(db, "payroll_batches")
        addNullableBranchId(db, "storage_locations")
        addNullableBranchId(db, "purchases")
        addNullableBranchId(db, "fixed_assets")
        addNullableBranchId(db, "work_schedules")
        addNullableBranchId(db, "sales_cash_reconciliations")

        // Only exact normalized legacy keys are canonicalized. Ambiguous normalized keys are intentionally skipped.
        val legacyNames = """
            SELECT trim(branchName) AS rawName FROM employees WHERE trim(branchName) <> ''
            UNION ALL SELECT trim(branchName) FROM employment_assignments WHERE trim(branchName) <> ''
            UNION ALL SELECT trim(branchName) FROM payroll_batches WHERE branchName IS NOT NULL AND trim(branchName) <> ''
            UNION ALL SELECT trim(branchName) FROM storage_locations WHERE trim(branchName) <> ''
            UNION ALL SELECT trim(branchName) FROM purchases WHERE trim(branchName) <> ''
            UNION ALL SELECT trim(branch) FROM fixed_assets WHERE trim(branch) <> ''
            UNION ALL SELECT trim(branchName) FROM work_schedules WHERE trim(branchName) <> ''
        """.trimIndent()
        db.execSQL(
            """
            INSERT OR IGNORE INTO branches(globalId, organizationId, code, name, isActive, createdAtEpochMillis, updatedAtEpochMillis)
            SELECT 'legacy:branch:name:' || lower(rawName), NULL, NULL, MIN(rawName), 1, 0, 0
            FROM ($legacyNames)
            GROUP BY lower(rawName)
            HAVING COUNT(DISTINCT rawName) = 1
            """.trimIndent(),
        )

        backfill(db, "employees", "branchName")
        backfill(db, "employment_assignments", "branchName")
        backfill(db, "payroll_batches", "branchName")
        backfill(db, "storage_locations", "branchName")
        backfill(db, "purchases", "branchName")
        backfill(db, "fixed_assets", "branch")
        backfill(db, "work_schedules", "branchName")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_employees_branchId` ON `employees` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_employment_assignments_branchId` ON `employment_assignments` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_payroll_batches_branchId` ON `payroll_batches` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_storage_locations_branchId` ON `storage_locations` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_branchId` ON `purchases` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixed_assets_branchId` ON `fixed_assets` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_schedules_branchId` ON `work_schedules` (`branchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_cash_reconciliations_branchId` ON `sales_cash_reconciliations` (`branchId`)")
    }

    /**
     * Room v1 carried a SQLite DEFAULT 1 on daily_sales_summaries.branchId. Keeping that DDL
     * would allow raw/legacy inserts to acquire Branch 1 without an explicit business decision.
     * Rebuild only this table and its direct FK children from their actual sqlite_master DDL,
     * preserving every column, row, index, trigger and FK definition while removing that default.
     */
    private fun removeLegacyDailySalesBranchDefault(db: SupportSQLiteDatabase) {
        val parent = "daily_sales_summaries"
        val directChildren = listOf("daily_sales_menu_lines", "sales_day_closures", "daily_sales_settlements")
        val parentSql = tableSql(db, parent) ?: return
        val withoutDefault = parentSql.replace(
            Regex(
                """([`"]?branchId[`"]?\s+INTEGER\s+NOT\s+NULL)\s+DEFAULT\s+(?:1|'1'|"1"|\(\s*1\s*\))""",
                RegexOption.IGNORE_CASE,
            ),
            "$1",
        )
        require(withoutDefault != parentSql) { "daily_sales_branch_default_not_found" }

        val tableDefinitions = linkedMapOf(parent to withoutDefault)
        directChildren.forEach { child ->
            tableSql(db, child)?.let { tableDefinitions[child] = it }
        }
        val affectedTables = tableDefinitions.keys.toList()
        val schemaObjects = affectedTables.flatMap { table -> schemaObjects(db, table) }

        affectedTables.forEach { table ->
            val backup = branchMigrationBackupName(table)
            db.execSQL("DROP TABLE IF EXISTS temp.`$backup`")
            db.execSQL("CREATE TEMP TABLE `$backup` AS SELECT * FROM `$table`")
        }

        // Children must be removed first so the parent can be rebuilt without violating FK RESTRICT.
        affectedTables.asReversed().forEach { table -> db.execSQL("DROP TABLE `$table`") }

        affectedTables.forEach { table ->
            db.execSQL(requireNotNull(tableDefinitions[table]))
            val backup = branchMigrationBackupName(table)
            db.execSQL("INSERT INTO `$table` SELECT * FROM temp.`$backup`")
        }
        schemaObjects.forEach { sql -> db.execSQL(sql) }
        affectedTables.forEach { table -> db.execSQL("DROP TABLE temp.`${branchMigrationBackupName(table)}`") }

        db.query("PRAGMA table_info(`daily_sales_summaries`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "branchId") {
                    found = true
                    require(cursor.isNull(4)) { "daily_sales_branch_default_still_present" }
                }
            }
            require(found) { "daily_sales_branch_column_missing" }
        }
    }

    private fun tableSql(db: SupportSQLiteDatabase, table: String): String? =
        db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='$table' LIMIT 1").use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }

    private fun schemaObjects(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query(
            "SELECT sql FROM sqlite_master WHERE tbl_name='$table' AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type, name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun branchMigrationBackupName(table: String): String = "phase2_branch_backup_$table"

    private fun addNullableBranchId(db: SupportSQLiteDatabase, table: String) {
        db.execSQL("ALTER TABLE `$table` ADD COLUMN `branchId` INTEGER")
    }

    private fun backfill(db: SupportSQLiteDatabase, table: String, legacyColumn: String) {
        db.execSQL(
            """
            UPDATE `$table`
            SET branchId = (
                SELECT b.id FROM branches b
                WHERE b.globalId = 'legacy:branch:name:' || lower(trim(`$table`.`$legacyColumn`))
                LIMIT 1
            )
            WHERE branchId IS NULL
              AND trim(`$legacyColumn`) <> ''
              AND EXISTS (
                  SELECT 1 FROM branches b
                  WHERE b.globalId = 'legacy:branch:name:' || lower(trim(`$table`.`$legacyColumn`))
              )
            """.trimIndent(),
        )
    }
}
