package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.motion.MotionArtworkCache
import io.github.roggzuay.ondalune.data.motion.MotionAsset
import io.github.roggzuay.ondalune.data.motion.MotionFormat
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
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionArtworkCacheTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000L
    private val dir by lazy { tmp.newFolder("motion") }
    private val cache by lazy { MotionArtworkCache(dir) { now } }

    private fun asset(key: String, location: String = "") =
        MotionAsset(key, "server-cover", "Your server's album cover", null, location, MotionFormat.MP4, "Album", "Artist", 0.98f, 0)

    private fun download(key: String, size: Int): File = cache.partFile(key).apply { writeBytes(ByteArray(size)) }

    @Test
    fun `a miss, then a hit that every song of the album shares`() {
        assertNull(cache.get("3:al-1"))
        val saved = cache.putFound(asset("3:al-1"), download("3:al-1", 100))
        assertTrue(File(saved.location).isFile)
        assertEquals(100L, saved.bytes)
        assertFalse("the half-download is moved, not left behind", cache.partFile("3:al-1").exists())
        assertEquals(saved, cache.get("3:al-1")?.asset())
    }

    @Test
    fun `what was found survives a restart`() {
        cache.putFound(asset("3:al-1"), download("3:al-1", 10))
        cache.putNone("3:al-2")
        val reopened = MotionArtworkCache(dir) { now }
        assertEquals(MotionArtworkCache.Status.FOUND, reopened.get("3:al-1")?.status)
        assertEquals(MotionArtworkCache.Status.NONE, reopened.get("3:al-2")?.status)
    }

    @Test
    fun `nothing found is remembered, then forgotten after its time`() {
        cache.putNone("3:al-9")
        now += TimeUnit.HOURS.toMillis(23)
        assertEquals(MotionArtworkCache.Status.NONE, cache.get("3:al-9")?.status)
        now += TimeUnit.HOURS.toMillis(2)
        assertNull(cache.get("3:al-9"))
    }

    @Test
    fun `looking again forgets what had nothing, and keeps what was found`() {
        cache.putFound(asset("3:al-1"), download("3:al-1", 10))
        cache.putNone("3:al-2")
        cache.forgetMisses()
        assertNull(cache.get("3:al-2"))
        assertEquals(MotionArtworkCache.Status.FOUND, cache.get("3:al-1")?.status)
        assertNull("forgotten for good, not just in memory", MotionArtworkCache(dir) { now }.get("3:al-2"))
    }

    @Test
    fun `found artwork goes stale after its time but is kept meanwhile`() {
        cache.putFound(asset("3:al-1"), download("3:al-1", 10))
        assertFalse(cache.isStale(cache.get("3:al-1")!!))
        now += TimeUnit.DAYS.toMillis(91)
        val entry = cache.get("3:al-1")
        assertNotNull(entry)
        assertTrue(cache.isStale(entry!!))
    }

    @Test
    fun `a copy deleted behind the cache's back is a miss`() {
        val saved = cache.putFound(asset("3:al-1"), download("3:al-1", 10))
        File(saved.location).delete()
        assertNull(cache.get("3:al-1"))
    }

    @Test
    fun `over the limit, the artwork shown longest ago goes first`() {
        cache.putFound(asset("a"), download("a", 400)); now += 1
        cache.putFound(asset("b"), download("b", 400)); now += 1
        cache.putFound(asset("c"), download("c", 400)); now += 1
        cache.touch("a")
        assertEquals(listOf("b"), cache.evictTo(900))
        assertNull(cache.get("b"))
        assertNotNull(cache.get("a"))
        assertEquals(800L, cache.sizeBytes())
    }

    @Test
    fun `artwork in a music folder is pointed to, never copied or evicted`() {
        val folderFile = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FA%2Fcover.mp4"
        cache.putFound(asset("local", folderFile).copy(bytes = 5_000), copy = null)
        assertEquals(0L, cache.sizeBytes())
        assertTrue(cache.evictTo(0).isEmpty())
        assertEquals(folderFile, cache.get("local")?.location)
    }

    @Test
    fun `clearing removes copies, half downloads and what was looked up`() {
        cache.putFound(asset("a"), download("a", 10))
        download("b", 20)
        cache.putNone("c")
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("c"))
        assertEquals(0L, cache.sizeBytes())
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a broken index starts over instead of failing`() {
        dir.resolve("index.json").writeText("{not json")
        assertNull(cache.get("anything"))
        cache.putNone("x")
        assertEquals(MotionArtworkCache.Status.NONE, MotionArtworkCache(dir) { now }.get("x")?.status)
    }

    @Test
    fun `stray files with no entry are cleaned up`() {
        cache.putFound(asset("a"), download("a", 10))
        val stray = dir.resolve("old.webm").apply { writeBytes(ByteArray(5)) }
        cache.removeOrphans()
        assertFalse(stray.exists())
        assertNotNull(cache.get("a"))
    }
}
