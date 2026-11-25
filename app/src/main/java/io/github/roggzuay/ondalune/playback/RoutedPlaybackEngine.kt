package io.github.roggzuay.ondalune.playback

import io.github.roggzuay.ondalune.cast.CastGateway
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.isOnDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the music actually comes out: this phone, or something else on the network.
 *
 * The queue above this has no idea which it is. It asks for a song to play, and this decides
 * whether that means Ondalune's own audio engine or a Cast device fetching the song from your
 * server for itself.
 *
 * **What casting costs.** A Cast receiver plays the file, so the speed, the pitch, the reverb,
 * the equalizer, the crossfeed, the vinyl and the crossfade are all Ondalune's and all stop
 * applying while the music is playing elsewhere. The app says so where it happens.
 *
 * **What cannot be cast.** Music stored on this phone has no address a device on the network can
 * fetch, so those songs stay here, and the app says that too rather than failing quietly.
 */
class RoutedPlaybackEngine(
    private val local: PlaybackEngine,
    private val cast: CastGateway,
    /** The URL a device on the network can fetch this song from; null when there isn't one. */
    private val streamUrlFor: suspend (SubsonicTrack, SubsonicServerConfig) -> String?,
    private val coverUrlFor: (SubsonicTrack) -> String,
    private val scope: CoroutineScope,
    private val showBanner: (String) -> Unit
) : PlaybackEngine {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /** What the device is playing, so a disconnect can pick it up where it left off. */
    private var castingTrack: SubsonicTrack? = null
    private var castingServer: SubsonicServerConfig? = null
    private var ticker: Job? = null
    private var loadJob: Job? = null

    /** True while the music is coming out of something other than this phone. */
    val casting: Boolean get() = cast.state.value.connected

    init {
        scope.launch {
            cast.state.collect { state ->
                if (state.connected) startTicking() else stopTicking()
                if (state.connected) _isPlaying.value = state.playing
            }
        }
        scope.launch {
            local.isPlaying.collect { playing -> if (!casting) _isPlaying.value = playing }
        }
        scope.launch {
            local.positionMs.collect { position -> if (!casting) _positionMs.value = position }
        }
    }

    /**
     * A device joined. Whatever was playing here moves there, from where it had got to, and this
     * phone goes quiet.
     */
    fun onCastConnected(current: SubsonicTrack?, server: SubsonicServerConfig?, positionMs: Long, wasPlaying: Boolean) {
        local.pause()
        val name = cast.state.value.deviceName.ifBlank { "the other device" }
        if (current == null || server == null) {
            showBanner("Playing on $name")
            return
        }
        if (current.isOnDevice) {
            showBanner("Music on this phone can't be cast; it keeps playing here")
            return
        }
        showBanner("Playing on $name. The sound engine stays here.")
        play(current, server, EqualizerPreset.FLAT_STUDIO, positionMs, wasPlaying)
    }

    /** The device left. The song comes back here, at the point the device had reached. */
    fun onCastDisconnected(resume: (SubsonicTrack, SubsonicServerConfig, Long) -> Unit) {
        val track = castingTrack
        val server = castingServer
        val at = _positionMs.value
        castingTrack = null
        castingServer = null
        stopTicking()
        if (track != null && server != null) resume(track, server, at)
    }

    override fun play(track: SubsonicTrack, server: SubsonicServerConfig, eq: EqualizerPreset) =
        play(track, server, eq, startMs = 0L, autoplay = true)

    private fun play(
        track: SubsonicTrack,
        server: SubsonicServerConfig,
        eq: EqualizerPreset,
        startMs: Long,
        autoplay: Boolean
    ) {
        if (!casting) {
            castingTrack = null
            local.play(track, server, eq)
            // The local engine starts from the beginning; honour a position if one was asked for.
            if (startMs > 0L) local.seekTo(startMs)
            if (!autoplay) local.pause()
            return
        }
        if (track.isOnDevice) {
            showBanner("\"${track.title}\" is on this phone, so it can't be cast")
            return
        }
        castingTrack = track
        castingServer = server
        _positionMs.value = startMs
        loadJob?.cancel()
        loadJob = scope.launch {
            val url = runCatching { streamUrlFor(track, server) }.getOrNull()
            if (url.isNullOrBlank()) {
                showBanner("Couldn't give \"${track.title}\" to the other device")
                return@launch
            }
            cast.load(track, url, coverUrlFor(track), startMs, autoplay)
        }
    }

    override fun togglePlayPause() {
        if (!casting) {
            local.togglePlayPause()
            return
        }
        if (cast.state.value.playing) cast.pause() else cast.play()
    }

    override fun pause() {
        if (casting) cast.pause() else local.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (!casting) {
            local.seekTo(positionMs)
            return
        }
        _positionMs.value = positionMs
        cast.seekTo(positionMs)
    }

    /** The phone's own volume slider belongs to the phone; the device has its own. */
    override fun setOutputVolume(volume: Float) {
        if (!casting) local.setOutputVolume(volume)
    }

    /** Gapless and crossfade are this phone's; a device plays one song at a time. */
    override fun expectNextTrack(trackId: String?) {
        if (!casting) local.expectNextTrack(trackId)
    }

    private fun startTicking() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                // Zero is a real position: the start of a song, or a seek back to it. Skipping
                // it left the progress bar showing the last song's place.
                _positionMs.value = cast.positionMs().coerceAtLeast(0L)
                _isPlaying.value = cast.state.value.playing
                delay(PollMs)
            }
        }
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        /** A device reports where it is; asking twice a second is enough for a progress bar. */
        const val PollMs = 500L
    }
}
