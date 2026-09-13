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
class SyncMigration17To18Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "sync-migration-17-18.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun addsVersionedPayloadColumnsAndQuarantinesLegacyPendingRows() {
        open(version = 17, createLegacy = true).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO sync_changes(changeId, entityType, entityId, changeType, deviceId, occurredAtEpochMillis, payloadHash, state, lastError) VALUES ('c1', 'SALE', 7, 'CREATE', 'd1', 100, 'legacy-hash', 'PENDING', '')",
            )
        }

        open(version = 18, createLegacy = false).use { helper ->
            val database = helper.writableDatabase
            database.query("SELECT revision, payloadVersion, payload, payloadHash, state, lastError FROM sync_changes WHERE changeId = 'c1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("legacy", cursor.getString(2))
                assertEquals(64, cursor.getString(3).length)
                assertEquals("REJECTED", cursor.getString(4))
                assertTrue(cursor.getString(5).contains("Legacy payload"))
            }
        }
    }

    private fun open(version: Int, createLegacy: Boolean): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (createLegacy) {
                    db.execSQL("CREATE TABLE sync_changes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, changeId TEXT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, changeType TEXT NOT NULL, deviceId TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, payloadHash TEXT NOT NULL, state TEXT NOT NULL, lastError TEXT NOT NULL)")
                }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 17 && newVersion == 18) MIGRATION_17_18.migrate(db)
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
