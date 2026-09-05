package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalWorkflowTest {
    @Test
    fun approvedRequestCannotBeApprovedTwice() {
        val request = ApprovalRequest(1, "PURCHASE", 4, 1000, "manager").approve("owner")
        assertEquals(ApprovalStatus.APPROVED, request.status)
    }

    @Test
    fun thresholdPolicyIsInclusive() {
        assertEquals(true, ApprovalPolicy.requiresApproval("DISCOUNT", 500, 500))
    }
}
