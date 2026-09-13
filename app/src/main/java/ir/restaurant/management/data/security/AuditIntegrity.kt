package ir.restaurant.management.data.security

import ir.restaurant.management.data.db.AuditLogEntity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AuditIntegrityCanonicalizer {
    fun hashEvent(entity: AuditLogEntity): String {
        require(entity.integritySequence > 0) { "audit_integrity_sequence_invalid" }
        return sha256Hex(canonicalPayload(entity))
    }

    fun sha256Hex(value: String?): String = sha256Hex((value ?: "").toByteArray(StandardCharsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun canonicalPayload(e: AuditLogEntity): ByteArray {
        val fields = listOf(
            "v1",
            e.integritySequence.toString(),
            e.previousEventHash,
            e.globalId,
            e.actorId?.toString().orEmpty(),
            e.actor,
            e.actorRoleSnapshot,
            e.actorBranchIdSnapshot?.toString().orEmpty(),
            e.deviceId,
            e.action,
            e.entityType,
            e.entityId?.toString().orEmpty(),
            e.businessEpochDay?.toString().orEmpty(),
            e.createdAtEpochMillis.toString(),
            e.reason,
            e.correlationId,
            e.referenceType.orEmpty(),
            e.referenceId?.toString().orEmpty(),
            sha256Hex(e.beforeSnapshot),
            sha256Hex(e.afterSnapshot),
            sha256Hex(e.description),
        )
        return buildString {
            fields.forEach { field -> append(field.length).append(':').append(field).append('|') }
        }.toByteArray(StandardCharsets.UTF_8)
    }
}

data class AuditIntegrityVerification(
    val valid: Boolean,
    val checkedEvents: Int,
    val failure: String? = null,
    val terminalHash: String = "",
)
