package com.downflow.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.downflow.app.R
import com.downflow.app.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val CHANNEL_ID_ENGINE    = "downflow_engine"
const val CHANNEL_ID_DOWNLOADS = "downflow_downloads"
const val NOTIF_ID_ENGINE      = 1001

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val engineChannel = NotificationChannel(
            CHANNEL_ID_ENGINE,
            context.getString(R.string.notification_channel_engine),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_engine_desc)
            setShowBadge(false)
        }

        val downloadChannel = NotificationChannel(
            CHANNEL_ID_DOWNLOADS,
            context.getString(R.string.notification_channel_download),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_download_desc)
        }

        nm.createNotificationChannels(listOf(engineChannel, downloadChannel))
    }

    fun buildEngineNotification(
        downloadCount: Int = 0,
        totalSpeedBps: Long = 0L,
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (downloadCount > 0) {
            "$downloadCount active • ${formatSpeed(totalSpeedBps)}"
        } else {
            context.getString(R.string.notification_engine_running)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_ENGINE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateEngineNotification(downloadCount: Int, totalSpeedBps: Long) {
        nm.notify(NOTIF_ID_ENGINE, buildEngineNotification(downloadCount, totalSpeedBps))
    }

    private fun formatSpeed(bps: Long): String = when {
        bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
        bps >= 1_000     -> "%.0f KB/s".format(bps / 1_000.0)
        else             -> "$bps B/s"
    }
}
