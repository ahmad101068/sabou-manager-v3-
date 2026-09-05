package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["customerCode"], unique = true),
        Index("name"),
        Index("phone"),
        Index("nationalId"),
        Index("isActive"),
    ],
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerCode: String,
    val name: String,
    val phone: String,
    val nationalId: String,
    val creditLimitRial: Long,
    val notes: String,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val mobile: String = "",
    @ColumnInfo(defaultValue = "''") val address: String = "",
    @ColumnInfo(defaultValue = "''") val branch: String = "",
    @ColumnInfo(defaultValue = "0") val paymentTermsDays: Int = 0,
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    @ColumnInfo(defaultValue = "'PERSON'") val partyType: String = "PERSON",
)

@Entity(
    tableName = "sales_invoices",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["invoiceNo"], unique = true),
        Index(value = ["commandId"], unique = true),
        Index(value = ["voidCommandId"], unique = true),
        Index("businessEpochDay"),
        Index("customerId"),
        Index("status"),
        Index("createdAtEpochMillis"),
        Index("branchName"),
    ],
)
data class SalesInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val commandId: String,
    val businessEpochDay: Long,
    @ColumnInfo(defaultValue = "''") val branchName: String = "",
    val customerId: Long?,
    val dueEpochDay: Long?,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val creditRial: Long,
    val theoreticalCostRial: Long,
    val journalEntryId: Long?,
    val cogsJournalEntryId: Long?,
    val status: String,
    val notes: String,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
    val voidedAtEpochDay: Long? = null,
    val voidCommandId: String? = null,
    val voidReason: String = "",
    val voidJournalEntryId: Long? = null,
    val voidCogsJournalEntryId: Long? = null,
)

@Entity(
    tableName = "sales_invoice_lines",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MenuItemEntity::class, parentColumns = ["id"], childColumns = ["menuItemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecipeVersionEntity::class, parentColumns = ["id"], childColumns = ["recipeVersionId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("invoiceId"), Index("menuItemId"), Index("recipeVersionId")],
)
data class SalesInvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val menuItemId: Long,
    val recipeVersionId: Long,
    val menuItemNameSnapshot: String,
    val quantityMicros: Long,
    val unitPriceRial: Long,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val foodCostSnapshotRial: Long,
    val packagingCostSnapshotRial: Long,
    val directLaborCostSnapshotRial: Long,
    val allocatedOverheadSnapshotRial: Long,
)

@Entity(
    tableName = "sales_payments",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("invoiceId"), Index("method")],
)
data class SalesPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val method: String,
    val amountRial: Long,
    val referenceNo: String,
)

/** Immutable inventory/cost snapshot used for deterministic partial returns. */
@Entity(
    tableName = "sales_consumption_snapshots",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceLineEntity::class, parentColumns = ["id"], childColumns = ["invoiceLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["inventoryItemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["invoiceLineId", "inventoryItemId"], unique = true),
        Index("inventoryItemId"),
    ],
)
data class SalesConsumptionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceLineId: Long,
    val inventoryItemId: Long,
    val inventoryItemNameSnapshot: String,
    val quantityMicros: Long,
    val valueRial: Long,
)

@Entity(
    tableName = "sales_returns",
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["returnNo"], unique = true),
        Index(value = ["commandId"], unique = true),
        Index("invoiceId"),
        Index("returnEpochDay"),
    ],
)
data class SalesReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNo: String,
    val commandId: String,
    val invoiceId: Long,
    val returnEpochDay: Long,
    val refundMethod: String,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val refundRial: Long,
    val cogsRial: Long,
    val reason: String,
    val journalEntryId: Long?,
    val cogsJournalEntryId: Long?,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "sales_return_lines",
    foreignKeys = [
        ForeignKey(entity = SalesReturnEntity::class, parentColumns = ["id"], childColumns = ["returnId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesInvoiceLineEntity::class, parentColumns = ["id"], childColumns = ["invoiceLineId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("returnId"), Index("invoiceLineId")],
)
data class SalesReturnLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val invoiceLineId: Long,
    val quantityMicros: Long,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val cogsRial: Long,
)

@Entity(
    tableName = "invoice_sales_day_closures",
    indices = [Index("status"), Index("createdAtEpochMillis")],
)
data class InvoiceSalesDayClosureEntity(
    @PrimaryKey val businessEpochDay: Long,
    val grossSalesRial: Long,
    val netSalesRial: Long,
    val returnRial: Long,
    val cogsRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val creditRial: Long,
    val invoiceCount: Int,
    val returnCount: Int,
    val status: String = "CLOSED",
    val revisionNo: Int = 1,
    val closedByActorId: Long,
    val closedByName: String,
    val note: String,
    val reopenedByActorId: Long? = null,
    val reopenedByName: String? = null,
    val reopenReason: String = "",
    val reopenedAtEpochMillis: Long? = null,
    val createdAtEpochMillis: Long,
)

data class CustomerWithOutstandingRow(
    val id: Long,
    val customerCode: String,
    val name: String,
    val phone: String,
    val nationalId: String,
    val creditLimitRial: Long,
    val notes: String,
    val isActive: Boolean,
    val mobile: String,
    val address: String,
    val branch: String,
    val paymentTermsDays: Int,
    val status: String,
    val partyType: String,
    val outstandingRial: Long,
)

data class SalesDashboardSummaryRow(
    val netSalesRial: Long,
    val invoiceNetRial: Long,
    val invoiceCount: Int,
    val returnRial: Long,
    val newCustomerCount: Int,
    val customerReceivablesRial: Long,
)

data class SalesInvoiceListRow(
    val id: Long,
    val invoiceNo: String,
    val businessEpochDay: Long,
    val customerName: String?,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
)

data class SalesInvoiceHeaderRow(
    val id: Long,
    val invoiceNo: String,
    val businessEpochDay: Long,
    val customerId: Long?,
    val customerName: String?,
    val dueEpochDay: Long?,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
)

data class SalesInvoiceLineRow(
    val id: Long,
    val menuItemId: Long,
    val recipeVersionId: Long,
    val menuItemNameSnapshot: String,
    val quantityMicros: Long,
    val unitPriceRial: Long,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val returnedQuantityMicros: Long,
)

data class SalesReturnedTotalsRow(
    val invoiceLineId: Long,
    val quantityMicros: Long,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val cogsRial: Long,
)


data class SalesDashboardKpiRow(
    val todayInvoiceCount: Long,
    val todayReturnCount: Long,
    val todayNetSalesRial: Long,
    val todayCogsRial: Long,
    val customerReceivablesRial: Long,
)


data class SalesDayTotalsRow(
    val grossSalesRial: Long,
    val netSalesRial: Long,
    val returnRial: Long,
    val cogsRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val creditRial: Long,
    val invoiceCount: Long,
    val returnCount: Long,
)
