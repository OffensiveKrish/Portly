package com.portly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.portly.core.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "portly_updates"
        const val NOTIFICATION_ID = 2001
        const val EXTRA_OPEN_UPDATES = "com.portly.app.OPEN_UPDATES"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Portly Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for newly published Portly releases and updates"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(updateInfo: UpdateInfo) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = EXTRA_OPEN_UPDATES
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_UPDATES, true)
            putExtra("UPDATE_VERSION_NAME", updateInfo.versionName)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, com.portly.core.ui.R.drawable.ic_portly_logo)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.portly.core.ui.R.drawable.ic_portly_logo)
            .apply {
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
            .setContentTitle("PORTLY UPDATE AVAILABLE")
            .setContentText("Version ${updateInfo.versionName} is ready to download & install.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Version ${updateInfo.versionName} (${updateInfo.releaseChannel.name}) is now available.\n\n" +
                                if (updateInfo.releaseNotes.isNotBlank()) updateInfo.releaseNotes.take(300) else "Tap to review release notes and install."
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                0,
                "Update Now",
                pendingIntent,
            )

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelUpdateNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
