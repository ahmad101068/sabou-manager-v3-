package ir.restaurant.management.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var manager: BackupManager
    private lateinit var database: File

    @Before
    fun setUp() {
        database = context.getDatabasePath("restaurant_management.db")
        context.deleteDatabase(database.name)
        context.getSharedPreferences("backup_state", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "migration-recovery").listFiles().orEmpty().forEach { it.delete() }
        database.parentFile?.mkdirs()
        manager = BackupManager(context)
    }

    @After
    fun tearDown() {
        runCatching { manager.clearAll() }
        context.deleteDatabase(database.name)
    }

    @Test
    fun failedMigrationRestoresDatabaseAndWalWithoutRetryingSameBuild() {
        val originalDatabase = ByteArray(8_192) { (it % 251).toByte() }
        val originalWal = ByteArray(2_048) { (it % 127).toByte() }
        database.writeBytes(originalDatabase)
        File("${database.path}-wal").writeBytes(originalWal)

        manager.preparePreMigrationRecovery(39)
        database.writeBytes(ByteArray(4_096) { 9 })
        File("${database.path}-wal").writeBytes(ByteArray(1_024) { 8 })

        assertTrue(manager.rollbackPreMigrationRecovery())
        assertArrayEquals(originalDatabase, database.readBytes())
        assertArrayEquals(originalWal, File("${database.path}-wal").readBytes())
        try {
            manager.preparePreMigrationRecovery(39)
            fail("Expected retry guard for the same failed build")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun interruptedRollbackIsCompletedBeforeDatabaseCanOpenAgain() {
        val originalDatabase = ByteArray(8_192) { (it % 239).toByte() }
        database.writeBytes(originalDatabase)
        manager.preparePreMigrationRecovery(39)
        database.writeBytes(ByteArray(4_096) { 5 })
        context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("migration_rollback_in_progress", true)
            .commit()

        try {
            manager.preparePreMigrationRecovery(39)
            fail("Expected the same-build migration retry guard")
        } catch (_: IllegalStateException) {
            // Recovery is completed first, then opening this known-bad build is blocked.
        }

        assertArrayEquals(originalDatabase, database.readBytes())
        assertTrue(
            context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
                .getBoolean("migration_recovery_failed", false),
        )
    }
}
