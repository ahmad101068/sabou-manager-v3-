package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.data.security.SensitiveAuthenticationRequiredException
import ir.restaurant.management.domain.control.AccountingPeriodDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagementControlTransactionIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var sensitiveActionGate: SensitiveActionGate
    private lateinit var securityRepository: LocalSecurityRepository
    private val now = 3_000_000L

    @Before
    fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        sensitiveActionGate = SensitiveActionGate(clockMillis = { now })
        securityRepository = LocalSecurityRepository(
            database,
            clock = { now },
            authorizer = authorizer,
            sensitiveActionGate = sensitiveActionGate,
        )
        securityRepository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        Unit
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun closePeriodWithoutFreshReauthenticationIsRejectedBeforeMutation() = runBlocking {
        try {
            repository(SyncRecorder(database, "management-test-device"))
                .closeAccountingPeriod(AccountingPeriodDraft(100, 130, "پایان دوره آزمایشی"))
            fail("Expected sensitive-action authentication failure")
        } catch (_: SensitiveAuthenticationRequiredException) {
            // expected
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM accounting_period_locks"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='ACCOUNTING_PERIOD'"))
    }

    @Test
    fun closePeriodRollsBackLockAndAuditWhenOutboxFails() = runBlocking {
        val repository = repository(failingRecorder())
        authorize(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", "100:130"))

        try {
            repository.closeAccountingPeriod(AccountingPeriodDraft(100, 130, "پایان دوره آزمایشی"))
            fail("Expected outbox failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(0L, scalar("SELECT COUNT(*) FROM accounting_period_locks"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='ACCOUNTING_PERIOD'"))
    }

    @Test
    fun closePeriodCommitsOneAuditAndOutboxInSameTransaction() = runBlocking {
        val repository = repository(SyncRecorder(database, "management-test-device"))
        authorize(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", "100:130"))

        val id = repository.closeAccountingPeriod(AccountingPeriodDraft(100, 130, "پایان دوره آزمایشی"))

        assertEquals("CLOSED", database.managementControlDao().accountingPeriodLock(id)?.status)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='ACCOUNTING_PERIOD' AND action='CLOSE'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM sync_changes WHERE entityType='ACCOUNTING_PERIOD'"))
    }

    @Test
    fun reopenPeriodRollsBackStatusAndAuditWhenOutboxFails() = runBlocking {
        authorize(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", "100:130"))
        val id = repository(SyncRecorder(database, "management-test-device"))
            .closeAccountingPeriod(AccountingPeriodDraft(100, 130, "پایان دوره آزمایشی"))

        authorize(SensitiveAction.REOPEN_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", id))
        try {
            repository(failingRecorder()).reopenAccountingPeriod(id)
            fail("Expected outbox failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals("CLOSED", database.managementControlDao().accountingPeriodLock(id)?.status)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='ACCOUNTING_PERIOD' AND action='REOPEN'"))
    }

    private fun repository(recorder: SyncRecorder) = LocalManagementControlRepository(
        database = database,
        procurementRepository = LocalProcurementRepository(database, authorizer),
        authorizer = authorizer,
        clock = { now },
        syncRecorder = recorder,
        sensitiveActionGate = sensitiveActionGate,
    )

    private suspend fun authorize(action: SensitiveAction, context: SensitiveActionContext) {
        securityRepository.authorizeSensitiveAction(action, "123456", context)
    }

    private fun failingRecorder() = object : SyncRecorder(database, "failing-device") {
        override suspend fun record(
            entityType: String,
            entityId: Long,
            changeType: String,
            occurredAt: Long,
            payloadFields: Map<String, String>,
            recordAudit: Boolean,
        ) {
            error("forced outbox failure")
        }
    }

    private fun scalar(sql: String): Long =
        database.openHelper.writableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
