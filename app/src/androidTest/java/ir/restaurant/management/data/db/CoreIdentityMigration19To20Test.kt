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
class CoreIdentityMigration19To20Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "core-identity-migration-19-20.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun addsFatherNameAndRecoveryHashWithoutLosingRows() {
        open(19).use { helper ->
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE app_users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
            db.execSQL("INSERT INTO employees(name) VALUES ('کارمند تست')")
            db.execSQL("INSERT INTO app_users(username) VALUES ('owner')")
        }

        open(20).use { helper ->
            val db = helper.writableDatabase
            db.query("SELECT name, fatherName FROM employees").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("کارمند تست", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
            db.query("SELECT username, recoveryCodeHash FROM app_users").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("owner", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }

    private fun open(version: Int): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 19 && newVersion == 20) MIGRATION_19_20.migrate(db)
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
