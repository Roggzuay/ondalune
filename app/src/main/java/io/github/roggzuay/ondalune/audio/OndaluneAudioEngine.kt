package io.github.roggzuay.ondalune.audio

import io.github.roggzuay.ondalune.data.source.MusicSources
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Build
import io.github.roggzuay.ondalune.data.model.CrossfeedLevel
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.codecName
import io.github.roggzuay.ondalune.data.model.isOnDevice
import io.github.roggzuay.ondalune.data.model.dolbyMime
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbProcessingMode
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real-Time PCM DSP & OpenSubsonic Audio Engine supporting:
 * 1. Real-time stereo PCM streaming via [AudioTrack] + live [OndaluneReverbDspProcessor] + 5-band
 *    floating-point EQ + simultaneous [PlaybackParams] speed (0.80x..2.00x) and dynamic pitch.
 * 2. Live decoding of local/offline files and HTTP streams via [MediaExtractor] + [MediaCodec]
 *    into the real-time stereo PCM DSP pipeline, with automatic fallback to [MediaPlayer] +
 *    hardware [EnvironmentalReverb]/[PresetReverb] if a codec/container blocks raw extraction.
 * 3. Offline-first playback: automatically plays downloaded tracks from
 *    `filesDir/ondalune_offline_downloads/server_<id>/` when available.
 * 4. Dry master synthesis for the OpenSubsonic Sandbox: source WAV files on disk are always pure
 *    dry PCM so reverb and EQ changes take effect instantaneously in real time without regenerating
 *    files or restarting the track.
 */
