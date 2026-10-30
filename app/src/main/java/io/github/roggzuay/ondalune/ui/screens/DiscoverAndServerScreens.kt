@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.ui.components.BackgroundPlaybackSettingsRow
import io.github.roggzuay.ondalune.ui.components.SupportDeveloperCard
import io.github.roggzuay.ondalune.ui.components.AboutLicencesRow
import io.github.roggzuay.ondalune.ui.theme.styleSpecFor
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.ui.components.ViewModeMenuButton
import io.github.roggzuay.ondalune.ui.components.collectionEntries
import io.github.roggzuay.ondalune.ui.components.LocalTrackSelection
import io.github.roggzuay.ondalune.ui.components.SelectableTracks
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import io.github.roggzuay.ondalune.ui.theme.LocalCornerScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.theme.monoLabelSmall
import io.github.roggzuay.ondalune.ui.components.MorphShape
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.Morph
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.text.style.TextAlign
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.AppIconPreviewFrameShape
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.data.model.CuratedColorPalette
import io.github.roggzuay.ondalune.data.model.DockStyle
import io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SleepTimerState
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.WidgetLayoutMode
import io.github.roggzuay.ondalune.data.model.WidgetThemeMode
import io.github.roggzuay.ondalune.ui.components.AppIconLauncherManager
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.components.SpringPressableSurface
import io.github.roggzuay.ondalune.ui.components.OndaluneBrandEmblem
import io.github.roggzuay.ondalune.ui.components.rememberHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.theme.parseHexColor
import io.github.roggzuay.ondalune.ui.viewmodel.FavouritesFilterTab
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Expandable Settings Section IDs for the Accordion List view in Settings.
 */
enum class SettingsExpandableSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    APP_ICON(
        title = "App icon",
        subtitle = "Colours, shapes and themed icons",
        icon = Icons.Filled.AutoAwesome
    ),
    EQ_BITRATE(
        title = "Sound & streaming",
        subtitle = "Equalizer, transcoding quality, downloads & cache",
        icon = Icons.Filled.Equalizer
    ),
    APPEARANCE_THEME(
        title = "Appearance",
        subtitle = "Interface style, colours, player background, dock",
        icon = Icons.Filled.Palette
    ),
    PLAYBACK_DSP(
        title = "Playback",
        subtitle = "Speed & pitch, shuffle, refresh rate, haptics",
        icon = Icons.Filled.Tune
    ),
    WIDGET_NOTIFICATION(
        title = "Widget & notification",
        subtitle = "Home screen widget and media controls",
        icon = Icons.Filled.Widgets
    ),
    SERVER_HUB(
        title = "Servers",
        subtitle = "Connection, library sync, add a server",
        icon = Icons.Filled.Dns
    )
}


/**
 * Dedicated Favourites Screen (4th Primary Tab).
 */
