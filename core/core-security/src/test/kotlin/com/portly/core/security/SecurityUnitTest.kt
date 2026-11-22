package com.portly.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUnitTest {

    @Test
    fun `pbkdf2 round-trip encrypts and decrypts cleanly`() {
        val secret = """{"username":"alice","password":"super-secret-value"}"""
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val encrypted = Pbkdf2Crypto.encryptWithPassphrase(secret, passphrase)
        assertFalse(encrypted.contains("super-secret-value"))
        assertTrue(encrypted.startsWith("pbkdf2v1:600000:"))

        val decrypted = Pbkdf2Crypto.decryptWithPassphrase(encrypted, passphrase)
        assertEquals(secret, decrypted)
    }

    @Test(expected = Exception::class)
    fun `pbkdf2 fails with wrong passphrase`() {
        val encrypted = Pbkdf2Crypto.encryptWithPassphrase("secret", "right-pass".toCharArray())
        Pbkdf2Crypto.decryptWithPassphrase(encrypted, "wrong-pass".toCharArray())
    }

    @Test
    fun `log redactor strips passwords headers tokens and external paths`() {
        val raw = "USER alice PASS mySecret99 Authorization: Bearer abc.def.ghi /storage/emulated/0/Private/secret.txt ?token=xyz123"
        val redacted = LogRedactor.redact(raw, allowedServerRoot = "/storage/emulated/0/PortlyShare")
        assertFalse(redacted.contains("mySecret99"))
        assertFalse(redacted.contains("abc.def.ghi"))
        assertFalse(redacted.contains("xyz123"))
        assertFalse(redacted.contains("Private/secret.txt"))
    }

    @Test
    fun `self-signed cert generation yields valid SHA-256 fingerprint`() {
        val certPair = CertificateManager.createSelfSignedCertificate(
            commonName = "Portly Test",
            sanIpAddresses = listOf("192.168.1.42"),
        )
        val fp = CertificateManager.sha256Fingerprint(certPair.certificate)
        assertEquals(32 * 2 + 31, fp.length)
        assertNotEquals("", fp)
    }

    @Test
    fun `enterprise policy map parser honors defaults and explicit overrides`() {
        val defaults = EnterprisePolicyManager.fromMap(emptyMap())
        assertFalse(defaults.isManaged)
        assertTrue(defaults.allowPublicServerBind)

        val managed = EnterprisePolicyManager.fromMap(
            mapOf(
                EnterprisePolicyManager.KEY_DISABLE_PLAIN_FTP to true,
                EnterprisePolicyManager.KEY_DISABLE_SMB1_LEGACY to true,
                EnterprisePolicyManager.KEY_ALLOW_PUBLIC_SERVER_BIND to false,
                EnterprisePolicyManager.KEY_REQUIRE_BIOMETRIC_LOCK to true,
                EnterprisePolicyManager.KEY_DISABLE_SERVERS to false,
                EnterprisePolicyManager.KEY_DIAGNOSTIC_LOGGING_ENABLED to true,
            ),
        )
        assertTrue(managed.isManaged)
        assertTrue(managed.disablePlainFtp)
        assertTrue(managed.disableSmb1Legacy)
        assertFalse(managed.allowPublicServerBind)
        assertTrue(managed.requireBiometricLock)
        assertEquals(true, managed.diagnosticLoggingOverride)
    }
}
