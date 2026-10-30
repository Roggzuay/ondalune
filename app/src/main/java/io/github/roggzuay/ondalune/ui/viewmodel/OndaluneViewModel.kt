package io.github.roggzuay.ondalune.ui.viewmodel

import kotlinx.coroutines.withTimeoutOrNull
import io.github.roggzuay.ondalune.data.model.genreTokens
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.roggzuay.ondalune.audio.OndaluneAudioEngine
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.local.OndaluneDownloadManager
import io.github.roggzuay.ondalune.data.model.AlbumListType
import io.github.roggzuay.ondalune.data.model.AppFont
import kotlinx.coroutines.flow.collectLatest
import io.github.roggzuay.ondalune.ui.theme.resolveDynamicSurfacePalette
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.data.model.CuratedColorPalette
import io.github.roggzuay.ondalune.data.model.DockStyle
import io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.OfflineStorageSummary
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbProcessingMode
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SeekBarStyle
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SleepTimerState
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.repository.OndaluneRepository
import io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager
import io.github.roggzuay.ondalune.widget.MediaBridgeCommand
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class PrimaryDestination(val index: Int, val route: String, val label: String) {
    HOME(0, "home", "Home"),
    LIBRARY(1, "library", "Library"),
    PLAYLISTS(2, "playlists", "Playlists"),
    FAVOURITES(3, "favourites", "Favourites"),
    SETTINGS(4, "settings", "Settings"),
    ARTISTS(5, "artists", "Artists")
}

enum class LibraryFilterTab(val label: String) {
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    GENRES("Genres"),
    FOLDERS("Folders"),
    SONGS("Songs")
}

enum class FavouritesFilterTab(val label: String) {
    ALL("All Starred"),
    TRACKS("Songs"),
    ALBUMS("Albums")
}

enum class NowPlayingSheetTab(val label: String) {
    COVER("Artwork"),
    LYRICS("Synced Lyrics"),
    QUEUE("Up Next")
}

/** How long a confirmation banner stays up before it clears itself. */
private const val BannerTimeoutMs = 3200L

