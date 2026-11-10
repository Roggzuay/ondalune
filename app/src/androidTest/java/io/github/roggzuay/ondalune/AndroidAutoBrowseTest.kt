package io.github.roggzuay.ondalune

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.roggzuay.ondalune.auto.OndaluneArtProvider
import io.github.roggzuay.ondalune.auto.OndaluneAutoService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Android Auto does, with no screen open: connect to the browser service, walk the tabs down
 * to a song, play it through the media session, and load a cover from the art provider.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoBrowseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun aCarBrowsesTheLibraryAndPlaysTheSongItTaps() = runBlocking {
        val connected = CompletableDeferred<Unit>()
        lateinit var browser: MediaBrowser
        instrumentation.runOnMainSync {
            browser = MediaBrowser(
                context, ComponentName(context, OndaluneAutoService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() { connected.complete(Unit) }
                    override fun onConnectionFailed() { connected.completeExceptionally(AssertionError("connection refused")) }
                },
                null
            )
            browser.connect()
        }
        withTimeout(WAIT_MS) { connected.await() }

        val tabs = browser.children(browser.root)
        assertEquals(listOf("home", "playlists", "albums", "downloads"), tabs.map { it.mediaId })
        assertTrue("tabs are folders with icons", tabs.all { it.isBrowsable && it.description.iconUri != null })

        val albums = browser.children("albums")
        assertTrue("the library has albums", albums.isNotEmpty())
        val song = browser.children(albums.first().mediaId!!).last()
        assertTrue(song.isPlayable)

        val controller = MediaController(context, browser.sessionToken)
        val asked = PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        assertEquals("the session offers play-by-id and play-by-voice", asked, controller.playbackState!!.actions and asked)

        controller.transportControls.playFromMediaId(song.mediaId, null)
        controller.awaitPlaying(song.description.title.toString())

        val playlist = browser.children("playlists").first()
        val first = browser.children(playlist.mediaId!!).first().description.title.toString()
        controller.transportControls.playFromSearch(playlist.description.title.toString(), null)
        controller.awaitPlaying(first)
        controller.transportControls.pause()

        instrumentation.runOnMainSync { browser.disconnect() }
    }

    @Test
    fun aCoverIsServedUnderAnAddressThatHidesItsSource() {
        val source = "android.resource://${context.packageName}/${R.drawable.ic_auto_albums}?u=someone&t=secret"
        val uri = OndaluneArtProvider.uriFor(context, source)!!
        assertEquals("content", uri.scheme)
        assertTrue("no part of the source in $uri", listOf("someone", "secret", "resource").none { it in uri.toString() })
        val cover = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        assertTrue("a decodable picture", cover != null && cover.width > 0)
    }

    private suspend fun MediaBrowser.children(parentId: String): List<MediaBrowser.MediaItem> {
        val loaded = CompletableDeferred<List<MediaBrowser.MediaItem>>()
        val callback = object : MediaBrowser.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaBrowser.MediaItem>) { loaded.complete(children) }
            override fun onError(parentId: String) { loaded.completeExceptionally(AssertionError("no folder $parentId")) }
        }
        instrumentation.runOnMainSync { subscribe(parentId, callback) }
        return withTimeout(WAIT_MS) { loaded.await() }.also { instrumentation.runOnMainSync { unsubscribe(parentId) } }
    }

    private suspend fun MediaController.awaitPlaying(title: String) = withTimeout(WAIT_MS) {
        while (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != title ||
            playbackState?.state !in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
        ) delay(200)
    }

    private companion object {
        const val WAIT_MS = 20_000L
    }
}
