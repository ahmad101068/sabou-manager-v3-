package ir.restaurant.management.data.security

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-protected, authenticated and installation-independent backup envelope. */
object PortableBackupCodec {
    fun encrypt(
        databaseKey: ByteArray,
        database: InputStream,
        password: CharArray,
        destination: OutputStream,
        manifest: BackupManifest,
    ): Long {
        require(databaseKey.size == DATABASE_KEY_BYTES) { "کلید پایگاه داده معتبر نیست." }
        validatePassword(password)
        val manifestBytes = manifest.encode()
        require(manifestBytes.size <= MAX_MANIFEST_BYTES) { "Manifest پشتیبان بیش از حد بزرگ است." }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val header = header(salt, iv, version = CURRENT_VERSION)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header)
        }
        destination.write(header)
        var databaseBytes = 0L
        CipherOutputStream(destination, cipher).use { encrypted ->
            encrypted.write(databaseKey)
            DataOutputStream(encrypted).apply {
                writeInt(manifestBytes.size)
                write(manifestBytes)
                flush()
            }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = database.read(buffer)
                if (read < 0) break
                encrypted.write(buffer, 0, read)
                databaseBytes += read
            }
        }
        return databaseBytes
    }

    fun decrypt(
        source: InputStream,
        password: CharArray,
        databaseDestination: OutputStream,
        maxDatabaseBytes: Long = Long.MAX_VALUE,
    ): PortableBackupPayload {
        validatePassword(password)
        require(maxDatabaseBytes > 0L) { "حداکثر اندازه پشتیبان معتبر نیست." }
        val input = DataInputStream(source)
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "این فایل، پشتیبان قابل‌انتقال مدیریت رستوران نیست." }
        val version = input.readInt()
        require(version == LEGACY_VERSION || version == CURRENT_VERSION) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود." }
        val iterations = input.readInt()
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) { "پارامتر امنیتی فایل معتبر نیست." }
        val salt = ByteArray(SALT_BYTES).also(input::readFully)
        val iv = ByteArray(IV_BYTES).also(input::readFully)
        val header = header(salt, iv, iterations, version)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header)
        }
        val databaseKey = ByteArray(DATABASE_KEY_BYTES)
        return try {
            CipherInputStream(input, cipher).use { decrypted ->
                val decryptedInput = DataInputStream(decrypted)
                decryptedInput.readFully(databaseKey)
                val manifest = if (version >= CURRENT_VERSION) {
                    val manifestSize = decryptedInput.readInt()
                    require(manifestSize in 1..MAX_MANIFEST_BYTES) { "اندازه Manifest فایل معتبر نیست." }
                    val manifestBytes = ByteArray(manifestSize).also(decryptedInput::readFully)
                    BackupManifest.decode(manifestBytes)
                } else {
                    null
                }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var databaseBytes = 0L
                while (true) {
                    val read = decryptedInput.read(buffer)
                    if (read < 0) break
                    val nextSize = databaseBytes + read
                    require(nextSize <= maxDatabaseBytes) {
                        "اندازه پایگاه داده پشتیبان از سقف مجاز بیشتر است."
                    }
                    databaseDestination.write(buffer, 0, read)
                    databaseBytes = nextSize
                }
                PortableBackupPayload(databaseKey, manifest)
            }
        } catch (error: Throwable) {
            databaseKey.fill(0)
            throw error
        }
    }

    private fun validatePassword(password: CharArray) {
        require(password.size >= MIN_PASSWORD_CHARS) { "رمز پشتیبان باید حداقل $MIN_PASSWORD_CHARS نویسه باشد." }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, AES_KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(PBKDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun header(
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int = ITERATIONS,
        version: Int = CURRENT_VERSION,
    ): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(version)
                output.writeInt(iterations)
                output.write(salt)
                output.write(iv)
            }
            bytes.toByteArray()
        }

    private val MAGIC = byteArrayOf(0x53, 0x41, 0x42, 0x4f, 0x55, 0x42, 0x4b, 0x32) // portable-backup envelope signature
    private const val LEGACY_VERSION = 2
    private const val CURRENT_VERSION = 3
    private const val ITERATIONS = 310_000
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 2_000_000
    private const val MIN_PASSWORD_CHARS = 10
    private const val DATABASE_KEY_BYTES = 32
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val AES_KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val PBKDF = "PBKDF2WithHmacSHA1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}

data class PortableBackupPayload(
    val databaseKey: ByteArray,
    val manifest: BackupManifest?,
)
