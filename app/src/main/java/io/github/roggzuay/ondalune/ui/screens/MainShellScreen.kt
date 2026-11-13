@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.data.model.isOnDevice
import io.github.roggzuay.ondalune.data.model.streamQuality
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.withFrameNanos
import io.github.roggzuay.ondalune.ui.components.BackgroundPlayback
import io.github.roggzuay.ondalune.ui.components.BackgroundPlaybackPrompt
import io.github.roggzuay.ondalune.data.model.AppIconPreviewFrameShape
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.data.model.DockStyle
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import androidx.compose.runtime.rememberUpdatedState
import io.github.roggzuay.ondalune.ui.components.TrackOptionsSheet
import io.github.roggzuay.ondalune.ui.components.WhatsNewDialog
import io.github.roggzuay.ondalune.ui.components.UpdateDetailsSheet
import io.github.roggzuay.ondalune.ui.components.DownloadsSheet
import io.github.roggzuay.ondalune.ui.components.DownloadActivityButton
import io.github.roggzuay.ondalune.ui.components.DownloadOrder
import io.github.roggzuay.ondalune.data.local.AppUpdateInstaller
import io.github.roggzuay.ondalune.data.local.UpdateInstallState
import io.github.roggzuay.ondalune.BuildConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.platform.LocalUriHandler
import io.github.roggzuay.ondalune.ui.components.SelectionTopBar
import io.github.roggzuay.ondalune.ui.components.LocalDownloads
import io.github.roggzuay.ondalune.ui.components.LocalTrackSelection
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import io.github.roggzuay.ondalune.ui.components.TrackSelection
import io.github.roggzuay.ondalune.ui.components.TrackActions
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.ContentTransform
import io.github.roggzuay.ondalune.ui.theme.LocalArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.rememberArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.ArtworkPaletteCache
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.core.content.ContextCompat
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.ui.components.PullSyncIndicator
import io.github.roggzuay.ondalune.ui.components.ExpressiveNavItem
import io.github.roggzuay.ondalune.ui.components.ExpressiveNavigationDock
import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.components.OndaluneBrandEmblem
import io.github.roggzuay.ondalune.ui.components.rememberHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.OndaluneExpressiveTheme
import io.github.roggzuay.ondalune.ui.theme.FadingColors
import io.github.roggzuay.ondalune.ui.theme.parseHexColor
import io.github.roggzuay.ondalune.ui.viewmodel.PrimaryDestination
import io.github.roggzuay.ondalune.ui.viewmodel.LibraryFilterTab
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelfKind
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.Box
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.source

/**
 * Process-scoped tracker ensuring the Ondalune Animated Launch Intro runs at most once per cold
 * process start and never repeats on `onResume` or configuration changes.
 */
object OndaluneLaunchIntroTracker {
    @Volatile
    private var hasShownInProcess: Boolean = false

    /** Set once the system splash (Android 12+) has faded; the intro holds its first frame until then. */
    val splashGone = MutableStateFlow(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S)

    fun shouldPlayIntroOnColdStart(reducedMotion: Boolean): Boolean {
        if (reducedMotion) {
            hasShownInProcess = true
            return false
        }
        if (hasShownInProcess) return false
        hasShownInProcess = true
        return true
    }

    fun markCompleted() {
        hasShownInProcess = true
    }

    fun hasCompletedInProcess(): Boolean = hasShownInProcess

    fun resetForTesting() {
        hasShownInProcess = false
    }
}

