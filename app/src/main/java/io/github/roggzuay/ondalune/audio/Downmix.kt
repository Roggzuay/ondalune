package io.github.roggzuay.ondalune.audio

import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Folds multichannel 16-bit frames down to stereo the way ITU-R BS.775 describes: the centre and
 * the surrounds join both or one side 3 dB down, the LFE is left out, and every gain is scaled so
 * that a full-scale signal on all channels can't clip. Mono and stereo pass through unchanged, so
 * the ordinary paths stay bit-for-bit what they were. Pure arithmetic, no Android.
 *
 * [channelMask] is the decoder's `KEY_CHANNEL_MASK` in `AudioFormat.CHANNEL_OUT_*` bits (the i-th
 * channel of a frame is the i-th set bit); 0 or a mask that doesn't match [channels] means the
 * standard Android order for that count (5.1 = FL FR FC LFE BL BR, 7.1 adds SL SR). A mask in the
 * native layout (front left = 0x1, which some decoders report) is shifted into the Java one.
 */
internal class Downmix(val channels: Int, channelMask: Int = 0) {
    private val gainL = FloatArray(channels)
    private val gainR = FloatArray(channels)

    init {
        val positions = positionsOf(channels, channelMask)
        for (c in 0 until channels) {
            val (l, r) = gainsOf(positions[c])
            gainL[c] = l
            gainR[c] = r
        }
        val loudest = max(gainL.sum(), gainR.sum())
        if (loudest > 1f) {
            for (c in 0 until channels) {
                gainL[c] /= loudest
                gainR[c] /= loudest
            }
        }
    }

    /** Mono and stereo input copy straight through, as before any downmix existed. */
    val isPassThrough: Boolean get() = channels <= 2

