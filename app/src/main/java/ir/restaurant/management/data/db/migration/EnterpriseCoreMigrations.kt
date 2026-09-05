package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Enterprise-core migration. v45 remains the immutable version-45 baseline. */
internal val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing masters/lifecycles are extended non-destructively.
        db.execSQL("ALTER TABLE customers ADD COLUMN mobile TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE customers ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE customers ADD COLUMN branch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE customers ADD COLUMN paymentTermsDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")

        db.execSQL("ALTER TABLE recipe_versions ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE recipe_versions ADD COLUMN parentVersionId INTEGER")

        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN branch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN responsiblePerson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN impairmentRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN disposedEpochDay INTEGER")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN salePriceRial INTEGER")

        db.execSQL("ALTER TABLE app_alerts ADD COLUMN status TEXT NOT NULL DEFAULT 'NEW'")
        db.execSQL("INSERT OR IGNORE INTO accounts(code,name,type,isSystem,isActive) VALUES ('1503','کاهش ارزش انباشته دارایی‌ها','ASSET',1,1)")
        db.execSQL("UPDATE app_alerts SET status=CASE WHEN isDismissed=1 THEN 'DISMISSED' WHEN isRead=1 THEN 'READ' ELSE 'NEW' END")

        createTreasuryTables(db)
        createRemovedDiningWorkflowCompatibilitySchema(db)
        createRecipeLifecycleTables(db)
        createAssetLifecycleTables(db)
        createCrmLedgerTables(db)
        createEnterpriseCoreIndexes(db)
    }
}

