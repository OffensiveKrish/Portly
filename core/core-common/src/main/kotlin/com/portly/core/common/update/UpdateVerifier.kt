package com.portly.core.common.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.portly.core.common.ChecksumUtils
import com.portly.core.model.UpdateInfo
import com.portly.core.model.UpdateResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateVerifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Executes strict multi-stage verification on downloaded APK:
     * 1. SHA-256 checksum check (against expected metadata/checksums)
     * 2. Package archive parsing integrity
     * 3. Package name matching (com.portly.app)
     * 4. Version code downgrade prevention
     * 5. Cryptographic signing certificate SHA-256 fingerprint matching
     */
    fun verifyDownloadedApk(
        apkFile: File,
        updateInfo: UpdateInfo,
        currentVersionCode: Long,
    ): UpdateResult<Unit> {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return UpdateResult.Error("Downloaded APK file is empty or does not exist")
        }

        // 1. Checksum verification
        val expectedSha256 = updateInfo.apkSha256
        if (!expectedSha256.isNullOrBlank()) {
            val actualSha256 = ChecksumUtils.computeFileDigest(apkFile, "SHA-256")
            if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                return UpdateResult.Error(
                    message = "SHA-256 checksum mismatch! Expected $expectedSha256 but got $actualSha256. The file may have been corrupted or tampered with.",
                    isTampered = true,
                )
            }
        }

        // 2. Package archive parsing
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return UpdateResult.Error("Android Package Manager failed to parse APK archive. Invalid or corrupted package.")

        // 3. Package Name verification
        val targetPackageName = context.packageName
        val archivePackageName = packageInfo.packageName
        if (archivePackageName != targetPackageName) {
            return UpdateResult.Error("Package ID mismatch: expected $targetPackageName, archive was $archivePackageName")
        }

        // 4. Anti-downgrade check
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        if (archiveVersionCode <= currentVersionCode) {
            return UpdateResult.Error(
                message = "Downgrade attempt rejected: archive versionCode ($archiveVersionCode) is not greater than installed versionCode ($currentVersionCode)",
                isDowngrade = true,
            )
        }

        // 5. Signing Certificate Verification
        val archiveCertFingerprint = extractCertFingerprint(packageInfo)
            ?: return UpdateResult.Error("Failed to read signing certificate from APK archive.")

        val installedPackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(targetPackageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(targetPackageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: Exception) {
            null
        }

        val installedCertFingerprint = installedPackageInfo?.let { extractCertFingerprint(it) }

        // Compare against installed app's certificate fingerprint
        if (installedCertFingerprint != null) {
            if (!installedCertFingerprint.equals(archiveCertFingerprint, ignoreCase = true)) {
                return UpdateResult.Error(
                    message = "Signing certificate fingerprint mismatch! Installed: $installedCertFingerprint, Archive: $archiveCertFingerprint. Rejecting unauthorized package.",
                    isTampered = true,
                )
            }
        }

        // Also verify against metadata certificate fingerprint if provided
        val expectedCertFingerprint = updateInfo.signingCertificateFingerprint
        if (!expectedCertFingerprint.isNullOrBlank()) {
            val cleanExpected = expectedCertFingerprint.replace(":", "").trim()
            val cleanActual = archiveCertFingerprint.replace(":", "").trim()
            if (!cleanExpected.equals(cleanActual, ignoreCase = true)) {
                return UpdateResult.Error(
                    message = "Release metadata certificate mismatch! Expected: $expectedCertFingerprint, Archive: $archiveCertFingerprint.",
                    isTampered = true,
                )
            }
        }

        return UpdateResult.Success(Unit)
    }

    private fun extractCertFingerprint(pkgInfo: PackageInfo): String? {
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
            } else {
                signingInfo.signingCertificateHistory?.firstOrNull()?.toByteArray()
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.firstOrNull()?.toByteArray()
        } ?: return null

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(certBytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}
