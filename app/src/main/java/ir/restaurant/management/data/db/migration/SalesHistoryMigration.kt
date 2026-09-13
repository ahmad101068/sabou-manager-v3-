package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 44→45: transactional document numbering, customer sub-ledger and posted sales ledger. */
internal val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createSalesHistoryTables(db)
        createSalesHistoryIndexes(db)
        backfillDocumentSequences(db)
        installSalesHistoryGuards(db, "sales_pos_day_closures")
    }
}

private fun createSalesHistoryTables(db: SupportSQLiteDatabase) {
    listOf(
        """CREATE TABLE IF NOT EXISTS document_sequences (
            sequenceKey TEXT NOT NULL,
            nextValue INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            PRIMARY KEY(sequenceKey)
        )""",
        """CREATE TABLE IF NOT EXISTS customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            customerCode TEXT NOT NULL,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            nationalId TEXT NOT NULL,
            creditLimitRial INTEGER NOT NULL,
            notes TEXT NOT NULL,
            isActive INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL
        )""",
        """CREATE TABLE IF NOT EXISTS sales_invoices (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            invoiceNo TEXT NOT NULL,
            commandId TEXT NOT NULL,
            businessEpochDay INTEGER NOT NULL,
            customerId INTEGER,
            dueEpochDay INTEGER,
            grossRial INTEGER NOT NULL,
            discountRial INTEGER NOT NULL,
            serviceRial INTEGER NOT NULL,
            taxRial INTEGER NOT NULL,
            netRial INTEGER NOT NULL,
            creditRial INTEGER NOT NULL,
            theoreticalCostRial INTEGER NOT NULL,
            journalEntryId INTEGER,
            cogsJournalEntryId INTEGER,
            status TEXT NOT NULL,
            notes TEXT NOT NULL,
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            voidedAtEpochDay INTEGER,
            voidCommandId TEXT,
            voidReason TEXT NOT NULL,
            voidJournalEntryId INTEGER,
            voidCogsJournalEntryId INTEGER,
            FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_invoice_lines (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            invoiceId INTEGER NOT NULL,
            menuItemId INTEGER NOT NULL,
            recipeVersionId INTEGER NOT NULL,
            menuItemNameSnapshot TEXT NOT NULL,
            quantityMicros INTEGER NOT NULL,
            unitPriceRial INTEGER NOT NULL,
            grossRial INTEGER NOT NULL,
            discountRial INTEGER NOT NULL,
            serviceRial INTEGER NOT NULL,
            taxRial INTEGER NOT NULL,
            netRial INTEGER NOT NULL,
            theoreticalCostRial INTEGER NOT NULL,
            foodCostSnapshotRial INTEGER NOT NULL,
            packagingCostSnapshotRial INTEGER NOT NULL,
            directLaborCostSnapshotRial INTEGER NOT NULL,
            allocatedOverheadSnapshotRial INTEGER NOT NULL,
            FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(menuItemId) REFERENCES menu_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(recipeVersionId) REFERENCES recipe_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_payments (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            invoiceId INTEGER NOT NULL,
            method TEXT NOT NULL,
            amountRial INTEGER NOT NULL,
            referenceNo TEXT NOT NULL,
            FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_consumption_snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            invoiceLineId INTEGER NOT NULL,
            inventoryItemId INTEGER NOT NULL,
            inventoryItemNameSnapshot TEXT NOT NULL,
            quantityMicros INTEGER NOT NULL,
            valueRial INTEGER NOT NULL,
            FOREIGN KEY(invoiceLineId) REFERENCES sales_invoice_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(inventoryItemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_returns (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            returnNo TEXT NOT NULL,
            commandId TEXT NOT NULL,
            invoiceId INTEGER NOT NULL,
            returnEpochDay INTEGER NOT NULL,
            refundMethod TEXT NOT NULL,
            grossRial INTEGER NOT NULL,
            discountRial INTEGER NOT NULL,
            serviceRial INTEGER NOT NULL,
            taxRial INTEGER NOT NULL,
            refundRial INTEGER NOT NULL,
            cogsRial INTEGER NOT NULL,
            reason TEXT NOT NULL,
            journalEntryId INTEGER,
            cogsJournalEntryId INTEGER,
            createdByActorId INTEGER NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_return_lines (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            returnId INTEGER NOT NULL,
            invoiceLineId INTEGER NOT NULL,
            quantityMicros INTEGER NOT NULL,
            grossRial INTEGER NOT NULL,
            discountRial INTEGER NOT NULL,
            serviceRial INTEGER NOT NULL,
            taxRial INTEGER NOT NULL,
            netRial INTEGER NOT NULL,
            cogsRial INTEGER NOT NULL,
            FOREIGN KEY(returnId) REFERENCES sales_returns(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(invoiceLineId) REFERENCES sales_invoice_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_holds (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            holdNo TEXT NOT NULL,
            commandId TEXT NOT NULL,
            customerId INTEGER,
            orderDiscountRial INTEGER NOT NULL,
            serviceRial INTEGER NOT NULL,
            taxRial INTEGER NOT NULL,
            notes TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_hold_lines (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            holdId INTEGER NOT NULL,
            menuItemId INTEGER NOT NULL,
            quantityMicros INTEGER NOT NULL,
            unitPriceRial INTEGER NOT NULL,
            lineDiscountRial INTEGER NOT NULL,
            FOREIGN KEY(holdId) REFERENCES sales_holds(id) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(menuItemId) REFERENCES menu_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
        """CREATE TABLE IF NOT EXISTS sales_pos_day_closures (
            businessEpochDay INTEGER NOT NULL,
            grossSalesRial INTEGER NOT NULL,
            netSalesRial INTEGER NOT NULL,
            returnRial INTEGER NOT NULL,
            cogsRial INTEGER NOT NULL,
            cashRial INTEGER NOT NULL,
            cardRial INTEGER NOT NULL,
            transferRial INTEGER NOT NULL,
            creditRial INTEGER NOT NULL,
            invoiceCount INTEGER NOT NULL,
            returnCount INTEGER NOT NULL,
            status TEXT NOT NULL,
            revisionNo INTEGER NOT NULL,
            closedByActorId INTEGER NOT NULL,
            closedByName TEXT NOT NULL,
            note TEXT NOT NULL,
            reopenedByActorId INTEGER,
            reopenedByName TEXT,
            reopenReason TEXT NOT NULL,
            reopenedAtEpochMillis INTEGER,
            createdAtEpochMillis INTEGER NOT NULL,
            PRIMARY KEY(businessEpochDay)
        )""",
    ).forEach(db::execSQL)
}

private fun createSalesHistoryIndexes(db: SupportSQLiteDatabase) {
    listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_customers_customerCode ON customers(customerCode)",
        "CREATE INDEX IF NOT EXISTS index_customers_name ON customers(name)",
        "CREATE INDEX IF NOT EXISTS index_customers_phone ON customers(phone)",
        "CREATE INDEX IF NOT EXISTS index_customers_nationalId ON customers(nationalId)",
        "CREATE INDEX IF NOT EXISTS index_customers_isActive ON customers(isActive)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoices_invoiceNo ON sales_invoices(invoiceNo)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoices_commandId ON sales_invoices(commandId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoices_voidCommandId ON sales_invoices(voidCommandId)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoices_businessEpochDay ON sales_invoices(businessEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoices_customerId ON sales_invoices(customerId)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoices_status ON sales_invoices(status)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoices_createdAtEpochMillis ON sales_invoices(createdAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoice_lines_invoiceId ON sales_invoice_lines(invoiceId)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoice_lines_menuItemId ON sales_invoice_lines(menuItemId)",
        "CREATE INDEX IF NOT EXISTS index_sales_invoice_lines_recipeVersionId ON sales_invoice_lines(recipeVersionId)",
        "CREATE INDEX IF NOT EXISTS index_sales_payments_invoiceId ON sales_payments(invoiceId)",
        "CREATE INDEX IF NOT EXISTS index_sales_payments_method ON sales_payments(method)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_consumption_snapshots_invoiceLineId_inventoryItemId ON sales_consumption_snapshots(invoiceLineId, inventoryItemId)",
        "CREATE INDEX IF NOT EXISTS index_sales_consumption_snapshots_inventoryItemId ON sales_consumption_snapshots(inventoryItemId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_returns_returnNo ON sales_returns(returnNo)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_returns_commandId ON sales_returns(commandId)",
        "CREATE INDEX IF NOT EXISTS index_sales_returns_invoiceId ON sales_returns(invoiceId)",
        "CREATE INDEX IF NOT EXISTS index_sales_returns_returnEpochDay ON sales_returns(returnEpochDay)",
        "CREATE INDEX IF NOT EXISTS index_sales_return_lines_returnId ON sales_return_lines(returnId)",
        "CREATE INDEX IF NOT EXISTS index_sales_return_lines_invoiceLineId ON sales_return_lines(invoiceLineId)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_holds_holdNo ON sales_holds(holdNo)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_sales_holds_commandId ON sales_holds(commandId)",
        "CREATE INDEX IF NOT EXISTS index_sales_holds_customerId ON sales_holds(customerId)",
        "CREATE INDEX IF NOT EXISTS index_sales_holds_createdAtEpochMillis ON sales_holds(createdAtEpochMillis)",
        "CREATE INDEX IF NOT EXISTS index_sales_hold_lines_holdId ON sales_hold_lines(holdId)",
        "CREATE INDEX IF NOT EXISTS index_sales_hold_lines_menuItemId ON sales_hold_lines(menuItemId)",
        "CREATE INDEX IF NOT EXISTS index_sales_pos_day_closures_status ON sales_pos_day_closures(status)",
        "CREATE INDEX IF NOT EXISTS index_sales_pos_day_closures_createdAtEpochMillis ON sales_pos_day_closures(createdAtEpochMillis)",
    ).forEach(db::execSQL)
}


/**
 * Seeds the transactional allocator from existing human-readable numbers. This prevents the new
 * sequence table from restarting at one after a 44 -> 45 upgrade. Unknown legacy formats are left
 * untouched; repositories still retain their unique-key collision checks as a secondary guard.
 */
private fun backfillDocumentSequences(db: SupportSQLiteDatabase) {
    val nowSql = "CAST(strftime('%s','now') AS INTEGER) * 1000"
    val legacySequences = listOf(
        Triple("purchase_invoice", "purchases", "invoiceNo|PUR-|5"),
        Triple("purchase_requisition", "purchase_requisitions", "requestNo|PR-|4"),
        Triple("purchase_order", "purchase_orders", "orderNo|PO-|4"),
        Triple("goods_receipt", "goods_receipts", "receiptNo|GR-|4"),
        Triple("purchase_return", "purchase_returns", "returnNo|PRT-|5"),
        Triple("inventory_transfer", "stock_transfers", "transferNo|TRF-|5"),
        Triple("fixed_asset", "fixed_assets", "assetCode|AST-|5"),
        Triple("employee", "employees", "employeeCode|EMP-|5"),
    )
    legacySequences.forEach { (sequenceKey, table, spec) ->
        val (column, prefix, start) = spec.split('|')
        db.execSQL(
            """
            INSERT OR REPLACE INTO document_sequences(sequenceKey,nextValue,updatedAtEpochMillis)
            SELECT '$sequenceKey',
                   MAX(1, COALESCE(MAX(CASE
                       WHEN $column GLOB '$prefix[0-9]*' THEN CAST(substr($column, $start) AS INTEGER)
                       ELSE 0 END), 0) + 1),
                   $nowSql
            FROM $table
            """.trimIndent(),
        )
    }
    listOf("sales_invoice", "sales_return", "sales_hold", "customer").forEach { sequenceKey ->
        db.execSQL(
            "INSERT OR IGNORE INTO document_sequences(sequenceKey,nextValue,updatedAtEpochMillis) VALUES('$sequenceKey',1,$nowSql)",
        )
    }
}
