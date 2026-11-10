package io.github.roggzuay.ondalune.ui.viewmodel

import io.github.roggzuay.ondalune.data.model.overrideFor
import io.github.roggzuay.ondalune.data.model.withOverride
import io.github.roggzuay.ondalune.data.model.source
import io.github.roggzuay.ondalune.data.model.SourceType
import kotlinx.coroutines.withTimeoutOrNull
import android.app.Application
import android.os.SystemClock
import coil.imageLoader
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.roggzuay.ondalune.audio.OndaluneAudioEngine
import io.github.roggzuay.ondalune.data.local.AppUpdateInstaller
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.local.UpdateInstallState
import kotlinx.coroutines.CancellationException
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.local.OndaluneDownloadManager
import io.github.roggzuay.ondalune.data.model.AlbumListType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import io.github.roggzuay.ondalune.ui.theme.resolveDynamicSurfacePalette
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.data.model.CuratedColorPalette
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
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbProcessingMode
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.yourMix
import io.github.roggzuay.ondalune.playback.PlaybackCoordinator
import io.github.roggzuay.ondalune.data.local.AnalysisInput
import io.github.roggzuay.ondalune.data.local.analysisInputs
import io.github.roggzuay.ondalune.data.model.isLocalSource
import io.github.roggzuay.ondalune.data.model.isOnDevice
import io.github.roggzuay.ondalune.data.source.LocalMusicSource
import io.github.roggzuay.ondalune.data.source.MusicSources
import kotlinx.coroutines.flow.drop
import io.github.roggzuay.ondalune.playback.PlaybackEngine
import io.github.roggzuay.ondalune.playback.PlaybackHost
import io.github.roggzuay.ondalune.data.model.LibraryMix
import io.github.roggzuay.ondalune.data.model.localEpochDay
import io.github.roggzuay.ondalune.data.model.SongFeatures
import io.github.roggzuay.ondalune.data.local.SongAnalysis
import io.github.roggzuay.ondalune.data.model.libraryMixes
import kotlinx.coroutines.flow.first
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.StreamQuality
import io.github.roggzuay.ondalune.data.model.streamQuality
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.data.network.AppUpdate
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.network.UpdateCheckStatus
import io.github.roggzuay.ondalune.data.repository.OndaluneRepository
import io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager
import io.github.roggzuay.ondalune.ui.components.ReleaseNotes
import io.github.roggzuay.ondalune.ui.components.majorMinor
import io.github.roggzuay.ondalune.ui.components.whatsNewToShow
import io.github.roggzuay.ondalune.BuildConfig
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random
import io.github.roggzuay.ondalune.data.model.TempoInfo
import io.github.roggzuay.ondalune.data.model.tempoOf
import io.github.roggzuay.ondalune.data.local.AnalysisState
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.DocumentsContract
import io.github.roggzuay.ondalune.data.model.LocalFolder
import io.github.roggzuay.ondalune.data.source.FolderReader
import io.github.roggzuay.ondalune.data.source.FolderPermissions
import io.github.roggzuay.ondalune.data.source.folderLocation
import io.github.roggzuay.ondalune.data.model.OfflineSummary
import io.github.roggzuay.ondalune.data.model.offlineSummaryOf
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import io.github.roggzuay.ondalune.audio.AudioCapabilities
import io.github.roggzuay.ondalune.data.model.AudioQuality
import io.github.roggzuay.ondalune.data.model.OpenedStream
import io.github.roggzuay.ondalune.data.model.audioQualityOf
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import io.github.roggzuay.ondalune.data.update.UpdateStore
import io.github.roggzuay.ondalune.data.update.InstallSource
import io.github.roggzuay.ondalune.data.update.installSourceOf
import io.github.roggzuay.ondalune.data.update.UpdateCheckWorker
import io.github.roggzuay.ondalune.data.update.updateChecksBuiltIn

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

    private val prefs = application.getSharedPreferences("ondalune_prefs", android.content.Context.MODE_PRIVATE)
    /** Saved options; the plain ones are set on it directly (`viewModel.settings.setX`). */
    val settings = SettingsStore(prefs)
    private val database = OndaluneDatabase.getInstance(application)
    private val client = OpenSubsonicClient()
    private val repository = OndaluneRepository(
        database.dao(),
        client,
        sourceFor = { MusicSources.forServer(application, client, it) },
        folderPermissions = FolderPermissions(application),
        jellyfin = MusicSources.jellyfin(application)
    )
    val audioEngine: OndaluneAudioEngine = OndalunePlaybackRuntime.getOrCreateEngine(application, client)

    /** Notification, widget and headset controls while this screen is alive (see onCleared). */
    private val bridgeCommand: (MediaBridgeCommand) -> Unit = { action ->
        viewModelScope.launch {
            when (action) {
                MediaBridgeCommand.TOGGLE_PLAY_PAUSE -> playback.togglePlayPause()
                MediaBridgeCommand.PLAY -> if (!isPlaying.value) playback.togglePlayPause()
                MediaBridgeCommand.PAUSE -> if (isPlaying.value) playback.togglePlayPause()
                MediaBridgeCommand.SKIP_NEXT -> playback.skipNext()
                MediaBridgeCommand.SKIP_PREVIOUS -> playback.skipPrevious()
                MediaBridgeCommand.TOGGLE_STAR -> {
                    playback.currentTrack.value?.let { toggleStarTrack(it) }
                }
            }
        }
    }
    private val bridgeSeek: (Long) -> Unit = { posMs -> viewModelScope.launch { seekTo(posMs) } }
    val downloadManager = OndaluneDownloadManager(application, database.dao(), client, viewModelScope)
    private val songAnalysis = SongAnalysis(application)

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

    /** Every library row: the servers and, when it is on, this phone's music. */
    private val sources: StateFlow<List<SubsonicServerConfig>> = repository.serversFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The servers you can switch between (this phone's music joins whichever is active). */
    val servers: StateFlow<List<SubsonicServerConfig>> = sources
        .map { list -> list.filterNot { it.isLocalSource } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** This phone's music, when it is part of the library. */
    val phoneSource: StateFlow<SubsonicServerConfig?> = sources
        .map { list -> list.firstOrNull { it.isLocalSource } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)


    /** On mobile data (or any metered network, like a phone's hotspot), where streams use the mobile quality. */
    fun onMobileData(): Boolean =
        getApplication<Application>().getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

    /** [track]'s library row, streaming at the quality set for the network the phone is on now. */
    private fun streamServerFor(track: SubsonicTrack): SubsonicServerConfig {
        val quality = settings.customization.value.streamQuality(onMobileData())
        return serverFor(track).copy(transcodeFormat = quality.format, maxBitRate = quality.maxBitRate)
    }

    /** The library row [track] belongs to: its server, or this phone. */
    private fun serverFor(track: SubsonicTrack): SubsonicServerConfig =
        sources.value.firstOrNull { it.id == track.serverId } ?: activeServer()

    // No server until ensureSeeded() says which is active. Starting on 1 (the demo server) loaded
    // the demo library first, and the launch restore picked one of its songs.
    private val _activeServerId = MutableStateFlow(-1L)

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
    /** The synced songs as their source has them, before edits made in the app. */
    private val syncedTracks: StateFlow<List<SubsonicTrack>> = combine(
        _activeServerId,
        phoneSource.map { it?.id }.distinctUntilChanged()
    ) { active, phone -> active to phone }
        .flatMapLatest { (active, phone) ->
            val server = repository.observeTracksForServer(active)
            if (phone == null) server
            else combine(server, repository.observeTracksForServer(phone)) { remote, local -> remote + local }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allServerTracks: StateFlow<List<SubsonicTrack>> = combine(syncedTracks, repository.overridesFlow) { list, edits ->
        if (edits.isEmpty()) list else list.map { it.withOverride(edits[it.serverId to it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** [track] as its source has it, without edits made in the app. */
    fun originalOf(track: SubsonicTrack): SubsonicTrack =
        syncedTracks.value.firstOrNull { it.id == track.id && it.serverId == track.serverId } ?: track

    /** Saves edited details for [track]; details equal to the source's drop the edit. Never sent to the server. */
    fun saveTrackDetails(track: SubsonicTrack, title: String, artist: String, genre: String, year: Int) {
        val original = originalOf(track)
        viewModelScope.launch { repository.setOverride(original, overrideFor(original, title, artist, genre, year)) }
    }

    // Only completed ids matter for filtering, so progress ticks don't re-filter the library.
    private val downloadedIds = downloadsMap
        .map { map -> map.values.filter { it.isCompleted }.mapTo(HashSet()) { it.trackId } }
        .distinctUntilChanged()

    val tracks: StateFlow<List<SubsonicTrack>> = combine(allServerTracks, _libraryFilters, downloadedIds) { list, f, downloaded ->
        if (!f.active) list else list.filter { t ->
            (!f.downloadedOnly || t.id in downloaded || t.isOnDevice) &&
                (!f.hideShortTracks || t.durationSec >= 60) &&
                (!f.losslessOnly || t.suffix.uppercase() in LosslessSuffixes)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineStorageSummary: StateFlow<OfflineStorageSummary> = downloadManager.storageSummary

    /** What plays without a connection, for the Downloaded filter. */
    val offlineSummary: StateFlow<OfflineSummary> by lazy {
        combine(allServerTracks, downloadedIds, playlists) { all, ids, lists -> offlineSummaryOf(all, ids, lists) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineSummary())
    }

    private val _downloadsHintSeen = MutableStateFlow(prefs.getBoolean(PREF_DOWNLOADS_HINT_SEEN, false))

    /**
     * Once there is downloaded music, a one-time note says where to find it: after the first
     * download, or on Home for someone who downloaded before the note existed.
     */
    val showDownloadsHint: StateFlow<Boolean> = combine(_downloadsHintSeen, downloadedIds) { seen, ids -> !seen && ids.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun dismissDownloadsHint() {
        _downloadsHintSeen.value = true
        prefs.edit().putBoolean(PREF_DOWNLOADS_HINT_SEEN, true).apply()
    }

    private val _currentDestination = MutableStateFlow(PrimaryDestination.HOME)
    val currentDestination: StateFlow<PrimaryDestination> = _currentDestination.asStateFlow()

    private val _selectedAlbumListType = MutableStateFlow(AlbumListType.NEWEST)
    val selectedAlbumListType: StateFlow<AlbumListType> = _selectedAlbumListType.asStateFlow()

    private val _libraryFilterTab = MutableStateFlow(LibraryFilterTab.ALBUMS)
    val libraryFilterTab: StateFlow<LibraryFilterTab> = _libraryFilterTab.asStateFlow()

    val tabDisplayPreferences: StateFlow<Map<String, TabDisplayPreference>> = settings.tabPreferences

    private val _libraryViewMode = MutableStateFlow(
        settings.tabPreferences.value[LibraryFilterTab.ALBUMS.name]?.viewMode ?: LibraryViewMode.GRID
    )
    val libraryViewMode: StateFlow<LibraryViewMode> = _libraryViewMode.asStateFlow()

    private val _librarySortOption = MutableStateFlow(
        settings.tabPreferences.value[LibraryFilterTab.ALBUMS.name]?.sortOption ?: LibrarySortOption.NAME_ASC
    )
    val librarySortOption: StateFlow<LibrarySortOption> = _librarySortOption.asStateFlow()

    private val _favouritesFilterTab = MutableStateFlow(FavouritesFilterTab.ALL)
    val favouritesFilterTab: StateFlow<FavouritesFilterTab> = _favouritesFilterTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    // Shown once per install, new or upgraded: the player's speed button resets on a long press.
    private val _showSpeedResetHint = MutableStateFlow(!prefs.getBoolean("hint_speed_hold_reset", false))
    val showSpeedResetHint: StateFlow<Boolean> = _showSpeedResetHint.asStateFlow()

    fun markSpeedResetHintShown() {
        _showSpeedResetHint.value = false
        prefs.edit().putBoolean("hint_speed_hold_reset", true).apply()
    }

    private val _whatsNew = MutableStateFlow(
        whatsNewToShow(BuildConfig.VERSION_NAME, prefs.getString("whats_new_seen", null), isAppUpdate(application))
            // Nothing to say on this launch: remember the version, so a later patch update of a
            // fresh install doesn't count as news.
            .also { if (it == null) markWhatsNewSeen() }
    )
    val whatsNew: StateFlow<ReleaseNotes?> = _whatsNew.asStateFlow()

    fun dismissWhatsNew() {
        _whatsNew.value = null
        markWhatsNewSeen()
    }

    private fun markWhatsNewSeen() {
        prefs.edit().putString("whats_new_seen", majorMinor(BuildConfig.VERSION_NAME)).apply()
    }

    private val updateStore = UpdateStore(application)

    /** How this copy was installed, which decides how it is updated (never by the wrong route). */
    val installSource: InstallSource by lazy { installSourceOf(getApplication()) }

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)

    /**
     * A newer release than this one, while there is one. It stays (as a small indicator on Home)
     * after its notification is dismissed or its notes are read, and goes once the app is updated.
     */
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    private val _updateBannerFolded = MutableStateFlow(false)

    /** "Later" folds the Home banner into a small indicator; it never hides the update. */
    val updateBannerFolded: StateFlow<Boolean> = _updateBannerFolded.asStateFlow()

    private val _updateDetailsOpen = MutableStateFlow(false)
    val updateDetailsOpen: StateFlow<Boolean> = _updateDetailsOpen.asStateFlow()

    fun showUpdateDetails() {
        if (_availableUpdate.value != null) _updateDetailsOpen.value = true
    }

    fun closeUpdateDetails() {
        _updateDetailsOpen.value = false
    }

    /** Shows the cached release if it is newer (no network), then checks GitHub if the cache is old. */
    private fun checkForUpdate() {
        val prefs = settings.customization.value
        if (!updateChecksBuiltIn || !prefs.checkForUpdates) return
        publishAvailableUpdate()
        viewModelScope.launch {
            updateStore.refresh(force = false, includePrereleases = prefs.updatePrereleases)
            publishAvailableUpdate()
        }
    }

    private fun publishAvailableUpdate() {
        val update = updateStore.availableFor(BuildConfig.VERSION_NAME)?.takeIf { it.prerelease.not() || settings.customization.value.updatePrereleases }
        _availableUpdate.value = update
        _updateBannerFolded.value = update != null && prefs.getString(PREF_UPDATE_FOLDED, null) == update.version
        if (update == null) {
            // Up to date, maybe through an in-app update: its downloaded APK can go.
            viewModelScope.launch(Dispatchers.IO) { AppUpdateInstaller.clearDownloads(getApplication()) }
        }
    }

    private val _updateCheckStatus = MutableStateFlow(UpdateCheckStatus.IDLE)
    val updateCheckStatus: StateFlow<UpdateCheckStatus> = _updateCheckStatus.asStateFlow()

    /** Settings → Updates → Check now: asks GitHub right away, even with daily checks off. */
    fun checkForUpdatesNow() {
        if (!updateChecksBuiltIn || _updateCheckStatus.value == UpdateCheckStatus.CHECKING) return
        viewModelScope.launch {
            _updateCheckStatus.value = UpdateCheckStatus.CHECKING
            if (updateStore.refresh(force = true, includePrereleases = settings.customization.value.updatePrereleases) == UpdateStore.Result.FAILED &&
                updateStore.cached() == null
            ) {
                _updateCheckStatus.value = UpdateCheckStatus.FAILED
                return@launch
            }
            publishAvailableUpdate()
            if (_availableUpdate.value != null) {
                _updateBannerFolded.value = false
                _updateCheckStatus.value = UpdateCheckStatus.IDLE
                _updateDetailsOpen.value = true
            } else {
                _updateCheckStatus.value = UpdateCheckStatus.UP_TO_DATE
            }
        }
    }

    /** "Later": the banner folds into the small indicator for this version. */
    fun dismissUpdate() {
        _availableUpdate.value?.let { prefs.edit().putString(PREF_UPDATE_FOLDED, it.version).apply() }
        _updateBannerFolded.value = true
    }

    fun setUpdateNotifications(enabled: Boolean) = settings.update { it.copy(updateNotifications = enabled) }

    fun setUpdatePrereleases(enabled: Boolean) {
        settings.update { it.copy(updatePrereleases = enabled) }
        checkForUpdatesNow()
    }

    private val _updateInstall = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    val updateInstall: StateFlow<UpdateInstallState> = _updateInstall.asStateFlow()
    private var updateJob: Job? = null

    /** Downloads the update shown in the banner, then hands it to Android's installer. */
    fun startUpdate() {
        // Only a copy signed like the release APK can install it; anything else gets instructions.
        if (!updateChecksBuiltIn || installSource != InstallSource.GITHUB) return
        val version = _availableUpdate.value?.version ?: return
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            val app = getApplication<Application>()
            _updateInstall.value = UpdateInstallState.Downloading(0f)
            val apk = try {
                AppUpdateInstaller.download(app, version) { _updateInstall.value = UpdateInstallState.Downloading(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _updateInstall.value = UpdateInstallState.Failed("Couldn't download the update. Check your connection and try again.")
                return@launch
            }
            if (!withContext(Dispatchers.IO) { AppUpdateInstaller.isTrustedUpdate(app, apk) }) {
                AppUpdateInstaller.clearDownloads(app)
                _updateInstall.value = UpdateInstallState.Failed("This download isn't signed by Ondalune, so it wasn't installed.")
                return@launch
            }
            _updateInstall.value = if (AppUpdateInstaller.canInstall(app)) {
                UpdateInstallState.Ready(apk)
            } else {
                UpdateInstallState.NeedsPermission(apk)
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        _updateInstall.value = UpdateInstallState.Idle
    }

    /** Back from Android's "Install unknown apps" screen: carry on if it was allowed. */
    fun resumeUpdateInstall() {
        val state = _updateInstall.value as? UpdateInstallState.NeedsPermission ?: return
        if (AppUpdateInstaller.canInstall(getApplication())) _updateInstall.value = UpdateInstallState.Ready(state.apk)
    }

    private fun isAppUpdate(app: Application): Boolean = runCatching {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).let { it.lastUpdateTime > it.firstInstallTime }
    }.getOrDefault(false)

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

    private val _equalizerPreset = MutableStateFlow(
        runCatching {
            EqualizerPreset.valueOf(prefs.getString("eq_preset", EqualizerPreset.FLAT_STUDIO.name)!!)
        }.getOrDefault(EqualizerPreset.FLAT_STUDIO)
    )
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _customEqBands = MutableStateFlow(_equalizerPreset.value.bandsDb)
    val customEqBands: StateFlow<List<Float>> = _customEqBands.asStateFlow()

    val customizationSettings: StateFlow<PlayerCustomizationSettings> = settings.customization

    private val _selectedAlbumDetailId = MutableStateFlow<String?>(null)
    val selectedAlbumDetailId: StateFlow<String?> = _selectedAlbumDetailId.asStateFlow()

    private val _albumDetailOriginDestination = MutableStateFlow<PrimaryDestination?>(null)

    private val _selectedArtistDetailId = MutableStateFlow<String?>(null)
    val selectedArtistDetailId: StateFlow<String?> = _selectedArtistDetailId.asStateFlow()

    private val _selectedGenreDetail = MutableStateFlow<String?>(null)
    val selectedGenreDetail: StateFlow<String?> = _selectedGenreDetail.asStateFlow()

    private val _selectedFolderDetail = MutableStateFlow<String?>(null)
    val selectedFolderDetail: StateFlow<String?> = _selectedFolderDetail.asStateFlow()

    private val _selectedPlaylistDetailId = MutableStateFlow<String?>(null)
    val selectedPlaylistDetailId: StateFlow<String?> = _selectedPlaylistDetailId.asStateFlow()

    /**
     * A page opened by a jump (the player's names, "Go to artist", the Top artists row), which can
     * switch tabs, with the view it left. Back from that page restores the view instead of landing
     * on the page's own tab.
     */
    private data class Jump(
        val page: String,
        val destination: PrimaryDestination,
        val shelf: HomeShelfKind?,
        val albumId: String?,
        val artistId: String?,
        val genre: String?,
        val folder: String?,
        val playlistId: String?
    )
    private val jumps = ArrayDeque<Jump>()

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
        settings.customization
    ) { trackList, settings ->
        repository.deriveArtists(trackList, settings.fallbackArtistToAlbumArt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var songAnalysisJob: Job? = null

    /** Analysed features of the active server's songs, by track id. */
    val songFeatures: StateFlow<Map<String, SongFeatures>> = combine(songAnalysis.features, _activeServerId, phoneSource) { all, server, phone ->
        val prefixes = listOfNotNull("${server}_", phone?.let { "${it.id}_" })
        all.entries.mapNotNull { (key, value) ->
            prefixes.firstOrNull { key.startsWith(it) }?.let { key.removePrefix(it) to value }
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * The playing song's tempo: its tag, or a confident analysis; while analysis is on and the song
     * is stored on the phone, whether it is waiting or being analysed. Never a made-up number.
     */
    val currentTempo: StateFlow<TempoInfo> by lazy {
        // Lazy: [playback] is set up further down.
        combine(playback.currentTrack, songFeatures, songAnalysis.states) { track, features, states ->
            val state = track?.let { states[it.analysisKey] }
            tempoOf(track, track?.let { features[it.id] }, state == AnalysisState.ANALYSING, state == AnalysisState.QUEUED)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempoInfo.Unknown)
    }

    // ---- Audio quality ------------------------------------------------------------------

    private val audioCapabilities by lazy { AudioCapabilities(getApplication()) }
    private val _outputDevice = MutableStateFlow<String?>(null)
    private val outputDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshOutputDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshOutputDevice()
    }

    private fun refreshOutputDevice() {
        _outputDevice.value = audioCapabilities.deviceName(audioEngine.routedOutput())
    }

    /** What changes the sound right now, in words; empty when nothing does. */
    private val activeProcessing: Flow<List<String>> by lazy {
        combine(speedPitchState, reverbState, equalizerPreset, settings.customization) { sp, reverb, eq, prefs ->
            buildList {
                if (sp.speed != 1f) add("Speed ${"%.2f".format(sp.speed)}× (${sp.pitchMode.label.substringBefore(" (")})")
                if (sp.pitchMode == PitchCorrectionMode.INDEPENDENT_SEMITONES && sp.independentPitchRatio != 1f) {
                    add("Pitch ×${"%.2f".format(sp.independentPitchRatio)}")
                }
                if (reverb.enabled && reverb.preset != ReverbPreset.OFF) add("Reverb: ${reverb.preset.label}")
                if (eq != EqualizerPreset.FLAT_STUDIO) add("EQ: ${eq.label}")
                if (prefs.replayGainMode != ReplayGainMode.OFF) add("Volume levelling: ${prefs.replayGainMode.label.substringBefore(" (")}")
            }
        }
    }

    /**
     * The quality line under the cover and its details, from what is actually playing: the song's
     * own details, what the engine opened, any conversion by the server, the processing that is
     * on, and the output device.
     */
    val audioQuality: StateFlow<AudioQuality?> by lazy {
        combine(playback.currentTrack, audioEngine.playingStream, activeProcessing, _outputDevice, isPlayingOfflineFile) { track, playing, processing, device, offline ->
            track ?: return@combine null
            val server = streamServerFor(track)
            val convertedBy = server.takeUnless { offline || track.isOnDevice || it.isDemoServer || it.transcodeFormat == "RAW" }
                ?.let { s -> listOfNotNull(s.transcodeFormat, s.maxBitRate.takeIf { it > 0 }?.let { "up to $it kbps" }).joinToString(", ") }
            val opened = playing?.let { p ->
                OpenedStream(
                    mime = p.decoded?.mime.orEmpty(),
                    sampleRate = p.decoded?.sampleRate ?: 0,
                    channels = p.decoded?.channels ?: 0,
                    bitrate = p.decoded?.bitrate ?: 0,
                    bitsPerSample = p.decoded?.bitsPerSample ?: 0,
                    ownPipeline = p.ownPipeline,
                    outputSampleRate = p.outputSampleRate
                )
            }
            audioQualityOf(track, opened, convertedBy, processing, device)
        }.onEach { refreshOutputDevice() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    /** The Dolby formats this phone decodes (none is used by Ondalune's stereo engine). */
    val dolbyDecoders: List<String> get() = audioCapabilities.dolbyDecoders

    /** Whether Android's spatial audio applies to Ondalune's output now; null where there is none. */
    fun spatialAudioActive(): Boolean? = audioCapabilities.spatialAudioActive()

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
        _recentTrackIds,
        songFeatures
    ) { trackList, order, recentIds, features ->
        val albumsByYear = repository.deriveAlbums(trackList, AlbumListType.NEWEST)
        val albumsById = albumsByYear.associateBy { it.id }
        val tracksById = trackList.associateBy { it.id }
        fun serverOrdered(type: String) = order[type].orEmpty().mapNotNull { albumsById[it] }
        val day = localEpochDay()
        // Full lists: Home shows the first few of each, the shelf's "More" page shows all of it.
        val tracksByAlbum = trackList.groupBy { it.albumId }
        HomeShelves(
            jumpBackIn = recentIds.take(40).mapNotNull { tracksById[it] },
            recentlyAdded = serverOrdered("newest").ifEmpty { albumsByYear },
            favouriteTracks = trackList.filter { it.starred }.sortedByDescending { it.playCount },
            mostPlayed = trackList.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
                .ifEmpty { serverOrdered("frequent").flatMap { tracksByAlbum[it.id].orEmpty() } }
                .take(200),
            recentlyPlayed = serverOrdered("recent"),
            rediscover = albumsByYear.shuffled(kotlin.random.Random(day)).take(60),
            mixes = libraryMixes(trackList, recentIds, order["newest"].orEmpty(), seed = day, features = features)
        )
    }.flowOn(Dispatchers.Default) // whole-library work; keep it off the main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeShelves())

    private val _homeIntroSeen = MutableStateFlow(prefs.getBoolean(PREF_HOME_INTRO_SEEN, false))
    /** Whether "A Home that learns" has been shown (once, when the library first has mixes). */
    val homeIntroSeen: StateFlow<Boolean> = _homeIntroSeen.asStateFlow()

    fun dismissHomeIntro() {
        _homeIntroSeen.value = true
        prefs.edit().putBoolean(PREF_HOME_INTRO_SEEN, true).apply()
    }

    /** Plays a Home mix from its first song. */
    fun playMix(mix: LibraryMix) {
        if (mix.tracks.isEmpty()) return
        prefetchCovers(mix.tracks.take(2))
        playback.playInOrder(mix.tracks)
        _statusBanner.value = "${mix.title}: ${mix.tracks.size} songs"
    }

    private val _scrollToTopSignal = MutableStateFlow(0)
    /** Bumped when the current tab is tapped again at its root: lists scroll back to the top. */
    val scrollToTopSignal: StateFlow<Int> = _scrollToTopSignal.asStateFlow()

    /** Tapping the tab you're on: first back out of any open page, then scroll to the top. */
    fun reselectDestination() {
        val detailOpen = _openHomeShelf.value != null || _selectedAlbumDetailId.value != null ||
            _selectedArtistDetailId.value != null || _selectedGenreDetail.value != null || _selectedFolderDetail.value != null ||
            _selectedPlaylistDetailId.value != null
        if (detailOpen) {
            jumps.clear()
            _openHomeShelf.value = null
            _selectedAlbumDetailId.value = null
            _albumDetailOriginDestination.value = null
            _selectedArtistDetailId.value = null
            _selectedGenreDetail.value = null
            _selectedFolderDetail.value = null
            _selectedPlaylistDetailId.value = null
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
        if (server.isDemoServer || server.source != SourceType.SUBSONIC) return
        viewModelScope.launch {
            val fetched = listOf("newest", "recent", "frequent").associateWith { type ->
                repository.client.fetchAlbumListIds(server, type, size = 500)
            }
            _serverAlbumOrder.value = fetched.filterValues { it.isNotEmpty() }
        }
    }

    private fun recordPlayed(trackId: String) {
        // Kept longer than "Jump back in" shows, so shuffles can move recent songs back.
        val updated = (listOf(trackId) + _recentTrackIds.value.filterNot { it == trackId }).take(200)
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
        LibraryQueries.searchTracks(trackList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAlbums: StateFlow<List<SubsonicAlbum>> = combine(
        albums,
        _searchQuery
    ) { albumList, query ->
        LibraryQueries.searchAlbums(albumList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredArtists: StateFlow<List<SubsonicArtist>> = combine(
        artists,
        _searchQuery
    ) { artistList, query ->
        LibraryQueries.searchArtists(artistList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredPlaylists: StateFlow<List<SubsonicPlaylist>> = combine(
        playlists,
        _searchQuery
    ) { playlistList, query ->
        LibraryQueries.searchPlaylists(playlistList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGenres: StateFlow<List<SubsonicGenre>> = combine(
        genres,
        _searchQuery
    ) { genreList, query ->
        LibraryQueries.searchGenres(genreList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredFolders: StateFlow<List<SubsonicFolder>> = combine(
        folders,
        _searchQuery
    ) { folderList, query ->
        LibraryQueries.searchFolders(folderList, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The queue and what plays next. Declared after the library flows it reads. */
    val playback = PlaybackCoordinator(
        engine = object : PlaybackEngine {
            override val isPlaying get() = audioEngine.isPlaying
            override val positionMs get() = audioEngine.positionMs
            override fun play(track: SubsonicTrack, server: SubsonicServerConfig, eq: EqualizerPreset) =
                audioEngine.playTrack(track, server, eq)
            override fun togglePlayPause() = audioEngine.togglePlayPause()
            override fun pause() = audioEngine.pausePlayback()
            override fun seekTo(positionMs: Long) = audioEngine.seekTo(positionMs)
            override fun setOutputVolume(volume: Float) = audioEngine.setOutputVolume(volume)
            override fun expectNextTrack(trackId: String?) = audioEngine.expectNextTrack(trackId)
        },
        host = object : PlaybackHost {
            override val library get() = tracks.value
            override val allTracks get() = allServerTracks.value
            override val settings get() = this@OndaluneViewModel.settings.customization.value
            override val equalizer get() = _equalizerPreset.value
            override val recentTrackIds get() = _recentTrackIds.value
            override val songFeatures get() = this@OndaluneViewModel.songFeatures.value
            override fun serverFor(track: SubsonicTrack) = streamServerFor(track)
            override fun onTrackStarted(track: SubsonicTrack, server: SubsonicServerConfig) {
                loadLyricsForTrack(track)
                if (settings.autoScrobble) {
                    viewModelScope.launch { repository.recordPlay(server, track) }
                }
            }
            override fun showBanner(message: String) { _statusBanner.value = message }
            override fun saveLastPlayed(trackId: String) = prefs.edit().putString("last_played_track_id", trackId).apply()
            override fun saveQueue(trackIds: List<String>) = prefs.edit().putString("saved_queue_ids", trackIds.joinToString(",")).apply()
        },
        scope = viewModelScope,
        initialShuffleMode = runCatching { ShuffleMode.valueOf(prefs.getString("shuffle_mode", ShuffleMode.OFF.name)!!) }
            .getOrDefault(ShuffleMode.OFF),
        initialRepeatOne = prefs.getBoolean("repeat_one_enabled", false)
    )

    val playingFromPlaylist: StateFlow<SubsonicPlaylist?> = combine(playback.queuePlaylistId, playlists) { id, all ->
        all.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        if (settings.customization.value.songAnalysis) startSongAnalysis()
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
            combine(playback.currentTrack, isPlaying) { track, playing -> if (playing) track?.id else null }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { recordPlayed(it) }
        }
        audioEngine.pauseOnHeadphoneDisconnect = settings.customization.value.pauseOnHeadphoneDisconnect
        audioEngine.setSpeedAndPitch(_speedPitchState.value.speed, _speedPitchState.value.effectivePitch)
        audioEngine.setReverbState(_reverbState.value)

        audioEngine.onTrackEnded = { playback.onTrackEnded() }
        // Gapless and crossfade: the engine lines up whatever plays next and moves on by itself.
        audioEngine.nextTrackProvider = { playback.nextForEngine() }
        audioEngine.onTrackAdvanced = { track -> playback.onEngineAdvanced(track) }
        viewModelScope.launch {
            settings.customization.collect { s ->
                audioEngine.crossfadeMs = s.crossfadeSeconds * 1000
                audioEngine.gaplessEnabled = s.gaplessPlayback
                audioEngine.replayGainMode = s.replayGainMode
            }
        }
        OndaluneMediaBridge.onCommand = bridgeCommand
        OndaluneMediaBridge.onSeekTo = bridgeSeek
        checkForUpdate()
        UpdateCheckWorker.schedule(application, settings.customization.value.checkForUpdates)
        viewModelScope.launch {
            val activeServer = repository.ensureSeeded()
            _activeServerId.value = activeServer.id
            downloadManager.refreshStorageSummary(activeServer.id)
            resumeInterruptedDownloads(activeServer)
            _pingDiagnostic.value = repository.pingAndInspect(activeServer)
            val lastFull = prefs.getLong(PREF_FULL_SYNC_AT + activeServer.id, 0L)
            if (shouldSyncOnLaunch(lastFull, activeServer.isDemoServer)) {
                triggerQuickSync(activeServer)
            }
        }
        // The quality details name the output device; follow headphones and speakers coming and going.
        getApplication<Application>().getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(outputDeviceCallback, null)
        viewModelScope.launch {
            // 1.5.0 added every audio file on the phone; now only picked folders count.
            if (repository.removeWholePhoneScanSongs() > 0) {
                _statusBanner.value = "Music on this phone now comes from folders you pick: add them in Settings → Music sources"
            }
            // Songs added, changed or removed while the app was closed.
            if (settings.customization.value.rescanFoldersOnLaunch) scanFolders(repository.localFolders())
        }
        viewModelScope.launch {
            tracks.collect { list ->
                // Only bring back the song you last played. With nothing to resume, nothing is
                // current and the mini player stays hidden until you play something.
                val savedTrackId = prefs.getString("last_played_track_id", null)
                    ?.takeIf { settings.customization.value.resumePlaybackOnLaunch }
                playback.onLibraryChanged(list, savedTrackId)?.let { loadLyricsForTrack(it) }
            }
        }
        viewModelScope.launch {
            combine(
                playback.currentTrack,
                isPlaying,
                settings.customization,
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
        jumps.clear()
        showDestination(destination)
    }

    private fun showDestination(destination: PrimaryDestination) {
        _currentDestination.value = destination
        _openHomeShelf.value = null
        _selectedAlbumDetailId.value = null
        _albumDetailOriginDestination.value = null
        _selectedArtistDetailId.value = null
        _selectedGenreDetail.value = null
        _selectedFolderDetail.value = null
        _selectedPlaylistDetailId.value = null
    }

    /** Collapses the player and runs [open], remembering the current view for Back from [page]. */
    private fun jumpTo(page: String, open: () -> Unit) {
        val from = Jump(
            page, _currentDestination.value, _openHomeShelf.value, _selectedAlbumDetailId.value,
            _selectedArtistDetailId.value, _selectedGenreDetail.value, _selectedFolderDetail.value,
            _selectedPlaylistDetailId.value
        )
        _isPlayerExpanded.value = false
        open()
        jumps.addLast(from)
    }

    /** Back from [page]: if a jump opened it, restores the view the jump left and returns true. */
    private fun returnFromJump(page: String): Boolean {
        if (jumps.lastOrNull()?.page != page) return false
        val from = jumps.removeLast()
        _currentDestination.value = from.destination
        _openHomeShelf.value = from.shelf
        _selectedAlbumDetailId.value = from.albumId
        _albumDetailOriginDestination.value = null
        _selectedArtistDetailId.value = from.artistId
        _selectedGenreDetail.value = from.genre
        _selectedFolderDetail.value = from.folder
        _selectedPlaylistDetailId.value = from.playlistId
        return true
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
            // The server answers in pages; the pacer turns them into a steady climb.
            val found = java.util.concurrent.atomic.AtomicInteger(0) // written from the network thread
            val pacer = SyncCountPacer(SystemClock.uptimeMillis())
            var shown = 0
            _statusBanner.value = "Syncing library…"
            val counter = launch {
                while (true) {
                    val next = pacer.next(found.get(), SystemClock.uptimeMillis())
                    if (next != shown) {
                        shown = next
                        _statusBanner.value = "Syncing library… $shown songs"
                    }
                    delay(32L)
                }
            }
            val syncedCount = repository.syncServerCatalog(server) { n -> found.set(n) }
            found.set(syncedCount)
            pacer.finish(syncedCount, SystemClock.uptimeMillis())
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
        jumps.clear()
        _selectedAlbumDetailId.value = null
        _albumDetailOriginDestination.value = null
        _selectedArtistDetailId.value = null
        _selectedGenreDetail.value = null
        _selectedFolderDetail.value = null
    }

    fun getTabPreference(categoryKey: String): TabDisplayPreference {
        val key = categoryKey.uppercase()
        return settings.tabPreferences.value[key] ?: TabDisplayPreference.defaultForCategory(key)
    }

    fun setCategoryViewMode(categoryKey: String, mode: LibraryViewMode) {
        val key = categoryKey.uppercase()
        val current = getTabPreference(key)
        val updated = current.copy(viewMode = mode)
        settings.putTab(key, updated)
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
            LibrarySortOption.TRACK_COUNT,
            LibrarySortOption.ALBUM_COUNT,
            LibrarySortOption.DATE_MODIFIED,
            LibrarySortOption.RECENTLY_FAVOURITED -> false
            LibrarySortOption.NAME_ASC,
            LibrarySortOption.ARTIST,
            LibrarySortOption.ALBUM,
            LibrarySortOption.TRACK_NUMBER -> true
            else -> current.ascending
        }
        val updated = current.copy(sortOption = option, ascending = nextAsc)
        settings.putTab(key, updated)
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
        settings.putTab(key, updated)
    }

    fun setCategoryGridColumns(categoryKey: String, columns: Int) {
        val key = categoryKey.uppercase()
        val clamped = columns.coerceIn(2, 4)
        val current = getTabPreference(key)
        val updated = current.copy(gridColumns = clamped)
        settings.putTab(key, updated)
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
        if (!server.isDemoServer && server.source == SourceType.SUBSONIC && trimmed.length >= 2) {
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
            if (returnFromJump("album:${_selectedAlbumDetailId.value}")) return
            _selectedAlbumDetailId.value = null
            val origin = _albumDetailOriginDestination.value
            if (origin != null) {
                _currentDestination.value = origin
                _albumDetailOriginDestination.value = null
            }
        }
    }

    /** Similar artists the server named for each opened artist (ids, keyed by the opened id). */
    private val _similarArtistIds = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val similarArtistIds: StateFlow<Map<String, List<String>>> = _similarArtistIds.asStateFlow()

    private val _favouriteArtistIds = MutableStateFlow(prefs.getStringSet(PREF_FAVOURITE_ARTISTS, emptySet()).orEmpty().toSet())
    /** Artists starred from their page; kept here and starred on the server too. */
    val favouriteArtistIds: StateFlow<Set<String>> = _favouriteArtistIds.asStateFlow()

    fun toggleFavouriteArtist(artist: SubsonicArtist) {
        val favourite = artist.id !in _favouriteArtistIds.value
        val updated = if (favourite) _favouriteArtistIds.value + artist.id else _favouriteArtistIds.value - artist.id
        _favouriteArtistIds.value = updated
        prefs.edit().putStringSet(PREF_FAVOURITE_ARTISTS, updated).apply()
        val server = activeServer()
        if (server.isDemoServer) return
        val named = tracks.value.filter { it.artist.equals(artist.name, ignoreCase = true) }
        if (named.isNotEmpty() && named.all { it.isOnDevice }) return // only on this phone: the heart stays in the app
        val rawId = named.firstOrNull { !it.isOnDevice }?.artistId?.ifBlank { null } ?: artist.id
        viewModelScope.launch { repository.setArtistFavourite(server, rawId, favourite) }
    }

    fun openArtistDetail(artistId: String?) {
        if (artistId == null && returnFromJump("artist:${_selectedArtistDetailId.value}")) return
        _selectedArtistDetailId.value = artistId
        if (artistId != null) {
            val matchedArtist = artists.value.find { it.id == artistId }
            val artistTracks = tracks.value.filter {
                it.artistId == artistId || it.artist.equals(matchedArtist?.name.orEmpty(), ignoreCase = true)
            }
            // An artist on both a server and this phone is looked up on the server.
            val sample = artistTracks.firstOrNull { !it.isOnDevice } ?: artistTracks.firstOrNull()
            val server = sample?.let(::serverFor) ?: activeServer()
            if (!server.isDemoServer) {
                val rawArtistId = sample?.artistId?.ifBlank { artistId } ?: artistId
                viewModelScope.launch {
                    val similar = repository.enrichArtistMetadata(
                        server = server,
                        artistId = rawArtistId,
                        artistName = matchedArtist?.name.orEmpty()
                    )
                    if (similar.isNotEmpty()) _similarArtistIds.value += (artistId to similar)
                }
            }
        }
    }

    /** From the player: collapse it and land on the playing track's artist page in Artists. */
    fun openArtistFromTrack(track: SubsonicTrack) = openArtistByName(track.artistId, track.artist)

    /** Opens the artist's page on the Artists tab; Back returns to where you were. */
    fun openArtistByName(artistId: String, name: String) {
        val artist = artists.value.find { artistId.isNotBlank() && it.id == artistId }
            ?: artists.value.find { it.name.equals(name, ignoreCase = true) }
            ?: return
        jumpTo("artist:${artist.id}") {
            showDestination(PrimaryDestination.ARTISTS)
            openArtistDetail(artist.id)
        }
    }

    fun openPlaylistDetail(playlistId: String?) {
        if (playlistId == null && returnFromJump("playlist:${_selectedPlaylistDetailId.value}")) return
        _selectedPlaylistDetailId.value = playlistId
    }

    /** Opens the playlist's page on the Playlists tab; Back returns to where you were. */
    fun openPlaylistAnywhere(playlistId: String) = jumpTo("playlist:$playlistId") {
        showDestination(PrimaryDestination.PLAYLISTS)
        _selectedPlaylistDetailId.value = playlistId
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
        useTokenAuth: Boolean,
        sourceType: SourceType = SourceType.SUBSONIC
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
                isActive = true,
                sourceType = sourceType.name
            )
            OndaluneMediaBridge.clearRemoteArtworkCache()
            // Only Subsonic servers say up front how many songs there are.
            val total = async { if (sourceType == SourceType.SUBSONIC) repository.client.fetchLibrarySongCount(config) else null }
            // Pages arrive in bursts; the pacer turns them into a steady climb.
            val found = java.util.concurrent.atomic.AtomicInteger(0)
            val pacer = SyncCountPacer(SystemClock.uptimeMillis())
            var shown = 0
            val counter = launch {
                while (true) {
                    val target = found.get()
                    shown = pacer.next(target, SystemClock.uptimeMillis())
                    if (target > 0) {
                        _onboardingSync.value = OnboardingSync(shown, total.takeIf { it.isCompleted }?.getCompleted(), OnboardingSync.Phase.SYNCING)
                    }
                    delay(32L)
                }
            }
            val (saved, diag) = repository.saveAndActivateServer(config) { n -> found.set(n) }
            if (saved.id != 0L) _activeServerId.value = saved.id
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
            pacer.finish(synced, SystemClock.uptimeMillis())
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
    // Settings & Customization Updaters (Persisted + Live)
    // =========================================================================

    fun setAppIconVariant(variant: AppIconVariant) {
        settings.update { it.copy(appIconVariant = variant) }
        AppIconLauncherManager.applyLauncherIconVariant(getApplication(), variant)
        _statusBanner.value = "App Icon updated to ${variant.label}"
    }

    fun setThemeMode(mode: AppThemeMode) {
        settings.update { it.copy(themeMode = mode) }
        followThemeIcon()
    }

    fun setUnifiedMaterialYouColor(enabled: Boolean) {
        settings.update {
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
        settings.update {
            it.copy(
                curatedPalette = palette,
                useMaterialYouDynamicColor = false,
                unifiedMaterialYouColor = false
            )
        }
        followThemeIcon()
    }

    fun setInterfaceStyle(style: InterfaceStyle) {
        settings.update { it.copy(interfaceStyle = style) }
        followThemeIcon()
    }

    fun setIconFollowsTheme(enabled: Boolean) {
        settings.update { it.copy(iconFollowsTheme = enabled) }
        followThemeIcon()
    }

    /**
     * When the icon follows the theme, switches the launcher icon to the variant nearest the
     * theme's primary colour: Daylight for light mode, Graphite for grey palettes, otherwise the
     * closest hue.
     */
    private fun followThemeIcon() {
        val s = settings.customization.value
        if (!s.iconFollowsTheme) return
        val palette = resolveDynamicSurfacePalette(
            getApplication(), s.curatedPalette.primarySeedHex, s.curatedPalette.accentSeedHex,
            s.unifiedMaterialYouColor, s.useMaterialYouDynamicColor, harmonizeWithArtwork = false,
            curatedPalette = s.curatedPalette
        )
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(palette.primaryArgb, hsv)
        val target = when {
            s.themeMode == AppThemeMode.LIGHT -> AppIconVariant.PAPER
            hsv[1] < 0.18f -> AppIconVariant.GRAPHITE
            else -> AppIconVariant.MOONLIGHT
        }
        if (target != s.appIconVariant) {
            settings.update { it.copy(appIconVariant = target) }
            AppIconLauncherManager.applyLauncherIconVariant(getApplication(), target)
        }
    }

    fun setPlayerBackgroundStyle(style: PlayerBackgroundAnimationStyle) {
        settings.update { it.copy(playerBackgroundStyle = style) }
        _statusBanner.value = "Player Background Animation: ${style.label}"
    }

    fun setSongAnalysis(enabled: Boolean) {
        settings.update { it.copy(songAnalysis = enabled) }
        if (enabled) startSongAnalysis() else {
            songAnalysisJob?.cancel()
            songAnalysisJob = null
            viewModelScope.launch { songAnalysis.clear() }
        }
    }

    /** Analyses songs stored on the phone (downloads, then its own music) as they arrive, one at a time, while it is on. */
    private fun startSongAnalysis() {
        if (songAnalysisJob?.isActive == true) return
        songAnalysisJob = viewModelScope.launch {
            songAnalysis.load()
            // The playing song first, then downloads, then this phone's own songs.
            combine(
                downloadsMap.map { map -> map.values.analysisInputs() },
                combine(phoneSource, allServerTracks) { phone, all ->
                    if (phone == null) emptyList()
                    else all.filter { it.isOnDevice }.map { t ->
                        AnalysisInput("${phone.id}_${t.id}", Long.MAX_VALUE) { LocalMusicSource.documentUri(getApplication(), t) }
                    }
                },
                playback.currentTrack.map { it?.analysisKey }.distinctUntilChanged()
            ) { downloads, phoneSongs, playing ->
                (downloads + phoneSongs).map { if (it.key == playing) AnalysisInput(it.key, Long.MIN_VALUE, it.path) else it }
            }
                .distinctUntilChangedBy { list -> list.map { it.key to it.order }.toSet() }
                .collectLatest { inputs -> songAnalysis.analysePending(inputs) }
        }
    }

    fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        audioEngine.pauseOnHeadphoneDisconnect = enabled
        settings.update { it.copy(pauseOnHeadphoneDisconnect = enabled) }
    }

    fun setHighRefreshRate120Hz(enabled: Boolean) {
        settings.update { it.copy(highRefreshRate120Hz = enabled) }
        _statusBanner.value = if (enabled) {
            "120Hz High-Refresh Display Mode enabled"
        } else {
            "Standard 60Hz refresh rate mode"
        }
    }

    fun setSmartShuffleProfile(profile: SmartShuffleProfile) {
        settings.update { it.copy(smartShuffleProfile = profile) }
        playback.reshuffleIfSmart(profile)
        _statusBanner.value = "Smart Shuffle profile: ${profile.label}"
    }

    fun setDownloadSpeedLimit(limit: DownloadSpeedLimit) {
        settings.update { it.copy(downloadSpeedLimit = limit) }
        _statusBanner.value = "Download bandwidth limit set to ${limit.label}"
    }

    /** Sets the streaming quality for mobile data or Wi-Fi; it applies from the next song. */
    fun setStreamQuality(mobile: Boolean, quality: StreamQuality) {
        settings.update {
            if (mobile) it.copy(mobileTranscodeFormat = quality.format, mobileMaxBitRate = quality.maxBitRate)
            else it.copy(wifiTranscodeFormat = quality.format, wifiMaxBitRate = quality.maxBitRate)
        }
        _statusBanner.value = "${if (mobile) "Mobile data" else "Wi-Fi"}: ${quality.label}"
    }

    fun setWidgetLayoutMode(mode: WidgetLayoutMode) {
        settings.update { it.copy(widgetLayoutMode = mode) }
        _statusBanner.value = "Home Widget Layout: ${mode.label}"
    }

    fun setWidgetThemeMode(mode: WidgetThemeMode) {
        settings.update { it.copy(widgetThemeMode = mode) }
        _statusBanner.value = "Home Widget Theme: ${mode.label}"
    }

    fun setCheckForUpdates(enabled: Boolean) {
        settings.update { it.copy(checkForUpdates = enabled) }
        UpdateCheckWorker.schedule(getApplication(), enabled)
        if (enabled) checkForUpdate() else _availableUpdate.value = null
    }

    /**
     * Restores all UI, Library, Player, DSP, and Widget preferences back to factory defaults
     * without deleting any saved OpenSubsonic servers, tracks, or playlists.
     */
    fun restoreDefaultCustomizationSettings() {
        val defaults = PlayerCustomizationSettings()
        val defaultTabs = settings.restoreDefaults()
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

    /**
     * "Your Mix": favourites, top and recent tracks as seeds, topped up with library songs that
     * share their genres, artists and era, then shuffled so every press sounds different.
     */
    /**
     * Plays a fresh smart mix. The songs are picked in the background straight away; when
     * [startAfter] is given, playback waits for it, so the Your Mix hero can hold the start (heavy
     * main-thread work) until its zoom covers the screen. Returns the job that finishes once the
     * mix is playing, or null when there is nothing to play.
     */
    /** Decodes covers the player is about to show into the image cache, at the player's largest size. */
    private fun prefetchCovers(tracks: List<SubsonicTrack>) {
        val app = getApplication<Application>()
        val px = (420 * app.resources.displayMetrics.density).toInt()
        tracks.map { it.coverArtUrl }.filter { it.isNotBlank() }.forEach { url ->
            val key = artworkCacheKey(url)
            app.imageLoader.enqueue(ImageRequest.Builder(app).data(url).memoryCacheKey(key).diskCacheKey(key).size(px).build())
        }
    }

    /** Plays a fresh Your Mix; every call draws a new one (see [yourMix]). */
    fun playYourMix(startAfter: Job? = null): Job? {
        val library = allServerTracks.value
        if (library.isEmpty()) return null
        val recent = _recentTrackIds.value
        return viewModelScope.launch {
            // Scoring the whole library is too slow for the main thread on big libraries; it
            // would stall the hero's launch animation.
            val picks = withContext(Dispatchers.Default) { yourMix(library, recent) }
            if (picks.isEmpty()) return@launch
            // The player opens on the first song: start decoding its cover now, during the zoom,
            // so the player shows the cover rather than an empty tile while it loads.
            prefetchCovers(picks.take(2))
            startAfter?.join()
            playback.playInOrder(picks)
            _statusBanner.value = "Your Mix: ${picks.size} songs picked for you"
        }
    }

    fun seekTo(targetMs: Long) {
        playback.seekTo(targetMs)
        OndaluneMediaBridge.updatePlaybackProgress(
            context = getApplication(),
            positionMs = targetMs,
            durationMs = durationMs.value,
            isPlaying = isPlaying.value,
            playbackSpeed = playbackSpeed.value
        )
    }

    fun toggleStarTrack(track: SubsonicTrack) {
        val updatedStarred = !track.starred
        playback.updateCurrent(track.id) { it.copy(starred = updatedStarred) }
        viewModelScope.launch {
            repository.toggleStarTrack(serverFor(track), track)
            playback.reshuffleIfSmart()
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
    // Offline Downloads & Storage Management
    // =========================================================================

    fun downloadTrackForOffline(track: SubsonicTrack) {
        if (track.isOnDevice) return
        viewModelScope.launch {
            val server = activeServer()
            downloadManager.enqueueTrackDownload(
                server = server,
                track = track,
                speedLimit = settings.customization.value.downloadSpeedLimit
            )
            // No banner: the download button that was tapped turns into a spinner, and the
            // download activity button shows it from anywhere else.
        }
    }

    fun downloadAlbumForOffline(albumId: String) {
        // Songs already on the phone stay as they are; only the rest are fetched.
        val albumTracks = tracks.value.filter { it.albumId == albumId && !it.isOnDevice && downloadsMap.value[it.id]?.isCompleted != true }
        if (albumTracks.isEmpty()) return
        viewModelScope.launch {
            val server = activeServer()
            downloadManager.enqueueAlbumDownload(
                server = server,
                albumTracks = albumTracks,
                speedLimit = settings.customization.value.downloadSpeedLimit
            )
        }
    }

    fun cancelOfflineDownload(trackId: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(_activeServerId.value, trackId)
            _statusBanner.value = "Cancelled download"
        }
    }

    /** Downloads cut off when the app last closed pick up again, in their old queue order. */
    private suspend fun resumeInterruptedDownloads(server: SubsonicServerConfig) {
        val interrupted = downloadManager.interruptedDownloads(server.id).sortedBy { it.updatedAtMs }
        if (interrupted.isEmpty()) return
        val byId = repository.observeTracksForServer(server.id).first().associateBy { it.id }
        interrupted.mapNotNull { byId[it.trackId] }.forEach {
            downloadManager.enqueueTrackDownload(server, it, settings.customization.value.downloadSpeedLimit)
        }
    }

    fun retryOfflineDownloads(trackIds: Collection<String>) {
        val byId = allServerTracks.value.associateBy { it.id }
        val server = activeServer()
        trackIds.mapNotNull { byId[it] }.forEach {
            downloadManager.enqueueTrackDownload(server, it, settings.customization.value.downloadSpeedLimit)
        }
    }

    fun pauseOfflineDownloads(trackIds: Collection<String>) {
        viewModelScope.launch {
            downloadManager.pauseDownloads(_activeServerId.value, trackIds)
        }
    }

    /** Resuming queues the songs again, in the order given. */
    fun resumeOfflineDownloads(trackIds: Collection<String>) =
        retryOfflineDownloads(trackIds.filter { downloadsMap.value[it]?.stateEnum == OfflineDownloadState.PAUSED })

    fun removeOfflineDownloads(trackIds: Collection<String>) {
        viewModelScope.launch {
            trackIds.forEach { downloadManager.removeDownload(_activeServerId.value, it) }
            _statusBanner.value = if (trackIds.size == 1) "Removed offline download" else "Removed ${trackIds.size} offline downloads"
        }
    }

    /** From the downloads sheet: opens the song's album and plays it from there. */
    fun openAndPlayDownloadedTrack(trackId: String) {
        val track = allServerTracks.value.firstOrNull { it.id == trackId } ?: return
        openAlbumAnywhere(track.albumId)
        playback.playTrack(track, allServerTracks.value.filter { it.albumId == track.albumId })
    }

    fun cancelOfflineDownloads(trackIds: Collection<String>) {
        viewModelScope.launch {
            trackIds.forEach { downloadManager.cancelDownload(_activeServerId.value, it) }
            _statusBanner.value = if (trackIds.size == 1) "Cancelled download" else "Cancelled ${trackIds.size} downloads"
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

    fun createPlaylist(name: String, comment: String, selectedTrackIds: List<String>) {
        if (name.isBlank()) return
        val seedTrack = tracks.value.firstOrNull { it.id in selectedTrackIds } ?: playback.currentTrack.value
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

    fun setTracksFavourite(list: List<SubsonicTrack>, favourite: Boolean) {
        list.filter { it.starred != favourite }.forEach { toggleStarTrack(it) }
        _statusBanner.value = when {
            list.size == 1 && favourite -> "Added \"${list.first().title}\" to favourites"
            list.size == 1 -> "Removed \"${list.first().title}\" from favourites"
            favourite -> "Added ${list.size} tracks to favourites"
            else -> "Removed ${list.size} tracks from favourites"
        }
    }

    fun downloadTracks(tracks: List<SubsonicTrack>) {
        val list = tracks.filter { !it.isOnDevice && downloadsMap.value[it.id]?.isCompleted != true }
        if (list.isEmpty()) return
        if (list.size == 1) return downloadTrackForOffline(list.first())
        viewModelScope.launch {
            downloadManager.enqueueAlbumDownload(activeServer(), list, settings.customization.value.downloadSpeedLimit)
        }
    }

    fun addTracksToPlaylist(list: List<SubsonicTrack>, playlist: SubsonicPlaylist) {
        viewModelScope.launch {
            _statusBanner.value = when {
                !repository.addTracksToPlaylist(playlist, list.map { it.id }) ->
                    if (list.any { it.isOnDevice }) "Songs on this phone can't go in a server playlist"
                    else "${activeServer().name} didn't let Ondalune change \"${playlist.name}\""
                list.size == 1 -> "Added \"${list.first().title}\" to ${playlist.name}"
                else -> "Added ${list.size} tracks to ${playlist.name}"
            }
        }
    }

    /**
     * Opens [albumId]'s page from anywhere (player, any list), staying on the current tab when it
     * can show albums. Back returns to where you were. Does nothing for an album not in the library.
     */
    fun openAlbumAnywhere(albumId: String) {
        if (albums.value.none { it.id == albumId }) return
        jumpTo("album:$albumId") {
            if (_currentDestination.value !in setOf(PrimaryDestination.HOME, PrimaryDestination.LIBRARY, PrimaryDestination.FAVOURITES, PrimaryDestination.ARTISTS)) {
                showDestination(PrimaryDestination.LIBRARY)
            }
            openAlbumDetail(albumId)
        }
    }

    fun deletePlaylist(playlistId: String) {
        val playlist = playlists.value.find { it.id == playlistId } ?: return
        viewModelScope.launch {
            if (!repository.deletePlaylist(playlist)) {
                _statusBanner.value = "${activeServer().name} didn't let Ondalune delete \"${playlist.name}\""
            }
        }
    }

    private val _isRefreshingPlaylists = MutableStateFlow(false)
    val isRefreshingPlaylists: StateFlow<Boolean> = _isRefreshingPlaylists.asStateFlow()

    /** Reads the server's playlists again; the saved ones stay when it can't be reached. */
    fun refreshServerPlaylists() {
        val server = activeServer()
        if (server.isDemoServer || _isRefreshingPlaylists.value) return
        viewModelScope.launch {
            _isRefreshingPlaylists.value = true
            if (!repository.syncServerPlaylists(server)) {
                _statusBanner.value = "Couldn't reach ${server.name}; showing the playlists saved on this phone"
            }
            _isRefreshingPlaylists.value = false
        }
    }

    fun testAndSaveServer(
        name: String,
        baseUrl: String,
        username: String,
        passwordOrToken: String,
        useTokenAuth: Boolean,
        transcodeFormat: String,
        maxBitRate: Int,
        sourceType: SourceType = SourceType.SUBSONIC
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
                isActive = true,
                sourceType = sourceType.name
            )
            OndaluneMediaBridge.clearRemoteArtworkCache()
            val (saved, diag) = repository.saveAndActivateServer(config)
            if (saved.id != 0L) _activeServerId.value = saved.id
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

    // ---- Music folders on this phone -----------------------------------------------------

    /** Folders picked in Settings → Music sources; only their music joins the library. */
    val localFolders: StateFlow<List<LocalFolder>> = repository.observeLocalFolders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A folder scan under way: which folder, how many songs found and read. */
    data class FolderScan(val folderName: String, val found: Int, val read: Int)

    private val _folderScan = MutableStateFlow<FolderScan?>(null)
    val folderScan: StateFlow<FolderScan?> = _folderScan.asStateFlow()
    private var folderScanJob: Job? = null
    private val folderReader by lazy { FolderReader(getApplication()) }
    private val folderPermissions by lazy { FolderPermissions(getApplication()) }

    /** Adds a folder picked in Android's folder picker, keeps access to it, and reads its music. */
    fun addMusicFolder(treeUri: Uri) {
        viewModelScope.launch {
            if (runCatching { folderPermissions.take(treeUri) }.isFailure) {
                _statusBanner.value = "Ondalune couldn't keep access to that folder"
                return@launch
            }
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val name = docId.substringAfter(':').substringAfterLast('/').ifBlank { folderLocation(docId).substringAfterLast(" › ") }
            val alreadyPicked = localFolders.value.any { it.treeUri == treeUri.toString() }
            when (repository.addLocalFolder(treeUri.toString(), docId, name)) {
                OndaluneRepository.FolderAdd.ALREADY_INCLUDED -> {
                    if (!alreadyPicked) folderPermissions.release(treeUri.toString())
                    _statusBanner.value = "\"$name\" is already in your music folders"
                }
                OndaluneRepository.FolderAdd.REPLACED_INNER -> {
                    _statusBanner.value = "\"$name\" replaces the folders inside it"
                    scanFolders(repository.localFolders().filter { it.treeUri == treeUri.toString() })
                }
                OndaluneRepository.FolderAdd.ADDED ->
                    scanFolders(repository.localFolders().filter { it.treeUri == treeUri.toString() })
            }
        }
    }

    /** Forgets a folder and its songs; the files stay on the phone. */
    fun removeMusicFolder(folder: LocalFolder) {
        viewModelScope.launch {
            if (_folderScan.value?.folderName == folder.name) folderScanJob?.cancel()
            repository.removeLocalFolder(folder)
        }
    }

    fun rescanMusicFolder(folder: LocalFolder) = scanFolders(listOf(folder))

    fun rescanAllMusicFolders() = scanFolders(localFolders.value)

    fun cancelFolderScan() {
        folderScanJob?.cancel()
    }

    fun setRescanFoldersOnLaunch(enabled: Boolean) = settings.setRescanFoldersOnLaunch(enabled)

    /** Scans [folders] one after another, after any scan already running; never on the main thread. */
    private fun scanFolders(folders: List<LocalFolder>) {
        if (folders.isEmpty()) return
        val before = folderScanJob
        folderScanJob = viewModelScope.launch(Dispatchers.IO) {
            before?.join()
            try {
                for (folder in folders) {
                    _folderScan.value = FolderScan(folder.name, 0, 0)
                    val result = repository.scanLocalFolder(folder, folderReader) { found, read ->
                        _folderScan.value = FolderScan(folder.name, found, read)
                    }
                    if (!result.accessible) {
                        _statusBanner.value = "Can't open \"${folder.name}\". Its songs stay; allow it again in Settings → Music sources"
                    }
                }
            } finally {
                _folderScan.value = null
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    private fun loadLyricsForTrack(track: SubsonicTrack) {
        viewModelScope.launch {
            val result = repository.fetchLyricsResult(serverFor(track), track)
            _currentLyricsResult.value = result
            _currentLyrics.value = result.syncedLines
            OndaluneMediaBridge.updateLyrics(getApplication(), track.id, result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().getSystemService(AudioManager::class.java)?.unregisterAudioDeviceCallback(outputDeviceCallback)
        playback.release()
        // These handlers launch in viewModelScope, which is cancelled now: left attached they
        // swallowed every notification command, so music playing on could not be paused or
        // seeked. Detached, the bridge drives the engine directly. A newer screen's handlers stay.
        if (OndaluneMediaBridge.onCommand === bridgeCommand) OndaluneMediaBridge.onCommand = null
        if (OndaluneMediaBridge.onSeekTo === bridgeSeek) OndaluneMediaBridge.onSeekTo = null
    }
}

private const val PREF_RECENT_TRACKS = "home_recent_track_ids"
private const val PREF_FAVOURITE_ARTISTS = "favourite_artist_ids"
private const val PREF_HOME_INTRO_SEEN = "intro_home_learns_seen"
private val LosslessSuffixes = setOf("FLAC", "ALAC", "WAV", "AIFF", "AIF", "APE", "WV", "DSF", "DFF")

data class LibraryFilters(
    val downloadedOnly: Boolean = false,
    val hideShortTracks: Boolean = false,
    val losslessOnly: Boolean = false
) {
    val active: Boolean get() = downloadedOnly || hideShortTracks || losslessOnly
}
private const val PREF_FULL_SYNC_AT = "full_library_sync_at_"
private const val PREF_DOWNLOADS_HINT_SEEN = "hint_downloads_seen"
private const val PREF_UPDATE_FOLDED = "update_dismissed_version"

/**
 * The library syncs on its own only once per server, when it has never been fully synced (first
 * run, or a server added outside onboarding). After that only the user starts a sync: pull to
 * refresh on Home, or Sync in the library filter and server sheets.
 */
internal fun shouldSyncOnLaunch(lastFullSyncAt: Long, isDemoServer: Boolean): Boolean =
    !isDemoServer && lastFullSyncAt == 0L

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
    val rediscover: List<SubsonicAlbum> = emptyList(),
    val mixes: List<LibraryMix> = emptyList()
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

/** The key song analysis files a song's result under ("serverId_trackId"). */
private val SubsonicTrack.analysisKey: String get() = "${serverId}_$id"
