package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration48To49Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-48-49.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun removesOperationalRestaurantTablesAndPreservesPostedSalesHistory() {
        open(48).use { helper ->
            val db = helper.writableDatabase
            createSalesGuardDependencies(db)
            db.execSQL("CREATE TABLE restaurant_orders(id INTEGER PRIMARY KEY NOT NULL, postedInvoiceId INTEGER)")
            db.execSQL("CREATE TABLE restaurant_order_lines(id INTEGER PRIMARY KEY NOT NULL, orderId INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE kitchen_tickets(id INTEGER PRIMARY KEY NOT NULL)")
            db.execSQL("CREATE TABLE sales_holds(id INTEGER PRIMARY KEY NOT NULL)")
            db.execSQL("INSERT INTO restaurant_orders(id,postedInvoiceId) VALUES(1,77)")
            db.execSQL("INSERT INTO restaurant_order_lines(id,orderId) VALUES(1,1)")
            db.execSQL("INSERT INTO kitchen_tickets(id) VALUES(1)")
            db.execSQL("INSERT INTO sales_holds(id) VALUES(1)")
            db.execSQL("INSERT INTO sales_invoices(id,invoiceNo,commandId,businessEpochDay,grossRial,discountRial,serviceRial,taxRial,netRial,creditRial,theoreticalCostRial,notes,createdByActorId,createdAtEpochMillis) VALUES(77,'I-77','cmd-77',20000,1000,0,0,90,1090,0,400,'posted',1,1)")
            db.execSQL("INSERT INTO app_users(id,role) VALUES(1,'KITCHEN')")
            db.execSQL("INSERT INTO sync_changes(id,entityType) VALUES(1,'TABLE')")
            db.execSQL("INSERT INTO sync_changes(id,entityType) VALUES(2,'SALES_INVOICE')")
        }

        open(49).use { helper ->
            val db = helper.writableDatabase
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='restaurant_orders'"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='kitchen_tickets'"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='sales_holds'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sales_invoices WHERE id=77 AND netRial=1090"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM invoice_sales_day_closures"))
            assertEquals("RESTRICTED", text(db, "SELECT role FROM app_users WHERE id=1"))
            assertEquals(0L, scalar(db, "SELECT COUNT(*) FROM sync_changes WHERE entityType='TABLE'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sync_changes WHERE entityType='SALES_INVOICE'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_sales_invoices_no_delete'"))
        }
    }

    private fun createSalesGuardDependencies(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE sales_pos_day_closures(businessEpochDay INTEGER PRIMARY KEY NOT NULL, status TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX index_sales_pos_day_closures_status ON sales_pos_day_closures(status)")
        db.execSQL("CREATE INDEX index_sales_pos_day_closures_createdAtEpochMillis ON sales_pos_day_closures(createdAtEpochMillis)")
        db.execSQL("INSERT INTO sales_pos_day_closures VALUES(19999,'CLOSED',1)")
        db.execSQL("CREATE TABLE sales_day_closures(businessEpochDay INTEGER PRIMARY KEY NOT NULL, status TEXT NOT NULL)")
        db.execSQL("CREATE TABLE app_users(id INTEGER PRIMARY KEY NOT NULL, role TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sync_changes(id INTEGER PRIMARY KEY NOT NULL, entityType TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sales_invoices(id INTEGER PRIMARY KEY NOT NULL, invoiceNo TEXT NOT NULL, commandId TEXT NOT NULL, businessEpochDay INTEGER NOT NULL, customerId INTEGER, dueEpochDay INTEGER, grossRial INTEGER NOT NULL, discountRial INTEGER NOT NULL, serviceRial INTEGER NOT NULL, taxRial INTEGER NOT NULL, netRial INTEGER NOT NULL, creditRial INTEGER NOT NULL, theoreticalCostRial INTEGER NOT NULL, notes TEXT NOT NULL, createdByActorId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sales_invoice_lines(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE sales_payments(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE sales_consumption_snapshots(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE sales_returns(id INTEGER PRIMARY KEY NOT NULL, returnNo TEXT NOT NULL, commandId TEXT NOT NULL, invoiceId INTEGER NOT NULL, returnEpochDay INTEGER NOT NULL, refundMethod TEXT NOT NULL, grossRial INTEGER NOT NULL, discountRial INTEGER NOT NULL, serviceRial INTEGER NOT NULL, taxRial INTEGER NOT NULL, refundRial INTEGER NOT NULL, cogsRial INTEGER NOT NULL, reason TEXT NOT NULL, createdByActorId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE sales_return_lines(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE stock_movements(id INTEGER PRIMARY KEY NOT NULL, referenceType TEXT NOT NULL, movementEpochDay INTEGER NOT NULL)")
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 48 && newVersion == 49) MIGRATION_48_49.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName).callback(callback).build(),
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun text(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }
}
