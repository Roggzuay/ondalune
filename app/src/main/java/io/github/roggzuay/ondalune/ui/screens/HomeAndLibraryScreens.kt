@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberUpdatedState
import io.github.roggzuay.ondalune.data.model.hasGenre
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import io.github.roggzuay.ondalune.ui.components.SelectedBadge
import io.github.roggzuay.ondalune.data.model.genreList
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec
import io.github.roggzuay.ondalune.ui.components.ViewModeMenuButton
import io.github.roggzuay.ondalune.ui.components.collectionEntries
import io.github.roggzuay.ondalune.ui.components.CollectionEntry
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.IconButton
import io.github.roggzuay.ondalune.ui.components.TrackOptionsSheet
import io.github.roggzuay.ondalune.ui.components.LocalDownloads
import io.github.roggzuay.ondalune.ui.components.LocalTrackSelection
import io.github.roggzuay.ondalune.ui.components.SelectableTracks
import io.github.roggzuay.ondalune.ui.components.TrackSelection
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelfKind
import io.github.roggzuay.ondalune.ui.theme.LocalInterfaceStyle
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelves
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LocalContentColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.AlbumListType
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import io.github.roggzuay.ondalune.ui.components.AnimatedAudioBarsIndicator
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.components.SpringPressableSurface
import io.github.roggzuay.ondalune.ui.components.formatDurationSec
import io.github.roggzuay.ondalune.ui.theme.parseHexColor
import io.github.roggzuay.ondalune.ui.viewmodel.LibraryFilterTab
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel

/**
 * Home ("Listen Now") Screen with Pull-Down Quick Sync (popping a Material 3 Expressive morphing
 * icon from the top-center camera cutout), Material You Dynamic Hero Card, Plump Category Filter
 * Boxes, Album Carousel and the Home sections in the user's order.
 */
