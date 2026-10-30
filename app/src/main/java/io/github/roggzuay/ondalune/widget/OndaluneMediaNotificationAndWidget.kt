package io.github.roggzuay.ondalune.widget

import android.net.wifi.WifiManager
import android.os.PowerManager
import io.github.roggzuay.ondalune.ui.theme.ArtworkPaletteCache
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.roggzuay.ondalune.MainActivity
import io.github.roggzuay.ondalune.R
import io.github.roggzuay.ondalune.audio.OndaluneAudioEngine
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.ui.theme.DynamicSurfacePalette
import io.github.roggzuay.ondalune.ui.theme.resolveDynamicSurfacePalette
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

enum class MediaBridgeCommand {
    TOGGLE_PLAY_PAUSE,
    PLAY,
    PAUSE,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    TOGGLE_STAR
}

/**
 * Application-scoped Playback Runtime holder ensuring that [OndaluneAudioEngine] and background
 * transport commands survive Activity recreation or closure without interrupting active playback.
 */
object OndalunePlaybackRuntime {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var sharedEngine: OndaluneAudioEngine? = null

    fun getOrCreateEngine(
        context: Context,
        subsonicClient: OpenSubsonicClient = OpenSubsonicClient()
    ): OndaluneAudioEngine {
        return sharedEngine ?: synchronized(this) {
            sharedEngine ?: OndaluneAudioEngine(
                context = context.applicationContext,
                subsonicClient = subsonicClient,
                scope = runtimeScope
            ).also { sharedEngine = it }
        }
    }

    fun peekEngine(): OndaluneAudioEngine? = sharedEngine
}

/**
 * Dedicated Foreground Media Playback Service (`foregroundServiceType="mediaPlayback"`).
 * Keeps OpenSubsonic streaming & real-time DSP playback alive when the user leaves the Activity,
 * locks the device, or interacts via notifications, widgets, and hardware headsets.
 */
class OndalunePlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    // The audio engine decodes and streams on its own threads. With the screen off the CPU and
    // Wi-Fi radio would otherwise sleep mid-song, so both stay awake while playback is foreground.
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun holdLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ondalune:playback")
                .apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        }
        if (wifiLock?.isHeld != true) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager)
                .createWifiLock(mode, "Ondalune:stream")
                .apply { setReferenceCounted(false); runCatching { acquire() } }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            OndaluneMediaBridge.ACTION_SERVICE_START_FOREGROUND -> {
                // startForeground must always follow startForegroundService (Android 8+ kills the
                // app otherwise), so fall back to a plain notification if no track is loaded yet.
                val notification = OndaluneMediaBridge.buildCurrentMediaNotification(applicationContext)
                    ?: OndaluneMediaBridge.buildFallbackNotification(applicationContext)
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            OndaluneMediaBridge.NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(OndaluneMediaBridge.NOTIFICATION_ID, notification)
                    }
                    holdLocks()
                }
            }
            OndaluneMediaBridge.ACTION_SERVICE_STOP_FOREGROUND -> {
                releaseLocks()
                runCatching {
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
                stopSelf()
            }
            OndaluneMediaBridge.ACTION_TOGGLE_PLAY -> {
                OndaluneMediaBridge.dispatchCommand(applicationContext, MediaBridgeCommand.TOGGLE_PLAY_PAUSE)
            }
            OndaluneMediaBridge.ACTION_SKIP_NEXT -> {
                OndaluneMediaBridge.dispatchCommand(applicationContext, MediaBridgeCommand.SKIP_NEXT)
            }
            OndaluneMediaBridge.ACTION_SKIP_PREVIOUS -> {
                OndaluneMediaBridge.dispatchCommand(applicationContext, MediaBridgeCommand.SKIP_PREVIOUS)
            }
            OndaluneMediaBridge.ACTION_TOGGLE_STAR -> {
                OndaluneMediaBridge.dispatchCommand(applicationContext, MediaBridgeCommand.TOGGLE_STAR)
            }
        }
        return START_STICKY
    }
}

/**
 * BroadcastReceiver that handles transport button taps from both the system Media Notification
 * and the Home Screen AppWidget. Routes through [OndaluneMediaBridge.dispatchCommand] so controls
 * remain responsive even if the UI/ViewModel was recreated or detached.
 */
class OndaluneMediaControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            OndaluneMediaBridge.ACTION_TOGGLE_PLAY -> {
                OndaluneMediaBridge.dispatchCommand(context, MediaBridgeCommand.TOGGLE_PLAY_PAUSE)
            }
            OndaluneMediaBridge.ACTION_SKIP_NEXT -> {
                OndaluneMediaBridge.dispatchCommand(context, MediaBridgeCommand.SKIP_NEXT)
            }
            OndaluneMediaBridge.ACTION_SKIP_PREVIOUS -> {
                OndaluneMediaBridge.dispatchCommand(context, MediaBridgeCommand.SKIP_PREVIOUS)
            }
            OndaluneMediaBridge.ACTION_TOGGLE_STAR -> {
                OndaluneMediaBridge.dispatchCommand(context, MediaBridgeCommand.TOGGLE_STAR)
            }
        }
    }
}

/**
 * Material 3 Expressive Home Screen AppWidgetProvider supporting:
 * 1. Compact Player Widget
 * 2. Expanded Studio Deck Widget (larger artwork, progress bar, elapsed/duration time, favorite action)
 * 3. Smart Dynamic Player Widget (adapts automatically on widget resize via [onAppWidgetOptionsChanged]
 *    and supports Dynamic, Dark, Light, and Transparent appearances).
 */
class OndaluneAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        OndaluneMediaBridge.refreshAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        OndaluneMediaBridge.refreshAllWidgets(
            context = context,
            explicitManager = appWidgetManager,
            explicitIds = intArrayOf(appWidgetId),
            explicitOptionsMap = mapOf(appWidgetId to newOptions)
        )
    }
}

/**
 * Central coordinator for the Ondalune Native System MediaSession Notification, Foreground Service,
 * and Dynamic Home Screen Widget.
 *
 * Uses a real Android [MediaSession] + [Notification.MediaStyle] with [PlaybackState.ACTION_SEEK_TO],
 * custom Star/Favorite action, and [MediaMetadata.METADATA_KEY_DURATION] so Samsung One UI, Pixel,
 * and Android 11–16 render the native dynamic system media player with a live, interactive progress
 * scrubber in the notification panel and lock screen.
 */
object OndaluneMediaBridge {
    const val CHANNEL_ID = "ondalune_playback_channel"
    const val NOTIFICATION_ID = 4108
    const val ACTION_TOGGLE_PLAY = "io.github.roggzuay.ondalune.ondalune.ACTION_TOGGLE_PLAY"
    const val ACTION_SKIP_NEXT = "io.github.roggzuay.ondalune.ondalune.ACTION_SKIP_NEXT"
    const val ACTION_SKIP_PREVIOUS = "io.github.roggzuay.ondalune.ondalune.ACTION_SKIP_PREVIOUS"
    const val ACTION_TOGGLE_STAR = "io.github.roggzuay.ondalune.ondalune.ACTION_TOGGLE_STAR"
    const val EXTRA_OPEN_LYRICS = "io.github.roggzuay.ondalune.ondalune.EXTRA_OPEN_LYRICS"
    const val ACTION_SERVICE_START_FOREGROUND = "io.github.roggzuay.ondalune.ondalune.ACTION_SERVICE_START_FOREGROUND"
    const val ACTION_SERVICE_STOP_FOREGROUND = "io.github.roggzuay.ondalune.ondalune.ACTION_SERVICE_STOP_FOREGROUND"
    private const val CUSTOM_ACTION_STAR = "io.github.roggzuay.ondalune.ondalune.CUSTOM_ACTION_STAR"

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Server-scoped LruCache keyed by "${serverId}_${url}" to prevent cross-server cache leakage
    // Full-resolution covers (up to 720 px) for the lock screen; sized by bytes, ~24 MB.
    private val remoteArtworkCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Rendered notification & widget artwork bitmap cache so progress updates never re-allocate bitmaps
    private val renderedArtworkCache = object : LruCache<String, Bitmap>(24) {}
    private val renderedWidgetCardCache = object : LruCache<String, Bitmap>(12) {}
    private val inFlightKeys = mutableSetOf<String>()

    @Volatile
    private var mediaSession: MediaSession? = null

    @Volatile
    var onCommand: ((MediaBridgeCommand) -> Unit)? = null

    @Volatile
    var onSeekTo: ((Long) -> Unit)? = null

    @Volatile
    private var lastTrack: SubsonicTrack? = null

    @Volatile
    private var lastIsPlaying: Boolean = false

    @Volatile
    private var lastIsBuffering: Boolean = false

    @Volatile
    private var lastPositionMs: Long = 0L

    @Volatile
    private var lastDurationMs: Long = 0L

    @Volatile
    private var lastPlaybackSpeed: Float = 1.0f

    @Volatile
    private var lastSettings: PlayerCustomizationSettings = PlayerCustomizationSettings()

    @Volatile
    private var lyricsTrackId: String? = null

    @Volatile
    private var lyricsResult: TrackLyricsResult = TrackLyricsResult()

    /** Lyrics for the widget's lyric lines; the app pushes them whenever a track's lyrics load. */
    fun updateLyrics(context: Context, trackId: String, result: TrackLyricsResult) {
        lyricsTrackId = trackId
        lyricsResult = result
        refreshAllWidgets(context.applicationContext)
    }

    /**
     * The line being sung and the one after it. Synced lyrics follow the timestamps; plain lyrics
     * are spread evenly over the song so the widget still moves along with it.
     */
    private fun currentLyricPair(track: SubsonicTrack?): Pair<String, String>? {
        if (track == null || track.id != lyricsTrackId) return null
        val synced = lyricsResult.syncedLines.filter { it.text.isNotBlank() }
        if (synced.isNotEmpty()) {
            val index = synced.indexOfLast { it.timeMs <= lastPositionMs }
            if (index < 0) return "♪" to synced.first().text
            return synced[index].text to synced.getOrNull(index + 1)?.text.orEmpty()
        }
        val plain = lyricsResult.plainTextLines.filter { it.isNotBlank() }
        if (plain.isEmpty()) return null
        val index = ((lastPositionMs.toDouble() / lastDurationMs.coerceAtLeast(1000L)) * plain.size).toInt().coerceIn(0, plain.lastIndex)
        return plain[index] to plain.getOrNull(index + 1).orEmpty()
    }

    private fun cacheKey(serverId: Long, url: String): String = "${serverId}_$url"

    fun clearRemoteArtworkCache() {
        remoteArtworkCache.evictAll()
        renderedArtworkCache.evictAll()
    }

