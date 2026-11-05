package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.source.FolderFile
import io.github.roggzuay.ondalune.data.source.LocalMusicSource
import io.github.roggzuay.ondalune.data.source.SongTags
import io.github.roggzuay.ondalune.data.source.SupportedAudio
import io.github.roggzuay.ondalune.data.source.folderLocation
import io.github.roggzuay.ondalune.data.source.folderTrackFrom
import io.github.roggzuay.ondalune.data.source.isInside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFoldersTest {
    private val file = FolderFile(
        documentId = "primary:Music/Air/Moon Safari/03 All I Need.flac", name = "03 All I Need.flac",
        size = 30_000_000, modifiedMs = 1, coverUri = null, lyrics = null
    )
    private val tags = SongTags(
        title = "All I Need", artist = "Air", albumArtist = null, album = "Moon Safari", durationMs = 268_000,
        track = "3/10", disc = "1/1", year = "1998-01-16", genre = "Downtempo", bitrate = 0, sampleRate = 44100, bitsPerSample = 16
    )

    @Test
    fun `only music formats are read, and hidden files never`() {
        listOf("a.flac", "b.MP3", "c.m4a", "d.ogg", "e.opus", "f.wav", "g.aac", "h.oga").forEach { assertTrue(it, SupportedAudio.isMusicFile(it)) }
        listOf("ring.amr", "voice.3gp", "clip.mp4", "list.m3u", "notes.txt", "cover.jpg", ".hidden.mp3", "song.flac.part").forEach {
            assertFalse(it, SupportedAudio.isMusicFile(it))
        }
    }

    @Test
    fun `sound folders inside a picked folder are skipped`() {
        listOf("Notifications", "Ringtones", "Alarms", "Recordings", "Voice Recorder", "Android", ".cache", "Call recordings").forEach {
            assertTrue(it, SupportedAudio.skipsFolder(it))
        }
        listOf("Albums", "Air", "Live 2019").forEach { assertFalse(it, SupportedAudio.skipsFolder(it)) }
    }

    @Test
    fun `a song file becomes a library track with its real details`() {
        val t = folderTrackFrom(file, tags, sourceId = 9, coverArtUrl = "content://cover")
        assertEquals("local:primary:Music/Air/Moon Safari/03 All I Need.flac", t.id)
        assertEquals(9L, t.serverId)
        assertEquals("All I Need", t.title)
        assertEquals("Air", t.artist)
        assertEquals("Moon Safari", t.album)
        assertEquals(268, t.durationSec)
        assertEquals(3, t.trackNumber)
        assertEquals(1998, t.year)
        assertEquals("FLAC", t.suffix)
        assertEquals(16, t.bitDepth)
        assertEquals(44100, t.samplingRate)
        assertEquals(895, t.bitRate) // from size and length, as the file has no bitrate tag
        assertEquals("/Music/Air/Moon Safari", t.folderPath)
        assertEquals(0, t.bpm)
    }

    @Test
    fun `untagged files fall back to their name without inventing details`() {
        val t = folderTrackFrom(file, SongTags(null, null, null, null, 0, null, null, null, null, 0, 0, 0), 9, "")
        assertEquals("03 All I Need", t.title)
        assertEquals("Unknown artist", t.artist)
        assertEquals(0, t.year)
        assertEquals(0, t.bitDepth)
        assertEquals(0, t.samplingRate)
        assertEquals(0, t.bitRate)
    }

    @Test
    fun `songs of one album share an album id whoever sings each`() {
        val a = folderTrackFrom(file, tags.copy(albumArtist = "Various", artist = "One"), 9, "")
        val b = folderTrackFrom(file.copy(documentId = "primary:Music/x.flac"), tags.copy(albumArtist = "Various", artist = "Two"), 9, "")
        assertEquals(a.albumId, b.albumId)
    }

    @Test
    fun `a folder inside a picked one is already included`() {
        assertTrue(isInside("primary:Music/Air", "primary:Music"))
        assertTrue(isInside("primary:Music", "primary:"))
        assertFalse(isInside("primary:Music", "primary:Music"))
        assertFalse(isInside("primary:Musicals", "primary:Music"))
        assertFalse(isInside("1234-ABCD:Music/Air", "primary:Music"))
    }

    @Test
    fun `folders say where they are`() {
        assertEquals("Internal storage › Music › Albums", folderLocation("primary:Music/Albums"))
        assertEquals("Storage 1234-ABCD › Music", folderLocation("1234-ABCD:Music"))
    }

    @Test
    fun `lyrics files are synced when timed and plain otherwise`() {
        val parse = { raw: String -> if (raw.startsWith("[")) listOf(LyricLine(1000, "Hello")) else emptyList() }
        assertEquals(LyricsAvailability.SYNCED, LocalMusicSource.lyricsFrom("[00:01.00]Hello", parse).availability)
        assertEquals(LyricsAvailability.PLAIN_UNSYNCED, LocalMusicSource.lyricsFrom("Hello", parse).availability)
        assertEquals(LyricsAvailability.UNAVAILABLE, LocalMusicSource.lyricsFrom("", parse).availability)
    }
}
