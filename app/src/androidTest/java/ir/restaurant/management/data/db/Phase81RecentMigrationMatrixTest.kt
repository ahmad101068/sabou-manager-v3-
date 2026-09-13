package ir.restaurant.management.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase81RecentMigrationMatrixTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val createdNames = mutableListOf<String>()

    @After
    fun cleanUp() {
        createdNames.forEach(context::deleteDatabase)
        createdNames.clear()
    }

    @Test fun migrates55ToCurrent() = migrateFrom(55)
    @Test fun migrates56ToCurrent() = migrateFrom(56)
    @Test fun migrates57ToCurrent() = migrateFrom(57)
    @Test fun migrates58ToCurrent() = migrateFrom(58)
    @Test fun migrates59ToCurrent() = migrateFrom(59)

    @Test
    fun cleanInstallCurrentSchemaOpensAtVersion60() = runBlocking {
        val db = AppDatabase.createInMemory(context)
        try {
            val sqlite = db.openHelper.writableDatabase
            assertEquals(60, sqlite.version)
            sqlite.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        } finally {
            db.close()
        }
    }

    private fun migrateFrom(version: Int) {
        val name = "phase81-migration-${version}-to-60.db"
        createdNames += name
        helper.createDatabase(name, version).use { db ->
            assertEquals(version, db.version)
        }
        helper.runMigrationsAndValidate(name, 60, true, *ALL_MIGRATIONS).use { db ->
            assertEquals(60, db.version)
            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }
}