@Composable
fun ListenNowHomeScreen(
    albums: List<SubsonicAlbum>,
    tracks: List<SubsonicTrack>,
    selectedListType: AlbumListType,
    activeEqPreset: EqualizerPreset,
    currentTrackId: String?,
    isPlaying: Boolean,
    shuffleMode: ShuffleMode = ShuffleMode.OFF,
    selectedAlbumId: String? = null,
    onSelectListType: (AlbumListType) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbumDetail: () -> Unit = {},
    onPlayAlbum: (String, Boolean) -> Unit,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onToggleStarTrack: (SubsonicTrack) -> Unit,
    onOpenSonicLab: () -> Unit,
    onStartSmartShuffle: () -> Unit = {},
    onOpenPlayerFromMix: () -> Unit = {},
    onPullProgressChange: (Float) -> Unit = {},
    onQuickSync: () -> Unit = {},
    shelves: HomeShelves = HomeShelves(),
    openShelf: HomeShelfKind? = null,
    scrollToTopSignal: Int = 0,
    onOpenShelf: (HomeShelfKind?) -> Unit = {},
    onPlayTrackFrom: (SubsonicTrack, List<SubsonicTrack>) -> Unit = { track, _ -> onPlayTrack(track) },
    homeSections: List<HomeSection> = emptyList(),
    onSetHomeSections: (List<HomeSection>) -> Unit = {},
    onResetHomeSections: () -> Unit = {}
) {
    var showLayoutEditor by remember { mutableStateOf(false) }
    val sections = homeSections.ifEmpty { LocalStyleSpec.current.defaultHome }
    // If an album was opened from the Home screen, render AlbumDetailSubScreen right here
    // so pressing Back returns directly to the Home screen!
    val detailAlbum = albums.find { it.id == selectedAlbumId }
    if (detailAlbum != null) {
        BackHandler { onCloseAlbumDetail() }
        val albumTracks = tracks.filter { it.albumId == detailAlbum.id }
        AlbumDetailSubScreen(
            album = detailAlbum,
            tracks = albumTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = onCloseAlbumDetail,
            onPlayAll = { onPlayAlbum(detailAlbum.id, false) },
            onShuffleAll = { onPlayAlbum(detailAlbum.id, true) },
            onPlayTrack = { onPlayTrackFrom(it, albumTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    // A shelf's "More" page: the complete list behind that Home row.
    if (openShelf != null) {
        BackHandler { onOpenShelf(null) }
        val shelfTracks = shelves.tracksFor(openShelf)
        if (shelfTracks != null) {
            GenreOrFolderDetailSubScreen(
                title = openShelf.title,
                subtitle = "${shelfTracks.size} tracks",
                tracks = shelfTracks,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                onBack = { onOpenShelf(null) },
                onPlayAll = { shelfTracks.firstOrNull()?.let { onPlayTrackFrom(it, shelfTracks) } },
                onPlayTrack = { onPlayTrackFrom(it, shelfTracks) },
                onToggleStar = onToggleStarTrack
            )
        } else {
            AlbumGridSubScreen(
                title = openShelf.title,
                albums = shelves.albumsFor(openShelf),
                onBack = { onOpenShelf(null) },
                onOpenAlbum = onOpenAlbum,
                onPlayAlbum = onPlayAlbum
            )
        }
        return
    }

    val featuredAlbum = albums.firstOrNull()
    val listState = rememberLazyListState()
    ScrollToTopOnSignal(listState, scrollToTopSignal)
    val hapticManager = LocalHapticFeedbackManager.current
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    val syncTriggerThresholdPx = 140f

    val pullSyncConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistancePx > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pullDistancePx)
                    pullDistancePx = (pullDistancePx + consumed).coerceAtLeast(0f)
                    onPullProgressChange((pullDistancePx / syncTriggerThresholdPx).coerceIn(0f, 1f))
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (source == NestedScrollSource.UserInput && atTop && available.y > 0f) {
                    val prev = pullDistancePx
                    pullDistancePx = (pullDistancePx + available.y * 0.65f).coerceAtMost(syncTriggerThresholdPx * 1.35f)
                    if (prev < syncTriggerThresholdPx && pullDistancePx >= syncTriggerThresholdPx) {
                        hapticManager?.performSubtleTick()
                    }
                    onPullProgressChange((pullDistancePx / syncTriggerThresholdPx).coerceIn(0f, 1f))
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistancePx >= syncTriggerThresholdPx * 0.85f) {
                    hapticManager?.performPlayPauseTransition(isStartingPlay = true)
                    onQuickSync()
                }
                pullDistancePx = 0f
                onPullProgressChange(0f)
                return Velocity.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullSyncConnection)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("listen_now_home_list"),
            contentPadding = PaddingValues(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        sections.forEach { section ->
            when (section) {
                HomeSection.YOUR_MIX -> item(key = "your_mix") {
                    YourMixHero(shelves = shelves, onPlay = onStartSmartShuffle, onOpenPlayer = onOpenPlayerFromMix)
                }
                HomeSection.QUICK_ACTIONS -> item(key = "quick_actions") {
                    HomeQuickActions(onOpenShelf = onOpenShelf, onShuffle = onStartSmartShuffle)
                }
                HomeSection.FEATURED -> if (featuredAlbum != null) item(key = "featured") {
                    FeaturedAlbumHeroCard(
                        album = featuredAlbum,
                        onOpenAlbum = { onOpenAlbum(featuredAlbum.id) },
                        onPlayAlbum = { onPlayAlbum(featuredAlbum.id, false) },
                        onShuffleAlbum = { onPlayAlbum(featuredAlbum.id, true) }
                    )
                }
                HomeSection.JUMP_BACK_IN -> if (shelves.jumpBackIn.isNotEmpty()) item(key = "shelf_jump") {
                    TrackShelf(HomeShelfKind.JUMP_BACK_IN.title, shelves.jumpBackIn, currentTrackId, isPlaying, onPlayTrackFrom) { onOpenShelf(HomeShelfKind.JUMP_BACK_IN) }
                }
                HomeSection.RECENTLY_ADDED -> if (shelves.recentlyAdded.isNotEmpty()) item(key = "shelf_added") {
                    AlbumShelf(HomeShelfKind.RECENTLY_ADDED.title, shelves.recentlyAdded, onOpenAlbum, onPlayAlbum) { onOpenShelf(HomeShelfKind.RECENTLY_ADDED) }
                }
                HomeSection.FAVOURITE_TRACKS -> if (shelves.favouriteTracks.isNotEmpty()) item(key = "shelf_fav") {
                    TrackShelf(HomeShelfKind.FAVOURITE_TRACKS.title, shelves.favouriteTracks, currentTrackId, isPlaying, onPlayTrackFrom) { onOpenShelf(HomeShelfKind.FAVOURITE_TRACKS) }
                }
                HomeSection.MOST_PLAYED -> if (shelves.mostPlayed.isNotEmpty()) item(key = "shelf_most") {
                    TrackShelf(HomeShelfKind.MOST_PLAYED.title, shelves.mostPlayed, currentTrackId, isPlaying, onPlayTrackFrom) { onOpenShelf(HomeShelfKind.MOST_PLAYED) }
                }
                HomeSection.TOP_ARTISTS -> item(key = "top_artists") {
                    TopArtistsShelf(shelves) { ShelfHeader("Top artists", onMore = null) }
                }
                HomeSection.RECENTLY_PLAYED -> if (shelves.recentlyPlayed.isNotEmpty()) item(key = "shelf_recent") {
                    AlbumShelf(HomeShelfKind.RECENTLY_PLAYED.title, shelves.recentlyPlayed, onOpenAlbum, onPlayAlbum) { onOpenShelf(HomeShelfKind.RECENTLY_PLAYED) }
                }
                HomeSection.REDISCOVER -> if (shelves.rediscover.isNotEmpty()) item(key = "shelf_rediscover") {
                    AlbumShelf(HomeShelfKind.REDISCOVER.title, shelves.rediscover, onOpenAlbum, onPlayAlbum) { onOpenShelf(HomeShelfKind.REDISCOVER) }
                }
            }
        }
        item(key = "customize_home") { CustomizeHomeButton { showLayoutEditor = true } }
        }
    }
    if (showLayoutEditor) {
        HomeLayoutSheet(
            customSections = homeSections,
            onChange = onSetHomeSections,
            onReset = onResetHomeSections,
            onDismiss = { showLayoutEditor = false }
        )
    }
}

@Composable
fun FeaturedAlbumHeroCard(
    album: SubsonicAlbum,
    onOpenAlbum: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit
) {
    val seedColor = parseHexColor(album.paletteSeedHex)
    val accentColor = parseHexColor(album.accentHex)
    val spec = LocalStyleSpec.current
    val standard = !spec.artBorder

    SpringPressableSurface(
        onClick = onOpenAlbum,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                if (standard) Modifier else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = cornerShape(30.dp)
                )
            ),
        cornerRadius = 30.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (standard) Modifier else Modifier.background(
                        Brush.linearGradient(
                            colors = listOf(
                                seedColor.copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                accentColor.copy(alpha = 0.22f)
                            )
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (standard) {
                        Text(
                            text = if (spec.eyebrows) "FEATURED FOR YOU" else "Featured album",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else Surface(
                        shape = cornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "FEATURED MASTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val heroActions = LocalTrackActions.current
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(cornerShape(6.dp))
                            .clickable { heroActions?.openArtist(album.artistId, album.artist) }
                            .testTag("album_artist_link")
                    )
                    Text(
                        text = "${album.genre} • ${album.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpringPressableSurface(
                            onClick = onPlayAlbum,
                            color = MaterialTheme.colorScheme.primary,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("hero_play_album_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .height(46.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (standard) "Play" else "Play Master",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        ExpressiveBounceIconButton(
                            onClick = onShuffleAlbum,
                            size = 46.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle Album",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                ExpressiveArtwork(
                    seedHex = album.paletteSeedHex,
                    accentHex = album.accentHex,
                    artStyleIndex = album.artStyleIndex,
                    coverArtUrl = album.coverArtUrl,
                    shapeMode = if (standard) ArtworkShapeMode.SQUIRCLE else ArtworkShapeMode.COOKIE,
                    isSpinning = !standard,
                    modifier = Modifier.size(116.dp)
                )
            }
        }
    }
}

/** Scrolls [state] back to the top each time [signal] changes (tab tapped again). */
@Composable
internal fun ScrollToTopOnSignal(state: LazyListState, signal: Int) {
    var seen by remember { mutableIntStateOf(signal) }
    LaunchedEffect(signal) {
        if (signal != seen) {
            seen = signal
            state.animateScrollToItem(0)
        }
    }
}

@Composable
internal fun ShelfHeader(title: String, onMore: (() -> Unit)?, onPlayAll: (() -> Unit)? = null) {
    val spec = LocalStyleSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 6.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (spec.standardShapes) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Bloom and Poster put a play pill next to the title; other themes keep the quiet link.
        if (onPlayAll != null && spec.shelfPlayButtons) {
            FilledTonalButton(
                onClick = onPlayAll,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("shelf_play_${title.lowercase().replace(' ', '_')}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
        }
        if (onMore != null) TextButton(
            onClick = onMore,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.testTag("shelf_more_${title.lowercase().replace(' ', '_')}")
        ) { Text(if (spec.eyebrows) "See all" else "More") }
    }
}

/** How many items a Home shelf previews before "More". */
private const val ShelfPreviewCount = 12

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<SubsonicAlbum>,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit,
    onMore: () -> Unit
) {
    Column {
        ShelfHeader(title, onMore, onPlayAll = albums.firstOrNull()?.let { first -> { onPlayAlbum(first.id, false) } })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums.take(ShelfPreviewCount), key = { it.id }) { album ->
                AlbumExpressiveCard(
                    album = album,
                    onClick = { onOpenAlbum(album.id) },
                    onPlayClick = { onPlayAlbum(album.id, false) }
                )
            }
        }
    }
}

