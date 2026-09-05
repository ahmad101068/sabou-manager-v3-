package ir.restaurant.management.ui

import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModuleListKeysTest {
    @Test
    fun crmRowTypesNeverShareAComposeKey() {
        assertNotEquals(crmCustomerListKey(1), crmFollowUpListKey(1))
    }


}
