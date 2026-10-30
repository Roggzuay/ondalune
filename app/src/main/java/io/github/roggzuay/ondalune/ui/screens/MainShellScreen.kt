@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

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
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.theme.LocalArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.rememberArtworkPalette
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.ui.components.PullSyncIndicator
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.ExpressiveNavItem
import io.github.roggzuay.ondalune.ui.components.ExpressiveNavigationDock
import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.components.OndaluneBrandEmblem
import io.github.roggzuay.ondalune.ui.components.rememberHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.OndaluneExpressiveTheme
import io.github.roggzuay.ondalune.ui.theme.parseHexColor
import io.github.roggzuay.ondalune.ui.viewmodel.PrimaryDestination
import io.github.roggzuay.ondalune.ui.viewmodel.LibraryFilterTab
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelfKind
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Process-scoped tracker ensuring the Ondalune Animated Launch Intro runs at most once per cold
 * process start and never repeats on `onResume` or configuration changes.
 */
object OndaluneLaunchIntroTracker {
    @Volatile
    private var hasShownInProcess: Boolean = false

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

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playbackQueue by viewModel.playbackQueue.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentLyricsResult by viewModel.currentLyricsResult.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val reverbProcessingMode by viewModel.reverbProcessingMode.collectAsStateWithLifecycle()
    val isPlayingOfflineFile by viewModel.isPlayingOfflineFile.collectAsStateWithLifecycle()
    val downloadsMap by viewModel.downloadsMap.collectAsStateWithLifecycle()
    val offlineStorageSummary by viewModel.offlineStorageSummary.collectAsStateWithLifecycle()

