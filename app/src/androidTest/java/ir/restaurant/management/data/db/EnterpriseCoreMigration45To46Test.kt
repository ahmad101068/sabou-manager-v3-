package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnterpriseCoreMigration45To46Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "enterprise-core-45-46.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test
    fun migratesEnterpriseCoreAndPreservesHistoricReceivableTruth() {
        openDatabase(45).use { helper ->
            val db = helper.writableDatabase
            createV45Prerequisites(db)
            db.execSQL("INSERT INTO customers(id,phone,nationalId) VALUES(1,'09120000000','0012345678')")
            db.execSQL("INSERT INTO app_alerts(id,isRead,isDismissed) VALUES(1,1,0),(2,0,1),(3,0,0)")
            db.execSQL("INSERT INTO recipe_versions(id) VALUES(10)")
            db.execSQL("INSERT INTO fixed_assets(id) VALUES(20)")
            db.execSQL(
                """INSERT INTO sales_invoices(id,invoiceNo,businessEpochDay,customerId,dueEpochDay,creditRial,status,createdByActorId,createdAtEpochMillis)
                   VALUES(30,'SAL-00000030',22000,1,22030,1000,'POSTED',7,100)""",
            )
            db.execSQL(
                """INSERT INTO sales_returns(id,returnNo,invoiceId,returnEpochDay,refundMethod,refundRial,createdByActorId,createdAtEpochMillis)
                   VALUES(40,'SRT-00000040',30,22001,'CREDIT',250,7,200)""",
            )
        }

        openDatabase(46).use { helper ->
            val db = helper.writableDatabase
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='treasury_transactions'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='restaurant_orders'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recipe_components'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='asset_lifecycle_events'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='customer_receivable_ledger'"))

            assertEquals("ACTIVE", text(db, "SELECT status FROM customers WHERE id=1"))
            assertEquals("ACTIVE", text(db, "SELECT status FROM recipe_versions WHERE id=10"))
            assertEquals(0L, scalar(db, "SELECT impairmentRial FROM fixed_assets WHERE id=20"))
            assertEquals("READ", text(db, "SELECT status FROM app_alerts WHERE id=1"))
            assertEquals("DISMISSED", text(db, "SELECT status FROM app_alerts WHERE id=2"))
            assertEquals("NEW", text(db, "SELECT status FROM app_alerts WHERE id=3"))

            assertEquals(1000L, scalar(db, "SELECT COALESCE(SUM(debitRial),0) FROM customer_receivable_ledger WHERE customerId=1"))
            assertEquals(250L, scalar(db, "SELECT COALESCE(SUM(creditRial),0) FROM customer_receivable_ledger WHERE customerId=1"))
            assertEquals(750L, scalar(db, "SELECT COALESCE(SUM(debitRial-creditRial),0) FROM customer_receivable_ledger WHERE customerId=1"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM customer_receivable_ledger WHERE sourceType='SALES_INVOICE' AND sourceId=30"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM customer_receivable_ledger WHERE sourceType='SALES_RETURN' AND sourceId=40"))

            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM restaurant_halls WHERE name='سالن اصلی'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM accounts WHERE code='1503'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_treasury_transactions_commandId'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_customer_receivable_ledger_customerId_businessEpochDay'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_restaurant_orders_customerId'"))
            assertForeignKey(db, "restaurant_orders", "app_users", "waiterUserId", "id", "RESTRICT")
            assertForeignKey(db, "restaurant_orders", "customers", "customerId", "id", "RESTRICT")
            assertForeignKey(db, "restaurant_orders", "restaurant_tables", "tableId", "id", "RESTRICT")

            db.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    private fun createV45Prerequisites(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY NOT NULL,phone TEXT NOT NULL DEFAULT '',nationalId TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE app_users(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE recipe_versions(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE fixed_assets(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE app_alerts(id INTEGER PRIMARY KEY NOT NULL,isRead INTEGER NOT NULL DEFAULT 0,isDismissed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,code TEXT NOT NULL UNIQUE,name TEXT NOT NULL,type TEXT NOT NULL,isSystem INTEGER NOT NULL,isActive INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE inventory_items(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE menu_items(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL(
            """CREATE TABLE sales_invoices(
                id INTEGER PRIMARY KEY NOT NULL, invoiceNo TEXT NOT NULL, businessEpochDay INTEGER NOT NULL,
                customerId INTEGER, dueEpochDay INTEGER, creditRial INTEGER NOT NULL, status TEXT NOT NULL,
                createdByActorId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON DELETE RESTRICT
            )""",
        )
        db.execSQL(
            """CREATE TABLE sales_returns(
                id INTEGER PRIMARY KEY NOT NULL, returnNo TEXT NOT NULL, invoiceId INTEGER NOT NULL,
                returnEpochDay INTEGER NOT NULL, refundMethod TEXT NOT NULL, refundRial INTEGER NOT NULL,
                createdByActorId INTEGER NOT NULL, createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON DELETE RESTRICT
            )""",
        )
    }


    private fun assertForeignKey(
        db: SupportSQLiteDatabase,
        childTable: String,
        parentTable: String,
        fromColumn: String,
        toColumn: String,
        onDelete: String,
    ) {
        db.query("PRAGMA foreign_key_list(`$childTable`)").use { cursor ->
            val tableIndex = cursor.getColumnIndexOrThrow("table")
            val fromIndex = cursor.getColumnIndexOrThrow("from")
            val toIndex = cursor.getColumnIndexOrThrow("to")
            val onDeleteIndex = cursor.getColumnIndexOrThrow("on_delete")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(tableIndex) == parentTable &&
                    cursor.getString(fromIndex) == fromColumn &&
                    cursor.getString(toIndex) == toColumn &&
                    cursor.getString(onDeleteIndex).equals(onDelete, ignoreCase = true)
                ) {
                    found = true
                    break
                }
            }
            assertTrue("Missing FK $childTable.$fromColumn -> $parentTable.$toColumn ON DELETE $onDelete", found)
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun text(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 45 && newVersion == 46) MIGRATION_45_46.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
