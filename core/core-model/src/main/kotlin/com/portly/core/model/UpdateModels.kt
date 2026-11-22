package com.portly.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReleaseChannel {
    STABLE,
    BETA,
}

@Serializable
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long = 0L,
    val releaseChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val minSupportedSdk: Int = 26,
    val apkDownloadUrl: String,
    val apkSha256: String? = null,
    val apkSizeBytes: Long = 0L,
    val signingCertificateFingerprint: String? = null,
    val htmlUrl: String = "",
)

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class UpToDate(val lastCheckedTimestamp: Long) : UpdateStatus
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateStatus
    data class Downloading(
        val updateInfo: UpdateInfo,
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : UpdateStatus
    data class Verifying(val updateInfo: UpdateInfo) : UpdateStatus
    data class ReadyToInstall(
        val updateInfo: UpdateInfo,
        val apkPath: String,
    ) : UpdateStatus
    data class PermissionRequired(
        val updateInfo: UpdateInfo,
        val apkPath: String,
    ) : UpdateStatus
    data class Error(
        val message: String,
        val isDowngrade: Boolean = false,
        val isTampered: Boolean = false,
    ) : UpdateStatus
}

sealed interface UpdateResult<out T> {
    data class Success<T>(val data: T) : UpdateResult<T>
    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val isDowngrade: Boolean = false,
        val isTampered: Boolean = false,
    ) : UpdateResult<Nothing>
}
