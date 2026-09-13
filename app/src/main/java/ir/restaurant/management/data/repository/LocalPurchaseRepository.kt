package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.security.UserRole
import ir.restaurant.management.domain.common.DocumentNumberType

import androidx.room.withTransaction
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.PurchaseEntity
import ir.restaurant.management.data.db.PurchaseLineEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalDraft
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PostedPurchaseSettlement
import ir.restaurant.management.domain.purchase.PurchaseCalculator
import ir.restaurant.management.domain.purchase.PurchaseDetails
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseLineRecord
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.purchase.SupplierInvoiceNumber
import ir.restaurant.management.domain.purchase.PurchaseRepository
import ir.restaurant.management.domain.purchase.PurchaseReversalDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementReversalDraft
import ir.restaurant.management.domain.purchase.PurchaseSettlementRecord
import ir.restaurant.management.domain.purchase.SettlementPaymentMethod
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.operations.UnitConversionFactor
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LocalPurchaseRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val syncRecorder: SyncRecorder? = null,
    private val authorizer: SessionAuthorizer,
    private val treasury: TreasuryService = ir.restaurant.management.data.treasury.LocalTreasuryServiceV2(
        database = database,
        accounting = LocalAccountingPostingEngine(database, clock = clock),
        authorizer = authorizer,
        accountCatalog = ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog(),
        clock = clock,
    ),
    private val treasuryReader: TreasuryLedgerReader = treasury as? TreasuryLedgerReader
        ?: error("purchase_treasury_reader_required"),
) : PurchaseRepository {
    private val inventoryCommands = LocalInventoryCommandEngine(database, clock = clock, authorizer = authorizer)
    private val accountingPosting = LocalAccountingPostingEngine(database, clock = clock)
    private val auditWriter = LocalAuditEventWriter(database)
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val branchResolver = CanonicalBranchResolver(database)
    private val dataScope = LocalDataScopeService(database, authorizer)
    private val payables = LocalSupplierPayableService(database, clock)
    override suspend fun post(draft: PurchaseDraft): PostedPurchase {
        val actor = authorizer.require(Permission.PURCHASES)
        val normalizedCommandId = GlobalId.parse(draft.commandId).value
        val now = clock()

        return database.withTransaction {
            database.purchaseDao().byCommandId(normalizedCommandId)?.let { existing ->
                val expectedTotal = MoneyRial.sum(draft.lines.map { it.unitCost.times(it.quantity) }).value
                require(
                    existing.supplierId == draft.supplierId &&
                        existing.purchaseEpochDay == draft.purchaseEpochDay &&
                        existing.branchId == draft.branchId &&
                        existing.locationId == draft.locationId &&
                        existing.totalRial == expectedTotal,
                ) { "purchase_idempotency_conflict" }
                val journal = database.accountingDao().entryBySource("PURCHASE", existing.id)
                return@withTransaction PostedPurchase(existing.id, journal?.id, MoneyRial.of(existing.totalRial), existing.invoiceNo)
            }

            val branchId = requireNotNull(draft.branchId) { "شعبه خرید اضطراری باید صریح انتخاب شود." }
            val locationId = requireNotNull(draft.locationId) { "انبار مقصد خرید اضطراری باید صریح انتخاب شود." }
            val branch = dataScope.requireBranch(branchId)
            dataScope.requireLocation(locationId, branchId)
            val emergencyReason = draft.emergencyReason.trim()
            require(emergencyReason.length in 3..300) {
                "خرید مستقیم فقط به‌عنوان خرید اضطراری و با دلیل ثبت‌شده مجاز است."
            }

            val numberedDraft = if (draft.invoiceNo.isBlank()) {
                draft.copy(invoiceNo = numbering.next(DocumentNumberType.PURCHASE))
            } else draft
            val prepared = PurchaseCalculator.prepare(numberedDraft.copy(branchId = branchId, locationId = locationId, emergencyReason = emergencyReason, commandId = normalizedCommandId))
            val normalizedDraft = prepared.draft
            val preparedLines = prepared.lines
            val total = prepared.total
            val supplier = database.supplierDao().activeById(normalizedDraft.supplierId)
                ?: error("تأمین‌کننده فعال پیدا نشد.")
            val normalizedInvoiceNo = SupplierInvoiceNumber.normalize(normalizedDraft.invoiceNo)
            require(normalizedInvoiceNo.isNotBlank()) { "شماره فاکتور خرید پس از نرمال‌سازی معتبر نیست." }
            require(!database.purchaseDao().supplierInvoiceExists(supplier.id, normalizedInvoiceNo)) {
                "این شماره فاکتور برای تأمین‌کننده انتخاب‌شده قبلاً ثبت شده است."
            }

            val paid = normalizedDraft.paymentMethod != PurchasePaymentMethod.PAYABLE
            val purchaseId = database.purchaseDao().insert(
                PurchaseEntity(
                    invoiceNo = normalizedDraft.invoiceNo,
                    normalizedInvoiceNo = normalizedInvoiceNo,
                    supplierId = supplier.id,
                    purchaseEpochDay = normalizedDraft.purchaseEpochDay,
                    branchName = branch.name,
                    branchId = branch.id,
                    locationId = locationId,
                    commandId = normalizedCommandId,
                    dueEpochDay = normalizedDraft.dueEpochDay,
                    totalRial = total.value,
                    paidRial = if (paid) total.value else 0,
                    paymentStatus = if (paid) PurchasePaymentStatus.PAID.storedValue else PurchasePaymentStatus.UNPAID.storedValue,
                    paymentMethod = normalizedDraft.paymentMethod.storedValue,
                    reminderEnabled = !paid && normalizedDraft.reminderEnabled,
                    reminderEpochDay = if (!paid && normalizedDraft.reminderEnabled) normalizedDraft.reminderEpochDay else null,
                    createdAtEpochMillis = now,
                ),
            )

            val purchaseLines = preparedLines.map { line ->
                val item = database.inventoryDao().activeById(line.itemId)
                    ?: error("یکی از کالاهای خرید پیدا نشد.")
                val stockQuantityMicros = UnitConversionFactor(
                    item.purchaseToStockNumerator, item.purchaseToStockDenominator,
                ).toStockMicros(line.quantityMicros)
                // Emergency/direct purchase is intentionally denied for lot-controlled stock so it
                // cannot bypass the canonical PO -> GR -> lot allocation workflow.
                require(!item.trackLot) {
                    "کالای «${item.name}» لات‌محور است؛ دریافت آن فقط از مسیر سفارش خرید و رسید کالا مجاز است."
                }
                inventoryCommands.receive(
                    itemId = item.id,
                    quantityMicros = stockQuantityMicros,
                    valueRial = line.total.value,
                    movementType = InventoryMovementType.PURCHASE,
                    referenceType = InventoryReferenceType.PURCHASE,
                    referenceId = purchaseId,
                    movementEpochDay = normalizedDraft.purchaseEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.PURCHASE,
                        referenceId = purchaseId,
                        suffix = "emergency_receive:${item.id}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.PURCHASE_RECEIPT,
                        reason = emergencyReason,
                        correlationId = "purchase:$purchaseId",
                        locationId = locationId,
                    ),
                    notes = "${normalizedDraft.invoiceNo} · $emergencyReason",
                    enforceLotPolicy = true,
                )
                PurchaseLineEntity(
                    purchaseId = purchaseId,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    quantityMicros = stockQuantityMicros,
                    unitCostRial = FixedPointRatio.unitCostRial(line.total.value, stockQuantityMicros),
                    lineTotalRial = line.total.value,
                )
            }
            database.purchaseDao().insertLines(purchaseLines)

            val journalId = if (total > MoneyRial.ZERO) {
                accountingPosting.post(
                    draft = SemanticJournalDraft(
                        entryNo = "خ-$purchaseId",
                        description = "خرید اضطراری مواد اولیه از ${supplier.name}",
                        entryEpochDay = normalizedDraft.purchaseEpochDay,
                        sourceType = "PURCHASE",
                        sourceId = purchaseId,
                        accountingScope = AccountingScope.BRANCH,
                        branchId = branch.id,
                        lines = listOf(
                            SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, debit = total),
                            SemanticJournalLine(SemanticAccountRole.SUPPLIER_PAYABLE, credit = total),
                        ),
                    ),
                    context = AccountingPostingContext.local(
                        sourceType = "PURCHASE",
                        sourceId = purchaseId,
                        suffix = "post",
                        actorId = actor.id,
                        correlationId = "purchase:$purchaseId",
                    ),
                )
            } else null

            payables.ensureOrigin(
                sourceType = "PURCHASE",
                sourceId = purchaseId,
                sourceDocumentNo = normalizedDraft.invoiceNo,
                supplierId = supplier.id,
                branchId = branch.id,
                issueEpochDay = normalizedDraft.purchaseEpochDay,
                dueEpochDay = normalizedDraft.dueEpochDay,
                originalRial = total.value,
                actorId = actor.id,
                correlationId = "purchase:$purchaseId",
                originJournalEntryId = journalId,
            )

            if (paid && total > MoneyRial.ZERO) {
                val accountId = TreasuryAccountId.parse(requireNotNull(normalizedDraft.paymentMethod.treasuryAccountId))
                val channel = requireNotNull(normalizedDraft.paymentMethod.treasuryChannel)
                val treasuryCommandId = GlobalId.new()
                val treasuryResult = treasury.execute(
                    TreasuryCommand.Settlement(
                        commandId = treasuryCommandId,
                        businessEpochDay = normalizedDraft.purchaseEpochDay,
                        correlationId = CorrelationId.forCommand("purchase_immediate_settlement", treasuryCommandId),
                        businessIntent = TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT,
                        sourceId = purchaseId,
                        reason = "پرداخت هم‌زمان فاکتور ${normalizedDraft.invoiceNo}",
                        accountingScope = AccountingScope.BRANCH,
                        branchId = branch.id,
                        accountId = accountId,
                        direction = ir.restaurant.management.domain.treasury.TreasuryDirection.PAYMENT,
                        channel = channel,
                        amount = total,
                    ),
                )
                val treasuryJournalId = requireNotNull(treasuryResult.journalEntryId) { "سند خزانه پرداخت خرید ایجاد نشد." }
                payables.settle(
                    sourceType = "PURCHASE",
                    sourceId = purchaseId,
                    amountRial = total.value,
                    businessEpochDay = normalizedDraft.purchaseEpochDay,
                    commandId = GlobalId.new().value,
                    correlationId = treasuryResult.correlationId.value,
                    treasuryTransactionId = treasuryResult.id,
                    journalEntryId = treasuryJournalId,
                    actorId = actor.id,
                    reason = "پرداخت هم‌زمان خرید اضطراری",
                )
            }

            syncRecorder?.record("PURCHASE", purchaseId, "CREATE", now)
            auditWriter.appendAuthorized(
                authorizer, "CREATE", "PURCHASE", purchaseId,
                "ثبت خرید اضطراری ${normalizedDraft.invoiceNo} به مبلغ ${total.value} ریال",
                now, businessEpochDay = normalizedDraft.purchaseEpochDay, reason = emergencyReason,
                afterSnapshot = "supplier=${supplier.id};branch=${branch.id};location=$locationId;total=${total.value}",
                correlationId = "purchase:$purchaseId",
            )
            PostedPurchase(purchaseId, journalId, total, normalizedDraft.invoiceNo)
        }
    }

    private val purchaseReadScope = combine(
        dataScope.scopedBranches(),
        dataScope.scopedLocations(),
        database.securityDao().observeCurrentUser(),
    ) { branches, locations, user ->
        Triple(branches.map { it.id }.toSet(), locations.map { it.id }.toSet(), user?.role?.let(UserRole::fromStoredValue) == UserRole.OWNER)
    }

    override fun details(purchaseId: Long): Flow<PurchaseDetails?> =
        combine(
            database.purchaseDao().observeHeader(purchaseId),
            database.purchaseDao().observeDetailLines(purchaseId),
            database.accountingDao().observePurchaseSettlements(purchaseId),
            purchaseReadScope,
        ) { header, lines, settlements, scope ->
            val visible = header?.let {
                if (scope.third) true
                else it.branchId != null && it.locationId != null && it.branchId in scope.first && it.locationId in scope.second
            } ?: false
            header?.takeIf { visible }?.let {
                PurchaseDetails(
                    id = it.purchaseId,
                    invoiceNo = it.invoiceNo,
                    supplierName = it.supplierName,
                    purchaseEpochDay = it.purchaseEpochDay,
                    dueEpochDay = it.dueEpochDay,
                    totalRial = it.totalRial,
                    paidRial = it.paidRial,
                    paymentStatus = PurchasePaymentStatus.fromStoredValue(it.paymentStatus),
                    paymentMethod = PurchasePaymentMethod.fromStored(it.paymentMethod),
                    reminderEnabled = it.reminderEnabled,
                    reminderEpochDay = it.reminderEpochDay,
                    lines = lines.map { line ->
                        PurchaseLineRecord(
                            itemId = line.itemId,
                            itemName = line.itemName,
                            unit = line.unit,
                            quantityMicros = line.quantityMicros,
                            unitCostRial = line.unitCostRial,
                            lineTotalRial = line.lineTotalRial,
                        )
                    },
                    settlements = settlements.map { settlement ->
                        PurchaseSettlementRecord(
                            journalEntryId = settlement.journalEntryId,
                            entryNo = settlement.entryNo,
                            settlementEpochDay = settlement.settlementEpochDay,
                            amountRial = settlement.amountRial,
                            paymentMethod = SettlementPaymentMethod.fromStoredValue(settlement.paymentMethod),
                            referenceNo = settlement.referenceNo,
                            notes = settlement.notes,
                            isReversed = settlement.isReversed,
                        )
                    },
                )
            }
        }

    override suspend fun settle(draft: PurchaseSettlementDraft): PostedPurchaseSettlement {
        val actor = authorizer.require(Permission.PAYMENT_APPROVE)
        val valid = draft.validated()
        return database.withTransaction {
            val purchase = database.purchaseDao().byId(valid.purchaseId)
                ?: error("فاکتور خرید پیدا نشد.")
            requirePurchaseScope(purchase)
            treasuryReader.reversalContext(valid.commandId)?.let { replay ->
                require(replay.sourceType == TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT.storedValue && replay.sourceId == purchase.id) {
                    "purchase_settlement_idempotency_conflict"
                }
                require(replay.amountRial == valid.amount.value && replay.accountId.value == valid.paymentMethod.treasuryAccountId) {
                    "purchase_settlement_idempotency_conflict"
                }
                val journalId = requireNotNull(replay.journalEntryId) { "سند خزانه تسویه پیدا نشد." }
                val journal = database.accountingDao().entryById(journalId) ?: error("سند حسابداری تسویه پیدا نشد.")
                return@withTransaction PostedPurchaseSettlement(
                    purchaseId = purchase.id,
                    journalEntryId = journal.id,
                    journalEntryNo = journal.entryNo,
                    remaining = MoneyRial.of(purchase.totalRial) - MoneyRial.of(purchase.paidRial),
                )
            }
            require(purchase.paymentMethod == null) { "این فاکتور هنگام خرید پرداخت شده است." }
            require(PurchasePaymentStatus.fromStoredValue(purchase.paymentStatus) in setOf(PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIAL)) {
                "این فاکتور قابل تسویه نیست."
            }
            require(valid.settlementEpochDay >= purchase.purchaseEpochDay) { "تاریخ تسویه نمی‌تواند قبل از تاریخ خرید باشد." }
            val remaining = MoneyRial.of(purchase.totalRial) - MoneyRial.of(purchase.paidRial)
            require(valid.amount <= remaining) { "مبلغ تسویه از مانده فاکتور بیشتر است." }
            val commandId = GlobalId.parse(valid.commandId)
            val treasuryResult = treasury.execute(
                TreasuryCommand.Settlement(
                    commandId = commandId,
                    businessEpochDay = valid.settlementEpochDay,
                    correlationId = CorrelationId.forCommand("purchase_settlement", commandId),
                    businessIntent = TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT,
                    sourceId = purchase.id,
                    reason = valid.notes.ifBlank { valid.referenceNo.ifBlank { "تسویه فاکتور ${purchase.invoiceNo}" } },
                    accountingScope = if (purchase.branchId != null) AccountingScope.BRANCH else AccountingScope.ORGANIZATION,
                    branchId = purchase.branchId,
                    accountId = TreasuryAccountId.parse(valid.paymentMethod.treasuryAccountId),
                    direction = ir.restaurant.management.domain.treasury.TreasuryDirection.PAYMENT,
                    channel = valid.paymentMethod.treasuryChannel,
                    amount = valid.amount,
                ),
            )
            val newPaid = MoneyRial.of(purchase.paidRial) + valid.amount
            val newRemaining = MoneyRial.of(purchase.totalRial) - newPaid
            val paidInFull = newRemaining == MoneyRial.ZERO
            check(
                database.purchaseDao().updateSettlementState(
                    purchaseId = purchase.id,
                    expectedPaidRial = purchase.paidRial,
                    newPaidRial = newPaid.value,
                    paymentStatus = if (paidInFull) PurchasePaymentStatus.PAID.storedValue else PurchasePaymentStatus.PARTIAL.storedValue,
                    reminderEnabled = !paidInFull && purchase.reminderEnabled,
                    reminderEpochDay = if (paidInFull) null else purchase.reminderEpochDay,
                ) == 1,
            ) { "مانده فاکتور هم‌زمان تغییر کرده است؛ دوباره تلاش کنید." }
            val journalId = requireNotNull(treasuryResult.journalEntryId) { "سند خزانه تسویه ایجاد نشد." }
            payables.settle(
                sourceType = "PURCHASE",
                sourceId = purchase.id,
                amountRial = valid.amount.value,
                businessEpochDay = valid.settlementEpochDay,
                commandId = valid.commandId,
                correlationId = treasuryResult.correlationId.value,
                treasuryTransactionId = treasuryResult.id,
                journalEntryId = journalId,
                actorId = actor.id,
                reason = valid.notes.ifBlank { valid.referenceNo.ifBlank { "تسویه فاکتور ${purchase.invoiceNo}" } },
            )
            val journal = database.accountingDao().entryById(journalId) ?: error("سند حسابداری تسویه پیدا نشد.")
            syncRecorder?.record("PURCHASE", purchase.id, "SETTLEMENT", clock())
            audit("SETTLEMENT", purchase.id, "تسویه ${valid.amount.value} ریال؛ سند=${journal.entryNo}؛ مانده=${newRemaining.value}", clock())
            PostedPurchaseSettlement(purchase.id, journal.id, journal.entryNo, newRemaining)
        }
    }


    override suspend fun reverseSettlement(draft: PurchaseSettlementReversalDraft) {
        val actor = authorizer.require(Permission.PAYMENT_APPROVE)
        authorizer.require(Permission.JOURNAL_REVERSE)
        val valid = draft.validated()
        database.withTransaction {
            val purchase = database.purchaseDao().byId(valid.purchaseId) ?: error("فاکتور خرید پیدا نشد.")
            requirePurchaseScope(purchase)
            treasuryReader.reversalContext(valid.commandId)?.let { replay ->
                require(replay.reversalOfTransactionId != null && replay.sourceId == purchase.id) { "purchase_settlement_reversal_idempotency_conflict" }
                return@withTransaction
            }
            require(purchase.paymentMethod == null) { "برگشت تسویه فقط برای خرید نسیه مجاز است." }
            require(PurchasePaymentStatus.fromStoredValue(purchase.paymentStatus) != PurchasePaymentStatus.REVERSED) { "فاکتور برگشت‌خورده قابل اصلاح تسویه نیست." }
            val settlementEntry = database.accountingDao().entryById(valid.settlementJournalEntryId) ?: error("سند تسویه پیدا نشد.")
            require(settlementEntry.sourceType == TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT.storedValue && settlementEntry.sourceId == purchase.id) {
                "سند انتخاب‌شده متعلق به تسویه خزانه این فاکتور نیست."
            }
            require(valid.reversalEpochDay >= settlementEntry.entryEpochDay) { "تاریخ برگشت تسویه نمی‌تواند قبل از تاریخ خود تسویه باشد." }
            val context = treasuryReader.reversalContextByJournalEntryId(settlementEntry.id) ?: error("تراکنش خزانه تسویه پیدا نشد.")
            require(context.sourceId == purchase.id && context.amountRial in 1..purchase.paidRial) { "مبلغ/منشأ تسویه خزانه معتبر نیست." }
            val reversalCommandId = GlobalId.parse(valid.commandId)
            val treasuryReversal = treasury.reverse(
                TreasuryReversalCommand(
                    commandId = reversalCommandId,
                    originalTransactionId = context.transactionId,
                    originalJournalEntryId = settlementEntry.id,
                    businessEpochDay = valid.reversalEpochDay,
                    correlationId = CorrelationId.forCommand("purchase_settlement_reversal", reversalCommandId),
                    sourceType = "PURCHASE_SETTLEMENT_REVERSAL",
                    sourceId = purchase.id,
                    reason = valid.reason,
                    accountId = context.accountId,
                    channel = context.channel,
                    amount = MoneyRial.of(context.amountRial),
                ),
            )
            payables.reverseSettlement(
                sourceType = "PURCHASE",
                sourceId = purchase.id,
                amountRial = context.amountRial,
                businessEpochDay = valid.reversalEpochDay,
                commandId = valid.commandId,
                correlationId = treasuryReversal.correlationId.value,
                treasuryTransactionId = treasuryReversal.id,
                journalEntryId = requireNotNull(treasuryReversal.journalEntryId),
                actorId = actor.id,
                reason = valid.reason,
            )
            val newPaid = MoneyRial.of(purchase.paidRial) - MoneyRial.of(context.amountRial)
            val newStatus = if (newPaid == MoneyRial.ZERO) PurchasePaymentStatus.UNPAID.storedValue else PurchasePaymentStatus.PARTIAL.storedValue
            check(
                database.purchaseDao().updateSettlementState(
                    purchaseId = purchase.id,
                    expectedPaidRial = purchase.paidRial,
                    newPaidRial = newPaid.value,
                    paymentStatus = newStatus,
                    reminderEnabled = purchase.reminderEnabled,
                    reminderEpochDay = purchase.reminderEpochDay,
                ) == 1,
            ) { "مانده فاکتور هم‌زمان تغییر کرده است؛ دوباره تلاش کنید." }
            val now = clock()
            syncRecorder?.record("PURCHASE", purchase.id, "SETTLEMENT_REVERSAL", now)
            audit("REVERSE_SETTLEMENT", purchase.id, "برگشت تسویه ${settlementEntry.entryNo} به مبلغ ${context.amountRial} ریال؛ دلیل=${valid.reason}", now)
        }
    }


    override suspend fun reverse(draft: PurchaseReversalDraft) {
        authorizer.require(Permission.PURCHASES)
        val actor = authorizer.require(Permission.JOURNAL_REVERSE)
        val valid = draft.validated()
        database.withTransaction {
            val purchase = database.purchaseDao().byId(valid.purchaseId)
                ?: error("فاکتور خرید پیدا نشد.")
            requirePurchaseScope(purchase)
            require(PurchasePaymentStatus.fromStoredValue(purchase.paymentStatus) != PurchasePaymentStatus.REVERSED) {
                "این فاکتور قبلاً برگشت خورده است."
            }
            require(!database.purchaseDao().isProcurementInvoice(purchase.id)) {
                "فاکتور تطبیق‌شده با سفارش خرید باید از مسیر برگشت کالا و اصلاح تدارکات برگشت بخورد."
            }
            require(valid.reversalEpochDay >= purchase.purchaseEpochDay) {
                "تاریخ برگشت نمی‌تواند قبل از تاریخ خرید باشد."
            }
            require(purchase.paidRial == 0L || purchase.paymentMethod != null) {
                "فاکتور نسیه‌ای که تسویه دارد ابتدا باید از مسیر اصلاح تسویه بررسی شود."
            }
            require(!database.accountingDao().hasPurchaseReversal(purchase.id)) {
                "برای این فاکتور قبلاً سند برگشت ثبت شده است."
            }
            val originalJournal = if (purchase.totalRial > 0) {
                database.accountingDao().entryBySource("PURCHASE", purchase.id)
                    ?: error("سند حسابداری خرید پیدا نشد.")
            } else null
            val originalJournalLines = originalJournal?.let { originalEntry ->
                database.accountingDao().linesByEntry(originalEntry.id).also { lines ->
                    require(lines.size >= 2) { "آرتیکل‌های سند خرید کامل نیستند." }
                }
            }.orEmpty()
            val purchaseLines = database.purchaseDao().linesByPurchase(purchase.id)
            require(purchaseLines.isNotEmpty()) { "ردیف‌های فاکتور خرید پیدا نشدند." }
            val now = clock()

            purchaseLines.forEach { line ->
                val item = database.inventoryDao().byId(line.itemId)
                    ?: error("کالای «${line.itemNameSnapshot}» پیدا نشد.")
                inventoryCommands.issue(
                    itemId = item.id,
                    quantityMicros = line.quantityMicros,
                    valueRial = line.lineTotalRial,
                    movementType = InventoryMovementType.PURCHASE_REVERSAL,
                    referenceType = InventoryReferenceType.PURCHASE,
                    referenceId = purchase.id,
                    movementEpochDay = valid.reversalEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.PURCHASE,
                        referenceId = purchase.id,
                        suffix = "reverse:${line.itemId}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.PURCHASE_REVERSAL,
                        reason = valid.reason,
                        correlationId = "purchase_reversal:${purchase.id}",
                        locationId = requireNotNull(purchase.locationId) { "انبار خرید برای برگشت مشخص نیست." },
                    ),
                    notes = valid.reason,
                    lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALLOCATED_ONLY,
                )
            }

            if (purchase.paymentMethod != null && purchase.paidRial > 0L) {
                val treasuryContexts = treasuryReader.activeReversalContextsBySource(TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT.storedValue, purchase.id)
                require(treasuryContexts.isNotEmpty()) { "تراکنش خزانه پرداخت هم‌زمان خرید پیدا نشد." }
                treasuryContexts.forEach { context ->
                    val originalTreasuryJournalId = requireNotNull(context.journalEntryId) { "سند خزانه خرید پیدا نشد." }
                    val reversalCommandId = GlobalId.new()
                    val treasuryReversal = treasury.reverse(
                        TreasuryReversalCommand(
                            commandId = reversalCommandId,
                            originalTransactionId = context.transactionId,
                            originalJournalEntryId = originalTreasuryJournalId,
                            businessEpochDay = valid.reversalEpochDay,
                            correlationId = CorrelationId.forCommand("purchase_immediate_reversal", reversalCommandId),
                            sourceType = "PURCHASE_SETTLEMENT_REVERSAL",
                            sourceId = purchase.id,
                            reason = valid.reason,
                            accountId = context.accountId,
                            channel = context.channel,
                            amount = MoneyRial.of(context.amountRial),
                        ),
                    )
                    payables.reverseSettlement(
                        sourceType = "PURCHASE",
                        sourceId = purchase.id,
                        amountRial = context.amountRial,
                        businessEpochDay = valid.reversalEpochDay,
                        commandId = reversalCommandId.value,
                        correlationId = treasuryReversal.correlationId.value,
                        treasuryTransactionId = treasuryReversal.id,
                        journalEntryId = requireNotNull(treasuryReversal.journalEntryId),
                        actorId = actor.id,
                        reason = valid.reason,
                    )
                }
            }

            if (originalJournalLines.isNotEmpty()) {
                accountingPosting.postBalanced(
                    draft = BalancedJournalDraft(
                        entryEpochDay = valid.reversalEpochDay,
                        description = "برگشت فاکتور ${purchase.invoiceNo}: ${valid.reason}",
                        sourceType = "PURCHASE_REVERSAL",
                        sourceId = purchase.id,
                        accountingScope = AccountingScope.fromStoredValue(requireNotNull(originalJournal).accountingScope),
                        branchId = requireNotNull(originalJournal).branchId,
                        lines = originalJournalLines.map { line ->
                            JournalLineDraft(
                                accountCode = line.accountCode,
                                debit = MoneyRial.of(line.creditRial),
                                credit = MoneyRial.of(line.debitRial),
                                memo = valid.reason,
                            )
                        },
                    ),
                    context = AccountingPostingContext.local(
                        sourceType = "PURCHASE_REVERSAL",
                        sourceId = purchase.id,
                        suffix = "reverse:${purchase.id}",
                        actorId = actor.id,
                        correlationId = "purchase_reversal:${purchase.id}",
                        reversalOfEntryId = requireNotNull(originalJournal).id,
                    ),
                    entryNoFactory = { id -> "بخ-$id" },
                )
            }
            payables.voidOrigin(
                sourceType = "PURCHASE",
                sourceId = purchase.id,
                businessEpochDay = valid.reversalEpochDay,
                commandId = GlobalId.new().value,
                correlationId = "purchase_reversal:${purchase.id}",
                journalEntryId = originalJournal?.id,
                actorId = actor.id,
                reason = valid.reason,
            )
            check(database.purchaseDao().markReversed(purchase.id) == 1) {
                "وضعیت فاکتور تغییر نکرد."
            }
            syncRecorder?.record("PURCHASE", purchase.id, "REVERSAL", now)
            audit("REVERSE", purchase.id, "برگشت فاکتور ${purchase.invoiceNo}؛ مبلغ=${purchase.totalRial}؛ دلیل=${valid.reason}", now)
        }
    }

    private suspend fun requirePurchaseScope(purchase: PurchaseEntity) {
        val branchId = purchase.branchId
        val locationId = purchase.locationId
        if (branchId == null || locationId == null) {
            authorizer.requireOwner()
            return
        }
        dataScope.requireLocation(locationId, branchId)
    }

    private suspend fun audit(action: String, entityId: Long, description: String, now: Long) {
        auditWriter.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = "PURCHASE",
            entityId = entityId,
            description = description,
            occurredAtEpochMillis = now,
            correlationId = "purchase:$entityId:$action:$now",
        )
    }
}
