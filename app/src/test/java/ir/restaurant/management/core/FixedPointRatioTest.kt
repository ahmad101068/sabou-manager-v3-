package ir.restaurant.management.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixedPointRatioTest {
    @Test
    fun multiplyDivideAvoidsIntermediateLongOverflow() {
        assertEquals(
            Long.MAX_VALUE - 1,
            FixedPointRatio.multiplyDivide(Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE),
        )
    }

    @Test
    fun halfUpRoundingIsExactAtBoundary() {
        assertEquals(2L, FixedPointRatio.multiplyDivide(3, 1, 2, FixedPointRounding.HALF_UP))
        assertEquals(1L, FixedPointRatio.multiplyDivide(3, 1, 2, FixedPointRounding.DOWN))
    }

    @Test
    fun finalOverflowIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            FixedPointRatio.multiplyDivide(Long.MAX_VALUE, 2, 1)
        }
    }
}
