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
class AssetQuantityMigration20To21Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "asset-quantity-migration-20-21.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun addsQuantityWithoutLosingExistingAsset() {
        open(20).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE fixed_assets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            db.execSQL("INSERT INTO fixed_assets(name) VALUES ('میز تست')")
        }

        open(21).use { helper ->
            helper.writableDatabase.query("SELECT name, quantity FROM fixed_assets").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("میز تست", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 20 && newVersion == 21) MIGRATION_20_21.migrate(db)
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
