package io.github.roggzuay.ondalune.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Decoded audio as interleaved stereo 16-bit frames, read in exact frame counts so two songs can
 * be mixed sample-for-sample during a crossfade. Not thread-safe: one reader at a time.
 */
internal interface PcmSource {
    val sampleRate: Int
    /** Frames in the whole song, or 0 when the container doesn't say. */
    val durationFrames: Long
    /** Song position of the next frame [read] returns. */
    val positionFrames: Long
    /** Reads up to [frames] frames into [dst] starting at frame [offset]; returns fewer only at the end. */
    fun read(dst: ShortArray, offset: Int, frames: Int): Int
    fun seekTo(ms: Long)
    fun release()
}

/** 16-bit PCM WAV file (offline demo masters). */
internal class WavPcmSource(file: File) : PcmSource {
    private val raf = RandomAccessFile(file, "r")
    private val channels: Int
    override val sampleRate: Int
    override val durationFrames: Long
    override var positionFrames = 0L
        private set
    private var bytes = ByteArray(0)

    init {
        require(raf.length() > 44L) { "WAV source too short" }
        raf.seek(22)
        channels = java.lang.Short.reverseBytes(raf.readShort()).toInt().coerceIn(1, 2)
        sampleRate = Integer.reverseBytes(raf.readInt()).coerceIn(8000, 96000)
        durationFrames = ((raf.length() - 44L) / (channels * 2)).coerceAtLeast(1L)
        raf.seek(44L)
    }

    override fun read(dst: ShortArray, offset: Int, frames: Int): Int {
        val bytesPerFrame = channels * 2
        val want = frames * bytesPerFrame
        if (bytes.size < want) bytes = ByteArray(want)
        var filled = 0
        while (filled < want) {
            val n = raf.read(bytes, filled, want - filled)
            if (n <= 0) break
            filled += n
        }
        val got = filled / bytesPerFrame
        for (f in 0 until got) {
            val b = f * bytesPerFrame
            val left = ((bytes[b + 1].toInt() shl 8) or (bytes[b].toInt() and 0xFF)).toShort()
            val right = if (channels == 2) ((bytes[b + 3].toInt() shl 8) or (bytes[b + 2].toInt() and 0xFF)).toShort() else left
            dst[(offset + f) * 2] = left
            dst[(offset + f) * 2 + 1] = right
        }
        positionFrames += got
        return got
    }

    override fun seekTo(ms: Long) {
        positionFrames = (ms * sampleRate / 1000L).coerceIn(0L, durationFrames)
        raf.seek(44L + positionFrames * channels * 2)
    }

    override fun release() {
        runCatching { raf.close() }
    }
}

