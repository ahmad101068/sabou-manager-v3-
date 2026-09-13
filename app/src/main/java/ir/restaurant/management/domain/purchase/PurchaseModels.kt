package ir.restaurant.management.domain.purchase

import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.treasury.TreasuryChannel
import kotlinx.coroutines.flow.Flow


object SupplierInvoiceNumber {
    /** Stable duplicate key for supplier invoices. Display text is preserved separately. */
    fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        raw.trim().forEach { ch ->
            when (ch) {
                '۰', '٠' -> out.append('0')
                '۱', '١' -> out.append('1')
                '۲', '٢' -> out.append('2')
                '۳', '٣' -> out.append('3')
                '۴', '٤' -> out.append('4')
                '۵', '٥' -> out.append('5')
                '۶', '٦' -> out.append('6')
                '۷', '٧' -> out.append('7')
                '۸', '٨' -> out.append('8')
                '۹', '٩' -> out.append('9')
                'ي', 'ى' -> out.append('ی')
                'ك' -> out.append('ک')
                else -> if (!ch.isWhitespace()) out.append(ch.uppercaseChar())
            }
        }
        return out.toString()
    }
}

enum class PurchasePaymentMethod(
    val storedValue: String?,
    val title: String,
    val treasuryAccountId: String?,
    val treasuryChannel: TreasuryChannel?,
) {
    PAYABLE(null, "نسیه", null, null),
    CASH("نقدی", "نقدی", "cash_main", TreasuryChannel.CASH),
    CARD("کارتخوان", "کارتخوان", "card_terminal", TreasuryChannel.CARD),
    TRANSFER("حواله", "حواله", "bank_main", TreasuryChannel.BANK),
    ;

    companion object {
        fun fromStored(value: String?): PurchasePaymentMethod {
            if (value == null) return PAYABLE
            return entries.firstOrNull { it.storedValue == value }
                ?: throw BusinessError.UnknownStoredValue(
                    ownerDomain = "procurement",
                    field = "purchase.paymentMethod",
                    storedValue = value,
                ).asViolation()
        }
    }
}

enum class PurchasePaymentStatus(val storedValue: String) {
    UNPAID("UNPAID"),
    PARTIAL("PARTIAL"),
    PAID("PAID"),
    REVERSED("REVERSED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): PurchasePaymentStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class PurchaseLineDraft(
    val itemId: Long,
    val quantity: QuantityMicros,
    val unitCost: MoneyRial,
)

data class PurchaseDraft(
    val invoiceNo: String,
    val supplierId: Long,
    val purchaseEpochDay: Long,
    val branchName: String = "",
    val dueEpochDay: Long,
    val paymentMethod: PurchasePaymentMethod,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
    val lines: List<PurchaseLineDraft>,
    val branchId: Long? = null,
    val locationId: Long? = null,
    val emergencyReason: String = "",
    val commandId: String = GlobalId.new().value,
)

data class PostedPurchase(
    val purchaseId: Long,
    val journalEntryId: Long?,
    val total: MoneyRial,
    val invoiceNo: String,
)

enum class SettlementPaymentMethod(val title: String, val treasuryAccountId: String, val treasuryChannel: TreasuryChannel) {
    CASH("نقدی", "cash_main", TreasuryChannel.CASH),
    CARD("کارتخوان", "card_terminal", TreasuryChannel.CARD),
    TRANSFER("حواله", "bank_main", TreasuryChannel.BANK),
    ;

    companion object {
        fun fromStoredValue(value: String): SettlementPaymentMethod =
            entries.firstOrNull { it.title == value }
                ?: throw BusinessError.UnknownStoredValue(
                    ownerDomain = "treasury",
                    field = "purchaseSettlement.paymentMethod",
                    storedValue = value,
                ).asViolation()
    }
}

data class PurchaseSettlementDraft(
    val purchaseId: Long,
    val settlementEpochDay: Long,
    val amount: MoneyRial,
    val paymentMethod: SettlementPaymentMethod,
    val referenceNo: String = "",
    val notes: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PurchaseSettlementDraft {
        require(purchaseId > 0) { "فاکتور خرید معتبر نیست." }
        require(amount > MoneyRial.ZERO) { "مبلغ تسویه باید بیشتر از صفر باشد." }
        require(referenceNo.trim().length <= 80) { "شماره پیگیری بیش از حد طولانی است." }
        require(notes.trim().length <= 300) { "توضیحات بیش از حد طولانی است." }
        val normalizedCommandId = GlobalId.parse(commandId).value
        return copy(referenceNo = referenceNo.trim(), notes = notes.trim(), commandId = normalizedCommandId)
    }
}

data class PostedPurchaseSettlement(
    val purchaseId: Long,
    val journalEntryId: Long,
    val journalEntryNo: String,
    val remaining: MoneyRial,
)

data class PurchaseReversalDraft(
    val purchaseId: Long,
    val reversalEpochDay: Long,
    val reason: String,
) {
    fun validated(): PurchaseReversalDraft {
        require(purchaseId > 0) { "فاکتور خرید معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..200) {
            "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد."
        }
        return copy(reason = normalizedReason)
    }
}

data class PurchaseLineRecord(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)

data class PurchaseSettlementRecord(
    val journalEntryId: Long,
    val entryNo: String,
    val settlementEpochDay: Long,
    val amountRial: Long,
    val paymentMethod: SettlementPaymentMethod,
    val referenceNo: String,
    val notes: String,
    val isReversed: Boolean,
) {
    val canReverse: Boolean get() = !isReversed
}

data class PurchaseSettlementReversalDraft(
    val purchaseId: Long,
    val settlementJournalEntryId: Long,
    val reversalEpochDay: Long,
    val reason: String,
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): PurchaseSettlementReversalDraft {
        require(purchaseId > 0) { "فاکتور خرید معتبر نیست." }
        require(settlementJournalEntryId > 0) { "تسویه انتخاب‌شده معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..200) {
            "دلیل برگشت تسویه باید بین ۳ تا ۲۰۰ نویسه باشد."
        }
        return copy(reason = normalizedReason, commandId = GlobalId.parse(commandId).value)
    }
}

data class PurchaseDetails(
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
    val lines: List<PurchaseLineRecord>,
    val settlements: List<PurchaseSettlementRecord>,
) {
    val outstandingRial: Long get() = totalRial - paidRial
    val isReversed: Boolean get() = paymentStatus == PurchasePaymentStatus.REVERSED
    val canSettle: Boolean
        get() = !isReversed && paymentMethod == PurchasePaymentMethod.PAYABLE && outstandingRial > 0
    val canReverse: Boolean
        get() = !isReversed && (paidRial == 0L || paymentMethod != PurchasePaymentMethod.PAYABLE)
}

interface PurchaseRepository {
    suspend fun post(draft: PurchaseDraft): PostedPurchase
    fun details(purchaseId: Long): Flow<PurchaseDetails?>
    suspend fun settle(draft: PurchaseSettlementDraft): PostedPurchaseSettlement
    suspend fun reverseSettlement(draft: PurchaseSettlementReversalDraft)
    suspend fun reverse(draft: PurchaseReversalDraft)
}
