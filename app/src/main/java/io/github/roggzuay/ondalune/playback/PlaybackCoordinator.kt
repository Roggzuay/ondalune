package io.github.roggzuay.ondalune.playback

import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SleepTimerState
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.autoplayPicks
import io.github.roggzuay.ondalune.data.model.smartShuffle
import io.github.roggzuay.ondalune.data.model.spreadOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The part of the audio engine the coordinator drives. The app adapts [OndaluneAudioEngine]
 * to it without changing the engine; tests use a fake.
 */
interface PlaybackEngine {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    fun play(track: SubsonicTrack, server: SubsonicServerConfig, eq: EqualizerPreset)
    fun togglePlayPause()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setOutputVolume(volume: Float)
    /** Which song plays after the current one, so gapless and crossfade can prepare it. */
    fun expectNextTrack(trackId: String?)
}

/** What the coordinator reads from the rest of the app, and what it tells it. */
interface PlaybackHost {
    /** The library as shown (filters applied): what "play" falls back to without a queue. */
    val library: List<SubsonicTrack>
    /** Every synced song, before filters: the pool autoplay picks from. */
    val allTracks: List<SubsonicTrack>
    val settings: PlayerCustomizationSettings
    val equalizer: EqualizerPreset
    /** Recently played track ids, most recent first. */
    val recentTrackIds: List<String>
    val songFeatures: Map<String, SongFeatures>
    /** The source [track] plays from. */
    fun serverFor(track: SubsonicTrack): SubsonicServerConfig
    /** A song became current (lyrics, scrobbling). */
    fun onTrackStarted(track: SubsonicTrack, server: SubsonicServerConfig)
    fun showBanner(message: String)
    fun saveLastPlayed(trackId: String)
    fun saveQueue(trackIds: List<String>)
}

/**
 * The queue and what plays next: play, skip, shuffle, repeat, queue edits, autoplay at the end of
 * the queue and the sleep timer. It drives the engine through [PlaybackEngine] and knows nothing
 * about screens; the view model hands it the library through [PlaybackHost].
 */
