package ir.restaurant.management.ui

import ir.restaurant.management.domain.operations.AppUserRecord
import ir.restaurant.management.domain.operations.AuditLogRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class AuditSeverity { INFO, NOTICE, WARNING, CRITICAL }

internal data class AuditPresentation(
    val actionLabel: String,
    val entityLabel: String,
    val actorLabel: String,
    val timeLabel: String,
    val severity: AuditSeverity,
)

internal object AuditPresentationMapper {
    private val actionLabels = mapOf(
        "CREATE" to "ایجاد",
        "UPDATE" to "ویرایش",
        "DELETE" to "حذف",
        "APPROVE" to "تأیید",
        "REJECT" to "رد",
        "POST" to "ثبت نهایی",
        "REVERSE" to "برگشت عملیات",
        "VOID" to "ابطال",
        "PAY" to "پرداخت",
        "LOGIN" to "ورود",
        "LOGOUT" to "خروج",
        "RESTORE" to "بازیابی",
        "EXPORT" to "خروجی",
        "CLOSE" to "بستن",
        "REOPEN" to "بازگشایی",
    )
    private val entityLabels = mapOf(
        "EMPLOYEE" to "پرسنل",
        "EMPLOYMENT_CONTRACT" to "قرارداد پرسنلی",
        "ATTENDANCE" to "حضور و غیاب",
        "ATTENDANCE_CORRECTION" to "اصلاح حضور",
        "OVERTIME_APPROVAL" to "تأیید اضافه‌کار",
        "PAYROLL" to "حقوق و دستمزد",
        "PAYROLL_PAYSLIP" to "فیش حقوقی",
        "INVENTORY" to "انبار",
        "INVENTORY_ITEM" to "کالای انبار",
        "SALE" to "فروش",
        "PURCHASE" to "خرید",
        "TREASURY" to "خزانه",
        "ACCOUNTING" to "حسابداری",
        "ASSET" to "دارایی",
        "CUSTOMER" to "مشتری",
        "USER" to "کاربر",
        "SECURITY" to "امنیت",
    )

    fun map(record: AuditLogRecord, users: List<AppUserRecord>, nowEpochMillis: Long = System.currentTimeMillis()): AuditPresentation {
        val actor = record.actorId?.let { id -> users.firstOrNull { it.id == id } }
        return AuditPresentation(
            actionLabel = localizeAction(record.action),
            entityLabel = localizeEntity(record.entityType),
            actorLabel = actor?.let { "${it.displayName} · ${it.role.title}" } ?: record.actor.ifBlank { "کاربر تاریخی" },
            timeLabel = formatTime(record.createdAtEpochMillis, nowEpochMillis),
            severity = severity(record),
        )
    }

    fun localizeAction(value: String): String {
        val normalized = value.trim().uppercase()
        actionLabels[normalized]?.let { return it }
        val token = actionLabels.entries.firstOrNull { (key, _) -> normalized.startsWith("${key}_") || normalized.endsWith("_${key}") }
        return token?.value ?: "رویداد سیستمی"
    }

    fun localizeEntity(value: String): String {
        val normalized = value.trim().uppercase()
        entityLabels[normalized]?.let { return it }
        return entityLabels.entries.firstOrNull { (key, _) -> normalized.contains(key) }?.value ?: "بخش سیستم"
    }

    fun redactSensitiveSnapshot(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val keyPattern = Regex("(?i)(password|pin|token|national.?id|bank.?account|card.?number|iban)(\\s*[:=]\\s*)([^,;}\\n]+)")
        return value.replace(keyPattern) { match -> "${match.groupValues[1]}${match.groupValues[2]}***" }.take(1500)
    }

    fun severity(record: AuditLogRecord): AuditSeverity {
        val action = record.action.uppercase()
        val text = "${record.description} ${record.reason}".uppercase()
        return when {
            action.contains("DELETE") || action.contains("REVERSE") || action.contains("RESTORE") || action.contains("REOPEN") -> AuditSeverity.CRITICAL
            action.contains("REJECT") || action.contains("VOID") || text.contains("FAILED") || text.contains("ناموفق") -> AuditSeverity.WARNING
            action.contains("APPROVE") || action.contains("POST") || action.contains("PAY") || action.contains("LOGIN") -> AuditSeverity.NOTICE
            else -> AuditSeverity.INFO
        }
    }

    fun formatTime(epochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): String {
        val event = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        val time = event.format(DateTimeFormatter.ofPattern("HH:mm"))
        val delta = now.toLocalDate().toEpochDay() - event.toLocalDate().toEpochDay()
        return when (delta) {
            0L -> "امروز، $time"
            1L -> "دیروز، $time"
            else -> "${epochDayToPersian(event.toLocalDate().toEpochDay()).display()}، ساعت $time"
        }
    }
}
