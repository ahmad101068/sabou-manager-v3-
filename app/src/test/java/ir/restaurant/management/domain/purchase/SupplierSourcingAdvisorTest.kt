package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierSourcingAdvisorTest {
    @Test
    fun comparesActualOrderValueAfterMinimumAndMultiple() {
        val cheapWithLargeMinimum = offer(1, "ارزان", 80_000, 20_000_000, 5_000_000, 5)
        val practical = offer(2, "عملی", 100_000, 0, 1_000_000, 2)

        val result = SupplierSourcingAdvisor.choose(
            listOf(SupplierOfferCandidate(cheapWithLargeMinimum, 900), SupplierOfferCandidate(practical, 800)),
            requiredQuantityMicros = 8_000_000,
            baselineUnitCostRial = 120_000,
            preferredSupplierId = 1,
        )

        requireNotNull(result)
        assertEquals(2, result.offer.supplierId)
        assertEquals(8_000_000, result.orderQuantityMicros)
        assertEquals(800_000, result.orderValueRial)
        assertEquals(160_000, result.estimatedSavingsRial)
    }

    @Test
    fun usesLeadTimeWhenTotalCostIsEqual() {
        val slow = offer(1, "کند", 100_000, 0, 1_000_000, 7)
        val fast = offer(2, "سریع", 100_000, 0, 1_000_000, 2)
        val result = SupplierSourcingAdvisor.choose(
            listOf(SupplierOfferCandidate(slow, 950), SupplierOfferCandidate(fast, 700)),
            5_000_000, 100_000, null,
        )
        assertEquals(2, requireNotNull(result).offer.supplierId)
    }

    private fun offer(id: Long, name: String, cost: Long, minimum: Long, multiple: Long, lead: Int) = SupplierOfferRecord(
        id, id, name, 10, "قهوه", "SKU-$id", cost, minimum, multiple, lead, null, true,
    )
}