class PlaybackCoordinator(
    private val engine: PlaybackEngine,
    private val host: PlaybackHost,
    private val scope: CoroutineScope,
    initialShuffleMode: ShuffleMode = ShuffleMode.OFF,
    initialRepeatOne: Boolean = false
) {
    private val _currentTrack = MutableStateFlow<SubsonicTrack?>(null)
    val currentTrack: StateFlow<SubsonicTrack?> = _currentTrack.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<SubsonicTrack>>(emptyList())
    val playbackQueue: StateFlow<List<SubsonicTrack>> = _playbackQueue.asStateFlow()

    /** The playlist the queue was started from; the player names it and links to it. */
    private val _queuePlaylistId = MutableStateFlow<String?>(null)
    val queuePlaylistId: StateFlow<String?> = _queuePlaylistId.asStateFlow()

    private val _shuffleMode = MutableStateFlow(initialShuffleMode)
    val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(initialShuffleMode != ShuffleMode.OFF)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatOneEnabled = MutableStateFlow(initialRepeatOne)
    val repeatOneEnabled: StateFlow<Boolean> = _repeatOneEnabled.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var sleepTimerJob: Job? = null

    /** Songs that couldn't play here one after another; coming back round to one ends the run. */
    private val unplayableRun = HashSet<String>()

    init {
        scope.launch {
            combine(_playbackQueue, _currentTrack, _repeatOneEnabled, _sleepTimerState) { _, _, _, _ ->
                upcomingTrack(extendQueue = false)?.id
            }.distinctUntilChanged().collect { engine.expectNextTrack(it) }
        }
        scope.launch {
            engine.isPlaying.collect { if (it) unplayableRun.clear() }
        }
    }

    // ---- Starting playback ---------------------------------------------------------------

    /** Smart Shuffle queue ([smartShuffle]), starting with [anchorTrack] when given. */
    private fun buildSmartShuffledQueue(
        anchorTrack: SubsonicTrack?,
        candidates: List<SubsonicTrack>,
        profile: SmartShuffleProfile = host.settings.smartShuffleProfile
    ): List<SubsonicTrack> {
        if (candidates.isEmpty()) return emptyList()
        val remaining = candidates.filter { it.id != anchorTrack?.id }
        return listOfNotNull(anchorTrack) + smartShuffle(remaining, profile, host.recentTrackIds, after = anchorTrack)
    }

    private fun buildQueueForMode(
        anchorTrack: SubsonicTrack,
        candidates: List<SubsonicTrack>,
        mode: ShuffleMode
    ): List<SubsonicTrack> {
        return when (mode) {
            ShuffleMode.OFF -> candidates
            ShuffleMode.STANDARD ->
                listOf(anchorTrack) + spreadOut(candidates.filter { it.id != anchorTrack.id }.shuffled(), after = anchorTrack)
            ShuffleMode.SMART -> buildSmartShuffledQueue(
                anchorTrack = anchorTrack,
                candidates = candidates,
                profile = host.settings.smartShuffleProfile
            )
        }
    }

    fun playTrack(track: SubsonicTrack, queueContext: List<SubsonicTrack>? = null) {
        unplayableRun.clear()
        _queuePlaylistId.value = null
        val newQueue = queueContext?.ifEmpty { null } ?: host.library.ifEmpty { listOf(track) }
        _playbackQueue.value = buildQueueForMode(track, newQueue, _shuffleMode.value)
        startPlayback(track)
        // A single track (or a one-track album) would stop right after; keep the music going.
        extendQueueWithSuggestionsIfEnding()
    }

    /** Plays [track] from the existing queue without rebuilding it (skip, queue taps). */
    fun playFromQueue(track: SubsonicTrack) {
        if (_playbackQueue.value.none { it.id == track.id }) return playTrack(track)
        startPlayback(track)
        extendQueueWithSuggestionsIfEnding()
    }

    /** Plays [tracks] in the order given, shuffle off (mixes). */
    fun playInOrder(tracks: List<SubsonicTrack>) {
        val first = tracks.firstOrNull() ?: return
        _shuffleMode.value = ShuffleMode.OFF
        _shuffleEnabled.value = false
        _queuePlaylistId.value = null
        _playbackQueue.value = tracks
        host.saveQueue(tracks.map { it.id })
        startPlayback(first)
    }

    /** Makes [track] current; [startEngine] false when the engine already moved on to it by itself. */
    private fun startPlayback(track: SubsonicTrack, startEngine: Boolean = true) {
        _currentTrack.value = track
        host.saveLastPlayed(track.id)
        val server = host.serverFor(track)
        if (startEngine) engine.play(track, server, host.equalizer)
        host.onTrackStarted(track, server)
    }

    fun playAlbum(albumId: String, shuffle: Boolean = false) {
        val albumTracks = host.library.filter { it.albumId == albumId }
        if (albumTracks.isEmpty()) return
        if (shuffle) {
            _shuffleMode.value = ShuffleMode.SMART
            _shuffleEnabled.value = true
            val smartOrdered = buildSmartShuffledQueue(
                anchorTrack = null,
                candidates = albumTracks,
                profile = host.settings.smartShuffleProfile
            )
            playTrack(smartOrdered.first(), smartOrdered)
            host.showBanner("Smart Shuffle: Prioritizing top-rated tracks in ${smartOrdered.first().album}")
        } else {
            playTrack(albumTracks.first(), albumTracks)
        }
    }

    fun startSmartShuffleSession(sourceTracks: List<SubsonicTrack>? = null) {
        val pool = sourceTracks?.ifEmpty { null } ?: host.library
        if (pool.isEmpty()) return
        val profile = host.settings.smartShuffleProfile
        _shuffleMode.value = ShuffleMode.SMART
        _shuffleEnabled.value = true
        val smartQueue = buildSmartShuffledQueue(
            anchorTrack = null,
            candidates = pool,
            profile = profile
        )
        val lead = smartQueue.first()
        _queuePlaylistId.value = null
        _playbackQueue.value = smartQueue
        startPlayback(lead)
        host.showBanner(
            "Smart Shuffle (${profile.label}): playing \"${lead.title}\""
        )
    }

    /** Plays [playlist] from [startAt] (its first track when null). */
    fun playPlaylist(playlist: SubsonicPlaylist, startAt: SubsonicTrack? = null) {
        val library = host.library
        val mapById = library.associateBy { it.id }
        val own = playlist.trackIds.mapNotNull { mapById[it] }
        val plTracks = own.ifEmpty { library }
        if (plTracks.isNotEmpty()) {
            playTrack(startAt ?: plTracks.first(), plTracks)
            if (own.isNotEmpty()) _queuePlaylistId.value = playlist.id
        }
    }

    // ---- Transport -----------------------------------------------------------------------

    fun togglePlayPause() {
        val current = _currentTrack.value
        val library = host.library
        if (current == null && library.isNotEmpty()) {
            playTrack(library.first(), library)
            return
        }
        if (!engine.isPlaying.value && engine.positionMs.value == 0L && current != null) {
            engine.play(current, host.serverFor(current), host.equalizer)
        } else {
            engine.togglePlayPause()
        }
    }

    fun skipNext() {
        if (_playbackQueue.value.isEmpty()) _playbackQueue.value = host.library
        if (_playbackQueue.value.isEmpty()) return
        // Walks the queue as built (never reshuffles), so "Play next" inserts stay where they were put.
        extendQueueWithSuggestionsIfEnding()
        val queue = _playbackQueue.value
        val idx = queue.indexOfFirst { it.id == _currentTrack.value?.id }
        startPlayback(queue.getOrNull(idx + 1) ?: queue.first())
        extendQueueWithSuggestionsIfEnding()
    }

    fun skipPrevious(restartIfPlayed: Boolean = true) {
        if (restartIfPlayed && engine.positionMs.value > 4000L) {
            engine.seekTo(0L)
            return
        }
        val queue = _playbackQueue.value.ifEmpty { host.library }
        if (queue.isEmpty()) return
        val currentId = _currentTrack.value?.id
        val idx = queue.indexOfFirst { it.id == currentId }
        val prevTrack = if (idx > 0) {
            queue[idx - 1]
        } else {
            queue.last()
        }
        if (_playbackQueue.value.isEmpty()) _playbackQueue.value = queue
        startPlayback(prevTrack)
    }

    fun seekTo(targetMs: Long) = engine.seekTo(targetMs)

    // ---- What plays next -----------------------------------------------------------------

    /**
     * When the current track is the last one queued, appends library tracks that sound like
     * what's playing: shared genres and sub-genres first, then same artist, then nearby years.
     */
    private fun extendQueueWithSuggestionsIfEnding() {
        if (_repeatOneEnabled.value || !host.settings.autoplaySimilar) return
        val current = _currentTrack.value ?: return
        val queue = _playbackQueue.value
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx in 0 until queue.lastIndex) return
        val recentSeeds = (queue.takeLast(5) + current).distinctBy { it.id }
        val suggestions = autoplayPicks(
            seeds = recentSeeds,
            library = host.allTracks,
            recentIds = host.recentTrackIds,
            exclude = queue.mapTo(HashSet()) { it.id },
            count = 25,
            features = host.songFeatures
        )
        if (suggestions.isEmpty()) return
        _playbackQueue.value = queue.ifEmpty { listOf(current) } + suggestions
        host.saveQueue(_playbackQueue.value.map { it.id })
        host.showBanner("Up next: songs like \"${current.title}\"")
    }

    /**
     * The song that plays after the current one, using the same rules as [onTrackEnded]: null
     * when the sleep timer stops at the end of this song, the same song on repeat-one, otherwise
     * the next in the queue (wrapping round). [extendQueue] first adds suggestions when the queue
     * is about to run out, as skipping would.
     */
    private fun upcomingTrack(extendQueue: Boolean): SubsonicTrack? {
        val current = _currentTrack.value ?: return null
        val sleep = _sleepTimerState.value
        if (sleep.isActive && (sleep.stopAtEndOfCurrentTrack || sleep.waitingForTrackEnd)) return null
        if (_repeatOneEnabled.value) return current
        if (extendQueue) extendQueueWithSuggestionsIfEnding()
        val queue = _playbackQueue.value.ifEmpty { host.library }
        if (queue.isEmpty()) return null
        val idx = queue.indexOfFirst { it.id == current.id }
        return queue.getOrNull(idx + 1) ?: queue.first()
    }

    /** For gapless and crossfade: the song the engine should line up next, with its source. */
    fun nextForEngine(): Pair<SubsonicTrack, SubsonicServerConfig>? =
        upcomingTrack(extendQueue = true)?.let { it to host.serverFor(it) }

    /** The engine moved on to [track] by itself (gapless or crossfade). */
    fun onEngineAdvanced(track: SubsonicTrack) {
        startPlayback(track, startEngine = false)
        extendQueueWithSuggestionsIfEnding()
    }

    /** The engine finished a song with nothing lined up: stop for the sleep timer, repeat, or skip on. */
    fun onTrackEnded() {
        val current = _currentTrack.value ?: return
        val sleep = _sleepTimerState.value
        if (sleep.isActive && (sleep.stopAtEndOfCurrentTrack || sleep.waitingForTrackEnd)) {
            engine.pause()
            _sleepTimerState.value = sleep.copy(
                isActive = false,
                stopAtEndOfCurrentTrack = false,
                remainingMs = 0L,
                waitingForTrackEnd = false
            )
            host.showBanner("Sleep timer paused playback at end of track")
            return
        }

        if (_repeatOneEnabled.value) {
            engine.play(current, host.serverFor(current), host.equalizer)
        } else {
            skipNext()
        }
    }

    /**
     * The engine can't play [track] on this phone ([message] says why): say so and move on to the
     * next song, unless every song since the last one that played has failed the same way.
     */
    fun onTrackUnplayable(track: SubsonicTrack, message: String) {
        if (_currentTrack.value?.id != track.id) return
        if (!unplayableRun.add(track.id)) {
            unplayableRun.clear()
            host.showBanner(message)
            return
        }
        skipNext()
        host.showBanner("$message, so \"${track.title}\" was skipped")
    }

    // ---- Shuffle and repeat --------------------------------------------------------------

    fun toggleShuffle() {
        val nextMode = when (_shuffleMode.value) {
            ShuffleMode.OFF -> ShuffleMode.SMART
            ShuffleMode.SMART -> ShuffleMode.STANDARD
            ShuffleMode.STANDARD -> ShuffleMode.OFF
        }
        setShuffleMode(nextMode)
    }

    fun setShuffleMode(mode: ShuffleMode) {
        _shuffleMode.value = mode
        _shuffleEnabled.value = mode != ShuffleMode.OFF
        val current = _currentTrack.value
        val base = host.library
        if (current != null && base.isNotEmpty()) {
            _playbackQueue.value = buildQueueForMode(current, base, mode)
        }
        val profile = host.settings.smartShuffleProfile
        host.showBanner(
            when (mode) {
                ShuffleMode.SMART -> "Smart Shuffle Active (${profile.label}) — Prioritizing 4–5★ & most-played tracks"
                ShuffleMode.STANDARD -> "Standard Shuffle Active — Uniform random track order"
                ShuffleMode.OFF -> "Shuffle Off — Sequential playback order"
            }
        )
    }

    fun toggleRepeatOne() {
        _repeatOneEnabled.value = !_repeatOneEnabled.value
    }

    /** Re-deals a Smart Shuffle queue after favourites change what it favours. */
    fun reshuffleIfSmart(profile: SmartShuffleProfile = host.settings.smartShuffleProfile) {
        if (_shuffleMode.value != ShuffleMode.SMART) return
        val base = host.library
        if (base.isEmpty()) return
        val current = _currentTrack.value
        _playbackQueue.value = buildSmartShuffledQueue(current, base, profile)
    }

    // ---- Current song and library changes ------------------------------------------------

    /** Applies [transform] to the current song when it is [trackId] (a heart, an edit). */
    fun updateCurrent(trackId: String, transform: (SubsonicTrack) -> SubsonicTrack) {
        val current = _currentTrack.value ?: return
        if (current.id == trackId) _currentTrack.value = transform(current)
    }

    /**
     * The library changed (sync, server switch). Keeps the current song's details fresh; with
     * nothing current yet, brings back [resumeTrackId] (the song last played) without playing it.
     * Returns the song brought back, if any.
     */
    fun onLibraryChanged(library: List<SubsonicTrack>, resumeTrackId: String?): SubsonicTrack? {
        val current = _currentTrack.value
        if (current == null) {
            val resumed = resumeTrackId?.let { id -> library.find { it.id == id } } ?: return null
            _currentTrack.value = resumed
            _playbackQueue.value = library
            return resumed
        }
        library.find { it.id == current.id }?.let { _currentTrack.value = it }
        return null
    }

    // ---- Queue editing (Play next, Add to queue, Remove, Reorder) ------------------------

    fun playTrackNext(track: SubsonicTrack) {
        val currentQueue = _playbackQueue.value.toMutableList()
        val currentId = _currentTrack.value?.id
        currentQueue.removeAll { it.id == track.id && it.id != currentId }
        val currentIndex = currentQueue.indexOfFirst { it.id == currentId }
        val insertIndex = if (currentIndex >= 0) (currentIndex + 1).coerceAtMost(currentQueue.size) else 0
        currentQueue.add(insertIndex, track)
        _playbackQueue.value = currentQueue
        host.saveQueue(currentQueue.map { it.id })
        host.showBanner("Playing next: \"${track.title}\"")
    }

    fun addTrackToQueue(track: SubsonicTrack) {
        val currentQueue = _playbackQueue.value + track
        _playbackQueue.value = currentQueue
        host.saveQueue(currentQueue.map { it.id })
        host.showBanner("Added \"${track.title}\" to queue")
    }

    fun removeTrackFromQueue(trackId: String) {
        val currentId = _currentTrack.value?.id
        if (trackId == currentId && _playbackQueue.value.size <= 1) return
        val updated = _playbackQueue.value.filterNot { it.id == trackId }
        _playbackQueue.value = updated
        host.saveQueue(updated.map { it.id })
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        val list = _playbackQueue.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _playbackQueue.value = list
        host.saveQueue(list.map { it.id })
    }

    fun playTracksNext(list: List<SubsonicTrack>) {
        if (list.isEmpty()) return
        // Insert in reverse so the first selected track ends up playing first.
        list.asReversed().forEach { playTrackNext(it) }
        if (list.size > 1) host.showBanner("Playing ${list.size} tracks next")
    }

    fun addTracksToQueue(list: List<SubsonicTrack>) {
        if (list.isEmpty()) return
        list.forEach { addTrackToQueue(it) }
        if (list.size > 1) host.showBanner("Added ${list.size} tracks to the queue")
    }

    // ---- Sleep timer (a countdown, or the end of the current song) -----------------------

    fun startSleepTimerDuration(
        minutes: Int,
        finishLastTrackOnExpiry: Boolean = _sleepTimerState.value.finishLastTrackOnExpiry,
        gentleFadeOut: Boolean = _sleepTimerState.value.gentleFadeOut
    ) {
        sleepTimerJob?.cancel()
        val cleanMins = minutes.coerceIn(1, 180)
        val totalMs = cleanMins * 60_000L
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            stopAtEndOfCurrentTrack = false,
            finishLastTrackOnExpiry = finishLastTrackOnExpiry,
            gentleFadeOut = gentleFadeOut,
            totalDurationMinutes = cleanMins,
            remainingMs = totalMs,
            waitingForTrackEnd = false
        )
        engine.setOutputVolume(1.0f)
        host.showBanner("Sleep timer set for $cleanMins min")
        runSleepTimerCountdown()
    }

    fun startSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        engine.setOutputVolume(1.0f)
        _sleepTimerState.value = _sleepTimerState.value.copy(
            isActive = true,
            stopAtEndOfCurrentTrack = true,
            remainingMs = 0L,
            waitingForTrackEnd = true
        )
        host.showBanner("Sleep timer will stop playback at the end of this track")
    }

    fun addSleepTimerMinutes(extraMinutes: Int = 5) {
        val current = _sleepTimerState.value
        if (!current.isActive) {
            startSleepTimerDuration(extraMinutes)
            return
        }
        val addedMs = extraMinutes * 60_000L
        _sleepTimerState.value = current.copy(
            stopAtEndOfCurrentTrack = false,
            waitingForTrackEnd = false,
            remainingMs = current.remainingMs + addedMs,
            totalDurationMinutes = current.totalDurationMinutes + extraMinutes
        )
        engine.setOutputVolume(1.0f)
        if (sleepTimerJob?.isActive != true) {
            runSleepTimerCountdown()
        }
    }

    fun updateSleepTimerOptions(
        finishLastTrackOnExpiry: Boolean,
        gentleFadeOut: Boolean
    ) {
        _sleepTimerState.value = _sleepTimerState.value.copy(
            finishLastTrackOnExpiry = finishLastTrackOnExpiry,
            gentleFadeOut = gentleFadeOut
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        engine.setOutputVolume(1.0f)
        _sleepTimerState.value = _sleepTimerState.value.copy(
            isActive = false,
            stopAtEndOfCurrentTrack = false,
            remainingMs = 0L,
            waitingForTrackEnd = false
        )
        host.showBanner("Sleep timer cancelled")
    }

    private fun runSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            while (isActive && _sleepTimerState.value.isActive && !_sleepTimerState.value.stopAtEndOfCurrentTrack) {
                delay(1000L)
                val state = _sleepTimerState.value
                if (!state.isActive) break
                val nextRemaining = (state.remainingMs - 1000L).coerceAtLeast(0L)
                _sleepTimerState.value = state.copy(remainingMs = nextRemaining)

                // Apply gentle volume fade-out during the final 10 seconds if stopping immediately
                if (state.gentleFadeOut && !state.finishLastTrackOnExpiry && nextRemaining in 1L..10_000L) {
                    val fadeVol = (nextRemaining.toFloat() / 10_000f).coerceIn(0.08f, 1.0f)
                    engine.setOutputVolume(fadeVol)
                }

                if (nextRemaining == 0L) {
                    if (state.finishLastTrackOnExpiry && engine.isPlaying.value) {
                        _sleepTimerState.value = state.copy(
                            remainingMs = 0L,
                            waitingForTrackEnd = true
                        )
                        host.showBanner("Sleep timer reached 0:00 — finishing current track")
                    } else {
                        engine.pause()
                        _sleepTimerState.value = state.copy(
                            isActive = false,
                            remainingMs = 0L,
                            waitingForTrackEnd = false
                        )
                        host.showBanner("Sleep timer finished — playback paused")
                    }
                    break
                }
            }
        }
    }

    fun release() {
        sleepTimerJob?.cancel()
    }
}
