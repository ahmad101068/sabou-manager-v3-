package ir.restaurant.management.domain.operations

import kotlin.math.min

data class SyncRetryDecision(val canRetry: Boolean, val delayMillis: Long, val reason: String)

object SyncRetryPolicy {
    fun afterFailure(attempt: Int, baseDelayMillis: Long = 5_000L, maxDelayMillis: Long = 15 * 60_000L): SyncRetryDecision {
        require(attempt > 0) { "تعداد تلاش نامعتبر است." }
        require(baseDelayMillis > 0 && maxDelayMillis >= baseDelayMillis) { "بازه تأخیر نامعتبر است." }
        if (attempt >= 8) return SyncRetryDecision(false, 0, "پیام پس از ۸ تلاش به Dead-letter منتقل شد.")
        val delay = min(maxDelayMillis, baseDelayMillis * (1L shl attempt.coerceAtMost(30)))
        return SyncRetryDecision(true, delay, "تلاش بعدی پس از backoff نمایی")
    }

    fun decide(attempt: Int, nowMillis: Long, lastAttemptMillis: Long, baseDelayMillis: Long = 5_000L, maxDelayMillis: Long = 15 * 60_000L): SyncRetryDecision {
        require(attempt >= 0) { "تعداد تلاش نامعتبر است." }
        require(baseDelayMillis > 0 && maxDelayMillis >= baseDelayMillis) { "بازه تأخیر نامعتبر است." }
        if (attempt >= 8) return SyncRetryDecision(false, 0, "حداکثر تلاش مجاز تکمیل شده است.")
        // Gate foreground retries against the conservative three-window exponential bound. The
        // worker may still schedule a jittered attempt inside that window, but a caller cannot
        // replay the command early by repeatedly invoking this check.
        val exponent = 1L shl attempt
        val scale = exponent * 3L
        val delay = if (baseDelayMillis > maxDelayMillis / scale) maxDelayMillis else baseDelayMillis * scale
        val elapsed = (nowMillis - lastAttemptMillis).coerceAtLeast(0)
        return SyncRetryDecision(elapsed >= delay, (delay - elapsed).coerceAtLeast(0), "retry پس از backoff نمایی")
    }
}
