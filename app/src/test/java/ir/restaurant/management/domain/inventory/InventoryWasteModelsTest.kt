package ir.restaurant.management.domain.inventory

import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryWasteModelsTest {
    @Test
    fun legacyPersianReasonsMapExplicitlyWithoutLosingOtherCategory() {
        assertEquals(WasteReason.SPOILAGE, WasteReason.fromStoredInput("فساد مواد اولیه"))
        assertEquals(WasteReason.EXPIRED, WasteReason.fromStoredInput("انقضای لات"))
        assertEquals(WasteReason.OTHER, WasteReason.fromStoredInput("علت محلی ثبت‌شده"))
    }

    @Test
    fun approvalPolicyHasNoHiddenThreshold() {
        assertFalse(WasteApprovalPolicy.NO_APPROVAL_REQUIRED.requiresApproval(Long.MAX_VALUE))
        assertTrue(WasteApprovalPolicy.ALWAYS_REQUIRE_APPROVAL.requiresApproval(0))
        assertFalse(WasteApprovalPolicy(100_000).requiresApproval(99_999))
        assertTrue(WasteApprovalPolicy(100_000).requiresApproval(100_000))
    }

    @Test
    fun postedWasteCannotReturnToApproval() {
        assertFailsWith<BusinessRuleViolation> {
            WasteTransitionPolicy.requireAllowed(WasteStatus.POSTED, WasteStatus.APPROVED)
        }
    }
}
