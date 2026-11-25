package io.github.roggzuay.ondalune.cast

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Casting, in the build that has the Cast SDK.
 *
 * Device discovery and the picker are Google's own: a [MediaRouteButton] brings up the same sheet
 * every other app on the phone uses, and this class only listens for a session starting and
 * ending. That is far less of our code to be wrong, and it behaves the way people already expect.
 *
 * The receiver fetches the song from your server itself, so the URL handed to it has to be
 * reachable from the device and not only from this phone. A song stored on the phone cannot be
 * cast at all, and the caller is the one that knows which is which.
 */
class GoogleCastGateway(context: Context) : CastGateway {

    private val appContext = context.applicationContext
    override val supported: Boolean = true

    private val _state = MutableStateFlow(CastState())
    override val state: StateFlow<CastState> = _state.asStateFlow()

    private var castContext: CastContext? = null
    private var session: CastSession? = null
    private var endedCallback: (() -> Unit)? = null
    /** So one finished song is not reported twice while the device sits idle at the end of it. */
    private var reportedEndFor: String? = null
    private var loadedTrackId: String? = null

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = session?.remoteMediaClient ?: return
            _state.value = _state.value.copy(playing = client.isPlaying)
            val finished = client.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                client.idleReason == MediaStatus.IDLE_REASON_FINISHED
            if (finished && loadedTrackId != null && reportedEndFor != loadedTrackId) {
                reportedEndFor = loadedTrackId
                endedCallback?.invoke()
            }
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(s: CastSession) {
            _state.value = CastState(connecting = true, deviceName = s.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionStarted(s: CastSession, sessionId: String) = attachSession(s)

        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attachSession(s)

        override fun onSessionResuming(s: CastSession, sessionId: String) {
            _state.value = _state.value.copy(connecting = true)
        }

        override fun onSessionStartFailed(s: CastSession, error: Int) = detachSession()
        override fun onSessionResumeFailed(s: CastSession, error: Int) = detachSession()
        override fun onSessionSuspended(s: CastSession, reason: Int) = detachSession()
        override fun onSessionEnding(s: CastSession) = Unit
        override fun onSessionEnded(s: CastSession, error: Int) = detachSession()
    }

    private fun attachSession(s: CastSession) {
        session?.remoteMediaClient?.unregisterCallback(mediaCallback)
        session = s
        s.remoteMediaClient?.registerCallback(mediaCallback)
        _state.value = CastState(
            connected = true,
            deviceName = s.castDevice?.friendlyName.orEmpty(),
            playing = s.remoteMediaClient?.isPlaying == true,
            connecting = false
        )
    }

    private fun detachSession() {
        session?.remoteMediaClient?.unregisterCallback(mediaCallback)
        session = null
        loadedTrackId = null
        reportedEndFor = null
        _state.value = CastState()
    }

    override fun attach(activity: Activity) {
        if (castContext != null) return
        // Play Services can be missing or too old on a perfectly ordinary phone. That is not a
        // failure worth crashing over: the app simply has no Cast button.
        val ctx = runCatching { CastContext.getSharedInstance(appContext) }.getOrNull() ?: return
        castContext = ctx
        ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        ctx.sessionManager.currentCastSession?.let(::attachSession)
    }

    override fun load(
        track: SubsonicTrack,
        streamUrl: String,
        coverUrl: String,
        positionMs: Long,
        autoplay: Boolean
    ) {
        val client = session?.remoteMediaClient ?: return
        if (streamUrl.isBlank()) return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.title)
            putString(MediaMetadata.KEY_ARTIST, track.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
            if (coverUrl.isNotBlank()) addImage(WebImage(Uri.parse(coverUrl)))
        }
        val info = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            // The receiver works the real type out from the stream; this is only the hint.
            .setContentType(contentTypeFor(track.suffix))
            .setMetadata(metadata)
            .setStreamDuration(track.durationSec * 1000L)
            .build()
        loadedTrackId = track.id
        reportedEndFor = null
        client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(autoplay)
                .setCurrentTime(positionMs.coerceAtLeast(0L))
                .build()
        )
    }

    override fun play() {
        session?.remoteMediaClient?.play()
    }

    override fun pause() {
        session?.remoteMediaClient?.pause()
    }

    override fun seekTo(positionMs: Long) {
        session?.remoteMediaClient?.seek(
            com.google.android.gms.cast.MediaSeekOptions.Builder()
                .setPosition(positionMs.coerceAtLeast(0L))
                .build()
        )
    }

    override fun stop() {
        session?.remoteMediaClient?.stop()
        castContext?.sessionManager?.endCurrentSession(true)
        detachSession()
    }

    override fun positionMs(): Long = session?.remoteMediaClient?.approximateStreamPosition ?: 0L

    override fun durationMs(): Long = session?.remoteMediaClient?.streamDuration ?: 0L

    override fun onTrackEnded(callback: () -> Unit) {
        endedCallback = callback
    }

    private fun contentTypeFor(suffix: String): String = when (suffix.lowercase()) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "aac", "alac" -> "audio/mp4"
        "ogg", "oga", "vorbis" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }
}

