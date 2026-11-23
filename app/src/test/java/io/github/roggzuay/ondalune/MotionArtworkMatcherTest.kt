package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.motion.MotionArtworkMatcher
import io.github.roggzuay.ondalune.data.motion.MotionCandidate
import io.github.roggzuay.ondalune.data.motion.MotionFormat
import io.github.roggzuay.ondalune.data.motion.MotionIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionArtworkMatcherTest {
    private val song = MotionIdentity(
        serverId = 3, albumId = "al-1", album = "Random Access Memories", artist = "Daft Punk",
        title = "Contact", year = 2013, durationSec = 381
    )

    private fun offer(claimed: MotionIdentity, format: MotionFormat = MotionFormat.MP4, source: String = "s") =
        MotionCandidate("p", source, format, claimed)

    private fun elsewhere(album: String = song.album, artist: String = song.artist, year: Int = song.year, mbid: String? = null) =
        MotionIdentity(serverId = 99, albumId = "x", album = album, artist = artist, year = year, releaseMbid = mbid)

    @Test
    fun `the same MusicBrainz release is a certain match, a different one never matches`() {
        val mine = song.copy(releaseMbid = "aaaa-1")
        assertEquals(1f, MotionArtworkMatcher.score(mine, offer(elsewhere(mbid = "AAAA-1"))).score)
        // Even with identical names, a different release id is another release.
        val other = MotionArtworkMatcher.score(mine, offer(elsewhere(mbid = "bbbb-2")))
        assertEquals(0f, other.score)
        assertFalse(other.accepted)
    }

    @Test
    fun `the album's own artwork in the library is accepted`() {
        val m = MotionArtworkMatcher.score(song, offer(song.copy(title = "")))
        assertTrue(m.accepted)
        // Same album id on another server is not the same album.
        assertFalse(MotionArtworkMatcher.score(song, offer(song.copy(serverId = 4, album = "", artist = ""))).accepted)
    }

    @Test
    fun `an exact album and artist match from another source is accepted, with the year agreeing`() {
        assertTrue(MotionArtworkMatcher.score(song, offer(elsewhere(album = "random access memories!", artist = "DAFT PUNK"))).accepted)
        // Accents and "&" don't stop a match.
        val beyonce = song.copy(album = "Lemonade", artist = "Beyoncé", year = 2016)
        assertTrue(MotionArtworkMatcher.score(beyonce, offer(elsewhere(album = "Lemonade", artist = "Beyonce", year = 2016))).accepted)
    }

    @Test
    fun `another edition of the album is too uncertain to show`() {
        val deluxe = MotionArtworkMatcher.score(song, offer(elsewhere(album = "Random Access Memories (10th Anniversary Edition)")))
        assertTrue(deluxe.score > 0f)
        assertFalse(deluxe.accepted)
    }

    @Test
    fun `a matching artist alone never matches`() {
        val m = MotionArtworkMatcher.score(song, offer(elsewhere(album = "Discovery", year = 2001)))
        assertEquals(0f, m.score)
        assertFalse(m.accepted)
    }

    @Test
    fun `a year far apart turns an exact name match down`() {
        assertFalse(MotionArtworkMatcher.score(song, offer(elsewhere(year = 1997))).accepted)
    }

    @Test
    fun `a shared ISRC matches the recording`() {
        val mine = song.copy(isrc = "USQX91300108")
        assertTrue(MotionArtworkMatcher.score(mine, offer(MotionIdentity(9, "", "", "", isrc = "usqx91300108"))).accepted)
    }

    @Test
    fun `two different albums scoring alike are both turned down`() {
        val a = offer(elsewhere(), source = "a")
        val b = offer(elsewhere().copy(albumId = "y", year = 2013, artist = "Daft Punk", album = "Random Access Memories", serverId = 98, releaseMbid = null), source = "b")
        // Same names and year: the same release, so not ambiguous.
        assertEquals(2, MotionArtworkMatcher.pick(song, listOf(a, b)).size)
        val c = offer(MotionIdentity(97, "z", "Random Access Memories", "Daft Punk", year = 2014), source = "c")
        val d = offer(MotionIdentity(96, "w", "Random Access Memories", "Daft Punk", year = 2012), source = "d")
        // Two releases a year either side score the same: no guess.
        assertTrue(MotionArtworkMatcher.pick(song.copy(year = 0), listOf(c, d.copy(claimed = d.claimed.copy(year = 2015)))).isEmpty())
    }

    @Test
    fun `the best match comes first, video before animated pictures on a tie`() {
        val gif = offer(song, MotionFormat.GIF, "gif")
        val mp4 = offer(song, MotionFormat.MP4, "mp4")
        val weak = offer(elsewhere(), MotionFormat.MP4, "weak")
        val picked = MotionArtworkMatcher.pick(song, listOf(gif, weak, mp4)).map { it.candidate.source }
        assertEquals(listOf("mp4", "gif", "weak"), picked)
    }

    @Test
    fun `rejected candidates are left out of the pick`() {
        assertTrue(MotionArtworkMatcher.pick(song, listOf(offer(elsewhere(album = "Homework")))).isEmpty())
    }
}
