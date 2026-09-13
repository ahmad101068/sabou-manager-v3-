package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.AuthenticationRequiredException
import ir.restaurant.management.data.security.AccessDeniedException
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.data.security.SensitiveAuthenticationRequiredException
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.security.Permission
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var sensitiveActionGate: SensitiveActionGate
    private var now = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        sensitiveActionGate = SensitiveActionGate(clockMillis = { now }, permitLifetimeMillis = 5_000L)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun commandsAreDeniedWhenSessionIsMissing() = runBlocking {
        expectThrows<AuthenticationRequiredException> { authorizer.require(Permission.ASSETS) }
    }

    @Test
    fun firstUserMustBeOwnerAndOwnerCanCreateAnotherUser() = runBlocking {
        val repository = repository()
        expectThrows<IllegalArgumentException> {
            repository.save(null, UserDraft("cashier", "صندوقدار", "123456", UserRole.CASHIER))
        }
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        assertTrue(requireNotNull(database.securityDao().byId(ownerId)).pinHash.startsWith("pbkdf2-sha1$310000$"))
        assertEquals(ownerId, database.securityDao().currentUser()?.id)
        repository.switchUser(ownerId, "123456")
        val cashierId = repository.save(null, UserDraft("cashier", "صندوقدار", "654321", UserRole.CASHIER))
        assertTrue(cashierId > ownerId)
        assertEquals(2, database.securityDao().userCount())
        assertEquals(2L, scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='SECURITY_USER' AND action IN ('USER_CREATE','USER_UPDATE')"))
        assertEquals(
            2L,
            scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType='SECURITY_USER' AND action IN ('USER_CREATE','USER_UPDATE') AND actorId IS NOT NULL AND deviceId != '' AND reason != '' AND correlationId != '' AND afterSnapshot IS NOT NULL"),
        )
        assertTrue(auditPayloads().none { "123456" in it || "654321" in it || "87654321" in it })
    }

    @Test
    fun managerCannotCreateOrPromoteUsers() = runBlocking {
        val repository = repository()
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        repository.switchUser(ownerId, "123456")
        val managerId = repository.save(null, UserDraft("manager", "مدیر", "654321", UserRole.MANAGER))
        repository.switchUser(managerId, "654321")
        expectThrows<AccessDeniedException> {
            repository.save(null, UserDraft("second", "کاربر دوم", "111111", UserRole.OWNER))
        }
        assertEquals(2, database.securityDao().userCount())
    }

    @Test
    fun fiveWrongPinsLockLoginAndSuccessfulLoginResetsCounter() = runBlocking {
        val repository = repository()
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        repeat(5) {
            expectThrows<IllegalArgumentException> { repository.switchUser(ownerId, "000000") }
        }
        val locked = requireNotNull(database.securityDao().byId(ownerId))
        assertEquals(5, locked.failedPinAttempts)
        assertEquals(now + 30_000L, locked.lockUntilEpochMillis)
        assertEquals(5L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='LOGIN_FAILURE'"))
        expectThrows<IllegalArgumentException> { repository.switchUser(ownerId, "123456") }
        now += 30_000L
        repository.switchUser(ownerId, "123456")
        val recovered = requireNotNull(database.securityDao().byId(ownerId))
        assertEquals(0, recovered.failedPinAttempts)
        assertEquals(0L, recovered.lockUntilEpochMillis)
    }

    @Test
    fun logoutClearsTheActiveSession() = runBlocking {
        val repository = repository()
        repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        assertTrue(database.securityDao().currentUser() != null)

        repository.logout()

        assertEquals(null, database.securityDao().currentUser())
    }

    @Test
    fun recoveryCodeResetsForgottenPinAndStartsSession() = runBlocking {
        val repository = repository()
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        repository.setRecoveryCode(ownerId, "11223344")
        repository.logout()

        repository.resetPinWithRecovery(ownerId, "11223344", "654321")

        assertEquals(ownerId, database.securityDao().currentUser()?.id)
        repository.logout()
        repository.switchUser(ownerId, "654321")
        assertEquals(ownerId, database.securityDao().currentUser()?.id)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='PIN_RECOVERY_SUCCESS'"))
        assertTrue(auditPayloads().none { "11223344" in it || "654321" in it })
    }

    @Test
    fun auditLogIsAppendOnlyAndSoleOwnerCannotBeDemoted() = runBlocking {
        val repository = repository()
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))

        expectThrows<IllegalArgumentException> {
            repository.save(ownerId, UserDraft("owner", "مالک", "123456", UserRole.MANAGER))
        }
        expectThrows<Exception> {
            database.openHelper.writableDatabase.execSQL("UPDATE audit_logs SET action='TAMPERED'")
        }
        expectThrows<Exception> {
            database.openHelper.writableDatabase.execSQL("DELETE FROM audit_logs")
        }
        assertTrue(scalar("SELECT COUNT(*) FROM audit_logs") > 0L)
    }

    @Test
    fun sensitiveActionRequiresFreshPinAndIssuesOneShotAuditedPermit() = runBlocking {
        val repository = repository()
        val ownerId = repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))

        expectThrows<IllegalArgumentException> {
            repository.authorizeSensitiveAction(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, "000000")
        }
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='SENSITIVE_AUTH_FAILURE'"))
        assertEquals(1, requireNotNull(database.securityDao().byId(ownerId)).failedPinAttempts)

        repository.authorizeSensitiveAction(SensitiveAction.CLOSE_ACCOUNTING_PERIOD, "123456")

        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='SENSITIVE_AUTH_SUCCESS'"))
        sensitiveActionGate.requireAndConsume(ownerId, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)
        expectThrows<SensitiveAuthenticationRequiredException> {
            sensitiveActionGate.requireAndConsume(ownerId, SensitiveAction.CLOSE_ACCOUNTING_PERIOD)
        }
        assertTrue(auditPayloads().none { "123456" in it || "000000" in it })
    }

    @Test
    fun ownerOnlySensitiveActionDenialIsAuditedWithoutIssuingPermit() = runBlocking {
        val repository = repository()
        repository.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        val managerId = repository.save(null, UserDraft("manager", "مدیر", "654321", UserRole.MANAGER))
        repository.switchUser(managerId, "654321")

        expectThrows<AccessDeniedException> {
            repository.authorizeSensitiveAction(SensitiveAction.RESTORE_BACKUP, "654321")
        }

        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='SENSITIVE_AUTH_DENIED' AND entityId=$managerId"))
        expectThrows<SensitiveAuthenticationRequiredException> {
            sensitiveActionGate.requireAndConsume(managerId, SensitiveAction.RESTORE_BACKUP)
        }
        assertTrue(auditPayloads().none { "654321" in it })
    }

    private fun repository() = LocalSecurityRepository(
        database,
        clock = { now },
        authorizer = authorizer,
        sensitiveActionGate = sensitiveActionGate,
        deviceIdProvider = { "security-integration-test" },
    )

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun auditPayloads(): List<String> = database.openHelper.writableDatabase
        .query("SELECT description || '|' || reason || '|' || COALESCE(beforeSnapshot,'') || '|' || COALESCE(afterSnapshot,'') FROM audit_logs")
        .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private suspend inline fun <reified T : Throwable> expectThrows(crossinline block: suspend () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (error: Throwable) {
            assertTrue("Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}", error is T)
        }
    }
}
