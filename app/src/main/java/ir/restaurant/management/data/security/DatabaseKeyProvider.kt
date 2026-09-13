package ir.restaurant.management.data.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyUnavailableException(cause: Throwable) :
    IllegalStateException("کلید پایگاه داده در دسترس نیست؛ بازیابی امن لازم است.", cause)

class DatabaseKeyProvider(private val context: Context) {
    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = preferences.getString(WRAPPED_KEY, null)
        return try {
            if (wrapped == null) {
                val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
                // The encrypted key must be durably stored before it is returned to the caller.
                preferences.edit()
                    .putString(WRAPPED_KEY, wrap(passphrase))
                    .commit()
                    .also { require(it) { "ذخیره کلید پایگاه داده انجام نشد." } }
                passphrase
            } else {
                unwrap(wrapped)
            }
        } catch (error: DatabaseKeyUnavailableException) {
            throw error
        } catch (error: Throwable) {
            throw DatabaseKeyUnavailableException(error)
        }
    }

    /** Protects a database passphrase for local restore metadata using Android Keystore. */
    fun protectPassphrase(passphrase: ByteArray): String {
        require(passphrase.size == PASSPHRASE_BYTES) { "طول کلید پایگاه داده معتبر نیست." }
        return wrap(passphrase)
    }

    fun unprotectPassphrase(protectedValue: String): ByteArray = unwrap(protectedValue)

    /** Encrypts small application secrets (for example sync tokens) with Android Keystore. */
    fun protectSecret(value: String): String = if (value.isBlank()) "" else wrap(value.toByteArray(Charsets.UTF_8))

    fun unprotectSecret(protectedValue: String): String =
        if (protectedValue.isBlank()) "" else unwrapRaw(protectedValue).toString(Charsets.UTF_8)

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun stageRestorePassphrase(passphrase: ByteArray) {
        require(passphrase.size == PASSPHRASE_BYTES) { "طول کلید پایگاه داده معتبر نیست." }
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        check(preferences.edit().putString(PENDING_WRAPPED_KEY, wrap(passphrase)).commit()) {
            "آماده‌سازی کلید بازیابی انجام نشد."
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun activateStagedRestorePassphrase() {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = preferences.getString(PENDING_WRAPPED_KEY, null) ?: return
        if (preferences.getString(PREVIOUS_WRAPPED_KEY, null) == null) {
            val current = preferences.getString(WRAPPED_KEY, null)
            val editor = preferences.edit().putString(WRAPPED_KEY, pending)
            if (current != null) editor.putString(PREVIOUS_WRAPPED_KEY, current)
            check(editor.commit()) { "فعال‌سازی کلید بازیابی انجام نشد." }
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun commitRestorePassphrase() {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        check(preferences.edit().remove(PENDING_WRAPPED_KEY).remove(PREVIOUS_WRAPPED_KEY).commit()) {
            "نهایی‌سازی کلید بازیابی انجام نشد."
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun rollbackRestorePassphrase() {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previous = preferences.getString(PREVIOUS_WRAPPED_KEY, null)
        val editor = preferences.edit().remove(PENDING_WRAPPED_KEY).remove(PREVIOUS_WRAPPED_KEY)
        if (previous != null) editor.putString(WRAPPED_KEY, previous)
        check(editor.commit()) { "بازگردانی کلید پایگاه داده انجام نشد." }
    }

    private fun wrap(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(value)
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun unwrap(value: String): ByteArray = unwrapRaw(value).also {
        require(it.size == PASSPHRASE_BYTES) { "طول کلید پایگاه داده معتبر نیست." }
    }

    private fun unwrapRaw(value: String): ByteArray {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "ساختار کلید رمزگذاری‌شده معتبر نیست." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(128, packed.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size))
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "restaurant-management_v3_secure"
        const val WRAPPED_KEY = "wrapped_database_key"
        const val PENDING_WRAPPED_KEY = "pending_wrapped_database_key"
        const val PREVIOUS_WRAPPED_KEY = "previous_wrapped_database_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "restaurant-management_v3_database_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val IV_BYTES = 12
    }
}
