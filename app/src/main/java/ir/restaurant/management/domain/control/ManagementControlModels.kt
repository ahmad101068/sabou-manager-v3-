package ir.restaurant.management.domain.control

import ir.restaurant.management.core.GlobalId

import ir.restaurant.management.domain.purchase.PurchaseOrderRecord
import ir.restaurant.management.domain.purchase.PurchaseOrderStatus
import kotlinx.coroutines.flow.Flow

enum class ProcurementExceptionKind {
    NOT_DISPATCHED,
    NOT_ACKNOWLEDGED,
    DELIVERY_OVERDUE,
    PARTIAL_RECEIPT_OVERDUE,
    ETA_CHANGED,
}

enum class ControlSeverity { CRITICAL, HIGH, MEDIUM, LOW }

data class ProcurementException(
    val purchaseOrderId: Long,
    val orderNo: String,
    val supplierName: String,
    val kind: ProcurementExceptionKind,
    val severity: ControlSeverity,
    val dueEpochDay: Long,
    val ageDays: Long,
    val title: String,
)

object ProcurementExceptionCalculator {
    fun scan(
        orders: List<PurchaseOrderRecord>,
        todayEpochDay: Long,
        dispatchGraceDays: Int = 1,
        acknowledgementGraceDays: Int = 2,
    ): List<ProcurementException> {
        require(dispatchGraceDays in 0..30 && acknowledgementGraceDays in 0..30)
        return orders.flatMap { order ->
            if (order.status !in setOf(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PARTIALLY_RECEIVED)) return@flatMap emptyList()
            buildList {
                if (order.sentAtEpochMillis == null && todayEpochDay > order.orderEpochDay + dispatchGraceDays) {
                    add(order.exception(ProcurementExceptionKind.NOT_DISPATCHED, ControlSeverity.HIGH, order.orderEpochDay + dispatchGraceDays, todayEpochDay, "سفارش ارسال نشده است"))
                }
                if (order.sentAtEpochMillis != null && order.acknowledgedAtEpochMillis == null && todayEpochDay > order.orderEpochDay + acknowledgementGraceDays) {
                    add(order.exception(ProcurementExceptionKind.NOT_ACKNOWLEDGED, ControlSeverity.HIGH, order.orderEpochDay + acknowledgementGraceDays, todayEpochDay, "تأمین‌کننده سفارش را تأیید نکرده است"))
                }
                val committedDate = order.confirmedExpectedEpochDay ?: order.expectedEpochDay
                if (todayEpochDay > committedDate) {
                    val kind = if (order.status == PurchaseOrderStatus.PARTIALLY_RECEIVED) ProcurementExceptionKind.PARTIAL_RECEIPT_OVERDUE else ProcurementExceptionKind.DELIVERY_OVERDUE
                    add(order.exception(kind, ControlSeverity.CRITICAL, committedDate, todayEpochDay, if (kind == ProcurementExceptionKind.PARTIAL_RECEIPT_OVERDUE) "مانده سفارش از موعد تحویل گذشته است" else "تحویل سفارش عقب افتاده است"))
                }
                if (order.confirmedExpectedEpochDay != null && order.confirmedExpectedEpochDay != order.expectedEpochDay) {
                    add(order.exception(ProcurementExceptionKind.ETA_CHANGED, ControlSeverity.MEDIUM, order.confirmedExpectedEpochDay, todayEpochDay, "موعد تحویل توسط تأمین‌کننده تغییر کرده است"))
                }
            }
        }.sortedWith(compareBy<ProcurementException> { it.severity.ordinal }.thenByDescending { it.ageDays })
    }

    private fun PurchaseOrderRecord.exception(
        kind: ProcurementExceptionKind,
        severity: ControlSeverity,
        due: Long,
        today: Long,
        title: String,
    ) = ProcurementException(id, orderNo, supplierName, kind, severity, due, (today - due).coerceAtLeast(0), title)
}

