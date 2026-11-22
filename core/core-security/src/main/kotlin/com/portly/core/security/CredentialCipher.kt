package com.portly.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

interface CredentialCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

object AesGcmCodec {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    fun encryptWithKey(key: SecretKey, plaintext: String, iv: ByteArray? = null): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (iv != null) {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        }
        val usedIv = cipher.iv ?: error("Cipher did not generate IV")
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(usedIv, Base64.NO_WRAP)
        val bodyB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "v1:$ivB64:$bodyB64"
    }

    fun decryptWithKey(key: SecretKey, payload: String): String {
        val parts = payload.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Malformed encrypted credential payload" }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == IV_LENGTH_BYTES) { "Invalid GCM IV length" }
        val body = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plain = cipher.doFinal(body)
        return String(plain, Charsets.UTF_8)
    }
}

@Singleton
class KeystoreCredentialCipher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CredentialCipher {

    private val lock = Any()

    override fun encrypt(plaintext: String): String = synchronized(lock) {
        val key = getOrCreateKey()
        // When AndroidKeyStore is used, Keystore generates a fresh 12-byte IV per encrypt().
        // When fallback SecretKeySpec is used, pass explicit random 12-byte IV.
        val explicitIv = if (key is SecretKeySpec) {
            ByteArray(12).also { SecureRandom().nextBytes(it) }
        } else {
            null
        }
        AesGcmCodec.encryptWithKey(key, plaintext, explicitIv)
    }

    override fun decrypt(ciphertext: String): String = synchronized(lock) {
        val key = getOrCreateKey()
        AesGcmCodec.decryptWithKey(key, ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        }.getOrElse {
            getOrCreateFallbackKey()
        }
    }

    @Suppress("DEPRECATION")
    private fun getOrCreateFallbackKey(): SecretKey {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            FALLBACK_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        val encoded = prefs.getString(FALLBACK_KEY_PREF, null)
        val keyBytes = if (encoded != null) {
            Base64.decode(encoded, Base64.NO_WRAP)
        } else {
            ByteArray(32).also { generated ->
                SecureRandom().nextBytes(generated)
                prefs.edit().putString(
                    FALLBACK_KEY_PREF,
                    Base64.encodeToString(generated, Base64.NO_WRAP),
                ).apply()
            }
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "portly_credential_aes256_gcm_v1"
        const val FALLBACK_PREFS_NAME = "portly_credential_fallback_prefs"
        const val FALLBACK_KEY_PREF = "aes_key_b64"
    }
}
