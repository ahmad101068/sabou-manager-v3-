package ir.restaurant.management.domain.inventory

import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InventoryTransferModelsTest {
    @Test
    fun lifecycleOnlyAllowsControlledIssueAndReceipt() {
        InventoryTransferTransitionPolicy.requireAllowed(
            InventoryTransferStatus.REQUESTED,
            InventoryTransferStatus.APPROVED,
        )
        InventoryTransferTransitionPolicy.requireAllowed(
            InventoryTransferStatus.APPROVED,
            InventoryTransferStatus.IN_TRANSIT,
        )
        InventoryTransferTransitionPolicy.requireAllowed(
            InventoryTransferStatus.IN_TRANSIT,
            InventoryTransferStatus.COMPLETED,
        )
        assertFailsWith<BusinessRuleViolation> {
            InventoryTransferTransitionPolicy.requireAllowed(
                InventoryTransferStatus.REQUESTED,
                InventoryTransferStatus.COMPLETED,
            )
        }
    }

    @Test
    fun receiveCommandRequiresEveryQuantityToBePositive() {
        assertFailsWith<IllegalArgumentException> {
            ReceiveInventoryTransferCommand(
                transferId = 1,
                actorId = 2,
                businessEpochDay = 3,
                receivedQuantityByLineId = mapOf(4L to 0L),
                reason = "دریافت آزمون",
            ).validated()
        }
    }

    @Test
    fun unknownStoredStatusIsExplicitlyRestricted() {
        assertEquals(
            InventoryTransferStatus.LEGACY_UNKNOWN,
            InventoryTransferStatus.fromStoredValue("FUTURE_STATE"),
        )
        assertFailsWith<BusinessRuleViolation> {
            InventoryTransferTransitionPolicy.requireAllowed(
                InventoryTransferStatus.LEGACY_UNKNOWN,
                InventoryTransferStatus.COMPLETED,
            )
        }
    }
}
