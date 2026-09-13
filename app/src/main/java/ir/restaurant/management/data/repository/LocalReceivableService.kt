package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.ReceivableCollectionEntity
import ir.restaurant.management.data.db.ReceivableEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.receivables.DailySalesReceivableOriginDraft
import ir.restaurant.management.domain.receivables.ReceivableAging
import ir.restaurant.management.domain.receivables.ReceivableCollectionDraft
import ir.restaurant.management.domain.receivables.ReceivableCollectionReversalDraft
import ir.restaurant.management.domain.receivables.ReceivableRecord
import ir.restaurant.management.domain.receivables.ReceivableService
import ir.restaurant.management.domain.receivables.ReceivableStatus
import ir.restaurant.management.domain.receivables.ReceivableType
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalReceivableService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val treasury: TreasuryService = LocalTreasuryServiceV2(
        database = database,
        accounting = LocalAccountingPostingEngine(database, clock = clock),
        authorizer = authorizer,
        accountCatalog = DefaultTreasuryAccountCatalog(),
        clock = clock,
    ),
    private val treasuryReader: TreasuryLedgerReader = treasury as? TreasuryLedgerReader
        ?: error("receivable_treasury_reader_required"),
) : ReceivableService {
    /** Legacy-only reversal adapter for collections created before canonical Treasury ownership. */
    private val legacyAccounting = LocalAccountingPostingEngine(database, clock = clock)
    private val audit = LocalAuditEventWriter(database)
    private val branchResolver = CanonicalBranchResolver(database)

    override fun observeOpen(branchId: Long): Flow<List<ReceivableRecord>> =
        database.businessOperationsDao().observeOpenReceivables(branchId).map { rows ->
            rows.map { row ->
                ReceivableRecord(row.id,row.globalId,row.branchId,row.partyId,ReceivableType.valueOf(row.type),row.sourceType,row.sourceId,row.originalAmountRial,row.paidAmountRial,row.outstandingAmountRial,row.issueEpochDay,row.dueEpochDay,ReceivableStatus.valueOf(row.status))
            }
        }

    override suspend fun createFromDailySales(draft: DailySalesReceivableOriginDraft): Long {
        val actor = authorizer.require(Permission.DAILY_SALES_POST)
        val valid = draft.validated()
        return database.withTransaction {
            val dao = database.businessOperationsDao()
            dao.receivableByGlobalId(valid.commandId)?.let { existing ->
                require(existing.branchId == valid.branchId && existing.partyId == valid.partyId) { "receivable_origin_idempotency_conflict" }
                require(existing.type == valid.type.name && existing.sourceType == "DAILY_SALES" && existing.sourceId == valid.dailySalesId) { "receivable_origin_idempotency_conflict" }
                require(existing.originalAmountRial == valid.amountRial && existing.issueEpochDay == valid.issueEpochDay && existing.dueEpochDay == valid.dueEpochDay) { "receivable_origin_idempotency_conflict" }
                return@withTransaction existing.id
            }
            branchResolver.requireExisting(valid.branchId)
            val party = database.salesDao().activeCustomerById(valid.partyId) ?: error("طرف‌حساب اعتباری فعال نیست.")
            require(if (valid.type == ReceivableType.CORPORATE) party.partyType == "COMPANY" else party.partyType == "PERSON") {
                "نوع طرف‌حساب با نوع دریافتنی سازگار نیست."
            }
            val now = clock()
            val receivableId = dao.insertReceivable(
                ReceivableEntity(
                    globalId = valid.commandId,
                    branchId = valid.branchId,
                    partyId = valid.partyId,
                    type = valid.type.name,
                    sourceType = "DAILY_SALES",
                    sourceId = valid.dailySalesId,
                    originalAmountRial = valid.amountRial,
                    outstandingAmountRial = valid.amountRial,
                    issueEpochDay = valid.issueEpochDay,
                    dueEpochDay = valid.dueEpochDay,
                    status = ReceivableStatus.OPEN.name,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            val ledgerReference = receivableLedgerReference(receivableId)
            database.customerReceivableDao().insertLedger(
                CustomerReceivableLedgerEntity(
                    customerId = valid.partyId,
                    businessEpochDay = valid.issueEpochDay,
                    entryType = "CREDIT_SALE",
                    debitRial = valid.amountRial,
                    creditRial = 0,
                    sourceType = "RECEIVABLE",
                    sourceId = receivableId,
                    reference = ledgerReference,
                    dueEpochDay = valid.dueEpochDay,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            check(database.customerReceivableDao().balanceByReference(valid.partyId, ledgerReference) == valid.amountRial) {
                "ثبت دفتر دریافتنی فروش اعتباری ناقص است."
            }
            audit.appendAuthorized(
                authorizer, "CREATE", "RECEIVABLE", receivableId,
                "ایجاد دریافتنی از فروش روزانه ${valid.dailySalesId}", now, valid.issueEpochDay,
                correlationId = "daily_sales_receivable:${valid.commandId}", referenceType = "DAILY_SALES", referenceId = valid.dailySalesId,
            )
            receivableId
        }
    }

    override suspend fun voidFromDailySales(dailySalesId: Long, reversalEpochDay: Long, reason: String) {
        val actor = authorizer.require(Permission.DAILY_SALES_VOID)
        require(dailySalesId > 0 && reversalEpochDay > 0) { "منشأ فروش روزانه معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..300) { "دلیل ابطال دریافتنی الزامی است." }
        database.withTransaction {
            val dao = database.businessOperationsDao()
            val rows = dao.receivablesBySource("DAILY_SALES", dailySalesId)
            val now = clock()
            rows.forEach { receivable ->
                if (receivable.status == ReceivableStatus.VOIDED.name) return@forEach
                require(dao.activeCollectionCount(receivable.id) == 0) { "دریافتنی فروش دارای وصول فعال است." }
                require(receivable.paidAmountRial == 0L) { "دریافتنی فروش پرداخت‌شده بدون برگشت وصول قابل ابطال نیست." }
                val ledgerReference = receivableLedgerReference(receivable.id)
                database.customerReceivableDao().insertLedger(
                    CustomerReceivableLedgerEntity(
                        customerId = receivable.partyId,
                        businessEpochDay = reversalEpochDay,
                        entryType = "SALE_REVERSAL",
                        debitRial = 0,
                        creditRial = receivable.outstandingAmountRial,
                        sourceType = "RECEIVABLE_REVERSAL",
                        sourceId = receivable.id,
                        reference = ledgerReference,
                        dueEpochDay = receivable.dueEpochDay,
                        actorId = actor.id,
                        createdAtEpochMillis = now,
                    ),
                )
                check(dao.voidReceivable(receivable.id, receivable.paidAmountRial, receivable.outstandingAmountRial, now) == 1) {
                    "دریافتنی فروش هم‌زمان تغییر کرده است."
                }
                check(database.customerReceivableDao().balanceByReference(receivable.partyId, ledgerReference) == 0L) {
                    "مانده دریافتنی بعد از برگشت فروش صفر نشد."
                }
                audit.appendAuthorized(
                    authorizer, "VOID_FROM_DAILY_SALES", "RECEIVABLE", receivable.id,
                    "ابطال دریافتنی ناشی از برگشت فروش $dailySalesId", now, reversalEpochDay,
                    reason = normalizedReason, correlationId = "daily_sales_receivable_reversal:$dailySalesId",
                    referenceType = "DAILY_SALES", referenceId = dailySalesId,
                )
            }
        }
    }

    override suspend fun collect(draft: ReceivableCollectionDraft): Long {
        val actor = authorizer.require(Permission.RECEIVABLE_COLLECT)
        return database.withTransaction {
            val dao = database.businessOperationsDao()
            val current = dao.receivable(draft.receivableId) ?: error("مطالبه پیدا نشد.")
            branchResolver.requireExisting(current.branchId)
            val normalized = draft.normalized()
            dao.collectionByGlobalId(normalized.commandId)?.let { existing ->
                verifyCollectionReplay(existing, normalized)
                return@withTransaction existing.id
            }
            require(current.status in setOf("OPEN", "PARTIALLY_PAID")) { "این مطالبه قابل وصول نیست." }
            val valid = normalized.validated(current.outstandingAmountRial)
            val treasuryResult = treasury.execute(
                TreasuryCommand.Receipt(
                    commandId = GlobalId.parse(valid.commandId),
                    businessEpochDay = valid.businessEpochDay,
                    correlationId = CorrelationId.forCommand("receivable_collection", GlobalId.parse(valid.commandId)),
                    businessIntent = if (current.type == ReceivableType.CORPORATE.name) TreasuryBusinessIntent.CORPORATE_RECEIVABLE_COLLECTION else TreasuryBusinessIntent.CUSTOMER_RECEIVABLE_COLLECTION,
                    sourceId = current.id,
                    reason = valid.reference ?: "وصول دریافتنی ${current.id}",
                    accountingScope = AccountingScope.BRANCH,
                    branchId = current.branchId,
                    accountId = TreasuryAccountId.parse(valid.treasuryAccountId),
                    channel = valid.method.toTreasuryChannel(),
                    amount = MoneyRial.of(valid.amountRial),
                ),
            )
            require(treasuryResult.journalEntryId != null) { "سند حسابداری وصول ایجاد نشد." }
            val now = clock()
            val collectionId = dao.insertCollection(
                ReceivableCollectionEntity(
                    globalId = valid.commandId,
                    receivableId = current.id,
                    amountRial = valid.amountRial,
                    method = valid.method.name,
                    reference = valid.reference,
                    businessEpochDay = valid.businessEpochDay,
                    createdByUserId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            val ledgerReference = receivableLedgerReference(current.id)
            database.customerReceivableDao().insertLedger(
                CustomerReceivableLedgerEntity(
                    customerId = current.partyId,
                    businessEpochDay = valid.businessEpochDay,
                    entryType = "COLLECTION",
                    debitRial = 0,
                    creditRial = valid.amountRial,
                    sourceType = "RECEIVABLE_COLLECTION",
                    sourceId = collectionId,
                    reference = ledgerReference,
                    dueEpochDay = current.dueEpochDay,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            val newPaid = SignedLongMath.add(current.paidAmountRial, valid.amountRial)
            val outstanding = SignedLongMath.subtract(current.originalAmountRial, newPaid)
            val status = if (outstanding == 0L) "PAID" else "PARTIALLY_PAID"
            check(dao.updateReceivableBalance(current.id, current.paidAmountRial, current.outstandingAmountRial, newPaid, outstanding, status, now) == 1)
            check(database.customerReceivableDao().balanceByReference(current.partyId, ledgerReference) == outstanding) {
                "مانده سند دریافتنی با دفتر دریافتنی یکسان نیست."
            }
            audit.appendAuthorized(
                authorizer,"COLLECT","RECEIVABLE",current.id,
                "وصول ${valid.amountRial} ریال؛ مانده=$outstanding",now,valid.businessEpochDay,
                correlationId="receivable_collection:${valid.commandId}", referenceType="TREASURY_TRANSACTION", referenceId=current.id,
            )
            collectionId
        }
    }

    override suspend fun reverseCollection(draft: ReceivableCollectionReversalDraft) {
        val actor = authorizer.require(Permission.RECEIVABLE_ADJUST)
        val valid = draft.validated()
        val now = clock()
        database.withTransaction {
            val dao = database.businessOperationsDao()
            val collection = dao.collection(valid.collectionId) ?: error("وصول مطالبات پیدا نشد.")
            if (collection.reversedAtEpochMillis != null) return@withTransaction
            val receivable = dao.receivable(collection.receivableId) ?: error("مطالبه مرتبط پیدا نشد.")
            val treasuryContext = treasuryReader.reversalContext(collection.globalId)
            val reversalJournalId = if (treasuryContext != null) {
                val reversalCommandId = GlobalId.new()
                val reversal = treasury.reverse(
                    TreasuryReversalCommand(
                        commandId = reversalCommandId,
                        originalTransactionId = treasuryContext.transactionId,
                        originalJournalEntryId = requireNotNull(treasuryContext.journalEntryId) { "سند حسابداری وصول خزانه پیدا نشد." },
                        businessEpochDay = valid.reversalEpochDay,
                        correlationId = CorrelationId.forCommand("receivable_collection_reversal", reversalCommandId),
                        sourceType = "RECEIVABLE_COLLECTION_REVERSAL",
                        sourceId = receivable.id,
                        reason = valid.reason,
                        accountId = treasuryContext.accountId,
                        channel = treasuryContext.channel,
                        amount = MoneyRial.of(collection.amountRial),
                    ),
                )
                requireNotNull(reversal.journalEntryId)
            } else {
                reverseLegacyCollectionAccounting(collection.id, valid, actor.id)
            }

            val ledgerReference = receivableLedgerReference(receivable.id)
            database.customerReceivableDao().insertLedger(
                CustomerReceivableLedgerEntity(
                    customerId = receivable.partyId,
                    businessEpochDay = valid.reversalEpochDay,
                    entryType = "COLLECTION_REVERSAL",
                    debitRial = collection.amountRial,
                    creditRial = 0,
                    sourceType = "RECEIVABLE_COLLECTION_REVERSAL",
                    sourceId = collection.id,
                    reference = ledgerReference,
                    dueEpochDay = receivable.dueEpochDay,
                    actorId = actor.id,
                    createdAtEpochMillis = now,
                ),
            )
            val newPaid = SignedLongMath.subtract(receivable.paidAmountRial, collection.amountRial)
            require(newPaid >= 0) { "مانده وصول سند دریافتنی نامعتبر شد." }
            val outstanding = SignedLongMath.subtract(receivable.originalAmountRial, newPaid)
            val status = if (newPaid == 0L) "OPEN" else "PARTIALLY_PAID"
            check(dao.updateReceivableBalance(receivable.id, receivable.paidAmountRial, receivable.outstandingAmountRial, newPaid, outstanding, status, now) == 1)
            check(database.customerReceivableDao().balanceByReference(receivable.partyId, ledgerReference) == outstanding) {
                "مانده سند دریافتنی با دفتر دریافتنی یکسان نیست."
            }
            check(dao.markCollectionReversed(collection.id, now, valid.reason, reversalJournalId) == 1)
            audit.appendAuthorized(
                authorizer,"REVERSE_COLLECTION","RECEIVABLE",receivable.id,
                "برگشت وصول ${collection.amountRial} ریال؛ مانده=$outstanding",now,valid.reversalEpochDay,
                reason=valid.reason,correlationId="receivable_collection_reversal:${collection.id}",
            )
        }
    }

    override suspend fun aging(branchId: Long, todayEpochDay: Long): ReceivableAging {
        authorizer.require(Permission.RECEIVABLE_VIEW)
        require(branchId > 0 && todayEpochDay > 0)
        branchResolver.requireExisting(branchId)
        val lots = CanonicalReceivableReadModel(database).openLotsForBranch(branchId)
        var current=0L; var d1=0L; var d8=0L; var d31=0L; var d61=0L; var d90=0L
        lots.forEach { lot ->
            val days = todayEpochDay - lot.dueEpochDay
            when {
                days <= 0 -> current = SignedLongMath.add(current, lot.outstandingRial)
                days <= 7 -> d1 = SignedLongMath.add(d1, lot.outstandingRial)
                days <= 30 -> d8 = SignedLongMath.add(d8, lot.outstandingRial)
                days <= 60 -> d31 = SignedLongMath.add(d31, lot.outstandingRial)
                days <= 90 -> d61 = SignedLongMath.add(d61, lot.outstandingRial)
                else -> d90 = SignedLongMath.add(d90, lot.outstandingRial)
            }
        }
        return ReceivableAging(current,d1,d8,d31,d61,d90)
    }

    private suspend fun verifyCollectionReplay(existing: ReceivableCollectionEntity, valid: ReceivableCollectionDraft) {
        require(existing.receivableId == valid.receivableId && existing.amountRial == valid.amountRial) { "receivable_collection_idempotency_conflict" }
        require(existing.method == valid.method.name && existing.reference == valid.reference && existing.businessEpochDay == valid.businessEpochDay) { "receivable_collection_idempotency_conflict" }
        val treasuryContext = treasuryReader.reversalContext(existing.globalId) ?: error("receivable_collection_treasury_integrity_missing")
        require(treasuryContext.sourceId == existing.receivableId && treasuryContext.amountRial == existing.amountRial) { "receivable_collection_treasury_integrity_mismatch" }
        require(treasuryContext.accountId.value == valid.treasuryAccountId) { "receivable_collection_idempotency_conflict" }
    }

    private suspend fun reverseLegacyCollectionAccounting(
        collectionId: Long,
        valid: ReceivableCollectionReversalDraft,
        actorId: Long,
    ): Long {
        val originalJournal = database.accountingDao().entryBySource("RECEIVABLE_COLLECTION", collectionId)
            ?: error("سند حسابداری وصول تاریخی پیدا نشد.")
        val originalLines = database.accountingDao().linesByEntry(originalJournal.id)
        require(originalLines.size >= 2) { "آرتیکل‌های سند وصول تاریخی کامل نیستند." }
        val reversal = legacyAccounting.postBalanced(
            BalancedJournalDraft(
                entryEpochDay = valid.reversalEpochDay,
                description = "برگشت وصول تاریخی: ${valid.reason}",
                sourceType = "RECEIVABLE_COLLECTION_REVERSAL",
                sourceId = collectionId,
                accountingScope = AccountingScope.fromStoredValue(originalJournal.accountingScope),
                branchId = originalJournal.branchId,
                lines = originalLines.map { line ->
                    JournalLineDraft(
                        accountCode = line.accountCode,
                        debit = MoneyRial.of(line.creditRial),
                        credit = MoneyRial.of(line.debitRial),
                        memo = valid.reason,
                    )
                },
            ),
            AccountingPostingContext.local(
                sourceType = "RECEIVABLE_COLLECTION_REVERSAL",
                sourceId = collectionId,
                suffix = "legacy-reverse:${originalJournal.id}",
                actorId = actorId,
                correlationId = "receivable_collection_legacy_reversal:$collectionId",
                reversalOfEntryId = originalJournal.id,
            ),
            entryNoFactory = { id -> "RCVR-$id" },
        )
        return reversal.entryId
    }

    private fun receivableLedgerReference(receivableId: Long): String = "RECEIVABLE:$receivableId"

    private fun ir.restaurant.management.domain.receivables.ReceivableCollectionMethod.toTreasuryChannel(): TreasuryChannel = when (this) {
        ir.restaurant.management.domain.receivables.ReceivableCollectionMethod.CASH -> TreasuryChannel.CASH
        ir.restaurant.management.domain.receivables.ReceivableCollectionMethod.CARD -> TreasuryChannel.CARD
        ir.restaurant.management.domain.receivables.ReceivableCollectionMethod.BANK_TRANSFER -> TreasuryChannel.BANK
    }
}
