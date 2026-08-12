#!/usr/bin/env python3
from pathlib import Path
import re


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_test_body(path, test_name, replacement):
    text = read(path)
    pattern = rf'''    @Test(?:\s+)?(?:fun|\n    fun) {re.escape(test_name)}\(\) \{{.*?\n    \}}\n\n    private fun open\(version: Int\): SupportSQLiteOpenHelper'''
    new_text, count = re.subn(
        pattern,
        replacement + "\n\n    private fun open(version: Int): SupportSQLiteOpenHelper",
        text,
        count=1,
        flags=re.S,
    )
    if count != 1:
        raise SystemExit(f"FIX13_TEST_REWRITE_FAIL:{path}:{test_name}:count={count}")
    write(path, new_text)


CAT = "app/src/androidTest/java/ir/sabou/inventory/data/db/SupplierCatalogMigration26To27Test.kt"
PERF = "app/src/androidTest/java/ir/sabou/inventory/data/db/SupplierPerformanceMigration24To25Test.kt"
SPLIT = "app/src/androidTest/java/ir/sabou/inventory/data/db/SupplierSplitMigration27To28Test.kt"

catalog = r'''    @Test
    fun addsCatalogAndKeepsExistingData() {
        val helper = open(26)
        try {
            val db = helper.writableDatabase
            createCatalogFixture(db)
            migrateCatalogTo27(db)
            assertCatalogVersionAndMarker(db)
            assertCatalogColumns(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createCatalogFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("INSERT INTO marker VALUES (1, 'محفوظ')")
    }

    private fun migrateCatalogTo27(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_26_27.migrate(db)
            db.version = 27
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertCatalogVersionAndMarker(db: SupportSQLiteDatabase) {
        assertEquals(27, db.version)
        val cursor = db.query("SELECT value FROM marker")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("محفوظ", cursor.getString(0))
        } finally {
            cursor.close()
        }
    }

    private fun assertCatalogColumns(db: SupportSQLiteDatabase) {
        val expected = mutableSetOf(
            "supplierId",
            "itemId",
            "unitCostRial",
            "minimumOrderMicros",
            "orderMultipleMicros",
            "leadTimeDays",
            "validUntilEpochDay",
        )
        val cursor = db.query("PRAGMA table_info(supplier_item_offers)")
        try {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) expected.remove(cursor.getString(nameIndex))
        } finally {
            cursor.close()
        }
        assertTrue("missing supplier catalog columns: $expected", expected.isEmpty())
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA foreign_key_check")
        try {
            assertEquals(0, cursor.count)
        } finally {
            cursor.close()
        }
    }'''
replace_test_body(CAT, "addsCatalogAndKeepsExistingData", catalog)

performance = r'''    @Test
    fun addsReturnsAndPreservesExistingOrderLine() {
        val helper = open(24)
        try {
            val db = helper.writableDatabase
            createPerformanceFixture(db)
            migratePerformanceTo25(db)
            assertPerformanceVersionAndOrderLine(db)
            assertPerformanceTables(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createPerformanceFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE purchase_orders (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("INSERT INTO purchase_orders(id) VALUES(1)")
        db.execSQL("INSERT INTO suppliers(id) VALUES(1)")
        db.execSQL("INSERT INTO inventory_items(id) VALUES(2)")
        db.execSQL("CREATE TABLE purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseOrderId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, orderedQtyMicros INTEGER NOT NULL, unitCostRial INTEGER NOT NULL, receivedQtyMicros INTEGER NOT NULL, rejectedQtyMicros INTEGER NOT NULL)")
        db.execSQL("INSERT INTO purchase_order_lines(purchaseOrderId,itemId,itemNameSnapshot,orderedQtyMicros,unitCostRial,receivedQtyMicros,rejectedQtyMicros) VALUES (1,2,'قهوه',1000000,500000,1000000,0)")
    }

    private fun migratePerformanceTo25(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_24_25.migrate(db)
            db.version = 25
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertPerformanceVersionAndOrderLine(db: SupportSQLiteDatabase) {
        assertEquals(25, db.version)
        val cursor = db.query("SELECT itemNameSnapshot,returnedQtyMicros FROM purchase_order_lines")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("قهوه", cursor.getString(0))
            assertEquals(0L, cursor.getLong(1))
        } finally {
            cursor.close()
        }
    }

    private fun assertPerformanceTables(db: SupportSQLiteDatabase) {
        assertTableReadable(db, "purchase_returns")
        assertTableReadable(db, "purchase_return_lines")
        assertTableReadable(db, "supplier_credits")
    }

    private fun assertTableReadable(db: SupportSQLiteDatabase, table: String) {
        val cursor = db.query("SELECT COUNT(*) FROM $table")
        try {
            assertTrue(cursor.moveToFirst())
        } finally {
            cursor.close()
        }
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA foreign_key_check")
        try {
            assertEquals(0, cursor.count)
        } finally {
            cursor.close()
        }
    }'''
replace_test_body(PERF, "addsReturnsAndPreservesExistingOrderLine", performance)

split = r'''    @Test
    fun addsAssignmentsAndKeepsExistingRequestLine() {
        val helper = open(27)
        try {
            val db = helper.writableDatabase
            createSplitFixture(db)
            migrateSplitTo28(db)
            assertSplitRequestLine(db)
            assertSplitOrderLineColumn(db)
            assertNoForeignKeyViolations(db)
        } finally {
            helper.close()
        }
    }

    private fun createSplitFixture(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE purchase_requisition_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requisitionId INTEGER NOT NULL, itemId INTEGER NOT NULL, itemNameSnapshot TEXT NOT NULL, requestedQtyMicros INTEGER NOT NULL, estimatedUnitCostRial INTEGER NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE purchase_order_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        db.execSQL("INSERT INTO purchase_requisition_lines(requisitionId,itemId,itemNameSnapshot,requestedQtyMicros,estimatedUnitCostRial,note) VALUES(1,2,'قهوه',1000000,100000,'قدیمی')")
    }

    private fun migrateSplitTo28(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            MIGRATION_27_28.migrate(db)
            db.version = 28
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun assertSplitRequestLine(db: SupportSQLiteDatabase) {
        assertEquals(28, db.version)
        val cursor = db.query("SELECT itemNameSnapshot, recommendedSupplierId, supplierSkuSnapshot, recommendedLeadTimeDays FROM purchase_requisition_lines")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals("قهوه", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        } finally {
            cursor.close()
        }
    }

    private fun assertSplitOrderLineColumn(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT supplierSkuSnapshot FROM purchase_order_lines")
        try {
            assertEquals(1, cursor.columnCount)
        } finally {
            cursor.close()
        }
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA foreign_key_check")
        try {
            assertEquals(0, cursor.count)
        } finally {
            cursor.close()
        }
    }'''
replace_test_body(SPLIT, "addsAssignmentsAndKeepsExistingRequestLine", split)

checks = [
    (CAT, "private fun assertCatalogColumns"),
    (PERF, "private fun assertPerformanceTables"),
    (SPLIT, "private fun migrateSplitTo28"),
]
for path, needle in checks:
    if needle not in read(path):
        raise SystemExit(f"FIX13_VERIFY_FAIL:{path}:{needle}")
print("DASHBOARD_UX2_FIX13_API23_SUPPLIER_MIGRATIONS=PASS catalog=1 performance=1 split=1")
