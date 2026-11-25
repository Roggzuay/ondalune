package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.PlayHistoryEntry
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundProfileScope
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.StoredSound
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.adjusted
import io.github.roggzuay.ondalune.data.model.playedSounds
import io.github.roggzuay.ondalune.data.model.savedSounds
import io.github.roggzuay.ondalune.data.model.snapshot
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A song, the way you like to hear it: what is remembered, and what counts as a change. */
class SoundProfileTest {

    private fun track(id: String, album: String = "al1", serverId: Long = 1L) = SubsonicTrack(
        id = id,
        serverId = serverId,
        title = "Song $id",
        artist = "Artist",
        artistId = "ar1",
        album = "Album",
        albumId = album,
        durationSec = 200,
        bitRate = 320,
        suffix = "FLAC"
    )

    private val slowed = SoundSnapshot(
        speedPitch = SpeedPitchState(
            speed = 0.80f,
            pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED
        ),
        reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT).copy(enabled = true).sanitized()
    )

    // ---- What counts as a change ----

    @Test
    fun `a song played as mastered is neutral`() {
        assertTrue(SoundSnapshot().isNeutral)
    }

    @Test
    fun `slowing a song down is not neutral`() {
        assertFalse(slowed.isNeutral)
    }

    @Test
    fun `reverb that is on but fully dry is still neutral`() {
        val dry = SoundSnapshot(reverb = ReverbState(enabled = true, preset = ReverbPreset.HALL, wetDryMix = 0f))
        assertTrue(dry.isNeutral)
    }

    @Test
    fun `an equalizer preset that lifts a band is not neutral`() {
        val eq = SoundSnapshot(
            eqPreset = EqualizerPreset.DEEP_SUB,
            eqBandsDb = EqualizerPreset.DEEP_SUB.bandsDb
        )
        assertFalse(eq.isNeutral)
    }

    @Test
    fun `a slider nudged and put back is not a change`() {
        val base = SoundSnapshot(speedPitch = SpeedPitchState(speed = 1.00f))
        val nudged = SoundSnapshot(speedPitch = SpeedPitchState(speed = 1.001f))
        assertFalse(nudged.differsFrom(base))
    }

    @Test
    fun `a real speed change is a change`() {
        assertTrue(slowed.differsFrom(SoundSnapshot()))
    }

    @Test
    fun `changing only the pitch mode is a change`() {
        val a = SoundSnapshot(speedPitch = SpeedPitchState(speed = 1.2f, pitchMode = PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH))
        val b = SoundSnapshot(speedPitch = SpeedPitchState(speed = 1.2f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED))
        assertTrue(b.differsFrom(a))
    }

    @Test
    fun `reverb room size only counts while the reverb is actually heard`() {
        val quiet = SoundSnapshot(reverb = ReverbState(enabled = false, preset = ReverbPreset.HALL, roomSize = 0.2f))
        val other = SoundSnapshot(reverb = ReverbState(enabled = false, preset = ReverbPreset.HALL, roomSize = 0.9f))
        assertFalse(other.differsFrom(quiet))
    }

    @Test
    fun `a band moved past the slack is a change`() {
        val a = SoundSnapshot(eqBandsDb = listOf(0f, 0f, 0f, 0f, 0f))
        val b = SoundSnapshot(eqBandsDb = listOf(0f, 3f, 0f, 0f, 0f))
        assertTrue(b.differsFrom(a))
    }

    // ---- How it reads ----

    @Test
    fun `the summary says the speed, the semitones and the room`() {
        val text = slowed.summary
        assertTrue(text, text.contains("0.80x"))
        assertTrue(text, text.contains("-3.86 st"))
        assertTrue(text, text.contains(ReverbPreset.AMBIENT.label))
    }

    @Test
    fun `an untouched sound says so`() {
        assertEquals("As mastered", SoundSnapshot().summary)
    }

    @Test
    fun `the ribbon prefers the speed and falls back in order`() {
        assertEquals("0.80x", slowed.ribbon)
        val pitched = SoundSnapshot(
            speedPitch = SpeedPitchState(
                pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
                independentPitchRatio = SpeedPitchState.semitonesToRatio(2f)
            )
        )
        assertEquals("+2 st", pitched.ribbon)
        val reverbOnly = SoundSnapshot(reverb = ReverbState.fromPreset(ReverbPreset.HALL).copy(enabled = true))
        assertEquals("Reverb", reverbOnly.ribbon)
        assertNull(SoundSnapshot().ribbon)
    }

    // ---- Saving and reading back ----

    @Test
    fun `a saved sound comes back exactly as it went in`() {
        val profile = soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, updatedAt = 10L)
        val back = profile.snapshot
        assertEquals(slowed.speedPitch.speed, back.speedPitch.speed, 0.0001f)
        assertEquals(slowed.speedPitch.pitchMode, back.speedPitch.pitchMode)
        assertEquals(slowed.reverb.preset, back.reverb.preset)
        assertEquals(slowed.reverb.wetDryMix, back.reverb.wetDryMix, 0.0001f)
        assertFalse(back.differsFrom(slowed))
    }

    @Test
    fun `a custom equalizer curve survives the round trip`() {
        val curve = SoundSnapshot(eqPreset = EqualizerPreset.FLAT_STUDIO, eqBandsDb = listOf(1.5f, -2f, 3f, 0f, -4.5f))
        val back = curve.stored().snapshot()
        assertEquals(curve.eqBandsDb, back.eqBandsDb)
    }

    @Test
    fun `a row written by a newer version falls back instead of crashing`() {
        val broken = StoredSound(
            pitchMode = "SOMETHING_NEW",
            reverbPreset = "CATHEDRAL_OF_THE_FUTURE",
            eqPreset = "NOT_A_PRESET",
            eqBandsDb = "nonsense"
        )
        val back = broken.snapshot()
        assertEquals(PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH, back.speedPitch.pitchMode)
        assertEquals(ReverbPreset.OFF, back.reverb.preset)
        assertEquals(EqualizerPreset.FLAT_STUDIO, back.eqPreset)
        assertEquals(EqualizerPreset.FLAT_STUDIO.bandsDb, back.eqBandsDb)
    }

    @Test
    fun `a stored speed outside the engine's range is pulled back in`() {
        val back = StoredSound(speed = 9f, independentPitchRatio = 99f).snapshot()
        assertEquals(2.00f, back.speedPitch.speed, 0.0001f)
        assertEquals(2.00f, back.speedPitch.independentPitchRatio, 0.0001f)
    }

    // ---- The play history ----

    private fun play(trackId: String, at: Long, sound: SoundSnapshot, serverId: Long = 1L) =
        PlayHistoryEntry(serverId = serverId, trackId = trackId, playedAt = at, sound = sound.stored())

    @Test
    fun `a play that changed nothing is not marked as adjusted`() {
        assertFalse(play("s1", 1L, SoundSnapshot()).adjusted)
        assertTrue(play("s1", 1L, slowed).adjusted)
    }

    @Test
    fun `the card shows the newest play of each song, changed ones only`() {
        val tracks = listOf(track("s1"), track("s2"), track("s3"))
        val history = listOf(
            play("s1", 300L, slowed),
            play("s1", 100L, SoundSnapshot()),
            play("s2", 200L, SoundSnapshot()),
            play("s3", 50L, slowed)
        )
        val shown = playedSounds(history, tracks)
        assertEquals(listOf("s1", "s3"), shown.map { it.track.id })
        assertEquals(300L, shown.first().playedAt)
    }

    @Test
    fun `plays of songs the library no longer holds are left out`() {
        val shown = playedSounds(listOf(play("gone", 10L, slowed)), listOf(track("s1")))
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `the same song id on another server is not confused for this one`() {
        val shown = playedSounds(listOf(play("s1", 10L, slowed, serverId = 2L)), listOf(track("s1", serverId = 1L)))
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `the card stops at the limit it is given`() {
        val tracks = (1..30).map { track("s$it") }
        val history = (1..30).map { play("s$it", it.toLong(), slowed) }
        assertEquals(5, playedSounds(history, tracks, limit = 5).size)
    }

    // ---- Saved sounds, matched to songs and albums ----

    private fun album(id: String) = SubsonicAlbum(
        id = id,
        serverId = 1L,
        name = "Album $id",
        artist = "Artist",
        artistId = "ar1",
        songCount = 2,
        durationSec = 400,
        year = 2020,
        genre = "Jazz",
        starred = false
    )

    @Test
    fun `a song's saved sound is listed against that song`() {
        val rows = savedSounds(
            listOf(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 5L)),
            listOf(track("s1")),
            listOf(album("al1"))
        )
        assertEquals(1, rows.size)
        assertEquals("Song s1", rows.first().title)
        assertFalse(rows.first().isAlbum)
    }

    @Test
    fun `an album's saved sound plays its first song`() {
        val tracks = listOf(
            track("s2").copy(trackNumber = 2),
            track("s1").copy(trackNumber = 1)
        )
        val rows = savedSounds(
            listOf(soundProfileOf(1L, SoundProfileScope.ALBUM, "al1", slowed, 5L)),
            tracks,
            listOf(album("al1"))
        )
        assertEquals(1, rows.size)
        assertTrue(rows.first().isAlbum)
        assertEquals("s1", rows.first().track?.id)
    }

    @Test
    fun `saved sounds are listed newest first`() {
        val rows = savedSounds(
            listOf(
                soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L),
                soundProfileOf(1L, SoundProfileScope.TRACK, "s2", slowed, 9L)
            ),
            listOf(track("s1"), track("s2")),
            emptyList()
        )
        assertEquals(listOf("Song s2", "Song s1"), rows.map { it.title })
    }

    @Test
    fun `a saved sound whose song has left the library is not listed`() {
        val rows = savedSounds(
            listOf(soundProfileOf(1L, SoundProfileScope.TRACK, "gone", slowed, 1L)),
            listOf(track("s1")),
            emptyList()
        )
        assertTrue(rows.isEmpty())
    }
}
