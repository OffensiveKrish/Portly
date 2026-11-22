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
    fun testVersionCodeParsing() {
        assertEquals(1000000L, UpdateGitHubClient.parseVersionCodeFromVersionName("1"))
        assertEquals(1002000L, UpdateGitHubClient.parseVersionCodeFromVersionName("1.2"))
        assertEquals(1002003L, UpdateGitHubClient.parseVersionCodeFromVersionName("1.2.3"))
        assertEquals(2000000L, UpdateGitHubClient.parseVersionCodeFromVersionName("2.0.0-beta.1"))
    }

    @Test
    fun testChecksumVerification() {
        val sampleFile = tempFolder.newFile("sample.txt")
        sampleFile.writeText("Portly secure update verification test content.")

        val sha256 = ChecksumUtils.computeFileDigest(sampleFile, "SHA-256")
        assertNotNull(sha256)
        assertEquals(64, sha256.length)

        // Verify matches itself
        val recomputed = ChecksumUtils.computeFileDigest(sampleFile, "SHA-256")
        assertEquals(sha256, recomputed)
    }

    @Test
    fun testUpdateInfoDefaults() {
        val info = UpdateInfo(
            versionName = "1.1.0",
            versionCode = 1001000L,
            releaseChannel = ReleaseChannel.STABLE,
            releaseNotes = "New features",
            apkDownloadUrl = "https://example.com/Portly.apk",
            apkSha256 = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            apkSizeBytes = 8000000L,
        )

        assertEquals("1.1.0", info.versionName)
        assertEquals(ReleaseChannel.STABLE, info.releaseChannel)
        assertTrue(info.versionCode > 1)
        assertFalse(info.apkSha256.isNullOrBlank())
    }
}
