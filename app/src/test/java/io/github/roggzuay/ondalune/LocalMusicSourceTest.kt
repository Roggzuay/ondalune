package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.isOnDevice
import io.github.roggzuay.ondalune.data.source.LocalAudioRow
import io.github.roggzuay.ondalune.data.source.LocalMusicSource
import io.github.roggzuay.ondalune.data.source.localTrackFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalMusicSourceTest {
    private fun row(
        title: String? = "Blue in Green",
        artist: String? = "Miles Davis",
        album: String? = "Kind of Blue",
        track: Int = 3,
        bitrate: Int = 0,
        path: String? = "/storage/emulated/0/Music/Miles Davis/Kind of Blue/03 Blue in Green.flac"
    ) = LocalAudioRow(
        mediaId = 42, title = title, artist = artist, artistId = 7, album = album, albumId = 9,
        durationMs = 337_000, track = track, year = 1959, path = path, genre = "Jazz",
        bitrate = bitrate, sizeBytes = 40_000_000
    )

    @Test
    fun `a MediaStore row becomes a library song that is marked as on the phone`() {
        val t = localTrackFrom(row(), serverId = 5, sdkInt = 34)
        assertEquals("local:42", t.id)
        assertTrue(t.isOnDevice)
        assertEquals(5L, t.serverId)
        assertEquals("Blue in Green", t.title)
        assertEquals("Miles Davis", t.artist)
        assertEquals("local-album:9", t.albumId)
        assertEquals(337, t.durationSec)
        assertEquals("FLAC", t.suffix)
        assertEquals("/Music/Miles Davis/Kind of Blue", t.folderPath)
        assertEquals("content://media/external/audio/albums/9", t.coverArtUrl)
        // No made-up details: no rating, tempo or sample format until something real says so.
        assertEquals(0, t.userRating)
        assertEquals(0, t.bpm)
        assertEquals(0, t.samplingRate)
    }

    @Test
    fun `missing tags fall back to the file name and plain words`() {
        val t = localTrackFrom(row(title = null, artist = "<unknown>", album = " "), serverId = 5)
        assertEquals("03 Blue in Green", t.title)
        assertEquals("Unknown artist", t.artist)
        assertEquals("Unknown album", t.album)
    }

    @Test
    fun `disc numbers are dropped from track numbers and the bitrate is estimated when unknown`() {
        val t = localTrackFrom(row(track = 2003), serverId = 5)
        assertEquals(3, t.trackNumber)
        assertEquals(40_000_000L * 8 / 337_000, t.bitRate.toLong())
        assertEquals(320, localTrackFrom(row(bitrate = 320_000), serverId = 5).bitRate)
    }

    @Test
    fun `older Android reads covers through albumart`() {
        assertEquals("content://media/external/audio/albumart/9", localTrackFrom(row(), serverId = 5, sdkInt = 28).coverArtUrl)
    }

    @Test
    fun `lyrics come from an lrc file beside the song`() {
        val dir = createTempDirectory().toFile()
        val song = File(dir, "song.mp3").apply { writeText("x") }
        val parse = { raw: String -> raw.lines().filter { it.startsWith("[") }.map { LyricLine(0, it.substringAfter(']')) } }
        assertEquals(LyricsAvailability.UNAVAILABLE, LocalMusicSource.sidecarLyrics(song.path, parse).availability)
        File(dir, "song.lrc").writeText("[00:01.00]Hello\n[00:02.00]World")
        val synced = LocalMusicSource.sidecarLyrics(song.path, parse)
        assertEquals(LyricsAvailability.SYNCED, synced.availability)
        assertEquals(listOf("Hello", "World"), synced.syncedLines.map { it.text })
        File(dir, "song.lrc").writeText("Just words")
        assertEquals(LyricsAvailability.PLAIN_UNSYNCED, LocalMusicSource.sidecarLyrics(song.path, parse).availability)
        dir.deleteRecursively()
    }
}
