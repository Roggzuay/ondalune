package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.autoplayPicks
import io.github.roggzuay.ondalune.data.model.hasGenre
import io.github.roggzuay.ondalune.data.model.libraryMixes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixesTest {
    private fun track(i: Int, genre: String, artist: String, plays: Int = 0, starred: Boolean = false) = SubsonicTrack(
        id = "t$i", serverId = 1, title = "Song $i", artist = artist, artistId = artist, album = "Album ${i / 4}",
        albumId = "al${i / 4}", durationSec = 200, bitRate = 320, suffix = "MP3", genre = genre, playCount = plays,
        starred = starred, userRating = 0
    )

    private val library = (0 until 40).map { track(it, "Jazz", "Ora ${it % 4}", plays = it % 3, starred = it % 4 == 0) } +
        (40 until 80).map { track(it, "Rock", "Night ${it % 4}", plays = 6) } +
        (80 until 84).map { track(it, "Polka", "Tiny", plays = 50) }

    @Test
    fun `genre mixes hold only their genre and skip genres too small to mix`() {
        val mixes = libraryMixes(library, emptyList(), emptyList(), seed = 1)
        val rock = mixes.first { it.key == "genre:rock" }
        assertTrue(rock.tracks.all { it.hasGenre("Rock") })
        assertEquals("Rock mix", rock.title)
        assertFalse(mixes.any { it.key == "genre:polka" })
        // Rock is played most, so it leads.
        assertEquals("genre:rock", mixes.first().key)
    }

    @Test
    fun `favourites mix is only starred songs, and forgotten favourites skip the last plays`() {
        val recent = library.filter { it.playCount >= 2 }.take(20).map { it.id }
        val mixes = libraryMixes(library, recent, emptyList(), seed = 2)
        assertTrue(mixes.first { it.key == "favourites" }.tracks.all { it.starred })
        val forgotten = mixes.first { it.key == "forgotten" }.tracks
        assertTrue(forgotten.none { it.id in recent } && forgotten.all { it.playCount >= 2 })
    }

    @Test
    fun `same day gives the same mixes`() {
        val a = libraryMixes(library, emptyList(), emptyList(), seed = 7).map { m -> m.tracks.map { it.id } }
        val b = libraryMixes(library, emptyList(), emptyList(), seed = 7).map { m -> m.tracks.map { it.id } }
        assertEquals(a, b)
    }

    @Test
    fun `autoplay follows the seeds, leaves out the queue and spaces artists`() {
        val seeds = library.filter { it.hasGenre("Rock") }.take(3)
        val queue = seeds.mapTo(HashSet()) { it.id }
        val picks = autoplayPicks(seeds, library, recentIds = emptyList(), exclude = queue, count = 20, random = Random(3))
        assertEquals(20, picks.size)
        assertTrue(picks.none { it.id in queue })
        assertTrue(picks.all { it.hasGenre("Rock") })
        picks.zipWithNext().forEach { (a, b) -> assertFalse("${a.artist} twice", a.artist == b.artist) }
    }

    @Test
    fun `autoplay pushes just-played songs back`() {
        val seeds = library.filter { it.hasGenre("Rock") }.take(2)
        val justPlayed = library.filter { it.hasGenre("Rock") }.drop(2).take(10).map { it.id }
        val picks = autoplayPicks(seeds, library, justPlayed, seeds.mapTo(HashSet()) { it.id }, count = 10, random = Random(4))
        assertTrue("recent songs crowded the picks", picks.count { it.id in justPlayed } <= 2)
    }
}

class MoodMixesTest {
    private fun track(i: Int) = SubsonicTrack(
        id = "m$i", serverId = 1, title = "Song $i", artist = "Artist ${i % 5}", artistId = "a${i % 5}", album = "Album ${i / 3}",
        albumId = "al${i / 3}", durationSec = 200, bitRate = 320, suffix = "MP3", genre = "Pop", userRating = 0
    )

    @Test
    fun `mood mixes appear only with analysis and follow tempo and energy`() {
        val library = (0 until 40).map(::track)
        assertFalse(libraryMixes(library, emptyList(), emptyList(), seed = 1).any { it.key.startsWith("mood:") })
        val features = library.associate { t ->
            val i = t.id.removePrefix("m").toInt()
            t.id to if (i < 20) io.github.roggzuay.ondalune.data.model.SongFeatures(132f, 0.8f, 0.5f, 0.7f)
            else io.github.roggzuay.ondalune.data.model.SongFeatures(84f, 0.3f, 0.3f, 0.2f)
        }
        val mixes = libraryMixes(library, emptyList(), emptyList(), seed = 1, features = features)
        assertTrue(mixes.first { it.key == "mood:upbeat" }.tracks.all { features.getValue(it.id).bpm > 120 })
        assertTrue(mixes.first { it.key == "mood:calm" }.tracks.all { features.getValue(it.id).bpm < 100 })
    }
}
