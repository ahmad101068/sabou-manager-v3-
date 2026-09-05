package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import ir.restaurant.management.core.GlobalId

@Entity(
    tableName = "inventory_counts",
    foreignKeys = [ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)],
    indices = [
        Index("itemId"),
        Index("countEpochDay"),
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index("correlationId"),
        Index("actorId"),
        Index("locationId"),
    ],
)
data class InventoryCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val previousQuantityMicros: Long,
    val countedQuantityMicros: Long,
    val previousValueRial: Long,
    val countedValueRial: Long,
    val countEpochDay: Long,
    val reason: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    @ColumnInfo(defaultValue = "''") val idempotencyKey: String = "auto:${GlobalId.new().value}",
    @ColumnInfo(defaultValue = "''") val correlationId: String = "local:${GlobalId.new().value}",
    val actorId: Long? = null,
    @ColumnInfo(defaultValue = "'legacy-unknown'") val deviceId: String = "legacy-unknown",
    val locationId: Long? = null,
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index("createdAtEpochMillis"),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["globalId"], unique = true),
        Index("actorId"),
        Index("businessEpochDay"),
        Index(value = ["referenceType", "referenceId"]),
        Index("correlationId"),
        Index("actorBranchIdSnapshot"),
        Index(value = ["integritySequence"], unique = true),
    ],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val description: String,
    val actor: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val actorId: Long? = null,
    val businessEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "'legacy-unknown'") val deviceId: String = "legacy-unknown",
    val referenceType: String? = null,
    val referenceId: Long? = null,
    @ColumnInfo(defaultValue = "''") val reason: String = "",
    val beforeSnapshot: String? = null,
    val afterSnapshot: String? = null,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "",
    @ColumnInfo(defaultValue = "'UNKNOWN'") val actorRoleSnapshot: String = "UNKNOWN",
    val actorBranchIdSnapshot: Long? = null,
    @ColumnInfo(defaultValue = "0") val integritySequence: Long = 0,
    @ColumnInfo(defaultValue = "''") val previousEventHash: String = "",
    @ColumnInfo(defaultValue = "''") val eventHash: String = "",
)
