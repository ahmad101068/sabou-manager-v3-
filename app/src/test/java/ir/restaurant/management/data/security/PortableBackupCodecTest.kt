package ir.restaurant.management.data.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PortableBackupCodecTest {
    @Test
    fun roundTripPreservesDatabaseAndDatabaseKey() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val database = ByteArray(8_192) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()

        val manifest = manifest(database)
        PortableBackupCodec.encrypt(key, ByteArrayInputStream(database), "a strong portable password".toCharArray(), encrypted, manifest)

        val restored = ByteArrayOutputStream()
        val payload = PortableBackupCodec.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()),
            "a strong portable password".toCharArray(),
            restored,
        )
        assertContentEquals(key, payload.databaseKey)
        assertEquals(manifest, payload.manifest)
        assertContentEquals(database, restored.toByteArray())
    }

    @Test
    fun wrongPasswordAndTamperingAreRejected() {
        val encrypted = ByteArrayOutputStream().also {
            val database = ByteArray(4_096)
            PortableBackupCodec.encrypt(ByteArray(32), ByteArrayInputStream(database), "right password 123".toCharArray(), it, manifest(database))
        }.toByteArray()
        assertFails {
            PortableBackupCodec.decrypt(ByteArrayInputStream(encrypted), "wrong password 123".toCharArray(), ByteArrayOutputStream())
        }
        encrypted[encrypted.lastIndex - 3] = (encrypted[encrypted.lastIndex - 3].toInt() xor 1).toByte()
        assertFails {
            PortableBackupCodec.decrypt(ByteArrayInputStream(encrypted), "right password 123".toCharArray(), ByteArrayOutputStream())
        }
    }
    @Test
    fun oversizedDatabaseIsRejectedBeforeWritingPastConfiguredLimit() {
        val encrypted = ByteArrayOutputStream().also {
            PortableBackupCodec.encrypt(
                ByteArray(32) { 4 },
                ByteArrayInputStream(ByteArray(8_192) { 6 }),
                "bounded portable password".toCharArray(),
                it,
                manifest(ByteArray(8_192) { 6 }),
            )
        }.toByteArray()
        val restored = ByteArrayOutputStream()

        assertFails {
            PortableBackupCodec.decrypt(
                ByteArrayInputStream(encrypted),
                "bounded portable password".toCharArray(),
                restored,
                maxDatabaseBytes = 4_096,
            )
        }
        assertTrue(restored.size() <= 4_096)
    }

    @Test
    fun legacyVersionTwoEnvelopeRemainsImportable() {
        val key = ByteArray(32) { (it + 11).toByte() }
        val database = ByteArray(4_096) { (it % 193).toByte() }
        val password = "legacy portable password".toCharArray()
        val encrypted = legacyVersionTwoEnvelope(key, database, password)
        val restored = ByteArrayOutputStream()

        val payload = PortableBackupCodec.decrypt(
            ByteArrayInputStream(encrypted),
            password,
            restored,
        )

        assertContentEquals(key, payload.databaseKey)
        assertNull(payload.manifest)
        assertContentEquals(database, restored.toByteArray())
    }

    private fun manifest(database: ByteArray) = BackupManifest(
        applicationId = "ir.restaurant.management",
        appVersionName = "test",
        appVersionCode = 1,
        schemaVersion = 39,
        createdAtEpochMillis = 1,
        sourceDeviceId = "unit-test",
        databaseSizeBytes = database.size.toLong(),
        databaseSha256 = MessageDigest.getInstance("SHA-256").digest(database).joinToString("") { "%02x".format(it) },
        tableRecordCounts = mapOf("audit_logs" to 2L),
    )

    private fun legacyVersionTwoEnvelope(databaseKey: ByteArray, database: ByteArray, password: CharArray): ByteArray {
        val magic = byteArrayOf(0x53, 0x41, 0x42, 0x4f, 0x55, 0x42, 0x4b, 0x32)
        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 21).toByte() }
        val header = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(magic)
                output.writeInt(2)
                output.writeInt(310_000)
                output.write(salt)
                output.write(iv)
            }
            bytes.toByteArray()
        }
        val spec = PBEKeySpec(password, salt, 310_000, 256)
        val key = try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(header)
        }
        return ByteArrayOutputStream().also { destination ->
            destination.write(header)
            CipherOutputStream(destination, cipher).use { encrypted ->
                encrypted.write(databaseKey)
                encrypted.write(database)
            }
        }.toByteArray()
    }

}
