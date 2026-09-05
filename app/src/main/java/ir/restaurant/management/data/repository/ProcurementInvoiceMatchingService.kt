package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.core.toLongExactCompat
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.ProcurementInvoiceLineMatchEntity
import ir.restaurant.management.data.db.ProcurementInvoiceLinkEntity
import ir.restaurant.management.data.db.PurchaseEntity
import ir.restaurant.management.data.db.PurchaseLineEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.purchase.PostedPurchase
import ir.restaurant.management.domain.purchase.PurchaseCalculator
import ir.restaurant.management.domain.purchase.PurchaseDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import ir.restaurant.management.domain.purchase.PurchasePaymentMethod
import ir.restaurant.management.domain.purchase.PurchasePaymentStatus
import ir.restaurant.management.domain.purchase.SupplierInvoiceNumber
import ir.restaurant.management.domain.purchase.ThreeWayMatchResult
import ir.restaurant.management.domain.purchase.ThreeWayMatchStatus
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryDirection
import ir.restaurant.management.domain.treasury.TreasuryService
import java.math.BigInteger
import kotlin.math.abs

/**
 * Canonical line-level PO -> GR -> supplier invoice boundary.
 *
 * Partial receipts and multiple invoices per PO are supported. A supplier invoice can only consume
 * quantity that has actually been accepted and has not already been matched to another invoice.
 * No inventory mutation occurs here; the physical stock was created by the GR boundary.
 */
