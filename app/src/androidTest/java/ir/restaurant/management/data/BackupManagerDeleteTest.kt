package ir.restaurant.management.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import ir.restaurant.management.data.security.DatabaseKeyProvider
import ir.restaurant.management.data.security.BackupManifest
import ir.restaurant.management.data.security.PortableBackupCodec
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class BackupManagerDeleteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var backup: File

    @Before
    fun setUp() {
        val backupDirectory = File(context.filesDir, "backups").apply { mkdirs() }
        backup = File(backupDirectory, "delete-test-${System.nanoTime()}.db")
        backup.writeBytes(ByteArray(4_096) { 1 })
        val keyProvider = DatabaseKeyProvider(context)
        File(backupDirectory, "${backup.name}.key").writeText(keyProvider.protectPassphrase(keyProvider.getOrCreatePassphrase()))
        File(backupDirectory, "${backup.name}.sha256").writeText(sha256(backup))
    }

    @After
    fun tearDown() {
        backup.delete()
        File(backup.parentFile, "${backup.name}.key").delete()
        File(backup.parentFile, "${backup.name}.sha256").delete()
        File(backup.parentFile, "${backup.name}.manifest").delete()
    }

    @Test
    fun manualBackupCanBeDeleted() {
        val manager = BackupManager(context)
        manager.delete(backup.name)

        assertFalse(backup.exists())
        assertNull(
            context.getSharedPreferences("backup_state", Context.MODE_PRIVATE)
                .getString("pending_restore", null),
        )
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    @Test
    fun importedBackupDetectsLaterTampering() {
        val manager = BackupManager(context)
        val database = ByteArray(4_096) { 7 }
        val portable = ByteArrayOutputStream().also { output ->
            PortableBackupCodec.encrypt(
                ByteArray(32) { 9 },
                ByteArrayInputStream(database),
                "correct horse battery".toCharArray(),
                output,
                BackupManifest(
                    applicationId = "ir.restaurant.management",
                    appVersionName = "test",
                    appVersionCode = 1,
                    schemaVersion = 39,
                    createdAtEpochMillis = 1,
                    sourceDeviceId = "instrumentation-test",
                    databaseSizeBytes = database.size.toLong(),
                    databaseSha256 = MessageDigest.getInstance("SHA-256").digest(database).joinToString("") { "%02x".format(it) },
                    tableRecordCounts = emptyMap(),
                ),
            )
        }.toByteArray()
        val name = manager.importPortable(ByteArrayInputStream(portable), "correct horse battery".toCharArray())
        val imported = File(context.filesDir, "backups/$name")
        try {
            assertTrue(manager.verify(name))
            imported.appendBytes(byteArrayOf(1))
            assertFalse(manager.verify(name))
        } finally {
            if (imported.exists()) manager.delete(name)
        }
    }
}
