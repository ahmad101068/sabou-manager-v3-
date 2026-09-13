package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.operations.SyncTransport
import ir.restaurant.management.domain.operations.SyncMetrics
import ir.restaurant.management.domain.operations.SyncMetricsCalculator

data class SyncRunResult(val uploaded: Int, val conflicts: Int, val rejected: Int, val metrics: SyncMetrics = SyncMetrics(uploaded, conflicts, rejected, 0))

class SyncCoordinator(
    private val repository: LocalSyncRepository,
    private val transport: SyncTransport,
) {
    suspend fun runOnce(limit: Int = 50): SyncRunResult {
        val started = System.currentTimeMillis()
        val batch = repository.nextBatch(limit)
        if (batch.changes.isEmpty()) return SyncRunResult(0, 0, 0)
        val result = runCatching { transport.upload(batch.changes) }.getOrElse { failure ->
            repository.recordBatchFailure(batch.changes.map { it.changeId }, failure.message ?: "خطای ارتباط با سرویس همگام‌سازی")
            throw failure
        }
        val expected = batch.changes.map { it.changeId }.toSet()
        val categorized = result.accepted + result.conflicts + result.rejected
        require(categorized.all { it in expected }) { "پاسخ سرویس شامل شناسه ناشناخته است." }
        require(categorized.size == categorized.distinct().size) { "یک تغییر در چند وضعیت همگام‌سازی گزارش شده است." }
        result.accepted.forEach { repository.markSynced(it) }
        result.conflicts.forEach { repository.markConflict(it, "تعارض در سرویس همگام‌سازی") }
        result.rejected.forEach { repository.markRejected(it, "تغییر توسط سرویس همگام‌سازی رد شد") }
        val unacknowledged = expected - categorized.toSet()
        if (unacknowledged.isNotEmpty()) repository.recordBatchFailure(unacknowledged.toList(), "سرویس برای این تغییر وضعیت نهایی برنگرداند.")
        return SyncRunResult(result.accepted.size, result.conflicts.size, result.rejected.size, SyncMetricsCalculator.from(result,(System.currentTimeMillis()-started).coerceAtLeast(0)))
    }
}
