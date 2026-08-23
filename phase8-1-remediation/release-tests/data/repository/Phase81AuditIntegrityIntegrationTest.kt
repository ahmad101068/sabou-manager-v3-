package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase81AuditIntegrityIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun untouchedChainPasses() = runBlocking {
        appendEvents(3)
        val result = AuditIntegrityVerifier(database).verify()
        assertTrue(result.valid)
        assertTrue(result.checkedEvents == 3)
        assertTrue(result.terminalHash.isNotBlank())
    }

    @Test
    fun changedEventIsDetected() = runBlocking {
        appendEvents(3)
        allowPhysicalTamper(update = true)
        val id = database.auditLogDao().allForIntegrityVerification()[1].id
        database.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET description='tampered' WHERE id=$id")
        assertFalse(AuditIntegrityVerifier(database).verify().valid)
    }

    @Test
    fun missingMiddleEventIsDetected() = runBlocking {
        appendEvents(3)
        allowPhysicalTamper(delete = true)
        val id = database.auditLogDao().allForIntegrityVerification()[1].id
        database.openHelper.writableDatabase.execSQL("DELETE FROM audit_logs WHERE id=$id")
        val result = AuditIntegrityVerifier(database).verify()
        assertFalse(result.valid)
        assertTrue(result.failure?.startsWith("AUDIT_SEQUENCE_GAP_OR_REORDER") == true)
    }

    @Test
    fun alteredSequenceIsDetected() = runBlocking {
        appendEvents(3)
        allowPhysicalTamper(update = true)
        val id = database.auditLogDao().allForIntegrityVerification()[1].id
        database.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET integritySequence=9 WHERE id=$id")
        assertFalse(AuditIntegrityVerifier(database).verify().valid)
    }

    @Test
    fun forgedPreviousHashIsDetected() = runBlocking {
        appendEvents(3)
        allowPhysicalTamper(update = true)
        val id = database.auditLogDao().allForIntegrityVerification()[1].id
        database.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET previousEventHash='forged' WHERE id=$id")
        val result = AuditIntegrityVerifier(database).verify()
        assertFalse(result.valid)
        assertTrue(result.failure?.startsWith("AUDIT_PREVIOUS_HASH_MISMATCH") == true)
    }

    @Test
    fun replayedChainElementIsDetected() = runBlocking {
        appendEvents(3)
        allowPhysicalTamper(update = true)
        val rows = database.auditLogDao().allForIntegrityVerification()
        database.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET eventHash='${rows.first().eventHash}' WHERE id=${rows.last().id}")
        assertFalse(AuditIntegrityVerifier(database).verify().valid)
    }

    private suspend fun appendEvents(count: Int) {
        val writer = LocalAuditEventWriter(database)
        repeat(count) { ordinal ->
            writer.append(
                AuditEventDraft(
                    action = AuditAction.of("UPDATE"),
                    entityType = AuditEntityType.of("PHASE81_TEST"),
                    entityId = (ordinal + 1).toLong(),
                    actorId = 1L,
                    actorDisplayName = "Phase81 Auditor",
                    occurredAtEpochMillis = 1_900_000_000_000L + ordinal,
                    businessEpochDay = 22_000L,
                    deviceId = "phase81-test-device",
                    referenceType = "PHASE81_TEST",
                    referenceId = (ordinal + 1).toLong(),
                    reason = "integrity verification",
                    beforeSnapshot = "before-$ordinal",
                    afterSnapshot = "after-$ordinal",
                    correlationId = "phase81:audit:$ordinal",
                    description = "event-$ordinal",
                    actorRoleSnapshot = "ADMIN",
                    actorBranchIdSnapshot = 1L,
                ),
            )
        }
    }

    private fun allowPhysicalTamper(update: Boolean = false, delete: Boolean = false) {
        val sqlite = database.openHelper.writableDatabase
        if (update) sqlite.execSQL("DROP TRIGGER IF EXISTS trg_audit_logs_no_update")
        if (delete) sqlite.execSQL("DROP TRIGGER IF EXISTS trg_audit_logs_no_delete")
    }
}
