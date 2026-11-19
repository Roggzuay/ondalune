package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.ui.viewmodel.LyricsController
import io.github.roggzuay.ondalune.ui.viewmodel.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The lyrics part on its own, with the server faked and online lookups off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricsControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = app.getSharedPreferences("lyrics_controller_test", Context.MODE_PRIVATE)
    private val server = SubsonicServerConfig(
        id = 7, name = "Home", baseUrl = "https://music.example/", username = "me", passwordOrToken = "x",
        sourceType = SourceType.SUBSONIC.name
    )
    private val song = SubsonicTrack(
        id = "s1", serverId = 7, title = "Song", artist = "Artist", artistId = "a1",
        album = "Album", albumId = "al1", durationSec = 200, bitRate = 0, suffix = "FLAC",
        starred = false, starredAtMs = 0, coverArtUrl = ""
    )
    private val synced = TrackLyricsResult(
        availability = LyricsAvailability.SYNCED,
        syncedLines = listOf(LyricLine(0, "First"), LyricLine(2_000, "Second"))
    )

    private fun waitFor(done: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun showsTheServersLyricsForANewSong() {
        val settings = SettingsStore(prefs).apply { setFindLyricsOnline(false) }
        var asked = 0
        val lyrics = LyricsController(app, settings, CoroutineScope(Dispatchers.Main), { server }) { _, _ ->
            asked++
            synced
        }
        lyrics.loadLyricsForTrack(song)
        waitFor { lyrics.currentLyrics.value.isNotEmpty() }

        assertEquals(1, asked)
        assertEquals(listOf("First", "Second"), lyrics.currentLyrics.value.map { it.text })
        assertTrue(lyrics.lyricsSource.value.detail.startsWith("Your server"))
    }
}