private fun createTreasuryTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS treasury_transactions (
            id TEXT NOT NULL,
            commandId TEXT NOT NULL,
            kind TEXT NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            sourceType TEXT NOT NULL,
            sourceId INTEGER NOT NULL,
            counterpartyType TEXT NOT NULL DEFAULT '',
            counterpartyId INTEGER,
            reference TEXT NOT NULL DEFAULT '',
            reason TEXT NOT NULL,
            amountRial INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'POSTED',
            journalEntryId INTEGER,
            reversalOfTransactionId TEXT,
            actorId INTEGER NOT NULL,
            correlationId TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            reversedAtEpochMillis INTEGER,
            PRIMARY KEY(id)
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS treasury_ledger_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            transactionId TEXT NOT NULL,
            accountId TEXT NOT NULL,
            direction TEXT NOT NULL,
            amountRial INTEGER NOT NULL,
            sourceType TEXT NOT NULL,
            sourceId INTEGER NOT NULL,
            counterpartyType TEXT NOT NULL DEFAULT '',
            counterpartyId INTEGER,
            reference TEXT NOT NULL DEFAULT '',
            businessEpochDay INTEGER NOT NULL,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(transactionId) REFERENCES treasury_transactions(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS treasury_reconciliations (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            transactionId TEXT NOT NULL,
            accountId TEXT NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            expectedRial INTEGER NOT NULL,
            actualRial INTEGER NOT NULL,
            differenceRial INTEGER NOT NULL,
            reason TEXT NOT NULL,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL
        )""",
    )
}

private fun createRemovedDiningWorkflowCompatibilitySchema(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_halls (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            isActive INTEGER NOT NULL DEFAULT 1,
            createdAtEpochMillis INTEGER NOT NULL
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_tables (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            hallId INTEGER NOT NULL,
            tableNo TEXT NOT NULL,
            capacity INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'AVAILABLE',
            currentOrderId INTEGER,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(hallId) REFERENCES restaurant_halls(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_reservations (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            customerId INTEGER,
            guestName TEXT NOT NULL,
            guestPhone TEXT NOT NULL,
            reservationEpochDay INTEGER NOT NULL,
            startMinuteOfDay INTEGER NOT NULL,
            guestCount INTEGER NOT NULL,
            tableId INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'BOOKED',
            note TEXT NOT NULL DEFAULT '',
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(tableId) REFERENCES restaurant_tables(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_orders (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            commandId TEXT NOT NULL,
            tableId INTEGER NOT NULL,
            waiterUserId INTEGER,
            shiftReference TEXT NOT NULL DEFAULT '',
            customerId INTEGER,
            guestCount INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'OPEN',
            openedEpochDay INTEGER NOT NULL,
            openedAtEpochMillis INTEGER NOT NULL,
            closedAtEpochMillis INTEGER,
            salesInvoiceId INTEGER,
            note TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(tableId) REFERENCES restaurant_tables(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(waiterUserId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_order_lines (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            orderId INTEGER NOT NULL,
            menuItemId INTEGER NOT NULL,
            menuItemNameSnapshot TEXT NOT NULL,
            course TEXT NOT NULL,
            quantityMicros INTEGER NOT NULL,
            unitPriceRial INTEGER NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'ACTIVE',
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(orderId) REFERENCES restaurant_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(menuItemId) REFERENCES menu_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS kitchen_tickets (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            orderLineId INTEGER NOT NULL,
            status TEXT NOT NULL DEFAULT 'NEW',
            acceptedAtEpochMillis INTEGER,
            preparingAtEpochMillis INTEGER,
            readyAtEpochMillis INTEGER,
            servedAtEpochMillis INTEGER,
            cancelledAtEpochMillis INTEGER,
            cancelReason TEXT NOT NULL DEFAULT '',
            updatedByActorId INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(orderLineId) REFERENCES restaurant_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS kitchen_ticket_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            ticketId INTEGER NOT NULL,
            fromStatus TEXT NOT NULL,
            toStatus TEXT NOT NULL,
            reason TEXT NOT NULL DEFAULT '',
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(ticketId) REFERENCES kitchen_tickets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS restaurant_bill_splits (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            orderId INTEGER NOT NULL,
            payerNo INTEGER NOT NULL,
            method TEXT NOT NULL,
            amountRial INTEGER NOT NULL,
            referenceNo TEXT NOT NULL DEFAULT '',
            orderLineId INTEGER,
            FOREIGN KEY(orderId) REFERENCES restaurant_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )

    // A default hall is safe, deterministic master data; tables themselves are never fabricated.
    db.execSQL(
        """INSERT INTO restaurant_halls(name,sortOrder,isActive,createdAtEpochMillis)
           SELECT 'سالن اصلی',0,1,CAST(strftime('%s','now') AS INTEGER)*1000
           WHERE NOT EXISTS(SELECT 1 FROM restaurant_halls)""",
    )
}

private fun createRecipeLifecycleTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS recipe_components (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            recipeVersionId INTEGER NOT NULL,
            subRecipeVersionId INTEGER NOT NULL,
            quantityMicrosPerUnit INTEGER NOT NULL,
            FOREIGN KEY(recipeVersionId) REFERENCES recipe_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(subRecipeVersionId) REFERENCES recipe_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS recipe_substitutions (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            recipeVersionId INTEGER NOT NULL,
            originalInventoryItemId INTEGER NOT NULL,
            substituteInventoryItemId INTEGER NOT NULL,
            ratioNumerator INTEGER NOT NULL DEFAULT 1,
            ratioDenominator INTEGER NOT NULL DEFAULT 1,
            reason TEXT NOT NULL,
            approvedByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(recipeVersionId) REFERENCES recipe_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(originalInventoryItemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(substituteInventoryItemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
}

private fun createAssetLifecycleTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS asset_lifecycle_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            assetId INTEGER NOT NULL,
            eventType TEXT NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            amountRial INTEGER NOT NULL DEFAULT 0,
            fromLocation TEXT NOT NULL DEFAULT '',
            toLocation TEXT NOT NULL DEFAULT '',
            fromBranch TEXT NOT NULL DEFAULT '',
            toBranch TEXT NOT NULL DEFAULT '',
            fromResponsiblePerson TEXT NOT NULL DEFAULT '',
            toResponsiblePerson TEXT NOT NULL DEFAULT '',
            counterparty TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            journalEntryId INTEGER,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(assetId) REFERENCES fixed_assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS asset_maintenance (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            assetId INTEGER NOT NULL,
            serviceType TEXT NOT NULL,
            serviceEpochDay INTEGER NOT NULL,
            costRial INTEGER NOT NULL,
            contractor TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '',
            nextServiceEpochDay INTEGER,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(assetId) REFERENCES fixed_assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
}

private fun createCrmLedgerTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS customer_receivable_ledger (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            customerId INTEGER NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            entryType TEXT NOT NULL,
            debitRial INTEGER NOT NULL,
            creditRial INTEGER NOT NULL,
            sourceType TEXT NOT NULL,
            sourceId INTEGER NOT NULL,
            reference TEXT NOT NULL DEFAULT '',
            dueEpochDay INTEGER,
            reversalOfLedgerId INTEGER,
            actorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS customer_merge_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            sourceCustomerId INTEGER NOT NULL,
            targetCustomerId INTEGER NOT NULL,
            reason TEXT NOT NULL,
            actorId INTEGER NOT NULL,
            mergedAtEpochMillis INTEGER NOT NULL
        )""",
    )

    // Backfill receivables from posted historic credit sales. Existing immutable sales rows remain untouched.
    db.execSQL(
        """INSERT INTO customer_receivable_ledger(customerId,businessEpochDay,entryType,debitRial,creditRial,sourceType,sourceId,reference,dueEpochDay,actorId,createdAtEpochMillis)
           SELECT customerId,businessEpochDay,'SALE',creditRial,0,'SALES_INVOICE',id,invoiceNo,dueEpochDay,createdByActorId,createdAtEpochMillis
           FROM sales_invoices
           WHERE customerId IS NOT NULL AND creditRial>0 AND status!='VOIDED'
             AND NOT EXISTS(SELECT 1 FROM customer_receivable_ledger l WHERE l.sourceType='SALES_INVOICE' AND l.sourceId=sales_invoices.id)""",
    )
    // Historic credit returns reduce the migrated customer balance as well. Excluding them would
    // overstate receivables after upgrading a v45 database that already contains returns.
    db.execSQL(
        """INSERT INTO customer_receivable_ledger(customerId,businessEpochDay,entryType,debitRial,creditRial,sourceType,sourceId,reference,dueEpochDay,actorId,createdAtEpochMillis)
           SELECT i.customerId,r.returnEpochDay,'RETURN',0,r.refundRial,'SALES_RETURN',r.id,r.returnNo,NULL,r.createdByActorId,r.createdAtEpochMillis
           FROM sales_returns r
           JOIN sales_invoices i ON i.id=r.invoiceId
           WHERE i.customerId IS NOT NULL AND i.status!='VOIDED' AND r.refundMethod='CREDIT' AND r.refundRial>0
             AND NOT EXISTS(SELECT 1 FROM customer_receivable_ledger l WHERE l.sourceType='SALES_RETURN' AND l.sourceId=r.id)""",
    )
}

