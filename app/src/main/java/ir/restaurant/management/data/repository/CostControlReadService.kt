package ir.restaurant.management.data.repository

import ir.restaurant.management.core.SignedLongMath
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.control.ActualCostDataQuality
import ir.restaurant.management.domain.control.ConsumptionCostVariance
import ir.restaurant.management.domain.control.CostControlReadService
import ir.restaurant.management.domain.control.PurchasePriceInsight
import ir.restaurant.management.domain.security.Permission
import java.math.BigInteger

class LocalCostControlReadService(
    private val database: AppDatabase,
    private val authorizer: SessionAuthorizer,
) : CostControlReadService {
    override suspend fun purchasePriceInsights(branchId: Long, fromEpochDay: Long, toEpochDay: Long): List<PurchasePriceInsight> {
        authorizer.require(Permission.CONTROL_VIEW)
        require(branchId > 0 && fromEpochDay > 0 && toEpochDay >= fromEpochDay)
        CanonicalBranchResolver(database).requireExisting(branchId)
        return database.businessOperationsDao().purchasePriceSpikeRows(branchId, fromEpochDay, toEpochDay).map { row ->
            val previous = row.previousPriceRial.takeIf { it > 0 }
            val average = row.average30DayRial.takeIf { it > 0 }
            PurchasePriceInsight(
                itemId = row.itemId,
                itemName = row.itemName,
                currentPriceRial = row.currentPriceRial,
                previousPriceRial = previous,
                average30DayRial = average,
                changeBasisPointsVs30Day = average?.let { basisPoints(SignedLongMath.subtract(row.currentPriceRial, it), it) },
            )
        }
    }

    override suspend fun consumptionVariance(branchId: Long, fromEpochDay: Long, toEpochDay: Long): ConsumptionCostVariance {
        authorizer.require(Permission.CONTROL_VIEW)
        require(branchId > 0 && fromEpochDay > 0 && toEpochDay >= fromEpochDay)
        CanonicalBranchResolver(database).requireExisting(branchId)
        val row = database.businessOperationsDao().foodCostVariance(branchId,fromEpochDay,toEpochDay)
        // Standard sales consumption is scoped through its DAILY_SALES document. Independent actual
        // evidence (waste/count/adjustment) is scoped through the canonical storage location branch.
        // Legacy movements without a canonical location remain excluded instead of being fabricated
        // as Branch 1 evidence.
        if (row.actualEvidenceCount <= 0L) return ConsumptionCostVariance(
            branchId, fromEpochDay, toEpochDay, row.theoreticalCostRial, null, null, null,
            ActualCostDataQuality.ACTUAL_NOT_AVAILABLE,
            "برای دوره، شمارش/ضایعات/تعدیل واقعی مستقل از مصرف استاندارد فروش ثبت نشده است.",
        )
        val actual = SignedLongMath.subtract(
            SignedLongMath.add(
                SignedLongMath.add(row.standardSalesLedgerCostRial, row.wasteCostRial),
                row.negativeAdjustmentCostRial,
            ),
            row.positiveAdjustmentCostRial,
        )
        val variance = SignedLongMath.subtract(actual,row.theoreticalCostRial)
        return ConsumptionCostVariance(
            branchId, fromEpochDay, toEpochDay, row.theoreticalCostRial, actual, variance,
            row.theoreticalCostRial.takeIf { it>0 }?.let { basisPoints(variance,it) },
            ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE,
            "برآورد دفتر: مصرف استاندارد فروش + ضایعات + کسری/تعدیل منفی - اصلاحات مثبت؛ انتقال‌ها حذف شده‌اند.",
        )
    }

    private fun basisPoints(delta:Long,base:Long):Int {
        val v=BigInteger.valueOf(delta).multiply(BigInteger.valueOf(10_000L)).divide(BigInteger.valueOf(base))
        return v.coerceIn(BigInteger.valueOf(Int.MIN_VALUE.toLong()),BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt()
    }
}
