package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

enum class WasteReason(val storedValue: String) {
    SPOILAGE("SPOILAGE"),
    EXPIRED("EXPIRED"),
    PREPARATION_WASTE("PREPARATION_WASTE"),
    OVERPRODUCTION("OVERPRODUCTION"),
    QUALITY_REJECT("QUALITY_REJECT"),
    DAMAGE("DAMAGE"),
    STAFF_MEAL("STAFF_MEAL"),
    COMPLIMENTARY("COMPLIMENTARY"),
    OTHER("OTHER"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): WasteReason =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN

        /** Explicit compatibility mapping; the original free-text reason is retained as detail. */
        fun fromStoredInput(value: String): WasteReason {
            val normalized = value.trim().uppercase()
            entries.firstOrNull { it != LEGACY_UNKNOWN && it.storedValue == normalized }?.let { return it }
            return when {
                "انقضا" in value || "منقض" in value -> EXPIRED
                "فساد" in value -> SPOILAGE
                "آماده" in value -> PREPARATION_WASTE
                "تولید" in value -> OVERPRODUCTION
                "کیفیت" in value -> QUALITY_REJECT
                "آسیب" in value || "شکست" in value -> DAMAGE
                "پرسنل" in value || "کارکنان" in value -> STAFF_MEAL
                "مهمان" in value || "رایگان" in value -> COMPLIMENTARY
                else -> OTHER
            }
        }
    }
}

enum class WasteStatus(val storedValue: String) {
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    POSTED("POSTED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): WasteStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class WasteApprovalPolicy(val approvalThresholdRial: Long?) {
    init {
        approvalThresholdRial?.let { MoneyRial.of(it) }
    }

    fun requiresApproval(totalCostRial: Long): Boolean {
        if (approvalThresholdRial == null) return false
        MoneyRial.of(totalCostRial)
        return totalCostRial >= approvalThresholdRial
    }

    companion object {
        /** Approval can be enabled through policy without changing the command/storage contract. */
        val NO_APPROVAL_REQUIRED = WasteApprovalPolicy(null)
        val ALWAYS_REQUIRE_APPROVAL = WasteApprovalPolicy(0)
    }
}

data class CreateWasteCommand(
    val itemId: Long,
    val locationId: Long,
    val lotId: Long? = null,
    val quantityMicros: Long,
    val reason: WasteReason,
    val businessEpochDay: Long,
    val reasonDetail: String = "",
    val notes: String = "",
    val actorId: Long,
    val commandId: String = GlobalId.new().value,
    val correlationId: String = "inventory_waste:${GlobalId.new().value}",
) {
    fun validated(): CreateWasteCommand {
        require(itemId > 0 && locationId > 0 && actorId > 0)
        require(lotId == null || lotId > 0)
        QuantityMicros.positive(quantityMicros)
        require(reason != WasteReason.LEGACY_UNKNOWN)
        require(businessEpochDay > 0)
        val detail = reasonDetail.trim()
        val normalizedNotes = notes.trim()
        require(detail.length <= 300 && normalizedNotes.length <= 500)
        return copy(
            reasonDetail = detail,
            notes = normalizedNotes,
            commandId = GlobalId.parse(commandId).value,
            correlationId = CorrelationId.parse(correlationId).value,
        )
    }
}

data class WasteActionCommand(
    val wasteId: Long,
    val actorId: Long,
    val reason: String,
)

data class PostWasteCommand(
    val wasteId: Long,
    val actorId: Long,
    val commandId: String = GlobalId.new().value,
)

data class InventoryWasteDocument(
    val id: Long,
    val globalId: String,
    val documentNumber: String,
    val itemId: Long,
    val locationId: Long,
    val lotId: Long?,
    val quantityMicros: Long,
    val unitCostRial: Long?,
    val totalCostRial: Long,
    val reason: WasteReason,
    val reasonDetail: String,
    val businessEpochDay: Long,
    val createdByActorId: Long,
    val approvedByActorId: Long?,
    val postedByActorId: Long?,
    val status: WasteStatus,
    val correlationId: String,
)

data class InventoryWasteSearch(
    val status: WasteStatus? = null,
    val locationId: Long? = null,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryWasteSearch {
        require(status != WasteStatus.LEGACY_UNKNOWN)
        require(locationId == null || locationId > 0)
        require(fromEpochDay > 0 && fromEpochDay <= toEpochDay)
        require(limit in 1..200 && offset >= 0)
        return this
    }
}

object WasteTransitionPolicy {
    fun requireAllowed(from: WasteStatus, to: WasteStatus) {
        val allowed = when (from) {
            WasteStatus.PENDING_APPROVAL -> setOf(WasteStatus.APPROVED)
            WasteStatus.APPROVED -> setOf(WasteStatus.POSTED)
            WasteStatus.POSTED, WasteStatus.LEGACY_UNKNOWN -> emptySet()
        }
        if (to !in allowed) {
            throw BusinessError.InvalidStateTransition("INVENTORY_WASTE", from.storedValue, to.storedValue).asViolation()
        }
    }
}

interface InventoryWasteService {
    suspend fun search(query: InventoryWasteSearch): List<InventoryWasteDocument>
    suspend fun submit(command: CreateWasteCommand): InventoryWasteDocument
    suspend fun submitAndPost(command: CreateWasteCommand): InventoryWasteDocument
    suspend fun approve(command: WasteActionCommand): InventoryWasteDocument
    suspend fun post(command: PostWasteCommand): InventoryWasteDocument
    suspend fun document(id: Long): InventoryWasteDocument
}