private fun createEnterpriseCoreIndexes(db: SupportSQLiteDatabase) {
    listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_transactions_commandId ON treasury_transactions(commandId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_transactions_businessEpochDay ON treasury_transactions(businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_treasury_transactions_sourceType_sourceId ON treasury_transactions(sourceType,sourceId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_transactions_journalEntryId ON treasury_transactions(journalEntryId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_transactions_status ON treasury_transactions(status)",
        "CREATE INDEX IF NOT EXISTS index_treasury_transactions_reversalOfTransactionId ON treasury_transactions(reversalOfTransactionId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_ledger_entries_transactionId ON treasury_ledger_entries(transactionId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_ledger_entries_accountId_businessEpochDay ON treasury_ledger_entries(accountId,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_treasury_ledger_entries_sourceType_sourceId ON treasury_ledger_entries(sourceType,sourceId)",
        "CREATE INDEX IF NOT EXISTS index_treasury_ledger_entries_direction ON treasury_ledger_entries(direction)",
        "CREATE INDEX IF NOT EXISTS index_treasury_reconciliations_accountId_businessEpochDay ON treasury_reconciliations(accountId,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_treasury_reconciliations_transactionId ON treasury_reconciliations(transactionId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_restaurant_halls_name ON restaurant_halls(name)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_halls_isActive ON restaurant_halls(isActive)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_restaurant_tables_hallId_tableNo ON restaurant_tables(hallId,tableNo)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_tables_status ON restaurant_tables(status)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_reservations_tableId ON restaurant_reservations(tableId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_reservations_customerId ON restaurant_reservations(customerId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_reservations_reservationEpochDay_startMinuteOfDay ON restaurant_reservations(reservationEpochDay,startMinuteOfDay)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_reservations_status ON restaurant_reservations(status)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_orders_tableId ON restaurant_orders(tableId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_orders_status ON restaurant_orders(status)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_orders_openedEpochDay ON restaurant_orders(openedEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_orders_waiterUserId ON restaurant_orders(waiterUserId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_orders_customerId ON restaurant_orders(customerId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_restaurant_orders_commandId ON restaurant_orders(commandId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_order_lines_orderId ON restaurant_order_lines(orderId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_order_lines_menuItemId ON restaurant_order_lines(menuItemId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_order_lines_status ON restaurant_order_lines(status)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_kitchen_tickets_orderLineId ON kitchen_tickets(orderLineId)",
        "CREATE INDEX IF NOT EXISTS index_kitchen_tickets_status ON kitchen_tickets(status)",
        "CREATE INDEX IF NOT EXISTS index_kitchen_tickets_updatedAtEpochMillis ON kitchen_tickets(updatedAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_kitchen_ticket_events_ticketId ON kitchen_ticket_events(ticketId)",
        "CREATE INDEX IF NOT EXISTS index_kitchen_ticket_events_createdAtEpochMillis ON kitchen_ticket_events(createdAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_bill_splits_orderId ON restaurant_bill_splits(orderId)",
        "CREATE INDEX IF NOT EXISTS index_restaurant_bill_splits_payerNo ON restaurant_bill_splits(payerNo)",
        "CREATE INDEX IF NOT EXISTS index_recipe_components_recipeVersionId ON recipe_components(recipeVersionId)",
        "CREATE INDEX IF NOT EXISTS index_recipe_components_subRecipeVersionId ON recipe_components(subRecipeVersionId)",
        "CREATE INDEX IF NOT EXISTS index_recipe_substitutions_recipeVersionId ON recipe_substitutions(recipeVersionId)",
        "CREATE INDEX IF NOT EXISTS index_recipe_substitutions_originalInventoryItemId ON recipe_substitutions(originalInventoryItemId)",
        "CREATE INDEX IF NOT EXISTS index_recipe_substitutions_substituteInventoryItemId ON recipe_substitutions(substituteInventoryItemId)",
        "CREATE INDEX IF NOT EXISTS index_asset_lifecycle_events_assetId ON asset_lifecycle_events(assetId)",
        "CREATE INDEX IF NOT EXISTS index_asset_lifecycle_events_eventType ON asset_lifecycle_events(eventType)",
        "CREATE INDEX IF NOT EXISTS index_asset_lifecycle_events_businessEpochDay ON asset_lifecycle_events(businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_asset_lifecycle_events_journalEntryId ON asset_lifecycle_events(journalEntryId)",
        "CREATE INDEX IF NOT EXISTS index_asset_maintenance_assetId ON asset_maintenance(assetId)",
        "CREATE INDEX IF NOT EXISTS index_asset_maintenance_serviceEpochDay ON asset_maintenance(serviceEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_asset_maintenance_nextServiceEpochDay ON asset_maintenance(nextServiceEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_customer_receivable_ledger_customerId_businessEpochDay ON customer_receivable_ledger(customerId,businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_customer_receivable_ledger_sourceType_sourceId ON customer_receivable_ledger(sourceType,sourceId)",
        "CREATE INDEX IF NOT EXISTS index_customer_receivable_ledger_entryType ON customer_receivable_ledger(entryType)",
        "CREATE INDEX IF NOT EXISTS index_customer_merge_history_sourceCustomerId ON customer_merge_history(sourceCustomerId)",
        "CREATE INDEX IF NOT EXISTS index_customer_merge_history_targetCustomerId ON customer_merge_history(targetCustomerId)",
        "CREATE INDEX IF NOT EXISTS index_customer_merge_history_mergedAtEpochMillis ON customer_merge_history(mergedAtEpochMillis)",
    ).forEach(db::execSQL)
}
