package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.common.asViolation
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.CreateInventoryTransferLine
import ir.restaurant.management.domain.inventory.InventoryLocationCode
import ir.restaurant.management.domain.inventory.InventoryLocationDraft
import ir.restaurant.management.domain.inventory.InventoryLocationType
import ir.restaurant.management.domain.inventory.InventoryRepository
import ir.restaurant.management.domain.inventory.InventoryLotDraft
import ir.restaurant.management.domain.inventory.InventoryLotService
import ir.restaurant.management.domain.inventory.InventoryLotStatus
import ir.restaurant.management.domain.inventory.InventoryTransferService
import ir.restaurant.management.domain.inventory.RegisterInventoryLotCommand

import androidx.room.withTransaction
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BudgetSpendEntryEntity
import ir.restaurant.management.data.db.AccountingPeriodLockEntity
import ir.restaurant.management.data.db.SalesCashReconciliationEntity
import ir.restaurant.management.data.db.EmployeeAvailabilityEntity
import ir.restaurant.management.data.db.LaborPolicyEntity
import ir.restaurant.management.data.db.OperatingBudgetEntity
import ir.restaurant.management.data.db.PurchaseOrderFollowUpEntity
import ir.restaurant.management.data.db.ShiftSwapRequestEntity
import ir.restaurant.management.data.db.WorkBreakEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.security.SensitiveActionGate
import ir.restaurant.management.domain.operations.SensitiveAction
import ir.restaurant.management.domain.operations.SensitiveActionContext
import ir.restaurant.management.domain.control.BudgetCategory
import ir.restaurant.management.domain.control.BudgetDraft
import ir.restaurant.management.domain.control.BudgetRecord
import ir.restaurant.management.domain.control.AccountingPeriodDraft
import ir.restaurant.management.domain.control.AccountingPeriodRecord
import ir.restaurant.management.domain.control.AccountingPeriodStatus
import ir.restaurant.management.domain.control.CashReconciliationDraft
import ir.restaurant.management.domain.control.CashReconciliationRecord
import ir.restaurant.management.domain.control.CashReconciliationStatus
import ir.restaurant.management.domain.control.KpiTraceRecord
import ir.restaurant.management.domain.control.AvailabilityDraft
import ir.restaurant.management.domain.control.EmployeeAvailabilityRecord
import ir.restaurant.management.domain.control.FoodCostSummary
import ir.restaurant.management.domain.control.InventoryLotRecord
import ir.restaurant.management.domain.control.LaborComplianceCalculator
import ir.restaurant.management.domain.control.LaborPolicy
import ir.restaurant.management.domain.control.LaborShiftInput
import ir.restaurant.management.domain.control.LotRegistrationDraft
import ir.restaurant.management.domain.control.LotTransferDraft
import ir.restaurant.management.domain.control.ShiftSwapDraft
import ir.restaurant.management.domain.control.ShiftSwapRecord
import ir.restaurant.management.domain.control.ManagementControlRepository
import ir.restaurant.management.domain.control.ManagementControlSnapshot
import ir.restaurant.management.domain.control.ProcurementExceptionCalculator
import ir.restaurant.management.domain.control.StorageLocationRecord
import ir.restaurant.management.domain.purchase.ProcurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private data class InventoryControlSignals(
    val locations: List<StorageLocationRecord>,
    val lots: List<InventoryLotRecord>,
)

private data class FinancialControlSignals(
    val foodCost: FoodCostSummary,
    val budgets: List<BudgetRecord>,
)

private data class LaborControlSignals(
    val alerts: List<ir.restaurant.management.domain.control.LaborComplianceAlert>,
    val availabilities: List<EmployeeAvailabilityRecord>,
    val swaps: List<ShiftSwapRecord>,
    val shifts: List<LaborShiftInput>,
)

private data class GovernanceSignals(val periods:List<AccountingPeriodRecord>,val reconciliations:List<CashReconciliationRecord>,val trace:List<KpiTraceRecord>)

