package io.github.roggzuay.ondalune.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns the PCM a decoder or a WAV file hands over into float samples, full scale = ±1, so a
 * 24-bit song keeps every bit on its way through the engine. Integer samples are divided by their
 * full scale (2^15 for 16-bit), the way Android converts them, so a 16-bit song at unity gain comes
 * out of the mixer exactly as it went in. Pure arithmetic, no Android.
 */
internal object PcmConvert {
    // AudioFormat.ENCODING_PCM_* values, kept here so the arithmetic needs no Android classes.
    const val PCM_16BIT = 2
    const val PCM_8BIT = 3
    const val PCM_FLOAT = 4
    const val PCM_24BIT_PACKED = 21
    const val PCM_32BIT = 22

    private const val SCALE_8 = 1f / 128f
    private const val SCALE_16 = 1f / 32768f
    private const val SCALE_24 = 1f / 8388608f
    private const val SCALE_32 = 1f / 2147483648f

    /** Bytes per sample of [encoding]; 0 for one this can't read. */
    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        PCM_8BIT -> 1
        PCM_16BIT -> 2
        PCM_24BIT_PACKED -> 3
        PCM_32BIT, PCM_FLOAT -> 4
        else -> 0
    }

    /** Bits of resolution [encoding] carries; float counts as 32. */
    fun bitsOf(encoding: Int): Int = bytesPerSample(encoding) * 8

    /**
     * Reads [count] samples of [encoding] from [src], starting at its position and in its byte
     * order, into [dst] from index [dstOffset]. A float sample that isn't a number becomes silence;
     * floats beyond ±1 are kept, since gain later on may bring them back.
     */
    fun toFloat(src: ByteBuffer, encoding: Int, count: Int, dst: FloatArray, dstOffset: Int) {
        val start = src.position()
        when (encoding) {
            PCM_16BIT -> {
                val s = src.asShortBuffer()
                for (i in 0 until count) dst[dstOffset + i] = s.get(i) * SCALE_16
            }
            PCM_FLOAT -> {
                src.asFloatBuffer().get(dst, dstOffset, count)
                for (i in dstOffset until dstOffset + count) if (!dst[i].isFinite()) dst[i] = 0f
            }
            PCM_32BIT -> {
                val s = src.asIntBuffer()
                for (i in 0 until count) dst[dstOffset + i] = s.get(i) * SCALE_32
            }
            PCM_24BIT_PACKED -> {
                val little = src.order() == ByteOrder.LITTLE_ENDIAN
                for (i in 0 until count) {
                    val p = start + i * 3
                    val lo = src.get(if (little) p else p + 2).toInt() and 0xFF
                    val mid = src.get(p + 1).toInt() and 0xFF
                    // The top byte keeps its sign, which carries into the 32-bit value.
                    val hi = src.get(if (little) p + 2 else p).toInt()
                    dst[dstOffset + i] = ((hi shl 16) or (mid shl 8) or lo) * SCALE_24
                }
            }
            PCM_8BIT -> for (i in 0 until count) dst[dstOffset + i] = ((src.get(start + i).toInt() and 0xFF) - 128) * SCALE_8
            else -> throw IllegalArgumentException("PCM encoding $encoding")
        }
    }
}
