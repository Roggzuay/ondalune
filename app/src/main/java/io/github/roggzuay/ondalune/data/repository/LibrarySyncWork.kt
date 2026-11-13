package io.github.roggzuay.ondalune.data.repository

import io.github.roggzuay.ondalune.data.local.ArtworkStore
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.roggzuay.ondalune.data.source.FolderReader
import java.util.concurrent.TimeUnit

/**
 * The optional background library scan (WorkManager), every few hours as picked in Settings →
 * Music sources: it syncs the server in use and looks through the picked folders, so new music is
 * already there when Ondalune opens. On any network, never while the battery is low, and it
 * shows nothing. Covers new to the library are saved with it.
 */
class LibrarySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = OndaluneRepository.from(applicationContext)
        repository.activeServer()?.takeUnless { it.isDemoServer }?.let { server ->
            repository.syncServerCatalog(server)
            ArtworkStore.saveAll(applicationContext, repository.tracksOf(server))
        }
        val reader = FolderReader(applicationContext)
        repository.localFolders().forEach { repository.scanLocalFolder(it, reader) }
        return Result.success()
    }

    companion object {
        const val PREF_HOURS = "cfg_library_sync_hours"
        private const val NAME = "ondalune-library-sync"

        /**
         * Scans every [hours]; 0 stops it. The first scan comes one interval from now (opening the
         * app has just synced). A scan already scheduled keeps its timing unless [restart] is set,
         * which applies a newly picked interval.
         */
        fun schedule(context: Context, hours: Int, restart: Boolean = false) {
            // Absent only where Android's startup didn't run (unit tests); never stop the app for it.
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (hours <= 0) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<LibrarySyncWorker>(hours.toLong(), TimeUnit.HOURS)
                .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            val policy = if (restart) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            work.enqueueUniquePeriodicWork(NAME, policy, request)
        }
    }
}