@Composable
private fun TrackShelf(
    title: String,
    tracks: List<SubsonicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayTrack: (SubsonicTrack, List<SubsonicTrack>) -> Unit,
    onMore: () -> Unit
) {
    Column {
        ShelfHeader(title, onMore, onPlayAll = tracks.firstOrNull()?.let { first -> { onPlayTrack(first, tracks) } })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tracks.take(ShelfPreviewCount), key = { it.id }) { track ->
                val isCurrent = track.id == currentTrackId
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onPlayTrack(track, tracks) }
                        .padding(bottom = 8.dp)
                        .testTag("shelf_track_${track.id}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ExpressiveArtwork(
                            seedHex = track.paletteSeedHex,
                            accentHex = track.accentHex,
                            artStyleIndex = track.artStyleIndex,
                            coverArtUrl = track.coverArtUrl,
                            shapeMode = if (isCurrent && isPlaying) ArtworkShapeMode.COOKIE else ArtworkShapeMode.SQUIRCLE,
                            modifier = Modifier.size(132.dp)
                        )
                    }
                    Text(
                        text = track.title,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumExpressiveCard(
    album: SubsonicAlbum,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    fillWidth: Boolean = false
) {
    val standard = !LocalStyleSpec.current.cardTiles
    SpringPressableSurface(
        onClick = onClick,
        color = if (standard) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        cornerRadius = 26.dp,
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(if (standard) 144.dp else 168.dp))
            .testTag("album_card_${album.id}")
    ) {
        Column(modifier = Modifier.padding(if (standard) 0.dp else 12.dp)) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ExpressiveArtwork(
                    seedHex = album.paletteSeedHex,
                    accentHex = album.accentHex,
                    artStyleIndex = album.artStyleIndex,
                    coverArtUrl = album.coverArtUrl,
                    shapeMode = ArtworkShapeMode.SQUIRCLE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillWidth) Modifier.aspectRatio(1f) else Modifier.height(144.dp))
                )
                ExpressiveBounceIconButton(
                    onClick = onPlayClick,
                    size = 42.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${album.name}",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val cardActions = LocalTrackActions.current
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { cardActions?.openArtist(album.artistId, album.artist) }
            )
            if (!standard) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${album.songCount} tracks • ${album.year}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Track row used by every list. Swipe left = play next, swipe right = favourite, long-press =
 * options sheet (or toggle while multi-selecting). Artist and album names open their pages, and a
 * thin progress bar appears while the track downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackExpressiveRow(
    track: SubsonicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleStar: () -> Unit,
    downloadEntry: SubsonicDownloadEntity? = null,
    @Suppress("UNUSED_PARAMETER") onDownloadTrack: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onRemoveDownload: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onPlayNext: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Dense one-line row: smaller art, no badges, no heart button. */
    compact: Boolean = false
) {
    val actions = LocalTrackActions.current
    val selection = LocalTrackSelection.current
    val downloads = LocalDownloads.current
    val haptics = LocalHapticFeedbackManager.current
    val entry = downloadEntry ?: downloads[track.id]
    val isDownloaded = entry?.stateEnum == OfflineDownloadState.COMPLETED
    val isTransferring = entry?.stateEnum == OfflineDownloadState.DOWNLOADING || entry?.stateEnum == OfflineDownloadState.QUEUED
    val selected = selection.isSelected(track.id)
    var showOptions by remember { mutableStateOf(false) }

    // The swipe state (and its callback) is remembered once per row; read the latest track and
    // callback through these, or a second swipe acts on the track as it was first shown
    // (e.g. favouriting again instead of un-favouriting).
    val latestTrack by rememberUpdatedState(track)
    val latestToggleStar by rememberUpdatedState(onToggleStar)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics?.performSubtleTick()
                    actions?.playNext(listOf(latestTrack))
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics?.performSubtleTick()
                    latestToggleStar()
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false // never dismiss: the row springs back after the action
        },
        positionalThreshold = { it * 0.32f }
    )

    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = actions != null && !selection.active,
        enableDismissFromEndToStart = actions != null && !selection.active,
        backgroundContent = { TrackSwipeBackground(swipeState.dismissDirection, track.starred) },
        modifier = modifier.fillMaxWidth()
    ) {
        SpringPressableSurface(
            onClick = { if (selection.active) selection.toggle(track) else onPlay() },
            onLongClick = {
                haptics?.performSubtleTick()
                if (selection.active) selection.toggle(track) else showOptions = true
            },
            color = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                // Flat-row themes draw rows straight on the page; solid while swiping.
                !LocalStyleSpec.current.cardRows && swipeState.dismissDirection == SwipeToDismissBoxValue.Settled -> Color.Transparent
                !LocalStyleSpec.current.cardRows -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            cornerRadius = 22.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("track_row_${track.id}")
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ExpressiveArtwork(
                            seedHex = track.paletteSeedHex,
                            accentHex = track.accentHex,
                            artStyleIndex = track.artStyleIndex,
                            coverArtUrl = track.coverArtUrl,
                            shapeMode = if (isCurrent && isPlaying) ArtworkShapeMode.COOKIE else ArtworkShapeMode.SQUIRCLE,
                            isSpinning = isCurrent && isPlaying,
                            styleShape = LocalStyleSpec.current.rowArtShape,
                            modifier = Modifier.size(if (compact) 42.dp else 52.dp)
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .size(if (compact) 42.dp else 52.dp)
                                    .clip(cornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) { SelectedBadge() }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Plain text: the row itself is the play target (names link from full screens).
                        Text(
                            text = if (compact) "${track.artist} • ${formatDurationSec(track.durationSec)}" else "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!compact) Spacer(modifier = Modifier.height(4.dp))
                        if (!compact) Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = cornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "${track.suffix} ${track.bitDepth}/${track.samplingRate / 1000}k",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (isDownloaded) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = "Available offline",
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${track.playCount} plays • ${formatDurationSec(track.durationSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isCurrent) {
                        AnimatedAudioBarsIndicator(
                            isPlaying = isPlaying,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    if (!compact) ExpressiveBounceIconButton(
                        onClick = onToggleStar,
                        active = track.starred,
                        size = 42.dp,
                        containerColor = if (track.starred) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (track.starred) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Icon(
                            imageVector = if (track.starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favourite",
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (actions != null) {
                        ExpressiveBounceIconButton(
                            onClick = { showOptions = true },
                            size = 40.dp,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("track_more_menu_${track.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Track options",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Download progress, animated between the throttled updates so it glides.
                AnimatedVisibility(visible = isTransferring) {
                    val progress by animateFloatAsState(
                        targetValue = (entry?.progressPercent ?: 0) / 100f,
                        animationSpec = tween(400),
                        label = "rowDownload"
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                            .testTag("track_download_progress_${track.id}"),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }
    }

    if (showOptions) {
        TrackOptionsSheet(
            tracks = listOf(track),
            downloads = downloads,
            onSelect = { selection.toggle(track) },
            onDismiss = { showOptions = false }
        )
    }
}

/** Swipe reveal: favourite on the left edge, play next on the right. */
@Composable
private fun TrackSwipeBackground(direction: SwipeToDismissBoxValue, starred: Boolean) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val toFavourite = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(cornerShape(22.dp))
            .background(if (toFavourite) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = if (toFavourite) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tint = if (toFavourite) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
            Icon(
                imageVector = when {
                    !toFavourite -> Icons.Filled.PlaylistPlay
                    starred -> Icons.Filled.HeartBroken
                    else -> Icons.Filled.Favorite
                },
                contentDescription = null,
                tint = tint
            )
            Text(
                text = when {
                    !toFavourite -> "Play next"
                    starred -> "Unfavourite"
                    else -> "Favourite"
                },
                style = MaterialTheme.typography.labelLarge,
                color = tint
            )
        }
    }
}

internal fun SubsonicAlbum.toEntry(onOpen: () -> Unit, onPlay: () -> Unit) = CollectionEntry(
    key = "alb_$id",
    title = name,
    subtitle = artist,
    detail = listOfNotNull(year.takeIf { it > 0 }?.toString(), "$songCount tracks", genre.takeIf { it.isNotBlank() })
        .joinToString(" • "),
    coverArtUrl = coverArtUrl,
    seedHex = paletteSeedHex,
    accentHex = accentHex,
    artStyleIndex = artStyleIndex,
    onClick = onOpen,
    onPlay = onPlay
)

/** Playlists show their first track's cover; delete sits on the row and on long-press. */
internal fun SubsonicPlaylist.toEntry(
    trackMap: Map<String, SubsonicTrack>,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)?
) = CollectionEntry(
    key = "pl_$id",
    title = name,
    subtitle = "${trackIds.size} tracks",
    detail = comment,
    coverArtUrl = trackIds.firstNotNullOfOrNull { trackMap[it]?.coverArtUrl?.takeIf(String::isNotBlank) }.orEmpty(),
    seedHex = paletteSeedHex,
    accentHex = accentHex,
    artStyleIndex = name.length % 5,
    onClick = onOpen,
    onPlay = onPlay,
    onLongClick = onDelete,
    trailing = onDelete?.let { delete ->
        {
            IconButton(onClick = delete, modifier = Modifier.testTag("playlist_delete_$id")) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
)

/**
 * Tracks in any layout: Compact and Rows use the swipeable track row; Tiles and Cards show
 * artwork tiles (tap plays, long-press opens the options sheet).
 */
internal fun LazyListScope.trackCollection(
    tracks: List<SubsonicTrack>,
    mode: LibraryViewMode,
    columns: Int,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlay: (SubsonicTrack) -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit,
    onLongPress: (SubsonicTrack) -> Unit,
    selection: TrackSelection,
    keyPrefix: String = "trk"
) {
    when (mode) {
        LibraryViewMode.COMPACT_LIST, LibraryViewMode.LIST -> items(
            items = tracks,
            key = { "${keyPrefix}_${it.id}" },
            contentType = { "song_row" }
        ) { track ->
            TrackExpressiveRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onPlay = { onPlay(track) },
                onToggleStar = { onToggleStar(track) },
                compact = mode == LibraryViewMode.COMPACT_LIST
            )
        }
        else -> collectionEntries(
            tracks.map { t ->
                CollectionEntry(
                    key = "${keyPrefix}_${t.id}",
                    title = t.title,
                    subtitle = t.artist,
                    detail = "${t.album} • ${formatDurationSec(t.durationSec)}",
                    coverArtUrl = t.coverArtUrl,
                    seedHex = t.paletteSeedHex,
                    accentHex = t.accentHex,
                    artStyleIndex = t.artStyleIndex,
                    highlighted = t.id == currentTrackId,
                    selected = { selection.isSelected(t.id) },
                    onClick = { if (selection.active) selection.toggle(t) else onPlay(t) },
                    onPlay = { if (selection.active) selection.toggle(t) else onPlay(t) },
                    onLongClick = { if (selection.active) selection.toggle(t) else onLongPress(t) }
                )
            },
            mode, columns
        )
    }
}

@Composable
internal fun TrackOptionsPopup(track: SubsonicTrack, onDismiss: () -> Unit) {
    val selection = LocalTrackSelection.current
    TrackOptionsSheet(
        tracks = listOf(track),
        downloads = LocalDownloads.current,
        onSelect = { selection.toggle(track) },
        onDismiss = onDismiss
    )
}

@Composable
internal fun DeletePlaylistDialog(playlist: SubsonicPlaylist, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${playlist.name}\"?") },
        text = { Text("The playlist is removed from the server. Its songs stay in your library.") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Library Collection Screen supporting Albums, Artists, Playlists, Genres, and Folders,
 * plus drill-down detail views with BackHandler support.
 */
@Composable
fun LibraryCollectionScreen(
    albums: List<SubsonicAlbum>,
    artists: List<SubsonicArtist>,
    tracks: List<SubsonicTrack>,
    playlists: List<SubsonicPlaylist>,
    genres: List<SubsonicGenre>,
    folders: List<SubsonicFolder>,
    activeTab: LibraryFilterTab,
    visibleTabs: List<LibraryFilterTab> = LibraryFilterTab.entries,
    scrollToTopSignal: Int = 0,
    viewMode: LibraryViewMode = LibraryViewMode.LIST,
    sortOption: LibrarySortOption = LibrarySortOption.NAME_ASC,
    tabDisplayPreferences: Map<String, TabDisplayPreference> = emptyMap(),
    fallbackArtistToAlbumArt: Boolean = false,
    selectedAlbumId: String?,
    selectedArtistId: String?,
    selectedGenreName: String?,
    selectedFolderPath: String?,
    currentTrackId: String?,
    isPlaying: Boolean,
    onSelectTab: (LibraryFilterTab) -> Unit,
    onSetViewMode: (LibraryViewMode) -> Unit = {},
    onSetSortOption: (LibrarySortOption) -> Unit = {},
    onToggleSortDirection: () -> Unit = {},
    onSetGridColumns: (Int) -> Unit = {},
    onOpenAlbum: (String?) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onOpenGenre: (String?) -> Unit,
    onOpenFolder: (String?) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit,
    onPlayTrack: (SubsonicTrack, List<SubsonicTrack>?) -> Unit,
    onToggleStarTrack: (SubsonicTrack) -> Unit,
    onPlayPlaylist: (SubsonicPlaylist) -> Unit,
    onCreatePlaylist: (String, String, List<String>) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    downloadsMap: Map<String, SubsonicDownloadEntity> = emptyMap(),
    onDownloadTrack: (SubsonicTrack) -> Unit = {},
    onDownloadAlbum: (String) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onPlayTrackNext: (SubsonicTrack) -> Unit = {},
    onAddTrackToQueue: (SubsonicTrack) -> Unit = {}
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var optionsTrack by remember { mutableStateOf<SubsonicTrack?>(null) }
    var pendingDelete by remember { mutableStateOf<SubsonicPlaylist?>(null) }
    val trackMap = remember(tracks) { tracks.associateBy { it.id } }
    // One cover per genre so genre tiles show real artwork instead of a placeholder.
    val genreCovers = remember(tracks) {
        val covers = HashMap<String, String>()
        tracks.forEach { t -> if (t.coverArtUrl.isNotBlank()) t.genreList().forEach { g -> covers.putIfAbsent(g.lowercase(), t.coverArtUrl) } }
        covers
    }

    val activeTabPref = tabDisplayPreferences[activeTab.name]
        ?: TabDisplayPreference(
            viewMode = viewMode,
            sortOption = sortOption,
            ascending = sortOption != LibrarySortOption.NAME_DESC,
            gridColumns = 2
        )
    val effectiveViewMode = activeTabPref.viewMode
    val effectiveSortOption = activeTabPref.sortOption
    val effectiveAscending = activeTabPref.ascending
    val isCompactList = effectiveViewMode == LibraryViewMode.COMPACT_LIST

    val availableSortOptions: List<LibrarySortOption> = remember(activeTab) {
        TabDisplayPreference.supportedSortOptionsFor(activeTab.name)
    }

    // Per-category independent sorting & direction
    val sortedAlbums = remember(albums, activeTabPref) {
        OndaluneViewModel.sortAlbums(albums, activeTabPref)
    }

    val sortedArtists = remember(artists, activeTabPref) {
        OndaluneViewModel.sortArtists(artists, activeTabPref)
    }

    val sortedPlaylists = remember(playlists, activeTabPref) {
        OndaluneViewModel.sortPlaylists(playlists, activeTabPref)
    }

    val sortedGenres = remember(genres, activeTabPref) {
        OndaluneViewModel.sortGenres(genres, activeTabPref)
    }

    val sortedFolders = remember(folders, activeTabPref) {
        OndaluneViewModel.sortFolders(folders, activeTabPref)
    }

    val sortedTracks = remember(tracks, activeTabPref) {
        OndaluneViewModel.sortTracks(tracks, activeTabPref)
    }

    // Sub-screen 1: Album Detail View
    val detailAlbum = albums.find { it.id == selectedAlbumId }
    if (detailAlbum != null) {
        BackHandler { onOpenAlbum(null) }
        val albumTracks = tracks.filter { it.albumId == detailAlbum.id }
        AlbumDetailSubScreen(
            album = detailAlbum,
            tracks = albumTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            downloadsMap = downloadsMap,
            onBack = { onOpenAlbum(null) },
            onPlayAll = { onPlayAlbum(detailAlbum.id, false) },
            onShuffleAll = { onPlayAlbum(detailAlbum.id, true) },
            onDownloadAlbum = { onDownloadAlbum(detailAlbum.id) },
            onPlayTrack = { onPlayTrack(it, albumTracks) },
            onToggleStar = onToggleStarTrack,
            onDownloadTrack = onDownloadTrack,
            onRemoveDownload = onRemoveDownload,
            onPlayTrackNext = onPlayTrackNext,
            onAddTrackToQueue = onAddTrackToQueue
        )
        return
    }

    // Sub-screen 2: Artist Detail View
    val detailArtist = artists.find { it.id == selectedArtistId || it.name == selectedArtistId }
    if (detailArtist != null) {
        BackHandler { onOpenArtist(null) }
        val artistTracks = tracks.filter { it.artist.equals(detailArtist.name, ignoreCase = true) }
        ArtistDetailSubScreen(
            artist = detailArtist,
            tracks = artistTracks,
            albums = albums.filter { it.artist.equals(detailArtist.name, ignoreCase = true) },
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenArtist(null) },
            onPlayTrack = { onPlayTrack(it, artistTracks) },
            onToggleStar = onToggleStarTrack,
            onOpenAlbum = { onOpenAlbum(it) },
            onPlayAlbum = { onPlayAlbum(it, false) }
        )
        return
    }

    // Sub-screen 3: Genre Detail View
    val detailGenre = genres.find { it.name == selectedGenreName }
    if (detailGenre != null) {
        BackHandler { onOpenGenre(null) }
        val genreTracks = tracks.filter { it.hasGenre(detailGenre.name) }
        GenreOrFolderDetailSubScreen(
            title = detailGenre.name,
            subtitle = "${detailGenre.trackCount} tracks • ${detailGenre.albumCount} albums",
            tracks = genreTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenGenre(null) },
            onPlayAll = { if (genreTracks.isNotEmpty()) onPlayTrack(genreTracks.first(), genreTracks) },
            onPlayTrack = { onPlayTrack(it, genreTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    // Sub-screen 4: Folder Detail View
    val detailFolder = folders.find { it.path == selectedFolderPath }
    if (detailFolder != null) {
        BackHandler { onOpenFolder(null) }
        GenreOrFolderDetailSubScreen(
            title = detailFolder.displayName,
            subtitle = detailFolder.path,
            tracks = detailFolder.tracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenFolder(null) },
            onPlayAll = {
                if (detailFolder.tracks.isNotEmpty()) {
                    onPlayTrack(detailFolder.tracks.first(), detailFolder.tracks)
                }
            },
            onPlayTrack = { onPlayTrack(it, detailFolder.tracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Tiles: three across on phones, more on wide screens (Cards are always two).
        val gridCols = when {
            maxWidth >= 900.dp -> 6
            maxWidth >= 580.dp -> 4
            else -> 3
        }

        val libraryListState = rememberLazyListState()
        ScrollToTopOnSignal(libraryListState, scrollToTopSignal)
        val selection = LocalTrackSelection.current
        SelectableTracks(if (activeTab == LibraryFilterTab.SONGS) sortedTracks else emptyList())
        LazyColumn(
            state = libraryListState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_collection_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactList) 2.dp else 12.dp)
        ) {
        // Clean Single-Row Header: Category Pills + 3-Dot View Mode Icon Menu
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (visibleTabs.size == 1) Text(
                    text = "${artists.size} artists",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                ) else LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(visibleTabs) { tab ->
                        val selected = tab == activeTab
                        val bg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            label = "libTabBg"
                        )
                        val fg by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "libTabFg"
                        )
                        SpringPressableSurface(
                            onClick = { onSelectTab(tab) },
                            color = bg,
                            cornerRadius = if (selected) 16.dp else 22.dp,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("library_tab_${tab.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        LibraryFilterTab.ALBUMS -> Icons.Filled.Album
                                        LibraryFilterTab.ARTISTS -> Icons.Filled.Person
                                        LibraryFilterTab.SONGS -> Icons.Filled.MusicNote
                                        LibraryFilterTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                                        LibraryFilterTab.GENRES -> Icons.Filled.Category
                                        LibraryFilterTab.FOLDERS -> Icons.Filled.FolderOpen
                                    },
                                    contentDescription = null,
                                    tint = fg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = fg
                                )
                            }
                        }
                    }
                }

                ViewModeMenuButton(
                    viewMode = effectiveViewMode,
                    onSetViewMode = onSetViewMode,
                    sortOptions = availableSortOptions,
                    sortOption = effectiveSortOption,
                    ascending = effectiveAscending,
                    onSetSortOption = onSetSortOption,
                    onToggleSortDirection = onToggleSortDirection
                )
            }
        }

        when (activeTab) {
            LibraryFilterTab.ALBUMS -> collectionEntries(
                sortedAlbums.map { album -> album.toEntry({ onOpenAlbum(album.id) }, { onPlayAlbum(album.id, false) }) },
                effectiveViewMode, gridCols
            )

            LibraryFilterTab.ARTISTS -> collectionEntries(
                sortedArtists.map { artist ->
                    val fallbackUrl = if (fallbackArtistToAlbumArt && artist.coverArtUrl.isBlank()) {
                        albums.firstOrNull { it.artist.equals(artist.name, ignoreCase = true) }?.coverArtUrl.orEmpty()
                    } else ""
                    CollectionEntry(
                        key = "art_${artist.id}_${artist.name}",
                        title = artist.name,
                        subtitle = "${artist.albumCount} albums • ${artist.trackCount} tracks",
                        detail = artist.genre,
                        coverArtUrl = artist.coverArtUrl.ifBlank { fallbackUrl },
                        seedHex = artist.paletteSeedHex,
                        accentHex = artist.accentHex,
                        artStyleIndex = artist.artStyleIndex,
                        monogram = artist.name,
                        round = true,
                        onClick = { onOpenArtist(artist.id) }
                    )
                },
                effectiveViewMode, gridCols
            )

            LibraryFilterTab.SONGS -> trackCollection(
                tracks = sortedTracks,
                mode = effectiveViewMode,
                columns = gridCols,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                onPlay = { onPlayTrack(it, sortedTracks) },
                onToggleStar = onToggleStarTrack,
                onLongPress = { optionsTrack = it },
                selection = selection
            )

            LibraryFilterTab.PLAYLISTS -> {
                item(key = "create_playlist") {
                    SpringPressableSurface(
                        onClick = { showCreatePlaylistDialog = true },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        cornerRadius = 22.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_playlist_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "New playlist",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                collectionEntries(
                    sortedPlaylists.map { pl ->
                        pl.toEntry(trackMap, onOpen = { onPlayPlaylist(pl) }, onPlay = { onPlayPlaylist(pl) }, onDelete = { pendingDelete = pl })
                    },
                    effectiveViewMode, gridCols
                )
            }

            LibraryFilterTab.GENRES -> collectionEntries(
                sortedGenres.map { genre ->
                    CollectionEntry(
                        key = "gen_${genre.name}",
                        title = genre.name,
                        subtitle = "${genre.trackCount} tracks • ${genre.albumCount} albums",
                        seedHex = genre.paletteSeedHex,
                        accentHex = genre.accentHex,
                        artStyleIndex = genre.artStyleIndex,
                        coverArtUrl = genreCovers[genre.name.lowercase()].orEmpty(),
                        onClick = { onOpenGenre(genre.name) },
                        onPlay = {
                            val genreTracks = tracks.filter { it.hasGenre(genre.name) }
                            genreTracks.firstOrNull()?.let { onPlayTrack(it, genreTracks) }
                        }
                    )
                },
                effectiveViewMode, gridCols
            )

            LibraryFilterTab.FOLDERS -> collectionEntries(
                sortedFolders.map { folder ->
                    CollectionEntry(
                        key = "fold_${folder.path}",
                        title = folder.displayName,
                        subtitle = folder.path,
                        detail = "${folder.tracks.size} audio files",
                        seedHex = folder.paletteSeedHex,
                        accentHex = folder.accentHex,
                        coverArtUrl = folder.tracks.firstOrNull()?.coverArtUrl.orEmpty(),
                        onClick = { onOpenFolder(folder.path) },
                        onPlay = { folder.tracks.firstOrNull()?.let { onPlayTrack(it, folder.tracks) } }
                    )
                },
                effectiveViewMode, gridCols
            )
        }

            item { Spacer(modifier = Modifier.height(150.dp)) }
        }
    }

    optionsTrack?.let { TrackOptionsPopup(it) { optionsTrack = null } }
    pendingDelete?.let { pl -> DeletePlaylistDialog(pl, onConfirm = { onDeletePlaylist(pl.id) }, onDismiss = { pendingDelete = null }) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            allTracks = tracks,
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name, comment, ids ->
                onCreatePlaylist(name, comment, ids)
                showCreatePlaylistDialog = false
            }
        )
    }
}

@Composable
private fun SubScreenHeader(title: String, subtitle: String, onBack: () -> Unit, onPlayAll: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveBounceIconButton(onClick = onBack, size = 46.dp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onPlayAll != null) {
            ExpressiveBounceIconButton(
                onClick = onPlayAll,
                size = 46.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play All")
            }
        }
    }
}

/** Full album list behind an album shelf ("Recently added", "Rediscover", ...). */
@Composable
private fun AlbumGridSubScreen(
    title: String,
    albums: List<SubsonicAlbum>,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().testTag("album_grid_page"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SubScreenHeader(title, "${albums.size} albums", onBack, onPlayAll = null)
        }
        items(albums, key = { it.id }) { album ->
            AlbumExpressiveCard(
                album = album,
                onClick = { onOpenAlbum(album.id) },
                onPlayClick = { onPlayAlbum(album.id, false) },
                fillWidth = true
            )
        }
    }
}

@Composable
private fun GenreOrFolderDetailSubScreen(
    title: String,
    subtitle: String,
    tracks: List<SubsonicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit
) {
    SelectableTracks(tracks)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader(title, subtitle, onBack, onPlayAll) }

        items(tracks, key = { it.id }) { track ->
            TrackExpressiveRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onPlay = { onPlayTrack(track) },
                onToggleStar = { onToggleStar(track) }
            )
        }

        item { Spacer(modifier = Modifier.height(150.dp)) }
    }
}

