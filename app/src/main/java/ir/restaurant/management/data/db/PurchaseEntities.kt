package ir.restaurant.management.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.inventory.InventorySku

@Entity(
    tableName = "suppliers",
    indices = [
        Index(value = ["code"], unique = true),
        Index("name"),
        Index(value = ["normalizedName"]),
        Index(value = ["legalId"], unique = true),
        Index(value = ["bankIban"], unique = true),
    ],
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val code: String = "",
    val name: String,
    @ColumnInfo(defaultValue = "''") val normalizedName: String = "",
    @ColumnInfo(defaultValue = "'COMPANY'") val partyType: String = "COMPANY",
    val legalId: String? = null,
    val economicCode: String? = null,
    val bankIban: String? = null,
    val contactName: String = "",
    val phone: String = "",
    val address: String = "",
    val paymentTermsDays: Int = 0,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["sku"], unique = true),
        Index(value = ["primaryBarcode"], unique = true),
        Index("itemType"),
        Index("category"),
        Index("isActive"),
        Index("supplierId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val unit: String,
    @ColumnInfo(defaultValue = "''") val sku: String = InventorySku.generated().value,
    @ColumnInfo(defaultValue = "'INGREDIENT'") val itemType: String = "INGREDIENT",
    val purchaseUnit: String = unit,
    val purchaseToStockNumerator: Long = 1,
    val purchaseToStockDenominator: Long = 1,
    val recipeUnit: String = unit,
    val recipeToStockNumerator: Long = 1,
    val recipeToStockDenominator: Long = 1,
    val stockMicros: Long = 0,
    val inventoryValueRial: Long = 0,
    val alertEnabled: Boolean = true,
    val alertThresholdMicros: Long = 0,
    val supplierId: Long? = null,
    val primaryBarcode: String? = null,
    @ColumnInfo(defaultValue = "''") val brand: String = "",
    @ColumnInfo(defaultValue = "'AMBIENT'") val storageCondition: String = "AMBIENT",
    val shelfLifeDays: Int? = null,
    @ColumnInfo(defaultValue = "0") val trackLot: Boolean = false,
    @ColumnInfo(defaultValue = "0") val trackExpiry: Boolean = false,
    @ColumnInfo(defaultValue = "0") val minimumStockMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val maximumStockMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val safetyStockMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val reorderPointMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val leadTimeDays: Int = 0,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["supplierId", "normalizedInvoiceNo"], unique = true),
        Index("supplierId"),
        Index("purchaseEpochDay"),
        Index("dueEpochDay"),
        Index("branchName"),
        Index("branchId"),
        Index("locationId"),
        Index(value = ["commandId"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    @ColumnInfo(defaultValue = "''") val normalizedInvoiceNo: String = "",
    val supplierId: Long,
    val purchaseEpochDay: Long,
    @ColumnInfo(defaultValue = "''") val branchName: String = "",
    val branchId: Long? = null,
    val locationId: Long? = null,
    @ColumnInfo(defaultValue = "''") val commandId: String = "",
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("purchaseId"), Index("itemId")],
)
data class PurchaseLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("itemId"),
        Index("movementEpochDay"),
        Index(value = ["referenceType", "referenceId"]),
        Index(value = ["globalId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index("correlationId"),
        Index("actorId"),
        Index("locationId"),
        Index(value = ["itemId", "locationId", "movementEpochDay"]),
        Index(value = ["locationId", "movementEpochDay"]),
        Index(value = ["reversalOfMovementId"], unique = true),
    ],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val movementType: String,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val referenceType: String,
    val referenceId: Long,
    val movementEpochDay: Long,
    val notes: String,
    val createdAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    @ColumnInfo(defaultValue = "''") val idempotencyKey: String = "auto:${GlobalId.new().value}",
    @ColumnInfo(defaultValue = "''") val correlationId: String = "local:${GlobalId.new().value}",
    val actorId: Long? = null,
    @ColumnInfo(defaultValue = "'legacy-unknown'") val deviceId: String = "legacy-unknown",
    val locationId: Long? = null,
    @ColumnInfo(defaultValue = "0") val unitCostRial: Long = 0,
    @ColumnInfo(defaultValue = "'LEGACY'") val reasonCode: String = "LEGACY",
    val reversalOfMovementId: Long? = null,
)

