package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.restaurant.management.core.GlobalId

@Entity(
    tableName = "stock_transfers",
    foreignKeys = [
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceLocationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationLocationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["transferNo"], unique = true),
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["issueCommandId"], unique = true),
        Index(value = ["receiveCommandId"], unique = true),
        Index("sourceLocationId"),
        Index("destinationLocationId"),
        Index("transferEpochDay"),
        Index("status"),
        Index("requestedByActorId"),
        Index("approvedByActorId"),
        Index("issuedByActorId"),
        Index("receivedByActorId"),
        Index("correlationId"),
    ],
)
data class StockTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferNo: String,
    val sourceLocationId: Long,
    val destinationLocationId: Long,
    val transferEpochDay: Long,
    val note: String,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    @ColumnInfo(defaultValue = "''") val idempotencyKey: String,
    val issueCommandId: String? = null,
    val receiveCommandId: String? = null,
    @ColumnInfo(defaultValue = "''") val correlationId: String,
    val status: String,
    val requestedByActorId: Long,
    val actorDisplayNameSnapshot: String,
    val requestedAtEpochMillis: Long,
    val approvedByActorId: Long? = null,
    val approvedAtEpochMillis: Long? = null,
    val issuedByActorId: Long? = null,
    val issuedAtEpochMillis: Long? = null,
    val receivedByActorId: Long? = null,
    val receivedAtEpochMillis: Long? = null,
    @ColumnInfo(defaultValue = "'legacy-unknown'") val deviceId: String = "legacy-unknown",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "stock_transfer_lines",
    foreignKeys = [
        ForeignKey(
            entity = StockTransferEntity::class,
            parentColumns = ["id"],
            childColumns = ["transferId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
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
        Index(value = ["transferId", "itemId", "lotKey"], unique = true),
        Index("transferId"),
        Index("itemId"),
        Index("lotId"),
    ],
)
data class StockTransferLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferId: Long,
    val itemId: Long,
    val lotId: Long?,
    val lotKey: Long,
    val lotCodeSnapshot: String,
    val requestedQuantityMicros: Long,
    val issuedQuantityMicros: Long? = null,
    val receivedQuantityMicros: Long? = null,
    val varianceQuantityMicros: Long? = null,
    val unitCostRial: Long? = null,
    val valueRial: Long? = null,
    val updatedAtEpochMillis: Long,
)
