package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Operational-domain historical upgrade edges. */
internal val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS daily_sales_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                businessEpochDay INTEGER NOT NULL,
                grossSalesRial INTEGER NOT NULL,
                discountRial INTEGER NOT NULL,
                serviceRial INTEGER NOT NULL,
                taxRial INTEGER NOT NULL,
                netSalesRial INTEGER NOT NULL,
                theoreticalCostRial INTEGER NOT NULL,
                cashRial INTEGER NOT NULL,
                cardRial INTEGER NOT NULL,
                transferRial INTEGER NOT NULL,
                notes TEXT NOT NULL,
                journalEntryId INTEGER,
                costJournalEntryId INTEGER,
                isLegacyArchive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_sales_summaries_businessEpochDay ON daily_sales_summaries(businessEpochDay)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS daily_sales_menu_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                summaryId INTEGER NOT NULL,
                menuItemId INTEGER,
                menuItemNameSnapshot TEXT NOT NULL,
                quantityMicros INTEGER NOT NULL,
                grossSalesRial INTEGER NOT NULL,
                theoreticalCostRial INTEGER NOT NULL,
                FOREIGN KEY(summaryId) REFERENCES daily_sales_summaries(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_menu_lines_summaryId ON daily_sales_menu_lines(summaryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_menu_lines_menuItemId ON daily_sales_menu_lines(menuItemId)")
        db.execSQL(
            """INSERT OR IGNORE INTO daily_sales_summaries(
                businessEpochDay,grossSalesRial,discountRial,serviceRial,taxRial,netSalesRial,theoreticalCostRial,
                cashRial,cardRial,transferRial,notes,journalEntryId,costJournalEntryId,isLegacyArchive,createdAtEpochMillis
            )
            SELECT s.saleEpochDay,
                   SUM(s.subtotalRial), SUM(s.discountRial), SUM(s.deliveryRial), 0, SUM(s.totalRial),
                   COALESCE(SUM((SELECT COALESCE(SUM(sl.costOfGoodsRial),0) FROM sale_lines sl WHERE sl.saleId=s.id)),0),
                   SUM(CASE WHEN s.paymentMethod='نقدی' THEN s.paidRial ELSE 0 END),
                   SUM(CASE WHEN s.paymentMethod IN ('کارتخوان','ترکیبی') OR s.paymentMethod IS NULL THEN s.paidRial ELSE 0 END),
                   SUM(CASE WHEN s.paymentMethod='حواله' THEN s.paidRial ELSE 0 END),
                   'آرشیو تبدیل‌شده از فروش فاکتوری نسخه‌های قبل', NULL, NULL, 1, MIN(s.createdAtEpochMillis)
            FROM sales s WHERE s.reversedAtEpochDay IS NULL GROUP BY s.saleEpochDay""",
        )
        db.execSQL(
            """INSERT INTO daily_sales_menu_lines(summaryId,menuItemId,menuItemNameSnapshot,quantityMicros,grossSalesRial,theoreticalCostRial)
            SELECT ds.id, sl.menuItemId, sl.productNameSnapshot, SUM(sl.quantityMicros), SUM(sl.lineTotalRial), SUM(sl.costOfGoodsRial)
            FROM sale_lines sl
            INNER JOIN sales s ON s.id=sl.saleId AND s.reversedAtEpochDay IS NULL
            INNER JOIN daily_sales_summaries ds ON ds.businessEpochDay=s.saleEpochDay
            GROUP BY ds.id, sl.menuItemId, sl.productNameSnapshot""",
        )
        db.execSQL("UPDATE journal_entries SET description='فروش آرشیوی نسخه‌های قبل', sourceType='LEGACY_SALE' WHERE sourceType='SALE'")
        db.execSQL("UPDATE journal_entries SET description='بهای تمام‌شده فروش آرشیوی', sourceType='LEGACY_SALE_COGS' WHERE sourceType='SALE_COGS'")
        db.execSQL("DELETE FROM app_alerts WHERE sourceType IN ('CRM_FOLLOW_UP','SALE_RECEIVABLE')")
        db.execSQL("DELETE FROM sync_changes WHERE entityType IN ('CUSTOMER','CRM_FOLLOW_UP','LOYALTY_TRANSACTION','SALE','TABLE','RESERVATION','KITCHEN_TICKET','TABLE_ORDER','BILL_SPLIT','CASH_SHIFT')")
        // Audit rows deliberately outlive the retired operational tables. They have no foreign keys
        // and remain the evidence trail for legacy customer/table/order activity.
        listOf(
            "bill_split_lines",
            "bill_splits",
            "table_order_lines",
            "table_orders",
            "cash_shifts",
            "kitchen_tickets",
            "reservations",
            "restaurant_tables",
            "approval_requests",
            "customer_followups",
            "loyalty_transactions",
            "sale_payments",
            "sale_lines",
            "sales",
            "customers",
        ).forEach { table -> db.execSQL("DROP TABLE IF EXISTS $table") }
    }
}
internal val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_sales_summaries ADD COLUMN reversedAtEpochDay INTEGER")
        db.execSQL("ALTER TABLE daily_sales_summaries ADD COLUMN reversalReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE daily_sales_summaries ADD COLUMN reversalJournalEntryId INTEGER")
        db.execSQL("ALTER TABLE daily_sales_summaries ADD COLUMN reversalCostJournalEntryId INTEGER")
        db.execSQL("DROP INDEX IF EXISTS index_daily_sales_summaries_businessEpochDay")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_summaries_businessEpochDay ON daily_sales_summaries(businessEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_summaries_reversedAtEpochDay ON daily_sales_summaries(reversedAtEpochDay)")
    }
}

