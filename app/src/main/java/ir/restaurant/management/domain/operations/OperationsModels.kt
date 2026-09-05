package ir.restaurant.management.domain.operations

import ir.restaurant.management.domain.inventory.InventoryLocationRecord
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryItemType
import ir.restaurant.management.domain.inventory.InventorySku
import ir.restaurant.management.domain.inventory.InventoryStorageCondition
import ir.restaurant.management.domain.inventory.ItemBarcode
import ir.restaurant.management.core.SignedLongMath
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

enum class SupplierPartyType { PERSON, COMPANY }

data class SupplierRecord(
    val id: Long,
    val code: String = "",
    val name: String,
    val partyType: SupplierPartyType = SupplierPartyType.COMPANY,
    val legalId: String? = null,
    val economicCode: String? = null,
    val bankIban: String? = null,
    val contactName: String,
    val phone: String,
    val address: String,
    val paymentTermsDays: Int,
    val notes: String,
    val isActive: Boolean = true,
)

data class SupplierMergeDraft(
    val sourceSupplierId: Long,
    val targetSupplierId: Long,
    val reason: String,
) {
    fun validated(): SupplierMergeDraft {
        require(sourceSupplierId > 0 && targetSupplierId > 0 && sourceSupplierId != targetSupplierId) { "تأمین‌کنندگان ادغام معتبر نیستند." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 5..300) { "دلیل ادغام تأمین‌کننده باید بین ۵ تا ۳۰۰ نویسه باشد." }
        return copy(reason = normalizedReason)
    }
}

data class SupplierDraft(
    val name: String,
    val partyType: SupplierPartyType = SupplierPartyType.COMPANY,
    val legalId: String? = null,
    val economicCode: String? = null,
    val bankIban: String? = null,
    val contactName: String = "",
    val phone: String = "",
    val address: String = "",
    val paymentTermsDays: Int = 0,
    val notes: String = "",
) {
    fun validated(): SupplierDraft {
        val normalizedName = name.trim().replace(Regex("\\s+"), " ")
        val normalizedPhone = phone.trim().replace(" ", "").replace("-", "")
        val normalizedLegalId = legalId?.filter(Char::isDigit)?.ifBlank { null }
        val normalizedEconomicCode = economicCode?.trim()?.ifBlank { null }
        val normalizedIban = bankIban?.replace(" ", "")?.uppercase()?.ifBlank { null }
        require(normalizedName.length in 2..120) {
            "نام تأمین‌کننده باید بین ۲ تا ۱۲۰ نویسه باشد."
        }
        require(paymentTermsDays in 0..3650) {
            "مهلت پرداخت معتبر نیست."
        }
        require(normalizedPhone.length <= 30) {
            "شماره تماس بیش از حد طولانی است."
        }
        require(normalizedLegalId == null || normalizedLegalId.length in 8..14) {
            "شناسه ملی/شناسه حقوقی تأمین‌کننده معتبر نیست."
        }
        require(normalizedEconomicCode == null || normalizedEconomicCode.length <= 30) {
            "کد اقتصادی بیش از حد طولانی است."
        }
        require(normalizedIban == null || normalizedIban.matches(Regex("IR\\d{24}"))) {
            "شماره شبای تأمین‌کننده معتبر نیست."
        }
        require(address.trim().length <= 500) { "نشانی بیش از حد طولانی است." }
        require(notes.trim().length <= 1000) { "یادداشت بیش از حد طولانی است." }
        return copy(
            name = normalizedName,
            legalId = normalizedLegalId,
            economicCode = normalizedEconomicCode,
            bankIban = normalizedIban,
            contactName = contactName.trim(),
            phone = normalizedPhone,
            address = address.trim(),
            notes = notes.trim(),
        )
    }
}

