package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalBranchIdentityTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalBranchRepository
    private var now = 1_930_000_000_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        val authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("branch-master-owner", "مالک شعب", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        repository = LocalBranchRepository(database, authorizer, clock = { ++now })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun renameChangesDisplayNameWithoutChangingCanonicalIdentity() = runBlocking {
        val branchId = repository.create(BranchDraft(name = "ونک", code = "VNK"))
        val before = requireNotNull(repository.getById(branchId))

        repository.rename(branchId, "ونک مرکزی")

        val after = requireNotNull(repository.getById(branchId))
        assertEquals(branchId, after.id)
        assertEquals(before.globalId, after.globalId)
        assertEquals("VNK", after.code)
        assertEquals("ونک مرکزی", after.name)
        assertNotEquals(before.name, after.name)
    }

    @Test
    fun duplicateOrganizationCodeIsRejectedAndDeactivateRemovesBranchFromActiveResolver() = runBlocking {
        val branchId = repository.create(BranchDraft(name = "ونک", code = "VNK", organizationId = 7L))
        try {
            repository.create(BranchDraft(name = "ونک دوم", code = "VNK", organizationId = 7L))
            throw AssertionError("duplicate organization+code must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("duplicate"))
        }

        repository.setActive(branchId, false)
        assertFalse(requireNotNull(repository.getById(branchId)).isActive)
        assertTrue(repository.listActive().none { it.id == branchId })
        try {
            CanonicalBranchResolver(database).requireActive(branchId)
            throw AssertionError("inactive branch must not resolve for a new transaction")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("فعال") || expected.message.orEmpty().contains("شعبه"))
        }
    }
}
