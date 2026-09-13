package ir.restaurant.management.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Database-level invariants that preserve historical posted-sales facts. */
internal val salesHistoryGuardNames = listOf(
    "trg_sales_invoice_financial_identity_immutable",
    "trg_sales_invoices_no_delete",
    "trg_sales_invoice_lines_no_update",
    "trg_sales_invoice_lines_no_delete",
    "trg_sales_payments_no_update",
    "trg_sales_payments_no_delete",
    "trg_sales_consumption_no_update",
    "trg_sales_consumption_no_delete",
    "trg_sales_return_identity_immutable",
    "trg_sales_returns_no_delete",
    "trg_sales_return_lines_no_update",
    "trg_sales_return_lines_no_delete",
    "trg_invoice_sales_closed_day_invoice_insert",
    "trg_invoice_sales_closed_day_return_insert",
    "trg_invoice_sales_closed_day_stock_insert",
)

internal fun installSalesHistoryGuards(
    db: SupportSQLiteDatabase,
    invoiceClosureTable: String = "invoice_sales_day_closures",
) {
    require(invoiceClosureTable.matches(Regex("[a-z_]+"))) { "unsupported_sales_history_closure_table" }
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_sales_invoice_financial_identity_immutable
        BEFORE UPDATE ON sales_invoices
        WHEN NEW.invoiceNo IS NOT OLD.invoiceNo
          OR NEW.commandId IS NOT OLD.commandId
          OR NEW.businessEpochDay IS NOT OLD.businessEpochDay
          OR NEW.customerId IS NOT OLD.customerId
          OR NEW.dueEpochDay IS NOT OLD.dueEpochDay
          OR NEW.grossRial IS NOT OLD.grossRial
          OR NEW.discountRial IS NOT OLD.discountRial
          OR NEW.serviceRial IS NOT OLD.serviceRial
          OR NEW.taxRial IS NOT OLD.taxRial
          OR NEW.netRial IS NOT OLD.netRial
          OR NEW.creditRial IS NOT OLD.creditRial
          OR NEW.theoreticalCostRial IS NOT OLD.theoreticalCostRial
          OR NEW.notes IS NOT OLD.notes
          OR NEW.createdByActorId IS NOT OLD.createdByActorId
          OR NEW.createdAtEpochMillis IS NOT OLD.createdAtEpochMillis
        BEGIN SELECT RAISE(ABORT, 'SALES_INVOICE_IMMUTABLE'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_sales_invoices_no_delete
        BEFORE DELETE ON sales_invoices BEGIN SELECT RAISE(ABORT, 'SALES_INVOICE_APPEND_ONLY'); END""")
    immutable(db, "sales_invoice_lines", "trg_sales_invoice_lines")
    immutable(db, "sales_payments", "trg_sales_payments")
    immutable(db, "sales_consumption_snapshots", "trg_sales_consumption")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_sales_return_identity_immutable
        BEFORE UPDATE ON sales_returns
        WHEN NEW.returnNo IS NOT OLD.returnNo OR NEW.commandId IS NOT OLD.commandId
          OR NEW.invoiceId IS NOT OLD.invoiceId OR NEW.returnEpochDay IS NOT OLD.returnEpochDay
          OR NEW.refundMethod IS NOT OLD.refundMethod OR NEW.grossRial IS NOT OLD.grossRial
          OR NEW.discountRial IS NOT OLD.discountRial OR NEW.serviceRial IS NOT OLD.serviceRial
          OR NEW.taxRial IS NOT OLD.taxRial OR NEW.refundRial IS NOT OLD.refundRial
          OR NEW.cogsRial IS NOT OLD.cogsRial OR NEW.reason IS NOT OLD.reason
          OR NEW.createdByActorId IS NOT OLD.createdByActorId OR NEW.createdAtEpochMillis IS NOT OLD.createdAtEpochMillis
        BEGIN SELECT RAISE(ABORT, 'SALES_RETURN_IMMUTABLE'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_sales_returns_no_delete
        BEFORE DELETE ON sales_returns BEGIN SELECT RAISE(ABORT, 'SALES_RETURN_APPEND_ONLY'); END""")
    immutable(db, "sales_return_lines", "trg_sales_return_lines")

    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_invoice_sales_closed_day_invoice_insert
        BEFORE INSERT ON sales_invoices
        WHEN EXISTS(SELECT 1 FROM sales_day_closures c WHERE c.status='CLOSED' AND c.businessEpochDay=NEW.businessEpochDay)
          OR EXISTS(SELECT 1 FROM $invoiceClosureTable p WHERE p.status='CLOSED' AND p.businessEpochDay=NEW.businessEpochDay)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_invoice_sales_closed_day_return_insert
        BEFORE INSERT ON sales_returns
        WHEN EXISTS(SELECT 1 FROM sales_day_closures c WHERE c.status='CLOSED' AND c.businessEpochDay=NEW.returnEpochDay)
          OR EXISTS(SELECT 1 FROM $invoiceClosureTable p WHERE p.status='CLOSED' AND p.businessEpochDay=NEW.returnEpochDay)
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
    db.execSQL("""CREATE TRIGGER IF NOT EXISTS trg_invoice_sales_closed_day_stock_insert
        BEFORE INSERT ON stock_movements
        WHEN NEW.referenceType IN ('SALES_INVOICE','SALES_RETURN','SALES_VOID')
         AND (EXISTS(SELECT 1 FROM sales_day_closures c WHERE c.status='CLOSED' AND c.businessEpochDay=NEW.movementEpochDay)
          OR EXISTS(SELECT 1 FROM $invoiceClosureTable p WHERE p.status='CLOSED' AND p.businessEpochDay=NEW.movementEpochDay))
        BEGIN SELECT RAISE(ABORT, 'SALES_DAY_CLOSED'); END""")
}

private fun immutable(db: SupportSQLiteDatabase, table: String, prefix: String) {
    db.execSQL("CREATE TRIGGER IF NOT EXISTS ${prefix}_no_update BEFORE UPDATE ON $table BEGIN SELECT RAISE(ABORT, 'SALES_LEDGER_IMMUTABLE'); END")
    db.execSQL("CREATE TRIGGER IF NOT EXISTS ${prefix}_no_delete BEFORE DELETE ON $table BEGIN SELECT RAISE(ABORT, 'SALES_LEDGER_IMMUTABLE'); END")
}
