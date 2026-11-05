package io.github.roggzuay.ondalune.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.roggzuay.ondalune.BuildConfig
import io.github.roggzuay.ondalune.MainActivity
import io.github.roggzuay.ondalune.R
import io.github.roggzuay.ondalune.data.network.AppUpdate
import java.util.concurrent.TimeUnit

/** Update checks in this build: off in builds that must not fetch updates themselves (F-Droid). */
val updateChecksBuiltIn: Boolean get() = BuildConfig.SELF_UPDATE

/**
 * The daily background update check (WorkManager): only with a network and enough battery, at
 * most once a day (the store keeps the answer), and it tells about each release once.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ondalune_prefs", Context.MODE_PRIVATE)
        if (!updateChecksBuiltIn || !prefs.getBoolean("cfg_check_updates", true)) return Result.success()
        val store = UpdateStore(applicationContext)
        store.refresh(force = false, includePrereleases = prefs.getBoolean("cfg_update_prereleases", false))
        val update = store.availableFor(BuildConfig.VERSION_NAME) ?: return Result.success()
        if (prefs.getBoolean("cfg_update_notifications", true) && store.shouldNotify(update.version) &&
            UpdateNotifier.show(applicationContext, update)
        ) store.markNotified(update.version)
        return Result.success()
    }

    companion object {
        private const val NAME = "ondalune-update-check"

        /** Starts (or stops) the daily check; a check already scheduled keeps its timing. */
        fun schedule(context: Context, enabled: Boolean) {
            // Absent only where Android's startup didn't run (unit tests); never stop the app for it.
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!enabled || !updateChecksBuiltIn) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** The "update available" notification, in its own channel people can turn off in Android settings. */
object UpdateNotifier {
    const val CHANNEL_ID = "app_updates"
    const val EXTRA_SHOW_UPDATE = "io.github.roggzuay.ondalune.SHOW_UPDATE"
    private const val NOTIFICATION_ID = 4401

    /** Posts it quietly (no sound, no pop-up over music); false when Android or the user doesn't allow it. */
    fun show(context: Context, update: AppUpdate): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "When a new version of Ondalune is out"
                }
            )
            if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("Ondalune ${update.version} is available")
                .setContentText(update.notes.firstOrNull() ?: "See what's new and update")
                .setStyle(NotificationCompat.BigTextStyle().bigText(update.notes.take(3).joinToString("\n").ifBlank { "See what's new and update" }))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .build()
        )
        return true
    }
}
