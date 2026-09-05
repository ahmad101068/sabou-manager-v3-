package ir.restaurant.management.domain.inventory

import ir.restaurant.management.core.QuantityMicros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryReplenishmentCalculatorTest {
    @Test
    fun leadTimeDemandSafetyStockAndOrderMultipleDetermineRecommendation() {
        val result = InventoryReplenishmentCalculator.recommend(
            input(
                onHandMicros = 2_000_000,
                usageMicros = 30_000_000,
                unitCostRial = 600_000,
                policy = policy(
                    targetCoverDays = 14,
                    leadTimeDays = 3,
                    safetyStockMicros = 2_000_000,
                    orderMultipleMicros = 5_000_000,
                ),
            ),
        )

        assertEquals(1_000_000L, result.averageDailyUsageMicros)
        assertEquals(3_000_000L, result.leadTimeDemandMicros)
        assertEquals(5_000_000L, result.reorderPointMicros)
        assertEquals(19_000_000L, result.targetStockMicros)
        assertEquals(20_000_000L, result.suggestedQuantityMicros)
        assertEquals(20_000L, result.daysOfCoverBasisPoints)
        assertEquals(12_000_000L, result.estimatedOrderValueRial)
        assertEquals(InventoryReplenishmentRisk.BELOW_SAFETY_STOCK, result.risk)
    }

    @Test
    fun inTransitAndOnOrderPreventUnnecessaryRecommendation() {
        val result = InventoryReplenishmentCalculator.recommend(
            input(
                onHandMicros = 1_000_000,
                inTransitMicros = 2_000_000,
                onOrderMicros = 4_000_000,
                usageMicros = 30_000_000,
                policy = policy(targetCoverDays = 5, leadTimeDays = 1, safetyStockMicros = 1_000_000),
            ),
        )

        assertEquals(0L, result.suggestedQuantityMicros)
        assertFalse(result.isActionable)
    }

    @Test
    fun zeroUsageIsSafeAndMasterMinMaxCanStillDriveReplenishment() {
        val result = InventoryReplenishmentCalculator.recommend(
            input(
                onHandMicros = 0,
                usageMicros = 0,
                policy = policy(
                    minimumStockMicros = 5_000_000,
                    maximumStockMicros = 10_000_000,
                ),
            ),
        )

        assertNull(result.daysOfCoverBasisPoints)
        assertEquals(10_000_000L, result.suggestedQuantityMicros)
        assertEquals(InventoryReplenishmentRisk.OUT_OF_STOCK, result.risk)
    }

    @Test
    fun pendingRequisitionKeepsRecommendationVisibleButBlocksDuplicateAction() {
        val result = InventoryReplenishmentCalculator.recommend(
            input(
                onHandMicros = 0,
                usageMicros = 30_000_000,
                hasPendingRequisition = true,
                policy = policy(minimumStockMicros = 1_000_000, maximumStockMicros = 5_000_000),
            ),
        )

        assertTrue(result.suggestedQuantityMicros > 0)
        assertFalse(result.isActionable)
    }

    @Test
    fun quantityOverflowIsRejectedInsteadOfClamped() {
        assertFailsWith<IllegalArgumentException> {
            InventoryReplenishmentCalculator.recommend(
                input(
                    onHandMicros = 0,
                    usageMicros = QuantityMicros.MAX_VALUE,
                    usageWindowDays = 1,
                    policy = policy(targetCoverDays = 365, leadTimeDays = 365),
                ),
            )
        }
    }

    private fun input(
        onHandMicros: Long,
        inTransitMicros: Long = 0,
        onOrderMicros: Long = 0,
        usageMicros: Long,
        usageWindowDays: Int = 30,
        unitCostRial: Long = 0,
        hasPendingRequisition: Boolean = false,
        policy: InventoryReplenishmentPolicy,
    ) = InventoryReplenishmentInput(
        itemId = 1,
        itemName = "برنج",
        unit = "کیلوگرم",
        locationId = 1,
        locationName = "انبار اصلی",
        onHandMicros = onHandMicros,
        reservedMicros = 0,
        damagedMicros = 0,
        quarantinedMicros = 0,
        inTransitMicros = inTransitMicros,
        onOrderMicros = onOrderMicros,
        usageMicros = usageMicros,
        usageWindowDays = usageWindowDays,
        estimatedUnitCostRial = unitCostRial,
        preferredSupplierId = null,
        preferredSupplierName = null,
        hasPendingRequisition = hasPendingRequisition,
        policy = policy,
    )

    private fun policy(
        targetCoverDays: Int = 7,
        leadTimeDays: Int = 0,
        safetyStockMicros: Long = 0,
        minimumStockMicros: Long = 0,
        maximumStockMicros: Long = 0,
        orderMultipleMicros: Long = 1,
    ) = InventoryReplenishmentPolicy(
        targetCoverDays = targetCoverDays,
        leadTimeDays = leadTimeDays,
        safetyStockMicros = safetyStockMicros,
        minimumStockMicros = minimumStockMicros,
        maximumStockMicros = maximumStockMicros,
        configuredReorderPointMicros = 0,
        orderMultipleMicros = orderMultipleMicros,
    )
}
