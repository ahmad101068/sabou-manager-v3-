package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesHistoryMigration44To45Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "sales-history-44-45.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test
    fun createsSalesHistoryLedgerNumberingAndImmutableFacts() {
        openDatabase(44).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE sales_day_closures(businessEpochDay INTEGER PRIMARY KEY NOT NULL,status TEXT NOT NULL)")
            db.execSQL("CREATE TABLE stock_movements(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,referenceType TEXT NOT NULL,movementEpochDay INTEGER NOT NULL)")
            // Minimal v44 document sources required to prove sequence backfill with existing data.
            db.execSQL("CREATE TABLE purchases(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,invoiceNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE purchase_requisitions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,requestNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE purchase_orders(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,orderNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE goods_receipts(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,receiptNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE purchase_returns(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,returnNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE stock_transfers(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,transferNo TEXT NOT NULL)")
            db.execSQL("CREATE TABLE fixed_assets(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,assetCode TEXT NOT NULL)")
            db.execSQL("CREATE TABLE employees(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,employeeCode TEXT NOT NULL)")
            db.execSQL("INSERT INTO purchases(invoiceNo) VALUES('PUR-00000041')")
            db.execSQL("INSERT INTO purchase_requisitions(requestNo) VALUES('PR-00000007')")
            db.execSQL("INSERT INTO purchase_orders(orderNo) VALUES('PO-00000009')")
            db.execSQL("INSERT INTO goods_receipts(receiptNo) VALUES('GR-00000013')")
            db.execSQL("INSERT INTO purchase_returns(returnNo) VALUES('PRT-00000003')")
            db.execSQL("INSERT INTO stock_transfers(transferNo) VALUES('TRF-00000005')")
            db.execSQL("INSERT INTO fixed_assets(assetCode) VALUES('AST-00000021')")
            db.execSQL("INSERT INTO employees(employeeCode) VALUES('EMP-00000102')")
        }

        openDatabase(45).use { helper ->
            val db = helper.writableDatabase
            listOf(
                "document_sequences", "customers", "sales_invoices", "sales_invoice_lines",
                "sales_payments", "sales_consumption_snapshots", "sales_returns", "sales_return_lines",
                "sales_holds", "sales_hold_lines", "sales_pos_day_closures",
            ).forEach { table ->
                assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'"))
            }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_sales_invoices_invoiceNo'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_sales_invoice_financial_identity_immutable'"))

            assertEquals(1L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='sales_invoice'"))
            db.execSQL("UPDATE document_sequences SET nextValue=2,updatedAtEpochMillis=2 WHERE sequenceKey='sales_invoice' AND nextValue=1")
            assertEquals(2L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='sales_invoice'"))
            assertEquals(42L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='purchase_invoice'"))
            assertEquals(8L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='purchase_requisition'"))
            assertEquals(10L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='purchase_order'"))
            assertEquals(14L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='goods_receipt'"))
            assertEquals(4L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='purchase_return'"))
            assertEquals(6L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='inventory_transfer'"))
            assertEquals(22L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='fixed_asset'"))
            assertEquals(103L, scalar(db, "SELECT nextValue FROM document_sequences WHERE sequenceKey='employee'"))

            db.execSQL("INSERT INTO customers(id,customerCode,name,phone,nationalId,creditLimitRial,notes,isActive,createdAtEpochMillis,updatedAtEpochMillis) VALUES(1,'CUS-00000001','مشتری','','',0,'',1,1,1)")
            db.execSQL("""INSERT INTO sales_invoices(
                id,invoiceNo,commandId,businessEpochDay,customerId,dueEpochDay,grossRial,discountRial,serviceRial,taxRial,
                netRial,creditRial,theoreticalCostRial,journalEntryId,cogsJournalEntryId,status,notes,createdByActorId,
                createdAtEpochMillis,voidedAtEpochDay,voidCommandId,voidReason,voidJournalEntryId,voidCogsJournalEntryId
            ) VALUES(1,'SAL-00000001','00000000-0000-0000-0000-000000000001',20000,1,NULL,1000,0,0,0,1000,0,400,NULL,NULL,'POSTED','',1,1,NULL,NULL,'',NULL,NULL)""")

            db.execSQL("UPDATE sales_invoices SET journalEntryId=10,cogsJournalEntryId=11 WHERE id=1")
            assertEquals(10L, scalar(db, "SELECT journalEntryId FROM sales_invoices WHERE id=1"))
            assertThrows(Exception::class.java) { db.execSQL("UPDATE sales_invoices SET netRial=999 WHERE id=1") }
            assertThrows(Exception::class.java) { db.execSQL("DELETE FROM sales_invoices WHERE id=1") }
            db.execSQL("INSERT INTO sales_pos_day_closures(businessEpochDay,grossSalesRial,netSalesRial,returnRial,cogsRial,cashRial,cardRial,transferRial,creditRial,invoiceCount,returnCount,status,revisionNo,closedByActorId,closedByName,note,reopenedByActorId,reopenedByName,reopenReason,reopenedAtEpochMillis,createdAtEpochMillis) VALUES(20001,0,0,0,0,0,0,0,0,0,0,'CLOSED',1,1,'owner','',NULL,NULL,'',NULL,2)")
            assertThrows(Exception::class.java) {
                db.execSQL("""INSERT INTO sales_invoices(
                    id,invoiceNo,commandId,businessEpochDay,customerId,dueEpochDay,grossRial,discountRial,serviceRial,taxRial,
                    netRial,creditRial,theoreticalCostRial,journalEntryId,cogsJournalEntryId,status,notes,createdByActorId,createdAtEpochMillis,
                    voidedAtEpochDay,voidCommandId,voidReason,voidJournalEntryId,voidCogsJournalEntryId
                ) VALUES(2,'SAL-00000002','00000000-0000-0000-0000-000000000002',20001,NULL,NULL,1000,0,0,0,1000,0,400,NULL,NULL,'POSTED','',1,2,NULL,NULL,'',NULL,NULL)""")
            }
            db.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            assertTrue(scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'trg_sales_%'") >= 10)
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 44 && newVersion == 45) MIGRATION_44_45.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
