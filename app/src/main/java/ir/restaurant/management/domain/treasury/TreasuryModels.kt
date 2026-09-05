package ir.restaurant.management.domain.treasury

import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation

enum class TreasuryChannel(val storedValue: String) {
    CASH("CASH"),
    BANK("BANK"),
    CARD("CARD"),
    TRANSFER("TRANSFER");

    companion object {
        fun fromStoredValue(value: String): TreasuryChannel =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() }
                ?: throw BusinessError.UnknownStoredValue(
                    ownerDomain = "treasury",
                    field = "treasury.channel",
                    storedValue = value,
                ).asViolation()
    }
}

enum class TreasuryDirection { RECEIPT, PAYMENT }
enum class TreasuryAccountKind { CASH, BANK, CARD_TERMINAL, PETTY_CASH }
enum class TreasuryTransactionKind { RECEIPT, PAYMENT, INTERNAL_TRANSFER, SETTLEMENT, RECONCILIATION }

/**
 * Typed economic intent for treasury commands. `sourceType` persisted in legacy tables is derived
 * from this enum; it is never parsed with substring/keyword heuristics to decide accounting nature.
 */
enum class TreasuryBusinessIntent(
    val storedValue: String,
    val counterpartRole: SemanticAccountRole?,
    val direction: TreasuryDirection?,
) {
    CUSTOMER_RECEIVABLE_COLLECTION("RECEIVABLE_COLLECTION", SemanticAccountRole.CUSTOMER_RECEIVABLE, TreasuryDirection.RECEIPT),
    CORPORATE_RECEIVABLE_COLLECTION("CORPORATE_RECEIVABLE_COLLECTION", SemanticAccountRole.CORPORATE_RECEIVABLE, TreasuryDirection.RECEIPT),
    PURCHASE_PAYABLE_SETTLEMENT("PURCHASE_SETTLEMENT", SemanticAccountRole.SUPPLIER_PAYABLE, TreasuryDirection.PAYMENT),
    SUPPLIER_SETTLEMENT("SUPPLIER_SETTLEMENT", SemanticAccountRole.SUPPLIER_PAYABLE, TreasuryDirection.PAYMENT),
    PAYROLL_PAYMENT("PAYROLL_PAYMENT", SemanticAccountRole.PAYROLL_PAYABLE, TreasuryDirection.PAYMENT),
    OWNER_CAPITAL("OWNER_CAPITAL", SemanticAccountRole.OWNER_CAPITAL, TreasuryDirection.RECEIPT),
    OTHER_INCOME("OTHER_INCOME", SemanticAccountRole.OTHER_INCOME, TreasuryDirection.RECEIPT),
    OPERATING_EXPENSE("OPERATING_EXPENSE", SemanticAccountRole.OTHER_OPERATING_EXPENSE, TreasuryDirection.PAYMENT),
    ASSET_ACQUISITION("ASSET_ACQUISITION", SemanticAccountRole.FIXED_ASSET, TreasuryDirection.PAYMENT),
    ASSET_MAINTENANCE("ASSET_MAINTENANCE", SemanticAccountRole.MAINTENANCE_EXPENSE, TreasuryDirection.PAYMENT),
    ASSET_DISPOSAL_RECEIPT("ASSET_DISPOSAL_RECEIPT", SemanticAccountRole.TREASURY_CLEARING, TreasuryDirection.RECEIPT),
    EMPLOYEE_ADVANCE_DISBURSEMENT("EMPLOYEE_ADVANCE_DISBURSEMENT", SemanticAccountRole.EMPLOYEE_ADVANCE_RECEIVABLE, TreasuryDirection.PAYMENT),
    EMPLOYEE_ADVANCE_REPAYMENT("EMPLOYEE_ADVANCE_REPAYMENT", SemanticAccountRole.EMPLOYEE_ADVANCE_RECEIVABLE, TreasuryDirection.RECEIPT),
    TAX_PAYMENT("TAX_PAYMENT", SemanticAccountRole.TAX_PAYABLE, TreasuryDirection.PAYMENT),
    DAILY_SALES_SETTLEMENT("DAILY_SALES_SETTLEMENT", SemanticAccountRole.TREASURY_CLEARING, TreasuryDirection.RECEIPT),
    INTERNAL_TRANSFER("INTERNAL_TRANSFER", null, null),
    TREASURY_RECONCILIATION("TREASURY_RECONCILIATION", null, null),
    ;

    fun requireDirection(actual: TreasuryDirection) {
        direction?.let { require(it == actual) { "treasury_business_intent_direction_mismatch" } }
    }

    companion object {
        /** Exact manual/UI adapter. Unknown or module-owned financial intents fail closed. */
        fun fromExternalSource(sourceType: String, direction: TreasuryDirection): TreasuryBusinessIntent {
            val normalized = sourceType.trim().uppercase().replace(' ', '_')
            val intent = when (normalized) {
                "OWNER_CAPITAL" -> OWNER_CAPITAL
                "OTHER_INCOME" -> OTHER_INCOME
                "OPERATING_EXPENSE" -> OPERATING_EXPENSE
                "TAX_PAYMENT" -> TAX_PAYMENT
                else -> throw BusinessError.UnknownStoredValue(
                    ownerDomain = "treasury",
                    field = "treasury.businessIntent",
                    storedValue = sourceType,
                ).asViolation()
            }
            intent.requireDirection(direction)
            return intent
        }
    }
}

