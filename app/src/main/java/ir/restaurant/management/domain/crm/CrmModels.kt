package ir.restaurant.management.domain.crm

import ir.restaurant.management.core.GlobalId
import kotlinx.coroutines.flow.Flow

data class ReceivableLedgerRecord(
    val id: Long,
    val customerId: Long,
    val businessEpochDay: Long,
    val entryType: String,
    val debitRial: Long,
    val creditRial: Long,
    val sourceType: String,
    val sourceId: Long,
    val reference: String,
    val dueEpochDay: Long?,
)

data class ReceivableAging(
    val currentRial: Long,
    val days1To30Rial: Long,
    val days31To60Rial: Long,
    val days61To90Rial: Long,
    val over90Rial: Long,
) {
    val totalRial: Long get() = currentRial + days1To30Rial + days31To60Rial + days61To90Rial + over90Rial
}

data class CustomerDuplicateCandidate(val id: Long, val customerCode: String, val name: String, val phone: String, val nationalId: String)

enum class ReceivableAdjustmentDirection { DEBIT, CREDIT }

enum class ReceivableAdjustmentEconomicNature {
    SALES_CORRECTION,
    OTHER_INCOME,
    OPERATING_EXPENSE,
}

data class CustomerOpeningBalanceCommand(
    val customerId: Long,
    val businessEpochDay: Long,
    val amountRial: Long,
    val direction: ReceivableAdjustmentDirection = ReceivableAdjustmentDirection.DEBIT,
    val dueEpochDay: Long? = null,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): CustomerOpeningBalanceCommand {
        require(customerId > 0) { "مشتری معتبر نیست." }
        require(businessEpochDay > 0) { "تاریخ مانده افتتاحیه معتبر نیست." }
        require(amountRial > 0) { "مبلغ مانده افتتاحیه باید بیشتر از صفر باشد." }
        require(dueEpochDay == null || dueEpochDay >= businessEpochDay) { "سررسید مانده افتتاحیه معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل مانده افتتاحیه الزامی است." }
        require(commandId.isNotBlank()) { "شناسه فرمان مانده افتتاحیه معتبر نیست." }
        return copy(reason = normalizedReason, commandId = commandId.trim())
    }
}

data class CustomerReceivableAdjustmentCommand(
    val customerId: Long,
    val businessEpochDay: Long,
    val amountRial: Long,
    val direction: ReceivableAdjustmentDirection,
    val economicNature: ReceivableAdjustmentEconomicNature,
    val dueEpochDay: Long? = null,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): CustomerReceivableAdjustmentCommand {
        require(customerId > 0) { "مشتری معتبر نیست." }
        require(businessEpochDay > 0) { "تاریخ تعدیل معتبر نیست." }
        require(amountRial > 0) { "مبلغ تعدیل باید بیشتر از صفر باشد." }
        require(dueEpochDay == null || dueEpochDay >= businessEpochDay) { "سررسید تعدیل معتبر نیست." }
        when (economicNature) {
            ReceivableAdjustmentEconomicNature.SALES_CORRECTION -> Unit
            ReceivableAdjustmentEconomicNature.OTHER_INCOME -> require(direction == ReceivableAdjustmentDirection.DEBIT) { "سایر درآمد فقط برای افزایش دریافتنی مجاز است." }
            ReceivableAdjustmentEconomicNature.OPERATING_EXPENSE -> require(direction == ReceivableAdjustmentDirection.CREDIT) { "هزینه عملیاتی فقط برای کاهش دریافتنی مجاز است." }
        }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل تعدیل الزامی است." }
        require(commandId.isNotBlank()) { "شناسه فرمان تعدیل معتبر نیست." }
        return copy(reason = normalizedReason, commandId = commandId.trim())
    }
}

data class CustomerAccountPostingResult(
    val ledgerId: Long,
    val journalEntryId: Long,
    val idempotentReplay: Boolean,
)

interface CustomerAccountService {
    fun observeLedger(customerId: Long): Flow<List<ReceivableLedgerRecord>>
    suspend fun aging(customerId: Long, todayEpochDay: Long): ReceivableAging
    suspend fun duplicateCandidates(customerId: Long, phone: String, nationalId: String): List<CustomerDuplicateCandidate>
    suspend fun postOpeningBalance(command: CustomerOpeningBalanceCommand): CustomerAccountPostingResult
    suspend fun postAdjustment(command: CustomerReceivableAdjustmentCommand): CustomerAccountPostingResult
    suspend fun merge(sourceCustomerId: Long, targetCustomerId: Long, reason: String): Long
}
