package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.Downmix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownmixTest {
    /** Mixes 16-bit-scaled samples (as floats ×32768) and gives them back on the same scale, rounded. */
    private fun mix(channels: Int, mask: Int = 0, vararg frames: Short): ShortArray {
        val out = FloatArray(frames.size / channels * 2)
        Downmix(channels, mask).toStereo(FloatArray(frames.size) { frames[it] / 32768f }, frames.size / channels, out, 0)
        return ShortArray(out.size) { Math.round(out[it] * 32768f).coerceIn(-32768, 32767).toShort() }
    }

    @Test
    fun `stereo and mono are untouched`() {
        assertArrayEquals(shortArrayOf(1000, -2000, 32767, -32768), mix(2, 0, 1000, -2000, 32767, -32768))
        assertArrayEquals(shortArrayOf(1234, 1234, -32768, -32768), mix(1, 0, 1234, -32768))
        assertTrue(Downmix(2).isPassThrough)
        assertTrue(!Downmix(6).isPassThrough)
    }

    @Test
    fun `a 5_1 centre comes out equally on both sides`() {
        val out = mix(6, 0, 0, 0, 10000, 0, 0, 0)
        assertEquals(out[0], out[1])
        assertTrue(out[0] > 0)
    }

    @Test
    fun `a 5_1 left surround comes out on the left only`() {
        val out = mix(6, 0, 0, 0, 0, 0, 10000, 0)
        assertTrue(out[0] > 0)
        assertEquals(0, out[1].toInt())
    }

    @Test
    fun `the LFE is left out`() {
        assertArrayEquals(shortArrayOf(0, 0), mix(6, 0, 0, 0, 0, 32767, 0, 0))
    }

    @Test
    fun `full scale on every channel does not clip`() {
        val full = ShortArray(8) { 32767 }
        val out = mix(8, 0, *full)
        assertTrue(out[0] <= 32767 && out[1] <= 32767)
        // And it is as loud as the scaling allows: the front pair plus centre plus the two surrounds sum to one.
        assertTrue(out[0] > 32000 && out[1] > 32000)
        val negative = ShortArray(6) { -32768 }
        val outNeg = mix(6, 0, *negative)
        assertTrue(outNeg[0] >= -32768 && outNeg[1] >= -32768)
    }

    @Test
    fun `the decoder's channel mask decides where each channel sits`() {
        // 5.1(side): FL FR FC LFE SL SR. Channel 4 is a left side speaker here, not a back one, but still left.
        val side = Downmix.FRONT_LEFT or Downmix.FRONT_RIGHT or Downmix.FRONT_CENTER or Downmix.LOW_FREQUENCY or Downmix.SIDE_LEFT or Downmix.SIDE_RIGHT
        val out = mix(6, side, 0, 0, 0, 0, 0, 10000)
        assertEquals(0, out[0].toInt())
        assertTrue(out[1] > 0)
        // A mask that doesn't fit the channel count is ignored in favour of the standard order.
        val wrong = mix(6, Downmix.FRONT_LEFT or Downmix.FRONT_RIGHT, 0, 0, 0, 0, 10000, 0)
        assertTrue(wrong[0] > 0 && wrong[1].toInt() == 0)
    }

    @Test
    fun `a plain stereo pair inside 5_1 keeps its balance`() {
        val out = mix(6, 0, 20000, -20000, 0, 0, 0, 0)
        assertEquals(-out[0].toInt(), out[1].toInt())
        assertTrue(out[0] > 0)
    }

    @Test
    fun `float samples keep resolution finer than 16-bit`() {
        val tiny = 1f / 8388608f // one 24-bit step
        val out = FloatArray(4)
        Downmix(2).toStereo(floatArrayOf(tiny, -tiny, 1.5f, 0f), 2, out, 0)
        assertArrayEquals(floatArrayOf(tiny, -tiny, 1.5f, 0f), out, 0f)
        val centre = FloatArray(2)
        Downmix(6).toStereo(floatArrayOf(0f, 0f, tiny * 4, 0f, 0f, 0f), 1, centre, 0)
        assertEquals(centre[0], centre[1])
        assertTrue(centre[0] > 0f && centre[0] < tiny * 4)
    }

    @Test
    fun `a native-layout channel mask is read as the same speakers`() {
        // Native 5.1(side): FL=0x1 FR=0x2 FC=0x4 LFE=0x8 SL=0x200 SR=0x400; the last channel is the right side.
        val native = 0x1 or 0x2 or 0x4 or 0x8 or 0x200 or 0x400
        val out = mix(6, native, 0, 0, 0, 0, 0, 10000)
        assertEquals(0, out[0].toInt())
        assertTrue(out[1] > 0)
    }
}
