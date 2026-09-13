package ir.restaurant.management.data.security

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.repository.LocalAuditEventWriter
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.AuthorizedActor
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole

class AuthenticationRequiredException :
    BusinessRuleViolation(BusinessError.AuthenticationRequired)

class AccessDeniedException(val permission: Permission) :
    BusinessRuleViolation(BusinessError.PermissionDenied(permission))

/** Enforces authorization at the data-command boundary. UI filtering is not a security boundary. */
class SessionAuthorizer(
    private val database: AppDatabase,
    private val monotonicClockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MILLIS,
) : AuthorizationService {
    private var activeUserId: Long? = null
    private var lastActivityMonotonicMillis: Long? = null

    suspend fun actor(): String = try {
        actorIdentity().displayName
    } catch (_: AuthenticationRequiredException) {
        "SYSTEM"
    }

    override suspend fun actorIdentity(): AuthorizedActor {
        val user = database.securityDao().currentUser() ?: throw AuthenticationRequiredException()
        if (!user.isActive) throw AuthenticationRequiredException()
        enforceIdleSession(user.id)
        return AuthorizedActor(
            id = user.id,
            displayName = user.displayName.ifBlank { user.username },
            role = UserRole.fromStoredValue(user.role),
        )
    }

    override suspend fun can(permission: Permission): Boolean = try {
        actorIdentity().role.allows(permission)
    } catch (_: AuthenticationRequiredException) {
        false
    }

    override suspend fun require(permission: Permission): AuthorizedActor {
        val actor = actorIdentity()
        val role = actor.role
        if (!role.allows(permission)) {
            val now = System.currentTimeMillis()
            LocalAuditEventWriter(database).append(
                AuditEventDraft(
                    action = AuditAction.of("ACCESS_DENIED"),
                    entityType = AuditEntityType.of("SECURITY"),
                    entityId = actor.id,
                    actorId = actor.id,
                    actorDisplayName = actor.displayName,
                    occurredAtEpochMillis = now,
                    businessEpochDay = null,
                    deviceId = "local-android",
                    referenceType = "SECURITY",
                    referenceId = actor.id,
                    reason = "PERMISSION_DENIED:${permission.name}",
                    beforeSnapshot = null,
                    afterSnapshot = null,
                    correlationId = "security:denied:${actor.id}:${permission.name}:${System.nanoTime()}",
                    description = "رد دسترسی به مجوز ${permission.name}",
                ),
            )
            throw AccessDeniedException(permission)
        }
        return actor
    }

    suspend fun requireOwner(): AuthorizedActor {
        val actor = actorIdentity()
        if (actor.role != UserRole.OWNER) {
            throw AccessDeniedException(Permission.MANAGE_USERS)
        }
        return actor
    }

    internal suspend fun currentUserId(): Long {
        return actorIdentity().id
    }

    private suspend fun enforceIdleSession(userId: Long) {
        val now = monotonicClockMillis()
        if (activeUserId != userId) {
            activeUserId = userId
            lastActivityMonotonicMillis = now
            return
        }
        val last = lastActivityMonotonicMillis
        if (last != null && now - last > idleTimeoutMillis) {
            activeUserId = null
            lastActivityMonotonicMillis = null
            database.securityDao().clearSession()
            throw AuthenticationRequiredException()
        }
        lastActivityMonotonicMillis = now
    }

    private companion object {
        const val DEFAULT_IDLE_TIMEOUT_MILLIS = 30L * 60L * 1_000L
    }
}
