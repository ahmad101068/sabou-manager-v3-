package ir.restaurant.management.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonnelListKeysTest {
    @Test
    fun employeeAndPayrollWithSameDatabaseIdHaveDifferentComposeKeys() {
        assertNotEquals(personnelEmployeeListKey(1L), personnelPayrollListKey(1L))
    }

    @Test
    fun keysRemainStableForRecomposition() {
        assertEquals("employee-42", personnelEmployeeListKey(42L))
        assertEquals("payroll-42", personnelPayrollListKey(42L))
    }
}