@Entity(
    tableName = "purchase_requisitions",
    indices = [
        Index(value = ["requestNo"], unique = true),
        Index(value = ["globalId"], unique = true),
        Index("status"),
        Index("requiredEpochDay"),
        Index("requestedByActorId"),
        Index("approvedByActorId"),
        Index("branchId"),
        Index("destinationLocationId"),
        Index("correlationId"),
    ],
)
data class PurchaseRequisitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestNo: String,
    val department: String,
    val requiredEpochDay: Long,
    val branchId: Long? = null,
    val destinationLocationId: Long? = null,
    val status: String,
    val requestedBy: String,
    val approvedBy: String?,
    @androidx.room.ColumnInfo(defaultValue = "1") val requiredApprovalLevel: Int = 1,
    @androidx.room.ColumnInfo(defaultValue = "0") val completedApprovalLevel: Int = 0,
    val firstApprovedBy: String? = null,
    val secondApprovedBy: String? = null,
    val committedBudgetId: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val committedBudgetRial: Long = 0,
    val note: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "''") val globalId: String = GlobalId.new().value,
    val requestedByActorId: Long? = null,
    val approvedByActorId: Long? = null,
    val firstApprovedByActorId: Long? = null,
    val secondApprovedByActorId: Long? = null,
    @ColumnInfo(defaultValue = "''") val correlationId: String = "",
)

@Entity(
    tableName = "purchase_requisition_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseRequisitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["requisitionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("requisitionId"), Index("itemId"), Index("recommendedSupplierId")],
)
data class PurchaseRequisitionLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requisitionId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val requestedQtyMicros: Long,
    val estimatedUnitCostRial: Long,
    val recommendedSupplierId: Long?,
    val supplierSkuSnapshot: String?,
    val recommendedLeadTimeDays: Int?,
    val note: String,
)

@Entity(
    tableName = "purchase_orders",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseRequisitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["requisitionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["orderNo"], unique = true),
        Index("supplierId"),
        Index("requisitionId"),
        Index("branchId"),
        Index("destinationLocationId"),
        Index("status"),
        Index("expectedEpochDay"),
    ],
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNo: String,
    val supplierId: Long,
    val supplierNameSnapshot: String,
    val requisitionId: Long,
    val branchId: Long? = null,
    val destinationLocationId: Long? = null,
    val orderEpochDay: Long,
    val expectedEpochDay: Long,
    val sentAtEpochMillis: Long?,
    val sentBy: String?,
    val dispatchChannel: String?,
    val acknowledgedAtEpochMillis: Long?,
    val supplierConfirmationNo: String?,
    val confirmedExpectedEpochDay: Long?,
    val status: String,
    val note: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "purchase_order_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseOrderId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("purchaseOrderId"), Index("itemId")],
)
data class PurchaseOrderLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseOrderId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val supplierSkuSnapshot: String?,
    val orderedQtyMicros: Long,
    val unitCostRial: Long,
    val receivedQtyMicros: Long,
    val rejectedQtyMicros: Long,
    val returnedQtyMicros: Long = 0,
)

