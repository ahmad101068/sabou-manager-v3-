package ir.restaurant.management.data.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Authenticated metadata carried beside local backups and inside portable backup envelopes. */
data class BackupManifest(
    val applicationId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val sourceDeviceId: String,
    val databaseSizeBytes: Long,
    val databaseSha256: String,
    val tableRecordCounts: Map<String, Long>,
) {
    init {
        require(applicationId.length in 3..120) { "شناسه برنامه در Manifest معتبر نیست." }
        require(appVersionName.length in 1..120) { "نسخه برنامه در Manifest معتبر نیست." }
        require(appVersionCode >= 0L && schemaVersion >= 0) { "نسخه‌های Manifest معتبر نیستند." }
        require(createdAtEpochMillis > 0L && databaseSizeBytes >= 0L) { "مشخصات زمانی یا اندازه Manifest معتبر نیست." }
        require(sourceDeviceId.length in 1..120) { "شناسه دستگاه در Manifest معتبر نیست." }
        require(databaseSha256.matches(SHA256_PATTERN)) { "اثر انگشت Manifest معتبر نیست." }
        require(tableRecordCounts.size <= MAX_TABLES) { "تعداد جدول‌های Manifest بیش از حد مجاز است." }
        require(tableRecordCounts.all { (table, count) -> table.matches(TABLE_NAME_PATTERN) && count >= 0L }) {
            "شمارنده جدول‌های Manifest معتبر نیست."
        }
    }

    fun encode(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(applicationId)
            output.writeUTF(appVersionName)
            output.writeLong(appVersionCode)
            output.writeInt(schemaVersion)
            output.writeLong(createdAtEpochMillis)
            output.writeUTF(sourceDeviceId)
            output.writeLong(databaseSizeBytes)
            output.writeUTF(databaseSha256)
            val sortedCounts = tableRecordCounts.toSortedMap()
            output.writeInt(sortedCounts.size)
            sortedCounts.forEach { (table, count) ->
                output.writeUTF(table)
                output.writeLong(count)
            }
        }
        bytes.toByteArray()
    }

    fun requireCompatibleDatabase(
        expectedApplicationId: String,
        maximumSchemaVersion: Int,
        actualSizeBytes: Long,
        actualSha256: String,
    ) {
        require(applicationId == expectedApplicationId) { "این پشتیبان متعلق به برنامه دیگری است." }
        require(schemaVersion == LEGACY_UNKNOWN_SCHEMA || schemaVersion in 1..maximumSchemaVersion) {
            "نسخه ساختار پایگاه داده پشتیبان پشتیبانی نمی‌شود."
        }
        require(databaseSizeBytes == actualSizeBytes) { "اندازه پایگاه داده با Manifest مطابقت ندارد." }
        require(databaseSha256 == actualSha256) { "اثر انگشت پایگاه داده با Manifest مطابقت ندارد." }
    }

    companion object {
        const val LEGACY_UNKNOWN_SCHEMA = 0
        private const val MAGIC = 0x53424d31 // SBM1
        private const val FORMAT_VERSION = 1
        private const val MAX_MANIFEST_BYTES = 256 * 1024
        private const val MAX_TABLES = 256
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val TABLE_NAME_PATTERN = Regex("[A-Za-z0-9_]{1,80}")

        fun decode(bytes: ByteArray): BackupManifest {
            require(bytes.size in 1..MAX_MANIFEST_BYTES) { "اندازه Manifest معتبر نیست." }
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC) { "امضای Manifest معتبر نیست." }
                require(input.readInt() == FORMAT_VERSION) { "نسخه Manifest پشتیبانی نمی‌شود." }
                val applicationId = input.readUTF()
                val appVersionName = input.readUTF()
                val appVersionCode = input.readLong()
                val schemaVersion = input.readInt()
                val createdAt = input.readLong()
                val sourceDeviceId = input.readUTF()
                val databaseSize = input.readLong()
                val databaseSha256 = input.readUTF()
                val count = input.readInt()
                require(count in 0..MAX_TABLES) { "تعداد جدول‌های Manifest معتبر نیست." }
                val counts = linkedMapOf<String, Long>()
                repeat(count) {
                    val table = input.readUTF()
                    require(table.matches(TABLE_NAME_PATTERN) && table !in counts) { "نام جدول Manifest معتبر نیست." }
                    val records = input.readLong()
                    require(records >= 0L) { "تعداد رکورد Manifest معتبر نیست." }
                    counts[table] = records
                }
                require(input.available() == 0) { "Manifest دارای داده اضافی ناشناخته است." }
                BackupManifest(
                    applicationId = applicationId,
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    schemaVersion = schemaVersion,
                    createdAtEpochMillis = createdAt,
                    sourceDeviceId = sourceDeviceId,
                    databaseSizeBytes = databaseSize,
                    databaseSha256 = databaseSha256,
                    tableRecordCounts = counts,
                )
            }
        }

        fun legacy(databaseSizeBytes: Long, databaseSha256: String, createdAtEpochMillis: Long): BackupManifest =
            BackupManifest(
                applicationId = "ir.restaurant.management",
                appVersionName = "legacy-portable-v2",
                appVersionCode = 0,
                schemaVersion = LEGACY_UNKNOWN_SCHEMA,
                createdAtEpochMillis = createdAtEpochMillis.coerceAtLeast(1L),
                sourceDeviceId = "legacy-unknown",
                databaseSizeBytes = databaseSizeBytes,
                databaseSha256 = databaseSha256,
                tableRecordCounts = emptyMap(),
            )
    }
}
