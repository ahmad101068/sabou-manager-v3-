package ir.restaurant.management.domain.operations

import java.net.URI

/**
 * Production sync is deliberately fail-closed until the protocol supports a
 * full push/pull cycle and applies remote entity payloads transactionally.
 *
 * The outbox remains available as a durable change log, but it must not be
 * presented as a completed multi-device replication feature.
 */
object SyncSafetyGate {
    const val isProductionReady: Boolean = false
    const val blockedReason: String =
        "همگام‌سازی چنددستگاهی این نسخه آزمایشی است و تا تکمیل دریافت و اعمال امن داده‌های سرور غیرفعال می‌ماند."

    fun requireProductionReady() {
        check(isProductionReady) { blockedReason }
    }
}

data class CloudSyncConfig(val endpoint: String, val organizationId: String, val enabled: Boolean = false, val accessToken: String = "", val refreshToken: String = "", val accessTokenExpiresAtEpochMillis: Long = 0, val deviceId: String = "") {
    val accessTokenExpired: Boolean get() = accessTokenExpiresAtEpochMillis <= System.currentTimeMillis() + 30_000L
    internal fun normalizedHttpsEndpoint(): String {
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        val uri = runCatching { URI(normalizedEndpoint) }.getOrNull()
        require(
            uri != null &&
                uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) { "نشانی Sync باید یک HTTPS endpoint معتبر و بدون query، fragment یا اطلاعات کاربری باشد." }
        return normalizedEndpoint
    }

    fun validated(): CloudSyncConfig {
        val normalizedEndpoint = normalizedHttpsEndpoint()
        require(organizationId.isNotBlank()) { "شناسه مجموعه Sync الزامی است." }
        require(deviceId.isNotBlank()) { "شناسه دستگاه Sync الزامی است." }
        require(accessToken.isNotBlank() && !accessTokenExpired) { "توکن دسترسی Sync معتبر یا فعال نیست." }
        return copy(endpoint = normalizedEndpoint, organizationId = organizationId.trim(), deviceId = deviceId.trim())
    }
}
