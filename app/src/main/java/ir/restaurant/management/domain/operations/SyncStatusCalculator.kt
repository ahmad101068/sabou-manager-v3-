package ir.restaurant.management.domain.operations

data class SyncStatusSummary(
    val total: Int,
    val pending: Int,
    val synced: Int,
    val conflicts: Int,
    val lastChangeEpochMillis: Long?,
    val health: SyncHealth,
)

enum class SyncHealth { HEALTHY, PENDING_WORK, NEEDS_ATTENTION }

object SyncStatusCalculator {
    fun summarize(changes: List<SyncEnvelope>): SyncStatusSummary {
        changes.forEach { it.validated() }
        val pending = changes.count { it.state == SyncState.PENDING }
        val synced = changes.count { it.state == SyncState.SYNCED }
        val conflicts = changes.count { it.state == SyncState.CONFLICT || it.state == SyncState.DEAD_LETTER }
        return SyncStatusSummary(changes.size, pending, synced, conflicts, changes.maxOfOrNull { it.occurredAtEpochMillis }, when {
            conflicts > 0 -> SyncHealth.NEEDS_ATTENTION
            pending > 0 -> SyncHealth.PENDING_WORK
            else -> SyncHealth.HEALTHY
        })
    }
}
