package com.portly.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.portly.core.common.ApplicationScope
import com.portly.core.common.Formatters
import com.portly.core.model.TransferEngine
import com.portly.core.model.TransferStatus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class TransferForegroundService : Service() {

    @Inject
    lateinit var transferEngine: TransferEngine

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initial = buildNotification(
            title = "Portly Transfers",
            text = "Preparing transfer queue…",
            progressPercent = 0,
            indeterminate = true,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }

        observerJob?.cancel()
        observerJob = appScope.launch {
            transferEngine.observeTransfers().collectLatest { transfers ->
                val active = transfers.filter {
                    it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED
                }
                if (active.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val running = active.filter { it.status == TransferStatus.RUNNING }
                val primary = running.firstOrNull() ?: active.first()
                val percent = primary.progress.percent.toInt().coerceIn(0, 100)
                val title = if (active.size == 1) {
                    "${primary.spec.direction.name}: ${primary.spec.fileName}"
                } else {
                    "${active.size} active transfers • ${primary.spec.fileName}"
                }
                val text = if (running.isNotEmpty()) {
                    "${Formatters.formatBytes(primary.progress.bytesTransferred)} / ${Formatters.formatBytes(primary.progress.totalBytes)} • ${Formatters.formatSpeed(primary.progress.speedBytesPerSec)}"
                } else {
                    "Queued (${active.size})"
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = title,
                        text = text,
                        progressPercent = percent,
                        indeterminate = running.isEmpty(),
                    ),
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(
        title: String,
        text: String,
        progressPercent: Int,
        indeterminate: Boolean,
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_START_ROUTE, PortlyDestination.TRANSFERS.route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(100, progressPercent, indeterminate)
            .setContentIntent(pendingOpen)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.deleteNotificationChannel("portly_transfers") }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active File Transfers",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "portly_transfers_v2"
        const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "com.portly.app.STOP_TRANSFER_FG"
        const val EXTRA_START_ROUTE = "start_route"

        fun start(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

@Singleton
class TransferForegroundCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val transferEngine: TransferEngine,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            transferEngine.observeTransfers()
                .map { list -> list.any { it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED } }
                .distinctUntilChanged()
                .collectLatest { hasActive ->
                    if (hasActive) {
                        runCatching { TransferForegroundService.start(context) }
                    }
                }
        }
    }
}
