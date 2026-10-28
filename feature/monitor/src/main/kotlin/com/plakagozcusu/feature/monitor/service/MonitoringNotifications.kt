package com.plakagozcusu.feature.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.plakagozcusu.feature.monitor.R
import com.plakagozcusu.feature.monitor.session.MonitoringState
import com.plakagozcusu.feature.monitor.session.PauseReason

/** Ongoing + warning notifications. Never contain plate text. */
internal class MonitoringNotifications(
    private val context: Context,
) {
    fun ensureChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                context.getString(R.string.notif_channel_monitoring),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNINGS,
                context.getString(R.string.notif_channel_warnings),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun ongoing(state: MonitoringState): Notification {
        val text =
            when (state) {
                is MonitoringState.Running -> {
                    context.getString(R.string.notif_running, state.stats.vehicles)
                }

                is MonitoringState.Paused -> {
                    context.getString(pauseText(state.reason))
                }

                else -> {
                    context.getString(R.string.notif_starting)
                }
            }
        val stop =
            PendingIntent.getService(
                context,
                REQUEST_STOP,
                Intent(context, MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent =
            open?.let {
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN,
                    it,
                    PendingIntent.FLAG_IMMUTABLE,
                )
            }
        return NotificationCompat
            .Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_monitoring)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyOngoing(state: MonitoringState) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        @Suppress("MissingPermission") // Guarded by areNotificationsEnabled() above.
        NotificationManagerCompat.from(context).notify(ID_ONGOING, ongoing(state))
    }

    fun warn(reasonText: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val n =
            NotificationCompat
                .Builder(context, CHANNEL_WARNINGS)
                .setSmallIcon(R.drawable.ic_stat_monitoring)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentText(context.getString(reasonText))
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // Guarded by areNotificationsEnabled() above.
        NotificationManagerCompat.from(context).notify(ID_WARNING, n)
    }

    companion object {
        const val ID_ONGOING = 1001
        private const val ID_WARNING = 1002
        private const val CHANNEL_ONGOING = "monitoring"
        private const val CHANNEL_WARNINGS = "warnings"
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN = 2

        fun pauseText(reason: PauseReason): Int =
            when (reason) {
                PauseReason.CAMERA_INTERRUPTED -> R.string.pause_camera
                PauseReason.SCENE_SHIFT -> R.string.pause_scene_shift
                PauseReason.OVERHEATING -> R.string.pause_overheating
                PauseReason.STORAGE_FULL -> R.string.pause_storage
                PauseReason.STALLED -> R.string.pause_stalled
            }
    }
}
