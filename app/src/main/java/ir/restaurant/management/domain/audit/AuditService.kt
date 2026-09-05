package ir.restaurant.management.domain.audit

/**
 * Append-only audit boundary. Implementations must propagate persistence failure to the caller.
 */
interface AuditService {
    suspend fun record(event: AuditEventDraft): Long
}
