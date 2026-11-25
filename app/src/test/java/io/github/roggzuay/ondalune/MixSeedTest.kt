package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.MixSeed
import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.mixFromSeed
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A mix made to order. The dials have to lean the result without turning it into a filter: asking
 * for "driving" should bring driving songs forward, not throw the quiet ones out of the library.
 */
class MixSeedTest {

    private fun track(
        id: String,
        artist: String = "Artist $id",
        genre: String = "Electronic",
        year: Int = 2020,
        plays: Int = 0,
        starred: Boolean = false,
        album: String = "al-$id"
    ) = SubsonicTrack(
        id = id,
        serverId = 1L,
        title = "Song $id",
        artist = artist,
        artistId = "ar-$artist",
        album = "Album $album",
        albumId = album,
        durationSec = 200,
        bitRate = 320,
        suffix = "FLAC",
        year = year,
        genre = genre,
        playCount = plays,
        starred = starred
    )

    private fun features(vararg pairs: Pair<String, Float>): Map<String, SongFeatures> =
        pairs.associate { (id, energy) ->
            "1_$id" to SongFeatures(bpm = 120f, energy = energy, brightness = 0.5f, danceability = 0.5f)
        }

    /** The same draw every time, so a lean can be measured rather than guessed at. */
    private fun fixed() = Random(42)

    // ---- Nothing asked for ----

    @Test
    fun `every dial in the middle is just Your Mix`() {
        assertTrue(MixSeed().isPlain)
        assertFalse(MixSeed(energy = 0.9f).isPlain)
        assertFalse(MixSeed(year = 1975).isPlain)
        assertFalse(MixSeed(genre = "Jazz").isPlain)
    }

    @Test
    fun `an empty library gives an empty mix rather than falling over`() {
        assertTrue(mixFromSeed(emptyList(), MixSeed(energy = 1f), emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `a mix never returns more than it was asked for`() {
        val library = (1..80).map { track("s$it", artist = "Artist ${it % 12}") }
        val mix = mixFromSeed(library, MixSeed(familiarity = 0.9f, size = 20), emptyList(), emptyMap(), fixed())
        assertEquals(20, mix.size)
    }

    @Test
    fun `a mix has no song in it twice`() {
        val library = (1..40).map { track("s$it", artist = "Artist ${it % 7}") }
        val mix = mixFromSeed(library, MixSeed(familiarity = 0.8f, size = 25), emptyList(), emptyMap(), fixed())
        assertEquals(mix.size, mix.distinctBy { it.id }.size)
    }

    // ---- The dials lean, rather than filter ----

    @Test
    fun `asking for the ones you always play brings them forward`() {
        val library = (1..20).map { track("loved$it", plays = 60, starred = true) } +
            (1..20).map { track("never$it", plays = 0) }
        val mix = mixFromSeed(library, MixSeed(familiarity = 1f, size = 20), emptyList(), emptyMap(), fixed())
        val loved = mix.count { it.id.startsWith("loved") }
        assertTrue("only $loved of 20 were songs you play", loved >= 13)
    }

    @Test
    fun `asking for new to you brings forward the ones you never play`() {
        val library = (1..20).map { track("loved$it", plays = 60, starred = true) } +
            (1..20).map { track("never$it", plays = 0) }
        val mix = mixFromSeed(library, MixSeed(familiarity = 0f, size = 20), emptyList(), emptyMap(), fixed())
        val fresh = mix.count { it.id.startsWith("never") }
        assertTrue("only $fresh of 20 were songs you never play", fresh >= 13)
    }

    @Test
    fun `a lean is not a filter, so the other side is still reachable`() {
        val library = (1..40).map { track("loved$it", plays = 60) } + listOf(track("never1", plays = 0))
        // Over many draws, the quiet one still comes up: it was weighted down, not removed.
        val seen = (1..60).any { run ->
            mixFromSeed(library, MixSeed(familiarity = 1f, size = 30), emptyList(), emptyMap(), Random(run.toLong()))
                .any { it.id == "never1" }
        }
        assertTrue("a song weighted down should still be reachable", seen)
    }

    @Test
    fun `asking for driving brings forward the songs measured as loud`() {
        val library = (1..20).map { track("loud$it") } + (1..20).map { track("quiet$it") }
        val measured = features(
            *((1..20).map { "loud$it" to 0.92f } + (1..20).map { "quiet$it" to 0.08f }).toTypedArray()
        )
        val mix = mixFromSeed(library, MixSeed(energy = 1f, size = 20), emptyList(), measured, fixed())
        val loud = mix.count { it.id.startsWith("loud") }
        assertTrue("only $loud of 20 were loud", loud >= 13)
    }

    @Test
    fun `with nothing measured the energy dial does not guess`() {
        val library = (1..30).map { track("s$it") }
        val driving = mixFromSeed(library, MixSeed(energy = 1f, size = 15), emptyList(), emptyMap(), Random(7))
        val still = mixFromSeed(library, MixSeed(energy = 0f, size = 15), emptyList(), emptyMap(), Random(7))
        assertEquals(driving.map { it.id }, still.map { it.id })
    }

    // ---- Era and the plain filters ----

    @Test
    fun `an era brings its decade forward without shutting the rest out`() {
        val library = (1..20).map { track("old$it", year = 1975) } + (1..20).map { track("new$it", year = 2022) }
        val mix = mixFromSeed(library, MixSeed(year = 1975, size = 20), emptyList(), emptyMap(), fixed())
        val old = mix.count { it.id.startsWith("old") }
        assertTrue("only $old of 20 were from the seventies", old >= 13)
    }

    @Test
    fun `a genre is a plain filter, because that is what picking one means`() {
        val library = (1..20).map { track("jazz$it", genre = "Jazz") } +
            (1..20).map { track("rock$it", genre = "Rock") }
        val mix = mixFromSeed(library, MixSeed(genre = "Jazz", size = 20), emptyList(), emptyMap(), fixed())
        assertTrue(mix.isNotEmpty())
        assertTrue(mix.all { it.genre == "Jazz" })
    }

    @Test
    fun `an artist is a plain filter too`() {
        val library = (1..10).map { track("a$it", artist = "Pixies") } +
            (1..10).map { track("b$it", artist = "Slowdive") }
        val mix = mixFromSeed(library, MixSeed(artist = "Pixies", size = 20), emptyList(), emptyMap(), fixed())
        assertTrue(mix.isNotEmpty())
        assertTrue(mix.all { it.artist == "Pixies" })
    }

    @Test
    fun `asking for something the library does not have gives nothing, not a wrong mix`() {
        val library = (1..10).map { track("s$it", genre = "Jazz") }
        assertTrue(mixFromSeed(library, MixSeed(genre = "Polka"), emptyList(), emptyMap(), fixed()).isEmpty())
    }

    @Test
    fun `songs just played go to the back rather than out of a small library`() {
        val library = (1..8).map { track("s$it") }
        val recent = listOf("s1", "s2", "s3")
        val mix = mixFromSeed(library, MixSeed(familiarity = 0.9f, size = 8), recent, emptyMap(), fixed())
        assertEquals(8, mix.size)
    }

    // ---- What the mix is called ----

    @Test
    fun `the mix says what it is`() {
        assertEquals("A mix for now", MixSeed().title)
        assertEquals("Driving", MixSeed(energy = 1f).title)
        assertEquals("Still, new to you", MixSeed(energy = 0f, familiarity = 0f).title)
        assertTrue(MixSeed(genre = "Jazz").title.contains("Jazz"))
        assertTrue(MixSeed(year = 1975).title.contains("1975"))
    }
}