data class FoodCostSummary(
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val salesRial: Long,
    val theoreticalCostRial: Long,
    val actualCostRial: Long?,
    val wasteCostRial: Long,
    val actualDataQuality: ActualCostDataQuality = if (actualCostRial == null) ActualCostDataQuality.ACTUAL_NOT_AVAILABLE else ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
) {
    val varianceRial: Long? get() = actualCostRial?.let { Math.subtractExact(it, theoreticalCostRial) }
    val theoreticalBasisPoints: Long get() = ratio(theoreticalCostRial, salesRial)
    val actualBasisPoints: Long? get() = actualCostRial?.let { ratio(it, salesRial) }

    init {
        require(fromEpochDay <= toEpochDay)
        require(listOf(salesRial, theoreticalCostRial, wasteCostRial).all { it >= 0 })
        require(actualCostRial == null || actualCostRial >= 0)
    }

    private fun ratio(value: Long, denominator: Long): Long = if (denominator <= 0) 0 else ((value.toBigInteger() * 10_000.toBigInteger()) / denominator.toBigInteger()).toLong()
}

data class StorageLocationRecord(
    val id: Long,
    val name: String,
    val kind: String,
    val isActive: Boolean,
    val code: String = "",
    val type: ir.restaurant.management.domain.inventory.InventoryLocationType =
        ir.restaurant.management.domain.inventory.InventoryLocationType.fromStoredValue(kind),
)
data class InventoryLotRecord(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val locationId: Long,
    val locationName: String,
    val lotCode: String,
    val receivedEpochDay: Long,
    val expiryEpochDay: Long?,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val barcode: String?,
    val supplierLotNumber: String? = null,
    val productionEpochDay: Long? = null,
    val initialQuantityMicros: Long = quantityMicros,
    val status: ir.restaurant.management.domain.inventory.InventoryLotStatus =
        ir.restaurant.management.domain.inventory.InventoryLotStatus.ACTIVE,
    val sourceReceiptId: Long? = null,
    val globalId: String = "",
    val correlationId: String = "",
) {
    fun expiryRisk(todayEpochDay: Long): ControlSeverity? = when {
        expiryEpochDay == null -> null
        expiryEpochDay < todayEpochDay -> ControlSeverity.CRITICAL
        expiryEpochDay <= todayEpochDay + 3 -> ControlSeverity.HIGH
        expiryEpochDay <= todayEpochDay + 7 -> ControlSeverity.MEDIUM
        else -> null
    }
}

data class LotRegistrationDraft(
    val itemId: Long,
    val locationId: Long,
    val lotCode: String,
    val receivedEpochDay: Long,
    val expiryEpochDay: Long?,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val barcode: String? = null,
    val supplierLotNumber: String? = null,
    val productionEpochDay: Long? = null,
    val sourceReceiptId: Long? = null,
    val correlationId: String = "inventory:lot:${GlobalId.new().value}",
) {
    fun validated(): LotRegistrationDraft {
        require(itemId > 0 && locationId > 0)
        require(lotCode.trim().length in 1..80) { "شماره بچ/سری ساخت الزامی است." }
        require(quantityMicros > 0 && unitCostRial >= 0)
        require(expiryEpochDay == null || expiryEpochDay >= receivedEpochDay) { "تاریخ انقضا نمی‌تواند قبل از تاریخ ورود باشد." }
        require(barcode == null || barcode.trim().length in 4..80) { "بارکد باید بین ۴ تا ۸۰ نویسه باشد." }
        return copy(lotCode = lotCode.trim(), barcode = barcode?.trim()?.ifBlank { null })
    }
}

data class LotTransferDraft(
    val sourceLotId: Long,
    val destinationLocationId: Long,
    val quantityMicros: Long,
    val transferEpochDay: Long,
    val note: String = "",
    val commandId: String = GlobalId.new().value,
) {
    fun validated(): LotTransferDraft {
        require(sourceLotId > 0 && destinationLocationId > 0 && quantityMicros > 0)
        require(note.trim().length <= 300)
        val normalizedCommandId = GlobalId.parse(commandId).value
        return copy(note = note.trim(), commandId = normalizedCommandId)
    }
}

enum class BudgetCategory { PURCHASE, LABOR, WASTE, OTHER }
data class BudgetRecord(
    val id: Long,
    val name: String,
    val category: BudgetCategory,
    val costCenter: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val limitRial: Long,
    val actualRial: Long,
    val committedRial: Long = 0,
) {
    val remainingRial: Long get() = limitRial - actualRial - committedRial
    val utilizationBasisPoints: Long get() = if (limitRial == 0L) 0 else ((actualRial.toBigInteger() * 10_000.toBigInteger()) / limitRial.toBigInteger()).toLong()
}