class OndaluneAudioEngine(
    private val context: Context,
    private val subsonicClient: OpenSubsonicClient,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    /** Set only while a Dolby stream plays through the phone's own Dolby engine. */
    private var dolby: DolbyPassthrough? = null
    private var pcmStreamingJob: Job? = null
    private var progressJob: Job? = null

    private var environmentalReverb: EnvironmentalReverb? = null
    private var presetReverbFallback: PresetReverb? = null
    private var hardwareEqualizer: Equalizer? = null

    val reverbDspProcessor = OndaluneReverbDspProcessor(sampleRate = 44100)

    private val isPcmPipelineActive = AtomicBoolean(false)
    private val isPcmPaused = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(-1L)

    private val audibleClock = AudibleClock()
    private val audibleStamp = AudioTimestamp()
    private var audibleStampTrack: AudioTrack? = null
    private var audibleStampOk = false
    private var audibleStampReadNanos = 0L

    @Volatile
    private var currentEqPreset: EqualizerPreset = EqualizerPreset.FLAT_STUDIO

    @Volatile
    private var masterVolumeGain: Float = 1.0f

    // Biquad / shelf filter state for real-time 5-band stereo EQ
    private var lowStateL = 0f
    private var lowStateR = 0f
    private var highStateL = 0f
    private var highStateR = 0f

    private val audioManager: AudioManager? =
        runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false
    var pauseOnHeadphoneDisconnect: Boolean = true

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(36_000L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _reverbState = MutableStateFlow(ReverbState())
    val reverbState: StateFlow<ReverbState> = _reverbState.asStateFlow()

    private val _reverbProcessingMode = MutableStateFlow(ReverbProcessingMode.BYPASSED_DRY)
    val reverbProcessingMode: StateFlow<ReverbProcessingMode> = _reverbProcessingMode.asStateFlow()

    private val _isPlayingOfflineFile = MutableStateFlow(false)
    val isPlayingOfflineFile: StateFlow<Boolean> = _isPlayingOfflineFile.asStateFlow()

    private val _audioSessionError = MutableStateFlow<String?>(null)
    val audioSessionError: StateFlow<String?> = _audioSessionError.asStateFlow()

    /**
     * Counter of stereo PCM frames processed in real time by [processRealtimeStereoChunk]
     * during active playback, enabling deterministic verification that the custom DSP pipeline is
     * actively processing the stream rather than baking effects into static files.
     */
    private val _realtimeProcessedFrames = MutableStateFlow(0L)
    val realtimeProcessedFrames: StateFlow<Long> = _realtimeProcessedFrames.asStateFlow()

    /**
     * What is playing as it was opened and decoded, and which way it goes out. Read-only facts for
     * the player's quality details; setting them changes nothing about playback.
     */
    data class PlayingStream(
        val decoded: DecodedStream?,
        /** True: Ondalune's own pipeline (32-bit float stereo to an AudioTrack); false: Android's MediaPlayer. */
        val ownPipeline: Boolean,
        val outputSampleRate: Int,
        /** True: the untouched Dolby stream goes to the phone's own Dolby engine; nothing of Ondalune's applies. */
        val dolbyEngine: Boolean = false
    )

    private val _playingStream = MutableStateFlow<PlayingStream?>(null)
    val playingStream: StateFlow<PlayingStream?> = _playingStream.asStateFlow()

    /** The output device Android routes this engine's audio to right now, when it says. */
    fun routedOutput(): android.media.AudioDeviceInfo? =
        dolby?.routedDevice ?: audioTrack?.routedDevice ?: if (android.os.Build.VERSION.SDK_INT >= 28) mediaPlayer?.routedDevice else null

    var onTrackEnded: (() -> Unit)? = null

    /** A song this phone can't decode (a Dolby stream with no decoder here); main thread, with the message to show. */
    var onTrackUnplayable: ((SubsonicTrack, String) -> Unit)? = null

    /** Hand Dolby streams to the phone's own Dolby engine when the output takes them (Settings). */
    @Volatile
    var dolbyPassthroughEnabled: Boolean = true

    // ---- Transitions: gapless hand-over, crossfade and ReplayGain ------------------------------

    /** Crossfade length between songs; 0 = none. */
    @Volatile
    var crossfadeMs: Int = 0

    /** Start the next song on the very next sample instead of after a pause. */
    @Volatile
    var gaplessEnabled: Boolean = true

    @Volatile
    var replayGainMode: ReplayGainMode = ReplayGainMode.OFF

    /** The song and server to line up after the current one; called on the main thread. */
    var nextTrackProvider: (() -> Pair<SubsonicTrack, SubsonicServerConfig>?)? = null

    /** The pipeline moved on to [SubsonicTrack] by itself (gapless or crossfade); main thread. */
    var onTrackAdvanced: ((SubsonicTrack) -> Unit)? = null

    @Volatile
    private var expectedNextId: String? = null

    @Volatile
    private var expectationKnown = false

    /** A next song that can't share the current output (e.g. other sample rate); not retried. */
    @Volatile
    private var unpreparableNextId: String? = null

    private val preparedNext = AtomicReference<QueuedSource?>(null)
    private var prepareJob: Job? = null
    private val pipelineSession = AtomicInteger(0)

    /**
     * Tells the engine which song should follow the current one ([trackId] null = nothing, e.g. the
     * sleep timer stops at the end). A lined-up song that no longer matches is dropped.
     */
    fun expectNextTrack(trackId: String?) {
        expectedNextId = trackId
        expectationKnown = true
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setOutputVolume(0.28f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                setOutputVolume(1.0f)
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && pauseOnHeadphoneDisconnect) {
                pausePlayback()
            }
        }
    }

    private fun requestSystemAudioFocus() {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
        registerNoisyReceiverIfNeeded()
    }

    private fun registerNoisyReceiverIfNeeded() {
        if (noisyReceiverRegistered) return
        runCatching {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    becomingNoisyReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(becomingNoisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiverIfNeeded() {
        if (!noisyReceiverRegistered) return
        runCatching {
            context.applicationContext.unregisterReceiver(becomingNoisyReceiver)
        }
        noisyReceiverRegistered = false
    }

    /**
     * Resolves an offline downloaded track file under `filesDir/ondalune_offline_downloads/server_<id>/`
     * if one exists.
     */
    fun findOfflineTrackFile(serverId: Long, trackId: String): File? {
        val sanitizedId = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "track" }
        val serverDir = File(context.filesDir, "ondalune_offline_downloads/server_$serverId")
        if (!serverDir.exists()) return null
        return serverDir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") && f.nameWithoutExtension == sanitizedId && f.length() > 44L
        }?.firstOrNull()
    }

    /** Songs saved ahead of the queue by [cacheAhead]. Android may clear them like any cache. */
    private val songCacheDir get() = File(context.cacheDir, "songs")

    private fun cachedSongFile(serverId: Long, trackId: String): File? {
        val id = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return songCacheDir.listFiles { f -> f.isFile && f.name.startsWith("${serverId}_$id.") && !f.name.endsWith(".part") }
            ?.firstOrNull()
    }

    /** True when [track] plays from a copy saved ahead rather than from the server. */
    fun hasSavedCopy(track: SubsonicTrack) = cachedSongFile(track.serverId, track.id) != null

    private val _songsAhead = MutableStateFlow(SongsAhead())
    /** How many of the songs coming up are on the phone already. */
    val songsAhead: StateFlow<SongsAhead> = _songsAhead.asStateFlow()

    /**
     * Saves the [upcoming] songs to the phone in queue order, as the same stream they would play
     * from, so they start at once and never wait on the server. One at a time, and only while the
     * song playing has had the line to itself for a while: the moment it waits on the network, the
     * download stops and tries again later. Older saved songs beyond [keep] are let go, least
     * recently played first.
     */
    suspend fun cacheAhead(upcoming: List<Pair<SubsonicTrack, SubsonicServerConfig>>, keep: Int) = withContext(Dispatchers.IO) {
        songCacheDir.mkdirs()
        val wanted = upcoming.filter { (track, server) -> !server.isDemoServer && findOfflineTrackFile(server.id, track.id) == null }
        fun ready() = wanted.count { (track, server) -> cachedSongFile(server.id, track.id) != null }
        _songsAhead.value = SongsAhead(ready(), wanted.size, active = true)
        try {
            for ((track, requested) in wanted) {
                // A download stopped to give the line back is tried again once it is quiet.
                var steppedAside = true
                while (steppedAside && cachedSongFile(requested.id, track.id) == null) {
                    awaitQuietLine()
                    val server = streamServerFor(track, requested)
                    val url = MusicSources.forServer(context, subsonicClient, server).playablePath(server, track)
                        ?.takeIf { it.startsWith("http") } ?: break
                    val raw = server.transcodeFormat.isBlank() || server.transcodeFormat == "RAW"
                    val ext = if (raw) track.suffix.lowercase().ifBlank { "audio" } else server.transcodeFormat.lowercase()
                    val id = track.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    steppedAside = false
                    coroutineScope {
                        savingAhead = true
                        val download = launch {
                            subsonicClient.downloadAudioStreamToFile(url, track, File(songCacheDir, "${requested.id}_$id.$ext")) { _, _ -> }
                        }
                        val stepAside = launch {
                            val from = streamWaitedAtMs.value
                            merge(streamWaitedAtMs.filter { it != from }, isBuffering.filter { it }).first()
                            steppedAside = true
                            download.cancel()
                        }
                        download.join()
                        stepAside.cancel()
                        savingAhead = false
                    }
                }
                _songsAhead.value = SongsAhead(ready(), wanted.size, active = true)
            }
        } finally {
            savingAhead = false
            _songsAhead.value = _songsAhead.value.copy(active = false)
            songCacheDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            val spare = songCacheDir.listFiles()?.filter { it.isFile }.orEmpty().sortedByDescending { it.lastModified() }
            spare.drop(keep).forEach { it.delete() }
        }
    }

    /** When the song playing last had to wait on the network. */
    private val streamWaitedAtMs = MutableStateFlow(0L)
    @Volatile
    private var savingAhead = false

    /** Until nothing has been loading or waiting on the network for [QuietLineMs]. */
    private suspend fun awaitQuietLine() {
        var quietSince = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (isBuffering.value || now - streamWaitedAtMs.value < QuietLineMs) quietSince = now
            if (now - quietSince >= QuietLineMs) return
            delay(1_000L)
        }
    }

    /** Bumped by every [playTrack]; a load that finishes after a newer request is dropped. */
    private val playRequest = AtomicInteger(0)

    fun playTrack(
        track: SubsonicTrack,
        requestedServer: SubsonicServerConfig,
        eqPreset: EqualizerPreset,
        startMs: Long = 0L
    ) {
        val server = streamServerFor(track, requestedServer)
        val request = playRequest.incrementAndGet()
        scope.launch {
            _isBuffering.value = true
            _audioSessionError.value = null
            _durationMs.value = (track.durationSec * 1000L).coerceAtLeast(10_000L)
            _positionMs.value = 0L
            currentEqPreset = eqPreset
            masterVolumeGain = 1.0f

            releasePlayerInternal()
            reverbDspProcessor.clearDelayLines()
            lowStateL = 0f
            lowStateR = 0f
            highStateL = 0f
            highStateR = 0f
            requestSystemAudioFocus()

            try {
                val (sourcePath, offline) = resolveSourcePath(track, server)
                if (request != playRequest.get()) return@launch
                _isPlayingOfflineFile.value = offline
                // Real-time PCM DSP pipeline first (a Dolby stream goes to the phone's own Dolby engine
                // when it takes it); MediaPlayer + hardware session effects if the container can't be
                // decoded to raw PCM.
                val opened = withContext(Dispatchers.IO) {
                    openOrConvert(sourcePath, track, server).also { opened ->
                        // A converted stream can't seek, so it's decoded up to [startMs] instead.
                        val source = (opened.playable as? Playable.Pcm)?.source ?: return@also
                        val target = startMs * source.sampleRate / 1000L
                        val scratch = FloatArray(4096 * 2)
                        while (source.positionFrames < target && request == playRequest.get()) {
                            if (source.read(scratch, 0, min(4096L, target - source.positionFrames).toInt()) <= 0) break
                        }
                    }
                }
                val playable = opened.playable
                // A slow stream can finish opening after another song was picked: never start both.
                if (request != playRequest.get()) {
                    playable.release()
                    return@launch
                }
                when (playable) {
                    is Playable.Pcm -> {
                        startPcmPipeline(
                            QueuedSource(
                                track, playable.source, track.durationSec * 1000L, replayGainLinear(track, replayGainMode),
                                rawStreamFrom = opened.server.takeIf { !offline && isRawStream(opened.path, it) }
                            )
                        )
                        if (startMs > 0L) _positionMs.value = startMs
                    }
                    is Playable.Dolby -> startDolbyPassthrough(playable.audio, playable.output, track, opened.path, fromMs = 0L)
                    is Playable.NoDecoder -> reportUnplayable(track, cantDecode(playable.mime))
                    Playable.None -> startMediaPlayerFallback(opened.path, eqPreset)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (request != playRequest.get()) return@launch
                _isBuffering.value = false
                _isPlaying.value = false
                _reverbProcessingMode.value = ReverbProcessingMode.UNSUPPORTED_STREAM_ERROR
                _audioSessionError.value = e.localizedMessage ?: "Audio initialization error"
            }
        }
    }

    /** Where [track] plays from (downloaded file, the demo's dry WAV master, or the server stream) and whether it's offline. */
    private suspend fun resolveSourcePath(track: SubsonicTrack, server: SubsonicServerConfig): Pair<String, Boolean> {
        io.github.roggzuay.ondalune.data.source.OpenedFile.uriOf(track)?.let { return it to true }
        val offlineFile = withContext(Dispatchers.IO) { findOfflineTrackFile(server.id, track.id) }
        val savedAhead = if (offlineFile == null) withContext(Dispatchers.IO) { cachedSongFile(server.id, track.id) } else null
        val path = when {
            offlineFile != null -> offlineFile.absolutePath
            savedAhead != null -> savedAhead.also { it.setLastModified(System.currentTimeMillis()) }.absolutePath
            server.isDemoServer -> withContext(Dispatchers.Default) { ensureDryMasterTrackWav(track).absolutePath }
            else -> MusicSources.forServer(context, subsonicClient, server).playablePath(server, track)
                ?: throw java.io.FileNotFoundException("\"${track.title}\" is no longer on this phone")
        }
        return path to (offlineFile != null)
    }

    /**
     * Bitrate (kbps) of a lossless stream that couldn't keep up on this run. Songs at or above it get
     * a converted copy from the start; one that falls behind lowers it. Lossless is tried again
     * [LosslessRetryMs] after the last time one fell behind.
     */
    @Volatile
    private var losslessLimitKbps = Int.MAX_VALUE
    @Volatile
    private var losslessFellBehindAtMs = 0L

    private fun isRawStream(path: String, server: SubsonicServerConfig) =
        path.startsWith("http") && (server.transcodeFormat.isBlank() || server.transcodeFormat == "RAW")

    private fun kbpsOf(track: SubsonicTrack) =
        // Without a bitrate from the server: about what FLAC packs stereo PCM of that depth and rate into.
        track.bitRate.takeIf { it > 0 } ?: (track.bitDepth * track.samplingRate * 2 * 6 / 10_000)

    /** [server], or the same server sending Opus when this song's lossless stream can't keep up. */
    private fun streamServerFor(track: SubsonicTrack, server: SubsonicServerConfig): SubsonicServerConfig {
        val lossless = server.transcodeFormat.isBlank() || server.transcodeFormat == "RAW"
        if (SystemClock.elapsedRealtime() - losslessFellBehindAtMs > LosslessRetryMs) losslessLimitKbps = Int.MAX_VALUE
        return if (lossless && kbpsOf(track) >= losslessLimitKbps) server.copy(transcodeFormat = "OPUS", maxBitRate = 192) else server
    }

    /** How a song can play: decoded into the PCM pipeline, as a Dolby stream for the phone's engine, or not at all here. */
    private sealed class Playable {
        class Pcm(val source: PcmSource) : Playable()
        class Dolby(val audio: OpenedAudio, val output: DolbyPassthrough.Output) : Playable()
        /** A Dolby stream ([mime]) this phone has no decoder for. */
        class NoDecoder(val mime: String) : Playable()
        /** Only MediaPlayer may manage this one. */
        object None : Playable()

        fun release() {
            when (this) {
                is Pcm -> source.release()
                is Dolby -> audio.release()
                else -> Unit
            }
        }
    }

    /** What [openOrConvert] opened, from where, and with which server settings. */
    private class Opened(val playable: Playable, val path: String, val server: SubsonicServerConfig)

    /**
     * Opens [path]; a server song this device has no decoder for (Dolby Atmos on most tablets and
     * on phones without Dolby) plays from the server again, converted. AAC keeps Atmos's 5.1 bed;
     * MP3 is the stereo fallback. Not Opus: servers fail to convert 5.1 to it. Blocking: IO thread.
     */
    private suspend fun openOrConvert(path: String, track: SubsonicTrack, server: SubsonicServerConfig): Opened {
        val playable = openForPlayback(path, track, server)
        if (playable !is Playable.NoDecoder || track.isOnDevice || server.isDemoServer) return Opened(playable, path, server)
        for (converting in convertingServers(server)) {
            val url = runCatching { MusicSources.forServer(context, subsonicClient, converting).playablePath(converting, track) }
                .getOrNull()?.takeIf { it.startsWith("http") } ?: continue
            when (val converted = runCatching { openForPlayback(url, track, converting) }.getOrNull()) {
                null, Playable.None -> Unit
                is Playable.NoDecoder -> converted.release()
                else -> {
                    playable.release()
                    return Opened(converted, url, converting)
                }
            }
        }
        return Opened(playable, path, server)
    }

    /**
     * Opens [path] for playback. A Dolby stream is left untouched for the phone's Dolby engine when
     * that is on and the current output takes it; otherwise it is decoded like anything else, by
     * the phone's own Dolby decoder, and reported when the phone has none. Blocking: IO thread.
     */
    private fun openForPlayback(path: String, track: SubsonicTrack, server: SubsonicServerConfig): Playable {
        if (path.endsWith(".wav", ignoreCase = true) && File(path).exists()) {
            // A WAV this can't read (compressed, 64-bit float) goes to Android's extractor instead.
            runCatching { WavPcmSource(File(path)) }.getOrNull()?.let { return Playable.Pcm(it) }
        }
        // A stream the server converts isn't the FLAC file, and the probe would wait out the server's
        // conversion only to find that. A song the server says is 16-bit needs no probe either.
        val converted = path.startsWith("http") && server.transcodeFormat.isNotBlank() && server.transcodeFormat != "RAW"
        val mayBeHiRes = track.bitDepth == 0 || track.bitDepth > 16
        if (!converted && mayBeHiRes &&
            (track.suffix.equals("flac", ignoreCase = true) || path.endsWith(".flac", ignoreCase = true))
        ) {
            // A FLAC deeper than 16-bit is read here, since Android's extractor would cut it to 16-bit.
            FlacFrames.open(context, path)?.let { flac -> CodecPcmSource.from(flac)?.let { return Playable.Pcm(it) } }
        }
        val audio = OpenedAudio.open(context, path) ?: run {
            // Unopenable here. MediaPlayer gets its usual try, except for a Dolby song this phone
            // couldn't decode in any container.
            val dolbyMime = track.dolbyMime()
            return if (dolbyMime != null && dolbyDecodersFor(dolbyMime).isEmpty()) Playable.NoDecoder(dolbyMime) else Playable.None
        }
        val mime = audio.mime
        val isDolby = isDolbyMime(mime)
        val output = if (isDolby && dolbyPassthroughEnabled) DolbyPassthrough.outputFor(audio) else null
        val way = playPathFor(
            mime,
            passthroughOn = dolbyPassthroughEnabled,
            outputTakesIt = output != null,
            phoneDecodes = !isDolby || dolbyDecodersFor(mime).isNotEmpty()
        )
        return when (way) {
            PlayPath.DOLBY_ENGINE -> Playable.Dolby(audio, output!!)
            PlayPath.NO_DECODER -> {
                audio.release()
                Playable.NoDecoder(mime)
            }
            PlayPath.DECODE -> try {
                CodecPcmSource.from(audio)?.let { source ->
                    Playable.Pcm(
                        if (converted && path.contains("/rest/stream.view")) {
                            ReopeningStreamSource(source, track.durationSec * 1000L) { sec ->
                                val from = if (sec > 0L) "$path&timeOffset=$sec" else path
                                OpenedAudio.open(context, from)?.let { CodecPcmSource.from(it) }
                            }
                        } else source
                    )
                } ?: Playable.None
            } catch (e: NoDecoderException) {
                Playable.NoDecoder(e.mime)
            }
        }
    }

    /**
     * Plays [audio] (a Dolby stream) through the phone's own Dolby engine from [fromMs]. Ondalune's
     * DSP, gapless and crossfade don't apply; when the engine lets go of the stream (output change,
     * write error) the same song carries on decoded from where it was.
     */
    private fun startDolbyPassthrough(audio: OpenedAudio, output: DolbyPassthrough.Output, track: SubsonicTrack, path: String, fromMs: Long) {
        isPcmPipelineActive.set(false)
        _reverbProcessingMode.value = ReverbProcessingMode.BYPASSED_DRY
        val request = playRequest.get()
        lateinit var passthrough: DolbyPassthrough
        passthrough = DolbyPassthrough(audio, output, scope, object : DolbyPassthrough.Listener {
            override fun onPosition(positionMs: Long) {
                if (dolby === passthrough) _positionMs.value = positionMs.coerceIn(0L, _durationMs.value)
            }

            override fun onEnded() {
                scope.launch(Dispatchers.Main) {
                    if (dolby !== passthrough || request != playRequest.get()) return@launch
                    dolby = null
                    _isPlaying.value = false
                    _positionMs.value = _durationMs.value
                    onTrackEnded?.invoke()
                }
            }

            override fun onFailed(positionMs: Long, reason: String) {
                scope.launch(Dispatchers.Main) {
                    if (dolby === passthrough && request == playRequest.get()) fallBackToDecoding(track, path, positionMs)
                }
            }
        })
        dolby = passthrough
        passthrough.durationMs.takeIf { it > 1000L }?.let { _durationMs.value = it }
        _playingStream.value = PlayingStream(passthrough.stream, ownPipeline = false, outputSampleRate = passthrough.sampleRate, dolbyEngine = true)
        passthrough.setVolume(masterVolumeGain)
        passthrough.start(fromMs)
        _isBuffering.value = false
        _isPlaying.value = true
    }

    /** The Dolby engine let go of [track] at [positionMs]: decode it from there, or report it. Main thread. */
    private fun fallBackToDecoding(track: SubsonicTrack, path: String, positionMs: Long) {
        val request = playRequest.get()
        dolby?.stop()
        dolby = null
        _isBuffering.value = true
        scope.launch {
            val opened = withContext(Dispatchers.IO) { runCatching { OpenedAudio.open(context, path)?.let { CodecPcmSource.from(it) } } }
            if (request != playRequest.get()) {
                opened.getOrNull()?.release()
                return@launch
            }
            val source = opened.getOrNull()
            if (source == null) {
                val noDecoder = opened.exceptionOrNull() as? NoDecoderException
                reportUnplayable(track, if (noDecoder != null) cantDecode(noDecoder.mime) else "\"${track.title}\" stopped when the audio output changed")
                return@launch
            }
            source.seekTo(positionMs)
            _positionMs.value = positionMs
            startPcmPipeline(QueuedSource(track, source, track.durationSec * 1000L, replayGainLinear(track, replayGainMode)))
        }
    }

    private fun cantDecode(mime: String) = "This phone can't decode ${codecName(mime)}"

    /** [track] can't play here: say so ([message]) and leave it to the app to move on. */
    private fun reportUnplayable(track: SubsonicTrack, message: String) {
        _isBuffering.value = false
        _isPlaying.value = false
        _playingStream.value = null
        _audioSessionError.value = message
        onTrackUnplayable?.invoke(track, message)
    }

    /**
     * Real-time PCM pipeline: decoded audio -> [TransitionMixer] (ReplayGain, gapless hand-over,
     * crossfade) -> [processRealtimeStereoChunk] (EQ + Freeverb/Schroeder reverb + volume) ->
     * [AudioTrack]. All of it in float at the song's own rate, so a 24-bit or hi-res song reaches the
     * AudioTrack whole. Changing reverb, EQ, speed or pitch modifies the live stream immediately. Near
     * the end of a song the next one is opened in the background so it can follow without a gap.
     */
    private fun startPcmPipeline(first: QueuedSource) {
        isPcmPipelineActive.set(true)
        isPcmPaused.set(false)
        pendingSeekMs.set(-1L)
        updateReverbProcessingModeForPcmPath()
        val session = pipelineSession.get()

        pcmStreamingJob = scope.launch(Dispatchers.IO) {
            val mixer = TransitionMixer(first)
            val sampleRate = first.source.sampleRate
            var trackOut: AudioTrack? = null
            try {
                first.durationMs().takeIf { it > 1000L }?.let { _durationMs.value = it }

                // In bytes, 8 per float stereo frame: at least 2048 frames, as with 16-bit before.
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ).coerceAtLeast(16384)

                trackOut = runCatching {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }.getOrNull()

                audioTrack = trackOut
                audibleClock.restart()
                _playingStream.value = PlayingStream(first.source.stream, ownPipeline = true, outputSampleRate = sampleRate)
                trackOut?.let { at ->
                    applySpeedAndPitchToAudioTrack(at, _playbackSpeed.value, _playbackPitch.value)
                    runCatching { at.play() }
                }

                _isBuffering.value = false
                _isPlaying.value = true

                val chunkFrames = 1024
                val buffer = FloatArray(chunkFrames * 2)
                val result = TransitionMixer.Result()
                var stalledMs = 0L

                while (isActive && isPcmPipelineActive.get()) {
                    val seekReqMs = pendingSeekMs.getAndSet(-1L)
                    if (seekReqMs >= 0L) {
                        mixer.current.source.seekTo(seekReqMs)
                        audibleClock.seekApplied(sampleRate)
                        mixer.cancelFade()
                        reverbDspProcessor.clearDelayLines()
                    }

                    if (isPcmPaused.get()) {
                        delay(24L)
                        continue
                    }

                    syncUpcoming(mixer, session)
                    mixer.current.gain = replayGainLinear(mixer.current.track, replayGainMode)
                    mixer.next?.let { it.gain = replayGainLinear(it.track, replayGainMode) }
                    mixer.gapless = gaplessEnabled
                    mixer.crossfadeFrames = crossfadeFramesBetween(mixer.current, mixer.next)

                    val renderStart = System.nanoTime()
                    mixer.render(buffer, chunkFrames, result)
                    // Waiting on the network longer than the audio it gave is a gap you hear.
                    val waitedMs = (System.nanoTime() - renderStart) / 1_000_000L - result.frames * 1000L / sampleRate
                    if (waitedMs > 100L) streamWaitedAtMs.value = SystemClock.elapsedRealtime()
                    mixer.current.rawStreamFrom?.let { from ->
                        // A song being saved ahead took the line and stops now, so that wait isn't the stream's.
                        if (waitedMs > 100L && !savingAhead) stalledMs += waitedMs
                        if (stalledMs > LosslessStallLimitMs) {
                            val cur = mixer.current
                            losslessLimitKbps = min(losslessLimitKbps, kbpsOf(cur.track) * 4 / 5)
                            losslessFellBehindAtMs = SystemClock.elapsedRealtime()
                            Log.w("OndalunePcm", "Lossless stream fell behind ${stalledMs}ms: continuing \"${cur.track.title}\" as Opus")
                            val at = cur.positionMs()
                            withContext(Dispatchers.Main) { playTrack(cur.track, from, currentEqPreset, startMs = at) }
                            return@launch
                        }
                    }
                    if (result.frames > 0) {
                        // Real-time stereo PCM DSP (EQ + Freeverb/Schroeder Reverb + Volume)
                        processRealtimeStereoChunk(
                            samples = buffer,
                            frameCount = result.frames,
                            eqPreset = currentEqPreset,
                            sampleRate = sampleRate
                        )
                        // The backdrop that moves with the music reads the finished audio here,
                        // and only while something is on screen looking at it.
                        if (levelsWanted) measureLevels(buffer, result.frames, sampleRate)
                        // Counts are in floats, two to a frame.
                        val written = trackOut?.write(buffer, 0, result.frames * 2, AudioTrack.WRITE_BLOCKING) ?: 0
                        if (written > 0) audibleClock.wrote(written / 2, mixer.current.positionMs(), sampleRate)
                        // In local JVM unit tests where AudioTrack.write returns immediately without blocking:
                        if (written <= 0) {
                            val frameDurationMs = ((result.frames * 1000L) / sampleRate.toLong()).coerceAtLeast(8L)
                            val scaledDelay = (frameDurationMs / _playbackSpeed.value.coerceAtLeast(0.5f)).toLong()
                            delay(scaledDelay.coerceAtLeast(4L))
                        }
                    }

                    if (result.advanced) {
                        stalledMs = 0L
                        val now = mixer.current
                        _playingStream.value = PlayingStream(now.source.stream, ownPipeline = true, outputSampleRate = sampleRate)
                        _durationMs.value = now.durationMs().takeIf { it > 1000L } ?: (now.track.durationSec * 1000L)
                        _isPlayingOfflineFile.value = findOfflineTrackFile(now.track.serverId, now.track.id) != null
                        withContext(Dispatchers.Main) { onTrackAdvanced?.invoke(now.track) }
                    }
                    _positionMs.value = mixer.current.positionMs().coerceIn(0L, _durationMs.value)

                    if (result.ended) {
                        _isPlaying.value = false
                        _positionMs.value = _durationMs.value
                        withContext(Dispatchers.Main) {
                            onTrackEnded?.invoke()
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                if (isPcmPipelineActive.get()) {
                    _isBuffering.value = false
                    _isPlaying.value = false
                    _audioSessionError.value = e.localizedMessage ?: "PCM playback error"
                }
            } finally {
                runCatching { trackOut?.stop() }
                runCatching { trackOut?.release() }
                if (audioTrack === trackOut) {
                    audioTrack = null
                }
                mixer.current.source.release()
                mixer.next?.source?.release()
            }
        }
    }

    /**
     * Keeps the next song lined up: opens it in the background once the current one is within the
     * crossfade plus [PrepareLeadMs] of its end, hands it to the mixer, and drops it if the queue
     * changed meanwhile. Runs on the audio thread, so mixer sources are only released here or by it.
     */
    private fun syncUpcoming(mixer: TransitionMixer, session: Int) {
        val expected = expectedNextId
        if (expectationKnown && !mixer.isFading) {
            mixer.next?.takeIf { it.track.id != expected }?.let {
                it.source.release()
                mixer.next = null
            }
            preparedNext.get()?.takeIf { it.track.id != expected }?.let {
                if (preparedNext.compareAndSet(it, null)) it.source.release()
            }
        }
        if (mixer.next == null) {
            preparedNext.getAndSet(null)?.let { mixer.next = it }
        }
        if (!gaplessEnabled && crossfadeMs <= 0) return
        if (mixer.next != null || preparedNext.get() != null || prepareJob?.isActive == true) return
        if (expectationKnown && (expected == null || expected == unpreparableNextId)) return
        val leadFrames = (crossfadeMs + PrepareLeadMs).toLong() * mixer.current.source.sampleRate / 1000L
        if (mixer.current.remainingFrames > leadFrames) return
        val sampleRate = mixer.current.source.sampleRate
        prepareJob = scope.launch(Dispatchers.IO) { prepareNext(sampleRate, session) }
    }

    private suspend fun prepareNext(sampleRate: Int, session: Int) {
        val (track, requestedServer) = withContext(Dispatchers.Main) { nextTrackProvider?.invoke() } ?: return
        val server = streamServerFor(track, requestedServer)
        val resolved = runCatching { resolveSourcePath(track, server) }.getOrNull()
        val opened = resolved?.let { (path, _) -> runCatching { openOrConvert(path, track, server) }.getOrNull() }
        val playable = opened?.playable
        val source = (playable as? Playable.Pcm)?.source
        if (source == null || source.sampleRate != sampleRate) {
            // A different sample rate needs a new AudioTrack, and a Dolby stream for the phone's engine
            // can't be mixed in at all, so this one starts the usual way.
            playable?.release()
            unpreparableNextId = track.id
            return
        }
        val queued = QueuedSource(
            track, source, track.durationSec * 1000L, replayGainLinear(track, replayGainMode),
            rawStreamFrom = opened?.server?.takeIf { resolved?.second == false && isRawStream(opened.path, it) }
        )
        if (pipelineSession.get() != session || !preparedNext.compareAndSet(null, queued)) {
            source.release()
        }
    }

    /** Crossfade length in frames between [cur] and [next]; consecutive album tracks stay gapless. */
    private fun crossfadeFramesBetween(cur: QueuedSource, next: QueuedSource?): Int {
        if (next == null || crossfadeMs <= 0) return 0
        val sameAlbumInOrder = cur.track.albumId.isNotBlank() && cur.track.albumId == next.track.albumId &&
            next.track.trackNumber == cur.track.trackNumber + 1
        if (gaplessEnabled && sameAlbumInOrder) return 0
        val wanted = crossfadeMs.toLong() * cur.source.sampleRate / 1000L
        // Never longer than half of either song.
        return min(wanted, min(cur.durationFrames, next.durationFrames) / 2).toInt().coerceAtLeast(0)
    }

    private fun startMediaPlayerFallback(
        dataSourcePath: String,
        eqPreset: EqualizerPreset
    ) {
        isPcmPipelineActive.set(false)
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSourceOf(context, dataSourcePath)
            setOnPreparedListener { mp ->
                _isBuffering.value = false
                // MediaPlayer doesn't say reliably what it decoded (it can report its raw output), so
                // only the path is recorded.
                _playingStream.value = PlayingStream(decoded = null, ownPipeline = false, outputSampleRate = 0)
                val actualDur = mp.duration.toLong()
                if (actualDur > 1000L) {
                    _durationMs.value = actualDur
                }
                mp.setVolume(masterVolumeGain, masterVolumeGain)
                attachAndSyncAudioSessionEffects(mp.audioSessionId, eqPreset, _reverbState.value)
                applySpeedAndPitchToPlayer(mp, _playbackSpeed.value, _playbackPitch.value)
                mp.start()
                _isPlaying.value = true
                startProgressTicker()
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _positionMs.value = _durationMs.value
                onTrackEnded?.invoke()
            }
            setOnErrorListener { _, what, extra ->
                _isBuffering.value = false
                _isPlaying.value = false
                _reverbProcessingMode.value = ReverbProcessingMode.UNSUPPORTED_STREAM_ERROR
                _audioSessionError.value = "Stream error ($what/$extra)"
                true
            }
            prepareAsync()
        }
        mediaPlayer = player
    }

    @Volatile
    private var customEqBandsDb: List<Float> = EqualizerPreset.FLAT_STUDIO.bandsDb

    fun setEqualizerBandsDb(bandsDb: List<Float>) {
        if (bandsDb.size >= 5) {
            customEqBandsDb = bandsDb.map { it.coerceIn(-12f, 12f) }
        }
    }

    // EQ split-filter coefficients at the rate they were last worked out for.
    private var eqRate = 0
    private var eqLowCoeff = 0f
    private var eqHighCoeff = 0f

    // =========================================================================
    // Headphone crossfeed
    // =========================================================================

    /** Off by default; set from Settings. Read on the audio thread, written from the UI thread. */
    @Volatile
    var crossfeed: CrossfeedLevel = CrossfeedLevel.OFF

    private var cfMode = CrossfeedLevel.OFF
    private var cfRate = 0
    private var cfCoef = 0f
    private var cfGain = 0f
    private var cfNorm = 1f
    private var cfDelay = 0
    private val cfLineL = FloatArray(CrossfeedLineFrames)
    private val cfLineR = FloatArray(CrossfeedLineFrames)
    private var cfWrite = 0
    private var cfLpL = 0f
    private var cfLpR = 0f

    /**
     * Sends a little of each side to the other ear: quieter, a fraction of a millisecond later,
     * and with the top rolled off, which is what the far side of a head hears in a room.
     *
     * One delay line read, two one-pole filters and four multiply-adds per frame. Off, it returns
     * on the first line.
     */
    private fun applyCrossfeed(samples: FloatArray, frameCount: Int, sampleRate: Int) {
        val mode = crossfeed
        if (mode == CrossfeedLevel.OFF) {
            if (cfMode != CrossfeedLevel.OFF) resetCrossfeed()
            return
        }
        if (mode != cfMode || sampleRate != cfRate) {
            cfMode = mode
            cfRate = sampleRate
            cfCoef = (1.0 - exp(-2.0 * Math.PI * mode.cutoffHz / sampleRate)).toFloat().coerceIn(0f, 1f)
            cfGain = Math.pow(10.0, mode.feedDb / 20.0).toFloat()
            // Both ears gain the other side's signal, so pull the whole thing back to where it was.
            cfNorm = 1f / (1f + cfGain)
            cfDelay = ((mode.delayUs / 1_000_000f) * sampleRate)
                .toInt()
                .coerceIn(1, CrossfeedLineFrames - 1)
        }
        val size = CrossfeedLineFrames
        for (i in 0 until frameCount) {
            val left = samples[i * 2]
            val right = samples[i * 2 + 1]
            cfLineL[cfWrite] = left
            cfLineR[cfWrite] = right
            var read = cfWrite - cfDelay
            if (read < 0) read += size
            cfLpL += cfCoef * (cfLineL[read] - cfLpL)
            cfLpR += cfCoef * (cfLineR[read] - cfLpR)
            samples[i * 2] = (left + cfLpR * cfGain) * cfNorm
            samples[i * 2 + 1] = (right + cfLpL * cfGain) * cfNorm
            cfWrite++
            if (cfWrite >= size) cfWrite = 0
        }
    }

    /** Empties the delay line, so switching it off never leaves a tail of the last song behind. */
    internal fun resetCrossfeed() {
        cfMode = CrossfeedLevel.OFF
        cfRate = 0
        cfWrite = 0
        cfLpL = 0f
        cfLpR = 0f
        java.util.Arrays.fill(cfLineL, 0f)
        java.util.Arrays.fill(cfLineR, 0f)
    }

    // =========================================================================
    // Audio levels for the reactive backdrop
    // =========================================================================

    /**
     * Set by the player while a backdrop that moves with the music is on screen. False (the
     * default) and the measurement below never runs: one boolean test per buffer, nothing else.
     */
    @Volatile
    var levelsWanted: Boolean = false

    @Volatile private var levelLow = 0f
    @Volatile private var levelMid = 0f
    @Volatile private var levelHigh = 0f

    // Filter state, kept between buffers. Audio thread only.
    private var lvLow = 0f
    private var lvMid = 0f
    private var lvRate = 0
    private var lvCoefLow = 0f
    private var lvCoefMid = 0f

    /**
     * How loud the bass, the middle and the top of the music are right now, each 0..1 and already
     * smoothed. Read from the UI thread; the three values are each written whole, so the worst a
     * reader sees is one frame of a stale band.
     */
    fun audioLevels(out: FloatArray) {
        if (out.size < 3) return
        out[0] = levelLow
        out[1] = levelMid
        out[2] = levelHigh
    }

    /**
     * Measures the three bands from audio that has already been through the EQ and the reverb, so
     * the picture moves with what is actually heard.
     *
     * Every fourth frame is looked at, in mono, through two one-pole filters: no window, no FFT,
     * no allocation, a few hundred multiply-adds for a buffer of ten milliseconds. The envelope
     * rises quickly and falls slowly, which is what reads as movement rather than flicker.
     */
    internal fun measureLevels(samples: FloatArray, frameCount: Int, sampleRate: Int) {
        val step = 4
        val rate = (sampleRate / step).coerceAtLeast(1)
        if (rate != lvRate) {
            lvRate = rate
            lvCoefLow = (1.0 - exp(-2.0 * Math.PI * 250.0 / rate)).toFloat().coerceIn(0f, 1f)
            lvCoefMid = (1.0 - exp(-2.0 * Math.PI * 2000.0 / rate)).toFloat().coerceIn(0f, 1f)
        }
        var peakLow = 0f
        var peakMid = 0f
        var peakHigh = 0f
        var frame = 0
        while (frame < frameCount) {
            val i = frame * 2
            val mono = (samples[i] + samples[i + 1]) * 0.5f
            lvLow += lvCoefLow * (mono - lvLow)
            lvMid += lvCoefMid * (mono - lvMid)
            val low = abs(lvLow)
            val mid = abs(lvMid - lvLow)
            val high = abs(mono - lvMid)
            if (low > peakLow) peakLow = low
            if (mid > peakMid) peakMid = mid
            if (high > peakHigh) peakHigh = high
            frame += step
        }
        levelLow = follow(levelLow, (peakLow * LevelGainLow).coerceIn(0f, 1f))
        levelMid = follow(levelMid, (peakMid * LevelGainMid).coerceIn(0f, 1f))
        levelHigh = follow(levelHigh, (peakHigh * LevelGainHigh).coerceIn(0f, 1f))
    }

    private fun follow(current: Float, target: Float): Float {
        val coef = if (target > current) LevelAttack else LevelRelease
        val next = current + (target - current) * coef
        return if (next.isNaN()) 0f else next
    }

    /** Levels settle back to nothing when the music stops, so the picture comes to rest. */
    internal fun clearAudioLevels() {
        levelLow = 0f
        levelMid = 0f
        levelHigh = 0f
        lvLow = 0f
        lvMid = 0f
    }

    /**
     * Real-time in-place stereo float DSP used for every audio buffer before output. Applies:
     * 1. Live 5-band stereo tonal shaping ([eqPreset] / [customEqBandsDb])
     * 2. Live Schroeder/Freeverb stereo reverberation ([dsp])
     * 3. Output gain, then a clamp to full scale (the only limit on the way out)
     * Filters and reverb follow [sampleRate], so a 96 kHz song sounds as a 44.1 kHz one does.
     */
    fun processRealtimeStereoChunk(
        samples: FloatArray,
        frameCount: Int = samples.size / 2,
        eqPreset: EqualizerPreset = currentEqPreset,
        sampleRate: Int = 44100,
        dsp: OndaluneReverbDspProcessor = reverbDspProcessor,
        reverbState: ReverbState = _reverbState.value
    ) {
        val safeFrames = minOf(frameCount, samples.size / 2)
        if (safeFrames <= 0) return

        dsp.updateState(reverbState, sampleRate)

        val bands = if (customEqBandsDb.size >= 5 && customEqBandsDb.any { it != 0f }) {
            customEqBandsDb
        } else {
            eqPreset.bandsDb
        }
        val eqActive = bands.any { kotlin.math.abs(it) > 0.05f }
        if (eqActive) {
            if (sampleRate != eqRate) {
                // Tuned at 44.1 kHz (corners near 590 Hz and 3.4 kHz); kept there at any rate.
                eqRate = sampleRate
                eqLowCoeff = onePoleCoeffAt(0.08f, sampleRate)
                eqHighCoeff = onePoleCoeffAt(0.38f, sampleRate)
            }
            val lowGainLinear = 1.0f + (bands[0] / 18.0f)
            val midGainLinear = 1.0f + (bands[2] / 20.0f)
            val highGainLinear = 1.0f + (bands[4] / 18.0f)
            val a = eqLowCoeff
            val b = eqHighCoeff
            for (i in 0 until safeFrames) {
                val left = samples[i * 2]
                val right = samples[i * 2 + 1]
                // First-order low/high split filter for zero-phase-distortion real-time EQ
                lowStateL += a * (left - lowStateL)
                lowStateR += a * (right - lowStateR)
                highStateL += b * (left - highStateL)
                highStateR += b * (right - highStateR)

                val trebleL = left - highStateL
                val trebleR = right - highStateR
                val midL = highStateL - lowStateL
                val midR = highStateR - lowStateR

                samples[i * 2] = (lowStateL * lowGainLinear) + (midL * midGainLinear) + (trebleL * highGainLinear)
                samples[i * 2 + 1] = (lowStateR * lowGainLinear) + (midR * midGainLinear) + (trebleR * highGainLinear)
            }
        }

        // Run through the custom stereo Freeverb/Schroeder DSP engine in real time
        dsp.processStereoInterleavedFloat(samples, safeFrames)

        // Crossfeed sits last in the chain, over the finished stereo picture, where a pair of
        // speakers in a room would sit.
        applyCrossfeed(samples, safeFrames, sampleRate)

        val gain = masterVolumeGain.coerceIn(0f, 1f)
        for (i in 0 until safeFrames * 2) {
            samples[i] = (samples[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        _realtimeProcessedFrames.value += safeFrames.toLong()
    }

    fun togglePlayPause() {
        dolby?.let { stream ->
            if (stream.isPaused) {
                requestSystemAudioFocus()
                stream.resume()
                _isPlaying.value = true
            } else {
                stream.pause()
                _isPlaying.value = false
            }
            return
        }
        if (isPcmPipelineActive.get()) {
            val currentlyPaused = isPcmPaused.get()
            if (currentlyPaused) {
                requestSystemAudioFocus()
                isPcmPaused.set(false)
                runCatching { audioTrack?.play() }
                _isPlaying.value = true
            } else {
                isPcmPaused.set(true)
                runCatching { audioTrack?.pause() }
                _isPlaying.value = false
            }
            return
        }

        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                requestSystemAudioFocus()
                mp.setVolume(masterVolumeGain, masterVolumeGain)
                applySpeedAndPitchToPlayer(mp, _playbackSpeed.value, _playbackPitch.value)
                applyLiveReverbToActiveSession(_reverbState.value)
                mp.start()
                _isPlaying.value = true
                startProgressTicker()
            }
        } catch (_: Exception) {
        }
    }

    fun pausePlayback() {
        dolby?.let {
            it.pause()
            masterVolumeGain = 1.0f
            it.setVolume(1.0f)
            _isPlaying.value = false
            return
        }
        if (isPcmPipelineActive.get()) {
            isPcmPaused.set(true)
            runCatching { audioTrack?.pause() }
            masterVolumeGain = 1.0f
            _isPlaying.value = false
            return
        }

        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
            }
            mp.setVolume(1.0f, 1.0f)
            masterVolumeGain = 1.0f
            _isPlaying.value = false
        } catch (_: Exception) {
        }
    }

    fun setOutputVolume(volume: Float) {
        val clean = volume.coerceIn(0.0f, 1.0f)
        masterVolumeGain = clean
        dolby?.setVolume(clean)
        try {
            audioTrack?.setVolume(clean)
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.setVolume(clean, clean)
        } catch (_: Exception) {
        }
    }

    fun seekTo(targetMs: Long) {
        val clamped = targetMs.coerceIn(0L, _durationMs.value)
        _positionMs.value = clamped
        reverbDspProcessor.clearDelayLines()
        dolby?.let {
            it.seekTo(clamped)
            return
        }
        if (isPcmPipelineActive.get()) {
            audibleClock.seekRequested(clamped)
            pendingSeekMs.set(clamped)
            return
        }
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
    }

    /**
     * The position leaving the speaker. [positionMs] is the decoder's read head, which runs ahead of
     * it by whatever the AudioTrack still holds; lyrics follow this one. It only reads the
     * AudioTrack's counters. Main thread.
     */
    fun audiblePositionMs(): Long {
        dolby?.let { return runCatching { it.audiblePositionMs() }.getOrDefault(_positionMs.value) }
        val track = audioTrack
        if (track == null || !isPcmPipelineActive.get()) {
            return runCatching { mediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong() }.getOrNull() ?: _positionMs.value
        }
        return runCatching {
            val playing = track.playState == AudioTrack.PLAYSTATE_PLAYING
            val now = System.nanoTime()
            if (track !== audibleStampTrack) {
                audibleStampTrack = track
                audibleStampOk = false
                audibleStampReadNanos = 0L
            }
            if (playing && now - audibleStampReadNanos > AudibleStampRefreshNanos) {
                audibleStampOk = track.getTimestamp(audibleStamp)
                audibleStampReadNanos = now
            }
            audibleClock.positionMs(
                sampleRate = track.sampleRate,
                head = track.playbackHeadPosition,
                playing = playing,
                stampFrames = if (audibleStampOk) audibleStamp.framePosition else -1L,
                stampAgeNanos = now - audibleStamp.nanoTime,
                speed = _playbackSpeed.value
            )
        }.getOrDefault(_positionMs.value)
    }

    /**
     * Speed (0.80x..2.00x) and Pitch (0.50x..2.00x) controller.
     * Operates simultaneously with Live Reverb without resetting or interrupting reverb state.
     */
    fun setSpeedAndPitch(speed: Float, pitch: Float) {
        val cleanSpeed = if (speed.isNaN()) 1.0f else speed.coerceIn(0.80f, 2.00f)
        val cleanPitch = if (pitch.isNaN()) 1.0f else pitch.coerceIn(0.50f, 2.00f)
        _playbackSpeed.value = cleanSpeed
        _playbackPitch.value = cleanPitch

        audioTrack?.let { at ->
            applySpeedAndPitchToAudioTrack(at, cleanSpeed, cleanPitch)
        }
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            applySpeedAndPitchToPlayer(mp, cleanSpeed, cleanPitch)
        }
    }

    /**
     * Updates the Live Reverb DSP state in real time without restarting or interrupting playback.
     */
    fun setReverbState(newState: ReverbState) {
        val sanitized = newState.sanitized()
        _reverbState.value = sanitized
        reverbDspProcessor.updateParameters(sanitized, snapImmediately = false)
        if (isPcmPipelineActive.get()) {
            updateReverbProcessingModeForPcmPath()
        } else {
            applyLiveReverbToActiveSession(sanitized)
        }
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        val next = ReverbState.fromPreset(preset)
        setReverbState(next)
    }

    fun setEqualizerPresetLive(eqPreset: EqualizerPreset) {
        currentEqPreset = eqPreset
        runCatching {
            val eq = hardwareEqualizer ?: return
            eq.enabled = eqPreset != EqualizerPreset.FLAT_STUDIO
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            for (b in 0 until minOf(numBands, eqPreset.bandsDb.size)) {
                val targetMb = (eqPreset.bandsDb[b] * 100f).toInt().coerceIn(minMb, maxMb).toShort()
                eq.setBandLevel(b.toShort(), targetMb)
            }
        }
    }

    /**
     * Processes a stereo interleaved floating-point block through the Live Reverb DSP pipeline.
     */
    fun processLiveDspStereoBlock(
        stereoInterleaved: FloatArray,
        frameCount: Int = stereoInterleaved.size / 2
    ) {
        reverbDspProcessor.processStereoInterleavedFloat(stereoInterleaved, frameCount)
    }

    private fun updateReverbProcessingModeForPcmPath() {
        _reverbProcessingMode.value = if (_reverbState.value.isActivelyProcessing) {
            ReverbProcessingMode.CUSTOM_REALTIME_PCM_DSP
        } else {
            ReverbProcessingMode.BYPASSED_DRY
        }
    }

    private fun attachAndSyncAudioSessionEffects(
        audioSessionId: Int,
        eqPreset: EqualizerPreset,
        reverb: ReverbState
    ) {
        if (audioSessionId <= 0) return
        releaseEffectsInternal()
        runCatching {
            hardwareEqualizer = Equalizer(0, audioSessionId).apply {
                enabled = eqPreset != EqualizerPreset.FLAT_STUDIO
            }
            setEqualizerPresetLive(eqPreset)
        }
        runCatching {
            environmentalReverb = EnvironmentalReverb(0, audioSessionId)
        }.onFailure {
            runCatching {
                presetReverbFallback = PresetReverb(0, audioSessionId)
            }
        }
        applyLiveReverbToActiveSession(reverb)
    }

    private fun applyLiveReverbToActiveSession(reverb: ReverbState) {
        if (!reverb.isActivelyProcessing) {
            _reverbProcessingMode.value = ReverbProcessingMode.BYPASSED_DRY
        }
        val hwParams = OndaluneReverbDspProcessor.toEnvironmentalReverbParams(reverb)
        runCatching {
            val env = environmentalReverb
            if (env != null) {
                env.roomLevel = hwParams.roomLevelMillibels
                env.roomHFLevel = hwParams.roomHfLevelMillibels
                env.decayTime = hwParams.decayTimeMs
                env.decayHFRatio = hwParams.decayHfRatioPermille
                env.reflectionsLevel = hwParams.reflectionsLevelMillibels
                env.reflectionsDelay = hwParams.reflectionsDelayMs
                env.reverbLevel = hwParams.reverbLevelMillibels
                env.reverbDelay = hwParams.reverbDelayMs
                env.diffusion = hwParams.diffusionPermille
                env.density = hwParams.densityPermille
                env.enabled = hwParams.enabled
                _reverbProcessingMode.value = if (hwParams.enabled) {
                    ReverbProcessingMode.HARDWARE_AUX_FALLBACK
                } else {
                    ReverbProcessingMode.BYPASSED_DRY
                }
                return
            }
        }
        runCatching {
            val presetRev = presetReverbFallback ?: return
            if (!hwParams.enabled) {
                presetRev.preset = PresetReverb.PRESET_NONE
                presetRev.enabled = false
                _reverbProcessingMode.value = ReverbProcessingMode.BYPASSED_DRY
            } else {
                presetRev.preset = when {
                    reverb.roomSize < 0.38f -> PresetReverb.PRESET_SMALLROOM
                    reverb.roomSize < 0.62f -> PresetReverb.PRESET_MEDIUMROOM
                    reverb.roomSize < 0.80f -> PresetReverb.PRESET_MEDIUMHALL
                    else -> PresetReverb.PRESET_LARGEHALL
                }
                presetRev.enabled = true
                _reverbProcessingMode.value = ReverbProcessingMode.HARDWARE_AUX_FALLBACK
            }
        }
    }

    private fun applySpeedAndPitchToAudioTrack(at: AudioTrack, speed: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams()
                    .allowDefaults()
                    .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                    .setSpeed(speed)
                    .setPitch(pitch)
                at.playbackParams = params
            } catch (_: Exception) {
            }
        }
    }

    private fun applySpeedAndPitchToPlayer(mp: MediaPlayer, speed: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = PlaybackParams()
                    .allowDefaults()
                    .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                    .setSpeed(speed)
                    .setPitch(pitch)
                mp.playbackParams = params
            } catch (_: Exception) {
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value && !isPcmPipelineActive.get()) {
                try {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val nextPos = mp.currentPosition.toLong().coerceAtLeast(0L)
                        if (kotlin.math.abs(nextPos - _positionMs.value) >= 48L || nextPos == 0L) {
                            _positionMs.value = nextPos
                        }
                    }
                } catch (_: Exception) {
                }
                delay(64L)
            }
        }
    }

    private fun releaseEffectsInternal() {
        runCatching { environmentalReverb?.release() }
        environmentalReverb = null
        runCatching { presetReverbFallback?.release() }
        presetReverbFallback = null
        runCatching { hardwareEqualizer?.release() }
        hardwareEqualizer = null
    }

    private fun releasePlayerInternal() {
        isPcmPipelineActive.set(false)
        isPcmPaused.set(false)
        pipelineSession.incrementAndGet()
        prepareJob?.cancel()
        prepareJob = null
        preparedNext.getAndSet(null)?.source?.release()
        unpreparableNextId = null
        pcmStreamingJob?.cancel()
        pcmStreamingJob = null
        progressJob?.cancel()
        progressJob = null
        releaseEffectsInternal()
        dolby?.stop()
        dolby = null
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _isPlaying.value = false
    }

    fun release() {
        releasePlayerInternal()
        unregisterNoisyReceiverIfNeeded()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            }
        }
    }

    /**
     * Synthesizes a pure, uncolored dry 16-bit stereo PCM WAV master (`${track.id}_DRY_MASTER.wav`).
     * Reverb and EQ are never baked into this file; instead, they are applied in real time on the
     * live PCM stream by [processRealtimeStereoChunk].
     */
    fun ensureDryMasterTrackWav(track: SubsonicTrack): File {
        val cacheDir = File(context.cacheDir, "ondalune_synth")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val outFile = File(cacheDir, "${track.id}_DRY_MASTER.wav")
        if (outFile.exists() && outFile.length() > 4096) {
            return outFile
        }

        val sampleRate = 22050
        val durationSec = track.durationSec.coerceIn(20, 45)
        val numSamples = sampleRate * durationSec
        val numChannels = 2
        val dataSize = numSamples * numChannels * 2

        val chordRoots = when (track.artStyleIndex % 5) {
            0 -> doubleArrayOf(174.61, 130.81, 155.56, 116.54)
            1 -> doubleArrayOf(155.56, 207.65, 130.81, 196.00)
            2 -> doubleArrayOf(146.83, 174.61, 196.00, 164.81)
            3 -> doubleArrayOf(220.00, 174.61, 130.81, 196.00)
            else -> doubleArrayOf(130.81, 164.81, 146.83, 196.00)
        }

        val arpeggioMultipliers = doubleArrayOf(1.0, 1.2, 1.5, 2.0, 1.5, 1.25, 2.0, 2.4)
        val beatDurationSec = 60.0 / track.bpm.coerceIn(85, 140).toDouble()

        FileOutputStream(outFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * numChannels * 2)
            header.putShort((numChannels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chunkFrames = 2048
            val pcmBuffer = ByteBuffer.allocate(chunkFrames * numChannels * 2).order(ByteOrder.LITTLE_ENDIAN)
            var frameIndex = 0

            while (frameIndex < numSamples) {
                pcmBuffer.clear()
                val framesToWrite = minOf(chunkFrames, numSamples - frameIndex)
                for (i in 0 until framesToWrite) {
                    val sampleIdx = frameIndex + i
                    val t = sampleIdx.toDouble() / sampleRate.toDouble()

                    val masterEnv = when {
                        t < 1.2 -> t / 1.2
                        t > durationSec - 2.0 -> ((durationSec - t) / 2.0).coerceAtLeast(0.0)
                        else -> 1.0
                    }

                    val chordIdx = ((t / (beatDurationSec * 4.0)).toInt()) % chordRoots.size
                    val rootHz = chordRoots[chordIdx]

                    val padL = 0.20 * sin(2.0 * PI * rootHz * t) +
                        0.14 * sin(2.0 * PI * (rootHz * 1.2) * t + 0.3) +
                        0.12 * sin(2.0 * PI * (rootHz * 1.498) * t)
                    val padR = 0.20 * sin(2.0 * PI * (rootHz * 1.002) * t) +
                        0.14 * sin(2.0 * PI * (rootHz * 1.2) * t - 0.3) +
                        0.12 * sin(2.0 * PI * (rootHz * 1.501) * t)

                    val beatPhase = (t % beatDurationSec) / beatDurationSec
                    val bassEnv = exp(-3.8 * beatPhase)
                    val subBass = 0.26 * bassEnv * sin(2.0 * PI * (rootHz * 0.5) * t)

                    val eighthDuration = beatDurationSec * 0.5
                    val arpStep = ((t / eighthDuration).toInt()) % arpeggioMultipliers.size
                    val arpPhase = (t % eighthDuration) / eighthDuration
                    val arpEnv = exp(-5.5 * arpPhase)
                    val arpHz = rootHz * arpeggioMultipliers[arpStep]
                    val arpSignal = 0.16 * arpEnv * (
                        sin(2.0 * PI * arpHz * t) + 0.35 * sin(2.0 * PI * arpHz * 2.0 * t)
                        )
                    val panLeft = if (arpStep % 2 == 0) 1.15 else 0.75
                    val panRight = if (arpStep % 2 == 0) 0.75 else 1.15

                    val leftVal = ((padL + subBass + arpSignal * panLeft) * masterEnv)
                        .coerceIn(-0.95, 0.95)
                    val rightVal = ((padR + subBass + arpSignal * panRight) * masterEnv)
                        .coerceIn(-0.95, 0.95)

                    pcmBuffer.putShort((leftVal * 32767.0).toInt().toShort())
                    pcmBuffer.putShort((rightVal * 32767.0).toInt().toShort())
                }
                fos.write(pcmBuffer.array(), 0, framesToWrite * numChannels * 2)
                frameIndex += framesToWrite
            }
        }
        return outFile
    }
}