internal val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN advanceDeductionRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS payroll_advance_allocations (
                payrollId INTEGER NOT NULL,
                advanceId INTEGER NOT NULL,
                amountRial INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(payrollId, advanceId),
                FOREIGN KEY(payrollId) REFERENCES payroll_runs(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(advanceId) REFERENCES employee_advances(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_advance_allocations_advanceId ON payroll_advance_allocations(advanceId)")
    }
}

internal val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payroll_policies (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            effectiveFromEpochDay INTEGER NOT NULL,
            effectiveToEpochDay INTEGER,
            overtimeHourlyRateRial INTEGER NOT NULL,
            absenceDailyDeductionRial INTEGER NOT NULL,
            lateMinuteDeductionRial INTEGER NOT NULL,
            createdBy TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_policies_effectiveFromEpochDay ON payroll_policies(effectiveFromEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_policies_effectiveToEpochDay ON payroll_policies(effectiveToEpochDay)")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN periodStartEpochDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN periodEndEpochDay INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN payrollPolicyId INTEGER")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN automaticOvertimeRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN attendanceDeductionRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_payrollPolicyId ON payroll_runs(payrollPolicyId)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS inventory_period_closures (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            fromEpochDay INTEGER NOT NULL,
            toEpochDay INTEGER NOT NULL,
            openingValueRial INTEGER NOT NULL,
            netPurchaseValueRial INTEGER NOT NULL,
            recordedOutflowValueRial INTEGER NOT NULL,
            expectedClosingValueRial INTEGER NOT NULL,
            countedClosingValueRial INTEGER NOT NULL,
            varianceValueRial INTEGER NOT NULL,
            itemCount INTEGER NOT NULL,
            status TEXT NOT NULL,
            closedBy TEXT NOT NULL,
            note TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_period_closures_fromEpochDay_toEpochDay ON inventory_period_closures(fromEpochDay,toEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_period_closures_toEpochDay ON inventory_period_closures(toEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS inventory_period_closure_lines (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            closureId INTEGER NOT NULL,
            itemId INTEGER NOT NULL,
            itemNameSnapshot TEXT NOT NULL,
            unitSnapshot TEXT NOT NULL,
            openingQuantityMicros INTEGER NOT NULL,
            openingValueRial INTEGER NOT NULL,
            netPurchaseQuantityMicros INTEGER NOT NULL,
            netPurchaseValueRial INTEGER NOT NULL,
            recordedOutflowQuantityMicros INTEGER NOT NULL,
            recordedOutflowValueRial INTEGER NOT NULL,
            adjustmentQuantityMicros INTEGER NOT NULL,
            adjustmentValueRial INTEGER NOT NULL,
            expectedClosingQuantityMicros INTEGER NOT NULL,
            expectedClosingValueRial INTEGER NOT NULL,
            countedClosingQuantityMicros INTEGER NOT NULL,
            countedClosingValueRial INTEGER NOT NULL,
            FOREIGN KEY(closureId) REFERENCES inventory_period_closures(id) ON UPDATE NO ACTION ON DELETE CASCADE
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_period_closure_lines_closureId ON inventory_period_closure_lines(closureId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_period_closure_lines_itemId ON inventory_period_closure_lines(itemId)")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_insert
            BEFORE INSERT ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND NEW.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay)
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_update
            BEFORE UPDATE ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND (OLD.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay OR NEW.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay))
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS prevent_closed_inventory_movement_delete
            BEFORE DELETE ON stock_movements
            WHEN EXISTS(SELECT 1 FROM inventory_period_closures c WHERE c.status='CLOSED' AND OLD.movementEpochDay BETWEEN c.fromEpochDay AND c.toEpochDay)
            BEGIN SELECT RAISE(ABORT, 'INVENTORY_PERIOD_CLOSED'); END""")
    }
}

internal val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN revisionNo INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN reversalEpochDay INTEGER")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN reversalReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN reversalJournalEntryId INTEGER")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN reversedBy TEXT")
        db.execSQL("DROP INDEX IF EXISTS index_payroll_runs_employeeId_periodYear_periodMonth")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_runs_employeeId_periodYear_periodMonth_revisionNo ON payroll_runs(employeeId,periodYear,periodMonth,revisionNo)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sales_day_closures (
            businessEpochDay INTEGER NOT NULL,
            summaryId INTEGER NOT NULL,
            grossSalesRial INTEGER NOT NULL,
            netSalesRial INTEGER NOT NULL,
            theoreticalCostRial INTEGER NOT NULL,
            cashRial INTEGER NOT NULL,
            cardRial INTEGER NOT NULL,
            transferRial INTEGER NOT NULL,
            closedBy TEXT NOT NULL,
            note TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            PRIMARY KEY(businessEpochDay),
            FOREIGN KEY(summaryId) REFERENCES daily_sales_summaries(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_day_closures_summaryId ON sales_day_closures(summaryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_day_closures_createdAtEpochMillis ON sales_day_closures(createdAtEpochMillis)")
        installSalesDayGuards(db, hasClosureStatusColumn = false)
    }
}

internal val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_period_closures ADD COLUMN revisionNo INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE inventory_period_closures ADD COLUMN reopenedBy TEXT")
        db.execSQL("ALTER TABLE inventory_period_closures ADD COLUMN reopenReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_period_closures ADD COLUMN reopenedAtEpochMillis INTEGER")

        db.execSQL("ALTER TABLE sales_day_closures ADD COLUMN status TEXT NOT NULL DEFAULT 'CLOSED'")
        db.execSQL("ALTER TABLE sales_day_closures ADD COLUMN revisionNo INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE sales_day_closures ADD COLUMN reopenedBy TEXT")
        db.execSQL("ALTER TABLE sales_day_closures ADD COLUMN reopenReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sales_day_closures ADD COLUMN reopenedAtEpochMillis INTEGER")

        db.execSQL("ALTER TABLE sync_changes ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE sync_changes SET idempotencyKey = changeId")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_changes_idempotencyKey ON sync_changes(idempotencyKey)")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN attemptCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN lastAttemptAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN nextAttemptAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN deadLetteredAtEpochMillis INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_changes_nextAttemptAtEpochMillis ON sync_changes(nextAttemptAtEpochMillis)")

        listOf(
            "prevent_closed_sales_day_insert", "prevent_closed_sales_day_update", "prevent_closed_sales_day_delete",
            "prevent_closed_sales_line_insert", "prevent_closed_sales_line_update", "prevent_closed_sales_line_delete",
            "prevent_closed_sales_stock_insert", "prevent_closed_sales_stock_update", "prevent_closed_sales_stock_delete",
        ).forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
        installSalesDayGuards(db)
    }
}

internal val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN requiredApprovalLevel INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN completedApprovalLevel INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN firstApprovedBy TEXT")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN secondApprovedBy TEXT")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN committedBudgetId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN committedBudgetRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN approvedBy TEXT")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN approvedAtEpochMillis INTEGER")

        db.execSQL("""CREATE TABLE IF NOT EXISTS budget_commitments(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,budgetId INTEGER NOT NULL,referenceType TEXT NOT NULL,referenceId INTEGER NOT NULL,amountRial INTEGER NOT NULL,status TEXT NOT NULL,actor TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL,updatedAtEpochMillis INTEGER NOT NULL,FOREIGN KEY(budgetId) REFERENCES operating_budgets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_commitments_budgetId ON budget_commitments(budgetId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_budget_commitments_referenceType_referenceId ON budget_commitments(referenceType,referenceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_commitments_status ON budget_commitments(status)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS accounting_period_locks(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,fromEpochDay INTEGER NOT NULL,toEpochDay INTEGER NOT NULL,status TEXT NOT NULL,reason TEXT NOT NULL,closedBy TEXT NOT NULL,closedAtEpochMillis INTEGER NOT NULL,reopenedBy TEXT,reopenedAtEpochMillis INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounting_period_locks_fromEpochDay_toEpochDay ON accounting_period_locks(fromEpochDay,toEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounting_period_locks_status ON accounting_period_locks(status)")

        db.execSQL("""CREATE TABLE IF NOT EXISTS sales_cash_reconciliations(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,businessEpochDay INTEGER NOT NULL,revisionNo INTEGER NOT NULL,expectedCashRial INTEGER NOT NULL,expectedCardRial INTEGER NOT NULL,expectedTransferRial INTEGER NOT NULL,actualCashRial INTEGER NOT NULL,actualCardRial INTEGER NOT NULL,actualTransferRial INTEGER NOT NULL,status TEXT NOT NULL,note TEXT NOT NULL,reconciledBy TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_cash_reconciliations_businessEpochDay_revisionNo ON sales_cash_reconciliations(businessEpochDay,revisionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_cash_reconciliations_status ON sales_cash_reconciliations(status)")
        installAccountingPeriodGuards(db)
    }
}

internal val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        installJournalLineGuards(db)
    }
}


internal val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS recipe_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                menuItemId INTEGER NOT NULL,
                revisionNo INTEGER NOT NULL,
                effectiveFromEpochDay INTEGER NOT NULL,
                yieldMicros INTEGER NOT NULL DEFAULT 1000000,
                portionWeightMicros INTEGER NOT NULL DEFAULT 0,
                preparationWasteBasisPoints INTEGER NOT NULL DEFAULT 0,
                cookingWasteBasisPoints INTEGER NOT NULL DEFAULT 0,
                packagingCostRial INTEGER NOT NULL DEFAULT 0,
                directLaborCostRial INTEGER NOT NULL DEFAULT 0,
                allocatedOverheadRial INTEGER NOT NULL DEFAULT 0,
                note TEXT NOT NULL DEFAULT '',
                createdBy TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(menuItemId) REFERENCES menu_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recipe_versions_menuItemId_revisionNo ON recipe_versions(menuItemId, revisionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_versions_menuItemId_effectiveFromEpochDay ON recipe_versions(menuItemId, effectiveFromEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_versions_createdAtEpochMillis ON recipe_versions(createdAtEpochMillis)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS recipe_version_ingredients (
                recipeVersionId INTEGER NOT NULL,
                inventoryItemId INTEGER NOT NULL,
                quantityMicrosPerUnit INTEGER NOT NULL,
                PRIMARY KEY(recipeVersionId, inventoryItemId),
                FOREIGN KEY(recipeVersionId) REFERENCES recipe_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(inventoryItemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_version_ingredients_inventoryItemId ON recipe_version_ingredients(inventoryItemId)")

        // Existing formulas become immutable revision 1. Epoch day 1 means effective for all historical dates.
        db.execSQL(
            """INSERT INTO recipe_versions(
                menuItemId, revisionNo, effectiveFromEpochDay, yieldMicros, portionWeightMicros,
                preparationWasteBasisPoints, cookingWasteBasisPoints, packagingCostRial,
                directLaborCostRial, allocatedOverheadRial, note, createdBy, createdAtEpochMillis
            )
            SELECT id, 1, 1, 1000000, 0, 0, 0, 0, 0, 0,
                   'نسخه اولیه تبدیل‌شده از رسپی قبلی', 'MIGRATION_38_39', createdAtEpochMillis
            FROM menu_items""",
        )
        db.execSQL(
            """INSERT INTO recipe_version_ingredients(recipeVersionId, inventoryItemId, quantityMicrosPerUnit)
            SELECT rv.id, ri.inventoryItemId, ri.quantityMicrosPerUnit
            FROM recipe_ingredients ri
            INNER JOIN recipe_versions rv ON rv.menuItemId = ri.menuItemId AND rv.revisionNo = 1""",
        )

        db.execSQL("ALTER TABLE daily_sales_menu_lines ADD COLUMN recipeVersionId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_menu_lines_recipeVersionId ON daily_sales_menu_lines(recipeVersionId)")
        db.execSQL(
            """UPDATE daily_sales_menu_lines
            SET recipeVersionId = (
                SELECT rv.id FROM recipe_versions rv
                WHERE rv.menuItemId = daily_sales_menu_lines.menuItemId AND rv.revisionNo = 1
                LIMIT 1
            )
            WHERE menuItemId IS NOT NULL""",
        )

        installRecipeVersionGuards(db)
    }
}

/** Nullable snapshots intentionally preserve the distinction between historical and current sales. */
internal val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_sales_menu_lines ADD COLUMN foodCostSnapshotRial INTEGER")
        db.execSQL("ALTER TABLE daily_sales_menu_lines ADD COLUMN packagingCostSnapshotRial INTEGER")
        db.execSQL("ALTER TABLE daily_sales_menu_lines ADD COLUMN directLaborCostSnapshotRial INTEGER")
        db.execSQL("ALTER TABLE daily_sales_menu_lines ADD COLUMN allocatedOverheadSnapshotRial INTEGER")
    }
}

internal val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_order_follow_ups (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseOrderId INTEGER NOT NULL, note TEXT NOT NULL, actor TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(purchaseOrderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_follow_ups_purchaseOrderId ON purchase_order_follow_ups(purchaseOrderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_follow_ups_createdAtEpochMillis ON purchase_order_follow_ups(createdAtEpochMillis)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS storage_locations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, isActive INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_storage_locations_name ON storage_locations(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_storage_locations_isActive ON storage_locations(isActive)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS inventory_lots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL, locationId INTEGER NOT NULL, lotCode TEXT NOT NULL, receivedEpochDay INTEGER NOT NULL, expiryEpochDay INTEGER, quantityMicros INTEGER NOT NULL, unitCostRial INTEGER NOT NULL, barcode TEXT, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_lots_itemId_locationId_lotCode ON inventory_lots(itemId, locationId, lotCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_barcode ON inventory_lots(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_locationId ON inventory_lots(locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_expiryEpochDay ON inventory_lots(expiryEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS stock_transfers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transferNo TEXT NOT NULL, sourceLocationId INTEGER NOT NULL, destinationLocationId INTEGER NOT NULL, transferEpochDay INTEGER NOT NULL, note TEXT NOT NULL, transferredBy TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(sourceLocationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(destinationLocationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_transfers_transferNo ON stock_transfers(transferNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfers_sourceLocationId ON stock_transfers(sourceLocationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfers_destinationLocationId ON stock_transfers(destinationLocationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfers_transferEpochDay ON stock_transfers(transferEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS stock_transfer_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, transferId INTEGER NOT NULL, itemId INTEGER NOT NULL, lotCode TEXT NOT NULL, quantityMicros INTEGER NOT NULL, FOREIGN KEY(transferId) REFERENCES stock_transfers(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfer_lines_transferId ON stock_transfer_lines(transferId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfer_lines_itemId ON stock_transfer_lines(itemId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS operating_budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, costCenter TEXT NOT NULL, fromEpochDay INTEGER NOT NULL, toEpochDay INTEGER NOT NULL, limitRial INTEGER NOT NULL, createdBy TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_operating_budgets_name_fromEpochDay_toEpochDay ON operating_budgets(name, fromEpochDay, toEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_operating_budgets_category ON operating_budgets(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_operating_budgets_costCenter ON operating_budgets(costCenter)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS budget_spend_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, budgetId INTEGER NOT NULL, amountRial INTEGER NOT NULL, spendEpochDay INTEGER NOT NULL, reference TEXT NOT NULL, actor TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(budgetId) REFERENCES operating_budgets(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_spend_entries_budgetId ON budget_spend_entries(budgetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_spend_entries_spendEpochDay ON budget_spend_entries(spendEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS labor_policy (singletonId INTEGER NOT NULL, maxWeeklyMinutes INTEGER NOT NULL, maxShiftMinutes INTEGER NOT NULL, minimumRestMinutes INTEGER NOT NULL, breakRequiredAfterMinutes INTEGER NOT NULL, minimumBreakMinutes INTEGER NOT NULL, updatedBy TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(singletonId))""")
        db.execSQL("""INSERT OR IGNORE INTO labor_policy(singletonId,maxWeeklyMinutes,maxShiftMinutes,minimumRestMinutes,breakRequiredAfterMinutes,minimumBreakMinutes,updatedBy,updatedAtEpochMillis) VALUES(1,2640,720,660,360,30,'SYSTEM',0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS work_breaks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, recordedBy TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(shiftId) REFERENCES planned_shifts(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_breaks_shiftId ON work_breaks(shiftId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS employee_availability (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, employeeId INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, fromMinute INTEGER NOT NULL, toMinute INTEGER NOT NULL, isAvailable INTEGER NOT NULL, updatedBy TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_employee_availability_employeeId_dayOfWeek ON employee_availability(employeeId, dayOfWeek)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_availability_dayOfWeek ON employee_availability(dayOfWeek)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS shift_swap_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, shiftId INTEGER NOT NULL, requesterEmployeeId INTEGER NOT NULL, targetEmployeeId INTEGER, status TEXT NOT NULL, note TEXT NOT NULL, reviewedBy TEXT, createdAtEpochMillis INTEGER NOT NULL, reviewedAtEpochMillis INTEGER, FOREIGN KEY(shiftId) REFERENCES planned_shifts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(requesterEmployeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_swap_requests_shiftId ON shift_swap_requests(shiftId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_swap_requests_requesterEmployeeId ON shift_swap_requests(requesterEmployeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_swap_requests_targetEmployeeId ON shift_swap_requests(targetEmployeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_shift_swap_requests_status ON shift_swap_requests(status)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS inventory_lot_consumptions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, stockMovementId INTEGER NOT NULL, lotId INTEGER NOT NULL, quantityMicros INTEGER NOT NULL, reversedQuantityMicros INTEGER NOT NULL, FOREIGN KEY(stockMovementId) REFERENCES stock_movements(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(lotId) REFERENCES inventory_lots(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lot_consumptions_stockMovementId ON inventory_lot_consumptions(stockMovementId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lot_consumptions_lotId ON inventory_lot_consumptions(lotId)")
    }
}

internal val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN sentAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN sentBy TEXT")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN dispatchChannel TEXT")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN acknowledgedAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN supplierConfirmationNo TEXT")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN confirmedExpectedEpochDay INTEGER")
    }
}

internal val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_requisition_lines ADD COLUMN recommendedSupplierId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisition_lines ADD COLUMN supplierSkuSnapshot TEXT")
        db.execSQL("ALTER TABLE purchase_requisition_lines ADD COLUMN recommendedLeadTimeDays INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisition_lines_recommendedSupplierId ON purchase_requisition_lines(recommendedSupplierId)")
        db.execSQL("ALTER TABLE purchase_order_lines ADD COLUMN supplierSkuSnapshot TEXT")
    }
}

internal val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_item_offers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, supplierId INTEGER NOT NULL, itemId INTEGER NOT NULL, supplierSku TEXT NOT NULL, unitCostRial INTEGER NOT NULL, minimumOrderMicros INTEGER NOT NULL, orderMultipleMicros INTEGER NOT NULL, leadTimeDays INTEGER NOT NULL, validUntilEpochDay INTEGER, isActive INTEGER NOT NULL, updatedBy TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_item_offers_supplierId_itemId ON supplier_item_offers(supplierId, itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_item_offers_itemId ON supplier_item_offers(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_item_offers_validUntilEpochDay ON supplier_item_offers(validUntilEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_item_offers_isActive ON supplier_item_offers(isActive)")
    }
}

internal val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS inventory_replenishment_policies (itemId INTEGER NOT NULL, preferredSupplierId INTEGER, targetCoverDays INTEGER NOT NULL, leadTimeDays INTEGER NOT NULL, safetyStockMicros INTEGER NOT NULL, orderMultipleMicros INTEGER NOT NULL, isEnabled INTEGER NOT NULL, updatedBy TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(preferredSupplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE SET NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_replenishment_policies_preferredSupplierId ON inventory_replenishment_policies(preferredSupplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_replenishment_policies_isEnabled ON inventory_replenishment_policies(isEnabled)")
    }
}

internal val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_order_lines ADD COLUMN returnedQtyMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_returns (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, returnNo TEXT NOT NULL, purchaseOrderId INTEGER NOT NULL, purchaseId INTEGER, supplierId INTEGER NOT NULL, returnEpochDay INTEGER NOT NULL, reason TEXT NOT NULL, returnedBy TEXT NOT NULL, inventoryValueRial INTEGER NOT NULL, supplierCreditValueRial INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(purchaseOrderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(purchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_returns_returnNo ON purchase_returns(returnNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_purchaseOrderId ON purchase_returns(purchaseOrderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_purchaseId ON purchase_returns(purchaseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_supplierId ON purchase_returns(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_returnEpochDay ON purchase_returns(returnEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_return_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseReturnId INTEGER NOT NULL, purchaseOrderLineId INTEGER NOT NULL, itemId INTEGER NOT NULL, quantityMicros INTEGER NOT NULL, inventoryUnitCostRial INTEGER NOT NULL, supplierUnitCreditRial INTEGER NOT NULL, inventoryValueRial INTEGER NOT NULL, supplierCreditValueRial INTEGER NOT NULL, reason TEXT NOT NULL, FOREIGN KEY(purchaseReturnId) REFERENCES purchase_returns(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(purchaseOrderLineId) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_purchaseReturnId ON purchase_return_lines(purchaseReturnId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_purchaseOrderLineId ON purchase_return_lines(purchaseOrderLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_itemId ON purchase_return_lines(itemId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS supplier_credits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, creditNo TEXT NOT NULL, supplierId INTEGER NOT NULL, sourceReturnId INTEGER NOT NULL, appliedPurchaseId INTEGER, amountRial INTEGER NOT NULL, appliedRial INTEGER NOT NULL, status TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(sourceReturnId) REFERENCES purchase_returns(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(appliedPurchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_credits_creditNo ON supplier_credits(creditNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_credits_supplierId ON supplier_credits(supplierId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_credits_sourceReturnId ON supplier_credits(sourceReturnId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_credits_appliedPurchaseId ON supplier_credits(appliedPurchaseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_credits_status ON supplier_credits(status)")
    }
}

internal val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_requisitions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requestNo TEXT NOT NULL, department TEXT NOT NULL, requiredEpochDay INTEGER NOT NULL, status TEXT NOT NULL, requestedBy TEXT NOT NULL, approvedBy TEXT, note TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_requisitions_requestNo ON purchase_requisitions(requestNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_status ON purchase_requisitions(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_requiredEpochDay ON purchase_requisitions(requiredEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_requisition_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requisitionId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, requestedQtyMicros INTEGER NOT NULL, estimatedUnitCostRial INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(requisitionId) REFERENCES purchase_requisitions(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisition_lines_requisitionId ON purchase_requisition_lines(requisitionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisition_lines_itemId ON purchase_requisition_lines(itemId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, orderNo TEXT NOT NULL, supplierId INTEGER NOT NULL, supplierNameSnapshot TEXT NOT NULL, requisitionId INTEGER NOT NULL, orderEpochDay INTEGER NOT NULL, expectedEpochDay INTEGER NOT NULL, status TEXT NOT NULL, note TEXT NOT NULL, createdBy TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(requisitionId) REFERENCES purchase_requisitions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_orders_orderNo ON purchase_orders(orderNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_supplierId ON purchase_orders(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_requisitionId ON purchase_orders(requisitionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_status ON purchase_orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_expectedEpochDay ON purchase_orders(expectedEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseOrderId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, orderedQtyMicros INTEGER NOT NULL, unitCostRial INTEGER NOT NULL, receivedQtyMicros INTEGER NOT NULL, rejectedQtyMicros INTEGER NOT NULL, FOREIGN KEY(purchaseOrderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_purchaseOrderId ON purchase_order_lines(purchaseOrderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_order_lines_itemId ON purchase_order_lines(itemId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS goods_receipts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, receiptNo TEXT NOT NULL, purchaseOrderId INTEGER NOT NULL, receiptEpochDay INTEGER NOT NULL, deliveryNoteNo TEXT NOT NULL, receivedBy TEXT NOT NULL, note TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(purchaseOrderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goods_receipts_receiptNo ON goods_receipts(receiptNo)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goods_receipts_purchaseOrderId_deliveryNoteNo ON goods_receipts(purchaseOrderId, deliveryNoteNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_purchaseOrderId ON goods_receipts(purchaseOrderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_receiptEpochDay ON goods_receipts(receiptEpochDay)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS goods_receipt_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, goodsReceiptId INTEGER NOT NULL, purchaseOrderLineId INTEGER NOT NULL, itemId INTEGER NOT NULL, deliveredQtyMicros INTEGER NOT NULL, acceptedQtyMicros INTEGER NOT NULL, rejectedQtyMicros INTEGER NOT NULL, rejectionReason TEXT NOT NULL, acceptedValueRial INTEGER NOT NULL, FOREIGN KEY(goodsReceiptId) REFERENCES goods_receipts(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(purchaseOrderLineId) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_goodsReceiptId ON goods_receipt_lines(goodsReceiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipt_lines_purchaseOrderLineId ON goods_receipt_lines(purchaseOrderLineId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS procurement_invoice_links (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseOrderId INTEGER NOT NULL, purchaseId INTEGER NOT NULL, matchStatus TEXT NOT NULL, acceptedValueRial INTEGER NOT NULL, invoiceValueRial INTEGER NOT NULL, priceVarianceRial INTEGER NOT NULL, varianceApprovedBy TEXT, matchedAtEpochMillis INTEGER NOT NULL, FOREIGN KEY(purchaseOrderId) REFERENCES purchase_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(purchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_procurement_invoice_links_purchaseOrderId ON procurement_invoice_links(purchaseOrderId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_procurement_invoice_links_purchaseId ON procurement_invoice_links(purchaseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_invoice_links_matchStatus ON procurement_invoice_links(matchStatus)")
    }
}

internal val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE kitchen_tickets ADD COLUMN orderLineId INTEGER")
        db.execSQL("ALTER TABLE table_order_lines ADD COLUMN voidReason TEXT")
        db.execSQL("ALTER TABLE table_order_lines ADD COLUMN voidedBy TEXT")
        db.execSQL("ALTER TABLE table_order_lines ADD COLUMN voidedAtEpochMillis INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_splits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                label TEXT NOT NULL,
                mode TEXT NOT NULL,
                status TEXT NOT NULL,
                saleId INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                settledAtEpochMillis INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_splits_orderId ON bill_splits(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_splits_status ON bill_splits(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_splits_saleId ON bill_splits(saleId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bill_split_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                splitId INTEGER NOT NULL,
                orderLineId INTEGER NOT NULL,
                FOREIGN KEY(splitId) REFERENCES bill_splits(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(orderLineId) REFERENCES table_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_split_lines_splitId ON bill_split_lines(splitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bill_split_lines_orderLineId ON bill_split_lines(orderLineId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cash_shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                openedAtEpochMillis INTEGER NOT NULL,
                closedAtEpochMillis INTEGER,
                openingFloatRial INTEGER NOT NULL,
                expectedCashRial INTEGER NOT NULL,
                countedCashRial INTEGER,
                varianceRial INTEGER,
                openedBy TEXT NOT NULL,
                closedBy TEXT,
                status TEXT NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shifts_status ON cash_shifts(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_shifts_openedAtEpochMillis ON cash_shifts(openedAtEpochMillis)")
    }
}

internal val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS table_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderNo TEXT NOT NULL,
                tableId INTEGER NOT NULL,
                guestCount INTEGER NOT NULL,
                waiterName TEXT NOT NULL,
                customerId INTEGER,
                customerName TEXT NOT NULL,
                status TEXT NOT NULL,
                openedAtEpochMillis INTEGER NOT NULL,
                closedAtEpochMillis INTEGER,
                mergedIntoOrderId INTEGER,
                saleId INTEGER,
                notes TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_table_orders_orderNo ON table_orders(orderNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_orders_tableId ON table_orders(tableId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_orders_status ON table_orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_orders_openedAtEpochMillis ON table_orders(openedAtEpochMillis)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS table_order_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                menuItemId INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                quantityMicros INTEGER NOT NULL,
                unitPriceRial INTEGER NOT NULL,
                seatNo INTEGER NOT NULL,
                courseNo INTEGER NOT NULL,
                notes TEXT NOT NULL,
                status TEXT NOT NULL,
                sentToKitchenAtEpochMillis INTEGER,
                FOREIGN KEY(orderId) REFERENCES table_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_order_lines_orderId ON table_order_lines(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_order_lines_menuItemId ON table_order_lines(menuItemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_table_order_lines_status ON table_order_lines(status)")
    }
}

internal val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
    }
}

internal val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE employees ADD COLUMN fatherName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE app_users ADD COLUMN recoveryCodeHash TEXT NOT NULL DEFAULT ''")
    }
}

internal val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS restaurant_tables (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,label TEXT NOT NULL,capacity INTEGER NOT NULL,zone TEXT NOT NULL,status TEXT NOT NULL,activeOrderId INTEGER)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_restaurant_tables_label ON restaurant_tables(label)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reservations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,customerName TEXT NOT NULL,phone TEXT NOT NULL,tableId INTEGER NOT NULL,startEpochMillis INTEGER NOT NULL,endEpochMillis INTEGER NOT NULL,partySize INTEGER NOT NULL,notes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reservations_tableId ON reservations(tableId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reservations_startEpochMillis ON reservations(startEpochMillis)")
        db.execSQL("CREATE TABLE IF NOT EXISTS kitchen_tickets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,orderNo TEXT NOT NULL,station TEXT NOT NULL,itemName TEXT NOT NULL,quantity INTEGER NOT NULL,createdAtEpochMillis INTEGER NOT NULL,status TEXT NOT NULL,priority INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_kitchen_tickets_status ON kitchen_tickets(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_kitchen_tickets_createdAtEpochMillis ON kitchen_tickets(createdAtEpochMillis)")
        db.execSQL("CREATE TABLE IF NOT EXISTS planned_shifts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeId INTEGER NOT NULL,employeeName TEXT NOT NULL,role TEXT NOT NULL,epochDay INTEGER NOT NULL,startMinute INTEGER NOT NULL,endMinute INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_employeeId ON planned_shifts(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_shifts_epochDay ON planned_shifts(epochDay)")
        db.execSQL("CREATE TABLE IF NOT EXISTS approval_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,requestType TEXT NOT NULL,entityId INTEGER NOT NULL,amountRial INTEGER NOT NULL,requestedBy TEXT NOT NULL,approver TEXT,status TEXT NOT NULL,note TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_status ON approval_requests(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_requestType_entityId ON approval_requests(requestType,entityId)")
    }
}

internal val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN payloadVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE sync_changes ADD COLUMN payload TEXT NOT NULL DEFAULT 'legacy'")
        db.execSQL("UPDATE sync_changes SET payloadHash = '${"0".repeat(64)}', state = 'REJECTED', lastError = 'Legacy payload requires regeneration' WHERE state IN ('PENDING', 'LOCAL_ONLY')")
    }
}
