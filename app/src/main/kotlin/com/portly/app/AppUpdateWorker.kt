package com.portly.app

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.portly.core.common.update.UpdateGitHubClient
import com.portly.core.common.update.UpdateManager
import com.portly.core.datastore.PortlySettingsDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val gitHubClient: UpdateGitHubClient,
    private val settingsDataStore: PortlySettingsDataStore,
    private val notificationHelper: UpdateNotificationHelper,
    private val updateManager: UpdateManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsDataStore.settings.first()
        if (!settings.autoCheckUpdates) {
            return Result.success()
        }

        val result = gitHubClient.fetchLatestUpdate(
            repo = settings.customGitHubRepo,
            channel = settings.updateReleaseChannel,
            currentVersionCode = updateManager.currentVersionCode,
        )

        val now = System.currentTimeMillis()
        settingsDataStore.setLastUpdateCheckTimestamp(now)

        return when (result) {
            is com.portly.core.model.UpdateResult.Success -> {
                val update = result.data
                if (update != null && update.versionCode > settings.ignoredUpdateVersionCode) {
                    if (settings.notifyOnUpdate) {
                        notificationHelper.showUpdateNotification(update)
                    }
                }
                Result.success()
            }
            is com.portly.core.model.UpdateResult.Error -> {
                // Return success so WorkManager continues normal periodic scheduling
                Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME = "portly_periodic_update_check"
    }
}
