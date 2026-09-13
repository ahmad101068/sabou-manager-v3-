package ir.restaurant.management.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "treasury_transactions",
    indices = [
        Index(value = ["commandId"], unique = true),
        Index("businessEpochDay"),
        Index(value = ["sourceType", "sourceId"]),
        Index("journalEntryId"),
        Index("status"),
        Index("reversalOfTransactionId"),
    ],
)
data class TreasuryTransactionEntity(
    @PrimaryKey val id: String,
    val commandId: String,
    val kind: String,
    val businessEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val counterpartyType: String = "",
    val counterpartyId: Long? = null,
    val reference: String = "",
    val reason: String,
    val amountRial: Long,
    val status: String = "POSTED",
    val journalEntryId: Long? = null,
    val reversalOfTransactionId: String? = null,
    val actorId: Long,
    val correlationId: String,
    val createdAtEpochMillis: Long,
    val reversedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "treasury_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = TreasuryTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("transactionId"),
        Index(value = ["accountId", "businessEpochDay"]),
        Index(value = ["sourceType", "sourceId"]),
        Index("direction"),
    ],
)
data class TreasuryLedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val accountId: String,
    val direction: String,
    val amountRial: Long,
    val sourceType: String,
    val sourceId: Long,
    val counterpartyType: String = "",
    val counterpartyId: Long? = null,
    val reference: String = "",
    val businessEpochDay: Long,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "treasury_reconciliations",
    indices = [Index(value = ["accountId", "businessEpochDay"]), Index("transactionId")],
)
data class TreasuryReconciliationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: String,
    val accountId: String,
    val businessEpochDay: Long,
    val expectedRial: Long,
    val actualRial: Long,
    val differenceRial: Long,
    val reason: String,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "recipe_components",
    foreignKeys = [
        ForeignKey(entity = RecipeVersionEntity::class, parentColumns = ["id"], childColumns = ["recipeVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecipeVersionEntity::class, parentColumns = ["id"], childColumns = ["subRecipeVersionId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("recipeVersionId"), Index("subRecipeVersionId")],
)
data class RecipeComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeVersionId: Long,
    val subRecipeVersionId: Long,
    val quantityMicrosPerUnit: Long,
)

@Entity(
    tableName = "recipe_substitutions",
    foreignKeys = [
        ForeignKey(entity = RecipeVersionEntity::class, parentColumns = ["id"], childColumns = ["recipeVersionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["originalInventoryItemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = InventoryItemEntity::class, parentColumns = ["id"], childColumns = ["substituteInventoryItemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("recipeVersionId"), Index("originalInventoryItemId"), Index("substituteInventoryItemId"), Index("effectiveFromEpochDay")],
)
data class RecipeSubstitutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeVersionId: Long,
    val originalInventoryItemId: Long,
    val substituteInventoryItemId: Long,
    val ratioNumerator: Long = 1,
    val ratioDenominator: Long = 1,
    val reason: String,
    val approvedByActorId: Long,
    val createdAtEpochMillis: Long,
    @androidx.room.ColumnInfo(defaultValue = "0") val effectiveFromEpochDay: Long = 0,
)

@Entity(
    tableName = "asset_lifecycle_events",
    foreignKeys = [
        ForeignKey(entity = FixedAssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("assetId"), Index("eventType"), Index("businessEpochDay"), Index("journalEntryId")],
)
data class AssetLifecycleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val eventType: String,
    val businessEpochDay: Long,
    val amountRial: Long = 0,
    val fromLocation: String = "",
    val toLocation: String = "",
    val fromBranch: String = "",
    val toBranch: String = "",
    val fromResponsiblePerson: String = "",
    val toResponsiblePerson: String = "",
    val counterparty: String = "",
    val note: String = "",
    val journalEntryId: Long? = null,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "asset_maintenance",
    foreignKeys = [
        ForeignKey(entity = FixedAssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("assetId"), Index("serviceEpochDay"), Index("nextServiceEpochDay"), Index("supplierId")],
)
data class AssetMaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val serviceType: String,
    val serviceEpochDay: Long,
    val costRial: Long,
    val contractor: String = "",
    val note: String = "",
    val nextServiceEpochDay: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "'BANK'") val paymentSource: String = "BANK",
    val supplierId: Long? = null,
    val payableDueEpochDay: Long? = null,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "customer_receivable_ledger",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["customerId", "businessEpochDay"]), Index(value = ["sourceType", "sourceId"]), Index("entryType")],
)
data class CustomerReceivableLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val businessEpochDay: Long,
    val entryType: String,
    val debitRial: Long,
    val creditRial: Long,
    val sourceType: String,
    val sourceId: Long,
    val reference: String = "",
    val dueEpochDay: Long? = null,
    val reversalOfLedgerId: Long? = null,
    val actorId: Long,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "customer_merge_history",
    indices = [Index("sourceCustomerId"), Index("targetCustomerId"), Index("mergedAtEpochMillis")],
)
data class CustomerMergeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceCustomerId: Long,
    val targetCustomerId: Long,
    val reason: String,
    val actorId: Long,
    val mergedAtEpochMillis: Long,
)