@Entity(
    tableName = "goods_receipts",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseOrderId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["receiptNo"], unique = true),
        Index(value = ["purchaseOrderId", "deliveryNoteNo"], unique = true),
        Index("purchaseOrderId"),
        Index("branchId"),
        Index("destinationLocationId"),
        Index("receiptEpochDay"),
    ],
)
data class GoodsReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val purchaseOrderId: Long,
    val branchId: Long? = null,
    val destinationLocationId: Long? = null,
    val receiptEpochDay: Long,
    val deliveryNoteNo: String,
    val receivedBy: String,
    val note: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "goods_receipt_lines",
    foreignKeys = [
        ForeignKey(
            entity = GoodsReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["goodsReceiptId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseOrderLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseOrderLineId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("goodsReceiptId"), Index("purchaseOrderLineId")],
)
data class GoodsReceiptLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goodsReceiptId: Long,
    val purchaseOrderLineId: Long,
    val itemId: Long,
    val deliveredQtyMicros: Long,
    val acceptedQtyMicros: Long,
    val rejectedQtyMicros: Long,
    val rejectionReason: String,
    val acceptedValueRial: Long,
    val lotNumber: String? = null,
    val supplierLotNumber: String? = null,
    val productionEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val lotBarcode: String? = null,
)

@Entity(
    tableName = "procurement_invoice_links",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseOrderId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("purchaseOrderId"),
        Index(value = ["purchaseId"], unique = true),
        Index("branchId"),
        Index("matchStatus"),
    ],
)
data class ProcurementInvoiceLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseOrderId: Long,
    val purchaseId: Long,
    val branchId: Long? = null,
    val matchStatus: String,
    val acceptedValueRial: Long,
    val invoiceValueRial: Long,
    val priceVarianceRial: Long,
    val varianceApprovedBy: String?,
    val matchedAtEpochMillis: Long,
)

@Entity(
    tableName = "purchase_returns",
    foreignKeys = [
        ForeignKey(entity = PurchaseOrderEntity::class, parentColumns = ["id"], childColumns = ["purchaseOrderId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["returnNo"], unique = true),
        Index("purchaseOrderId"),
        Index("purchaseId"),
        Index("supplierId"),
        Index("branchId"),
        Index("locationId"),
        Index("returnEpochDay"),
    ],
)
data class PurchaseReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNo: String,
    val purchaseOrderId: Long,
    val purchaseId: Long?,
    val supplierId: Long,
    val branchId: Long? = null,
    val locationId: Long? = null,
    val returnEpochDay: Long,
    val reason: String,
    val returnedBy: String,
    val inventoryValueRial: Long,
    val supplierCreditValueRial: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "purchase_return_lines",
    foreignKeys = [
        ForeignKey(entity = PurchaseReturnEntity::class, parentColumns = ["id"], childColumns = ["purchaseReturnId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseOrderLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseOrderLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("purchaseReturnId"), Index("purchaseOrderLineId"), Index("itemId"), Index("lotId"), Index("locationId")],
)
data class PurchaseReturnLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseReturnId: Long,
    val purchaseOrderLineId: Long,
    val itemId: Long,
    val lotId: Long? = null,
    val locationId: Long? = null,
    val quantityMicros: Long,
    val inventoryUnitCostRial: Long,
    val supplierUnitCreditRial: Long,
    val inventoryValueRial: Long,
    val supplierCreditValueRial: Long,
    val reason: String,
)

@Entity(
    tableName = "supplier_credits",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseReturnEntity::class, parentColumns = ["id"], childColumns = ["sourceReturnId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["appliedPurchaseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["creditNo"], unique = true),
        Index("supplierId"),
        Index(value = ["sourceReturnId"], unique = true),
        Index("appliedPurchaseId"),
        Index("status"),
    ],
)
data class SupplierCreditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditNo: String,
    val supplierId: Long,
    val sourceReturnId: Long,
    val appliedPurchaseId: Long?,
    val amountRial: Long,
    val appliedRial: Long,
    val status: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_replenishment_policies",
    foreignKeys = [
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["preferredSupplierId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("preferredSupplierId"), Index("isEnabled")],
)
data class InventoryReplenishmentPolicyEntity(
    @PrimaryKey val itemId: Long,
    val preferredSupplierId: Long?,
    val targetCoverDays: Int,
    val leadTimeDays: Int,
    val safetyStockMicros: Long,
    val orderMultipleMicros: Long,
    val isEnabled: Boolean,
    val updatedBy: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "supplier_item_offers",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["supplierId", "itemId"], unique = true), Index("itemId"), Index("validUntilEpochDay"), Index("isActive")],
)
data class SupplierItemOfferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val itemId: Long,
    val supplierSku: String,
    val unitCostRial: Long,
    val minimumOrderMicros: Long,
    val orderMultipleMicros: Long,
    val leadTimeDays: Int,
    val validUntilEpochDay: Long?,
    val isActive: Boolean,
    val updatedBy: String,
    val updatedAtEpochMillis: Long,
)
