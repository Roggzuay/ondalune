package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundProfileScope
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.ui.viewmodel.SoundController
import io.github.roggzuay.ondalune.ui.viewmodel.SoundProfileController
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Putting a saved sound back on.
 *
 * The case that matters most is the one that was broken: replaying the song you are already
 * listening to. That is exactly what the Sound Profiles card does, and it has to work the same as
 * starting any other song.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundProfileControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: OndaluneDatabase
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var sound: SoundController
    private val scope = TestScope(UnconfinedTestDispatcher())

    private val slowed = SoundSnapshot(
        speedPitch = SpeedPitchState(speed = 0.80f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED),
        reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT).copy(enabled = true).sanitized()
    )

    private val track = SubsonicTrack(
        id = "s1",
        serverId = 1L,
        title = "Tangerine",
        artist = "Artist",
        artistId = "ar1",
        album = "Album",
        albumId = "al1",
        durationSec = 200,
        bitRate = 320,
        suffix = "FLAC"
    )

    @Before
    fun open() {
        // Room's suspend queries normally hop to its own executor; run them inline so a launch
        // that reads a profile has finished by the time the test looks.
        db = Room.inMemoryDatabaseBuilder(app, OndaluneDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        prefs = app.getSharedPreferences("sound_profile_controller_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        sound = SoundController(prefs, OndalunePlaybackRuntime.getOrCreateEngine(app, OpenSubsonicClient())) { }
    }

    @After
    fun close() = db.close()

    private fun controller() = SoundProfileController(db.dao(), sound, scope) { 10L }

    @Test
    fun `replaying the song you are already on still puts its sound back`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        // The song is playing, as it would be when you tap it on the Sound Profiles card.
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(1.0f, sound.speedPitchState.value.speed, 0.001f)

        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(ReverbPreset.AMBIENT, sound.reverbState.value.preset)
    }

    @Test
    fun `the sound asked for by the card wins over the song's own saved one`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(
            soundProfileOf(1L, SoundProfileScope.TRACK, "s1", SoundSnapshot(SpeedPitchState(speed = 1.5f)), 1L)
        )
        val profiles = controller()
        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `a sound asked for is used once and does not follow the next song`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        // The next song inherits the sound, as it always has, but is not "the one you asked for".
        profiles.onTrackStarted(track.copy(id = "s2"), autoApply = false)
        assertNull(profiles.offer.value)
    }

    @Test
    fun `a saved sound is offered rather than put on, until you say otherwise`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        val offer = profiles.offer.value
        assertTrue(offer != null && !offer.applied)
        assertEquals(1.0f, sound.speedPitchState.value.speed, 0.001f)

        profiles.applyOffer()
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertTrue(profiles.offer.value?.applied == true)
    }

    @Test
    fun `with auto-apply on it goes on by itself and says so`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertTrue(profiles.offer.value?.applied == true)
    }

    @Test
    fun `an album's saved sound covers its songs`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.ALBUM, "al1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `a song's own saved sound beats its album's`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.ALBUM, "al1", slowed, 1L))
        db.dao().upsertSoundProfile(
            soundProfileOf(1L, SoundProfileScope.TRACK, "s1", SoundSnapshot(SpeedPitchState(speed = 1.25f)), 2L)
        )
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(1.25f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `forgetting a sound stops it coming back`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.forget(1L, SoundProfileScope.TRACK, "s1")
        profiles.onTrackStarted(track, autoApply = true)
        assertNull(profiles.offer.value)
        assertEquals(1.0f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `a song with nothing saved is left alone`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertNull(profiles.offer.value)
        assertFalse(sound.currentSnapshot().differsFrom(SoundSnapshot()))
    }


    // ---- A saved sound belongs to its song, not to everything after it ----

    @Test
    fun `a song without a saved sound goes back to how you had it`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        // You had the whole app at 1.10x by hand.
        sound.setPlaybackSpeed(1.10f)
        profiles.onTrackStarted(track, autoApply = false)

        // This song has a sound of its own, played from the Sound Profiles list.
        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        // Skip on to a song that has none: back to 1.10x, not stuck at 0.80x.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(1.10f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(ReverbPreset.OFF, sound.reverbState.value.preset)
    }

    @Test
    fun `a saved sound put on from the strip is just as much its song's`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(1.00f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `auto-applied sounds are lifted off the next song too`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = true)
        assertEquals(1.00f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `one saved sound gives way to the next, and the last gives way to how you had it`() = runTest(UnconfinedTestDispatcher()) {
        sound.setPlaybackSpeed(1.25f)
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        db.dao().upsertSoundProfile(
            soundProfileOf(1L, SoundProfileScope.TRACK, "s2", SoundSnapshot(SpeedPitchState(speed = 1.75f)), 1L)
        )
        val profiles = controller()

        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = true)
        assertEquals(1.75f, sound.speedPitchState.value.speed, 0.001f)

        // Not back to 0.80x, and not stuck at 1.75x: back to the 1.25x you set by hand.
        profiles.onTrackStarted(track.copy(id = "s3", albumId = "al3"), autoApply = true)
        assertEquals(1.25f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `with no saved sounds anywhere the sound carries on as it always has`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        sound.setPlaybackSpeed(0.90f)
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = true)
        assertEquals(0.90f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `resetting by hand means the old sound does not come back later`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        sound.setPlaybackSpeed(1.10f)
        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        // You reset it yourself: that is the sound you want now, not the one from before.
        sound.resetSpeedAndPitch()
        sound.resetReverbToDry()
        profiles.onSoundResetByHand()

        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(1.00f, sound.speedPitchState.value.speed, 0.001f)
    }


    @Test
    fun `a sound you save while listening comes off the next song, back to how the song started`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        sound.setPlaybackSpeed(1.10f)
        profiles.onTrackStarted(track, autoApply = false)

        // You bend this song and keep it.
        sound.setPlaybackSpeed(0.80f)
        profiles.save(track, SoundProfileScope.TRACK, sound.currentSnapshot())
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)

        // The next song is back to the 1.10x this one started on, not the 0.80x you just saved.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(1.10f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `a sound you bend by hand and do not save still carries, as it always has`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        sound.setPlaybackSpeed(0.90f)
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(0.90f, sound.speedPitchState.value.speed, 0.001f)
    }
}
