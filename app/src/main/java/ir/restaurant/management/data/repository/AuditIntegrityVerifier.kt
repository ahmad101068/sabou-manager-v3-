package ir.restaurant.management.data.repository

import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.security.AuditIntegrityCanonicalizer
import ir.restaurant.management.data.security.AuditIntegrityVerification

class AuditIntegrityVerifier(private val database: AppDatabase) {
    suspend fun verify(): AuditIntegrityVerification {
        val rows = database.auditLogDao().allForIntegrityVerification()
        var expectedSequence = 1L
        var expectedPrevious = ""
        val hashes = HashSet<String>(rows.size)
        rows.forEachIndexed { index, row ->
            if (row.integritySequence != expectedSequence) {
                return AuditIntegrityVerification(false, index, "AUDIT_SEQUENCE_GAP_OR_REORDER:${row.id}", expectedPrevious)
            }
            if (row.previousEventHash != expectedPrevious) {
                return AuditIntegrityVerification(false, index, "AUDIT_PREVIOUS_HASH_MISMATCH:${row.id}", expectedPrevious)
            }
            val calculated = AuditIntegrityCanonicalizer.hashEvent(row)
            if (calculated != row.eventHash) {
                return AuditIntegrityVerification(false, index, "AUDIT_EVENT_HASH_MISMATCH:${row.id}", expectedPrevious)
            }
            if (!hashes.add(row.eventHash)) {
                return AuditIntegrityVerification(false, index, "AUDIT_REPLAYED_HASH:${row.id}", expectedPrevious)
            }
            expectedPrevious = row.eventHash
            expectedSequence++
        }
        return AuditIntegrityVerification(true, rows.size, terminalHash = expectedPrevious)
    }
}
