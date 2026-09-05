package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.control.ManagementTaskDraft
import ir.restaurant.management.domain.control.ManagementTaskPriority
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase6SecurityManagementIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private var now = 8_000_000L
    private var mono = 1_000L
    private var ownerId = 0L
    private var assigneeId = 0L
    private var otherManagerId = 0L

    @Before fun setup() = runBlocking {
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(db, monotonicClockMillis = { mono }, idleTimeoutMillis = 1_000L)
        security = LocalSecurityRepository(db, authorizer = authorizer, clock = { ++now })
        ownerId = security.save(null, UserDraft("ph6-owner", "مالک فاز شش", "123456", UserRole.OWNER, "87654321"))
        assigneeId = security.save(null, UserDraft("ph6-assignee", "مدیر مجری", "234567", UserRole.MANAGER, "87654322"))
        otherManagerId = security.save(null, UserDraft("ph6-other", "مدیر دیگر", "345678", UserRole.MANAGER, "87654323"))
        db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO user_scope_profiles(userId,primaryBranchId,updatedAtEpochMillis) VALUES($assigneeId,1,$now),($otherManagerId,1,$now)")
        db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO user_branch_scopes(userId,branchId,createdAtEpochMillis) VALUES($assigneeId,1,$now),($otherManagerId,1,$now)")
    }

    @After fun teardown() = db.close()

    @Test fun assignedTaskRequiresAssigneeAndMakerCheckerAndAuditSnapshotsActorScope() = runBlocking {
        security.switchUser(ownerId, "123456")
        val service = LocalManagementWorkflowService(db, authorizer, clock = { ++now })
        val taskId = service.createTask(ManagementTaskDraft(1,"کنترل بستن صندوق","کنترل مستقل",ManagementTaskPriority.HIGH,assignedUserId=assigneeId,requiresApproval=true))

        security.switchUser(otherManagerId, "345678")
        val unauthorized = runCatching { service.startTask(taskId) }
        assertTrue(unauthorized.isFailure)
        assertEquals("TODO", db.businessOperationsDao().task(taskId)?.status)

        security.switchUser(assigneeId, "234567")
        service.startTask(taskId)
        service.completeTask(taskId)
        assertEquals("WAITING_APPROVAL", db.businessOperationsDao().task(taskId)?.status)
        assertEquals(assigneeId, db.businessOperationsDao().task(taskId)?.completedByUserId)
        assertTrue(runCatching { service.approveTask(taskId) }.isFailure)
        assertEquals("WAITING_APPROVAL", db.businessOperationsDao().task(taskId)?.status)

        val audit = db.openHelper.writableDatabase.query("SELECT actorRoleSnapshot,actorBranchIdSnapshot FROM audit_logs WHERE entityType='MANAGEMENT_TASK' AND actorId=$assigneeId ORDER BY id DESC LIMIT 1")
        audit.use {
            assertTrue(it.moveToFirst())
            assertEquals("MANAGER", it.getString(0))
            assertEquals(1L, it.getLong(1))
        }

        security.switchUser(ownerId, "123456")
        service.approveTask(taskId)
        assertEquals("COMPLETED", db.businessOperationsDao().task(taskId)?.status)
        assertEquals(ownerId, db.businessOperationsDao().task(taskId)?.approvedByUserId)
    }

    @Test fun idleTimeoutInvalidatesSessionAndCanDoesNotBypassIt() = runBlocking {
        security.switchUser(ownerId, "123456")
        assertTrue(authorizer.can(ir.restaurant.management.domain.security.Permission.ACCOUNTING))
        mono += 1_001L
        assertFalse(authorizer.can(ir.restaurant.management.domain.security.Permission.ACCOUNTING))
        assertEquals(null, db.securityDao().currentUser())
    }
}