/** How long before a song ends (plus the crossfade) the next one is opened and buffered. */
private const val PrepareLeadMs = 12_000

/** Gaps from a lossless stream waiting on the network, summed over a song, before it goes converted. */
private const val LosslessStallLimitMs = 2_000L
private const val LosslessRetryMs = 15 * 60_000L
/** [server] converting a song this device has no decoder for, in the order to try. */
internal fun convertingServers(server: SubsonicServerConfig): List<SubsonicServerConfig> =
    listOf("AAC", "MP3").map { server.copy(transcodeFormat = it, maxBitRate = 320) }
private const val QuietLineMs = 10_000L

/** How often the AudioTrack is asked for a fresh timestamp; between asks the last one is carried forward. */
private const val AudibleStampRefreshNanos = 250_000_000L

// Backdrop levels. Music rarely peaks near full scale in any one band, so each is lifted into a
// range the eye can read, then clamped. The envelope rises in about three buffers and falls over
// about a dozen: movement, not flicker.
private const val LevelGainLow = 3.2f
private const val LevelGainMid = 5.0f
private const val LevelGainHigh = 7.5f
private const val LevelAttack = 0.45f
private const val LevelRelease = 0.08f

/** Long enough for any crossfeed delay at 192 kHz, and small enough to stay in cache. */
private const val CrossfeedLineFrames = 128

/** Songs coming up that are on the phone already ([ready] of [total]); [active] while more are being saved. */
data class SongsAhead(val ready: Int = 0, val total: Int = 0, val active: Boolean = false)
