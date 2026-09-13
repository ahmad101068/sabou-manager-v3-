package ir.restaurant.management.domain.sales

import ir.restaurant.management.domain.accounting.SemanticAccountRole
import kotlinx.coroutines.flow.Flow

enum class SalesPaymentMethod(
    val title: String,
    val accountRole: SemanticAccountRole,
) {
    CASH("نقدی", SemanticAccountRole.CASH),
    CARD("کارتخوان", SemanticAccountRole.BANK),
    TRANSFER("حواله", SemanticAccountRole.BANK),
    CREDIT("اعتباری", SemanticAccountRole.CUSTOMER_RECEIVABLE),
}

enum class SalesInvoiceStatus(val storedValue: String) {
    POSTED("POSTED"),
    PARTIALLY_RETURNED("PARTIALLY_RETURNED"),
    RETURNED("RETURNED"),
    VOIDED("VOIDED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): SalesInvoiceStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class CustomerDraft(
    val name: String,
    val phone: String = "",
    val nationalId: String = "",
    val creditLimitRial: Long = 0,
    val notes: String = "",
    val mobile: String = "",
    val address: String = "",
    val branch: String = "",
    val paymentTermsDays: Int = 0,
    val status: String = "ACTIVE",
    val partyType: PartyFinancialType = PartyFinancialType.PERSON,
) {
    fun validated(): CustomerDraft {
        val normalizedName = name.trim()
        val normalizedPhone = phone.filter(Char::isDigit)
        val normalizedNationalId = nationalId.filter(Char::isDigit)
        require(normalizedName.length in 2..120) { "نام مشتری باید بین ۲ تا ۱۲۰ نویسه باشد." }
        require(normalizedPhone.isBlank() || normalizedPhone.length in 7..15) { "شماره تماس مشتری معتبر نیست." }
        require(normalizedNationalId.isBlank() || normalizedNationalId.length in 8..12) { "شناسه ملی/کد ملی مشتری معتبر نیست." }
        require(creditLimitRial >= 0) { "سقف اعتبار نمی‌تواند منفی باشد." }
        require(notes.trim().length <= 500) { "توضیحات مشتری بیش از حد طولانی است." }
        require(paymentTermsDays in 0..3650) { "مهلت پرداخت مشتری معتبر نیست." }
        require(status.trim().uppercase() in setOf("ACTIVE", "ON_HOLD", "INACTIVE")) { "وضعیت مشتری معتبر نیست." }
        return copy(
            name = normalizedName,
            phone = normalizedPhone,
            nationalId = normalizedNationalId,
            mobile = mobile.filter(Char::isDigit),
            address = address.trim(),
            branch = branch.trim(),
            status = status.trim().uppercase(),
            partyType = partyType,
            notes = notes.trim(),
        )
    }
}

data class CustomerRecord(
    val id: Long,
    val customerCode: String,
    val name: String,
    val phone: String,
    val nationalId: String,
    val creditLimitRial: Long,
    val outstandingRial: Long,
    val notes: String,
    val isActive: Boolean,
    val mobile: String = "",
    val address: String = "",
    val branch: String = "",
    val paymentTermsDays: Int = 0,
    val status: String = "ACTIVE",
    val partyType: PartyFinancialType = PartyFinancialType.PERSON,
)

data class SalesInvoiceLineRecord(
    val id: Long,
    val menuItemId: Long,
    val recipeVersionId: Long,
    val name: String,
    val quantityMicros: Long,
    val unitPriceRial: Long,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val returnedQuantityMicros: Long,
)

data class SalesPaymentRecord(
    val method: SalesPaymentMethod,
    val amountRial: Long,
    val referenceNo: String,
)

data class SalesInvoiceRecord(
    val id: Long,
    val invoiceNo: String,
    val businessEpochDay: Long,
    val customerName: String?,
    val grossRial: Long,
    val discountRial: Long,
    val serviceRial: Long,
    val taxRial: Long,
    val netRial: Long,
    val theoreticalCostRial: Long,
    val status: SalesInvoiceStatus,
    val notes: String,
    val createdAtEpochMillis: Long? = null,
)

data class SalesInvoiceDetails(
    val invoice: SalesInvoiceRecord,
    val customerId: Long?,
    val dueEpochDay: Long?,
    val lines: List<SalesInvoiceLineRecord>,
    val payments: List<SalesPaymentRecord>,
)

data class HistoricalSalesDayClosureRecord(
    val businessEpochDay: Long,
    val netSalesRial: Long,
    val cogsRial: Long,
    val cashRial: Long,
    val cardRial: Long,
    val transferRial: Long,
    val creditRial: Long,
    val invoiceCount: Int,
    val returnCount: Int,
    val status: String,
    val revisionNo: Int,
    val closedByName: String,
    val note: String,
    val reopenReason: String,
)


data class SalesDashboardSummary(
    val netSalesRial: Long = 0,
    val invoiceNetRial: Long = 0,
    val invoiceCount: Int = 0,
    val returnRial: Long = 0,
    val newCustomerCount: Int = 0,
    val customerReceivablesRial: Long = 0,
) {
    val averageInvoiceRial: Long get() = if (invoiceCount <= 0) 0 else invoiceNetRial / invoiceCount
    val hasPeriodActivity: Boolean get() = invoiceCount > 0 || returnRial != 0L || netSalesRial != 0L || newCustomerCount > 0
}

interface SalesHistoryRepository {
    val customers: Flow<List<CustomerRecord>>
    val dayClosures: Flow<List<HistoricalSalesDayClosureRecord>>
    fun observeInvoices(query: String = ""): Flow<List<SalesInvoiceRecord>>
    fun observeRecentInvoices(limit: Int = 5): Flow<List<SalesInvoiceRecord>>
    fun observeDashboardSummary(
        fromEpochDay: Long,
        toEpochDay: Long,
        createdFromEpochMillis: Long,
        createdToEpochMillisExclusive: Long,
    ): Flow<SalesDashboardSummary>
    fun observeDetails(invoiceId: Long): Flow<SalesInvoiceDetails?>
    suspend fun saveCustomer(id: Long?, draft: CustomerDraft): Long
    suspend fun deactivateCustomer(id: Long)
}
