package ir.restaurant.management.domain.sales

import ir.restaurant.management.domain.recipe.MenuPerformanceResult
import ir.restaurant.management.core.SignedLongMath
import kotlinx.coroutines.flow.Flow

data class DailyMenuSaleDraft(
    val menuItemId: Long,
    val quantityMicros: Long,
    val grossSalesRial: Long?,
)

data class DailySalesDraft(
    val businessEpochDay: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val notes: String = "",
    val lines: List<DailyMenuSaleDraft>,
    val branchId: Long,
    val locationId: Long = 0L,
    val returnRial: Long = 0L,
    val settlements: List<DailySalesSettlementDraft> = emptyList(),
    val grossSalesRial: Long? = null,
)

data class DailySalesReversalDraft(
    val summaryId: Long,
    val reversalEpochDay: Long,
    val reason: String,
) {
    fun validated(originalEpochDay: Long): DailySalesReversalDraft {
        val normalizedReason = reason.trim()
        require(summaryId > 0) { "فروش روزانه معتبر نیست." }
        require(reversalEpochDay >= originalEpochDay) { "تاریخ برگشت نمی‌تواند قبل از روز فروش باشد." }
        require(normalizedReason.length in 3..200) { "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد." }
        return copy(reason = normalizedReason)
    }
}

data class DailySalesItem(
    val id: Long,
    val branchId: Long,
    val locationId: Long? = null,
    val businessEpochDay: Long,
    val grossSalesRial: Long,
    val discountRial: Long,
    val returnRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netSalesRial: Long,
    val theoreticalCostRial: Long,
    val fullCostRial: Long? = null,
    val fullMarginRial: Long? = null,
    val fullCostCoverageLineCount: Int = 0,
    val totalLineCount: Int = 0,
    val profitabilityLines: List<DailySalesProfitabilityLine> = emptyList(),
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val settlements: List<DailySalesSettlementDraft> = emptyList(),
    val status: DailySalesStatus = DailySalesStatus.POSTED,
    val notes: String,
    val isLegacyArchive: Boolean,
    val reversedAtEpochDay: Long?,
    val reversalReason: String,
    val isClosed: Boolean = false,
    val closedBy: String? = null,
    val closeNote: String = "",
    val closureStatus: String? = null,
    val closureRevisionNo: Int = 0,
    val reopenedBy: String? = null,
    val reopenReason: String = "",
    val isReversed: Boolean = reversedAtEpochDay != null,
) {
    val revenueRial: Long get() = SignedLongMath.add(netSalesRial, serviceRial)
    val amountToSettleRial: Long get() = SignedLongMath.add(revenueRial, taxRial)
}

data class DailySalesProfitabilityLine(
    val menuItemId: Long?,
    val recipeVersionId: Long?,
    val name: String,
    val quantityMicros: Long,
    val salesRial: Long?,
    val ingredientCostRial: Long,
    val foodCostRial: Long?,
    val packagingCostRial: Long?,
    val directLaborCostRial: Long?,
    val allocatedOverheadRial: Long?,
) {
    val fullCostRial: Long? get() {
        val food = foodCostRial ?: return null
        val packaging = packagingCostRial ?: return null
        val labor = directLaborCostRial ?: return null
        val overhead = allocatedOverheadRial ?: return null
        return listOf(food, packaging, labor, overhead).fold(0L, SignedLongMath::add)
    }
    val fullMarginRial: Long? get() = salesRial?.let { sales -> fullCostRial?.let { SignedLongMath.subtract(sales, it) } }
}

data class SalesDayClosureDraft(val branchId: Long, val businessEpochDay: Long, val note: String = "") {
    fun validated(): SalesDayClosureDraft {
        require(branchId > 0 && businessEpochDay > 0) { "شعبه و روز فروش معتبر نیست." }
        return copy(note = note.trim())
    }
}

data class SalesDayReopenDraft(val branchId: Long, val businessEpochDay: Long, val reason: String) {
    fun validated(): SalesDayReopenDraft {
        require(branchId > 0 && businessEpochDay > 0) { "شعبه و روز فروش معتبر نیست." }
        val normalized = reason.trim()
        require(normalized.length in 5..300) { "دلیل بازگشایی باید بین ۵ تا ۳۰۰ نویسه باشد." }
        return copy(reason = normalized)
    }
}

data class SalesDayClosureRecord(
    val businessEpochDay: Long,
    val summaryId: Long,
    val grossSalesRial: Long,
    val netSalesRial: Long,
    val theoreticalCostRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val status: String,
    val revisionNo: Int,
    val closedBy: String,
    val note: String,
    val reopenedBy: String? = null,
    val reopenReason: String = "",
    val createdAtEpochMillis: Long,
)

data class DailySalesReport(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val dayCount: Int,
    val salesRial: Long,
    val costOfGoodsRial: Long,
    val grossProfitRial: Long,
    val menuPerformance: List<MenuPerformanceResult>,
    val fullCostRial: Long?,
    val fullMarginRial: Long?,
    val fullCostCoverageLineCount: Int,
    val totalLineCount: Int,
    val menuProfitability: List<MenuProfitabilityResult>,
)

data class MenuProfitabilityResult(
    val menuItemId: Long,
    val name: String,
    val unitsSold: Long,
    val salesRial: Long?,
    val foodCostRial: Long,
    val fullCostRial: Long?,
    val foodMarginRial: Long?,
    val fullMarginRial: Long?,
    val fullCostBasisPoints: Int?,
    val hasCompleteFullCost: Boolean,
)

interface DailySalesRepository {
    val dayClosures: Flow<List<SalesDayClosureRecord>>
    /** Backward-compatible composite command; executes CREATE → CONFIRM → POST with all permission checks. */
    suspend fun post(draft: DailySalesDraft): Long
    suspend fun createDraft(draft: DailySalesDraft): Long
    suspend fun updateDraft(summaryId: Long, draft: DailySalesDraft)
    suspend fun confirm(summaryId: Long)
    suspend fun postConfirmed(summaryId: Long)
    suspend fun reverse(draft: DailySalesReversalDraft)
    suspend fun closeDay(draft: SalesDayClosureDraft)
    suspend fun reopenDay(draft: SalesDayReopenDraft)
    fun observe(query: String = ""): Flow<List<DailySalesItem>>
    fun observeReport(fromEpochDay: Long, toEpochDay: Long): Flow<DailySalesReport>
}
