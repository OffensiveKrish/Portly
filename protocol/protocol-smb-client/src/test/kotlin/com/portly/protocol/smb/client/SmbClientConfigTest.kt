package com.portly.protocol.smb.client

import com.portly.core.model.ConnectionProfile
import com.portly.core.model.ProtocolError
import com.portly.core.model.ProtocolType
import jcifs.smb.NtStatus
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbClientConfigTest {

    @Test
    fun `default jcifs properties enforce SMB202 minimum and SMB311 maximum`() {
        val props = JcifsSmbProtocolClient.buildJcifsProperties(timeoutMs = 10_000, allowSmb1Legacy = false)
        assertEquals("SMB202", props.getProperty("jcifs.smb.client.minVersion"))
        assertEquals("SMB311", props.getProperty("jcifs.smb.client.maxVersion"))
        assertEquals("true", props.getProperty("jcifs.smb.client.enableSMB2"))
    }

    @Test
    fun `legacy toggle enables SMB1 negotiation only when explicitly requested`() {
        val props = JcifsSmbProtocolClient.buildJcifsProperties(timeoutMs = 10_000, allowSmb1Legacy = true)
        assertEquals("SMB1", props.getProperty("jcifs.smb.client.minVersion"))
        assertEquals("SMB311", props.getProperty("jcifs.smb.client.maxVersion"))
    }

    @Test
    fun `smb url builder supports share discovery root and normalized nested paths`() {
        val browseRootProfile = ConnectionProfile(
            id = "1",
            name = "NAS",
            protocol = ProtocolType.SMB,
            host = "192.168.1.20",
            port = 445,
        )
        assertEquals("smb://192.168.1.20/", JcifsSmbProtocolClient.buildSmbUrl(browseRootProfile, "/"))
        assertEquals("smb://192.168.1.20/Public/docs/", JcifsSmbProtocolClient.buildSmbUrl(browseRootProfile, "/Public/../Public/docs", ensureTrailingSlash = true))

        val shareProfile = browseRootProfile.copy(smbShare = "Media", port = 4445)
        assertEquals("smb://192.168.1.20:4445/Media/Movies/", JcifsSmbProtocolClient.buildSmbUrl(shareProfile, "/Movies", ensureTrailingSlash = true))
    }

    @Test
    fun `nt status errors map cleanly to ProtocolError subtypes`() {
        assertTrue(JcifsSmbProtocolClient.mapSmbError(SmbException(NtStatus.NT_STATUS_LOGON_FAILURE, false)) is ProtocolError.AuthenticationFailed)
        assertTrue(JcifsSmbProtocolClient.mapSmbError(SmbException(NtStatus.NT_STATUS_ACCESS_DENIED, false)) is ProtocolError.PermissionDenied)
        assertTrue(JcifsSmbProtocolClient.mapSmbError(SmbException(NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND, false)) is ProtocolError.PathNotFound)
    }
}
