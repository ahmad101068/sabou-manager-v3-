package ir.restaurant.management.domain.operations

import ir.restaurant.management.core.SignedLongMath

data class SyncMetrics(val uploaded: Int, val conflicts: Int, val rejected: Int, val durationMillis: Long) {
    val successRatePercent: Long
        get() {
            val total = SignedLongMath.add(SignedLongMath.add(uploaded.toLong(), conflicts.toLong()), rejected.toLong())
            return if (total == 0L) 100 else SignedLongMath.multiply(uploaded.toLong(), 100) / total
        }
}

object SyncMetricsCalculator {
    fun from(result: SyncUploadResult, durationMillis: Long): SyncMetrics {
        require(durationMillis >= 0) { "مدت همگام‌سازی نامعتبر است." }
        return SyncMetrics(result.accepted.size, result.conflicts.size, result.rejected.size, durationMillis)
    }
}
