package io.github.roggzuay.ondalune

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.CrossfeedLevel
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Crossfeed sends a little of each side to the other ear. It has to do nothing at all when it is
 * off, bring a hard-panned record back towards the middle when it is on, and never push the music
 * louder than it came in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CrossfeedTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engine = OndalunePlaybackRuntime.getOrCreateEngine(app, OpenSubsonicClient())
    private val rate = 48_000

    @Before
    fun dry() {
        engine.crossfeed = CrossfeedLevel.OFF
        engine.resetCrossfeed()
        engine.setOutputVolume(1f)
    }

    @After
    fun off() {
        engine.crossfeed = CrossfeedLevel.OFF
        engine.resetCrossfeed()
    }

    /** A tone on the left channel only, which is what crossfeed is for. */
    private fun hardLeft(frames: Int, hz: Double = 400.0, startFrame: Int = 0): FloatArray {
        val buf = FloatArray(frames * 2)
        for (f in 0 until frames) {
            buf[f * 2] = (0.6 * sin(2.0 * PI * hz * (startFrame + f) / rate)).toFloat()
            buf[f * 2 + 1] = 0f
        }
        return buf
    }

    private fun run(level: CrossfeedLevel, chunks: Int = 20, frames: Int = 512): Pair<Float, Float> {
        engine.crossfeed = level
        var at = 0
        var peakL = 0f
        var peakR = 0f
        repeat(chunks) {
            val buf = hardLeft(frames, startFrame = at)
            engine.processRealtimeStereoChunk(
                samples = buf,
                frameCount = frames,
                eqPreset = EqualizerPreset.FLAT_STUDIO,
                sampleRate = rate,
                reverbState = ReverbState()
            )
            // Ignore the first chunks while the delay line and the filter fill.
            if (it >= chunks / 2) {
                for (f in 0 until frames) {
                    peakL = maxOf(peakL, abs(buf[f * 2]))
                    peakR = maxOf(peakR, abs(buf[f * 2 + 1]))
                }
            }
            at += frames
        }
        return peakL to peakR
    }

    @Test
    fun `off, the silent ear stays silent`() {
        val (left, right) = run(CrossfeedLevel.OFF)
        assertTrue("left=$left", left > 0.5f)
        assertEquals(0f, right, 0.0001f)
    }

    @Test
    fun `on, the far ear hears some of the near one`() {
        val (left, right) = run(CrossfeedLevel.NATURAL)
        assertTrue("right=$right", right > 0.05f)
        assertTrue("the near ear should still lead: left=$left right=$right", left > right)
    }

    @Test
    fun `a stronger setting sends more across`() {
        val gentle = run(CrossfeedLevel.GENTLE).second
        engine.resetCrossfeed()
        val wide = run(CrossfeedLevel.WIDE).second
        assertTrue("gentle=$gentle wide=$wide", wide > gentle)
    }

    @Test
    fun `it never makes the music louder than it came in`() {
        for (level in CrossfeedLevel.entries) {
            engine.resetCrossfeed()
            val (left, right) = run(level)
            assertTrue("$level pushed the level to $left", left <= 0.61f)
            assertTrue("$level pushed the level to $right", right <= 0.61f)
        }
    }

    @Test
    fun `switching it off empties the line, so no tail is left behind`() {
        run(CrossfeedLevel.WIDE)
        engine.crossfeed = CrossfeedLevel.OFF
        val quiet = FloatArray(1024)
        engine.processRealtimeStereoChunk(
            samples = quiet,
            frameCount = 512,
            eqPreset = EqualizerPreset.FLAT_STUDIO,
            sampleRate = rate,
            reverbState = ReverbState()
        )
        assertTrue("silence came out as ${quiet.maxOf { abs(it) }}", quiet.all { abs(it) < 0.0001f })
    }

    @Test
    fun `every setting leaves finite audio at any sample rate`() {
        for (level in CrossfeedLevel.entries) {
            for (sampleRate in listOf(44_100, 48_000, 96_000, 192_000)) {
                engine.resetCrossfeed()
                engine.crossfeed = level
                val buf = hardLeft(256)
                engine.processRealtimeStereoChunk(
                    samples = buf,
                    frameCount = 256,
                    eqPreset = EqualizerPreset.FLAT_STUDIO,
                    sampleRate = sampleRate,
                    reverbState = ReverbState()
                )
                assertTrue("$level at $sampleRate", buf.all { it.isFinite() && abs(it) <= 1f })
            }
        }
    }
}
