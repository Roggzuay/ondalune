package io.github.roggzuay.ondalune.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.util.Log
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Decoded audio as interleaved stereo float frames (full scale = ±1), read in exact frame counts so
 * two songs can be mixed sample-for-sample during a crossfade. Float keeps a 24-bit song's full
 * resolution all the way to the AudioTrack. Not thread-safe: one reader at a time.
 */
internal interface PcmSource {
    val sampleRate: Int
    /** What was opened, as the container and decoder report it (for the player's quality details). */
    val stream: DecodedStream get() = DecodedStream("", 0, 0, 0, 0, "")
    /** Frames in the whole song, or 0 when the container doesn't say. */
    val durationFrames: Long
    /** Song position of the next frame [read] returns. */
    val positionFrames: Long
    /** Reads up to [frames] frames into [dst] starting at frame [offset]; returns fewer only at the end. */
    fun read(dst: FloatArray, offset: Int, frames: Int): Int
    fun seekTo(ms: Long)
    fun release()
}

/**
 * A WAV file on this phone (a download, or the demo's dry masters): integer PCM of 8 to 32 bits or
 * 32-bit float, any channel count. Anything else throws, and the file goes to Android's extractor.
 */
internal class WavPcmSource(file: File) : PcmSource {
    private val raf = RandomAccessFile(file, "r")
    private var channels = 2
    private var encoding = PcmConvert.PCM_16BIT
    private var dataStart = 0L
    override var sampleRate = 44100
        private set
    override var durationFrames = 0L
        private set
    override var positionFrames = 0L
        private set
    override val stream get() = DecodedStream(
        "audio/wav", sampleRate, channels, bitrate = 0, bitsPerSample = PcmConvert.bitsOf(encoding), decoder = "", decodedBits = PcmConvert.bitsOf(encoding)
    )
    private var downmix = Downmix(2)
    private var bytesPerFrame = 4
    private var bytes = ByteArray(0)
    private var buffer: ByteBuffer = ByteBuffer.wrap(bytes)
    private var samples = FloatArray(0)

    init {
        try {
            parse()
        } catch (e: Throwable) {
            runCatching { raf.close() }
            throw e
        }
    }

    private fun parse() {
        val header = ByteArray(12)
        raf.readFully(header)
        require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { "Not a WAV file" }
        var fmt: ByteBuffer? = null
        var start = -1L
        var size = 0L
        // Chunks follow one another, each padded to an even length; only "fmt " and "data" matter.
        while (start < 0 && raf.filePointer + 8 <= raf.length()) {
            val id = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
            val length = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
            when (id) {
                "fmt " -> {
                    val body = ByteArray(length.coerceAtMost(64L).toInt()).also { raf.readFully(it) }
                    fmt = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
                    raf.seek(raf.filePointer - body.size + length + (length and 1L))
                }
                "data" -> {
                    start = raf.filePointer
                    // Files still being written (or streamed) can say 0 or too much.
                    size = if (length == 0L || start + length > raf.length()) raf.length() - start else length
                }
                else -> raf.seek(raf.filePointer + length + (length and 1L))
            }
        }
        val f = requireNotNull(fmt) { "WAV without a format" }
        require(start >= 0 && f.capacity() >= 16) { "WAV without audio" }
        var tag = f.getShort(0).toInt() and 0xFFFF
        channels = f.getShort(2).toInt().also { require(it in 1..8) { "WAV with $it channels" } }
        sampleRate = f.getInt(4).also { require(it in 8000..768000) { "WAV at $it Hz" } }
        val bits = f.getShort(14).toInt()
        var mask = 0
        if (tag == 0xFFFE && f.capacity() >= 26) {
            // WAVE_FORMAT_EXTENSIBLE: the speaker mask (Android's native layout) and the real format.
            mask = f.getInt(20)
            tag = f.getShort(24).toInt() and 0xFFFF
        }
        encoding = when {
            tag == 1 && bits == 8 -> PcmConvert.PCM_8BIT
            tag == 1 && bits == 16 -> PcmConvert.PCM_16BIT
            tag == 1 && bits == 24 -> PcmConvert.PCM_24BIT_PACKED
            tag == 1 && bits == 32 -> PcmConvert.PCM_32BIT
            tag == 3 && bits == 32 -> PcmConvert.PCM_FLOAT
            else -> throw IllegalArgumentException("WAV format $tag, $bits-bit")
        }
        bytesPerFrame = channels * PcmConvert.bytesPerSample(encoding)
        downmix = Downmix(channels, mask)
        dataStart = start
        durationFrames = (size / bytesPerFrame).coerceAtLeast(1L)
        raf.seek(dataStart)
    }

