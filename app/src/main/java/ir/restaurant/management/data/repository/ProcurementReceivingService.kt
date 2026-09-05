package ir.restaurant.management.data.repository

import androidx.room.withTransaction
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.FixedPointRatio
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.GoodsReceiptEntity
import ir.restaurant.management.data.db.GoodsReceiptLineEntity
import ir.restaurant.management.data.db.ProcurementReceiptLotAllocationEntity
import ir.restaurant.management.data.db.PurchaseOrderLineEntity
import ir.restaurant.management.data.db.PurchaseReturnEntity
import ir.restaurant.management.data.db.PurchaseReturnLineEntity
import ir.restaurant.management.data.db.SupplierCreditEntity
import ir.restaurant.management.domain.accounting.AccountingPostingCommand
import ir.restaurant.management.domain.accounting.AccountingPostingService
import ir.restaurant.management.domain.accounting.AccountingScope
import ir.restaurant.management.domain.accounting.SemanticAccountRole
import ir.restaurant.management.domain.accounting.SemanticJournalLine
import ir.restaurant.management.domain.common.DocumentNumberType
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.InventoryReceiptLot
import ir.restaurant.management.domain.inventory.ReceiveInventoryCommand
import ir.restaurant.management.domain.purchase.GoodsReceiptDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import ir.restaurant.management.domain.purchase.PurchaseReturnDraft
import ir.restaurant.management.domain.security.AuthorizationService
import ir.restaurant.management.domain.security.Permission

