package ir.restaurant.management.domain.accounting

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial

/**
 * Stable business-facing account identities. Business domains depend on these roles rather than
 * concrete chart-of-account codes. The mapping to the current system chart belongs to accounting.
 */
enum class SemanticAccountRole {
    CASH,
    BANK,
    CARD_SETTLEMENT,
    PETTY_CASH,
    TREASURY_CLEARING,
    INVENTORY_ASSET,
    CUSTOMER_RECEIVABLE,
    PERSONAL_RECEIVABLE,
    CORPORATE_RECEIVABLE,
    SUPPLIER_CREDIT_RECEIVABLE,
    EMPLOYEE_ADVANCE_RECEIVABLE,
    FIXED_ASSET,
    ACCUMULATED_DEPRECIATION,
    ACCUMULATED_IMPAIRMENT,
    SUPPLIER_PAYABLE,
    GOODS_RECEIVED_NOT_INVOICED,
    PAYROLL_PAYABLE,
    TAX_PAYABLE,
    INSURANCE_PAYABLE,
    OWNER_CAPITAL,
    SALES_REVENUE,
    SERVICE_REVENUE,
    COGS,
    SALARY_EXPENSE,
    OVERTIME_EXPENSE,
    BONUS_EXPENSE,
    ALLOWANCE_EXPENSE,
    INVENTORY_WASTE_EXPENSE,
    INVENTORY_COUNT_GAIN,
    INVENTORY_COUNT_LOSS,
    PURCHASE_PRICE_VARIANCE,
    DEPRECIATION_EXPENSE,
    ASSET_DISPOSAL_LOSS,
    ASSET_DISPOSAL_GAIN,
    OTHER_INCOME,
    OTHER_OPERATING_EXPENSE,
    MAINTENANCE_EXPENSE,
    ASSET_IMPAIRMENT_LOSS,
}

interface SemanticAccountResolver {
    fun codeFor(role: SemanticAccountRole): String
}

/** Current system chart mapping, centralized behind semantic roles. */
object SystemSemanticAccountResolver : SemanticAccountResolver {
    private val codes = mapOf(
        SemanticAccountRole.CASH to "1101",
        SemanticAccountRole.BANK to "1102",
        SemanticAccountRole.CARD_SETTLEMENT to "1104",
        SemanticAccountRole.PETTY_CASH to "1103",
        SemanticAccountRole.TREASURY_CLEARING to "2199",
        SemanticAccountRole.INVENTORY_ASSET to "1301",
        SemanticAccountRole.CUSTOMER_RECEIVABLE to "1201",
        SemanticAccountRole.PERSONAL_RECEIVABLE to "1201",
        SemanticAccountRole.CORPORATE_RECEIVABLE to "1202",
        SemanticAccountRole.SUPPLIER_CREDIT_RECEIVABLE to "1203",
        SemanticAccountRole.EMPLOYEE_ADVANCE_RECEIVABLE to "1401",
        SemanticAccountRole.FIXED_ASSET to "1501",
        SemanticAccountRole.ACCUMULATED_DEPRECIATION to "1502",
        SemanticAccountRole.ACCUMULATED_IMPAIRMENT to "1503",
        SemanticAccountRole.SUPPLIER_PAYABLE to "2101",
        SemanticAccountRole.GOODS_RECEIVED_NOT_INVOICED to "2105",
        SemanticAccountRole.PAYROLL_PAYABLE to "2102",
        SemanticAccountRole.TAX_PAYABLE to "2103",
        SemanticAccountRole.INSURANCE_PAYABLE to "2104",
        SemanticAccountRole.OWNER_CAPITAL to "3101",
        SemanticAccountRole.SALES_REVENUE to "4101",
        SemanticAccountRole.SERVICE_REVENUE to "4103",
        SemanticAccountRole.COGS to "5101",
        SemanticAccountRole.SALARY_EXPENSE to "6101",
        SemanticAccountRole.OVERTIME_EXPENSE to "6113",
        SemanticAccountRole.BONUS_EXPENSE to "6114",
        SemanticAccountRole.ALLOWANCE_EXPENSE to "6115",
        SemanticAccountRole.INVENTORY_WASTE_EXPENSE to "6104",
        SemanticAccountRole.INVENTORY_COUNT_GAIN to "4102",
        SemanticAccountRole.INVENTORY_COUNT_LOSS to "6105",
        SemanticAccountRole.PURCHASE_PRICE_VARIANCE to "6111",
        SemanticAccountRole.DEPRECIATION_EXPENSE to "6110",
        SemanticAccountRole.ASSET_DISPOSAL_LOSS to "6112",
        SemanticAccountRole.ASSET_DISPOSAL_GAIN to "4102",
        SemanticAccountRole.OTHER_INCOME to "4102",
        SemanticAccountRole.OTHER_OPERATING_EXPENSE to "6105",
        SemanticAccountRole.MAINTENANCE_EXPENSE to "6107",
        SemanticAccountRole.ASSET_IMPAIRMENT_LOSS to "6112",
    )

