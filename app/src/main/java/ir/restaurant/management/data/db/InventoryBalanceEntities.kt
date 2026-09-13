package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Rebuildable location projection. Immutable stock movements remain historical authority. */
@Entity(
    tableName = "inventory_balances",
    primaryKeys = ["itemId", "locationId"],
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
    ],
    indices = [Index("locationId"), Index("updatedAtEpochMillis")],
)
data class InventoryBalanceEntity(
    val itemId: Long,
    val locationId: Long,
    val onHandMicros: Long,
    val reservedMicros: Long = 0,
    val inTransitMicros: Long = 0,
    val damagedMicros: Long = 0,
    val quarantinedMicros: Long = 0,
    val inventoryValueRial: Long,
    val updatedAtEpochMillis: Long,
)
