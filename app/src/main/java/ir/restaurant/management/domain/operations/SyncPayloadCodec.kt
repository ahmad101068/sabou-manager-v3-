package ir.restaurant.management.domain.operations

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object SyncPayloadCodec {
    fun canonicalize(fields: Map<String, String>): String {
        require(fields.isNotEmpty()) { "payload همگام‌سازی خالی است." }
        require(fields.keys.none(String::isBlank)) { "کلید payload معتبر نیست." }
        return fields.toSortedMap().entries.joinToString(separator = "\n") { (key, value) ->
            "${key.toUtf8Hex()}=${value.toUtf8Hex()}"
        }
    }

    fun sha256(payload: String): String {
        require(payload.isNotEmpty())
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    fun verify(payload: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            sha256(payload).toByteArray(StandardCharsets.US_ASCII),
            expectedHash.lowercase().toByteArray(StandardCharsets.US_ASCII),
        )

    private fun String.toUtf8Hex(): String = toByteArray(StandardCharsets.UTF_8)
        .joinToString("") { byte -> "%02x".format(byte) }
}
