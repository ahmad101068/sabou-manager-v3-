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
class ManagementControlMigration29To30Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "management-control-migration-29-30.db"
    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun createsOfflineControlTablesAndDefaultPolicy() {
        open(29).use { it.writableDatabase }
        open(30).use { helper ->
            helper.writableDatabase.query("SELECT COUNT(*) FROM labor_policy WHERE singletonId = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            helper.writableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('inventory_lots','operating_budgets','shift_swap_requests','purchase_order_follow_ups')").use { cursor ->
                cursor.moveToFirst()
                assertEquals(4, cursor.getInt(0))
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 29 && newVersion == 30) MIGRATION_29_30.migrate(db)
            }
        }
        return FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build())
    }
}
