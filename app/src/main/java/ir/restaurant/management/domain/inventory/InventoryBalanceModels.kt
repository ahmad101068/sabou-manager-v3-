package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros

data class InventoryBalance(
    val itemId: Long,
    val locationId: Long,
    val onHandMicros: Long,
    val reservedMicros: Long,
    val inTransitMicros: Long,
    val damagedMicros: Long,
    val quarantinedMicros: Long,
    val inventoryValueRial: Long,
) {
    val availableMicros: Long = onHandMicros - reservedMicros - damagedMicros - quarantinedMicros

    init {
        require(itemId > 0 && locationId > 0) { "شناسه مانده موجودی معتبر نیست." }
        QuantityMicros.of(onHandMicros)
        listOf(reservedMicros, inTransitMicros, damagedMicros, quarantinedMicros).forEach(QuantityMicros::of)
        require(availableMicros >= 0) { "موجودی قابل‌دسترس نمی‌تواند منفی باشد." }
        MoneyRial.of(inventoryValueRial)
    }
}

enum class InventoryIntegrityIssueType {
    NEGATIVE_BALANCE,
    LEDGER_PROJECTION_MISMATCH,
    AGGREGATE_PROJECTION_MISMATCH,
    LOT_BALANCE_MISMATCH,
    ORPHAN_MOVEMENT,
    INVALID_LOCATION,
    INVALID_LOT,
    EXPIRED_ACTIVE_LOT,
    TRANSFER_IMBALANCE,
}

enum class InventoryIntegritySeverity { CRITICAL, HIGH, MEDIUM }

data class InventoryIntegrityIssue(
    val type: InventoryIntegrityIssueType,
    val severity: InventoryIntegritySeverity,
    val itemId: Long?,
    val locationId: Long?,
    val lotId: Long?,
    val referenceId: Long?,
    val expectedQuantityMicros: Long?,
    val actualQuantityMicros: Long?,
    val expectedValueRial: Long?,
    val actualValueRial: Long?,
)

data class InventoryIntegrityReport(
    val checkedAtEpochMillis: Long,
    val issues: List<InventoryIntegrityIssue>,
) {
    val isHealthy: Boolean get() = issues.isEmpty()
    val criticalCount: Int get() = issues.count { it.severity == InventoryIntegritySeverity.CRITICAL }
}

interface InventoryIntegrityService {
    suspend fun verify(): InventoryIntegrityReport
}

enum class InventoryValuationMethod { WEIGHTED_AVERAGE }

interface InventoryValuationService {
    val method: InventoryValuationMethod
    fun issueValue(balanceQuantityMicros: Long, balanceValueRial: Long, issueQuantityMicros: Long): Long
    fun transferValue(unitCostRial: Long, quantityMicros: Long): Long
}

object WeightedAverageInventoryValuationService : InventoryValuationService {
    override val method: InventoryValuationMethod = InventoryValuationMethod.WEIGHTED_AVERAGE

    override fun issueValue(balanceQuantityMicros: Long, balanceValueRial: Long, issueQuantityMicros: Long): Long {
        QuantityMicros.positive(balanceQuantityMicros)
        QuantityMicros.positive(issueQuantityMicros)
        require(issueQuantityMicros <= balanceQuantityMicros) { "مقدار خروج از مانده بیشتر است." }
        MoneyRial.of(balanceValueRial)
        if (issueQuantityMicros == balanceQuantityMicros) return balanceValueRial
        return FixedPointRatio.multiplyDivide(balanceValueRial, issueQuantityMicros, balanceQuantityMicros)
    }

    override fun transferValue(unitCostRial: Long, quantityMicros: Long): Long =
        MoneyRial.of(unitCostRial).times(QuantityMicros.positive(quantityMicros)).value
}
