package com.portly.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based AES-256-GCM encryption for versioned profile export/import.
 * Enforces 600,000 PBKDF2-HMAC-SHA256 iterations per OWASP 2023+ guidance.
 */
object Pbkdf2Crypto {
    const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

    fun encryptWithPassphrase(plaintext: String, passphrase: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getEncoder()
        return listOf(
            "pbkdf2v1",
            PBKDF2_ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(iv),
            encoder.encodeToString(encrypted),
        ).joinToString(":")
    }

    fun decryptWithPassphrase(payload: String, passphrase: CharArray): String {
        val parts = payload.split(':')
        require(parts.size == 5 && parts[0] == "pbkdf2v1") { "Unsupported encrypted export format" }
        val iterations = parts[1].toIntOrNull()?.takeIf { it >= 100_000 }
            ?: throw IllegalArgumentException("Invalid PBKDF2 iteration count")
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[2])
        val iv = decoder.decode(parts[3])
        val body = decoder.decode(parts[4])

        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(body)
        return String(plain, Charsets.UTF_8)
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
