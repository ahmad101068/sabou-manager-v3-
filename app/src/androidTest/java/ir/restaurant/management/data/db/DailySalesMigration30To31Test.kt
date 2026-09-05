package ir.restaurant.management.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySalesMigration30To31Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "daily-sales-migration-30-31.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun archivesActiveSalesAndRemovesCustomerFacingTables() {
        openDatabase(30).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("""CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, saleEpochDay INTEGER NOT NULL, subtotalRial INTEGER NOT NULL, discountRial INTEGER NOT NULL, deliveryRial INTEGER NOT NULL, totalRial INTEGER NOT NULL, paymentMethod TEXT, paidRial INTEGER NOT NULL, reversedAtEpochDay INTEGER, createdAtEpochMillis INTEGER NOT NULL)""")
            db.execSQL("""CREATE TABLE sale_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, saleId INTEGER NOT NULL, menuItemId INTEGER, productNameSnapshot TEXT NOT NULL, quantityMicros INTEGER NOT NULL, lineTotalRial INTEGER NOT NULL, costOfGoodsRial INTEGER NOT NULL)""")
            db.execSQL("CREATE TABLE journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, description TEXT NOT NULL, sourceType TEXT NOT NULL)")
            db.execSQL("CREATE TABLE app_alerts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceType TEXT NOT NULL)")
            db.execSQL("CREATE TABLE sync_changes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL)")
            db.execSQL("CREATE TABLE audit_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL)")
            db.execSQL("""INSERT INTO sales(id,saleEpochDay,subtotalRial,discountRial,deliveryRial,totalRial,paymentMethod,paidRial,reversedAtEpochDay,createdAtEpochMillis) VALUES(1,20000,100000,10000,5000,95000,'نقدی',95000,NULL,1234)""")
            db.execSQL("""INSERT INTO sales(id,saleEpochDay,subtotalRial,discountRial,deliveryRial,totalRial,paymentMethod,paidRial,reversedAtEpochDay,createdAtEpochMillis) VALUES(2,20000,50000,0,0,50000,'نقدی',50000,20001,1235)""")
            db.execSQL("""INSERT INTO sale_lines(saleId,menuItemId,productNameSnapshot,quantityMicros,lineTotalRial,costOfGoodsRial) VALUES(1,7,'کباب تست',2000000,100000,40000)""")
            db.execSQL("INSERT INTO journal_entries(description,sourceType) VALUES('فروش قدیمی','SALE')")
            db.execSQL("INSERT INTO app_alerts(sourceType) VALUES('CRM_FOLLOW_UP')")
            db.execSQL("INSERT INTO sync_changes(entityType) VALUES('CUSTOMER')")
            db.execSQL("INSERT INTO audit_logs(entityType) VALUES('TABLE_ORDER')")
        }

        openDatabase(31).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT grossSalesRial, discountRial, serviceRial, netSalesRial, cashRial, theoreticalCostRial, isLegacyArchive FROM daily_sales_summaries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(100000L, cursor.getLong(0))
                assertEquals(10000L, cursor.getLong(1))
                assertEquals(5000L, cursor.getLong(2))
                assertEquals(95000L, cursor.getLong(3))
                assertEquals(95000L, cursor.getLong(4))
                assertEquals(40000L, cursor.getLong(5))
                assertEquals(1, cursor.getInt(6))
                assertFalse(cursor.moveToNext())
            }
            db.query("SELECT menuItemId, menuItemNameSnapshot, quantityMicros FROM daily_sales_menu_lines").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7L, cursor.getLong(0))
                assertEquals("کباب تست", cursor.getString(1))
                assertEquals(2000000L, cursor.getLong(2))
            }
            db.query("SELECT description, sourceType FROM journal_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("فروش آرشیوی نسخه‌های قبل", cursor.getString(0))
                assertEquals("LEGACY_SALE", cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('sales','sale_lines','customers','restaurant_tables','kitchen_tickets')").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM app_alerts").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
            db.query("SELECT COUNT(*) FROM sync_changes").use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
            db.query("SELECT entityType FROM audit_logs").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TABLE_ORDER", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun openDatabase(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 30 && newVersion == 31) MIGRATION_30_31.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
    }
}
