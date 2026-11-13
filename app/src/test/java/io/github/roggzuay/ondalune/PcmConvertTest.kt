package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.OndaluneReverbDspProcessor
import io.github.roggzuay.ondalune.audio.PcmConvert
import io.github.roggzuay.ondalune.audio.WavPcmSource
import io.github.roggzuay.ondalune.audio.onePoleCoeffAt
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConvertTest {
    private fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).also { it.flip() }

    private fun convert(buf: ByteBuffer, encoding: Int, count: Int) = FloatArray(count).also { PcmConvert.toFloat(buf, encoding, count, it, 0) }

    @Test
    fun `16-bit is divided by 2^15`() {
        val out = convert(le(8) { putShort(0); putShort(16384); putShort(-32768); putShort(32767) }, PcmConvert.PCM_16BIT, 4)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f, 32767f / 32768f), out, 0f)
    }

    @Test
    fun `24-bit packed keeps every step and its sign`() {
        fun put24(b: ByteBuffer, v: Int) { b.put(v.toByte()); b.put((v shr 8).toByte()); b.put((v shr 16).toByte()) }
        val values = intArrayOf(1, -1, 8388607, -8388608, 4194304, 0x123456)
        val out = convert(le(values.size * 3) { values.forEach { put24(this, it) } }, PcmConvert.PCM_24BIT_PACKED, values.size)
        for (i in values.indices) assertEquals(values[i] / 8388608f, out[i], 0f)
        // One 24-bit step is still there: finer than anything 16-bit can hold.
        assertTrue(out[0] > 0f && out[0] < 1f / 32768f)
    }

    @Test
    fun `32-bit integer and 8-bit unsigned scale to full range`() {
        val i32 = convert(le(12) { putInt(Int.MIN_VALUE); putInt(1 shl 30); putInt(256) }, PcmConvert.PCM_32BIT, 3)
        assertArrayEquals(floatArrayOf(-1f, 0.5f, 256f / 2147483648f), i32, 0f)
        val u8 = convert(le(3) { put(0); put(128.toByte()); put(192.toByte()) }, PcmConvert.PCM_8BIT, 3)
        assertArrayEquals(floatArrayOf(-1f, 0f, 0.5f), u8, 0f)
    }

    @Test
    fun `float passes through, overs kept, NaN silenced`() {
        val out = convert(le(16) { putFloat(0.25f); putFloat(-1.5f); putFloat(Float.NaN); putFloat(Float.POSITIVE_INFINITY) }, PcmConvert.PCM_FLOAT, 4)
        assertArrayEquals(floatArrayOf(0.25f, -1.5f, 0f, 0f), out, 0f)
    }

    @Test
    fun `conversion starts at the buffer position`() {
        val buf = le(6) { putShort(111); putShort(16384); putShort(-16384) }
        buf.position(2)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), convert(buf, PcmConvert.PCM_16BIT, 2), 0f)
        assertEquals(3, PcmConvert.bytesPerSample(PcmConvert.PCM_24BIT_PACKED))
        assertEquals(32, PcmConvert.bitsOf(PcmConvert.PCM_FLOAT))
    }

    /** A WAV file with a LIST chunk before the audio and a trailing one after, as taggers write them. */
    private fun wav(tag: Int, bits: Int, channels: Int, rate: Int, data: ByteArray): File {
        val f = File.createTempFile("pcm", ".wav").apply { deleteOnExit() }
        val list = "LISTxxxx".toByteArray()
        val b = ByteBuffer.allocate(12 + 24 + 8 + 4 + 8 + data.size + 8 + 4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(tag.toShort()).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * bits / 8).putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        b.put("LIST".toByteArray()).putInt(4).put(list, 0, 4)
        b.put("data".toByteArray()).putInt(data.size).put(data)
        b.put("LIST".toByteArray()).putInt(4).put(list, 0, 4)
        f.writeBytes(b.array().copyOf(b.position()))
        return f
    }

    @Test
    fun `a 24-bit 96 kHz WAV reads at full resolution and stops at its data`() {
        val data = ByteArray(4 * 6).also { d ->
            // Frames: (1 step, -1 step), (half, -half), (0, 0), (max, min)
            val v = intArrayOf(1, -1, 4194304, -4194304, 0, 0, 8388607, -8388608)
            for (i in v.indices) { d[i * 3] = v[i].toByte(); d[i * 3 + 1] = (v[i] shr 8).toByte(); d[i * 3 + 2] = (v[i] shr 16).toByte() }
        }
        val src = WavPcmSource(wav(1, 24, 2, 96000, data))
        assertEquals(96000, src.sampleRate)
        assertEquals(4L, src.durationFrames)
        assertEquals(24, src.stream.decodedBits)
        val out = FloatArray(16)
        assertEquals(4, src.read(out, 0, 8))
        assertEquals(1f / 8388608f, out[0], 0f)
        assertEquals(-0.5f, out[3], 0f)
        assertEquals(-1f, out[7], 0f)
        src.release()
    }

    @Test
    fun `16-bit mono and float WAVs read too`() {
        val mono = WavPcmSource(wav(1, 16, 1, 22050, byteArrayOf(0, 0x40)))
        val out = FloatArray(2)
        assertEquals(1, mono.read(out, 0, 1))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), out, 0f)
        val f = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.125f).putFloat(-0.75f).array()
        val float = WavPcmSource(wav(3, 32, 2, 48000, f))
        assertEquals(1, float.read(out, 0, 4))
        assertArrayEquals(floatArrayOf(0.125f, -0.75f), out, 0f)
    }

    @Test
    fun `filters keep their corner frequency at any rate`() {
        assertEquals(0.08f, onePoleCoeffAt(0.08f, 44100), 0f)
        // Corner in Hz of a one-pole coefficient: -ln(1 - a)·rate / 2π.
        fun cornerHz(a: Float, rate: Int) = -ln(1.0 - a) * rate / (2 * Math.PI)
        for (rate in intArrayOf(48000, 88200, 96000, 192000)) {
            assertEquals(cornerHz(0.08f, 44100), cornerHz(onePoleCoeffAt(0.08f, rate), rate), 1.0)
        }
        assertTrue(abs(onePoleCoeffAt(0.38f, 96000) - (1 - exp(ln(0.62) * 44100 / 96000)).toFloat()) < 1e-5f)
    }

    @Test
    fun `reverb tail lasts as long in seconds at 96 kHz as at 44_1 kHz`() {
        fun tailMs(rate: Int): Int {
            val dsp = OndaluneReverbDspProcessor(rate)
            dsp.updateParameters(ReverbState.fromPreset(ReverbPreset.LARGE_HALL), snapImmediately = true)
            val block = FloatArray(2 * rate / 100) // 10 ms
            for (i in 0 until 64) block[i] = 0.8f
            var last = 0
            for (ms in 0 until 3000 step 10) {
                dsp.processStereoInterleavedFloat(block)
                if (block.any { abs(it) > 1e-3f }) last = ms
                block.fill(0f)
            }
            return last
        }
        val base = tailMs(44100)
        assertTrue(base > 200)
        assertEquals(base.toDouble(), tailMs(96000).toDouble(), base * 0.15)
        // Switching rate rebuilds the delay lines for it.
        val dsp = OndaluneReverbDspProcessor(44100)
        dsp.setSampleRate(96000)
        assertEquals(96000, dsp.sampleRate)
    }
}
