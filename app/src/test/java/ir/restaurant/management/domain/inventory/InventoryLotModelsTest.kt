package ir.restaurant.management.domain.inventory

import ir.restaurant.management.domain.common.BusinessRuleViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryLotModelsTest {
    private val request = LotAllocationRequest(
        itemId = 10,
        locationId = 20,
        requiredQuantityMicros = 5_000_000,
        businessEpochDay = 100,
        trackExpiry = true,
    )

    @Test
    fun fefoSelectsEarliestExpiryDeterministicallyAcrossMixedLots() {
        val result = FefoLotAllocator.allocate(
            request,
            listOf(
                lot(3, expiry = 110, quantity = 4_000_000, received = 80),
                lot(1, expiry = 105, quantity = 2_000_000, received = 90),
                lot(2, expiry = 105, quantity = 2_000_000, received = 70),
                lot(4, expiry = null, quantity = 9_000_000, received = 60),
            ),
        )

        assertTrue(result.isComplete)
        assertEquals(listOf(2L, 1L, 3L), result.allocations.map { it.lotId })
        assertEquals(listOf(2_000_000L, 2_000_000L, 1_000_000L), result.allocations.map { it.quantityMicros })
    }

    @Test
    fun normalConsumptionExcludesExpiredQuarantinedBlockedAndMissingExpiryLots() {
        val result = FefoLotAllocator.allocate(
            request,
            listOf(
                lot(1, expiry = 99, quantity = 2_000_000),
                lot(2, expiry = 101, quantity = 2_000_000, status = InventoryLotStatus.QUARANTINED),
                lot(3, expiry = 102, quantity = 2_000_000, status = InventoryLotStatus.BLOCKED),
                lot(4, expiry = null, quantity = 2_000_000),
                lot(5, expiry = 103, quantity = 1_500_000),
            ),
        )

        assertFalse(result.isComplete)
        assertEquals(3_500_000L, result.shortageMicros)
        assertEquals(listOf(5L), result.allocations.map { it.lotId })
    }

    @Test
    fun disposalCanAllocateExpiredAndQuarantinedLots() {
        val result = FefoLotAllocator.allocate(
            request.copy(requiredQuantityMicros = 3_000_000, purpose = LotAllocationPurpose.DISPOSAL),
            listOf(
                lot(1, expiry = 90, quantity = 2_000_000, status = InventoryLotStatus.EXPIRED),
                lot(2, expiry = 95, quantity = 2_000_000, status = InventoryLotStatus.QUARANTINED),
            ),
        )

        assertTrue(result.isComplete)
        assertEquals(listOf(1L, 2L), result.allocations.map { it.lotId })
    }

    @Test
    fun unknownPersistedStatusIsFailSafeAndNeverAllocated() {
        val result = FefoLotAllocator.allocate(
            request.copy(requiredQuantityMicros = 1_000_000),
            listOf(lot(1, expiry = 110, quantity = 2_000_000, status = InventoryLotStatus.LEGACY_UNKNOWN)),
        )

        assertEquals(1_000_000L, result.shortageMicros)
        assertTrue(result.allocations.isEmpty())
    }

    @Test
    fun depletedLotCannotTransitionBackToActive() {
        assertFailsWith<BusinessRuleViolation> {
            InventoryLotTransitionPolicy.requireAllowed(
                InventoryLotStatus.DEPLETED,
                InventoryLotStatus.ACTIVE,
                0,
            )
        }
    }

    private fun lot(
        id: Long,
        expiry: Long?,
        quantity: Long,
        received: Long = 80,
        status: InventoryLotStatus = InventoryLotStatus.ACTIVE,
    ) = LotAllocationCandidate(
        lotId = id,
        locationId = 20,
        receivedEpochDay = received,
        expiryEpochDay = expiry,
        availableQuantityMicros = quantity,
        unitCostRial = 100,
        status = status,
    )
}
