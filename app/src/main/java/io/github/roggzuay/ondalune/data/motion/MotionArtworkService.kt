package io.github.roggzuay.ondalune.data.motion

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.source.OpenedFile
import java.io.File
import java.util.concurrent.TimeUnit

/** Reads and writes the Motion artwork settings in Ondalune's preferences. */
object MotionArtworkPreferences {
    const val KEY_MODE = "cfg_motion_mode"
    const val KEY_AUTO_RETRIEVE = "cfg_motion_auto_retrieve"
    const val KEY_WIFI_ONLY = "cfg_motion_wifi_only"
    const val KEY_SCOPE = "cfg_motion_scope"
    const val KEY_CACHE_MB = "cfg_motion_cache_mb"

    fun read(prefs: SharedPreferences): MotionArtworkSettings {
        val d = MotionArtworkSettings()
        return MotionArtworkSettings(
            mode = prefs.getString(KEY_MODE, null)?.let { runCatching { MotionArtworkMode.valueOf(it) }.getOrNull() } ?: d.mode,
            autoRetrieve = prefs.getBoolean(KEY_AUTO_RETRIEVE, d.autoRetrieve),
            wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, d.wifiOnly),
            fetchScope = prefs.getString(KEY_SCOPE, null)?.let { runCatching { MotionFetchScope.valueOf(it) }.getOrNull() } ?: d.fetchScope,
            cacheLimitMb = prefs.getInt(KEY_CACHE_MB, d.cacheLimitMb).coerceIn(MotionArtworkSettings.CUSTOM_LIMIT_MB)
        )
    }

    fun write(editor: SharedPreferences.Editor, s: MotionArtworkSettings): SharedPreferences.Editor = editor
        .putString(KEY_MODE, s.mode.name)
        .putBoolean(KEY_AUTO_RETRIEVE, s.autoRetrieve)
        .putBoolean(KEY_WIFI_ONLY, s.wifiOnly)
        .putString(KEY_SCOPE, s.fetchScope.name)
        .putInt(KEY_CACHE_MB, s.cacheLimitMb)
}

/** The one [MotionArtworkRepository] of the app, wired to this phone's folders, servers and network. */
object MotionArtworkService {
    @Volatile private var instance: MotionArtworkRepository? = null

    fun get(context: Context): MotionArtworkRepository = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(app: Context): MotionArtworkRepository {
        val prefs = app.getSharedPreferences("ondalune_prefs", Context.MODE_PRIVATE)
        val dao = OndaluneDatabase.getInstance(app).dao()
        val folders = FolderMotionProvider(
            context = app,
            treeOf = { dao.folderOfTrack(it.id) },
            albumsBeside = { track ->
                val dir = track.id.substringBeforeLast('/', "")
                val prefix = likeEscape("$dir/")
                dao.albumsInFolder(track.serverId, "$prefix%", "$prefix%/%").toSet()
            }
        )
        val cache = MotionArtworkCache(File(app.filesDir, "motion-artwork"))
        return MotionArtworkRepository(
            cache = cache,
            providers = listOf(folders, ServerCoverMotionProvider(OpenSubsonicClient())),
            settings = { MotionArtworkPreferences.read(prefs) },
            networkAllowed = { wifiOnly -> networkAllowed(app, wifiOnly) },
            playable = { format, location -> playable(app, format, location) }
        ).also { cache.removeOrphans() }
    }

    internal fun likeEscape(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun networkAllowed(context: Context, wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Videos must hold a picture; animated pictures need Android 9's decoder. */
    private fun playable(context: Context, format: MotionFormat, location: String): Boolean {
        if (!format.isVideo) return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val retriever = MediaMetadataRetriever()
        return try {
            if (location.startsWith("content://")) retriever.setDataSource(context, Uri.parse(location)) else retriever.setDataSource(location)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" &&
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * Goes through the whole library for moving artwork when Settings → Motion artwork asks for it:
 * once a day, on Wi-Fi when that is set, never on low battery or storage. One song per album is
 * enough; albums already looked up are skipped by the cache.
 */
class MotionArtworkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ondalune_prefs", Context.MODE_PRIVATE)
        val settings = MotionArtworkPreferences.read(prefs)
        if (!wanted(settings)) return Result.success()
        val repository = MotionArtworkService.get(applicationContext)
        val dao = OndaluneDatabase.getInstance(applicationContext).dao()
        val albums = dao.getAllServersOnce().filter { !it.isDemoServer }
            .flatMap { server -> albumsOf(dao.getTracksForServerOnce(server.id)).map { it to server } }
        runLibrary(repository, albums) { isStopped }
        return Result.success()
    }

    companion object {
        private const val NAME = "ondalune-motion-artwork"

        fun wanted(s: MotionArtworkSettings) =
            s.mode != MotionArtworkMode.DISABLED && s.autoRetrieve && s.fetchScope == MotionFetchScope.WHOLE_LIBRARY

        /**
         * Looks up every album in turn, reporting how far it got. It stops once the saved artwork
         * reaches the size limit: the limit says how much it may fill, and artwork already shown
         * is never pushed out for albums nobody has played yet.
         */
        suspend fun runLibrary(
            repository: MotionArtworkRepository,
            albums: List<Pair<SubsonicTrack, SubsonicServerConfig>>,
            stopped: () -> Boolean
        ) {
            try {
                albums.forEachIndexed { i, (track, server) ->
                    if (stopped() || repository.full()) return
                    repository.libraryRun(MotionArtworkRepository.LibraryRun(i, albums.size))
                    runCatching { repository.resolve(track, server, fillOnly = true) }
                }
            } finally {
                repository.libraryRun(null)
            }
        }

        /** One song for each album, skipping files opened from other apps. */
        fun albumsOf(tracks: List<SubsonicTrack>): List<SubsonicTrack> =
            tracks.filter { it.albumId.isNotBlank() && !it.id.startsWith(OpenedFile.PREFIX) }.distinctBy { it.albumId }

        /** Goes through the whole library once now, under the same conditions as the daily run. */
        fun runNow(context: Context, settings: MotionArtworkSettings) {
            if (!wanted(settings)) return
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val request = OneTimeWorkRequestBuilder<MotionArtworkWorker>().setConstraints(constraints(settings)).build()
            work.enqueueUniqueWork("$NAME-now", ExistingWorkPolicy.REPLACE, request)
        }

        private fun constraints(settings: MotionArtworkSettings) = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        /** Starts, updates or stops the daily library run to match [settings]. */
        fun schedule(context: Context, settings: MotionArtworkSettings) {
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!wanted(settings)) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<MotionArtworkWorker>(1, TimeUnit.DAYS).setConstraints(constraints(settings)).build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
