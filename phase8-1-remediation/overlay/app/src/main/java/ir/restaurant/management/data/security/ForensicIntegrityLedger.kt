package ir.restaurant.management.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

data class ForensicReceipt(
    val sequence: Long,
    val operationType: String,
    val requestEpochMillis: Long,
    val completionEpochMillis: Long,
    val actorId: Long?,
    val actor: String,
    val deviceId: String,
    val sourceDbFingerprint: String,
    val destinationDbFingerprint: String,
    val backupChecksum: String,
    val auditTerminalHash: String,
    val schemaVersion: Int,
    val correlationId: String,
    val result: String,
)

data class ForensicLedgerVerification(val valid: Boolean, val receiptCount: Int, val failure: String? = null, val terminalMac: String = "")

/** HMAC-chained, app-private and no-backup forensic receipts outside the restorable Room database. */
class ForensicIntegrityLedger(context: Context) {
    private val file = File(context.noBackupFilesDir, "phase81-forensic-integrity-ledger.log")
    private val key: SecretKey by lazy(::getOrCreateKey)

    @Synchronized
    fun append(
        operationType: String,
        requestEpochMillis: Long,
        completionEpochMillis: Long = System.currentTimeMillis(),
        actorId: Long?,
        actor: String,
        deviceId: String,
        sourceDbFingerprint: String = "",
        destinationDbFingerprint: String = "",
        backupChecksum: String = "",
        auditTerminalHash: String = "",
        schemaVersion: Int,
        correlationId: String,
        result: String,
    ): ForensicReceipt {
        require(operationType.matches(Regex("[A-Z0-9_]{3,80}")))
        require(correlationId.isNotBlank() && result.isNotBlank())
        val previous = readVerifiedLines()
        val sequence = (previous.lastOrNull()?.first ?: 0L) + 1L
        val previousMac = previous.lastOrNull()?.second.orEmpty()
        val receipt = ForensicReceipt(sequence, operationType, requestEpochMillis, completionEpochMillis, actorId, actor, deviceId, sourceDbFingerprint, destinationDbFingerprint, backupChecksum, auditTerminalHash, schemaVersion, correlationId, result)
        val payload = payload(receipt, previousMac)
        val mac = hmac(payload)
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { output ->
            output.write((payload + "|" + mac + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        return receipt
    }

    @Synchronized
    fun verify(): ForensicLedgerVerification = try {
        val rows = readVerifiedLines()
        ForensicLedgerVerification(true, rows.size, terminalMac = rows.lastOrNull()?.second.orEmpty())
    } catch (error: Throwable) {
        ForensicLedgerVerification(false, 0, error.message ?: error.javaClass.simpleName)
    }

    @Synchronized
    fun latestAuditAnchorHash(): String? {
        if (!file.exists()) return null
        file.readLines(Charsets.UTF_8).asReversed().forEach { line ->
            if (line.isBlank()) return@forEach
            val payload = line.substringBeforeLast('|')
            val parts = payload.split('|')
            if (parts.size == FIELD_COUNT && decode(parts[2]) == "AUDIT_ANCHOR") {
                return decode(parts[11]).takeIf(String::isNotBlank)
            }
        }
        return null
    }

    private fun readVerifiedLines(): List<Pair<Long, String>> {
        if (!file.exists()) return emptyList()
        var expectedSequence = 1L
        var previousMac = ""
        val result = mutableListOf<Pair<Long, String>>()
        file.readLines(Charsets.UTF_8).filter(String::isNotBlank).forEach { line ->
            val cut = line.lastIndexOf('|')
            require(cut > 0) { "FORENSIC_RECEIPT_FORMAT_INVALID" }
            val payload = line.substring(0, cut)
            val storedMac = line.substring(cut + 1)
            require(MessageDigest.isEqual(hexToBytes(storedMac), hexToBytes(hmac(payload)))) { "FORENSIC_RECEIPT_HMAC_MISMATCH" }
            val parts = payload.split('|')
            require(parts.size == FIELD_COUNT) { "FORENSIC_RECEIPT_FIELD_COUNT" }
            val sequence = parts[0].toLong()
            require(sequence == expectedSequence) { "FORENSIC_RECEIPT_SEQUENCE_GAP" }
            require(decode(parts[1]) == previousMac) { "FORENSIC_RECEIPT_CHAIN_BROKEN" }
            expectedSequence++
            previousMac = storedMac
            result += sequence to storedMac
        }
        return result
    }

    private fun payload(r: ForensicReceipt, previousMac: String): String = listOf(
        r.sequence.toString(), encode(previousMac), encode(r.operationType), r.requestEpochMillis.toString(), r.completionEpochMillis.toString(),
        r.actorId?.toString().orEmpty(), encode(r.actor), encode(r.deviceId), encode(r.sourceDbFingerprint), encode(r.destinationDbFingerprint),
        encode(r.backupChecksum), encode(r.auditTerminalHash), r.schemaVersion.toString(), encode(r.correlationId), encode(r.result),
    ).joinToString("|")

    private fun hmac(payload: String): String = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val KEY_ALIAS = "sabou_phase81_forensic_hmac_v1"
        private const val FIELD_COUNT = 15
        private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
        private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        private fun hexToBytes(value: String): ByteArray {
            require(value.length % 2 == 0 && value.matches(Regex("[0-9a-f]*")))
            return ByteArray(value.length / 2) { i -> value.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
        fun fingerprint(file: File): String {
            if (!file.isFile) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