    val speedPitchState by viewModel.speedPitchState.collectAsStateWithLifecycle()
    val reverbState by viewModel.reverbState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val tabDisplayPreferences by viewModel.tabDisplayPreferences.collectAsStateWithLifecycle()

    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    var playerZoomEntry by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayerExpanded) { if (!isPlayerExpanded) playerZoomEntry = false }
    val isSonicLabOpen by viewModel.isSonicLabSheetOpen.collectAsStateWithLifecycle()
    val isSpeedPitchOpen by viewModel.isSpeedPitchSheetOpen.collectAsStateWithLifecycle()
    val isSleepTimerOpen by viewModel.isSleepTimerSheetOpen.collectAsStateWithLifecycle()
    val isUserServerOpen by viewModel.isUserServerSheetOpen.collectAsStateWithLifecycle()
    val isAppIconSettingsOpen by viewModel.isAppIconSettingsOpen.collectAsStateWithLifecycle()
    val showServerOnboarding by viewModel.showServerOnboarding.collectAsStateWithLifecycle()
    val isCutoutSyncActive by viewModel.isCutoutSyncActive.collectAsStateWithLifecycle()

    val nowPlayingTab by viewModel.nowPlayingTab.collectAsStateWithLifecycle()
    val artworkShapeMode by viewModel.artworkShapeMode.collectAsStateWithLifecycle()
    val shuffleMode by viewModel.shuffleMode.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatOneEnabled by viewModel.repeatOneEnabled.collectAsStateWithLifecycle()

    val eqPreset by viewModel.equalizerPreset.collectAsStateWithLifecycle()
    val customEqBands by viewModel.customEqBands.collectAsStateWithLifecycle()
    val customizationSettings by viewModel.customizationSettings.collectAsStateWithLifecycle()

    val selectedAlbumId by viewModel.selectedAlbumDetailId.collectAsStateWithLifecycle()
    val selectedArtistId by viewModel.selectedArtistDetailId.collectAsStateWithLifecycle()
    val selectedGenreName by viewModel.selectedGenreDetail.collectAsStateWithLifecycle()
    val selectedFolderPath by viewModel.selectedFolderDetail.collectAsStateWithLifecycle()
    val openHomeShelf by viewModel.openHomeShelf.collectAsStateWithLifecycle()
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
            override fun playNext(tracks: List<SubsonicTrack>) = viewModel.playTracksNext(tracks)
            override fun addToQueue(tracks: List<SubsonicTrack>) = viewModel.addTracksToQueue(tracks)
            override fun toggleFavourite(track: SubsonicTrack) = viewModel.toggleStarTrack(track)
            override fun setFavourite(tracks: List<SubsonicTrack>, favourite: Boolean) = viewModel.setTracksFavourite(tracks, favourite)
            override fun download(tracks: List<SubsonicTrack>) = viewModel.downloadTracks(tracks)
            override fun removeDownload(track: SubsonicTrack) = viewModel.removeOfflineDownload(track.id)
            override fun addToPlaylist(tracks: List<SubsonicTrack>, playlist: SubsonicPlaylist) = viewModel.addTracksToPlaylist(tracks, playlist)
            override fun createPlaylist(name: String, tracks: List<SubsonicTrack>) = viewModel.createPlaylist(name, "", tracks.map { it.id })
            override fun openAlbum(track: SubsonicTrack) = viewModel.openAlbumAnywhere(track.albumId)
            override fun openArtist(track: SubsonicTrack) = viewModel.openArtistFromTrack(track)
            override fun openArtist(artistId: String, name: String) = viewModel.openArtistByName(artistId, name)
        }
    }
    // Keep each download where it first appeared (queue order). The database orders rows by last
    // update, so sorting by it made the list reshuffle on every progress tick.
    val downloadOrder = remember { HashMap<String, Int>() }
    val activeDownloads = remember(downloadsMap) {
        val active = downloadsMap.values.filter {
            it.status == OfflineDownloadState.DOWNLOADING.name || it.status == OfflineDownloadState.QUEUED.name
        }
        active.filter { it.downloadKey !in downloadOrder }.sortedBy { it.updatedAtMs }
            .forEach { downloadOrder[it.downloadKey] = downloadOrder.size }
        active.sortedBy { downloadOrder[it.downloadKey] }
    }

    val anyModalOpen = isPlayerExpanded || isSonicLabOpen || isSpeedPitchOpen ||
        isSleepTimerOpen || isUserServerOpen || isAppIconSettingsOpen || showServerOnboarding
    val anySubDetailOpen = openHomeShelf != null || selectedAlbumId != null || selectedArtistId != null ||
        selectedGenreName != null || selectedFolderPath != null

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
        LocalDownloads provides downloadsMap
    ) {
        val artworkPalette = rememberArtworkPalette(currentTrack)
        CompositionLocalProvider(LocalArtworkPalette provides artworkPalette) {
        OndaluneExpressiveTheme(
            seedHex = artworkPalette.seedHex,
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
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        if (trackSelection.active) {
                            SelectionTopBar(selection = trackSelection, onMore = { selectionSheetOpen = true })
                        } else {
                        val greetingName = activeServer.username.takeIf { !activeServer.isDemoServer && it.isNotBlank() }
                            ?.replaceFirstChar { it.uppercase() } ?: "there"
                        OndaluneTopBar(
                            title = if (currentDestination == PrimaryDestination.HOME) "Hi $greetingName" else currentDestination.label,
                            subtitle = if (currentDestination == PrimaryDestination.HOME) greetingLine(viewModel.greetingIndex) else null,
                            searchQuery = searchQuery,
                            statusBanner = statusBanner,
                            activeDownloads = activeDownloads,
                            filtersActive = libraryFilters.active,
                            isSettingsActive = currentDestination == PrimaryDestination.SETTINGS,
                            bannerBusy = isCutoutSyncActive,
                            onQueryChange = viewModel::updateSearchQuery,
                            onOpenFilters = { filterSheetOpen = true },
                            onOpenSettings = { viewModel.selectDestination(PrimaryDestination.SETTINGS) },
                            onCancelDownload = viewModel::cancelOfflineDownload,
                            onDismissBanner = viewModel::dismissBanner
                        )
                        }
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
                                val activeTrack = currentTrack
                                if (activeTrack != null) {
                                    PlaybackPosition(viewModel) { positionMs, durationMs ->
                                        FloatingExpressiveMiniPlayer(
                                            track = activeTrack,
                                            hazeState = hazeState,
                                            isPlaying = isPlaying,
                                            isBuffering = isBuffering,
                                            positionMs = positionMs,
                                            durationMs = durationMs,
                                            unifiedMaterialYouColor = customizationSettings.unifiedMaterialYouColor,
                                            onTogglePlay = viewModel::togglePlayPause,
                                            onSkipNext = viewModel::skipNext,
                                            onSkipPrevious = { viewModel.skipPrevious(restartIfPlayed = false) },
                                            onExpand = { viewModel.setPlayerExpanded(true) }
                                        )
                                    }
                                }

                                ExpressiveNavigationDock(
                                    items = navItems,
                                    selectedId = currentDestination,
                                    dockStyle = customizationSettings.dockStyle,
                                    hazeState = hazeState,
                                    onSelect = { dest ->
                                        if (dest == currentDestination) viewModel.reselectDestination() else viewModel.selectDestination(dest)
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        // No bottom inset: lists scroll under the dock and mini player (every tab
                        // ends with room for them), which is what makes the Standard dock see-through.
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = innerPadding.calculateTopPadding())
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
                                        onClick = { viewModel.selectDestination(dest) },
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

                        Column(modifier = Modifier.weight(1f)) {
                            val reducedMotion = LocalReducedMotion.current
                            AnimatedContent(
                                targetState = ShellNavState(currentDestination, selectedAlbumId, selectedArtistId, selectedGenreName, selectedFolderPath, openHomeShelf),
                                contentKey = { it.route + "/" + it.detailKey },
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
                                // Render the snapshot this content was created for, so an outgoing
                                // detail screen keeps its content while it animates away.
                                val destination = nav.destination
                                val selectedAlbumId = nav.albumId
                                val selectedArtistId = nav.artistId
                                val selectedGenreName = nav.genreName
                                val selectedFolderPath = nav.folderPath
                                val openHomeShelf = nav.shelf
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
                                        onPlayTrack = { track, queueCtx -> viewModel.playTrack(track, queueCtx) },
                                        onToggleStarTrack = viewModel::toggleStarTrack,
                                        onOpenAlbum = viewModel::openAlbumDetail,
                                        onPlayAlbum = viewModel::playAlbum,
                                        onOpenArtist = viewModel::openArtistDetail,
                                        onOpenGenre = viewModel::openGenreDetail,
                                        onOpenFolder = viewModel::openFolderDetail,
                                        onPlayPlaylist = viewModel::playPlaylist
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
                                            onPlayAlbum = viewModel::playAlbum,
                                            onPlayTrack = { track -> viewModel.playTrack(track, tracks) },
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                            onStartSmartShuffle = viewModel::playYourMix,
                                            onOpenPlayerFromMix = {
                                                playerZoomEntry = true
                                                viewModel.setPlayerExpanded(true)
                                            },
                                            homeSections = customizationSettings.homeSections,
                                            onSetHomeSections = viewModel::setHomeSections,
                                            onResetHomeSections = viewModel::resetHomeSections,
                                            onPullProgressChange = { homePullProgress = it },
                                            onQuickSync = viewModel::triggerQuickSync,
                                            shelves = homeShelves,
                                            scrollToTopSignal = scrollToTopSignal,
                                            openShelf = openHomeShelf,
                                            onOpenShelf = viewModel::openHomeShelf,
                                            onPlayTrackFrom = { track, queue -> viewModel.playTrack(track, queue) }
                                        )
                                    }

                                    PrimaryDestination.LIBRARY -> {
                                        LibraryCollectionScreen(
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
                                            onPlayAlbum = viewModel::playAlbum,
                                            onPlayTrack = viewModel::playTrack,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onPlayPlaylist = viewModel::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            downloadsMap = downloadsMap,
                                            onDownloadTrack = viewModel::downloadTrackForOffline,
                                            onDownloadAlbum = viewModel::downloadAlbumForOffline,
                                            onRemoveDownload = viewModel::removeOfflineDownload,
                                            onPlayTrackNext = viewModel::playTrackNext,
                                            onAddTrackToQueue = viewModel::addTrackToQueue
                                        )
                                    }

                                    PrimaryDestination.ARTISTS -> {
                                        val artistsKey = LibraryFilterTab.ARTISTS.name
                                        LibraryCollectionScreen(
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
                                            onPlayAlbum = viewModel::playAlbum,
                                            onPlayTrack = viewModel::playTrack,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onPlayPlaylist = viewModel::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            downloadsMap = downloadsMap,
                                            onDownloadTrack = viewModel::downloadTrackForOffline,
                                            onDownloadAlbum = viewModel::downloadAlbumForOffline,
                                            onRemoveDownload = viewModel::removeOfflineDownload,
                                            onPlayTrackNext = viewModel::playTrackNext,
                                            onAddTrackToQueue = viewModel::addTrackToQueue
                                        )
                                    }

                                    PrimaryDestination.PLAYLISTS -> {
                                        PlaylistsCollectionScreen(
                                            playlists = playlists,
                                            tracks = tracks,
                                            currentTrackId = currentTrack?.id,
                                            isPlaying = isPlaying,
                                            onPlayPlaylist = viewModel::playPlaylist,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            onPlayTrack = viewModel::playTrack,
                                            onToggleStarTrack = viewModel::toggleStarTrack,
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
                                            onPlayTrack = { trk, ctx -> viewModel.playTrack(trk, ctx) },
                                            onToggleStarTrack = viewModel::toggleStarTrack,
                                            onOpenAlbum = { albumId ->
                                                viewModel.openAlbumDetail(albumId)
                                            },
                                            onCloseAlbumDetail = {
                                                viewModel.openAlbumDetail(null)
                                            },
                                            onPlayAlbum = viewModel::playAlbum,
                                            onSmartShuffleTracks = { favTracks ->
                                                viewModel.startSmartShuffleSession(favTracks)
                                            },
                                            viewMode = tabDisplayPreferences["FAVOURITES"]?.viewMode ?: LibraryViewMode.LIST,
                                            onSetViewMode = { viewModel.setCategoryViewMode("FAVOURITES", it) },
                                            scrollToTopSignal = scrollToTopSignal
                                        )
                                    }

                                    PrimaryDestination.SETTINGS -> {
                                        SettingsCustomizationScreen(
                                            settings = customizationSettings,
                                            speedPitchState = speedPitchState,
                                            reverbState = reverbState,
                                            sleepTimerState = sleepTimerState,
                                            artworkShapeMode = artworkShapeMode,
                                            activeEqPreset = eqPreset,
                                            servers = servers,
                                            activeServer = activeServer,
                                            pingDiagnostic = pingDiagnostic,
                                            isSyncing = isSyncing,
                                            onSetAppIconVariant = viewModel::setAppIconVariant,
                                            onOpenAppIconSettings = { viewModel.setAppIconSettingsOpen(true) },
                                            onSetDockStyle = viewModel::setDockStyle,
                                            onSetThemeMode = viewModel::setThemeMode,
                                            onSetUseMaterialYou = viewModel::setUseMaterialYouDynamicColor,
                                            onSetHarmonizeArtwork = viewModel::setHarmonizePlayerWithArtwork,
                                            onSetUnifiedMaterialYouColor = viewModel::setUnifiedMaterialYouColor,
                                            onSetCuratedPalette = viewModel::setCuratedColorPalette,
                                            onSetCornerStylePreset = viewModel::setCornerStylePreset,
                                            onSetInterfaceStyle = viewModel::setInterfaceStyle,
                                            onSetHeaderFont = viewModel::setHeaderFont,
                                            onSetBodyFont = viewModel::setBodyFont,
                                            onSetArtworkCut = viewModel::setArtworkCut,
                                            onSetHomeSections = viewModel::setHomeSections,
                                            onResetHomeSections = viewModel::resetHomeSections,
                                            onSetPlayerBackgroundStyle = viewModel::setPlayerBackgroundStyle,
                                            onSetArtworkShape = viewModel::setArtworkShapeMode,
                                            onSetAnimatedSquiggly = viewModel::setAnimatedSquigglyWave,
                                            onSetHapticFeedback = viewModel::setHapticFeedbackEnabled,
                                            onSetHighRefreshRate120Hz = viewModel::setHighRefreshRate120Hz,
                                            shuffleMode = shuffleMode,
                                            onSetShuffleMode = viewModel::setShuffleMode,
                                            onSetSmartShuffleProfile = viewModel::setSmartShuffleProfile,
                                            onSelectReverbPreset = viewModel::selectReverbPreset,
                                            onSetCrossfadeSeconds = viewModel::setCrossfadeSeconds,
                                            onSetGapless = viewModel::setGaplessPlayback,
                                            onSetReplayGain = viewModel::setReplayGainMode,
                                            onSetAutoScrobble = viewModel::setAutoScrobble,
                                            onSetPauseOnHeadphoneDisconnect = viewModel::setPauseOnHeadphoneDisconnect,
                                            onSetFallbackArtistToAlbumArt = viewModel::setFallbackArtistToAlbumArt,
                                            onUpdateWifiQuality = viewModel::updateTranscodeProfile,
                                            onUpdateMobileQuality = viewModel::setMobileQuality,
                                            onSetDownloadSpeedLimit = viewModel::setDownloadSpeedLimit,
                                            onSetReadAheadBuffer = viewModel::setReadAheadBuffer,
                                            onSetWidgetLayoutMode = viewModel::setWidgetLayoutMode,
                                            onSetWidgetThemeMode = viewModel::setWidgetThemeMode,
                                            onSetWidgetShowArtwork = viewModel::setWidgetShowArtwork,
                                            onSetWidgetShowFavoriteButton = viewModel::setWidgetShowFavoriteButton,
                                            onSetWidgetShowLyrics = viewModel::setWidgetShowLyrics,
                                            onRestoreDefaults = viewModel::restoreDefaultCustomizationSettings,
                                            onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                            onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                                            onOpenSleepTimerSheet = { viewModel.setSleepTimerSheetOpen(true) },
                                            onStartSleepTimerMinutes = { mins -> viewModel.startSleepTimerDuration(mins) },
                                            onStartSleepTimerEndOfTrack = viewModel::startSleepTimerEndOfTrack,
                                            onCancelSleepTimer = viewModel::cancelSleepTimer,
                                            onSelectEqPreset = viewModel::selectEqualizerPreset,
                                            offlineStorageSummary = offlineStorageSummary,
                                            onClearOfflineDownloads = viewModel::clearAllOfflineDownloads,
                                            onClearTempAudioCache = viewModel::clearTemporaryAudioCache,
                                            onBack = { viewModel.selectDestination(PrimaryDestination.HOME) },
                                            onActivateServer = viewModel::activateExistingServer,
                                            onTestAndSaveServer = viewModel::testAndSaveServer
                                        )
                                    }
                                }
                            }

                            if (isExpandedScreen && currentTrack != null) {
                                PlaybackPosition(viewModel) { positionMs, durationMs ->
                                    FloatingExpressiveMiniPlayer(
                                        track = currentTrack!!,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        unifiedMaterialYouColor = customizationSettings.unifiedMaterialYouColor,
                                        onTogglePlay = viewModel::togglePlayPause,
                                        onSkipNext = viewModel::skipNext,
                                        onExpand = { viewModel.setPlayerExpanded(true) }
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

                // Full-Screen Opaque Material 3 Expressive Now Playing Studio Sheet
                AnimatedVisibility(
                    visible = isPlayerExpanded && currentTrack != null,
                    // From the Your Mix hero the player grows out of the zoom; elsewhere it slides up.
                    enter = if (playerZoomEntry) {
                        fadeIn(tween(240)) + scaleIn(initialScale = 0.9f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    } else slideInVertically(
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
                    val activeTrack = currentTrack
                    if (activeTrack != null) {
                        PlaybackPosition(viewModel) { positionMs, durationMs ->
                            NowPlayingStudioOverlay(
                                track = activeTrack,
                                server = activeServer,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                positionMs = positionMs,
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
                                showTechnicalFormatBadge = customizationSettings.showTechnicalFormatBadge,
                                showPlayerStarButton = customizationSettings.showPlayerStarButton,
                                unifiedMaterialYouColor = customizationSettings.unifiedMaterialYouColor,
                                backgroundAnimationStyle = customizationSettings.playerBackgroundStyle,
                                artworkShapeMode = artworkShapeMode,
                                activeTab = nowPlayingTab,
                                lyrics = currentLyrics,
                                lyricsResult = currentLyricsResult,
                                reverbProcessingMode = reverbProcessingMode,
                                isPlayingOfflineFile = isPlayingOfflineFile,
                                queue = playbackQueue,
                                eqPreset = eqPreset,
                                onCollapse = { viewModel.setPlayerExpanded(false) },
                                onSelectTab = viewModel::selectNowPlayingTab,
                                onCycleArtworkShape = viewModel::cycleArtworkShapeMode,
                                onTogglePlay = viewModel::togglePlayPause,
                                onSkipPrevious = viewModel::skipPrevious,
                                onSwipePrevious = { viewModel.skipPrevious(restartIfPlayed = false) },
                                onSkipNext = viewModel::skipNext,
                                onSeek = viewModel::seekTo,
                                onToggleShuffle = viewModel::toggleShuffle,
                                onToggleRepeatOne = viewModel::toggleRepeatOne,
                                onToggleStar = { viewModel.toggleStarTrack(activeTrack) },
                                onSetTrackRating = viewModel::setTrackRating,
                                onOpenSpeedPitchSheet = { viewModel.setSpeedPitchSheetOpen(true) },
                                onOpenSleepTimerSheet = { viewModel.setSleepTimerSheetOpen(true) },
                                onOpenArtist = { viewModel.openArtistFromTrack(activeTrack) },
                                onOpenSonicLab = { viewModel.setSonicLabSheetOpen(true) },
                                onPlayQueueTrack = { viewModel.playFromQueue(it) },
                                onRemoveQueueTrack = viewModel::removeTrackFromQueue,
                                onMoveQueueTrack = viewModel::moveQueueTrack
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
                        currentTrackBpm = currentTrack?.bpm ?: 120,
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
                        onStartDuration = viewModel::startSleepTimerDuration,
                        onStartEndOfTrack = viewModel::startSleepTimerEndOfTrack,
                        onAddFiveMinutes = { viewModel.addSleepTimerMinutes(5) },
                        onUpdateOptions = viewModel::updateSleepTimerOptions,
                        onCancel = viewModel::cancelSleepTimer,
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
                    SonicLabModalOverlay(
                        tracks = tracks,
                        activePreset = eqPreset,
                        customBandsDb = customEqBands,
                        activeTranscodeFormat = activeServer.transcodeFormat,
                        activeMaxBitRate = activeServer.maxBitRate,
                        onUpdateTranscodeProfile = viewModel::updateTranscodeProfile,
                        onSelectPreset = viewModel::selectEqualizerPreset,
                        onUpdateBand = viewModel::updateCustomEqBand,
                        onPlayMoodMix = { mixTracks ->
                            if (mixTracks.isNotEmpty()) {
                                viewModel.playTrack(mixTracks.first(), mixTracks)
                            }
                        },
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

                // Application Introduction / Server Details Onboarding Modal
                AnimatedVisibility(
                    visible = showServerOnboarding,
                    enter = fadeIn() + scaleIn(initialScale = 0.96f),
                    exit = fadeOut() + scaleOut(targetScale = 0.96f)
                ) {
                    ServerOnboardingIntroModal(
                        activeServer = activeServer,
                        onConnectServer = { name, url, user, pass, tokenAuth ->
                            viewModel.testAndSaveServer(
                                name = name,
                                baseUrl = url,
                                username = user,
                                passwordOrToken = pass,
                                useTokenAuth = tokenAuth,
                                transcodeFormat = activeServer.transcodeFormat,
                                maxBitRate = activeServer.maxBitRate
                            )
                            viewModel.completeServerOnboarding()
                        },
                        onContinueWithDemo = {
                            viewModel.completeServerOnboarding()
                        }
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
 * in while its night sky slides into place, the moon and note pop onto it, rings ripple out,
 * the wordmark rises letter by letter, then everything lifts away. It starts on the splash
 * colour so the system splash dissolves straight into it.
 */
@Composable
fun OndaluneAnimatedLaunchIntroOverlay(
    variant: AppIconVariant,
    onFinished: () -> Unit
) {
    val tile = remember { Animatable(0.55f) }
    val waves = remember { Animatable(-1f) }
    val note = remember { Animatable(0f) }
    val rings = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val accent = remember(variant) { parseHexColor(variant.accentHex) }
    val brandName = "Ondalune"

    LaunchedEffect(Unit) {
        // Start the clock only once frames are actually being drawn, so a busy cold start
        // can't skip the opening of the animation.
        repeat(2) { withFrameNanos { } }
        coroutineScope {
            launch { tile.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
            launch { waves.animateTo(0f, tween(900, easing = FastOutSlowInEasing)) }
            launch {
                delay(260L)
                note.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color(0xFF15152B))
            .testTag("ondalune_launch_intro_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.44f
            val unit = minOf(size.width, size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.32f * tile.value.coerceIn(0f, 1f)), Color.Transparent),
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(bottom = 48.dp)
                .graphicsLayer {
                    val lift = exit.value
                    translationY = -lift * 24.dp.toPx()
                    scaleX = 1f + lift * 0.06f
                    scaleY = scaleX
                }
        ) {
            OndaluneBrandEmblem(
                size = 128.dp,
                variant = variant,
                frameShape = AppIconPreviewFrameShape.SQUIRCLE,
                foregroundScale = 0.4f + 0.6f * note.value,
                foregroundAlpha = note.value.coerceIn(0f, 1f),
                backgroundShift = waves.value,
                modifier = Modifier.graphicsLayer {
                    scaleX = tile.value
                    scaleY = tile.value
                    rotationZ = (1f - tile.value) * -18f
                }
            )
            Spacer(modifier = Modifier.height(22.dp))
            Row {
                brandName.forEachIndexed { i, ch ->
                    val local = (word.value * (brandName.length + 3) - i).coerceIn(0f, 1f)
                    Text(
                        text = ch.toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 3.sp
                        ),
                        color = Color(0xFFF9C9A8),
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
    val shelf: HomeShelfKind?
) {
    val route: String get() = destination.route
    val detailKey: String? get() = albumId?.let { "album:$it" } ?: artistId?.let { "artist:$it" }
        ?: genreName?.let { "genre:$it" } ?: folderPath?.let { "folder:$it" } ?: shelf?.let { "shelf:${it.name}" }

    /** Navigation depth inside the tab (shelf page, then a detail on top), for slide direction. */
    val depth: Int get() = (if (shelf != null) 1 else 0) +
        (if (albumId != null || artistId != null || genreName != null || folderPath != null) 1 else 0)
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
