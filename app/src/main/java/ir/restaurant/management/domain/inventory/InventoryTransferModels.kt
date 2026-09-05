package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

enum class InventoryTransferStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    REQUESTED("REQUESTED"),
    APPROVED("APPROVED"),
    ISSUED("ISSUED"),
    IN_TRANSIT("IN_TRANSIT"),
    RECEIVED("RECEIVED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryTransferStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class CreateInventoryTransferLine(
    val itemId: Long,
    val lotId: Long? = null,
    val requestedQuantityMicros: Long,
) {
    fun validated(): CreateInventoryTransferLine {
        require(itemId > 0 && (lotId == null || lotId > 0))
        QuantityMicros.positive(requestedQuantityMicros)
        return this
    }
}

data class CreateInventoryTransferCommand(
    val sourceLocationId: Long,
    val destinationLocationId: Long,
    val businessEpochDay: Long,
    val lines: List<CreateInventoryTransferLine>,
    val notes: String,
    val actorId: Long,
    val commandId: String = GlobalId.new().value,
    val correlationId: String = "inventory_transfer:${GlobalId.new().value}",
) {
    fun validated(): CreateInventoryTransferCommand {
        require(sourceLocationId > 0 && destinationLocationId > 0 && sourceLocationId != destinationLocationId)
        require(businessEpochDay > 0 && actorId > 0)
        require(lines.isNotEmpty() && lines.size <= 500)
        val validLines = lines.map(CreateInventoryTransferLine::validated)
        require(validLines.map { it.itemId to (it.lotId ?: 0L) }.distinct().size == validLines.size) {
            "هر کالا/لات فقط یک‌بار در انتقال مجاز است."
        }
        require(notes.trim().length <= 500)
        return copy(
            lines = validLines,
            notes = notes.trim(),
            commandId = GlobalId.parse(commandId).value,
            correlationId = CorrelationId.parse(correlationId).value,
        )
    }
}

data class TransferActionCommand(
    val transferId: Long,
    val actorId: Long,
    val businessEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): TransferActionCommand {
        require(transferId > 0 && actorId > 0 && businessEpochDay > 0)
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل عملیات انتقال الزامی است." }
        return copy(reason = normalizedReason, commandId = GlobalId.parse(commandId).value)
    }
}

data class ReceiveInventoryTransferCommand(
    val transferId: Long,
    val actorId: Long,
    val businessEpochDay: Long,
    val receivedQuantityByLineId: Map<Long, Long>,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): ReceiveInventoryTransferCommand {
        require(transferId > 0 && actorId > 0 && businessEpochDay > 0)
        require(receivedQuantityByLineId.isNotEmpty() && receivedQuantityByLineId.size <= 500)
        receivedQuantityByLineId.forEach { (lineId, quantity) ->
            require(lineId > 0)
            QuantityMicros.positive(quantity)
        }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل دریافت انتقال الزامی است." }
        return copy(reason = normalizedReason, commandId = GlobalId.parse(commandId).value)
    }
}

data class InventoryTransferLine(
    val id: Long,
    val itemId: Long,
    val lotId: Long?,
    val lotCode: String,
    val requestedQuantityMicros: Long,
    val issuedQuantityMicros: Long?,
    val receivedQuantityMicros: Long?,
    val varianceQuantityMicros: Long?,
    val unitCostRial: Long?,
    val valueRial: Long?,
)

data class InventoryTransferDocument(
    val id: Long,
    val globalId: String,
    val documentNumber: String,
    val sourceLocationId: Long,
    val destinationLocationId: Long,
    val businessEpochDay: Long,
    val status: InventoryTransferStatus,
    val requestedByActorId: Long,
    val approvedByActorId: Long?,
    val issuedByActorId: Long?,
    val receivedByActorId: Long?,
    val notes: String,
    val correlationId: String,
    val lines: List<InventoryTransferLine>,
)

data class InventoryTransferSearch(
    val status: InventoryTransferStatus? = null,
    val locationId: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryTransferSearch {
        require(status != InventoryTransferStatus.LEGACY_UNKNOWN)
        require(locationId == null || locationId > 0)
        require(limit in 1..200 && offset >= 0)
        return this
    }
}

object InventoryTransferTransitionPolicy {
    fun requireAllowed(from: InventoryTransferStatus, to: InventoryTransferStatus) {
        val allowed = when (from) {
            InventoryTransferStatus.DRAFT -> setOf(InventoryTransferStatus.REQUESTED, InventoryTransferStatus.CANCELLED)
            InventoryTransferStatus.REQUESTED -> setOf(InventoryTransferStatus.APPROVED, InventoryTransferStatus.CANCELLED)
            InventoryTransferStatus.APPROVED -> setOf(InventoryTransferStatus.IN_TRANSIT, InventoryTransferStatus.CANCELLED)
            InventoryTransferStatus.ISSUED -> setOf(InventoryTransferStatus.IN_TRANSIT)
            InventoryTransferStatus.IN_TRANSIT -> setOf(InventoryTransferStatus.COMPLETED)
            InventoryTransferStatus.RECEIVED -> setOf(InventoryTransferStatus.COMPLETED)
            InventoryTransferStatus.COMPLETED, InventoryTransferStatus.CANCELLED,
            InventoryTransferStatus.LEGACY_UNKNOWN -> emptySet()
        }
        if (to !in allowed) {
            throw BusinessError.InvalidStateTransition("INVENTORY_TRANSFER", from.storedValue, to.storedValue).asViolation()
        }
    }
}

interface InventoryTransferService {
    suspend fun search(query: InventoryTransferSearch): List<InventoryTransferDocument>
    suspend fun create(command: CreateInventoryTransferCommand): InventoryTransferDocument
    suspend fun approve(command: TransferActionCommand): InventoryTransferDocument
    suspend fun issue(command: TransferActionCommand): InventoryTransferDocument
    suspend fun receive(command: ReceiveInventoryTransferCommand): InventoryTransferDocument
    suspend fun createAndComplete(command: CreateInventoryTransferCommand): InventoryTransferDocument
    suspend fun document(id: Long): InventoryTransferDocument
}
