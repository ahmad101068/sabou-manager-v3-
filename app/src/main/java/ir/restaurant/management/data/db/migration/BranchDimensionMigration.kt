package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Branch-dimension migration. Historical v45/v46 schemas remain immutable. */
internal val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN branchName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchases ADD COLUMN branchName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE storage_locations ADD COLUMN branchName TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_branchName ON sales_invoices(branchName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_branchName ON purchases(branchName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_storage_locations_branchName ON storage_locations(branchName)")

        // Safe backfill where an authoritative branch already exists. Cash/anonymous historical
        // sales and historical purchases without an explicit branch remain unassigned instead of guessed.
        db.execSQL(
            """UPDATE sales_invoices
               SET branchName=COALESCE((SELECT c.branch FROM customers c WHERE c.id=sales_invoices.customerId),'')
               WHERE branchName='' AND customerId IS NOT NULL""",
        )
    }
}