    /** Reads [frames] interleaved frames from [src] and writes stereo frames to [dst] from frame [dstOffset]. */
    fun toStereo(src: ShortBuffer, frames: Int, dst: ShortArray, dstOffset: Int) {
        if (channels == 1) {
            for (f in 0 until frames) {
                val s = src.get(f)
                dst[(dstOffset + f) * 2] = s
                dst[(dstOffset + f) * 2 + 1] = s
            }
            return
        }
        if (channels == 2) {
            for (f in 0 until frames) {
                dst[(dstOffset + f) * 2] = src.get(f * 2)
                dst[(dstOffset + f) * 2 + 1] = src.get(f * 2 + 1)
            }
            return
        }
        for (f in 0 until frames) {
            var l = 0f
            var r = 0f
            val base = f * channels
            for (c in 0 until channels) {
                val s = src.get(base + c).toFloat()
                l += s * gainL[c]
                r += s * gainR[c]
            }
            dst[(dstOffset + f) * 2] = l.roundToInt().coerceIn(-32768, 32767).toShort()
            dst[(dstOffset + f) * 2 + 1] = r.roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** As the 16-bit [toStereo], for decoders that put out float samples (full scale = ±1). */
    fun toStereo(src: FloatBuffer, frames: Int, dst: ShortArray, dstOffset: Int) {
        for (f in 0 until frames) {
            var l: Float
            var r: Float
            when (channels) {
                1 -> {
                    l = src.get(f)
                    r = l
                }
                2 -> {
                    l = src.get(f * 2)
                    r = src.get(f * 2 + 1)
                }
                else -> {
                    l = 0f
                    r = 0f
                    val base = f * channels
                    for (c in 0 until channels) {
                        val s = src.get(base + c)
                        l += s * gainL[c]
                        r += s * gainR[c]
                    }
                }
            }
            dst[(dstOffset + f) * 2] = toPcm16(l)
            dst[(dstOffset + f) * 2 + 1] = toPcm16(r)
        }
    }

    companion object {
        private fun toPcm16(sample: Float): Short =
            if (sample.isNaN()) 0 else (sample.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()

        // AudioFormat.CHANNEL_OUT_* values, kept here so the arithmetic needs no Android classes.
        const val FRONT_LEFT = 0x4
        const val FRONT_RIGHT = 0x8
        const val FRONT_CENTER = 0x10
        const val LOW_FREQUENCY = 0x20
        const val BACK_LEFT = 0x40
        const val BACK_RIGHT = 0x80
        const val FRONT_LEFT_OF_CENTER = 0x100
        const val FRONT_RIGHT_OF_CENTER = 0x200
        const val BACK_CENTER = 0x400
        const val SIDE_LEFT = 0x800
        const val SIDE_RIGHT = 0x1000
        const val TOP_CENTER = 0x2000
        const val TOP_FRONT_LEFT = 0x4000
        const val TOP_FRONT_CENTER = 0x8000
        const val TOP_FRONT_RIGHT = 0x10000
        const val TOP_BACK_LEFT = 0x20000
        const val TOP_BACK_CENTER = 0x40000
        const val TOP_BACK_RIGHT = 0x80000
        const val TOP_SIDE_LEFT = 0x100000
        const val TOP_SIDE_RIGHT = 0x200000
        const val BOTTOM_FRONT_LEFT = 0x400000
        const val BOTTOM_FRONT_CENTER = 0x800000
        const val BOTTOM_FRONT_RIGHT = 0x1000000
        const val LOW_FREQUENCY_2 = 0x2000000
        const val FRONT_WIDE_LEFT = 0x4000000
        const val FRONT_WIDE_RIGHT = 0x8000000

        /** A mono channel has no position bit; it goes to both sides at full level. */
        private const val MONO = 0

        private const val SIDE = 0.70710677f
        private const val BOTH = 0.70710677f
        private const val REAR_CENTRE = 0.5f

        /** Speaker position per channel index, from the mask when it fits, else the standard order. */
        fun positionsOf(channels: Int, mask: Int): IntArray {
            // The two lowest bits are no speaker in the Java layout but front left and right in the native one.
            val channelMask = if (mask and 0x3 != 0) mask shl 2 else mask
            if (channelMask != 0 && Integer.bitCount(channelMask) == channels) {
                val out = IntArray(channels)
                var i = 0
                var bit = 1
                while (i < channels && bit != 0) {
                    if (channelMask and bit != 0) out[i++] = bit
                    bit = bit shl 1
                }
                return out
            }
            return when (channels) {
                1 -> intArrayOf(MONO)
                2 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT)
                3 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER)
                4 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, BACK_LEFT, BACK_RIGHT)
                5 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER, BACK_LEFT, BACK_RIGHT)
                6 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER, LOW_FREQUENCY, BACK_LEFT, BACK_RIGHT)
                7 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER, LOW_FREQUENCY, BACK_LEFT, BACK_RIGHT, BACK_CENTER)
                8 -> intArrayOf(FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER, LOW_FREQUENCY, BACK_LEFT, BACK_RIGHT, SIDE_LEFT, SIDE_RIGHT)
                // Beyond what Android names: the first two are the front pair, the rest is unknown and left out.
                else -> IntArray(channels) { if (it == 0) FRONT_LEFT else if (it == 1) FRONT_RIGHT else -1 }
            }
        }

        /** (left, right) gain of one speaker position before normalisation. */
        fun gainsOf(position: Int): Pair<Float, Float> = when (position) {
            MONO -> 1f to 1f
            FRONT_LEFT -> 1f to 0f
            FRONT_RIGHT -> 0f to 1f
            FRONT_CENTER -> BOTH to BOTH
            LOW_FREQUENCY, LOW_FREQUENCY_2 -> 0f to 0f
            BACK_LEFT, SIDE_LEFT, FRONT_LEFT_OF_CENTER, FRONT_WIDE_LEFT,
            TOP_FRONT_LEFT, TOP_BACK_LEFT, TOP_SIDE_LEFT, BOTTOM_FRONT_LEFT -> SIDE to 0f
            BACK_RIGHT, SIDE_RIGHT, FRONT_RIGHT_OF_CENTER, FRONT_WIDE_RIGHT,
            TOP_FRONT_RIGHT, TOP_BACK_RIGHT, TOP_SIDE_RIGHT, BOTTOM_FRONT_RIGHT -> 0f to SIDE
            BACK_CENTER, TOP_CENTER, TOP_FRONT_CENTER, TOP_BACK_CENTER, BOTTOM_FRONT_CENTER -> REAR_CENTRE to REAR_CENTRE
            else -> 0f to 0f
        }
    }
}