@Composable
fun OndaluneMainShell(viewModel: OndaluneViewModel) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tracks by viewModel.filteredTracks.collectAsStateWithLifecycle()
    val allAlbums by viewModel.albums.collectAsStateWithLifecycle()
    val albums by viewModel.filteredAlbums.collectAsStateWithLifecycle()
    val allArtists by viewModel.artists.collectAsStateWithLifecycle()
    val artists by viewModel.filteredArtists.collectAsStateWithLifecycle()
    val playlists by viewModel.filteredPlaylists.collectAsStateWithLifecycle()
    val genres by viewModel.filteredGenres.collectAsStateWithLifecycle()
    val folders by viewModel.filteredFolders.collectAsStateWithLifecycle()

    val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
    val selectedListType by viewModel.selectedAlbumListType.collectAsStateWithLifecycle()
    val libraryFilterTab by viewModel.libraryFilterTab.collectAsStateWithLifecycle()
    val libraryViewMode by viewModel.libraryViewMode.collectAsStateWithLifecycle()
    val librarySortOption by viewModel.librarySortOption.collectAsStateWithLifecycle()
    val favouritesFilterTab by viewModel.favouritesFilterTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val currentTrack by viewModel.playback.currentTrack.collectAsStateWithLifecycle()
    val playbackQueue by viewModel.playback.playbackQueue.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentLyricsResult by viewModel.currentLyricsResult.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val lyricsSearch by viewModel.lyricsSearch.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val downloadsMap by viewModel.downloadsMap.collectAsStateWithLifecycle()
    val offlineStorageSummary by viewModel.offlineStorageSummary.collectAsStateWithLifecycle()

    val speedPitchState by viewModel.speedPitchState.collectAsStateWithLifecycle()
    val reverbState by viewModel.reverbState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.playback.sleepTimerState.collectAsStateWithLifecycle()
    val tabDisplayPreferences by viewModel.tabDisplayPreferences.collectAsStateWithLifecycle()

    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isRefreshingPlaylists by viewModel.isRefreshingPlaylists.collectAsStateWithLifecycle()
    val currentTempo by viewModel.currentTempo.collectAsStateWithLifecycle()
    val offlineSummary by viewModel.offlineSummary.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val showDownloadsHint by viewModel.showDownloadsHint.collectAsStateWithLifecycle()
    // Your Mix launch, written by the hero each frame; the shell draws the zoom over its own bars.
    val heroLaunch = remember { HeroLaunch() }
    // How far the player is open; the mini player and the full player share it.
    val playerSheet = remember { PlayerSheetState() }
    val playerSheetScope = rememberCoroutineScope()
    LaunchedEffect(isPlayerExpanded) { playerSheet.settleTo(isPlayerExpanded, playerSheetScope) }
    val playerShown by remember { derivedStateOf { playerSheet.fraction > 0f } }
    val isSonicLabOpen by viewModel.isSonicLabSheetOpen.collectAsStateWithLifecycle()
    val isSpeedPitchOpen by viewModel.isSpeedPitchSheetOpen.collectAsStateWithLifecycle()
    val isSleepTimerOpen by viewModel.isSleepTimerSheetOpen.collectAsStateWithLifecycle()
    val isUserServerOpen by viewModel.isUserServerSheetOpen.collectAsStateWithLifecycle()
    val isAppIconSettingsOpen by viewModel.isAppIconSettingsOpen.collectAsStateWithLifecycle()
    val showServerOnboarding by viewModel.showServerOnboarding.collectAsStateWithLifecycle()
    val whatsNew by viewModel.whatsNew.collectAsStateWithLifecycle()
    val showSpeedResetHint by viewModel.showSpeedResetHint.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val updateInstall by viewModel.updateInstall.collectAsStateWithLifecycle()
    val updateCheckStatus by viewModel.updateCheckStatus.collectAsStateWithLifecycle()
    val updateDetailsOpen by viewModel.updateDetailsOpen.collectAsStateWithLifecycle()
    val updateBannerFolded by viewModel.updateBannerFolded.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val onboardingSync by viewModel.onboardingSync.collectAsStateWithLifecycle()
    val homeZoom by animateFloatAsState(
        targetValue = if (showServerOnboarding) 0.9f else 1f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "onboardingHomeZoom"
    )
    val isCutoutSyncActive by viewModel.isCutoutSyncActive.collectAsStateWithLifecycle()

    val nowPlayingTab by viewModel.nowPlayingTab.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.playback.shuffleMode.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.playback.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatOneEnabled by viewModel.playback.repeatOneEnabled.collectAsStateWithLifecycle()

    val eqPreset by viewModel.equalizerPreset.collectAsStateWithLifecycle()
    val customEqBands by viewModel.customEqBands.collectAsStateWithLifecycle()
    val customizationSettings by viewModel.customizationSettings.collectAsStateWithLifecycle()
    val localFolders by viewModel.localFolders.collectAsStateWithLifecycle()
    val folderScan by viewModel.folderScan.collectAsStateWithLifecycle()

    val selectedAlbumId by viewModel.selectedAlbumDetailId.collectAsStateWithLifecycle()
    val selectedArtistId by viewModel.selectedArtistDetailId.collectAsStateWithLifecycle()
    val selectedGenreName by viewModel.selectedGenreDetail.collectAsStateWithLifecycle()
    val selectedFolderPath by viewModel.selectedFolderDetail.collectAsStateWithLifecycle()
    val openHomeShelf by viewModel.openHomeShelf.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistDetailId.collectAsStateWithLifecycle()
    val shownPage = ShellNavState(currentDestination, selectedAlbumId, selectedArtistId, selectedGenreName, selectedFolderPath, openHomeShelf, selectedPlaylistId)
    // Held here, outside the tab transitions, so Home keeps its scroll position while a page from
    // it (or another tab) is open.
    val homeListState = rememberLazyListState()
    // A page keeps its saved state, its scroll position above all, while Back can still reach it.
    val pageStates = rememberSaveableStateHolder()
    val backTrail = remember { mutableListOf<String>() }

    var searchOpen by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    /** A tab press leaves search; pressing the open tab again otherwise takes it back to its top. */
    fun openTab(dest: PrimaryDestination) {
        val searching = searchOpen || searchQuery.isNotEmpty()
        if (searching) {
            searchOpen = false
            viewModel.updateSearchQuery("")
        }
        when {
            dest != currentDestination -> viewModel.selectDestination(dest)
            !searching -> viewModel.reselectDestination()
        }
    }

    // The mini player waits for the first song you play. A song restored from last time stays
    // loaded (headphone and notification play resume it) but out of the way.
    var playedThisSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isPlaying, isBuffering) { if (isPlaying || isBuffering) playedThisSession = true }
    val miniPlayerTrack = currentTrack?.takeIf { playedThisSession }

    // Scrolling a list down slides the mini player away; the slightest scroll up brings it back.
    var miniPlayerScrolledAway by remember { mutableStateOf(false) }
    val miniPlayerScrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y < -2f) miniPlayerScrolledAway = true
                else if (consumed.y > 2f) miniPlayerScrolledAway = false
                return Offset.Zero
            }
        }
    }
    // A new page or a new song shows it again.
    LaunchedEffect(currentDestination, selectedAlbumId, selectedArtistId, selectedPlaylistId, openHomeShelf, currentTrack?.id) {
        miniPlayerScrolledAway = false
    }
    val scrollToTopSignal by viewModel.scrollToTopSignal.collectAsStateWithLifecycle()

    val pingDiagnostic by viewModel.pingDiagnostic.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val homeShelves by viewModel.homeShelves.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.activeServer().id) { viewModel.refreshHomeShelves() }

    val activeServer = viewModel.activeServer()
    var homePullProgress by remember { mutableFloatStateOf(0f) }
    val libraryFilters by viewModel.libraryFilters.collectAsStateWithLifecycle()
    val allServerTracks by viewModel.allServerTracks.collectAsStateWithLifecycle()
    var filterSheetOpen by remember { mutableStateOf(false) }
    val trackSelection = remember { TrackSelection() }
    var selectionSheetOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = trackSelection.active) { trackSelection.clear() }
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentPlaylists by rememberUpdatedState(allPlaylists)
    val trackActions = remember(viewModel) {
        object : TrackActions {
            override val playlists get() = currentPlaylists
            override fun playNext(tracks: List<SubsonicTrack>) = viewModel.playback.playTracksNext(tracks)
            override fun addToQueue(tracks: List<SubsonicTrack>) = viewModel.playback.addTracksToQueue(tracks)
            override fun toggleFavourite(track: SubsonicTrack) = viewModel.toggleStarTrack(track)
            override fun setFavourite(tracks: List<SubsonicTrack>, favourite: Boolean) = viewModel.setTracksFavourite(tracks, favourite)
            override fun download(tracks: List<SubsonicTrack>) = viewModel.downloadTracks(tracks)
            override fun removeDownload(track: SubsonicTrack) = viewModel.removeOfflineDownload(track.id)
            override fun cancelDownloads(tracks: List<SubsonicTrack>) = viewModel.cancelOfflineDownloads(tracks.map { it.id })
            override fun resumeDownloads(tracks: List<SubsonicTrack>) = viewModel.resumeOfflineDownloads(tracks.map { it.id })
            override fun removeDownloads(tracks: List<SubsonicTrack>) = viewModel.removeOfflineDownloads(tracks.map { it.id })
            override fun addToPlaylist(tracks: List<SubsonicTrack>, playlist: SubsonicPlaylist) = viewModel.addTracksToPlaylist(tracks, playlist)
            override fun createPlaylist(name: String, tracks: List<SubsonicTrack>) = viewModel.createPlaylist(name, "", tracks.map { it.id })
            override fun openAlbum(track: SubsonicTrack) = viewModel.openAlbumAnywhere(track.albumId)
            override fun openArtist(track: SubsonicTrack) = viewModel.openArtistFromTrack(track)
            override fun openArtist(artistId: String, name: String) = viewModel.openArtistByName(artistId, name)
            override fun originalOf(track: SubsonicTrack) = viewModel.originalOf(track)
            override fun saveDetails(track: SubsonicTrack, title: String, artist: String, genre: String, year: Int) =
                viewModel.saveTrackDetails(track, title, artist, genre, year)
        }
    }
    val favouriteArtistIds by viewModel.favouriteArtistIds.collectAsStateWithLifecycle()
    val songFeatures by viewModel.songFeatures.collectAsStateWithLifecycle()
    val similarArtistIds by viewModel.similarArtistIds.collectAsStateWithLifecycle()
    // Artist pages on screen (id → portrait); during a page change two can briefly overlap, and
    // the one shown last decides.
    val shownPortraits = remember { mutableStateListOf<Pair<String, String?>>() }
    val artistExtras = remember(favouriteArtistIds, similarArtistIds, allArtists) {
        ArtistExtras(
            favourites = favouriteArtistIds,
            similar = similarArtistIds,
            allArtists = allArtists,
            onToggleFavourite = viewModel::toggleFavouriteArtist,
            onOpenArtist = viewModel::openArtistDetail,
            onShowPortrait = { id, url -> shownPortraits.removeAll { it.first == id }; shownPortraits += id to url },
            onHidePortrait = { id -> shownPortraits.removeAll { it.first == id } }
        )
    }
    // One fixed order for every downloads list: rows never trade places as they progress.
    val tracksById = remember(allServerTracks) { allServerTracks.associateBy { it.id } }
    val downloadOrder = remember { DownloadOrder() }
    val downloadComparator = remember(tracksById) { downloadOrder.comparator { tracksById[it]?.trackNumber ?: 0 } }
    val activeDownloads = remember(downloadsMap, downloadComparator) {
        downloadOrder.place(downloadsMap.values)
        downloadsMap.values.filter {
            it.status == OfflineDownloadState.DOWNLOADING.name || it.status == OfflineDownloadState.QUEUED.name
        }.sortedWith(downloadComparator)
    }
    var showDownloads by remember { mutableStateOf(false) }
    // Failures after the downloads sheet was last seen keep the activity button up, in red.
    var downloadsSeenAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val unseenDownloadFailures = remember(downloadsMap, downloadsSeenAt) {
        downloadsMap.values.any { it.status == OfflineDownloadState.FAILED.name && it.updatedAtMs > downloadsSeenAt }
    }

    val anyModalOpen = isPlayerExpanded || isSonicLabOpen || isSpeedPitchOpen ||
        isSleepTimerOpen || isUserServerOpen || isAppIconSettingsOpen || showServerOnboarding
    val anySubDetailOpen = openHomeShelf != null || selectedAlbumId != null || selectedArtistId != null ||
        selectedGenreName != null || selectedFolderPath != null || selectedPlaylistId != null

    if (currentDestination != PrimaryDestination.HOME && !anyModalOpen && !anySubDetailOpen) {
        BackHandler {
            viewModel.selectDestination(PrimaryDestination.HOME)
        }
    }

    // Primary bottom navigation: Home - Library - Favourites - Playlists - Artists (Settings lives in the top bar)
    val navItems = remember {
        listOf(
            ExpressiveNavItem(
                id = PrimaryDestination.HOME,
                label = PrimaryDestination.HOME.label,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_home"
            ),
            ExpressiveNavItem(
                id = PrimaryDestination.LIBRARY,
                label = PrimaryDestination.LIBRARY.label,
                selectedIcon = Icons.Filled.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_tab_library"
            ),
            ExpressiveNavItem(
                id = PrimaryDestination.FAVOURITES,
                label = PrimaryDestination.FAVOURITES.label,
                selectedIcon = Icons.Filled.Favorite,
                unselectedIcon = Icons.Outlined.FavoriteBorder,
                testTag = "nav_tab_favourites"
            ),
            ExpressiveNavItem(
                id = PrimaryDestination.PLAYLISTS,
                label = PrimaryDestination.PLAYLISTS.label,
                selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
                unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic,
                testTag = "nav_tab_playlists"
            ),
            ExpressiveNavItem(
                id = PrimaryDestination.ARTISTS,
                label = PrimaryDestination.ARTISTS.label,
                selectedIcon = Icons.Filled.Person,
                unselectedIcon = Icons.Outlined.Person,
                testTag = "nav_tab_artists"
            )
        )
    }

    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // First playback: offer to lift battery optimisation so locking the phone doesn't stop music.
    var showBatteryPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (isPlaying && BackgroundPlayback.shouldPrompt(context)) {
            delay(1500L)
            BackgroundPlayback.markPrompted(context)
            showBatteryPrompt = true
        }
    }

    LaunchedEffect(isPlaying, activeDownloads.isNotEmpty()) {
        if ((isPlaying || activeDownloads.isNotEmpty()) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    val hapticManager = rememberHapticFeedbackManager(
        enabled = customizationSettings.hapticWobbleButtons
    )

    // Observe play/pause state transitions so starting a song from any list, mini-player,
    // full-screen studio, or sleep-timer pause delivers the play/pause transition haptic
    var previousIsPlaying by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying != previousIsPlaying) {
            previousIsPlaying = isPlaying
            hapticManager.performPlayPauseTransition(isStartingPlay = isPlaying)
        }
    }

    CompositionLocalProvider(
        LocalHapticFeedbackManager provides hapticManager,
        LocalTrackActions provides trackActions,
        LocalTrackSelection provides trackSelection,
        LocalDownloads provides downloadsMap,
        LocalArtistExtras provides artistExtras
    ) {
        val artworkPalette = rememberArtworkPalette(currentTrack)
        // On an artist page the app takes the portrait's colours; the full player, and every other
        // page, keep the playing song's. Playing a song from the page doesn't change them.
        val portraitUrl = shownPortraits.lastOrNull()?.second
        val portraitPalette by produceState(portraitUrl?.let { ArtworkPaletteCache.peek(it) }, portraitUrl) {
            value = portraitUrl?.let { ArtworkPaletteCache.peek(it) ?: ArtworkPaletteCache.load(context, it) }
        }
        val portraitTheme = portraitPalette?.takeIf { portraitUrl != null && !isPlayerExpanded }
        CompositionLocalProvider(LocalArtworkPalette provides artworkPalette) {
        OndaluneExpressiveTheme(
            seedHex = portraitTheme?.seedHex ?: artworkPalette.seedHex,
            // A change of where the colours come from fades; a new song on the same source doesn't.
            transitionKey = if (portraitTheme != null) "artist:$portraitUrl" else "song",
            accentHex = currentTrack?.accentHex ?: "#70F5FF",
            themeMode = customizationSettings.themeMode,
            useMaterialYouDynamic = customizationSettings.useMaterialYouDynamicColor,
            harmonizeWithArtwork = customizationSettings.harmonizePlayerWithArtwork,
            unifiedMaterialYouColor = customizationSettings.unifiedMaterialYouColor,
            curatedPalette = customizationSettings.curatedPalette,
            cornerStylePreset = customizationSettings.cornerStylePreset,
            interfaceStyle = customizationSettings.interfaceStyle,
            headerFont = customizationSettings.headerFont,
            bodyFont = customizationSettings.bodyFont,
            artworkCut = customizationSettings.artworkCut
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val isExpandedScreen = maxWidth >= 640.dp

                Scaffold(
                    modifier = Modifier.graphicsLayer { scaleX = homeZoom; scaleY = homeZoom },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        // Artist pages show their portrait up to the top instead (selection still gets its bar).
                        // Leaving one, the bar is back at full height at once: the page coming in is laid
                        // out under it from its first frame, so it reopens where it was left.
                        val artistPageShown = shownPage.detailKey?.startsWith("artist:") == true
                        AnimatedVisibility(
                            visible = shownPortraits.isEmpty() || !artistPageShown || trackSelection.active,
                            enter = fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                        ) {
                        if (trackSelection.active) {
                            SelectionTopBar(selection = trackSelection, onMore = { selectionSheetOpen = true })
                        } else {
                        OndaluneTopBar(
                            title = currentDestination.label,
                            searchQuery = searchQuery,
                            searchOpen = searchOpen,
                            statusBanner = statusBanner,
                            filtersActive = libraryFilters.active,
                            isSettingsActive = currentDestination == PrimaryDestination.SETTINGS,
                            bannerBusy = isCutoutSyncActive,
                            onQueryChange = viewModel::updateSearchQuery,
                            onSearchOpenChange = { searchOpen = it },
                            onOpenFilters = { filterSheetOpen = true },
                            onOpenSettings = { viewModel.selectDestination(PrimaryDestination.SETTINGS) },
                            onDismissBanner = viewModel::dismissBanner
                        )
                        }
                        }
                    },
                    floatingActionButton = {
                        // An album page already shows its own download in place.
                        val shownInPlace = selectedAlbumId != null &&
                            activeDownloads.isNotEmpty() && activeDownloads.all { it.albumId == selectedAlbumId }
                        DownloadActivityButton(
                            visible = (activeDownloads.isNotEmpty() || unseenDownloadFailures) &&
                                !showDownloads && !isPlayerExpanded && !shownInPlace,
                            active = activeDownloads,
                            hasUnseenFailures = unseenDownloadFailures,
                            onClick = { showDownloads = true }
                        )
                    },
                    bottomBar = {
                        if (!isExpandedScreen) {
                            val pageColor = MaterialTheme.colorScheme.background
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        // Standard dock: no bar, just a soft fade so labels stay legible over content.
                                        if (customizationSettings.dockStyle == DockStyle.STATIC) Modifier.background(
                                            Brush.verticalGradient(0f to Color.Transparent, 0.3f to pageColor.copy(alpha = 0.55f), 0.55f to pageColor.copy(alpha = 0.9f), 1f to pageColor)
                                        ) else Modifier
                                    )
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                            ) {
                                AnimatedVisibility(
                                    visible = miniPlayerTrack != null &&
                                        !(customizationSettings.hideMiniPlayerOnScroll && miniPlayerScrolledAway),
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    val activeTrack = miniPlayerTrack ?: return@AnimatedVisibility
                                    PlaybackPosition(viewModel) { positionMs, durationMs ->
                                        FloatingExpressiveMiniPlayer(
                                            track = activeTrack,
                                            hazeState = hazeState,
                                            isPlaying = isPlaying,
                                            isBuffering = isBuffering,
                                            positionMs = positionMs,
                                            durationMs = durationMs,
                                            onTogglePlay = viewModel.playback::togglePlayPause,
                                            onSkipNext = viewModel.playback::skipNext,
                                            onSkipPrevious = { viewModel.playback.skipPrevious(restartIfPlayed = false) },
                                            onExpand = { viewModel.setPlayerExpanded(true) },
                                            sheet = playerSheet
                                        )
                                    }
                                }

                                ExpressiveNavigationDock(
                                    items = navItems,
                                    selectedId = currentDestination,
                                    dockStyle = customizationSettings.dockStyle,
                                    hazeState = hazeState,
                                    onSelect = ::openTab
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        // No bottom inset: lists scroll under the dock and mini player (every tab
                        // ends with room for them), which is what makes the Standard dock see-through.
                        // The top bar's height is read while laying out, not while composing: a page
                        // that comes back with the bar is measured at its real height from its first
                        // frame, so its list reopens where it was left instead of clamping to the top.
                        modifier = Modifier
                            .fillMaxSize()
                            .layout { measurable, constraints ->
                                val top = innerPadding.calculateTopPadding().roundToPx()
                                val placeable = measurable.measure(constraints.offset(vertical = -top))
                                layout(placeable.width, placeable.height + top) { placeable.place(0, top) }
                            }
                    ) {
                        if (isExpandedScreen) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                navItems.map { it.id }.forEach { dest ->
                                    val selected = dest == currentDestination
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { openTab(dest) },
                                        icon = {
                                            Icon(
                                                imageVector = destinationIcon(dest, selected),
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag("nav_rail_${dest.route}")
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f).nestedScroll(miniPlayerScrollWatcher)) {
                            val reducedMotion = LocalReducedMotion.current
                            LaunchedEffect(shownPage.key) { backTrail.follow(shownPage, pageStates) }
                            AnimatedContent(
                                targetState = shownPage,
                                contentKey = { it.key },
                                transitionSpec = {
                                    when {
                                        reducedMotion -> fadeIn(tween(110)).togetherWith(fadeOut(tween(90)))
                                        // Same tab, detail pushed or popped: shared axis X (forward in, back out).
                                        targetState.destination == initialState.destination ->
                                            sharedAxisX(forward = targetState.depth >= initialState.depth)
                                        // Top-level destinations: fade through, as bottom navigation specifies.
                                        else -> fadeThrough()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .hazeSource(hazeState),
                                label = "mainTabTransition"
                            ) { nav ->
                                pageStates.SaveableStateProvider(nav.key) {
                                // Render the snapshot this content was created for, so an outgoing
                                // detail screen keeps its content while it animates away.
                                val destination = nav.destination
                                val selectedAlbumId = nav.albumId
                                val selectedArtistId = nav.artistId
                                val selectedGenreName = nav.genreName
                                val selectedFolderPath = nav.folderPath
                                val openHomeShelf = nav.shelf
                                val selectedPlaylistId = nav.playlistId
                                if (searchQuery.isNotBlank() && destination != PrimaryDestination.SETTINGS) {
                                    SmartDynamicSearchScreen(
                                        searchQuery = searchQuery,
                                        allTracks = allTracks,
                                        allAlbums = allAlbums,
                                        allArtists = allArtists,
                                        matchedTracks = tracks,
                                        matchedAlbums = albums,
                                        matchedArtists = artists,
                                        matchedPlaylists = playlists,
                                        matchedGenres = genres,
                                        matchedFolders = folders,
                                        currentTrackId = currentTrack?.id,
                                        isPlaying = isPlaying,
                                        selectedAlbumId = selectedAlbumId,
                                        selectedArtistId = selectedArtistId,
                                        selectedGenreName = selectedGenreName,
                                        selectedFolderPath = selectedFolderPath,
                                        onUpdateQuery = viewModel::updateSearchQuery,
                                        onClearSearch = { viewModel.updateSearchQuery("") },
                                        onPlayTrack = { track, queueCtx -> viewModel.playback.playTrack(track, queueCtx) },
                                        onToggleStarTrack = viewModel::toggleStarTrack,
                                        onOpenAlbum = viewModel::openAlbumDetail,
                                        onPlayAlbum = viewModel.playback::playAlbum,
                                        onOpenArtist = viewModel::openArtistDetail,
                                        onOpenGenre = viewModel::openGenreDetail,
                                        onOpenFolder = viewModel::openFolderDetail,
                                        onPlayPlaylist = viewModel.playback::playPlaylist
                                    )
                                } else when (destination) {
                                    PrimaryDestination.HOME -> {
                                        ListenNowHomeScreen(
                                            albums = albums,
                                            tracks = tracks,
                                            selectedListType = selectedListType,
                                            activeEqPreset = eqPreset,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            shuffleMode = shuffleMode,
                                            selectedAlbumId = selectedAlbumId,
                                            onSelectListType = viewModel::selectAlbumListType,
                                            onOpenAlbum = { albumId ->
                                                viewModel.openAlbumDetail(albumId)
                                            },
                                            onCloseAlbumDetail = {
                                                viewModel.openAlbumDetail(null)
                                            },
                                            onPlayAlbum = viewModel.playback::playAlbum,
                                            onPlayTrack = { track -> viewModel.playback.playTrack(track, tracks) },
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                            onStartSmartShuffle = viewModel::playYourMix,
                                            onNewYourMix = { viewModel.playYourMix(fresh = true) },
                                            onOpenPlayerFromMix = {
                                                playerSheet.fromHero = true
                                                viewModel.setPlayerExpanded(true)
                                            },
                                            homeSections = customizationSettings.homeSections,
                                            onSetHomeSections = viewModel.settings::setHomeSections,
                                            onResetHomeSections = viewModel.settings::resetHomeSections,
                                            onPullProgressChange = { homePullProgress = it },
                                            onQuickSync = viewModel::triggerQuickSync,
                                            shelves = homeShelves,
                                            scrollToTopSignal = scrollToTopSignal,
                                            openShelf = openHomeShelf,
                                            onOpenShelf = viewModel::openHomeShelf,
                                            onPlayTrackFrom = { track, queue -> viewModel.playback.playTrack(track, queue) },
                                            availableUpdate = availableUpdate,
                                            onViewUpdate = viewModel::showUpdateDetails,
                                            onLaterUpdate = viewModel::dismissUpdate,
                                            updateFolded = updateBannerFolded,
                                            showDownloadsHint = showDownloadsHint,
                                            onShowDownloads = {
                                                viewModel.dismissDownloadsHint()
                                                filterSheetOpen = true
                                            },
                                            onDismissDownloadsHint = viewModel::dismissDownloadsHint,
                                            listState = homeListState,
                                            heroLaunch = heroLaunch,
                                            onPlayMix = viewModel::playMix
                                        )
                                    }

                                    PrimaryDestination.LIBRARY -> {
                                        LibraryCollectionScreen(
                                            serverName = activeServer.name,
                                            partialAlbums = if (libraryFilters.downloadedOnly) offlineSummary.partialAlbums else emptyMap(),
                                            albums = albums,
                                            artists = artists,
                                            tracks = tracks,
                                            playlists = playlists,
                                            genres = genres,
                                            folders = folders,
                                            activeTab = libraryFilterTab,
                                            scrollToTopSignal = scrollToTopSignal,
                                            viewMode = libraryViewMode,
                                            sortOption = librarySortOption,
                                            tabDisplayPreferences = tabDisplayPreferences,
                                            fallbackArtistToAlbumArt = customizationSettings.fallbackArtistToAlbumArt,
                                            selectedAlbumId = selectedAlbumId,
                                            selectedArtistId = selectedArtistId,
                                            selectedGenreName = selectedGenreName,
                                            selectedFolderPath = selectedFolderPath,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            onSelectTab = viewModel::selectLibraryFilterTab,
                                            onSetViewMode = viewModel::setLibraryViewMode,
                                            onSetSortOption = viewModel::setLibrarySortOption,
                                            onToggleSortDirection = { viewModel.toggleCategorySortDirection(libraryFilterTab.name) },
                                            onSetGridColumns = { cols -> viewModel.setCategoryGridColumns(libraryFilterTab.name, cols) },
                                            onOpenAlbum = viewModel::openAlbumDetail,
                                            onOpenArtist = viewModel::openArtistDetail,
                                            onOpenGenre = viewModel::openGenreDetail,
                                            onOpenFolder = viewModel::openFolderDetail,
                                            onPlayAlbum = viewModel.playback::playAlbum,
                                            onPlayTrack = viewModel.playback::playTrack,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onPlayPlaylist = viewModel.playback::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            downloadsMap = downloadsMap,
                                            onDownloadTrack = viewModel::downloadTrackForOffline,
                                            onDownloadAlbum = viewModel::downloadAlbumForOffline,
                                            onRemoveDownload = viewModel::removeOfflineDownload,
                                            onPlayTrackNext = viewModel.playback::playTrackNext,
                                            onAddTrackToQueue = viewModel.playback::addTrackToQueue
                                        )
                                    }

                                    PrimaryDestination.ARTISTS -> {
                                        val artistsKey = LibraryFilterTab.ARTISTS.name
                                        LibraryCollectionScreen(
                                            serverName = activeServer.name,
                                            partialAlbums = if (libraryFilters.downloadedOnly) offlineSummary.partialAlbums else emptyMap(),
                                            albums = albums,
                                            artists = artists,
                                            tracks = tracks,
                                            playlists = playlists,
                                            genres = genres,
                                            folders = folders,
                                            activeTab = LibraryFilterTab.ARTISTS,
                                            scrollToTopSignal = scrollToTopSignal,
                                            visibleTabs = listOf(LibraryFilterTab.ARTISTS),
                                            viewMode = viewModel.getTabPreference(artistsKey).viewMode,
                                            sortOption = viewModel.getTabPreference(artistsKey).sortOption,
                                            tabDisplayPreferences = tabDisplayPreferences,
                                            fallbackArtistToAlbumArt = customizationSettings.fallbackArtistToAlbumArt,
                                            selectedAlbumId = selectedAlbumId,
                                            selectedArtistId = selectedArtistId,
                                            selectedGenreName = null,
                                            selectedFolderPath = null,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            onSelectTab = {},
                                            onSetViewMode = { viewModel.setCategoryViewMode(artistsKey, it) },
                                            onSetSortOption = { viewModel.setCategorySortOption(artistsKey, it) },
                                            onToggleSortDirection = { viewModel.toggleCategorySortDirection(artistsKey) },
                                            onSetGridColumns = { cols -> viewModel.setCategoryGridColumns(artistsKey, cols) },
                                            onOpenAlbum = viewModel::openAlbumDetail,
                                            onOpenArtist = viewModel::openArtistDetail,
                                            onOpenGenre = viewModel::openGenreDetail,
                                            onOpenFolder = viewModel::openFolderDetail,
                                            onPlayAlbum = viewModel.playback::playAlbum,
                                            onPlayTrack = viewModel.playback::playTrack,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onPlayPlaylist = viewModel.playback::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            downloadsMap = downloadsMap,
                                            onDownloadTrack = viewModel::downloadTrackForOffline,
                                            onDownloadAlbum = viewModel::downloadAlbumForOffline,
                                            onRemoveDownload = viewModel::removeOfflineDownload,
                                            onPlayTrackNext = viewModel.playback::playTrackNext,
                                            onAddTrackToQueue = viewModel.playback::addTrackToQueue
                                        )
                                    }

                                    PrimaryDestination.PLAYLISTS -> {
                                        PlaylistsCollectionScreen(
                                            playlists = playlists,
                                            tracks = tracks,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            onPlayPlaylist = viewModel.playback::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            serverName = activeServer.name,
                                            isRefreshing = isRefreshingPlaylists,
                                            onRefresh = viewModel::refreshServerPlaylists.takeIf { activeServer.source == SourceType.SUBSONIC && !activeServer.isDemoServer },
                                            selectedPlaylistId = selectedPlaylistId,
                                            onOpenPlaylist = viewModel::openPlaylistDetail,
                                            viewMode = tabDisplayPreferences["PLAYLISTS"]?.viewMode ?: LibraryViewMode.LIST,
                                            onSetViewMode = { viewModel.setCategoryViewMode("PLAYLISTS", it) },
                                            scrollToTopSignal = scrollToTopSignal
                                        )
                                    }

                                    PrimaryDestination.FAVOURITES -> {
                                        FavouritesCollectionScreen(
                                            tracks = tracks,
                                            albums = albums,
                                            activeFilter = favouritesFilterTab,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            selectedAlbumId = selectedAlbumId,
                                            onSelectFilter = viewModel::selectFavouritesFilterTab,
                                            onPlayTrack = { trk, ctx -> viewModel.playback.playTrack(trk, ctx) },
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onOpenAlbum = { albumId ->
                                                viewModel.openAlbumDetail(albumId)
                                            },
                                            onCloseAlbumDetail = {
                                                viewModel.openAlbumDetail(null)
                                            },
                                            onPlayAlbum = viewModel.playback::playAlbum,
                                            onSmartShuffleTracks = { favTracks ->
                                                viewModel.playback.startSmartShuffleSession(favTracks)
                                            },
                                            viewMode = tabDisplayPreferences["FAVOURITES"]?.viewMode ?: LibraryViewMode.LIST,
                                            onSetViewMode = { viewModel.setCategoryViewMode("FAVOURITES", it) },
                                            sortPreference = tabDisplayPreferences["FAVOURITES"] ?: viewModel.getTabPreference("FAVOURITES"),
                                            onSetSortOption = { viewModel.setCategorySortOption("FAVOURITES", it) },
                                            onToggleSortDirection = { viewModel.toggleCategorySortDirection("FAVOURITES") },
                                            scrollToTopSignal = scrollToTopSignal
                                        )
                                    }

                                    PrimaryDestination.SETTINGS -> {
                                        SettingsCustomizationScreen(
                                            settings = customizationSettings,
                                            speedPitchState = speedPitchState,
                                            reverbState = reverbState,
                                            sleepTimerState = sleepTimerState,
                                            activeEqPreset = eqPreset,
                                            servers = servers,
                                            activeServer = activeServer,
                                            pingDiagnostic = pingDiagnostic,
                                            isSyncing = isSyncing,
                                            onSetAppIconVariant = viewModel::setAppIconVariant,
                                            onOpenAppIconSettings = { viewModel.setAppIconSettingsOpen(true) },
                                            onSetDockStyle = viewModel.settings::setDockStyle,
                                            onSetHideMiniPlayerOnScroll = viewModel.settings::setHideMiniPlayerOnScroll,
                                            onSetThemeMode = viewModel::setThemeMode,
                                            onSetUseMaterialYou = viewModel.settings::setUseMaterialYouDynamicColor,
                                            onSetHarmonizeArtwork = viewModel.settings::setHarmonizePlayerWithArtwork,
                                            onSetUnifiedMaterialYouColor = viewModel::setUnifiedMaterialYouColor,
                                            onSetCuratedPalette = viewModel::setCuratedColorPalette,
                                            onSetCornerStylePreset = viewModel.settings::setCornerStylePreset,
                                            onSetInterfaceStyle = viewModel::setInterfaceStyle,
                                            onSetHeaderFont = viewModel.settings::setHeaderFont,
                                            onSetBodyFont = viewModel.settings::setBodyFont,
                                            onSetArtworkCut = viewModel.settings::setArtworkCut,
                                            onSetHomeSections = viewModel.settings::setHomeSections,
                                            onResetHomeSections = viewModel.settings::resetHomeSections,
                                            onSetPlayerBackgroundStyle = viewModel::setPlayerBackgroundStyle,
                                            onSetAnimatedSquiggly = viewModel.settings::setAnimatedSquigglyWave,
                                            onSetHapticFeedback = viewModel.settings::setHapticFeedbackEnabled,
                                            onSetHighRefreshRate120Hz = viewModel::setHighRefreshRate120Hz,
                                            shuffleMode = shuffleMode,
                                            onSetShuffleMode = viewModel.playback::setShuffleMode,
                                            onSetSmartShuffleProfile = viewModel::setSmartShuffleProfile,
                                            onSelectReverbPreset = viewModel::selectReverbPreset,
                                            onSetCrossfadeSeconds = viewModel.settings::setCrossfadeSeconds,
                                            onSetGapless = viewModel.settings::setGaplessPlayback,
                                            onSetAutoplay = viewModel.settings::setAutoplaySimilar,
                                            onSetFindLyricsOnline = viewModel.settings::setFindLyricsOnline,
                                            onSetDolbyPassthrough = viewModel.settings::setDolbyPassthrough,
                                            onSetSongAnalysis = viewModel::setSongAnalysis,
                                            songAnalysisStatus = "${songFeatures.size} of ${downloadsMap.values.count { it.isCompleted } + allServerTracks.count { it.isOnDevice }} songs on this phone analysed",
                                            onSetReplayGain = viewModel.settings::setReplayGainMode,
                                            onSetAutoScrobble = viewModel.settings::setAutoScrobble,
                                            onSetPauseOnHeadphoneDisconnect = viewModel::setPauseOnHeadphoneDisconnect,
                                            onSetFallbackArtistToAlbumArt = viewModel.settings::setFallbackArtistToAlbumArt,
                                            onSetStreamQuality = viewModel::setStreamQuality,
                                            onSetDownloadSpeedLimit = viewModel::setDownloadSpeedLimit,
                                            onSetReadAheadBuffer = viewModel.settings::setReadAheadBuffer,
                                            onSetWidgetLayoutMode = viewModel::setWidgetLayoutMode,
                                            onSetWidgetThemeMode = viewModel::setWidgetThemeMode,
                                            onSetWidgetShowArtwork = viewModel.settings::setWidgetShowArtwork,
                                            onSetWidgetShowFavoriteButton = viewModel.settings::setWidgetShowFavoriteButton,
                                            onSetWidgetShowLyrics = viewModel.settings::setWidgetShowLyrics,
                                            onSetCheckForUpdates = viewModel::setCheckForUpdates,
                                            availableUpdate = availableUpdate,
                                            updateCheckStatus = updateCheckStatus,
                                            onCheckForUpdatesNow = viewModel::checkForUpdatesNow,
                                            onSetUpdateNotifications = viewModel::setUpdateNotifications,
                                            onSetUpdatePrereleases = viewModel::setUpdatePrereleases,
                                            onViewUpdate = viewModel::showUpdateDetails,
                                            onRestoreDefaults = viewModel::restoreDefaultCustomizationSettings,
                                            onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                            onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                                            onOpenSleepTimerSheet = { viewModel.setSleepTimerSheetOpen(true) },
                                            onStartSleepTimerMinutes = { mins -> viewModel.playback.startSleepTimerDuration(mins) },
                                            onStartSleepTimerEndOfTrack = viewModel.playback::startSleepTimerEndOfTrack,
                                            onCancelSleepTimer = viewModel.playback::cancelSleepTimer,
                                            onSelectEqPreset = viewModel::selectEqualizerPreset,
                                            offlineStorageSummary = offlineStorageSummary,
                                            onClearOfflineDownloads = viewModel::clearAllOfflineDownloads,
                                            onManageDownloads = { showDownloads = true },
                                            onClearTempAudioCache = viewModel::clearTemporaryAudioCache,
                                            onBack = { viewModel.selectDestination(PrimaryDestination.HOME) },
                                            onActivateServer = viewModel::activateExistingServer,
                                            onTestAndSaveServer = viewModel::testAndSaveServer,
                                            musicFolders = MusicFolders(
                                                folders = localFolders,
                                                scan = folderScan,
                                                rescanOnLaunch = customizationSettings.rescanFoldersOnLaunch,
                                                onAdd = viewModel::addMusicFolder,
                                                onRemove = viewModel::removeMusicFolder,
                                                onRescan = viewModel::rescanMusicFolder,
                                                onRescanAll = viewModel::rescanAllMusicFolders,
                                                onCancelScan = viewModel::cancelFolderScan,
                                                onSetRescanOnLaunch = viewModel::setRescanFoldersOnLaunch,
                                                backgroundScanHours = customizationSettings.librarySyncHours,
                                                onSetBackgroundScanHours = viewModel::setLibrarySyncHours
                                            )
                                        )
                                    }
                                }
                            }
                            }

                            if (isExpandedScreen && miniPlayerTrack != null) {
                                PlaybackPosition(viewModel) { positionMs, durationMs ->
                                    FloatingExpressiveMiniPlayer(
                                        track = miniPlayerTrack,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        onTogglePlay = viewModel.playback::togglePlayPause,
                                        onSkipNext = viewModel.playback::skipNext,
                                        onExpand = { viewModel.setPlayerExpanded(true) },
                                        sheet = playerSheet
                                    )
                                }
                            }
                        }
                    }
                }

                // Pull-to-sync indicator, dropping in just under the top bar
                PullSyncIndicator(
                    pullProgress = homePullProgress,
                    // Once syncing, progress lives in the banner; floating here it covered the banner.
                    isSyncing = false,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 84.dp)
                )

                // The Your Mix zoom again, above the app bars: Home's button is clipped to its list,
                // which left the bars (and, once they moved, the shelf behind the dock) showing at
                // the end. Same centre, curve and progress, so it is the same circle, swallowing the
                // top bar, dock and rail as it reaches them. Drawn from 0.25, when the play arrow
                // inside the button has faded and the two are indistinguishable.
                val heroZoomColor = MaterialTheme.colorScheme.primaryContainer
                var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
                ) {
                    val t = heroLaunch.progress
                    val centre = heroLaunch.centre
                    if (t < 0.25f || centre.isUnspecified) return@Canvas
                    drawCircle(
                        color = heroZoomColor,
                        radius = HeroButtonRadius.toPx() * heroZoom(t, heroLaunch.maxZoom),
                        center = centre - overlayOrigin
                    )
                }

                // The full player, grown out of the mini player (or the Your Mix zoom) as far as
                // [playerSheet] says.
                if (playerShown) Box(Modifier.fillMaxSize().then(playerSheet.containerModifier())) {
                    val activeTrack = currentTrack
                    // Its colours follow each new cover with a fade; the rest of the app switches at once.
                    if (activeTrack != null) FadingColors {
                        PlaybackPosition(viewModel) { positionMs, durationMs ->
                            NowPlayingStudioOverlay(
                                sheet = playerSheet,
                                audioQuality = audioQuality.takeIf { customizationSettings.showTechnicalFormatBadge },
                                spatialAudio = viewModel::spatialAudioActive,
                                dolbyDecoders = viewModel.dolbyDecoders,
                                track = activeTrack,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                positionMs = positionMs,
                                audiblePositionMs = viewModel::audiblePositionMs,
                                durationMs = durationMs,
                                speedPitchState = speedPitchState,
                                reverbState = reverbState,
                                sleepTimerState = sleepTimerState,
                                shuffleEnabled = shuffleEnabled,
                                shuffleMode = shuffleMode,
                                smartShuffleProfile = customizationSettings.smartShuffleProfile,
                                repeatOneEnabled = repeatOneEnabled,
                                animatedWaveEnabled = customizationSettings.animatedSquigglyWave,
                                showRemainingTime = customizationSettings.showRemainingTime,
                                showPlayerStarButton = customizationSettings.showPlayerStarButton,
                                unifiedMaterialYouColor = customizationSettings.unifiedMaterialYouColor,
                                backgroundAnimationStyle = customizationSettings.playerBackgroundStyle,
                                activeTab = nowPlayingTab,
                                lyrics = currentLyrics,
                                lyricsResult = currentLyricsResult,
                                lyricsSource = lyricsSource,
                                lyricsSearch = lyricsSearch,
                                onSearchLyrics = viewModel::searchLyrics,
                                onChooseLyrics = viewModel::chooseLyrics,
                                onUseServerLyrics = viewModel::useServerLyrics,
                                onRestoreLyrics = viewModel::restorePreviousLyrics,
                                download = if (activeTrack.isOnDevice) null else remember(activeTrack.id, downloadsMap[activeTrack.id]) {
                                    PlayerDownload(
                                        state = downloadsMap[activeTrack.id],
                                        onDownload = { viewModel.downloadTrackForOffline(activeTrack) },
                                        onCancel = { viewModel.cancelOfflineDownload(activeTrack.id) },
                                        onResume = { viewModel.resumeOfflineDownloads(listOf(activeTrack.id)) },
                                        onRemove = { viewModel.removeOfflineDownload(activeTrack.id) }
                                    )
                                },
                                queue = playbackQueue,
                                eqPreset = eqPreset,
                                onCollapse = { viewModel.setPlayerExpanded(false) },
                                onSelectTab = viewModel::selectNowPlayingTab,
                                onTogglePlay = viewModel.playback::togglePlayPause,
                                onSkipPrevious = viewModel.playback::skipPrevious,
                                onSwipePrevious = { viewModel.playback.skipPrevious(restartIfPlayed = false) },
                                onSkipNext = viewModel.playback::skipNext,
                                onSeek = viewModel::seekTo,
                                onToggleShuffle = viewModel.playback::toggleShuffle,
                                onToggleRepeatOne = viewModel.playback::toggleRepeatOne,
                                onToggleStar = { viewModel.toggleStarTrack(activeTrack) },
                                onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                                onResetSpeedPitch = {
                                    viewModel.resetSpeedAndPitch()
                                    viewModel.resetReverbToDry()
                                },
                                showSpeedResetHint = showSpeedResetHint,
                                onSpeedResetHintShown = viewModel::markSpeedResetHintShown,
                                onOpenSleepTimerSheet = { viewModel.setSleepTimerSheetOpen(true) },
                                onOpenArtist = { viewModel.openArtistFromTrack(activeTrack) },
                                onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                onPlayQueueTrack = { viewModel.playback.playFromQueue(it) },
                                onRemoveQueueTrack = viewModel.playback::removeTrackFromQueue,
                                onMoveQueueTrack = viewModel.playback::moveQueueTrack
                            )
                        }
                    }
                }

                // Speed, Dynamic Pitch & Live Studio Reverb Modal Sheet
                AnimatedVisibility(
                    visible = isSpeedPitchOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut()
                ) {
                    SpeedPitchStudioModal(
                        state = speedPitchState,
                        reverbState = reverbState,
                        tempo = currentTempo,
                        onSetSpeed = viewModel::setPlaybackSpeed,
                        onSetPitchMode = viewModel::setPitchCorrectionMode,
                        onSetIndependentSemitones = viewModel::setIndependentSemitones,
                        onSetIndependentPitchRatio = viewModel::setIndependentPitchRatio,
                        onSetReverbEnabled = viewModel::setReverbEnabled,
                        onSelectReverbPreset = viewModel::selectReverbPreset,
                        onSetReverbWetDryMix = viewModel::setReverbWetDryMix,
                        onSetReverbRoomSize = viewModel::setReverbRoomSize,
                        onSetReverbDecay = viewModel::setReverbDecay,
                        onSetReverbDamping = viewModel::setReverbDamping,
                        onResetReverb = viewModel::resetReverbToDry,
                        onReset = {
                            viewModel.resetSpeedAndPitch()
                            viewModel.resetReverbToDry()
                        },
                        onClose = { viewModel.setSpeedPitchSheetOpen(false) }
                    )
                }

                // Sleep Timer Modal Sheet (Accessible via Now Playing screen)
                AnimatedVisibility(
                    visible = isSleepTimerOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut()
                ) {
                    SleepTimerModal(
                        state = sleepTimerState,
                        currentTrackTitle = currentTrack?.title ?: "Current Track",
                        onStartDuration = viewModel.playback::startSleepTimerDuration,
                        onStartEndOfTrack = viewModel.playback::startSleepTimerEndOfTrack,
                        onAddFiveMinutes = { viewModel.playback.addSleepTimerMinutes(5) },
                        onUpdateOptions = viewModel.playback::updateSleepTimerOptions,
                        onCancel = viewModel.playback::cancelSleepTimer,
                        onClose = { viewModel.setSleepTimerSheetOpen(false) }
                    )
                }

                // Equalizer & Bitrate-Performance Modal Sheet
                AnimatedVisibility(
                    visible = isSonicLabOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut()
                ) {
                    val onMobileData = remember { viewModel.onMobileData() }
                    SonicLabModalOverlay(
                        moodMixes = homeShelves.mixes.filter { it.key.startsWith("mood:") },
                        songAnalysisOn = customizationSettings.songAnalysis,
                        activePreset = eqPreset,
                        customBandsDb = customEqBands,
                        streamQuality = customizationSettings.streamQuality(onMobileData),
                        onMobileData = onMobileData,
                        onSetStreamQuality = { viewModel.setStreamQuality(onMobileData, it) },
                        onSelectPreset = viewModel::selectEqualizerPreset,
                        onUpdateBand = viewModel::updateCustomEqBand,
                        onPlayMix = viewModel::playMix,
                        onEnableSongAnalysis = { viewModel.setSongAnalysis(true) },
                        onClose = { viewModel.setSonicLabSheetOpen(false) }
                    )
                }

                if (selectionSheetOpen && trackSelection.active) {
                    TrackOptionsSheet(
                        tracks = trackSelection.tracks,
                        downloads = downloadsMap,
                        onSelect = null,
                        onDismiss = {
                            selectionSheetOpen = false
                            trackSelection.clear()
                        }
                    )
                }

                if (filterSheetOpen) {
                    LibraryFilterSheet(
                        filters = libraryFilters,
                        offline = offlineSummary,
                        serverName = activeServer.name,
                        trackCount = allServerTracks.size,
                        isSyncing = isSyncing,
                        onChange = viewModel::setLibraryFilters,
                        onSync = { viewModel.triggerQuickSync() },
                        onSwitchServer = {
                            filterSheetOpen = false
                            viewModel.setUserServerSheetOpen(true)
                        },
                        onOpenSettings = {
                            filterSheetOpen = false
                            viewModel.selectDestination(PrimaryDestination.SETTINGS)
                        },
                        onOpenDownloads = {
                            filterSheetOpen = false
                            showDownloads = true
                        },
                        onDismiss = { filterSheetOpen = false }
                    )
                }

                // Server information & sync modal (from the filter sheet's "Switch server")
                AnimatedVisibility(
                    visible = isUserServerOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut()
                ) {
                    UserServerInfoModal(
                        activeServer = activeServer,
                        streamProfile = customizationSettings.streamQuality(viewModel.onMobileData()).badge,
                        servers = servers,
                        pingDiagnostic = pingDiagnostic,
                        isSyncing = isSyncing,
                        onQuickSync = {
                            viewModel.triggerQuickSync()
                        },
                        onActivateServer = viewModel::activateExistingServer,
                        onOpenOnboarding = {
                            viewModel.setUserServerSheetOpen(false)
                            viewModel.setShowServerOnboarding(true)
                        },
                        onOpenSettings = {
                            viewModel.setUserServerSheetOpen(false)
                            viewModel.selectDestination(PrimaryDestination.SETTINGS)
                        },
                        onClose = { viewModel.setUserServerSheetOpen(false) }
                    )
                }

                // Full-screen App Icon Settings & Preview Gallery Overlay
                AnimatedVisibility(
                    visible = isAppIconSettingsOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it / 4 },
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
                ) {
                    AppIconSettingsScreen(
                        selectedVariant = customizationSettings.appIconVariant,
                        onSelectVariant = viewModel::setAppIconVariant,
                        iconFollowsTheme = customizationSettings.iconFollowsTheme,
                        onSetIconFollowsTheme = viewModel::setIconFollowsTheme,
                        onClose = { viewModel.setAppIconSettingsOpen(false) }
                    )
                }

                // First launch after a major/minor update (1.1, 2.0): what changed.
                whatsNew?.takeIf { !showServerOnboarding }?.let { notes ->
                    WhatsNewDialog(notes, onDismiss = viewModel::dismissWhatsNew)
                }

                // Once the library has mixes, and after setup and any "What's new".
                val homeIntroSeen by viewModel.homeIntroSeen.collectAsStateWithLifecycle()
                if (!homeIntroSeen && !showServerOnboarding && whatsNew == null && homeShelves.mixes.isNotEmpty() && !isPlayerExpanded) {
                    HomeIntro(
                        mixes = homeShelves.mixes,
                        autoplay = customizationSettings.autoplaySimilar,
                        onAutoplayChange = viewModel.settings::setAutoplaySimilar,
                        songAnalysis = customizationSettings.songAnalysis,
                        onSongAnalysisChange = viewModel::setSongAnalysis,
                        onDone = viewModel::dismissHomeIntro
                    )
                }

                if (showDownloads) {
                    DownloadsSheet(
                        downloads = downloadsMap.values,
                        order = downloadComparator,
                        trackFor = tracksById::get,
                        onCancel = viewModel::cancelOfflineDownloads,
                        onPause = viewModel::pauseOfflineDownloads,
                        onResume = viewModel::resumeOfflineDownloads,
                        onRetry = viewModel::retryOfflineDownloads,
                        onRemove = viewModel::removeOfflineDownloads,
                        onOpenTrack = { trackId ->
                            showDownloads = false
                            downloadsSeenAt = System.currentTimeMillis()
                            viewModel.openAndPlayDownloadedTrack(trackId)
                        },
                        onDismiss = {
                            showDownloads = false
                            downloadsSeenAt = System.currentTimeMillis()
                        }
                    )
                }

                availableUpdate?.takeIf { updateDetailsOpen }?.let { update ->
                    UpdateDetailsSheet(
                        update,
                        installedVersion = BuildConfig.VERSION_NAME,
                        source = viewModel.installSource,
                        install = updateInstall,
                        onUpdate = viewModel::startUpdate,
                        onAllowInstalls = { runCatching { context.startActivity(AppUpdateInstaller.allowInstallsIntent(context)) } },
                        onInstall = { apk -> runCatching { context.startActivity(AppUpdateInstaller.installIntent(context, apk)) } },
                        onCancel = viewModel::cancelUpdate,
                        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
                        onDismiss = viewModel::closeUpdateDetails
                    )
                }
                // Downloaded and allowed: open Android's installer, which asks to confirm.
                LaunchedEffect(updateInstall) {
                    (updateInstall as? UpdateInstallState.Ready)?.let { ready ->
                        runCatching { context.startActivity(AppUpdateInstaller.installIntent(context, ready.apk)) }
                    }
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumeUpdateInstall() }

                // Application Introduction / Server Details Onboarding Modal
                // Leaving onboarding zooms in: the screen grows towards you and fades while Home
                // scales up into place behind it.
                AnimatedVisibility(
                    visible = showServerOnboarding,
                    enter = fadeIn() + scaleIn(initialScale = 0.96f),
                    exit = fadeOut(tween(520, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 1.18f, animationSpec = tween(620, easing = FastOutSlowInEasing))
                ) {
                    ServerOnboardingIntroModal(
                        activeServer = activeServer,
                        onConnectServer = { name, url, user, pass, tokenAuth, type ->
                            viewModel.connectServerDuringOnboarding(
                                name = name,
                                baseUrl = url,
                                username = user,
                                passwordOrToken = pass,
                                useTokenAuth = tokenAuth,
                                sourceType = type
                            )
                        },
                        onContinueWithDemo = {
                            viewModel.completeServerOnboarding()
                        },
                        syncState = onboardingSync
                    )
                }

                if (showBatteryPrompt) BackgroundPlaybackPrompt(onDismiss = { showBatteryPrompt = false })

                // Process-Once Animated Launch Introduction Overlay (Distinct from Server Onboarding)
                val reducedMotion = LocalReducedMotion.current
                var showLaunchIntro by remember {
                    mutableStateOf(OndaluneLaunchIntroTracker.shouldPlayIntroOnColdStart(reducedMotion))
                }
                if (showLaunchIntro) {
                    OndaluneAnimatedLaunchIntroOverlay(
                        variant = customizationSettings.appIconVariant,
                        onFinished = {
                            OndaluneLaunchIntroTracker.markCompleted()
                            showLaunchIntro = false
                        }
                    )
                }
            }
        }
        }
    }
}