    /**
     * Dispatches a transport command to the active ViewModel callback if attached, or falls back
     * directly to [OndalunePlaybackRuntime] when the Activity/ViewModel is detached in background.
     */
    fun dispatchCommand(context: Context, command: MediaBridgeCommand) {
        val handler = onCommand
        if (handler != null) {
            handler.invoke(command)
            return
        }
        val engine = OndalunePlaybackRuntime.peekEngine() ?: return
        when (command) {
            MediaBridgeCommand.TOGGLE_PLAY_PAUSE -> engine.togglePlayPause()
            MediaBridgeCommand.PLAY -> if (!engine.isPlaying.value) engine.togglePlayPause()
            MediaBridgeCommand.PAUSE -> engine.pausePlayback()
            MediaBridgeCommand.SKIP_NEXT -> engine.seekTo(0L)
            MediaBridgeCommand.SKIP_PREVIOUS -> engine.seekTo(0L)
            MediaBridgeCommand.TOGGLE_STAR -> {
                val cur = lastTrack ?: return
                lastTrack = cur.copy(starred = !cur.starred)
            }
        }
        syncPlaybackAndThemeState(
            context = context.applicationContext,
            track = lastTrack,
            isPlaying = engine.isPlaying.value,
            settings = lastSettings,
            positionMs = engine.positionMs.value,
            durationMs = engine.durationMs.value,
            playbackSpeed = engine.playbackSpeed.value,
            isBuffering = engine.isBuffering.value
        )
    }

