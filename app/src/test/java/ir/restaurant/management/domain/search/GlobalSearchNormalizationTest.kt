package ir.restaurant.management.domain.search

import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalSearchNormalizationTest {
    @Test
    fun normalizesArabicPersianVariantsAndDigits() {
        assertEquals("کبابی 123", normalizePersianSearchText("  كبابي ۱۲٣  "))
    }

    @Test
    fun collapsesDirectionalAndWhitespaceCharacters() {
        assertEquals("فروش روزانه", normalizePersianSearchText("فروش\u200c   روزانه"))
    }
}
