package io.github.roggzuay.ondalune

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The library sync writes tracks in 500-row chunks and prunes deleted songs while Home observes
 * the whole track list. With bios and lyrics that list spans several CursorWindows, and each
 * window refill re-runs the query, so a read outside a transaction can see the table shrink
 * halfway through and crash with "Couldn't read row N, col 0 from CursorWindow" (1.0.5).
 */
@RunWith(AndroidJUnit4::class)
class TrackListReadRaceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OndaluneDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(NAME)
        db = Room.databaseBuilder(context, OndaluneDatabase::class.java, NAME).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun fullTrackListReadsAreConsistentWhileSyncWrites() = runBlocking {
        val dao = db.dao()
        val bio = "An artist biography long enough to fill the cursor windows. ".repeat(40)
        val all = (0 until TRACKS).map { i ->
            SubsonicTrack(
                id = "t$i", serverId = 1, title = "Song $i", artist = "Artist", artistId = "a",
                album = "Album ${i / 10}", albumId = "al%04d".format(i / 10), durationSec = 200,
                bitRate = 900, suffix = "FLAC", trackNumber = i % 10 + 1, artistBio = bio
            )
        }
        dao.insertTracks(all)

        val random = Random(7)
        val writer = launch(Dispatchers.IO) {
            repeat(40) {
                val pruned = all.shuffled(random).take(CHUNK)
                dao.deleteTracks(1, pruned.map { it.id })
                dao.insertTracks(pruned)
            }
        }
        val sizes = async(Dispatchers.IO) {
            val seen = mutableSetOf<Int>()
            while (writer.isActive) {
                seen += dao.getTracksForServerOnce(1).size
                seen += dao.observeTracksForServer(1).first().size
            }
            seen
        }
        writer.join()

        // Every read is one snapshot: the full library, or the library minus one pruned chunk.
        val seen = sizes.await()
        assertTrue("sizes seen: $seen", seen.all { it == TRACKS || it == TRACKS - CHUNK })
    }

    private companion object {
        const val NAME = "track-read-race.db"
        const val TRACKS = 3000
        const val CHUNK = 500
    }
}
