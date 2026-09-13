package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.SyncChangeEntity
import ir.restaurant.management.domain.audit.AuditAction
import ir.restaurant.management.domain.audit.AuditEntityType
import ir.restaurant.management.domain.audit.AuditEventDraft
import ir.restaurant.management.domain.operations.SyncChangeClassifier
import ir.restaurant.management.domain.operations.SyncPayloadCodec

/** Records a deterministic, versioned command payload and joins an existing Room transaction when present. */
open class SyncRecorder(private val database: AppDatabase, private val deviceId: String = "local") {
    open suspend fun record(
        entityType: String,
        entityId: Long,
        changeType: String,
        occurredAt: Long,
        payloadFields: Map<String, String> = emptyMap(),
        recordAudit: Boolean = true,
    ) {
        database.withTransaction {
            val normalizedEntityType = entityType.trim().uppercase()
            val normalizedAction = SyncChangeClassifier.normalize(changeType)
            val normalizedType = SyncChangeClassifier.classify(normalizedAction)
            require(normalizedEntityType.isNotBlank() && entityId > 0 && occurredAt > 0)
            require(payloadFields.keys.none { it in RESERVED_FIELDS }) { "payload شامل کلید رزروشده است." }
            val revision = database.syncDao().maxRevision(normalizedEntityType, entityId) + 1
            check(revision > 0) { "revision همگام‌سازی سرریز کرد." }
            val payload = SyncPayloadCodec.canonicalize(
                mapOf(
                    "changeType" to normalizedType.name,
                    "action" to normalizedAction,
                    "entityId" to entityId.toString(),
                    "entityType" to normalizedEntityType,
                    "occurredAt" to occurredAt.toString(),
                    "revision" to revision.toString(),
                ) + payloadFields,
            )
            val payloadHash = SyncPayloadCodec.sha256(payload)
            val idempotencyKey = SyncPayloadCodec.sha256("$normalizedEntityType|$entityId|$revision|${normalizedType.name}|$payloadHash")
            val changeId = "$deviceId:$idempotencyKey"
            database.syncDao().insert(
                SyncChangeEntity(
                    changeId = changeId,
                    entityType = normalizedEntityType,
                    entityId = entityId,
                    changeType = normalizedType.name,
                    deviceId = deviceId,
                    occurredAtEpochMillis = occurredAt,
                    revision = revision,
                    payloadVersion = PAYLOAD_VERSION,
                    payload = payload,
                    payloadHash = payloadHash,
                    idempotencyKey = idempotencyKey,
                ),
            )
            if (recordAudit) {
                val actor = database.securityDao().currentUser()
                LocalAuditEventWriter(database).append(
                    AuditEventDraft(
                        action = AuditAction.of(normalizedAction),
                        entityType = AuditEntityType.of(normalizedEntityType),
                        entityId = entityId,
                        actorId = actor?.id,
                        actorDisplayName = actor?.displayName?.ifBlank { actor.username } ?: "SYSTEM",
                        occurredAtEpochMillis = occurredAt,
                        businessEpochDay = null,
                        deviceId = deviceId,
                        referenceType = normalizedEntityType,
                        referenceId = entityId,
                        reason = "SYNC_OUTBOX_RECORD",
                        beforeSnapshot = null,
                        afterSnapshot = "revision=$revision;payloadHash=$payloadHash;changeId=$changeId",
                        correlationId = changeId,
                        description = "$normalizedEntityType #$entityId · $normalizedAction",
                    ),
                )
            }
        }
    }

    private companion object {
        const val PAYLOAD_VERSION = 1
        val RESERVED_FIELDS = setOf("action", "changeType", "entityId", "entityType", "occurredAt", "revision")
    }
}
