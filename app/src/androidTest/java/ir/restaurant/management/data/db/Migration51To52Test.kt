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
class Migration51To52Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration-51-52.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun repairsLegacyNetSalesAndMakesClosureIdentityBranchSafe() {
        open(51).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE daily_sales_summaries(id INTEGER PRIMARY KEY NOT NULL, grossSalesRial INTEGER NOT NULL, discountRial INTEGER NOT NULL, returnRial INTEGER NOT NULL, netSalesRial INTEGER NOT NULL, serviceRial INTEGER NOT NULL, taxRial INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE sales_day_closures(businessEpochDay INTEGER PRIMARY KEY NOT NULL, summaryId INTEGER NOT NULL, grossSalesRial INTEGER NOT NULL, netSalesRial INTEGER NOT NULL, theoreticalCostRial INTEGER NOT NULL, cashRial INTEGER NOT NULL, cardRial INTEGER NOT NULL, transferRial INTEGER NOT NULL, status TEXT NOT NULL, revisionNo INTEGER NOT NULL, closedBy TEXT NOT NULL, note TEXT NOT NULL, reopenedBy TEXT, reopenReason TEXT NOT NULL, reopenedAtEpochMillis INTEGER, createdAtEpochMillis INTEGER NOT NULL)")
            db.execSQL("INSERT INTO daily_sales_summaries VALUES(1,100,10,0,105,5,10)")
            db.execSQL("INSERT INTO daily_sales_summaries VALUES(2,90,10,0,80,5,10)")
            db.execSQL("INSERT INTO sales_day_closures VALUES(20000,1,100,105,40,105,0,0,'CLOSED',1,'owner','legacy',NULL,'',NULL,1)")
        }

        open(52).use { helper ->
            val db = helper.writableDatabase
            assertEquals(90L, scalar(db, "SELECT netSalesRial FROM daily_sales_summaries WHERE id=1"))
            assertEquals(80L, scalar(db, "SELECT netSalesRial FROM daily_sales_summaries WHERE id=2"))
            assertEquals(90L, scalar(db, "SELECT netSalesRial FROM sales_day_closures WHERE summaryId=1"))
            db.execSQL("INSERT INTO sales_day_closures VALUES(20000,2,90,80,30,80,0,0,'CLOSED',1,'owner','branch-b',NULL,'',NULL,2)")
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM sales_day_closures WHERE businessEpochDay=20000"))
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 51 && newVersion == 52) MIGRATION_51_52.migrate(db)
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
}
