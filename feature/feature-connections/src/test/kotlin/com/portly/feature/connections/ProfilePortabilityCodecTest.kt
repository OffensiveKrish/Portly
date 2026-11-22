package com.portly.feature.connections

import com.portly.core.model.AuthType
import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ProtocolType
import com.portly.core.model.ResolvedCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePortabilityCodecTest {

    private val sampleProfile = ConnectionProfile(
        id = "prof-1",
        name = "NAS SFTP",
        protocol = ProtocolType.SFTP,
        host = "192.168.1.50",
        port = 2222,
        username = "krish",
        authType = AuthType.PASSWORD,
        rootPath = "/media",
        notes = "Backup NAS",
        isFavorite = true,
    )

    @Test
    fun `export excludes credentials by default and never leaks plaintext`() {
        val json = ProfilePortabilityCodec.exportProfiles(
            profiles = listOf(sampleProfile),
            credentialsByProfileId = mapOf("prof-1" to ResolvedCredentials("krish", "TopSecretPass99!")),
            includeEncryptedCredentials = false,
        )
        assertFalse(json.contains("TopSecretPass99!"))
        assertFalse(ProfilePortabilityCodec.bundleHasEncryptedSecrets(json))

        val imported = ProfilePortabilityCodec.importProfiles(json)
        assertEquals(1, imported.size)
        assertEquals("NAS SFTP", imported[0].first.name)
        assertNull(imported[0].second)
    }

    @Test
    fun `export with encrypted credentials round-trips via 600k PBKDF2 passphrase`() {
        val passphrase = "my-strong-export-passphrase".toCharArray()
        val json = ProfilePortabilityCodec.exportProfiles(
            profiles = listOf(sampleProfile),
            credentialsByProfileId = mapOf("prof-1" to ResolvedCredentials("krish", "TopSecretPass99!")),
            includeEncryptedCredentials = true,
            passphrase = passphrase,
        )
        assertFalse(json.contains("TopSecretPass99!"))
        assertTrue(ProfilePortabilityCodec.bundleHasEncryptedSecrets(json))

        val imported = ProfilePortabilityCodec.importProfiles(json, passphrase)
        assertEquals(1, imported.size)
        assertEquals("TopSecretPass99!", imported[0].second?.password)
    }

    @Test
    fun `quick connect URL parser extracts protocol, user, host, port, and root path`() {
        val parsed = ProfilePortabilityCodec.parseQuickConnectInput("sftp://alice@10.0.0.5:2222/share/docs")
        assertNotNull(parsed)
        assertEquals(ProtocolType.SFTP, parsed!!.protocol)
        assertEquals("alice", parsed.username)
        assertEquals("10.0.0.5", parsed.host)
        assertEquals(2222, parsed.port)
        assertEquals("/share/docs", parsed.rootPath)
    }
}
