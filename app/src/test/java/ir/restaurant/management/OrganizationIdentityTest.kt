package ir.restaurant.management

import kotlin.test.Test
import kotlin.test.assertEquals

class OrganizationIdentityTest {
    @Test
    fun blankNameUsesNeutralTitle() {
        assertEquals(DEFAULT_ORGANIZATION_TITLE, organizationDisplayTitle(""))
        assertEquals(DEFAULT_ORGANIZATION_TITLE, organizationDisplayTitle("   "))
    }

    @Test
    fun customNameIsTrimmedAndDisplayed() {
        assertEquals("رستوران نمونه", organizationDisplayTitle("  رستوران نمونه  "))
    }
}