    private fun ensureMediaSession(context: Context): MediaSession {
        val existing = mediaSession
        if (existing != null) return existing
        val appCtx = context.applicationContext
        return synchronized(this) {
            mediaSession ?: MediaSession(appCtx, "OndaluneMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        dispatchCommand(appCtx, MediaBridgeCommand.PLAY)
                    }

                    override fun onPause() {
                        dispatchCommand(appCtx, MediaBridgeCommand.PAUSE)
                    }

                    override fun onSkipToNext() {
                        dispatchCommand(appCtx, MediaBridgeCommand.SKIP_NEXT)
                    }

                    override fun onSkipToPrevious() {
                        dispatchCommand(appCtx, MediaBridgeCommand.SKIP_PREVIOUS)
                    }

                    override fun onSeekTo(pos: Long) {
                        val target = pos.coerceAtLeast(0L)
                        val seekHandler = onSeekTo
                        if (seekHandler != null) {
                            seekHandler.invoke(target)
                        } else {
                            OndalunePlaybackRuntime.peekEngine()?.seekTo(target)
                        }
                    }

                    override fun onCustomAction(action: String, extras: Bundle?) {
                        if (action == CUSTOM_ACTION_STAR) {
                            dispatchCommand(appCtx, MediaBridgeCommand.TOGGLE_STAR)
                        }
                    }
                })
                isActive = true
                mediaSession = this
            }
        }
    }

    fun syncPlaybackAndThemeState(
        context: Context,
        track: SubsonicTrack?,
        isPlaying: Boolean,
        settings: PlayerCustomizationSettings,
        positionMs: Long = lastPositionMs,
        durationMs: Long = lastDurationMs,
        playbackSpeed: Float = lastPlaybackSpeed,
        isBuffering: Boolean = lastIsBuffering
    ) {
        val appContext = context.applicationContext
        val previousIsPlaying = lastIsPlaying
        lastTrack = track
        lastIsPlaying = isPlaying
        lastIsBuffering = isBuffering
        lastSettings = settings
        lastPositionMs = positionMs.coerceAtLeast(0L)
        val resolvedDuration = if (durationMs > 0L) {
            durationMs
        } else {
            ((track?.durationSec ?: 0) * 1000L).coerceAtLeast(1000L)
        }
        lastDurationMs = resolvedDuration
        lastPlaybackSpeed = playbackSpeed.coerceIn(0.5f, 2.5f)

        if (track != null && track.coverArtUrl.startsWith("http", ignoreCase = true)) {
            prefetchRemoteArtworkIfNeeded(appContext, track.serverId, track.coverArtUrl)
        }

        val palette = resolvePalette(appContext, track, settings)
        updateMediaSessionStateAndMetadata(
            context = appContext,
            track = track,
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            positionMs = lastPositionMs,
            durationMs = resolvedDuration,
            playbackSpeed = lastPlaybackSpeed,
            settings = settings,
            palette = palette
        )
        postOrUpdateMediaNotification(appContext, track, isPlaying, settings, palette)
        syncForegroundPlaybackService(appContext, track, isPlaying || isBuffering, previousIsPlaying)
        refreshAllWidgets(appContext)
    }

    private fun syncForegroundPlaybackService(
        context: Context,
        track: SubsonicTrack?,
        isPlaying: Boolean,
        wasPlaying: Boolean
    ) {
        if (track == null) return
        runCatching {
            if (isPlaying) {
                foregroundStopHandler.removeCallbacksAndMessages(null)
                val serviceIntent = Intent(context, OndalunePlaybackService::class.java).apply {
                    action = ACTION_SERVICE_START_FOREGROUND
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else if (wasPlaying) {
                // Wait before leaving the foreground: between songs the engine briefly reports
                // "not playing". Dropping the service then meant that, with the phone locked,
                // Android 12+ refused to restart it and the next song never began.
                foregroundStopHandler.removeCallbacksAndMessages(null)
                foregroundStopHandler.postDelayed({
                    if (!lastIsPlaying) runCatching {
                        context.startService(Intent(context, OndalunePlaybackService::class.java).apply {
                            action = ACTION_SERVICE_STOP_FOREGROUND
                        })
                    }
                }, ForegroundStopGraceMs)
            }
        }
    }

    private val foregroundStopHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** How long a pause must last before the playback service leaves the foreground. */
    private const val ForegroundStopGraceMs = 30_000L

    /** Minimal ongoing notification used when the service must go foreground before a track is set. */
    fun buildFallbackNotification(context: Context): Notification {
        ensureNotificationChannel(context)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Ondalune")
            .setContentText("Getting your music ready")
            .setOngoing(true)
            .build()
    }

    /**
     * Lightweight progress-only update called when seeking, changing speed, or advancing playback
     * so Samsung One UI's MediaSession progress bar and the Home Screen Widget stay in lockstep.
     */
    fun updatePlaybackProgress(
        context: Context,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        playbackSpeed: Float,
        isBuffering: Boolean = false
    ) {
        val appContext = context.applicationContext
        val track = lastTrack ?: return
        lastPositionMs = positionMs.coerceAtLeast(0L)
        lastDurationMs = if (durationMs > 0L) durationMs else (track.durationSec * 1000L).coerceAtLeast(1000L)
        lastIsPlaying = isPlaying
        lastIsBuffering = isBuffering
        lastPlaybackSpeed = playbackSpeed.coerceIn(0.5f, 2.5f)

        try {
            val session = ensureMediaSession(appContext)
            session.setPlaybackState(
                buildPlaybackState(
                    isPlaying = lastIsPlaying,
                    isBuffering = lastIsBuffering,
                    positionMs = lastPositionMs,
                    playbackSpeed = lastPlaybackSpeed,
                    isStarred = track.starred
                )
            )
            refreshAllWidgets(appContext)
        } catch (_: Exception) {
        }
    }

    private fun prefetchRemoteArtworkIfNeeded(context: Context, serverId: Long, url: String) {
        val key = cacheKey(serverId, url)
        if (url.isBlank() || remoteArtworkCache.get(key) != null) return
        synchronized(inFlightKeys) {
            if (!inFlightKeys.add(key)) return
        }
        bridgeScope.launch {
            try {
                val req = Request.Builder().url(url).get().build()
                imageHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (decoded != null) {
                                remoteArtworkCache.put(key, squareCover(decoded, 720))
                                val current = lastTrack
                                if (current?.coverArtUrl == url && current.serverId == serverId) {
                                    syncPlaybackAndThemeState(
                                        context = context,
                                        track = current,
                                        isPlaying = lastIsPlaying,
                                        settings = lastSettings,
                                        positionMs = lastPositionMs,
                                        durationMs = lastDurationMs,
                                        playbackSpeed = lastPlaybackSpeed,
                                        isBuffering = lastIsBuffering
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                synchronized(inFlightKeys) {
                    inFlightKeys.remove(key)
                }
            }
        }
    }

    private fun buildPlaybackState(
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        playbackSpeed: Float,
        isStarred: Boolean
    ): PlaybackState {
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        val stateCode = when {
            isBuffering -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }

        val starCustomAction = PlaybackState.CustomAction.Builder(
            CUSTOM_ACTION_STAR,
            if (isStarred) "Unstar" else "Star",
            if (isStarred) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline
        ).build()

        return PlaybackState.Builder()
            .setActions(actions)
            .addCustomAction(starCustomAction)
            .setState(
                stateCode,
                positionMs.coerceAtLeast(0L),
                if (isPlaying) playbackSpeed else 0f,
                SystemClock.elapsedRealtime()
            )
            .build()
    }

    private fun updateMediaSessionStateAndMetadata(
        context: Context,
        track: SubsonicTrack?,
        isPlaying: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        settings: PlayerCustomizationSettings,
        palette: DynamicSurfacePalette
    ) {
        if (track == null) return
        try {
            val session = ensureMediaSession(context)
            session.isActive = true

            // The lock screen and media card draw this full width: give them the untouched square
            // cover (no rounded corners, which render black, and no theme tint), sharp at 720 px.
            val artworkBitmap = remoteArtworkCache.get(cacheKey(track.serverId, track.coverArtUrl))
                ?.takeIf { track.coverArtUrl.isNotBlank() && !it.isRecycled }
                ?: renderExpressiveArtworkBitmap(
                    palette = palette,
                    track = track,
                    unifiedMaterialYou = settings.unifiedMaterialYouColor,
                    sizePx = 512,
                    rounded = false
                )

            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_GENRE, track.genre)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.coerceAtLeast(1000L))
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, artworkBitmap)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artworkBitmap)
                .build()

            session.setMetadata(metadata)
            session.setPlaybackState(
                buildPlaybackState(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    positionMs = positionMs,
                    playbackSpeed = playbackSpeed,
                    isStarred = track.starred
                )
            )
        } catch (_: Exception) {
        }
    }

    fun resolvePalette(
        context: Context,
        track: SubsonicTrack?,
        settings: PlayerCustomizationSettings
    ): DynamicSurfacePalette {
        val defaultSeed = settings.curatedPalette.primarySeedHex
        val defaultAccent = settings.curatedPalette.accentSeedHex
        return resolveDynamicSurfacePalette(
            context = context,
            seedHex = if (settings.harmonizePlayerWithArtwork && track != null) ArtworkPaletteCache.seedHexFor(track) else defaultSeed,
            accentHex = if (settings.harmonizePlayerWithArtwork && track != null) track.accentHex else defaultAccent,
            unifiedMaterialYouColor = settings.unifiedMaterialYouColor,
            useMaterialYouDynamic = settings.useMaterialYouDynamicColor,
            harmonizeWithArtwork = settings.harmonizePlayerWithArtwork
        )
    }

    /**
     * Resolves whether a given widget instance should display the Expanded Studio Deck or
     * Compact Transport Strip based on [PlayerCustomizationSettings.widgetLayoutMode] and
     * the widget's live dimensions (`OPTION_APPWIDGET_MIN_WIDTH` / `OPTION_APPWIDGET_MIN_HEIGHT`).
     */
    fun isExpandedWidgetPresentation(
        settings: PlayerCustomizationSettings,
        options: Bundle?
    ): Boolean {
        return when (settings.widgetLayoutMode) {
            WidgetLayoutMode.COMPACT -> false
            WidgetLayoutMode.EXPANDED -> true
            // Height decides: below ~2 rows there is no room for the progress + transport row.
            WidgetLayoutMode.AUTO_SMART -> widgetHeightDp(options) >= 120
        }
    }

    /** Portrait height of the widget (launchers report the portrait size as MAX_HEIGHT). */
    private fun widgetHeightDp(options: Bundle?): Int =
        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: 150

    private fun formatTimeSec(sec: Long): String {
        val clamped = sec.coerceAtLeast(0L)
        val mins = clamped / 60L
        val secs = clamped % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    /**
     * Requests the Android launcher to pin the Ondalune dynamic player widget onto the home screen.
     */
    fun requestPinHomeScreenWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, OndaluneAppWidgetProvider::class.java)
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun refreshAllWidgets(
        context: Context,
        explicitManager: AppWidgetManager? = null,
        explicitIds: IntArray? = null,
        explicitOptionsMap: Map<Int, Bundle> = emptyMap()
    ) {
        try {
            val manager = explicitManager ?: AppWidgetManager.getInstance(context)
            val ids = explicitIds ?: manager.getAppWidgetIds(
                ComponentName(context, OndaluneAppWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val track = lastTrack
            val isPlaying = lastIsPlaying
            val isBuffering = lastIsBuffering
            val settings = lastSettings
            val palette = resolvePalette(context, track, settings)

            val artBitmap = renderExpressiveArtworkBitmap(
                palette = palette,
                track = track,
                unifiedMaterialYou = settings.unifiedMaterialYouColor,
                sizePx = 192
            )
            val wideButton = renderExpressivePlayButtonBitmap(palette, isPlaying, widthPx = 180, heightPx = 144)
            val roundButton = renderExpressivePlayButtonBitmap(palette, isPlaying, widthPx = 144, heightPx = 144)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                100,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val lyricsPendingIntent = PendingIntent.getActivity(
                context,
                105,
                Intent(launchIntent).putExtra(EXTRA_OPEN_LYRICS, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevPending = buildControlPendingIntent(context, ACTION_SKIP_PREVIOUS, 101)
            val playPending = buildControlPendingIntent(context, ACTION_TOGGLE_PLAY, 102)
            val nextPending = buildControlPendingIntent(context, ACTION_SKIP_NEXT, 103)
            val starPending = buildControlPendingIntent(context, ACTION_TOGGLE_STAR, 104)

            val stateLabel = when {
                track == null -> "Ready"
                isBuffering -> "Loading"
                isPlaying -> "Playing"
                else -> "Paused"
            }
            val codecTag = track?.suffix?.uppercase() ?: "FLAC"
            val durationMs = lastDurationMs.coerceAtLeast(1000L)
            val progressPermille = ((lastPositionMs.toDouble() / durationMs.toDouble()) * 1000.0)
                .toInt()
                .coerceIn(0, 1000)

            val isLightWidget = settings.widgetThemeMode == WidgetThemeMode.LIGHT
            val titleTextColor = if (isLightWidget) 0xFF10141C.toInt() else palette.onContainerArgb
            val subtitleTextColor = if (isLightWidget) 0xFF424857.toInt() else palette.onSurfaceVariantArgb
            val density = context.resources.displayMetrics.density

            for (widgetId in ids) {
                val options = explicitOptionsMap[widgetId] ?: runCatching {
                    manager.getAppWidgetOptions(widgetId)
                }.getOrNull()
                val isExpanded = isExpandedWidgetPresentation(settings, options)

                // Render the card at the widget's real size so corners never stretch.
                val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: 320
                val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: 150
                val bgBitmap = renderWidgetBackgroundBitmap(
                    context = context,
                    palette = palette,
                    track = track,
                    themeMode = settings.widgetThemeMode,
                    widthPx = (widthDp * density).toInt().coerceIn(200, 1080),
                    heightPx = (heightDp * density).toInt().coerceIn(120, 720)
                )

                val views = RemoteViews(context.packageName, R.layout.ondalune_player_widget)
                views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)
                views.setImageViewBitmap(R.id.widget_artwork, artBitmap)
                views.setImageViewBitmap(R.id.widget_btn_play_pause, wideButton)
                views.setImageViewBitmap(R.id.widget_btn_play_compact, roundButton)

                views.setViewVisibility(
                    R.id.widget_artwork,
                    if (settings.widgetShowArtwork) View.VISIBLE else View.GONE
                )

                views.setTextViewText(R.id.widget_badge, "$stateLabel · $codecTag")
                views.setTextColor(R.id.widget_badge, palette.primaryArgb)
                views.setTextViewText(R.id.widget_title, track?.title ?: "Ondalune")
                views.setTextColor(R.id.widget_title, titleTextColor)
                views.setTextViewText(
                    R.id.widget_subtitle,
                    if (track != null) "${track.artist} • ${track.album}" else "Tap to start listening"
                )
                views.setTextColor(R.id.widget_subtitle, subtitleTextColor)

                // Compact: one row, transport inline. Expanded: progress + transport below, centred.
                // Tall: track pinned top, transport pinned bottom, bigger artwork.
                val isTall = isExpanded && widgetHeightDp(options) >= 180
                views.setViewVisibility(R.id.widget_controls_row, if (isExpanded) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_compact_controls, if (isExpanded) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_spacer, if (isTall) View.VISIBLE else View.GONE)
                val lyric = if (isTall && settings.widgetShowLyrics) currentLyricPair(track) else null
                views.setViewVisibility(R.id.widget_lyrics, if (lyric != null) View.VISIBLE else View.GONE)
                if (lyric != null) {
                    views.setTextViewText(R.id.widget_lyric_current, lyric.first)
                    views.setTextColor(R.id.widget_lyric_current, titleTextColor)
                    views.setTextViewText(R.id.widget_lyric_next, lyric.second)
                    views.setTextColor(R.id.widget_lyric_next, (subtitleTextColor and 0x00FFFFFF) or (0xA6 shl 24))
                    views.setViewVisibility(R.id.widget_lyric_next, if (lyric.second.isNotBlank()) View.VISIBLE else View.GONE)
                    views.setOnClickPendingIntent(R.id.widget_lyrics, lyricsPendingIntent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val artDp = if (isTall) 84f else 56f
                    views.setViewLayoutWidth(R.id.widget_artwork, artDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(R.id.widget_artwork, artDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
                }

                views.setTextViewText(R.id.widget_time_label, formatTimeSec(lastPositionMs / 1000L))
                views.setTextViewText(R.id.widget_time_total, if (track != null) formatTimeSec(durationMs / 1000L) else "0:00")
                views.setTextColor(R.id.widget_time_label, subtitleTextColor)
                views.setTextColor(R.id.widget_time_total, subtitleTextColor)
                views.setProgressBar(R.id.widget_progress_bar, 1000, progressPermille, isBuffering)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        R.id.widget_progress_bar,
                        "setProgressTintList",
                        ColorStateList.valueOf(palette.primaryArgb)
                    )
                    views.setColorStateList(
                        R.id.widget_progress_bar,
                        "setProgressBackgroundTintList",
                        ColorStateList.valueOf((subtitleTextColor and 0x00FFFFFF) or (0x40 shl 24))
                    )
                }

                // Heart, same as the app's favourite: filled + primary when favourited.
                val showHeart = isExpanded && settings.widgetShowFavoriteButton && track != null
                views.setViewVisibility(R.id.widget_btn_star, if (showHeart) View.VISIBLE else View.GONE)
                views.setImageViewResource(
                    R.id.widget_btn_star,
                    if (track?.starred == true) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline
                )
                views.setInt(R.id.widget_btn_star, "setColorFilter", if (track?.starred == true) palette.primaryArgb else subtitleTextColor)

                for (id in listOf(R.id.widget_btn_prev, R.id.widget_btn_next, R.id.widget_btn_prev_compact, R.id.widget_btn_next_compact)) {
                    views.setInt(id, "setColorFilter", titleTextColor)
                }

                views.setOnClickPendingIntent(R.id.widget_root, contentPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_artwork, contentPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPending)
                views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)
                views.setOnClickPendingIntent(R.id.widget_btn_prev_compact, prevPending)
                views.setOnClickPendingIntent(R.id.widget_btn_play_compact, playPending)
                views.setOnClickPendingIntent(R.id.widget_btn_next_compact, nextPending)
                views.setOnClickPendingIntent(R.id.widget_btn_star, starPending)

                manager.updateAppWidget(widgetId, views)
            }
        } catch (_: Exception) {
        }
    }

    fun buildCurrentMediaNotification(context: Context): Notification? {
        val track = lastTrack ?: return null
        val palette = resolvePalette(context, track, lastSettings)
        return buildMediaNotificationInternal(context, track, lastIsPlaying, lastSettings, palette)
    }

    private fun buildMediaNotificationInternal(
        context: Context,
        track: SubsonicTrack,
        isPlaying: Boolean,
        settings: PlayerCustomizationSettings,
        palette: DynamicSurfacePalette
    ): Notification? {
        ensureNotificationChannel(context)
        return try {
            val session = ensureMediaSession(context)
            val largeIcon = renderExpressiveArtworkBitmap(
                palette = palette,
                track = track,
                unifiedMaterialYou = settings.unifiedMaterialYouColor,
                sizePx = 320
            )

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                200,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevPending = buildControlPendingIntent(context, ACTION_SKIP_PREVIOUS, 201)
            val playPending = buildControlPendingIntent(context, ACTION_TOGGLE_PLAY, 202)
            val nextPending = buildControlPendingIntent(context, ACTION_SKIP_NEXT, 203)
            val starPending = buildControlPendingIntent(context, ACTION_TOGGLE_STAR, 204)

            val modeLabel = if (settings.unifiedMaterialYouColor) {
                "Material You Unified"
            } else {
                "Dynamic Song Theme"
            }

            val prevAction = Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_media_previous),
                "Previous",
                prevPending
            ).build()

            val playPauseAction = Notification.Action.Builder(
                Icon.createWithResource(
                    context,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                ),
                if (isPlaying) "Pause" else "Play",
                playPending
            ).build()

            val nextAction = Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_media_next),
                "Next",
                nextPending
            ).build()

            val starAction = Notification.Action.Builder(
                Icon.createWithResource(
                    context,
                    if (track.starred) R.drawable.ic_widget_heart else R.drawable.ic_widget_heart_outline
                ),
                if (track.starred) "Unstar" else "Star",
                starPending
            ).build()

            val mediaStyle = Notification.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

            builder
                .setSmallIcon(
                    if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
                )
                .setLargeIcon(largeIcon)
                .setContentTitle(track.title)
                .setContentText("${track.artist} — ${track.album}")
                .setSubText("$modeLabel • ${track.suffix.uppercase()} ${track.bitRate}kbps")
                .setContentIntent(contentPendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(palette.primaryArgb)
                .setColorized(true)
                .setStyle(mediaStyle)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .addAction(starAction)
                .build()
        } catch (_: Exception) {
            null
        }
    }

    private fun postOrUpdateMediaNotification(
        context: Context,
        track: SubsonicTrack?,
        isPlaying: Boolean,
        settings: PlayerCustomizationSettings,
        palette: DynamicSurfacePalette
    ) {
        if (track == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        try {
            val notification = buildMediaNotificationInternal(context, track, isPlaying, settings, palette)
                ?: return
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ondalune Playback & Media Controls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active OpenSubsonic playback controls with dynamic Material You & artwork theming"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    private fun buildControlPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, OndaluneMediaControlReceiver::class.java).apply {
            this.action = action
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Widget card at its real pixel size: the theme surface with a soft blur of the current cover
     * (an 8x8 downscale stretched back up) under a left-to-right scrim, like the in-app player.
     * Cached per size/cover so progress ticks reuse it.
     */
    fun renderWidgetBackgroundBitmap(
        context: Context,
        palette: DynamicSurfacePalette,
        track: SubsonicTrack?,
        themeMode: WidgetThemeMode,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val cover = track?.takeIf { it.coverArtUrl.isNotBlank() }
            ?.let { remoteArtworkCache.get(cacheKey(it.serverId, it.coverArtUrl)) }
            ?.takeIf { !it.isRecycled }
        val key = "${palette.containerArgb}_${palette.primaryArgb}_${track?.id}_${cover != null}_${themeMode.name}_${widthPx}x$heightPx"
        renderedWidgetCardCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else {
            24f * context.resources.displayMetrics.density
        }
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })

        val base = when (themeMode) {
            WidgetThemeMode.DYNAMIC -> palette.containerArgb
            WidgetThemeMode.DARK -> 0xFF111318.toInt()
            WidgetThemeMode.LIGHT -> 0xFFF3F3F7.toInt()
            WidgetThemeMode.TRANSPARENT -> (palette.containerArgb and 0x00FFFFFF) or (0x99 shl 24)
        }
        fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
        canvas.drawColor(base)

        if (cover != null) {
            // The cover itself, heavily blurred and filling the card, so the widget takes on the
            // artwork's real colours. A scrim tinted with the cover's own darkest shade keeps the
            // text readable even on near-white covers.
            val blurred = blurredCover(cover)
            val side = maxOf(widthPx, heightPx) * 1.1f
            val left = (widthPx - side) / 2f
            val top = (heightPx - side) / 2f
            val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                alpha = if (themeMode == WidgetThemeMode.TRANSPARENT) 0xC0 else 0xFF
            }
            if (themeMode != WidgetThemeMode.TRANSPARENT) canvas.drawColor(0xFF000000.toInt())
            canvas.drawBitmap(blurred, null, RectF(left, top, left + side, top + side), blurPaint)

            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(averageColor(blurred), hsl)
            val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(averageColor(blurred)).toFloat()
            if (themeMode == WidgetThemeMode.LIGHT) {
                hsl[2] = 0.95f
                hsl[1] = hsl[1].coerceAtMost(0.35f)
                val tint = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                canvas.drawColor(withAlpha(tint, 0xB8))
            } else {
                hsl[2] = 0.08f
                hsl[1] = hsl[1].coerceAtMost(0.55f)
                val tint = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                // Brighter covers need a heavier scrim before light text sits on them.
                val strength = (0.38f + luminance * 0.55f).coerceIn(0.38f, 0.82f)
                canvas.drawColor(withAlpha(tint, (strength * 255).toInt()))
                canvas.drawRect(rect, Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, heightPx.toFloat(),
                        withAlpha(tint, 0x10), withAlpha(tint, 0x70),
                        Shader.TileMode.CLAMP
                    )
                })
            }
        } else {
            canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    widthPx * 0.88f, 0f, heightPx * 1.4f,
                    withAlpha(palette.primaryArgb, if (themeMode == WidgetThemeMode.LIGHT) 0x22 else 0x33), 0,
                    Shader.TileMode.CLAMP
                )
            })
        }

        renderedWidgetCardCache.put(key, bitmap)
        return bitmap
    }

    private val blurredCoverCache = LruCache<Bitmap, Bitmap>(4)

    /** A soft blur of [cover]: shrink to 48 px, three box-blur passes, then let bilinear scaling smooth it. */
    private fun blurredCover(cover: Bitmap): Bitmap {
        blurredCoverCache.get(cover)?.takeIf { !it.isRecycled }?.let { return it }
        val size = 48
        val small = Bitmap.createScaledBitmap(cover, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        repeat(3) { boxBlur(pixels, size, radius = 3) }
        val out = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        blurredCoverCache.put(cover, out)
        return out
    }

    /** One horizontal + vertical box-blur pass over a square ARGB buffer, edges clamped. */
    private fun boxBlur(pixels: IntArray, size: Int, radius: Int) {
        val temp = IntArray(pixels.size)
        for (horizontal in listOf(true, false)) {
            val src = if (horizontal) pixels else temp
            val dst = if (horizontal) temp else pixels
            for (line in 0 until size) {
                for (i in 0 until size) {
                    var r = 0; var g = 0; var b = 0
                    for (k in -radius..radius) {
                        val j = (i + k).coerceIn(0, size - 1)
                        val c = if (horizontal) src[line * size + j] else src[j * size + line]
                        r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                    }
                    val n = radius * 2 + 1
                    val value = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                    if (horizontal) dst[line * size + i] = value else dst[i * size + line] = value
                }
            }
        }
    }

    private fun averageColor(bitmap: Bitmap): Int {
        val one = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
        return one.getPixel(0, 0) or (0xFF shl 24)
    }

    /** Centre-crops [source] to a square no larger than [maxPx], keeping its aspect (no stretching). */
    private fun squareCover(source: Bitmap, maxPx: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        return if (side > maxPx) Bitmap.createScaledBitmap(cropped, maxPx, maxPx, true) else cropped
    }

    /**
     * Renders the artwork bitmap for the MediaSession/Notification and Home Screen Widget.
     * Cached in [renderedArtworkCache] so repeated metadata/progress updates do not re-render bitmaps.
     */
    fun renderExpressiveArtworkBitmap(
        palette: DynamicSurfacePalette,
        track: SubsonicTrack?,
        unifiedMaterialYou: Boolean,
        sizePx: Int = 192,
        rounded: Boolean = true
    ): Bitmap {
        val cachedRemote = if (track != null && track.coverArtUrl.isNotBlank()) {
            remoteArtworkCache.get(cacheKey(track.serverId, track.coverArtUrl))
        } else {
            null
        }
        val hasRemote = cachedRemote != null && !cachedRemote.isRecycled
        val artKey = "${track?.serverId ?: 0}_${track?.id.orEmpty()}_${hasRemote}_${palette.primaryArgb}_${palette.accentArgb}_${unifiedMaterialYou}_${sizePx}_$rounded"
        val existingArt = renderedArtworkCache.get(artKey)
        if (existingArt != null && !existingArt.isRecycled) {
            return existingArt
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        val corner = if (rounded) sizePx * 0.2f else 0f

        if (cachedRemote != null && !cachedRemote.isRecycled) {
            val clipPath = Path().apply {
                addRoundRect(rect, corner, corner, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)
            val srcRect = android.graphics.Rect(0, 0, cachedRemote.width, cachedRemote.height)
            val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (unifiedMaterialYou) {
                val tintOverlay = (palette.primaryArgb and 0x00FFFFFF) or (0x55 shl 24)
                imgPaint.colorFilter = PorterDuffColorFilter(tintOverlay, PorterDuff.Mode.SRC_ATOP)
            }
            canvas.drawBitmap(cachedRemote, srcRect, rect, imgPaint)
            canvas.restore()
            renderedArtworkCache.put(artKey, bitmap)
            return bitmap
        }

        // No cover yet: the same quiet tonal tile with a note the app shows while covers load.
        canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.containerHighArgb })
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (palette.onSurfaceVariantArgb and 0x00FFFFFF) or (0x99 shl 24)
            textSize = sizePx * 0.42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("\u266A", sizePx / 2f, sizePx / 2f - (notePaint.descent() + notePaint.ascent()) / 2f, notePaint)

        renderedArtworkCache.put(artKey, bitmap)
        return bitmap
    }

    /**
     * Play/pause button matching the app's: a primary pill while paused that squares its corners
     * while playing.
     */
    fun renderExpressivePlayButtonBitmap(
        palette: DynamicSurfacePalette,
        isPlaying: Boolean,
        widthPx: Int = 144,
        heightPx: Int = 144
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val corner = heightPx * if (isPlaying) 0.3f else 0.5f
        canvas.drawRoundRect(rect, corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.primaryArgb })

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.containerArgb }
        val cx = widthPx / 2f
        val cy = heightPx / 2f
        val unit = heightPx / 24f // draw in the 24dp icon grid
        if (isPlaying) {
            canvas.drawRoundRect(RectF(cx - 5f * unit, cy - 6f * unit, cx - 1.5f * unit, cy + 6f * unit), unit, unit, iconPaint)
            canvas.drawRoundRect(RectF(cx + 1.5f * unit, cy - 6f * unit, cx + 5f * unit, cy + 6f * unit), unit, unit, iconPaint)
        } else {
            canvas.drawPath(Path().apply {
                moveTo(cx - 3.5f * unit, cy - 6.5f * unit)
                lineTo(cx + 6f * unit, cy)
                lineTo(cx - 3.5f * unit, cy + 6.5f * unit)
                close()
            }, iconPaint)
        }
        return bitmap
    }

    private fun parseHexArgb(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