/** Any compressed file or HTTP stream Android can decode (FLAC, MP3, AAC, Opus, Vorbis, WAV). */
internal class CodecPcmSource private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    rate: Int,
    channelCount: Int,
    private val durationUs: Long
) : PcmSource {
    override var sampleRate = rate
        private set
    private var channels = channelCount
    private val info = MediaCodec.BufferInfo()
    private var inputEos = false
    private var outputEos = false

    // Decoded but not yet read stereo samples, plus the song time of the first one.
    private var fifo = ShortArray(16384)
    private var head = 0
    private var size = 0
    private var anchorUs = 0L
    private var consumedSinceAnchor = 0L

    override val durationFrames: Long
        get() = if (durationUs > 0L) durationUs * sampleRate / 1_000_000L else 0L

    override val positionFrames: Long
        get() = anchorUs * sampleRate / 1_000_000L + consumedSinceAnchor

    override fun read(dst: ShortArray, offset: Int, frames: Int): Int {
        var got = 0
        while (got < frames) {
            if (size == 0) {
                if (outputEos) break
                pump()
                continue
            }
            val n = min(frames - got, size / 2)
            System.arraycopy(fifo, head, dst, (offset + got) * 2, n * 2)
            head += n * 2
            size -= n * 2
            got += n
            consumedSinceAnchor += n
        }
        return got
    }

    override fun seekTo(ms: Long) {
        extractor.seekTo(ms * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        codec.flush()
        inputEos = false
        outputEos = false
        head = 0
        size = 0
        anchorUs = ms * 1000L
        consumedSinceAnchor = 0L
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    /** Feeds one input buffer and drains at most one output buffer. */
    private fun pump() {
        if (!inputEos) {
            val inIndex = codec.dequeueInputBuffer(5_000L)
            if (inIndex >= 0) {
                val inBuf = codec.getInputBuffer(inIndex)
                val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputEos = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val outIndex = codec.dequeueOutputBuffer(info, 5_000L)
        when {
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                // The decoder's real output can differ from the container (e.g. HE-AAC doubles the rate).
                val f = codec.outputFormat
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            outIndex >= 0 -> {
                val outBuf = codec.getOutputBuffer(outIndex)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val shorts = outBuf.order(ByteOrder.nativeOrder()).asShortBuffer()
                    val frameCount = (info.size / 2) / channels
                    if (size == 0) {
                        head = 0
                        anchorUs = info.presentationTimeUs
                        consumedSinceAnchor = 0L
                    } else if (head > 0) {
                        System.arraycopy(fifo, head, fifo, 0, size)
                        head = 0
                    }
                    if (fifo.size < size + frameCount * 2) fifo = fifo.copyOf(size + frameCount * 2)
                    for (f in 0 until frameCount) {
                        val left = shorts.get(f * channels)
                        fifo[size + f * 2] = left
                        fifo[size + f * 2 + 1] = if (channels >= 2) shorts.get(f * channels + 1) else left
                    }
                    size += frameCount * 2
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true
            }
        }
    }

    companion object {
        /** Opens [path] and decodes the first audio so the real sample rate is known; null if unsupported. */
        fun open(path: String): CodecPcmSource? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(path)
                val trackIndex = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
                } ?: run {
                    extractor.release()
                    return null
                }
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(format, null, null, 0)
                codec.start()
                val source = CodecPcmSource(
                    extractor,
                    codec,
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100,
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 2,
                    if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                )
                var attempts = 0
                while (source.size == 0 && !source.outputEos && attempts++ < 400) source.pump()
                source
            } catch (_: Throwable) {
                runCatching { extractor.release() }
                null
            }
        }
    }
}

/** A song lined up in the mixer: its decoded audio and the ReplayGain it plays at. */
internal class QueuedSource(
    val track: SubsonicTrack,
    val source: PcmSource,
    /** Used when the container has no duration (some transcoded streams). */
    private val metadataDurationMs: Long,
    var gain: Float = 1f
) {
    val durationFrames: Long
        get() = source.durationFrames.takeIf { it > 0L } ?: (metadataDurationMs * source.sampleRate / 1000L)

    val remainingFrames: Long
        get() = (durationFrames - source.positionFrames).coerceAtLeast(0L)

    fun positionMs(): Long = source.positionFrames * 1000L / source.sampleRate
    fun durationMs(): Long = durationFrames * 1000L / source.sampleRate
}

/**
 * Pulls audio from the current song and, at its end, hands over to the next one: gapless (the next
 * song's first sample follows the last one directly) or an equal-power crossfade of
 * [crossfadeFrames]. Pure PCM logic with no Android playback, so transitions are unit-testable.
 */
internal class TransitionMixer(var current: QueuedSource) {
    var next: QueuedSource? = null
    var crossfadeFrames = 0
    var gapless = true

    private var fadePos = -1
    private var fadeLen = 0
    /** After a hand-over mid-fade, the new song keeps fading in for the rest of the fade. */
    private var rampPos = -1
    private var scratch = ShortArray(0)

    val isFading: Boolean get() = fadePos >= 0

    class Result {
        var frames = 0
        var advanced = false
        var ended = false
    }

    /** Called on seek: the fade restarts from scratch if the end comes round again. */
    fun cancelFade() {
        if (fadePos >= 0) next?.source?.seekTo(0L)
        fadePos = -1
        rampPos = -1
    }

    fun render(out: ShortArray, frames: Int, result: Result): Result {
        result.frames = 0
        result.advanced = false
        result.ended = false
        var written = 0
        while (written < frames) {
            var want = frames - written
            val upcoming = next
            if (fadePos < 0 && upcoming != null && crossfadeFrames > 0) {
                val remaining = current.remainingFrames
                if (remaining > crossfadeFrames) {
                    // Stop exactly where the fade should begin.
                    want = min(want.toLong(), remaining - crossfadeFrames).toInt()
                } else {
                    fadePos = 0
                    fadeLen = remaining.coerceAtLeast(1L).toInt()
                }
            }

            val n = current.source.read(out, written, want)
            applyGain(out, written, n) { i ->
                var g = current.gain
                if (fadePos >= 0) g *= fadeOut(fadePos + i, fadeLen)
                if (rampPos >= 0) g *= fadeIn(rampPos + i, fadeLen)
                g
            }
            if (rampPos >= 0) {
                rampPos += n
                if (rampPos >= fadeLen) rampPos = -1
            }
            if (fadePos >= 0 && upcoming != null && n > 0) {
                if (scratch.size < n * 2) scratch = ShortArray(n * 2)
                val m = upcoming.source.read(scratch, 0, n)
                for (i in 0 until m) {
                    val g = upcoming.gain * fadeIn(fadePos + i, fadeLen)
                    for (c in 0..1) {
                        val idx = (written + i) * 2 + c
                        out[idx] = clamp(out[idx] + scratch[i * 2 + c] * g)
                    }
                }
                fadePos += n
            }
            written += n

            if (n < want) {
                if (upcoming != null && (gapless || fadePos >= 0)) {
                    current.source.release()
                    current = upcoming
                    next = null
                    rampPos = if (fadePos in 0 until fadeLen) fadePos else -1
                    fadePos = -1
                    result.advanced = true
                } else {
                    result.ended = true
                    break
                }
            } else if (n == 0) {
                break
            }
        }
        result.frames = written
        return result
    }

    private inline fun applyGain(buf: ShortArray, offsetFrames: Int, frames: Int, gainAt: (Int) -> Float) {
        for (i in 0 until frames) {
            val g = gainAt(i)
            if (g == 1f) continue
            val idx = (offsetFrames + i) * 2
            buf[idx] = clamp(buf[idx] * g)
            buf[idx + 1] = clamp(buf[idx + 1] * g)
        }
    }

    private fun fadeOut(pos: Int, len: Int): Float = cos((pos.toFloat() / len).coerceIn(0f, 1f) * (PI / 2).toFloat())
    private fun fadeIn(pos: Int, len: Int): Float = sin((pos.toFloat() / len).coerceIn(0f, 1f) * (PI / 2).toFloat())
    private fun clamp(v: Float): Short = v.roundToInt().coerceIn(-32768, 32767).toShort()
}

/**
 * Linear gain for [track] under [mode], from the OpenSubsonic ReplayGain values. Positive gain is
 * capped by the stored peak so loud tracks never clip; songs without ReplayGain play unchanged.
 */
internal fun replayGainLinear(track: SubsonicTrack, mode: ReplayGainMode): Float {
    val (gainDb, peak) = when (mode) {
        ReplayGainMode.OFF -> return 1f
        ReplayGainMode.TRACK -> (track.rgTrackGain ?: track.rgAlbumGain) to (track.rgTrackPeak ?: track.rgAlbumPeak)
        ReplayGainMode.ALBUM -> (track.rgAlbumGain ?: track.rgTrackGain) to (track.rgAlbumPeak ?: track.rgTrackPeak)
    }
    if (gainDb == null) return 1f
    var linear = 10f.pow(gainDb / 20f)
    if (peak != null && peak > 0f) linear = min(linear, 1f / peak)
    return linear.coerceIn(0.05f, 4f)
}
