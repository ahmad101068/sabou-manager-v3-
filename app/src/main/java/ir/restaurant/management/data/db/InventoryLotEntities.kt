package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.restaurant.management.core.GlobalId

@Entity(
    tableName = "inventory_lots",
    foreignKeys = [
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["itemId", "locationId", "lotCode"], unique = true),
        Index(value = ["globalId"], unique = true),
        Index("barcode"),
        Index("locationId"),
        Index("expiryEpochDay"),
        Index(value = ["itemId", "locationId", "expiryEpochDay"]),
        Index(value = ["status", "expiryEpochDay"]),
        Index("sourceReceiptId"),
        Index("correlationId"),
    ],
)
data class InventoryLotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val itemId: Long,
    val locationId: Long,
    val lotCode: String,
    val supplierLotNumber: String? = null,
    val receivedEpochDay: Long,
    val productionEpochDay: Long? = null,
    val expiryEpochDay: Long?,
    val quantityMicros: Long,
    @ColumnInfo(defaultValue = "0") val initialQuantityMicros: Long = quantityMicros,
    val unitCostRial: Long,
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    val barcode: String?,
    val sourceReceiptId: Long? = null,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "legacy:lot:${GlobalId.new().value}",
    val createdByActorId: Long? = null,
    @ColumnInfo(defaultValue = "0") val createdAtEpochMillis: Long = 0,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_lot_consumptions",
    foreignKeys = [
        ForeignKey(entity = StockMovementEntity::class, parentColumns = ["id"], childColumns = ["stockMovementId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryLotEntity::class, parentColumns = ["id"], childColumns = ["lotId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("stockMovementId"), Index("lotId")],
)
data class InventoryLotConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stockMovementId: Long,
    val lotId: Long,
    val quantityMicros: Long,
    val reversedQuantityMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val unitCostRial: Long = 0,
    @ColumnInfo(defaultValue = "'ACTIVE'") val lotStatusSnapshot: String = "ACTIVE",
)

data class InventoryLotRow(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val locationId: Long,
    val locationName: String,
    val lotCode: String,
    val supplierLotNumber: String?,
    val receivedEpochDay: Long,
    val productionEpochDay: Long?,
    val expiryEpochDay: Long?,
    val quantityMicros: Long,
    val initialQuantityMicros: Long,
    val unitCostRial: Long,
    val status: String,
    val barcode: String?,
    val sourceReceiptId: Long?,
    val globalId: String,
    val correlationId: String,
)