internal class ProcurementInvoiceMatchingService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val accountingPosting: AccountingPostingService,
    private val treasury: TreasuryService,
    private val clock: () -> Long,
    private val syncRecorder: SyncRecorder?,
) {
    private val dataScope = LocalDataScopeService(database, authorizer)
    private val payables = LocalSupplierPayableService(database, clock)

    suspend fun preview(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
    ): ThreeWayMatchResult = database.withTransaction { calculateMatch(purchaseOrderId, invoice).summary }

    suspend fun post(
        purchaseOrderId: Long,
        invoice: PurchaseDraft,
        approvePriceVariance: Boolean,
    ): PostedPurchase {
        val actor = authorizer.require(Permission.PURCHASES)
        val prepared = PurchaseCalculator.prepare(invoice)
        val now = clock()
        return database.withTransaction {
            database.purchaseDao().byCommandId(prepared.draft.commandId)?.let { existing ->
                require(existing.supplierId == prepared.draft.supplierId && existing.totalRial == prepared.total.value) {
                    "procurement_invoice_idempotency_conflict"
                }
                val journal = database.accountingDao().entryBySource("PROCUREMENT_INVOICE", existing.id)
                return@withTransaction PostedPurchase(existing.id, journal?.id, MoneyRial.of(existing.totalRial), existing.invoiceNo)
            }

            val order = database.procurementDao().orderById(purchaseOrderId)
                ?: error("سفارش خرید پیدا نشد.")
            require(order.status in setOf(PurchaseOrderStatus.PARTIALLY_RECEIVED.name, PurchaseOrderStatus.RECEIVED.name)) {
                "برای تطبیق فاکتور باید حداقل یک دریافت پذیرفته‌شده و فاکتورنشده وجود داشته باشد."
            }
            val branchId = requireNotNull(order.branchId) { "سفارش خرید بدون شعبه قابل تطبیق نیست." }
            val locationId = requireNotNull(order.destinationLocationId) { "سفارش خرید بدون انبار مقصد قابل تطبیق نیست." }
            val branch = dataScope.requireBranch(branchId)
            dataScope.requireLocation(locationId, branchId)
            require(order.supplierId == prepared.draft.supplierId) { "تأمین‌کننده فاکتور با سفارش یکسان نیست." }
            require(prepared.draft.branchId == null || prepared.draft.branchId == branchId) { "شعبه فاکتور با سفارش خرید یکسان نیست." }
            require(prepared.draft.locationId == null || prepared.draft.locationId == locationId) { "انبار فاکتور با سفارش خرید یکسان نیست." }
            require(prepared.draft.purchaseEpochDay >= order.orderEpochDay) { "تاریخ فاکتور نمی‌تواند قبل از سفارش خرید باشد." }
            val normalizedInvoiceNo = SupplierInvoiceNumber.normalize(prepared.draft.invoiceNo)
            require(normalizedInvoiceNo.isNotBlank()) { "شماره فاکتور خرید پس از نرمال‌سازی معتبر نیست." }
            require(!database.purchaseDao().supplierInvoiceExists(order.supplierId, normalizedInvoiceNo)) {
                "این شماره فاکتور برای تأمین‌کننده قبلاً ثبت شده است."
            }

            val plan = calculateMatch(order.id, prepared.draft)
            require(plan.summary.status != ThreeWayMatchStatus.QUANTITY_VARIANCE) {
                "مقدار یکی از ردیف‌های فاکتور از مقدار دریافت‌شده و فاکتورنشده بیشتر است."
            }
            val requiresApproval = plan.summary.status == ThreeWayMatchStatus.PRICE_VARIANCE &&
                plan.summary.priceVarianceBasisPoints > PRICE_VARIANCE_APPROVAL_BASIS_POINTS
            val varianceApprover = if (requiresApproval) {
                require(approvePriceVariance) { "مغایرت قیمت بیش از ۵٪ است و تأیید مجاز لازم دارد." }
                val approver = authorizer.require(Permission.PURCHASE_VARIANCE_APPROVE)
                require(approver.id != actor.id) { "تأییدکننده مغایرت قیمت باید غیر از ثبت‌کننده فاکتور باشد." }
                approver
            } else null

            val paid = prepared.draft.paymentMethod != PurchasePaymentMethod.PAYABLE
            val purchaseId = database.purchaseDao().insert(
                PurchaseEntity(
                    invoiceNo = prepared.draft.invoiceNo,
                    normalizedInvoiceNo = normalizedInvoiceNo,
                    supplierId = order.supplierId,
                    purchaseEpochDay = prepared.draft.purchaseEpochDay,
                    branchName = branch.name,
                    branchId = branchId,
                    locationId = locationId,
                    commandId = prepared.draft.commandId,
                    dueEpochDay = prepared.draft.dueEpochDay,
                    totalRial = prepared.total.value,
                    paidRial = if (paid) prepared.total.value else 0L,
                    paymentStatus = if (paid) PurchasePaymentStatus.PAID.storedValue else PurchasePaymentStatus.UNPAID.storedValue,
                    paymentMethod = prepared.draft.paymentMethod.storedValue,
                    reminderEnabled = !paid && prepared.draft.reminderEnabled,
                    reminderEpochDay = if (!paid && prepared.draft.reminderEnabled) prepared.draft.reminderEpochDay else null,
                    createdAtEpochMillis = now,
                ),
            )
            val purchaseLineIds = database.purchaseDao().insertLines(
                plan.lines.map { line ->
                    PurchaseLineEntity(
                        purchaseId = purchaseId,
                        itemId = line.orderLine.itemId,
                        itemNameSnapshot = line.orderLine.itemNameSnapshot,
                        quantityMicros = line.invoiceQtyMicros,
                        unitCostRial = line.invoiceUnitCostRial,
                        lineTotalRial = line.invoiceValueRial,
                    )
                },
            )
            require(purchaseLineIds.size == plan.lines.size) { "ثبت ردیف‌های فاکتور ناقص بود." }

            val journalLines = buildList {
                add(SemanticJournalLine(SemanticAccountRole.GOODS_RECEIVED_NOT_INVOICED, debit = MoneyRial.of(plan.summary.acceptedValueRial)))
                if (plan.summary.priceVarianceRial > 0) {
                    add(SemanticJournalLine(SemanticAccountRole.PURCHASE_PRICE_VARIANCE, debit = MoneyRial.of(plan.summary.priceVarianceRial)))
                } else if (plan.summary.priceVarianceRial < 0) {
                    add(SemanticJournalLine(SemanticAccountRole.PURCHASE_PRICE_VARIANCE, credit = MoneyRial.of(-plan.summary.priceVarianceRial)))
                }
                add(SemanticJournalLine(SemanticAccountRole.SUPPLIER_PAYABLE, credit = prepared.total))
            }
            val journalId = accountingPosting.post(
                AccountingPostingCommand(
                    entryNo = "تخ-$purchaseId",
                    businessEpochDay = prepared.draft.purchaseEpochDay,
                    description = "تطبیق فاکتور ${prepared.draft.invoiceNo} با ${order.orderNo}",
                    sourceType = "PROCUREMENT_INVOICE",
                    sourceId = purchaseId,
                    accountingScope = AccountingScope.BRANCH,
                    branchId = branchId,
                    lines = journalLines,
                    idempotencyKey = "PROCUREMENT_INVOICE:$purchaseId:post",
                    correlationId = CorrelationId.parse("procurement_invoice:$purchaseId"),
                    actorId = actor.id,
                ),
            ).entryId

            val payable = payables.ensureOrigin(
                sourceType = "PURCHASE",
                sourceId = purchaseId,
                sourceDocumentNo = prepared.draft.invoiceNo,
                supplierId = order.supplierId,
                branchId = branchId,
                issueEpochDay = prepared.draft.purchaseEpochDay,
                dueEpochDay = prepared.draft.dueEpochDay,
                originalRial = prepared.total.value,
                actorId = actor.id,
                correlationId = "procurement_invoice:$purchaseId",
                originJournalEntryId = journalId,
            )

            if (paid && prepared.total > MoneyRial.ZERO) {
                val commandId = GlobalId.new()
                val treasuryResult = treasury.execute(
                    TreasuryCommand.Settlement(
                        commandId = commandId,
                        businessEpochDay = prepared.draft.purchaseEpochDay,
                        correlationId = CorrelationId.forCommand("procurement_invoice_settlement", commandId),
                        businessIntent = TreasuryBusinessIntent.PURCHASE_PAYABLE_SETTLEMENT,
                        sourceId = purchaseId,
                        reason = "پرداخت فاکتور تطبیق‌شده ${prepared.draft.invoiceNo}",
                        accountingScope = AccountingScope.BRANCH,
                        branchId = branchId,
                        accountId = TreasuryAccountId.parse(requireNotNull(prepared.draft.paymentMethod.treasuryAccountId)),
                        direction = TreasuryDirection.PAYMENT,
                        channel = requireNotNull(prepared.draft.paymentMethod.treasuryChannel),
                        amount = prepared.total,
                    ),
                )
                val treasuryJournal = requireNotNull(treasuryResult.journalEntryId) { "سند خزانه پرداخت فاکتور تطبیق‌شده ایجاد نشد." }
                payables.settle(
                    sourceType = "PURCHASE",
                    sourceId = purchaseId,
                    amountRial = prepared.total.value,
                    businessEpochDay = prepared.draft.purchaseEpochDay,
                    commandId = commandId.value,
                    correlationId = treasuryResult.correlationId.value,
                    treasuryTransactionId = treasuryResult.id,
                    journalEntryId = treasuryJournal,
                    actorId = actor.id,
                    reason = "پرداخت هم‌زمان فاکتور تطبیق‌شده",
                )
            }

            val invoiceLinkId = database.procurementDao().insertInvoiceLink(
                ProcurementInvoiceLinkEntity(
                    purchaseOrderId = order.id,
                    purchaseId = purchaseId,
                    branchId = branchId,
                    matchStatus = plan.summary.status.name,
                    acceptedValueRial = plan.summary.acceptedValueRial,
                    invoiceValueRial = plan.summary.invoiceValueRial,
                    priceVarianceRial = plan.summary.priceVarianceRial,
                    varianceApprovedBy = varianceApprover?.displayName,
                    matchedAtEpochMillis = now,
                ),
            )
            database.phase3Dao().insertInvoiceLineMatches(
                plan.lines.mapIndexed { index, line ->
                    ProcurementInvoiceLineMatchEntity(
                        invoiceLinkId = invoiceLinkId,
                        purchaseOrderLineId = line.orderLine.id,
                        purchaseLineId = purchaseLineIds[index],
                        poQtyMicros = line.orderLine.orderedQtyMicros,
                        receivedQtyMicros = line.receivedNetQtyMicros,
                        invoiceQtyMicros = line.invoiceQtyMicros,
                        poUnitCostRial = line.orderLine.unitCostRial,
                        invoiceUnitCostRial = line.invoiceUnitCostRial,
                        quantityVarianceMicros = 0L,
                        priceVarianceRial = line.priceVarianceRial,
                    )
                },
            )

            val refreshedLines = database.procurementDao().orderLines(order.id)
            val allInvoiceableConsumed = refreshedLines.all { row ->
                val netReceived = SignedLongMath.subtract(row.receivedQtyMicros, row.returnedQtyMicros)
                database.phase3Dao().invoicedQuantityForOrderLine(row.id) >= netReceived
            }
            val fullyReceived = refreshedLines.all { it.receivedQtyMicros >= it.orderedQtyMicros }
            val nextStatus = when {
                fullyReceived && allInvoiceableConsumed -> PurchaseOrderStatus.CLOSED
                fullyReceived -> PurchaseOrderStatus.RECEIVED
                else -> PurchaseOrderStatus.PARTIALLY_RECEIVED
            }
            check(database.procurementDao().updateOrderStatus(order.id, nextStatus.name, now) == 1) { "به‌روزرسانی وضعیت سفارش انجام نشد." }
            if (nextStatus == PurchaseOrderStatus.CLOSED) {
                database.managementControlDao().transitionCommitment(
                    referenceType = "PURCHASE_REQUISITION",
                    referenceId = order.requisitionId,
                    status = "CONSUMED",
                    now = now,
                )
            }
            LocalAuditEventWriter(database).appendAuthorized(
                authorizer = authorizer,
                action = "THREE_WAY_MATCH",
                entityType = "PROCUREMENT_INVOICE",
                entityId = purchaseId,
                description = "تطبیق خط‌به‌خط فاکتور ${prepared.draft.invoiceNo} با ${order.orderNo}",
                occurredAtEpochMillis = now,
                businessEpochDay = prepared.draft.purchaseEpochDay,
                reason = "PO/GR/INVOICE_LINE_MATCH",
                afterSnapshot = "order=${order.id};link=$invoiceLinkId;lines=${plan.lines.size};status=${plan.summary.status};orderStatus=${nextStatus.name}",
                correlationId = "procurement_invoice:$purchaseId",
                referenceType = "PURCHASE_ORDER",
                referenceId = order.id,
            )
            syncRecorder?.record("PURCHASE", purchaseId, "THREE_WAY_MATCH", now, recordAudit = false)
            PostedPurchase(purchaseId, journalId, prepared.total, prepared.draft.invoiceNo)
        }
    }

    private suspend fun calculateMatch(purchaseOrderId: Long, invoice: PurchaseDraft): InvoiceMatchPlan {
        val prepared = PurchaseCalculator.prepare(invoice)
        val orderLines = database.procurementDao().orderLines(purchaseOrderId)
        require(orderLines.isNotEmpty()) { "ردیف‌های سفارش پیدا نشدند." }
        val byItem = orderLines.associateBy { it.itemId }
        require(byItem.size == orderLines.size) { "یک کالا چند بار در سفارش ثبت شده و تطبیق خودکار امن نیست." }

        var quantityVariance = false
        val plans = prepared.lines.map { invoiceLine ->
            val orderLine = byItem[invoiceLine.itemId]
                ?: throw IllegalArgumentException("کالای فاکتور در سفارش خرید وجود ندارد.")
            val receivedNet = SignedLongMath.subtract(orderLine.receivedQtyMicros, orderLine.returnedQtyMicros)
            val alreadyInvoiced = database.phase3Dao().invoicedQuantityForOrderLine(orderLine.id)
            val invoiceable = SignedLongMath.subtract(receivedNet, alreadyInvoiced).coerceAtLeast(0L)
            if (invoiceLine.quantityMicros > invoiceable) quantityVariance = true
            val expected = MoneyRial.of(orderLine.unitCostRial).times(QuantityMicros.of(invoiceLine.quantityMicros)).value
            val invoiceValue = invoiceLine.total.value
            LineMatchPlan(
                orderLine = orderLine,
                receivedNetQtyMicros = receivedNet,
                alreadyInvoicedQtyMicros = alreadyInvoiced,
                invoiceQtyMicros = invoiceLine.quantityMicros,
                invoiceUnitCostRial = invoiceLine.unitCostRial,
                acceptedValueRial = expected,
                invoiceValueRial = invoiceValue,
                priceVarianceRial = SignedLongMath.subtract(invoiceValue, expected),
            )
        }
        val acceptedValue = MoneyRial.sum(plans.map { MoneyRial.of(it.acceptedValueRial) }).value
        val invoiceValue = prepared.total.value
        val variance = SignedLongMath.subtract(invoiceValue, acceptedValue)
        val basisPoints = if (acceptedValue == 0L) 0L else BigInteger.valueOf(abs(variance))
            .multiply(BigInteger.valueOf(10_000))
            .divide(BigInteger.valueOf(acceptedValue))
            .toLongExactCompat()
        val status = when {
            quantityVariance -> ThreeWayMatchStatus.QUANTITY_VARIANCE
            variance != 0L -> ThreeWayMatchStatus.PRICE_VARIANCE
            else -> ThreeWayMatchStatus.MATCHED
        }
        return InvoiceMatchPlan(
            summary = ThreeWayMatchResult(status, acceptedValue, invoiceValue, variance, basisPoints),
            lines = plans,
        )
    }

    private data class InvoiceMatchPlan(
        val summary: ThreeWayMatchResult,
        val lines: List<LineMatchPlan>,
    )

    private data class LineMatchPlan(
        val orderLine: PurchaseOrderLineEntity,
        val receivedNetQtyMicros: Long,
        val alreadyInvoicedQtyMicros: Long,
        val invoiceQtyMicros: Long,
        val invoiceUnitCostRial: Long,
        val acceptedValueRial: Long,
        val invoiceValueRial: Long,
        val priceVarianceRial: Long,
    )

    private companion object {
        const val PRICE_VARIANCE_APPROVAL_BASIS_POINTS = 500L
    }
}
