package io.github.roggzuay.ondalune

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.RemotePlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.serverPlaylistKey
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.network.parseRemotePlaylist
import io.github.roggzuay.ondalune.data.repository.OndaluneRepository
import io.github.roggzuay.ondalune.data.source.MusicSource
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistSyncTest {
    private lateinit var db: OndaluneDatabase
    private lateinit var repo: OndaluneRepository
    private val fake = FakeServer()
    private lateinit var server: SubsonicServerConfig

    private fun song(id: String) = SubsonicTrack(
        id = id, serverId = 0, title = "Song $id", artist = "Artist", artistId = "ar", album = "Album",
        albumId = "al", durationSec = 200, bitRate = 0, suffix = "FLAC"
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OndaluneDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = OndaluneRepository(db.dao(), OpenSubsonicClient(), sourceFor = { fake })
        val id = db.dao().insertServer(
            SubsonicServerConfig(name = "Navidrome", baseUrl = "https://music.example", username = "me", passwordOrToken = "x")
        )
        server = db.dao().getAllServersOnce().first { it.id == id }
        db.dao().insertTracks(listOf("a", "b", "c").map { song(it).copy(serverId = id) })
        db.dao().insertPlaylist(SubsonicPlaylist(id = "pl_local", serverId = id, name = "Road trip", comment = "", trackIdsCsv = "a"))
    }

    @After
    fun tearDown() = db.close()

    private fun playlists() = runBlocking { db.dao().getPlaylistsForServerOnce(server.id) }
    private fun serverRow(remoteId: String) = playlists().first { it.id == serverPlaylistKey(server.id, remoteId) }

    @Test
    fun `server playlists come in once each, with their songs, next to Ondalune's own`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", owner = "me", changed = "t1"), remote("p2", "Alex's", owner = "alex", changed = "t1"), remote("p3", "Empty", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a"), song("b")), "p2" to listOf(song("c")), "p3" to emptyList())

        assertTrue(repo.syncServerPlaylists(server))
        assertTrue(repo.syncServerPlaylists(server))

        assertEquals(4, playlists().size)
        assertEquals(listOf("a", "b"), serverRow("p1").trackIds)
        assertFalse(serverRow("p1").readOnly)
        assertTrue("someone else's playlist is read-only", serverRow("p2").readOnly)
        assertEquals(emptyList<String>(), serverRow("p3").trackIds)
        assertEquals("Road trip", playlists().first { it.id == "pl_local" }.name)
        assertEquals("unchanged playlists aren't fetched again", 3, fake.songFetches)
    }

    @Test
    fun `changes on the server show after a refresh, and deleted playlists go`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", changed = "t1"), remote("p2", "Old", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a")), "p2" to listOf(song("b")))
        repo.syncServerPlaylists(server)

        fake.lists = listOf(remote("p1", "Mine, renamed", changed = "t2"))
        fake.songs = mapOf("p1" to listOf(song("a"), song("c")))
        repo.syncServerPlaylists(server)

        assertEquals("Mine, renamed", serverRow("p1").name)
        assertEquals(listOf("a", "c"), serverRow("p1").trackIds)
        assertTrue(playlists().none { it.remoteId == "p2" })
        assertTrue("Ondalune's playlist stays", playlists().any { it.id == "pl_local" })
    }

    @Test
    fun `an unreachable server keeps the saved playlists`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a")))
        repo.syncServerPlaylists(server)

        fake.lists = null
        assertFalse(repo.syncServerPlaylists(server))
        assertEquals(listOf("a"), serverRow("p1").trackIds)
    }

    @Test
    fun `a playlist whose songs can't be read keeps its songs and is read again next time`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a")))
        repo.syncServerPlaylists(server)

        fake.lists = listOf(remote("p1", "Mine", changed = "t2"))
        fake.songs = emptyMap() // getPlaylist fails
        repo.syncServerPlaylists(server)
        assertEquals(listOf("a"), serverRow("p1").trackIds)
        assertEquals("", serverRow("p1").remoteChanged)

        fake.songs = mapOf("p1" to listOf(song("b")))
        repo.syncServerPlaylists(server)
        assertEquals(listOf("b"), serverRow("p1").trackIds)
    }

    @Test
    fun `songs the library doesn't have yet are added so the playlist plays`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a"), song("new")))
        repo.syncServerPlaylists(server)
        assertTrue(db.dao().getTrackIdsForServer(server.id).contains("new"))
    }

    @Test
    fun `edits go to the server only for your own playlists`() = runBlocking {
        fake.lists = listOf(remote("p1", "Mine", owner = "me", changed = "t1"), remote("p2", "Alex's", owner = "alex", changed = "t1"))
        fake.songs = mapOf("p1" to listOf(song("a")), "p2" to listOf(song("a")))
        repo.syncServerPlaylists(server)

        assertTrue(repo.addTracksToPlaylist(serverRow("p1"), listOf("b")))
        assertEquals(listOf("p1" to listOf("b")), fake.added)
        assertEquals(listOf("a", "b"), serverRow("p1").trackIds)

        assertFalse(repo.addTracksToPlaylist(serverRow("p2"), listOf("b")))
        assertFalse(repo.deletePlaylist(serverRow("p2")))
        assertEquals(1, fake.added.size)

        fake.refuse = true
        assertFalse("a refusal changes nothing here either", repo.addTracksToPlaylist(serverRow("p1"), listOf("c")))
        assertEquals(listOf("a", "b"), serverRow("p1").trackIds)
    }

    @Test
    fun `getPlaylists entries are read field by field`() {
        val p = parseRemotePlaylist(
            JSONObject("""{"id":"800","name":"Chill","comment":"evenings","owner":"admin","public":true,"songCount":12,"coverArt":"pl-800","changed":"2026-11-05T10:00:00Z"}""")
        )!!
        assertEquals(RemotePlaylist("800", "Chill", "evenings", "admin", true, 12, "pl-800", "2026-11-05T10:00:00Z"), p)
        assertNull(parseRemotePlaylist(JSONObject("""{"name":"no id"}""")))
    }

    private fun remote(id: String, name: String, owner: String = "me", changed: String) =
        RemotePlaylist(id, name, "", owner, false, 0, "", changed)

    /** Stands in for a Navidrome server's playlist endpoints. */
    private class FakeServer : MusicSource {
        var lists: List<RemotePlaylist>? = emptyList()
        var songs: Map<String, List<SubsonicTrack>> = emptyMap()
        var songFetches = 0
        val added = mutableListOf<Pair<String, List<String>>>()
        var refuse = false

        override suspend fun playlists(server: SubsonicServerConfig) = lists
        override suspend fun playlistSongs(server: SubsonicServerConfig, playlistId: String): List<SubsonicTrack>? {
            songFetches++
            return songs[playlistId]?.map { it.copy(serverId = server.id) }
        }
        override suspend fun addToPlaylist(server: SubsonicServerConfig, playlistId: String, trackIds: List<String>): Boolean {
            if (refuse) return false
            added += playlistId to trackIds
            return true
        }
        override suspend fun deletePlaylist(server: SubsonicServerConfig, playlistId: String) = !refuse
        override suspend fun ping(server: SubsonicServerConfig) = PingDiagnosticResult(success = true, statusMessage = "ok")
        override suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit) = null
        override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) = null
        override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) = TrackLyricsResult()
    }
}
