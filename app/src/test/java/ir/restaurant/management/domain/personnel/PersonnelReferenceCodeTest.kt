package ir.restaurant.management.domain.personnel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonnelReferenceCodeTest {
    @Test
    fun shiftCodesAreGeneratedWithoutSharedSequentialState() {
        val codes = List(1_000) { PersonnelReferenceCode.newShiftCode() }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("SHF-[0-9A-F]{12}")) })
    }

    @Test
    fun workScheduleCodesAreGeneratedWithoutSharedSequentialState() {
        val codes = List(1_000) { PersonnelReferenceCode.newWorkScheduleCode() }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("WSC-[0-9A-F]{12}")) })
    }

    @Test
    fun blankDraftCodesAreReservedForAutomaticAllocation() {
        val shift = ShiftTemplateDraft(
            code = "", name = "صبح", category = ShiftCategory.MORNING, startMinute = 480, endMinute = 960,
        ).validated()
        assertEquals("", shift.code)

        val schedule = WorkScheduleDraft(
            code = "", name = "هفتگی", patternType = WorkSchedulePatternType.WEEKLY_FIXED, cycleLengthDays = 7,
            effectiveFromEpochDay = 20_000,
            days = (0 until 7).map { day ->
                WorkScheduleDayRule(day, day + 1, if (day == 6) null else 1L, day == 6)
            },
        ).validated()
        assertEquals("", schedule.code)
    }
}
