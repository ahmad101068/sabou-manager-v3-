package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_alerts",
    indices = [
        Index(value = ["sourceType", "sourceId", "locationId"], unique = true),
        Index("severity"),
        Index("isRead"),
        Index("isDismissed"),
        Index("dueEpochDay"),
        Index("branchId"),
        Index("locationId"),
        Index("snoozedUntilEpochMillis"),
    ],
)
data class AppAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: String,
    val sourceId: Long,
    val title: String,
    val message: String,
    val severity: String,
    val dueEpochDay: Long?,
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "'NEW'") val status: String = "NEW",
    @ColumnInfo(defaultValue = "0") val branchId: Long = 0,
    @ColumnInfo(defaultValue = "0") val locationId: Long = 0,
    val snoozedUntilEpochMillis: Long? = null,
)

data class OverdueSaleAlertRow(
    val id: Long,
    val invoiceNo: String,
    val customerName: String,
    val dueEpochDay: Long,
    val outstandingRial: Long,
)

data class OverduePurchaseAlertRow(
    val id: Long,
    val invoiceNo: String,
    val supplierName: String,
    val dueEpochDay: Long,
    val outstandingRial: Long,
)


data class GeneratedAlertRow(
    val sourceId: Long,
    val title: String,
    val message: String,
    val severity: String,
    val dueEpochDay: Long?,
    val branchId: Long = 0,
    val locationId: Long = 0,
)