@OptIn(ExperimentalCoroutinesApi::class)
class OndaluneViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val ALL_CATEGORY_KEYS = listOf(
            "ALBUMS",
            "ARTISTS",
            "PLAYLISTS",
            "GENRES",
            "FOLDERS",
            "SONGS",
            "FAVOURITES"
        )

        fun sortAlbums(albums: List<SubsonicAlbum>, pref: TabDisplayPreference): List<SubsonicAlbum> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
                LibrarySortOption.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
                LibrarySortOption.ARTIST -> albums.sortedWith(
                    compareBy<SubsonicAlbum> { it.artist.lowercase() }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.YEAR_NEWEST -> albums.sortedWith(
                    compareBy<SubsonicAlbum> { it.year }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.TRACK_COUNT -> albums.sortedWith(
                    compareBy<SubsonicAlbum> { it.songCount }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.DURATION -> albums.sortedWith(
                    compareBy<SubsonicAlbum> { it.durationSec }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.RECENTLY_ADDED -> albums.sortedBy { it.id }
                else -> albums.sortedBy { it.name.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        fun sortArtists(artists: List<SubsonicArtist>, pref: TabDisplayPreference): List<SubsonicArtist> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> artists.sortedBy { it.name.lowercase() }
                LibrarySortOption.NAME_DESC -> artists.sortedByDescending { it.name.lowercase() }
                LibrarySortOption.ALBUM_COUNT -> artists.sortedWith(
                    compareBy<SubsonicArtist> { it.albumCount }.thenBy { it.trackCount }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.TRACK_COUNT -> artists.sortedWith(
                    compareBy<SubsonicArtist> { it.trackCount }.thenBy { it.albumCount }.thenBy { it.name.lowercase() }
                )
                else -> artists.sortedBy { it.name.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        fun sortGenres(genres: List<SubsonicGenre>, pref: TabDisplayPreference): List<SubsonicGenre> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> genres.sortedBy { it.name.lowercase() }
                LibrarySortOption.NAME_DESC -> genres.sortedByDescending { it.name.lowercase() }
                LibrarySortOption.TRACK_COUNT -> genres.sortedWith(
                    compareBy<SubsonicGenre> { it.trackCount }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.ALBUM_COUNT -> genres.sortedWith(
                    compareBy<SubsonicGenre> { it.albumCount }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.DURATION -> genres.sortedWith(
                    compareBy<SubsonicGenre> { it.totalDurationSec }.thenBy { it.name.lowercase() }
                )
                else -> genres.sortedBy { it.name.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        fun sortFolders(folders: List<SubsonicFolder>, pref: TabDisplayPreference): List<SubsonicFolder> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> folders.sortedBy { it.displayName.lowercase() }
                LibrarySortOption.NAME_DESC -> folders.sortedByDescending { it.displayName.lowercase() }
                LibrarySortOption.TRACK_COUNT -> folders.sortedWith(
                    compareBy<SubsonicFolder> { it.tracks.size }.thenBy { it.displayName.lowercase() }
                )
                else -> folders.sortedBy { it.displayName.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        fun sortTracks(tracks: List<SubsonicTrack>, pref: TabDisplayPreference): List<SubsonicTrack> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> tracks.sortedBy { it.title.lowercase() }
                LibrarySortOption.NAME_DESC -> tracks.sortedByDescending { it.title.lowercase() }
                LibrarySortOption.ARTIST -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
                )
                LibrarySortOption.ALBUM -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.album.lowercase() }.thenBy { it.trackNumber }
                )
                LibrarySortOption.DURATION -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.durationSec }.thenBy { it.title.lowercase() }
                )
                LibrarySortOption.TRACK_NUMBER -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.trackNumber }.thenBy { it.title.lowercase() }
                )
                LibrarySortOption.YEAR_NEWEST -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.year }.thenBy { it.title.lowercase() }
                )
                LibrarySortOption.MOST_PLAYED -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.playCount }.thenBy { it.userRating }.thenBy { it.title.lowercase() }
                )
                LibrarySortOption.TOP_RATED -> tracks.sortedWith(
                    compareBy<SubsonicTrack> { it.userRating }.thenBy { it.starred }.thenBy { it.playCount }
                )
                else -> tracks.sortedBy { it.title.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        fun sortPlaylists(playlists: List<SubsonicPlaylist>, pref: TabDisplayPreference): List<SubsonicPlaylist> {
            val sorted = when (pref.sortOption) {
                LibrarySortOption.NAME_ASC -> playlists.sortedBy { it.name.lowercase() }
                LibrarySortOption.NAME_DESC -> playlists.sortedByDescending { it.name.lowercase() }
                LibrarySortOption.TRACK_COUNT -> playlists.sortedWith(
                    compareBy<SubsonicPlaylist> { it.trackIds.size }.thenBy { it.name.lowercase() }
                )
                LibrarySortOption.DATE_MODIFIED -> playlists.sortedBy { it.updatedAtMs }
                else -> playlists.sortedBy { it.name.lowercase() }
            }
            return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
                sorted
            } else if (pref.ascending) {
                sorted
            } else {
                sorted.reversed()
            }
        }

        /**
         * Smart, dynamic, multi-token & typo-tolerant relevance scorer across primary and secondary metadata fields.
         * Returns 0 if any query token fails to match, or a positive relevance score (> 0) when all tokens match.
         */
        fun smartMatchScore(
            query: String,
            primaryFields: List<String>,
            secondaryFields: List<String> = emptyList()
        ): Int {
            val cleanQuery = query.trim().lowercase()
            if (cleanQuery.isEmpty()) return 100

            val tokens = cleanQuery.split(Regex("[\\s,•/\\-]+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return 100

            val normPrimary = primaryFields.map { it.lowercase() }
            val normSecondary = secondaryFields.map { it.lowercase() }
            val primaryWords = normPrimary.flatMap { it.split(Regex("[^a-z0-9]+")) }.filter { it.isNotBlank() }

            var totalScore = 0

            // Full phrase bonus if the multi-word query appears contiguously in a primary field
            if (tokens.size > 1 && normPrimary.any { it.contains(cleanQuery) }) {
                totalScore += 65
            }

            for (token in tokens) {
                var bestTokenScore = 0
                for (field in normPrimary) {
                    val s = when {
                        field == token -> 130
                        field.startsWith(token) -> 95
                        field.contains(token) -> 60
                        else -> 0
                    }
                    if (s > bestTokenScore) bestTokenScore = s
                }
                if (bestTokenScore < 80) {
                    for (word in primaryWords) {
                        val s = when {
                            word == token -> 115
                            word.startsWith(token) -> 85
                            token.length >= 4 && isWithinOneEdit(word, token) -> 42
                            else -> 0
                        }
                        if (s > bestTokenScore) bestTokenScore = s
                    }
                }
                if (bestTokenScore == 0) {
                    for (field in normSecondary) {
                        if (field.contains(token)) {
                            bestTokenScore = 32
                            break
                        }
                    }
                }
                if (bestTokenScore == 0) {
                    return 0
                }
                totalScore += bestTokenScore
            }

            return totalScore
        }

        private fun isWithinOneEdit(a: String, b: String): Boolean {
            val lenDiff = kotlin.math.abs(a.length - b.length)
            if (lenDiff > 1) return false
            var i = 0
            var j = 0
            var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) {
                    i++
                    j++
                } else {
                    edits++
                    if (edits > 1) return false
                    if (a.length > b.length) {
                        i++
                    } else if (b.length > a.length) {
                        j++
                    } else {
                        i++
                        j++
                    }
                }
            }
            if (i < a.length || j < b.length) edits++
            return edits <= 1
        }

        fun searchTracks(tracks: List<SubsonicTrack>, query: String): List<SubsonicTrack> {
            if (query.isBlank()) return tracks
            return tracks.mapNotNull { track ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(track.title, track.artist, track.album, track.genre),
                    secondaryFields = listOf(
                        track.suffix,
                        track.year.toString(),
                        track.folderPath,
                        track.musicalKey,
                        track.syncedLyricsRaw
                    )
                )
                if (score > 0) track to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        fun searchAlbums(albums: List<SubsonicAlbum>, query: String): List<SubsonicAlbum> {
            if (query.isBlank()) return albums
            return albums.mapNotNull { album ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(album.name, album.artist, album.genre),
                    secondaryFields = listOf(album.year.toString())
                )
                if (score > 0) album to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        fun searchArtists(artists: List<SubsonicArtist>, query: String): List<SubsonicArtist> {
            if (query.isBlank()) return artists
            return artists.mapNotNull { artist ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(artist.name, artist.genre),
                    secondaryFields = listOf(artist.bio)
                )
                if (score > 0) artist to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        fun searchPlaylists(playlists: List<SubsonicPlaylist>, query: String): List<SubsonicPlaylist> {
            if (query.isBlank()) return playlists
            return playlists.mapNotNull { playlist ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(playlist.name),
                    secondaryFields = listOf(playlist.comment)
                )
                if (score > 0) playlist to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        fun searchGenres(genres: List<SubsonicGenre>, query: String): List<SubsonicGenre> {
            if (query.isBlank()) return genres
            return genres.mapNotNull { genre ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(genre.name)
                )
                if (score > 0) genre to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

        fun searchFolders(folders: List<SubsonicFolder>, query: String): List<SubsonicFolder> {
            if (query.isBlank()) return folders
            return folders.mapNotNull { folder ->
                val score = smartMatchScore(
                    query = query,
                    primaryFields = listOf(folder.displayName, folder.category),
                    secondaryFields = listOf(folder.path) + folder.tracks.map { "${it.title} ${it.artist}" }
                )
                if (score > 0) folder to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }
    }

    private val prefs = application.getSharedPreferences("ondalune_prefs", android.content.Context.MODE_PRIVATE)
    private val database = OndaluneDatabase.getInstance(application)
    private val client = OpenSubsonicClient()
    private val repository = OndaluneRepository(database.dao(), client)
    val audioEngine: OndaluneAudioEngine = OndalunePlaybackRuntime.getOrCreateEngine(application, client)
    val downloadManager = OndaluneDownloadManager(application, database.dao(), client, viewModelScope)

    private var sleepTimerJob: Job? = null
    private var cutoutSyncJob: Job? = null
    private var remoteSearchJob: Job? = null

    val isPlaying: StateFlow<Boolean> = audioEngine.isPlaying
    val isBuffering: StateFlow<Boolean> = audioEngine.isBuffering
    val positionMs: StateFlow<Long> = audioEngine.positionMs
    val durationMs: StateFlow<Long> = audioEngine.durationMs
    val playbackSpeed: StateFlow<Float> = audioEngine.playbackSpeed
    val reverbProcessingMode: StateFlow<ReverbProcessingMode> = audioEngine.reverbProcessingMode
    val isPlayingOfflineFile: StateFlow<Boolean> = audioEngine.isPlayingOfflineFile

    private val _speedPitchState = MutableStateFlow(loadPersistedSpeedPitchState())
    val speedPitchState: StateFlow<SpeedPitchState> = _speedPitchState.asStateFlow()

    private val _reverbState = MutableStateFlow(loadPersistedReverbState())
    val reverbState: StateFlow<ReverbState> = _reverbState.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    val servers: StateFlow<List<SubsonicServerConfig>> = repository.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeServerId = MutableStateFlow(1L)

    val playlists: StateFlow<List<SubsonicPlaylist>> = _activeServerId
        .flatMapLatest { id -> repository.observePlaylistsForServer(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadsMap: StateFlow<Map<String, SubsonicDownloadEntity>> = _activeServerId
        .flatMapLatest { id -> downloadManager.observeDownloadsMap(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _libraryFilters = MutableStateFlow(
        LibraryFilters(
            downloadedOnly = prefs.getBoolean("filter_downloaded_only", false),
            hideShortTracks = prefs.getBoolean("filter_hide_short", false),
            losslessOnly = prefs.getBoolean("filter_lossless_only", false)
        )
    )
    /** Library-wide filters from the top bar's filter sheet; applied to [tracks], so every screen honours them. */
    val libraryFilters: StateFlow<LibraryFilters> = _libraryFilters.asStateFlow()

    fun setLibraryFilters(filters: LibraryFilters) {
        _libraryFilters.value = filters
        prefs.edit()
            .putBoolean("filter_downloaded_only", filters.downloadedOnly)
            .putBoolean("filter_hide_short", filters.hideShortTracks)
            .putBoolean("filter_lossless_only", filters.losslessOnly)
            .apply()
    }

    /** Every synced track on the active server, before [libraryFilters]. */
    val allServerTracks: StateFlow<List<SubsonicTrack>> = _activeServerId
        .flatMapLatest { id -> repository.observeTracksForServer(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Only completed ids matter for filtering, so progress ticks don't re-filter the library.
    private val downloadedIds = downloadsMap
        .map { map -> map.values.filter { it.isCompleted }.mapTo(HashSet()) { it.trackId } }
        .distinctUntilChanged()

    val tracks: StateFlow<List<SubsonicTrack>> = combine(allServerTracks, _libraryFilters, downloadedIds) { list, f, downloaded ->
        if (!f.active) list else list.filter { t ->
            (!f.downloadedOnly || t.id in downloaded) &&
                (!f.hideShortTracks || t.durationSec >= 60) &&
                (!f.losslessOnly || t.suffix.uppercase() in LosslessSuffixes)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rotates once per launch: index into the Home greeting lines. */
    val greetingIndex: Int = prefs.getInt("greeting_index", -1).plus(1).also {
        prefs.edit().putInt("greeting_index", it).apply()
    }

    val offlineStorageSummary: StateFlow<OfflineStorageSummary> = downloadManager.storageSummary

    private val _currentDestination = MutableStateFlow(PrimaryDestination.HOME)
    val currentDestination: StateFlow<PrimaryDestination> = _currentDestination.asStateFlow()

    private val _selectedAlbumListType = MutableStateFlow(AlbumListType.NEWEST)
    val selectedAlbumListType: StateFlow<AlbumListType> = _selectedAlbumListType.asStateFlow()

    private val _libraryFilterTab = MutableStateFlow(LibraryFilterTab.ALBUMS)
    val libraryFilterTab: StateFlow<LibraryFilterTab> = _libraryFilterTab.asStateFlow()

    private val _tabDisplayPreferences = MutableStateFlow(loadPersistedTabPreferences())
    val tabDisplayPreferences: StateFlow<Map<String, TabDisplayPreference>> = _tabDisplayPreferences.asStateFlow()

    private val _libraryViewMode = MutableStateFlow(
        _tabDisplayPreferences.value[LibraryFilterTab.ALBUMS.name]?.viewMode ?: LibraryViewMode.GRID
    )
    val libraryViewMode: StateFlow<LibraryViewMode> = _libraryViewMode.asStateFlow()

    private val _librarySortOption = MutableStateFlow(
        _tabDisplayPreferences.value[LibraryFilterTab.ALBUMS.name]?.sortOption ?: LibrarySortOption.NAME_ASC
    )
    val librarySortOption: StateFlow<LibrarySortOption> = _librarySortOption.asStateFlow()

    private val _favouritesFilterTab = MutableStateFlow(FavouritesFilterTab.ALL)
    val favouritesFilterTab: StateFlow<FavouritesFilterTab> = _favouritesFilterTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentTrack = MutableStateFlow<SubsonicTrack?>(null)
    val currentTrack: StateFlow<SubsonicTrack?> = _currentTrack.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<SubsonicTrack>>(emptyList())
    val playbackQueue: StateFlow<List<SubsonicTrack>> = _playbackQueue.asStateFlow()

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _currentLyricsResult = MutableStateFlow(
        TrackLyricsResult(availability = LyricsAvailability.UNAVAILABLE)
    )
    val currentLyricsResult: StateFlow<TrackLyricsResult> = _currentLyricsResult.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isSonicLabSheetOpen = MutableStateFlow(false)
    val isSonicLabSheetOpen: StateFlow<Boolean> = _isSonicLabSheetOpen.asStateFlow()

    private val _isSpeedPitchSheetOpen = MutableStateFlow(false)
    val isSpeedPitchSheetOpen: StateFlow<Boolean> = _isSpeedPitchSheetOpen.asStateFlow()

    private val _isSleepTimerSheetOpen = MutableStateFlow(false)
    val isSleepTimerSheetOpen: StateFlow<Boolean> = _isSleepTimerSheetOpen.asStateFlow()

    private val _isUserServerSheetOpen = MutableStateFlow(false)
    val isUserServerSheetOpen: StateFlow<Boolean> = _isUserServerSheetOpen.asStateFlow()

    private val _isAppIconSettingsOpen = MutableStateFlow(false)
    val isAppIconSettingsOpen: StateFlow<Boolean> = _isAppIconSettingsOpen.asStateFlow()

    private val _showServerOnboarding = MutableStateFlow(!prefs.getBoolean("onboarding_completed", false))
    val showServerOnboarding: StateFlow<Boolean> = _showServerOnboarding.asStateFlow()

    private val _isCutoutSyncActive = MutableStateFlow(false)
    val isCutoutSyncActive: StateFlow<Boolean> = _isCutoutSyncActive.asStateFlow()

    private val _nowPlayingTab = MutableStateFlow(NowPlayingSheetTab.COVER)
    val nowPlayingTab: StateFlow<NowPlayingSheetTab> = _nowPlayingTab.asStateFlow()

    private val _artworkShapeMode = MutableStateFlow(
        runCatching {
            ArtworkShapeMode.valueOf(prefs.getString("artwork_shape_mode", ArtworkShapeMode.SQUIRCLE.name)!!)
        }.getOrDefault(ArtworkShapeMode.SQUIRCLE)
    )
    val artworkShapeMode: StateFlow<ArtworkShapeMode> = _artworkShapeMode.asStateFlow()

    private val _shuffleMode = MutableStateFlow(
        runCatching {
            ShuffleMode.valueOf(prefs.getString("shuffle_mode", ShuffleMode.OFF.name)!!)
        }.getOrDefault(ShuffleMode.OFF)
    )
    val shuffleMode: StateFlow<ShuffleMode> = _shuffleMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(_shuffleMode.value != ShuffleMode.OFF)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatOneEnabled = MutableStateFlow(prefs.getBoolean("repeat_one_enabled", false))
    val repeatOneEnabled: StateFlow<Boolean> = _repeatOneEnabled.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(
        runCatching {
            EqualizerPreset.valueOf(prefs.getString("eq_preset", EqualizerPreset.FLAT_STUDIO.name)!!)
        }.getOrDefault(EqualizerPreset.FLAT_STUDIO)
    )
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _customEqBands = MutableStateFlow(_equalizerPreset.value.bandsDb)
    val customEqBands: StateFlow<List<Float>> = _customEqBands.asStateFlow()

    private val _customizationSettings = MutableStateFlow(loadPersistedCustomizationSettings())
    val customizationSettings: StateFlow<PlayerCustomizationSettings> = _customizationSettings.asStateFlow()

    private val _selectedAlbumDetailId = MutableStateFlow<String?>(null)
    val selectedAlbumDetailId: StateFlow<String?> = _selectedAlbumDetailId.asStateFlow()

    private val _albumDetailOriginDestination = MutableStateFlow<PrimaryDestination?>(null)
    val albumDetailOriginDestination: StateFlow<PrimaryDestination?> = _albumDetailOriginDestination.asStateFlow()

    private val _selectedArtistDetailId = MutableStateFlow<String?>(null)
    val selectedArtistDetailId: StateFlow<String?> = _selectedArtistDetailId.asStateFlow()

    private val _selectedGenreDetail = MutableStateFlow<String?>(null)
    val selectedGenreDetail: StateFlow<String?> = _selectedGenreDetail.asStateFlow()

    private val _selectedFolderDetail = MutableStateFlow<String?>(null)
    val selectedFolderDetail: StateFlow<String?> = _selectedFolderDetail.asStateFlow()

    private val _pingDiagnostic = MutableStateFlow<PingDiagnosticResult?>(null)
    val pingDiagnostic: StateFlow<PingDiagnosticResult?> = _pingDiagnostic.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val albums: StateFlow<List<SubsonicAlbum>> = combine(
        tracks,
        _selectedAlbumListType
    ) { trackList, listType ->
        repository.deriveAlbums(trackList, listType)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<SubsonicArtist>> = combine(
        tracks,
        _customizationSettings
    ) { trackList, settings ->
        repository.deriveArtists(trackList, settings.fallbackArtistToAlbumArt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Home shelves -------------------------------------------------------------------
    /** Server-side album orderings keyed by getAlbumList2 type (newest / recent / frequent). */
    private val _serverAlbumOrder = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Locally played track ids, most recent first (persisted; feeds "Jump back in"). */
    private val _recentTrackIds = MutableStateFlow(
        prefs.getString(PREF_RECENT_TRACKS, "").orEmpty().split(',').filter { it.isNotBlank() }
    )

    val homeShelves: StateFlow<HomeShelves> = combine(
        tracks,
        _serverAlbumOrder,
        _recentTrackIds
    ) { trackList, order, recentIds ->
        val albumsByYear = repository.deriveAlbums(trackList, AlbumListType.NEWEST)
        val albumsById = albumsByYear.associateBy { it.id }
        val tracksById = trackList.associateBy { it.id }
        fun serverOrdered(type: String) = order[type].orEmpty().mapNotNull { albumsById[it] }
        val day = java.time.LocalDate.now().toEpochDay()
        // Full lists: Home shows the first few of each, the shelf's "More" page shows all of it.
        val tracksByAlbum = trackList.groupBy { it.albumId }
        HomeShelves(
            jumpBackIn = recentIds.mapNotNull { tracksById[it] },
            recentlyAdded = serverOrdered("newest").ifEmpty { albumsByYear },
            favouriteTracks = trackList.filter { it.starred }.sortedByDescending { it.userRating },
            mostPlayed = trackList.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
                .ifEmpty { serverOrdered("frequent").flatMap { tracksByAlbum[it.id].orEmpty() } }
                .take(200),
            recentlyPlayed = serverOrdered("recent"),
            rediscover = albumsByYear.shuffled(kotlin.random.Random(day)).take(60)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeShelves())

    private val _scrollToTopSignal = MutableStateFlow(0)
    /** Bumped when the current tab is tapped again at its root: lists scroll back to the top. */
    val scrollToTopSignal: StateFlow<Int> = _scrollToTopSignal.asStateFlow()

    /** Tapping the tab you're on: first back out of any open page, then scroll to the top. */
    fun reselectDestination() {
        val detailOpen = _openHomeShelf.value != null || _selectedAlbumDetailId.value != null ||
            _selectedArtistDetailId.value != null || _selectedGenreDetail.value != null || _selectedFolderDetail.value != null
        if (detailOpen) {
            _openHomeShelf.value = null
            _selectedAlbumDetailId.value = null
            _albumDetailOriginDestination.value = null
            _selectedArtistDetailId.value = null
            _selectedGenreDetail.value = null
            _selectedFolderDetail.value = null
        } else {
            _scrollToTopSignal.value++
        }
    }

    private val _openHomeShelf = MutableStateFlow<HomeShelfKind?>(null)
    /** Home shelf whose full "More" list is open, if any. */
    val openHomeShelf: StateFlow<HomeShelfKind?> = _openHomeShelf.asStateFlow()

    fun openHomeShelf(kind: HomeShelfKind?) {
        _openHomeShelf.value = kind
    }

    private fun markFullSync(server: SubsonicServerConfig) {
        if (!server.isDemoServer) prefs.edit().putLong(PREF_FULL_SYNC_AT + server.id, System.currentTimeMillis()).apply()
    }

    fun refreshHomeShelves(server: SubsonicServerConfig = activeServer()) {
        if (server.isDemoServer) return
        viewModelScope.launch {
            val fetched = listOf("newest", "recent", "frequent").associateWith { type ->
                repository.client.fetchAlbumListIds(server, type, size = 500)
            }
            _serverAlbumOrder.value = fetched.filterValues { it.isNotEmpty() }
        }
    }

    private fun recordPlayed(trackId: String) {
        val updated = (listOf(trackId) + _recentTrackIds.value.filterNot { it == trackId }).take(40)
        _recentTrackIds.value = updated
        prefs.edit().putString(PREF_RECENT_TRACKS, updated.joinToString(",")).apply()
    }

    val genres: StateFlow<List<SubsonicGenre>> = tracks.map { trackList ->
        repository.deriveGenres(trackList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<SubsonicFolder>> = tracks.map { trackList ->
        repository.deriveFolders(trackList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<SubsonicTrack>> = combine(
        tracks,
        _searchQuery
    ) { trackList, query ->
        searchTracks(trackList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums: StateFlow<List<SubsonicAlbum>> = combine(
        albums,
        _searchQuery
    ) { albumList, query ->
        searchAlbums(albumList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArtists: StateFlow<List<SubsonicArtist>> = combine(
        artists,
        _searchQuery
    ) { artistList, query ->
        searchArtists(artistList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredPlaylists: StateFlow<List<SubsonicPlaylist>> = combine(
        playlists,
        _searchQuery
    ) { playlistList, query ->
        searchPlaylists(playlistList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGenres: StateFlow<List<SubsonicGenre>> = combine(
        genres,
        _searchQuery
    ) { genreList, query ->
        searchGenres(genreList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFolders: StateFlow<List<SubsonicFolder>> = combine(
        folders,
        _searchQuery
    ) { folderList, query ->
        searchFolders(folderList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Confirmation banners ("Reverb set to…", "Added to queue") clear themselves; only the live
        // sync counter stays up while the sync runs.
        viewModelScope.launch {
            _statusBanner.collectLatest { message ->
                if (message == null) return@collectLatest
                delay(BannerTimeoutMs)
                while (_isCutoutSyncActive.value && _isSyncing.value) delay(500L)
                if (_statusBanner.value == message) _statusBanner.value = null
            }
        }
        viewModelScope.launch {
            combine(currentTrack, isPlaying) { track, playing -> if (playing) track?.id else null }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { recordPlayed(it) }
        }
        audioEngine.pauseOnHeadphoneDisconnect = _customizationSettings.value.pauseOnHeadphoneDisconnect
        audioEngine.setSpeedAndPitch(_speedPitchState.value.speed, _speedPitchState.value.effectivePitch)
        audioEngine.setReverbState(_reverbState.value)

        audioEngine.onTrackEnded = {
            handleTrackFinished()
        }
        // Gapless and crossfade: the engine lines up whatever plays next and moves on by itself.
        audioEngine.nextTrackProvider = { upcomingTrack(extendQueue = true)?.let { it to activeServer() } }
        audioEngine.onTrackAdvanced = { track ->
            startPlayback(track, startEngine = false)
            extendQueueWithSuggestionsIfEnding()
        }
        viewModelScope.launch {
            combine(_playbackQueue, _currentTrack, _repeatOneEnabled, _sleepTimerState) { _, _, _, _ ->
                upcomingTrack(extendQueue = false)?.id
            }.distinctUntilChanged().collect { audioEngine.expectNextTrack(it) }
        }
        viewModelScope.launch {
            _customizationSettings.collect { s ->
                audioEngine.crossfadeMs = s.crossfadeSeconds * 1000
                audioEngine.gaplessEnabled = s.gaplessPlayback
                audioEngine.replayGainMode = s.replayGainMode
            }
        }
        OndaluneMediaBridge.onCommand = { action ->
            viewModelScope.launch {
                when (action) {
                    MediaBridgeCommand.TOGGLE_PLAY_PAUSE -> togglePlayPause()
                    MediaBridgeCommand.PLAY -> if (!isPlaying.value) togglePlayPause()
                    MediaBridgeCommand.PAUSE -> if (isPlaying.value) togglePlayPause()
                    MediaBridgeCommand.SKIP_NEXT -> skipNext()
                    MediaBridgeCommand.SKIP_PREVIOUS -> skipPrevious()
                    MediaBridgeCommand.TOGGLE_STAR -> {
                        _currentTrack.value?.let { toggleStarTrack(it) }
                    }
                }
            }
        }
        OndaluneMediaBridge.onSeekTo = { posMs ->
            viewModelScope.launch {
                seekTo(posMs)
            }
        }
        viewModelScope.launch {
            val activeServer = repository.ensureSeeded()
            _activeServerId.value = activeServer.id
            downloadManager.refreshStorageSummary(activeServer.id)
            _pingDiagnostic.value = repository.pingAndInspect(activeServer)
            // Keep the local mirror complete: full walk on first launch after install and when stale.
            val lastFull = prefs.getLong(PREF_FULL_SYNC_AT + activeServer.id, 0L)
            if (!activeServer.isDemoServer && System.currentTimeMillis() - lastFull > FULL_SYNC_INTERVAL_MS) {
                triggerQuickSync(activeServer)
            }
        }
        viewModelScope.launch {
            tracks.collect { list ->
                if (list.isNotEmpty() && _currentTrack.value == null) {
                    val savedTrackId = prefs.getString("last_played_track_id", null)
                    val first = if (_customizationSettings.value.resumePlaybackOnLaunch && !savedTrackId.isNullOrBlank()) {
                        list.find { it.id == savedTrackId } ?: list.first()
                    } else {
                        list.first()
                    }
                    _currentTrack.value = first
                    _playbackQueue.value = list
                    loadLyricsForTrack(first)
                } else if (_currentTrack.value != null) {
                    val updated = list.find { it.id == _currentTrack.value?.id }
                    if (updated != null) {
                        _currentTrack.value = updated
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(
                _currentTrack,
                isPlaying,
                _customizationSettings,
                durationMs,
                playbackSpeed
            ) { track, playing, settings, durMs, speed ->
                listOf(track, playing, settings, durMs, speed)
            }.collect { values ->
                val track = values[0] as? SubsonicTrack
                val playing = values[1] as Boolean
                val settings = values[2] as PlayerCustomizationSettings
                val durMs = values[3] as Long
                val speed = values[4] as Float
                OndaluneMediaBridge.syncPlaybackAndThemeState(
                    context = getApplication(),
                    track = track,
                    isPlaying = playing,
                    settings = settings,
                    positionMs = positionMs.value,
                    durationMs = durMs,
                    playbackSpeed = speed,
                    isBuffering = isBuffering.value
                )
            }
        }
        viewModelScope.launch {
            var lastBridgeSyncMs = 0L
            positionMs.collect { pos ->
                val now = System.currentTimeMillis()
                if (now - lastBridgeSyncMs >= 900L || pos == 0L) {
                    lastBridgeSyncMs = now
                    OndaluneMediaBridge.updatePlaybackProgress(
                        context = getApplication(),
                        positionMs = pos,
                        durationMs = durationMs.value,
                        isPlaying = isPlaying.value,
                        playbackSpeed = playbackSpeed.value,
                        isBuffering = isBuffering.value
                    )
                }
            }
        }
    }

    fun activeServer(): SubsonicServerConfig {
        return servers.value.firstOrNull { it.isActive }
            ?: servers.value.firstOrNull()
            ?: DemoSubsonicCatalog.createDefaultDemoServer()
    }

    fun selectDestination(destination: PrimaryDestination) {
        _currentDestination.value = destination
        _openHomeShelf.value = null
        _selectedAlbumDetailId.value = null
        _albumDetailOriginDestination.value = null
        _selectedArtistDetailId.value = null
        _selectedGenreDetail.value = null
        _selectedFolderDetail.value = null
    }

    fun selectAlbumListType(type: AlbumListType) {
        // The whole library is local now; list types are just orderings of it.
        _selectedAlbumListType.value = type
    }

    /**
     * Triggers a quick catalog sync and activates the Material 3 Expressive morphing icon
     * popping from the top-center camera cutout area.
     */
    fun triggerQuickSync(server: SubsonicServerConfig = activeServer()) {
        cutoutSyncJob?.cancel()
        cutoutSyncJob = viewModelScope.launch {
            _isCutoutSyncActive.value = true
            _isSyncing.value = true
            val diag = repository.pingAndInspect(server)
            _pingDiagnostic.value = diag
            // The server answers in pages; count up toward each new total frame by frame so the
            // number climbs steadily instead of leaping a page at a time.
            val found = java.util.concurrent.atomic.AtomicInteger(0) // written from the network thread
            var shown = 0
            _statusBanner.value = "Syncing library…"
            val counter = launch {
                while (true) {
                    val target = found.get()
                    if (shown < target) {
                        shown += ((target - shown + 7) / 8).coerceAtLeast(1)
                        _statusBanner.value = "Syncing library… $shown songs"
                    }
                    delay(32L)
                }
            }
            val syncedCount = repository.syncServerCatalog(server) { n -> found.set(n) }
            found.set(syncedCount)
            withTimeoutOrNull(1500L) { while (shown < syncedCount) delay(32L) }
            counter.cancel()
            markFullSync(server)
            refreshHomeShelves(server)
            _isSyncing.value = false
            _statusBanner.value = "Synced $syncedCount tracks from ${server.name}"
            delay(450L)
            _isCutoutSyncActive.value = false
        }
    }

    fun selectLibraryFilterTab(tab: LibraryFilterTab) {
        _libraryFilterTab.value = tab
        val tabPref = getTabPreference(tab.name)
        _libraryViewMode.value = tabPref.viewMode
        _librarySortOption.value = tabPref.sortOption
        _selectedAlbumDetailId.value = null
        _albumDetailOriginDestination.value = null
        _selectedArtistDetailId.value = null
        _selectedGenreDetail.value = null
        _selectedFolderDetail.value = null
    }

    fun getTabPreference(categoryKey: String): TabDisplayPreference {
        val key = categoryKey.uppercase()
        return _tabDisplayPreferences.value[key] ?: TabDisplayPreference.defaultForCategory(key)
    }

    fun setCategoryViewMode(categoryKey: String, mode: LibraryViewMode) {
        val key = categoryKey.uppercase()
        val current = getTabPreference(key)
        val updated = current.copy(viewMode = mode)
        val map = _tabDisplayPreferences.value.toMutableMap().apply { put(key, updated) }
        _tabDisplayPreferences.value = map
        persistTabPreference(key, updated)
        if (_libraryFilterTab.value.name == key) {
            _libraryViewMode.value = mode
        }
    }

    fun setCategorySortOption(categoryKey: String, option: LibrarySortOption) {
        val key = categoryKey.uppercase()
        val current = getTabPreference(key)
        // Automatically adjust ascending default when switching to NAME_DESC vs NAME_ASC
        val nextAsc = when (option) {
            LibrarySortOption.NAME_DESC -> false
            LibrarySortOption.YEAR_NEWEST,
            LibrarySortOption.MOST_PLAYED,
            LibrarySortOption.TOP_RATED,
            LibrarySortOption.TRACK_COUNT,
            LibrarySortOption.ALBUM_COUNT,
            LibrarySortOption.DATE_MODIFIED -> false
            LibrarySortOption.NAME_ASC,
            LibrarySortOption.ARTIST,
            LibrarySortOption.ALBUM,
            LibrarySortOption.TRACK_NUMBER -> true
            else -> current.ascending
        }
        val updated = current.copy(sortOption = option, ascending = nextAsc)
        val map = _tabDisplayPreferences.value.toMutableMap().apply { put(key, updated) }
        _tabDisplayPreferences.value = map
        persistTabPreference(key, updated)
        if (_libraryFilterTab.value.name == key) {
            _librarySortOption.value = option
        }
    }

    fun toggleCategorySortDirection(categoryKey: String) {
        val key = categoryKey.uppercase()
        val current = getTabPreference(key)
        val nextAsc = !current.ascending
        val normalizedOption = if (current.sortOption == LibrarySortOption.NAME_DESC) {
            LibrarySortOption.NAME_ASC
        } else {
            current.sortOption
        }
        val updated = current.copy(sortOption = normalizedOption, ascending = nextAsc)
        val map = _tabDisplayPreferences.value.toMutableMap().apply { put(key, updated) }
        _tabDisplayPreferences.value = map
        persistTabPreference(key, updated)
    }

    fun setCategorySortDirection(categoryKey: String, ascending: Boolean) {
        val key = categoryKey.uppercase()
        val current = getTabPreference(key)
        val normalizedOption = if (current.sortOption == LibrarySortOption.NAME_DESC) {
            LibrarySortOption.NAME_ASC
        } else {
            current.sortOption
        }
        val updated = current.copy(sortOption = normalizedOption, ascending = ascending)
        val map = _tabDisplayPreferences.value.toMutableMap().apply { put(key, updated) }
        _tabDisplayPreferences.value = map
        persistTabPreference(key, updated)
    }

    fun setCategoryGridColumns(categoryKey: String, columns: Int) {
        val key = categoryKey.uppercase()
        val clamped = columns.coerceIn(2, 4)
        val current = getTabPreference(key)
        val updated = current.copy(gridColumns = clamped)
        val map = _tabDisplayPreferences.value.toMutableMap().apply { put(key, updated) }
        _tabDisplayPreferences.value = map
        persistTabPreference(key, updated)
    }

    fun setLibraryViewMode(mode: LibraryViewMode) {
        _libraryViewMode.value = mode
        setCategoryViewMode(_libraryFilterTab.value.name, mode)
    }

    fun setLibrarySortOption(option: LibrarySortOption) {
        _librarySortOption.value = option
        setCategorySortOption(_libraryFilterTab.value.name, option)
    }

    fun selectFavouritesFilterTab(tab: FavouritesFilterTab) {
        _favouritesFilterTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        remoteSearchJob?.cancel()
        val trimmed = query.trim()
        val server = activeServer()
        if (!server.isDemoServer && trimmed.length >= 2) {
            remoteSearchJob = viewModelScope.launch {
                delay(220L)
                val remoteHits = client.searchRemote(server, trimmed)
                if (remoteHits.isNotEmpty()) {
                    database.dao().insertTracks(remoteHits)
                }
            }
        }
    }

    fun openAlbumDetail(albumId: String?) {
        if (albumId != null) {
            if (_albumDetailOriginDestination.value == null) {
                _albumDetailOriginDestination.value = _currentDestination.value
            }
            _selectedAlbumDetailId.value = albumId
        } else {
            _selectedAlbumDetailId.value = null
            val origin = _albumDetailOriginDestination.value
            if (origin != null) {
                _currentDestination.value = origin
                _albumDetailOriginDestination.value = null
            }
        }
    }

    fun openArtistDetail(artistId: String?) {
        _selectedArtistDetailId.value = artistId
        if (artistId != null) {
            val server = activeServer()
            if (!server.isDemoServer) {
                val matchedArtist = artists.value.find { it.id == artistId }
                val rawArtistId = tracks.value.firstOrNull {
                    it.artistId == artistId ||
                        it.artist.equals(matchedArtist?.name.orEmpty(), ignoreCase = true)
                }?.artistId?.ifBlank { artistId } ?: artistId
                viewModelScope.launch {
                    repository.enrichArtistMetadata(
                        server = server,
                        artistId = rawArtistId,
                        artistName = matchedArtist?.name.orEmpty()
                    )
                }
            }
        }
    }

    /** From the player: collapse it and land on the playing track's artist page in Library. */
    fun openArtistFromTrack(track: SubsonicTrack) = openArtistByName(track.artistId, track.artist)

    fun openArtistByName(artistId: String, name: String) {
        val artist = artists.value.find { artistId.isNotBlank() && it.id == artistId }
            ?: artists.value.find { it.name.equals(name, ignoreCase = true) }
            ?: return
        _isPlayerExpanded.value = false
        selectDestination(PrimaryDestination.ARTISTS)
        openArtistDetail(artist.id)
    }

    fun openGenreDetail(genreName: String?) {
        _selectedGenreDetail.value = genreName
    }

    fun openFolderDetail(folderPath: String?) {
        _selectedFolderDetail.value = folderPath
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun setSonicLabSheetOpen(open: Boolean) {
        _isSonicLabSheetOpen.value = open
    }

    fun setSpeedPitchSheetOpen(open: Boolean) {
        _isSpeedPitchSheetOpen.value = open
    }

    fun setSleepTimerSheetOpen(open: Boolean) {
        _isSleepTimerSheetOpen.value = open
    }

    fun setUserServerSheetOpen(open: Boolean) {
        _isUserServerSheetOpen.value = open
    }

    fun setAppIconSettingsOpen(open: Boolean) {
        _isAppIconSettingsOpen.value = open
    }

    fun setShowServerOnboarding(show: Boolean) {
        _showServerOnboarding.value = show
    }

    /** The first library sync, shown on the onboarding screen until it reaches 100%. */
    data class OnboardingSync(val found: Int, val total: Int?, val phase: Phase, val error: String? = null) {
        enum class Phase { CONNECTING, SYNCING, DONE, FAILED }

        /** 0..1, or null while the size of the library is unknown. */
        val fraction: Float?
            get() = when {
                phase == Phase.DONE -> 1f
                total == null || total <= 0 -> null
                else -> (found.toFloat() / total).coerceIn(0f, 0.99f)
            }
    }

    private val _onboardingSync = MutableStateFlow<OnboardingSync?>(null)
    val onboardingSync: StateFlow<OnboardingSync?> = _onboardingSync.asStateFlow()

    /**
     * Connects the first server and keeps onboarding open while the library syncs, counting songs
     * up toward the server's library size; at 100% onboarding closes into Home. A failed
     * connection returns to the form with the reason.
     */
    fun connectServerDuringOnboarding(
        name: String,
        baseUrl: String,
        username: String,
        passwordOrToken: String,
        useTokenAuth: Boolean
    ) {
        if (_onboardingSync.value?.phase == OnboardingSync.Phase.CONNECTING ||
            _onboardingSync.value?.phase == OnboardingSync.Phase.SYNCING
        ) return
        viewModelScope.launch {
            _onboardingSync.value = OnboardingSync(0, null, OnboardingSync.Phase.CONNECTING)
            _isSyncing.value = true
            val current = activeServer()
            val config = SubsonicServerConfig(
                name = name.trim().ifBlank { "OpenSubsonic Server" },
                baseUrl = baseUrl.trim(),
                username = username.trim(),
                passwordOrToken = passwordOrToken,
                useTokenAuth = useTokenAuth,
                transcodeFormat = current.transcodeFormat,
                maxBitRate = current.maxBitRate,
                isDemoServer = false,
                isActive = true
            )
            OndaluneMediaBridge.clearRemoteArtworkCache()
            val total = async { repository.client.fetchLibrarySongCount(config) }
            // Pages arrive in bursts; count up toward each new total smoothly.
            val found = java.util.concurrent.atomic.AtomicInteger(0)
            var shown = 0
            val counter = launch {
                while (true) {
                    val target = found.get()
                    if (shown < target) shown += ((target - shown + 7) / 8).coerceAtLeast(1)
                    if (target > 0) {
                        _onboardingSync.value = OnboardingSync(shown, total.takeIf { it.isCompleted }?.getCompleted(), OnboardingSync.Phase.SYNCING)
                    }
                    delay(32L)
                }
            }
            val (saved, diag) = repository.saveAndActivateServer(config) { n -> found.set(n) }
            _activeServerId.value = saved.id
            _pingDiagnostic.value = diag
            if (!diag.success) {
                counter.cancel()
                total.cancel()
                _isSyncing.value = false
                _onboardingSync.value = OnboardingSync(0, null, OnboardingSync.Phase.FAILED, diag.statusMessage)
                return@launch
            }
            val synced = tracks.value.count { it.serverId == saved.id }.coerceAtLeast(found.get())
            found.set(synced)
            withTimeoutOrNull(1500L) { while (shown < synced) delay(32L) }
            counter.cancel()
            markFullSync(saved)
            refreshHomeShelves(saved)
            _isSyncing.value = false
            _onboardingSync.value = OnboardingSync(synced, synced, OnboardingSync.Phase.DONE)
            delay(900L) // let 100% land before zooming in
            completeServerOnboarding()
            _statusBanner.value = "Synced $synced songs from ${saved.name}"
        }
    }

    fun dismissOnboardingSyncError() {
        if (_onboardingSync.value?.phase == OnboardingSync.Phase.FAILED) _onboardingSync.value = null
    }

    fun completeServerOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
        _showServerOnboarding.value = false
    }

    fun selectNowPlayingTab(tab: NowPlayingSheetTab) {
        _nowPlayingTab.value = tab
    }

    fun cycleArtworkShapeMode() {
        val modes = ArtworkShapeMode.entries
        val nextIdx = (modes.indexOf(_artworkShapeMode.value) + 1) % modes.size
        _artworkShapeMode.value = modes[nextIdx]
    }

    fun setArtworkShapeMode(mode: ArtworkShapeMode) {
        _artworkShapeMode.value = mode
    }

    // =========================================================================
    // Speed (0.80x..2.00x) & Dynamic Pitch Correction Engine
    // =========================================================================

    fun setPlaybackSpeed(speed: Float) {
        val rounded = ((speed.coerceIn(0.80f, 2.00f) * 100f).roundToInt() / 100f)
        val updated = _speedPitchState.value.copy(speed = rounded)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        audioEngine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setPitchCorrectionMode(mode: PitchCorrectionMode) {
        val updated = _speedPitchState.value.copy(pitchMode = mode)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        audioEngine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setIndependentSemitones(semitones: Float) {
        val ratio = SpeedPitchState.semitonesToRatio(semitones.coerceIn(-12f, 12f))
        val updated = _speedPitchState.value.copy(
            pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
            independentPitchRatio = ratio
        )
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        audioEngine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setIndependentPitchRatio(ratio: Float) {
        val clean = ratio.coerceIn(0.50f, 2.00f)
        val updated = _speedPitchState.value.copy(
            pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
            independentPitchRatio = clean
        )
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        audioEngine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun resetSpeedAndPitch() {
        val resetState = SpeedPitchState(
            speed = 1.0f,
            pitchMode = PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH,
            independentPitchRatio = 1.0f
        )
        _speedPitchState.value = resetState
        persistSpeedPitchState(resetState)
        audioEngine.setSpeedAndPitch(1.0f, 1.0f)
    }

    // =========================================================================
    // Live Studio Reverb DSP Engine (Simultaneous with Speed & Dynamic Pitch)
    // =========================================================================

    fun setReverbEnabled(enabled: Boolean) {
        val current = _reverbState.value
        val nextPreset = if (enabled && current.preset == ReverbPreset.OFF) {
            ReverbPreset.STUDIO_ROOM
        } else {
            current.preset
        }
        val updated = if (enabled && current.preset == ReverbPreset.OFF) {
            ReverbState.fromPreset(ReverbPreset.STUDIO_ROOM).copy(enabled = true)
        } else {
            current.copy(enabled = enabled, preset = nextPreset)
        }
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
    }

    fun selectReverbPreset(preset: ReverbPreset) {
        val updated = ReverbState.fromPreset(preset).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
        _statusBanner.value = if (updated.isActivelyProcessing) {
            "Live Reverb: ${preset.label} (${(updated.wetDryMix * 100).roundToInt()}% Wet)"
        } else {
            "Live Reverb bypassed (Dry signal)"
        }
    }

    fun setReverbWetDryMix(wetDryMix: Float) {
        val clean = wetDryMix.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = clean > 0.005f,
            preset = if (clean <= 0.005f) ReverbPreset.OFF else ReverbPreset.CUSTOM,
            wetDryMix = clean
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
    }

    fun setReverbRoomSize(roomSize: Float) {
        val clean = roomSize.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            roomSize = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
    }

    fun setReverbDecay(decay: Float) {
        val clean = decay.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            decay = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
    }

    fun setReverbDamping(damping: Float) {
        val clean = damping.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            damping = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        audioEngine.setReverbState(updated)
    }

    fun resetReverbToDry() {
        val dry = ReverbState.fromPreset(ReverbPreset.OFF)
        _reverbState.value = dry
        persistReverbState(dry)
        audioEngine.setReverbState(dry)
        _statusBanner.value = "Live Reverb reset to clean dry studio signal"
    }

    fun processLiveDspStereoBlock(interleavedStereo: FloatArray): FloatArray {
        audioEngine.processLiveDspStereoBlock(interleavedStereo)
        return interleavedStereo
    }

    // =========================================================================
    // Sleep Timer Engine (Duration Countdown OR End of Current Track)
    // =========================================================================

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
        audioEngine.setOutputVolume(1.0f)
        _statusBanner.value = "Sleep timer set for $cleanMins min"
        runSleepTimerCountdown()
    }

    fun startSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        audioEngine.setOutputVolume(1.0f)
        _sleepTimerState.value = _sleepTimerState.value.copy(
            isActive = true,
            stopAtEndOfCurrentTrack = true,
            remainingMs = 0L,
            waitingForTrackEnd = true
        )
        _statusBanner.value = "Sleep timer will stop playback at the end of this track"
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
        audioEngine.setOutputVolume(1.0f)
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
        audioEngine.setOutputVolume(1.0f)
        _sleepTimerState.value = _sleepTimerState.value.copy(
            isActive = false,
            stopAtEndOfCurrentTrack = false,
            remainingMs = 0L,
            waitingForTrackEnd = false
        )
        _statusBanner.value = "Sleep timer cancelled"
    }

    private fun runSleepTimerCountdown() {
        sleepTimerJob?.cancel()
        sleepTimerJob = viewModelScope.launch {
            while (isActive && _sleepTimerState.value.isActive && !_sleepTimerState.value.stopAtEndOfCurrentTrack) {
                delay(1000L)
                val state = _sleepTimerState.value
                if (!state.isActive) break
                val nextRemaining = (state.remainingMs - 1000L).coerceAtLeast(0L)
                _sleepTimerState.value = state.copy(remainingMs = nextRemaining)

                // Apply gentle volume fade-out during the final 10 seconds if stopping immediately
                if (state.gentleFadeOut && !state.finishLastTrackOnExpiry && nextRemaining in 1L..10_000L) {
                    val fadeVol = (nextRemaining.toFloat() / 10_000f).coerceIn(0.08f, 1.0f)
                    audioEngine.setOutputVolume(fadeVol)
                }

                if (nextRemaining == 0L) {
                    if (state.finishLastTrackOnExpiry && isPlaying.value) {
                        _sleepTimerState.value = state.copy(
                            remainingMs = 0L,
                            waitingForTrackEnd = true
                        )
                        _statusBanner.value = "Sleep timer reached 0:00 — finishing current track"
                    } else {
                        audioEngine.pausePlayback()
                        _sleepTimerState.value = state.copy(
                            isActive = false,
                            remainingMs = 0L,
                            waitingForTrackEnd = false
                        )
                        _statusBanner.value = "Sleep timer finished — playback paused"
                    }
                    break
                }
            }
        }
    }

    // =========================================================================
    // Settings & Customization Updaters (Persisted + Live)
    // =========================================================================

    private fun updateCustomization(transform: (PlayerCustomizationSettings) -> PlayerCustomizationSettings) {
        val next = transform(_customizationSettings.value)
        _customizationSettings.value = next
        persistCustomizationSettings(next)
    }

    fun setAppIconVariant(variant: AppIconVariant) {
        updateCustomization { it.copy(appIconVariant = variant) }
        AppIconLauncherManager.applyLauncherIconVariant(getApplication(), variant)
        _statusBanner.value = "App Icon updated to ${variant.label}"
    }

    fun setDockStyle(style: DockStyle) {
        updateCustomization { it.copy(dockStyle = style) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        updateCustomization { it.copy(themeMode = mode) }
        followThemeIcon()
    }

    fun setUseMaterialYouDynamicColor(enabled: Boolean) {
        updateCustomization { it.copy(useMaterialYouDynamicColor = enabled) }
    }

    fun setHarmonizePlayerWithArtwork(enabled: Boolean) {
        updateCustomization { it.copy(harmonizePlayerWithArtwork = enabled) }
    }

    fun setUnifiedMaterialYouColor(enabled: Boolean) {
        updateCustomization {
            it.copy(
                unifiedMaterialYouColor = enabled,
                useMaterialYouDynamicColor = if (enabled) true else it.useMaterialYouDynamicColor,
                harmonizePlayerWithArtwork = !enabled
            )
        }
        _statusBanner.value = if (enabled) {
            "Material You Expressive Active — 1 unified color across app, player, notification & widget"
        } else {
            "Dynamic Song Artwork Palette Active — Colors adapt per track"
        }
    }

    fun setCuratedColorPalette(palette: CuratedColorPalette) {
        updateCustomization {
            it.copy(
                curatedPalette = palette,
                useMaterialYouDynamicColor = false,
                unifiedMaterialYouColor = false
            )
        }
        followThemeIcon()
    }

    fun setInterfaceStyle(style: InterfaceStyle) {
        updateCustomization { it.copy(interfaceStyle = style) }
        followThemeIcon()
    }

    fun setHeaderFont(font: AppFont) = updateCustomization { it.copy(headerFont = font) }

    fun setBodyFont(font: AppFont) = updateCustomization { it.copy(bodyFont = font) }

    fun setArtworkCut(cut: io.github.roggzuay.ondalune.data.model.ArtworkCut) = updateCustomization { it.copy(artworkCut = cut) }

    /** Saves a custom Home order (visible sections only). */
    fun setHomeSections(sections: List<HomeSection>) = updateCustomization { it.copy(homeSections = sections) }

    /** Drops the custom Home order so Home follows the theme's layout again. */
    fun resetHomeSections() = updateCustomization { it.copy(homeSections = emptyList()) }

    fun setIconFollowsTheme(enabled: Boolean) {
        updateCustomization { it.copy(iconFollowsTheme = enabled) }
        followThemeIcon()
    }

    /**
     * When the icon follows the theme, switches the launcher icon to the variant nearest the
     * theme's primary colour: Daylight for light mode, Graphite for grey palettes, otherwise the
     * closest hue.
     */
    private fun followThemeIcon() {
        val s = _customizationSettings.value
        if (!s.iconFollowsTheme) return
        val palette = resolveDynamicSurfacePalette(
            getApplication(), s.curatedPalette.primarySeedHex, s.curatedPalette.accentSeedHex,
            s.unifiedMaterialYouColor, s.useMaterialYouDynamicColor, harmonizeWithArtwork = false,
            curatedPalette = s.curatedPalette
        )
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(palette.primaryArgb, hsv)
        val target = when {
            s.themeMode == AppThemeMode.LIGHT -> AppIconVariant.CREAM
            hsv[1] < 0.18f -> AppIconVariant.NIGHT
            else -> listOf(
                AppIconVariant.DUSK to 15f, AppIconVariant.MOSS to 120f, AppIconVariant.TIDE to 210f,
                AppIconVariant.AMETHYST to 290f
            ).minBy { (_, hue) -> minOf(kotlin.math.abs(hsv[0] - hue), 360f - kotlin.math.abs(hsv[0] - hue)) }.first
        }
        if (target != s.appIconVariant) {
            updateCustomization { it.copy(appIconVariant = target) }
            AppIconLauncherManager.applyLauncherIconVariant(getApplication(), target)
        }
    }

    fun setCornerStylePreset(preset: CornerStylePreset) {
        updateCustomization { it.copy(cornerStylePreset = preset) }
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundAnimationStyle) {
        updateCustomization { it.copy(playerBackgroundStyle = style) }
        _statusBanner.value = "Player Background Animation: ${style.label}"
    }

    fun setSeekBarStyle(style: SeekBarStyle) {
        updateCustomization {
            it.copy(
                seekBarStyle = style,
                animatedSquigglyWave = style == SeekBarStyle.SQUIGGLY_WAVE
            )
        }
    }

    fun setShowRemainingTime(showRemaining: Boolean) {
        updateCustomization { it.copy(showRemainingTime = showRemaining) }
    }

    fun setShowTechnicalFormatBadge(show: Boolean) {
        updateCustomization { it.copy(showTechnicalFormatBadge = show) }
    }

    fun setShowPlayerStarButton(show: Boolean) {
        updateCustomization { it.copy(showPlayerStarButton = show) }
    }

    fun setShowCodecBadgesInLists(show: Boolean) {
        updateCustomization { it.copy(showCodecBadgesInLists = show) }
    }

    fun setShowStarRatingsInLists(show: Boolean) {
        updateCustomization { it.copy(showStarRatingsInLists = show) }
    }

    fun setFallbackArtistToAlbumArt(enabled: Boolean) {
        updateCustomization { it.copy(fallbackArtistToAlbumArt = enabled) }
    }

    fun setDefaultGridColumns(columns: Int) {
        val clamped = columns.coerceIn(2, 4)
        updateCustomization { it.copy(defaultGridColumns = clamped) }
        val updatedMap = _tabDisplayPreferences.value.mapValues { (key, pref) ->
            val next = pref.copy(gridColumns = clamped)
            persistTabPreference(key, next)
            next
        }
        _tabDisplayPreferences.value = updatedMap
    }

    fun setCrossfadeSeconds(seconds: Int) {
        updateCustomization { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        updateCustomization { it.copy(gaplessPlayback = enabled) }
    }

    fun setReplayGainMode(mode: ReplayGainMode) {
        updateCustomization { it.copy(replayGainMode = mode) }
    }

    fun setAutoScrobble(enabled: Boolean) {
        updateCustomization { it.copy(autoScrobble = enabled) }
    }

    fun setResumePlaybackOnLaunch(enabled: Boolean) {
        updateCustomization { it.copy(resumePlaybackOnLaunch = enabled) }
    }

    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        audioEngine.pauseOnHeadphoneDisconnect = enabled
        updateCustomization { it.copy(pauseOnHeadphoneDisconnect = enabled) }
    }

    fun setAnimatedSquigglyWave(enabled: Boolean) {
        updateCustomization {
            it.copy(
                animatedSquigglyWave = enabled,
                seekBarStyle = if (enabled) SeekBarStyle.SQUIGGLY_WAVE else SeekBarStyle.CLASSIC_LINEAR
            )
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        updateCustomization { it.copy(hapticWobbleButtons = enabled) }
    }

    fun setHighRefreshRate120Hz(enabled: Boolean) {
        updateCustomization { it.copy(highRefreshRate120Hz = enabled) }
        _statusBanner.value = if (enabled) {
            "120Hz High-Refresh Display Mode enabled"
        } else {
            "Standard 60Hz refresh rate mode"
        }
    }

    fun setSmartShuffleProfile(profile: SmartShuffleProfile) {
        updateCustomization { it.copy(smartShuffleProfile = profile) }
        if (_shuffleMode.value == ShuffleMode.SMART) {
            val current = _currentTrack.value
            val base = tracks.value
            if (base.isNotEmpty()) {
                _playbackQueue.value = buildSmartShuffledQueue(current, base, profile)
            }
        }
        _statusBanner.value = "Smart Shuffle profile: ${profile.label}"
    }

    fun setDownloadSpeedLimit(limit: DownloadSpeedLimit) {
        updateCustomization { it.copy(downloadSpeedLimit = limit) }
        _statusBanner.value = "Download bandwidth limit set to ${limit.label}"
    }

    fun setReadAheadBuffer(tracksCount: Int) {
        updateCustomization { it.copy(readAheadTrackBuffer = tracksCount) }
    }

    fun setMobileQuality(format: String, maxBitRate: Int) {
        updateCustomization {
            it.copy(
                mobileTranscodeFormat = format,
                mobileMaxBitRate = maxBitRate
            )
        }
        _statusBanner.value = "Cellular quality set to $format • ${maxBitRate}kbps"
    }

    fun setWidgetLayoutMode(mode: WidgetLayoutMode) {
        updateCustomization { it.copy(widgetLayoutMode = mode) }
        _statusBanner.value = "Home Widget Layout: ${mode.label}"
    }

    fun setWidgetThemeMode(mode: WidgetThemeMode) {
        updateCustomization { it.copy(widgetThemeMode = mode) }
        _statusBanner.value = "Home Widget Theme: ${mode.label}"
    }

    fun setWidgetShowArtwork(show: Boolean) {
        updateCustomization { it.copy(widgetShowArtwork = show) }
    }

    fun setWidgetShowFavoriteButton(show: Boolean) {
        updateCustomization { it.copy(widgetShowFavoriteButton = show) }
    }

    fun setWidgetShowLyrics(show: Boolean) {
        updateCustomization { it.copy(widgetShowLyrics = show) }
    }

    /**
     * Restores all UI, Library, Player, DSP, and Widget preferences back to factory defaults
     * without deleting any saved OpenSubsonic servers, tracks, or playlists.
     */
    fun restoreDefaultCustomizationSettings() {
        val defaults = PlayerCustomizationSettings()
        _customizationSettings.value = defaults
        persistCustomizationSettings(defaults)

        val defaultTabs = ALL_CATEGORY_KEYS.associateWith { key ->
            val pref = TabDisplayPreference.defaultForCategory(key)
            persistTabPreference(key, pref)
            pref
        }
        _tabDisplayPreferences.value = defaultTabs
        _libraryViewMode.value = defaultTabs[_libraryFilterTab.value.name]?.viewMode ?: LibraryViewMode.GRID
        _librarySortOption.value = defaultTabs[_libraryFilterTab.value.name]?.sortOption ?: LibrarySortOption.NAME_ASC

        resetSpeedAndPitch()
        resetReverbToDry()
        selectEqualizerPreset(EqualizerPreset.FLAT_STUDIO)
        _artworkShapeMode.value = ArtworkShapeMode.SQUIRCLE
        audioEngine.pauseOnHeadphoneDisconnect = defaults.pauseOnHeadphoneDisconnect
        _statusBanner.value = "Restored default appearance, library & DSP settings (Servers & library preserved)"
    }

    // =========================================================================
    // SharedPreferences Persistence Helpers
    // =========================================================================

    private fun loadPersistedSpeedPitchState(): SpeedPitchState {
        val speed = prefs.getFloat("dsp_speed", 1.0f).coerceIn(0.80f, 2.00f)
        val modeName = prefs.getString("dsp_pitch_mode", PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH.name)
        val mode = PitchCorrectionMode.entries.find { it.name == modeName }
            ?: PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH
        val ratio = prefs.getFloat("dsp_pitch_ratio", 1.0f).coerceIn(0.50f, 2.00f)
        return SpeedPitchState(speed = speed, pitchMode = mode, independentPitchRatio = ratio)
    }

    private fun persistSpeedPitchState(state: SpeedPitchState) {
        prefs.edit()
            .putFloat("dsp_speed", state.speed)
            .putString("dsp_pitch_mode", state.pitchMode.name)
            .putFloat("dsp_pitch_ratio", state.independentPitchRatio)
            .apply()
    }

    private fun loadPersistedReverbState(): ReverbState {
        val enabled = prefs.getBoolean("reverb_enabled", false)
        val presetName = prefs.getString("reverb_preset", ReverbPreset.OFF.name)
        val preset = ReverbPreset.entries.find { it.name == presetName } ?: ReverbPreset.OFF
        val wetDry = prefs.getFloat("reverb_wet_dry", preset.defaultWetDryMix)
        val roomSize = prefs.getFloat("reverb_room_size", preset.defaultRoomSize)
        val decay = prefs.getFloat("reverb_decay", preset.defaultDecay)
        val damping = prefs.getFloat("reverb_damping", preset.defaultDamping)
        return ReverbState(
            enabled = enabled,
            preset = preset,
            wetDryMix = wetDry,
            roomSize = roomSize,
            decay = decay,
            damping = damping
        ).sanitized()
    }

    private fun persistReverbState(state: ReverbState) {
        val clean = state.sanitized()
        prefs.edit()
            .putBoolean("reverb_enabled", clean.enabled)
            .putString("reverb_preset", clean.preset.name)
            .putFloat("reverb_wet_dry", clean.wetDryMix)
            .putFloat("reverb_room_size", clean.roomSize)
            .putFloat("reverb_decay", clean.decay)
            .putFloat("reverb_damping", clean.damping)
            .apply()
    }

    private fun loadPersistedTabPreferences(): Map<String, TabDisplayPreference> {
        return ALL_CATEGORY_KEYS.associateWith { key ->
            val defaultPref = TabDisplayPreference.defaultForCategory(key)
            val viewName = prefs.getString("tab_${key}_view", defaultPref.viewMode.name)
            val sortName = prefs.getString("tab_${key}_sort", defaultPref.sortOption.name)
            val asc = prefs.getBoolean("tab_${key}_asc", defaultPref.ascending)
            val cols = prefs.getInt("tab_${key}_cols", defaultPref.gridColumns).coerceIn(2, 4)
            TabDisplayPreference(
                viewMode = LibraryViewMode.entries.find { it.name == viewName } ?: defaultPref.viewMode,
                sortOption = LibrarySortOption.entries.find { it.name == sortName } ?: defaultPref.sortOption,
                ascending = asc,
                gridColumns = cols
            )
        }
    }

    private fun persistTabPreference(categoryKey: String, pref: TabDisplayPreference) {
        val key = categoryKey.uppercase()
        prefs.edit()
            .putString("tab_${key}_view", pref.viewMode.name)
            .putString("tab_${key}_sort", pref.sortOption.name)
            .putBoolean("tab_${key}_asc", pref.ascending)
            .putInt("tab_${key}_cols", pref.gridColumns.coerceIn(2, 4))
            .apply()
    }

    private fun loadPersistedCustomizationSettings(): PlayerCustomizationSettings {
        val defaults = PlayerCustomizationSettings()
        if (!prefs.contains("cfg_initialized")) return defaults
        return PlayerCustomizationSettings(
            appIconVariant = AppIconVariant.entries.find { it.name == prefs.getString("cfg_app_icon_variant", defaults.appIconVariant.name) } ?: defaults.appIconVariant,
            dockStyle = DockStyle.entries.find { it.name == prefs.getString("cfg_dock_style", defaults.dockStyle.name) } ?: defaults.dockStyle,
            themeMode = AppThemeMode.entries.find { it.name == prefs.getString("cfg_theme_mode", defaults.themeMode.name) } ?: defaults.themeMode,
            useMaterialYouDynamicColor = prefs.getBoolean("cfg_material_you", defaults.useMaterialYouDynamicColor),
            unifiedMaterialYouColor = prefs.getBoolean("cfg_unified_material_you", defaults.unifiedMaterialYouColor),
            curatedPalette = CuratedColorPalette.entries.find { it.name == prefs.getString("cfg_curated_palette", defaults.curatedPalette.name) } ?: defaults.curatedPalette,
            cornerStylePreset = CornerStylePreset.entries.find { it.name == prefs.getString("cfg_corner_preset", defaults.cornerStylePreset.name) } ?: defaults.cornerStylePreset,
            interfaceStyle = InterfaceStyle.entries.find { it.name == prefs.getString("cfg_ui_style", defaults.interfaceStyle.name) } ?: defaults.interfaceStyle,
            headerFont = AppFont.entries.find { it.name == prefs.getString("cfg_header_font", null) } ?: defaults.headerFont,
            bodyFont = AppFont.entries.find { it.name == prefs.getString("cfg_body_font", null) } ?: defaults.bodyFont,
            artworkCut = io.github.roggzuay.ondalune.data.model.ArtworkCut.entries.find { it.name == prefs.getString("cfg_artwork_cut", null) } ?: defaults.artworkCut,
            homeSections = prefs.getString("cfg_home_sections", "").orEmpty().split(',')
                .mapNotNull { name -> HomeSection.entries.find { it.name == name } },
            iconFollowsTheme = prefs.getBoolean("cfg_icon_follows_theme", defaults.iconFollowsTheme),
            harmonizePlayerWithArtwork = prefs.getBoolean("cfg_harmonize_artwork", defaults.harmonizePlayerWithArtwork),
            playerBackgroundStyle = PlayerBackgroundAnimationStyle.entries.find { it.name == prefs.getString("cfg_bg_style_v2", defaults.playerBackgroundStyle.name) } ?: defaults.playerBackgroundStyle,
            seekBarStyle = SeekBarStyle.entries.find { it.name == prefs.getString("cfg_seekbar_style", defaults.seekBarStyle.name) } ?: defaults.seekBarStyle,
            showRemainingTime = prefs.getBoolean("cfg_show_remaining_time", defaults.showRemainingTime),
            showTechnicalFormatBadge = prefs.getBoolean("cfg_show_tech_badge", defaults.showTechnicalFormatBadge),
            showPlayerStarButton = prefs.getBoolean("cfg_show_player_star", defaults.showPlayerStarButton),
            showCodecBadgesInLists = prefs.getBoolean("cfg_show_codec_lists", defaults.showCodecBadgesInLists),
            showStarRatingsInLists = prefs.getBoolean("cfg_show_ratings_lists", defaults.showStarRatingsInLists),
            fallbackArtistToAlbumArt = prefs.getBoolean("cfg_fallback_artist_album", defaults.fallbackArtistToAlbumArt),
            defaultGridColumns = prefs.getInt("cfg_default_grid_cols", defaults.defaultGridColumns).coerceIn(2, 4),
            crossfadeSeconds = prefs.getInt("cfg_crossfade_sec", defaults.crossfadeSeconds).coerceIn(0, 12),
            gaplessPlayback = prefs.getBoolean("cfg_gapless", defaults.gaplessPlayback),
            replayGainMode = ReplayGainMode.entries.find { it.name == prefs.getString("cfg_replay_gain", defaults.replayGainMode.name) } ?: defaults.replayGainMode,
            autoScrobble = prefs.getBoolean("cfg_auto_scrobble", defaults.autoScrobble),
            resumePlaybackOnLaunch = prefs.getBoolean("cfg_resume_on_launch", defaults.resumePlaybackOnLaunch),
            pauseOnHeadphoneDisconnect = prefs.getBoolean("cfg_pause_noisy", defaults.pauseOnHeadphoneDisconnect),
            animatedSquigglyWave = prefs.getBoolean("cfg_squiggly_wave", defaults.animatedSquigglyWave),
            hapticWobbleButtons = prefs.getBoolean("cfg_haptics", defaults.hapticWobbleButtons),
            highRefreshRate120Hz = prefs.getBoolean("cfg_120hz", defaults.highRefreshRate120Hz),
            smartShuffleProfile = SmartShuffleProfile.entries.find { it.name == prefs.getString("cfg_smart_shuffle", defaults.smartShuffleProfile.name) } ?: defaults.smartShuffleProfile,
            downloadSpeedLimit = DownloadSpeedLimit.entries.find { it.name == prefs.getString("cfg_dl_limit", defaults.downloadSpeedLimit.name) } ?: defaults.downloadSpeedLimit,
            readAheadTrackBuffer = prefs.getInt("cfg_read_ahead", defaults.readAheadTrackBuffer),
            wifiTranscodeFormat = prefs.getString("cfg_wifi_fmt", defaults.wifiTranscodeFormat) ?: defaults.wifiTranscodeFormat,
            wifiMaxBitRate = prefs.getInt("cfg_wifi_kbps", defaults.wifiMaxBitRate),
            mobileTranscodeFormat = prefs.getString("cfg_mobile_fmt", defaults.mobileTranscodeFormat) ?: defaults.mobileTranscodeFormat,
            mobileMaxBitRate = prefs.getInt("cfg_mobile_kbps", defaults.mobileMaxBitRate),
            widgetLayoutMode = WidgetLayoutMode.entries.find { it.name == prefs.getString("cfg_widget_layout", defaults.widgetLayoutMode.name) } ?: defaults.widgetLayoutMode,
            widgetThemeMode = WidgetThemeMode.entries.find { it.name == prefs.getString("cfg_widget_theme", defaults.widgetThemeMode.name) } ?: defaults.widgetThemeMode,
            widgetShowArtwork = prefs.getBoolean("cfg_widget_artwork", defaults.widgetShowArtwork),
            widgetShowFavoriteButton = prefs.getBoolean("cfg_widget_fav", defaults.widgetShowFavoriteButton),
            widgetShowLyrics = prefs.getBoolean("cfg_widget_lyrics", defaults.widgetShowLyrics)
        )
    }

    private fun persistCustomizationSettings(s: PlayerCustomizationSettings) {
        prefs.edit()
            .putBoolean("cfg_initialized", true)
            .putString("cfg_app_icon_variant", s.appIconVariant.name)
            .putString("cfg_dock_style", s.dockStyle.name)
            .putString("cfg_theme_mode", s.themeMode.name)
            .putBoolean("cfg_material_you", s.useMaterialYouDynamicColor)
            .putBoolean("cfg_unified_material_you", s.unifiedMaterialYouColor)
            .putString("cfg_curated_palette", s.curatedPalette.name)
            .putString("cfg_corner_preset", s.cornerStylePreset.name)
            .putString("cfg_ui_style", s.interfaceStyle.name)
            .putString("cfg_header_font", s.headerFont.name)
            .putString("cfg_body_font", s.bodyFont.name)
            .putString("cfg_artwork_cut", s.artworkCut.name)
            .putString("cfg_home_sections", s.homeSections.joinToString(",") { it.name })
            .putBoolean("cfg_icon_follows_theme", s.iconFollowsTheme)
            .putBoolean("cfg_harmonize_artwork", s.harmonizePlayerWithArtwork)
            .putString("cfg_bg_style_v2", s.playerBackgroundStyle.name)
            .putString("cfg_seekbar_style", s.seekBarStyle.name)
            .putBoolean("cfg_show_remaining_time", s.showRemainingTime)
            .putBoolean("cfg_show_tech_badge", s.showTechnicalFormatBadge)
            .putBoolean("cfg_show_player_star", s.showPlayerStarButton)
            .putBoolean("cfg_show_codec_lists", s.showCodecBadgesInLists)
            .putBoolean("cfg_show_ratings_lists", s.showStarRatingsInLists)
            .putBoolean("cfg_fallback_artist_album", s.fallbackArtistToAlbumArt)
            .putInt("cfg_default_grid_cols", s.defaultGridColumns)
            .putInt("cfg_crossfade_sec", s.crossfadeSeconds)
            .putBoolean("cfg_gapless", s.gaplessPlayback)
            .putString("cfg_replay_gain", s.replayGainMode.name)
            .putBoolean("cfg_auto_scrobble", s.autoScrobble)
            .putBoolean("cfg_resume_on_launch", s.resumePlaybackOnLaunch)
            .putBoolean("cfg_pause_noisy", s.pauseOnHeadphoneDisconnect)
            .putBoolean("cfg_squiggly_wave", s.animatedSquigglyWave)
            .putBoolean("cfg_haptics", s.hapticWobbleButtons)
            .putBoolean("cfg_120hz", s.highRefreshRate120Hz)
            .putString("cfg_smart_shuffle", s.smartShuffleProfile.name)
            .putString("cfg_dl_limit", s.downloadSpeedLimit.name)
            .putInt("cfg_read_ahead", s.readAheadTrackBuffer)
            .putString("cfg_wifi_fmt", s.wifiTranscodeFormat)
            .putInt("cfg_wifi_kbps", s.wifiMaxBitRate)
            .putString("cfg_mobile_fmt", s.mobileTranscodeFormat)
            .putInt("cfg_mobile_kbps", s.mobileMaxBitRate)
            .putString("cfg_widget_layout", s.widgetLayoutMode.name)
            .putString("cfg_widget_theme", s.widgetThemeMode.name)
            .putBoolean("cfg_widget_artwork", s.widgetShowArtwork)
            .putBoolean("cfg_widget_fav", s.widgetShowFavoriteButton)
            .putBoolean("cfg_widget_lyrics", s.widgetShowLyrics)
            .apply()
    }

    /**
     * Builds a personalized Smart Shuffle queue using Efraimidis-Spirakis weighted reservoir
     * sampling (`u^(1 / weight)`) where weight combines:
     * - User rating (1–5★)
     * - Starred / Favourite status
     * - Play count frequency
     * Followed by an artist de-clustering pass so back-to-back songs vary across artists.
     */
    private fun buildSmartShuffledQueue(
        anchorTrack: SubsonicTrack?,
        candidates: List<SubsonicTrack>,
        profile: SmartShuffleProfile = _customizationSettings.value.smartShuffleProfile
    ): List<SubsonicTrack> {
        if (candidates.isEmpty()) return emptyList()
        val remaining = if (anchorTrack != null) {
            candidates.filter { it.id != anchorTrack.id }
        } else {
            candidates
        }

        val weightedSorted = remaining
            .map { track ->
                val weight = track.smartShuffleWeight(profile)
                val u = Random.nextDouble(0.08, 0.9999)
                val key = u.pow(1.0 / weight)
                track to key
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toMutableList()

        // Artist de-clustering pass: avoid consecutive tracks by the same artist when possible
        for (i in 0 until (weightedSorted.size - 1)) {
            val prevArtist = if (i == 0 && anchorTrack != null) {
                anchorTrack.artist
            } else {
                weightedSorted[i].artist
            }
            val checkIdx = if (i == 0 && anchorTrack != null) 0 else i + 1
            if (checkIdx in weightedSorted.indices && weightedSorted[checkIdx].artist == prevArtist) {
                val swapIdx = ((checkIdx + 1) until weightedSorted.size).firstOrNull { idx ->
                    weightedSorted[idx].artist != prevArtist
                }
                if (swapIdx != null) {
                    val temp = weightedSorted[checkIdx]
                    weightedSorted[checkIdx] = weightedSorted[swapIdx]
                    weightedSorted[swapIdx] = temp
                }
            }
        }

        return if (anchorTrack != null) {
            listOf(anchorTrack) + weightedSorted
        } else {
            weightedSorted
        }
    }

    private fun buildQueueForMode(
        anchorTrack: SubsonicTrack,
        candidates: List<SubsonicTrack>,
        mode: ShuffleMode
    ): List<SubsonicTrack> {
        return when (mode) {
            ShuffleMode.OFF -> candidates
            ShuffleMode.STANDARD -> listOf(anchorTrack) + candidates.filter { it.id != anchorTrack.id }.shuffled()
            ShuffleMode.SMART -> buildSmartShuffledQueue(
                anchorTrack = anchorTrack,
                candidates = candidates,
                profile = _customizationSettings.value.smartShuffleProfile
            )
        }
    }

    fun playTrack(track: SubsonicTrack, queueContext: List<SubsonicTrack>? = null) {
        val newQueue = queueContext?.ifEmpty { null } ?: tracks.value.ifEmpty { listOf(track) }
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

    /** Makes [track] current; [startEngine] false when the engine already moved on to it by itself. */
    private fun startPlayback(track: SubsonicTrack, startEngine: Boolean = true) {
        _currentTrack.value = track
        prefs.edit().putString("last_played_track_id", track.id).apply()
        val server = activeServer()
        if (startEngine) audioEngine.playTrack(track, server, _equalizerPreset.value)
        loadLyricsForTrack(track)
        if (_customizationSettings.value.autoScrobble) {
            viewModelScope.launch {
                repository.recordPlay(server, track)
            }
        }
    }

    fun playAlbum(albumId: String, shuffle: Boolean = false) {
        val albumTracks = tracks.value.filter { it.albumId == albumId }
        if (albumTracks.isEmpty()) return
        if (shuffle) {
            _shuffleMode.value = ShuffleMode.SMART
            _shuffleEnabled.value = true
            val smartOrdered = buildSmartShuffledQueue(
                anchorTrack = null,
                candidates = albumTracks,
                profile = _customizationSettings.value.smartShuffleProfile
            )
            playTrack(smartOrdered.first(), smartOrdered)
            _statusBanner.value = "Smart Shuffle: Prioritizing top-rated tracks in ${smartOrdered.first().album}"
        } else {
            playTrack(albumTracks.first(), albumTracks)
        }
    }

    fun startSmartShuffleSession(sourceTracks: List<SubsonicTrack>? = null) {
        val pool = sourceTracks?.ifEmpty { null } ?: tracks.value
        if (pool.isEmpty()) return
        val profile = _customizationSettings.value.smartShuffleProfile
        _shuffleMode.value = ShuffleMode.SMART
        _shuffleEnabled.value = true
        val smartQueue = buildSmartShuffledQueue(
            anchorTrack = null,
            candidates = pool,
            profile = profile
        )
        val lead = smartQueue.first()
        _playbackQueue.value = smartQueue
        _currentTrack.value = lead
        prefs.edit().putString("last_played_track_id", lead.id).apply()
        val server = activeServer()
        audioEngine.playTrack(lead, server, _equalizerPreset.value)
        loadLyricsForTrack(lead)
        if (_customizationSettings.value.autoScrobble) {
            viewModelScope.launch {
                repository.recordPlay(server, lead)
            }
        }
        _statusBanner.value =
            "Smart Shuffle (${profile.label}): Playing \"${lead.title}\" (${lead.userRating}★ • ${lead.playCount} plays • ${lead.smartAffinityPercent(profile)}% match)"
    }

    /**
     * "Your Mix": favourites, top and recent tracks as seeds, topped up with library songs that
     * share their genres, artists and era, then shuffled so every press sounds different.
     */
    fun playYourMix() {
        val shelves = homeShelves.value
        val seeds = (shelves.favouriteTracks.take(25) + shelves.mostPlayed.take(25) + shelves.jumpBackIn.take(15))
            .distinctBy { it.id }
        val library = allServerTracks.value
        if (library.isEmpty()) return
        viewModelScope.launch {
            // Scoring the whole library is too slow for the main thread on big libraries; it
            // would stall the hero's launch animation.
            val picks = withContext(Dispatchers.Default) {
                if (seeds.isEmpty()) {
                    library.shuffled().take(60)
                } else {
                    val core = seeds.shuffled().take(20)
                    (core + similarTracks(core, core.map { it.id }.toSet(), 40)).shuffled()
                }
            }
            if (picks.isEmpty()) return@launch
            _shuffleMode.value = ShuffleMode.OFF
            _shuffleEnabled.value = false
            _playbackQueue.value = picks
            persistQueueTrackIds(picks)
            startPlayback(picks.first())
            _statusBanner.value = "Your Mix: ${picks.size} songs picked for you"
        }
    }

    fun playPlaylist(playlist: SubsonicPlaylist) {
        val mapById = tracks.value.associateBy { it.id }
        val plTracks = playlist.trackIds.mapNotNull { mapById[it] }.ifEmpty { tracks.value }
        if (plTracks.isNotEmpty()) {
            playTrack(plTracks.first(), plTracks)
        }
    }

    fun togglePlayPause() {
        val current = _currentTrack.value
        if (current == null && tracks.value.isNotEmpty()) {
            playTrack(tracks.value.first(), tracks.value)
            return
        }
        if (!isPlaying.value && positionMs.value == 0L && current != null) {
            audioEngine.playTrack(current, activeServer(), _equalizerPreset.value)
        } else {
            audioEngine.togglePlayPause()
        }
    }

    fun skipNext() {
        if (_playbackQueue.value.isEmpty()) _playbackQueue.value = tracks.value
        if (_playbackQueue.value.isEmpty()) return
        // Walks the queue as built (never reshuffles), so "Play next" inserts stay where they were put.
        extendQueueWithSuggestionsIfEnding()
        val queue = _playbackQueue.value
        val idx = queue.indexOfFirst { it.id == _currentTrack.value?.id }
        startPlayback(queue.getOrNull(idx + 1) ?: queue.first())
        extendQueueWithSuggestionsIfEnding()
    }

    fun skipPrevious(restartIfPlayed: Boolean = true) {
        if (restartIfPlayed && positionMs.value > 4000L) {
            audioEngine.seekTo(0L)
            return
        }
        val queue = _playbackQueue.value.ifEmpty { tracks.value }
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

    // ---- Smart suggestions ---------------------------------------------------------------

    /**
     * When the current track is the last one queued, appends library tracks that sound like
     * what's playing: shared genres and sub-genres first, then same artist, then nearby years.
     */
    private fun extendQueueWithSuggestionsIfEnding() {
        if (_repeatOneEnabled.value) return
        val current = _currentTrack.value ?: return
        val queue = _playbackQueue.value
        val idx = queue.indexOfFirst { it.id == current.id }
        if (idx in 0 until queue.lastIndex) return
        val recentSeeds = (queue.takeLast(5) + current).distinctBy { it.id }
        val suggestions = similarTracks(recentSeeds, exclude = queue.mapTo(HashSet()) { it.id }, count = 25)
        if (suggestions.isEmpty()) return
        _playbackQueue.value = queue.ifEmpty { listOf(current) } + suggestions
        persistQueueTrackIds(_playbackQueue.value)
        _statusBanner.value = "Up next: songs like \"${current.title}\""
    }

    private fun similarTracks(seeds: List<SubsonicTrack>, exclude: Set<String>, count: Int): List<SubsonicTrack> {
        val seedGenres = seeds.flatMap { it.genreTokens() }.toSet()
        val seedArtists = seeds.map { it.artist.lowercase() }.toSet()
        val seedYear = seeds.map { it.year }.filter { it > 0 }.average().takeIf { !it.isNaN() }
        val random = kotlin.random.Random(System.nanoTime())
        val scored = allServerTracks.value.asSequence()
            .filter { it.id !in exclude }
            .map { t ->
                val shared = t.genreTokens().count { it in seedGenres }
                var score = shared * 3.0
                if (t.artist.lowercase() in seedArtists) score += 2.0
                if (seedYear != null && t.year > 0 && kotlin.math.abs(t.year - seedYear) <= 5) score += 1.0
                t to score
            }
            .filter { it.second > 0.0 }
            .toList()
        // Weighted shuffle: strong matches lead, but the queue doesn't replay the same order every
        // time. The jitter is drawn once per track; a random sort key breaks the sort contract.
        return scored.map { (t, score) -> t to score + random.nextDouble() * 2.5 }
            .sortedByDescending { it.second }
            .distinctBy { it.first.albumId to it.first.title }
            .take(count)
            .map { it.first }
    }

    /**
     * The song that plays after the current one, using the same rules as [handleTrackFinished]:
     * null when the sleep timer stops at the end of this song, the same song on repeat-one,
     * otherwise the next in the queue (wrapping round). [extendQueue] first adds suggestions when
     * the queue is about to run out, as skipping would.
     */
    private fun upcomingTrack(extendQueue: Boolean): SubsonicTrack? {
        val current = _currentTrack.value ?: return null
        val sleep = _sleepTimerState.value
        if (sleep.isActive && (sleep.stopAtEndOfCurrentTrack || sleep.waitingForTrackEnd)) return null
        if (_repeatOneEnabled.value) return current
        if (extendQueue) extendQueueWithSuggestionsIfEnding()
        val queue = _playbackQueue.value.ifEmpty { tracks.value }
        if (queue.isEmpty()) return null
        val idx = queue.indexOfFirst { it.id == current.id }
        return queue.getOrNull(idx + 1) ?: queue.first()
    }

    private fun handleTrackFinished() {
        val current = _currentTrack.value ?: return
        val sleep = _sleepTimerState.value
        // Check if Sleep Timer is set to stop at the end of the current track!
        if (sleep.isActive && (sleep.stopAtEndOfCurrentTrack || sleep.waitingForTrackEnd)) {
            audioEngine.pausePlayback()
            _sleepTimerState.value = sleep.copy(
                isActive = false,
                stopAtEndOfCurrentTrack = false,
                remainingMs = 0L,
                waitingForTrackEnd = false
            )
            _statusBanner.value = "Sleep timer paused playback at end of track"
            return
        }

        if (_repeatOneEnabled.value) {
            audioEngine.playTrack(current, activeServer(), _equalizerPreset.value)
        } else {
            skipNext()
        }
    }

    fun seekTo(targetMs: Long) {
        audioEngine.seekTo(targetMs)
        OndaluneMediaBridge.updatePlaybackProgress(
            context = getApplication(),
            positionMs = targetMs,
            durationMs = durationMs.value,
            isPlaying = isPlaying.value,
            playbackSpeed = playbackSpeed.value
        )
    }

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
        val base = tracks.value
        if (current != null && base.isNotEmpty()) {
            _playbackQueue.value = buildQueueForMode(current, base, mode)
        }
        val profile = _customizationSettings.value.smartShuffleProfile
        _statusBanner.value = when (mode) {
            ShuffleMode.SMART -> "Smart Shuffle Active (${profile.label}) — Prioritizing 4–5★ & most-played tracks"
            ShuffleMode.STANDARD -> "Standard Shuffle Active — Uniform random track order"
            ShuffleMode.OFF -> "Shuffle Off — Sequential playback order"
        }
    }

    fun toggleRepeatOne() {
        _repeatOneEnabled.value = !_repeatOneEnabled.value
    }

    fun toggleStarTrack(track: SubsonicTrack) {
        val updatedStarred = !track.starred
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(starred = updatedStarred)
        }
        viewModelScope.launch {
            repository.toggleStarTrack(activeServer(), track)
            if (_shuffleMode.value == ShuffleMode.SMART) {
                val cur = _currentTrack.value
                val latest = tracks.value
                if (cur != null && latest.isNotEmpty()) {
                    _playbackQueue.value = buildQueueForMode(cur, latest, ShuffleMode.SMART)
                }
            }
        }
    }

    fun setTrackRating(track: SubsonicTrack, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(userRating = clamped)
        }
        viewModelScope.launch {
            repository.setTrackRating(activeServer(), track, clamped)
            val cur = _currentTrack.value
            val latest = tracks.value
            if (_shuffleMode.value == ShuffleMode.SMART && cur != null && latest.isNotEmpty()) {
                _playbackQueue.value = buildQueueForMode(cur, latest, ShuffleMode.SMART)
            }
            _statusBanner.value = "Rated \"${track.title}\" ${clamped}★ — Smart Shuffle affinity updated"
        }
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        _customEqBands.value = preset.bandsDb
        prefs.edit().putString("eq_preset", preset.name).apply()
        audioEngine.setEqualizerBandsDb(preset.bandsDb)
    }

    fun updateCustomEqBand(bandIndex: Int, gainDb: Float) {
        val currentList = _customEqBands.value.toMutableList()
        if (bandIndex in currentList.indices) {
            currentList[bandIndex] = gainDb.coerceIn(-10f, 10f)
            _customEqBands.value = currentList
            audioEngine.setEqualizerBandsDb(currentList)
        }
    }

    // =========================================================================
    // Queue Editing (Play Next, Add to Queue, Remove, Reorder)
    // =========================================================================

    fun playTrackNext(track: SubsonicTrack) {
        val currentQueue = _playbackQueue.value.toMutableList()
        val currentId = _currentTrack.value?.id
        currentQueue.removeAll { it.id == track.id && it.id != currentId }
        val currentIndex = currentQueue.indexOfFirst { it.id == currentId }
        val insertIndex = if (currentIndex >= 0) (currentIndex + 1).coerceAtMost(currentQueue.size) else 0
        currentQueue.add(insertIndex, track)
        _playbackQueue.value = currentQueue
        persistQueueTrackIds(currentQueue)
        _statusBanner.value = "Playing next: \"${track.title}\""
    }

    fun addTrackToQueue(track: SubsonicTrack) {
        val currentQueue = _playbackQueue.value.toMutableList()
        if (currentQueue.none { it.id == track.id }) {
            currentQueue.add(track)
        } else {
            currentQueue.add(track)
        }
        _playbackQueue.value = currentQueue
        persistQueueTrackIds(currentQueue)
        _statusBanner.value = "Added \"${track.title}\" to queue"
    }

    fun removeTrackFromQueue(trackId: String) {
        val currentId = _currentTrack.value?.id
        if (trackId == currentId && _playbackQueue.value.size <= 1) return
        val updated = _playbackQueue.value.filterNot { it.id == trackId }
        _playbackQueue.value = updated
        persistQueueTrackIds(updated)
    }

    fun moveQueueTrack(fromIndex: Int, toIndex: Int) {
        val list = _playbackQueue.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _playbackQueue.value = list
        persistQueueTrackIds(list)
    }

    private fun persistQueueTrackIds(queue: List<SubsonicTrack>) {
        prefs.edit().putString("saved_queue_ids", queue.joinToString(",") { it.id }).apply()
    }

    // =========================================================================
    // Offline Downloads & Storage Management
    // =========================================================================

    fun downloadTrackForOffline(track: SubsonicTrack) {
        viewModelScope.launch {
            val server = activeServer()
            downloadManager.enqueueTrackDownload(
                server = server,
                track = track,
                speedLimit = _customizationSettings.value.downloadSpeedLimit
            )
            _statusBanner.value = "Downloading \"${track.title}\" for offline playback"
        }
    }

    fun downloadAlbumForOffline(albumId: String) {
        val albumTracks = tracks.value.filter { it.albumId == albumId }
        if (albumTracks.isEmpty()) return
        viewModelScope.launch {
            val server = activeServer()
            downloadManager.enqueueAlbumDownload(
                server = server,
                albumTracks = albumTracks,
                speedLimit = _customizationSettings.value.downloadSpeedLimit
            )
            _statusBanner.value = "Downloading ${albumTracks.size} tracks from \"${albumTracks.first().album}\" for offline playback"
        }
    }

    fun cancelOfflineDownload(trackId: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(_activeServerId.value, trackId)
            _statusBanner.value = "Cancelled download"
        }
    }

    fun removeOfflineDownload(trackId: String) {
        viewModelScope.launch {
            downloadManager.removeDownload(_activeServerId.value, trackId)
            _statusBanner.value = "Removed offline download"
        }
    }

    fun clearAllOfflineDownloads() {
        viewModelScope.launch {
            downloadManager.clearAllDownloadsForServer(_activeServerId.value)
            _statusBanner.value = "Cleared all offline downloads"
        }
    }

    fun clearTemporaryAudioCache() {
        viewModelScope.launch {
            downloadManager.clearTemporaryCache(_activeServerId.value)
            _statusBanner.value = "Cleared temporary audio cache"
        }
    }

    fun updateTranscodeProfile(format: String, maxBitRate: Int) {
        val currentServer = activeServer()
        val updated = currentServer.copy(transcodeFormat = format, maxBitRate = maxBitRate)
        updateCustomization {
            it.copy(
                wifiTranscodeFormat = format,
                wifiMaxBitRate = maxBitRate
            )
        }
        viewModelScope.launch {
            database.dao().updateServer(updated)
            _statusBanner.value = "Wi-Fi Streaming set to $format • ${if (maxBitRate == 0) "Lossless Bit-Perfect" else "${maxBitRate}kbps"}"
        }
    }

    fun createPlaylist(name: String, comment: String, selectedTrackIds: List<String>) {
        if (name.isBlank()) return
        val seedTrack = tracks.value.firstOrNull { it.id in selectedTrackIds } ?: _currentTrack.value
        viewModelScope.launch {
            repository.createPlaylist(
                serverId = activeServer().id,
                name = name,
                comment = comment,
                trackIds = selectedTrackIds.ifEmpty { tracks.value.take(4).map { it.id } },
                seedHex = seedTrack?.paletteSeedHex ?: "#FF6B5B",
                accentHex = seedTrack?.accentHex ?: "#70F5FF"
            )
            _statusBanner.value = "Created playlist \"$name\""
        }
    }

    // ---- Bulk track actions (swipe, long-press sheet, multi-select) ----------------------

    fun playTracksNext(list: List<SubsonicTrack>) {
        if (list.isEmpty()) return
        // Insert in reverse so the first selected track ends up playing first.
        list.asReversed().forEach { playTrackNext(it) }
        if (list.size > 1) _statusBanner.value = "Playing ${list.size} tracks next"
    }

    fun addTracksToQueue(list: List<SubsonicTrack>) {
        if (list.isEmpty()) return
        list.forEach { addTrackToQueue(it) }
        if (list.size > 1) _statusBanner.value = "Added ${list.size} tracks to the queue"
    }

    fun setTracksFavourite(list: List<SubsonicTrack>, favourite: Boolean) {
        list.filter { it.starred != favourite }.forEach { toggleStarTrack(it) }
        _statusBanner.value = when {
            list.size == 1 && favourite -> "Added \"${list.first().title}\" to favourites"
            list.size == 1 -> "Removed \"${list.first().title}\" from favourites"
            favourite -> "Added ${list.size} tracks to favourites"
            else -> "Removed ${list.size} tracks from favourites"
        }
    }

    fun downloadTracks(list: List<SubsonicTrack>) {
        if (list.isEmpty()) return
        if (list.size == 1) return downloadTrackForOffline(list.first())
        viewModelScope.launch {
            downloadManager.enqueueAlbumDownload(activeServer(), list, _customizationSettings.value.downloadSpeedLimit)
            _statusBanner.value = "Downloading ${list.size} tracks"
        }
    }

    fun addTracksToPlaylist(list: List<SubsonicTrack>, playlist: SubsonicPlaylist) {
        viewModelScope.launch {
            repository.addTracksToPlaylist(playlist, list.map { it.id })
            _statusBanner.value = if (list.size == 1) "Added \"${list.first().title}\" to ${playlist.name}" else "Added ${list.size} tracks to ${playlist.name}"
        }
    }

    /** Opens [albumId]'s page from anywhere (player, any list), staying on the current tab when it can show albums. */
    fun openAlbumAnywhere(albumId: String) {
        _isPlayerExpanded.value = false
        if (_currentDestination.value !in setOf(PrimaryDestination.HOME, PrimaryDestination.LIBRARY, PrimaryDestination.FAVOURITES, PrimaryDestination.ARTISTS)) {
            selectDestination(PrimaryDestination.LIBRARY)
        }
        openAlbumDetail(albumId)
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun testAndSaveServer(
        name: String,
        baseUrl: String,
        username: String,
        passwordOrToken: String,
        useTokenAuth: Boolean,
        transcodeFormat: String,
        maxBitRate: Int
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            val config = SubsonicServerConfig(
                name = name.trim().ifBlank { "OpenSubsonic Server" },
                baseUrl = baseUrl.trim(),
                username = username.trim(),
                passwordOrToken = passwordOrToken,
                useTokenAuth = useTokenAuth,
                transcodeFormat = transcodeFormat,
                maxBitRate = maxBitRate,
                isDemoServer = false,
                isActive = true
            )
            OndaluneMediaBridge.clearRemoteArtworkCache()
            val (saved, diag) = repository.saveAndActivateServer(config)
            _activeServerId.value = saved.id
            if (diag.success) {
                markFullSync(saved)
                refreshHomeShelves(saved)
            }
            _pingDiagnostic.value = diag
            _statusBanner.value = diag.statusMessage
            _isSyncing.value = false
        }
    }

    fun activateExistingServer(server: SubsonicServerConfig) {
        viewModelScope.launch {
            _isSyncing.value = true
            OndaluneMediaBridge.clearRemoteArtworkCache()
            _activeServerId.value = server.id
            val diag = repository.switchActiveServer(server)
            _pingDiagnostic.value = diag
            _statusBanner.value = "Active Server: ${server.name}"
            _isSyncing.value = false
        }
    }

    fun deleteServer(server: SubsonicServerConfig) {
        if (server.isDemoServer) return
        viewModelScope.launch {
            repository.deleteServer(server.id)
            val demo = servers.value.firstOrNull { it.isDemoServer }
            if (demo != null) {
                activateExistingServer(demo)
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun loadLyricsForTrack(track: SubsonicTrack) {
        viewModelScope.launch {
            val result = repository.fetchLyricsResult(activeServer(), track)
            _currentLyricsResult.value = result
            _currentLyrics.value = result.syncedLines
            OndaluneMediaBridge.updateLyrics(getApplication(), track.id, result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepTimerJob?.cancel()
        // Preserve background playback when MainActivity closes while audio is actively playing
        if (!audioEngine.isPlaying.value) {
            OndaluneMediaBridge.onCommand = null
            OndaluneMediaBridge.onSeekTo = null
        }
    }
}

private const val PREF_RECENT_TRACKS = "home_recent_track_ids"
private val LosslessSuffixes = setOf("FLAC", "ALAC", "WAV", "AIFF", "AIF", "APE", "WV", "DSF", "DFF")

data class LibraryFilters(
    val downloadedOnly: Boolean = false,
    val hideShortTracks: Boolean = false,
    val losslessOnly: Boolean = false
) {
    val active: Boolean get() = downloadedOnly || hideShortTracks || losslessOnly
}
private const val PREF_FULL_SYNC_AT = "full_library_sync_at_"
private const val FULL_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L

enum class HomeShelfKind(val title: String) {
    JUMP_BACK_IN("Jump back in"),
    RECENTLY_ADDED("Recently added"),
    FAVOURITE_TRACKS("Favourite tracks"),
    MOST_PLAYED("Most played"),
    RECENTLY_PLAYED("Recently played"),
    REDISCOVER("Rediscover")
}

data class HomeShelves(
    val jumpBackIn: List<SubsonicTrack> = emptyList(),
    val recentlyAdded: List<SubsonicAlbum> = emptyList(),
    val favouriteTracks: List<SubsonicTrack> = emptyList(),
    val mostPlayed: List<SubsonicTrack> = emptyList(),
    val recentlyPlayed: List<SubsonicAlbum> = emptyList(),
    val rediscover: List<SubsonicAlbum> = emptyList()
) {
    fun tracksFor(kind: HomeShelfKind): List<SubsonicTrack>? = when (kind) {
        HomeShelfKind.JUMP_BACK_IN -> jumpBackIn
        HomeShelfKind.FAVOURITE_TRACKS -> favouriteTracks
        HomeShelfKind.MOST_PLAYED -> mostPlayed
        else -> null
    }

    fun albumsFor(kind: HomeShelfKind): List<SubsonicAlbum> = when (kind) {
        HomeShelfKind.RECENTLY_ADDED -> recentlyAdded
        HomeShelfKind.RECENTLY_PLAYED -> recentlyPlayed
        HomeShelfKind.REDISCOVER -> rediscover
        else -> emptyList()
    }
}
