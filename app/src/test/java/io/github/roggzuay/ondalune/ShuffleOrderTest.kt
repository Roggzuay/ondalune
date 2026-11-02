package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.smartShuffle
import io.github.roggzuay.ondalune.data.model.spreadOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleOrderTest {
    private fun track(id: String, artist: String, album: String = "$artist-album", starred: Boolean = false, plays: Int = 0) =
        SubsonicTrack(
            id = id, serverId = 1, title = id, artist = artist, artistId = artist, album = album, albumId = album,
            durationSec = 200, bitRate = 320, suffix = "MP3", starred = starred, playCount = plays, userRating = 0
        )

    private val library = (0 until 200).map { i -> track("t$i", "artist${i % 20}", "album${i % 40}") }

    @Test
    fun keepsEverySongOnce() {
        val order = smartShuffle(library, SmartShuffleProfile.BALANCED, emptyList(), random = Random(1))
        assertEquals(library.map { it.id }.sorted(), order.map { it.id }.sorted())
    }

    @Test
    fun noArtistWithinTwoSongsWhenThereIsChoice() {
        val order = smartShuffle(library, SmartShuffleProfile.BALANCED, emptyList(), random = Random(2))
        order.windowed(3).forEach { (a, b, c) ->
            assertTrue(a.artist != b.artist && a.artist != c.artist && b.artist != c.artist)
        }
    }

    @Test
    fun singleArtistKeepsItsOrder() {
        val album = (1..8).map { track("s$it", "solo", "one") }
        assertEquals(album, spreadOut(album))
    }

    @Test
    fun favouritesDoNotTakeOverTheStart() {
        // 20 starred, much-played songs in 200: across shuffles they should lead only somewhat
        // more often than their 10% share, not fill the opening songs every time.
        val favourites = library.take(20).map { it.copy(starred = true, playCount = 80, userRating = 5) }.toSet()
        val pool = favourites + library.drop(20)
        val favouriteIds = favourites.map { it.id }.toSet()
        val shares = (0 until 200).map { seed ->
            smartShuffle(pool.toList(), SmartShuffleProfile.BALANCED, emptyList(), random = Random(seed))
                .take(10).count { it.id in favouriteIds } / 10.0
        }
        val average = shares.average()
        assertTrue("favourites lead ${average * 100}% of openings", average in 0.12..0.45)
    }

    @Test
    fun recentlyPlayedSongsMoveBack() {
        val recent = library.take(40).map { it.id }
        val starts = (0 until 200).map { seed ->
            smartShuffle(library, SmartShuffleProfile.BALANCED, recent, random = Random(seed))
                .take(20).count { it.id in recent } / 20.0
        }
        // A fifth of the library, so without the nudge they'd fill about 20% of the opening.
        assertTrue("recent songs open ${starts.average() * 100}% of shuffles", starts.average() < 0.13)
    }
}
