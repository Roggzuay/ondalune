package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.blendTo
import io.github.roggzuay.ondalune.data.model.soundGlideEase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slow slide into a tapped sound, frame by frame.
 *
 * What is being protected here is that the slide *arrives*. Audible interpolation is easy to get
 * nearly right and nearly right is a song left at 0.93x with nothing in the app that says why, so
 * every case below ends by checking the last frame equals the sound that was asked for.
 */
class SoundGlideTest {

    private val plain = SoundSnapshot()

    private val slowedAndWet = SoundSnapshot(
        speedPitch = SpeedPitchState(
            speed = 0.80f,
            pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED
        ),
        reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT),
        eqPreset = EqualizerPreset.WARM_ANALOG,
        eqBandsDb = EqualizerPreset.WARM_ANALOG.bandsDb
    )

    // ---- The ends of the slide ----

    @Test
    fun `the first frame is the sound you already had`() {
        assertEquals(plain, plain.blendTo(slowedAndWet, 0f))
    }

    @Test
    fun `the last frame is exactly the sound you asked for`() {
        assertEquals(slowedAndWet, plain.blendTo(slowedAndWet, 1f))
    }

    @Test
    fun `a fraction past the end still lands on the sound, not past it`() {
        assertEquals(slowedAndWet, plain.blendTo(slowedAndWet, 1.4f))
    }

    @Test
    fun `a negative fraction is the sound you already had`() {
        assertEquals(plain, plain.blendTo(slowedAndWet, -0.3f))
    }

    @Test
    fun `sliding into the sound you are already on changes nothing anywhere`() {
        assertEquals(slowedAndWet, slowedAndWet.blendTo(slowedAndWet, 0.5f))
    }

    // ---- Speed and pitch ----

    @Test
    fun `speed sits half way across at half way`() {
        val mid = plain.blendTo(slowedAndWet, 0.5f)
        assertEquals(0.90f, mid.speedPitch.speed, 0.001f)
    }

    @Test
    fun `two sounds in the same mode keep it, and the numbers under it travel`() {
        // Switching the mode mid-slide would show a mode on the controls that nobody chose.
        val from = SoundSnapshot(
            speedPitch = SpeedPitchState(
                speed = 1.0f,
                pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
                independentPitchRatio = 1.0f
            )
        )
        val to = SoundSnapshot(
            speedPitch = SpeedPitchState(
                speed = 0.90f,
                pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
                independentPitchRatio = 0.80f
            )
        )
        val mid = from.blendTo(to, 0.5f)
        assertEquals(PitchCorrectionMode.INDEPENDENT_SEMITONES, mid.speedPitch.pitchMode)
        assertEquals(0.95f, mid.speedPitch.speed, 0.001f)
        assertEquals(0.90f, mid.speedPitch.independentPitchRatio, 0.001f)
    }

    @Test
    fun `a slide between two speed-linked sounds keeps pitch following speed`() {
        val from = SoundSnapshot(
            speedPitch = SpeedPitchState(speed = 1.0f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED)
        )
        val to = SoundSnapshot(
            speedPitch = SpeedPitchState(speed = 0.80f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED)
        )
        val mid = from.blendTo(to, 0.5f)
        assertEquals(PitchCorrectionMode.VARISPEED_NATURAL_LINKED, mid.speedPitch.pitchMode)
        // Linked means the engine takes the pitch from the speed, so it arrives for free.
        assertEquals(0.90f, mid.speedPitch.speed, 0.001f)
        assertEquals(0.90f, mid.speedPitch.effectivePitch, 0.001f)
    }

    @Test
    fun `the slide travels along the pitch the engine hears when the modes disagree`() {
        val mid = plain.blendTo(slowedAndWet, 0.5f)
        // Modes are not a scale, so mid-slide it runs on the one that is.
        assertEquals(PitchCorrectionMode.INDEPENDENT_SEMITONES, mid.speedPitch.pitchMode)
        // Half way between 1.00 (pitch preserved) and 0.80 (pitch linked to speed).
        assertEquals(0.90f, mid.speedPitch.effectivePitch, 0.001f)
    }

    @Test
    fun `the real pitch mode arrives at the end, when setting it changes nothing`() {
        val landed = plain.blendTo(slowedAndWet, 1f)
        assertEquals(PitchCorrectionMode.VARISPEED_NATURAL_LINKED, landed.speedPitch.pitchMode)
        assertEquals(0.80f, landed.speedPitch.effectivePitch, 0.001f)
    }

    @Test
    fun `pitch moves monotonically and never overshoots`() {
        var last = plain.speedPitch.effectivePitch
        for (step in 1..20) {
            val pitch = plain.blendTo(slowedAndWet, step / 20f).speedPitch.effectivePitch
            assertTrue("frame $step went the wrong way", pitch <= last + 0.0001f)
            assertTrue("frame $step overshot", pitch >= 0.80f - 0.0001f)
            last = pitch
        }
        assertEquals(0.80f, last, 0.001f)
    }

    @Test
    fun `a speed slide stays inside what the engine accepts`() {
        val fast = SoundSnapshot(speedPitch = SpeedPitchState(speed = 2.00f))
        for (step in 0..10) {
            val speed = fast.blendTo(plain, step / 10f).speedPitch.speed
            assertTrue(speed in 0.80f..2.00f)
        }
    }

    // ---- Reverb ----

    @Test
    fun `a room is switched on at the first frame so its wetness can climb from silence`() {
        val early = plain.blendTo(slowedAndWet, 0.05f)
        assertTrue("a reverb that is off cannot fade in", early.reverb.enabled)
        assertEquals(ReverbPreset.AMBIENT, early.reverb.preset)
        // Climbing from nothing, not from whatever mix the off reverb happened to store.
        assertTrue(early.reverb.wetDryMix < slowedAndWet.reverb.wetDryMix)
        assertTrue(early.reverb.wetDryMix > 0f)
    }

    @Test
    fun `wetness is half the target at half way`() {
        val mid = plain.blendTo(slowedAndWet, 0.5f)
        assertEquals(slowedAndWet.reverb.wetDryMix / 2f, mid.reverb.wetDryMix, 0.001f)
    }

    @Test
    fun `an off reverb contributes no wetness to slide away from`() {
        // A stored mix on a switched-off reverb is not being heard, so starting the slide there
        // would open the room with a jump.
        val offButWet = SoundSnapshot(
            reverb = ReverbState(enabled = false, preset = ReverbPreset.OFF, wetDryMix = 0.9f)
        )
        val early = offButWet.blendTo(slowedAndWet, 0.1f)
        assertTrue(early.reverb.wetDryMix < 0.2f)
    }

    @Test
    fun `leaving a room drains it rather than cutting the tail`() {
        val mid = slowedAndWet.blendTo(plain, 0.5f)
        assertTrue("the room must still exist while it empties", mid.reverb.enabled)
        assertEquals(slowedAndWet.reverb.wetDryMix / 2f, mid.reverb.wetDryMix, 0.001f)

        val landed = slowedAndWet.blendTo(plain, 1f)
        assertFalse(landed.reverb.isActivelyProcessing)
    }

    @Test
    fun `the shape of the room travels too`() {
        val roomy = SoundSnapshot(
            reverb = ReverbState(
                enabled = true,
                preset = ReverbPreset.AMBIENT,
                wetDryMix = 0.5f,
                roomSize = 1.0f,
                decay = 1.0f,
                damping = 1.0f
            )
        )
        val tight = SoundSnapshot(
            reverb = ReverbState(
                enabled = true,
                preset = ReverbPreset.AMBIENT,
                wetDryMix = 0.5f,
                roomSize = 0.0f,
                decay = 0.05f,
                damping = 0.0f
            )
        )
        val mid = roomy.blendTo(tight, 0.5f)
        assertEquals(0.5f, mid.reverb.roomSize, 0.001f)
        assertEquals(0.525f, mid.reverb.decay, 0.001f)
        assertEquals(0.5f, mid.reverb.damping, 0.001f)
    }

    @Test
    fun `every frame of a reverb slide is a sound the engine will accept`() {
        for (step in 0..20) {
            val frame = plain.blendTo(slowedAndWet, step / 20f).reverb
            assertEquals(frame, frame.sanitized())
        }
    }

    // ---- Equalizer ----

    @Test
    fun `each band travels its own distance`() {
        val mid = plain.blendTo(slowedAndWet, 0.5f)
        val target = EqualizerPreset.WARM_ANALOG.bandsDb
        assertEquals(target.size, mid.eqBandsDb.size)
        target.indices.forEach { band ->
            assertEquals("band $band", target[band] / 2f, mid.eqBandsDb[band], 0.001f)
        }
    }

    @Test
    fun `curves of different lengths have nothing to mix, so the target is taken whole`() {
        // Not a case the app produces, but half of a curve that does not fit is worse than the
        // curve that does.
        val shortCurve = SoundSnapshot(eqBandsDb = listOf(1f, 2f))
        val mid = shortCurve.blendTo(slowedAndWet, 0.5f)
        assertEquals(EqualizerPreset.WARM_ANALOG.bandsDb, mid.eqBandsDb)
    }

    @Test
    fun `a band slide is monotonic in both directions`() {
        val lifted = SoundSnapshot(
            eqPreset = EqualizerPreset.DEEP_SUB,
            eqBandsDb = EqualizerPreset.DEEP_SUB.bandsDb
        )
        var previous = lifted.eqBandsDb
        for (step in 1..10) {
            val frame = lifted.blendTo(plain, step / 10f).eqBandsDb
            frame.indices.forEach { band ->
                val wasAbove = lifted.eqBandsDb[band] > 0f
                if (wasAbove) {
                    assertTrue("band $band rose", frame[band] <= previous[band] + 0.0001f)
                } else {
                    assertTrue("band $band fell", frame[band] >= previous[band] - 0.0001f)
                }
            }
            previous = frame
        }
        assertEquals(plain.eqBandsDb, previous)
    }

    // ---- A mid-slide sound is a real one ----

    @Test
    fun `a sound half way into a slide is not the sound it started from`() {
        val mid = plain.blendTo(slowedAndWet, 0.5f)
        assertNotEquals(plain, mid)
        assertTrue(mid.differsFrom(plain))
        assertTrue(mid.differsFrom(slowedAndWet))
        // Half of something is still something: this must not read as "as mastered".
        assertFalse(mid.isNeutral)
    }

    // ---- The easing curve ----

    @Test
    fun `easing is still at both ends and symmetrical in the middle`() {
        assertEquals(0f, soundGlideEase(0f), 0.0001f)
        assertEquals(1f, soundGlideEase(1f), 0.0001f)
        assertEquals(0.5f, soundGlideEase(0.5f), 0.0001f)
    }

    @Test
    fun `easing starts and finishes more slowly than it travels`() {
        val opening = soundGlideEase(0.1f) - soundGlideEase(0.0f)
        val middle = soundGlideEase(0.55f) - soundGlideEase(0.45f)
        val closing = soundGlideEase(1.0f) - soundGlideEase(0.9f)
        assertTrue("the start should be gentle", opening < middle)
        assertTrue("the end should be gentle", closing < middle)
    }

    @Test
    fun `easing never goes backwards and never leaves the track`() {
        var last = -1f
        for (step in 0..100) {
            val eased = soundGlideEase(step / 100f)
            assertTrue(eased >= last)
            assertTrue(eased in 0f..1f)
            last = eased
        }
    }

    @Test
    fun `easing clamps a fraction that is out of range`() {
        assertEquals(0f, soundGlideEase(-2f), 0.0001f)
        assertEquals(1f, soundGlideEase(9f), 0.0001f)
    }

    @Test
    fun `between two sounding rooms the preset is kept until the slide lands`() {
        val big = SoundSnapshot(
            reverb = ReverbState.fromPreset(ReverbPreset.LARGE_HALL).copy(enabled = true).sanitized()
        )
        val small = SoundSnapshot(
            reverb = ReverbState.fromPreset(ReverbPreset.SMALL_ROOM).copy(enabled = true).sanitized()
        )
        // Halfway, the room you can hear is still the one you were in: switching algorithm here
        // would put the engine in the small room while it still held the hall's size and tail.
        assertEquals(ReverbPreset.LARGE_HALL, big.blendTo(small, 0.5f).reverb.preset)
        assertEquals(ReverbPreset.LARGE_HALL, big.blendTo(small, 0.99f).reverb.preset)
        // And it does arrive.
        assertEquals(ReverbPreset.SMALL_ROOM, big.blendTo(small, 1f).reverb.preset)
    }

    @Test
    fun `coming from silence the target room is taken at once`() {
        val dry = SoundSnapshot(reverb = ReverbState.fromPreset(ReverbPreset.OFF).sanitized())
        val small = SoundSnapshot(
            reverb = ReverbState.fromPreset(ReverbPreset.SMALL_ROOM).copy(enabled = true).sanitized()
        )
        // Nothing to keep, so the room is the target's from the first frame and only the mix climbs.
        val early = dry.blendTo(small, 0.05f)
        assertEquals(ReverbPreset.SMALL_ROOM, early.reverb.preset)
        assertTrue(early.reverb.wetDryMix < small.reverb.wetDryMix)
    }
}
