package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.OndaluneVinylDsp
import io.github.roggzuay.ondalune.data.model.VinylMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vinyl mode puts a record under the music. Off it must do nothing at all; on it must hiss,
 * crackle, drift and roll both ends off, and never shout over the song.
 */
class VinylModeTest {
    private val rate = 48_000

    private fun tone(frames: Int, hz: Double = 440.0, amplitude: Float = 0.5f, startFrame: Int = 0): FloatArray {
        val buf = FloatArray(frames * 2)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2.0 * PI * hz * (startFrame + f) / rate)).toFloat()
            buf[f * 2] = v
            buf[f * 2 + 1] = v
        }
        return buf
    }

    private fun silence(frames: Int) = FloatArray(frames * 2)

    private fun rms(buf: FloatArray): Float {
        var sum = 0.0
        for (v in buf) sum += v.toDouble() * v
        return sqrt(sum / buf.size).toFloat()
    }

    private fun dsp(mode: VinylMode) = OndaluneVinylDsp().apply { this.mode = mode }

    /** Runs a few chunks so the filters and the delay line settle, then returns the last one. */
    private fun settled(mode: VinylMode, frames: Int = 2048, chunks: Int = 6, signal: Boolean = true): FloatArray {
        val d = dsp(mode)
        var buf = FloatArray(0)
        var at = 0
        repeat(chunks) {
            buf = if (signal) tone(frames, startFrame = at) else silence(frames)
            d.process(buf, frames, rate)
            at += frames
        }
        return buf
    }

    @Test
    fun `off, not one sample is touched`() {
        val d = dsp(VinylMode.OFF)
        val buf = tone(512)
        val before = buf.copyOf()
        d.process(buf, 512, rate)
        assertTrue(before.contentEquals(buf))
    }

    @Test
    fun `off, silence stays perfectly silent`() {
        val d = dsp(VinylMode.OFF)
        val buf = silence(512)
        d.process(buf, 512, rate)
        assertTrue(buf.all { it == 0f })
    }

    @Test
    fun `on, a silent groove still hisses`() {
        val quiet = rms(settled(VinylMode.CLASSIC, signal = false))
        assertTrue("the groove was silent: $quiet", quiet > 0.0001f)
    }

    @Test
    fun `a worn record hisses louder than a new one`() {
        val light = rms(settled(VinylMode.LIGHT, signal = false))
        val worn = rms(settled(VinylMode.WORN, signal = false))
        assertTrue("light=$light worn=$worn", worn > light * 1.5f)
    }

    @Test
    fun `the groove never drowns the music`() {
        val music = rms(tone(2048))
        for (mode in VinylMode.entries - VinylMode.OFF) {
            val groove = rms(settled(mode, signal = false))
            assertTrue("$mode: groove=$groove music=$music", groove < music * 0.15f)
        }
    }

    @Test
    fun `the song is still the song underneath`() {
        val out = settled(VinylMode.CLASSIC)
        // A 440 Hz tone at half scale comes out at roughly the same level, not swallowed or doubled.
        val level = rms(out)
        assertTrue("came out at $level", level > 0.20f && level < 0.45f)
    }

    @Test
    fun `nothing ever clips or goes to nonsense`() {
        for (mode in VinylMode.entries) {
            val d = dsp(mode)
            val buf = tone(4096, amplitude = 1.0f)
            d.process(buf, 4096, rate)
            assertTrue("$mode", buf.all { it.isFinite() && abs(it) <= 1f })
        }
    }

    @Test
    fun `the needle lands louder than the groove that follows it`() {
        val d = dsp(VinylMode.CLASSIC)
        // Settle first, so what we measure is the drop and not the filters filling.
        repeat(4) { d.process(silence(2048), 2048, rate) }
        d.dropNeedle()
        val drop = silence(2048)
        d.process(drop, 2048, rate)
        val after = silence(2048)
        repeat(6) { d.process(after, 2048, rate) }
        assertTrue("drop=${rms(drop)} after=${rms(after)}", rms(drop) > rms(after) * 1.3f)
    }

    /**
     * Both real callers ask for the needle before the first buffer of that mode: switching vinyl
     * on mid-song, and starting a song. It used to be a countdown set from a length that is only
     * worked out once a buffer has been seen, so on the very first play there was no landing at
     * all --- only the pop.
     */
    @Test
    fun `the needle lands on the very first buffer, before any has been processed`() {
        val d = dsp(VinylMode.WORN)
        d.dropNeedle()
        val first = silence(2048)
        d.process(first, 2048, rate)
        val later = silence(2048)
        repeat(8) { d.process(later, 2048, rate) }
        assertTrue("first=${rms(first)} later=${rms(later)}", rms(first) > rms(later) * 1.3f)
    }

    @Test
    fun `the needle does nothing when vinyl is off`() {
        val d = dsp(VinylMode.OFF)
        d.dropNeedle()
        val buf = silence(1024)
        d.process(buf, 1024, rate)
        assertTrue(buf.all { it == 0f })
    }

    @Test
    fun `the top is rolled off, as a record rolls it off`() {
        val d = dsp(VinylMode.WORN)
        var at = 0
        var buf = FloatArray(0)
        repeat(6) {
            buf = tone(2048, hz = 18_000.0, startFrame = at)
            d.process(buf, 2048, rate)
            at += 2048
        }
        // Compare with the same tone well inside the record's range.
        val d2 = dsp(VinylMode.WORN)
        var at2 = 0
        var mid = FloatArray(0)
        repeat(6) {
            mid = tone(2048, hz = 1_000.0, startFrame = at2)
            d2.process(mid, 2048, rate)
            at2 += 2048
        }
        assertTrue("18 kHz=${rms(buf)} 1 kHz=${rms(mid)}", rms(buf) < rms(mid) * 0.7f)
    }

    @Test
    fun `the deepest bass is rolled off too`() {
        val d = dsp(VinylMode.WORN)
        var at = 0
        var low = FloatArray(0)
        repeat(8) {
            low = tone(2048, hz = 20.0, startFrame = at)
            d.process(low, 2048, rate)
            at += 2048
        }
        val d2 = dsp(VinylMode.WORN)
        var at2 = 0
        var mid = FloatArray(0)
        repeat(8) {
            mid = tone(2048, hz = 1_000.0, startFrame = at2)
            d2.process(mid, 2048, rate)
            at2 += 2048
        }
        assertTrue("20 Hz=${rms(low)} 1 kHz=${rms(mid)}", rms(low) < rms(mid) * 0.9f)
    }

    @Test
    fun `the same record sounds the same twice`() {
        val a = silence(2048)
        val b = silence(2048)
        OndaluneVinylDsp(seed = 99L).apply { mode = VinylMode.CLASSIC }.process(a, 2048, rate)
        OndaluneVinylDsp(seed = 99L).apply { mode = VinylMode.CLASSIC }.process(b, 2048, rate)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `a different record sounds different`() {
        val a = silence(2048)
        val b = silence(2048)
        OndaluneVinylDsp(seed = 1L).apply { mode = VinylMode.CLASSIC }.process(a, 2048, rate)
        OndaluneVinylDsp(seed = 2L).apply { mode = VinylMode.CLASSIC }.process(b, 2048, rate)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `lifting the needle leaves nothing behind`() {
        val d = dsp(VinylMode.WORN)
        d.process(tone(2048), 2048, rate)
        d.mode = VinylMode.OFF
        val buf = silence(2048)
        d.process(buf, 2048, rate)
        assertTrue(buf.all { it == 0f })
    }

    @Test
    fun `it works at every sample rate without falling over`() {
        for (sampleRate in listOf(44_100, 48_000, 96_000, 192_000)) {
            val d = dsp(VinylMode.CLASSIC)
            val buf = tone(1024)
            d.process(buf, 1024, sampleRate)
            assertTrue("$sampleRate", buf.all { it.isFinite() })
        }
    }

    @Test
    fun `a tiny buffer is handled without reading past it`() {
        val d = dsp(VinylMode.CLASSIC)
        val buf = FloatArray(4)
        d.process(buf, 2, rate)
        assertTrue(buf.all { it.isFinite() })
    }

    @Test
    fun `a frame count larger than the buffer is clamped, not crashed`() {
        val d = dsp(VinylMode.CLASSIC)
        val buf = FloatArray(16)
        d.process(buf, 9_999, rate)
        assertTrue(buf.all { it.isFinite() })
    }

    /** Ten minutes of 48 kHz stereo through the record, inside a budget a slow machine can meet. */
    @Test
    fun `a whole side of a record stays inside its budget`() {
        val d = dsp(VinylMode.WORN)
        val frames = 1024
        val chunks = (rate * 600) / frames
        repeat(200) { d.process(tone(frames), frames, rate) }
        val buf = tone(frames)
        val startNs = System.nanoTime()
        repeat(chunks) { d.process(buf, frames, rate) }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("ten minutes of vinyl took ${elapsedMs}ms", elapsedMs < 6_000L)
        assertEquals(frames * 2, buf.size)
    }
}