enum class AccountingPeriodStatus(val storedValue: String, val title: String) {
    CLOSED("CLOSED", "بسته"),
    REOPENED("REOPENED", "بازگشایی‌شده"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", "وضعیت قدیمی ناشناخته");

    companion object {
        fun fromStoredValue(value: String): AccountingPeriodStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

enum class CashReconciliationStatus(val storedValue: String, val title: String) {
    MATCHED("MATCHED", "تطبیق کامل"),
    VARIANCE("VARIANCE", "دارای مغایرت"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", "وضعیت قدیمی ناشناخته");

    companion object {
        fun fromStoredValue(value: String): CashReconciliationStatus =
            entries.firstOrNull { it.storedValue == value } ?: LEGACY_UNKNOWN
    }
}

data class AccountingPeriodRecord(val id: Long, val fromEpochDay: Long, val toEpochDay: Long, val status: AccountingPeriodStatus, val reason: String, val closedBy: String, val reopenedBy: String?)
data class AccountingPeriodDraft(val fromEpochDay: Long, val toEpochDay: Long, val reason: String) {
    fun validated(): AccountingPeriodDraft { require(fromEpochDay > 0 && toEpochDay >= fromEpochDay); require(reason.trim().length in 5..300) { "دلیل بستن دوره باید بین ۵ تا ۳۰۰ نویسه باشد." }; return copy(reason=reason.trim()) }
}
data class CashReconciliationDraft(val businessEpochDay: Long,val actualCashRial: Long,val actualCardRial: Long,val actualTransferRial: Long,val note: String="", val branchId: Long? = null) {
    fun validated(): CashReconciliationDraft { require(businessEpochDay>0); require(branchId == null || branchId > 0); require(listOf(actualCashRial,actualCardRial,actualTransferRial).all{it>=0}); require(note.trim().length<=300); return copy(note=note.trim()) }
}
data class CashReconciliationRecord(val id:Long,val businessEpochDay:Long,val revisionNo:Int,val expectedTotalRial:Long,val actualTotalRial:Long,val varianceRial:Long,val status:CashReconciliationStatus,val reconciledBy:String, val branchId: Long? = null)
data class KpiTraceRecord(val id:Long,val entryNo:String,val entryEpochDay:Long,val description:String,val sourceType:String,val sourceId:Long,val debitRial:Long,val creditRial:Long)

data class BudgetDraft(
    val name: String,
    val category: BudgetCategory,
    val costCenter: String,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val limitRial: Long,
) {
    fun validated(): BudgetDraft {
        require(name.trim().length in 2..80) { "نام بودجه معتبر نیست." }
        require(costCenter.trim().length in 2..80) { "مرکز هزینه معتبر نیست." }
        require(fromEpochDay <= toEpochDay && limitRial > 0)
        return copy(name = name.trim(), costCenter = costCenter.trim())
    }
}

data class LaborPolicy(
    val maxWeeklyMinutes: Int = 2_640,
    val maxShiftMinutes: Int = 720,
    val minimumRestMinutes: Int = 660,
    val breakRequiredAfterMinutes: Int = 360,
    val minimumBreakMinutes: Int = 30,
) {
    fun validated(): LaborPolicy {
        require(maxWeeklyMinutes in 60..10_080)
        require(maxShiftMinutes in 60..1_440)
        require(minimumRestMinutes in 0..1_440)
        require(breakRequiredAfterMinutes in 60..1_440)
        require(minimumBreakMinutes in 0..240)
        return this
    }
}

data class LaborShiftInput(val shiftId: Long, val employeeId: Long, val employeeName: String, val epochDay: Long, val startMinute: Int, val endMinute: Int, val breakMinutes: Int = 0)
data class LaborComplianceAlert(val shiftId: Long, val employeeId: Long, val employeeName: String, val severity: ControlSeverity, val message: String)
data class EmployeeAvailabilityRecord(val id: Long, val employeeId: Long, val employeeName: String, val dayOfWeek: Int, val fromMinute: Int, val toMinute: Int, val isAvailable: Boolean)
data class AvailabilityDraft(val employeeId: Long, val dayOfWeek: Int, val fromMinute: Int, val toMinute: Int, val isAvailable: Boolean = true) {
    fun validated(): AvailabilityDraft {
        require(employeeId > 0 && dayOfWeek in 1..7)
        require(fromMinute in 0..1439 && toMinute in 1..1440 && fromMinute < toMinute)
        return this
    }
}
data class ShiftSwapRecord(val id: Long, val shiftId: Long, val requesterEmployeeId: Long, val requesterName: String, val targetEmployeeId: Long?, val targetName: String?, val status: String, val note: String)
data class ShiftSwapDraft(val shiftId: Long, val requesterEmployeeId: Long, val targetEmployeeId: Long?, val note: String) {
    fun validated(): ShiftSwapDraft {
        require(shiftId > 0 && requesterEmployeeId > 0)
        require(targetEmployeeId == null || targetEmployeeId > 0)
        require(targetEmployeeId != requesterEmployeeId)
        require(note.trim().length in 3..300)
        return copy(note = note.trim())
    }
}

object LaborComplianceCalculator {
    fun evaluate(shifts: List<LaborShiftInput>, policy: LaborPolicy): List<LaborComplianceAlert> {
        policy.validated()
        val result = mutableListOf<LaborComplianceAlert>()
        shifts.forEach { shift ->
            val duration = shift.endMinute - shift.startMinute
            if (duration > policy.maxShiftMinutes) result += shift.alert(ControlSeverity.HIGH, "طول شیفت از سقف سیاست بیشتر است")
            if (duration > policy.breakRequiredAfterMinutes && shift.breakMinutes < policy.minimumBreakMinutes) result += shift.alert(ControlSeverity.HIGH, "استراحت ثبت‌شده برای این شیفت کافی نیست")
        }
        shifts.groupBy { it.employeeId }.forEach { (_, employeeShifts) ->
            employeeShifts.groupBy { it.epochDay / 7L }.forEach { (_, week) ->
                if (week.sumOf { it.endMinute - it.startMinute } > policy.maxWeeklyMinutes) {
                    week.firstOrNull()?.let { result += it.alert(ControlSeverity.HIGH, "ساعات برنامه‌ریزی‌شده هفتگی از سقف عبور کرده است") }
                }
            }
            employeeShifts.sortedWith(compareBy<LaborShiftInput> { it.epochDay }.thenBy { it.startMinute }).zipWithNext().forEach { (first, second) ->
                val rest = ((second.epochDay - first.epochDay) * 1_440 + second.startMinute - first.endMinute).toInt()
                if (rest < policy.minimumRestMinutes) result += second.alert(ControlSeverity.MEDIUM, "فاصله استراحت بین دو شیفت کافی نیست")
            }
        }
        return result.sortedBy { it.severity.ordinal }
    }

    private fun LaborShiftInput.alert(severity: ControlSeverity, message: String) = LaborComplianceAlert(shiftId, employeeId, employeeName, severity, message)
}

data class ManagementControlSnapshot(
    val procurementExceptions: List<ProcurementException> = emptyList(),
    val foodCost: FoodCostSummary,
    val locations: List<StorageLocationRecord> = emptyList(),
    val lots: List<InventoryLotRecord> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val laborAlerts: List<LaborComplianceAlert> = emptyList(),
    val availabilities: List<EmployeeAvailabilityRecord> = emptyList(),
    val shiftSwaps: List<ShiftSwapRecord> = emptyList(),
    val plannedShifts: List<LaborShiftInput> = emptyList(),
    val accountingPeriods: List<AccountingPeriodRecord> = emptyList(),
    val cashReconciliations: List<CashReconciliationRecord> = emptyList(),
    val kpiTrace: List<KpiTraceRecord> = emptyList(),
)

interface ManagementControlRepository {
    fun observeSnapshot(fromEpochDay: Long, toEpochDay: Long): Flow<ManagementControlSnapshot>
    suspend fun recordPurchaseOrderFollowUp(purchaseOrderId: Long, note: String)
    suspend fun createLocation(name: String, kind: String): Long
    suspend fun registerLot(draft: LotRegistrationDraft): Long
    suspend fun transferLot(draft: LotTransferDraft): Long
    suspend fun saveBudget(id: Long?, draft: BudgetDraft): Long
    suspend fun recordBudgetSpend(budgetId: Long, amountRial: Long, epochDay: Long, reference: String)
    suspend fun saveLaborPolicy(policy: LaborPolicy)
    suspend fun saveAvailability(draft: AvailabilityDraft)
    suspend fun requestShiftSwap(draft: ShiftSwapDraft): Long
    suspend fun reviewShiftSwap(requestId: Long, approve: Boolean)
    suspend fun recordWorkBreak(shiftId: Long, startMinute: Int, endMinute: Int)
    suspend fun closeAccountingPeriod(draft: AccountingPeriodDraft): Long
    suspend fun reopenAccountingPeriod(id: Long)
    suspend fun reconcileSalesCash(draft: CashReconciliationDraft): Long
}