data class InventoryItemRecord(
    val id: Long,
    val name: String,
    val category: String,
    val unit: String,
    val purchaseUnit: String = unit,
    val purchaseToStockNumerator: Long = 1,
    val purchaseToStockDenominator: Long = 1,
    val recipeUnit: String = unit,
    val recipeToStockNumerator: Long = 1,
    val recipeToStockDenominator: Long = 1,
    val stockMicros: Long,
    val inventoryValueRial: Long,
    val alertEnabled: Boolean,
    val alertThresholdMicros: Long,
    val supplierId: Long?,
    val sku: String = "",
    val itemType: InventoryItemType = InventoryItemType.INGREDIENT,
    val primaryBarcode: String? = null,
    val brand: String = "",
    val storageCondition: InventoryStorageCondition = InventoryStorageCondition.AMBIENT,
    val shelfLifeDays: Int? = null,
    val trackLot: Boolean = false,
    val trackExpiry: Boolean = false,
    val minimumStockMicros: Long = 0,
    val maximumStockMicros: Long = 0,
    val safetyStockMicros: Long = 0,
    val reorderPointMicros: Long = 0,
    val leadTimeDays: Int = 0,
)

data class InventoryItemDraft(
    val name: String,
    val category: String,
    val unit: String,
    val purchaseUnit: String = unit,
    val purchaseToStockNumerator: Long = 1,
    val purchaseToStockDenominator: Long = 1,
    val recipeUnit: String = unit,
    val recipeToStockNumerator: Long = 1,
    val recipeToStockDenominator: Long = 1,
    val alertEnabled: Boolean,
    val alertThresholdMicros: Long,
    val supplierId: Long?,
    val sku: String = "",
    val itemType: InventoryItemType = InventoryItemType.INGREDIENT,
    val primaryBarcode: String? = null,
    val brand: String = "",
    val storageCondition: InventoryStorageCondition = InventoryStorageCondition.AMBIENT,
    val shelfLifeDays: Int? = null,
    val trackLot: Boolean = false,
    val trackExpiry: Boolean = false,
    val minimumStockMicros: Long = 0,
    val maximumStockMicros: Long = 0,
    val safetyStockMicros: Long = 0,
    val reorderPointMicros: Long = 0,
    val leadTimeDays: Int = 0,
) {
    fun validated(): InventoryItemDraft {
        val normalizedName = name.trim()
        val normalizedCategory = category.trim()
        val normalizedUnit = unit.trim()
        require(normalizedName.length in 2..120) {
            "نام کالا باید بین ۲ تا ۱۲۰ نویسه باشد."
        }
        require(normalizedCategory.isNotEmpty()) {
            "دسته‌بندی کالا را وارد کنید."
        }
        require(normalizedUnit.isNotEmpty()) {
            "واحد شمارش کالا را وارد کنید."
        }
        require(purchaseUnit.trim().isNotEmpty() && recipeUnit.trim().isNotEmpty()) { "واحد خرید و رسپی الزامی است." }
        UnitConversionFactor(purchaseToStockNumerator, purchaseToStockDenominator)
        UnitConversionFactor(recipeToStockNumerator, recipeToStockDenominator)
        require(alertThresholdMicros >= 0) {
            "حد هشدار موجودی نمی‌تواند منفی باشد."
        }
        val normalizedSku = sku.takeIf { it.isNotBlank() }?.let { InventorySku.parse(it).value }.orEmpty()
        val normalizedBarcode = primaryBarcode?.takeIf { it.isNotBlank() }?.let { ItemBarcode.parse(it).value }
        listOf(minimumStockMicros, maximumStockMicros, safetyStockMicros, reorderPointMicros).forEach {
            require(it >= 0) { "حدهای موجودی نمی‌توانند منفی باشند." }
        }
        require(maximumStockMicros == 0L || minimumStockMicros <= maximumStockMicros) {
            "حداقل موجودی از حداکثر بیشتر است."
        }
        require(!trackExpiry || trackLot) { "ردیابی تاریخ انقضا به ردیابی لات نیاز دارد." }
        require(shelfLifeDays == null || shelfLifeDays in 1..3_650) { "عمر ماندگاری معتبر نیست." }
        require(leadTimeDays in 0..365) { "زمان تأمین معتبر نیست." }
        return copy(
            name = normalizedName,
            category = normalizedCategory,
            unit = normalizedUnit,
            purchaseUnit = purchaseUnit.trim(),
            recipeUnit = recipeUnit.trim(),
            sku = normalizedSku,
            primaryBarcode = normalizedBarcode,
            brand = brand.trim().take(80),
        )
    }
}

data class PurchaseDashboardSummary(
    val periodPurchaseRial: Long = 0L,
    val openOrderCount: Int = 0,
    val activeSupplierCount: Int = 0,
    val supplierPayablesRial: Long = 0L,
    val pendingReceiptCount: Int = 0,
    val openRequisitionCount: Int = 0,
    val pendingApprovalCount: Int = 0,
    val overdueOrderCount: Int = 0,
)

