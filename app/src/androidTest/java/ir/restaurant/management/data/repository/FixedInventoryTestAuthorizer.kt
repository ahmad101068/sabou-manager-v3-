package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.AuthorizedActor
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole

internal class FixedInventoryTestAuthorizer(
    private val actorId: Long = 42L,
    private val role: UserRole = UserRole.OWNER,
) : AuthorizationService {
    private val actor = AuthorizedActor(actorId, "inventory-test", role)
    override suspend fun actorIdentity(): AuthorizedActor = actor
    override suspend fun can(permission: Permission): Boolean = role.allows(permission)
    override suspend fun require(permission: Permission): AuthorizedActor {
        check(role.allows(permission)) { "test role missing $permission" }
        return actor
    }
}
