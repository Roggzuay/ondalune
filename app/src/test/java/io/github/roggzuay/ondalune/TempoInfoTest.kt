package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TempoInfo
import io.github.roggzuay.ondalune.data.model.tempoOf
import org.junit.Assert.assertEquals
import org.junit.Test

class TempoInfoTest {
    private val song = SubsonicTrack(
        id = "s", serverId = 1, title = "T", artist = "A", artistId = "a", album = "B", albumId = "b", durationSec = 200, bitRate = 0, suffix = "MP3"
    )

    @Test
    fun `a song nobody measured shows no tempo`() {
        assertEquals(0, song.bpm)
        assertEquals(TempoInfo.Unknown, tempoOf(song, null, analysing = false, queued = false))
    }

    @Test
    fun `the song's own tag wins over an estimate`() {
        assertEquals(TempoInfo.Known(124), tempoOf(song.copy(bpm = 124), SongFeatures(90f, 0.5f, 0.5f, 0.9f), false, false))
    }

    @Test
    fun `a steady analysed beat is shown, rounded`() {
        assertEquals(TempoInfo.Known(128), tempoOf(song, SongFeatures(127.6f, 0.5f, 0.5f, 0.6f), false, false))
    }

    @Test
    fun `an estimate from a song without a steady beat is not shown`() {
        assertEquals(TempoInfo.Unknown, tempoOf(song, SongFeatures(97f, 0.2f, 0.4f, 0.05f), false, false))
        assertEquals(TempoInfo.Unknown, tempoOf(song, SongFeatures(400f, 0.8f, 0.4f, 0.9f), false, false))
    }

    @Test
    fun `waiting and analysing are shown while nothing is known yet`() {
        assertEquals(TempoInfo.Analysing, tempoOf(song, null, analysing = true, queued = false))
        assertEquals(TempoInfo.Queued, tempoOf(song, null, analysing = false, queued = true))
        assertEquals(TempoInfo.Known(100), tempoOf(song.copy(bpm = 100), null, analysing = true, queued = false))
    }
}
