package ir.restaurant.management.domain.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InventoryReadModelsTest {
    @Test
    fun availableBalanceExcludesReservedDamagedAndQuarantinedButNotInTransit() {
        val balance = InventoryBalanceView(
            itemId = 1,
            itemName = "برنج",
            sku = "SKU-RICE",
            baseUnit = "کیلوگرم",
            locationId = 2,
            locationName = "انبار اصلی",
            onHandMicros = 10_000_000,
            reservedMicros = 2_000_000,
            inTransitMicros = 4_000_000,
            damagedMicros = 500_000,
            quarantinedMicros = 1_000_000,
            inventoryValueRial = 5_000_000,
            reorderPointMicros = 3_000_000,
        )

        assertEquals(6_500_000L, balance.availableMicros)
    }

    @Test
    fun readQueriesRejectUnboundedPagesAndInvalidDateRanges() {
        assertFailsWith<IllegalArgumentException> { InventoryBalanceQuery(limit = 201).validated() }
        assertFailsWith<IllegalArgumentException> {
            InventoryMovementQuery(fromEpochDay = 20, toEpochDay = 10).validated()
        }
        assertEquals(80, InventoryBalanceQuery(query = "x".repeat(100)).validated().query.length)
    }
}