    override fun read(dst: FloatArray, offset: Int, frames: Int): Int {
        val wantFrames = min(frames.toLong(), durationFrames - positionFrames).toInt().coerceAtLeast(0)
        val want = wantFrames * bytesPerFrame
        if (bytes.size < want) {
            bytes = ByteArray(want)
            buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        }
        var filled = 0
        while (filled < want) {
            val n = raf.read(bytes, filled, want - filled)
            if (n <= 0) break
            filled += n
        }
        val got = filled / bytesPerFrame
        if (samples.size < got * channels) samples = FloatArray(got * channels)
        buffer.clear()
        PcmConvert.toFloat(buffer, encoding, got * channels, samples, 0)
        downmix.toStereo(samples, got, dst, offset)
        positionFrames += got
        return got
    }

    override fun seekTo(ms: Long) {
        positionFrames = (ms * sampleRate / 1000L).coerceIn(0L, durationFrames)
        raf.seek(dataStart + positionFrames * bytesPerFrame)
    }

    override fun release() {
        runCatching { raf.close() }
    }
}

/**
 * The audio track of a file or stream as the extractor sees it, selected and ready to read. The
 * engine looks at [mime] before deciding how the song plays; whoever takes it must [release] it.
 */
internal class OpenedAudio(val extractor: MediaExtractor, val format: MediaFormat) {
    val mime: String = format.getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
    fun release() = runCatching { extractor.release() }

    companion object {
        /** Opens [path]'s first audio track; null when there is none or the container can't be read. */
        fun open(context: Context, path: String): OpenedAudio? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSourceOf(context, path)
                val trackIndex = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
                } ?: run {
                    extractor.release()
                    return null
                }
                extractor.selectTrack(trackIndex)
                OpenedAudio(extractor, extractor.getTrackFormat(trackIndex))
            } catch (_: Throwable) {
                runCatching { extractor.release() }
                null
            }
        }
    }
}

/** This phone has no decoder for a Dolby stream; the song is reported instead of handed to MediaPlayer. */
internal class NoDecoderException(val mime: String) : Exception("No decoder for $mime")

