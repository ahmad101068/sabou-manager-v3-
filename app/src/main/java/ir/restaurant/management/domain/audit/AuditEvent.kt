package ir.restaurant.management.domain.audit

import ir.restaurant.management.core.CorrelationId

@JvmInline
value class AuditAction private constructor(val storedValue: String) {
    companion object {
        fun of(value: String): AuditAction {
            val normalized = value.trim().uppercase()
            require(normalized.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "نوع عملیات Audit معتبر نیست." }
            return AuditAction(normalized)
        }
    }
}

@JvmInline
value class AuditEntityType private constructor(val storedValue: String) {
    companion object {
        fun of(value: String): AuditEntityType {
            val normalized = value.trim().uppercase()
            require(normalized.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) { "نوع Entity در Audit معتبر نیست." }
            return AuditEntityType(normalized)
        }
    }
}

data class AuditEventDraft(
    val action: AuditAction,
    val entityType: AuditEntityType,
    val entityId: Long?,
    val actorId: Long?,
    val actorDisplayName: String,
    val occurredAtEpochMillis: Long,
    val businessEpochDay: Long?,
    val deviceId: String,
    val referenceType: String?,
    val referenceId: Long?,
    val reason: String,
    val beforeSnapshot: String?,
    val afterSnapshot: String?,
    val correlationId: String,
    val description: String,
    val actorRoleSnapshot: String = "UNKNOWN",
    val actorBranchIdSnapshot: Long? = null,
) {
    fun validated(): AuditEventDraft {
        val normalizedReferenceType = referenceType?.trim()?.uppercase()?.take(64)
        require(entityId == null || entityId > 0) { "شناسه Entity در Audit معتبر نیست." }
        require(actorId == null || actorId > 0) { "شناسه actor در Audit معتبر نیست." }
        require(occurredAtEpochMillis > 0) { "زمان Audit معتبر نیست." }
        require(businessEpochDay == null || businessEpochDay > 0) { "تاریخ کسب‌وکار در Audit معتبر نیست." }
        require(referenceId == null || referenceId > 0) { "شناسه مرجع Audit معتبر نیست." }
        require((normalizedReferenceType == null) == (referenceId == null)) { "نوع و شناسه مرجع Audit باید با هم ثبت شوند." }
        require(normalizedReferenceType == null || normalizedReferenceType.matches(Regex("[A-Z][A-Z0-9_]{1,63}"))) {
            "نوع مرجع Audit معتبر نیست."
        }
        return copy(
            actorDisplayName = actorDisplayName.sanitize(120).ifBlank { "SYSTEM" },
            deviceId = deviceId.sanitize(120).ifBlank { "unknown-device" },
            referenceType = normalizedReferenceType,
            reason = reason.sanitize(500),
            beforeSnapshot = beforeSnapshot?.take(MAX_SNAPSHOT_LENGTH),
            afterSnapshot = afterSnapshot?.take(MAX_SNAPSHOT_LENGTH),
            correlationId = CorrelationId.parse(correlationId).value,
            description = description.sanitize(1_000),
            actorRoleSnapshot = actorRoleSnapshot.sanitize(64).ifBlank { "UNKNOWN" }.uppercase(),
            actorBranchIdSnapshot = actorBranchIdSnapshot?.also { require(it > 0) { "شناسه شعبه snapshot در Audit معتبر نیست." } },
        )
    }

    private fun String.sanitize(maxLength: Int): String =
        replace('\n', ' ').replace('\r', ' ').take(maxLength).trim()

    private companion object {
        const val MAX_SNAPSHOT_LENGTH = 16_000
    }
}
