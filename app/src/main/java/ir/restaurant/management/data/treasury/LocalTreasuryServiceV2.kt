package ir.restaurant.management.data.treasury

import androidx.room.withTransaction
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.TreasuryLedgerEntryEntity
import ir.restaurant.management.data.db.TreasuryReconciliationEntity
import ir.restaurant.management.data.db.TreasuryTransactionEntity
import ir.restaurant.management.data.repository.LocalAuditEventWriter
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.AccountingReversalCommand
import ir.restaurant.management.domain.accounting.JournalStatus
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.treasury.TreasuryAccount
import ir.restaurant.management.domain.treasury.TreasuryAccountCatalog
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.treasury.TreasuryTransaction
import ir.restaurant.management.domain.treasury.TreasuryTransactionKind
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryLedgerRecord
import ir.restaurant.management.domain.treasury.TreasuryReversalContext
import ir.restaurant.management.domain.treasury.validated
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Treasury 2.0 is the canonical owner of operational liquidity movements. Every treasury command
 * creates its accounting posting, treasury header/lines and audit event inside the same
 * outer Room transaction, so a failure in any side effect rolls the whole command back.
 */
class LocalTreasuryServiceV2(
    private val database: AppDatabase,
    private val accounting: AccountingPostingService,
    private val authorizer: AuthorizationService,
    private val accountCatalog: TreasuryAccountCatalog,
    private val clock: () -> Long = System::currentTimeMillis,
) : TreasuryService, TreasuryLedgerReader {
    private val audit = LocalAuditEventWriter(database)

    override val recentTransactions: Flow<List<TreasuryLedgerRecord>> = database.treasuryDao().observeRecentTransactions().map { rows ->
        rows.map { row -> TreasuryLedgerRecord(
            id = row.id, kind = TreasuryTransactionKind.valueOf(row.kind), businessEpochDay = row.businessEpochDay,
            sourceType = row.sourceType, sourceId = row.sourceId, amountRial = row.amountRial, status = row.status, reason = row.reason,
            journalEntryId = row.journalEntryId, createdAtEpochMillis = row.createdAtEpochMillis,
        ) }
    }

    override fun observeBalance(accountId: ir.restaurant.management.domain.treasury.TreasuryAccountId): Flow<Long> =
        database.treasuryDao().observeLedgerBalance(accountId.value)

    override suspend fun reversalContext(transactionId: String): TreasuryReversalContext? {
        val normalizedId = transactionId.trim()
        if (normalizedId.isEmpty()) return null
        return database.treasuryDao().transactionById(normalizedId)?.toReversalContext()
    }

    override suspend fun reversalContextByJournalEntryId(journalEntryId: Long): TreasuryReversalContext? {
        if (journalEntryId <= 0) return null
        return database.treasuryDao().transactionByJournalEntryId(journalEntryId)?.toReversalContext()
    }

    override suspend fun activeReversalContextsBySource(sourceType: String, sourceId: Long): List<TreasuryReversalContext> {
        if (sourceType.isBlank() || sourceId <= 0) return emptyList()
        return database.treasuryDao().activeTransactionsBySource(sourceType.trim().uppercase(), sourceId).mapNotNull { it.toReversalContext() }
    }

    private suspend fun TreasuryTransactionEntity.toReversalContext(): TreasuryReversalContext? {
        val primaryLine = database.treasuryDao().entriesForTransaction(id).firstOrNull() ?: return null
        val accountId = ir.restaurant.management.domain.treasury.TreasuryAccountId.parse(primaryLine.accountId)
        val account = accountCatalog.account(accountId) ?: return null
        return TreasuryReversalContext(
            transactionId = id,
            status = status,
            journalEntryId = journalEntryId,
            businessEpochDay = businessEpochDay,
            sourceType = sourceType,
            sourceId = sourceId,
            amountRial = amountRial,
            accountId = account.id,
            channel = account.channel,
            reversalOfTransactionId = reversalOfTransactionId,
        )
    }

    override suspend fun execute(command: TreasuryCommand): TreasuryTransaction {
        val valid = command.validated()
        validateAccountSelection(valid)
        val actor = authorize(valid)
        return database.withTransaction {
            database.treasuryDao().byCommandId(valid.commandId.value)?.let { existing ->
                verifyReplay(existing, valid)
                return@withTransaction existing.toDomain(idempotentReplay = true)
            }

            val now = clock()
            val posting = when (valid) {
                is TreasuryCommand.Receipt -> postReceipt(valid, actor.id)
                is TreasuryCommand.Payment -> postPayment(valid, actor.id)
                is TreasuryCommand.InternalTransfer -> postTransfer(valid, actor.id)
                is TreasuryCommand.Settlement -> postSettlement(valid, actor.id)
                is TreasuryCommand.Reconciliation -> postReconciliation(valid, actor.id)
            }
            val entries = ledgerEntries(valid, actor.id, now)
            val transaction = TreasuryTransactionEntity(
                id = valid.commandId.value,
                commandId = valid.commandId.value,
                kind = valid.kindName(),
                businessEpochDay = valid.businessEpochDay,
                sourceType = valid.sourceType,
                sourceId = valid.sourceId,
                counterpartyType = valid.sourceType,
                counterpartyId = valid.sourceId,
                reference = valid.commandId.value,
                reason = valid.reason,
                amountRial = valid.transactionAmountRial(),
                journalEntryId = posting?.entryId,
                actorId = actor.id,
                correlationId = valid.correlationId.value,
                createdAtEpochMillis = now,
            )
            database.treasuryDao().insertTransaction(transaction)
            if (entries.isNotEmpty()) database.treasuryDao().insertLedgerEntries(entries)
            if (valid is TreasuryCommand.Reconciliation) {
                val difference = valid.actual.value - valid.expected.value
                database.treasuryDao().insertReconciliation(
                    TreasuryReconciliationEntity(
                        transactionId = transaction.id,
                        accountId = valid.accountId.value,
                        businessEpochDay = valid.businessEpochDay,
                        expectedRial = valid.expected.value,
                        actualRial = valid.actual.value,
                        differenceRial = difference,
                        reason = valid.reason,
                        actorId = actor.id,
                        createdAtEpochMillis = now,
                    ),
                )
            }
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "POST",
                entityType = "TREASURY_TRANSACTION",
                entityId = null,
                description = "${transaction.kind} ${transaction.amountRial} ریال؛ ${transaction.sourceType}:${transaction.sourceId}",
                occurredAtEpochMillis = now,
                businessEpochDay = transaction.businessEpochDay,
                reason = transaction.reason,
                afterSnapshot = "id=${transaction.id};kind=${transaction.kind};amount=${transaction.amountRial};journal=${transaction.journalEntryId}",
                correlationId = transaction.correlationId,
                referenceType = transaction.sourceType,
                referenceId = transaction.sourceId,
            )
            transaction.toDomain(idempotentReplay = posting?.idempotentReplay == true)
        }
    }

    override suspend fun reverse(command: TreasuryReversalCommand): TreasuryTransaction {
        val valid = command.validated()
        return database.withTransaction {
            database.treasuryDao().byCommandId(valid.commandId.value)?.let { replay ->
                return@withTransaction replay.toDomain(idempotentReplay = true)
            }
            val original = database.treasuryDao().transactionById(valid.originalTransactionId)
                ?: throw BusinessError.EntityNotFound("TREASURY_TRANSACTION", null).asViolation()
            val actor = authorizeReversal(original.sourceType)
            require(original.status == "POSTED") { "treasury_transaction_not_posted" }
            require(original.journalEntryId == valid.originalJournalEntryId) { "treasury_original_journal_mismatch" }
            require(valid.sourceId == original.sourceId) { "treasury_reversal_source_id_mismatch" }
            require(valid.amount.value == original.amountRial) { "treasury_reversal_amount_mismatch" }
            val originalLines = database.treasuryDao().entriesForTransaction(original.id)
            val originalPrimaryAccountId = originalLines.firstOrNull()?.accountId ?: error("treasury_original_ledger_missing")
            require(originalPrimaryAccountId == valid.accountId.value) { "treasury_reversal_account_mismatch" }
            require(account(valid.accountId).channel == valid.channel) { "treasury_reversal_channel_mismatch" }
            val result = accounting.reverse(
                AccountingReversalCommand(
                    originalEntryId = valid.originalJournalEntryId,
                    entryNo = "خز-ب-${valid.commandId.value}",
                    sourceType = valid.sourceType,
                    sourceId = valid.sourceId,
                    businessEpochDay = valid.businessEpochDay,
                    reason = valid.reason,
                    idempotencyKey = "TREASURY_REVERSAL:${valid.commandId.value}",
                    correlationId = valid.correlationId,
                    actorId = actor.id,
                ),
            )
            val now = clock()
            require(database.treasuryDao().markReversed(original.id, now) == 1) { "treasury_concurrent_reversal" }
            val reversal = TreasuryTransactionEntity(
                id = valid.commandId.value,
                commandId = valid.commandId.value,
                kind = original.kind,
                businessEpochDay = valid.businessEpochDay,
                sourceType = valid.sourceType,
                sourceId = valid.sourceId,
                counterpartyType = original.counterpartyType,
                counterpartyId = original.counterpartyId,
                reference = original.reference,
                reason = valid.reason,
                amountRial = original.amountRial,
                status = "POSTED",
                journalEntryId = result.entryId,
                reversalOfTransactionId = original.id,
                actorId = actor.id,
                correlationId = valid.correlationId.value,
                createdAtEpochMillis = now,
            )
            database.treasuryDao().insertTransaction(reversal)
            database.treasuryDao().insertLedgerEntries(
                originalLines.map { line ->
                    line.copy(
                        id = 0,
                        transactionId = reversal.id,
                        direction = if (line.direction == TreasuryDirection.RECEIPT.name) TreasuryDirection.PAYMENT.name else TreasuryDirection.RECEIPT.name,
                        sourceType = valid.sourceType,
                        sourceId = valid.sourceId,
                        reference = "REVERSAL_OF:${original.id}",
                        actorId = actor.id,
                        createdAtEpochMillis = now,
                    )
                },
            )
            audit.appendAuthorized(
                authorizer = authorizer,
                action = "REVERSE",
                entityType = "TREASURY_TRANSACTION",
                entityId = null,
                description = "برگشت تراکنش خزانه ${original.id}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.businessEpochDay,
                reason = valid.reason,
                beforeSnapshot = "id=${original.id};status=${original.status};journal=${original.journalEntryId}",
                afterSnapshot = "reversal=${reversal.id};journal=${reversal.journalEntryId}",
                correlationId = valid.correlationId.value,
                referenceType = valid.sourceType,
                referenceId = valid.sourceId,
            )
            reversal.toDomain(idempotentReplay = result.idempotentReplay)
        }
    }

    private suspend fun postReceipt(command: TreasuryCommand.Receipt, actorId: Long) =
        accounting.post(
            postingCommand(
                command = command,
                actorId = actorId,
                lines = listOf(
                    SemanticJournalLine(accountRole(account(command.accountId)), debit = command.amount, memo = command.reason),
                    SemanticJournalLine(counterpartRole(command), credit = command.amount, memo = command.businessIntent.storedValue),
                ),
            ),
        )

    private suspend fun postPayment(command: TreasuryCommand.Payment, actorId: Long) =
        accounting.post(
            postingCommand(
                command = command,
                actorId = actorId,
                lines = listOf(
                    SemanticJournalLine(counterpartRole(command), debit = command.amount, memo = command.businessIntent.storedValue),
                    SemanticJournalLine(accountRole(account(command.accountId)), credit = command.amount, memo = command.reason),
                ),
            ),
        )

    private suspend fun postSettlement(command: TreasuryCommand.Settlement, actorId: Long) =
        if (command.direction == TreasuryDirection.RECEIPT) {
            accounting.post(
                postingCommand(
                    command,
                    actorId,
                    listOf(
                        SemanticJournalLine(accountRole(account(command.accountId)), debit = command.amount),
                        SemanticJournalLine(counterpartRole(command), credit = command.amount),
                    ),
                ),
            )
        } else {
            accounting.post(
                postingCommand(
                    command,
                    actorId,
                    listOf(
                        SemanticJournalLine(counterpartRole(command), debit = command.amount),
                        SemanticJournalLine(accountRole(account(command.accountId)), credit = command.amount),
                    ),
                ),
            )
        }

    private suspend fun postTransfer(command: TreasuryCommand.InternalTransfer, actorId: Long) =
        accounting.post(
            postingCommand(
                command,
                actorId,
                listOf(
                    SemanticJournalLine(accountRole(account(command.toAccountId)), debit = command.amount, memo = "انتقال ورودی"),
                    SemanticJournalLine(accountRole(account(command.fromAccountId)), credit = command.amount, memo = "انتقال خروجی"),
                ),
            ),
        )

    private suspend fun postReconciliation(command: TreasuryCommand.Reconciliation, actorId: Long): ir.restaurant.management.domain.accounting.AccountingPostingResult? {
        val difference = command.actual.value - command.expected.value
        if (difference == 0L) return null
        val amount = MoneyRial.of(difference.absoluteValue)
        val accountRole = accountRole(account(command.accountId))
        val lines = if (difference > 0) {
            listOf(
                SemanticJournalLine(accountRole, debit = amount, memo = "افزایش ناشی از مغایرت"),
                SemanticJournalLine(SemanticAccountRole.OTHER_INCOME, credit = amount, memo = command.reason),
            )
        } else {
            listOf(
                SemanticJournalLine(SemanticAccountRole.OTHER_OPERATING_EXPENSE, debit = amount, memo = command.reason),
                SemanticJournalLine(accountRole, credit = amount, memo = "کسری ناشی از مغایرت"),
            )
        }
        return accounting.post(postingCommand(command, actorId, lines))
    }

    private fun postingCommand(command: TreasuryCommand, actorId: Long, lines: List<SemanticJournalLine>) =
        AccountingPostingCommand(
            entryNo = "خز-${command.commandId.value}",
            sourceType = command.sourceType,
            sourceId = command.sourceId,
            businessEpochDay = command.businessEpochDay,
            description = command.reason,
            lines = lines,
            idempotencyKey = "TREASURY:${command.commandId.value}",
            correlationId = command.correlationId,
            actorId = actorId,
            status = JournalStatus.POSTED,
            accountingScope = command.accountingScope,
            branchId = command.branchId,
        )


    private fun validateAccountSelection(command: TreasuryCommand) {
        when (command) {
            is TreasuryCommand.Receipt -> require(account(command.accountId).channel == command.channel) { "treasury_account_channel_mismatch" }
            is TreasuryCommand.Payment -> require(account(command.accountId).channel == command.channel) { "treasury_account_channel_mismatch" }
            is TreasuryCommand.Settlement -> require(account(command.accountId).channel == command.channel) { "treasury_account_channel_mismatch" }
            is TreasuryCommand.InternalTransfer -> {
                account(command.fromAccountId)
                account(command.toAccountId)
            }
            is TreasuryCommand.Reconciliation -> account(command.accountId)
        }
    }

    private fun account(id: ir.restaurant.management.domain.treasury.TreasuryAccountId): TreasuryAccount =
        accountCatalog.account(id)?.takeIf { it.isActive }
            ?: throw BusinessError.EntityNotFound("TREASURY_ACCOUNT", null).asViolation()

    private fun accountRole(account: TreasuryAccount): SemanticAccountRole = account.settlementRole

    private fun counterpartRole(command: TreasuryCommand): SemanticAccountRole =
        command.businessIntent.counterpartRole
            ?: throw BusinessError.InvalidBusinessState("TREASURY_INTENT", command.businessIntent.storedValue).asViolation()

    private suspend fun authorize(command: TreasuryCommand): ir.restaurant.management.domain.security.AuthorizedActor {
        return when (command.businessIntent) {
            TreasuryBusinessIntent.DAILY_SALES_SETTLEMENT -> authorizer.require(Permission.DAILY_SALES_POST)
            TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION, TreasuryBusinessIntent.CORPORATE_RECEIVABLE_COLLECTION -> authorizer.require(Permission.RECEIVABLE_COLLECT)
            TreasuryBusinessIntent.PAYROLL_PAYMENT -> authorizer.require(Permission.PAYROLL_PAY)
            TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT, TreasuryBusinessIntent.SUPPLIER_SETTLEMENT -> {
                authorizer.require(Permission.PURCHASES)
                authorizer.require(Permission.PAYMENT_APPROVE)
            }
            TreasuryBusinessIntent.ASSET_ACQUISITION,
            TreasuryBusinessIntent.ASSET_MAINTENANCE,
            TreasuryBusinessIntent.ASSET_DISPOSAL_RECEIPT -> authorizer.require(Permission.ASSET_LIFECYCLE)
            TreasuryBusinessIntent.EMPLOYEE_ADVANCE_DISBURSEMENT -> authorizer.require(Permission.ADVANCE_CREATE)
            TreasuryBusinessIntent.EMPLOYEE_ADVANCE_REPAYMENT -> authorizer.require(Permission.ADVANCE_SETTLE)
            else -> {
                authorizer.require(Permission.TREASURY)
                when (command) {
                    is TreasuryCommand.Payment, is TreasuryCommand.Settlement -> authorizer.require(Permission.PAYMENT_APPROVE)
                    else -> authorizer.require(Permission.ACCOUNTING)
                }
            }
        }
    }

    private suspend fun authorizeReversal(sourceType: String): ir.restaurant.management.domain.security.AuthorizedActor = when (sourceType) {
        TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION.storedValue, TreasuryBusinessIntent.CORPORATE_RECEIVABLE_COLLECTION.storedValue -> authorizer.require(Permission.RECEIVABLE_ADJUST)
        TreasuryBusinessIntent.DAILY_SALES_SETTLEMENT.storedValue -> authorizer.require(Permission.DAILY_SALES_VOID)
        TreasuryBusinessIntent.PAYROLL_PAYMENT.storedValue -> authorizer.require(Permission.PAYROLL_REVERSE)
        TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT.storedValue, "PURCHASE_PAYABLE", "PURCHASE_SETTLEMENT" -> authorizer.require(Permission.PAYMENT_APPROVE)
        else -> {
            authorizer.require(Permission.TREASURY)
            authorizer.require(Permission.JOURNAL_REVERSE)
        }
    }

    private fun ledgerEntries(command: TreasuryCommand, actorId: Long, now: Long): List<TreasuryLedgerEntryEntity> {
        fun row(accountId: String, direction: TreasuryDirection, amount: Long) = TreasuryLedgerEntryEntity(
            transactionId = command.commandId.value,
            accountId = accountId,
            direction = direction.name,
            amountRial = amount,
            sourceType = command.sourceType,
            sourceId = command.sourceId,
            counterpartyType = command.sourceType,
            counterpartyId = command.sourceId,
            reference = command.commandId.value,
            businessEpochDay = command.businessEpochDay,
            actorId = actorId,
            createdAtEpochMillis = now,
        )
        return when (command) {
            is TreasuryCommand.Receipt -> listOf(row(command.accountId.value, TreasuryDirection.RECEIPT, command.amount.value))
            is TreasuryCommand.Payment -> listOf(row(command.accountId.value, TreasuryDirection.PAYMENT, command.amount.value))
            is TreasuryCommand.Settlement -> listOf(row(command.accountId.value, command.direction, command.amount.value))
            is TreasuryCommand.InternalTransfer -> listOf(
                row(command.fromAccountId.value, TreasuryDirection.PAYMENT, command.amount.value),
                row(command.toAccountId.value, TreasuryDirection.RECEIPT, command.amount.value),
            )
            is TreasuryCommand.Reconciliation -> {
                val difference = command.actual.value - command.expected.value
                if (difference == 0L) emptyList() else listOf(
                    row(
                        command.accountId.value,
                        if (difference > 0) TreasuryDirection.RECEIPT else TreasuryDirection.PAYMENT,
                        difference.absoluteValue,
                    ),
                )
            }
        }
    }

    private suspend fun verifyReplay(existing: TreasuryTransactionEntity, command: TreasuryCommand) {
        fun conflict(): Nothing = throw BusinessError.IdempotencyConflict(command.commandId.value).asViolation()
        if (existing.kind != command.kindName() || existing.businessEpochDay != command.businessEpochDay ||
            existing.sourceType != command.sourceType || existing.sourceId != command.sourceId ||
            existing.amountRial != command.transactionAmountRial() || existing.reason != command.reason ||
            existing.correlationId != command.correlationId.value
        ) conflict()

        val ledger = database.treasuryDao().entriesForTransaction(existing.id)
        val expectedAccounts = when (command) {
            is TreasuryCommand.Receipt -> listOf(command.accountId.value to TreasuryDirection.RECEIPT.name)
            is TreasuryCommand.Payment -> listOf(command.accountId.value to TreasuryDirection.PAYMENT.name)
            is TreasuryCommand.Settlement -> listOf(command.accountId.value to command.direction.name)
            is TreasuryCommand.InternalTransfer -> listOf(
                command.fromAccountId.value to TreasuryDirection.PAYMENT.name,
                command.toAccountId.value to TreasuryDirection.RECEIPT.name,
            )
            is TreasuryCommand.Reconciliation -> {
                val reconciliation = database.treasuryDao().reconciliationByTransactionId(existing.id) ?: conflict()
                if (reconciliation.accountId != command.accountId.value ||
                    reconciliation.expectedRial != command.expected.value || reconciliation.actualRial != command.actual.value
                ) conflict()
                val difference = command.actual.value - command.expected.value
                if (difference == 0L) emptyList() else listOf(
                    command.accountId.value to if (difference > 0) TreasuryDirection.RECEIPT.name else TreasuryDirection.PAYMENT.name,
                )
            }
        }
        val actualAccounts = ledger.map { it.accountId to it.direction }
        if (actualAccounts != expectedAccounts || ledger.any { it.amountRial != command.transactionAmountRial() }) conflict()

        existing.journalEntryId?.let { journalId ->
            val journal = database.accountingDao().entryById(journalId) ?: conflict()
            if (journal.accountingScope != command.accountingScope.name || journal.branchId != command.branchId) conflict()
        } ?: run {
            if (command !is TreasuryCommand.Reconciliation || command.actual != command.expected) conflict()
        }
    }

}

