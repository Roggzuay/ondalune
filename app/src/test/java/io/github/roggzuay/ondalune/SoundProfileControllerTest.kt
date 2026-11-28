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
import io.github.roggzuay.ondalune.data.model.adjusted
import io.github.roggzuay.ondalune.data.model.snapshot
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.ui.viewmodel.SoundController
import io.github.roggzuay.ondalune.ui.viewmodel.SoundProfileController
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    private fun controller(keepHistory: Boolean = true) =
        SoundProfileController(db.dao(), sound, scope, { keepHistory }) { 10L }

    /** Wired as the ViewModel wires it: a hand on any sound control calls off a running slide. */
    private fun wiredController(keepHistory: Boolean = true) =
        controller(keepHistory).also { sound.onChangedByHand = it::onSoundChangedByHand }

    /** Runs a tapped sound's slide all the way to where it was going. */
    private fun landSlide() = scope.testScheduler.advanceUntilIdle()

    /** Lets the sound hold still long enough for the play it belongs to to be rewritten. */
    private fun settle() {
        scope.testScheduler.advanceTimeBy(SoundProfileController.SoundSettleMs + 50L)
        scope.testScheduler.runCurrent()
    }

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
        assertTrue(profiles.offer.value?.applied == true)
        // Tapping leans the song into the sound rather than switching it, so let the slide land.
        landSlide()
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
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
        landSlide()
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

    // ---- The sound a play is remembered by ----
    //
    // A play is written down thirty seconds in, but reaching for the speed and the reverb is
    // something you do after a song has got going. The row used to be written before the
    // interesting part and left saying "as mastered", so an adjustment nobody saved never showed
    // up on Home at all — the one thing the whole feature exists for.

    @Test
    fun `an adjustment made after the play was counted still reaches the history`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        // Thirty seconds in, nothing has been touched yet.
        profiles.recordPlay(track)
        assertFalse(db.dao().observePlayHistory(10).first().single().adjusted)

        // Now you slow it down and like it that way, and never save it.
        sound.setPlaybackSpeed(0.80f)
        settle()

        val entry = db.dao().observePlayHistory(10).first().single()
        assertTrue("an unsaved adjustment has to be remembered", entry.adjusted)
        assertEquals(0.80f, entry.snapshot.speedPitch.speed, 0.001f)
    }

    @Test
    fun `the sound is followed for the whole play, not just the first change`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.recordPlay(track)

        sound.setPlaybackSpeed(0.90f)
        settle()
        sound.setReverbEnabled(true)
        settle()

        val entry = db.dao().observePlayHistory(10).first().single()
        assertEquals(0.90f, entry.snapshot.speedPitch.speed, 0.001f)
        assertTrue(entry.snapshot.reverb.isActivelyProcessing)
    }

    @Test
    fun `a play still counts as one row however much the sound is moved`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.recordPlay(track)
        listOf(0.95f, 0.90f, 0.85f, 0.80f).forEach { speed ->
            sound.setPlaybackSpeed(speed)
            settle()
        }
        assertEquals(1, db.dao().observePlayHistory(10).first().size)
    }

    @Test
    fun `putting the sound back where it was leaves the play unadjusted`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.recordPlay(track)
        sound.setPlaybackSpeed(0.80f)
        settle()
        sound.setPlaybackSpeed(1.00f)
        settle()
        assertFalse(db.dao().observePlayHistory(10).first().single().adjusted)
    }

    @Test
    fun `the song after does not rewrite the play of the song before`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.recordPlay(track)

        // On to the next song, and bend that one instead.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        sound.setPlaybackSpeed(0.85f)
        settle()

        val rows = db.dao().observePlayHistory(10).first().associateBy { it.trackId }
        assertFalse("that play was not the one you bent", rows.getValue("s1").adjusted)
        // The bend belongs to the song being heard, and gets its own row even this early in it.
        assertEquals(0.85f, rows.getValue("s2").snapshot.speedPitch.speed, 0.001f)
    }

    @Test
    fun `a play counted just as the song changed belongs to the song that was heard`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        // The count lands for the previous song after the next one has already started.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        profiles.recordPlay(track)

        sound.setPlaybackSpeed(0.85f)
        settle()

        val entry = db.dao().observePlayHistory(10).first().single { it.trackId == "s1" }
        assertFalse(entry.adjusted)
    }

    @Test
    fun `clearing the history does not leave a row being written to`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.recordPlay(track)
        profiles.clearHistory()

        sound.setPlaybackSpeed(0.80f)
        settle()

        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    // ---- Leaning into a tapped sound ----

    @Test
    fun `tapping the strip leans into the sound instead of switching to it`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(1.00f, sound.speedPitchState.value.speed, 0.001f)

        profiles.applyOffer()
        // The tap is taken at once, even though the sound has not arrived.
        assertTrue(profiles.offer.value!!.applied)

        // Part way across, the song is somewhere between the two, not at either.
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 2)
        scope.testScheduler.runCurrent()
        val midway = sound.speedPitchState.value.speed
        assertTrue("expected a sound in transit, got $midway", midway > 0.80f && midway < 1.00f)
        assertTrue(profiles.glide.value > 0f)

        // And it arrives exactly.
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(ReverbPreset.AMBIENT, sound.reverbState.value.preset)
        assertEquals(0f, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `the room opens from silence rather than arriving at full wetness`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()

        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideFrameMs * 2)
        scope.testScheduler.runCurrent()
        val early = sound.reverbState.value
        assertTrue("the room has to exist to be heard filling", early.enabled)
        assertTrue(early.wetDryMix < slowed.reverb.wetDryMix)

        scope.testScheduler.advanceUntilIdle()
        assertEquals(slowed.reverb.wetDryMix, sound.reverbState.value.wetDryMix, 0.001f)
    }

    @Test
    fun `a second tap during a slide does not restart it`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 2)
        scope.testScheduler.runCurrent()
        val halfWay = profiles.glide.value

        profiles.applyOffer()
        scope.testScheduler.runCurrent()
        assertEquals("the slide was already on its way", halfWay, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `a slide cut short by the next song is landed, not abandoned half way`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideFrameMs * 3)
        scope.testScheduler.runCurrent()

        // Skipping on mid-slide. The song that follows has no saved sound, so what comes back is
        // the sound you had before the slide — never a sound from inside it.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0f, profiles.glide.value, 0.0001f)
        assertEquals(1.00f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `resetting the sound by hand during a slide leaves your reset standing`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 3)
        scope.testScheduler.runCurrent()

        // Same order as the player: the slide is called off first, then the reset lands.
        profiles.onSoundResetByHand()
        sound.resetSpeedAndPitch()
        sound.resetReverbToDry()
        scope.testScheduler.advanceUntilIdle()

        assertEquals("the slide must not keep bending underneath", 1.00f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(0f, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `a sound played from the Sound Profiles list still arrives at once`() = runTest(UnconfinedTestDispatcher()) {
        // Only the strip leans. Asking for a song by its sound means you want that song, and
        // hearing it ramp up for three seconds every time would be a bug, not a mechanic.
        val profiles = controller()
        profiles.playWith(track.id, slowed)
        profiles.onTrackStarted(track, autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(0f, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `auto-apply still arrives at once`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = true)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(0f, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `a sound changed before the play was counted is written down anyway`() = runTest(UnconfinedTestDispatcher()) {
        // The reported bug. A play is only counted thirty seconds in, so tuning a song and moving
        // on left nothing behind at all — and this history is for exactly those sounds.
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)

        sound.setPlaybackSpeed(0.80f)
        settle()

        val entry = db.dao().observePlayHistory(10).first().single()
        assertTrue("changing the sound has to be enough to be remembered", entry.adjusted)
        assertEquals(0.80f, entry.snapshot.speedPitch.speed, 0.001f)
    }

    @Test
    fun `the play counted later corrects that row instead of adding a second`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        sound.setPlaybackSpeed(0.80f)
        settle()

        profiles.recordPlay(track)
        sound.setPlaybackSpeed(0.90f)
        settle()

        val rows = db.dao().observePlayHistory(10).first()
        assertEquals("one play, one row", 1, rows.size)
        assertEquals(0.90f, rows.single().snapshot.speedPitch.speed, 0.001f)
    }

    @Test
    fun `turning the sound history off writes nothing down`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller(keepHistory = false)
        profiles.onTrackStarted(track, autoApply = false)

        sound.setPlaybackSpeed(0.80f)
        settle()

        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    @Test
    fun `a sound put straight back leaves nothing behind`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)

        sound.setPlaybackSpeed(0.80f)
        sound.setPlaybackSpeed(1.00f)
        settle()

        assertTrue(
            "a slider dragged out and back is not a sound anybody chose",
            db.dao().observePlayHistory(10).first().isEmpty()
        )
    }

    @Test
    fun `nothing is written down with no song playing`() = runTest(UnconfinedTestDispatcher()) {
        controller()

        sound.setPlaybackSpeed(0.80f)
        settle()

        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    @Test
    fun `a sound leaned into from the strip is written down without waiting`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)

        profiles.applyOffer()
        landSlide()
        settle()

        val entry = db.dao().observePlayHistory(10).first().single()
        assertEquals(0.80f, entry.snapshot.speedPitch.speed, 0.001f)
        assertTrue("the song had a saved sound when it played", entry.hadProfile)
    }

    @Test
    fun `a play counted after the history is cleared is not written back`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        sound.setPlaybackSpeed(0.80f)
        settle()
        assertEquals(1, db.dao().observePlayHistory(10).first().size)

        // You clear the list while the song is still playing; thirty seconds later the play it
        // was already counting arrives. The song must not put itself back under your thumb.
        profiles.clearHistory()
        profiles.recordPlay(track)
        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    @Test
    fun `with the history switch off the counted play writes nothing down`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller(keepHistory = false)
        profiles.onTrackStarted(track, autoApply = false)
        sound.setPlaybackSpeed(0.80f)
        settle()
        profiles.recordPlay(track)
        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    @Test
    fun `the song after a cleared history is recorded again`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.clearHistory()

        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        profiles.recordPlay(track.copy(id = "s2", albumId = "al2"))
        assertEquals(1, db.dao().observePlayHistory(10).first().size)
    }

    @Test
    fun `moving a sound control by hand stops the slide and your change stands`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = wiredController()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 2)
        scope.testScheduler.runCurrent()

        // Halfway into the slide you grab the speed slider. It used to crawl away under your
        // thumb for the rest of the three and a half seconds.
        sound.setPlaybackSpeed(1.50f)
        landSlide()
        assertEquals(1.50f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(0f, profiles.glide.value, 0.0001f)
    }

    @Test
    fun `a slide cut short by the next song lands on the sound it was going to`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s2", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 2)
        scope.testScheduler.runCurrent()
        val midway = sound.speedPitchState.value.speed
        assertTrue("the slide should be in transit, was $midway", midway > 0.80f && midway < 1.00f)

        // The next song has a sound saved but is only offered it, so nothing else writes the
        // speed. Whatever is left standing is what the cut-short slide left.
        profiles.onTrackStarted(track.copy(id = "s2", albumId = "al2"), autoApply = false)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `a slide does not offer to save the sound it is passing through`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()
        scope.testScheduler.advanceTimeBy(SoundProfileController.GlideMs / 2)
        scope.testScheduler.runCurrent()

        // Sixty-odd frames, none of them a sound anyone chose: the save card must stay down.
        assertNull(sound.pendingSave.value)
        landSlide()
    }

    @Test
    fun `the slide reads as moving from the moment it is tapped`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.TRACK, "s1", slowed, 1L))
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        profiles.applyOffer()

        // No time advanced. The strip decides its wording from this, so a frame of it sitting at
        // zero showed "Playing your saved sound" for a blink before the slide had begun.
        assertTrue(profiles.glide.value > 0f)
        landSlide()
    }

    @Test
    fun `a song skipped through on an album with a saved sound is not written down as yours`() = runTest(UnconfinedTestDispatcher()) {
        db.dao().upsertSoundProfile(soundProfileOf(1L, SoundProfileScope.ALBUM, "al1", slowed, 1L))
        val profiles = controller()

        // Auto-apply puts the album's sound on. That is the app's doing, not a choice made for
        // this song, so skipping through the album must not fill the history card with it.
        profiles.onTrackStarted(track, autoApply = true)
        settle()
        assertTrue(db.dao().observePlayHistory(10).first().isEmpty())
    }

    @Test
    fun `a late count for the song before leaves the playing song's row alone`() = runTest(UnconfinedTestDispatcher()) {
        val profiles = controller()
        profiles.onTrackStarted(track, autoApply = false)
        sound.setPlaybackSpeed(0.80f)
        settle()

        val second = track.copy(id = "s2", albumId = "al2")
        profiles.onTrackStarted(second, autoApply = false)
        sound.setPlaybackSpeed(0.85f)
        settle()
        val rows = db.dao().observePlayHistory(10).first().size

        // The first song's play is counted at last, while the second song's row is open. The late
        // count gets its own row and must not take the open one away: losing it meant the next
        // sound move opened a second row for the same play.
        profiles.recordPlay(track)
        sound.setPlaybackSpeed(0.90f)
        settle()
        assertEquals(rows + 1, db.dao().observePlayHistory(10).first().size)
    }
}
