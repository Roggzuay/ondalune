package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.overrideFor
import io.github.roggzuay.ondalune.data.model.withOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackOverrideTest {
    private val original = SubsonicTrack(
        id = "s1", serverId = 2, title = "Teardrp", artist = "Massive Atack", artistId = "a", album = "Mezzanine",
        albumId = "al", durationSec = 300, bitRate = 320, suffix = "MP3", genre = "Trip-Hop", year = 1998
    )

    @Test
    fun `only changed details are kept, and the song shows them`() {
        val o = overrideFor(original, "Teardrop", "Massive Attack", "Trip-Hop", 1998)!!
        assertEquals("Teardrop", o.title)
        assertEquals("Massive Attack", o.artist)
        assertNull("unchanged genre stays the source's", o.genre)
        assertNull(o.year)
        val shown = original.withOverride(o)
        assertEquals("Teardrop", shown.title)
        assertEquals("Massive Attack", shown.artist)
        assertEquals("Mezzanine", shown.album)
    }

    @Test
    fun `putting the original details back drops the edit`() {
        assertNull(overrideFor(original, " Teardrp ", "Massive Atack", "Trip-Hop", 1998))
    }

    @Test
    fun `a sync that renames the song keeps the edit on top`() {
        val o = overrideFor(original, "Teardrop", "Massive Atack", "Trip-Hop", 1998)
        val resynced = original.copy(title = "Teardrop (Remastered)", playCount = 9)
        assertEquals("Teardrop", resynced.withOverride(o).title)
        assertEquals(9, resynced.withOverride(o).playCount)
    }
}
