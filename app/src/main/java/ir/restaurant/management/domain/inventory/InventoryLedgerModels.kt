package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId

enum class InventoryMovementType(val storedValue: String) {
    PURCHASE("PURCHASE"),
    PURCHASE_REVERSAL("PURCHASE_REVERSAL"),
    GOODS_RECEIPT("GOODS_RECEIPT"),
    PURCHASE_RETURN("PURCHASE_RETURN"),
    LEGACY_SALE_CONSUMPTION("SALE_CONSUMPTION"),
    DAILY_SALES_CONSUMPTION("DAILY_SALES_CONSUMPTION"),
    DAILY_SALES_REVERSAL("DAILY_SALES_REVERSAL"),
    SALES_INVOICE_CONSUMPTION("SALES_INVOICE_CONSUMPTION"),
    SALES_RETURN("SALES_RETURN"),
    SALES_VOID("SALES_VOID"),
    RECIPE_CONSUMPTION("RECIPE_CONSUMPTION"),
    PRODUCTION_OUTPUT("PRODUCTION_OUTPUT"),
    INVENTORY_COUNT("INVENTORY_COUNT"),
    COUNT_VARIANCE("COUNT_VARIANCE"),
    INVENTORY_ADJUSTMENT("INVENTORY_ADJUSTMENT"),
    WASTE("WASTE"),
    TRANSFER_IN("TRANSFER_IN"),
    TRANSFER_OUT("TRANSFER_OUT"),
    OPENING_BALANCE("OPENING_BALANCE"),
    REVERSAL("REVERSAL"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryMovementType =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

enum class InventoryReferenceType(val storedValue: String) {
    PURCHASE("PURCHASE"),
    GOODS_RECEIPT("GOODS_RECEIPT"),
    PURCHASE_RETURN("PURCHASE_RETURN"),
    DAILY_SALES("DAILY_SALES"),
    SALES_INVOICE("SALES_INVOICE"),
    SALES_RETURN("SALES_RETURN"),
    SALES_VOID("SALES_VOID"),
    INVENTORY_COUNT("INVENTORY_COUNT"),
    WASTE("WASTE"),
    STOCK_TRANSFER("STOCK_TRANSFER"),
    INVENTORY_ADJUSTMENT("INVENTORY_ADJUSTMENT"),
    RECIPE("RECIPE"),
    PRODUCTION("PRODUCTION"),
    MIGRATION("MIGRATION"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryReferenceType =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

enum class InventoryReasonCode(val storedValue: String) {
    PURCHASE_RECEIPT("PURCHASE_RECEIPT"),
    PURCHASE_REVERSAL("PURCHASE_REVERSAL"),
    GOODS_RECEIPT("GOODS_RECEIPT"),
    PURCHASE_RETURN("PURCHASE_RETURN"),
    SALES_CONSUMPTION("SALES_CONSUMPTION"),
    SALES_REVERSAL("SALES_REVERSAL"),
    PHYSICAL_COUNT("PHYSICAL_COUNT"),
    WASTE("WASTE"),
    STOCK_TRANSFER("STOCK_TRANSFER"),
    INVENTORY_ADJUSTMENT("INVENTORY_ADJUSTMENT"),
    COUNT_VARIANCE("COUNT_VARIANCE"),
    OPENING_BALANCE("OPENING_BALANCE"),
    REVERSAL("REVERSAL"),
}

data class InventoryCommandContext(
    val idempotencyKey: String,
    val correlationId: String,
    val actorId: Long,
    val deviceId: String,
    val locationId: Long?,
    val reasonCode: InventoryReasonCode,
    val reason: String,
) {
    fun validated(): InventoryCommandContext {
        val normalizedKey = idempotencyKey.trim()
        val normalizedCorrelation = CorrelationId.parse(correlationId).value
        val normalizedDevice = deviceId.trim()
        val normalizedReason = reason.trim()
        require(normalizedKey.matches(Regex("[A-Za-z0-9:_./-]{8,180}"))) {
            "کلید idempotency معتبر نیست."
        }
        require(actorId > 0) { "شناسه actor معتبر نیست." }
        require(normalizedDevice.length in 1..120) { "شناسه دستگاه معتبر نیست." }
        require(locationId == null || locationId > 0) { "شناسه مکان معتبر نیست." }
        require(normalizedReason.length in 2..500) { "دلیل عملیات موجودی الزامی است." }
        return copy(
            idempotencyKey = normalizedKey,
            correlationId = normalizedCorrelation,
            deviceId = normalizedDevice,
            reason = normalizedReason,
        )
    }

    companion object {
        fun local(
            referenceType: InventoryReferenceType,
            referenceId: Long,
            suffix: String,
            actorId: Long,
            reasonCode: InventoryReasonCode,
            reason: String,
            correlationId: String = "local:${GlobalId.new().value}",
            deviceId: String = "local-android",
            locationId: Long? = null,
        ): InventoryCommandContext {
            require(referenceId > 0) { "شناسه مرجع موجودی معتبر نیست." }
            val normalizedSuffix = suffix.trim().replace(' ', '_').take(80)
            require(normalizedSuffix.isNotBlank()) { "جزء کلید عملیات موجودی الزامی است." }
            val key = "${referenceType.storedValue}:$referenceId:$normalizedSuffix"
            return InventoryCommandContext(
                idempotencyKey = key,
                correlationId = correlationId,
                actorId = actorId,
                deviceId = deviceId,
                locationId = locationId,
                reasonCode = reasonCode,
                reason = reason,
            ).validated()
        }
    }
}

data class InventoryLedgerResult(
    val movementId: Long,
    val globalId: String,
    val idempotentReplay: Boolean,
)

data class InventoryReceiptLot(
    val lotNumber: String,
    val supplierLotNumber: String? = null,
    val productionEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val barcode: String? = null,
) {
    fun validated(receivedEpochDay: Long, trackExpiry: Boolean): InventoryReceiptLot {
        val number = lotNumber.trim()
        val supplierNumber = supplierLotNumber?.trim()?.ifBlank { null }
        val normalizedBarcode = barcode?.trim()?.ifBlank { null }
        require(number.length in 1..80) { "شماره لات الزامی است." }
        require(supplierNumber == null || supplierNumber.length <= 80)
        require(productionEpochDay == null || productionEpochDay <= receivedEpochDay)
        require(expiryEpochDay == null || expiryEpochDay >= (productionEpochDay ?: receivedEpochDay))
        require(!trackExpiry || expiryEpochDay != null) { "تاریخ انقضای کالای ردیابی‌شونده الزامی است." }
        require(normalizedBarcode == null || normalizedBarcode.length in 4..80)
        return copy(lotNumber = number, supplierLotNumber = supplierNumber, barcode = normalizedBarcode)
    }
}

data class ReceiveInventoryCommand(
    val itemId: Long,
    val quantityMicros: Long,
    val valueRial: Long,
    val movementType: InventoryMovementType,
    val referenceType: InventoryReferenceType,
    val referenceId: Long,
    val businessEpochDay: Long,
    val context: InventoryCommandContext,
    val notes: String? = null,
    val lot: InventoryReceiptLot? = null,
)

data class IssueInventoryCommand(
    val itemId: Long,
    val quantityMicros: Long,
    val valueRial: Long,
    val movementType: InventoryMovementType,
    val referenceType: InventoryReferenceType,
    val referenceId: Long,
    val businessEpochDay: Long,
    val context: InventoryCommandContext,
    val notes: String? = null,
    val allocateTrackedLots: Boolean = true,
    val lotId: Long? = null,
)

data class AdjustInventoryCommand(
    val itemId: Long,
    val countedQuantityMicros: Long,
    val countedValueRial: Long,
    val referenceId: Long,
    val businessEpochDay: Long,
    val context: InventoryCommandContext,
    val notes: String? = null,
)

data class ReverseInventoryCommand(
    val originalMovementId: Long,
    val reversalMovementType: InventoryMovementType,
    val businessEpochDay: Long,
    val context: InventoryCommandContext,
    val notes: String? = null,
)

interface InventoryCommandService {
    suspend fun receive(command: ReceiveInventoryCommand): InventoryLedgerResult
    suspend fun issue(command: IssueInventoryCommand): InventoryLedgerResult
    suspend fun adjust(command: AdjustInventoryCommand): InventoryLedgerResult
    suspend fun reverse(command: ReverseInventoryCommand): InventoryLedgerResult
}
