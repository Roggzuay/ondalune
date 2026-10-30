package io.github.roggzuay.ondalune.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Build
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbProcessingMode
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var pcmStreamingJob: Job? = null
    private var progressJob: Job? = null

    private var environmentalReverb: EnvironmentalReverb? = null
    private var presetReverbFallback: PresetReverb? = null
    private var hardwareEqualizer: Equalizer? = null

    val reverbDspProcessor = OndaluneReverbDspProcessor(sampleRate = 44100)

    private val isPcmPipelineActive = AtomicBoolean(false)
    private val isPcmPaused = AtomicBoolean(false)
    private val pendingSeekMs = AtomicLong(-1L)

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
     * Counter of stereo PCM frames processed in real time by [processRealtimePcm16StereoChunk]
     * during active playback, enabling deterministic verification that the custom DSP pipeline is
     * actively processing the stream rather than baking effects into static files.
     */
    private val _realtimeProcessedFrames = MutableStateFlow(0L)
    val realtimeProcessedFrames: StateFlow<Long> = _realtimeProcessedFrames.asStateFlow()

    var onTrackEnded: (() -> Unit)? = null

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

    fun playTrack(
        track: SubsonicTrack,
        server: SubsonicServerConfig,
        eqPreset: EqualizerPreset
    ) {
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
                _isPlayingOfflineFile.value = offline
                // Real-time PCM DSP pipeline first; MediaPlayer + hardware session effects if the
                // container can't be decoded to raw PCM.
                val source = withContext(Dispatchers.IO) { openPcmSource(sourcePath) }
                if (source != null) {
                    startPcmPipeline(
                        QueuedSource(track, source, track.durationSec * 1000L, replayGainLinear(track, replayGainMode))
                    )
                } else {
                    startMediaPlayerFallback(sourcePath, eqPreset)
                }
            } catch (e: Exception) {
                _isBuffering.value = false
                _isPlaying.value = false
                _reverbProcessingMode.value = ReverbProcessingMode.UNSUPPORTED_STREAM_ERROR
                _audioSessionError.value = e.localizedMessage ?: "Audio initialization error"
            }
        }
    }

    /** Where [track] plays from (downloaded file, the demo's dry WAV master, or the server stream) and whether it's offline. */
    private suspend fun resolveSourcePath(track: SubsonicTrack, server: SubsonicServerConfig): Pair<String, Boolean> {
        val offlineFile = withContext(Dispatchers.IO) { findOfflineTrackFile(server.id, track.id) }
        val path = when {
            offlineFile != null -> offlineFile.absolutePath
            server.isDemoServer -> withContext(Dispatchers.Default) { ensureDryMasterTrackWav(track).absolutePath }
            else -> subsonicClient.buildStreamUrl(server, track.id)
        }
        return path to (offlineFile != null)
    }

    /** Opens [path] for the PCM pipeline, or null if only MediaPlayer can play it. */
    private fun openPcmSource(path: String): PcmSource? =
        if (path.endsWith(".wav", ignoreCase = true) && File(path).exists()) {
            runCatching { WavPcmSource(File(path)) }.getOrNull()
        } else {
            CodecPcmSource.open(path)
        }

    /**
     * Real-time PCM pipeline: decoded audio -> [TransitionMixer] (ReplayGain, gapless hand-over,
     * crossfade) -> [processRealtimePcm16StereoChunk] (EQ + Freeverb/Schroeder reverb + volume) ->
     * [AudioTrack]. Changing reverb, EQ, speed or pitch modifies the live stream immediately. Near
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

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)

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
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }.getOrNull()

                audioTrack = trackOut
                trackOut?.let { at ->
                    applySpeedAndPitchToAudioTrack(at, _playbackSpeed.value, _playbackPitch.value)
                    runCatching { at.play() }
                }

                _isBuffering.value = false
                _isPlaying.value = true

                val chunkFrames = 1024
                val buffer = ShortArray(chunkFrames * 2)
                val result = TransitionMixer.Result()

                while (isActive && isPcmPipelineActive.get()) {
                    val seekReqMs = pendingSeekMs.getAndSet(-1L)
                    if (seekReqMs >= 0L) {
                        mixer.current.source.seekTo(seekReqMs)
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

                    mixer.render(buffer, chunkFrames, result)
                    if (result.frames > 0) {
                        // Real-time stereo PCM DSP (EQ + Freeverb/Schroeder Reverb + Volume)
                        processRealtimePcm16StereoChunk(
                            pcm16Stereo = buffer,
                            frameCount = result.frames,
                            eqPreset = currentEqPreset
                        )
                        val written = trackOut?.write(buffer, 0, result.frames * 2) ?: 0
                        // In local JVM unit tests where AudioTrack.write returns immediately without blocking:
                        if (written <= 0) {
                            val frameDurationMs = ((result.frames * 1000L) / sampleRate.toLong()).coerceAtLeast(8L)
                            val scaledDelay = (frameDurationMs / _playbackSpeed.value.coerceAtLeast(0.5f)).toLong()
                            delay(scaledDelay.coerceAtLeast(4L))
                        }
                    }

                    if (result.advanced) {
                        val now = mixer.current
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
        val (track, server) = withContext(Dispatchers.Main) { nextTrackProvider?.invoke() } ?: return
        val source = runCatching { openPcmSource(resolveSourcePath(track, server).first) }.getOrNull()
        if (source == null || source.sampleRate != sampleRate) {
            // A different sample rate needs a new AudioTrack, so this one starts the usual way.
            source?.release()
            unpreparableNextId = track.id
            return
        }
        val queued = QueuedSource(track, source, track.durationSec * 1000L, replayGainLinear(track, replayGainMode))
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
            setDataSource(dataSourcePath)
            setOnPreparedListener { mp ->
                _isBuffering.value = false
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

    /**
     * Real-time in-place stereo 16-bit PCM DSP processor used for every audio buffer before output.
     * Applies:
     * 1. Live 5-band stereo tonal shaping ([eqPreset] / [customEqBandsDb])
     * 2. Live 64-bit internal Schroeder/Freeverb stereo reverberation ([reverbDspProcessor])
     * 3. Smooth output gain & soft-knee saturation guard
     */
    fun processRealtimePcm16StereoChunk(
        pcm16Stereo: ShortArray,
        frameCount: Int = pcm16Stereo.size / 2,
        eqPreset: EqualizerPreset = currentEqPreset,
        sampleRate: Int = 22050,
        dsp: OndaluneReverbDspProcessor = reverbDspProcessor,
        reverbState: ReverbState = _reverbState.value
    ) {
        val safeFrames = minOf(frameCount, pcm16Stereo.size / 2)
        if (safeFrames <= 0) return

        dsp.updateState(reverbState, sampleRate)

        val floatInterleaved = FloatArray(safeFrames * 2)
        val invShort = 1.0f / 32768.0f
        val bands = if (customEqBandsDb.size >= 5 && customEqBandsDb.any { it != 0f }) {
            customEqBandsDb
        } else {
            eqPreset.bandsDb
        }
        val eqActive = bands.any { kotlin.math.abs(it) > 0.05f }
        val lowGainLinear = 1.0f + (bands[0] / 18.0f)
        val midGainLinear = 1.0f + (bands[2] / 20.0f)
        val highGainLinear = 1.0f + (bands[4] / 18.0f)

        for (i in 0 until safeFrames) {
            var left = pcm16Stereo[i * 2] * invShort
            var right = pcm16Stereo[i * 2 + 1] * invShort

            if (eqActive) {
                // First-order low/high split filter for zero-phase-distortion real-time EQ
                lowStateL += 0.08f * (left - lowStateL)
                lowStateR += 0.08f * (right - lowStateR)
                highStateL += 0.38f * (left - highStateL)
                highStateR += 0.38f * (right - highStateR)

                val bassL = lowStateL
                val bassR = lowStateR
                val trebleL = left - highStateL
                val trebleR = right - highStateR
                val midL = highStateL - lowStateL
                val midR = highStateR - lowStateR

                left = (bassL * lowGainLinear) + (midL * midGainLinear) + (trebleL * highGainLinear)
                right = (bassR * lowGainLinear) + (midR * midGainLinear) + (trebleR * highGainLinear)
            }

            floatInterleaved[i * 2] = left
            floatInterleaved[i * 2 + 1] = right
        }

        // Run through the custom stereo Freeverb/Schroeder DSP engine in real time
        dsp.processStereoInterleavedFloat(floatInterleaved, safeFrames)

        val gain = masterVolumeGain.coerceIn(0f, 1f)
        for (i in 0 until safeFrames * 2) {
            val scaled = (floatInterleaved[i] * gain).coerceIn(-1.0f, 1.0f)
            pcm16Stereo[i] = (scaled * 32767.0f).toInt().toShort()
        }

        _realtimeProcessedFrames.value += safeFrames.toLong()
    }

    fun togglePlayPause() {
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
        if (isPcmPipelineActive.get()) {
            pendingSeekMs.set(clamped)
            return
        }
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
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
     * live PCM stream by [processRealtimePcm16StereoChunk].
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
