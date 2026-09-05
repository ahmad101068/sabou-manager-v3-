package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.restaurant.management.core.GlobalId

/** Immutable business document that explains a WASTE stock movement. */
@Entity(
    tableName = "inventory_waste_documents",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryLotEntity::class,
            parentColumns = ["id"],
            childColumns = ["lotId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["documentNumber"], unique = true),
        Index(value = ["postCommandId"], unique = true),
        Index("itemId"),
        Index("locationId"),
        Index("lotId"),
        Index("status"),
        Index("wasteEpochDay"),
        Index("actorId"),
        Index("correlationId"),
    ],
)
data class InventoryWasteDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val documentNumber: String,
    val idempotencyKey: String,
    val postCommandId: String? = null,
    val correlationId: String,
    val itemId: Long,
    val locationId: Long,
    val lotId: Long? = null,
    val quantityMicros: Long,
    val unitCostRial: Long?,
    val valueRial: Long,
    val stockQuantitySnapshotMicros: Long?,
    val stockValueSnapshotRial: Long?,
    val lotQuantitySnapshotMicros: Long?,
    val wasteEpochDay: Long,
    val reasonCode: String,
    val reason: String,
    val notes: String,
    val status: String,
    val actorId: Long,
    val approvedByActorId: Long? = null,
    val approvedAtEpochMillis: Long? = null,
    val postedByActorId: Long? = null,
    val postedAtEpochMillis: Long? = null,
    val deviceId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
