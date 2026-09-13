package ir.restaurant.management.domain.inventory

import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryCountModelsTest {
    @Test
    fun blindCountHidesSystemAndVarianceFactsFromCounter() {
        val view = line().toView(blindCount = true, canReviewVariance = false)

        assertNull(view.systemQuantityMicros)
        assertNull(view.systemValueRial)
        assertNull(view.varianceQuantityMicros)
        assertNull(view.varianceValueRial)
        assertEquals(9_000_000L, view.finalCountQuantityMicros)
    }

    @Test
    fun authorizedReviewerCanSeeBlindCountVariance() {
        val view = line().toView(blindCount = true, canReviewVariance = true)

        assertEquals(10_000_000L, view.systemQuantityMicros)
        assertEquals(2_000_000L, view.systemValueRial)
        assertEquals(-1_000_000L, view.varianceQuantityMicros)
        assertEquals(-200_000L, view.varianceValueRial)
    }

    @Test
    fun recountPolicyUsesExplicitQuantityAndValueThresholds() {
        val policy = InventoryRecountPolicy(
            quantityThresholdMicros = 100_000,
            valueThresholdRial = 50_000,
        )

        assertFalse(policy.requiresRecount(1_000_000, 900_000, 200_000, 150_000))
        assertTrue(policy.requiresRecount(1_000_000, 899_999, 200_000, 150_000))
        assertTrue(policy.requiresRecount(1_000_000, 1_000_000, 200_000, 149_999))
    }

    @Test
    fun postedSessionCannotReturnToApproval() {
        assertFailsWith<BusinessRuleViolation> {
            InventoryCountTransitionPolicy.requireAllowed(
                InventoryCountStatus.POSTED,
                InventoryCountStatus.PENDING_APPROVAL,
            )
        }
    }

    private fun line() = InventoryCountLine(
        id = 11,
        sessionId = 7,
        itemId = 5,
        lotId = null,
        systemQuantitySnapshotMicros = 10_000_000,
        systemValueSnapshotRial = 2_000_000,
        firstCountQuantityMicros = 9_000_000,
        secondCountQuantityMicros = 9_000_000,
        finalCountQuantityMicros = 9_000_000,
        finalCountValueRial = 1_800_000,
        varianceQuantityMicros = -1_000_000,
        varianceValueRial = -200_000,
        status = InventoryCountLineStatus.FINALIZED,
        reason = "کسری شمارش",
    )
}