/**
 * Launch intro, played once per cold start and always to the end (~2 s): the icon tile springs
 * in while its background slides into place, the moon and waveform pop onto it, rings ripple out,
 * the wordmark rises letter by letter, then everything lifts away. It starts on the splash
 * colour so the system splash dissolves straight into it.
 */
@Composable
fun OndaluneAnimatedLaunchIntroOverlay(
    variant: AppIconVariant,
    onFinished: () -> Unit
) {
    // The system splash leaves the moon at the centre of the screen, a little larger than the
    // emblem draws it, so the intro starts there and then settles, rises and gains its tile.
    val settle = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val waves = remember { Animatable(-1f) }
    val rings = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val accent = remember(variant) { parseHexColor(variant.accentHex) }
    val brandName = "Ondalune"
    val restLift = 60.dp

    LaunchedEffect(Unit) {
        // Start the clock only once frames are actually being drawn, so a busy cold start
        // can't skip the opening of the animation.
        repeat(2) { withFrameNanos { } }
        // The splash covers this overlay until it fades: start once it's gone, so its moon
        // hands over to ours in place. Without a splash (opened from a notification) the
        // listener never runs, hence the cap.
        withTimeoutOrNull(1_200L) { OndaluneLaunchIntroTracker.splashGone.first { it } }
        coroutineScope {
            launch { settle.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
            launch { waves.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            launch {
                delay(300L)
                rise.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }
            launch {
                delay(420L)
                rings.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(560L)
                word.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }
        }
        delay(380L)
        exit.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color(0xFF0B0B0C))
            .testTag("ondalune_launch_intro_overlay"),
        contentAlignment = Alignment.Center
    ) {
        val textTop = maxHeight / 2 - restLift + 64.dp + 22.dp
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f - rise.value * restLift.toPx()
            val unit = minOf(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.32f * settle.value.coerceIn(0f, 1f)), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = unit * 0.7f
                ),
                radius = unit * 0.7f,
                center = Offset(cx, cy)
            )
            // Two ripples leaving the icon, like sound from a speaker.
            for (i in 0..1) {
                val t = (rings.value - i * 0.28f).coerceIn(0f, 1f)
                if (t > 0f) drawCircle(
                    color = accent.copy(alpha = 0.45f * (1f - t)),
                    radius = unit * (0.16f + 0.34f * t),
                    center = Offset(cx, cy),
                    style = Stroke(width = (3.dp.toPx() * (1f - t)).coerceAtLeast(0.5f))
                )
            }
        }

        val exitLift = Modifier.graphicsLayer {
            val lift = exit.value
            translationY = -lift * 24.dp.toPx()
            scaleX = 1f + lift * 0.06f
            scaleY = scaleX
        }
        OndaluneBrandEmblem(
            size = 128.dp,
            variant = variant,
            frameShape = AppIconPreviewFrameShape.SQUIRCLE,
            backgroundShift = waves.value,
            backgroundAlpha = settle.value.coerceIn(0f, 1f),
            modifier = exitLift.graphicsLayer {
                val scale = 1.12f - 0.12f * settle.value
                scaleX = scale
                scaleY = scale
                translationY = -rise.value * restLift.toPx()
            }
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = textTop)
                .then(exitLift)
        ) {
            Row {
                brandName.forEachIndexed { i, ch ->
                    val local = (word.value * (brandName.length + 3) - i).coerceIn(0f, 1f)
                    Text(
                        text = ch.toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 3.sp
                        ),
                        color = Color(0xFFEDEDF0),
                        modifier = Modifier.graphicsLayer {
                            alpha = local
                            translationY = (1f - local) * 14.dp.toPx()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your music, by moonlight",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.graphicsLayer { alpha = ((word.value - 0.55f) / 0.45f).coerceIn(0f, 1f) }
            )
        }
    }
}

