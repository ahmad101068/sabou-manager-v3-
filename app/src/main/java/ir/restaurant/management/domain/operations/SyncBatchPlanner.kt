package ir.restaurant.management.domain.operations

data class SyncBatch(val changes: List<SyncEnvelope>, val hasMore: Boolean)

object SyncBatchPlanner {
    fun plan(changes: List<SyncEnvelope>, limit: Int = 50): SyncBatch {
        require(limit in 1..500) { "اندازه بسته همگام‌سازی نامعتبر است." }
        val ordered = changes.sortedWith(compareBy<SyncEnvelope> { it.occurredAtEpochMillis }.thenBy { it.changeId })
        return SyncBatch(ordered.take(limit), ordered.size > limit)
    }
}