/** Any compressed file or HTTP stream Android can decode (FLAC, MP3, AAC, Opus, Vorbis, WAV). */
internal class CodecPcmSource private constructor(
    private val input: EncodedInput,
    private val codec: MediaCodec,
    rate: Int,
    channelCount: Int,
    channelMask: Int,
    private val durationUs: Long,
    private val opened: DecodedStream
) : PcmSource {
    override var sampleRate = rate
        private set
    private var channels = channelCount
    private var mask = channelMask
    private var downmix = Downmix(channelCount, channelMask)
    /** What the decoder puts out: float when it honours the request, else its own integer PCM. */
    private var encoding = AudioFormat.ENCODING_PCM_16BIT
    /** Float was asked of the decoder, so four bytes a sample means float rather than 32-bit integers. */
    private var askedFloat = false
    // The previous output buffer's time and size: from two buffers the real sample width follows.
    private var lastPts = -1L
    private var lastBytes = 0
    private var lastWidth = -1
    /** The sample width has been checked against the timestamps (or can't be). */
    private var widthChecked = false
    private val info = MediaCodec.BufferInfo()
    private var inputEos = false
    private var outputEos = false

    override val stream get() = opened.copy(decodedBits = PcmConvert.bitsOf(encoding))

    // Decoded but not yet read stereo samples, plus the song time of the first one.
    private var fifo = FloatArray(16384)
    /** One decoder buffer as float, before it is folded to stereo. */
    private var decoded = FloatArray(0)
    private var head = 0
    private var size = 0
    private var anchorUs = 0L
    private var consumedSinceAnchor = 0L

    override val durationFrames: Long
        get() = if (durationUs > 0L) durationUs * sampleRate / 1_000_000L else 0L

    override val positionFrames: Long
        get() = anchorUs * sampleRate / 1_000_000L + consumedSinceAnchor

    override fun read(dst: FloatArray, offset: Int, frames: Int): Int {
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
        input.seekTo(ms * 1000L)
        codec.flush()
        inputEos = false
        outputEos = false
        head = 0
        size = 0
        anchorUs = ms * 1000L
        consumedSinceAnchor = 0L
        lastPts = -1L
        lastWidth = -1
    }

    override fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        input.release()
    }

    /** Feeds one input buffer and drains at most one output buffer. */
    private fun pump() {
        if (!inputEos) {
            val inIndex = codec.dequeueInputBuffer(5_000L)
            if (inIndex >= 0) {
                val inBuf = codec.getInputBuffer(inIndex)
                val sampleSize = if (inBuf != null) input.readSampleData(inBuf, 0) else -1
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputEos = true
                } else {
                    codec.queueInputBuffer(inIndex, 0, sampleSize, input.sampleTime, 0)
                    input.advance()
                }
            }
        }
        val outIndex = codec.dequeueOutputBuffer(info, 5_000L)
        when {
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                // The decoder's real output can differ from the container (e.g. HE-AAC doubles the rate).
                readOutputFormat(codec.outputFormat)
            }
            outIndex >= 0 -> {
                val outBuf = codec.getOutputBuffer(outIndex)
                if (outBuf != null && info.size > 0) {
                    checkSampleWidth()
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val frameCount = info.size / (PcmConvert.bytesPerSample(encoding) * channels)
                    if (size == 0) {
                        head = 0
                        anchorUs = info.presentationTimeUs
                        consumedSinceAnchor = 0L
                    } else if (head > 0) {
                        System.arraycopy(fifo, head, fifo, 0, size)
                        head = 0
                    }
                    if (fifo.size < size + frameCount * 2) fifo = fifo.copyOf(size + frameCount * 2)
                    if (decoded.size < frameCount * channels) decoded = FloatArray(frameCount * channels)
                    PcmConvert.toFloat(outBuf.order(ByteOrder.nativeOrder()), encoding, frameCount * channels, decoded, 0)
                    // Surround is folded to stereo here (centre and surrounds into both or one side) rather
                    // than keeping only the first two channels, which would drop the vocals of a 5.1 mix.
                    downmix.toStereo(decoded, frameCount, fifo, size / 2)
                    size += frameCount * 2
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true
            }
        }
    }

    /**
     * Some decoders report one sample format and deliver another (a float request answered with
     * 16-bit while the format still says float), which plays twice as fast and as noise. The time
     * between two buffers says how many frames the first held, and so how wide its samples were;
     * when that disagrees, the width the data shows wins and what was misread is dropped.
     */
    private fun checkSampleWidth() {
        val pts = info.presentationTimeUs
        if (lastPts >= 0) {
            // Two pairs in a row must agree: a first buffer trimmed of the decoder's delay is short.
            val real = if (pts > lastPts) {
                val frames = (pts - lastPts) * sampleRate / 1_000_000.0
                val width = lastBytes / (frames * channels)
                intArrayOf(2, 3, 4).firstOrNull { abs(width - it) / it < 0.12 } ?: 0
            } else 0
            if (real != 0 && real == lastWidth && real != PcmConvert.bytesPerSample(encoding)) {
                Log.w("OndalunePcm", "${opened.decoder} said ${PcmConvert.bytesPerSample(encoding)}-byte samples, gives $real")
                encoding = when (real) {
                    2 -> AudioFormat.ENCODING_PCM_16BIT
                    3 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
                    else -> if (askedFloat) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_32BIT
                }
                head = 0
                size = 0
                anchorUs = pts
                consumedSinceAnchor = 0L
            }
            if (lastWidth != -1) widthChecked = true
            lastWidth = real
        }
        lastPts = pts
        lastBytes = info.size
    }

    private fun readOutputFormat(f: MediaFormat) {
        lastPts = -1L
        lastWidth = -1
        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        mask = f.intOr(MediaFormat.KEY_CHANNEL_MASK)
        if (downmix.channels != channels || mask != 0) downmix = Downmix(channels, mask)
        // No encoding said means 16-bit; one this can't read is taken as 16-bit too, as before.
        val said = f.intOr(MediaFormat.KEY_PCM_ENCODING)
        encoding = if (PcmConvert.bytesPerSample(said) > 0) said else AudioFormat.ENCODING_PCM_16BIT
    }

    companion object {
        /**
         * Decodes [opened] (which it takes over, released even on failure) far enough that the real
         * sample rate is known; null if nothing here decodes it. A Dolby stream this phone has no
         * decoder for throws [NoDecoderException] instead.
         */
        fun from(opened: OpenedAudio): CodecPcmSource? = start(ExtractorInput(opened.extractor), opened.format, opened.mime)

        /** A FLAC deeper than 16-bit, read by [FlacFrames] so its decoder can give float; null if it won't decode. */
        fun from(flac: FlacFrames): CodecPcmSource? = start(flac, flac.format(), "audio/flac")

        private fun start(input: EncodedInput, format: MediaFormat, mime: String): CodecPcmSource? {
            var created: MediaCodec? = null
            return try {
                val stream = DecodedStream.of(format, decoder = "")
                val (codec, decodesAs) = createDecoder(mime) ?: run {
                    input.release()
                    if (isDolbyMime(mime)) throw NoDecoderException(mime)
                    return null
                }
                // An E-AC-3 decoder plays Atmos (JOC) streams as their 5.1 core, under its own type.
                if (decodesAs != mime) format.setString(MediaFormat.KEY_MIME, decodesAs)
                created = codec
                // Float output keeps a 24-bit song's resolution; a decoder that can't give it says what it
                // gives instead. Raw PCM goes through its decoder untouched, so it keeps the encoding the
                // extractor read it in (float for a 24-bit FLAC or WAV on recent Android).
                // Only a source deeper than 16 bits asks: a 16-bit or lossy one gains nothing from it.
                val wantsFloat = decodesAs != "audio/raw" && stream.bitsPerSample > 16
                if (wantsFloat) format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
                try {
                    codec.configure(format, null, null, 0)
                } catch (e: Exception) {
                    if (!wantsFloat) throw e
                    // A decoder that refuses float gets its usual 16-bit.
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                    codec.reset()
                    codec.configure(format, null, null, 0)
                }
                codec.start()
                val source = CodecPcmSource(
                    input,
                    codec,
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100,
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 2,
                    format.intOr(MediaFormat.KEY_CHANNEL_MASK),
                    if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L,
                    stream.copy(decoder = codec.name)
                )
                source.askedFloat = wantsFloat && format.intOr(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                runCatching { source.readOutputFormat(codec.outputFormat) }
                // A few buffers in, so the sample width is checked before anything plays.
                var attempts = 0
                while ((source.size == 0 || !source.widthChecked) && !source.outputEos && attempts++ < 400) source.pump()
                source
            } catch (e: NoDecoderException) {
                throw e
            } catch (_: Throwable) {
                runCatching { created?.release() }
                input.release()
                null
            }
        }

        /**
         * The decoder for [mime] and the type it is set up for. Dolby streams go to the phone's own
         * (vendor) decoder when it has one, which is the only kind there is for them; everything
         * else keeps Android's usual choice.
         */
        private fun createDecoder(mime: String): Pair<MediaCodec, String>? {
            if (isDolbyMime(mime)) {
                for ((name, type) in dolbyDecodersFor(mime)) {
                    runCatching { MediaCodec.createByCodecName(name) }.getOrNull()?.let { return it to type }
                }
                return null
            }
            return runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()?.let { it to mime }
        }
    }
}

