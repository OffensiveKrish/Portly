package com.portly.core.common.update

import com.portly.core.model.ReleaseChannel
import com.portly.core.model.UpdateInfo
import com.portly.core.model.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateGitHubClient @Inject constructor() {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchLatestUpdate(
        repo: String,
        channel: ReleaseChannel,
        currentVersionCode: Long,
        currentVersionName: String = "1.0.0",
    ): UpdateResult<UpdateInfo?> = withContext(Dispatchers.IO) {
        val cleanRepo = repo.trim().trim('/')
        if (cleanRepo.isBlank() || !cleanRepo.contains('/')) {
            return@withContext UpdateResult.Error("Invalid GitHub repository format: $cleanRepo (expected owner/repo)")
        }

        val url = "https://api.github.com/repos/$cleanRepo/releases"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Portly-Android-Updater")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext if (code == 404) {
                        UpdateResult.Error("GitHub repository or releases not found: $cleanRepo (HTTP 404)")
                    } else if (code == 403) {
                        UpdateResult.Error("GitHub API rate limit exceeded or access forbidden (HTTP 403)")
                    } else {
                        UpdateResult.Error("GitHub Releases API returned HTTP $code: ${response.message}")
                    }
                }

                val responseBody = response.body.string()
                if (responseBody.isBlank()) return@withContext UpdateResult.Success(null)
                val releasesElement = json.parseToJsonElement(responseBody)
                if (releasesElement !is JsonArray || releasesElement.isEmpty()) {
                    return@withContext UpdateResult.Success(null)
                }

                val eligibleReleases = releasesElement.mapNotNull { it as? JsonObject }
                    .filter { release ->
                        val isDraft = release["draft"]?.jsonPrimitive?.booleanOrNull ?: false
                        val isPrerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
                        if (isDraft) return@filter false
                        when (channel) {
                            ReleaseChannel.STABLE -> !isPrerelease
                            ReleaseChannel.BETA -> true
                        }
                    }

                if (eligibleReleases.isEmpty()) {
                    return@withContext UpdateResult.Success(null)
                }

                // Evaluate all candidate releases to find the best newer eligible update
                val updateCandidates = mutableListOf<UpdateInfo>()

                for (targetRelease in eligibleReleases) {
                    val tagName = targetRelease["tag_name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val rawVersionName = tagName.removePrefix("v").removePrefix("V").trim()
                    val releaseNotes = targetRelease["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    val publishedAt = targetRelease["published_at"]?.jsonPrimitive?.contentOrNull ?: ""
                    val htmlUrl = targetRelease["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
                    val isPrerelease = targetRelease["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false

                    val assets = targetRelease["assets"]?.jsonArray?.mapNotNull { it as? JsonObject } ?: emptyList()

                    // Check for update-metadata.json asset
                    val metadataAsset = assets.firstOrNull {
                        it["name"]?.jsonPrimitive?.contentOrNull.equals("update-metadata.json", ignoreCase = true)
                    }
                    var parsedMetadata: UpdateInfo? = null
                    if (metadataAsset != null) {
                        val metadataDownloadUrl = metadataAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                        if (!metadataDownloadUrl.isNullOrBlank()) {
                            parsedMetadata = fetchRemoteMetadata(metadataDownloadUrl)
                        }
                    }

                    // Locate APK asset (prefer universal apk, otherwise any non-test apk)
                    val apkAsset = assets.firstOrNull {
                        val name = it["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        name.endsWith("-universal.apk", ignoreCase = true)
                    } ?: assets.firstOrNull {
                        val name = it["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        name.endsWith(".apk", ignoreCase = true) && !name.contains("-androidTest")
                    }

                    val apkDownloadUrl = parsedMetadata?.apkDownloadUrl?.takeIf { it.isNotBlank() }
                        ?: apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                        ?: ""

                    if (apkDownloadUrl.isBlank()) {
                        continue
                    }

                    val apkSize = parsedMetadata?.apkSizeBytes?.takeIf { it > 0 }
                        ?: apkAsset?.get("size")?.jsonPrimitive?.longOrNull
                        ?: 0L

                    // Checksum verification
                    var sha256 = parsedMetadata?.apkSha256
                    if (sha256.isNullOrBlank()) {
                        val checksumsAsset = assets.firstOrNull {
                            val name = it["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            name.equals("SHA256SUMS.txt", ignoreCase = true) ||
                                name.endsWith("-checksums.txt", ignoreCase = true) ||
                                name.endsWith("checksums.txt", ignoreCase = true)
                        }
                        if (checksumsAsset != null) {
                            val checksumUrl = checksumsAsset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                            if (!checksumUrl.isNullOrBlank()) {
                                sha256 = fetchChecksumFromSha256Sums(checksumUrl, apkAsset?.get("name")?.jsonPrimitive?.contentOrNull)
                            }
                        }
                    }

                    val candidateVersionName = parsedMetadata?.versionName?.takeIf { it.isNotBlank() } ?: rawVersionName
                    val candidateVersionCode = parsedMetadata?.versionCode?.takeIf { it > 0 }
                        ?: PortlyVersion.parse(candidateVersionName)?.estimatedVersionCode
                        ?: parseVersionCodeFromVersionName(candidateVersionName)

                    // Check upgrade eligibility using calibrated CalVer/SemVer comparator
                    val isEligible = PortlyVersion.isUpgradeEligible(
                        candidateVersionName = candidateVersionName,
                        candidateVersionCode = candidateVersionCode,
                        candidateIsPrerelease = isPrerelease,
                        channel = channel,
                        currentVersionName = currentVersionName,
                        currentVersionCode = currentVersionCode,
                    )

                    if (isEligible) {
                        updateCandidates.add(
                            UpdateInfo(
                                versionName = candidateVersionName,
                                versionCode = candidateVersionCode,
                                releaseChannel = if (isPrerelease) ReleaseChannel.BETA else ReleaseChannel.STABLE,
                                releaseNotes = parsedMetadata?.releaseNotes?.takeIf { it.isNotBlank() } ?: releaseNotes,
                                publishedAt = publishedAt,
                                minSupportedSdk = parsedMetadata?.minSupportedSdk ?: 26,
                                apkDownloadUrl = apkDownloadUrl,
                                apkSha256 = sha256,
                                apkSizeBytes = apkSize,
                                signingCertificateFingerprint = parsedMetadata?.signingCertificateFingerprint,
                                htmlUrl = htmlUrl,
                            )
                        )
                    }
                }

                if (updateCandidates.isEmpty()) {
                    return@withContext UpdateResult.Success(null)
                }

                // Pick the highest candidate by PortlyVersion and versionCode
                val bestUpdate = updateCandidates.maxWithOrNull(
                    compareBy<UpdateInfo> {
                        PortlyVersion.parse(it.versionName)
                    }.thenBy {
                        it.versionCode
                    }
                )

                UpdateResult.Success(bestUpdate)
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to connect to GitHub Releases API", e)
        }
    }

    /**
     * Parses update-metadata.json, supporting both the CalVer structured schema
     * and legacy SemVer backwards-compatible properties.
     */
    private fun fetchRemoteMetadata(url: String): UpdateInfo? {
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body.string()
                    val el = json.parseToJsonElement(body).jsonObject

                    // Security check: Verify target applicationId if present
                    val appId = el["applicationId"]?.jsonPrimitive?.contentOrNull
                    if (appId != null && appId != "com.portly.app") {
                        return null
                    }

                    // Extract version name (supports "version" or "versionName")
                    val vName = el["version"]?.jsonPrimitive?.contentOrNull
                        ?: el["versionName"]?.jsonPrimitive?.contentOrNull
                        ?: ""

                    // Extract versionCode
                    val vCode = el["versionCode"]?.jsonPrimitive?.longOrNull ?: 0L

                    // Extract channel
                    val chStr = el["channel"]?.jsonPrimitive?.contentOrNull
                        ?: el["releaseChannel"]?.jsonPrimitive?.contentOrNull
                        ?: "STABLE"
                    val channel = runCatching { ReleaseChannel.valueOf(chStr.uppercase()) }.getOrDefault(ReleaseChannel.STABLE)

                    // Extract download URL (supports "downloadUrl" or "apkDownloadUrl")
                    val dlUrl = el["downloadUrl"]?.jsonPrimitive?.contentOrNull
                        ?: el["apkDownloadUrl"]?.jsonPrimitive?.contentOrNull
                        ?: ""

                    // Extract SHA-256 (supports "sha256" or "apkSha256")
                    val sha = el["sha256"]?.jsonPrimitive?.contentOrNull
                        ?: el["apkSha256"]?.jsonPrimitive?.contentOrNull

                    val size = el["apkSizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L
                    val notes = el["releaseNotes"]?.jsonPrimitive?.contentOrNull ?: ""
                    val pubDate = el["releaseDate"]?.jsonPrimitive?.contentOrNull
                        ?: el["publishedAt"]?.jsonPrimitive?.contentOrNull
                        ?: ""
                    val certFingerprint = el["signingCertificateFingerprint"]?.jsonPrimitive?.contentOrNull

                    UpdateInfo(
                        versionName = vName,
                        versionCode = vCode,
                        releaseChannel = channel,
                        releaseNotes = notes,
                        publishedAt = pubDate,
                        minSupportedSdk = el["minSupportedSdk"]?.jsonPrimitive?.longOrNull?.toInt() ?: 26,
                        apkDownloadUrl = dlUrl,
                        apkSha256 = sha,
                        apkSizeBytes = size,
                        signingCertificateFingerprint = certFingerprint,
                        htmlUrl = el["htmlUrl"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchChecksumFromSha256Sums(url: String, apkFileName: String?): String? {
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val lines = res.body.string().lines()
                    for (line in lines) {
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val hash = parts[0]
                            val filename = parts[1].removePrefix("*")
                            if (apkFileName == null || filename.equals(apkFileName, ignoreCase = true) || filename.endsWith(".apk")) {
                                return hash
                            }
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun parseVersionCodeFromVersionName(versionName: String): Long {
            return PortlyVersion.parse(versionName)?.estimatedVersionCode ?: 1L
        }
    }
}
