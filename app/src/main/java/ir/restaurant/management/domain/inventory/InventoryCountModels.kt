package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

enum class InventoryCountStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    OPEN("OPEN"),
    COUNTING("COUNTING"),
    RECOUNT_REQUIRED("RECOUNT_REQUIRED"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    APPROVED("APPROVED"),
    POSTED("POSTED"),
    CANCELLED("CANCELLED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryCountStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

enum class InventoryCountLineStatus(val storedValue: String) {
    PENDING("PENDING"),
    COUNTED("COUNTED"),
    RECOUNT_REQUIRED("RECOUNT_REQUIRED"),
    FINALIZED("FINALIZED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): InventoryCountLineStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

enum class InventoryCountScope(val storedValue: String) {
    ALL_LOCATION("ALL_LOCATION"),
    ITEM_SELECTION("ITEM_SELECTION"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");
}

data class InventoryRecountPolicy(
    val quantityThresholdMicros: Long,
    val valueThresholdRial: Long,
) {
    init {
        QuantityMicros.of(quantityThresholdMicros)
        MoneyRial.of(valueThresholdRial)
    }

    fun requiresRecount(
        systemQuantityMicros: Long,
        countedQuantityMicros: Long,
        systemValueRial: Long,
        countedValueRial: Long,
    ): Boolean = absoluteDifference(systemQuantityMicros, countedQuantityMicros) > quantityThresholdMicros ||
        absoluteDifference(systemValueRial, countedValueRial) > valueThresholdRial

    private fun absoluteDifference(left: Long, right: Long): Long {
        val difference = SignedLongMath.subtract(left, right)
        require(difference != Long.MIN_VALUE) { "اختلاف شمارش از محدوده امن خارج است." }
        return kotlin.math.abs(difference)
    }

    companion object {
        val DEFAULT = InventoryRecountPolicy(
            quantityThresholdMicros = QuantityMicros.SCALE / 10,
            valueThresholdRial = 1_000_000,
        )
    }
}

data class InventoryCountSession(
    val id: Long,
    val globalId: String,
    val documentNumber: String,
    val locationId: Long,
    val scope: InventoryCountScope,
    val blindCount: Boolean,
    val createdByActorId: Long,
    val assignedToActorId: Long?,
    val status: InventoryCountStatus,
    val snapshotEpochMillis: Long,
    val businessEpochDay: Long,
    val submittedAtEpochMillis: Long?,
    val approvedByActorId: Long?,
    val approvedAtEpochMillis: Long?,
    val postedAtEpochMillis: Long?,
    val notes: String,
    val correlationId: String,
)

data class InventoryCountLine(
    val id: Long,
    val sessionId: Long,
    val itemId: Long,
    val lotId: Long?,
    val systemQuantitySnapshotMicros: Long,
    val systemValueSnapshotRial: Long,
    val firstCountQuantityMicros: Long?,
    val secondCountQuantityMicros: Long?,
    val finalCountQuantityMicros: Long?,
    val finalCountValueRial: Long?,
    val varianceQuantityMicros: Long?,
    val varianceValueRial: Long?,
    val status: InventoryCountLineStatus,
    val reason: String,
)

data class InventoryCountLineView(
    val lineId: Long,
    val itemId: Long,
    val lotId: Long?,
    val systemQuantityMicros: Long?,
    val systemValueRial: Long?,
    val firstCountQuantityMicros: Long?,
    val secondCountQuantityMicros: Long?,
    val finalCountQuantityMicros: Long?,
    val varianceQuantityMicros: Long?,
    val varianceValueRial: Long?,
    val status: InventoryCountLineStatus,
)

fun InventoryCountLine.toView(blindCount: Boolean, canReviewVariance: Boolean): InventoryCountLineView {
    val revealSystem = !blindCount || canReviewVariance
    return InventoryCountLineView(
        lineId = id,
        itemId = itemId,
        lotId = lotId,
        systemQuantityMicros = systemQuantitySnapshotMicros.takeIf { revealSystem },
        systemValueRial = systemValueSnapshotRial.takeIf { revealSystem },
        firstCountQuantityMicros = firstCountQuantityMicros,
        secondCountQuantityMicros = secondCountQuantityMicros,
        finalCountQuantityMicros = finalCountQuantityMicros,
        varianceQuantityMicros = varianceQuantityMicros.takeIf { canReviewVariance },
        varianceValueRial = varianceValueRial.takeIf { canReviewVariance },
        status = status,
    )
}

object InventoryCountTransitionPolicy {
    fun requireAllowed(from: InventoryCountStatus, to: InventoryCountStatus) {
        val allowed = when (from) {
            InventoryCountStatus.DRAFT -> setOf(InventoryCountStatus.OPEN, InventoryCountStatus.CANCELLED)
            InventoryCountStatus.OPEN -> setOf(InventoryCountStatus.COUNTING, InventoryCountStatus.CANCELLED)
            InventoryCountStatus.COUNTING -> setOf(
                InventoryCountStatus.RECOUNT_REQUIRED,
                InventoryCountStatus.PENDING_APPROVAL,
                InventoryCountStatus.CANCELLED,
            )
            InventoryCountStatus.RECOUNT_REQUIRED -> setOf(
                InventoryCountStatus.COUNTING,
                InventoryCountStatus.PENDING_APPROVAL,
                InventoryCountStatus.CANCELLED,
            )
            InventoryCountStatus.PENDING_APPROVAL -> setOf(
                InventoryCountStatus.APPROVED,
                InventoryCountStatus.RECOUNT_REQUIRED,
                InventoryCountStatus.CANCELLED,
            )
            InventoryCountStatus.APPROVED -> setOf(InventoryCountStatus.POSTED)
            InventoryCountStatus.POSTED, InventoryCountStatus.CANCELLED,
            InventoryCountStatus.LEGACY_UNKNOWN -> emptySet()
        }
        if (to !in allowed) {
            throw BusinessError.InvalidStateTransition("INVENTORY_COUNT_SESSION", from.storedValue, to.storedValue).asViolation()
        }
    }
}

data class CreateInventoryCountSessionCommand(
    val locationId: Long,
    val scope: InventoryCountScope,
    val itemIds: Set<Long> = emptySet(),
    val blindCount: Boolean = true,
    val assignedToActorId: Long? = null,
    val businessEpochDay: Long,
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
    val correlationId: String = "inventory_count:${GlobalId.new().value}",
) {
    fun validated(): CreateInventoryCountSessionCommand {
        require(locationId > 0 && businessEpochDay > 0)
        require(scope != InventoryCountScope.LEGACY_UNKNOWN)
        require(scope != InventoryCountScope.ITEM_SELECTION || itemIds.isNotEmpty()) {
            "برای شمارش انتخابی حداقل یک کالا لازم است."
        }
        require(itemIds.size <= 10_000 && itemIds.all { it > 0 })
        require(assignedToActorId == null || assignedToActorId > 0)
        require(notes.trim().length <= 500)
        return copy(
            notes = notes.trim(),
            commandId = GlobalId.parse(commandId).value,
            correlationId = CorrelationId.parse(correlationId).value,
        )
    }
}

data class RecordInventoryCountCommand(
    val sessionId: Long,
    val lineId: Long,
    val countedQuantityMicros: Long,
    val unitCostOverrideRial: Long? = null,
    val reason: String = "",
    val actorId: Long,
)

data class InventoryCountActionCommand(
    val sessionId: Long,
    val actorId: Long,
    val reason: String,
)

data class PostInventoryCountCommand(
    val sessionId: Long,
    val actorId: Long,
    val commandId: String = GlobalId.new().value,
)

data class InventoryCountSearch(
    val status: InventoryCountStatus? = null,
    val locationId: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) {
    fun validated(): InventoryCountSearch {
        require(status != InventoryCountStatus.LEGACY_UNKNOWN)
        require(locationId == null || locationId > 0)
        require(limit in 1..200 && offset >= 0)
        return this
    }
}

interface InventoryCountService {
    suspend fun search(query: InventoryCountSearch): List<InventoryCountSession>
    suspend fun create(command: CreateInventoryCountSessionCommand): Long
    suspend fun open(command: InventoryCountActionCommand)
    suspend fun record(command: RecordInventoryCountCommand)
    suspend fun submit(command: InventoryCountActionCommand)
    suspend fun approve(command: InventoryCountActionCommand)
    suspend fun cancel(command: InventoryCountActionCommand)
    suspend fun post(command: PostInventoryCountCommand): InventoryCountSession
    suspend fun session(id: Long): InventoryCountSession
    suspend fun lines(sessionId: Long, canReviewVariance: Boolean): List<InventoryCountLineView>
}
