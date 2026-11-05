package com.commerceos.android.session

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
 * Keystore-backed encrypted credential store. Values are AES/GCM-encrypted with a
 * per-alias key held in AndroidKeyStore; only ciphertext ever touches
 * SharedPreferences. Used to persist the CommerceOS access/refresh tokens so a
 * session survives process death without exposing secrets in plaintext.
 */
open class SecureCredentialStore(private val context: Context? = null) {

    private val prefs by lazy { context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    open fun save(alias: String, value: String): Boolean = try {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + ciphertext
        prefs?.edit()?.putString(alias, Base64.encodeToString(payload, Base64.NO_WRAP))?.commit() ?: false
    } catch (e: Exception) {
        false
    }

    sealed class SecureReadResult {
        data class Success(val value: String) : SecureReadResult()
        object NotFound : SecureReadResult()
        data class DecryptError(val cause: Throwable) : SecureReadResult()
    }

    open fun readWithResult(alias: String): SecureReadResult {
        if (context == null) {
            val overridden = try {
                javaClass.getMethod("read", String::class.java).declaringClass != SecureCredentialStore::class.java
            } catch (_: Exception) {
                false
            }
            if (overridden) {
                val v = read(alias)
                return if (v != null) SecureReadResult.Success(v) else SecureReadResult.NotFound
            }
            return SecureReadResult.NotFound
        }
        return try {
            val stored = prefs?.getString(alias, null) ?: return SecureReadResult.NotFound
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
            val key = getOrCreateKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            SecureReadResult.Success(decrypted)
        } catch (e: Exception) {
            SecureReadResult.DecryptError(e)
        }
    }

    open fun read(alias: String): String? {
        return when (val res = readWithResult(alias)) {
            is SecureReadResult.Success -> res.value
            else -> null
        }
    }

    open fun encryptBytes(alias: String, bytes: ByteArray): ByteArray? = try {
        if (context == null) {
            bytes
        } else {
            val key = getOrCreateKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(bytes)
            cipher.iv + ciphertext
        }
    } catch (_: Exception) {
        null
    }

    open fun decryptBytes(alias: String, encryptedPayload: ByteArray): ByteArray? = try {
        if (context == null) {
            encryptedPayload
        } else if (encryptedPayload.size <= IV_LENGTH) {
            null
        } else {
            val iv = encryptedPayload.copyOfRange(0, IV_LENGTH)
            val ciphertext = encryptedPayload.copyOfRange(IV_LENGTH, encryptedPayload.size)
            val key = getOrCreateKey(alias)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        }
    } catch (_: Exception) {
        null
    }

    open fun clear(alias: String): Boolean {
        return try {
            prefs?.edit()?.remove(alias)?.commit() ?: true
        } catch (e: Exception) {
            false
        }
    }

    open fun deleteKeyAndValue(alias: String): Boolean {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            // best-effort key removal; the value is still cleared below
        }
        return clear(alias)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS_NAME = "commerce_os_secure_credentials"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