private fun destinationIcon(dest: PrimaryDestination, selected: Boolean): ImageVector {
    return when (dest) {
        PrimaryDestination.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        PrimaryDestination.LIBRARY -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
        PrimaryDestination.PLAYLISTS -> if (selected) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic
        PrimaryDestination.FAVOURITES -> if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        PrimaryDestination.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
        PrimaryDestination.ARTISTS -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    }
}

/**
 * Reads the 15Hz playback position in its own small recomposition scope, so position ticks only
 * re-run the progress UI that needs them instead of the whole app shell.
 */
@Composable
private fun PlaybackPosition(
    viewModel: OndaluneViewModel,
    content: @Composable (positionMs: Long, durationMs: Long) -> Unit
) {
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    content(positionMs, durationMs)
}

/** What the shell's content area shows: a tab plus whichever detail screen is open in it. */
private data class ShellNavState(
    val destination: PrimaryDestination,
    val albumId: String?,
    val artistId: String?,
    val genreName: String?,
    val folderPath: String?,
    val shelf: HomeShelfKind?,
    val playlistId: String?
) {
    val route: String get() = destination.route
    /** One key per page: the tab, plus the detail shown on top of it. */
    val key: String get() = "$route/$detailKey"
    val detailKey: String? get() = albumId?.let { "album:$it" } ?: artistId?.let { "artist:$it" }
        ?: genreName?.let { "genre:$it" } ?: folderPath?.let { "folder:$it" } ?: playlistId?.let { "playlist:$it" }
        ?: shelf?.let { "shelf:${it.name}" }

    /** Navigation depth inside the tab (shelf page, then a detail on top), for slide direction. */
    val depth: Int get() = (if (shelf != null) 1 else 0) +
        (if (albumId != null || artistId != null || genreName != null || folderPath != null || playlistId != null) 1 else 0)
}