data class PurchaseSummary(
    val id: Long,
    val invoiceNo: String,
    val supplierName: String,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: PurchasePaymentStatus,
    val paymentMethod: PurchasePaymentMethod,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
) {
    val outstandingRial: Long get() = totalRial - paidRial
    val isPaid: Boolean get() = paymentStatus == PurchasePaymentStatus.PAID

    fun reminderIsDue(todayEpochDay: Long): Boolean =
        paymentStatus in setOf(PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIAL) &&
            outstandingRial > 0 &&
            reminderEnabled &&
            reminderEpochDay != null &&
            reminderEpochDay <= todayEpochDay
}


data class InventoryCountDraft(
    val itemId: Long,
    val countedQuantityMicros: Long,
    val countedValueRial: Long,
    val countEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
    val locationId: Long = 0L,
) {
    fun validated(): InventoryCountDraft {
        require(itemId > 0) { "کالای انبارگردانی نامعتبر است." }
        require(countedQuantityMicros >= 0) { "موجودی شمارش‌شده نمی‌تواند منفی باشد." }
        require(countedValueRial >= 0) { "ارزش شمارش‌شده نمی‌تواند منفی باشد." }
        require(countEpochDay > 0) { "تاریخ انبارگردانی معتبر نیست." }
        require(locationId > 0) { "انبار/محل انبارگردانی باید صریح انتخاب شود." }
        require(reason.trim().length in 3..300) { "دلیل اصلاح موجودی را وارد کنید." }
        return copy(reason = reason.trim(), commandId = GlobalId.parse(commandId).value)
    }
}

data class InventoryCountRecord(val id: Long, val itemId: Long, val previousQuantityMicros: Long, val countedQuantityMicros: Long, val previousValueRial: Long, val countedValueRial: Long, val countEpochDay: Long, val reason: String)

data class InventoryPeriodCloseDraft(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val note: String = "",
) {
    fun validated(): InventoryPeriodCloseDraft {
        require(fromEpochDay > 0 && toEpochDay >= fromEpochDay) { "بازه بستن انبار معتبر نیست." }
        return copy(note = note.trim())
    }
}

data class InventoryPeriodReopenDraft(val closureId: Long, val reason: String) {
    fun validated(): InventoryPeriodReopenDraft {
        require(closureId > 0) { "دوره انبار معتبر نیست." }
        val normalized = reason.trim()
        require(normalized.length in 5..300) { "دلیل بازگشایی باید بین ۵ تا ۳۰۰ نویسه باشد." }
        return copy(reason = normalized)
    }
}

