package com.portly.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.portly.core.common.ApplicationScope
import com.portly.core.model.TransferEngine
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PortlyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var transferEngine: TransferEngine

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var networkTransferCoordinator: NetworkTransferCoordinator

    @Inject
    lateinit var transferForegroundCoordinator: TransferForegroundCoordinator

    @Inject
    lateinit var serverForegroundCoordinator: ServerForegroundCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        networkTransferCoordinator.start()
        transferForegroundCoordinator.start()
        serverForegroundCoordinator.start()
        applicationScope.launch {
            transferEngine.reconcilePending()
        }
        schedulePeriodicUpdateChecks()
    }

    private fun schedulePeriodicUpdateChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateWorkRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            24, TimeUnit.HOURS,
            2, TimeUnit.HOURS, // flex interval
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AppUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateWorkRequest,
        )
    }
}
