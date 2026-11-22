package com.portly.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.portly.core.common.ApplicationScope
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.network.PortlyServerManager
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
class ServerForegroundService : Service() {

    @Inject
    lateinit var serverManager: PortlyServerManager

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
        if (intent?.action == ACTION_STOP_SERVER) {
            val typeName = intent.getStringExtra(EXTRA_SERVER_TYPE)
            val serverType = typeName?.let { runCatching { ServerType.valueOf(it) }.getOrNull() }
            appScope.launch {
                if (serverType != null) {
                    serverManager.stopServer(serverType)
                } else {
                    serverManager.stopAllServers()
                }
            }
            return START_NOT_STICKY
        }

        val initial = buildNotification(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initial)
        }

        observerJob?.cancel()
        observerJob = appScope.launch {
            serverManager.states.collectLatest { states ->
                val running = states.values.filter { it.status == ServerStatus.RUNNING }
                if (running.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, buildNotification(running))
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(running: List<com.portly.core.model.ServerRuntimeState>): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(TransferForegroundService.EXTRA_START_ROUTE, PortlyDestination.SERVERS.route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            201,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val totalClients = running.sumOf { it.activeClientCount }
        val summary = if (running.isEmpty()) {
            "Starting server…"
        } else {
            running.joinToString(" • ") { "${it.serverType.name}:${it.port}" } +
                " • $totalClients client(s)"
        }

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setContentTitle("Portly Server Active (${running.size})")
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingOpen)

        // Add per-server stop actions (up to 3 notification actions supported by Android)
        running.take(3).forEachIndexed { idx, srv ->
            val stopIntent = Intent(this, ServerForegroundService::class.java).apply {
                action = ACTION_STOP_SERVER
                putExtra(EXTRA_SERVER_TYPE, srv.serverType.name)
            }
            val pendingStop = PendingIntent.getService(
                this,
                300 + idx,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Stop ${srv.serverType.name}", pendingStop)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.deleteNotificationChannel("portly_servers") }
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active File Servers",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Shows an active notification and status bar indicator when Portly file servers are running"
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "portly_servers_v2"
        const val NOTIFICATION_ID = 4201
        const val ACTION_STOP_SERVER = "com.portly.app.STOP_SERVER"
        const val EXTRA_SERVER_TYPE = "server_type"

        fun start(context: Context) {
            val intent = Intent(context, ServerForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

@Singleton
class ServerForegroundCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverManager: PortlyServerManager,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            serverManager.states
                .map { map -> map.values.any { it.status == ServerStatus.RUNNING } }
                .distinctUntilChanged()
                .collectLatest { anyRunning ->
                    if (anyRunning) {
                        runCatching { ServerForegroundService.start(context) }
                    }
                }
        }
    }
}