enum class SettlementStatus(val storedValue: String) {
    PENDING("PENDING"),
    SETTLED("SETTLED"),
    REVERSED("REVERSED"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN");

    companion object {
        fun fromStoredValue(value: String): SettlementStatus =
            entries.firstOrNull { it.storedValue == value.trim().uppercase() } ?: LEGACY_UNKNOWN
    }
}

@JvmInline
value class TreasuryAccountId private constructor(val value: String) {
    companion object {
        fun parse(raw: String): TreasuryAccountId {
            val normalized = raw.trim().lowercase()
            require(normalized.matches(Regex("[a-z][a-z0-9_-]{2,63}"))) { "treasury_account_id_invalid" }
            return TreasuryAccountId(normalized)
        }
    }
}

data class TreasuryAccount(
    val id: TreasuryAccountId,
    val name: String,
    val kind: TreasuryAccountKind,
    val channel: TreasuryChannel,
    val settlementRole: SemanticAccountRole,
    val isActive: Boolean,
) {
    init {
        require(name.isNotBlank()) { "treasury_account_name_missing" }
        require(settlementRole in LIQUIDITY_ROLES) { "treasury_account_gl_role_not_liquidity" }
    }

    private companion object {
        val LIQUIDITY_ROLES = setOf(
            SemanticAccountRole.CASH,
            SemanticAccountRole.BANK,
            SemanticAccountRole.CARD_SETTLEMENT,
            SemanticAccountRole.PETTY_CASH,
        )
    }
}

interface TreasuryAccountCatalog {
    fun activeAccounts(): List<TreasuryAccount>
    fun account(id: TreasuryAccountId): TreasuryAccount?
}

sealed interface TreasuryCommand {
    val commandId: GlobalId
    val businessEpochDay: Long
    val correlationId: CorrelationId
    val businessIntent: TreasuryBusinessIntent
    val sourceType: String get() = businessIntent.storedValue
    val sourceId: Long
    val reason: String
    val accountingScope: AccountingScope
    val branchId: Long?

    data class Receipt(
        override val commandId: GlobalId,
        override val businessEpochDay: Long,
        override val correlationId: CorrelationId,
        override val businessIntent: TreasuryBusinessIntent,
        override val sourceId: Long,
        override val reason: String,
        override val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
        override val branchId: Long? = null,
        val accountId: TreasuryAccountId,
        val channel: TreasuryChannel,
        val amount: MoneyRial,
    ) : TreasuryCommand

