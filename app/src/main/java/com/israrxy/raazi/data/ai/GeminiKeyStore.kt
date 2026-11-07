package com.israrxy.raazi.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the user's personal Gemini API key on this device only.
 *
 * The key is encrypted with a non-exportable Android Keystore AES-GCM key, so
 * it never lands in DataStore, Drive sync, or a plain preferences file. The
 * preferences file is also excluded from Android auto-backup.
 */
class GeminiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = !readKey().isNullOrBlank()

    fun saveKey(key: String) {
        val normalized = key.trim()
        require(normalized.isNotBlank()) { "Enter a Gemini API key first." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Returns the decrypted key, or null when nothing is stored or the Keystore
     * entry was invalidated. Callers then simply ask the user to enter it again.
     */
    fun readKey(): String? {
        val encodedIv = prefs.getString(KEY_IV, null) ?: return null
        val encodedCiphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return runCatching {
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrElse {
            clearKey()
            null
        }
    }

    fun clearKey() {
        prefs.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .apply()
        runCatching {
            val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keystore.deleteEntry(KEY_ALIAS)
        }
    }

    fun readModel(): String =
        prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

    fun saveModel(model: String) {
        val normalized = model.trim()
        if (normalized.isBlank()) return
        prefs.edit().putString(KEY_MODEL, normalized).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey
            ?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        /** Default requested model. Availability is verified when the key is tested. */
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "raazi_gemini_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_FILE = "raazi_gemini"
        private const val KEY_IV = "key_iv"
        private const val KEY_CIPHERTEXT = "key_ciphertext"
        private const val KEY_MODEL = "model"
    }
}
