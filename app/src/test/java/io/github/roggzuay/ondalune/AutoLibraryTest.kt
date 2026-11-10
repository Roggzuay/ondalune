package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.auto.AutoLibrary
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoLibraryTest {
    private fun track(id: String, album: String, artist: String = "Artist", starredAtMs: Long = 0) = SubsonicTrack(
        id = id, serverId = 1, title = "Song $id", artist = artist, artistId = artist,
        album = album, albumId = "al-$album", durationSec = 200, bitRate = 0, suffix = "FLAC",
        starred = starredAtMs > 0, starredAtMs = starredAtMs, coverArtUrl = "cover-$album"
    )

    private fun album(name: String) = SubsonicAlbum("al-$name", 1, name, "Artist", "Artist", 2, 400, 2020, "Rock", false, "cover-$name")

    private val tracks = listOf(
        track("1", "Zebra", starredAtMs = 10), track("2", "Zebra"),
        track("3", "apple", artist = "Solo", starredAtMs = 30), track("4", "apple", starredAtMs = 20)
    )
    private val library = AutoLibrary(
        tracks = tracks,
        albums = listOf(album("Zebra"), album("apple")),
        playlists = listOf(
            SubsonicPlaylist("p|1", 1, "Road trip", "", "4,gone,1"),
            SubsonicPlaylist("p2", 1, "Empty", "", "")
        ),
        yourMix = listOf(tracks[1], tracks[2]),
        downloadedIds = setOf("2")
    )

    private fun ids(parent: String) = library.children(parent)?.map { it.id }

    @Test
    fun `root is the four tabs`() {
        assertEquals(listOf("home", "playlists", "albums", "downloads"), ids("root"))
        assertNull(ids("nowhere"))
    }

    @Test
    fun `home lists only the folders that have songs`() {
        assertEquals(listOf("mix", "favourites"), ids("home"))
        assertEquals("2 songs", library.children("home")!!.first().subtitle)
    }

    @Test
    fun `favourites come newest first`() {
        assertEquals(listOf("favourites|3", "favourites|4", "favourites|1"), ids("favourites"))
    }

    @Test
    fun `albums are sorted by name whatever the case`() {
        assertEquals(listOf("apple", "Zebra"), library.children("albums")!!.map { it.title })
    }

    @Test
    fun `a tapped song plays on from its own list`() {
        val album = ids("albums")!!.last()
        val queue = library.queueFor(ids(album)!!.last())!!
        assertEquals("2", queue.start.id)
        assertEquals(listOf("1", "2"), queue.tracks.map { it.id })
    }

    @Test
    fun `a playlist keeps its order, skips missing songs, and survives a bar in its id`() {
        val playlist = ids("playlists")!!.single()
        assertEquals(listOf("4", "1"), library.queueFor(playlist)!!.tracks.map { it.id })
        assertEquals("1", library.queueFor(ids(playlist)!!.last())!!.start.id)
    }

    @Test
    fun `downloads holds only saved songs`() {
        assertEquals(listOf("downloads|2"), ids("downloads"))
    }

    @Test
    fun `an unknown row plays nothing`() {
        assertNull(library.queueFor("mix|nope"))
        assertNull(library.queueFor("album/none"))
    }

    @Test
    fun `voice search finds a playlist, an album, an artist, then songs`() {
        assertEquals(listOf("4", "1"), library.search("road trip")!!.tracks.map { it.id })
        assertEquals(listOf("3", "4"), library.search("Apple")!!.tracks.map { it.id })
        assertEquals(listOf("3"), library.search("solo")!!.tracks.map { it.id })
        assertEquals("2", library.search("Song 2")!!.start.id)
        assertNull(library.search("  "))
    }
}
