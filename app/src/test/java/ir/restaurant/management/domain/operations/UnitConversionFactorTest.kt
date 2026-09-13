package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnitConversionFactorTest {
    @Test fun cartonToBottleIsExact() {
        val factor = UnitConversionFactor(12, 1)
        assertEquals(24_000_000L, factor.toStockMicros(2_000_000L))
        assertEquals(2_000_000L, factor.fromStockMicros(24_000_000L))
    }

    @Test fun kilogramToGramIsExact() {
        val factor = UnitConversionFactor(1000, 1)
        assertEquals(1_500_000_000L, factor.toStockMicros(1_500_000L))
    }

    @Test fun nonRepresentableMicrosAreRejectedInsteadOfRounded() {
        val factor = UnitConversionFactor(1, 3)
        assertThrows(IllegalArgumentException::class.java) { factor.toStockMicros(1L) }
    }

    @Test fun invalidFactorIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { UnitConversionFactor(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { UnitConversionFactor(1, 0) }
    }
}
