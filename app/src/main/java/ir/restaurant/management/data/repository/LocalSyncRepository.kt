package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.SyncChangeEntity
import ir.restaurant.management.domain.operations.SyncChangeType
import ir.restaurant.management.domain.operations.SyncConflictResolver
import ir.restaurant.management.domain.operations.SyncEnvelope
import ir.restaurant.management.domain.operations.SyncState
import ir.restaurant.management.domain.operations.SyncBatch
import ir.restaurant.management.domain.operations.SyncBatchPlanner
import ir.restaurant.management.domain.operations.SyncPayloadCodec
import ir.restaurant.management.domain.operations.SyncRetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalSyncRepository(private val database: AppDatabase) {
    private val dao get() = database.syncDao()

    val changes: Flow<List<SyncEnvelope>> = dao.observeAll().map { rows -> rows.map { it.toEnvelope() } }

    suspend fun enqueue(envelope: SyncEnvelope): Long {
        val valid = envelope.validated()
        return database.withTransaction {
            val key = idempotencyKey(valid)
            dao.byIdempotencyKey(key)?.id ?: dao.insert(SyncChangeEntity(changeId = valid.changeId, entityType = valid.entityType, entityId = valid.entityId, changeType = valid.type.name, deviceId = valid.deviceId, occurredAtEpochMillis = valid.occurredAtEpochMillis, revision = valid.revision, payloadVersion = valid.payloadVersion, payload = valid.payload, payloadHash = valid.payloadHash, idempotencyKey = key, state = valid.state.name))
        }
    }

    suspend fun markSynced(changeId: String) = updateState(changeId, SyncState.SYNCED, "")
    suspend fun markConflict(changeId: String, error: String) = updateState(changeId, SyncState.CONFLICT, error.trim())
    suspend fun markRejected(changeId: String, error: String) = updateState(changeId, SyncState.REJECTED, error.trim())

    suspend fun nextBatch(limit: Int = 50, nowMillis: Long = System.currentTimeMillis()): SyncBatch = SyncBatchPlanner.plan(dao.pending(nowMillis, limit).map { it.toEnvelope() }, limit)

    suspend fun recordBatchFailure(changeIds: List<String>, error: String, nowMillis: Long = System.currentTimeMillis()) {
        changeIds.distinct().forEach { changeId ->
            val current = dao.byChangeId(changeId) ?: return@forEach
            if (current.state != SyncState.PENDING.name) return@forEach
            val nextAttempt = current.attemptCount + 1
            val decision = SyncRetryPolicy.afterFailure(nextAttempt)
            dao.update(current.copy(
                state = if (decision.canRetry) SyncState.PENDING.name else SyncState.DEAD_LETTER.name,
                lastError = error.trim().take(500), attemptCount = nextAttempt,
                lastAttemptAtEpochMillis = nowMillis,
                nextAttemptAtEpochMillis = if (decision.canRetry) nowMillis + decision.delayMillis else Long.MAX_VALUE,
                deadLetteredAtEpochMillis = if (decision.canRetry) null else nowMillis,
            ))
        }
    }

    suspend fun requeueDeadLetters(): Int = dao.requeueDeadLetters()

    suspend fun resolveIssue(changeId: String, keepLocal: Boolean) {
        require(keepLocal) {
            "پذیرش نسخه سرور تا زمان پیاده‌سازی دریافت و اعمال تراکنشی دادهٔ راه‌دور مجاز نیست."
        }
        val current=dao.byChangeId(changeId)?:error("پیام Sync پیدا نشد.")
        require(current.state in setOf(SyncState.CONFLICT.name,SyncState.REJECTED.name,SyncState.DEAD_LETTER.name)){"این پیام نیازمند حل تعارض نیست."}
        check(dao.update(current.copy(state=SyncState.PENDING.name,lastError="",attemptCount=0,lastAttemptAtEpochMillis=0,nextAttemptAtEpochMillis=0,deadLetteredAtEpochMillis=null))==1)
    }

    suspend fun purgeSyncedBefore(beforeMillis: Long): Int {
        require(beforeMillis > 0) { "زمان پاک‌سازی نامعتبر است." }
        return dao.deleteSyncedBefore(beforeMillis)
    }

    suspend fun resolveConflict(local: SyncEnvelope, remote: SyncEnvelope): SyncEnvelope {
        val winner = SyncConflictResolver.choose(local, remote)
        markSynced(winner.changeId)
        return winner
    }

    private suspend fun updateState(changeId: String, state: SyncState, error: String) {
        val current = dao.byChangeId(changeId) ?: error("تغییر همگام‌سازی پیدا نشد.")
        check(dao.update(current.copy(state = state.name, lastError = error, nextAttemptAtEpochMillis = 0, deadLetteredAtEpochMillis = null)) == 1)
    }

    private fun idempotencyKey(value: SyncEnvelope): String = SyncPayloadCodec.sha256("${value.entityType}|${value.entityId}|${value.revision}|${value.type.name}|${value.payloadHash}")

    private fun SyncChangeEntity.toEnvelope() = SyncEnvelope(changeId = changeId, entityType = entityType, entityId = entityId, type = runCatching { SyncChangeType.valueOf(changeType) }.getOrDefault(SyncChangeType.UPDATE), deviceId = deviceId, occurredAtEpochMillis = occurredAtEpochMillis, revision = revision, payloadVersion = payloadVersion, payload = payload, payloadHash = payloadHash, state = runCatching { SyncState.valueOf(state) }.getOrDefault(SyncState.PENDING))
}