@Composable
fun AlbumDetailSubScreen(
    album: SubsonicAlbum,
    tracks: List<SubsonicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    downloadsMap: Map<String, SubsonicDownloadEntity> = emptyMap(),
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onDownloadAlbum: (() -> Unit)? = null,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit,
    onDownloadTrack: (SubsonicTrack) -> Unit = {},
    onRemoveDownload: (String) -> Unit = {},
    onPlayTrackNext: (SubsonicTrack) -> Unit = {},
    onAddTrackToQueue: (SubsonicTrack) -> Unit = {}
) {
    SelectableTracks(tracks)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("album_detail_subscreen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveBounceIconButton(
                    onClick = onBack,
                    size = 46.dp,
                    modifier = Modifier.testTag("album_detail_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val downloadActions = LocalTrackActions.current
                ExpressiveBounceIconButton(
                    // Every album page downloads, wherever it was opened from.
                    onClick = { onDownloadAlbum?.invoke() ?: downloadActions?.download(tracks) },
                    size = 46.dp,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("album_detail_download_button")
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "Download Album Offline")
                }
            }
        }

        item {
            FeaturedAlbumHeroCard(
                album = album,
                onOpenAlbum = {},
                onPlayAlbum = onPlayAll,
                onShuffleAlbum = onShuffleAll
            )
        }

        items(tracks, key = { it.id }) { track ->
            TrackExpressiveRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onPlay = { onPlayTrack(track) },
                onToggleStar = { onToggleStar(track) },
                downloadEntry = downloadsMap[track.id],
                onDownloadTrack = { onDownloadTrack(track) },
                onRemoveDownload = { onRemoveDownload(track.id) },
                onPlayNext = { onPlayTrackNext(track) },
                onAddToQueue = { onAddTrackToQueue(track) }
            )
        }

        item { Spacer(modifier = Modifier.height(150.dp)) }
    }
}

