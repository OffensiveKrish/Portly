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

class UpdateUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCalVerVersionParsing() {
        val v1 = PortlyVersion.parse("2026.11.0")
        assertNotNull(v1)
        assertEquals(2026, v1!!.yearOrMajor)
        assertEquals(11, v1.monthOrMinor)
        assertEquals(0, v1.patch)
        assertTrue(v1.isCalVer)
        assertFalse(v1.isPreRelease)
        assertEquals(202611000L, v1.estimatedVersionCode)

        val v2 = PortlyVersion.parse("v2026.11.1")
        assertNotNull(v2)
        assertEquals(2026, v2!!.yearOrMajor)
        assertEquals(11, v2.monthOrMinor)
        assertEquals(1, v2.patch)
        assertEquals(202611001L, v2.estimatedVersionCode)

        val v3 = PortlyVersion.parse("2026.12.0")
        assertNotNull(v3)
        assertEquals(2026, v3!!.yearOrMajor)
        assertEquals(12, v3.monthOrMinor)
        assertEquals(0, v3.patch)
        assertEquals(202612000L, v3.estimatedVersionCode)

        val vNewYear = PortlyVersion.parse("2027.01.0")
        assertNotNull(vNewYear)
        assertEquals(2027, vNewYear!!.yearOrMajor)
        assertEquals(1, vNewYear.monthOrMinor)
        assertEquals(0, vNewYear.patch)
        assertEquals(202701000L, vNewYear.estimatedVersionCode)
    }

    @Test
    fun testPreReleaseParsing() {
        val alpha = PortlyVersion.parse("2026.11.0-alpha.1")
        assertNotNull(alpha)
        assertTrue(alpha!!.isPreRelease)
        assertEquals(PortlyVersion.PreReleaseType.ALPHA, alpha.preReleaseType)
        assertEquals(1, alpha.preReleaseNumber)

        val beta = PortlyVersion.parse("2026.11.0-beta.2")
        assertNotNull(beta)
        assertTrue(beta!!.isPreRelease)
        assertEquals(PortlyVersion.PreReleaseType.BETA, beta.preReleaseType)
        assertEquals(2, beta.preReleaseNumber)

        val rc = PortlyVersion.parse("2026.11.0-rc.1")
        assertNotNull(rc)
        assertTrue(rc!!.isPreRelease)
        assertEquals(PortlyVersion.PreReleaseType.RC, rc.preReleaseType)
        assertEquals(1, rc.preReleaseNumber)
    }

    @Test
    fun testLegacySemVerParsing() {
        val legacy1 = PortlyVersion.parse("1.0.0")
        assertNotNull(legacy1)
        assertFalse(legacy1!!.isCalVer)
        assertEquals(1, legacy1.yearOrMajor)
        assertEquals(0, legacy1.monthOrMinor)
        assertEquals(0, legacy1.patch)

        val legacy2 = PortlyVersion.parse("v1.0.2")
        assertNotNull(legacy2)
        assertFalse(legacy2!!.isCalVer)
        assertEquals(1, legacy2.yearOrMajor)
        assertEquals(0, legacy2.monthOrMinor)
        assertEquals(2, legacy2.patch)
    }

    @Test
    fun testVersionComparisonOrdering() {
        val vLegacy1 = PortlyVersion.parse("v1.0.0")!!
        val vLegacy2 = PortlyVersion.parse("v2.0.0")!!
        val vCalVerNov0 = PortlyVersion.parse("2026.11.0")!!
        val vCalVerNov1 = PortlyVersion.parse("2026.11.1")!!
        val vCalVerNov10 = PortlyVersion.parse("2026.11.10")!!
        val vCalVerDec = PortlyVersion.parse("2026.12.0")!!
        val vCalVerNextYear = PortlyVersion.parse("2027.01.0")!!

        // CalVer always newer than legacy SemVer
        assertTrue(vCalVerNov0 > vLegacy1)
        assertTrue(vCalVerNov0 > vLegacy2)

        // Month and patch increments
        assertTrue(vCalVerNov1 > vCalVerNov0)
        assertTrue(vCalVerNov10 > vCalVerNov1)
        assertTrue(vCalVerDec > vCalVerNov10)
        assertTrue(vCalVerNextYear > vCalVerDec)

        // Pre-release ordering: Stable > RC > Beta > Alpha
        val alpha = PortlyVersion.parse("2026.11.0-alpha.1")!!
        val beta1 = PortlyVersion.parse("2026.11.0-beta.1")!!
        val beta2 = PortlyVersion.parse("2026.11.0-beta.2")!!
        val rc = PortlyVersion.parse("2026.11.0-rc.1")!!

        assertTrue(beta1 > alpha)
        assertTrue(beta2 > beta1)
        assertTrue(rc > beta2)
        assertTrue(vCalVerNov0 > rc)
    }

    @Test
    fun testUpgradeEligibilityMigration() {
        // 1. Existing v1.0.0 (code 1) upgrading to 2026.11.0 (code 202611000)
        val eligibleV1ToCalVer = PortlyVersion.isUpgradeEligible(
            candidateVersionName = "2026.11.0",
            candidateVersionCode = 202611000L,
            candidateIsPrerelease = false,
            channel = ReleaseChannel.STABLE,
            currentVersionName = "1.0.0",
            currentVersionCode = 1L,
        )
        assertTrue("Existing v1.0.0 users must be eligible to upgrade to 2026.11.0", eligibleV1ToCalVer)

        // 2. 2026.11.0 upgrading to 2026.11.1 patch
        val eligiblePatch = PortlyVersion.isUpgradeEligible(
            candidateVersionName = "2026.11.1",
            candidateVersionCode = 202611001L,
            candidateIsPrerelease = false,
            channel = ReleaseChannel.STABLE,
            currentVersionName = "2026.11.0",
            currentVersionCode = 202611000L,
        )
        assertTrue(eligiblePatch)

        // 3. Downgrade attempt rejected
        val downgrade = PortlyVersion.isUpgradeEligible(
            candidateVersionName = "2026.11.0",
            candidateVersionCode = 202611000L,
            candidateIsPrerelease = false,
            channel = ReleaseChannel.STABLE,
            currentVersionName = "2026.11.1",
            currentVersionCode = 202611001L,
        )
        assertFalse("Downgrades must be strictly rejected", downgrade)

        // 4. Stable channel ignores beta builds
        val betaForStable = PortlyVersion.isUpgradeEligible(
            candidateVersionName = "2026.12.0-beta.1",
            candidateVersionCode = 202612000L,
            candidateIsPrerelease = true,
            channel = ReleaseChannel.STABLE,
            currentVersionName = "2026.11.0",
            currentVersionCode = 202611000L,
        )
        assertFalse("Stable users must not receive beta pre-releases", betaForStable)

        // 5. Beta channel receives beta builds
        val betaForBeta = PortlyVersion.isUpgradeEligible(
            candidateVersionName = "2026.12.0-beta.1",
            candidateVersionCode = 202612000L,
            candidateIsPrerelease = true,
            channel = ReleaseChannel.BETA,
            currentVersionName = "2026.11.0",
            currentVersionCode = 202611000L,
        )
        assertTrue("Beta users should receive beta pre-releases", betaForBeta)
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
            versionName = "2026.11.0",
            versionCode = 202611000L,
            releaseChannel = ReleaseChannel.STABLE,
            releaseNotes = "New features",
            apkDownloadUrl = "https://example.com/Portly.apk",
            apkSha256 = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
            apkSizeBytes = 8000000L,
        )

        assertEquals("2026.11.0", info.versionName)
        assertEquals(ReleaseChannel.STABLE, info.releaseChannel)
        assertTrue(info.versionCode > 1)
        assertFalse(info.apkSha256.isNullOrBlank())
    }
}