    data class Payment(
        override val commandId: GlobalId,
        override val businessEpochDay: Long,
        override val correlationId: CorrelationId,
        override val businessIntent: TreasuryBusinessIntent,
        override val sourceId: Long,
        override val reason: String,
        override val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
        override val branchId: Long? = null,
        val accountId: TreasuryAccountId,
        val channel: TreasuryChannel,
        val amount: MoneyRial,
    ) : TreasuryCommand

    data class InternalTransfer(
        override val commandId: GlobalId,
        override val businessEpochDay: Long,
        override val correlationId: CorrelationId,
        override val businessIntent: TreasuryBusinessIntent = TreasuryBusinessIntent.INTERNAL_TRANSFER,
        override val sourceId: Long,
        override val reason: String,
        override val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
        override val branchId: Long? = null,
        val fromAccountId: TreasuryAccountId,
        val toAccountId: TreasuryAccountId,
        val amount: MoneyRial,
    ) : TreasuryCommand

    data class Settlement(
        override val commandId: GlobalId,
        override val businessEpochDay: Long,
        override val correlationId: CorrelationId,
        override val businessIntent: TreasuryBusinessIntent,
        override val sourceId: Long,
        override val reason: String,
        override val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
        override val branchId: Long? = null,
        val accountId: TreasuryAccountId,
        val direction: TreasuryDirection,
        val channel: TreasuryChannel,
        val amount: MoneyRial,
    ) : TreasuryCommand

