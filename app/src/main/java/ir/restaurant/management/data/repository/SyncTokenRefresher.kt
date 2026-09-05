package ir.restaurant.management.data.repository

import ir.restaurant.management.domain.operations.CloudSyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.HttpsURLConnection
import java.net.URL

class SyncTokenRefresher {
    suspend fun refresh(config: CloudSyncConfig): CloudSyncConfig = withContext(Dispatchers.IO) {
        val endpoint = config.normalizedHttpsEndpoint()
        require(config.organizationId.isNotBlank() && config.refreshToken.isNotBlank() && config.deviceId.isNotBlank()) { "اطلاعات تمدید توکن Sync کامل نیست." }
        val connection=((URL(endpoint+"/v1/auth/refresh").openConnection() as? HttpsURLConnection)
            ?: error("HTTPS connection provider is unavailable.")).apply{
            requestMethod="POST";connectTimeout=15_000;readTimeout=20_000;doOutput=true;instanceFollowRedirects=false;useCaches=false
            setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("X-Organization-Id",config.organizationId);setRequestProperty("X-Device-Id",config.deviceId)
        }
        val body="{\"refreshToken\":\"${config.refreshToken.json()}\",\"deviceId\":\"${config.deviceId.json()}\"}"
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(code in 200..299) { "تمدید توکن Sync با پاسخ $code ناموفق بود." }
            val token = requireNotNull(response.stringValue("accessToken")) { "سرویس accessToken برنگرداند." }
            val expires = (response.longValue("expiresInSeconds") ?: 900L).coerceIn(60L, 3_600L)
            config.copy(accessToken = token, accessTokenExpiresAtEpochMillis = System.currentTimeMillis() + expires * 1_000L)
        } finally {
            connection.disconnect()
        }
    }
    private fun String.json()=replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")
    private fun String.stringValue(key:String)=Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(this)?.groupValues?.get(1)
    private fun String.longValue(key:String)=Regex("\\\"$key\\\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull()
}
