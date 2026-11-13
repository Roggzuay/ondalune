package io.github.roggzuay.ondalune

import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.audio.FlacFrames
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A 0.25 s 24-bit / 96 kHz stereo FLAC of 1024-sample frames (24, the last one short) with no seek table. */
@RunWith(RobolectricTestRunner::class)
class FlacFramesTest {
    private val file = File(javaClass.classLoader!!.getResource("hires_24_96.flac")!!.toURI())
    private fun open() = FlacFrames.open(ApplicationProvider.getApplicationContext(), file.absolutePath)

    @Test
    fun `every frame is read once, in order, and together they are the whole audio`() {
        val flac = open()!!
        val buffer = ByteBuffer.allocate(64 shl 10)
        var frames = 0
        var bytes = 0L
        var lastTime = -1L
        while (true) {
            val n = flac.readSampleData(buffer, 0)
            if (n < 0) break
            assertTrue(flac.sampleTime > lastTime)
            assertEquals(frames * 1024L * 1_000_000L / 96000, flac.sampleTime)
            // Each frame starts with the FLAC sync code.
            assertEquals(0xFF, buffer.get(0).toInt() and 0xFF)
            lastTime = flac.sampleTime
            bytes += n
            frames++
            flac.advance()
        }
        assertEquals(24, frames)
        // Everything after the metadata (STREAMINFO, a comment and 8 KB of padding, 8286 bytes).
        assertEquals(file.length() - 8286, bytes)
        flac.release()
    }

    @Test
    fun `seeking lands on the frame that holds the time`() {
        val flac = open()!!
        for (ms in longArrayOf(0, 100, 37, 200, 249, 5)) {
            flac.seekTo(ms * 1000)
            val frameUs = 1024L * 1_000_000L / 96000
            assertTrue("seek to $ms ms gave ${flac.sampleTime}", flac.sampleTime <= ms * 1000 && flac.sampleTime > ms * 1000 - frameUs)
            assertTrue(flac.readSampleData(ByteBuffer.allocate(64 shl 10), 0) > 0)
        }
        flac.release()
    }

    @Test
    fun `a 16-bit FLAC or another file is left to Android's extractor`() {
        val other = File.createTempFile("not", ".flac").apply { writeBytes(ByteArray(64)); deleteOnExit() }
        assertNull(FlacFrames.open(ApplicationProvider.getApplicationContext(), other.absolutePath))
    }
}
