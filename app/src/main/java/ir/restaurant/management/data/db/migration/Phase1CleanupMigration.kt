package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Removes obsolete table-service, KDS, reservation and held-order workflow state while preserving posted sales facts. */
internal val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "trg_professional_sales_closed_day_invoice_insert",
            "trg_professional_sales_closed_day_return_insert",
            "trg_professional_sales_closed_day_stock_insert",
            "trg_invoice_sales_closed_day_invoice_insert",
            "trg_invoice_sales_closed_day_return_insert",
            "trg_invoice_sales_closed_day_stock_insert",
        ).forEach { trigger -> db.execSQL("DROP TRIGGER IF EXISTS $trigger") }

        db.execSQL("ALTER TABLE sales_pos_day_closures RENAME TO invoice_sales_day_closures")
        db.execSQL("DROP INDEX IF EXISTS index_sales_pos_day_closures_status")
        db.execSQL("DROP INDEX IF EXISTS index_sales_pos_day_closures_createdAtEpochMillis")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_sales_day_closures_status ON invoice_sales_day_closures(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_sales_day_closures_createdAtEpochMillis ON invoice_sales_day_closures(createdAtEpochMillis)")

        // Children first so foreign-key relationships are dismantled safely.
        listOf(
            "kitchen_ticket_events",
            "kitchen_tickets",
            "restaurant_bill_splits",
            "restaurant_order_lines",
            "restaurant_orders",
            "restaurant_reservations",
            "restaurant_tables",
            "restaurant_halls",
            "sales_hold_lines",
            "sales_holds",
        ).forEach { table -> db.execSQL("DROP TABLE IF EXISTS $table") }

        // A persisted KDS-only role is intentionally downgraded instead of being silently granted new access.
        db.execSQL("UPDATE app_users SET role='RESTRICTED' WHERE role='KITCHEN'")
        db.execSQL("DELETE FROM sync_changes WHERE entityType IN ('TABLE','RESERVATION','KITCHEN_TICKET','TABLE_ORDER','BILL_SPLIT','SALES_HOLD')")

        installSalesHistoryGuards(db)
    }
}
