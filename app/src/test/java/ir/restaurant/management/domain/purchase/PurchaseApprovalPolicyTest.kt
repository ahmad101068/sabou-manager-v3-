package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseApprovalPolicyTest {
    @Test fun `small requisition needs one approval`() {
        val plan = PurchaseApprovalPolicy.plan(PurchaseApprovalPolicy.SECOND_APPROVAL_THRESHOLD_RIAL - 1)
        assertEquals(1, plan.requiredLevel)
        assertFalse(plan.requiresOwnerAtFinalLevel)
    }

    @Test fun `threshold requisition needs owner second approval`() {
        val plan = PurchaseApprovalPolicy.plan(PurchaseApprovalPolicy.SECOND_APPROVAL_THRESHOLD_RIAL)
        assertEquals(2, plan.requiredLevel)
        assertTrue(plan.requiresOwnerAtFinalLevel)
    }

    @Test fun `threshold is injectable per organization policy`() {
        val policy = PurchaseApprovalPolicy(secondApprovalThresholdRial = 10_000_000)
        assertEquals(1, policy.plan(9_999_999).requiredLevel)
        assertEquals(2, policy.plan(10_000_000).requiredLevel)
    }
}
