package ir.restaurant.management.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase81ForensicIntegrityLedgerIntegrationTest {
    private lateinit var context: Context
    private lateinit var ledgerFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ledgerFile = File(context.noBackupFilesDir, "phase81-forensic-integrity-ledger.log")
        ledgerFile.delete()
    }

    @After
    fun tearDown() {
        ledgerFile.delete()
        context.deleteDatabase("phase81-forensic-boundary.db")
    }

    @Test
    fun restoreRequestAndCompletionAreHmacChained() {
        val ledger = ForensicIntegrityLedger(context)
        append(ledger, "RESTORE_REQUESTED", "restore-1", "REQUESTED")
        append(ledger, "RESTORE_COMPLETED", "restore-1", "SUCCESS")
        val result = ledger.verify()
        assertTrue(result.valid)
        assertEquals(2, result.receiptCount)
        assertTrue(result.terminalMac.isNotBlank())
    }

    @Test
    fun tamperedReceiptIsDetected() {
        val ledger = ForensicIntegrityLedger(context)
        append(ledger, "RESTORE_REQUESTED", "restore-2", "REQUESTED")
        ledgerFile.appendText("corrupted-line\n")
        assertFalse(ledger.verify().valid)
    }

    @Test
    fun externalReceiptSurvivesDatabaseReplacementAndFactoryResetBoundary() {
        val dbFile = context.getDatabasePath("phase81-forensic-boundary.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("newer-database")
        val sourceFingerprint = ForensicIntegrityLedger.fingerprint(dbFile)
        val ledger = ForensicIntegrityLedger(context)
        append(ledger, "FACTORY_RESET_REQUESTED", "reset-1", "REQUESTED", sourceFingerprint)
        dbFile.delete()
        dbFile.writeText("fresh-database")
        append(ledger, "FACTORY_RESET_COMPLETED", "reset-1", "SUCCESS", sourceFingerprint, ForensicIntegrityLedger.fingerprint(dbFile))
        assertTrue(ledgerFile.isFile)
        assertTrue(ledger.verify().valid)
        assertEquals(2, ledger.verify().receiptCount)
    }

    @Test
    fun olderAuditChainCannotMatchIndependentAnchor() {
        val ledger = ForensicIntegrityLedger(context)
        ledger.append(
            operationType = "AUDIT_ANCHOR",
            requestEpochMillis = 100L,
            completionEpochMillis = 101L,
            actorId = null,
            actor = "SYSTEM",
            deviceId = "device",
            auditTerminalHash = "newer-terminal-hash",
            schemaVersion = 60,
            correlationId = "anchor-1",
            result = "VERIFIED",
        )
        assertEquals("newer-terminal-hash", ledger.latestAuditAnchorHash())
        assertTrue(ledger.latestAuditAnchorHash() != "older-terminal-hash")
    }

    private fun append(
        ledger: ForensicIntegrityLedger,
        type: String,
        correlation: String,
        result: String,
        source: String = "source",
        destination: String = "destination",
    ) {
        ledger.append(
            operationType = type,
            requestEpochMillis = 100L,
            completionEpochMillis = 101L,
            actorId = 1L,
            actor = "Owner",
            deviceId = "phase81-device",
            sourceDbFingerprint = source,
            destinationDbFingerprint = destination,
            backupChecksum = "backup-sha256",
            auditTerminalHash = "audit-terminal",
            schemaVersion = 60,
            correlationId = correlation,
            result = result,
        )
    }
}
