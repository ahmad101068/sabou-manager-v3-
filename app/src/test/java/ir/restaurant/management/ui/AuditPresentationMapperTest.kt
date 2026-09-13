package ir.restaurant.management.ui

import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.AuditLogRecord
import ir.restaurant.management.domain.security.UserRole
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditPresentationMapperTest {
    private val user = AppUserRecord(7, "owner", "احمد", UserRole.OWNER, true, false)

    @Test
    fun `CREATE and EMPLOYEE are localized`() {
        val record = AuditLogRecord(1, "CREATE", "EMPLOYEE", 10, "ثبت", "legacy", 1_000, actorId = 7)
        val ui = AuditPresentationMapper.map(record, listOf(user), nowEpochMillis = 1_000)
        assertEquals("ایجاد", ui.actionLabel)
        assertEquals("پرسنل", ui.entityLabel)
        assertTrue(ui.actorLabel.contains("احمد"))
        assertTrue(ui.actorLabel.contains("مالک"))
    }

    @Test
    fun `unknown technical codes are not leaked as raw english`() {
        assertEquals("رویداد سیستمی", AuditPresentationMapper.localizeAction("SOME_INTERNAL_CODE"))
        assertEquals("بخش سیستم", AuditPresentationMapper.localizeEntity("UNMAPPED_ENTITY"))
    }

    @Test
    fun `today audit time is human readable`() {
        val zone = ZoneId.of("Asia/Tehran")
        val now = ZonedDateTime.of(2026, 8, 12, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
        val event = ZonedDateTime.of(2026, 8, 12, 13, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("امروز، 13:05", AuditPresentationMapper.formatTime(event, now, zone))
    }

    @Test
    fun `sensitive snapshot values are masked`() {
        val redacted = AuditPresentationMapper.redactSensitiveSnapshot("token=abc123, nationalId:0012345678, amount=10")!!
        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("0012345678"))
        assertTrue(redacted.contains("***"))
    }
}
