package ir.restaurant.management.domain.personnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkforceSchedulingModelsTest {
    @Test
    fun `night shift derives cross midnight duration`() {
        val shift = ShiftTemplateDraft(
            code = "night-a",
            name = "شب A",
            category = ShiftCategory.NIGHT,
            startMinute = 18 * 60,
            endMinute = 2 * 60,
            breakMinutes = 30,
        ).validated()

        assertEquals("NIGHT-A", shift.code)
        assertTrue(shift.crossesMidnight)
        assertEquals(450, shift.plannedWorkMinutes)
    }

    @Test
    fun `weekly schedule requires unique days and selected shift on workday`() {
        val schedule = WorkScheduleDraft(
            code = "kitchen-night",
            name = "الگوی آشپزخانه شب",
            patternType = WorkSchedulePatternType.WEEKLY_FIXED,
            cycleLengthDays = 7,
            effectiveFromEpochDay = 20_000,
            days = (0..6).map { sequence ->
                WorkScheduleDayRule(
                    sequenceDay = sequence,
                    dayOfWeek = sequence + 1,
                    shiftTemplateId = if (sequence == 2) null else 10L,
                    isOffDay = sequence == 2,
                )
            },
        ).validated()

        assertEquals(7, schedule.days.size)
        assertTrue(schedule.days[2].isOffDay)
    }
}