enum class InventoryPeriodStatus(val storedValue: String) {
    CLOSED("CLOSED"),
    REOPENED("REOPENED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryPeriodStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class InventoryPeriodClosureRecord(
    val id: Long,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val openingValueRial: Long,
    val netPurchaseValueRial: Long,
    val recordedOutflowValueRial: Long,
    val expectedClosingValueRial: Long,
    val countedClosingValueRial: Long,
    val varianceValueRial: Long,
    val itemCount: Int,
    val status: InventoryPeriodStatus,
    val revisionNo: Int,
    val closedBy: String,
    val note: String,
    val reopenedBy: String? = null,
    val reopenReason: String = "",
)

data class InventoryPeriodLineCalculation(
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

data class InventoryPeriodClosureLineRecord(
    val itemId: Long,
    val itemName: String,
    val unit: String,
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
) {
    val varianceQuantityMicros: Long get() = SignedLongMath.subtract(countedClosingQuantityMicros, expectedClosingQuantityMicros)
    val varianceValueRial: Long get() = SignedLongMath.subtract(countedClosingValueRial, expectedClosingValueRial)
}

data class InventoryPeriodClosureDetails(
    val closure: InventoryPeriodClosureRecord,
    val lines: List<InventoryPeriodClosureLineRecord>,
)
data class AuditLogRecord(
    val id: Long,
    val action: String,
    val entityType: String,
    val entityId: Long?,
    val description: String,
    val actor: String,
    val createdAtEpochMillis: Long,
    val actorId: Long? = null,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val reason: String = "",
    val beforeSnapshot: String? = null,
    val afterSnapshot: String? = null,
    val correlationId: String = "",
    val actorRoleSnapshot: String = "UNKNOWN",
    val actorBranchIdSnapshot: Long? = null,
)

data class AuditLogQuery(
    val search: String = "",
    val actor: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: Long? = null,
    val sourceReference: String = "",
    val severity: String = "",
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
)

data class InventoryUsageInsight(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val usageMicros30Days: Long,
    val averageDailyUsageMicros: Long,
)

data class SupplierPriceInsight(
    val itemId: Long,
    val itemName: String,
    val supplierName: String,
    val latestUnitCostRial: Long,
    val previousUnitCostRial: Long,
) {
    val changePercent: Int
        get() = if (previousUnitCostRial <= 0) 0
        else BigInteger.valueOf(latestUnitCostRial)
            .subtract(BigInteger.valueOf(previousUnitCostRial))
            .multiply(BigInteger.valueOf(100L))
            .divide(BigInteger.valueOf(previousUnitCostRial))
            .coerceIn(BigInteger.valueOf(Int.MIN_VALUE.toLong()), BigInteger.valueOf(Int.MAX_VALUE.toLong()))
            .toInt()
}

data class WasteDraft(
    val itemId: Long,
    val quantityMicros: Long,
    val wasteEpochDay: Long,
    val reason: String,
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): WasteDraft {
        require(itemId > 0) { "کالای ضایعات نامعتبر است." }
        require(quantityMicros > 0) { "مقدار ضایعات باید بیشتر از صفر باشد." }
        require(wasteEpochDay > 0) { "تاریخ ضایعات معتبر نیست." }
        require(reason.trim().length in 2..120) { "علت ضایعات را وارد کنید." }
        return copy(
            reason = reason.trim(),
            notes = notes.trim(),
            commandId = GlobalId.parse(commandId).value,
        )
    }
}

data class WasteRecord(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val valueRial: Long,
    val wasteEpochDay: Long,
    val reason: String,
)

data class StockMovementRecord(
    val id: Long,
    val itemId: Long,
    val movementType: InventoryMovementType,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val referenceType: InventoryReferenceType,
    val referenceId: Long,
    val movementEpochDay: Long,
    val notes: String,
)

interface OperationsRepository {
    val suppliers: Flow<List<SupplierRecord>>
    val inventoryLocations: Flow<List<InventoryLocationRecord>>
    val inventoryItems: Flow<List<InventoryItemRecord>>
    val lowStockItems: Flow<List<InventoryItemRecord>>
    val inventoryCounts: Flow<List<InventoryCountRecord>>
    val inventoryPeriodClosures: Flow<List<InventoryPeriodClosureRecord>>
    fun inventoryPeriodClosureDetails(closureId: Long): Flow<InventoryPeriodClosureDetails?>
    fun auditLogs(query: AuditLogQuery = AuditLogQuery()): Flow<List<AuditLogRecord>>
    val usageInsights: Flow<List<InventoryUsageInsight>>
    val supplierPriceInsights: Flow<List<SupplierPriceInsight>>
    val wasteRecords: Flow<List<WasteRecord>>
    val recentStockMovements: Flow<List<StockMovementRecord>>
    fun stockMovements(itemId: Long): Flow<List<StockMovementRecord>>

    fun purchases(query: String): Flow<List<PurchaseSummary>>
    fun purchaseDashboardSummary(fromEpochDay: Long, toEpochDay: Long, todayEpochDay: Long): Flow<PurchaseDashboardSummary>

    suspend fun createSupplier(draft: SupplierDraft): Long
    suspend fun updateSupplier(id: Long, draft: SupplierDraft)
    suspend fun deactivateSupplier(id: Long)
    suspend fun mergeSupplier(draft: SupplierMergeDraft)

    suspend fun createInventoryItem(draft: InventoryItemDraft): Long
    suspend fun updateInventoryItem(id: Long, draft: InventoryItemDraft)
    suspend fun deactivateInventoryItem(id: Long)
    suspend fun postInventoryCount(draft: InventoryCountDraft): Long
    suspend fun closeInventoryPeriod(draft: InventoryPeriodCloseDraft): Long
    suspend fun reopenInventoryPeriod(draft: InventoryPeriodReopenDraft)
    suspend fun postWaste(draft: WasteDraft): Long
}
