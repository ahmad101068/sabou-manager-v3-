package ir.restaurant.management.domain.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InventoryBalanceModelsTest {
    @Test
    fun availableExcludesReservedDamagedAndQuarantinedStock() {
        val balance = InventoryBalance(
            itemId = 1,
            locationId = 2,
            onHandMicros = 10_000_000,
            reservedMicros = 2_000_000,
            inTransitMicros = 3_000_000,
            damagedMicros = 1_000_000,
            quarantinedMicros = 500_000,
            inventoryValueRial = 2_000_000,
        )

        assertEquals(6_500_000L, balance.availableMicros)
    }

    @Test
    fun invalidStateBucketsCannotExceedPhysicalStock() {
        assertFailsWith<IllegalArgumentException> {
            InventoryBalance(
                itemId = 1,
                locationId = 2,
                onHandMicros = 1_000_000,
                reservedMicros = 700_000,
                inTransitMicros = 0,
                damagedMicros = 400_000,
                quarantinedMicros = 0,
                inventoryValueRial = 100_000,
            )
        }
    }

    @Test
    fun weightedAverageDisposesAllResidualValueWhenStockIsFullyIssued() {
        assertEquals(
            999_999L,
            WeightedAverageInventoryValuationService.issueValue(
                balanceQuantityMicros = 3_000_000,
                balanceValueRial = 999_999,
                issueQuantityMicros = 3_000_000,
            ),
        )
    }

    @Test
    fun zeroUsageCannotEnterValuationDivision() {
        assertFailsWith<IllegalArgumentException> {
            WeightedAverageInventoryValuationService.issueValue(0, 0, 1)
        }
    }
}
