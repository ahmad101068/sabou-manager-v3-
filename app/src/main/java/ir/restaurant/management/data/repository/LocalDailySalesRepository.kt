package ir.restaurant.management.data.repository

import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.domain.security.Permission

import androidx.room.withTransaction
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.DailySalesMenuLineEntity
import ir.restaurant.management.data.db.DailySalesSummaryEntity
import ir.restaurant.management.data.db.DailySalesSettlementEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.SalesDayClosureEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.recipe.MenuEngineeringCalculator
import ir.restaurant.management.domain.recipe.FullCostCalculator
import ir.restaurant.management.domain.recipe.MenuPerformanceInput
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalDraft
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.accounting.AccountingPostingContext
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.BalancedJournalDraft
import ir.restaurant.management.domain.accounting.JournalLineDraft
import ir.restaurant.management.domain.receivables.DailySalesReceivableOriginDraft
import ir.restaurant.management.domain.receivables.ReceivableService
import ir.restaurant.management.domain.receivables.ReceivableType
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryReversalCommand
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.sales.DailySalesDraft
import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.DailySalesStatus
import ir.restaurant.management.domain.sales.DailySalesLifecycle
import ir.restaurant.management.domain.sales.SalesSettlementType
import ir.restaurant.management.domain.sales.DailySalesItem
import ir.restaurant.management.domain.sales.DailySalesReport
import ir.restaurant.management.domain.sales.DailySalesRepository
import ir.restaurant.management.domain.sales.MenuProfitabilityResult
import ir.restaurant.management.domain.sales.DailySalesProfitabilityLine
import ir.restaurant.management.domain.sales.DailySalesReversalDraft
import ir.restaurant.management.domain.sales.SalesDayClosureDraft
import ir.restaurant.management.domain.sales.SalesDayReopenDraft
import ir.restaurant.management.domain.sales.SalesDayClosureRecord
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.WeightedAverageInventoryValuationService
import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.asViolation
import java.math.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LocalDailySalesRepository(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
    private val syncRecorder: SyncRecorder? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sensitiveActionGate: SensitiveActionGate = SensitiveActionGate(),
    private val treasury: TreasuryService = ir.restaurant.management.data.treasury.LocalTreasuryServiceV2(
        database = database,
        accounting = LocalAccountingPostingEngine(database, clock = clock),
        authorizer = authorizer,
        accountCatalog = ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog(),
        clock = clock,
    ),
    private val receivables: ReceivableService = LocalReceivableService(
        database = database,
        authorizer = authorizer,
        clock = clock,
        treasury = treasury,
    ),
    private val treasuryReader: TreasuryLedgerReader = treasury as? TreasuryLedgerReader
        ?: error("daily_sales_treasury_reader_required"),
) : DailySalesRepository {
    private val accountingPosting = LocalAccountingPostingEngine(database, clock = clock)
    private val inventoryCommands = LocalInventoryCommandEngine(database, clock = clock, authorizer = authorizer)
    private val auditWriter = LocalAuditEventWriter(database)
    private val branchResolver = CanonicalBranchResolver(database)
    private val dataScope = LocalDataScopeService(database, authorizer)
    override val dayClosures: Flow<List<SalesDayClosureRecord>> = combine(
        database.dailySalesDao().observeDayClosures(),
        database.businessOperationsDao().observeAllSettlements(),
    ) { rows, settlementRows ->
        val settlementsBySale = settlementRows.groupBy { it.dailySalesId }.mapValues { (_, values) -> values.toSettlementSnapshot() }
        rows.map { row ->
            val settlement = settlementsBySale[row.summaryId] ?: SettlementSnapshot.ZERO
            SalesDayClosureRecord(row.businessEpochDay, row.summaryId, row.grossSalesRial, row.netSalesRial, row.theoreticalCostRial, settlement.cashRial, settlement.cardRial, settlement.transferRial, row.status, row.revisionNo, row.closedBy, row.note, row.reopenedBy, row.reopenReason, row.createdAtEpochMillis)
        }
    }

    private data class ConsumptionNeed(
        val item: InventoryItemEntity,
        val lineIndex: Int,
        val quantity: Long,
    )
    private data class PreparedConsumption(
        val item: InventoryItemEntity,
        val quantity: Long,
        val cost: Long,
        val locationId: Long,
    )
    private data class PreparedLineSeed(
        val menuId: Long,
        val recipeVersionId: Long,
        val name: String,
        val quantity: Long,
        val gross: Long?,
        val packagingCost: Long,
        val directLaborCost: Long,
        val allocatedOverhead: Long,
        val recipeYieldMicros: Long,
        val preparationWasteBasisPoints: Int,
        val cookingWasteBasisPoints: Int,
    )
    private data class PreparedLine(
        val menuId: Long,
        val recipeVersionId: Long,
        val name: String,
        val quantity: Long,
        val gross: Long?,
        val cost: Long,
        val foodCost: Long,
        val packagingCost: Long,
        val directLaborCost: Long,
        val allocatedOverhead: Long,
    )
    private data class PreparedDailySale(
        val gross: Long,
        val netSales: Long,
        val revenue: Long,
        val amountToSettle: Long,
        val settlements: List<DailySalesSettlementDraft>,
        val cashSnapshot: Long,
        val cardSnapshot: Long,
        val transferSnapshot: Long,
        val lines: List<PreparedLine>,
        val consumptions: List<PreparedConsumption>,
        val totalCost: Long,
        val sourceLocationId: Long,
    )

    override suspend fun post(draft: DailySalesDraft): Long {
        val id = createDraft(draft)
        confirm(id)
        postConfirmed(id)
        return id
    }

    override suspend fun createDraft(draft: DailySalesDraft): Long {
        val actor = authorizer.require(Permission.DAILY_SALES_CREATE)
        val now = clock()
        return database.withTransaction {
            branchResolver.requireActive(draft.branchId)
            val prepared = prepare(draft)
            database.dailySalesDao().activeSummaryByDay(draft.branchId, draft.businessEpochDay)?.let { existing ->
                throw BusinessError.IdempotencyConflict("DAILY_SALES:${draft.branchId}:${draft.businessEpochDay}:${existing.id}").asViolation()
            }
            require(!database.dailySalesDao().dayClosed(draft.branchId, draft.businessEpochDay)) { "این روز فروش در شعبه انتخاب‌شده بسته و امضاشده است و قابل ثبت مجدد نیست." }
            val summaryId = database.dailySalesDao().insertSummary(
                DailySalesSummaryEntity(
                    globalId = GlobalId.new().value,
                    branchId = draft.branchId,
                    locationId = draft.locationId,
                    businessEpochDay = draft.businessEpochDay,
                    grossSalesRial = prepared.gross,
                    discountRial = draft.discountRial,
                    returnRial = draft.returnRial,
                    serviceRial = draft.serviceRial,
                    taxRial = draft.taxRial,
                    netSalesRial = prepared.netSales,
                    theoreticalCostRial = prepared.totalCost,
                    cashRial = prepared.cashSnapshot,
                    cardRial = prepared.cardSnapshot,
                    transferRial = prepared.transferSnapshot,
                    notes = draft.notes.trim(),
                    journalEntryId = null,
                    costJournalEntryId = null,
                    createdAtEpochMillis = now,
                    createdByUserId = actor.id,
                    status = DailySalesStatus.DRAFT.name,
                    updatedByUserId = actor.id,
                    updatedAtEpochMillis = now,
                ),
            )
            persistDraftDetails(summaryId, prepared, now)
            syncRecorder?.record("DAILY_SALES", summaryId, "DRAFT_CREATE", now)
            audit("CREATE_DRAFT", summaryId, "ایجاد پیش‌نویس فروش روز ${draft.businessEpochDay}؛ خالص=${prepared.netSales}؛ درآمد=${prepared.revenue}؛ قابل‌تسویه=${prepared.amountToSettle}", now, businessEpochDay=draft.businessEpochDay)
            summaryId
        }
    }

    override suspend fun updateDraft(summaryId: Long, draft: DailySalesDraft) {
        val actor = authorizer.require(Permission.DAILY_SALES_EDIT)
        val now = clock()
        database.withTransaction {
            branchResolver.requireActive(draft.branchId)
            val current = database.dailySalesDao().summary(summaryId) ?: error("فروش روزانه پیدا نشد.")
            DailySalesLifecycle.requireDirectEdit(DailySalesStatus.valueOf(current.status))
            require(current.branchId == draft.branchId) { "شعبه پیش‌نویس قابل تغییر نیست." }
            val prepared = prepare(draft)
            check(database.dailySalesDao().updateDraftSummary(
                summaryId, draft.businessEpochDay, draft.locationId, prepared.gross, draft.discountRial, draft.returnRial,
                draft.serviceRial, draft.taxRial, prepared.netSales, prepared.totalCost,
                prepared.cashSnapshot, prepared.cardSnapshot, prepared.transferSnapshot,
                draft.notes.trim(), actor.id, now,
            ) == 1) { "ویرایش پیش‌نویس انجام نشد." }
            database.dailySalesDao().deleteLines(summaryId)
            database.businessOperationsDao().deleteSettlements(summaryId)
            persistDraftDetails(summaryId, prepared, now)
            audit("EDIT_DRAFT", summaryId, "ویرایش پیش‌نویس فروش روز ${draft.businessEpochDay}", now, businessEpochDay=draft.businessEpochDay)
        }
    }

    override suspend fun confirm(summaryId: Long) {
        val actor = authorizer.require(Permission.DAILY_SALES_CONFIRM)
        val now = clock()
        database.withTransaction {
            val summary = database.dailySalesDao().summary(summaryId) ?: error("فروش روزانه پیدا نشد.")
            branchResolver.requireActive(summary.branchId)
            val from = DailySalesStatus.valueOf(summary.status)
            if (from == DailySalesStatus.CONFIRMED) return@withTransaction
            DailySalesLifecycle.requireTransition(from, DailySalesStatus.CONFIRMED)
            validatePersistedCreditAndSettlement(summary)
            check(database.dailySalesDao().transitionStatus(summary.id, DailySalesStatus.DRAFT.name, DailySalesStatus.CONFIRMED.name, actor.id, now) == 1) {
                "تأیید فروش روزانه انجام نشد."
            }
            audit("CONFIRM", summary.id, "تأیید فروش روز ${summary.businessEpochDay}", now, businessEpochDay=summary.businessEpochDay)
        }
    }

    override suspend fun postConfirmed(summaryId: Long) {
        val actor = authorizer.require(Permission.DAILY_SALES_POST)
        val now = clock()
        database.withTransaction {
            val summary = database.dailySalesDao().summary(summaryId) ?: error("فروش روزانه پیدا نشد.")
            branchResolver.requireActive(summary.branchId)
            val status = DailySalesStatus.valueOf(summary.status)
            if (status == DailySalesStatus.POSTED) return@withTransaction
            DailySalesLifecycle.requireTransition(status, DailySalesStatus.POSTED)
            require(status == DailySalesStatus.CONFIRMED) { "فقط فروش CONFIRMED قابل ثبت نهایی است." }
            validatePersistedCreditAndSettlement(summary)
            val persistedSettlements = database.businessOperationsDao().settlements(summary.id)
            val persistedLines = database.dailySalesDao().lines(summary.id)
            val reconstructed = DailySalesDraft(
                businessEpochDay = summary.businessEpochDay,
                discountRial = summary.discountRial,
                serviceRial = summary.serviceRial,
                taxRial = summary.taxRial,
                cashRial = summary.cashRial,
                cardRial = summary.cardRial,
                transferRial = summary.transferRial,
                notes = summary.notes,
                lines = persistedLines.map { line ->
                    DailyMenuSaleDraft(requireNotNull(line.menuItemId) { "ردیف فروش منو معتبر نیست." }, line.quantityMicros, line.grossSalesRial)
                },
                branchId = summary.branchId,
                locationId = requireNotNull(summary.locationId) { "برای این پیش‌نویس فروش، مکان مصرف موجودی ثبت نشده است." },
                returnRial = summary.returnRial,
                settlements = persistedSettlements.map(::settlementDraft),
                grossSalesRial = summary.grossSalesRial,
            )
            val prepared = prepare(reconstructed)
            // CONFIRMED is a financial snapshot. Posting may validate the current executable
            // consumption plan, but it must never rewrite confirmed recipe/cost history.
            require(prepared.totalCost == summary.theoreticalCostRial) {
                "daily_sales_confirmed_cost_snapshot_changed"
            }
            require(prepared.lines.size == persistedLines.size) { "daily_sales_confirmed_line_snapshot_changed" }
            val persistedByMenu = persistedLines.associateBy { requireNotNull(it.menuItemId) }
            prepared.lines.forEach { line ->
                val frozen = persistedByMenu[line.menuId] ?: error("daily_sales_confirmed_line_snapshot_changed")
                require(
                    frozen.recipeVersionId == line.recipeVersionId &&
                        frozen.quantityMicros == line.quantity &&
                        frozen.grossSalesRial == line.gross &&
                        frozen.theoreticalCostRial == line.cost
                ) { "daily_sales_confirmed_line_snapshot_changed" }
            }

            prepared.consumptions.forEach { consumption ->
                val item = consumption.item
                inventoryCommands.issue(
                    itemId = item.id,
                    quantityMicros = consumption.quantity,
                    valueRial = consumption.cost,
                    movementType = InventoryMovementType.DAILY_SALES_CONSUMPTION,
                    referenceType = InventoryReferenceType.DAILY_SALES,
                    referenceId = summary.id,
                    movementEpochDay = summary.businessEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.DAILY_SALES,
                        referenceId = summary.id,
                        suffix = "consume:${item.id}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.SALES_CONSUMPTION,
                        reason = "مصرف رسپی فروش روزانه",
                        correlationId = "daily_sales:${summary.id}",
                        locationId = consumption.locationId,
                    ),
                    notes = "مصرف رسپی فروش روزانه",
                    lotPolicy = LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALL,
                )
            }

            persistedSettlements.filter { it.amountRial > 0 }.forEach { row ->
                when (SalesSettlementType.valueOf(row.type)) {
                    SalesSettlementType.CASH, SalesSettlementType.CARD, SalesSettlementType.BANK_TRANSFER -> {
                        val (accountId, channel) = when (SalesSettlementType.valueOf(row.type)) {
                            SalesSettlementType.CASH -> TreasuryAccountId.parse("cash_main") to TreasuryChannel.CASH
                            SalesSettlementType.CARD -> TreasuryAccountId.parse("card_terminal") to TreasuryChannel.CARD
                            SalesSettlementType.BANK_TRANSFER -> TreasuryAccountId.parse("bank_main") to TreasuryChannel.BANK
                            else -> error("unsupported_daily_sales_liquidity_settlement")
                        }
                        val settlementCommandId = GlobalId.parse(row.globalId)
                        val treasuryResult = treasury.execute(
                            TreasuryCommand.Receipt(
                                commandId = settlementCommandId,
                                businessEpochDay = summary.businessEpochDay,
                                correlationId = ir.restaurant.management.core.CorrelationId.forCommand("daily_sales_settlement", settlementCommandId),
                                businessIntent = TreasuryBusinessIntent.DAILY_SALES_SETTLEMENT,
                                sourceId = summary.id,
                                reason = row.note?.takeIf { it.isNotBlank() } ?: row.referenceNumber?.takeIf { it.isNotBlank() } ?: "تسویه فروش روزانه ${summary.id}",
                                accountingScope = AccountingScope.BRANCH,
                                branchId = summary.branchId,
                                accountId = accountId,
                                channel = channel,
                                amount = MoneyRial.of(row.amountRial),
                            ),
                        )
                        require(treasuryResult.journalEntryId != null) { "سند خزانه تسویه فروش روزانه ایجاد نشد." }
                    }
                    SalesSettlementType.PERSONAL_CREDIT, SalesSettlementType.CORPORATE_CREDIT -> {
                        val partyId = requireNotNull(row.partyId) { "فروش اعتباری نیازمند طرف‌حساب است." }
                        val party = database.salesDao().activeCustomerById(partyId) ?: error("طرف‌حساب اعتباری فعال نیست.")
                        val type = if (row.type == SalesSettlementType.CORPORATE_CREDIT.name) ReceivableType.CORPORATE else ReceivableType.PERSONAL
                        val due = row.dueEpochDay ?: party.paymentTermsDays.takeIf { it > 0 }?.let { summary.businessEpochDay + it }
                        receivables.createFromDailySales(
                            DailySalesReceivableOriginDraft(
                                commandId = row.globalId,
                                branchId = summary.branchId,
                                partyId = partyId,
                                type = type,
                                dailySalesId = summary.id,
                                amountRial = row.amountRial,
                                issueEpochDay = summary.businessEpochDay,
                                dueEpochDay = due,
                            ),
                        )
                    }
                }
            }

            val revenueLines = buildList {
                val liquiditySettlementRial = persistedSettlements
                    .filter { it.type in setOf(SalesSettlementType.CASH.name, SalesSettlementType.CARD.name, SalesSettlementType.BANK_TRANSFER.name) }
                    .fold(0L) { total, settlement -> SignedLongMath.add(total, settlement.amountRial) }
                if (liquiditySettlementRial > 0) {
                    add(SemanticJournalLine(SemanticAccountRole.TREASURY_CLEARING, debit = MoneyRial.of(liquiditySettlementRial), memo = "تسویه خزانه فروش روزانه"))
                }
                persistedSettlements.forEach { settlement ->
                    if (settlement.amountRial <= 0) return@forEach
                    when (SalesSettlementType.valueOf(settlement.type)) {
                        SalesSettlementType.CASH, SalesSettlementType.CARD, SalesSettlementType.BANK_TRANSFER -> Unit
                        SalesSettlementType.PERSONAL_CREDIT -> add(SemanticJournalLine(SemanticAccountRole.PERSONAL_RECEIVABLE, debit = MoneyRial.of(settlement.amountRial)))
                        SalesSettlementType.CORPORATE_CREDIT -> add(SemanticJournalLine(SemanticAccountRole.CORPORATE_RECEIVABLE, debit = MoneyRial.of(settlement.amountRial)))
                    }
                }
                if (summary.grossSalesRial > 0) add(SemanticJournalLine(SemanticAccountRole.SALES_REVENUE, credit = MoneyRial.of(summary.grossSalesRial), memo = "فروش ناخالص"))
                if (summary.discountRial > 0) add(SemanticJournalLine(SemanticAccountRole.SALES_REVENUE, debit = MoneyRial.of(summary.discountRial), memo = "تخفیف فروش"))
                if (summary.returnRial > 0) add(SemanticJournalLine(SemanticAccountRole.SALES_REVENUE, debit = MoneyRial.of(summary.returnRial), memo = "برگشت فروش"))
                if (summary.serviceRial > 0) add(SemanticJournalLine(SemanticAccountRole.SERVICE_REVENUE, credit = MoneyRial.of(summary.serviceRial)))
                if (summary.taxRial > 0) add(SemanticJournalLine(SemanticAccountRole.TAX_PAYABLE, credit = MoneyRial.of(summary.taxRial)))
            }
            val journalId = accountingPosting.post(
                SemanticJournalDraft(
                    entryNo="فر-${summary.id}", description="فروش تجمیعی صندوق", entryEpochDay=summary.businessEpochDay,
                    sourceType="DAILY_SALES", sourceId=summary.id, lines=revenueLines,
                    accountingScope=AccountingScope.BRANCH, branchId=summary.branchId,
                ),
                AccountingPostingContext.local("DAILY_SALES", summary.id, "revenue", actor.id, correlationId="daily_sales:${summary.id}"),
            )
            val costJournalId = if (summary.theoreticalCostRial > 0) accountingPosting.post(
                SemanticJournalDraft(
                    entryNo="بفر-${summary.id}", description="بهای تمام‌شده فروش تجمیعی", entryEpochDay=summary.businessEpochDay,
                    sourceType="DAILY_SALES_COGS", sourceId=summary.id, lines=listOf(
                        SemanticJournalLine(SemanticAccountRole.COGS, debit=MoneyRial.of(summary.theoreticalCostRial)),
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, credit=MoneyRial.of(summary.theoreticalCostRial)),
                    ),
                    accountingScope=AccountingScope.BRANCH, branchId=summary.branchId,
                ),
                AccountingPostingContext.local("DAILY_SALES_COGS", summary.id, "cogs", actor.id, correlationId="daily_sales:${summary.id}"),
            ) else null
            check(database.dailySalesDao().linkJournals(summary.id, journalId, costJournalId) == 1)
            check(database.dailySalesDao().transitionStatus(summary.id, DailySalesStatus.CONFIRMED.name, DailySalesStatus.POSTED.name, actor.id, now) == 1) {
                "ثبت نهایی وضعیت فروش انجام نشد."
            }
            syncRecorder?.record("DAILY_SALES", summary.id, "POST", now)
            audit("POST", summary.id, "ثبت نهایی فروش روز ${summary.businessEpochDay}؛ خالص=${summary.netSalesRial}؛ سند=$journalId", now, businessEpochDay=summary.businessEpochDay)
        }
    }

    private suspend fun prepare(draft: DailySalesDraft): PreparedDailySale {
        require(draft.businessEpochDay > 0 && draft.branchId > 0 && draft.locationId > 0) { "تاریخ، شعبه و مکان مصرف فروش معتبر نیست." }
        dataScope.requireLocation(draft.locationId, draft.branchId)
        require(draft.lines.isNotEmpty()) { "حداقل یک آیتم از منو وارد کنید." }
        require(draft.lines.all { it.menuItemId > 0 && it.quantityMicros > 0 && (it.grossSalesRial == null || it.grossSalesRial >= 0) }) { "اطلاعات ردیف‌های فروش ناقص است." }
        require(draft.lines.map { it.menuItemId }.distinct().size == draft.lines.size) { "هر آیتم منو باید فقط یک‌بار در فروش روزانه ثبت شود." }
        require(listOfNotNull(draft.grossSalesRial, draft.discountRial, draft.returnRial, draft.serviceRial, draft.taxRial, draft.cashRial, draft.cardRial, draft.transferRial).all { it >= 0 }) { "مبالغ نمی‌توانند منفی باشند." }
        val knownLineGross = draft.lines.mapNotNull { it.grossSalesRial }.fold(0L, SignedLongMath::add)
        val gross = draft.grossSalesRial ?: run {
            require(draft.lines.all { it.grossSalesRial != null }) { "وقتی مبلغ بعضی ردیف‌های منو نامشخص است، فروش ناخالص Header باید وارد شود." }
            knownLineGross
        }
        require(gross > 0) { "فروش ناخالص باید بیشتر از صفر باشد." }
        if (draft.lines.all { it.grossSalesRial != null }) require(knownLineGross == gross) { "جمع مبلغ ردیف‌های منو با فروش ناخالص Header برابر نیست." }
        else require(knownLineGross <= gross) { "جمع مبلغ ردیف‌های معلوم از فروش ناخالص بیشتر است." }
        require(draft.discountRial <= gross) { "تخفیف نمی‌تواند از فروش ناخالص بیشتر باشد." }
        val afterDiscount = SignedLongMath.subtract(gross, draft.discountRial)
        require(draft.returnRial <= afterDiscount) { "برگشت فروش از مبلغ قابل برگشت بیشتر است." }
        val netSales = SignedLongMath.subtract(afterDiscount, draft.returnRial)
        val revenue = SignedLongMath.add(netSales, draft.serviceRial)
        val amountToSettle = SignedLongMath.add(revenue, draft.taxRial)
        val effectiveSettlements = if (draft.settlements.isNotEmpty()) draft.settlements.map(DailySalesSettlementDraft::validated) else buildList {
            if (draft.cashRial > 0) add(DailySalesSettlementDraft(SalesSettlementType.CASH, draft.cashRial))
            if (draft.cardRial > 0) add(DailySalesSettlementDraft(SalesSettlementType.CARD, draft.cardRial))
            if (draft.transferRial > 0) add(DailySalesSettlementDraft(SalesSettlementType.BANK_TRANSFER, draft.transferRial))
        }
        val settlementTotal = effectiveSettlements.fold(0L) { total, row -> SignedLongMath.add(total,row.amountRial) }
        require(settlementTotal == amountToSettle) { "جمع همه روش‌های تسویه باید دقیقاً با مبلغ قابل تسویه (درآمد + مالیات) برابر باشد." }
        val cashSnapshot = effectiveSettlements.filter { it.type == SalesSettlementType.CASH }.fold(0L) { a,b -> SignedLongMath.add(a,b.amountRial) }
        val cardSnapshot = effectiveSettlements.filter { it.type == SalesSettlementType.CARD }.fold(0L) { a,b -> SignedLongMath.add(a,b.amountRial) }
        val transferSnapshot = effectiveSettlements.filter { it.type == SalesSettlementType.BANK_TRANSFER }.fold(0L) { a,b -> SignedLongMath.add(a,b.amountRial) }

        // Inventory ownership is explicit: no hidden default warehouse is permitted.
        val sourceLocationId = draft.locationId

        val consumptionNeeds = mutableListOf<ConsumptionNeed>()
        val lineSeeds = draft.lines.mapIndexed { lineIndex, line ->
            val menu = database.recipeDao().activeMenuItem(line.menuItemId) ?: error("آیتم منوی انتخاب‌شده فعال نیست.")
            val recipeVersion = database.recipeDao().effectiveVersion(menu.id, draft.businessEpochDay) ?: error("برای «${menu.name}» در تاریخ فروش، نسخه مؤثر رسپی وجود ندارد.")
            val resolvedMaterials = RecipeMaterialResolver(database).resolve(
                recipeVersionId = recipeVersion.id,
                businessEpochDay = draft.businessEpochDay,
                outputQuantityMicros = line.quantityMicros,
            )
            resolvedMaterials.forEach { material ->
                val item = database.inventoryDao().activeById(material.inventoryItemId)
                    ?: error("یکی از مواد اولیه یا جایگزین «${menu.name}» فعال نیست.")
                require(material.quantityMicros > 0) { "مصرف محاسبه‌شده برای ${item.name} نامعتبر است." }
                consumptionNeeds += ConsumptionNeed(item, lineIndex, material.quantityMicros)
            }
            PreparedLineSeed(
                menuId = menu.id,
                recipeVersionId = recipeVersion.id,
                name = menu.name,
                quantity = line.quantityMicros,
                gross = line.grossSalesRial,
                packagingCost = mulDiv(recipeVersion.packagingCostRial, line.quantityMicros, 1_000_000L),
                directLaborCost = mulDiv(recipeVersion.directLaborCostRial, line.quantityMicros, 1_000_000L),
                allocatedOverhead = mulDiv(recipeVersion.allocatedOverheadRial, line.quantityMicros, 1_000_000L),
                recipeYieldMicros = recipeVersion.yieldMicros,
                preparationWasteBasisPoints = recipeVersion.preparationWasteBasisPoints,
                cookingWasteBasisPoints = recipeVersion.cookingWasteBasisPoints,
            )
        }

        val lineIngredientCosts = LongArray(lineSeeds.size)
        val preparedConsumptions = consumptionNeeds
            .groupBy { it.item.id }
            .toSortedMap()
            .map { (_, rows) ->
                val item = rows.first().item
                val quantity = rows.fold(0L) { total, row -> SignedLongMath.add(total, row.quantity) }
                val balance = database.inventoryBalanceDao().byKey(item.id, sourceLocationId)
                val hasAnyLocationBalance = database.inventoryBalanceDao().countForItem(item.id) > 0
                val balanceQuantity = balance?.onHandMicros ?: if (hasAnyLocationBalance) 0L else item.stockMicros
                val balanceValue = balance?.inventoryValueRial ?: if (hasAnyLocationBalance) 0L else item.inventoryValueRial
                val available = if (balance == null) {
                    balanceQuantity
                } else {
                    SignedLongMath.subtract(
                        SignedLongMath.subtract(
                            SignedLongMath.subtract(balance.onHandMicros, balance.reservedMicros),
                            balance.damagedMicros,
                        ),
                        balance.quarantinedMicros,
                    ).coerceAtLeast(0L)
                }
                require(available >= quantity) {
                    "موجودی ${item.name} در محل مصرف فروش روزانه کافی نیست."
                }
                val cost = WeightedAverageInventoryValuationService.issueValue(
                    balanceQuantityMicros = balanceQuantity,
                    balanceValueRial = balanceValue,
                    issueQuantityMicros = quantity,
                )

                // Allocate only for line-level reporting. Ledger/COGS keeps the exact item-level value.
                // The final row receives the deterministic rounding remainder so line sum == ledger value.
                val orderedRows = rows.sortedBy { it.lineIndex }
                var allocatedCost = 0L
                orderedRows.forEachIndexed { index, row ->
                    val share = if (index == orderedRows.lastIndex) {
                        SignedLongMath.subtract(cost, allocatedCost)
                    } else {
                        mulDiv(cost, row.quantity, quantity)
                    }
                    lineIngredientCosts[row.lineIndex] = SignedLongMath.add(lineIngredientCosts[row.lineIndex], share)
                    allocatedCost = SignedLongMath.add(allocatedCost, share)
                }
                check(allocatedCost == cost) { "تخصیص بهای مواد فروش روزانه با بهای دفتر موجودی تطابق ندارد." }
                PreparedConsumption(item, quantity, cost, sourceLocationId)
            }

        val preparedLines = lineSeeds.mapIndexed { index, seed ->
            val lineCost = lineIngredientCosts[index]
            val fullCost = FullCostCalculator.calculate(
                FullCostCalculator.Input(
                    lineCost,
                    seed.recipeYieldMicros,
                    seed.preparationWasteBasisPoints,
                    seed.cookingWasteBasisPoints,
                    seed.packagingCost,
                    seed.directLaborCost,
                    seed.allocatedOverhead,
                    seed.gross ?: 0L,
                ),
            )
            PreparedLine(
                seed.menuId,
                seed.recipeVersionId,
                seed.name,
                seed.quantity,
                seed.gross,
                lineCost,
                fullCost.foodCostRial,
                seed.packagingCost,
                seed.directLaborCost,
                seed.allocatedOverhead,
            )
        }
        val totalCost = preparedConsumptions.fold(0L) { total, row -> SignedLongMath.add(total, row.cost) }
        check(preparedLines.fold(0L) { total, line -> SignedLongMath.add(total, line.cost) } == totalCost) {
            "جمع بهای ردیف‌های فروش روزانه با بهای خروج موجودی تطابق ندارد."
        }
        return PreparedDailySale(
            gross,
            netSales,
            revenue,
            amountToSettle,
            effectiveSettlements,
            cashSnapshot,
            cardSnapshot,
            transferSnapshot,
            preparedLines,
            preparedConsumptions,
            totalCost,
            sourceLocationId,
        )
    }

    private suspend fun persistDraftDetails(summaryId: Long, prepared: PreparedDailySale, now: Long) {
        database.dailySalesDao().insertLines(prepared.lines.map {
            DailySalesMenuLineEntity(
                summaryId=summaryId,menuItemId=it.menuId,recipeVersionId=it.recipeVersionId,menuItemNameSnapshot=it.name,
                quantityMicros=it.quantity,grossSalesRial=it.gross,theoreticalCostRial=it.cost,foodCostSnapshotRial=it.foodCost,
                packagingCostSnapshotRial=it.packagingCost,directLaborCostSnapshotRial=it.directLaborCost,allocatedOverheadSnapshotRial=it.allocatedOverhead,
            )
        })
        database.businessOperationsDao().insertSettlements(prepared.settlements.map { settlement ->
            DailySalesSettlementEntity(
                globalId=GlobalId.new().value,dailySalesId=summaryId,type=settlement.type.name,amountRial=settlement.amountRial,
                cashboxId=settlement.cashboxId,bankAccountId=settlement.bankAccountId,cardTerminalId=settlement.cardTerminalId,partyId=settlement.partyId,
                dueEpochDay=settlement.dueEpochDay,contractId=settlement.contractId,referenceNumber=settlement.referenceNumber,note=settlement.note,
                createdAtEpochMillis=now,updatedAtEpochMillis=now,
            )
        })
    }

    private suspend fun validatePersistedCreditAndSettlement(summary: DailySalesSummaryEntity) {
        val settlements = database.businessOperationsDao().settlements(summary.id)
        val amountToSettle = SignedLongMath.add(SignedLongMath.add(summary.netSalesRial, summary.serviceRial), summary.taxRial)
        require(settlements.isNotEmpty() || amountToSettle == 0L) { "روش تسویه فروش وارد نشده است." }
        val total = settlements.fold(0L) { acc, row -> SignedLongMath.add(acc, row.amountRial) }
        require(total == amountToSettle) { "جمع همه روش‌های تسویه با مبلغ قابل تسویه (Net Sales + Service + Tax) برابر نیست." }
        val creditRows = settlements.filter {
            it.type == SalesSettlementType.PERSONAL_CREDIT.name || it.type == SalesSettlementType.CORPORATE_CREDIT.name
        }
        creditRows.forEach { row ->
            val partyId = requireNotNull(row.partyId) { "فروش اعتباری نیازمند طرف‌حساب است." }
            val party = database.salesDao().activeCustomerById(partyId) ?: error("طرف‌حساب اعتباری فعال نیست.")
            val corporate = row.type == SalesSettlementType.CORPORATE_CREDIT.name
            require(if (corporate) party.partyType == "COMPANY" else party.partyType == "PERSON") { "نوع طرف‌حساب با نوع تسویه اعتباری سازگار نیست." }
            val due = row.dueEpochDay ?: party.paymentTermsDays.takeIf { it > 0 }?.let { summary.businessEpochDay + it }
            require(due == null || due >= summary.businessEpochDay) { "سررسید فروش اعتباری نمی‌تواند قبل از تاریخ فروش باشد." }
            if (corporate) authorizer.require(Permission.CORPORATE_SALES_MANAGE)
        }
        creditRows.groupBy { requireNotNull(it.partyId) }.forEach { (partyId, rows) ->
            val party = database.salesDao().activeCustomerById(partyId) ?: error("طرف‌حساب اعتباری فعال نیست.")
            val pendingCredit = rows.fold(0L) { acc, row -> SignedLongMath.add(acc, row.amountRial) }
            val canonicalOutstanding = CanonicalReceivableReadModel(database).openLotsForParty(partyId)
                .fold(0L) { total, lot -> SignedLongMath.add(total, lot.outstandingRial) }
            if (party.creditLimitRial > 0 && SignedLongMath.add(canonicalOutstanding, pendingCredit) > party.creditLimitRial) {
                authorizer.require(Permission.CREDIT_OVERRIDE)
            }
        }
    }

    private fun settlementDraft(row: DailySalesSettlementEntity)=DailySalesSettlementDraft(
        type=SalesSettlementType.valueOf(row.type),amountRial=row.amountRial,cashboxId=row.cashboxId,bankAccountId=row.bankAccountId,
        cardTerminalId=row.cardTerminalId,partyId=row.partyId,dueEpochDay=row.dueEpochDay,contractId=row.contractId,
        referenceNumber=row.referenceNumber,note=row.note,
    )


    override suspend fun reverse(draft: DailySalesReversalDraft) {
        val actor = authorizer.require(Permission.DAILY_SALES_VOID)
        database.withTransaction {
            val summary = database.dailySalesDao().summary(draft.summaryId)
                ?: error("فروش روزانه پیدا نشد.")
            val valid = draft.validated(summary.businessEpochDay)
            require(!summary.isLegacyArchive) {
                "فروش آرشیوی نسخه‌های قبل از این مسیر قابل برگشت نیست."
            }
            if (summary.reversedAtEpochDay != null) {
                if (
                    summary.reversedAtEpochDay != valid.reversalEpochDay ||
                    summary.reversalReason != valid.reason
                ) {
                    throw BusinessError.IdempotencyConflict(
                        "DAILY_SALES_REVERSAL:${summary.id}",
                    ).asViolation()
                }
                return@withTransaction
            }
            require(summary.status == DailySalesStatus.POSTED.name) { "فقط فروش POSTED قابل برگشت است." }
            require(!database.dailySalesDao().dayClosed(summary.branchId, summary.businessEpochDay)) { "این روز فروش در شعبه انتخاب‌شده بسته و امضاشده است و قابل برگشت نیست." }
            val receivables = database.businessOperationsDao().receivablesBySource("DAILY_SALES", summary.id)
            val collectionCount = receivables.sumOf { database.businessOperationsDao().activeCollectionCount(it.id) }
            require(collectionCount == 0) { "این فروش دارای وصول مطالبات است. ابتدا وصول‌های مرتبط را برگشت دهید." }
            val movements = database.stockMovementDao().dailySalesConsumptions(summary.id)
            require(movements.isNotEmpty()) { "گردش موجودی فروش روزانه کامل نیست." }
            val now = clock()

            movements.forEach { movement ->
                require(movement.quantityDeltaMicros < 0 && movement.valueDeltaRial <= 0) {
                    "گردش مصرف فروش روزانه نامعتبر است."
                }
                inventoryCommands.restoreIssuedMovement(
                    movement = movement,
                    reversalMovementType = InventoryMovementType.DAILY_SALES_REVERSAL,
                    reversalEpochDay = valid.reversalEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.DAILY_SALES,
                        referenceId = summary.id,
                        suffix = "reverse:${movement.id}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.SALES_REVERSAL,
                        reason = valid.reason,
                        correlationId = "daily_sales_reversal:${summary.id}",
                    ),
                    notes = "برگشت فروش روزانه: ${valid.reason}",
                )
            }

            val settlementRows = database.businessOperationsDao().settlements(summary.id)
            settlementRows.filter { it.amountRial > 0 }.forEach { settlement ->
                when (SalesSettlementType.valueOf(settlement.type)) {
                    SalesSettlementType.CASH, SalesSettlementType.CARD, SalesSettlementType.BANK_TRANSFER -> {
                        val context = treasuryReader.reversalContext(settlement.globalId)
                            ?: error("تراکنش خزانه تسویه فروش روزانه پیدا نشد.")
                        require(context.status == "POSTED") { "تراکنش خزانه فروش قبلاً برگشت خورده است." }
                        val reversalCommandId = GlobalId.new()
                        treasury.reverse(
                            TreasuryReversalCommand(
                                commandId = reversalCommandId,
                                originalTransactionId = context.transactionId,
                                originalJournalEntryId = requireNotNull(context.journalEntryId) { "سند خزانه فروش روزانه پیدا نشد." },
                                businessEpochDay = valid.reversalEpochDay,
                                correlationId = ir.restaurant.management.core.CorrelationId.forCommand("daily_sales_treasury_reversal", reversalCommandId),
                                sourceType = "DAILY_SALES_SETTLEMENT_REVERSAL",
                                sourceId = summary.id,
                                reason = valid.reason,
                                accountId = context.accountId,
                                channel = context.channel,
                                amount = MoneyRial.of(settlement.amountRial),
                            ),
                        )
                    }
                    SalesSettlementType.PERSONAL_CREDIT, SalesSettlementType.CORPORATE_CREDIT -> Unit
                }
            }

            val revenueReversalId = reverseJournal(
                originalEntryId = summary.journalEntryId,
                summaryId = summary.id,
                epochDay = valid.reversalEpochDay,
                reason = valid.reason,
                originalSourceType = "DAILY_SALES",
                sourceType = "DAILY_SALES_REVERSAL",
                entryPrefix = "بفر",
                description = "برگشت فروش تجمیعی صندوق",
                now = now,
                actorId = actor.id,
            ) ?: error("سند درآمد فروش روزانه پیدا نشد.")
            val costReversalId = reverseJournal(
                originalEntryId = summary.costJournalEntryId,
                summaryId = summary.id,
                epochDay = valid.reversalEpochDay,
                reason = valid.reason,
                originalSourceType = "DAILY_SALES_COGS",
                sourceType = "DAILY_SALES_COGS_REVERSAL",
                entryPrefix = "ببفر",
                description = "برگشت بهای تمام‌شده فروش تجمیعی",
                now = now,
                actorId = actor.id,
            )
            if (receivables.isNotEmpty()) {
                this.receivables.voidFromDailySales(summary.id, valid.reversalEpochDay, valid.reason)
            }
            check(
                database.dailySalesDao().markReversed(
                    summaryId = summary.id,
                    reversalEpochDay = valid.reversalEpochDay,
                    reason = valid.reason,
                    reversalJournalEntryId = revenueReversalId,
                    reversalCostJournalEntryId = costReversalId,
                    actorId = actor.id,
                    updatedAt = now,
                ) == 1,
            ) { "وضعیت برگشت فروش روزانه ثبت نشد." }
            syncRecorder?.record("DAILY_SALES", summary.id, "REVERSAL", now)
            audit("REVERSE", summary.id, "برگشت فروش روز ${summary.businessEpochDay}؛ خالص=${summary.netSalesRial}؛ دلیل=${valid.reason}", now)
        }
    }

    override suspend fun closeDay(draft: SalesDayClosureDraft) {
        authorizer.require(Permission.SALES_DAY_CLOSE)
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.CLOSE_SALES_DAY, SensitiveActionContext.resource("SALES_DAY", "${valid.branchId}:${valid.businessEpochDay}", valid.branchId))
        database.withTransaction {
            val dao = database.dailySalesDao()
            require(!dao.dayClosed(valid.branchId, valid.businessEpochDay)) { "این روز در شعبه انتخاب‌شده قبلاً بسته شده است." }
            val summary = dao.activeSummaryByDay(valid.branchId, valid.businessEpochDay) ?: error("برای این روز و شعبه فروش فعال ثبت نشده است.")
            require(!summary.isLegacyArchive) { "روزهای آرشیوی باید از گردش کنترل مهاجرت بررسی شوند." }
            require(summary.status == DailySalesStatus.POSTED.name) { "فقط فروش POSTED قابل بستن روز است." }
            val actor = authorizer.actor()
            val now = clock()
            val previousClosure = dao.dayClosure(valid.branchId, valid.businessEpochDay)
            val settlement = database.businessOperationsDao().settlements(summary.id).toSettlementSnapshot()
            val refreshed = SalesDayClosureEntity(
                businessEpochDay = summary.businessEpochDay, summaryId = summary.id,
                grossSalesRial = summary.grossSalesRial, netSalesRial = summary.netSalesRial,
                theoreticalCostRial = summary.theoreticalCostRial,
                cashRial = settlement.cashRial, cardRial = settlement.cardRial, transferRial = settlement.transferRial,
                status = "CLOSED", revisionNo = (previousClosure?.revisionNo ?: 0) + 1,
                closedBy = actor, note = valid.note, createdAtEpochMillis = now,
            )
            if (previousClosure == null) dao.insertDayClosure(refreshed)
            else check(dao.updateDayClosure(refreshed) == 1) { "بستن مجدد روز فروش انجام نشد." }
            audit(
                action = "CLOSE",
                entityId = summary.id,
                description = "بستن روز فروش ${summary.businessEpochDay} به مبلغ ${summary.netSalesRial} ریال",
                now = now,
                entityType = "SALES_DAY",
                businessEpochDay = summary.businessEpochDay,
                reason = valid.note.ifBlank { "تأیید پایان روز فروش" },
                afterSnapshot = "status=CLOSED;netSalesRial=${summary.netSalesRial};revisionNo=${refreshed.revisionNo}",
            )
            syncRecorder?.record("SALES_DAY", summary.id, "CLOSE", now)
        }
    }

    override suspend fun reopenDay(draft: SalesDayReopenDraft) {
        authorizer.requireOwner()
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.REOPEN_SALES_DAY, SensitiveActionContext.resource("SALES_DAY", "${valid.branchId}:${valid.businessEpochDay}", valid.branchId))
        database.withTransaction {
            val dao = database.dailySalesDao()
            val closure = dao.dayClosure(valid.branchId, valid.businessEpochDay) ?: error("سند بستن روز فروش برای شعبه انتخاب‌شده پیدا نشد.")
            require(closure.status == "CLOSED") { "این روز در وضعیت بسته نیست." }
            val actor = authorizer.actor()
            val now = clock()
            check(dao.updateDayClosure(closure.copy(status = "REOPENED", reopenedBy = actor, reopenReason = valid.reason, reopenedAtEpochMillis = now)) == 1) {
                "بازگشایی روز فروش انجام نشد."
            }
            audit(
                action = "REOPEN",
                entityId = closure.summaryId,
                description = "بازگشایی کنترل‌شده روز فروش ${closure.businessEpochDay}: ${valid.reason}",
                now = now,
                entityType = "SALES_DAY",
                businessEpochDay = closure.businessEpochDay,
                reason = valid.reason,
                beforeSnapshot = "status=CLOSED;revisionNo=${closure.revisionNo}",
                afterSnapshot = "status=REOPENED;reason=${valid.reason}",
            )
            syncRecorder?.record("SALES_DAY", closure.summaryId, "REOPEN", now)
        }
    }

    override fun observe(query: String): Flow<List<DailySalesItem>> = combine(
        database.dailySalesDao().observeSummaries(query.trim()),
        database.dailySalesDao().observeDayClosures(),
        database.dailySalesDao().observeSummaryProfitability(),
        database.dailySalesDao().observeAllLines(),
        database.businessOperationsDao().observeAllSettlements(),
    ) { rows, closures, profitabilityRows, lineRows, settlementRows ->
        val bySummaryClosure = closures.associateBy { it.summaryId }
        val bySummary = profitabilityRows.associateBy { it.summaryId }
        val linesBySummary = lineRows.groupBy { it.summaryId }
        val settlementsBySale = settlementRows.groupBy { it.dailySalesId }
        rows.map { row -> row.toDomain(bySummaryClosure[row.id], bySummary[row.id], linesBySummary[row.id].orEmpty(), settlementsBySale[row.id].orEmpty()) }
    }

    override fun observeReport(fromEpochDay: Long, toEpochDay: Long): Flow<DailySalesReport> = combine(
        database.dailySalesDao().observeRange(fromEpochDay, toEpochDay),
        database.dailySalesDao().observeMenuPerformance(fromEpochDay, toEpochDay),
    ) { summaries, menuRows ->
        val performanceInputs = menuRows.mapNotNull { row ->
            row.menuItemId?.takeIf { id ->
                id > 0 && row.unitsSold > 0 && row.salesAmountLineCount == row.totalLineCount && row.salesRial > 0 && row.costRial >= 0
            }?.let { MenuPerformanceInput(it, row.name, row.unitsSold, row.salesRial, row.costRial) }
        }
        val menuPerformance = if (performanceInputs.isEmpty()) emptyList() else MenuEngineeringCalculator.classify(performanceInputs)
        val profitability = menuRows.mapNotNull { row ->
            val id = row.menuItemId ?: return@mapNotNull null
            val complete = row.totalLineCount > 0 && row.fullCostLineCount == row.totalLineCount
            val salesComplete = row.totalLineCount > 0 && row.salesAmountLineCount == row.totalLineCount
            val salesAmount = row.salesRial.takeIf { salesComplete }
            val fullCost = row.fullCostRial.takeIf { complete }
            MenuProfitabilityResult(
                menuItemId = id,
                name = row.name,
                unitsSold = row.unitsSold,
                salesRial = salesAmount,
                foodCostRial = row.costRial,
                fullCostRial = fullCost,
                foodMarginRial = salesAmount?.let { SignedLongMath.subtract(it, row.costRial) },
                fullMarginRial = salesAmount?.let { sales -> fullCost?.let { SignedLongMath.subtract(sales, it) } },
                fullCostBasisPoints = salesAmount?.takeIf { it > 0 }?.let { sales -> fullCost?.let { mulDiv(it, 10_000, sales).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() } },
                hasCompleteFullCost = complete,
            )
        }
        val sales = summaries.fold(0L) { total, summary ->
            val revenue = SignedLongMath.add(summary.netSalesRial, summary.serviceRial)
            val posted = if (summary.businessEpochDay in fromEpochDay..toEpochDay) revenue else 0
            val reversed = if (summary.reversedAtEpochDay?.let { it in fromEpochDay..toEpochDay } == true) revenue else 0
            SignedLongMath.add(total, SignedLongMath.subtract(posted, reversed))
        }
        val cost = summaries.fold(0L) { total, summary ->
            val posted = if (summary.businessEpochDay in fromEpochDay..toEpochDay) summary.theoreticalCostRial else 0
            val reversed = if (summary.reversedAtEpochDay?.let { it in fromEpochDay..toEpochDay } == true) summary.theoreticalCostRial else 0
            SignedLongMath.add(total, SignedLongMath.subtract(posted, reversed))
        }
        val eventDays = buildSet {
            summaries.forEach { summary ->
                if (summary.businessEpochDay in fromEpochDay..toEpochDay) add(summary.businessEpochDay)
                summary.reversedAtEpochDay?.takeIf { it in fromEpochDay..toEpochDay }?.let { add(it) }
            }
        }
        val coveredLines = menuRows.sumOf { it.fullCostLineCount }
        val totalLines = menuRows.sumOf { it.totalLineCount }
        val fullCost = menuRows.fold(0L) { total, row -> SignedLongMath.add(total, row.fullCostRial) }.takeIf { totalLines > 0 && coveredLines == totalLines }
        DailySalesReport(
            fromEpochDay,
            toEpochDay,
            eventDays.size,
            sales,
            cost,
            SignedLongMath.subtract(sales, cost),
            menuPerformance,
            fullCost,
            fullCost?.let { SignedLongMath.subtract(sales, it) },
            coveredLines,
            totalLines,
            profitability,
        )
    }

    private suspend fun reverseJournal(
        originalEntryId: Long?,
        summaryId: Long,
        epochDay: Long,
        reason: String,
        originalSourceType: String,
        sourceType: String,
        entryPrefix: String,
        description: String,
        now: Long,
        actorId: Long,
    ): Long? {
        if (originalEntryId == null) return null
        val original = database.accountingDao().entryById(originalEntryId)
            ?: error("سند حسابداری مبدأ پیدا نشد.")
        require(original.status == "POSTED") { "سند حسابداری مبدأ ثبت‌شده نیست." }
        require(original.sourceType == originalSourceType && original.sourceId == summaryId) {
            "سند حسابداری مبدأ با فروش روزانه تطابق ندارد."
        }
        val lines = database.accountingDao().linesByEntry(original.id)
        require(lines.size >= 2) { "آرتیکل‌های سند حسابداری مبدأ کامل نیستند." }
        val posted = accountingPosting.postBalanced(
            draft = BalancedJournalDraft(
                entryEpochDay = epochDay,
                description = "$description: $reason",
                sourceType = sourceType,
                sourceId = summaryId,
                accountingScope = AccountingScope.fromStoredValue(original.accountingScope),
                branchId = original.branchId,
                lines = lines.map { line ->
                    JournalLineDraft(
                        accountCode = line.accountCode,
                        debit = MoneyRial.of(line.creditRial),
                        credit = MoneyRial.of(line.debitRial),
                        memo = reason,
                    )
                },
            ),
            context = AccountingPostingContext.local(
                sourceType = sourceType,
                sourceId = summaryId,
                suffix = "reverse:${original.id}",
                actorId = actorId,
                correlationId = "daily_sales_reversal:$summaryId",
                reversalOfEntryId = original.id,
            ),
            entryNoFactory = { id -> "$entryPrefix-$id" },
        )
        return posted.entryId
    }

    private fun DailySalesSummaryEntity.toDomain(
        closure: SalesDayClosureEntity? = null,
        profitability: ir.restaurant.management.data.db.DailySalesProfitabilityRow? = null,
        lines: List<DailySalesMenuLineEntity> = emptyList(),
        settlementRows: List<DailySalesSettlementEntity> = emptyList(),
    ): DailySalesItem {
        val settlement = settlementRows.toSettlementSnapshot()
        val domainStatus = DailySalesStatus.entries.firstOrNull { it.name == status } ?: DailySalesStatus.POSTED
        val completeFullCost = profitability
            ?.takeIf { it.totalLineCount > 0 && it.coveredLineCount == it.totalLineCount }
            ?.fullCostRial
        return DailySalesItem(
            id = id,
            branchId = branchId,
            locationId = locationId,
            businessEpochDay = businessEpochDay,
            grossSalesRial = grossSalesRial,
            discountRial = discountRial,
            returnRial = returnRial,
            serviceRial = serviceRial,
            taxRial = taxRial,
            netSalesRial = netSalesRial,
            theoreticalCostRial = theoreticalCostRial,
            fullCostRial = completeFullCost,
            fullMarginRial = completeFullCost?.let { SignedLongMath.subtract(SignedLongMath.add(netSalesRial, serviceRial), it) },
            fullCostCoverageLineCount = profitability?.coveredLineCount ?: 0,
            totalLineCount = profitability?.totalLineCount ?: 0,
            profitabilityLines = lines.map { line ->
                DailySalesProfitabilityLine(
                    line.menuItemId,
                    line.recipeVersionId,
                    line.menuItemNameSnapshot,
                    line.quantityMicros,
                    line.grossSalesRial,
                    line.theoreticalCostRial,
                    line.foodCostSnapshotRial,
                    line.packagingCostSnapshotRial,
                    line.directLaborCostSnapshotRial,
                    line.allocatedOverheadSnapshotRial,
                )
            },
            cashRial = settlement.cashRial,
            cardRial = settlement.cardRial,
            transferRial = settlement.transferRial,
            settlements = settlementRows.map(::settlementDraft),
            status = domainStatus,
            notes = notes,
            isLegacyArchive = isLegacyArchive,
            reversedAtEpochDay = reversedAtEpochDay,
            reversalReason = reversalReason,
            isClosed = closure?.status == "CLOSED",
            closedBy = closure?.closedBy,
            closeNote = closure?.note.orEmpty(),
            closureStatus = closure?.status,
            closureRevisionNo = closure?.revisionNo ?: 0,
            reopenedBy = closure?.reopenedBy,
            reopenReason = closure?.reopenReason.orEmpty(),
        )
    }

    private data class SettlementSnapshot(val cashRial: Long, val cardRial: Long, val transferRial: Long) {
        companion object { val ZERO = SettlementSnapshot(0L, 0L, 0L) }
    }

    private fun List<DailySalesSettlementEntity>.toSettlementSnapshot(): SettlementSnapshot = SettlementSnapshot(
        cashRial = filter { it.type == SalesSettlementType.CASH.name }.fold(0L) { total, row -> SignedLongMath.add(total, row.amountRial) },
        cardRial = filter { it.type == SalesSettlementType.CARD.name }.fold(0L) { total, row -> SignedLongMath.add(total, row.amountRial) },
        transferRial = filter { it.type == SalesSettlementType.BANK_TRANSFER.name }.fold(0L) { total, row -> SignedLongMath.add(total, row.amountRial) },
    )

    private suspend fun audit(
        action: String,
        entityId: Long,
        description: String,
        now: Long,
        entityType: String = "DAILY_SALES",
        businessEpochDay: Long? = null,
        reason: String = description,
        beforeSnapshot: String? = null,
        afterSnapshot: String? = null,
    ) {
        auditWriter.appendAuthorized(
            authorizer = authorizer,
            action = action,
            entityType = entityType,
            entityId = entityId,
            description = description,
            occurredAtEpochMillis = now,
            businessEpochDay = businessEpochDay,
            reason = reason,
            beforeSnapshot = beforeSnapshot,
            afterSnapshot = afterSnapshot,
            correlationId = "sales:$entityId:$action:$now",
        )
    }

    private fun mulDiv(a: Long, b: Long, divisor: Long): Long = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).toLongExactCompat()
}
