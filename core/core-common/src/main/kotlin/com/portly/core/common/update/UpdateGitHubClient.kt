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

                // GitHub returns releases newest-first. The first eligible release is the
                // latest stable/beta candidate; versionCode remains the final authority.
                val targetRelease = eligibleReleases.first()
                val tagName = targetRelease["tag_name"]?.jsonPrimitive?.contentOrNull ?: ""
                val versionName = tagName.removePrefix("v").trim()
                val releaseNotes = targetRelease["body"]?.jsonPrimitive?.contentOrNull ?: ""
                val publishedAt = targetRelease["published_at"]?.jsonPrimitive?.contentOrNull ?: ""
                val htmlUrl = targetRelease["html_url"]?.jsonPrimitive?.contentOrNull ?: ""
                val isPrerelease = targetRelease["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false

                val assets = targetRelease["assets"]?.jsonArray?.mapNotNull { it as? JsonObject } ?: emptyList()

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

                val apkAsset = assets.firstOrNull {
                    val name = it["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    name.endsWith(".apk", ignoreCase = true) && !name.contains("-androidTest")
                }

                val apkDownloadUrl = parsedMetadata?.apkDownloadUrl?.takeIf { it.isNotBlank() }
                    ?: apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                    ?: ""

                if (apkDownloadUrl.isBlank()) {
                    return@withContext UpdateResult.Success(null)
                }

                val apkSize = parsedMetadata?.apkSizeBytes?.takeIf { it > 0 }
                    ?: apkAsset?.get("size")?.jsonPrimitive?.longOrNull
                    ?: 0L

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

                val estimatedVersionCode = parsedMetadata?.versionCode?.takeIf { it > 0 }
                    ?: parseVersionCodeFromVersionName(versionName)

                val updateInfo = UpdateInfo(
                    versionName = parsedMetadata?.versionName?.takeIf { it.isNotBlank() } ?: versionName,
                    versionCode = estimatedVersionCode,
                    releaseChannel = if (isPrerelease) ReleaseChannel.BETA else ReleaseChannel.STABLE,
                    releaseNotes = parsedMetadata?.releaseNotes?.takeIf { it.isNotBlank() } ?: releaseNotes,
                    publishedAt = parsedMetadata?.publishedAt?.takeIf { it.isNotBlank() } ?: publishedAt,
                    minSupportedSdk = parsedMetadata?.minSupportedSdk ?: 26,
                    apkDownloadUrl = apkDownloadUrl,
                    apkSha256 = sha256,
                    apkSizeBytes = apkSize,
                    signingCertificateFingerprint = parsedMetadata?.signingCertificateFingerprint,
                    htmlUrl = parsedMetadata?.htmlUrl?.takeIf { it.isNotBlank() } ?: htmlUrl,
                )

                if (updateInfo.versionCode > currentVersionCode) {
                    UpdateResult.Success(updateInfo)
                } else {
                    UpdateResult.Success(null)
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Failed to connect to GitHub Releases API", e)
        }
    }

    private fun fetchRemoteMetadata(url: String): UpdateInfo? {
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body.string()
                    val el = json.parseToJsonElement(body).jsonObject
                    UpdateInfo(
                        versionName = el["versionName"]?.jsonPrimitive?.contentOrNull ?: "",
                        versionCode = el["versionCode"]?.jsonPrimitive?.longOrNull ?: 0L,
                        releaseChannel = el["releaseChannel"]?.jsonPrimitive?.contentOrNull?.let {
                            runCatching { ReleaseChannel.valueOf(it) }.getOrNull()
                        } ?: ReleaseChannel.STABLE,
                        releaseNotes = el["releaseNotes"]?.jsonPrimitive?.contentOrNull ?: "",
                        publishedAt = el["publishedAt"]?.jsonPrimitive?.contentOrNull ?: "",
                        minSupportedSdk = el["minSupportedSdk"]?.jsonPrimitive?.longOrNull?.toInt() ?: 26,
                        apkDownloadUrl = el["apkDownloadUrl"]?.jsonPrimitive?.contentOrNull ?: "",
                        apkSha256 = el["apkSha256"]?.jsonPrimitive?.contentOrNull,
                        apkSizeBytes = el["apkSizeBytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                        signingCertificateFingerprint = el["signingCertificateFingerprint"]?.jsonPrimitive?.contentOrNull,
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
                            if (apkFileName == null || filename.equals(apkFileName, ignoreCase = true)) {
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
        /**
         * CalVer fallback must match version.properties:
         * versionCode = (YYYY * 100000) + (MM * 1000) + PATCH.
         * Keep SemVer parsing for older installations/releases during migration.
         * Release metadata should always supply the authoritative Android versionCode.
         */
        fun parseVersionCodeFromVersionName(versionName: String): Long {
            val clean = versionName.substringBefore('-').trim()
            val segments = clean.split(".").mapNotNull { it.toLongOrNull() }

            if (segments.size == 3 && segments[0] >= 2000L) {
                val year = segments[0]
                val month = segments[1]
                val patch = segments[2]
                if (month !in 1L..12L || patch !in 0L..999L) return 1L
                return (year * 100_000L) + (month * 1_000L) + patch
            }

            return when (segments.size) {
                1 -> segments[0] * 1_000_000L
                2 -> segments[0] * 1_000_000L + segments[1] * 1_000L
                3 -> segments[0] * 1_000_000L + segments[1] * 1_000L + segments[2]
                else -> 1L
            }
        }
    }
}
