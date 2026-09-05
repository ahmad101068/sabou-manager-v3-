package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_period_closures",
    indices = [Index(value = ["fromEpochDay", "toEpochDay"], unique = true), Index("toEpochDay")],
)
data class InventoryPeriodClosureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val openingValueRial: Long,
    val netPurchaseValueRial: Long,
    val recordedOutflowValueRial: Long,
    val expectedClosingValueRial: Long,
    val countedClosingValueRial: Long,
    val varianceValueRial: Long,
    val itemCount: Int,
    val status: String = "CLOSED",
    @ColumnInfo(defaultValue = "1") val revisionNo: Int = 1,
    val closedBy: String,
    val note: String,
    val reopenedBy: String? = null,
    @ColumnInfo(defaultValue = "''") val reopenReason: String = "",
    val reopenedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_period_closure_lines",
    foreignKeys = [ForeignKey(entity = InventoryPeriodClosureEntity::class, parentColumns = ["id"], childColumns = ["closureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("closureId"), Index("itemId")],
)
data class InventoryPeriodClosureLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val closureId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val unitSnapshot: String,
    val openingQuantityMicros: Long,
    val openingValueRial: Long,
    val netPurchaseQuantityMicros: Long,
    val netPurchaseValueRial: Long,
    val recordedOutflowQuantityMicros: Long,
    val recordedOutflowValueRial: Long,
    val adjustmentQuantityMicros: Long,
    val adjustmentValueRial: Long,
    val expectedClosingQuantityMicros: Long,
    val expectedClosingValueRial: Long,
    val countedClosingQuantityMicros: Long,
    val countedClosingValueRial: Long,
)

data class InventoryMovementTotalsRow(
    val itemId: Long,
    val netQuantityMicros: Long,
    val netValueRial: Long,
    val netPurchaseQuantityMicros: Long,
    val netPurchaseValueRial: Long,
    val countAdjustmentQuantityMicros: Long,
    val countAdjustmentValueRial: Long,
)