/**
 * Keeps this list to the detail pages Back can still reach from [page], and drops the saved state of
 * every page left behind. Tab pages are never in it: they keep their place across tab switches.
 */
private fun MutableList<String>.follow(page: ShellNavState, states: SaveableStateHolder) {
    val keep = if (page.detailKey == null) 0 else indexOf(page.key).let { if (it < 0) size else it + 1 }
    subList(keep, size).apply { forEach(states::removeState); clear() }
    if (page.detailKey != null && lastOrNull() != page.key) add(page.key)
}

/** Material fade through: outgoing fades fast, incoming fades and settles up from 92% scale. */
private fun fadeThrough(): ContentTransform =
    (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(210, delayMillis = 90, easing = FastOutSlowInEasing), initialScale = 0.92f))
        .togetherWith(fadeOut(tween(90, easing = FastOutLinearInEasing)))

/** Material shared axis X: both screens slide together along the axis of travel and cross-fade. */
private fun sharedAxisX(forward: Boolean): ContentTransform {
    val slide = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val dir = if (forward) 1 else -1
    return (slideInHorizontally(slide) { dir * it / 8 } + fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)))
        .togetherWith(slideOutHorizontally(slide) { -dir * it / 8 } + fadeOut(tween(90, easing = FastOutLinearInEasing)))
}
