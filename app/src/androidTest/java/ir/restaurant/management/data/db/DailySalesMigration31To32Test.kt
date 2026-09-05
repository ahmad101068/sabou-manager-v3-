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
class DailySalesMigration31To32Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "daily-sales-migration-31-32.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun addsReversalAuditFieldsAndAllowsReplacementForAReversedDay() {
        open(31).use { helper ->
            val db = helper.writableDatabase
            db.execSQL(
                """CREATE TABLE daily_sales_summaries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    businessEpochDay INTEGER NOT NULL,
                    grossSalesRial INTEGER NOT NULL,
                    discountRial INTEGER NOT NULL,
                    serviceRial INTEGER NOT NULL,
                    taxRial INTEGER NOT NULL,
                    netSalesRial INTEGER NOT NULL,
                    theoreticalCostRial INTEGER NOT NULL,
                    cashRial INTEGER NOT NULL,
                    cardRial INTEGER NOT NULL,
                    transferRial INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    journalEntryId INTEGER,
                    costJournalEntryId INTEGER,
                    isLegacyArchive INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL
                )""",
            )
            db.execSQL("CREATE UNIQUE INDEX index_daily_sales_summaries_businessEpochDay ON daily_sales_summaries(businessEpochDay)")
            insertSummary(db, 1, 21000)
        }

        open(32).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("UPDATE daily_sales_summaries SET reversedAtEpochDay=21001, reversalReason='اصلاح مبلغ' WHERE id=1")
            insertSummary(db, 2, 21000)

            db.query("SELECT reversedAtEpochDay, reversalReason, reversalJournalEntryId, reversalCostJournalEntryId FROM daily_sales_summaries WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(21001L, cursor.getLong(0))
                assertEquals("اصلاح مبلغ", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM daily_sales_summaries WHERE businessEpochDay=21000").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            db.query("PRAGMA index_list('daily_sales_summaries')").use { cursor ->
                var businessDayIndexIsNonUnique = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "index_daily_sales_summaries_businessEpochDay") {
                        businessDayIndexIsNonUnique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 0
                    }
                }
                assertTrue(businessDayIndexIsNonUnique)
            }
        }
    }

    private fun insertSummary(db: SupportSQLiteDatabase, id: Long, epochDay: Long) {
        db.execSQL(
            """INSERT INTO daily_sales_summaries(
                id,businessEpochDay,grossSalesRial,discountRial,serviceRial,taxRial,netSalesRial,
                theoreticalCostRial,cashRial,cardRial,transferRial,notes,journalEntryId,costJournalEntryId,
                isLegacyArchive,createdAtEpochMillis
            ) VALUES(?,?,1000,0,0,0,1000,400,1000,0,0,'',NULL,NULL,0,1000)""",
            arrayOf(id, epochDay),
        )
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 31 && newVersion == 32) MIGRATION_31_32.migrate(db)
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
