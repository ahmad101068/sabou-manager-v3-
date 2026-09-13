package ir.restaurant.management.domain.security

import ir.restaurant.management.domain.common.BusinessError
import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SegregationOfDutiesTest {
    @Test
    fun creatorCannotApproveOwnCommand() {
        val error = assertFailsWith<BusinessRuleViolation> {
            SegregationOfDuties.requireDifferentActors("PAYROLL_APPROVAL", 7, 7)
        }
        assertTrue(error.error is BusinessError.SeparationOfDutiesViolation)
    }

    @Test
    fun distinctActorsAreAccepted() {
        SegregationOfDuties.requireDifferentActors("PURCHASE_APPROVAL", 7, 8)
    }

    @Test
    fun legacyNamesFailClosedWhenTheyMatch() {
        assertFailsWith<BusinessRuleViolation> {
            SegregationOfDuties.requireDifferentHistoricalAware(
                operation = "LEGACY_APPROVAL",
                creatorActorId = null,
                creatorDisplayName = "مدیر",
                approverActorId = 8,
                approverDisplayName = " مدیر ",
            )
        }
    }
}