/** Owns procurement receiving and supplier-return workflows while the legacy repository stays a facade. */
internal class ProcurementReceivingService(
    private val database: AppDatabase,
    private val authorizer: AuthorizationService,
    private val accountingPosting: AccountingPostingService,
    private val clock: () -> Long,
    private val syncRecorder: SyncRecorder?,
) {
    private val inventoryCommands = LocalInventoryCommandEngine(database, clock = clock, authorizer = authorizer)
    private val numbering = LocalDocumentNumberAllocator(database, clock)
    private val auditWriter = LocalAuditEventWriter(database)
    private val dataScope = LocalDataScopeService(database, authorizer)
    private val payables = LocalSupplierPayableService(database, clock)

    suspend fun receive(draft: GoodsReceiptDraft): Long {
        val actor = authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            val order = database.procurementDao().orderById(valid.purchaseOrderId)
                ?: error("سفارش خرید پیدا نشد.")
            val branchId = requireNotNull(order.branchId) { "سفارش خرید بدون شعبه قابل دریافت نیست." }
            val destinationLocationId = requireNotNull(order.destinationLocationId) { "سفارش خرید بدون انبار مقصد قابل دریافت نیست." }
            require(valid.destinationLocationId == destinationLocationId) { "مقصد رسید باید همان مقصد ثابت سفارش خرید باشد." }
            dataScope.requireLocation(destinationLocationId, branchId)
            val existingReceipt = database.procurementDao().receiptByDeliveryNote(order.id, valid.deliveryNoteNo)
            val currentOrderLines = database.procurementDao().orderLines(order.id)
            GoodsReceiptIdempotency.existingReplayIdOrThrow(
                existing = existingReceipt,
                existingLines = existingReceipt?.let { database.procurementDao().receiptLines(it.id) }.orEmpty(),
                currentOrderLines = currentOrderLines,
                draft = valid,
            )?.let { return@withTransaction it }
            require(order.status in setOf(PurchaseOrderStatus.OPEN.name, PurchaseOrderStatus.PARTIALLY_RECEIVED.name)) {
                "این سفارش قابل دریافت نیست."
            }
            require(valid.receiptEpochDay >= order.orderEpochDay) {
                "تاریخ دریافت نمی‌تواند قبل از سفارش باشد."
            }
            val receiptNo = numbering.next(DocumentNumberType.GOODS_RECEIPT)
            require(!database.procurementDao().receiptNoExists(receiptNo)) { "شماره رسید تکراری است." }
            require(!database.procurementDao().deliveryNoteExists(order.id, valid.deliveryNoteNo)) {
                "این شماره حواله قبلاً برای سفارش ثبت شده است."
            }
            val receiptId = database.procurementDao().insertReceipt(
                GoodsReceiptEntity(
                    receiptNo = receiptNo,
                    purchaseOrderId = order.id,
                    branchId = branchId,
                    destinationLocationId = destinationLocationId,
                    receiptEpochDay = valid.receiptEpochDay,
                    deliveryNoteNo = valid.deliveryNoteNo,
                    receivedBy = actor.displayName,
                    note = valid.note,
                    createdAtEpochMillis = now,
                ),
            )
            val orderLines = currentOrderLines.associateBy { it.id }
            var acceptedTotal = MoneyRial.ZERO
            val receiptLines = valid.lines.map { received ->
                val orderLine = orderLines[received.purchaseOrderLineId]
                    ?: error("ردیف تحویل متعلق به این سفارش نیست.")
                val remaining = orderLine.orderedQtyMicros - orderLine.receivedQtyMicros
                require(received.deliveredQtyMicros <= remaining) {
                    "تحویل «${orderLine.itemNameSnapshot}» از مانده سفارش بیشتر است."
                }
                val rejected = if (valid.finalizeOrder) {
                    remaining - received.acceptedQtyMicros
                } else {
                    received.deliveredQtyMicros - received.acceptedQtyMicros
                }
                require(rejected == 0L || received.rejectionReason.length >= 3) {
                    "برای کسری یا رد «${orderLine.itemNameSnapshot}» دلیل ثبت کنید."
                }
                check(
                    database.procurementDao().addReceiptQuantities(
                        lineId = orderLine.id,
                        purchaseOrderId = order.id,
                        acceptedQtyMicros = received.acceptedQtyMicros,
                        rejectedQtyMicros = rejected,
                    ) == 1,
                ) { "مقدار دریافت هم‌زمان تغییر کرده است؛ دوباره تلاش کنید." }
                val acceptedValue = MoneyRial.of(orderLine.unitCostRial)
                    .times(QuantityMicros.of(received.acceptedQtyMicros))
                acceptedTotal += acceptedValue
                if (received.acceptedQtyMicros > 0) {
                    inventoryCommands.receive(
                        ReceiveInventoryCommand(
                        itemId = orderLine.itemId,
                        quantityMicros = received.acceptedQtyMicros,
                        valueRial = acceptedValue.value,
                        movementType = InventoryMovementType.GOODS_RECEIPT,
                        referenceType = InventoryReferenceType.GOODS_RECEIPT,
                        referenceId = receiptId,
                        businessEpochDay = valid.receiptEpochDay,
                        context = InventoryCommandContext.local(
                            referenceType = InventoryReferenceType.GOODS_RECEIPT,
                            referenceId = receiptId,
                            suffix = "receive:${orderLine.id}",
                            actorId = actor.id,
                            reasonCode = InventoryReasonCode.GOODS_RECEIPT,
                            reason = "دریافت ${order.orderNo} / ${valid.deliveryNoteNo}",
                            correlationId = "goods_receipt:$receiptId",
                            locationId = destinationLocationId,
                        ),
                        notes = "${order.orderNo} / ${valid.deliveryNoteNo}",
                        lot = received.lotNumber?.let { lotNumber ->
                            InventoryReceiptLot(
                                lotNumber = lotNumber,
                                supplierLotNumber = received.supplierLotNumber,
                                productionEpochDay = received.productionEpochDay,
                                expiryEpochDay = received.expiryEpochDay,
                                barcode = received.lotBarcode,
                            )
                        },
                        ),
                    )
                }
                GoodsReceiptLineEntity(
                    goodsReceiptId = receiptId,
                    purchaseOrderLineId = orderLine.id,
                    itemId = orderLine.itemId,
                    deliveredQtyMicros = received.deliveredQtyMicros,
                    acceptedQtyMicros = received.acceptedQtyMicros,
                    rejectedQtyMicros = rejected,
                    rejectionReason = received.rejectionReason,
                    acceptedValueRial = acceptedValue.value,
                    lotNumber = received.lotNumber,
                    supplierLotNumber = received.supplierLotNumber,
                    productionEpochDay = received.productionEpochDay,
                    expiryEpochDay = received.expiryEpochDay,
                    lotBarcode = received.lotBarcode,
                )
            }
            database.procurementDao().insertReceiptLines(receiptLines)
            receiptLines.filter { it.acceptedQtyMicros > 0 && !it.lotNumber.isNullOrBlank() }.forEach { line ->
                val lot = database.inventoryLotDao().byNaturalKey(line.itemId, destinationLocationId, requireNotNull(line.lotNumber))
                    ?: error("لات دریافت‌شده پس از ثبت موجودی پیدا نشد.")
                database.phase3Dao().insertReceiptLotAllocation(
                    ProcurementReceiptLotAllocationEntity(
                        goodsReceiptId = receiptId,
                        purchaseOrderLineId = line.purchaseOrderLineId,
                        lotId = lot.id,
                        receivedQuantityMicros = line.acceptedQtyMicros,
                        createdAtEpochMillis = now,
                    ),
                )
            }
            if (acceptedTotal > MoneyRial.ZERO) {
                postJournal(
                    entryNo = "رس-$receiptId",
                    epochDay = valid.receiptEpochDay,
                    description = "دریافت کالا برای ${order.orderNo}",
                    sourceType = "GOODS_RECEIPT",
                    sourceId = receiptId,
                    lines = listOf(
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, debit = acceptedTotal),
                        SemanticJournalLine(
                            SemanticAccountRole.GOODS_RECEIVED_NOT_INVOICED,
                            credit = acceptedTotal,
                        ),
                    ),
                    actorId = actor.id,
                )
            }
            val refreshed = database.procurementDao().orderLines(order.id)
            val status = if (
                valid.finalizeOrder || refreshed.all { it.receivedQtyMicros == it.orderedQtyMicros }
            ) {
                PurchaseOrderStatus.RECEIVED
            } else {
                PurchaseOrderStatus.PARTIALLY_RECEIVED
            }
            check(database.procurementDao().updateOrderStatus(order.id, status.name, now) == 1) {
                "وضعیت سفارش تغییر نکرد."
            }
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "RECEIVE",
                entityType = "GOODS_RECEIPT",
                entityId = receiptId,
                description = "دریافت کالا برای ${order.orderNo}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.receiptEpochDay,
                reason = "DELIVERY_NOTE:${valid.deliveryNoteNo}",
                afterSnapshot = "status=${status.name};acceptedValueRial=${acceptedTotal.value};lineCount=${receiptLines.size}",
                correlationId = "goods_receipt:$receiptId",
                referenceType = "PURCHASE_ORDER",
                referenceId = order.id,
            )
            syncRecorder?.record("GOODS_RECEIPT", receiptId, "POST", now, recordAudit = false)
            receiptId
        }
    }

    suspend fun returnToSupplier(draft: PurchaseReturnDraft): Long {
        val actor = authorizer.require(Permission.PURCHASES)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            val order = database.procurementDao().orderById(valid.purchaseOrderId)
                ?: error("سفارش خرید پیدا نشد.")
            val branchId = requireNotNull(order.branchId) { "سفارش خرید بدون شعبه قابل مرجوعی نیست." }
            val locationId = requireNotNull(order.destinationLocationId) { "سفارش خرید بدون انبار مقصد قابل مرجوعی نیست." }
            dataScope.requireLocation(locationId, branchId)
            require(order.status in setOf(PurchaseOrderStatus.PARTIALLY_RECEIVED.name, PurchaseOrderStatus.RECEIVED.name, PurchaseOrderStatus.CLOSED.name)) {
                "این سفارش کالای قابل مرجوعی ندارد."
            }
            val latestReceiptDay = database.procurementDao().latestReceiptEpochDay(order.id)
                ?: error("برای این سفارش رسید کالایی ثبت نشده است.")
            require(valid.returnEpochDay >= latestReceiptDay) { "تاریخ مرجوعی نمی‌تواند قبل از آخرین رسید کالا باشد." }

            val orderLines = database.procurementDao().orderLines(order.id).associateBy { it.id }
            val financialAllocations = valid.lines.flatMap { returned ->
                val orderLine = orderLines[returned.purchaseOrderLineId]
                    ?: error("ردیف مرجوعی متعلق به این سفارش نیست.")
                financialAllocationsForReturn(orderLine.id, returned.quantityMicros)
            }
            val remainingLotQuantity = mutableMapOf<Long, Long>()
            val physicalLines = valid.lines.flatMap { returned ->
                val orderLine = orderLines[returned.purchaseOrderLineId]
                    ?: error("ردیف مرجوعی متعلق به این سفارش نیست.")
                val returnable = orderLine.receivedQtyMicros - orderLine.returnedQtyMicros
                require(returned.quantityMicros <= returnable) {
                    "مقدار مرجوعی «${orderLine.itemNameSnapshot}» از مقدار قابل مرجوع بیشتر است."
                }
                val item = database.inventoryDao().activeById(orderLine.itemId)
                    ?: error("کالای «${orderLine.itemNameSnapshot}» فعال نیست.")
                if (!item.trackLot) {
                    listOf(
                        PreparedPurchaseReturnLine(
                            orderLine = orderLine,
                            quantityMicros = returned.quantityMicros,
                            inventoryUnitCostRial = orderLine.unitCostRial,
                            supplierUnitCreditRial = 0L,
                            inventoryValue = MoneyRial.of(orderLine.unitCostRial).times(QuantityMicros.of(returned.quantityMicros)),
                            supplierCreditValue = MoneyRial.ZERO,
                            reason = returned.reason,
                            lotId = null,
                            receiptLotAllocationId = null,
                            locationId = locationId,
                        ),
                    )
                } else {
                    var remaining = returned.quantityMicros
                    val allocations = mutableListOf<PreparedPurchaseReturnLine>()
                    database.phase3Dao().returnableProcurementLots(orderLine.id).forEach { candidate ->
                        if (remaining <= 0) return@forEach
                        val physicalRemaining = remainingLotQuantity.getOrPut(candidate.lotId) { candidate.currentLotQuantityMicros }
                        val allocationRemaining = candidate.receivedQuantityMicros - candidate.returnedQuantityMicros
                        val quantity = minOf(remaining, physicalRemaining, allocationRemaining)
                        if (quantity > 0) {
                            allocations += PreparedPurchaseReturnLine(
                                orderLine = orderLine,
                                quantityMicros = quantity,
                                inventoryUnitCostRial = candidate.lotUnitCostRial,
                                supplierUnitCreditRial = 0L,
                                inventoryValue = MoneyRial.of(candidate.lotUnitCostRial).times(QuantityMicros.of(quantity)),
                                supplierCreditValue = MoneyRial.ZERO,
                                reason = returned.reason,
                                lotId = candidate.lotId,
                                receiptLotAllocationId = candidate.allocationId,
                                locationId = locationId,
                            )
                            remaining = ir.restaurant.management.core.SignedLongMath.subtract(remaining, quantity)
                            remainingLotQuantity[candidate.lotId] = ir.restaurant.management.core.SignedLongMath.subtract(physicalRemaining, quantity)
                        }
                    }
                    require(remaining == 0L) { "لات واقعی دریافت‌شده برای مرجوعی «${orderLine.itemNameSnapshot}» به مقدار کافی موجود نیست." }
                    allocations
                }
            }
            val preparedLines = allocateSupplierCredits(physicalLines, financialAllocations)
            val inventoryTotal = MoneyRial.sum(preparedLines.map { it.inventoryValue })
            val supplierCreditTotal = MoneyRial.sum(preparedLines.map { it.supplierCreditValue })
            val linkedPurchaseIds = financialAllocations.map { it.purchaseId }.distinct()
            val returnId = database.procurementDao().insertPurchaseReturn(
                PurchaseReturnEntity(
                    returnNo = numbering.next(DocumentNumberType.PURCHASE_RETURN),
                    purchaseOrderId = order.id,
                    purchaseId = linkedPurchaseIds.singleOrNull(),
                    supplierId = order.supplierId,
                    branchId = branchId,
                    locationId = locationId,
                    returnEpochDay = valid.returnEpochDay,
                    reason = valid.reason,
                    returnedBy = actor.displayName,
                    inventoryValueRial = inventoryTotal.value,
                    supplierCreditValueRial = supplierCreditTotal.value,
                    createdAtEpochMillis = now,
                ),
            )

            preparedLines.groupBy { it.orderLine.id }.forEach { (_, rows) ->
                val totalQuantity = rows.fold(0L) { total, row -> ir.restaurant.management.core.SignedLongMath.add(total, row.quantityMicros) }
                check(database.procurementDao().addReturnedQuantity(rows.first().orderLine.id, order.id, totalQuantity) == 1) {
                    "مقدار قابل مرجوع هم‌زمان تغییر کرده است؛ دوباره تلاش کنید."
                }
            }
            preparedLines.forEach { line ->
                val item = database.inventoryDao().activeById(line.orderLine.itemId)
                    ?: error("کالای «${line.orderLine.itemNameSnapshot}» فعال نیست.")
                inventoryCommands.issue(
                    itemId = item.id,
                    quantityMicros = line.quantityMicros,
                    valueRial = line.inventoryValue.value,
                    movementType = InventoryMovementType.PURCHASE_RETURN,
                    referenceType = InventoryReferenceType.PURCHASE_RETURN,
                    referenceId = returnId,
                    movementEpochDay = valid.returnEpochDay,
                    context = InventoryCommandContext.local(
                        referenceType = InventoryReferenceType.PURCHASE_RETURN,
                        referenceId = returnId,
                        suffix = "return:${line.orderLine.id}:${line.lotId ?: 0}",
                        actorId = actor.id,
                        reasonCode = InventoryReasonCode.PURCHASE_RETURN,
                        reason = line.reason,
                        correlationId = "purchase_return:$returnId",
                        locationId = line.locationId,
                    ),
                    notes = "${order.orderNo} · ${line.reason}",
                    lotPolicy = if (line.lotId == null) LocalInventoryCommandEngine.LotIssuePolicy.NONE else LocalInventoryCommandEngine.LotIssuePolicy.FEFO_ALL,
                    requestedLotId = line.lotId,
                )
                line.receiptLotAllocationId?.let { allocationId ->
                    val current = database.phase3Dao().returnableProcurementLots(line.orderLine.id).firstOrNull { it.allocationId == allocationId }
                        ?: error("ردیف لات دریافت‌شده برای مرجوعی دیگر قابل استفاده نیست.")
                    check(database.phase3Dao().addReturnedReceiptLotQuantity(allocationId, current.returnedQuantityMicros, line.quantityMicros) == 1) {
                        "مقدار مرجوعی لات هم‌زمان تغییر کرده است."
                    }
                }
            }
            database.procurementDao().insertPurchaseReturnLines(
                preparedLines.map { line ->
                    PurchaseReturnLineEntity(
                        purchaseReturnId = returnId,
                        purchaseOrderLineId = line.orderLine.id,
                        itemId = line.orderLine.itemId,
                        lotId = line.lotId,
                        locationId = line.locationId,
                        quantityMicros = line.quantityMicros,
                        inventoryUnitCostRial = line.inventoryUnitCostRial,
                        supplierUnitCreditRial = line.supplierUnitCreditRial,
                        inventoryValueRial = line.inventoryValue.value,
                        supplierCreditValueRial = line.supplierCreditValue.value,
                        reason = line.reason,
                    )
                },
            )

            if (financialAllocations.isEmpty()) {
                postJournal(
                    entryNo = "مر-$returnId",
                    epochDay = valid.returnEpochDay,
                    description = "مرجوعی پیش از فاکتور ${order.orderNo}",
                    sourceType = "PURCHASE_RETURN",
                    sourceId = returnId,
                    lines = listOf(
                        SemanticJournalLine(SemanticAccountRole.GOODS_RECEIVED_NOT_INVOICED, debit = inventoryTotal),
                        SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, credit = inventoryTotal),
                    ),
                    actorId = actor.id,
                )
            } else {
                postInvoicedReturn(
                    orderNo = order.orderNo,
                    supplierId = order.supplierId,
                    allocations = financialAllocations,
                    returnId = returnId,
                    returnEpochDay = valid.returnEpochDay,
                    inventoryTotal = inventoryTotal,
                    supplierCreditTotal = supplierCreditTotal,
                    actorId = actor.id,
                    now = now,
                )
            }
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "RETURN_TO_SUPPLIER",
                entityType = "PURCHASE_RETURN",
                entityId = returnId,
                description = "مرجوعی واقعی لات/مکان برای ${order.orderNo}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.returnEpochDay,
                reason = valid.reason,
                afterSnapshot = "inventoryRial=${inventoryTotal.value};supplierCreditRial=${supplierCreditTotal.value};invoiceCount=${linkedPurchaseIds.size}",
                correlationId = "purchase_return:$returnId",
                referenceType = "PURCHASE_ORDER",
                referenceId = order.id,
            )
            syncRecorder?.record("PURCHASE_RETURN", returnId, "POST", now, recordAudit = false)
            returnId
        }
    }

    private suspend fun financialAllocationsForReturn(
        purchaseOrderLineId: Long,
        returnQuantityMicros: Long,
    ): List<ReturnFinancialAllocation> {
        var skip = database.phase3Dao().priorInvoicedReturnQuantity(purchaseOrderLineId)
        var remaining = returnQuantityMicros
        val result = mutableListOf<ReturnFinancialAllocation>()
        database.phase3Dao().returnInvoiceMatches(purchaseOrderLineId).forEach { match ->
            if (remaining <= 0L) return@forEach
            val skippedHere = minOf(skip, match.invoiceQtyMicros)
            skip -= skippedHere
            val available = match.invoiceQtyMicros - skippedHere
            val qty = minOf(remaining, available)
            if (qty > 0L) {
                result += ReturnFinancialAllocation(
                    purchaseId = match.purchaseId,
                    purchaseOrderLineId = purchaseOrderLineId,
                    quantityMicros = qty,
                    unitCreditRial = match.invoiceUnitCostRial,
                    creditValue = MoneyRial.of(match.invoiceUnitCostRial).times(QuantityMicros.of(qty)),
                )
                remaining -= qty
            }
        }
        // Any remainder belongs to received-but-not-yet-invoiced quantity and therefore has no supplier AP credit.
        return result
    }

    private fun allocateSupplierCredits(
        physicalLines: List<PreparedPurchaseReturnLine>,
        financialAllocations: List<ReturnFinancialAllocation>,
    ): List<PreparedPurchaseReturnLine> {
        val queues = financialAllocations.groupBy { it.purchaseOrderLineId }.mapValues { (_, v) -> v.toMutableList() }.toMutableMap()
        return physicalLines.map { physical ->
            var remainingQty = physical.quantityMicros
            var credit = MoneyRial.ZERO
            var weightedUnit = 0L
            val queue = queues.getOrPut(physical.orderLine.id) { mutableListOf() }
            while (remainingQty > 0L && queue.isNotEmpty()) {
                val head = queue.first()
                val qty = minOf(remainingQty, head.quantityMicros)
                credit += MoneyRial.of(head.unitCreditRial).times(QuantityMicros.of(qty))
                weightedUnit = if (credit.value == 0L) 0L else FixedPointRatio.multiplyDivide(credit.value, QuantityMicros.SCALE, physical.quantityMicros)
                remainingQty -= qty
                if (qty == head.quantityMicros) {
                    queue.removeAt(0)
                } else {
                    queue[0] = head.copy(quantityMicros = head.quantityMicros - qty)
                }
            }
            physical.copy(
                supplierUnitCreditRial = weightedUnit.coerceAtLeast(0L),
                supplierCreditValue = credit,
            )
        }
    }

    private suspend fun postInvoicedReturn(
        orderNo: String,
        supplierId: Long,
        allocations: List<ReturnFinancialAllocation>,
        returnId: Long,
        returnEpochDay: Long,
        inventoryTotal: MoneyRial,
        supplierCreditTotal: MoneyRial,
        actorId: Long,
        now: Long,
    ) {
        val byPurchase = allocations.groupBy { it.purchaseId }.mapValues { (_, rows) -> MoneyRial.sum(rows.map { it.creditValue }) }
        var appliedTotal = MoneyRial.ZERO
        byPurchase.forEach { (purchaseId, amount) ->
            if (amount > MoneyRial.ZERO) {
                val command = GlobalId.new().value
                payables.applyCredit(
                    sourceType = "PURCHASE",
                    sourceId = purchaseId,
                    amountRial = amount.value,
                    businessEpochDay = returnEpochDay,
                    commandId = command,
                    correlationId = "purchase_return:$returnId",
                    journalEntryId = null,
                    actorId = actorId,
                    reason = "اعتبار مرجوعی خرید $orderNo",
                )
                appliedTotal += amount
            }
        }
        val openCredit = supplierCreditTotal - appliedTotal
        database.procurementDao().insertSupplierCredit(
            SupplierCreditEntity(
                creditNo = "SC-$returnId",
                supplierId = supplierId,
                sourceReturnId = returnId,
                appliedPurchaseId = byPurchase.keys.singleOrNull(),
                amountRial = supplierCreditTotal.value,
                appliedRial = appliedTotal.value,
                status = when {
                    appliedTotal == supplierCreditTotal -> "APPLIED"
                    appliedTotal > MoneyRial.ZERO -> "PARTIAL"
                    else -> "OPEN"
                },
                createdAtEpochMillis = now,
            ),
        )
        val journalLines = buildList {
            if (appliedTotal > MoneyRial.ZERO) add(SemanticJournalLine(SemanticAccountRole.SUPPLIER_PAYABLE, debit = appliedTotal))
            if (openCredit > MoneyRial.ZERO) add(SemanticJournalLine(SemanticAccountRole.SUPPLIER_CREDIT_RECEIVABLE, debit = openCredit))
            if (inventoryTotal > supplierCreditTotal) add(SemanticJournalLine(SemanticAccountRole.PURCHASE_PRICE_VARIANCE, debit = inventoryTotal - supplierCreditTotal))
            add(SemanticJournalLine(SemanticAccountRole.INVENTORY_ASSET, credit = inventoryTotal))
            if (supplierCreditTotal > inventoryTotal) add(SemanticJournalLine(SemanticAccountRole.PURCHASE_PRICE_VARIANCE, credit = supplierCreditTotal - inventoryTotal))
        }
        postJournal(
            entryNo = "مر-$returnId",
            epochDay = returnEpochDay,
            description = "مرجوعی خرید و اعتبار تأمین‌کننده $orderNo",
            sourceType = "PURCHASE_RETURN",
            sourceId = returnId,
            lines = journalLines,
            actorId = actorId,
        )
    }

    private suspend fun postJournal(
        entryNo: String,
        epochDay: Long,
        description: String,
        sourceType: String,
        sourceId: Long,
        lines: List<SemanticJournalLine>,
        actorId: Long,
    ): Long = accountingPosting.post(
        AccountingPostingCommand(
            entryNo = entryNo,
            businessEpochDay = epochDay,
            description = description,
            sourceType = sourceType,
            sourceId = sourceId,
            accountingScope = AccountingScope.ORGANIZATION,
            branchId = null,
            lines = lines,
            idempotencyKey = "$sourceType:$sourceId:post",
            correlationId = CorrelationId.parse("${sourceType.lowercase()}:$sourceId"),
            actorId = actorId,
        ),
    ).entryId
}

private data class ReturnFinancialAllocation(
    val purchaseId: Long,
    val purchaseOrderLineId: Long,
    val quantityMicros: Long,
    val unitCreditRial: Long,
    val creditValue: MoneyRial,
)

private data class PreparedPurchaseReturnLine(
    val orderLine: PurchaseOrderLineEntity,
    val quantityMicros: Long,
    val inventoryUnitCostRial: Long,
    val supplierUnitCreditRial: Long,
    val inventoryValue: MoneyRial,
    val supplierCreditValue: MoneyRial,
    val reason: String,
    val lotId: Long?,
    val receiptLotAllocationId: Long?,
    val locationId: Long,
)
