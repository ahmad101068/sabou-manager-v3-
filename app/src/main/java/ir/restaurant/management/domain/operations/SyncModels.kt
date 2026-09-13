package ir.restaurant.management.domain.operations

enum class SyncChangeType {
    CREATE,
    UPDATE,
    UPSERT,
    DEACTIVATE,
    DISPOSE,
    SETTLEMENT,
    RECEIPT,
    REVERSAL,
}

/**
 * Converts business-level audit actions to the small set of outbox mutations.
 *
 * Repositories intentionally record descriptive actions such as SUBMIT,
 * APPROVE, POST, and DISPATCH_PRINT. Those actions must remain visible in the
 * audit log, but they must not be parsed directly as [SyncChangeType] values:
 * doing so would throw inside the surrounding Room transaction and roll the
 * business operation back.
 */
object SyncChangeClassifier {
    fun normalize(action: String): String = action.trim().uppercase().also {
        require(it.matches(Regex("[A-Z][A-Z0-9_]*"))) {
            "عنوان عملیات همگام‌سازی معتبر نیست."
        }
    }

    fun classify(action: String): SyncChangeType {
        val normalized = normalize(action)
        SyncChangeType.entries.firstOrNull { it.name == normalized }?.let { return it }
        return when {
            normalized.startsWith("CREATE") -> SyncChangeType.CREATE
            normalized in setOf("POST", "REQUEST", "SUBMIT") -> SyncChangeType.CREATE
            else -> SyncChangeType.UPDATE
        }
    }
}
enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, CONFLICT, REJECTED, DEAD_LETTER }

data class SyncEnvelope(
    val changeId: String,
    val entityType: String,
    val entityId: Long,
    val type: SyncChangeType,
    val deviceId: String,
    val occurredAtEpochMillis: Long,
    val payloadHash: String,
    val revision: Long = 1,
    val payloadVersion: Int = 1,
    val payload: String = "",
    val state: SyncState = SyncState.PENDING,
) {
    fun validated(): SyncEnvelope {
        require(changeId.isNotBlank() && entityType.isNotBlank() && deviceId.isNotBlank()) { "شناسه تغییر همگام‌سازی معتبر نیست." }
        require(entityId > 0 && occurredAtEpochMillis > 0 && revision > 0) { "بسته همگام‌سازی ناقص است." }
        require(payloadVersion > 0 && payload.isNotBlank()) { "payload همگام‌سازی معتبر نیست." }
        require(payloadHash.matches(Regex("[0-9a-f]{64}"))) { "هش payload معتبر نیست." }
        require(SyncPayloadCodec.verify(payload, payloadHash)) { "محتوای payload با هش آن تطابق ندارد." }
        return this
    }
}

object SyncConflictResolver {
    fun choose(left: SyncEnvelope, right: SyncEnvelope): SyncEnvelope {
        left.validated(); right.validated()
        require(left.entityType == right.entityType && left.entityId == right.entityId) { "تغییرها متعلق به یک رکورد نیستند." }
        return when {
            left.occurredAtEpochMillis > right.occurredAtEpochMillis -> left.copy(state = SyncState.SYNCED)
            right.occurredAtEpochMillis > left.occurredAtEpochMillis -> right.copy(state = SyncState.SYNCED)
            left.changeId >= right.changeId -> left.copy(state = SyncState.SYNCED)
            else -> right.copy(state = SyncState.SYNCED)
        }
    }
}