@Composable
private fun ArtistDetailSubScreen(
    artist: SubsonicArtist,
    tracks: List<SubsonicTrack>,
    albums: List<SubsonicAlbum>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayTrack: (SubsonicTrack) -> Unit,
    onToggleStar: (SubsonicTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onPlayAlbum: (String) -> Unit
) {
    val topTracks = remember(tracks) { tracks.sortedByDescending { it.playCount } }
    SelectableTracks(topTracks)
    var bioExpanded by rememberSaveable(artist.id) { mutableStateOf(false) }
    var bioOverflows by remember(artist.id, artist.bio) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ExpressiveBounceIconButton(onClick = onBack, size = 46.dp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        // Hero: circular portrait, name, stats, play
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveArtwork(
                    seedHex = artist.paletteSeedHex,
                    accentHex = artist.accentHex,
                    artStyleIndex = artist.artStyleIndex,
                    coverArtUrl = artist.coverArtUrl,
                    monogramText = artist.name,
                    shapeMode = ArtworkShapeMode.VINYL,
                    modifier = Modifier.size(176.dp)
                )
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        artist.genre.takeIf { it.isNotBlank() },
                        "${artist.albumCount} albums",
                        "${tracks.size} tracks"
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (topTracks.isNotEmpty()) {
                    Button(
                        onClick = { onPlayTrack(topTracks.first()) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("artist_play_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }

        // About: expandable biography (server bio, or Wikipedia when the server has none)
        if (artist.bio.isNotBlank()) item {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column(
                    modifier = Modifier
                        .clickable(enabled = artist.bio.isNotBlank()) { bioExpanded = !bioExpanded }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (artist.bio.isNotBlank()) {
                        Text(
                            text = artist.bio,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (!bioExpanded) bioOverflows = it.hasVisualOverflow }
                        )
                        if (bioOverflows || bioExpanded) {
                            Text(
                                text = if (bioExpanded) "Show less" else "Read more",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.id }) { album ->
                        AlbumExpressiveCard(
                            album = album,
                            onClick = { onOpenAlbum(album.id) },
                            onPlayClick = { onPlayAlbum(album.id) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Top tracks",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(topTracks, key = { it.id }) { track ->
            TrackExpressiveRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onPlay = { onPlayTrack(track) },
                onToggleStar = { onToggleStar(track) }
            )
        }

        item { Spacer(modifier = Modifier.height(150.dp)) }
    }
}

@Composable
private fun CreatePlaylistDialog(
    allTracks: List<SubsonicTrack>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create OpenSubsonic Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Description / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Select Tracks (${selectedIds.size} chosen)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allTracks.take(5).forEach { track ->
                        val checked = track.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(cornerShape(10.dp))
                                .clickable {
                                    if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                                }
                            )
                            Text(
                                text = "${track.title} — ${track.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, comment, selectedIds.toList()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_playlist_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Playlist")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dedicated Playlists Screen (3rd Primary Bottom Tab: Home - Library - Playlist - Favourites).
 */
@Composable
fun PlaylistsCollectionScreen(
    playlists: List<SubsonicPlaylist>,
    tracks: List<SubsonicTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onPlayPlaylist: (SubsonicPlaylist) -> Unit,
    onPlayTrack: (SubsonicTrack, List<SubsonicTrack>) -> Unit,
    onToggleStarTrack: (SubsonicTrack) -> Unit,
    onCreatePlaylist: (String, String, List<String>) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    viewMode: LibraryViewMode = LibraryViewMode.LIST,
    onSetViewMode: (LibraryViewMode) -> Unit = {},
    scrollToTopSignal: Int = 0
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SubsonicPlaylist?>(null) }
    val trackMap = remember(tracks) { tracks.associateBy { it.id } }

    val selectedPlaylist = playlists.find { it.id == selectedPlaylistId }
    if (selectedPlaylist != null) {
        BackHandler { selectedPlaylistId = null }
        val playlistTracks = selectedPlaylist.trackIds.mapNotNull { trackMap[it] }
        GenreOrFolderDetailSubScreen(
            title = selectedPlaylist.name,
            subtitle = listOf("${playlistTracks.size} tracks", selectedPlaylist.comment).filter { it.isNotBlank() }.joinToString(" • "),
            tracks = playlistTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { selectedPlaylistId = null },
            onPlayAll = { onPlayPlaylist(selectedPlaylist) },
            onPlayTrack = { trk -> onPlayTrack(trk, playlistTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 580.dp) 4 else 3
        val listState = rememberLazyListState()
        ScrollToTopOnSignal(listState, scrollToTopSignal)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("playlists_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (viewMode == LibraryViewMode.COMPACT_LIST) 2.dp else 12.dp)
        ) {
            item(key = "playlists_header") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${playlists.size} playlists",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = { showCreatePlaylistDialog = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("playlists_tab_create_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New")
                    }
                    ViewModeMenuButton(viewMode = viewMode, onSetViewMode = onSetViewMode)
                }
            }
            collectionEntries(
                playlists.distinctBy { it.id }.map { pl ->
                    pl.toEntry(trackMap, onOpen = { selectedPlaylistId = pl.id }, onPlay = { onPlayPlaylist(pl) }, onDelete = { pendingDelete = pl })
                },
                viewMode, columns
            )
            item { Spacer(modifier = Modifier.height(160.dp)) }
        }
    }

    pendingDelete?.let { pl -> DeletePlaylistDialog(pl, onConfirm = { onDeletePlaylist(pl.id) }, onDismiss = { pendingDelete = null }) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            allTracks = tracks,
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name, comment, ids ->
                onCreatePlaylist(name, comment, ids)
                showCreatePlaylistDialog = false
            }
        )
    }
}

private enum class SearchScopeTab(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
    GENRES_FOLDERS("Genres & Folders")
}

/**
 * Smart & Dynamic Live Search Results Screen.
 * Automatically surfaces categorized matches across Songs, Albums, Artists, Playlists, Genres, and Folders
 * as the user types in the top search bar, or presents a clear "No results found" empty state with
 * one-tap suggestions when a search query is not in the library.
 */
@Composable
fun SmartDynamicSearchScreen(
    searchQuery: String,
    allTracks: List<SubsonicTrack>,
    allAlbums: List<SubsonicAlbum>,
    allArtists: List<SubsonicArtist>,
    matchedTracks: List<SubsonicTrack>,
    matchedAlbums: List<SubsonicAlbum>,
    matchedArtists: List<SubsonicArtist>,
    matchedPlaylists: List<SubsonicPlaylist>,
    matchedGenres: List<SubsonicGenre>,
    matchedFolders: List<SubsonicFolder>,
    currentTrackId: String?,
    isPlaying: Boolean,
    selectedAlbumId: String?,
    selectedArtistId: String?,
    selectedGenreName: String?,
    selectedFolderPath: String?,
    onUpdateQuery: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPlayTrack: (SubsonicTrack, List<SubsonicTrack>) -> Unit,
    onToggleStarTrack: (SubsonicTrack) -> Unit,
    onOpenAlbum: (String?) -> Unit,
    onPlayAlbum: (String, Boolean) -> Unit,
    onOpenArtist: (String?) -> Unit,
    onOpenGenre: (String?) -> Unit,
    onOpenFolder: (String?) -> Unit,
    onPlayPlaylist: (SubsonicPlaylist) -> Unit
) {
    // Support drill-down into Album, Artist, Genre, or Folder directly from Search Results
    val detailAlbum = allAlbums.find { it.id == selectedAlbumId }
    if (detailAlbum != null) {
        BackHandler { onOpenAlbum(null) }
        val albumTracks = allTracks.filter { it.albumId == detailAlbum.id }
        AlbumDetailSubScreen(
            album = detailAlbum,
            tracks = albumTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenAlbum(null) },
            onPlayAll = { onPlayAlbum(detailAlbum.id, false) },
            onShuffleAll = { onPlayAlbum(detailAlbum.id, true) },
            onPlayTrack = { onPlayTrack(it, albumTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    val detailArtist = allArtists.find { it.id == selectedArtistId || it.name == selectedArtistId }
    if (detailArtist != null) {
        BackHandler { onOpenArtist(null) }
        val artistTracks = allTracks.filter { it.artist.equals(detailArtist.name, ignoreCase = true) }
        ArtistDetailSubScreen(
            artist = detailArtist,
            tracks = artistTracks,
            albums = allAlbums.filter { it.artist.equals(detailArtist.name, ignoreCase = true) },
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenArtist(null) },
            onPlayTrack = { onPlayTrack(it, artistTracks) },
            onToggleStar = onToggleStarTrack,
            onOpenAlbum = { onOpenAlbum(it) },
            onPlayAlbum = { onPlayAlbum(it, false) }
        )
        return
    }

    if (selectedGenreName != null) {
        BackHandler { onOpenGenre(null) }
        val genreTracks = allTracks.filter { it.hasGenre(selectedGenreName) }
        GenreOrFolderDetailSubScreen(
            title = selectedGenreName,
            subtitle = "${genreTracks.size} tracks in genre",
            tracks = genreTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenGenre(null) },
            onPlayAll = { if (genreTracks.isNotEmpty()) onPlayTrack(genreTracks.first(), genreTracks) },
            onPlayTrack = { onPlayTrack(it, genreTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    val detailFolder = matchedFolders.find { it.path == selectedFolderPath }
    if (detailFolder != null) {
        BackHandler { onOpenFolder(null) }
        GenreOrFolderDetailSubScreen(
            title = detailFolder.displayName,
            subtitle = detailFolder.path,
            tracks = detailFolder.tracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = { onOpenFolder(null) },
            onPlayAll = {
                if (detailFolder.tracks.isNotEmpty()) {
                    onPlayTrack(detailFolder.tracks.first(), detailFolder.tracks)
                }
            },
            onPlayTrack = { onPlayTrack(it, detailFolder.tracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    BackHandler { onClearSearch() }

    var activeScope by remember { mutableStateOf(SearchScopeTab.ALL) }
    val totalMatches = matchedTracks.size + matchedAlbums.size + matchedArtists.size +
        matchedPlaylists.size + matchedGenres.size + matchedFolders.size
    SelectableTracks(matchedTracks)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("smart_dynamic_search_results_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Scope Filter Pills Row
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val tabsWithCounts = listOf(
                    SearchScopeTab.ALL to totalMatches,
                    SearchScopeTab.SONGS to matchedTracks.size,
                    SearchScopeTab.ALBUMS to matchedAlbums.size,
                    SearchScopeTab.ARTISTS to matchedArtists.size,
                    SearchScopeTab.PLAYLISTS to matchedPlaylists.size,
                    SearchScopeTab.GENRES_FOLDERS to (matchedGenres.size + matchedFolders.size)
                )
                items(tabsWithCounts) { (tab, count) ->
                    val selected = activeScope == tab
                    SpringPressableSurface(
                        onClick = { activeScope = tab },
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        cornerRadius = 18.dp,
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("search_scope_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${tab.label} ($count)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        // Empty State when Search Query has 0 matches in the Library
        if (totalMatches == 0) {
            item {
                Surface(
                    shape = cornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("search_no_results_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = cornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = "No results found",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "No results found for \"${searchQuery.trim()}\"",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "No matching songs, albums, artists, playlists, genres, or folders were found in your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Try a smart filter or keyword:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val suggestions = listOf("FLAC", "Electronic", "Ambient", "Synth", "Jazz")
                            items(suggestions) { suggestion ->
                                SpringPressableSurface(
                                    onClick = { onUpdateQuery(suggestion) },
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    cornerRadius = 14.dp,
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("search_suggestion_${suggestion.lowercase()}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        SpringPressableSurface(
                            onClick = onClearSearch,
                            color = MaterialTheme.colorScheme.primary,
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("search_clear_query_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 22.dp)
                            ) {
                                Text(
                                    text = "Clear Search",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Top Match Highlight Card (when viewing All)
            val topTrack = matchedTracks.firstOrNull()
            val topAlbum = matchedAlbums.firstOrNull()
            val topArtist = matchedArtists.firstOrNull()
            if (activeScope == SearchScopeTab.ALL && (topTrack != null || topAlbum != null || topArtist != null)) {
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = cornerShape(26.dp)
                            )
                            .testTag("search_top_match_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when {
                                        topTrack != null -> onPlayTrack(topTrack, matchedTracks)
                                        topAlbum != null -> onOpenAlbum(topAlbum.id)
                                        topArtist != null -> onOpenArtist(topArtist.id)
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveArtwork(
                                seedHex = topTrack?.paletteSeedHex ?: topAlbum?.paletteSeedHex ?: topArtist?.paletteSeedHex ?: "#FF6B5B",
                                accentHex = topTrack?.accentHex ?: topAlbum?.accentHex ?: topArtist?.accentHex ?: "#70F5FF",
                                artStyleIndex = topTrack?.artStyleIndex ?: topAlbum?.artStyleIndex ?: topArtist?.artStyleIndex ?: 0,
                                coverArtUrl = topTrack?.coverArtUrl ?: topAlbum?.coverArtUrl ?: topArtist?.coverArtUrl.orEmpty(),
                                monogramText = topArtist?.name.orEmpty(),
                                shapeMode = ArtworkShapeMode.COOKIE,
                                modifier = Modifier.size(68.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = cornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = when {
                                            topTrack != null -> "TOP SONG MATCH"
                                            topAlbum != null -> "TOP ALBUM MATCH"
                                            else -> "TOP ARTIST MATCH"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = topTrack?.title ?: topAlbum?.name ?: topArtist?.name.orEmpty(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        topTrack != null -> "${topTrack.artist} • ${topTrack.album} • ${topTrack.suffix}"
                                        topAlbum != null -> "${topAlbum.artist} • ${topAlbum.year} • ${topAlbum.songCount} tracks"
                                        else -> "${topArtist?.genre} • ${topArtist?.albumCount} albums"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (topTrack != null || topAlbum != null) {
                                ExpressiveBounceIconButton(
                                    onClick = {
                                        if (topTrack != null) {
                                            onPlayTrack(topTrack, matchedTracks)
                                        } else if (topAlbum != null) {
                                            onPlayAlbum(topAlbum.id, false)
                                        }
                                    },
                                    size = 48.dp,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play Top Match")
                                }
                            }
                        }
                    }
                }
            }

            // Matching Songs Section
            if ((activeScope == SearchScopeTab.ALL || activeScope == SearchScopeTab.SONGS) && matchedTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Songs (${matchedTracks.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val displayedTracks = if (activeScope == SearchScopeTab.ALL) matchedTracks.take(8) else matchedTracks
                items(displayedTracks, key = { "search_trk_${it.id}" }) { track ->
                    TrackExpressiveRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onPlay = { onPlayTrack(track, matchedTracks) },
                        onToggleStar = { onToggleStarTrack(track) }
                    )
                }
            }

            // Matching Albums Section
            if ((activeScope == SearchScopeTab.ALL || activeScope == SearchScopeTab.ALBUMS) && matchedAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums (${matchedAlbums.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(matchedAlbums, key = { "search_alb_${it.id}" }) { album ->
                            AlbumExpressiveCard(
                                album = album,
                                onClick = { onOpenAlbum(album.id) },
                                onPlayClick = { onPlayAlbum(album.id, false) }
                            )
                        }
                    }
                }
            }

            // Matching Artists Section
            if ((activeScope == SearchScopeTab.ALL || activeScope == SearchScopeTab.ARTISTS) && matchedArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Artists (${matchedArtists.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(matchedArtists, key = { "search_art_${it.id}" }) { artist ->
                    SpringPressableSurface(
                        onClick = { onOpenArtist(artist.id) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        cornerRadius = 22.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveArtwork(
                                seedHex = artist.paletteSeedHex,
                                accentHex = artist.accentHex,
                                artStyleIndex = artist.artStyleIndex,
                                coverArtUrl = artist.coverArtUrl,
                                monogramText = artist.name,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${artist.genre} • ${artist.albumCount} albums • ${artist.trackCount} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Matching Playlists Section
            if ((activeScope == SearchScopeTab.ALL || activeScope == SearchScopeTab.PLAYLISTS) && matchedPlaylists.isNotEmpty()) {
                item {
                    Text(
                        text = "Playlists (${matchedPlaylists.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(matchedPlaylists, key = { "search_pl_${it.id}" }) { pl ->
                    SpringPressableSurface(
                        onClick = { onPlayPlaylist(pl) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        cornerRadius = 22.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpressiveArtwork(
                                seedHex = pl.paletteSeedHex,
                                accentHex = pl.accentHex,
                                artStyleIndex = pl.name.length % 5,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${pl.trackIds.size} tracks • ${pl.comment}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ExpressiveBounceIconButton(
                                onClick = { onPlayPlaylist(pl) },
                                size = 42.dp,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play ${pl.name}")
                            }
                        }
                    }
                }
            }

            // Matching Genres & Folders Section
            if ((activeScope == SearchScopeTab.ALL || activeScope == SearchScopeTab.GENRES_FOLDERS) &&
                (matchedGenres.isNotEmpty() || matchedFolders.isNotEmpty())
            ) {
                item {
                    Text(
                        text = "Genres & Folders (${matchedGenres.size + matchedFolders.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(matchedGenres, key = { "search_gen_${it.name}" }) { genre ->
                    SpringPressableSurface(
                        onClick = { onOpenGenre(genre.name) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = genre.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${genre.trackCount} tracks • ${genre.albumCount} albums",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(matchedFolders, key = { "search_fld_${it.path}" }) { folder ->
                    SpringPressableSurface(
                        onClick = { onOpenFolder(folder.path) },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = folder.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${folder.tracks.size} tracks • ${folder.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}


