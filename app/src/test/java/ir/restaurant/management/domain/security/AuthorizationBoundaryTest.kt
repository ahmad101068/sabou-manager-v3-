package ir.restaurant.management.domain.security

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.common.asViolation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthorizationBoundaryTest {
    @Test
    fun requireEnforcesPermissionOutsideUi() = runBlocking {
        val owner = FakeAuthorizationService(UserRole.OWNER)
        val cashier = FakeAuthorizationService(UserRole.CASHIER)

        assertTrue(owner.can(Permission.ACCOUNTING_PERIOD_CLOSE))
        assertFalse(cashier.can(Permission.ACCOUNTING_PERIOD_CLOSE))
        assertEquals(1L, owner.require(Permission.ACCOUNTING_PERIOD_CLOSE).id)
        try {
            cashier.require(Permission.ACCOUNTING_PERIOD_CLOSE)
            fail("repository/application boundary must reject the missing permission")
        } catch (error: BusinessRuleViolation) {
            assertEquals(
                BusinessError.PermissionDenied(Permission.ACCOUNTING_PERIOD_CLOSE),
                error.error,
            )
        }
    }

    private class FakeAuthorizationService(role: UserRole) : AuthorizationService {
        private val actor = AuthorizedActor(1, "tester", role)

        override suspend fun actorIdentity(): AuthorizedActor = actor
        override suspend fun can(permission: Permission): Boolean = actor.role.allows(permission)
        override suspend fun require(permission: Permission): AuthorizedActor {
            if (!can(permission)) throw BusinessError.PermissionDenied(permission).asViolation()
            return actor
        }
    }
}
