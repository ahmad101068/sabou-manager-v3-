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
class FullCostSnapshotMigration39To40Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "full-cost-39-40.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun preservesLegacyLineAndMarksSnapshotsUnavailable() {
        open(39).use { helper ->
            helper.writableDatabase.execSQL(
                """CREATE TABLE daily_sales_menu_lines(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, summaryId INTEGER NOT NULL,
                    menuItemId INTEGER, recipeVersionId INTEGER, menuItemNameSnapshot TEXT NOT NULL,
                    quantityMicros INTEGER NOT NULL, grossSalesRial INTEGER NOT NULL,
                    theoreticalCostRial INTEGER NOT NULL)""",
            )
            helper.writableDatabase.execSQL("INSERT INTO daily_sales_menu_lines VALUES(1,2,3,4,'قهوه',1000000,500000,150000)")
        }
        open(40).use { helper ->
            helper.writableDatabase.query(
                "SELECT theoreticalCostRial,foodCostSnapshotRial,packagingCostSnapshotRial,directLaborCostSnapshotRial,allocatedOverheadSnapshotRial FROM daily_sales_menu_lines WHERE id=1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(150000L, cursor.getLong(0))
                (1..4).forEach { assertTrue(cursor.isNull(it)) }
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 39 && newVersion == 40) MIGRATION_39_40.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build(),
        )
    }
}
