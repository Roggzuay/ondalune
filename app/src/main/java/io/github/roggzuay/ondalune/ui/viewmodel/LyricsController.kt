package io.github.roggzuay.ondalune.ui.viewmodel

import android.app.Application
import io.github.roggzuay.ondalune.BuildConfig
import io.github.roggzuay.ondalune.data.lyrics.LrcLibClient
import io.github.roggzuay.ondalune.data.lyrics.LyricsOverlay
import io.github.roggzuay.ondalune.data.lyrics.LyricsQuery
import io.github.roggzuay.ondalune.data.lyrics.LyricsSearch
import io.github.roggzuay.ondalune.data.lyrics.LyricsService
import io.github.roggzuay.ondalune.data.lyrics.LyricsSource
import io.github.roggzuay.ondalune.data.lyrics.LyricsStore
import io.github.roggzuay.ondalune.data.lyrics.RankedCandidate
import io.github.roggzuay.ondalune.data.lyrics.lyricsKey
import io.github.roggzuay.ondalune.data.lyrics.quality
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The playing song's lyrics: from its server, found on LRCLIB, or chosen in the picker, and the
 * picker's search. The ViewModel owns it and calls [loadLyricsForTrack] when a song starts.
 */
class LyricsController(
    private val app: Application,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    /** The library row a song plays from (its server, or this phone). */
    private val serverFor: (SubsonicTrack) -> SubsonicServerConfig,
    /** The song's lyrics as its server has them. */
    private val fetchFromServer: suspend (SubsonicServerConfig, SubsonicTrack) -> TrackLyricsResult,
) {
    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _currentLyricsResult = MutableStateFlow(
        TrackLyricsResult(availability = LyricsAvailability.UNAVAILABLE)
    )
    val currentLyricsResult: StateFlow<TrackLyricsResult> = _currentLyricsResult.asStateFlow()

    private val lyricsService = LyricsService(
        LyricsStore(app),
        LrcLibClient("Ondalune/${BuildConfig.VERSION_NAME} (https://github.com/Roggzuay/ondalune)")
    )
    private var lyricsJob: Job? = null
    private var lyricsTrack: SubsonicTrack? = null
    private var lyricsKeyNow = ""
    /** The playing song's lyrics from its server, before lyrics found or chosen elsewhere. */
    private var serverLyrics = TrackLyricsResult()
    private val _lyricsSource = MutableStateFlow(LyricsSource())
    val lyricsSource: StateFlow<LyricsSource> = _lyricsSource.asStateFlow()
    private val _lyricsSearch = MutableStateFlow(LyricsSearch())
    val lyricsSearch: StateFlow<LyricsSearch> = _lyricsSearch.asStateFlow()

    /** Shows [track]'s lyrics: its own, then (when allowed) any found online. */
    fun loadLyricsForTrack(track: SubsonicTrack) {
        lyricsJob?.cancel()
        lyricsTrack = track
        _lyricsSearch.value = LyricsSearch()
        lyricsJob = scope.launch {
            val server = serverFor(track)
            val key = lyricsKey(server.id, track.id)
            lyricsKeyNow = key
            val fromServer = withContext(Dispatchers.IO) { lyricsService.fromServer(key) { fetchFromServer(server, track) } }
            serverLyrics = fromServer
            val shown = withContext(Dispatchers.IO) { lyricsService.current(key, fromServer) }
            publishLyrics(shown)
            if (settings.customization.value.findLyricsOnline) {
                val found = withContext(Dispatchers.IO) { lyricsService.discover(key, track.lyricsQuery(), fromServer) }
                publishLyrics(found ?: shown)
            }
        }
    }

    private fun SubsonicTrack.lyricsQuery() = LyricsQuery(title, artist, album, durationSec)

    private suspend fun publishLyrics(result: TrackLyricsResult) {
        val track = lyricsTrack ?: return
        val overlay = withContext(Dispatchers.IO) { lyricsService.overlay(lyricsKeyNow) }
        _currentLyricsResult.value = result
        _currentLyrics.value = result.syncedLines
        OndaluneMediaBridge.updateLyrics(app, track.id, result)
        val fromServer = result == serverLyrics
        _lyricsSource.value = LyricsSource(
            label = (if (fromServer) "Your server · " else "LRCLIB · ") + result.quality().label,
            detail = if (fromServer) "Your server · ${serverLyrics.quality().label}" else result.sourceLabel,
            fromServer = fromServer,
            serverQuality = serverLyrics.quality(),
            canRestore = overlay?.previous != null,
            offers = if (overlay?.state == LyricsOverlay.State.NONE_FOUND) overlay.offers else 0
        )
    }

    /** For the lyrics picker: LRCLIB's records for these words, best match to the song first. */
    fun searchLyrics(title: String, artist: String) {
        val track = lyricsTrack ?: return
        _lyricsSearch.value = LyricsSearch(title, artist, searching = true)
        scope.launch {
            _lyricsSearch.value = try {
                LyricsSearch(title, artist, searched = true, results = lyricsService.search(track.lyricsQuery(), title, artist))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                LyricsSearch(title, artist, searched = true, error = "LRCLIB can't be reached right now. Try again in a moment.")
            }
        }
    }

    /** Uses the picked record for the playing song from now on. */
    fun chooseLyrics(choice: RankedCandidate) = changeLyrics { key -> lyricsService.choose(key, choice.candidate, choice.score, serverLyrics) }

    /** Back to the server's lyrics for the playing song. */
    fun useServerLyrics() = changeLyrics { key -> lyricsService.useServer(key, serverLyrics) }

    /** Undoes the last change to the playing song's lyrics. */
    fun restorePreviousLyrics() = changeLyrics { key -> lyricsService.restorePrevious(key, serverLyrics) }

    private fun changeLyrics(change: (String) -> TrackLyricsResult) {
        val key = lyricsKeyNow.ifEmpty { return }
        scope.launch {
            val result = withContext(Dispatchers.IO) { change(key) }
            if (key == lyricsKeyNow) publishLyrics(result)
        }
    }
}
