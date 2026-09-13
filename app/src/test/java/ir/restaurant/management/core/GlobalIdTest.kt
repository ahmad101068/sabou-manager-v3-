package ir.restaurant.management.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GlobalIdTest {
    @Test
    fun newIdIsCanonicalAndRoundTrips() {
        val id = GlobalId.new()
        assertEquals(id, GlobalId.parse(id.value.uppercase()))
    }

    @Test
    fun deterministicLegacyIdIsExplicit() {
        assertEquals("legacy:stock_movement:42", GlobalId.legacy("stock_movement", 42).value)
    }

    @Test
    fun malformedOrZeroLegacyIdFailsClosed() {
        assertFailsWith<IllegalArgumentException> { GlobalId.parse("42") }
        assertFailsWith<IllegalArgumentException> { GlobalId.legacy("stock movement", 0) }
    }
}
