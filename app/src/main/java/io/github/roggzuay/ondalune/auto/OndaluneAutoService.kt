package io.github.roggzuay.ondalune.auto

import android.content.ContentResolver
import android.media.MediaDescription
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.service.media.MediaBrowserService
import io.github.roggzuay.ondalune.OndaluneApp
import io.github.roggzuay.ondalune.R
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android Auto's way in. The car browses the library here (Home, Playlists, Albums, Downloads) and
 * is handed the app's own media session, so its buttons and "play …" by voice reach the player the
 * phone uses. Holding that player is what lets the car start music with the app closed.
 */
class OndaluneAutoService : MediaBrowserService() {
    private val scope = MainScope()
    private var player: OndaluneViewModel? = null

    /** What each opened folder was last sent, so the car is told only when one really changes. */
    private val sent = HashMap<String, List<AutoItem>>()

    override fun onCreate() {
        super.onCreate()
        sessionToken = OndaluneMediaBridge.sessionToken(this)
    }

    override fun onDestroy() {
        scope.cancel()
        if (player != null) {
            OndaluneMediaBridge.carConnected = false
            (application as OndaluneApp).player.release()
        }
        super.onDestroy()
    }

    // The system has already checked that the package is the caller's own. Anyone else is turned
    // away: the library is the listener's, and other apps have no business reading it.
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (clientPackageName != packageName && clientUid != Process.SYSTEM_UID && clientPackageName !in CarClients) return null
        player()
        return BrowserRoot(AutoLibrary.ROOT, Bundle().apply { putBoolean(CONTENT_STYLE_SUPPORTED, true) })
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowser.MediaItem>>) {
        result.detach()
        scope.launch {
            // A car can start the app cold, and asks for a folder once: give the library time to load.
            val library = player().autoLibrary
            val children = (withTimeoutOrNull(LIBRARY_WAIT_MS) { library.first { !it.isEmpty } } ?: library.value).children(parentId)
            if (children != null) sent[parentId] = children
            result.sendResult(children?.map(::mediaItem))
        }
    }

    /** The shared player, taken when the first client is let in: one that is turned away costs nothing. */
    private fun player(): OndaluneViewModel = player ?: (application as OndaluneApp).player.acquire().also { held ->
        player = held
        OndaluneMediaBridge.carConnected = true
        scope.launch {
            held.autoLibrary.collect { library ->
                sent.filter { (id, items) -> library.children(id) != items }.keys.forEach(::notifyChildrenChanged)
            }
        }
    }

    private fun mediaItem(item: AutoItem): MediaBrowser.MediaItem {
        val description = MediaDescription.Builder()
            .setMediaId(item.id)
            .setTitle(item.title)
            .setSubtitle(item.subtitle.ifEmpty { null })
            .setIconUri(TabIcons[item.id]?.let(::drawableUri) ?: OndaluneArtProvider.uriFor(this, item.coverArtUrl))
            // Albums read best as a grid of covers; everything else is a list.
            .setExtras(if (item.id == AutoLibrary.ALBUMS) Bundle().apply { putInt(CONTENT_STYLE_BROWSABLE, STYLE_GRID) } else null)
            .build()
        return MediaBrowser.MediaItem(
            description,
            if (item.browsable) MediaBrowser.MediaItem.FLAG_BROWSABLE else MediaBrowser.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun drawableUri(id: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(packageName)
        .appendPath(resources.getResourceTypeName(id))
        .appendPath(resources.getResourceEntryName(id))
        .build()

    private companion object {
        const val LIBRARY_WAIT_MS = 8_000L
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val STYLE_GRID = 2

        /** Android Auto, and the Google assistant that plays what is asked for by voice. */
        val CarClients = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.googlequicksearchbox",
            "com.google.android.carassistant"
        )
        val TabIcons = mapOf(
            AutoLibrary.HOME to R.drawable.ic_auto_home,
            AutoLibrary.PLAYLISTS to R.drawable.ic_auto_playlists,
            AutoLibrary.ALBUMS to R.drawable.ic_auto_albums,
            AutoLibrary.DOWNLOADS to R.drawable.ic_auto_downloads
        )
    }
}
