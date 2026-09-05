package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ir.restaurant.management.core.GlobalId

/** Persistent user data-scope. Owners bypass these grants; every other role is fail-closed. */
@Entity(
    tableName = "user_scope_profiles",
    foreignKeys = [
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["primaryBranchId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("primaryBranchId")],
)
data class UserScopeProfileEntity(
    @PrimaryKey val userId: Long,
    val primaryBranchId: Long?,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "user_branch_scopes",
    primaryKeys = ["userId", "branchId"],
    foreignKeys = [
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("branchId")],
)
data class UserBranchScopeEntity(
    val userId: Long,
    val branchId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "user_warehouse_scopes",
    primaryKeys = ["userId", "locationId"],
    foreignKeys = [
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("locationId")],
)
data class UserWarehouseScopeEntity(
    val userId: Long,
    val locationId: Long,
    val createdAtEpochMillis: Long,
)

/** Immutable evidence of a controlled duplicate-supplier merge. */
@Entity(
    tableName = "supplier_merge_history",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["sourceSupplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["targetSupplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AppUserEntity::class, parentColumns = ["id"], childColumns = ["mergedByActorId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sourceSupplierId"), Index("targetSupplierId"), Index("mergedByActorId")],
)
data class SupplierMergeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSupplierId: Long,
    val targetSupplierId: Long,
    val mergedByActorId: Long,
    val reason: String,
    val createdAtEpochMillis: Long,
)

/** Canonical accounts-payable master. Financial settlement remains Treasury-owned. */
@Entity(
    tableName = "supplier_payables",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["globalId"], unique = true),
        Index(value = ["sourceType", "sourceId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index("supplierId"), Index("branchId"), Index("status"), Index("dueEpochDay"), Index("correlationId"),
    ],
)
data class SupplierPayableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val globalId: String = GlobalId.new().value,
    val supplierId: Long,
    val branchId: Long?,
    val sourceType: String,
    val sourceId: Long,
    val sourceDocumentNo: String,
    val issueEpochDay: Long,
    val dueEpochDay: Long,
    val originalRial: Long,
    val settledRial: Long,
    val status: String,
    val idempotencyKey: String,
    val correlationId: String,
    val createdByActorId: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "supplier_payable_ledger",
    foreignKeys = [
        ForeignKey(entity = SupplierPayableEntity::class, parentColumns = ["id"], childColumns = ["payableId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = BranchEntity::class, parentColumns = ["id"], childColumns = ["branchId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("payableId"), Index("supplierId"), Index("branchId"), Index("businessEpochDay"),
        Index(value = ["commandId"], unique = true), Index("correlationId"), Index("treasuryTransactionId"), Index("journalEntryId"),
    ],
)
data class SupplierPayableLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payableId: Long,
    val supplierId: Long,
    val branchId: Long?,
    val businessEpochDay: Long,
    val entryType: String,
    val amountDeltaRial: Long,
    val treasuryTransactionId: String?,
    val journalEntryId: Long?,
    val commandId: String,
    val correlationId: String,
    val reason: String,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

/** Per-line evidence for partial/multiple-invoice three-way matching. */
@Entity(
    tableName = "procurement_invoice_line_matches",
    foreignKeys = [
        ForeignKey(entity = ProcurementInvoiceLinkEntity::class, parentColumns = ["id"], childColumns = ["invoiceLinkId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseOrderLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseOrderLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseLineId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("invoiceLinkId"), Index("purchaseOrderLineId"), Index(value = ["purchaseLineId"], unique = true)],
)
data class ProcurementInvoiceLineMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceLinkId: Long,
    val purchaseOrderLineId: Long,
    val purchaseLineId: Long,
    val poQtyMicros: Long,
    val receivedQtyMicros: Long,
    val invoiceQtyMicros: Long,
    val poUnitCostRial: Long,
    val invoiceUnitCostRial: Long,
    val quantityVarianceMicros: Long,
    val priceVarianceRial: Long,
)

/** Links accepted procurement quantity to the physical lot actually created/extended by that receipt. */
@Entity(
    tableName = "procurement_receipt_lot_allocations",
    foreignKeys = [
        ForeignKey(entity = GoodsReceiptEntity::class, parentColumns = ["id"], childColumns = ["goodsReceiptId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseOrderLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseOrderLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryLotEntity::class, parentColumns = ["id"], childColumns = ["lotId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("goodsReceiptId"), Index("purchaseOrderLineId"), Index("lotId"), Index(value=["goodsReceiptId","purchaseOrderLineId","lotId"], unique=true)],
)
data class ProcurementReceiptLotAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goodsReceiptId: Long,
    val purchaseOrderLineId: Long,
    val lotId: Long,
    val receivedQuantityMicros: Long,
    @ColumnInfo(defaultValue = "0") val returnedQuantityMicros: Long = 0L,
    val createdAtEpochMillis: Long,
)

data class ReturnableProcurementLotRow(
    val allocationId: Long,
    val lotId: Long,
    val goodsReceiptId: Long,
    val purchaseOrderLineId: Long,
    val receivedQuantityMicros: Long,
    val returnedQuantityMicros: Long,
    val currentLotQuantityMicros: Long,
    val lotUnitCostRial: Long,
    val receivedEpochDay: Long,
    val status: String,
)

/** Financial allocation evidence used to apply supplier returns against the exact matched invoices. */
data class ProcurementReturnInvoiceMatchRow(
    val purchaseId: Long,
    val purchaseOrderLineId: Long,
    val invoiceQtyMicros: Long,
    val invoiceUnitCostRial: Long,
    val matchedAtEpochMillis: Long,
)
