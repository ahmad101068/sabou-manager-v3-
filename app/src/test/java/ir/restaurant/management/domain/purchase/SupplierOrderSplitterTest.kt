package ir.restaurant.management.domain.purchase

import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierOrderSplitterTest {
    @Test fun groupsLinesAndUsesLongestLeadTimePerSupplier() {
        val result = SupplierOrderSplitter.split(
            lines = listOf(
                SupplierAssignedRequisitionLine(1, 20, 2),
                SupplierAssignedRequisitionLine(2, 10, 3),
                SupplierAssignedRequisitionLine(3, 20, 7),
            ),
            orderEpochDay = 25_000,
            fallbackSupplierId = null,
        )
        assertEquals(listOf(10L, 20L), result.map { it.supplierId })
        assertEquals(listOf(2L), result[0].lineIds)
        assertEquals(25_003L, result[0].expectedEpochDay)
        assertEquals(listOf(1L, 3L), result[1].lineIds)
        assertEquals(25_007L, result[1].expectedEpochDay)
    }

    @Test fun assignsUnallocatedLinesToFallbackSupplier() {
        val result = SupplierOrderSplitter.split(
            listOf(SupplierAssignedRequisitionLine(1, null, null)), 10_000, 30,
        )
        assertEquals(30L, result.single().supplierId)
        assertEquals(10_000L, result.single().expectedEpochDay)
    }
}