/** Every decoder Android lists, read once: listing them is slow on some phones. */
private val decoderInfos: List<MediaCodecInfo> by lazy {
    runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder } }.getOrDefault(emptyList())
}

/**
 * This phone's decoders for a Dolby [mime], best first, with the type each is set up for: its own
 * vendor ones before any software one, and for Atmos (E-AC-3 JOC) the plain E-AC-3 decoders after
 * the JOC ones, since those still play the 5.1 core.
 */
internal fun dolbyDecodersFor(mime: String): List<Pair<String, String>> {
    val wanted = mime.lowercase()
    val types = if (wanted == "audio/eac3-joc") listOf(wanted, "audio/eac3") else listOf(wanted)
    return types.flatMap { type ->
        decoderInfos
            .filter { info -> info.supportedTypes.any { it.equals(type, ignoreCase = true) } }
            .sortedByDescending { info ->
                if (Build.VERSION.SDK_INT >= 29) (if (info.isVendor) 2 else 0) + (if (info.isHardwareAccelerated) 1 else 0)
                else if (info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")) 0 else 2
            }
            .map { it.name to type }
    }.distinctBy { it.first }
}

internal fun MediaFormat.intOr(key: String) = if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0

/** A song lined up in the mixer: its decoded audio and the ReplayGain it plays at. */
internal class QueuedSource(
    val track: SubsonicTrack,
    val source: PcmSource,
    /** Used when the container has no duration (some transcoded streams). */
    private val metadataDurationMs: Long,
    var gain: Float = 1f,
    /** The server's original file over the network, with [server] to fetch a converted copy instead. */
    val rawStreamFrom: SubsonicServerConfig? = null
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
    private var scratch = FloatArray(0)

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

    /**
     * Fills [out] with up to [frames] stereo frames. Gain and the fade sum stay in float, unclipped:
     * the engine's output stage is the one place that limits the level.
     */
    fun render(out: FloatArray, frames: Int, result: Result): Result {
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
                if (scratch.size < n * 2) scratch = FloatArray(n * 2)
                val m = upcoming.source.read(scratch, 0, n)
                for (i in 0 until m) {
                    val g = upcoming.gain * fadeIn(fadePos + i, fadeLen)
                    val idx = (written + i) * 2
                    out[idx] += scratch[i * 2] * g
                    out[idx + 1] += scratch[i * 2 + 1] * g
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

    private inline fun applyGain(buf: FloatArray, offsetFrames: Int, frames: Int, gainAt: (Int) -> Float) {
        for (i in 0 until frames) {
            val g = gainAt(i)
            if (g == 1f) continue
            val idx = (offsetFrames + i) * 2
            buf[idx] *= g
            buf[idx + 1] *= g
        }
    }

    private fun fadeOut(pos: Int, len: Int): Float = cos((pos.toFloat() / len).coerceIn(0f, 1f) * (PI / 2).toFloat())
    private fun fadeIn(pos: Int, len: Int): Float = sin((pos.toFloat() / len).coerceIn(0f, 1f) * (PI / 2).toFloat())
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

/**
 * One opened song as Android's extractor and decoder describe it. Read-only facts for the player's
 * quality details; nothing here changes how the audio is decoded or processed.
 */
data class DecodedStream(
    val mime: String,
    val sampleRate: Int,
    val channels: Int,
    /** Bits per second from the container; 0 when it doesn't say. */
    val bitrate: Int,
    /** Bits per sample when the container says (raw PCM, some FLAC); 0 otherwise. */
    val bitsPerSample: Int,
    /** The decoder Android picked, e.g. "c2.android.flac.decoder". */
    val decoder: String,
    /** Bits per sample the decoder hands the engine: 16 or 24 for integer PCM, 32 for float; 0 before it says. */
    val decodedBits: Int = 0
) {
    companion object {
        fun of(format: MediaFormat, decoder: String) = DecodedStream(
            mime = format.getString(MediaFormat.KEY_MIME).orEmpty(),
            sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE),
            channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT),
            bitrate = format.intOr(MediaFormat.KEY_BIT_RATE),
            bitsPerSample = when (format.intOr(MediaFormat.KEY_PCM_ENCODING)) {
                android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                // An extractor that decodes as it reads (FLAC, WAV) gives float for anything over 16-bit
                // and says the file's own depth alongside.
                android.media.AudioFormat.ENCODING_PCM_FLOAT -> format.intOr("bits-per-sample").takeIf { it > 0 } ?: 32
                else -> format.intOr("bits-per-sample")
            },
            decoder = decoder
        )
    }
}
