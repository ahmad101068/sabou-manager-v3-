package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.domain.control.DetectedIssue
import ir.restaurant.management.domain.control.ManagementIssueSeverity
import ir.restaurant.management.domain.control.ManagementIssueType
import ir.restaurant.management.domain.control.ManagementRule
import ir.restaurant.management.domain.control.ManagementRuleContext
import ir.restaurant.management.domain.control.ManagementDefaults
import kotlin.math.absoluteValue
import java.math.BigInteger



private fun signedBasisPoints(delta: Long, base: Long): Int {
    require(base > 0)
    val value = BigInteger.valueOf(delta).multiply(BigInteger.valueOf(10_000L)).divide(BigInteger.valueOf(base))
    return value.coerceIn(BigInteger.valueOf(Int.MIN_VALUE.toLong()), BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
}

private class ManagementThresholdCatalog(private val database: AppDatabase) {
    suspend fun basisPoints(branchId:Long,key:String,default:Int):Int {
        val rows=database.businessOperationsDao().thresholds(branchId)
        return rows.firstOrNull { it.branchScopeId==branchId && it.key==key }?.valueBasisPoints
            ?: rows.firstOrNull { it.branchScopeId==0L && it.key==key }?.valueBasisPoints
            ?: default
    }
    suspend fun rial(branchId:Long,key:String,default:Long):Long {
        val rows=database.businessOperationsDao().thresholds(branchId)
        return rows.firstOrNull { it.branchScopeId==branchId && it.key==key }?.valueRial
            ?: rows.firstOrNull { it.branchScopeId==0L && it.key==key }?.valueRial
            ?: default
    }
}

class OverdueReceivableRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        CanonicalReceivableReadModel(database).overdueLotsForRule(context.branchId, context.toEpochDay).forEach { lot ->
            val issueBranchId = lot.branchId ?: 0L // 0 = legacy/unassigned; never fabricate branch 1.
            val sourceType = if (lot.receivableId != null) "RECEIVABLE" else "LEGACY_RECEIVABLE"
            val sourceId = lot.receivableId ?: requireNotNull(lot.sourceLedgerId)
            add(
                DetectedIssue(
                    issueBranchId,
                    ManagementIssueType.OVERDUE_RECEIVABLE,
                    if(lot.outstandingRial>=50_000_000L) ManagementIssueSeverity.HIGH else ManagementIssueSeverity.MEDIUM,
                    "مطالبه سررسید گذشته",
                    "مانده ${lot.outstandingRial} ریال از طرف‌حساب ${lot.partyId}",
                    lot.outstandingRial,
                    context.toEpochDay,
                    sourceType,
                    sourceId,
                    lot.stableKey,
                ),
            )
        }
    }
}

class FoodCostVarianceRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        val row=database.businessOperationsDao().foodCostVariance(context.branchId,context.fromEpochDay,context.toEpochDay)
        val thresholdBasisPoints=ManagementThresholdCatalog(database).basisPoints(context.branchId,"FOOD_COST_VARIANCE_BP",ManagementDefaults.FOOD_COST_VARIANCE_BASIS_POINTS)
        if(row.theoreticalCostRial>0 && row.actualEvidenceCount>0) {
            val actual=SignedLongMath.subtract(SignedLongMath.add(SignedLongMath.add(row.standardSalesLedgerCostRial,row.wasteCostRial),row.negativeAdjustmentCostRial),row.positiveAdjustmentCostRial)
            val diff=actual-row.theoreticalCostRial
            val bp=signedBasisPoints(diff,row.theoreticalCostRial)
            if(bp.absoluteValue>=thresholdBasisPoints) add(DetectedIssue(context.branchId,ManagementIssueType.FOOD_COST_VARIANCE,if(bp.absoluteValue>=1000) ManagementIssueSeverity.HIGH else ManagementIssueSeverity.MEDIUM,"مغایرت Food Cost","برآورد واقعی دفتر=${actual}، نظری=${row.theoreticalCostRial}، انحراف=${bp/100.0}%",diff.absoluteValue,context.toEpochDay,"DAILY_SALES_PERIOD",context.toEpochDay,"${context.fromEpochDay}-${context.toEpochDay}"))
        }
    }
}

class CashVarianceRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        val thresholdRial=ManagementThresholdCatalog(database).rial(context.branchId,"CASH_VARIANCE_RIAL",ManagementDefaults.CASH_VARIANCE_RIAL)
        val variance=database.businessOperationsDao().cashVariance(context.fromEpochDay,context.toEpochDay)
        if(variance.absoluteValue>=thresholdRial) add(DetectedIssue(0L,ManagementIssueType.CASH_VARIANCE,ManagementIssueSeverity.HIGH,"مغایرت صندوق (شعبه نامشخص)","مغایرت صندوق ${variance} ریال؛ منبع تاریخی branchId ندارد.",variance.absoluteValue,context.toEpochDay,"UNSCOPED_CASH_RECONCILIATION",context.toEpochDay,"${context.fromEpochDay}-${context.toEpochDay}"))
    }
}

class LowStockRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        database.businessOperationsDao().lowStockRows().forEach { row ->
            add(DetectedIssue(0L,ManagementIssueType.LOW_STOCK,ManagementIssueSeverity.MEDIUM,"موجودی کم ${row.name} (شعبه نامشخص)","موجودی ${row.stockMicros} کمتر یا مساوی نقطه سفارش ${row.reorderPointMicros} است؛ منبع موجودی branchId ندارد.",null,context.toEpochDay,"UNSCOPED_INVENTORY_ITEM",row.id,"${context.fromEpochDay}-${context.toEpochDay}"))
        }
    }
}

