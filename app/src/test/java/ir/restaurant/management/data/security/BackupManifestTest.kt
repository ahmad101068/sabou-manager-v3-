package ir.restaurant.management.data.security

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class BackupManifestTest {
    @Test
    fun binaryRoundTripPreservesVersionIdentityAndRecordCounts() {
        val original = manifest()

        val decoded = BackupManifest.decode(original.encode())

        assertEquals(original, decoded)
        decoded.requireCompatibleDatabase("ir.restaurant.management", 39, 8_192, "a".repeat(64))
    }

    @Test
    fun futureSchemaAndChangedDatabaseAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            manifest(schemaVersion = 40).requireCompatibleDatabase(
                "ir.restaurant.management",
                39,
                8_192,
                "a".repeat(64),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            manifest().requireCompatibleDatabase(
                "ir.restaurant.management",
                39,
                8_192,
                "b".repeat(64),
            )
        }
    }

    private fun manifest(schemaVersion: Int = 39) = BackupManifest(
        applicationId = "ir.restaurant.management",
        appVersionName = "3.0.0-test",
        appVersionCode = 188,
        schemaVersion = schemaVersion,
        createdAtEpochMillis = 123_456,
        sourceDeviceId = "device-test",
        databaseSizeBytes = 8_192,
        databaseSha256 = "a".repeat(64),
        tableRecordCounts = mapOf("audit_logs" to 10, "journal_entries" to 20),
    )
}
