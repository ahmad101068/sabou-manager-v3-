package ir.restaurant.management.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftPlannerTest {
    @Test fun onlyAvailableStaffArePlanned() {
        val result = ShiftPlanner.plan(ShiftDemand(10, 600, 900, 1), listOf(StaffAvailability(1, "علی", 600, 900, "آشپز"), StaffAvailability(2, "رضا", 700, 900, "صندوقدار")))
        assertEquals(1, result.size)
        assertEquals(1L, result.first().employeeId)
    }
}
