package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_sales_summaries",
    indices = [Index("businessEpochDay"), Index("reversedAtEpochDay"), Index("branchId"), Index("locationId"), Index(value = ["branchId", "businessEpochDay"])],
)
data class DailySalesSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "'legacy:daily_sales:0'") val globalId: String = "legacy:daily_sales:0",
    val branchId: Long,
    val locationId: Long? = null,
    val businessEpochDay: Long,
    val grossSalesRial: Long,
    val discountRial: Long,
    @ColumnInfo(defaultValue = "0") val returnRial: Long = 0L,
    val serviceRial: Long,
    val taxRial: Long,
    val netSalesRial: Long,
    val theoreticalCostRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val notes: String,
    val journalEntryId: Long?,
    val costJournalEntryId: Long?,
    val isLegacyArchive: Boolean = false,
    val reversedAtEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val reversalReason: String = "",
    val reversalJournalEntryId: Long? = null,
    val reversalCostJournalEntryId: Long? = null,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val createdByUserId: Long = 0L,
    @ColumnInfo(defaultValue = "'POSTED'") val status: String = "POSTED",
    @ColumnInfo(defaultValue = "0") val updatedByUserId: Long? = null,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

@Entity(
    tableName = "daily_sales_menu_lines",
    foreignKeys = [
        ForeignKey(
            entity = DailySalesSummaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["summaryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("summaryId"), Index("menuItemId"), Index("recipeVersionId")],
)
data class DailySalesMenuLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val summaryId: Long,
    val menuItemId: Long?,
    val recipeVersionId: Long? = null,
    val menuItemNameSnapshot: String,
    val quantityMicros: Long,
    val grossSalesRial: Long?,
    val theoreticalCostRial: Long,
    /** Null distinguishes historical sales whose full-cost profile is unavailable. */
    val foodCostSnapshotRial: Long? = null,
    val packagingCostSnapshotRial: Long? = null,
    val directLaborCostSnapshotRial: Long? = null,
    val allocatedOverheadSnapshotRial: Long? = null,
)

@Entity(
    tableName = "sales_day_closures",
    foreignKeys = [ForeignKey(entity = DailySalesSummaryEntity::class, parentColumns = ["id"], childColumns = ["summaryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("businessEpochDay"), Index("createdAtEpochMillis")],
)
data class SalesDayClosureEntity(
    val businessEpochDay: Long,
    @PrimaryKey val summaryId: Long,
    val grossSalesRial: Long,
    val netSalesRial: Long,
    val theoreticalCostRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    @ColumnInfo(defaultValue = "'CLOSED'") val status: String = "CLOSED",
    @ColumnInfo(defaultValue = "1") val revisionNo: Int = 1,
    val closedBy: String,
    val note: String,
    val reopenedBy: String? = null,
    @ColumnInfo(defaultValue = "''") val reopenReason: String = "",
    val reopenedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
)
