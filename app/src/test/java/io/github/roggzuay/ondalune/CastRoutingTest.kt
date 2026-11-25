package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.cast.CastGateway
import io.github.roggzuay.ondalune.cast.CastState
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.playback.PlaybackEngine
import io.github.roggzuay.ondalune.playback.RoutedPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the music comes out. A Cast device cannot be had in a unit test, so what is tested here
 * is the decision: which engine gets the song, what happens to the one that doesn't, and what the
 * app says when a song cannot go where it is being sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CastRoutingTest {

    private class FakeLocal : PlaybackEngine {
        val played = mutableListOf<String>()
        var paused = 0
        var toggled = 0
        var seekedTo: Long? = null
        var volume: Float? = null
        var expected: String? = null
        override val isPlaying = MutableStateFlow(false)
        override val positionMs = MutableStateFlow(0L)
        override fun play(track: SubsonicTrack, server: SubsonicServerConfig, eq: EqualizerPreset) {
            played += track.id
        }
        override fun togglePlayPause() { toggled++ }
        override fun pause() { paused++ }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun setOutputVolume(volume: Float) { this.volume = volume }
        override fun expectNextTrack(trackId: String?) { expected = trackId }
    }

    private class FakeCast : CastGateway {
        override val supported = true
        override var enabled = true
        val mutable = MutableStateFlow(CastState())
        override val state: StateFlow<CastState> = mutable
        val loaded = mutableListOf<Pair<String, String>>()
        var plays = 0
        var pauses = 0
        var seekedTo: Long? = null
        var position = 0L
        override fun attach(activity: android.app.Activity) = Unit
        override fun detach() = Unit
        override fun load(
            track: SubsonicTrack,
            streamUrl: String,
            coverUrl: String,
            positionMs: Long,
            autoplay: Boolean
        ) { loaded += track.id to streamUrl }
        override fun play() { plays++ }
        override fun pause() { pauses++ }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun stop() = Unit
        override fun positionMs() = position
        override fun durationMs() = 0L
        override fun onTrackEnded(callback: () -> Unit) = Unit
        override fun onFailure(callback: (String) -> Unit) = Unit

        fun connect(name: String = "Living room", playing: Boolean = true) {
            mutable.value = CastState(connected = true, deviceName = name, playing = playing)
        }

        fun disconnect() { mutable.value = CastState() }
    }

    private val server = SubsonicServerConfig(
        id = 1L,
        name = "Home",
        baseUrl = "https://music.example",
        username = "me",
        passwordOrToken = "x"
    )

    private val phone = server.copy(id = 2L, name = "This phone", baseUrl = "", sourceType = SourceType.LOCAL.name)

    private fun track(id: String, serverId: Long = 1L) = SubsonicTrack(
        id = id,
        serverId = serverId,
        title = "Song $id",
        artist = "Artist",
        artistId = "ar1",
        album = "Album",
        albumId = "al1",
        durationSec = 200,
        bitRate = 320,
        suffix = "FLAC"
    )

    /**
     * The engine watches the Cast state and polls the device for ever while it is connected, so
     * it gets a scope of its own that each test cancels. Left on the test's scope, that poll would
     * never let the test finish.
     */
    private class Rig(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler, url: String? = "https://music.example/stream?id=s1") {
        val local = FakeLocal()
        val cast = FakeCast()
        val banners = mutableListOf<String>()
        private val scope = CoroutineScope(StandardTestDispatcher(scheduler))
        val engine = RoutedPlaybackEngine(
            local = local,
            cast = cast,
            streamUrlFor = { _, _ -> url },
            coverUrlFor = { it.coverArtUrl },
            scope = scope,
            showBanner = { banners += it }
        )
        fun close() = scope.cancel()
    }

    /** Each test gets a rig and gives it back, so the device poll never outlives the test. */
    private fun routing(
        url: String? = "https://music.example/stream?id=s1",
        body: suspend TestScope.(Rig) -> Unit
    ) = runTest(StandardTestDispatcher()) {
        val rig = Rig(testScheduler, url)
        try {
            body(rig)
        } finally {
            rig.close()
        }
    }

    // ---- Which engine gets the song ----

    @Test
    fun `with nothing connected the song plays here`() = routing { rig ->
        runCurrent()
        rig.engine.play(track("s1"), server, EqualizerPreset.FLAT_STUDIO)
        runCurrent()
        assertEquals(listOf("s1"), rig.local.played)
        assertTrue(rig.cast.loaded.isEmpty())
    }

    @Test
    fun `with a device connected the song goes there instead`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.play(track("s1"), server, EqualizerPreset.FLAT_STUDIO)
        runCurrent()
        assertEquals(listOf("s1" to "https://music.example/stream?id=s1"), rig.cast.loaded)
        assertTrue(rig.local.played.isEmpty())
    }

    @Test
    fun `a device joining takes over what was playing, and this phone goes quiet`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.onCastConnected(track("s1"), server, positionMs = 42_000L, wasPlaying = true)
        runCurrent()
        assertEquals(1, rig.local.paused)
        assertEquals(listOf("s1"), rig.cast.loaded.map { it.first })
        assertTrue(rig.banners.last(), rig.banners.last().contains("Living room"))
    }

    @Test
    fun `a device leaving hands the song back at the point it reached`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.play(track("s1"), server, EqualizerPreset.FLAT_STUDIO)
        runCurrent()
        // The device reports where it has got to; the poll picks it up on its next tick.
        rig.cast.position = 61_000L
        advanceTimeBy(600)
        runCurrent()

        var resumedAt: Long? = null
        var resumedId: String? = null
        rig.cast.disconnect()
        runCurrent()
        rig.engine.onCastDisconnected { track, _, at ->
            resumedId = track.id
            resumedAt = at
        }
        assertEquals("s1", resumedId)
        assertEquals(61_000L, resumedAt)
    }

    // ---- What cannot be cast ----

    @Test
    fun `music on this phone is not sent to a device, and says why`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.play(track("local:file-1", serverId = 2L), phone, EqualizerPreset.FLAT_STUDIO)
        runCurrent()
        assertTrue(rig.cast.loaded.isEmpty())
        assertTrue(rig.banners.last(), rig.banners.last().contains("can't be cast"))
    }

    @Test
    fun `a song with no address the device can reach is reported, not dropped silently`() = routing(url = null) { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.play(track("s1"), server, EqualizerPreset.FLAT_STUDIO)
        runCurrent()
        assertTrue(rig.cast.loaded.isEmpty())
        assertTrue(rig.banners.last(), rig.banners.last().contains("Couldn't give"))
    }

    // ---- The transport follows the music ----

    @Test
    fun `pause and seek go wherever the music is`() = routing { rig ->
        runCurrent()
        rig.engine.pause()
        rig.engine.seekTo(5_000L)
        assertEquals(1, rig.local.paused)
        assertEquals(5_000L, rig.local.seekedTo)

        rig.cast.connect()
        runCurrent()
        rig.engine.pause()
        rig.engine.seekTo(9_000L)
        assertEquals(1, rig.cast.pauses)
        assertEquals(9_000L, rig.cast.seekedTo)
        assertEquals(1, rig.local.paused)
    }

    @Test
    fun `play and pause toggle on the device, not here`() = routing { rig ->
        rig.cast.connect(playing = true)
        runCurrent()
        rig.engine.togglePlayPause()
        assertEquals(1, rig.cast.pauses)
        rig.cast.connect(playing = false)
        runCurrent()
        rig.engine.togglePlayPause()
        assertEquals(1, rig.cast.plays)
        assertEquals(0, rig.local.toggled)
    }

    @Test
    fun `the phone's volume slider does not reach for the device's volume`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.setOutputVolume(0.4f)
        assertEquals(null, rig.local.volume)
    }

    @Test
    fun `gapless is not prepared for a device that plays one song at a time`() = routing { rig ->
        runCurrent()
        rig.engine.expectNextTrack("s2")
        assertEquals("s2", rig.local.expected)

        rig.cast.connect()
        runCurrent()
        rig.engine.expectNextTrack("s3")
        assertEquals("s2", rig.local.expected)
    }

    // ---- What the rest of the app sees ----

    @Test
    fun `position and playing follow whichever engine is in charge`() = routing { rig ->
        runCurrent()
        rig.local.positionMs.value = 1_000L
        rig.local.isPlaying.value = true
        runCurrent()
        assertEquals(1_000L, rig.engine.positionMs.value)
        assertTrue(rig.engine.isPlaying.value)

        rig.cast.position = 77_000L
        rig.cast.connect(playing = false)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(77_000L, rig.engine.positionMs.value)
        assertFalse(rig.engine.isPlaying.value)

        // The local engine still ticking must not drag the shown position back.
        rig.local.positionMs.value = 2_000L
        runCurrent()
        assertEquals(77_000L, rig.engine.positionMs.value)
    }

    @Test
    fun `casting is false until something is connected`() = routing { rig ->
        runCurrent()
        assertFalse(rig.engine.casting)
        rig.cast.connect()
        runCurrent()
        assertTrue(rig.engine.casting)
    }

    @Test
    fun `a device joining with nothing playing just says where the music will go`() = routing { rig ->
        rig.cast.connect()
        runCurrent()
        rig.engine.onCastConnected(null, null, 0L, false)
        runCurrent()
        assertTrue(rig.cast.loaded.isEmpty())
        assertTrue(rig.banners.last(), rig.banners.last().contains("Living room"))
    }
}
