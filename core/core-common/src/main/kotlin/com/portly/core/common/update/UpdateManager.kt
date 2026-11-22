package com.portly.core.common.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.portly.core.model.ReleaseChannel
import com.portly.core.model.UpdateInfo
import com.portly.core.model.UpdateResult
import com.portly.core.model.UpdateStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gitHubClient: UpdateGitHubClient,
    private val downloader: UpdateDownloader,
    private val verifier: UpdateVerifier,
    private val installer: UpdateInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    val currentVersionName: String by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "2026.11.1"
        } catch (_: Exception) {
            "2026.11.1"
        }
    }

    val currentVersionCode: Long by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            202611000L
        }
    }

    fun checkForUpdates(
        repo: String = "OffensiveKrish/Portly",
        channel: ReleaseChannel = ReleaseChannel.STABLE,
        onResult: ((UpdateInfo?) -> Unit)? = null,
    ) {
        val targetRepo = if (repo.isBlank() || repo.equals("upgrdify/portly", ignoreCase = true)) {
            "OffensiveKrish/Portly"
        } else {
            repo
        }
        _status.value = UpdateStatus.Checking
        scope.launch(Dispatchers.IO) {
            val result = gitHubClient.fetchLatestUpdate(
                repo = targetRepo,
                channel = channel,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )

            val now = System.currentTimeMillis()
            when (result) {
                is UpdateResult.Success -> {
                    val update = result.data
                    if (update != null) {
                        _status.value = UpdateStatus.UpdateAvailable(update)
                    } else {
                        _status.value = UpdateStatus.UpToDate(now)
                    }
                    onResult?.invoke(update)
                }
                is UpdateResult.Error -> {
                    _status.value = UpdateStatus.Error(
                        message = result.message,
                        isDowngrade = result.isDowngrade,
                        isTampered = result.isTampered,
                    )
                    onResult?.invoke(null)
                }
            }
        }
    }

    fun startDownload(updateInfo: UpdateInfo) {
        scope.launch(Dispatchers.IO) {
            _status.value = UpdateStatus.Downloading(
                updateInfo = updateInfo,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = updateInfo.apkSizeBytes,
            )

            try {
                downloader.downloadApk(updateInfo).collect { progress ->
                    when (progress) {
                        is DownloadProgress.InProgress -> {
                            _status.value = UpdateStatus.Downloading(
                                updateInfo = updateInfo,
                                progress = progress.progress,
                                bytesDownloaded = progress.bytesDownloaded,
                                totalBytes = progress.totalBytes,
                            )
                        }
                        is DownloadProgress.Completed -> {
                            verifyAndPrepare(updateInfo, progress.file)
                        }
                    }
                }
            } catch (e: Exception) {
                _status.value = UpdateStatus.Error(
                    message = "Download failed: ${e.message ?: "Unknown network error"}"
                )
            }
        }
    }

    private fun verifyAndPrepare(updateInfo: UpdateInfo, apkFile: File) {
        _status.value = UpdateStatus.Verifying(updateInfo)

        val verifyResult = verifier.verifyDownloadedApk(
            apkFile = apkFile,
            updateInfo = updateInfo,
            currentVersionCode = currentVersionCode,
        )

        when (verifyResult) {
            is UpdateResult.Success -> {
                if (installer.canRequestPackageInstalls()) {
                    _status.value = UpdateStatus.ReadyToInstall(updateInfo, apkFile.absolutePath)
                } else {
                    _status.value = UpdateStatus.PermissionRequired(updateInfo, apkFile.absolutePath)
                }
            }
            is UpdateResult.Error -> {
                _status.value = UpdateStatus.Error(
                    message = verifyResult.message,
                    isDowngrade = verifyResult.isDowngrade,
                    isTampered = verifyResult.isTampered,
                )
            }
        }
    }

    fun installApk(apkPath: String): Boolean {
        val file = File(apkPath)
        return installer.launchPackageInstaller(file)
    }

    fun canRequestPackageInstalls(): Boolean = installer.canRequestPackageInstalls()

    fun getInstallPermissionIntent() = installer.createInstallPermissionIntent()

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
    }
}
