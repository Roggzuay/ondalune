package io.github.roggzuay.ondalune.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.roggzuay.ondalune.MainActivity
import io.github.roggzuay.ondalune.R

/**
 * One aggregate notification for the download queue: "Downloading 3 of 12" with overall
 * progress and the current title, replaced by a dismissible summary when the batch ends.
 */
class DownloadNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of tracks being saved for offline listening"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private val openApp: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showProgress(done: Int, total: Int, overallPercent: Int, currentTitle: String) {
        post(
            NotificationCompat.Builder(context, CHANNEL_ID)
                // App icon, not the system download arrow: files go to Ondalune's private storage,
                // not the phone's Downloads folder.
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(if (total == 1) "Saving for offline" else "Saving ${done + 1} of $total for offline")
                .setContentText(currentTitle)
                .setSubText("Stored in Ondalune")
                .setProgress(100, overallPercent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
        )
    }

    fun showFinished(completed: Int, failed: Int) {
        post(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(
                    when {
                        failed == 0 -> if (completed == 1) "Download complete" else "$completed tracks downloaded"
                        completed == 0 -> "Download failed"
                        else -> "$completed downloaded, $failed failed"
                    }
                )
                .setContentText("Saved inside Ondalune for offline listening")
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(openApp)
                .build()
        )
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun post(notification: android.app.Notification) {
        // Without the notification permission the in-app indicators still show progress.
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val CHANNEL_ID = "ondalune_downloads"
        const val NOTIFICATION_ID = 4210
    }
}
