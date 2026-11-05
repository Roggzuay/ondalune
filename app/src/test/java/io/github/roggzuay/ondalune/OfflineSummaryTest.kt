package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.offlineSummaryOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineSummaryTest {
    private fun song(id: String, album: String) = SubsonicTrack(
        id = id, serverId = 1, title = id, artist = "A", artistId = "a", album = album, albumId = album, durationSec = 100, bitRate = 0, suffix = "MP3"
    )
    private val library = listOf(song("a1", "A"), song("a2", "A"), song("b1", "B"), song("b2", "B"), song("b3", "B"), song("c1", "C"))

    @Test
    fun `whole and partly downloaded albums are told apart`() {
        val s = offlineSummaryOf(library, downloadedIds = setOf("a1", "a2", "b1"), playlists = emptyList())
        assertEquals(3, s.songs)
        assertEquals(1, s.wholeAlbums)
        assertEquals(mapOf("B" to (1 to 3)), s.partialAlbums)
        assertEquals("3 songs, 1 album, 1 partly downloaded album", s.description)
    }

    @Test
    fun `a playlist counts only when every song is offline`() {
        val lists = listOf(
            SubsonicPlaylist("p1", 1, "All here", "", "a1,a2"),
            SubsonicPlaylist("p2", 1, "Half", "", "a1,c1"),
            SubsonicPlaylist("p3", 1, "Empty", "", "")
        )
        assertEquals(1, offlineSummaryOf(library, setOf("a1", "a2"), lists).playlists)
    }

    @Test
    fun `nothing downloaded says so`() {
        // Only completed downloads are passed in: one still downloading isn't offline.
        assertNull(offlineSummaryOf(library, emptySet(), emptyList()).description)
    }
}
