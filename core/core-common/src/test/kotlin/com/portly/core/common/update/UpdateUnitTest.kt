package com.portly.core.common.update

import com.portly.core.common.ChecksumUtils
import com.portly.core.model.ReleaseChannel
import com.portly.core.model.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVersionCodeParsingSupportsLegacySemVerAndPortlyCalVer() {
        assertEquals(1000000L, UpdateGitHubClient.parseVersionCodeFromVersionName("1"))
        assertEquals(1002000L, UpdateGitHubClient.parseVersionCodeFromVersionName("1.2"))
        assertEquals(1002003L, UpdateGitHubClient.parseVersionCodeFromVersionName("1.2.3"))
        assertEquals(2000000L, UpdateGitHubClient.parseVersionCodeFromVersionName("2.0.0-beta.1"))

        // Must exactly match version.properties: (YYYY * 100000) + (MM * 1000) + PATCH.
        assertEquals(202611000L, UpdateGitHubClient.parseVersionCodeFromVersionName("2026.11.0"))
        assertEquals(202611001L, UpdateGitHubClient.parseVersionCodeFromVersionName("2026.11.1"))
        assertEquals(202701000L, UpdateGitHubClient.parseVersionCodeFromVersionName("2027.01.0"))
        assertEquals(202611001L, UpdateGitHubClient.parseVersionCodeFromVersionName("2026.11.1-beta.2"))
        assertEquals(1L, UpdateGitHubClient.parseVersionCodeFromVersionName("2026.13.0"))
        assertEquals(1L, UpdateGitHubClient.parseVersionCodeFromVersionName("2026.11.1000"))
    }

    @Test
    fun testChecksumVerification() {
        val sampleFile = tempFolder.newFile("sample.txt")
        sampleFile.writeText("Portly secure update verification test content.")

        val sha256 = ChecksumUtils.computeFileDigest(sampleFile, "SHA-256")
        assertNotNull(sha256)
        assertEquals(64, sha256.length)

        val recomputed = ChecksumUtils.computeFileDigest(sampleFile, "SHA-256")
        assertEquals(sha256, recomputed)
    }

    @Test
    fun testUpdateInfoDefaults() {
        val info = UpdateInfo(
            versionName = "2026.11.0",
            versionCode = 202611000L,
            releaseChannel = ReleaseChannel.STABLE,
            releaseNotes = "Release migration",
            apkDownloadUrl = "https://example.com/Portly-2026.11.0.apk",
            apkSha256 = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            apkSizeBytes = 8000000L,
        )

        assertEquals("2026.11.0", info.versionName)
        assertEquals(ReleaseChannel.STABLE, info.releaseChannel)
        assertTrue(info.versionCode > 1)
        assertFalse(info.apkSha256.isNullOrBlank())
    }
}
