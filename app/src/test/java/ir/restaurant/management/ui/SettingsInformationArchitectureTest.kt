package ir.restaurant.management.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInformationArchitectureTest {
    @Test
    fun `settings has the nine required admin sections`() {
        assertEquals(9, SettingsSection.entries.size)
        val titles = SettingsSection.entries.map { it.title }.toSet()
        listOf("عمومی", "ظاهر", "عملیات", "چاپ", "اعلان‌ها", "داده و پشتیبان", "کاربران و دسترسی", "امنیت و حسابرسی", "درباره برنامه")
            .forEach { assertTrue(it in titles) }
    }
}
