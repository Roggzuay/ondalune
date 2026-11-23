package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.motion.MotionArtworkCache
import io.github.roggzuay.ondalune.data.motion.MotionArtworkMode
import io.github.roggzuay.ondalune.data.motion.MotionArtworkProvider
import io.github.roggzuay.ondalune.data.motion.MotionArtworkRepository
import io.github.roggzuay.ondalune.data.motion.MotionArtworkSettings
import io.github.roggzuay.ondalune.data.motion.MotionArtworkWorker
import io.github.roggzuay.ondalune.data.motion.MotionCandidate
import io.github.roggzuay.ondalune.data.motion.MotionFormat
import io.github.roggzuay.ondalune.data.motion.MotionIdentity
import io.github.roggzuay.ondalune.data.motion.MotionStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionArtworkRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val video = MotionArtworkValidatorTest.mp4() + ByteArray(4000) { it.toByte() }
    private val tracks = DemoSubsonicCatalog.seedTracks(5L)
    private val albumA = tracks.first()
    private val sameAlbum = tracks.first { it.albumId == albumA.albumId && it.id != albumA.id }
    private val otherAlbum = tracks.first { it.albumId != albumA.albumId }
    private val server = SubsonicServerConfig(id = 5, name = "Test", baseUrl = "https://example.invalid", username = "u", passwordOrToken = "p")

    /**
     * A stand-in network source: offers [bytes] as an MP4 for [albums] (all when empty), counts its
     * lookups, and can fail or break off downloads on request.
     */
    private open inner class FakeSource(
        override val id: String = "fake",
        var bytes: ByteArray = video,
        var albums: Set<String> = emptySet(),
        var failFind: Boolean = false,
        /** Downloads break off after this many bytes, this many times. */
        var breakAfter: Int = -1,
        var breaks: Int = 0,
        val resumes: Boolean = true,
        override val usesNetwork: Boolean = true,
        override val showsInPlace: Boolean = false
    ) : MotionArtworkProvider {
        override val name = "Fake source"
        override val attribution: String? = "Artwork courtesy of Fake"
        val finds = AtomicInteger()
        val opens = mutableListOf<Long>()
        var gate: CompletableDeferred<Unit>? = null

        override fun handles(track: SubsonicTrack, server: SubsonicServerConfig?) = true

        override suspend fun find(identity: MotionIdentity, track: SubsonicTrack, server: SubsonicServerConfig?): List<MotionCandidate> {
            finds.incrementAndGet()
            gate?.await()
            if (failFind) throw IOException("Provider down")
            if (albums.isNotEmpty() && identity.albumId !in albums) return emptyList()
            return listOf(MotionCandidate(id, "fake://${identity.albumId}", MotionFormat.MP4, identity, bytes.size.toLong()))
        }

        override suspend fun open(candidate: MotionCandidate, fromByte: Long): MotionStream {
            opens += fromByte
            val start = if (resumes) fromByte.toInt() else 0
            val rest = bytes.copyOfRange(start, bytes.size)
            val stream: InputStream = if (breaks > 0) {
                breaks--
                BreakingStream(rest, breakAfter)
            } else ByteArrayInputStream(rest)
            return MotionStream(stream, start.toLong(), bytes.size.toLong())
        }
    }

    private class BreakingStream(private val data: ByteArray, private val after: Int) : InputStream() {
        private var pos = 0
        override fun read(): Int {
            if (pos >= after) throw IOException("Connection reset")
            return if (pos < data.size) data[pos++].toInt() and 0xFF else -1
        }
    }

    private var settings = MotionArtworkSettings()
    private var online = true
    private var unmetered = true
    private val cacheDir by lazy { tmp.newFolder("motion") }

    private fun repository(vararg sources: MotionArtworkProvider, playable: Boolean = true, keepFree: Long = 0L) = MotionArtworkRepository(
        cache = MotionArtworkCache(cacheDir),
        providers = sources.toList(),
        settings = { settings },
        networkAllowed = { wifiOnly -> online && (!wifiOnly || unmetered) },
        playable = { _, _ -> playable },
        io = Dispatchers.IO,
        retryDelayMs = 1,
        keepFreeBytes = keepFree
    )

    @Test
    fun `artwork is found, saved and shared by every song of the album`() = runBlocking {
        val source = FakeSource()
        val repo = repository(source)
        val asset = repo.resolve(albumA, server)
        assertNotNull(asset)
        assertTrue(File(asset!!.location).readBytes().contentEquals(video))
        assertEquals("Fake source", asset.providerName)
        assertEquals("Artwork courtesy of Fake", asset.attribution)
        assertEquals(asset, repo.resolve(sameAlbum, server))
        assertEquals("one lookup for the whole album", 1, source.finds.get())
        assertEquals(asset, repo.found.value[MotionIdentity.of(sameAlbum).releaseKey])
    }

    @Test
    fun `songs of an album asked for at once share one lookup`() = runBlocking {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val repo = repository(source)
        val waiting = (1..5).map { async(Dispatchers.IO) { repo.resolve(if (it % 2 == 0) albumA else sameAlbum, server) } }
        Thread.sleep(100)
        source.gate!!.complete(Unit)
        val results = waiting.awaitAll()
        assertEquals(1, source.finds.get())
        assertTrue(results.all { it != null && it == results.first() })
    }

    @Test
    fun `the cache answers after a restart without asking the source again`() = runBlocking {
        repository(FakeSource()).resolve(albumA, server)
        val again = FakeSource()
        assertNotNull(repository(again).resolve(sameAlbum, server))
        assertEquals(0, again.finds.get())
    }

    @Test
    fun `an album with no artwork is remembered and not looked up again for a while`() = runBlocking {
        val source = FakeSource(albums = setOf("nothing-here"))
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        assertNull(repo.resolve(sameAlbum, server))
        assertEquals(1, source.finds.get())
    }

    @Test
    fun `a source that is down is asked again next time, not written off`() = runBlocking {
        val source = FakeSource(failFind = true)
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        source.failFind = false
        assertNotNull(repo.resolve(albumA, server))
        assertEquals(2, source.finds.get())
    }

    @Test
    fun `wifi only keeps network sources quiet on mobile data, and nothing is written off`() = runBlocking {
        unmetered = false
        val source = FakeSource()
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        assertEquals(0, source.finds.get())
        settings = settings.copy(wifiOnly = false)
        assertNotNull(repo.resolve(albumA, server))
    }

    @Test
    fun `with automatic lookup off only music folders are looked at`() = runBlocking {
        settings = settings.copy(autoRetrieve = false)
        val network = FakeSource()
        val folder = FakeSource(id = "folder", usesNetwork = false, showsInPlace = true)
        val asset = repository(network, folder).resolve(albumA, server)
        assertEquals(0, network.finds.get())
        assertEquals("folder", asset?.providerId)
        assertEquals("folder artwork is shown where it is", "fake://${albumA.albumId}", asset?.location)
    }

    @Test
    fun `nothing is looked up while motion artwork is off`() = runBlocking {
        settings = settings.copy(mode = MotionArtworkMode.DISABLED)
        val source = FakeSource()
        assertNull(repository(source).resolve(albumA, server))
        assertEquals(0, source.finds.get())
    }

    @Test
    fun `an interrupted download resumes where it stopped`() = runBlocking {
        val source = FakeSource(breakAfter = 1500, breaks = 1)
        val asset = repository(source).resolve(albumA, server)
        assertNotNull(asset)
        assertEquals(listOf(0L, 1500L), source.opens)
        assertTrue(File(asset!!.location).readBytes().contentEquals(video))
    }

    @Test
    fun `a source that can't resume starts the download over`() = runBlocking {
        val source = FakeSource(breakAfter = 1500, breaks = 1, resumes = false)
        val asset = repository(source).resolve(albumA, server)
        assertTrue(File(asset!!.location).readBytes().contentEquals(video))
    }

    @Test
    fun `a download that keeps failing gives up and is tried again later`() = runBlocking {
        val source = FakeSource(breakAfter = 100, breaks = 3)
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        assertEquals(3, source.opens.size)
        assertNotNull("not written off as none", repo.resolve(albumA, server))
    }

    @Test
    fun `a file that isn't what was offered is thrown away`() = runBlocking {
        val source = FakeSource(bytes = ByteArray(5000) { 7 })
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        assertTrue(cacheDir.listFiles().orEmpty().none { it.name.endsWith(".mp4") || it.name.endsWith(".part") })
    }

    @Test
    fun `artwork the phone can't play is not kept`() = runBlocking {
        assertNull(repository(FakeSource(), playable = false).resolve(albumA, server))
    }

    @Test
    fun `another album never gets this album's artwork`() = runBlocking {
        val source = FakeSource(albums = setOf(albumA.albumId))
        val repo = repository(source)
        assertNotNull(repo.resolve(albumA, server))
        assertNull(repo.resolve(otherAlbum, server))
        assertNull(repo.found.value[MotionIdentity.of(otherAlbum).releaseKey])
    }

    @Test
    fun `the size limit evicts saved artwork and the player stops showing it`() = runBlocking {
        val repo = repository(FakeSource())
        val asset = repo.resolve(albumA, server)!!
        settings = settings.copy(cacheLimitMb = 0)
        repo.enforceLimit()
        assertFalse(File(asset.location).exists())
        assertNull(repo.found.value[asset.releaseKey])
        assertEquals(0L, repo.cacheBytes.value)
    }

    @Test
    fun `artwork that fails to play is dropped and written off for a while`() = runBlocking {
        val source = FakeSource()
        val repo = repository(source)
        val asset = repo.resolve(albumA, server)!!
        repo.reportUnplayable(asset)
        Thread.sleep(100)
        assertNull(repo.found.value[asset.releaseKey])
        assertNull(repo.resolve(albumA, server))
        assertEquals(1, source.finds.get())
    }

    @Test
    fun `nothing is downloaded while the phone is short of space, and the album is tried again later`() = runBlocking {
        val source = FakeSource()
        assertNull(repository(source, keepFree = Long.MAX_VALUE / 2).resolve(albumA, server))
        assertTrue(source.opens.isEmpty())
        assertNotNull("not written off as none", repository(source).resolve(albumA, server))
    }

    @Test
    fun `the library run fills up to the limit and no further`() = runBlocking {
        val albums = MotionArtworkWorker.albumsOf(tracks).map { it to server }
        assertTrue(albums.size > 3)
        settings = settings.copy(cacheLimitMb = 0)
        val source = FakeSource(bytes = video + ByteArray(400 * 1024))
        val repo = repository(source)
        MotionArtworkWorker.runLibrary(repo, albums) { false }
        // A zero limit is full before anything: nothing is fetched, nothing pushed out.
        assertEquals(0, source.finds.get())

        settings = settings.copy(cacheLimitMb = 1)
        MotionArtworkWorker.runLibrary(repo, albums) { false }
        // Two 400 KB covers fit; the third would pass 1 MB, so it is skipped and the run ends.
        assertEquals(3, source.finds.get())
        assertEquals(2, repo.found.value.size)
        assertEquals(2, source.opens.size)
        assertTrue(repo.cacheBytes.value <= 1024 * 1024)
        assertNull("no run is shown once it ends", repo.libraryRun.value)
    }

    @Test
    fun `the library run stops when asked to`() = runBlocking {
        val source = FakeSource()
        val albums = MotionArtworkWorker.albumsOf(tracks).map { it to server }
        var left = 2
        MotionArtworkWorker.runLibrary(repository(source), albums) { left-- <= 0 }
        assertEquals(2, source.finds.get())
    }

    @Test
    fun `an album whose cover changed is found after looking again`() = runBlocking {
        val source = FakeSource(albums = setOf("nothing-here"))
        val repo = repository(source)
        assertNull(repo.resolve(albumA, server))
        source.albums = setOf(albumA.albumId)
        assertNull("still remembered as having none", repo.resolve(albumA, server))
        repo.lookAgain()
        assertNotNull(repo.resolve(albumA, server))
    }

    @Test
    fun `clearing empties the cache and what the player shows`() = runBlocking {
        val repo = repository(FakeSource())
        repo.resolve(albumA, server)
        repo.clear()
        assertTrue(repo.found.value.isEmpty())
        assertEquals(0L, repo.cacheBytes.value)
    }
}
