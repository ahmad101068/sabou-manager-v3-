package ir.restaurant.management.data.repository

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AuditLogEntity
import ir.restaurant.management.data.security.AuditIntegrityCanonicalizer
import androidx.room.withTransaction
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.audit.AuditService
import ir.restaurant.management.domain.security.AuthorizationService

/** Append-only persistence boundary. Audit failure is intentionally propagated to the transaction. */
class LocalAuditEventWriter(
    private val database: AppDatabase,
) : AuditService {
    override suspend fun record(event: AuditEventDraft): Long = append(event)

    suspend fun append(draft: AuditEventDraft): Long {
        val valid = draft.validated()
        require(valid.actorId != null) { "شناسه actor رویداد Audit الزامی است." }
        require(valid.correlationId.isNotBlank()) { "شناسه correlation رویداد Audit الزامی است." }
        require(valid.reason.isNotBlank()) { "دلیل رویداد Audit الزامی است." }
        val actorEntity = valid.actorId?.let { database.securityDao().byId(it) }
        val roleSnapshot = valid.actorRoleSnapshot.takeUnless { it == "UNKNOWN" } ?: actorEntity?.role ?: "UNKNOWN"
        val branchSnapshot = valid.actorBranchIdSnapshot ?: valid.actorId?.let { database.phase3Dao().scopeProfile(it)?.primaryBranchId }
        return database.withTransaction {
            val head = database.auditLogDao().latestIntegrityHead()
            val sequence = Math.addExact(head?.integritySequence ?: 0L, 1L)
            val previousHash = head?.eventHash.orEmpty()
            val unsigned = AuditLogEntity(
                action = valid.action.storedValue,
                entityType = valid.entityType.storedValue,
                entityId = valid.entityId,
                description = valid.description,
                actor = valid.actorDisplayName,
                createdAtEpochMillis = valid.occurredAtEpochMillis,
                globalId = GlobalId.new().value,
                actorId = valid.actorId,
                businessEpochDay = valid.businessEpochDay,
                deviceId = valid.deviceId,
                referenceType = valid.referenceType,
                referenceId = valid.referenceId,
                reason = valid.reason,
                beforeSnapshot = valid.beforeSnapshot,
                afterSnapshot = valid.afterSnapshot,
                correlationId = valid.correlationId,
                actorRoleSnapshot = roleSnapshot,
                actorBranchIdSnapshot = branchSnapshot,
                integritySequence = sequence,
                previousEventHash = previousHash,
            )
            val entity = unsigned.copy(eventHash = AuditIntegrityCanonicalizer.hashEvent(unsigned))
            database.auditLogDao().insert(entity)
        }
    }

    suspend fun appendAuthorized(
        authorizer: AuthorizationService,
        action: String,
        entityType: String,
        entityId: Long?,
        description: String,
        occurredAtEpochMillis: Long,
        businessEpochDay: Long? = null,
        reason: String = description,
        beforeSnapshot: String? = null,
        afterSnapshot: String? = null,
        correlationId: String = "audit:$entityType:${entityId ?: 0}:$occurredAtEpochMillis",
        referenceType: String? = entityType.takeIf { entityId != null },
        referenceId: Long? = entityId,
        deviceId: String = "local-android",
    ): Long {
        val actor = authorizer.actorIdentity()
        return append(
            AuditEventDraft(
                action = AuditAction.of(action),
                entityType = AuditEntityType.of(entityType),
                entityId = entityId,
                actorId = actor.id,
                actorDisplayName = actor.displayName,
                occurredAtEpochMillis = occurredAtEpochMillis,
                businessEpochDay = businessEpochDay,
                deviceId = deviceId,
                referenceType = referenceType,
                referenceId = referenceId,
                reason = reason,
                beforeSnapshot = beforeSnapshot,
                afterSnapshot = afterSnapshot,
                correlationId = correlationId,
                description = description,
                actorRoleSnapshot = actor.role.name,
                actorBranchIdSnapshot = database.phase3Dao().scopeProfile(actor.id)?.primaryBranchId,
            ),
        )
    }
}