class LocalManagementControlRepository(
    private val database: AppDatabase,
    private val procurementRepository: ProcurementRepository,
    private val authorizer: SessionAuthorizer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val todayEpochDay: () -> Long = ::currentLocalEpochDay,
    private val syncRecorder: SyncRecorder? = null,
    private val sensitiveActionGate: SensitiveActionGate = SensitiveActionGate(),
    private val inventoryRepository: InventoryRepository = LocalInventoryRepository(
        database = database,
        authorizer = authorizer,
        clock = clock,
        syncRecorder = syncRecorder,
    ),
    private val inventoryLotService: InventoryLotService = LocalInventoryLotService(
        database = database,
        authorizer = authorizer,
        clock = clock,
        syncRecorder = syncRecorder,
    ),
    private val inventoryTransferService: InventoryTransferService = LocalInventoryTransferService(
        database = database,
        authorizer = authorizer,
        clock = clock,
        syncRecorder = syncRecorder,
    ),
) : ManagementControlRepository {
    private val dao get() = database.managementControlDao()
    private val auditWriter = LocalAuditEventWriter(database)
    private val sequenceAllocator = LocalDocumentNumberAllocator(database, clock)

    override fun observeSnapshot(fromEpochDay: Long, toEpochDay: Long): Flow<ManagementControlSnapshot> {
        require(fromEpochDay <= toEpochDay)
        val inventory = combine(dao.observeLocations(), database.inventoryLotDao().observeActiveStock()) { locations, lots ->
            InventoryControlSignals(
                locations.map {
                    StorageLocationRecord(
                        id = it.id,
                        name = it.name,
                        kind = it.kind,
                        isActive = it.isActive,
                        code = it.code,
                        type = InventoryLocationType.fromStoredValue(it.kind),
                    )
                },
                lots.map {
                    InventoryLotRecord(
                        id = it.id,
                        itemId = it.itemId,
                        itemName = it.itemName,
                        locationId = it.locationId,
                        locationName = it.locationName,
                        lotCode = it.lotCode,
                        receivedEpochDay = it.receivedEpochDay,
                        expiryEpochDay = it.expiryEpochDay,
                        quantityMicros = it.quantityMicros,
                        unitCostRial = it.unitCostRial,
                        barcode = it.barcode,
                        supplierLotNumber = it.supplierLotNumber,
                        productionEpochDay = it.productionEpochDay,
                        initialQuantityMicros = it.initialQuantityMicros,
                        status = InventoryLotStatus.fromStoredValue(it.status),
                        sourceReceiptId = it.sourceReceiptId,
                        globalId = it.globalId,
                        correlationId = it.correlationId,
                    )
                },
            )
        }
        val financial = combine(dao.observeFoodCost(fromEpochDay, toEpochDay), dao.observeBudgets()) { food, budgets ->
            val actual = if (food.actualEvidenceCount > 0) {
                SignedLongMath.subtract(
                    SignedLongMath.add(SignedLongMath.add(food.standardSalesLedgerCostRial, food.wasteCostRial), food.negativeAdjustmentCostRial),
                    food.positiveAdjustmentCostRial,
                )
            } else null
            FinancialControlSignals(
                FoodCostSummary(
                    fromEpochDay, toEpochDay, food.salesRial, food.theoreticalCostRial, actual, food.wasteCostRial,
                    if (actual == null) ir.restaurant.management.domain.control.ActualCostDataQuality.ACTUAL_NOT_AVAILABLE else ir.restaurant.management.domain.control.ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
                ),
                budgets.map {
                    BudgetRecord(it.id, it.name, BudgetCategory.valueOf(it.category), it.costCenter, it.fromEpochDay, it.toEpochDay, it.limitRial, SignedLongMath.add(it.manualSpendRial, it.automaticSpendRial), it.committedRial)
                },
            )
        }
        val shiftInputs = dao.observeShiftBreaks().combine(dao.observeLaborPolicy()) { shifts, entity ->
            val policy = entity?.let { LaborPolicy(it.maxWeeklyMinutes, it.maxShiftMinutes, it.minimumRestMinutes, it.breakRequiredAfterMinutes, it.minimumBreakMinutes) } ?: LaborPolicy()
            val inputs = shifts.map { LaborShiftInput(it.shiftId, it.employeeId, it.employeeName, it.epochDay, it.startMinute, it.endMinute, it.breakMinutes) }
            LaborComplianceCalculator.evaluate(inputs, policy) to inputs
        }
        val laborRoster = combine(dao.observeAvailabilities(), dao.observeShiftSwaps()) { availability, swaps -> availability to swaps }
        val labor = combine(shiftInputs, laborRoster) { compliance, roster ->
            LaborControlSignals(
                compliance.first,
                roster.first.map { EmployeeAvailabilityRecord(it.id, it.employeeId, it.employeeName, it.dayOfWeek, it.fromMinute, it.toMinute, it.isAvailable) },
                roster.second.map { ShiftSwapRecord(it.id, it.shiftId, it.requesterEmployeeId, it.requesterName, it.targetEmployeeId, it.targetName, it.status, it.note) },
                compliance.second,
            )
        }
        val governance = combine(dao.observeAccountingPeriodLocks(),dao.observeCashReconciliations(),dao.observeKpiTrace(fromEpochDay,toEpochDay)) { periods,cash,trace ->
            GovernanceSignals(
                periods.map { AccountingPeriodRecord(it.id,it.fromEpochDay,it.toEpochDay,AccountingPeriodStatus.fromStoredValue(it.status),it.reason,it.closedBy,it.reopenedBy) },
                cash.map { val expected=SignedLongMath.add(SignedLongMath.add(it.expectedCashRial,it.expectedCardRial),it.expectedTransferRial); val actual=SignedLongMath.add(SignedLongMath.add(it.actualCashRial,it.actualCardRial),it.actualTransferRial); CashReconciliationRecord(it.id,it.businessEpochDay,it.revisionNo,expected,actual,SignedLongMath.subtract(actual,expected),CashReconciliationStatus.fromStoredValue(it.status),it.reconciledBy,it.branchId) },
                trace.map { KpiTraceRecord(it.id,it.entryNo,it.entryEpochDay,it.description,it.sourceType,it.sourceId,it.debitRial,it.creditRial) },
            )
        }
        return combine(procurementRepository.overview, inventory, financial, labor, governance) { procurement, stock, finance, laborState, governanceState ->
            ManagementControlSnapshot(
                procurementExceptions = ProcurementExceptionCalculator.scan(procurement.orders, todayEpochDay()),
                foodCost = finance.foodCost,
                locations = stock.locations,
                lots = stock.lots,
                budgets = finance.budgets,
                laborAlerts = laborState.alerts,
                availabilities = laborState.availabilities,
                shiftSwaps = laborState.swaps,
                plannedShifts = laborState.shifts,
                accountingPeriods = governanceState.periods,
                cashReconciliations = governanceState.reconciliations,
                kpiTrace = governanceState.trace,
            )
        }
    }

    override suspend fun recordPurchaseOrderFollowUp(purchaseOrderId: Long, note: String) {
        authorizer.require(Permission.PURCHASES)
        val normalized = note.trim()
        require(normalized.length in 3..300) { "متن پیگیری باید بین ۳ تا ۳۰۰ نویسه باشد." }
        val order = dao.purchaseOrder(purchaseOrderId) ?: error("سفارش خرید پیدا نشد.")
        require(order.status in setOf("OPEN", "PARTIALLY_RECEIVED")) { "این سفارش باز نیست." }
        val now = clock()
        val id = dao.insertFollowUp(PurchaseOrderFollowUpEntity(purchaseOrderId = purchaseOrderId, note = normalized, actor = authorizer.actor(), createdAtEpochMillis = now))
        syncRecorder?.record("PURCHASE_ORDER_FOLLOW_UP", id, "CREATE", now)
    }

    override suspend fun createLocation(name: String, kind: String): Long {
        val normalizedType = InventoryLocationType.fromStoredValue(kind)
        return inventoryRepository.saveLocation(
            id = null,
            draft = InventoryLocationDraft(
                code = InventoryLocationCode.generated().value,
                name = name,
                type = normalizedType,
            ),
        )
    }

    override suspend fun registerLot(draft: LotRegistrationDraft): Long {
        val valid = draft.validated()
        val actor = authorizer.actorIdentity()
        return inventoryLotService.register(
            RegisterInventoryLotCommand(
                draft = InventoryLotDraft(
                    itemId = valid.itemId,
                    locationId = valid.locationId,
                    lotNumber = valid.lotCode,
                    supplierLotNumber = valid.supplierLotNumber,
                    receivedEpochDay = valid.receivedEpochDay,
                    productionEpochDay = valid.productionEpochDay,
                    expiryEpochDay = valid.expiryEpochDay,
                    quantityMicros = valid.quantityMicros,
                    unitCostRial = valid.unitCostRial,
                    barcode = valid.barcode,
                    sourceReceiptId = valid.sourceReceiptId,
                    correlationId = valid.correlationId,
                ),
                actorId = actor.id,
                reason = "ثبت لات از مسیر سازگار ManagementControl",
            ),
        )
    }

    override suspend fun transferLot(draft: LotTransferDraft): Long {
        val actor = authorizer.require(Permission.INVENTORY_TRANSFER_CREATE)
        val valid = draft.validated()
        val sourceLot = database.inventoryLotDao().byId(valid.sourceLotId)
            ?: throw ir.restaurant.management.domain.common.BusinessError.InvalidLot(
                valid.sourceLotId,
                "LOT_NOT_FOUND",
            ).asViolation()
        return inventoryTransferService.createAndComplete(
            CreateInventoryTransferCommand(
                sourceLocationId = sourceLot.locationId,
                destinationLocationId = valid.destinationLocationId,
                businessEpochDay = valid.transferEpochDay,
                lines = listOf(
                    CreateInventoryTransferLine(
                        itemId = sourceLot.itemId,
                        lotId = sourceLot.id,
                        requestedQuantityMicros = valid.quantityMicros,
                    ),
                ),
                notes = valid.note,
                actorId = actor.id,
                commandId = valid.commandId,
                correlationId = "stock_transfer:${valid.commandId}",
            ),
        ).id
    }

    override suspend fun saveBudget(id: Long?, draft: BudgetDraft): Long {
        authorizer.require(Permission.ACCOUNTING)
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            if (id == null) {
                dao.insertBudget(OperatingBudgetEntity(name = valid.name, category = valid.category.name, costCenter = valid.costCenter, fromEpochDay = valid.fromEpochDay, toEpochDay = valid.toEpochDay, limitRial = valid.limitRial, createdBy = authorizer.actor(), createdAtEpochMillis = now, updatedAtEpochMillis = now))
            } else {
                val old = dao.budget(id) ?: error("بودجه پیدا نشد.")
                check(dao.updateBudget(old.copy(name = valid.name, category = valid.category.name, costCenter = valid.costCenter, fromEpochDay = valid.fromEpochDay, toEpochDay = valid.toEpochDay, limitRial = valid.limitRial, updatedAtEpochMillis = now)) == 1)
                id
            }.also { syncRecorder?.record("OPERATING_BUDGET", it, if (id == null) "CREATE" else "UPDATE", now) }
        }
    }

    override suspend fun recordBudgetSpend(budgetId: Long, amountRial: Long, epochDay: Long, reference: String) {
        authorizer.require(Permission.ACCOUNTING)
        require(amountRial > 0 && reference.trim().length in 2..120)
        val budget = dao.budget(budgetId) ?: error("بودجه پیدا نشد.")
        require(epochDay in budget.fromEpochDay..budget.toEpochDay) { "تاریخ هزینه خارج از دوره بودجه است." }
        val now = clock()
        val id = dao.insertBudgetSpend(BudgetSpendEntryEntity(budgetId = budgetId, amountRial = amountRial, spendEpochDay = epochDay, reference = reference.trim(), actor = authorizer.actor(), createdAtEpochMillis = now))
        syncRecorder?.record("BUDGET_SPEND", id, "CREATE", now)
    }

    override suspend fun saveLaborPolicy(policy: LaborPolicy) {
        authorizer.require(Permission.PERSONNEL)
        val valid = policy.validated()
        val now = clock()
        dao.saveLaborPolicy(LaborPolicyEntity(maxWeeklyMinutes = valid.maxWeeklyMinutes, maxShiftMinutes = valid.maxShiftMinutes, minimumRestMinutes = valid.minimumRestMinutes, breakRequiredAfterMinutes = valid.breakRequiredAfterMinutes, minimumBreakMinutes = valid.minimumBreakMinutes, updatedBy = authorizer.actor(), updatedAtEpochMillis = now))
        syncRecorder?.record("LABOR_POLICY", 1, "UPDATE", now)
    }

    override suspend fun saveAvailability(draft: AvailabilityDraft) {
        authorizer.require(Permission.PERSONNEL)
        val valid = draft.validated()
        val employee = database.personnelDao().employeeById(valid.employeeId) ?: error("پرسنل پیدا نشد.")
        require(employee.status == "ACTIVE") { "پرسنل فعال نیست." }
        val now = clock()
        val old = dao.availability(valid.employeeId, valid.dayOfWeek)
        val id = dao.saveAvailability(EmployeeAvailabilityEntity(id = old?.id ?: 0, employeeId = valid.employeeId, dayOfWeek = valid.dayOfWeek, fromMinute = valid.fromMinute, toMinute = valid.toMinute, isAvailable = valid.isAvailable, updatedBy = authorizer.actor(), updatedAtEpochMillis = now))
        syncRecorder?.record("EMPLOYEE_AVAILABILITY", if (old == null) id else old.id, "UPSERT", now)
    }

    override suspend fun requestShiftSwap(draft: ShiftSwapDraft): Long {
        authorizer.require(Permission.PERSONNEL)
        val valid = draft.validated()
        val shift = dao.plannedShift(valid.shiftId) ?: error("شیفت پیدا نشد.")
        require(shift.employeeId == valid.requesterEmployeeId) { "درخواست‌کننده مالک این شیفت نیست." }
        valid.targetEmployeeId?.let { require(database.personnelDao().employeeById(it)?.status == "ACTIVE") { "پرسنل جایگزین فعال پیدا نشد." } }
        val now = clock()
        return dao.insertShiftSwap(ShiftSwapRequestEntity(shiftId = valid.shiftId, requesterEmployeeId = valid.requesterEmployeeId, targetEmployeeId = valid.targetEmployeeId, status = "PENDING", note = valid.note, reviewedBy = null, createdAtEpochMillis = now, reviewedAtEpochMillis = null)).also {
            syncRecorder?.record("SHIFT_SWAP", it, "REQUEST", now)
        }
    }

    override suspend fun reviewShiftSwap(requestId: Long, approve: Boolean) {
        authorizer.require(Permission.AUDIT)
        val now = clock()
        database.withTransaction {
            val request = dao.shiftSwap(requestId) ?: error("درخواست جابه‌جایی پیدا نشد.")
            require(request.status == "PENDING") { "این درخواست قبلاً بررسی شده است." }
            if (approve) {
                val targetId = request.targetEmployeeId ?: error("برای تأیید، پرسنل جایگزین باید مشخص باشد.")
                val target = database.personnelDao().employeeById(targetId) ?: error("پرسنل جایگزین پیدا نشد.")
                require(target.status == "ACTIVE") { "پرسنل جایگزین فعال نیست." }
                check(dao.reassignShift(request.shiftId, target.id, target.name) == 1) { "انتقال شیفت انجام نشد." }
            }
            check(dao.reviewShiftSwap(request.id, if (approve) "APPROVED" else "REJECTED", authorizer.actor(), now) == 1)
            syncRecorder?.record("SHIFT_SWAP", request.id, if (approve) "APPROVE" else "REJECT", now)
        }
    }

    override suspend fun recordWorkBreak(shiftId: Long, startMinute: Int, endMinute: Int) {
        authorizer.require(Permission.PERSONNEL)
        val shift = dao.plannedShift(shiftId) ?: error("شیفت پیدا نشد.")
        require(startMinute in shift.startMinute until shift.endMinute && endMinute in (startMinute + 1)..shift.endMinute) { "بازه استراحت باید داخل شیفت باشد." }
        require(dao.workBreaks(shiftId).none { startMinute < it.endMinute && endMinute > it.startMinute }) { "بازه استراحت با رکورد قبلی هم‌پوشانی دارد." }
        val now = clock()
        val id = dao.insertWorkBreak(WorkBreakEntity(shiftId = shiftId, startMinute = startMinute, endMinute = endMinute, recordedBy = authorizer.actor(), createdAtEpochMillis = now))
        syncRecorder?.record("WORK_BREAK", id, "CREATE", now)
    }

    override suspend fun closeAccountingPeriod(draft: AccountingPeriodDraft): Long {
        authorizer.require(Permission.ACCOUNTING)
        val valid = draft.validated()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.CLOSE_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", "${valid.fromEpochDay}:${valid.toEpochDay}"))
        val now = clock()
        val actor = authorizer.actor()
        return database.withTransaction {
            require(!dao.accountingPeriodOverlaps(valid.fromEpochDay, valid.toEpochDay)) {
                "این بازه با دوره مالی بسته دیگری هم‌پوشانی دارد."
            }
            val id = dao.insertAccountingPeriodLock(
                AccountingPeriodLockEntity(
                    fromEpochDay = valid.fromEpochDay,
                    toEpochDay = valid.toEpochDay,
                    reason = valid.reason,
                    closedBy = actor,
                    closedAtEpochMillis = now,
                ),
            )
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "CLOSE",
                entityType = "ACCOUNTING_PERIOD",
                entityId = id,
                description = "بستن دوره مالی ${valid.fromEpochDay} تا ${valid.toEpochDay}: ${valid.reason}",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.toEpochDay,
                reason = valid.reason,
                afterSnapshot = "from=${valid.fromEpochDay};to=${valid.toEpochDay};status=CLOSED",
                correlationId = "accounting_period:$id:CLOSE:$now",
            )
            syncRecorder?.record("ACCOUNTING_PERIOD", id, "CLOSE", now, recordAudit = false)
            id
        }
    }

    override suspend fun reopenAccountingPeriod(id: Long) {
        authorizer.requireOwner()
        sensitiveActionGate.requireAndConsume(authorizer.currentUserId(), SensitiveAction.REOPEN_ACCOUNTING_PERIOD, SensitiveActionContext.resource("ACCOUNTING_PERIOD", id))
        val now = clock()
        val actor = authorizer.actor()
        database.withTransaction {
            val period = dao.accountingPeriodLock(id) ?: error("دوره مالی پیدا نشد.")
            require(period.status == "CLOSED") { "این دوره مالی بسته نیست." }
            check(dao.reopenAccountingPeriod(id, actor, now) == 1) { "بازگشایی دوره مالی انجام نشد." }
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "REOPEN",
                entityType = "ACCOUNTING_PERIOD",
                entityId = id,
                description = "بازگشایی کنترل‌شده دوره مالی: ${period.reason}",
                occurredAtEpochMillis = now,
                businessEpochDay = period.toEpochDay,
                reason = period.reason,
                beforeSnapshot = "status=CLOSED",
                afterSnapshot = "status=REOPENED;reopenedBy=$actor",
                correlationId = "accounting_period:$id:REOPEN:$now",
            )
            syncRecorder?.record("ACCOUNTING_PERIOD", id, "REOPEN", now, recordAudit = false)
        }
    }

    override suspend fun reconcileSalesCash(draft: CashReconciliationDraft): Long {
        authorizer.require(Permission.SALES)
        val valid = draft.validated()
        val now = clock()
        val actor = authorizer.actor()
        return database.withTransaction {
            val dailySalesDao = database.dailySalesDao()
            val aggregateClosure = if (valid.branchId != null) {
                CanonicalBranchResolver(database).requireActive(valid.branchId)
                dailySalesDao.dayClosure(valid.branchId, valid.businessEpochDay)?.takeIf { it.status == "CLOSED" }
            } else {
                val aggregateClosureCount = dailySalesDao.closedDayClosureCountAnyBranch(valid.businessEpochDay)
                require(aggregateClosureCount <= 1) {
                    "برای این روز بیش از یک شعبه بسته شده است؛ شعبه تطبیق صندوق باید صریحاً انتخاب شود."
                }
                if (aggregateClosureCount == 1) dailySalesDao.dayClosureAnyBranch(valid.businessEpochDay)?.takeIf { it.status == "CLOSED" } else null
            }
            val invoiceClosure = database.salesDao().salesDayClosure(valid.businessEpochDay)?.takeIf { it.status == "CLOSED" }
            require(aggregateClosure != null || invoiceClosure != null) { "ابتدا روز فروش را در ثبت فاکتور یا ثبت تجمیعی فروش ببندید." }
            require(!(aggregateClosure != null && invoiceClosure != null)) { "برای یک روز دو سند بستن فروش یافت شد؛ ابتدا یکپارچگی روز را بررسی کنید." }
            val expectedCash = invoiceClosure?.cashRial ?: aggregateClosure!!.cashRial
            val expectedCard = invoiceClosure?.cardRial ?: aggregateClosure!!.cardRial
            val expectedTransfer = invoiceClosure?.transferRial ?: aggregateClosure!!.transferRial
            val expected = SignedLongMath.add(SignedLongMath.add(expectedCash, expectedCard), expectedTransfer)
            val actual = SignedLongMath.add(SignedLongMath.add(valid.actualCashRial, valid.actualCardRial), valid.actualTransferRial)
            val variance = SignedLongMath.subtract(actual, expected)
            val resolvedBranchId = aggregateClosure?.let { closure ->
                database.dailySalesDao().summary(closure.summaryId)?.branchId
                    ?: error("فروش مبنای تطبیق صندوق پیدا نشد.")
            }
            val id = dao.insertCashReconciliation(
                SalesCashReconciliationEntity(
                    businessEpochDay = valid.businessEpochDay,
                    branchId = resolvedBranchId,
                    revisionNo = sequenceAllocator.nextRaw("SALES_CASH_REVISION:${valid.businessEpochDay}").toInt(),
                    expectedCashRial = expectedCash,
                    expectedCardRial = expectedCard,
                    expectedTransferRial = expectedTransfer,
                    actualCashRial = valid.actualCashRial,
                    actualCardRial = valid.actualCardRial,
                    actualTransferRial = valid.actualTransferRial,
                    status = if (variance == 0L) "MATCHED" else "VARIANCE",
                    note = valid.note,
                    reconciledBy = actor,
                    createdAtEpochMillis = now,
                ),
            )
            auditWriter.appendAuthorized(
                authorizer = authorizer,
                action = "RECONCILE",
                entityType = "SALES_CASH",
                entityId = id,
                description = "تطبیق صندوق روز ${valid.businessEpochDay}؛ مغایرت $variance ریال",
                occurredAtEpochMillis = now,
                businessEpochDay = valid.businessEpochDay,
                reason = valid.note.ifBlank { "تطبیق پایان روز صندوق و ابزارهای پرداخت" },
                afterSnapshot = "source=${if (invoiceClosure != null) "INVOICE_SALES" else "AGGREGATE_SALES"};expectedRial=$expected;actualRial=$actual;varianceRial=$variance",
                correlationId = "sales_cash:$id:RECONCILE:$now",
            )
            syncRecorder?.record("SALES_CASH", id, "RECONCILE", now, recordAudit = false)
            if (variance != 0L) {
                OperationalAlertWriter(database, clock).append(
                    sourceType = LocalAlertRepository.RECONCILIATION_FAILURE,
                    sourceId = id,
                    title = "مغایرت تطبیق صندوق",
                    message = "تطبیق صندوق روز ${valid.businessEpochDay} با مغایرت $variance ریال ثبت شد.",
                    branchId = resolvedBranchId ?: 0L,
                )
            }
            id
        }
    }
}
