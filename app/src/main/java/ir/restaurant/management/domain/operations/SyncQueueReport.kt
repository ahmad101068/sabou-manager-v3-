package ir.restaurant.management.domain.operations

data class SyncQueueReport(val total: Int, val pending: Int, val synced: Int, val conflicts: Int, val oldestPendingAt: Long?, val deadLetters: Int = 0)

object SyncQueueReporter {
    fun summarize(changes: List<SyncEnvelope>): SyncQueueReport = SyncQueueReport(
        total = changes.size,
        pending = changes.count { it.state == SyncState.PENDING },
        synced = changes.count { it.state == SyncState.SYNCED },
        conflicts = changes.count { it.state == SyncState.CONFLICT },
        oldestPendingAt = changes.filter { it.state == SyncState.PENDING }.minOfOrNull { it.occurredAtEpochMillis },
        deadLetters = changes.count { it.state == SyncState.DEAD_LETTER },
    )
}
