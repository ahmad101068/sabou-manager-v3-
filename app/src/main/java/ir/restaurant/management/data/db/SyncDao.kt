package ir.restaurant.management.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_changes ORDER BY occurredAtEpochMillis, id")
    fun observeAll(): Flow<List<SyncChangeEntity>>

    @Query("SELECT * FROM sync_changes WHERE state = 'PENDING' AND nextAttemptAtEpochMillis <= :nowMillis ORDER BY occurredAtEpochMillis, id LIMIT :limit")
    suspend fun pending(nowMillis: Long, limit: Int): List<SyncChangeEntity>

    @Insert
    suspend fun insert(change: SyncChangeEntity): Long

    @Update
    suspend fun update(change: SyncChangeEntity): Int

    @Query("SELECT * FROM sync_changes WHERE changeId = :changeId LIMIT 1")
    suspend fun byChangeId(changeId: String): SyncChangeEntity?

    @Query("SELECT * FROM sync_changes WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun byIdempotencyKey(idempotencyKey: String): SyncChangeEntity?

    @Query("SELECT COALESCE(MAX(revision), 0) FROM sync_changes WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun maxRevision(entityType: String, entityId: Long): Long

    @Query("DELETE FROM sync_changes WHERE state = 'SYNCED' AND occurredAtEpochMillis < :beforeMillis")
    suspend fun deleteSyncedBefore(beforeMillis: Long): Int

    @Query("UPDATE sync_changes SET state = 'PENDING', lastError = '', attemptCount = 0, lastAttemptAtEpochMillis = 0, nextAttemptAtEpochMillis = 0, deadLetteredAtEpochMillis = NULL WHERE state = 'DEAD_LETTER'")
    suspend fun requeueDeadLetters(): Int
}
