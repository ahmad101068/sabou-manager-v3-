package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_count_sessions",
    foreignKeys = [
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["createdByActorId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["assignedToActorId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["approvedByActorId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["postedByActorId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["documentNumber"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["postCommandId"], unique = true),
        Index("locationId"),
        Index("status"),
        Index("businessEpochDay"),
        Index("createdByActorId"),
        Index("assignedToActorId"),
        Index("approvedByActorId"),
        Index("postedByActorId"),
        Index("correlationId"),
    ],
)
data class InventoryCountSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String,
    val documentNumber: String,
    val idempotencyKey: String,
    val postCommandId: String? = null,
    val locationId: Long,
    val scope: String,
    val blindCount: Boolean,
    val createdByActorId: Long,
    val assignedToActorId: Long?,
    val status: String,
    val snapshotEpochMillis: Long,
    val businessEpochDay: Long,
    val startedAtEpochMillis: Long?,
    val submittedAtEpochMillis: Long?,
    val approvedByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val postedByActorId: Long?,
    val postedAtEpochMillis: Long?,
    val cancelledAtEpochMillis: Long?,
    val notes: String,
    val correlationId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_count_lines",
    foreignKeys = [
        ForeignKey(entity = InventoryCountSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryLotEntity::class, parentColumns = ["id"], childColumns = ["lotId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["countedByActorId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["sessionId", "itemId", "lotKey"], unique = true),
        Index("sessionId"),
        Index("itemId"),
        Index("lotId"),
        Index("status"),
        Index("countedByActorId"),
    ],
)
data class InventoryCountLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val itemId: Long,
    val lotId: Long?,
    val lotKey: Long,
    val systemQuantitySnapshotMicros: Long,
    val systemValueSnapshotRial: Long,
    val firstCountQuantityMicros: Long?,
    val secondCountQuantityMicros: Long?,
    val finalCountQuantityMicros: Long?,
    val finalCountValueRial: Long?,
    val varianceQuantityMicros: Long?,
    val varianceValueRial: Long?,
    val status: String,
    val reason: String,
    val countedByActorId: Long?,
    val countedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
)
