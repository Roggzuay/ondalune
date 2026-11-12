package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.playback.PlaybackCoordinator
import io.github.roggzuay.ondalune.playback.PlaybackEngine
import io.github.roggzuay.ondalune.playback.PlaybackHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {
    private val server = SubsonicServerConfig(id = 1, name = "Test", baseUrl = "http://test", username = "u", passwordOrToken = "p")

    private fun track(i: Int, genre: String = "Jazz", artist: String = "Artist $i", album: Int = i) = SubsonicTrack(
        id = "t$i", serverId = 1, title = "Song $i", artist = artist, artistId = artist, album = "Album $album",
        albumId = "al$album", durationSec = 200, bitRate = 320, suffix = "MP3", genre = genre
    )

    private class FakeEngine : PlaybackEngine {
        override val isPlaying = MutableStateFlow(false)
        override val positionMs = MutableStateFlow(0L)
        val played = mutableListOf<String>()
        /** Songs that never start playing here (as a Dolby song on a phone without a decoder). */
        val unplayable = mutableSetOf<String>()
        /** Called for an unplayable song as the real engine would, once it has tried to open it. */
        var onUnplayable: ((SubsonicTrack) -> Unit)? = null
        var expectedNext: String? = null
        var paused = false
        var seekedTo: Long? = null
        override fun play(track: SubsonicTrack, server: SubsonicServerConfig, eq: EqualizerPreset) {
            played += track.id
            isPlaying.value = track.id !in unplayable
            if (track.id in unplayable) onUnplayable?.invoke(track)
        }
        override fun togglePlayPause() { isPlaying.value = !isPlaying.value }
        override fun pause() { paused = true; isPlaying.value = false }
        override fun seekTo(positionMs: Long) { seekedTo = positionMs }
        override fun setOutputVolume(volume: Float) {}
        override fun expectNextTrack(trackId: String?) { expectedNext = trackId }
    }

    private inner class FakeHost(
        override var library: List<SubsonicTrack>,
        override var allTracks: List<SubsonicTrack> = library,
        override var settings: PlayerCustomizationSettings = PlayerCustomizationSettings(autoplaySimilar = false)
    ) : PlaybackHost {
        override val equalizer = EqualizerPreset.FLAT_STUDIO
        override val recentTrackIds = emptyList<String>()
        override val songFeatures = emptyMap<String, SongFeatures>()
        val started = mutableListOf<String>()
        var savedQueue: List<String> = emptyList()
        var lastPlayed: String? = null
        override fun serverFor(track: SubsonicTrack) = server
        override fun onTrackStarted(track: SubsonicTrack, server: SubsonicServerConfig) { started += track.id }
        val banners = mutableListOf<String>()
        override fun showBanner(message: String) { banners += message }
        override fun saveLastPlayed(trackId: String) { lastPlayed = trackId }
        override fun saveQueue(trackIds: List<String>) { savedQueue = trackIds }
    }

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val engine = FakeEngine()
    private val songs = (1..5).map { track(it) }
    private val host = FakeHost(songs)
    private val playback = PlaybackCoordinator(engine, host, scope)

    private fun queueIds() = playback.playbackQueue.value.map { it.id }

    @Test
    fun `playing a song queues its context and tells the engine what comes next`() {
        playback.playTrack(songs[1], songs)
        assertEquals("t2", playback.currentTrack.value?.id)
        assertEquals(listOf("t2"), engine.played)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), queueIds())
        assertEquals("t3", engine.expectedNext)
        assertEquals("t2", host.lastPlayed)
        assertEquals(listOf("t2"), host.started)
    }

    @Test
    fun `skip next walks the queue and wraps round without autoplay`() {
        playback.playTrack(songs[3], songs)
        playback.skipNext()
        assertEquals("t5", playback.currentTrack.value?.id)
        playback.skipNext()
        assertEquals("t1", playback.currentTrack.value?.id)
    }

    @Test
    fun `skip previous restarts a song that has played a while, otherwise goes back`() {
        playback.playTrack(songs[2], songs)
        engine.positionMs.value = 10_000L
        playback.skipPrevious()
        assertEquals(0L, engine.seekedTo)
        assertEquals("t3", playback.currentTrack.value?.id)
        engine.positionMs.value = 1_000L
        playback.skipPrevious()
        assertEquals("t2", playback.currentTrack.value?.id)
    }

    @Test
    fun `play next puts songs right after the current one, in the order picked`() {
        playback.playTrack(songs[0], songs.take(3))
        val extra = listOf(track(10), track(11))
        playback.playTracksNext(extra)
        assertEquals(listOf("t1", "t10", "t11", "t2", "t3"), queueIds())
        assertEquals(queueIds(), host.savedQueue)
        playback.addTrackToQueue(track(12))
        assertEquals("t12", queueIds().last())
    }

    @Test
    fun `remove and move edit the queue, but the only song left stays`() {
        playback.playTrack(songs[0], songs.take(3))
        playback.moveQueueTrack(2, 1)
        assertEquals(listOf("t1", "t3", "t2"), queueIds())
        playback.removeTrackFromQueue("t3")
        assertEquals(listOf("t1", "t2"), queueIds())
        playback.playTrack(songs[0], listOf(songs[0]))
        playback.removeTrackFromQueue("t1")
        assertEquals(listOf("t1"), queueIds())
    }

    @Test
    fun `autoplay adds similar songs when the last queued song plays`() {
        val library = (1..30).map { if (it <= 20) track(it, "Jazz", "J${it % 5}", album = it / 3) else track(it, "Metal", "M${it % 3}", album = it / 3) }
        host.allTracks = library
        host.settings = PlayerCustomizationSettings(autoplaySimilar = true)
        playback.playTrack(library[0], listOf(library[0], library[1]))
        assertEquals(2, queueIds().size)
        playback.skipNext()
        val added = queueIds().drop(2)
        assertTrue("autoplay added songs", added.isNotEmpty())
        assertTrue("no song repeats", queueIds().toSet().size == queueIds().size)
        assertTrue("jazz seeds bring jazz first", added.take(10).all { id -> library.first { it.id == id }.genre == "Jazz" })
    }

    @Test
    fun `autoplay stays off when switched off`() {
        playback.playTrack(songs[0], listOf(songs[0]))
        assertEquals(listOf("t1"), queueIds())
    }

    @Test
    fun `repeat one replays the song when it ends`() {
        playback.playTrack(songs[0], songs)
        playback.toggleRepeatOne()
        assertEquals("t1", engine.expectedNext)
        playback.onTrackEnded()
        assertEquals(listOf("t1", "t1"), engine.played)
    }

    @Test
    fun `sleep timer at the end of the song pauses instead of moving on`() {
        playback.playTrack(songs[0], songs)
        playback.startSleepTimerEndOfTrack()
        assertNull(engine.expectedNext)
        assertNull(playback.nextForEngine())
        playback.onTrackEnded()
        assertTrue(engine.paused)
        assertEquals("t1", playback.currentTrack.value?.id)
        assertFalse(playback.sleepTimerState.value.isActive)
    }

    @Test
    fun `sleep timer countdown pauses when it reaches zero`() {
        playback.playTrack(songs[0], songs)
        playback.startSleepTimerDuration(1, finishLastTrackOnExpiry = false, gentleFadeOut = false)
        scope.advanceTimeBy(61_000L)
        scope.runCurrent()
        assertTrue(engine.paused)
        assertFalse(playback.sleepTimerState.value.isActive)
    }

    @Test
    fun `gapless hand-over makes the next song current without restarting the engine`() {
        playback.playTrack(songs[0], songs)
        val (next, _) = playback.nextForEngine()!!
        playback.onEngineAdvanced(next)
        assertEquals("t2", playback.currentTrack.value?.id)
        assertEquals(listOf("t1"), engine.played)
    }

    @Test
    fun `the last played song comes back on launch without playing`() {
        assertNull(playback.onLibraryChanged(songs, resumeTrackId = "missing"))
        assertEquals("t3", playback.onLibraryChanged(songs, resumeTrackId = "t3")?.id)
        assertEquals("t3", playback.currentTrack.value?.id)
        assertTrue(engine.played.isEmpty())
        // A later sync refreshes the current song's details.
        val renamed = songs.map { if (it.id == "t3") it.copy(title = "New name") else it }
        playback.onLibraryChanged(renamed, resumeTrackId = null)
        assertEquals("New name", playback.currentTrack.value?.title)
    }

    @Test
    fun `play pause with nothing current starts the library`() {
        playback.togglePlayPause()
        assertEquals(listOf("t1"), engine.played)
        playback.togglePlayPause()
        assertFalse(engine.isPlaying.value)
    }

    @Test
    fun `mixes play in their own order with shuffle off`() {
        playback.setShuffleMode(io.github.roggzuay.ondalune.data.model.ShuffleMode.STANDARD)
        val mix = songs.reversed()
        playback.playInOrder(mix)
        assertEquals(mix.map { it.id }, queueIds())
        assertFalse(playback.shuffleEnabled.value)
    }

    @Test
    fun `a song this phone can't play is reported and skipped`() {
        engine.unplayable += "t2"
        playback.playTrack(songs[1], songs)
        playback.onTrackUnplayable(songs[1], "This phone can't decode Dolby Digital Plus")
        assertEquals("t3", playback.currentTrack.value?.id)
        assertEquals(listOf("t2", "t3"), engine.played)
        assertTrue(host.banners.last().startsWith("This phone can't decode Dolby Digital Plus"))
        // A report about a song that is no longer current changes nothing.
        playback.onTrackUnplayable(songs[1], "late")
        assertEquals("t3", playback.currentTrack.value?.id)
    }

    @Test
    fun `a queue where nothing can play stops after one round`() {
        engine.unplayable += songs.map { it.id }
        engine.onUnplayable = { playback.onTrackUnplayable(it, "This phone can't decode Dolby Digital") }
        playback.playTrack(songs[0], songs)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t1"), engine.played)
        assertTrue(host.banners.contains("This phone can't decode Dolby Digital"))
    }
}