    data class Reconciliation(
        override val commandId: GlobalId,
        override val businessEpochDay: Long,
        override val correlationId: CorrelationId,
        override val businessIntent: TreasuryBusinessIntent = TreasuryBusinessIntent.TREASURY_RECONCILIATION,
        override val sourceId: Long,
        override val reason: String,
        override val accountingScope: AccountingScope = AccountingScope.ORGANIZATION,
        override val branchId: Long? = null,
        val accountId: TreasuryAccountId,
        val expected: MoneyRial,
        val actual: MoneyRial,
    ) : TreasuryCommand
}

data class TreasuryTransaction(
    val id: String,
    val kind: TreasuryTransactionKind,
    val businessEpochDay: Long,
    val correlationId: CorrelationId,
    val sourceType: String,
    val sourceId: Long,
    val amount: MoneyRial,
    val journalEntryId: Long?,
    val idempotentReplay: Boolean,
)

data class TreasuryReversalCommand(
    val commandId: GlobalId,
    val originalTransactionId: String,
    val originalJournalEntryId: Long,
    val businessEpochDay: Long,
    val correlationId: CorrelationId,
    val sourceType: String,
    val sourceId: Long,
    val reason: String,
    val accountId: TreasuryAccountId,
    val channel: TreasuryChannel,
    val amount: MoneyRial,
) {
    fun validated(): TreasuryReversalCommand {
        require(originalTransactionId.isNotBlank()) { "treasury_original_transaction_missing" }
        require(originalJournalEntryId > 0 && businessEpochDay > 0 && sourceId > 0)
        require(sourceType.trim().uppercase().matches(Regex("[A-Z][A-Z0-9_]{1,63}")))
        require(reason.trim().length in 3..500)
        require(amount > MoneyRial.ZERO)
        return copy(sourceType = sourceType.trim().uppercase(), reason = reason.trim())
    }
}

interface TreasuryService {
    suspend fun execute(command: TreasuryCommand): TreasuryTransaction
    suspend fun reverse(command: TreasuryReversalCommand): TreasuryTransaction
}

data class TreasuryLedgerRecord(
    val id: String,
    val kind: TreasuryTransactionKind,
    val businessEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val amountRial: Long,
    val status: String,
    val reason: String,
    val journalEntryId: Long?,
    val createdAtEpochMillis: Long,
)

data class TreasuryReversalContext(
    val transactionId: String,
    val status: String,
    val journalEntryId: Long?,
    val businessEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val amountRial: Long,
    val accountId: TreasuryAccountId,
    val channel: TreasuryChannel,
    val reversalOfTransactionId: String?,
)

interface TreasuryLedgerReader {
    val recentTransactions: kotlinx.coroutines.flow.Flow<List<TreasuryLedgerRecord>>
    fun observeBalance(accountId: TreasuryAccountId): kotlinx.coroutines.flow.Flow<Long>
    suspend fun reversalContext(transactionId: String): TreasuryReversalContext?
    suspend fun reversalContextByJournalEntryId(journalEntryId: Long): TreasuryReversalContext?
    suspend fun activeReversalContextsBySource(sourceType: String, sourceId: Long): List<TreasuryReversalContext>
}

fun TreasuryCommand.validated(): TreasuryCommand {
    require(businessEpochDay > 0) { "treasury_business_date_invalid" }
    require(sourceId > 0) { "treasury_source_id_invalid" }
    require(reason.trim().length in 3..500) { "treasury_reason_invalid" }
    val scopedBranchId = branchId
    when (accountingScope) {
        AccountingScope.BRANCH -> require(scopedBranchId != null && scopedBranchId > 0) { "treasury_branch_scope_missing" }
        AccountingScope.ORGANIZATION -> require(scopedBranchId == null) { "treasury_organization_scope_has_branch" }
        AccountingScope.UNASSIGNED_LEGACY -> error("treasury_new_command_cannot_be_unassigned_legacy")
    }
    when (this) {
        is TreasuryCommand.Receipt -> {
            businessIntent.requireDirection(TreasuryDirection.RECEIPT)
            require(businessIntent !in setOf(TreasuryBusinessIntent.INTERNAL_TRANSFER, TreasuryBusinessIntent.TREASURY_RECONCILIATION))
            require(amount > MoneyRial.ZERO) { "treasury_receipt_amount_not_positive" }
        }
        is TreasuryCommand.Payment -> {
            businessIntent.requireDirection(TreasuryDirection.PAYMENT)
            require(businessIntent !in setOf(TreasuryBusinessIntent.INTERNAL_TRANSFER, TreasuryBusinessIntent.TREASURY_RECONCILIATION))
            require(amount > MoneyRial.ZERO) { "treasury_payment_amount_not_positive" }
        }
        is TreasuryCommand.InternalTransfer -> {
            require(businessIntent == TreasuryBusinessIntent.INTERNAL_TRANSFER)
            require(amount > MoneyRial.ZERO) { "treasury_transfer_amount_not_positive" }
            require(fromAccountId != toAccountId) { "treasury_transfer_same_account" }
        }
        is TreasuryCommand.Settlement -> {
            businessIntent.requireDirection(direction)
            require(businessIntent !in setOf(TreasuryBusinessIntent.INTERNAL_TRANSFER, TreasuryBusinessIntent.TREASURY_RECONCILIATION))
            require(amount > MoneyRial.ZERO) { "treasury_settlement_amount_not_positive" }
        }
        is TreasuryCommand.Reconciliation -> require(businessIntent == TreasuryBusinessIntent.TREASURY_RECONCILIATION)
    }
    return this
}

data class TreasuryMovementDraft(
    val direction: TreasuryDirection,
    val channel: TreasuryChannel,
    val amount: MoneyRial,
    val epochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val reference: String = "",
) {
    fun validated(): TreasuryMovementDraft {
        require(amount > MoneyRial.ZERO) { "treasury_amount_not_positive" }
        require(epochDay > 0) { "treasury_business_date_invalid" }
        require(sourceType.isNotBlank() && sourceId > 0) { "treasury_source_missing" }
        return copy(sourceType = sourceType.trim(), reference = reference.trim())
    }
}