class ManagementRuleEngine(
    private val workflow: ir.restaurant.management.domain.control.ManagementWorkflowService,
    private val rules: List<ManagementRule>,
) {
    suspend fun refresh(context: ManagementRuleContext): Int {
        val detected=rules.flatMap { it.evaluate(context) }
        return workflow.recordDetectedIssues(detected)
    }
}

class WasteSpikeRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        val thresholdBasisPoints=ManagementThresholdCatalog(database).basisPoints(context.branchId,"WASTE_SPIKE_BP",ManagementDefaults.WASTE_SPIKE_BASIS_POINTS)
        val days=context.toEpochDay-context.fromEpochDay+1
        val current=database.businessOperationsDao().branchWasteCost(context.branchId,context.fromEpochDay,context.toEpochDay)
        val previous=database.businessOperationsDao().branchWasteCost(context.branchId,context.fromEpochDay-days,context.fromEpochDay-1)
        if(previous>0) {
            val bp=signedBasisPoints(current-previous,previous)
            if(bp>=thresholdBasisPoints) add(DetectedIssue(context.branchId,ManagementIssueType.WASTE_SPIKE,ManagementIssueSeverity.HIGH,"افزایش ضایعات","هزینه ضایعات منتسب به شعبه در دوره $current ریال در برابر $previous ریال دوره قبل است.",current-previous,context.toEpochDay,"WASTE_PERIOD",context.toEpochDay,"${context.fromEpochDay}-${context.toEpochDay}"))
        }
    }
}

class PurchasePriceSpikeRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        CanonicalBranchResolver(database).requireExisting(context.branchId)
        val thresholdBasisPoints=ManagementThresholdCatalog(database).basisPoints(context.branchId,"PURCHASE_PRICE_INCREASE_BP",ManagementDefaults.PURCHASE_PRICE_INCREASE_BASIS_POINTS)
        database.businessOperationsDao().purchasePriceSpikeRows(context.branchId,context.fromEpochDay,context.toEpochDay).forEach { row ->
            if(row.average30DayRial>0) {
                val bp=signedBasisPoints(row.currentPriceRial-row.average30DayRial,row.average30DayRial)
                if(bp>=thresholdBasisPoints) add(DetectedIssue(context.branchId,ManagementIssueType.PURCHASE_PRICE_SPIKE,ManagementIssueSeverity.MEDIUM,"افزایش قیمت خرید ${row.itemName}","قیمت خرید ${row.itemName}: جاری=${row.currentPriceRial}، قبلی=${row.previousPriceRial}، میانگین ۳۰روز=${row.average30DayRial} ریال برای همین شعبه.",null,context.toEpochDay,"INVENTORY_ITEM",row.itemId,"${context.fromEpochDay}-${context.toEpochDay}"))
            }
        }
    }
}

class CardSettlementVarianceRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        val thresholdRial=ManagementThresholdCatalog(database).rial(context.branchId,"CARD_VARIANCE_RIAL",ManagementDefaults.CASH_VARIANCE_RIAL)
        val variance=database.businessOperationsDao().cardVariance(context.fromEpochDay,context.toEpochDay)
        if(variance.absoluteValue>=thresholdRial) add(DetectedIssue(0L,ManagementIssueType.CARD_SETTLEMENT_VARIANCE,ManagementIssueSeverity.HIGH,"مغایرت کارتخوان (شعبه نامشخص)","مغایرت کارتخوان $variance ریال است؛ منبع تاریخی branchId ندارد.",variance.absoluteValue,context.toEpochDay,"UNSCOPED_CARD_RECONCILIATION",context.toEpochDay,"${context.fromEpochDay}-${context.toEpochDay}"))
    }
}

class InventoryUsageVarianceRule(private val database: AppDatabase) : ManagementRule {
    override suspend fun evaluate(context: ManagementRuleContext): List<DetectedIssue> = buildList {
        val row=database.businessOperationsDao().foodCostVariance(context.branchId,context.fromEpochDay,context.toEpochDay)
        val thresholdBasisPoints=ManagementThresholdCatalog(database).basisPoints(context.branchId,"INVENTORY_USAGE_VARIANCE_BP",ManagementDefaults.INVENTORY_USAGE_VARIANCE_BASIS_POINTS)
        if(row.theoreticalCostRial>0 && row.actualEvidenceCount>0) {
            val actual=SignedLongMath.subtract(SignedLongMath.add(SignedLongMath.add(row.standardSalesLedgerCostRial,row.wasteCostRial),row.negativeAdjustmentCostRial),row.positiveAdjustmentCostRial)
            val diff=actual-row.theoreticalCostRial
            val bp=signedBasisPoints(diff,row.theoreticalCostRial)
            if(bp.absoluteValue>=thresholdBasisPoints) add(DetectedIssue(context.branchId,ManagementIssueType.ABNORMAL_INVENTORY_USAGE,ManagementIssueSeverity.HIGH,"مصرف غیرعادی موجودی","برآورد مصرف دفتر ${actual} در برابر مصرف نظری ${row.theoreticalCostRial} است.",diff.absoluteValue,context.toEpochDay,"DAILY_SALES_PERIOD",context.toEpochDay,"${context.fromEpochDay}-${context.toEpochDay}"))
        }
    }
}
