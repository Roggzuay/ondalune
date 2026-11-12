package io.github.roggzuay.ondalune.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The Dolby stream types Android names; a phone decodes them only with its own licensed decoder. */
internal val DolbyMimes = setOf("audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/ac4")

internal fun isDolbyMime(mime: String) = mime.lowercase() in DolbyMimes

/** How one song plays here. */
internal enum class PlayPath {
    /** Decoded into Ondalune's engine (a Dolby stream by the phone's Dolby decoder, mixed down to stereo). */
    DECODE,
    /** Handed untouched to the phone's Dolby engine; Ondalune's processing doesn't apply. */
    DOLBY_ENGINE,
    /** A Dolby stream this phone has no decoder for, and no output that takes it as it is. */
    NO_DECODER
}

/**
 * The way a stream of type [mime] plays: anything but Dolby is decoded as always. A Dolby stream goes
 * to the phone's Dolby engine when that is switched on ([passthroughOn]) and the current output takes
 * it untouched ([outputTakesIt]); otherwise the phone's own Dolby decoder ([phoneDecodes]) decodes it.
 */
internal fun playPathFor(mime: String, passthroughOn: Boolean, outputTakesIt: Boolean, phoneDecodes: Boolean): PlayPath = when {
    !isDolbyMime(mime) -> PlayPath.DECODE
    passthroughOn && outputTakesIt -> PlayPath.DOLBY_ENGINE
    phoneDecodes -> PlayPath.DECODE
    else -> PlayPath.NO_DECODER
}

/**
 * Plays a Dolby stream untouched, handing its compressed frames to an [AudioTrack] in the stream's
 * own encoding so the phone's Dolby engine (not Ondalune) renders it, Atmos included where the
 * phone and its output can. Nothing of Ondalune's processing applies on this path: no EQ, reverb,
 * speed, pitch, levelling, gapless or crossfade. Any trouble, from an output that stops taking the
 * stream (a Bluetooth switch) to a write error, ends in [Listener.onFailed] with the position
 * reached, so the engine can carry on by decoding the same song instead.
 *
 * One song per instance. Methods are called from the main thread; the stream runs on its own
 * coroutine. The [AudioTrack] counts played frames at the stream's sample rate, which is what the
 * audible position (for lyrics) reads.
 */
