package ir.restaurant.management.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Installs the sales-day close guards for the schema contract that owns the database.
 *
 * Version 35 has no `status` column: existence of a closure row means the day is closed.
 * Version 36 introduced controlled reopen and therefore requires `status = 'CLOSED'`.
 */
internal fun installSalesDayGuards(
    db: SupportSQLiteDatabase,
    hasClosureStatusColumn: Boolean = true,
) {
    val closedClosure = if (hasClosureStatusColumn) "c.status='CLOSED' AND " else ""
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_day_insert
        BEFORE INSERT ON daily_sales_summaries
        WHEN EXISTS(SELECT 1 FROM sales_day_closures c WHERE ${closedClosure}c.businessEpochDay = NEW.businessEpochDay)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_day_update
        BEFORE UPDATE ON daily_sales_summaries
        WHEN EXISTS(SELECT 1 FROM sales_day_closures c WHERE ${closedClosure}(c.businessEpochDay = OLD.businessEpochDay OR c.businessEpochDay = NEW.businessEpochDay))
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_day_delete
        BEFORE DELETE ON daily_sales_summaries
        WHEN EXISTS(SELECT 1 FROM sales_day_closures c WHERE ${closedClosure}c.businessEpochDay = OLD.businessEpochDay)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_line_insert
        BEFORE INSERT ON daily_sales_menu_lines
        WHEN EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=NEW.summaryId)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_line_update
        BEFORE UPDATE ON daily_sales_menu_lines
        WHEN EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id IN (OLD.summaryId,NEW.summaryId))
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_line_delete
        BEFORE DELETE ON daily_sales_menu_lines
        WHEN EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=OLD.summaryId)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_stock_insert
        BEFORE INSERT ON stock_movements
        WHEN NEW.referenceType='DAILY_SALES' AND EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=NEW.referenceId)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_stock_update
        BEFORE UPDATE ON stock_movements
        WHEN (OLD.referenceType='DAILY_SALES' AND EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=OLD.referenceId)) OR (NEW.referenceType='DAILY_SALES' AND EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=NEW.referenceId))
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_sales_stock_delete
        BEFORE DELETE ON stock_movements
        WHEN OLD.referenceType='DAILY_SALES' AND EXISTS(SELECT 1 FROM daily_sales_summaries s INNER JOIN sales_day_closures c ON c.businessEpochDay=s.businessEpochDay WHERE ${closedClosure}s.id=OLD.referenceId)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
}
internal fun installAccountingPeriodGuards(db: SupportSQLiteDatabase) {
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_insert BEFORE INSERT ON journal_entries WHEN EXISTS(SELECT 1 FROM accounting_period_locks p WHERE p.status='CLOSED' AND NEW.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay) BEGIN SELECT RAISE(ABORT,'ACCOUNTING_PERIOD_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_update BEFORE UPDATE ON journal_entries WHEN EXISTS(SELECT 1 FROM accounting_period_locks p WHERE p.status='CLOSED' AND (OLD.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay OR NEW.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay)) BEGIN SELECT RAISE(ABORT,'ACCOUNTING_PERIOD_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_delete BEFORE DELETE ON journal_entries WHEN EXISTS(SELECT 1 FROM accounting_period_locks p WHERE p.status='CLOSED' AND OLD.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay) BEGIN SELECT RAISE(ABORT,'ACCOUNTING_PERIOD_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_overlapping_accounting_period_insert
        BEFORE INSERT ON accounting_period_locks
        WHEN NEW.status='CLOSED' AND EXISTS(
            SELECT 1 FROM accounting_period_locks p
            WHERE p.status='CLOSED'
              AND p.fromEpochDay <= NEW.toEpochDay
              AND p.toEpochDay >= NEW.fromEpochDay
        )
        BEGIN SELECT RAISE(ABORT,'ACCOUNTING_PERIOD_OVERLAP'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_overlapping_accounting_period_update
        BEFORE UPDATE ON accounting_period_locks
        WHEN NEW.status='CLOSED' AND EXISTS(
            SELECT 1 FROM accounting_period_locks p
            WHERE p.id <> OLD.id
              AND p.status='CLOSED'
              AND p.fromEpochDay <= NEW.toEpochDay
              AND p.toEpochDay >= NEW.fromEpochDay
        )
        BEGIN SELECT RAISE(ABORT,'ACCOUNTING_PERIOD_OVERLAP'); END""")
}

internal fun installJournalLineGuards(db: SupportSQLiteDatabase) {
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS validate_journal_line_insert
        BEFORE INSERT ON journal_lines
        WHEN NEW.debitRial < 0 OR NEW.creditRial < 0 OR NOT (
            (NEW.debitRial > 0 AND NEW.creditRial = 0) OR
            (NEW.creditRial > 0 AND NEW.debitRial = 0)
        )
        BEGIN SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS validate_journal_line_update
        BEFORE UPDATE ON journal_lines
        WHEN NEW.debitRial < 0 OR NEW.creditRial < 0 OR NOT (
            (NEW.debitRial > 0 AND NEW.creditRial = 0) OR
            (NEW.creditRial > 0 AND NEW.debitRial = 0)
        )
        BEGIN SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_line_insert
        BEFORE INSERT ON journal_lines
        WHEN EXISTS(
            SELECT 1 FROM journal_entries e
            INNER JOIN accounting_period_locks p ON p.status='CLOSED'
            WHERE e.id=NEW.entryId AND e.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay
        )
        BEGIN SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_line_update
        BEFORE UPDATE ON journal_lines
        WHEN EXISTS(
            SELECT 1 FROM journal_entries e
            INNER JOIN accounting_period_locks p ON p.status='CLOSED'
            WHERE e.id IN (OLD.entryId, NEW.entryId) AND e.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay
        )
        BEGIN SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_accounting_line_delete
        BEFORE DELETE ON journal_lines
        WHEN EXISTS(
            SELECT 1 FROM journal_entries e
            INNER JOIN accounting_period_locks p ON p.status='CLOSED'
            WHERE e.id=OLD.entryId AND e.entryEpochDay BETWEEN p.fromEpochDay AND p.toEpochDay
        )
        BEGIN SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_CLOSED'); END""")
}

internal fun installRecipeVersionGuards(db: SupportSQLiteDatabase) {
    listOf(
        "trg_recipe_versions_no_update",
        "trg_recipe_versions_no_delete",
        "trg_recipe_version_ingredients_no_update",
        "trg_recipe_version_ingredients_no_delete",
    ).forEach { name -> db.execSQL("DROP TRIGGER IF EXISTS $name") }

    db.execSQL(
        """CREATE TRIGGER trg_recipe_versions_no_update
        BEFORE UPDATE ON recipe_versions
        WHEN OLD.status != 'DRAFT'
          AND NOT (
            OLD.status = 'ACTIVE' AND NEW.status = 'RETIRED'
            AND NEW.menuItemId IS OLD.menuItemId
            AND NEW.revisionNo IS OLD.revisionNo
            AND NEW.effectiveFromEpochDay IS OLD.effectiveFromEpochDay
            AND NEW.yieldMicros IS OLD.yieldMicros
            AND NEW.portionWeightMicros IS OLD.portionWeightMicros
            AND NEW.preparationWasteBasisPoints IS OLD.preparationWasteBasisPoints
            AND NEW.cookingWasteBasisPoints IS OLD.cookingWasteBasisPoints
            AND NEW.packagingCostRial IS OLD.packagingCostRial
            AND NEW.directLaborCostRial IS OLD.directLaborCostRial
            AND NEW.allocatedOverheadRial IS OLD.allocatedOverheadRial
            AND NEW.note IS OLD.note
            AND NEW.createdBy IS OLD.createdBy
            AND NEW.createdAtEpochMillis IS OLD.createdAtEpochMillis
            AND NEW.parentVersionId IS OLD.parentVersionId
          )
        BEGIN SELECT RAISE(ABORT, 'recipe versions are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_versions_no_delete
        BEFORE DELETE ON recipe_versions
        BEGIN SELECT RAISE(ABORT, 'recipe versions are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_version_ingredients_no_update
        BEFORE UPDATE ON recipe_version_ingredients
        WHEN EXISTS(
            SELECT 1 FROM recipe_versions rv
            WHERE rv.id = OLD.recipeVersionId AND rv.status != 'DRAFT'
        )
        BEGIN SELECT RAISE(ABORT, 'recipe ingredients are immutable'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER trg_recipe_version_ingredients_no_delete
        BEFORE DELETE ON recipe_version_ingredients
        WHEN EXISTS(
            SELECT 1 FROM recipe_versions rv
            WHERE rv.id = OLD.recipeVersionId AND rv.status != 'DRAFT'
        )
        BEGIN SELECT RAISE(ABORT, 'recipe ingredients are immutable'); END""",
    )
}

private val factoryResetGuardNames = listOf(
    "prevent_closed_inventory_movement_insert",
    "prevent_closed_inventory_movement_update",
    "prevent_closed_inventory_movement_delete",
    "prevent_closed_sales_day_insert",
    "prevent_closed_sales_day_update",
    "prevent_closed_sales_day_delete",
    "prevent_closed_sales_line_insert",
    "prevent_closed_sales_line_update",
    "prevent_closed_sales_line_delete",
    "prevent_closed_sales_stock_insert",
    "prevent_closed_sales_stock_update",
    "prevent_closed_sales_stock_delete",
    "prevent_closed_accounting_insert",
    "prevent_closed_accounting_update",
    "prevent_closed_accounting_delete",
    "prevent_overlapping_accounting_period_insert",
    "prevent_overlapping_accounting_period_update",
    "validate_journal_line_insert",
    "validate_journal_line_update",
    "prevent_closed_accounting_line_insert",
    "prevent_closed_accounting_line_update",
    "prevent_closed_accounting_line_delete",
    "trg_recipe_versions_no_update",
    "trg_recipe_versions_no_delete",
    "trg_recipe_version_ingredients_no_update",
    "trg_recipe_version_ingredients_no_delete",
    "trg_audit_logs_no_update",
    "trg_audit_logs_no_delete",
    "trg_audit_logs_validate_insert",
    "trg_posted_journal_entries_no_update",
    "trg_posted_journal_entries_no_delete",
    "trg_posted_journal_lines_no_insert",
    "trg_posted_journal_lines_no_update",
    "trg_posted_journal_lines_no_delete",
    "trg_journal_entries_draft_only_insert",
    "trg_journal_entries_validate_post",
    "trg_journal_entries_status_transition",
    "trg_stock_movements_validate_insert",
    "trg_stock_movements_no_update",
    "trg_stock_movements_no_delete",
    "trg_inventory_balances_validate_insert",
    "trg_inventory_balances_validate_update",
    "trg_inventory_lots_validate_insert",
    "trg_inventory_lots_validate_update",
    "trg_inventory_lots_no_delete",
    "trg_inventory_lot_consumptions_validate_insert",
    "trg_inventory_lot_consumptions_validate_update",
    "trg_inventory_lot_consumptions_no_delete",
    "trg_inventory_count_sessions_validate_insert",
    "trg_inventory_count_sessions_validate_update",
    "trg_inventory_count_sessions_no_delete",
    "trg_inventory_count_lines_validate_insert",
    "trg_inventory_count_lines_validate_update",
    "trg_inventory_count_lines_no_delete",
    "trg_inventory_counts_validate_insert",
    "trg_inventory_counts_no_update",
    "trg_inventory_counts_no_delete",
    "trg_inventory_waste_documents_validate_insert",
    "trg_inventory_waste_documents_no_update",
    "trg_inventory_waste_documents_no_delete",
    "trg_stock_transfers_validate_insert",
    "trg_stock_transfers_no_update",
    "trg_stock_transfers_no_delete",
    "trg_stock_transfer_lines_no_update",
    "trg_stock_transfer_lines_no_delete",
    "trg_stock_transfer_lines_validate_insert",
) + hrPayrollGuardNames + salesHistoryGuardNames

internal fun dropDataIntegrityGuardsForFactoryReset(db: SupportSQLiteDatabase) {
    factoryResetGuardNames.forEach { name -> db.execSQL("DROP TRIGGER IF EXISTS $name") }
}

/**
 * Room clears tables with DELETE statements. Closed-period and immutable-history guards intentionally
 * block such deletes during normal operation, so they are suspended only for the owner-authorized reset.
 * The onOpen callback is an additional safety net if the process stops before the finally block.
 */
internal fun AppDatabase.clearAllTablesForFactoryReset() {
    val sqlite = openHelper.writableDatabase
    // Android 6 ships an older SQLite engine whose immediate FK enforcement can make
    // Room's generated clearAllTables() fail solely because of delete ordering. Factory
    // reset is the one owner-authorized destructive operation, so FK enforcement is
    // suspended only for the clear itself and restored before any seed data is written.
    sqlite.execSQL("PRAGMA foreign_keys=OFF")
    dropDataIntegrityGuardsForFactoryReset(sqlite)
    try {
        clearAllTables()
    } finally {
        sqlite.execSQL("PRAGMA foreign_keys=ON")
        AccountSeedCallback.seedMissingAccounts(sqlite)
        AccountSeedCallback.seedSystemLocations(sqlite)
        AccountSeedCallback.installClosedPeriodGuards(sqlite)
        installSalesDayGuards(sqlite)
        installAccountingPeriodGuards(sqlite)
        installJournalLineGuards(sqlite)
        installRecipeVersionGuards(sqlite)
        installAuditLogGuards(sqlite)
        installPostedJournalGuards(sqlite)
        installStockMovementGuards(sqlite)
        installInventoryBalanceGuards(sqlite)
        installInventoryLotGuards(sqlite)
        installInventoryCountSessionGuards(sqlite)
        installInventoryCountGuards(sqlite)
        installWasteDocumentGuards(sqlite)
        installStockTransferGuards(sqlite)
        installHrPayrollGuards(sqlite)
        installSalesHistoryGuards(sqlite)
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            check(!cursor.moveToFirst()) { "FACTORY_RESET_FOREIGN_KEY_VIOLATION" }
        }
    }
}
