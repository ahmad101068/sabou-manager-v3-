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
class InventoryMasterMigration42To43Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "inventory-master-42-43.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun preservesItemsAndLocationsWhileBackfillingStableCodes() {
        openDatabase(42).use { helper ->
            val db = helper.writableDatabase
            createVersion42Subset(db)
            db.execSQL(
                """INSERT INTO inventory_items(
                    id,name,category,unit,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,
                    recipeUnit,recipeToStockNumerator,recipeToStockDenominator,stockMicros,inventoryValueRial,
                    alertEnabled,alertThresholdMicros,supplierId,isActive,createdAtEpochMillis,updatedAtEpochMillis
                ) VALUES(7,'برنج','مواد اولیه','کیلوگرم','کیسه',10,1,'گرم',1,1000,2500000,750000,1,500000,NULL,1,10,20)""",
            )
            db.execSQL("INSERT INTO storage_locations(id,name,kind,isActive,createdAtEpochMillis) VALUES(3,'انبار اصلی','PRIMARY',1,11)")
            db.execSQL("INSERT INTO storage_locations(id,name,kind,isActive,createdAtEpochMillis) VALUES(4,'سردخانه','COLD',1,12)")
            db.execSQL(
                """INSERT INTO inventory_lots(
                    id,itemId,locationId,lotCode,receivedEpochDay,expiryEpochDay,quantityMicros,
                    unitCostRial,barcode,updatedAtEpochMillis
                ) VALUES(5,7,3,'LEGACY-LOT',90,150,1000000,300000,'626000000001',21)""",
            )
            db.execSQL("INSERT INTO app_users(id,isActive) VALUES(9,1)")
            db.execSQL(
                """INSERT INTO stock_movements(
                    id,itemId,movementType,quantityDeltaMicros,valueDeltaRial,referenceType,referenceId,
                    movementEpochDay,notes,createdAtEpochMillis,globalId,idempotencyKey,correlationId,
                    actorId,deviceId,locationId,unitCostRial,reasonCode,reversalOfMovementId
                ) VALUES(1,7,'PURCHASE',1000000,300000,'PURCHASE',1,100,'legacy receipt',1000,
                    'legacy:movement:1','legacy:movement:1','legacy:movement:1',9,'legacy-device',3,300000,
                    'PURCHASE_RECEIPT',NULL)""",
            )
            db.execSQL(
                """INSERT INTO inventory_waste_documents(
                    id,globalId,idempotencyKey,correlationId,itemId,locationId,quantityMicros,valueRial,
                    wasteEpochDay,reason,notes,actorId,deviceId,createdAtEpochMillis
                ) VALUES(2,'123e4567-e89b-42d3-a456-426614174111','legacy-waste-command-2',
                    'legacy:waste:2',7,3,100000,30000,99,'فساد قدیمی','یادداشت حفظ شود',9,
                    'legacy-device',999)""",
            )
            db.execSQL(
                """INSERT INTO stock_transfers(
                    id,transferNo,sourceLocationId,destinationLocationId,transferEpochDay,note,
                    transferredBy,createdAtEpochMillis,globalId,idempotencyKey,correlationId,actorId,deviceId
                ) VALUES(6,'TR-LEGACY-6',3,4,101,'انتقال قدیمی','انباردار',1010,
                    'legacy:stock_transfer:6','legacy:stock_transfer:6','legacy:stock_transfer:6',9,
                    'legacy-device')""",
            )
            db.execSQL(
                """INSERT INTO stock_transfer_lines(id,transferId,itemId,lotCode,quantityMicros)
                VALUES(8,6,7,'LEGACY-LOT',100000)""",
            )
        }

        openDatabase(43).use { helper ->
            val db = helper.writableDatabase
            db.query(
                """SELECT name,stockMicros,inventoryValueRial,sku,itemType,minimumStockMicros,
                    trackLot,trackExpiry FROM inventory_items WHERE id=7""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("برنج", cursor.getString(0))
                assertEquals(2_500_000L, cursor.getLong(1))
                assertEquals(750_000L, cursor.getLong(2))
                assertEquals("SKU-0000000007", cursor.getString(3))
                assertEquals("INGREDIENT", cursor.getString(4))
                assertEquals(500_000L, cursor.getLong(5))
                // Legacy lot allocation was partial; migration preserves it without falsely claiming full tracking.
                assertEquals(0, cursor.getInt(6))
                assertEquals(0, cursor.getInt(7))
            }
            db.query(
                """SELECT globalId,initialQuantityMicros,status,correlationId,createdAtEpochMillis
                FROM inventory_lots WHERE id=5""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy:inventory_lot:5", cursor.getString(0))
                assertEquals(1_000_000L, cursor.getLong(1))
                assertEquals("ACTIVE", cursor.getString(2))
                assertEquals("legacy:inventory_lot:5", cursor.getString(3))
                assertEquals(21L, cursor.getLong(4))
            }
            db.query("SELECT code,kind,updatedAtEpochMillis FROM storage_locations WHERE id=3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MAIN", cursor.getString(0))
                assertEquals("WAREHOUSE", cursor.getString(1))
                assertEquals(11L, cursor.getLong(2))
            }
            db.query("SELECT code,kind FROM storage_locations WHERE id=4").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("LOC-000004", cursor.getString(0))
                assertEquals("COLD_STORAGE", cursor.getString(1))
            }
            db.query(
                """SELECT quantityDeltaMicros,valueDeltaRial,actorId,locationId,reasonCode
                FROM stock_movements WHERE idempotencyKey='migration:42:43:opening:7'""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1_500_000L, cursor.getLong(0))
                assertEquals(450_000L, cursor.getLong(1))
                assertEquals(9L, cursor.getLong(2))
                assertEquals(3L, cursor.getLong(3))
                assertEquals("MIGRATION_OPENING_BALANCE", cursor.getString(4))
            }
            db.query("SELECT onHandMicros,inventoryValueRial FROM inventory_balances WHERE itemId=7 AND locationId=3").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2_500_000L, cursor.getLong(0))
                assertEquals(750_000L, cursor.getLong(1))
            }
            assertThrows(Exception::class.java) {
                db.execSQL(
                    """INSERT INTO inventory_items(
                        id,name,category,unit,purchaseUnit,purchaseToStockNumerator,purchaseToStockDenominator,
                        recipeUnit,recipeToStockNumerator,recipeToStockDenominator,stockMicros,inventoryValueRial,
                        alertEnabled,alertThresholdMicros,supplierId,isActive,createdAtEpochMillis,updatedAtEpochMillis,sku,itemType,
                        brand,storageCondition,trackLot,trackExpiry,minimumStockMicros,maximumStockMicros,safetyStockMicros,
                        reorderPointMicros,leadTimeDays
                    ) VALUES(8,'برنج دوم','مواد اولیه','کیلوگرم','کیلوگرم',1,1,'کیلوگرم',1,1,0,0,1,0,NULL,1,10,20,
                        'SKU-0000000007','INGREDIENT','','AMBIENT',0,0,0,0,0,0,0)""",
                )
            }
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='inventory_count_sessions'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='inventory_count_lines'"))
            db.execSQL(
                """INSERT INTO inventory_count_sessions(
                    globalId,documentNumber,idempotencyKey,postCommandId,locationId,scope,blindCount,
                    createdByActorId,assignedToActorId,status,snapshotEpochMillis,businessEpochDay,
                    startedAtEpochMillis,submittedAtEpochMillis,approvedByActorId,approvedAtEpochMillis,
                    postedByActorId,postedAtEpochMillis,cancelledAtEpochMillis,notes,correlationId,
                    createdAtEpochMillis,updatedAtEpochMillis
                ) VALUES('legacy:inventory_count_session:1','IC-MIGRATION-1','migration:count:1',NULL,3,
                    'ITEM_SELECTION',1,9,9,'DRAFT',1000,100,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'',
                    'migration:count:1',1000,1000)""",
            )
            db.execSQL(
                """INSERT INTO inventory_count_lines(
                    sessionId,itemId,lotId,lotKey,systemQuantitySnapshotMicros,systemValueSnapshotRial,
                    firstCountQuantityMicros,secondCountQuantityMicros,finalCountQuantityMicros,
                    finalCountValueRial,varianceQuantityMicros,varianceValueRial,status,reason,
                    countedByActorId,countedAtEpochMillis,updatedAtEpochMillis
                ) VALUES(1,7,NULL,0,2500000,750000,NULL,NULL,NULL,NULL,NULL,NULL,'PENDING','',NULL,NULL,1000)""",
            )
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE inventory_count_sessions SET status='POSTED' WHERE id=1")
            }
            assertEquals("DRAFT", stringScalar(db, "SELECT status FROM inventory_count_sessions WHERE id=1"))
            db.query(
                """SELECT documentNumber,postCommandId,lotId,unitCostRial,valueRial,reasonCode,reason,
                    status,postedByActorId,postedAtEpochMillis
                FROM inventory_waste_documents WHERE id=2""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("WD-LEGACY-0000000002", cursor.getString(0))
                assertEquals("123e4567-e89b-42d3-a456-426614174111", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertEquals(30_000L, cursor.getLong(4))
                assertEquals("LEGACY_UNKNOWN", cursor.getString(5))
                assertEquals("فساد قدیمی", cursor.getString(6))
                assertEquals("POSTED", cursor.getString(7))
                assertEquals(9L, cursor.getLong(8))
                assertEquals(999L, cursor.getLong(9))
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE inventory_waste_documents SET quantityMicros=1 WHERE id=2")
            }
            db.query(
                """SELECT transferNo,status,requestedByActorId,approvedByActorId,issuedByActorId,
                    receivedByActorId,actorDisplayNameSnapshot,issueCommandId,receiveCommandId
                FROM stock_transfers WHERE id=6""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TR-LEGACY-6", cursor.getString(0))
                assertEquals("COMPLETED", cursor.getString(1))
                assertEquals(9L, cursor.getLong(2))
                assertEquals(9L, cursor.getLong(3))
                assertEquals(9L, cursor.getLong(4))
                assertEquals(9L, cursor.getLong(5))
                assertEquals("انباردار", cursor.getString(6))
                assertEquals("legacy:stock_transfer:6:issue", cursor.getString(7))
                assertEquals("legacy:stock_transfer:6:receive", cursor.getString(8))
            }
            db.query(
                """SELECT lotId,lotKey,lotCodeSnapshot,requestedQuantityMicros,
                    issuedQuantityMicros,receivedQuantityMicros,varianceQuantityMicros
                FROM stock_transfer_lines WHERE id=8""",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5L, cursor.getLong(0))
                assertEquals(5L, cursor.getLong(1))
                assertEquals("LEGACY-LOT", cursor.getString(2))
                assertEquals(100_000L, cursor.getLong(3))
                assertEquals(100_000L, cursor.getLong(4))
                assertEquals(100_000L, cursor.getLong(5))
                assertEquals(0L, cursor.getLong(6))
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE stock_transfers SET note='ویرایش غیرمجاز' WHERE id=6")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM stock_transfer_lines WHERE id=8")
            }
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun stringScalar(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun createVersion42Subset(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE inventory_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                unit TEXT NOT NULL,
                purchaseUnit TEXT NOT NULL,
                purchaseToStockNumerator INTEGER NOT NULL,
                purchaseToStockDenominator INTEGER NOT NULL,
                recipeUnit TEXT NOT NULL,
                recipeToStockNumerator INTEGER NOT NULL,
                recipeToStockDenominator INTEGER NOT NULL,
                stockMicros INTEGER NOT NULL,
                inventoryValueRial INTEGER NOT NULL,
                alertEnabled INTEGER NOT NULL,
                alertThresholdMicros INTEGER NOT NULL,
                supplierId INTEGER,
                isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_inventory_items_name ON inventory_items(name)")
        db.execSQL("CREATE INDEX index_inventory_items_supplierId ON inventory_items(supplierId)")
        db.execSQL(
            """CREATE TABLE storage_locations(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_storage_locations_name ON storage_locations(name)")
        db.execSQL("CREATE INDEX index_storage_locations_isActive ON storage_locations(isActive)")
        db.execSQL(
            """CREATE TABLE inventory_lots(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                lotCode TEXT NOT NULL,
                receivedEpochDay INTEGER NOT NULL,
                expiryEpochDay INTEGER,
                quantityMicros INTEGER NOT NULL,
                unitCostRial INTEGER NOT NULL,
                barcode TEXT,
                updatedAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_inventory_lots_itemId_locationId_lotCode ON inventory_lots(itemId,locationId,lotCode)")
        db.execSQL("CREATE INDEX index_inventory_lots_barcode ON inventory_lots(barcode)")
        db.execSQL("CREATE INDEX index_inventory_lots_locationId ON inventory_lots(locationId)")
        db.execSQL("CREATE INDEX index_inventory_lots_expiryEpochDay ON inventory_lots(expiryEpochDay)")
        db.execSQL(
            """CREATE TABLE inventory_lot_consumptions(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                stockMovementId INTEGER NOT NULL,
                lotId INTEGER NOT NULL,
                quantityMicros INTEGER NOT NULL,
                reversedQuantityMicros INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX index_inventory_lot_consumptions_stockMovementId ON inventory_lot_consumptions(stockMovementId)")
        db.execSQL("CREATE INDEX index_inventory_lot_consumptions_lotId ON inventory_lot_consumptions(lotId)")
        db.execSQL("CREATE TABLE goods_receipts(id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL(
            """CREATE TABLE goods_receipt_lines(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                goodsReceiptId INTEGER NOT NULL,
                purchaseOrderLineId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                deliveredQtyMicros INTEGER NOT NULL,
                acceptedQtyMicros INTEGER NOT NULL,
                rejectedQtyMicros INTEGER NOT NULL,
                rejectionReason TEXT NOT NULL,
                acceptedValueRial INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE TABLE app_users(id INTEGER PRIMARY KEY NOT NULL,isActive INTEGER NOT NULL)")
        db.execSQL(
            """CREATE TABLE stock_movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                movementType TEXT NOT NULL,
                quantityDeltaMicros INTEGER NOT NULL,
                valueDeltaRial INTEGER NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId INTEGER NOT NULL,
                movementEpochDay INTEGER NOT NULL,
                notes TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                globalId TEXT NOT NULL,
                idempotencyKey TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                actorId INTEGER,
                deviceId TEXT NOT NULL,
                locationId INTEGER,
                unitCostRial INTEGER NOT NULL,
                reasonCode TEXT NOT NULL,
                reversalOfMovementId INTEGER
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_stock_movements_globalId ON stock_movements(globalId)")
        db.execSQL("CREATE UNIQUE INDEX index_stock_movements_idempotencyKey ON stock_movements(idempotencyKey)")
        db.execSQL("CREATE UNIQUE INDEX index_stock_movements_reversalOfMovementId ON stock_movements(reversalOfMovementId)")
        db.execSQL(
            """CREATE TABLE inventory_waste_documents(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                globalId TEXT NOT NULL DEFAULT '',
                idempotencyKey TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                quantityMicros INTEGER NOT NULL,
                valueRial INTEGER NOT NULL,
                wasteEpochDay INTEGER NOT NULL,
                reason TEXT NOT NULL,
                notes TEXT NOT NULL,
                actorId INTEGER NOT NULL,
                deviceId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_globalId ON inventory_waste_documents(globalId)")
        db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_idempotencyKey ON inventory_waste_documents(idempotencyKey)")
        db.execSQL("CREATE INDEX index_inventory_waste_documents_itemId ON inventory_waste_documents(itemId)")
        db.execSQL("CREATE INDEX index_inventory_waste_documents_locationId ON inventory_waste_documents(locationId)")
        db.execSQL("CREATE INDEX index_inventory_waste_documents_wasteEpochDay ON inventory_waste_documents(wasteEpochDay)")
        db.execSQL("CREATE INDEX index_inventory_waste_documents_actorId ON inventory_waste_documents(actorId)")
        db.execSQL("CREATE INDEX index_inventory_waste_documents_correlationId ON inventory_waste_documents(correlationId)")
        db.execSQL(
            """CREATE TABLE stock_transfers(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transferNo TEXT NOT NULL,
                sourceLocationId INTEGER NOT NULL,
                destinationLocationId INTEGER NOT NULL,
                transferEpochDay INTEGER NOT NULL,
                note TEXT NOT NULL,
                transferredBy TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                globalId TEXT NOT NULL DEFAULT '',
                idempotencyKey TEXT NOT NULL DEFAULT '',
                correlationId TEXT NOT NULL DEFAULT '',
                actorId INTEGER,
                deviceId TEXT NOT NULL DEFAULT 'legacy-unknown'
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_transferNo ON stock_transfers(transferNo)")
        db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_globalId ON stock_transfers(globalId)")
        db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_idempotencyKey ON stock_transfers(idempotencyKey)")
        db.execSQL("CREATE INDEX index_stock_transfers_sourceLocationId ON stock_transfers(sourceLocationId)")
        db.execSQL("CREATE INDEX index_stock_transfers_destinationLocationId ON stock_transfers(destinationLocationId)")
        db.execSQL("CREATE INDEX index_stock_transfers_transferEpochDay ON stock_transfers(transferEpochDay)")
        db.execSQL("CREATE INDEX index_stock_transfers_actorId ON stock_transfers(actorId)")
        db.execSQL("CREATE INDEX index_stock_transfers_correlationId ON stock_transfers(correlationId)")
        db.execSQL(
            """CREATE TABLE stock_transfer_lines(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transferId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotCode TEXT NOT NULL,
                quantityMicros INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX index_stock_transfer_lines_transferId ON stock_transfer_lines(transferId)")
        db.execSQL("CREATE INDEX index_stock_transfer_lines_itemId ON stock_transfer_lines(itemId)")
        db.execSQL(
            """CREATE TABLE inventory_period_closures(
                id INTEGER PRIMARY KEY NOT NULL,
                fromEpochDay INTEGER NOT NULL,
                toEpochDay INTEGER NOT NULL,
                status TEXT NOT NULL
            )""",
        )
    }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 42 && newVersion == 43) MIGRATION_42_43.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