    override fun codeFor(role: SemanticAccountRole): String =
        codes[role] ?: error("برای نقش حسابداری ${role.name} حساب سیستمی تعریف نشده است.")
}

data class SemanticJournalLine(
    val role: SemanticAccountRole,
    val debit: MoneyRial = MoneyRial.ZERO,
    val credit: MoneyRial = MoneyRial.ZERO,
    val memo: String = "",
) {
    init {
        require((debit > MoneyRial.ZERO) xor (credit > MoneyRial.ZERO)) {
            "هر آرتیکل معنایی باید فقط بدهکار یا فقط بستانکار باشد."
        }
    }
}

enum class JournalStatus(val storedValue: String) {
    DRAFT("DRAFT"),
    POSTED("POSTED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): JournalStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

/**
 * Structural scope of an accounting journal. A null branch id is not enough to distinguish a real
 * organization-wide document from historical data whose branch cannot be proven, so the scope is
 * persisted explicitly on the journal header.
 */
enum class AccountingScope(val storedValue: String) {
    BRANCH("BRANCH"),
    ORGANIZATION("ORGANIZATION"),
    UNASSIGNED_LEGACY("UNASSIGNED_LEGACY");

    fun requireCompatible(branchId: Long?) {
        when (this) {
            BRANCH -> require(branchId != null && branchId > 0) { "accounting_branch_scope_requires_branch" }
            ORGANIZATION, UNASSIGNED_LEGACY -> require(branchId == null) { "accounting_non_branch_scope_forbids_branch" }
        }
    }

    companion object {
        fun fromStoredValue(value: String): AccountingScope =
            entries.firstOrNull { it.storedValue == value } ?: UNASSIGNED_LEGACY
    }
}

data class AccountingPostingContext(
    val idempotencyKey: String,
    val correlationId: String,
    val actorId: Long,
    val reversalOfEntryId: Long? = null,
) {
    fun validated(): AccountingPostingContext {
        val key = idempotencyKey.trim()
        val correlation = CorrelationId.parse(correlationId).value
        require(key.matches(Regex("[A-Za-z0-9:_./-]{8,180}"))) { "کلید idempotency سند معتبر نیست." }
        require(actorId > 0) { "شناسه ثبت‌کننده سند معتبر نیست." }
        require(reversalOfEntryId == null || reversalOfEntryId > 0) { "شناسه سند مبنای برگشت معتبر نیست." }
        return copy(idempotencyKey = key, correlationId = correlation)
    }

    companion object {
        fun local(
            sourceType: String,
            sourceId: Long,
            suffix: String,
            actorId: Long,
            correlationId: String = "journal:${GlobalId.new().value}",
            reversalOfEntryId: Long? = null,
        ): AccountingPostingContext {
            val normalizedSource = sourceType.trim().uppercase().replace(' ', '_')
            val normalizedSuffix = suffix.trim().replace(' ', '_')
            require(normalizedSource.matches(Regex("[A-Z0-9_]{2,64}"))) { "نوع منبع سند معتبر نیست." }
            require(sourceId >= 0) { "شناسه منبع سند معتبر نیست." }
            require(normalizedSuffix.matches(Regex("[A-Za-z0-9:_./-]{2,100}"))) { "جزء کلید سند معتبر نیست." }
            return AccountingPostingContext(
                idempotencyKey = "$normalizedSource:$sourceId:$normalizedSuffix",
                correlationId = correlationId,
                actorId = actorId,
                reversalOfEntryId = reversalOfEntryId,
            ).validated()
        }
    }
}

data class ResolvedJournalPosting(
    val entryId: Long,
    val entryNo: String,
    val idempotentReplay: Boolean,
)

data class AccountingPostingCommand(
    val entryNo: String,
    val sourceType: String,
    val sourceId: Long,
    val businessEpochDay: Long,
    val description: String,
    val lines: List<SemanticJournalLine>,
    val idempotencyKey: String,
    val correlationId: CorrelationId,
    val actorId: Long,
    val status: JournalStatus = JournalStatus.POSTED,
    val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
    val branchId: Long? = null,
) {
    fun validated(): AccountingPostingCommand {
        val normalizedSource = sourceType.trim().uppercase().replace(' ', '_')
        val normalizedEntryNo = entryNo.trim()
        val normalizedDescription = description.trim()
        require(normalizedSource.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "accounting_source_type_invalid" }
        require(sourceId >= 0) { "accounting_source_id_invalid" }
        require(businessEpochDay > 0) { "accounting_business_date_invalid" }
        require(normalizedEntryNo.isNotBlank()) { "accounting_entry_no_missing" }
        require(normalizedDescription.isNotBlank()) { "accounting_description_missing" }
        require(status != JournalStatus.LEGACY_UNKNOWN) { "accounting_status_unknown" }
        accountingScope.requireCompatible(branchId)
        AccountingPostingContext(idempotencyKey, correlationId.value, actorId).validated()
        SemanticJournalDraft(
            entryNo = normalizedEntryNo,
            description = normalizedDescription,
            entryEpochDay = businessEpochDay,
            sourceType = normalizedSource,
            sourceId = sourceId,
            status = status,
            lines = lines,
            accountingScope = accountingScope,
            branchId = branchId,
        )
        return copy(
            entryNo = normalizedEntryNo,
            sourceType = normalizedSource,
            description = normalizedDescription,
        )
    }
}

data class AccountingReversalCommand(
    val originalEntryId: Long,
    val entryNo: String,
    val sourceType: String,
    val sourceId: Long,
    val businessEpochDay: Long,
    val reason: String,
    val idempotencyKey: String,
    val correlationId: CorrelationId,
    val actorId: Long,
) {
    fun validated(): AccountingReversalCommand {
        val normalizedSource = sourceType.trim().uppercase().replace(' ', '_')
        val normalizedReason = reason.trim()
        require(originalEntryId > 0) { "accounting_reversal_original_id_invalid" }
        require(sourceId > 0) { "accounting_reversal_source_id_invalid" }
        require(businessEpochDay > 0) { "accounting_reversal_date_invalid" }
        require(entryNo.isNotBlank()) { "accounting_reversal_entry_no_missing" }
        require(normalizedSource.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "accounting_reversal_source_type_invalid" }
        require(normalizedReason.length in 3..500) { "accounting_reversal_reason_invalid" }
        AccountingPostingContext(
            idempotencyKey = idempotencyKey,
            correlationId = correlationId.value,
            actorId = actorId,
            reversalOfEntryId = originalEntryId,
        ).validated()
        return copy(
            entryNo = entryNo.trim(),
            sourceType = normalizedSource,
            reason = normalizedReason,
        )
    }
}

data class AccountingPostingResult(
    val entryId: Long,
    val entryNo: String,
    val idempotentReplay: Boolean,
)

interface AccountingPostingService {
    suspend fun post(command: AccountingPostingCommand): AccountingPostingResult
    suspend fun reverse(command: AccountingReversalCommand): AccountingPostingResult
}

data class SemanticJournalDraft(
    val entryNo: String,
    val description: String,
    val entryEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val status: JournalStatus = JournalStatus.POSTED,
    val lines: List<SemanticJournalLine>,
    val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
    val branchId: Long? = null,
) {
    init {
        require(entryNo.isNotBlank()) { "شماره سند الزامی است." }
        require(description.isNotBlank()) { "شرح سند الزامی است." }
        require(entryEpochDay > 0) { "تاریخ سند معتبر نیست." }
        require(sourceType.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "نوع منبع سند معتبر نیست." }
        require(sourceId >= 0) { "شناسه منبع سند معتبر نیست." }
        accountingScope.requireCompatible(branchId)
        require(lines.size >= 2) { "سند باید حداقل دو آرتیکل داشته باشد." }
        require(MoneyRial.sum(lines.map { it.debit }) == MoneyRial.sum(lines.map { it.credit })) {
            "جمع بدهکار و بستانکار سند برابر نیست."
        }
    }
}
