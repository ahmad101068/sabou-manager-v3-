package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.operations.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.HttpsURLConnection
import java.net.URL

class HttpsSyncTransport(private val config: CloudSyncConfig) : SyncTransport {
    override suspend fun upload(batch: List<SyncEnvelope>): SyncUploadResult = withContext(Dispatchers.IO) {
        val valid = config.validated(); require(valid.enabled) { "همگام‌سازی ابری غیرفعال است." }
        require(batch.isNotEmpty()) { "بسته همگام‌سازی خالی است." }
        val batchIdempotencyKey = SyncPayloadCodec.sha256(batch.joinToString("|") { it.changeId })
        val connection = ((URL(valid.endpoint.trimEnd('/') + "/v1/sync/changes").openConnection() as? HttpsURLConnection)
            ?: error("HTTPS connection provider is unavailable.")).apply {
            requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 20_000; doOutput = true; instanceFollowRedirects = false; useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8"); setRequestProperty("X-Organization-Id", valid.organizationId)
            setRequestProperty("Authorization", "Bearer ${valid.accessToken}")
            setRequestProperty("X-Device-Id", valid.deviceId)
            setRequestProperty("Idempotency-Key", batchIdempotencyKey)
        }
        val body = batch.joinToString(prefix="{\"changes\":[",postfix="]}") { value ->
            val itemKey = SyncPayloadCodec.sha256("${value.entityType}|${value.entityId}|${value.revision}|${value.type.name}|${value.payloadHash}")
            "{\"changeId\":\"${value.changeId.json()}\",\"idempotencyKey\":\"$itemKey\",\"entityType\":\"${value.entityType.json()}\",\"entityId\":${value.entityId},\"changeType\":\"${value.type.name}\",\"revision\":${value.revision},\"payloadVersion\":${value.payloadVersion},\"payload\":\"${value.payload.json()}\",\"payloadHash\":\"${value.payloadHash}\"}"
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(code in 200..299) { "سرویس همگام‌سازی پاسخ $code داد." }
            SyncUploadResult(response.ids("accepted"), response.ids("conflicts"), response.ids("rejected"))
        } finally {
            connection.disconnect()
        }
    }
    private fun String.json()=replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")
    private fun String.ids(key:String):List<String>{val content=Regex("\\\"$key\\\"\\s*:\\s*\\[([^]]*)]",RegexOption.IGNORE_CASE).find(this)?.groupValues?.get(1)?:return emptyList();return Regex("\\\"([^\\\"]+)\\\"").findAll(content).map{it.groupValues[1]}.toList()}
}
