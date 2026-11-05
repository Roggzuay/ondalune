package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.yourMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YourMixTest {
    /** [artists] artists with [perArtist] songs each, over two albums per artist. */
    private fun library(artists: Int, perArtist: Int, genre: (Int) -> String = { "Rock" }) = (0 until artists).flatMap { a ->
        (0 until perArtist).map { n ->
            SubsonicTrack(
                id = "a$a-s$n", serverId = 1, title = "Song $n", artist = "Artist $a", artistId = "ar$a",
                album = "Album ${n % 2}", albumId = "al$a-${n % 2}", durationSec = 200, bitRate = 0, suffix = "FLAC",
                genre = genre(a)
            )
        }
    }

    /** The library a listener who only plays and loves Artist 0 has. */
    private fun skewed() = library(20, 20).map { t ->
        if (t.artist == "Artist 0") t.copy(starred = true, playCount = 40) else t
    }

    @Test
    fun `one much-loved artist doesn't take over the mix`() {
        repeat(20) { seed ->
            val mix = yourMix(skewed(), recentIds = emptyList(), size = 60, random = Random(seed))
            assertEquals(60, mix.size)
            val top = mix.groupingBy { it.artist }.eachCount().values.max()
            assertTrue("no artist above a tenth of the mix (got $top)", top <= 6)
            assertTrue("many artists (${mix.distinctBy { it.artist }.size})", mix.distinctBy { it.artist }.size >= 12)
        }
    }

    @Test
    fun `favourites come up more often, but not every time`() {
        val lib = skewed()
        val runs = (0 until 200).map { yourMix(lib, emptyList(), size = 60, random = Random(it)) }
        val loved = runs.map { mix -> mix.count { it.starred } }.average()
        // Artist 0 is 1/20 of the library: an unbiased draw would give 3 of 60.
        assertTrue("favourites lead ($loved per mix)", loved > 4.0)
        val lovedSongs = lib.filter { it.starred }.map { it.id }.toSet()
        val alwaysIn = lovedSongs.filter { id -> runs.all { mix -> mix.any { it.id == id } } }
        assertTrue("no favourite is guaranteed a place", alwaysIn.isEmpty())
    }

    @Test
    fun `every refresh gives a different mix`() {
        val lib = library(15, 20)
        val mixes = (0 until 12).map { yourMix(lib, emptyList(), size = 60, random = Random(it)).map { t -> t.id } }
        assertEquals(12, mixes.toSet().size)
        val a = mixes[0].toSet()
        val b = mixes[1].toSet()
        assertTrue("two mixes share less than half their songs", a.intersect(b).size < 30)
    }

    @Test
    fun `ratings have no say`() {
        val lib = library(10, 10)
        val rated = lib.mapIndexed { i, t -> t.copy(userRating = if (i % 3 == 0) 5 else 1) }
        assertEquals(
            yourMix(lib, emptyList(), random = Random(7)).map { it.id },
            yourMix(rated, emptyList(), random = Random(7)).map { it.id }
        )
    }

    @Test
    fun `songs just played are held back`() {
        val lib = library(10, 20)
        val recent = lib.shuffled(Random(1)).take(50).map { it.id }
        val hits = (0 until 50).sumOf { seed ->
            yourMix(lib, recent, size = 60, random = Random(seed)).count { it.id in recent.take(20) }
        }
        // 20 of 200 songs: about 6 per mix if they weren't held back.
        assertTrue("the last plays rarely come back ($hits in 50 mixes)", hits < 50)
    }

    @Test
    fun `a small library still plays everything it has`() {
        val tiny = library(1, 5)
        val mix = yourMix(tiny, emptyList(), size = 60, random = Random(3))
        assertEquals(tiny.map { it.id }.toSet(), mix.map { it.id }.toSet())
        val twoArtists = library(2, 30)
        assertEquals(60, yourMix(twoArtists, emptyList(), size = 60, random = Random(4)).size)
    }

    @Test
    fun `the same artist doesn't play twice running when it can be avoided`() {
        val mix = yourMix(library(12, 15), emptyList(), size = 60, random = Random(9))
        assertTrue(mix.zipWithNext().none { (x, y) -> x.artist == y.artist })
    }
}
