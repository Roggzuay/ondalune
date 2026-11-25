package io.github.roggzuay.ondalune

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The backdrop that moves with the music reads the audio on its way out. It has to hear the
 * difference between a bass note and a cymbal, come to rest in silence, and cost nothing at all
 * when no one is looking at it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioLevelsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engine = OndalunePlaybackRuntime.getOrCreateEngine(app, OpenSubsonicClient())
    private val rate = 48_000
    private val levels = FloatArray(3)

    @Before
    fun rest() {
        engine.levelsWanted = false
        engine.clearAudioLevels()
    }

    private fun tone(hz: Double, frames: Int, amplitude: Float = 0.5f, startFrame: Int = 0): FloatArray {
        val buf = FloatArray(frames * 2)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2.0 * PI * hz * (startFrame + f) / rate)).toFloat()
            buf[f * 2] = v
            buf[f * 2 + 1] = v
        }
        return buf
    }

    /** Runs a tone through long enough for the envelope to settle, and returns low, mid, high. */
    private fun settle(hz: Double, chunks: Int = 60, frames: Int = 512): FloatArray {
        var at = 0
        repeat(chunks) {
            engine.measureLevels(tone(hz, frames, startFrame = at), frames, rate)
            at += frames
        }
        engine.audioLevels(levels)
        return levels
    }

    @Test
    fun `a bass note is heard in the low band, not the top`() {
        val (low, _, high) = settle(60.0).toList()
        assertTrue("low=$low", low > 0.5f)
        assertTrue("high=$high should stay quiet, was $high", high < low)
    }

    @Test
    fun `a cymbal is heard at the top, not in the bass`() {
        val (low, _, high) = settle(9_000.0).toList()
        assertTrue("high=$high", high > 0.4f)
        assertTrue("low=$low should stay quiet", low < high)
    }

    @Test
    fun `a note in the middle lands in the middle band`() {
        val l = settle(900.0)
        assertTrue("mid=${l[1]} should beat low=${l[0]}", l[1] > l[0])
    }

    @Test
    fun `silence brings the picture to rest`() {
        settle(60.0)
        val quiet = FloatArray(1024)
        repeat(200) { engine.measureLevels(quiet, 512, rate) }
        engine.audioLevels(levels)
        assertTrue("levels should fall away, got ${levels.toList()}", levels.all { it < 0.02f })
    }

    @Test
    fun `levels never leave zero to one, however loud the music`() {
        repeat(80) { engine.measureLevels(tone(120.0, 512, amplitude = 1.0f), 512, rate) }
        engine.audioLevels(levels)
        assertTrue(levels.toList().toString(), levels.all { it in 0f..1f })
    }

    @Test
    fun `clearing puts every band back to nothing`() {
        settle(60.0)
        engine.clearAudioLevels()
        engine.audioLevels(levels)
        assertEquals(0f, levels[0], 0f)
        assertEquals(0f, levels[1], 0f)
        assertEquals(0f, levels[2], 0f)
    }

    @Test
    fun `a short buffer is measured without reading past it`() {
        engine.measureLevels(FloatArray(8), 4, rate)
        engine.audioLevels(levels)
        assertTrue(levels.all { it.isFinite() })
    }

    /**
     * This runs on the audio thread. A decoder reporting more frames than the buffer holds must
     * be clamped, not walked past: the same guard the rest of the pipeline already had.
     */
    @Test
    fun `a frame count larger than the buffer is clamped, not crashed`() {
        engine.measureLevels(FloatArray(16), 9_999, rate)
        engine.audioLevels(levels)
        assertTrue(levels.all { it.isFinite() })
    }

    @Test
    fun `a nonsense sample rate is refused rather than dividing by it`() {
        engine.measureLevels(FloatArray(64), 32, 0)
        engine.audioLevels(levels)
        assertTrue(levels.all { it.isFinite() })
    }

    /**
     * The budget: ten minutes of 48 kHz stereo, measured, in well under the time that audio takes
     * to play. The real cost is a fraction of this; the headroom is for a slow CI machine.
     */
    @Test
    fun `measuring ten minutes of audio stays inside its budget`() {
        val frames = 1024
        val chunks = (rate * 600) / frames
        val buf = tone(440.0, frames)
        // Warm the JIT so the budget measures the code, not the first run through it.
        repeat(200) { engine.measureLevels(buf, frames, rate) }
        val startNs = System.nanoTime()
        repeat(chunks) { engine.measureLevels(buf, frames, rate) }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("measuring 10 minutes of audio took ${elapsedMs}ms", elapsedMs < 1_500L)
    }


    /**
     * The reason the backdrop looked switched off: measured straight, a band of ordinary music
     * sits at a few per cent, so a scene driven from it moved by less than a pixel. Each band is
     * now scaled against the loudest it has been lately, so what the picture gets is the shape of
     * the music rather than its mastering.
     */
    @Test
    fun `music that gets louder and quieter swings the band across its range`() {
        val frames = 512
        var at = 0
        val seen = mutableListOf<Float>()
        // Four loud bars and four quiet ones, twice over.
        repeat(2) {
            repeat(40) {
                engine.measureLevels(tone(60.0, frames, amplitude = 0.7f, startFrame = at), frames, rate)
                at += frames
            }
            engine.audioLevels(levels)
            seen += levels[0]
            repeat(40) {
                engine.measureLevels(tone(60.0, frames, amplitude = 0.02f, startFrame = at), frames, rate)
                at += frames
            }
            engine.audioLevels(levels)
            seen += levels[0]
        }
        val swing = seen.max() - seen.min()
        assertTrue("the low band only swung $swing across loud and quiet passages: $seen", swing > 0.5f)
    }

    @Test
    fun `quiet music still moves the picture, instead of sitting flat`() {
        val frames = 512
        var at = 0
        // A recording mastered quietly: nothing here approaches full scale.
        repeat(80) {
            engine.measureLevels(tone(60.0, frames, amplitude = 0.05f, startFrame = at), frames, rate)
            at += frames
        }
        engine.audioLevels(levels)
        assertTrue("a quiet record left the low band at ${levels[0]}", levels[0] > 0.5f)
    }

    @Test
    fun `silence is not amplified into a light show`() {
        val frames = 512
        repeat(200) { engine.measureLevels(FloatArray(frames * 2), frames, rate) }
        engine.audioLevels(levels)
        assertTrue("silence read as ${levels.toList()}", levels.all { it < 0.02f })
    }


    // ---- The bands have to mean something next to each other ----

    @Test
    fun `a band ten times louder than another reads louder, not the same`() {
        // Scaling each band by its own recent peak made every band read 1.0, whatever the
        // spectrum: the picture then said "loud" rather than anything about the sound.
        val frames = 1024
        var at = 0
        repeat(80) {
            val buf = tone(60.0, frames, amplitude = 0.6f, startFrame = at)
            // A little top end, well below the bass.
            for (f in 0 until frames) {
                val v = (0.03 * kotlin.math.sin(2.0 * PI * 9_000.0 * (at + f) / rate)).toFloat()
                buf[f * 2] += v
                buf[f * 2 + 1] += v
            }
            engine.measureLevels(buf, frames, rate)
            at += frames
        }
        engine.audioLevels(levels)
        assertTrue("low=${levels[0]} high=${levels[2]} should be far apart", levels[0] > levels[2] * 3f)
    }

    @Test
    fun `a quiet song after a loud one does not sit motionless`() {
        val frames = 1024
        var at = 0
        repeat(120) {
            engine.measureLevels(tone(60.0, frames, amplitude = 0.7f, startFrame = at), frames, rate)
            at += frames
        }
        // A new song starts: the engine clears the levels, as playTrack does.
        engine.clearAudioLevels()
        repeat(60) {
            engine.measureLevels(tone(60.0, frames, amplitude = 0.03f, startFrame = at), frames, rate)
            at += frames
        }
        engine.audioLevels(levels)
        assertTrue("a quiet song was left at ${levels[0]}", levels[0] > 0.5f)
    }

    @Test
    fun `the picture reacts the same at every sample rate`() {
        fun settleAt(sampleRate: Int): Float {
            engine.clearAudioLevels()
            val frames = 1024
            var at = 0
            // The same half second of music, whatever the rate.
            val buffers = (sampleRate / 2) / frames
            repeat(buffers) {
                val buf = FloatArray(frames * 2)
                for (f in 0 until frames) {
                    val v = (0.5 * kotlin.math.sin(2.0 * PI * 60.0 * (at + f) / sampleRate)).toFloat()
                    buf[f * 2] = v
                    buf[f * 2 + 1] = v
                }
                engine.measureLevels(buf, frames, sampleRate)
                at += frames
            }
            engine.audioLevels(levels)
            return levels[0]
        }
        val cd = settleAt(44_100)
        val hiRes = settleAt(192_000)
        assertTrue("44.1k settled at $cd, 192k at $hiRes", kotlin.math.abs(cd - hiRes) < 0.2f)
    }

    @Test
    fun `one bad sample does not poison the bands for ever`() {
        val frames = 512
        val poisoned = tone(60.0, frames, amplitude = 0.5f)
        poisoned[10] = Float.NaN
        engine.measureLevels(poisoned, frames, rate)
        var at = frames
        repeat(60) {
            engine.measureLevels(tone(60.0, frames, amplitude = 0.5f, startFrame = at), frames, rate)
            at += frames
        }
        engine.audioLevels(levels)
        assertTrue("the bands died at ${levels.toList()}", levels[0] > 0.5f)
        assertTrue(levels.all { it.isFinite() })
    }
}
