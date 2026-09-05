package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fixed_assets", indices = [Index(value=["assetCode"], unique=true), Index("status"), Index("branchId"), Index("supplierId")])
data class FixedAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetCode: String,
    val name: String,
    val category: String,
    val quantity: Int = 1,
    val purchaseEpochDay: Long,
    val purchaseCostRial: Long,
    val salvageValueRial: Long,
    val usefulLifeMonths: Int,
    val accumulatedDepreciationRial: Long = 0,
    val location: String = "",
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val branch: String = "",
    val branchId: Long? = null,
    @ColumnInfo(defaultValue = "''") val responsiblePerson: String = "",
    @ColumnInfo(defaultValue = "0") val impairmentRial: Long = 0,
    val disposedEpochDay: Long? = null,
    val salePriceRial: Long? = null,
    @ColumnInfo(defaultValue = "'BANK'") val acquisitionSource: String = "BANK",
    val supplierId: Long? = null,
    val payableDueEpochDay: Long? = null,
)

@Entity(
    tableName = "asset_depreciations",
    foreignKeys=[ForeignKey(entity=FixedAssetEntity::class,parentColumns=["id"],childColumns=["assetId"],onDelete=ForeignKey.RESTRICT)],
    indices=[
        Index(value=["assetId","periodYear","periodMonth"]),
        Index("journalEntryId"),
        Index("reversalJournalEntryId"),
        Index(value=["commandId"], unique=true),
    ],
)
data class AssetDepreciationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val periodYear: Int,
    val periodMonth: Int,
    val amountRial: Long,
    val journalEntryId: Long,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1,
    @ColumnInfo(defaultValue = "0") val postingEpochDay: Long = 0,
    @ColumnInfo(defaultValue = "''") val reason: String = "",
    val commandId: String? = null,
    val reversedAtEpochMillis: Long? = null,
    val reversalEpochDay: Long? = null,
    @ColumnInfo(defaultValue = "''") val reversalReason: String = "",
    val reversalJournalEntryId: Long? = null,
)