internal class DolbyPassthrough(
    private val opened: OpenedAudio,
    /** How the current output takes the stream, from [outputFor]. */
    private val output: Output,
    private val scope: CoroutineScope,
    private val listener: Listener
) {
    interface Listener {
        /** The position being heard is now [positionMs] (stream thread, about ten times a second). */
        fun onPosition(positionMs: Long)
        /** The last frame has been heard (stream thread). */
        fun onEnded()
        /** The stream can't go on this way; it had reached [positionMs] (stream thread). */
        fun onFailed(positionMs: Long, reason: String)
    }

    /**
     * How an output takes a Dolby encoding: as a direct stream (the phone's Dolby engine, or the
     * bitstream on to a receiver over HDMI or USB) or offloaded to the phone's audio DSP.
     */
    class Output(val encoding: Int, val sampleRate: Int, val channelMask: Int, val offload: Boolean)

    private val extractor: MediaExtractor get() = opened.extractor
    val stream: DecodedStream = DecodedStream.of(opened.format, decoder = "")
    val sampleRate: Int = output.sampleRate
    val durationMs: Long = if (opened.format.containsKey(MediaFormat.KEY_DURATION)) opened.format.getLong(MediaFormat.KEY_DURATION) / 1000L else 0L

    @Volatile var audioTrack: AudioTrack? = null
        private set
    @Volatile var isPaused = false
        private set
    private var job: Job? = null
    private val stopped = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(-1L)
    private val routeChanged = AtomicBoolean(false)
    private val stamp = AudioTimestamp()
    @Volatile private var volume = 1f

    // Song time of the AudioTrack's frame zero (reset by every flush) and the frames written since.
    @Volatile private var originMs = 0L
    @Volatile private var writtenFrames = 0L
    // A fallback clock for outputs whose head position never moves for compressed audio.
    @Volatile private var playStartedNanos = 0L
    @Volatile private var playedBeforeMs = 0L

    val routedDevice: AudioDeviceInfo? get() = audioTrack?.routedDevice

    /** Where the next frame written comes from in the song. */
    fun positionMs(): Long = originMs + writtenFrames * 1000L / sampleRate

    /** The position being heard: played frames by the track's own count, never past what was written. */
    fun audiblePositionMs(): Long {
        // A seek not yet applied by the stream thread: the old position would flash back on screen.
        pendingSeekMs.get().takeIf { it >= 0L }?.let { return it }
        val track = audioTrack ?: return positionMs()
        val playing = !isPaused && track.playState == AudioTrack.PLAYSTATE_PLAYING
        val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        var played = head
        if (playing && runCatching { track.getTimestamp(stamp) }.getOrDefault(false)) {
            val presented = stamp.framePosition + ((System.nanoTime() - stamp.nanoTime) * sampleRate.toDouble() / 1e9).toLong()
            played = presented.coerceIn((head - sampleRate).coerceAtLeast(0L), head)
        }
        if (head == 0L && playing && playStartedNanos > 0L) played = elapsedPlayingMs() * sampleRate / 1000L
        return originMs + min(played, writtenFrames) * 1000L / sampleRate
    }

    private fun elapsedPlayingMs(): Long =
        playedBeforeMs + if (playStartedNanos > 0L) (System.nanoTime() - playStartedNanos) / 1_000_000L else 0L

    fun start(fromMs: Long = 0L) {
        // The extractor is let go however the job ends, even if it is stopped before it starts.
        job = scope.launch(Dispatchers.IO) { run(fromMs) }.also { it.invokeOnCompletion { opened.release() } }
    }

    fun pause() {
        isPaused = true
        if (playStartedNanos > 0L) {
            playedBeforeMs += (System.nanoTime() - playStartedNanos) / 1_000_000L
            playStartedNanos = 0L
        }
        runCatching { audioTrack?.pause() }
    }

    fun resume() {
        isPaused = false
        playStartedNanos = System.nanoTime()
        runCatching { audioTrack?.play() }
    }

    fun seekTo(ms: Long) {
        pendingSeekMs.set(ms.coerceAtLeast(0L))
    }

    /** Kept for a track still being built, so ducking right at the start isn't lost. */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        runCatching { audioTrack?.setVolume(this.volume) }
    }

    fun stop() {
        stopped.set(true)
        // Silence what is queued now; the stream thread may still be inside a network read and
        // releases the track when it gets out.
        audioTrack?.let { runCatching { it.pause(); it.flush() } }
        val running = job
        job = null
        if (running == null) opened.release() else running.cancel()
    }

    private suspend fun run(fromMs: Long) {
        var track: AudioTrack? = null
        try {
            track = buildTrack() ?: throw IllegalStateException("The output didn't take the Dolby stream")
            audioTrack = track
            runCatching { track.setVolume(volume) }
            track.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { routeChanged.set(true) }, Handler(Looper.getMainLooper()))
            applySeek(fromMs)
            if (!isPaused) {
                track.play()
                playStartedNanos = System.nanoTime()
            }
            val buffer = ByteBuffer.allocateDirect(MaxUnitBytes)
            var eos = false
            var unitFrames = DefaultUnitFrames
            var lastReportNanos = 0L
            var drainStartedNanos = 0L
            var drainWaitMs = 0L
            while (coroutineContext.isActive && !stopped.get()) {
                val seek = pendingSeekMs.get()
                if (seek >= 0L) {
                    track.pause()
                    track.flush()
                    applySeek(seek)
                    pendingSeekMs.compareAndSet(seek, -1L)
                    eos = false
                    drainStartedNanos = 0L
                    if (!isPaused) track.play()
                    listener.onPosition(positionMs())
                }
                if (isPaused) {
                    delay(24L)
                    continue
                }
                // Moving to an output without Dolby support (Bluetooth, most often) ends this way of playing.
                if (routeChanged.getAndSet(false) && outputFor(opened)?.encoding != output.encoding) {
                    throw IllegalStateException("The new output doesn't take the Dolby stream")
                }
                val now = System.nanoTime()
                if (now - lastReportNanos >= ReportEveryNanos) {
                    lastReportNanos = now
                    listener.onPosition(audiblePositionMs())
                }
                if (eos) {
                    // Let the last frames leave: until the track's count reaches what was written, or, where
                    // the count doesn't move for compressed audio, for as long as the queued audio lasts.
                    if (drainStartedNanos == 0L) {
                        drainStartedNanos = now
                        drainWaitMs = (positionMs() - audiblePositionMs()).coerceIn(0L, 10_000L)
                    }
                    val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    val waited = (now - drainStartedNanos) / 1_000_000L
                    if ((head > 0L && writtenFrames - head <= sampleRate / 25) || waited >= drainWaitMs) {
                        listener.onEnded()
                        return
                    }
                    delay(40L)
                    continue
                }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    eos = true
                    continue
                }
                val unitUs = extractor.sampleTime
                extractor.advance()
                val nextUs = extractor.sampleTime
                if (nextUs > unitUs) unitFrames = ((nextUs - unitUs) * sampleRate / 1_000_000L).toInt().coerceAtLeast(1)
                buffer.position(0)
                buffer.limit(size)
                // A whole Dolby frame goes in even across a pause (writes then return 0 until play); only a
                // seek abandons it, since the flush drops it anyway.
                while (buffer.hasRemaining() && coroutineContext.isActive && !stopped.get() && pendingSeekMs.get() < 0L) {
                    val written = track.write(buffer, buffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
                    if (written < 0) throw IllegalStateException("The output stopped taking the Dolby stream ($written)")
                    if (written == 0) delay(8L)
                }
                if (buffer.hasRemaining()) continue
                writtenFrames += unitFrames
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!stopped.get()) listener.onFailed(audiblePositionMs(), e.message ?: "Dolby playback error")
        } finally {
            audioTrack = null
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
        }
    }

    /** Moves the extractor to [ms] (the nearest frame it can start from) and restarts the frame count there. */
    private fun applySeek(ms: Long) {
        if (ms > 0L || writtenFrames > 0L) extractor.seekTo(ms * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        val at = extractor.sampleTime
        originMs = if (at >= 0L) at / 1000L else ms
        writtenFrames = 0L
        playedBeforeMs = 0L
        playStartedNanos = if (isPaused) 0L else System.nanoTime()
    }

    private fun buildTrack(): AudioTrack? {
        val format = audioFormat(output.encoding, output.sampleRate, output.channelMask)
        // About a quarter of a second at the encoding's highest bitrate, as players commonly size it.
        val bufferBytes = max(MinBufferBytes, maxBytesPerSecond(output.encoding) / 4)
        return runCatching {
            val builder = AudioTrack.Builder()
                .setAudioAttributes(mediaAttributes())
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (output.offload && Build.VERSION.SDK_INT >= 29) builder.setOffloadedPlayback(true)
            builder.build()
        }.getOrNull()?.takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    }

    companion object {
        /** Largest Dolby access unit the extractor hands over at once (E-AC-3 frames top out around 4 KiB). */
        private const val MaxUnitBytes = 64 * 1024
        private const val MinBufferBytes = 64 * 1024
        /** AC-3 and E-AC-3 frames carry 1536 PCM samples; used until the extractor's times say otherwise. */
        private const val DefaultUnitFrames = 1536
        private const val ReportEveryNanos = 100_000_000L

        /** The AudioFormat encoding for a Dolby mime, 0 when this Android can't name it. */
        fun encodingFor(mime: String): Int = when (mime.lowercase()) {
            "audio/ac3" -> AudioFormat.ENCODING_AC3
            "audio/eac3" -> AudioFormat.ENCODING_E_AC3
            "audio/eac3-joc" -> if (Build.VERSION.SDK_INT >= 28) AudioFormat.ENCODING_E_AC3_JOC else 0
            "audio/ac4" -> if (Build.VERSION.SDK_INT >= 28) AudioFormat.ENCODING_AC4 else 0
            else -> 0
        }

        private fun maxBytesPerSecond(encoding: Int): Int = when (encoding) {
            AudioFormat.ENCODING_AC3 -> 640_000 / 8
            AudioFormat.ENCODING_AC4 -> 2_688_000 / 8
            else -> 6_144_000 / 8
        }

        /**
         * The channel masks to offer the output, best first: the stream's own layout, then the usual
         * ones. Object-based streams (Atmos in E-AC-3 JOC, AC-4) have no fixed layout, and for a
         * bitstream the mask is mostly a formality, so the output's own answer decides.
         */
        private fun masksFor(channels: Int): List<Int> = listOfNotNull(
            when (channels) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                6 -> AudioFormat.CHANNEL_OUT_5POINT1
                8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                else -> null
            },
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND,
            AudioFormat.CHANNEL_OUT_5POINT1,
            AudioFormat.CHANNEL_OUT_STEREO
        ).distinct()

        /**
         * How the current output takes [opened] untouched, by Android's own answer, or null when it
         * doesn't (or [opened] isn't Dolby). Before Android 10 there is no way to ask, so: null.
         */
        fun outputFor(opened: OpenedAudio): Output? {
            if (Build.VERSION.SDK_INT < 29 || !isDolbyMime(opened.mime)) return null
            val encoding = encodingFor(opened.mime).takeIf { it != 0 } ?: return null
            val rate = opened.format.intOr(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: 48000
            val attributes = mediaAttributes()
            for (mask in masksFor(opened.format.intOr(MediaFormat.KEY_CHANNEL_COUNT))) {
                val format = audioFormat(encoding, rate, mask)
                val way = runCatching { directOrOffload(format, attributes) }.getOrNull() ?: continue
                return Output(encoding, rate, mask, offload = way == Way.OFFLOAD)
            }
            return null
        }

        private enum class Way { DIRECT, OFFLOAD }

        @RequiresApi(29)
        private fun directOrOffload(format: AudioFormat, attributes: AudioAttributes): Way? {
            if (Build.VERSION.SDK_INT >= 33) {
                val support = AudioManager.getDirectPlaybackSupport(format, attributes)
                return when {
                    support and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED != 0 -> Way.DIRECT
                    support and AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED != 0 -> Way.OFFLOAD
                    else -> null
                }
            }
            @Suppress("DEPRECATION")
            return when {
                AudioTrack.isDirectPlaybackSupported(format, attributes) -> Way.DIRECT
                AudioManager.isOffloadedPlaybackSupported(format, attributes) -> Way.OFFLOAD
                else -> null
            }
        }

        private fun audioFormat(encoding: Int, sampleRate: Int, channelMask: Int): AudioFormat =
            AudioFormat.Builder().setEncoding(encoding).setSampleRate(sampleRate).setChannelMask(channelMask).build()

        private fun mediaAttributes(): AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    }
}