@Composable
fun FavouritesCollectionScreen(
    tracks: List<SubsonicTrack>,
    albums: List<SubsonicAlbum>,
    activeFilter: FavouritesFilterTab,
    currentTrackId: String?,
    isPlaying: Boolean,
    selectedAlbumId: String? = null,
    onSelectFilter: (FavouritesFilterTab) -> Unit,
    onPlayTrack: (SubsonicTrack, List<SubsonicTrack>) -> Unit,
    onToggleStarTrack: (SubsonicTrack) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onCloseAlbumDetail: () -> Unit = {},
    onPlayAlbum: (String, Boolean) -> Unit,
    onSmartShuffleTracks: (List<SubsonicTrack>) -> Unit = {},
    viewMode: LibraryViewMode = LibraryViewMode.LIST,
    onSetViewMode: (LibraryViewMode) -> Unit = {},
    scrollToTopSignal: Int = 0
) {
    val activeAlbum = remember(albums, selectedAlbumId) {
        if (selectedAlbumId == null) null else albums.find { it.id == selectedAlbumId }
    }
    if (activeAlbum != null) {
        val albumTracks = remember(tracks, activeAlbum.id) {
            tracks.filter { it.albumId == activeAlbum.id }.sortedBy { it.trackNumber }
        }
        AlbumDetailSubScreen(
            album = activeAlbum,
            tracks = albumTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onBack = onCloseAlbumDetail,
            onPlayAll = { onPlayAlbum(activeAlbum.id, false) },
            onShuffleAll = { onPlayAlbum(activeAlbum.id, true) },
            onPlayTrack = { trk -> onPlayTrack(trk, albumTracks) },
            onToggleStar = onToggleStarTrack
        )
        return
    }

    val starredTracks = remember(tracks) { tracks.filter { it.starred } }
    val starredAlbums = remember(albums) { albums.filter { it.starred } }
    var optionsTrack by remember { mutableStateOf<SubsonicTrack?>(null) }
    val selection = LocalTrackSelection.current
    SelectableTracks(if (activeFilter == FavouritesFilterTab.ALBUMS) emptyList() else starredTracks)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 580.dp) 4 else 3
        val listState = rememberLazyListState()
        ScrollToTopOnSignal(listState, scrollToTopSignal)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("favourites_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (viewMode == LibraryViewMode.COMPACT_LIST) 2.dp else 12.dp)
        ) {
            item(key = "fav_header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${starredTracks.size} songs • ${starredAlbums.size} albums",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        ViewModeMenuButton(viewMode = viewMode, onSetViewMode = onSetViewMode)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { starredTracks.firstOrNull()?.let { onPlayTrack(it, starredTracks) } },
                            enabled = starredTracks.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("favourites_play_all_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                        FilledTonalButton(
                            onClick = { onSmartShuffleTracks(starredTracks) },
                            enabled = starredTracks.isNotEmpty(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("favourites_shuffle_button")
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FavouritesFilterTab.entries.forEach { tab ->
                            FilterChip(
                                selected = tab == activeFilter,
                                onClick = { onSelectFilter(tab) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("fav_filter_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if ((activeFilter == FavouritesFilterTab.ALL || activeFilter == FavouritesFilterTab.ALBUMS) && starredAlbums.isNotEmpty()) {
                item(key = "fav_albums_title") { Text("Albums", style = MaterialTheme.typography.titleLarge) }
                collectionEntries(
                    starredAlbums.map { album -> album.toEntry({ onOpenAlbum(album.id) }, { onPlayAlbum(album.id, false) }) },
                    viewMode, columns
                )
            }

            if (activeFilter == FavouritesFilterTab.ALL || activeFilter == FavouritesFilterTab.TRACKS) {
                item(key = "fav_tracks_title") { Text("Songs", style = MaterialTheme.typography.titleLarge) }
                trackCollection(
                    tracks = starredTracks,
                    mode = viewMode,
                    columns = columns,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onPlay = { onPlayTrack(it, starredTracks) },
                    onToggleStar = onToggleStarTrack,
                    onLongPress = { optionsTrack = it },
                    selection = selection,
                    keyPrefix = "fav_trk"
                )
            }

            item { Spacer(modifier = Modifier.height(160.dp)) }
        }
    }
    optionsTrack?.let { TrackOptionsPopup(it) { optionsTrack = null } }
}

/**
 * Expandable List Settings & Customization Hub Screen:
 * - Viewed as a clean vertical list where each section is expandable/collapsible (no pill-shaped category filter tabs).
 * - Includes:
 *   1. Equalizer & Bitrate Performance
 *   2. Theme, Material You Expressive Unified Color & Player Background Animation Style
 *   3. Music Player Behaviour, Smart Shuffle & Audio DSP
 *   4. Dynamic Media Notification & Home Screen Widget
 *   5. OpenSubsonic Server Hub
 */
@Composable
fun SettingsCustomizationScreen(
    settings: PlayerCustomizationSettings,
    speedPitchState: SpeedPitchState,
    reverbState: ReverbState = ReverbState(),
    sleepTimerState: SleepTimerState,
    artworkShapeMode: ArtworkShapeMode,
    activeEqPreset: EqualizerPreset,
    servers: List<SubsonicServerConfig>,
    activeServer: SubsonicServerConfig,
    pingDiagnostic: PingDiagnosticResult?,
    isSyncing: Boolean,
    onSetAppIconVariant: (AppIconVariant) -> Unit = {},
    onOpenAppIconSettings: () -> Unit = {},
    onSetDockStyle: (DockStyle) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetUseMaterialYou: (Boolean) -> Unit,
    onSetUnifiedMaterialYouColor: (Boolean) -> Unit = {},
    onSetCuratedPalette: (CuratedColorPalette) -> Unit = {},
    onSetCornerStylePreset: (CornerStylePreset) -> Unit = {},
    onSetInterfaceStyle: (InterfaceStyle) -> Unit = {},
    onSetHeaderFont: (AppFont) -> Unit = {},
    onSetBodyFont: (AppFont) -> Unit = {},
    onSetArtworkCut: (io.github.roggzuay.ondalune.data.model.ArtworkCut) -> Unit = {},
    onSetHomeSections: (List<HomeSection>) -> Unit = {},
    onResetHomeSections: () -> Unit = {},
    onSetHarmonizeArtwork: (Boolean) -> Unit,
    onSetPlayerBackgroundStyle: (PlayerBackgroundAnimationStyle) -> Unit = {},
    onSetArtworkShape: (ArtworkShapeMode) -> Unit,
    onSetAnimatedSquiggly: (Boolean) -> Unit,
    onSetHapticFeedback: (Boolean) -> Unit = {},
    onSetHighRefreshRate120Hz: (Boolean) -> Unit = {},
    shuffleMode: ShuffleMode = ShuffleMode.OFF,
    onSetShuffleMode: (ShuffleMode) -> Unit = {},
    onSetSmartShuffleProfile: (SmartShuffleProfile) -> Unit = {},
    onSelectReverbPreset: (ReverbPreset) -> Unit = {},
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetGapless: (Boolean) -> Unit,
    onSetReplayGain: (ReplayGainMode) -> Unit,
    onSetAutoScrobble: (Boolean) -> Unit,
    onSetPauseOnHeadphoneDisconnect: (Boolean) -> Unit = {},
    onSetFallbackArtistToAlbumArt: (Boolean) -> Unit = {},
    onUpdateWifiQuality: (String, Int) -> Unit,
    onUpdateMobileQuality: (String, Int) -> Unit,
    onSetDownloadSpeedLimit: (DownloadSpeedLimit) -> Unit,
    onSetReadAheadBuffer: (Int) -> Unit,
    onSetWidgetLayoutMode: (WidgetLayoutMode) -> Unit = {},
    onSetWidgetThemeMode: (WidgetThemeMode) -> Unit = {},
    onSetWidgetShowArtwork: (Boolean) -> Unit = {},
    onSetWidgetShowFavoriteButton: (Boolean) -> Unit = {},
    onSetWidgetShowLyrics: (Boolean) -> Unit = {},
    onRestoreDefaults: () -> Unit = {},
    onOpenSonicLab: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit = {},
    onStartSleepTimerMinutes: (Int) -> Unit = {},
    onStartSleepTimerEndOfTrack: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSelectEqPreset: (EqualizerPreset) -> Unit = {},
    onPinHomeWidget: () -> Unit = {},
    offlineStorageSummary: io.github.roggzuay.ondalune.data.model.OfflineStorageSummary = io.github.roggzuay.ondalune.data.model.OfflineStorageSummary(),
    onClearOfflineDownloads: () -> Unit = {},
    onClearTempAudioCache: () -> Unit = {},
    onBack: () -> Unit = {},
    onActivateServer: (SubsonicServerConfig) -> Unit,
    onTestAndSaveServer: (String, String, String, String, Boolean, String, Int) -> Unit
) {
    val context = LocalContext.current
    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useTokenAuth by remember { mutableStateOf(true) }

    // Track which expandable list sections are currently expanded
    val expandedMap = remember {
        mutableStateMapOf(
            SettingsExpandableSection.APP_ICON to true,
            SettingsExpandableSection.EQ_BITRATE to true,
            SettingsExpandableSection.APPEARANCE_THEME to true,
            SettingsExpandableSection.PLAYBACK_DSP to false,
            SettingsExpandableSection.WIDGET_NOTIFICATION to false,
            SettingsExpandableSection.SERVER_HUB to false
        )
    }

    // Compact root list of categories; a tapped category opens as its own page.
    var openSection by rememberSaveable { mutableStateOf<SettingsExpandableSection?>(null) }
    BackHandler(enabled = openSection != null) { openSection = null }

    AnimatedContent(
        targetState = openSection,
        transitionSpec = {
            val forward = targetState != null
            val slide = tween<IntOffset>(300, easing = FastOutSlowInEasing)
            (slideInHorizontally(slide) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(210, delayMillis = 90)))
                .togetherWith(slideOutHorizontally(slide) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(90)))
        },
        label = "settingsPage"
    ) { shownSection ->
    CompositionLocalProvider(LocalSettingsPage provides SettingsPageState(shownSection) { openSection = it }) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_customization_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (shownSection == null) 3.dp else 12.dp)
    ) {
        if (shownSection != null) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBounceIconButton(
                    onClick = { openSection = null },
                    size = 44.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = shownSection.title, style = MaterialTheme.typography.titleLarge)
            }
        }

        // 0. Expandable Section: Simplified App Icon Selector with Live Preview
        if (shownSection == null || shownSection == SettingsExpandableSection.APP_ICON) item {
            val isExpanded = expandedMap[SettingsExpandableSection.APP_ICON] == true
            val activeIcon = settings.appIconVariant
            val iconAccent = remember(activeIcon) { parseHexColor(activeIcon.accentHex) }

            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.APP_ICON,
                badgeText = activeIcon.shortLabel,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.APP_ICON] = !isExpanded
                },
                testTag = "settings_section_app_icon"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("settings_app_icon_content")
                ) {
                    // Simplified Live Icon Preview Card
                    Surface(
                        shape = cornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = iconAccent.copy(alpha = 0.55f),
                                shape = cornerShape(24.dp)
                            )
                            .testTag("settings_app_icon_live_preview")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            iconAccent.copy(alpha = 0.20f),
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    )
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .border(
                                        width = 2.dp,
                                        color = iconAccent.copy(alpha = 0.75f),
                                        shape = RoundedCornerShape(percent = 24)
                                    )
                            ) {
                                OndaluneBrandEmblem(
                                    size = 86.dp,
                                    variant = activeIcon,
                                    modifier = Modifier.testTag("settings_app_icon_preview_image")
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = cornerShape(10.dp),
                                    color = iconAccent.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = "LIVE ICON PREVIEW",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = activeIcon.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.testTag("settings_app_icon_preview_title")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeIcon.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Simplified 2x3 Grid of Selectable App Icon Versions
                    val rows = remember { AppIconVariant.entries.chunked(3) }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { rowVariants ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowVariants.forEach { variant ->
                                    val selected = variant == activeIcon
                                    val variantColor = parseHexColor(variant.accentHex)
                                    SpringPressableSurface(
                                        onClick = { onSetAppIconVariant(variant) },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        cornerRadius = 20.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) {
                                                    variantColor
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                },
                                                shape = cornerShape(20.dp)
                                            )
                                            .testTag("app_icon_option_${variant.name.lowercase()}")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(contentAlignment = Alignment.TopEnd) {
                                                OndaluneBrandEmblem(
                                                    size = 54.dp,
                                                    variant = variant
                                                )
                                                if (selected) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Filled.CheckCircle,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = variant.shortLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Button to open the full dedicated 'App Icon Settings' Screen & Preview Gallery
                    SpringPressableSurface(
                        onClick = onOpenAppIconSettings,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        cornerRadius = 18.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_app_icon_screen_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Open App Icon Settings & Preview Gallery",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Inspect adaptive shapes, home screen dock & Activity Alias state",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Surface(
                                shape = cornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Gallery",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Expandable Section: Equalizer & Bitrate Performance
        if (shownSection == null || shownSection == SettingsExpandableSection.EQ_BITRATE) item {
            val isExpanded = expandedMap[SettingsExpandableSection.EQ_BITRATE] == true
            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.EQ_BITRATE,
                badgeText = activeEqPreset.label,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.EQ_BITRATE] = !isExpanded
                },
                testTag = "settings_section_eq_bitrate"
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("settings_eq_bitrate_menu_card")
                ) {
                    SpringPressableSurface(
                        onClick = onOpenSonicLab,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        cornerRadius = 22.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_sonic_lab_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = LocalContentColor.current,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Open Equalizer & Bitrate Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Preset: ${activeEqPreset.label} • Stream: ${if (activeServer.maxBitRate == 0) "RAW Lossless" else "${activeServer.transcodeFormat} ${activeServer.maxBitRate} kbps"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Surface(
                                shape = cornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "Open EQ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Quick Acoustic EQ Presets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(EqualizerPreset.entries) { preset ->
                            val selected = preset == activeEqPreset
                            SpringPressableSurface(
                                onClick = { onSelectEqPreset(preset) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                cornerRadius = 16.dp,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Wi-Fi / LAN Streaming Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val wifiProfiles = listOf(
                        Triple("RAW", 0, "Lossless Bit-Perfect (RAW FLAC / ALAC)"),
                        Triple("OPUS", 320, "Opus 320 kbps Studio Transparent"),
                        Triple("MP3", 256, "MP3 256 kbps High Compatibility"),
                        Triple("MP3", 192, "MP3 192 kbps Balanced")
                    )
                    wifiProfiles.forEach { (fmt, br, label) ->
                        val active = activeServer.transcodeFormat == fmt && activeServer.maxBitRate == br
                        SpringPressableSurface(
                            onClick = { onUpdateWifiQuality(fmt, br) },
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi_quality_${fmt.lowercase()}_$br")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, style = MaterialTheme.typography.labelLarge)
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Cellular / Mobile Streaming Quality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val mobileProfiles = listOf(
                        Triple("OPUS", 320, "Opus 320 kbps High Fidelity"),
                        Triple("OPUS", 192, "Opus 192 kbps Recommended"),
                        Triple("OPUS", 128, "Opus 128 kbps Data Saver")
                    )
                    mobileProfiles.forEach { (fmt, br, label) ->
                        val active = settings.mobileTranscodeFormat == fmt && settings.mobileMaxBitRate == br
                        SpringPressableSurface(
                            onClick = { onUpdateMobileQuality(fmt, br) },
                            color = if (active) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 18.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = label, style = MaterialTheme.typography.labelLarge)
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Download & Pre-Cache Bandwidth Limit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DownloadSpeedLimit.entries.forEach { limit ->
                        val active = settings.downloadSpeedLimit == limit
                        SpringPressableSurface(
                            onClick = { onSetDownloadSpeedLimit(limit) },
                            color = if (active) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 18.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = limit.label, style = MaterialTheme.typography.labelLarge)
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Read-Ahead Track Cache", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 10).forEach { count ->
                                val selected = settings.readAheadTrackBuffer == count
                                SpringPressableSurface(
                                    onClick = { onSetReadAheadBuffer(count) },
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    cornerRadius = 12.dp
                                ) {
                                    Text(
                                        text = "$count tracks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Offline Downloads & Storage", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Offline tracks: ${offlineStorageSummary.completedTrackCount} (${offlineStorageSummary.formattedOfflineSize}) • Temp cache: ${offlineStorageSummary.formattedCacheSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = onClearOfflineDownloads,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_clear_offline_downloads_button")
                            ) {
                                Text("Clear Offline", style = MaterialTheme.typography.labelMedium)
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = onClearTempAudioCache,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_clear_temp_cache_button")
                            ) {
                                Text("Clear Cache", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // 2. Expandable Section: Theme, Material You Unified Color & Player Background Animation
        if (shownSection == null || shownSection == SettingsExpandableSection.APPEARANCE_THEME) item {
            val isExpanded = expandedMap[SettingsExpandableSection.APPEARANCE_THEME] == true
            val modeBadge = if (settings.unifiedMaterialYouColor) {
                "Material You Unified"
            } else {
                "Dynamic Song Theme"
            }
            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.APPEARANCE_THEME,
                badgeText = modeBadge,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.APPEARANCE_THEME] = !isExpanded
                },
                testTag = "settings_section_appearance"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ThemeGallery(selected = settings.interfaceStyle, onSelect = onSetInterfaceStyle)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Text(
                        text = "Fonts",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FontPicker("Headings", settings.headerFont, display = true, onSelect = onSetHeaderFont, tag = "font_header")
                    FontPicker("Songs & text", settings.bodyFont, display = false, onSelect = onSetBodyFont, tag = "font_body")

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Text(
                        text = "Artwork shape",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ArtworkShapePicker(selected = settings.artworkCut, onSelect = onSetArtworkCut)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Text(
                        text = "Home layout",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HomeLayoutEditor(
                        sections = settings.homeSections.ifEmpty { styleSpecFor(settings.interfaceStyle).defaultHome },
                        isCustom = settings.homeSections.isNotEmpty(),
                        onChange = onSetHomeSections,
                        onReset = onResetHomeSections
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Text(
                        text = "Color Palette Mode (App, Player, Notification & Widget)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Option A: Material You Expressive (1 Unified Color from Phone Theme)
                    SpringPressableSurface(
                        onClick = { onSetUnifiedMaterialYouColor(true) },
                        color = if (settings.unifiedMaterialYouColor) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("palette_mode_unified_material_you")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                tint = LocalContentColor.current
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Material You Expressive (1 Unified Phone Color)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Unifies the entire app, player, notification & widget into 1 cohesive color from your phone's current theme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.78f)
                                )
                            }
                            if (settings.unifiedMaterialYouColor) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }

                    // Option B: Dynamic Song Artwork Palette (Default)
                    SpringPressableSurface(
                        onClick = {
                            onSetUnifiedMaterialYouColor(false)
                            onSetHarmonizeArtwork(true)
                        },
                        color = if (!settings.unifiedMaterialYouColor) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        cornerRadius = 20.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("palette_mode_song_artwork")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = LocalContentColor.current
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic Song Artwork Palette",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Dynamically extracts and morphs app, notification & widget colors from the currently playing song",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.78f)
                                )
                            }
                            if (!settings.unifiedMaterialYouColor) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Player Background Animation Style Selector
                    Text(
                        text = "Player Background Animation Style",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    PlayerBackgroundAnimationStyle.entries.forEach { bgStyle ->
                        val selected = settings.playerBackgroundStyle == bgStyle
                        SpringPressableSurface(
                            onClick = { onSetPlayerBackgroundStyle(bgStyle) },
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 18.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_bg_style_${bgStyle.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bgStyle.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = bgStyle.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.78f)
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Text(
                        text = "Navigation Dock Style",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DockStyle.entries.forEach { style ->
                            val selected = settings.dockStyle == style
                            SpringPressableSurface(
                                onClick = { onSetDockStyle(style) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                cornerRadius = 20.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dock_style_${style.name.lowercase()}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = style.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = LocalContentColor.current,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = style.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.78f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Theme Brightness Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = settings.themeMode == mode
                            SpringPressableSurface(
                                onClick = { onSetThemeMode(mode) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("theme_mode_${mode.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = mode.label.split(" ").last(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }

                    SettingsToggleRow(
                        title = "Material You Dynamic Color (Android 12+)",
                        subtitle = "Extract system wallpaper Monet palette as base theme",
                        checked = settings.useMaterialYouDynamicColor,
                        onCheckedChange = onSetUseMaterialYou,
                        testTag = "toggle_material_you"
                    )

                    SettingsToggleRow(
                        title = "Artwork Color Harmonization",
                        subtitle = "Morph player & surface accents based on current song cover art",
                        checked = settings.harmonizePlayerWithArtwork,
                        onCheckedChange = onSetHarmonizeArtwork,
                        testTag = "toggle_harmonize_artwork"
                    )

                    Text(
                        text = "Curated Accent Palette (When Material You is Off)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CuratedColorPalette.entries) { palette ->
                            val selected = !settings.useMaterialYouDynamicColor && !settings.unifiedMaterialYouColor && settings.curatedPalette == palette
                            SpringPressableSurface(
                                onClick = { onSetCuratedPalette(palette) },
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("curated_palette_${palette.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                    Text(
                                        text = palette.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Corner Radius & Shape Scale",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CornerStylePreset.entries.forEach { preset ->
                            val selected = settings.cornerStylePreset == preset
                            SpringPressableSurface(
                                onClick = { onSetCornerStylePreset(preset) },
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("corner_preset_${preset.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = preset.label.split(" ").first(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Expandable Section: Music Player Behaviour & DSP
        if (shownSection == null || shownSection == SettingsExpandableSection.PLAYBACK_DSP) item {
            val isExpanded = expandedMap[SettingsExpandableSection.PLAYBACK_DSP] == true
            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.PLAYBACK_DSP,
                badgeText = if (settings.highRefreshRate120Hz) "120Hz • ${shuffleMode.shortLabel}" else shuffleMode.shortLabel,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.PLAYBACK_DSP] = !isExpanded
                },
                testTag = "settings_section_playback_dsp"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackgroundPlaybackSettingsRow()
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    // Speed & Pitch DSP Card inside Settings
                    SpringPressableSurface(
                        onClick = onOpenSpeedPitchSheet,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        cornerRadius = 22.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_speed_pitch_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Studio Speed & Pitch DSP (0.8x – 2.0x)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Speed %.2fx • Pitch %.2fx (%+.1f st) • Reverb: %s",
                                        speedPitchState.speed,
                                        speedPitchState.effectivePitch,
                                        speedPitchState.effectiveSemitones,
                                        reverbState.preset.label
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Live Studio Reverb DSP Preset",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReverbPreset.entries) { preset ->
                            val selected = reverbState.preset == preset
                            SpringPressableSurface(
                                onClick = { onSelectReverbPreset(preset) },
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("settings_reverb_preset_${preset.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Smart Shuffle Mode & Personalization Profile
                    Surface(
                        shape = cornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Smart Shuffle Personalization",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = shuffleMode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ShuffleMode.entries.forEach { mode ->
                                    val selected = mode == shuffleMode
                                    SpringPressableSurface(
                                        onClick = { onSetShuffleMode(mode) },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        cornerRadius = 14.dp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("settings_shuffle_mode_${mode.name.lowercase()}")
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = mode.shortLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "Smart Shuffle Weighting Profile",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            SmartShuffleProfile.entries.forEach { profile ->
                                val selected = settings.smartShuffleProfile == profile
                                SpringPressableSurface(
                                    onClick = { onSetSmartShuffleProfile(profile) },
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    cornerRadius = 16.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("smart_shuffle_profile_${profile.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profile.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = profile.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = LocalContentColor.current.copy(alpha = 0.78f)
                                            )
                                        }
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = LocalContentColor.current,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Now Playing Artwork Shape",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArtworkShapeMode.entries.forEach { shape ->
                            val selected = shape == artworkShapeMode
                            SpringPressableSurface(
                                onClick = { onSetArtworkShape(shape) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                cornerRadius = 16.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = shape.label.split(" ").last(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Crossfade between songs",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (settings.crossfadeSeconds == 0) "Off" else "${settings.crossfadeSeconds} s",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = settings.crossfadeSeconds.toFloat(),
                            onValueChange = { onSetCrossfadeSeconds(it.toInt()) },
                            valueRange = 0f..12f,
                            steps = 5
                        )
                        Text(
                            text = "Each song fades into the next. Songs that follow each other on the same album play gaplessly instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SettingsToggleRow(
                        title = "Gapless playback",
                        subtitle = "Buffers the next song so it starts on the very next beat, with no silence in between",
                        checked = settings.gaplessPlayback,
                        onCheckedChange = onSetGapless,
                        testTag = "toggle_gapless"
                    )

                    SettingsToggleRow(
                        title = "Animated Squiggly Waveform Scrubber",
                        subtitle = "Animate the progress wave while audio is actively playing",
                        checked = settings.animatedSquigglyWave,
                        onCheckedChange = onSetAnimatedSquiggly,
                        testTag = "toggle_squiggly"
                    )

                    SettingsToggleRow(
                        title = "Tactile Haptic Feedback Patterns",
                        subtitle = "Subtle haptics for viscous-glue controls, play/pause transitions, and squiggly waveform scrubbing",
                        checked = settings.hapticWobbleButtons,
                        onCheckedChange = onSetHapticFeedback,
                        testTag = "toggle_haptics"
                    )

                    SettingsToggleRow(
                        title = "120Hz ProMotion / LTPO Display Mode",
                        subtitle = "Negotiate maximum hardware refresh rate (120Hz) & VSYNC frame-paced wave physics",
                        checked = settings.highRefreshRate120Hz,
                        onCheckedChange = onSetHighRefreshRate120Hz,
                        testTag = "toggle_120hz_refresh"
                    )

                    SettingsToggleRow(
                        title = "OpenSubsonic Live Scrobbling",
                        subtitle = "Report Now Playing & completed listens via /rest/scrobble.view",
                        checked = settings.autoScrobble,
                        onCheckedChange = onSetAutoScrobble,
                        testTag = "toggle_scrobble"
                    )

                    Text(
                        text = "ReplayGain (volume levelling)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Uses the ReplayGain tags your server reads from your files. Songs without them play unchanged, and loud songs are never pushed into clipping.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ReplayGainMode.entries.forEach { rg ->
                        val selected = settings.replayGainMode == rg
                        SpringPressableSurface(
                            onClick = { onSetReplayGain(rg) },
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = rg.label, style = MaterialTheme.typography.bodyMedium)
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Expandable Section: Dynamic Notification & Home Screen Widget
        if (shownSection == null || shownSection == SettingsExpandableSection.WIDGET_NOTIFICATION) item {
            val isExpanded = expandedMap[SettingsExpandableSection.WIDGET_NOTIFICATION] == true
            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.WIDGET_NOTIFICATION,
                badgeText = "Active",
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.WIDGET_NOTIFICATION] = !isExpanded
                },
                testTag = "settings_section_widget_notification"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (settings.unifiedMaterialYouColor) {
                            "Both your Notification Shade Player and Home Screen Widget are dynamically tinted with your phone's 1 Unified Material You color."
                        } else {
                            "Both your Notification Shade Player and Home Screen Widget dynamically change color to match the currently playing song's artwork."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Live Dynamic Widget Preview Card inside Settings
                    Surface(
                        shape = cornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                shape = cornerShape(24.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (settings.unifiedMaterialYouColor) {
                                        "MATERIAL YOU UNIFIED • MEDIA & WIDGET"
                                    } else {
                                        "DYNAMIC SONG PALETTE • MEDIA & WIDGET"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Persistent Media Notification & 4×2 Home Widget",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }

                    Text(
                        text = "Home Widget Layout Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetLayoutMode.entries.forEach { mode ->
                            val selected = settings.widgetLayoutMode == mode
                            SpringPressableSurface(
                                onClick = { onSetWidgetLayoutMode(mode) },
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cornerRadius = 14.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("widget_layout_${mode.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = mode.label.split(" ").first(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Home Widget Theme Appearance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetThemeMode.entries.forEach { wTheme ->
                            val selected = settings.widgetThemeMode == wTheme
                            SpringPressableSurface(
                                onClick = { onSetWidgetThemeMode(wTheme) },
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                cornerRadius = 14.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("widget_theme_${wTheme.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = wTheme.label.split(" ").first(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }

                    SettingsToggleRow(
                        title = "Show Album Artwork on Home Widget",
                        subtitle = "Display rounded album cover art in the home screen widget",
                        checked = settings.widgetShowArtwork,
                        onCheckedChange = onSetWidgetShowArtwork,
                        testTag = "toggle_widget_artwork"
                    )

                    SettingsToggleRow(
                        title = "Show favourite button on the widget",
                        subtitle = "A heart to favourite the playing track from your home screen",
                        checked = settings.widgetShowFavoriteButton,
                        onCheckedChange = onSetWidgetShowFavoriteButton,
                        testTag = "toggle_widget_favorite"
                    )

                    SettingsToggleRow(
                        title = "Show lyrics on big widgets",
                        subtitle = "Tall widgets show the current lyric line, synced to the song",
                        checked = settings.widgetShowLyrics,
                        onCheckedChange = onSetWidgetShowLyrics,
                        testTag = "toggle_widget_lyrics"
                    )

                    Button(
                        onClick = {
                            OndaluneMediaBridge.requestPinHomeScreenWidget(context)
                            onPinHomeWidget()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pin_home_widget_button")
                    ) {
                        Icon(Icons.Filled.Widgets, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add widget to home screen", maxLines = 1)
                    }
                }
            }
        }

        // 5. Expandable Section: OpenSubsonic Server Hub
        if (shownSection == null || shownSection == SettingsExpandableSection.SERVER_HUB) item {
            val isExpanded = expandedMap[SettingsExpandableSection.SERVER_HUB] == true
            ExpandableSettingsSectionCard(
                section = SettingsExpandableSection.SERVER_HUB,
                badgeText = activeServer.name,
                isExpanded = isExpanded,
                onToggleExpand = {
                    expandedMap[SettingsExpandableSection.SERVER_HUB] = !isExpanded
                },
                testTag = "settings_section_server"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = cornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeServer.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = activeServer.baseUrl,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                }
                            }

                            val diag = pingDiagnostic
                            if (diag != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = diag.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (diag.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    servers.forEach { srv ->
                        val isCurrent = srv.id == activeServer.id
                        SpringPressableSurface(
                            onClick = { onActivateServer(srv) },
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            cornerRadius = 20.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("server_node_${srv.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dns,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = srv.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${srv.baseUrl} • user: ${srv.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalContentColor.current.copy(alpha = 0.78f)
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Active Node",
                                        tint = LocalContentColor.current
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    Text(
                        text = "Connect New OpenSubsonic Server",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("Server Label (e.g. Home Navidrome)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_name_input")
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server Base URL (https://...)") },
                        singleLine = true,
                        supportingText = if (serverUrl.trim().startsWith("http://", ignoreCase = true)) {
                            { Text("Plain HTTP isn't encrypted. Fine on your home network; use https:// over the internet.") }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("server_username_input")
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password / Token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("server_password_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "MD5 Salt + Token Auth (t & s)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "Standard OpenSubsonic v1.16.1 authentication",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = useTokenAuth,
                            onCheckedChange = { useTokenAuth = it },
                            modifier = Modifier.testTag("token_auth_switch")
                        )
                    }

                    Button(
                        onClick = {
                            onTestAndSaveServer(
                                serverName,
                                serverUrl,
                                username,
                                password,
                                useTokenAuth,
                                activeServer.transcodeFormat,
                                activeServer.maxBitRate
                            )
                        },
                        enabled = serverUrl.length > 8 && username.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("test_and_save_server_button")
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ping & Connect OpenSubsonic Server")
                    }
                }
            }
        }

        if (shownSection == null) item {
            Spacer(modifier = Modifier.height(12.dp))
            SupportDeveloperCard()
            Spacer(modifier = Modifier.height(12.dp))
            AboutLicencesRow()
        }

        // Restore Default Appearance & DSP Settings (Preserves Servers & Library)
        if (shownSection == null) item {
            Spacer(modifier = Modifier.height(12.dp))
            SpringPressableSurface(
                onClick = onRestoreDefaults,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                cornerRadius = 22.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("restore_defaults_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Restore default settings",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
    }
    }
}

private class SettingsPageState(val open: SettingsExpandableSection?, val onOpen: (SettingsExpandableSection?) -> Unit)

private val LocalSettingsPage = compositionLocalOf { SettingsPageState(null) {} }

/**
 * Expandable List Section Card used in Settings:
 * Presents each settings group as a clean list item row with an animated chevron and
 * smoothly expands/collapses its inner settings controls when tapped.
 */
@Composable
private fun ExpandableSettingsSectionCard(
    section: SettingsExpandableSection,
    @Suppress("UNUSED_PARAMETER") badgeText: String,
    @Suppress("UNUSED_PARAMETER") isExpanded: Boolean,
    @Suppress("UNUSED_PARAMETER") onToggleExpand: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    val page = LocalSettingsPage.current
    if (page.open == section) {
        // Opened as its own page: just the controls, on one quiet surface.
        Surface(
            shape = cornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().testTag(testTag)
        ) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
        return
    }

    // Root list: one compact row per category, grouped into a single rounded block.
    val first = section == SettingsExpandableSection.entries.first()
    val last = section == SettingsExpandableSection.entries.last()
    val big = 24.dp * LocalCornerScale.current
    val small = 6.dp * LocalCornerScale.current
    Surface(
        onClick = { page.onOpen(section) },
        shape = RoundedCornerShape(
            topStart = if (first) big else small, topEnd = if (first) big else small,
            bottomStart = if (last) big else small, bottomEnd = if (last) big else small
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("${testTag}_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = section.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * Material 3 Expressive Swipe-Down-to-Dismiss Modal Container.
 * Allows users to slide down from the top drag handle/header OR pull down when the inner
 * LazyColumn is at the top of its scroll to intuitively dismiss any modal sheet.
 */
@Composable
private fun SwipeDownDismissModalContainer(
    onClose: () -> Unit,
    testTag: String,
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit
) {
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = verticalDragOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "modalSwipeOffset"
    )

    val nestedScrollConnection = remember(onClose) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (verticalDragOffset > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-verticalDragOffset)
                    verticalDragOffset = (verticalDragOffset + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    verticalDragOffset = (verticalDragOffset + available.y * 0.85f).coerceIn(0f, 800f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (verticalDragOffset > 115f || (verticalDragOffset > 45f && available.y > 900f)) {
                    verticalDragOffset = 0f
                    onClose()
                    return available
                }
                verticalDragOffset = 0f
                return Velocity.Zero
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = animatedDragOffset
            }
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (verticalDragOffset > 115f) {
                            verticalDragOffset = 0f
                            onClose()
                        } else {
                            verticalDragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        verticalDragOffset = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        val next = verticalDragOffset + dragAmount
                        verticalDragOffset = next.coerceIn(0f, 800f)
                    }
                )
            }
            .testTag(testTag),
        color = containerColor,
        shape = RoundedCornerShape(
            topStart = if (animatedDragOffset > 4f) 32.dp else 0.dp,
            topEnd = if (animatedDragOffset > 4f) 32.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
        ) {
            // Top Swipe-Down Handle Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClose() }
                    .padding(top = 10.dp, bottom = 4.dp)
                    .testTag("${testTag}_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
            content()
        }
    }
}

/**
 * Speed (0.80x..2.00x) & Dynamic Pitch Correction Modal Sheet.
 */
@Composable
fun SpeedPitchStudioModal(
    state: SpeedPitchState,
    reverbState: ReverbState = ReverbState(),
    currentTrackBpm: Int,
    onSetSpeed: (Float) -> Unit,
    onSetPitchMode: (PitchCorrectionMode) -> Unit,
    onSetIndependentSemitones: (Float) -> Unit,
    onSetIndependentPitchRatio: (Float) -> Unit,
    onSetReverbEnabled: (Boolean) -> Unit = {},
    onSelectReverbPreset: (ReverbPreset) -> Unit = {},
    onSetReverbWetDryMix: (Float) -> Unit = {},
    onSetReverbRoomSize: (Float) -> Unit = {},
    onSetReverbDecay: (Float) -> Unit = {},
    onSetReverbDamping: (Float) -> Unit = {},
    onResetReverb: () -> Unit = {},
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val speedPresets = listOf(0.80f, 0.90f, 1.00f, 1.10f, 1.25f, 1.50f, 1.75f, 2.00f)
    val scaledBpm = (currentTrackBpm * state.speed).roundToInt()

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "speed_pitch_studio_modal",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Speed & Dynamic Pitch DSP",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Studio-precision time-stretch & varispeed engine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveBounceIconButton(
                    onClick = onReset,
                    size = 48.dp,
                    modifier = Modifier.testTag("reset_speed_pitch_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset to 1.0x")
                }
                ExpressiveBounceIconButton(
                    onClick = onClose,
                    size = 48.dp,
                    modifier = Modifier.testTag("close_speed_pitch_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Live Telemetry Readout Card
                item {
                    Surface(
                        shape = cornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SPEED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fx", state.speed),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$scaledBpm BPM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "EFFECTIVE PITCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2fx", state.effectivePitch),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.2f semitones", state.effectiveSemitones),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Speed Control Card (0.80x .. 2.00x)
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playback Speed (0.80x – 2.00x)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExpressiveBounceIconButton(
                                        onClick = { onSetSpeed(state.speed - 0.05f) },
                                        size = 38.dp,
                                        modifier = Modifier.testTag("speed_minus_button")
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "-0.05x", modifier = Modifier.size(18.dp))
                                    }
                                    ExpressiveBounceIconButton(
                                        onClick = { onSetSpeed(state.speed + 0.05f) },
                                        size = 38.dp,
                                        modifier = Modifier.testTag("speed_plus_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "+0.05x", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Slider(
                                value = state.speed,
                                onValueChange = onSetSpeed,
                                valueRange = 0.80f..2.00f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("speed_slider")
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(speedPresets) { preset ->
                                    val selected = kotlin.math.abs(state.speed - preset) < 0.01f
                                    SpringPressableSurface(
                                        onClick = { onSetSpeed(preset) },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.testTag("speed_preset_${preset}")
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%.2fx", preset),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Pitch Correction Modes (Architecture)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Dynamic Pitch Correction Mode",
                            style = MaterialTheme.typography.titleLarge
                        )
                        PitchCorrectionMode.entries.forEach { mode ->
                            val selected = state.pitchMode == mode
                            SpringPressableSurface(
                                onClick = { onSetPitchMode(mode) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                cornerRadius = 22.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pitch_mode_${mode.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Independent Semitone & Concert Pitch Card
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Semitone & Concert Pitch Shift",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Current Shift: %+.2f semitones (%.3fx)",
                                            state.effectiveSemitones,
                                            state.effectivePitch
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ExpressiveBounceIconButton(
                                        onClick = { onSetIndependentSemitones(state.effectiveSemitones - 1f) },
                                        size = 38.dp
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "-1 semitone", modifier = Modifier.size(18.dp))
                                    }
                                    ExpressiveBounceIconButton(
                                        onClick = { onSetIndependentSemitones(state.effectiveSemitones + 1f) },
                                        size = 38.dp
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "+1 semitone", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Slider(
                                value = state.effectiveSemitones.coerceIn(-12f, 12f),
                                onValueChange = { onSetIndependentSemitones(it) },
                                valueRange = -12f..12f,
                                steps = 23,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SpringPressableSurface(
                                    onClick = { onSetIndependentPitchRatio(432f / 440f) },
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text("432 Hz Natural (-0.32 st)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                SpringPressableSurface(
                                    onClick = { onSetIndependentSemitones(0f) },
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text("440 Hz Standard (0 st)", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Studio Reverb DSP Engine Card (Simultaneous with Speed & Dynamic Pitch)
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reverb_dsp_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Live Studio Reverb DSP",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (reverbState.isActivelyProcessing) {
                                            "${reverbState.preset.label} • ${(reverbState.wetDryMix * 100).roundToInt()}% Wet • Room ${(reverbState.roomSize * 100).roundToInt()}%"
                                        } else {
                                            "Clean Dry Signal (Zero DSP coloration)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (reverbState.isActivelyProcessing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Switch(
                                    checked = reverbState.enabled,
                                    onCheckedChange = onSetReverbEnabled,
                                    modifier = Modifier.testTag("reverb_enable_switch")
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ReverbPreset.entries) { preset ->
                                    val selected = reverbState.preset == preset
                                    SpringPressableSurface(
                                        onClick = { onSelectReverbPreset(preset) },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        },
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.testTag("reverb_preset_${preset.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Wet / Dry Mix", style = MaterialTheme.typography.labelMedium)
                                    Text("${(reverbState.wetDryMix * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = reverbState.wetDryMix,
                                    onValueChange = onSetReverbWetDryMix,
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reverb_wet_dry_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Room Size", style = MaterialTheme.typography.labelMedium)
                                    Text("${(reverbState.roomSize * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = reverbState.roomSize,
                                    onValueChange = onSetReverbRoomSize,
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reverb_room_size_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Decay Time (RT60)", style = MaterialTheme.typography.labelMedium)
                                    Text("${(reverbState.decay * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = reverbState.decay,
                                    onValueChange = onSetReverbDecay,
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reverb_decay_slider")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("High-Frequency Damping", style = MaterialTheme.typography.labelMedium)
                                    Text("${(reverbState.damping * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = reverbState.damping,
                                    onValueChange = onSetReverbDamping,
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reverb_damping_slider")
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
}

/**
 * Full-Screen Opaque Sleep Timer Modal Sheet with Swipe-Down-to-Close.
 * Supports stopping playback after a specified duration (5..120 min) OR at the end of the current track.
 */
@Composable
fun SleepTimerModal(
    state: SleepTimerState,
    currentTrackTitle: String,
    onStartDuration: (Int, Boolean, Boolean) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onAddFiveMinutes: () -> Unit,
    onUpdateOptions: (Boolean, Boolean) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var customMinutes by remember { mutableFloatStateOf(state.totalDurationMinutes.toFloat().coerceIn(5f, 120f)) }

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "sleep_timer_modal",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Sleep Timer",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Slide down anywhere to close • Auto-pause playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExpressiveBounceIconButton(
                onClick = onClose,
                size = 48.dp,
                modifier = Modifier.testTag("close_sleep_timer_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close Sleep Timer")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Active Status Banner
                item {
                    Surface(
                        shape = cornerShape(28.dp),
                        color = if (state.isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (state.isActive) "SLEEP TIMER ACTIVE" else "SLEEP TIMER INACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.statusBadgeText,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.isActive) {
                                Text(
                                    text = if (state.stopAtEndOfCurrentTrack || state.waitingForTrackEnd) {
                                        "Stopping after \"$currentTrackTitle\" finishes"
                                    } else {
                                        "Playback will gently pause when countdown reaches 0:00"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SpringPressableSurface(
                                        onClick = onAddFiveMinutes,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        cornerRadius = 16.dp,
                                        modifier = Modifier.testTag("sleep_add_5_min_button")
                                    ) {
                                        Text(
                                            text = "+5 Minutes",
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                    SpringPressableSurface(
                                        onClick = onCancel,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        cornerRadius = 16.dp,
                                        modifier = Modifier.testTag("sleep_cancel_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.TimerOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Cancel Timer",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Mode 1: Stop at End of Current Track
                item {
                    SpringPressableSurface(
                        onClick = onStartEndOfTrack,
                        color = if (state.isActive && state.stopAtEndOfCurrentTrack) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        cornerRadius = 24.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sleep_end_of_track_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = LocalContentColor.current
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stop at End of Current Track",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Finish playing \"$currentTrackTitle\" and pause before the next track starts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.78f)
                                )
                            }
                            if (state.isActive && state.stopAtEndOfCurrentTrack) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // Mode 2: Quick Duration Presets
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Quick Duration Presets",
                                style = MaterialTheme.typography.titleMedium
                            )

                            val presetRows = listOf(
                                listOf(5, 15, 30),
                                listOf(45, 60, 90)
                            )
                            presetRows.forEach { rowMins ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowMins.forEach { mins ->
                                        val active = state.isActive && !state.stopAtEndOfCurrentTrack && state.totalDurationMinutes == mins
                                        SpringPressableSurface(
                                            onClick = {
                                                customMinutes = mins.toFloat()
                                                onStartDuration(mins, state.finishLastTrackOnExpiry, state.gentleFadeOut)
                                            },
                                            color = if (active) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            cornerRadius = 18.dp,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("sleep_preset_${mins}m")
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = "$mins min",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Custom Duration Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Custom Duration: ${customMinutes.roundToInt()} min",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                SpringPressableSurface(
                                    onClick = {
                                        onStartDuration(
                                            customMinutes.roundToInt(),
                                            state.finishLastTrackOnExpiry,
                                            state.gentleFadeOut
                                        )
                                    },
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    cornerRadius = 14.dp
                                ) {
                                    Text(
                                        text = "Start ${customMinutes.roundToInt()}m Timer",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Slider(
                                value = customMinutes,
                                onValueChange = { customMinutes = it },
                                valueRange = 1f..120f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Sleep Timer Options
                item {
                    Surface(
                        shape = cornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SettingsToggleRow(
                                title = "Finish Current Track When Timer Ends",
                                subtitle = "Wait for the active track to complete instead of cutting off mid-song",
                                checked = state.finishLastTrackOnExpiry,
                                onCheckedChange = { onUpdateOptions(it, state.gentleFadeOut) },
                                testTag = "toggle_sleep_finish_track"
                            )

                            SettingsToggleRow(
                                title = "Gentle 10-Second Volume Fade-Out",
                                subtitle = "Smoothly attenuate audio output during the final seconds of the countdown",
                                checked = state.gentleFadeOut,
                                onCheckedChange = { onUpdateOptions(state.finishLastTrackOnExpiry, it) },
                                testTag = "toggle_sleep_fade_out"
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
}

/**
 * Full-screen Opaque Sonic Lab Modal Sheet.
 */
@Composable
fun SonicLabModalOverlay(
    tracks: List<SubsonicTrack>,
    activePreset: EqualizerPreset,
    customBandsDb: List<Float>,
    activeTranscodeFormat: String = "RAW",
    activeMaxBitRate: Int = 0,
    onUpdateTranscodeProfile: (String, Int) -> Unit = { _, _ -> },
    onSelectPreset: (EqualizerPreset) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onPlayMoodMix: (List<SubsonicTrack>) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val bandLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    val bitrateProfiles = listOf(
        Triple("RAW", 0, "FLAC Lossless • Bit-Perfect"),
        Triple("OPUS", 320, "OPUS • 320 kbps Studio"),
        Triple("OPUS", 192, "OPUS • 192 kbps Balanced"),
        Triple("MP3", 320, "MP3 • 320 kbps High")
    )

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "sonic_lab_modal_sheet",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Equalizer & Bitrate Performance",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "5-Band Parametric Equalizer & OpenSubsonic Stream Bitrate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExpressiveBounceIconButton(
                onClick = onClose,
                size = 48.dp,
                modifier = Modifier.testTag("close_sonic_lab_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close Equalizer")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
                // Bitrate & Streaming Performance Quick Selector
                item {
                    Surface(
                        shape = cornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bitrate & Streaming Performance",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (activeTranscodeFormat == "RAW" || activeMaxBitRate == 0) {
                                            "Lossless"
                                        } else {
                                            "$activeTranscodeFormat ${activeMaxBitRate}k"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(bitrateProfiles) { (fmt, rate, label) ->
                                    val selected = activeTranscodeFormat == fmt && activeMaxBitRate == rate
                                    SpringPressableSurface(
                                        onClick = { onUpdateTranscodeProfile(fmt, rate) },
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        cornerRadius = 18.dp,
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge,
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
                    }
                }

                item {
                    Surface(
                        shape = cornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frequency Response Curve",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = activePreset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            EqResponseCurveCanvas(
                                bandsDb = customBandsDb,
                                primaryColor = MaterialTheme.colorScheme.primary,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            customBandsDb.forEachIndexed { idx, dbValue ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bandLabels[idx],
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(56.dp)
                                    )
                                    Slider(
                                        value = dbValue,
                                        onValueChange = { onUpdateBand(idx, it) },
                                        valueRange = -10f..10f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "%+.1f dB".format(dbValue),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(58.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Studio Acoustic Profiles",
                            style = MaterialTheme.typography.titleLarge
                        )
                        EqualizerPreset.entries.forEach { preset ->
                            val selected = preset == activePreset
                            SpringPressableSurface(
                                onClick = { onSelectPreset(preset) },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                                cornerRadius = 22.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("eq_preset_${preset.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = preset.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalContentColor.current.copy(alpha = 0.78f)
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = LocalContentColor.current
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Instant Sonic Mood Mixes",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SpringPressableSurface(
                                onClick = {
                                    onPlayMoodMix(tracks.shuffled())
                                    onClose()
                                },
                                color = MaterialTheme.colorScheme.primaryContainer,
                                cornerRadius = 24.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mood_mix_kinetic")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Kinetic Night Drive", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "High-BPM synthwave & post-rock",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SpringPressableSurface(
                                onClick = {
                                    onPlayMoodMix(tracks.sortedBy { it.bpm })
                                    onClose()
                                },
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                cornerRadius = 24.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mood_mix_botanical")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Botanical Focus", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Warm ambient & nu-jazz grooves",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
}

@Composable
private fun EqResponseCurveCanvas(
    bandsDb: List<Float>,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val b0 by animateFloatAsState(bandsDb.getOrElse(0) { 0f }, spring(stiffness = Spring.StiffnessLow), label = "eq0")
    val b1 by animateFloatAsState(bandsDb.getOrElse(1) { 0f }, spring(stiffness = Spring.StiffnessLow), label = "eq1")
    val b2 by animateFloatAsState(bandsDb.getOrElse(2) { 0f }, spring(stiffness = Spring.StiffnessLow), label = "eq2")
    val b3 by animateFloatAsState(bandsDb.getOrElse(3) { 0f }, spring(stiffness = Spring.StiffnessLow), label = "eq3")
    val b4 by animateFloatAsState(bandsDb.getOrElse(4) { 0f }, spring(stiffness = Spring.StiffnessLow), label = "eq4")
    val animatedBands = listOf(b0, b1, b2, b3, b4)

    Canvas(
        modifier = modifier
            .clip(cornerShape(18.dp))
            .background(Color(0xFF12101B))
            .padding(12.dp)
    ) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.5.dp.toPx()
        )

        val points = animatedBands.mapIndexed { idx, db ->
            val x = (idx.toFloat() / (animatedBands.size - 1).coerceAtLeast(1)) * w
            val y = midY - (db / 12f) * (h * 0.44f)
            Offset(x, y)
        }

        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val ctrlX = (p0.x + p1.x) / 2f
                path.cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(primaryColor, accentColor)),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(
                color = primaryColor,
                radius = 5.dp.toPx(),
                center = pt
            )
        }
    }
}

/**
 * User & Server Information Modal Sheet:
 * Opened when clicking the Circle User Icon in the top bar.
 * Displays active user & server details, quick Sync Library action, saved server nodes,
 * and a shortcut to add/configure a new OpenSubsonic server.
 */
@Composable
fun UserServerInfoModal(
    activeServer: SubsonicServerConfig,
    servers: List<SubsonicServerConfig>,
    pingDiagnostic: PingDiagnosticResult?,
    isSyncing: Boolean,
    onQuickSync: () -> Unit,
    onActivateServer: (SubsonicServerConfig) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "user_server_info_modal",
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Account & Server Node",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ExpressiveBounceIconButton(
                    onClick = onClose,
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("close_user_server_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Surface(
                        shape = cornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = activeServer.name,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = activeServer.baseUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = cornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Active User",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = activeServer.username,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }

                                Surface(
                                    shape = cornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Stream Profile",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (activeServer.maxBitRate == 0) {
                                                "RAW Lossless"
                                            } else {
                                                "${activeServer.transcodeFormat} ${activeServer.maxBitRate}k"
                                            },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }

                            val diag = pingDiagnostic
                            if (diag != null) {
                                Text(
                                    text = "${diag.serverType} • ${diag.latencyMs}ms • ${diag.statusMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (diag.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SpringPressableSurface(
                                    onClick = onQuickSync,
                                    color = MaterialTheme.colorScheme.primary,
                                    cornerRadius = 18.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("user_modal_sync_now_button")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isSyncing) "Syncing…" else "Sync Library Now",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }

                                SpringPressableSurface(
                                    onClick = onOpenOnboarding,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    cornerRadius = 18.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("user_modal_configure_server_button")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Enter Server",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Saved OpenSubsonic Nodes",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(servers, key = { "modal_srv_${it.id}" }) { srv ->
                    val isCurrent = srv.id == activeServer.id
                    SpringPressableSurface(
                        onClick = { onActivateServer(srv) },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        cornerRadius = 22.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_modal_server_${srv.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = srv.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${srv.baseUrl} • user: ${srv.username}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(alpha = 0.78f)
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Active",
                                    tint = LocalContentColor.current
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Application Introduction & OpenSubsonic Server Setup Overlay:
 * Welcomes the user to Ondalune and lets them enter their OpenSubsonic / Navidrome / Gonic
 * server credentials immediately or explore with the built-in lossless studio server.
 */
@Composable
fun ServerOnboardingIntroModal(
    activeServer: SubsonicServerConfig,
    onConnectServer: (String, String, String, String, Boolean) -> Unit,
    onContinueWithDemo: () -> Unit,
    syncState: OndaluneViewModel.OnboardingSync? = null
) {
    val syncing = syncState != null && syncState.phase != OndaluneViewModel.OnboardingSync.Phase.FAILED
    var serverName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useTokenAuth by remember { mutableStateOf(true) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .testTag("server_onboarding_intro_modal")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OndaluneBrandEmblem(size = 72.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Welcome to Ondalune",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Material 3 Expressive OpenSubsonic Audiophile Player",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (syncing) item(key = "onboarding_sync") {
                OnboardingSyncPanel(syncState!!, serverName.ifBlank { "your server" })
            }

            if (!syncing) item {
                Surface(
                    shape = cornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (syncState?.phase == OndaluneViewModel.OnboardingSync.Phase.FAILED) {
                            Text(
                                text = "Couldn't connect: ${syncState.error.orEmpty().ifBlank { "check the address and your login" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("onboarding_connect_error")
                            )
                        }
                        Text(
                            text = "Enter Your OpenSubsonic Server Details",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Connect to Navidrome, Gonic, Airsonic-Advanced, or Ampache with MD5 salt + token authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = serverName,
                            onValueChange = { serverName = it },
                            label = { Text("Server Label (e.g. Home Navidrome)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_server_name_input")
                        )

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Server Base URL (https://...)") },
                            singleLine = true,
                            supportingText = if (serverUrl.trim().startsWith("http://", ignoreCase = true)) {
                                { Text("Plain HTTP isn't encrypted. Fine on your home network; use https:// over the internet.") }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_server_url_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_username_input")
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password / Token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboarding_password_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "MD5 Salt + Token Auth (t & s)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "Recommended for OpenSubsonic v1.16.1",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = useTokenAuth,
                                onCheckedChange = { useTokenAuth = it }
                            )
                        }

                        Button(
                            onClick = {
                                onConnectServer(
                                    serverName,
                                    serverUrl,
                                    username,
                                    password,
                                    useTokenAuth
                                )
                            },
                            enabled = serverUrl.length > 8 && username.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("onboarding_connect_server_button")
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connect & Sync Server")
                        }
                    }
                }
            }

            if (!syncing) item {
                SpringPressableSurface(
                    onClick = onContinueWithDemo,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    cornerRadius = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_continue_demo_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Explore with ${activeServer.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Start immediately with pre-loaded 24-bit/96kHz FLAC studio tracks & synced lyrics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = cornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Start Listening",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * First sync on the onboarding screen: a ring that fills with the share of the library already
 * copied, the live song count, and a check at 100% just before onboarding zooms into Home.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingSyncPanel(state: OndaluneViewModel.OnboardingSync, serverName: String) {
    val phase = state.phase
    val fraction = state.fraction
    val animated by animateFloatAsState(fraction ?: 0f, animationSpec = tween(420), label = "onboardingSyncProgress")
    Surface(
        shape = cornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_sync_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
                if (fraction == null) {
                    CircularWavyProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    CircularWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxSize())
                }
                if (phase == OndaluneViewModel.OnboardingSync.Phase.DONE) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Sync complete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Text(
                        text = if (fraction != null) "${(animated * 100).roundToInt()}%" else "…",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = when (phase) {
                    OndaluneViewModel.OnboardingSync.Phase.CONNECTING -> "Connecting to $serverName"
                    OndaluneViewModel.OnboardingSync.Phase.DONE -> "Your library is ready"
                    else -> "Syncing your library"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    phase == OndaluneViewModel.OnboardingSync.Phase.CONNECTING -> "Checking the server and your login"
                    state.total != null -> "%,d of %,d songs".format(state.found, state.total)
                    else -> "%,d songs so far".format(state.found)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("onboarding_sync_count")
            )
            if (phase != OndaluneViewModel.OnboardingSync.Phase.DONE) Text(
                text = "Big libraries take a minute the first time. After this, only changes are synced.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
