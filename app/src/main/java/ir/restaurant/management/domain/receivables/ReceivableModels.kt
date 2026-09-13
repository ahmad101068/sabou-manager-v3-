package ir.restaurant.management.domain.receivables

import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.treasury.TreasuryAccountId

enum class ReceivableType { PERSONAL, CORPORATE }
enum class ReceivableStatus { OPEN, PARTIALLY_PAID, PAID, VOIDED }
enum class ReceivableCollectionMethod(val canonicalTreasuryAccountId: String) {
    CASH("cash_main"),
    CARD("card_terminal"),
    BANK_TRANSFER("bank_main"),
}

data class ReceivableRecord(
    val id: Long,
    val globalId: String,
    val branchId: Long,
    val partyId: Long,
    val type: ReceivableType,
    val sourceType: String,
    val sourceId: Long,
    val originalAmountRial: Long,
    val paidAmountRial: Long,
    val outstandingAmountRial: Long,
    val issueEpochDay: Long,
    val dueEpochDay: Long?,
    val status: ReceivableStatus,
) {
    fun isOverdue(todayEpochDay: Long): Boolean = dueEpochDay != null && outstandingAmountRial > 0 && todayEpochDay > dueEpochDay
}

data class ReceivableCollectionDraft(
    val receivableId: Long,
    val amountRial: Long,
    val method: ReceivableCollectionMethod,
    val reference: String? = null,
    val businessEpochDay: Long,
    val commandId: String = GlobalId.new().value,
    val treasuryAccountId: String = method.canonicalTreasuryAccountId,
) {
    fun normalized(): ReceivableCollectionDraft {
        val normalizedCommandId = GlobalId.parse(commandId).value
        val normalizedAccountId = TreasuryAccountId.parse(treasuryAccountId).value
        require(receivableId > 0 && businessEpochDay > 0) { "دریافتنی معتبر نیست." }
        require(amountRial > 0) { "مبلغ وصول باید بیشتر از صفر باشد." }
        require(normalizedAccountId == method.canonicalTreasuryAccountId) { "حساب خزانه با روش وصول سازگار نیست." }
        return copy(
            commandId = normalizedCommandId,
            treasuryAccountId = normalizedAccountId,
            reference = reference?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    fun validated(outstandingRial: Long): ReceivableCollectionDraft {
        val normalized = normalized()
        require(normalized.amountRial <= outstandingRial) { "مبلغ وصول از مانده دریافتنی بیشتر است." }
        return normalized
    }
}

data class DailySalesReceivableOriginDraft(
    val commandId: String,
    val branchId: Long,
    val partyId: Long,
    val type: ReceivableType,
    val dailySalesId: Long,
    val amountRial: Long,
    val issueEpochDay: Long,
    val dueEpochDay: Long?,
) {
    fun validated(): DailySalesReceivableOriginDraft {
        GlobalId.parse(commandId)
        require(branchId > 0 && partyId > 0 && dailySalesId > 0 && issueEpochDay > 0) { "منشأ دریافتنی فروش معتبر نیست." }
        require(amountRial > 0) { "مبلغ دریافتنی باید بیشتر از صفر باشد." }
        require(dueEpochDay == null || dueEpochDay >= issueEpochDay) { "سررسید دریافتنی نمی‌تواند قبل از تاریخ فروش باشد." }
        return this
    }
}

data class ReceivableAging(
    val currentRial: Long,
    val overdue1To7Rial: Long,
    val overdue8To30Rial: Long,
    val overdue31To60Rial: Long,
    val overdue61To90Rial: Long,
    val overdueOver90Rial: Long,
)

data class ReceivableCollectionReversalDraft(
    val collectionId: Long,
    val reason: String,
    val reversalEpochDay: Long,
) {
    fun validated(): ReceivableCollectionReversalDraft {
        require(collectionId > 0 && reversalEpochDay > 0) { "وصول معتبر نیست." }
        val normalized = reason.trim()
        require(normalized.length in 3..300) { "دلیل برگشت وصول الزامی است." }
        return copy(reason = normalized)
    }
}

interface ReceivableService {
    fun observeOpen(branchId: Long): kotlinx.coroutines.flow.Flow<List<ReceivableRecord>>
    suspend fun createFromDailySales(draft: DailySalesReceivableOriginDraft): Long
    suspend fun voidFromDailySales(dailySalesId: Long, reversalEpochDay: Long, reason: String)
    suspend fun collect(draft: ReceivableCollectionDraft): Long
    suspend fun reverseCollection(draft: ReceivableCollectionReversalDraft)
    suspend fun aging(branchId: Long, todayEpochDay: Long): ReceivableAging
}