fun createCastGateway(context: Context): CastGateway = GoogleCastGateway(context)

/**
 * A context androidx.mediarouter can actually work against.
 *
 * Everything in mediarouter reads the AppCompat `colorPrimary` attribute and runs the answer
 * through `ColorUtils.calculateContrast`, which refuses a transparent colour. Ondalune's own
 * theme is `android:Theme.DeviceDefault.NoActionBar`, where that attribute is not defined at
 * all, so it comes back as #0 and mediarouter throws "background can not be translucent: #0" the
 * moment a route button is built.
 *
 * Rather than drag the whole app onto an AppCompat theme for one button — which the build
 * without Cast could not even compile, since AppCompat only arrives with mediarouter — the few
 * pieces of mediarouter Ondalune uses are given an AppCompat context of their own. The activity
 * stays underneath it, which the dialogs need in order to put up a window.
 */
fun castDialogContext(context: Context): Context =
    ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)

/** The activity under a Compose context, which the route dialogs need to show themselves. */
private fun Context.activity(): Activity? {
    var at: Context? = this
    while (at is ContextWrapper) {
        if (at is Activity) return at
        at = at.baseContext
    }
    return null
}

/** What Ondalune asks the network for: devices that can play its music. */
private fun castSelector(): MediaRouteSelector = MediaRouteSelector.Builder()
    .addControlCategory(CastMediaControlIntent.categoryForCast(DefaultMediaReceiver))
    .build()

/**
 * The Cast button, in Ondalune's own hand rather than Google's.
 *
 * The route button that ships with mediarouter cannot be used here: it needs an AppCompat theme
 * and a FragmentActivity, and Ondalune has neither. Watching for routes and putting up the
 * picker are plain API calls though, so the button is ours and the device list is still the one
 * everybody already knows.
 *
 * It draws nothing at all until there is something on the network to play to, and it watches for
 * that passively — an active scan is for while a picker is open, not for the whole time a player
 * is on screen.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val router = runCatching { MediaRouter.getInstance(context) }.getOrNull()
            ?: return@DisposableEffect onDispose { }
        val selector = castSelector()
        val callback = object : MediaRouter.Callback() {
            private fun refresh() {
                available = router.isRouteAvailable(
                    selector,
                    MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE
                )
                connected = router.selectedRoute.matchesSelector(selector)
            }

            override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(r: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        runCatching {
            router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            available = router.isRouteAvailable(selector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE)
            connected = router.selectedRoute.matchesSelector(selector)
        }
        onDispose { runCatching { router.removeCallback(callback) } }
    }

    if (!available) return

    ExpressiveBounceIconButton(
        onClick = {
            val activity = context.activity() ?: return@ExpressiveBounceIconButton
            val themed = castDialogContext(activity)
            runCatching {
                if (connected) {
                    MediaRouteControllerDialog(themed).show()
                } else {
                    MediaRouteChooserDialog(themed).apply { routeSelector = castSelector() }.show()
                }
            }
        },
        size = 44.dp,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (connected) Icons.Filled.CastConnected else Icons.Filled.Cast,
            contentDescription = if (connected) "Playing on another device" else "Play on another device"
        )
    }
}

/**
 * What the Cast framework reads out of the manifest. The default media receiver plays a URL it is
 * given, which is exactly what Ondalune needs: the song already lives on your own server.
 *
 * Notifications and the lock screen stay Ondalune's own, so the Cast SDK's media notification is
 * turned off rather than fighting with the one the app already draws.
 */
class OndaluneCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(DefaultMediaReceiver)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setMediaSessionEnabled(false)
                    .setNotificationOptions(null)
                    .build()
            )
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null

}

/** Google's default media receiver: plays a media URL, needs no registration. */
private const val DefaultMediaReceiver = "CC1AD845"
