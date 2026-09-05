package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

/** Persisted lot lifecycle. Unknown legacy values are deliberately non-allocatable. */
enum class InventoryLotStatus(val storedValue: String) {
    ACTIVE("ACTIVE"),
    QUARANTINED("QUARANTINED"),
    EXPIRED("EXPIRED"),
    DEPLETED("DEPLETED"),
    BLOCKED("BLOCKED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    val isUnavailable: Boolean
        get() = this in setOf(QUARANTINED, EXPIRED, BLOCKED)

    companion object {
        fun fromStoredValue(value: String): InventoryLotStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN

        fun requireKnown(value: String): InventoryLotStatus = fromStoredValue(value).also { status ->
            if (status == LEGACY_UNKNOWN) {
                throw BusinessError.UnknownStoredValue("inventory", "lot.status", value).asViolation()
            }
        }
    }
}

data class InventoryLotDraft(
    val itemId: Long,
    val locationId: Long,
    val lotNumber: String,
    val supplierLotNumber: String? = null,
    val receivedEpochDay: Long,
    val productionEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val barcode: String? = null,
    val sourceReceiptId: Long? = null,
    val correlationId: String,
) {
    fun validated(trackExpiry: Boolean): InventoryLotDraft {
        require(itemId > 0 && locationId > 0) { "شناسه کالا و محل لات معتبر نیست." }
        val normalizedNumber = lotNumber.trim()
        val normalizedSupplierNumber = supplierLotNumber?.trim()?.ifBlank { null }
        val normalizedBarcode = barcode?.trim()?.ifBlank { null }
        require(normalizedNumber.length in 1..80) { "شماره لات باید بین ۱ تا ۸۰ نویسه باشد." }
        require(normalizedSupplierNumber == null || normalizedSupplierNumber.length <= 80) {
            "شماره لات تأمین‌کننده بیش از حد طولانی است."
        }
        QuantityMicros.positive(quantityMicros)
        MoneyRial.of(unitCostRial)
        require(productionEpochDay == null || productionEpochDay <= receivedEpochDay) {
            "تاریخ تولید نمی‌تواند پس از تاریخ دریافت باشد."
        }
        require(expiryEpochDay == null || expiryEpochDay >= (productionEpochDay ?: receivedEpochDay)) {
            "تاریخ انقضا نمی‌تواند پیش از تاریخ تولید یا دریافت باشد."
        }
        require(!trackExpiry || expiryEpochDay != null) { "برای این کالا ثبت تاریخ انقضا الزامی است." }
        require(normalizedBarcode == null || normalizedBarcode.length in 4..80) {
            "بارکد باید بین ۴ تا ۸۰ نویسه باشد."
        }
        require(sourceReceiptId == null || sourceReceiptId > 0) { "شناسه رسید خرید معتبر نیست." }
        return copy(
            lotNumber = normalizedNumber,
            supplierLotNumber = normalizedSupplierNumber,
            barcode = normalizedBarcode,
            correlationId = CorrelationId.parse(correlationId).value,
        )
    }
}

data class InventoryLot(
    val id: Long,
    val globalId: String,
    val itemId: Long,
    val locationId: Long,
    val lotNumber: String,
    val supplierLotNumber: String?,
    val receivedEpochDay: Long,
    val productionEpochDay: Long?,
    val expiryEpochDay: Long?,
    val initialQuantityMicros: Long,
    val remainingQuantityMicros: Long,
    val unitCostRial: Long,
    val status: InventoryLotStatus,
    val barcode: String?,
    val sourceReceiptId: Long?,
    val correlationId: String,
)

data class LotAllocationCandidate(
    val lotId: Long,
    val locationId: Long,
    val receivedEpochDay: Long,
    val expiryEpochDay: Long?,
    val availableQuantityMicros: Long,
    val unitCostRial: Long,
    val status: InventoryLotStatus,
)

data class LotAllocation(
    val lotId: Long,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val expiryEpochDay: Long?,
)

data class LotAllocationResult(
    val allocations: List<LotAllocation>,
    val requestedQuantityMicros: Long,
    val shortageMicros: Long,
) {
    val allocatedQuantityMicros: Long = requestedQuantityMicros - shortageMicros
    val isComplete: Boolean get() = shortageMicros == 0L
}

enum class LotAllocationPurpose { NORMAL_CONSUMPTION, DISPOSAL, SUPPLIER_RETURN }

data class LotAllocationRequest(
    val itemId: Long,
    val locationId: Long,
    val requiredQuantityMicros: Long,
    val businessEpochDay: Long,
    val trackExpiry: Boolean,
    val purpose: LotAllocationPurpose = LotAllocationPurpose.NORMAL_CONSUMPTION,
) {
    fun validated(): LotAllocationRequest {
        require(itemId > 0 && locationId > 0 && businessEpochDay > 0)
        QuantityMicros.positive(requiredQuantityMicros)
        return this
    }
}

interface LotAllocationService {
    suspend fun allocate(request: LotAllocationRequest): LotAllocationResult
}

/** Pure, deterministic FEFO allocation. FEFO chooses lots; it does not define valuation policy. */
object FefoLotAllocator {
    fun allocate(
        request: LotAllocationRequest,
        candidates: List<LotAllocationCandidate>,
    ): LotAllocationResult {
        val valid = request.validated()
        var remaining = valid.requiredQuantityMicros
        val allocations = buildList {
            candidates
                .asSequence()
                .filter { it.locationId == valid.locationId && it.availableQuantityMicros > 0 }
                .filter { candidate -> candidate.isEligible(valid) }
                .sortedWith(
                    compareBy<LotAllocationCandidate> { it.expiryEpochDay == null }
                        .thenBy { it.expiryEpochDay ?: Long.MAX_VALUE }
                        .thenBy { it.receivedEpochDay }
                        .thenBy { it.lotId },
                )
                .forEach { candidate ->
                    if (remaining == 0L) return@forEach
                    val quantity = minOf(remaining, candidate.availableQuantityMicros)
                    add(LotAllocation(candidate.lotId, quantity, candidate.unitCostRial, candidate.expiryEpochDay))
                    remaining -= quantity
                }
        }
        return LotAllocationResult(allocations, valid.requiredQuantityMicros, remaining)
    }

