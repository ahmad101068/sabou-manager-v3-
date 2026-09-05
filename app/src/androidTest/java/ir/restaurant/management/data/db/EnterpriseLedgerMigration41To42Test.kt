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
class EnterpriseLedgerMigration41To42Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "enterprise-ledger-41-42.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun backfillsTraceabilityAndInstallsAppendOnlyGuards() {
        openDatabase(41).use { helper ->
            val db = helper.writableDatabase
            createVersion41Subset(db)
            db.execSQL("INSERT INTO storage_locations VALUES(1,'سردخانه','COLD',1,1)")
            db.execSQL("INSERT INTO inventory_items VALUES(1)")
            db.execSQL("INSERT INTO journal_entries VALUES(1,'POSTED',10)")
            db.execSQL("INSERT INTO journal_lines VALUES(1,1,100,0)")
            db.execSQL("INSERT INTO stock_movements VALUES(1,1,'PURCHASE',1000000,100000,'PURCHASE',5,100,'legacy',10)")
            db.execSQL("INSERT INTO inventory_counts VALUES(1,1,1000000,900000,100000,90000,100,'شمارش قدیمی',10)")
            db.execSQL("INSERT INTO stock_transfers VALUES(1,'TR-1',1,2,100,'legacy','legacy actor',10)")
            db.execSQL("INSERT INTO audit_logs VALUES(1)")
            db.execSQL("INSERT INTO performance_goals VALUES(1,1.25)")
            db.execSQL("INSERT INTO performance_scores VALUES(1,2.5)")
            db.execSQL("INSERT INTO purchase_requisitions VALUES(1)")
            db.execSQL("INSERT INTO payroll_runs VALUES(1,'POSTED',10)")
        }

        openDatabase(42).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT globalId,idempotencyKey,correlationId,locationId FROM stock_movements WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy:stock_movement:1", cursor.getString(0))
                assertEquals("legacy:stock:1", cursor.getString(1))
                assertEquals("legacy:stock:1", cursor.getString(2))
                assertEquals(2L, cursor.getLong(3))
            }
            db.query("SELECT targetValueMicros FROM performance_goals WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1_250_000L, cursor.getLong(0))
            }
            db.query("SELECT globalId,idempotencyKey,correlationId,locationId FROM inventory_counts WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy:inventory_count:1", cursor.getString(0))
                assertEquals("legacy:inventory_count:1", cursor.getString(1))
                assertEquals("legacy:inventory_count:1", cursor.getString(2))
                assertEquals(2L, cursor.getLong(3))
            }
            db.query("SELECT achievedValueMicros FROM performance_scores WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2_500_000L, cursor.getLong(0))
            }
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='inventory_waste_documents'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT globalId,idempotencyKey,correlationId FROM stock_transfers WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy:stock_transfer:1", cursor.getString(0))
                assertEquals("legacy:stock_transfer:1", cursor.getString(1))
                assertEquals("legacy:stock_transfer:1", cursor.getString(2))
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE journal_entries SET status='DRAFT' WHERE id=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM journal_lines WHERE id=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE stock_movements SET notes='tampered' WHERE id=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM inventory_counts WHERE id=1")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM audit_logs WHERE id=1")
            }
        }
    }

    private fun createVersion41Subset(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE storage_locations(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, kind TEXT NOT NULL, isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_storage_locations_name ON storage_locations(name)")
        db.execSQL("CREATE TABLE inventory_items(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL(
            """CREATE TABLE journal_entries(
                id INTEGER PRIMARY KEY NOT NULL,
                status TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE journal_lines(
                id INTEGER PRIMARY KEY NOT NULL,
                entryId INTEGER NOT NULL,
                debitRial INTEGER NOT NULL,
                creditRial INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE stock_movements(
                id INTEGER PRIMARY KEY NOT NULL,
                itemId INTEGER NOT NULL,
                movementType TEXT NOT NULL,
                quantityDeltaMicros INTEGER NOT NULL,
                valueDeltaRial INTEGER NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId INTEGER NOT NULL,
                movementEpochDay INTEGER NOT NULL,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE inventory_counts(
                id INTEGER PRIMARY KEY NOT NULL,
                itemId INTEGER NOT NULL,
                previousQuantityMicros INTEGER NOT NULL,
                countedQuantityMicros INTEGER NOT NULL,
                previousValueRial INTEGER NOT NULL,
                countedValueRial INTEGER NOT NULL,
                countEpochDay INTEGER NOT NULL,
                reason TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE TABLE audit_logs(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL(
            """CREATE TABLE stock_transfers(
                id INTEGER PRIMARY KEY NOT NULL,
                transferNo TEXT NOT NULL,
                sourceLocationId INTEGER NOT NULL,
                destinationLocationId INTEGER NOT NULL,
                transferEpochDay INTEGER NOT NULL,
                note TEXT NOT NULL,
                transferredBy TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE stock_transfer_lines(
                id INTEGER PRIMARY KEY NOT NULL,
                transferId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotCode TEXT NOT NULL,
                quantityMicros INTEGER NOT NULL)""",
        )
        db.execSQL("CREATE TABLE performance_goals(id INTEGER PRIMARY KEY NOT NULL,targetValue REAL)")
        db.execSQL("CREATE TABLE performance_scores(id INTEGER PRIMARY KEY NOT NULL,achievedValue REAL)")
        db.execSQL("CREATE TABLE purchase_requisitions(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE payroll_runs(id INTEGER PRIMARY KEY NOT NULL,status TEXT NOT NULL,createdAtEpochMillis INTEGER NOT NULL)")
    }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 41 && newVersion == 42) MIGRATION_41_42.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
