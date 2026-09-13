package ir.restaurant.management.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.DatabaseKeyProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreValidationIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keyProvider = DatabaseKeyProvider(context)
    private lateinit var database: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        File(context.filesDir, "backups").listFiles().orEmpty().forEach { it.delete() }
        context.getSharedPreferences("backup_state", Context.MODE_PRIVATE).edit().clear().commit()
        manager = BackupManager(context, keyProvider) { "restore-validation-test" }
        database = AppDatabase.create(context, keyProvider)
        database.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        runCatching { manager.clearAll() }
        runCatching { keyProvider.rollbackRestorePassphrase() }
        context.deleteDatabase(DATABASE_NAME)
        context.deleteDatabase(VALIDATION_DATABASE_NAME)
    }

    @Test
    fun backupHasManifestAndRestoreIsValidatedOnTemporaryRoomDatabase() {
        val selected = manager.create(database)
        val recovery = manager.create(database)

        assertTrue(manager.verify(selected))
        assertTrue(File(context.filesDir, "backups/$selected.manifest").isFile)
        manager.scheduleRestore(selected, recovery)

        assertEquals(
            selected,
            context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
                .getString("pending_restore", null),
        )
        assertFalse(context.getDatabasePath(VALIDATION_DATABASE_NAME).exists())
    }

    @Test
    fun validatedRestoreIsNeverRolledBackDuringRetryableFinalization() {
        val selected = manager.create(database)
        val recovery = manager.create(database)
        manager.scheduleRestore(selected, recovery)
        context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
            .edit()
            .putString("restore_phase", "VALIDATED")
            .commit()

        assertFalse(manager.rollbackLastRestore())
        assertTrue(File(context.filesDir, "backups/$recovery").isFile)
    }

    @Test
    fun rollbackRecoveryIsRetainedUntilRecoveredDatabaseIsValidated() {
        val selected = manager.create(database)
        val recovery = manager.create(database)
        manager.scheduleRestore(selected, recovery)
        database.close()
        context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
            .edit()
            .putString("restore_phase", "ROLLBACK_PREPARED")
            .commit()

        manager.applyPendingRestore()

        val preferences = context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
        assertEquals("ROLLBACK_AWAITING_VALIDATION", preferences.getString("restore_phase", null))
        assertTrue(File(context.filesDir, "backups/$recovery").isFile)

        database = AppDatabase.create(context, keyProvider)
        database.openHelper.writableDatabase
        manager.markRestoreValidated()

        assertFalse(File(context.filesDir, "backups/$recovery").exists())
        assertNull(preferences.getString("restore_phase", null))
    }

    private companion object {
        const val DATABASE_NAME = "restaurant_management.db"
        const val VALIDATION_DATABASE_NAME = "restaurant-management_restore_validation.db"
    }
}