    private fun LotAllocationCandidate.isEligible(request: LotAllocationRequest): Boolean {
        if (status == InventoryLotStatus.LEGACY_UNKNOWN || status == InventoryLotStatus.DEPLETED) return false
        val expiredByDate = expiryEpochDay != null && expiryEpochDay < request.businessEpochDay
        return when (request.purpose) {
            LotAllocationPurpose.NORMAL_CONSUMPTION ->
                status == InventoryLotStatus.ACTIVE && !expiredByDate && (!request.trackExpiry || expiryEpochDay != null)
            LotAllocationPurpose.DISPOSAL ->
                status in setOf(
                    InventoryLotStatus.ACTIVE,
                    InventoryLotStatus.QUARANTINED,
                    InventoryLotStatus.EXPIRED,
                    InventoryLotStatus.BLOCKED,
                )
            LotAllocationPurpose.SUPPLIER_RETURN ->
                status != InventoryLotStatus.DEPLETED && status != InventoryLotStatus.LEGACY_UNKNOWN
        }
    }
}

object InventoryLotTransitionPolicy {
    fun requireAllowed(from: InventoryLotStatus, to: InventoryLotStatus, remainingQuantityMicros: Long) {
        QuantityMicros.of(remainingQuantityMicros)
        val allowed = when (from) {
            InventoryLotStatus.ACTIVE -> setOf(
                InventoryLotStatus.QUARANTINED,
                InventoryLotStatus.EXPIRED,
                InventoryLotStatus.BLOCKED,
                InventoryLotStatus.DEPLETED,
            )
            InventoryLotStatus.QUARANTINED -> setOf(
                InventoryLotStatus.ACTIVE,
                InventoryLotStatus.EXPIRED,
                InventoryLotStatus.BLOCKED,
                InventoryLotStatus.DEPLETED,
            )
            InventoryLotStatus.BLOCKED -> setOf(
                InventoryLotStatus.ACTIVE,
                InventoryLotStatus.EXPIRED,
                InventoryLotStatus.DEPLETED,
            )
            InventoryLotStatus.EXPIRED -> setOf(InventoryLotStatus.DEPLETED)
            InventoryLotStatus.DEPLETED, InventoryLotStatus.LEGACY_UNKNOWN -> emptySet()
        }
        if (to !in allowed || (to == InventoryLotStatus.DEPLETED && remainingQuantityMicros != 0L)) {
            throw BusinessError.InvalidStateTransition("INVENTORY_LOT", from.storedValue, to.storedValue).asViolation()
        }
    }
}

data class RegisterInventoryLotCommand(
    val draft: InventoryLotDraft,
    val actorId: Long,
    val reason: String,
)

data class ChangeInventoryLotStatusCommand(
    val lotId: Long,
    val expectedStatus: InventoryLotStatus,
    val nextStatus: InventoryLotStatus,
    val businessEpochDay: Long,
    val actorId: Long,
    val reason: String,
    val correlationId: String = "inventory:lot:${GlobalId.new().value}",
)

data class InventoryLotSearch(
    val itemId: Long? = null,
    val locationId: Long? = null,
    val status: InventoryLotStatus? = null,
    val expiryFromEpochDay: Long? = null,
    val expiryToEpochDay: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryLotSearch {
        require(itemId == null || itemId > 0)
        require(locationId == null || locationId > 0)
        require(status != InventoryLotStatus.LEGACY_UNKNOWN)
        require(expiryFromEpochDay == null || expiryFromEpochDay > 0)
        require(expiryToEpochDay == null || expiryToEpochDay > 0)
        require(expiryFromEpochDay == null || expiryToEpochDay == null || expiryFromEpochDay <= expiryToEpochDay)
        require(limit in 1..200 && offset >= 0)
        return this
    }
}

interface InventoryLotService : LotAllocationService {
    suspend fun search(query: InventoryLotSearch): List<InventoryLot>
    suspend fun register(command: RegisterInventoryLotCommand): Long
    suspend fun changeStatus(command: ChangeInventoryLotStatusCommand)
}
