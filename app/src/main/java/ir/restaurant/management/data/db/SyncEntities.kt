package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sync_changes", indices = [Index(value = ["changeId"], unique = true), Index(value = ["idempotencyKey"], unique = true), Index("state"), Index("occurredAtEpochMillis"), Index("nextAttemptAtEpochMillis")])
data class SyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val changeId: String,
    val entityType: String,
    val entityId: Long,
    val changeType: String,
    val deviceId: String,
    val occurredAtEpochMillis: Long,
    val revision: Long,
    val payloadVersion: Int,
    val payload: String,
    val payloadHash: String,
    @ColumnInfo(defaultValue = "''") val idempotencyKey: String,
    val state: String = "PENDING",
    val lastError: String = "",
    @ColumnInfo(defaultValue = "0") val attemptCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastAttemptAtEpochMillis: Long = 0,
    @ColumnInfo(defaultValue = "0") val nextAttemptAtEpochMillis: Long = 0,
    val deadLetteredAtEpochMillis: Long? = null,
)