private fun TreasuryCommand.kindName(): String = when (this) {
    is TreasuryCommand.Receipt -> TreasuryTransactionKind.RECEIPT.name
    is TreasuryCommand.Payment -> TreasuryTransactionKind.PAYMENT.name
    is TreasuryCommand.InternalTransfer -> TreasuryTransactionKind.INTERNAL_TRANSFER.name
    is TreasuryCommand.Settlement -> TreasuryTransactionKind.SETTLEMENT.name
    is TreasuryCommand.Reconciliation -> TreasuryTransactionKind.RECONCILIATION.name
}

private fun TreasuryCommand.transactionAmountRial(): Long = when (this) {
    is TreasuryCommand.Receipt -> amount.value
    is TreasuryCommand.Payment -> amount.value
    is TreasuryCommand.InternalTransfer -> amount.value
    is TreasuryCommand.Settlement -> amount.value
    is TreasuryCommand.Reconciliation -> (actual.value - expected.value).absoluteValue
}

private fun TreasuryTransactionEntity.toDomain(idempotentReplay: Boolean) = TreasuryTransaction(
    id = id,
    kind = TreasuryTransactionKind.valueOf(kind),
    businessEpochDay = businessEpochDay,
    correlationId = ir.restaurant.management.core.CorrelationId.parse(correlationId),
    sourceType = sourceType,
    sourceId = sourceId,
    amount = MoneyRial.of(amountRial),
    journalEntryId = journalEntryId,
    idempotentReplay = idempotentReplay,
)
