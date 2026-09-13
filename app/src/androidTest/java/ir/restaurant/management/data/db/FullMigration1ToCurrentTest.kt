package ir.restaurant.management.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds the real exported version-1 schema, runs every production migration,
 * and lets Room validate the result against the current entity model.
 */
@RunWith(AndroidJUnit4::class)
class FullMigration1ToCurrentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schemaAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val databaseName = "full-migration-1-current.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesVersionOneWithoutDestructiveFallback() {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertEquals(APP_DATABASE_SCHEMA_VERSION, sqlite.version)
            sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("مهاجرت نباید کلید خارجی شکسته بسازد.", 0, cursor.count)
            }
            sqlite.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='accounting_period_locks'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            sqlite.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='branch_legacy_aliases'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("نسخه جاری باید compatibility read-model پایدار شعبه را داشته باشد.", 1, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    private fun createVersionOneDatabase() {
        val root = schemaAssets
            .open("ir.restaurant.management.data.db.AppDatabase/1.json")
            .bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject("database") }

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = root.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val tableName = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", tableName))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (indexPosition in 0 until indices.length()) {
                        db.execSQL(
                            indices.getJSONObject(indexPosition)
                                .getString("createSql")
                                .replace("${'$'}{TABLE_NAME}", tableName),
                        )
                    }
                }
                val setupQueries = root.getJSONArray("setupQueries")
                for (index in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(index))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build(),
        )
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }
}
