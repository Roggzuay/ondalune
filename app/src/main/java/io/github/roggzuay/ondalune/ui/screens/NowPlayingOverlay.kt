@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.ui.components.frosted
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import io.github.roggzuay.ondalune.ui.theme.LocalInterfaceStyle
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import androidx.compose.foundation.shape.CornerSize
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.theme.monoLabelSmall
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.text.TextStyle
import io.github.roggzuay.ondalune.ui.theme.LocalArtworkPalette
import io.github.roggzuay.ondalune.ui.components.PlayerBackdrop
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.LoadingIndicator
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.ReverbProcessingMode
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.data.model.SleepTimerState
import io.github.roggzuay.ondalune.data.model.SmartShuffleProfile
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.ui.components.AnimatedAudioBarsIndicator
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.ExpressiveSquigglySlider
import io.github.roggzuay.ondalune.ui.components.ExpressiveTransportGroup
import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.components.SpringPressableSurface
import io.github.roggzuay.ondalune.ui.components.rememberHapticFeedbackManager
import io.github.roggzuay.ondalune.ui.viewmodel.NowPlayingSheetTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.animation.core.animate
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey
import io.github.roggzuay.ondalune.ui.components.DownloadButton
import io.github.roggzuay.ondalune.ui.components.DownloadPhase
import io.github.roggzuay.ondalune.ui.components.downloadStatusOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.animateScrollBy
import io.github.roggzuay.ondalune.data.model.AudioQuality
import io.github.roggzuay.ondalune.ui.components.AudioQualityChip
import io.github.roggzuay.ondalune.ui.components.AudioDetailsSheet

/**
 * Floating Pill/Squircle Mini-Player docked above the bottom navigation dock.
 * Clean, uncluttered layout without a redundant like button, centered on wide tablets.
 * Dynamically adapts its tint to either the Unified Material You phone color or the active song's artwork color.
 */
@Composable
fun FloatingExpressiveMiniPlayer(
    track: SubsonicTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    sheet: PlayerSheetState
) {
    // Swipe the bar: left = next track, right = previous. It follows the finger, then springs back.
    // Upwards it opens the player, which follows the finger too.
    val dragX = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeHaptics = LocalHapticFeedbackManager.current
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }
    val latestNext by rememberUpdatedState(onSkipNext)
    val latestPrevious by rememberUpdatedState(onSkipPrevious)
    val latestExpand by rememberUpdatedState(onExpand)
    val progress = (positionMs.toFloat() / durationMs.coerceAtLeast(1000L).toFloat()).coerceIn(0f, 1f)
    // The theme already follows the cover's real colors; the track's hex hint is only a guess.
    DisposableEffect(sheet) {
        onDispose {
            sheet.miniCard = Rect.Zero
            sheet.miniArt = Rect.Zero
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SpringPressableSurface(
            onClick = onExpand,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .onGloballyPositioned { sheet.miniCard = it.rootRect() }
                .graphicsLayer {
                    translationX = dragX.value
                    // The opening player covers the bar from where it stands, so the bar fades out under it.
                    val opening = 1f - (sheet.fraction / 0.2f).coerceIn(0f, 1f)
                    alpha = opening * (1f - (kotlin.math.abs(dragX.value) / (swipeThreshold * 4f)).coerceIn(0f, 0.35f))
                }
                .pointerInput(sheet) {
                    var horizontal: Boolean? = null
                    val tracker = VelocityTracker()
                    fun open(velocityY: Float) {
                        if (sheet.release(swipeScope, velocityY, startedOpen = false)) latestExpand()
                    }
                    detectDragGestures(
                        onDragStart = {
                            horizontal = null
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            if (horizontal == true) {
                                val offset = dragX.value
                                when {
                                    offset < -swipeThreshold -> { swipeHaptics?.performSubtleTick(); latestNext() }
                                    offset > swipeThreshold -> { swipeHaptics?.performSubtleTick(); latestPrevious() }
                                }
                                swipeScope.launch { dragX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                            } else open(tracker.calculateVelocity().y)
                        },
                        onDragCancel = {
                            if (horizontal == true) swipeScope.launch { dragX.animateTo(0f) } else open(0f)
                        }
                    ) { change, amount ->
                        if (horizontal == null) horizontal = kotlin.math.abs(amount.x) > kotlin.math.abs(amount.y)
                        change.consume()
                        if (horizontal == true) {
                            swipeScope.launch { dragX.snapTo(dragX.value + amount.x) }
                        } else {
                            tracker.addPointerInputChange(change)
                            sheet.dragBy(amount.y)
                        }
                    }
                }
                .frosted(hazeState, cornerShape(22.dp))
                .testTag("floating_mini_player"),
            color = Color.Transparent,
            cornerRadius = 22.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveArtwork(
                        seedHex = track.paletteSeedHex,
                        accentHex = track.accentHex,
                        artStyleIndex = track.artStyleIndex,
                        coverArtUrl = track.coverArtUrl,
                        // Static thumbnail: no morph or spin in the mini player.
                        shapeMode = ArtworkShapeMode.SQUIRCLE,
                        modifier = Modifier
                            .size(40.dp)
                            .onGloballyPositioned { sheet.miniArt = it.rootRect() }
                            // While the player is open, its own cover stands here and grows out of it.
                            .graphicsLayer { alpha = if (sheet.thumbnailHidden) 0f else 1f }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = track.suffix,
                                style = monoLabelSmall(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Plain text here: the whole bar is a tap target to expand the player.
                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    val playCorner by animateDpAsState(
                        targetValue = if (isPlaying) 14.dp else 26.dp,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "miniPlayCorner"
                    )
                    FilledIconButton(
                        onClick = onTogglePlay,
                        shape = RoundedCornerShape(playCorner),
                        modifier = Modifier
                            .size(width = 48.dp, height = 40.dp)
                            .testTag("mini_player_play_button")
                    ) {
                        when {
                            isBuffering -> LoadingIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "Pause")
                            else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onSkipNext,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("mini_player_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Track",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                val waveAmplitude by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    label = "miniWave"
                )
                // Paused: the wave flattens and stops travelling, so nothing moves while silent.
                LinearWavyProgressIndicator(
                    progress = { progress },
                    amplitude = { waveAmplitude },
                    waveSpeed = if (isPlaying) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/**
 * Full-Screen Adaptive Opaque Material 3 Expressive Now Playing Studio Sheet.
 *
 * Dynamically adapts to screen ratio and form factor:
 * - Supports vertical swipe-down-to-dismiss anywhere on the screen (including over the artwork)
 *   while still allowing taps on the artwork to morph its shape.
 * - Uses the user's chosen `PlayerBackgroundAnimationStyle` and respects `unifiedMaterialYouColor`.
 * - Slides the "Up Next Queue" up from the bottom as a partial Material 3 Bottom Sheet (~62% height)
 *   without covering the whole screen when the bottom-left Play Next button is pressed.
 */
@Composable
fun NowPlayingStudioOverlay(
    sheet: PlayerSheetState,
    /** The quality line under the cover; null hides it (setting off, or nothing known). */
    audioQuality: AudioQuality? = null,
    spatialAudio: () -> Boolean? = { null },
    dolbyDecoders: List<String> = emptyList(),
    track: SubsonicTrack,
    server: SubsonicServerConfig,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speedPitchState: SpeedPitchState,
    sleepTimerState: SleepTimerState,
    shuffleEnabled: Boolean,
    repeatOneEnabled: Boolean,
    animatedWaveEnabled: Boolean,
    artworkShapeMode: ArtworkShapeMode,
    activeTab: NowPlayingSheetTab,
    lyrics: List<LyricLine>,
    queue: List<SubsonicTrack>,
    eqPreset: EqualizerPreset,
    onCollapse: () -> Unit,
    onSelectTab: (NowPlayingSheetTab) -> Unit,
    onCycleArtworkShape: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    /** Swiping the cover always goes back a song (the button restarts a song that has played a while). */
    onSwipePrevious: () -> Unit = onSkipPrevious,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatOne: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    /** Long-press on the speed button: back to 1×, original pitch, no reverb. */
    onResetSpeedPitch: () -> Unit = {},
    showSpeedResetHint: Boolean = false,
    onSpeedResetHintShown: () -> Unit = {},
    onOpenSleepTimerSheet: () -> Unit,
    onOpenArtist: () -> Unit = {},
    /** The playlist the queue came from, while the current track is in it; named in the header. */
    playingFromPlaylist: SubsonicPlaylist? = null,
    onOpenPlaylist: (String) -> Unit = {},
    onOpenSonicLab: () -> Unit,
    onPlayQueueTrack: (SubsonicTrack) -> Unit,
    lyricsResult: TrackLyricsResult = TrackLyricsResult(
        availability = if (lyrics.isNotEmpty()) LyricsAvailability.SYNCED else LyricsAvailability.UNAVAILABLE,
        syncedLines = lyrics
    ),
    reverbProcessingMode: ReverbProcessingMode = ReverbProcessingMode.BYPASSED_DRY,
    isPlayingOfflineFile: Boolean = false,
    download: PlayerDownload? = null,
    onRemoveQueueTrack: (String) -> Unit = {},
    onMoveQueueTrack: (Int, Int) -> Unit = { _, _ -> },
    reverbState: io.github.roggzuay.ondalune.data.model.ReverbState = io.github.roggzuay.ondalune.data.model.ReverbState(),
    showRemainingTime: Boolean = false,
    showPlayerStarButton: Boolean = true,
    shuffleMode: ShuffleMode = if (shuffleEnabled) ShuffleMode.SMART else ShuffleMode.OFF,
    smartShuffleProfile: SmartShuffleProfile = SmartShuffleProfile.BALANCED,
    backgroundAnimationStyle: PlayerBackgroundAnimationStyle = PlayerBackgroundAnimationStyle.APPLE_GLITTER_GRAIN,
    unifiedMaterialYouColor: Boolean = false,
) {
    val isUpNextBottomSheetOpen = activeTab == NowPlayingSheetTab.QUEUE
    var showAudioDetails by remember { mutableStateOf(false) }
    if (showAudioDetails && audioQuality != null) {
        AudioDetailsSheet(audioQuality, remember { spatialAudio() }, dolbyDecoders, onDismiss = { showAudioDetails = false })
    }
    BackHandler {
        if (isUpNextBottomSheetOpen) {
            onSelectTab(NowPlayingSheetTab.COVER)
        } else {
            onCollapse()
        }
    }

    val artworkPulse = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()

    // Pulling down anywhere (the cover included) shrinks the player back into the mini player,
    // following the finger; letting go finishes the way it was heading.
    val onSwipeDownDelta: (Float) -> Unit = { dy -> sheet.dragBy(dy) }
    val onSwipeDownFinish: (Float) -> Unit = { velocityY ->
        if (!sheet.release(scope, velocityY, startedOpen = true)) {
            hapticManager.performSubtleTick()
            onCollapse()
        }
    }
    // Everything but the cover fades in over the second half of opening, rising slightly.
    val contentLift = with(LocalDensity.current) { 48.dp.toPx() }
    val openingContent = Modifier.graphicsLayer {
        alpha = sheet.contentAlpha
        translationY = (1f - sheet.contentAlpha) * contentLift
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isUpNextBottomSheetOpen) {
                if (!isUpNextBottomSheetOpen) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || sheet.fraction < 1f) {
                                change.consume()
                                tracker.addPointerInputChange(change)
                                onSwipeDownDelta(dragAmount)
                            }
                        },
                        onDragEnd = { onSwipeDownFinish(tracker.calculateVelocity().y) },
                        onDragCancel = { onSwipeDownFinish(0f) }
                    )
                }
            }
            .testTag("now_playing_studio_sheet"),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val availWidth = maxWidth
            val availHeight = maxHeight
            val isWideOrLandscape = availWidth >= 680.dp || availWidth > availHeight * 1.08f
            val isUltraTallPortrait = !isWideOrLandscape && (availHeight / availWidth.coerceAtLeast(1.dp)) >= 1.92f

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sheet.backdropAlpha }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                PlayerBackdrop(
                    track = track,
                    palette = LocalArtworkPalette.current,
                    style = backgroundAnimationStyle,
                    isPlaying = isPlaying,
                    playbackSpeed = speedPitchState.speed,
                    themeColorsOnly = unifiedMaterialYouColor
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .widthIn(max = if (isWideOrLandscape) 1160.dp else 620.dp)
                    .padding(
                        horizontal = if (isWideOrLandscape) 32.dp else 22.dp,
                        vertical = if (isUltraTallPortrait) 18.dp else 10.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(openingContent) {
                    NowPlayingTopHeaderBar(
                        server = server,
                        track = track,
                        activeTab = activeTab,
                        eqPreset = eqPreset,
                        playingFromPlaylist = playingFromPlaylist,
                        onCollapse = onCollapse,
                        onSelectTab = onSelectTab,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenSonicLab = onOpenSonicLab
                    )
                }

                Spacer(modifier = Modifier.height(if (isUltraTallPortrait) 18.dp else 10.dp))

                if (isWideOrLandscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            NowPlayingCenterStage(
                                sheet = sheet,
                                quality = audioQuality,
                                onShowQuality = { showAudioDetails = true },
                                activeTab = activeTab,
                                track = track,
                                artworkShapeMode = artworkShapeMode,
                                isPlaying = isPlaying,
                                maxArtworkSize = minOf(availWidth * 0.42f, availHeight * 0.68f),
                                artworkPulse = artworkPulse.value,
                                lyrics = lyrics,
                                lyricsResult = lyricsResult,
                                positionMs = positionMs,
                                queue = queue,
                                shuffleMode = shuffleMode,
                                onTriggerPulseAndCycleShape = {
                                    scope.launch {
                                        artworkPulse.snapTo(1.06f)
                                        artworkPulse.animateTo(
                                            1f,
                                            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                    onCycleArtworkShape()
                                },
                                onSwipeDownDelta = onSwipeDownDelta,
                                onSwipeDownFinish = onSwipeDownFinish,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSwipePrevious,
                                onSeek = onSeek,
                                onPlayQueueTrack = onPlayQueueTrack
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1.05f)
                                .fillMaxHeight()
                                .then(openingContent),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NowPlayingControlsColumn(
                                track = track,
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                speedPitchState = speedPitchState,
                                reverbState = reverbState,
                                reverbProcessingMode = reverbProcessingMode,
                                isPlayingOfflineFile = isPlayingOfflineFile,
                                download = download,
                                sleepTimerState = sleepTimerState,
                                shuffleEnabled = shuffleEnabled,
                                shuffleMode = shuffleMode,
                                repeatOneEnabled = repeatOneEnabled,
                                animatedWaveEnabled = animatedWaveEnabled,
                                showRemainingTime = showRemainingTime,
                                showPlayerStarButton = showPlayerStarButton,
                                activeTab = activeTab,
                                isUltraTallPortrait = false,
                                onSelectTab = onSelectTab,
                                onTogglePlay = onTogglePlay,
                                onSkipPrevious = onSkipPrevious,
                                onSkipNext = onSkipNext,
                                onSeek = onSeek,
                                onToggleShuffle = onToggleShuffle,
                                onToggleRepeatOne = onToggleRepeatOne,
                                onToggleStar = onToggleStar,
                                onOpenSpeedPitchSheet = onOpenSpeedPitchSheet,
                                onResetSpeedPitch = onResetSpeedPitch,
                                showSpeedResetHint = showSpeedResetHint,
                                onSpeedResetHintShown = onSpeedResetHintShown,
                                onOpenSleepTimerSheet = onOpenSleepTimerSheet,
                                onOpenArtist = onOpenArtist
                            )
                        }
                    }
                } else {
                    val portraitArtSize = minOf(availWidth * 0.84f, availHeight * if (isUltraTallPortrait) 0.43f else 0.39f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        NowPlayingCenterStage(
                            sheet = sheet,
                            quality = audioQuality,
                            onShowQuality = { showAudioDetails = true },
                            activeTab = activeTab,
                            track = track,
                            artworkShapeMode = artworkShapeMode,
                            isPlaying = isPlaying,
                            maxArtworkSize = portraitArtSize,
                            artworkPulse = artworkPulse.value,
                            lyrics = lyrics,
                            lyricsResult = lyricsResult,
                            positionMs = positionMs,
                            queue = queue,
                            shuffleMode = shuffleMode,
                            onTriggerPulseAndCycleShape = {
                                scope.launch {
                                    artworkPulse.snapTo(1.06f)
                                    artworkPulse.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                                onCycleArtworkShape()
                            },
                            onSwipeDownDelta = onSwipeDownDelta,
                            onSwipeDownFinish = onSwipeDownFinish,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSwipePrevious,
                            onSeek = onSeek,
                            onPlayQueueTrack = onPlayQueueTrack
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isUltraTallPortrait) 16.dp else 10.dp))

                    Box(openingContent) {
                        NowPlayingControlsColumn(
                            track = track,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            speedPitchState = speedPitchState,
                            reverbState = reverbState,
                            reverbProcessingMode = reverbProcessingMode,
                            isPlayingOfflineFile = isPlayingOfflineFile,
                            download = download,
                            sleepTimerState = sleepTimerState,
                            shuffleEnabled = shuffleEnabled,
                            shuffleMode = shuffleMode,
                            repeatOneEnabled = repeatOneEnabled,
                            animatedWaveEnabled = animatedWaveEnabled,
                            showRemainingTime = showRemainingTime,
                            showPlayerStarButton = showPlayerStarButton,
                            activeTab = activeTab,
                            isUltraTallPortrait = isUltraTallPortrait,
                            onSelectTab = onSelectTab,
                            onTogglePlay = onTogglePlay,
                            onSkipPrevious = onSkipPrevious,
                            onSkipNext = onSkipNext,
                            onSeek = onSeek,
                            onToggleShuffle = onToggleShuffle,
                            onToggleRepeatOne = onToggleRepeatOne,
                            onToggleStar = onToggleStar,
                            onOpenSpeedPitchSheet = onOpenSpeedPitchSheet,
                            onResetSpeedPitch = onResetSpeedPitch,
                            showSpeedResetHint = showSpeedResetHint,
                            onSpeedResetHintShown = onSpeedResetHintShown,
                            onOpenSleepTimerSheet = onOpenSleepTimerSheet,
                            onOpenArtist = onOpenArtist
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isUpNextBottomSheetOpen,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.46f))
                        .clickable { onSelectTab(NowPlayingSheetTab.COVER) }
                        .testTag("up_next_bottom_sheet_scrim")
                )
            }

            AnimatedVisibility(
                visible = isUpNextBottomSheetOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                UpNextPartialBottomSheet(
                    queue = queue,
                    currentTrackId = track.id,
                    isPlaying = isPlaying,
                    shuffleMode = shuffleMode,
                    smartShuffleProfile = smartShuffleProfile,
                    onSelectTrack = { selected ->
                        onPlayQueueTrack(selected)
                    },
                    onRemoveTrack = onRemoveQueueTrack,
                    onMoveTrack = onMoveQueueTrack,
                    onDismiss = { onSelectTab(NowPlayingSheetTab.COVER) }
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopHeaderBar(
    server: SubsonicServerConfig,
    track: SubsonicTrack,
    activeTab: NowPlayingSheetTab,
    eqPreset: EqualizerPreset,
    playingFromPlaylist: SubsonicPlaylist?,
    onCollapse: () -> Unit,
    onSelectTab: (NowPlayingSheetTab) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSonicLab: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        ExpressiveBounceIconButton(
            onClick = onCollapse,
            size = 48.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("collapse_player_button")
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Minimize Player"
            )
        }

        if (activeTab == NowPlayingSheetTab.LYRICS) {
            // Subtle, non-intrusive compact mini-pill at the very top when viewing full-area Lyrics
            Surface(
                onClick = { onSelectTab(NowPlayingSheetTab.COVER) },
                shape = cornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 58.dp)
                    .testTag("lyrics_compact_header_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveArtwork(
                        seedHex = track.paletteSeedHex,
                        accentHex = track.accentHex,
                        artStyleIndex = track.artStyleIndex,
                        coverArtUrl = track.coverArtUrl,
                        shapeMode = ArtworkShapeMode.SQUIRCLE,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else if (playingFromPlaylist != null) {
            // Playing from a playlist: name it, and tapping it opens its page.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp)
                    .clip(cornerShape(10.dp))
                    .clickable { onOpenPlaylist(playingFromPlaylist.id) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .testTag("now_playing_playlist_link"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "PLAYING FROM PLAYLIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = playingFromPlaylist.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        ExpressiveBounceIconButton(
            onClick = onOpenSonicLab,
            active = eqPreset != EqualizerPreset.FLAT_STUDIO,
            size = 48.dp,
            containerColor = if (eqPreset != EqualizerPreset.FLAT_STUDIO) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (eqPreset != EqualizerPreset.FLAT_STUDIO) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .testTag("open_sonic_lab_pill")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = "Sonic Lab: equalizer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCenterStage(
    sheet: PlayerSheetState,
    quality: AudioQuality?,
    onShowQuality: () -> Unit,
    activeTab: NowPlayingSheetTab,
    track: SubsonicTrack,
    artworkShapeMode: ArtworkShapeMode,
    isPlaying: Boolean,
    maxArtworkSize: Dp,
    artworkPulse: Float,
    lyrics: List<LyricLine>,
    lyricsResult: TrackLyricsResult,
    positionMs: Long,
    queue: List<SubsonicTrack>,
    shuffleMode: ShuffleMode,
    onTriggerPulseAndCycleShape: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownFinish: (Float) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayQueueTrack: (SubsonicTrack) -> Unit
) {
    val latestNext by rememberUpdatedState(onSkipNext)
    val latestPrevious by rememberUpdatedState(onSkipPrevious)
    val latestSwipeDown by rememberUpdatedState(onSwipeDownDelta)
    val latestSwipeDownFinish by rememberUpdatedState(onSwipeDownFinish)
    val swipeHaptics = LocalHapticFeedbackManager.current
    val swipeScope = rememberCoroutineScope()
    // Horizontal offset of the cover while it is dragged or flung to the next/previous track. The
    // finger writes it directly (a coroutine per move event landed a frame late); [artSettle]
    // animates it back, or away.
    var artDragX by remember { mutableFloatStateOf(0f) }
    val artSettle = remember { arrayOfNulls<Job>(1) }
    suspend fun animateArtTo(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f) =
        animate(artDragX, target, velocity, spec) { value, _ -> artDragX = value }
    val flingVelocity = with(LocalDensity.current) { 700.dp.toPx() }
    // Brings a cover that left by [side] back in from the other side.
    suspend fun slideBackIn(side: Float) {
        artDragX = -side * sheet.fullArt.width * 0.6f
        animateArtTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
    }

    val context = LocalContext.current
    val artPx = with(LocalDensity.current) { maxArtworkSize.coerceIn(160.dp, 420.dp).roundToPx() }
    fun coverRequest(url: String) = artworkCacheKey(url).let { key ->
        ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key).size(artPx).build()
    }
    // Decode the neighbouring covers ahead, so a swipe lands on a cover that is already in memory
    // instead of decoding it while the new one slides in.
    LaunchedEffect(track.id, queue) {
        val i = queue.indexOfFirst { it.id == track.id }
        if (i < 0) return@LaunchedEffect
        listOfNotNull(queue.getOrNull(i + 1), queue.getOrNull(i - 1))
            .filter { it.coverArtUrl.isNotBlank() }
            .forEach { context.imageLoader.enqueue(coverRequest(it.coverArtUrl)) }
    }

    // The cover on screen trails [track] until the new picture has loaded (at most 0.4 s), so the
    // crossfade never fades in an empty tile. After a swipe the old cover has already left with
    // the finger, by [swipedFrom] (-1 or 1), so it isn't shown leaving again; when the cover
    // doesn't change (same album) it slides back in from the other side.
    var shownTrack by remember { mutableStateOf(track) }
    var swipedFrom by remember { mutableFloatStateOf(0f) }
    val exitHidden = remember { booleanArrayOf(false) }
    LaunchedEffect(track.id) {
        val side = swipedFrom
        if (side != 0f) artSettle[0]?.cancel()
        val coverChanged = shownTrack.coverKey() != track.coverKey()
        if (coverChanged && track.coverArtUrl.isNotBlank()) {
            withTimeoutOrNull(400) { context.imageLoader.execute(coverRequest(track.coverArtUrl)) }
        }
        exitHidden[0] = side != 0f
        shownTrack = track
        if (side == 0f) return@LaunchedEffect
        swipedFrom = 0f
        if (coverChanged) artDragX = 0f
        else artSettle[0] = swipeScope.launch { slideBackIn(side) }
    }

    val centerMode = if (activeTab == NowPlayingSheetTab.LYRICS) {
        NowPlayingSheetTab.LYRICS
    } else {
        NowPlayingSheetTab.COVER
    }

    AnimatedContent(
        targetState = centerMode,
        transitionSpec = {
            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)))
                .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.94f))
        },
        modifier = Modifier.fillMaxSize(),
        label = "centerStageTransition"
    ) { tab ->
        when (tab) {
            NowPlayingSheetTab.LYRICS -> {
                Box(Modifier.graphicsLayer { alpha = sheet.contentAlpha }) {
                    SyncedLyricsPane(
                        lyrics = lyrics,
                        lyricsResult = lyricsResult,
                        positionMs = positionMs,
                        onSeekToLyric = onSeek
                    )
                }
            }

            else -> {
                DisposableEffect(sheet) {
                    sheet.coverInPlayer = true
                    onDispose { sheet.coverInPlayer = false }
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The outer box takes the gestures and stays put; the inner one moves, so
                    // finger positions are never measured in a moving frame.
                    Box(
                        modifier = Modifier
                            .size(maxArtworkSize.coerceIn(160.dp, 420.dp))
                            .aspectRatio(1f)
                            .onGloballyPositioned { sheet.fullArt = it.rootRect() }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onTriggerPulseAndCycleShape() }
                                )
                            }
                            .pointerInput(Unit) {
                                // One drag detector so the gesture locks to one axis: sideways skips
                                // tracks, downwards closes the player.
                                var horizontal: Boolean? = null
                                val tracker = VelocityTracker()
                                detectDragGestures(
                                    onDragStart = {
                                        horizontal = null
                                        tracker.resetTracking()
                                        artSettle[0]?.cancel() // catch a cover that is still settling
                                    },
                                    onDragEnd = {
                                        val velocity = tracker.calculateVelocity()
                                        if (horizontal == true) {
                                            val width = size.width.toFloat()
                                            val offset = artDragX
                                            // A quick flick skips even when short; a slow drag has
                                            // to carry the cover past a fifth of its width.
                                            val direction = when {
                                                offset < -width * 0.22f || (offset < 0f && velocity.x < -flingVelocity) -> -1
                                                offset > width * 0.22f || (offset > 0f && velocity.x > flingVelocity) -> 1
                                                else -> 0
                                            }
                                            artSettle[0] = swipeScope.launch {
                                                if (direction == 0) {
                                                    animateArtTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy), velocity.x)
                                                    return@launch
                                                }
                                                swipeHaptics?.performSubtleTick()
                                                animateArtTo(direction * width * 1.1f, tween(160))
                                                swipedFrom = direction.toFloat()
                                                if (direction < 0) latestNext() else latestPrevious()
                                                // Nothing to skip to: the cover comes back.
                                                delay(700)
                                                if (swipedFrom != 0f) {
                                                    swipedFrom = 0f
                                                    slideBackIn(direction.toFloat())
                                                }
                                            }
                                        } else {
                                            latestSwipeDownFinish(velocity.y)
                                        }
                                    },
                                    onDragCancel = {
                                        if (horizontal == true) {
                                            artSettle[0] = swipeScope.launch { animateArtTo(0f, spring()) }
                                        } else {
                                            latestSwipeDownFinish(0f)
                                        }
                                    }
                                ) { change, amount ->
                                    // The first delta points the way the finger left the slop
                                    // circle. Lean sideways (up to ~56° off horizontal): a thumb's
                                    // arc starts a little downward, and that dragged the whole
                                    // player down instead of moving the cover.
                                    if (horizontal == null) horizontal = abs(amount.y) <= abs(amount.x) * 1.5f
                                    change.consume()
                                    tracker.addPointerInputChange(change)
                                    if (horizontal == true) artDragX += amount.x else latestSwipeDown(amount.y)
                                }
                            }
                            .testTag("cycle_art_shape_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(sheet.artTransform())
                                .graphicsLayer {
                                    scaleX = artworkPulse
                                    scaleY = artworkPulse
                                    // Straight sideways, no tilt: rotating around the centre made the
                                    // leading edge dip, as if the cover rode a wheel.
                                    translationX = artDragX
                                    alpha = 1f - abs(artDragX / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.7f)
                                }
                        ) {
                            // A new cover fades and grows into place while the old one recedes.
                            AnimatedContent(
                                targetState = shownTrack,
                                contentKey = { it.coverKey() },
                                transitionSpec = {
                                    val exit = if (exitHidden[0]) ExitTransition.None else
                                        fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 0.96f)
                                    fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(460, easing = FastOutSlowInEasing), initialScale = 0.9f) togetherWith exit
                                },
                                label = "coverChange"
                            ) { shown ->
                                // A swiped-away cover is gone from its first leaving frame (an exit
                                // animation would still draw it once, back in the middle).
                                val hidden = exitHidden[0] && transition.targetState == EnterExitState.PostExit
                                ExpressiveArtwork(
                                    seedHex = shown.paletteSeedHex,
                                    accentHex = shown.accentHex,
                                    artStyleIndex = shown.artStyleIndex,
                                    coverArtUrl = shown.coverArtUrl,
                                    shapeMode = artworkShapeMode,
                                    isSpinning = isPlaying,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (hidden) 0f else 1f }
                                )
                            }
                        }
                    }
                    if (quality != null) {
                        Spacer(Modifier.height(12.dp))
                        AudioQualityChip(quality, onClick = onShowQuality, modifier = Modifier.graphicsLayer { alpha = sheet.contentAlpha })
                    }
                }
            }
        }
    }
}

/** Identifies the picture a track shows, so songs from one album keep the same cover on screen. */
private fun SubsonicTrack.coverKey() = if (coverArtUrl.isBlank()) "track:$id" else artworkCacheKey(coverArtUrl)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingControlsColumn(
    track: SubsonicTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speedPitchState: SpeedPitchState,
    reverbState: io.github.roggzuay.ondalune.data.model.ReverbState,
    reverbProcessingMode: ReverbProcessingMode,
    isPlayingOfflineFile: Boolean,
    download: PlayerDownload?,
    sleepTimerState: SleepTimerState,
    shuffleEnabled: Boolean,
    shuffleMode: ShuffleMode,
    repeatOneEnabled: Boolean,
    animatedWaveEnabled: Boolean,
    showRemainingTime: Boolean,
    showPlayerStarButton: Boolean,
    activeTab: NowPlayingSheetTab,
    isUltraTallPortrait: Boolean,
    onSelectTab: (NowPlayingSheetTab) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatOne: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    onResetSpeedPitch: () -> Unit,
    showSpeedResetHint: Boolean,
    onSpeedResetHintShown: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    onOpenArtist: () -> Unit = {}
) {
    val verticalGap = if (isUltraTallPortrait) 12.dp else 7.dp
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
    val isLyricsMode = activeTab == NowPlayingSheetTab.LYRICS

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hide the big song title and artist when Lyrics mode is active
        // so the full-area blurred lyrics canvas is never disturbed.
        AnimatedVisibility(visible = !isLyricsMode) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(initialDelayMillis = 1800)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(cornerShape(8.dp))
                                    .clickable(onClick = onOpenArtist)
                                    .testTag("now_playing_artist_link")
                            )
                            Text(
                                text = " — ",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val trackActions = LocalTrackActions.current
                            Text(
                                text = track.album,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(cornerShape(8.dp))
                                    .clickable { trackActions?.openAlbum(track) }
                                    .testTag("now_playing_album_link")
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val downloaded = isPlayingOfflineFile || download?.state?.isCompleted == true
                            if (downloaded) {
                                Icon(
                                    imageVector = Icons.Filled.DownloadDone,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .testTag("now_playing_downloaded_mark")
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = buildString {
                                    append("${track.playCount} plays")
                                    if (reverbState.isActivelyProcessing) {
                                        append(" • ${reverbProcessingMode.label}")
                                    }
                                    if (downloaded) {
                                        append(" • Downloaded")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reverbState.isActivelyProcessing || downloaded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showPlayerStarButton) {
                        Spacer(modifier = Modifier.width(12.dp))

                        IconToggleButton(
                            checked = track.starred,
                            onCheckedChange = { onToggleStar() },
                            shapes = IconButtonDefaults.toggleableShapes(),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("now_playing_star_button")
                        ) {
                            Icon(
                                imageVector = if (track.starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Star on OpenSubsonic Server"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(verticalGap))
            }
        }

        ExpressiveSquigglySlider(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            animatedWaveEnabled = animatedWaveEnabled,
            showRemainingTime = showRemainingTime,
            onSeek = onSeek,
            activeColor = MaterialTheme.colorScheme.primary,
            accentColor = MaterialTheme.colorScheme.tertiary
        )

        Spacer(modifier = Modifier.height(verticalGap))

        AnimatedVisibility(visible = !isLyricsMode) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isDspModified = speedPitchState.speed != 1.0f || speedPitchState.effectivePitch != 1.0f || reverbState.isActivelyProcessing
                // One-time "Hold to reset" bubble, for new installs and upgrades alike. It counts as
                // seen only once it has been on screen, so closing the player early shows it again.
                val resetHint = rememberTooltipState(isPersistent = true)
                LaunchedEffect(Unit) {
                    if (!showSpeedResetHint) return@LaunchedEffect
                    delay(700)
                    launch { resetHint.show() }
                    delay(4000)
                    resetHint.dismiss()
                    onSpeedResetHintShown()
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Hold to reset speed & pitch") } },
                        state = resetHint,
                        // Long-press belongs to the reset, not to showing the tooltip.
                        enableUserInput = false
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            ExpressiveBounceIconButton(
                                onClick = onOpenSpeedPitchSheet,
                                onLongClick = onResetSpeedPitch,
                                active = isDspModified,
                                size = 48.dp,
                                containerColor = if (isDspModified) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (isDspModified) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("open_speed_pitch_pill")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = "Speed, Pitch & Live Reverb Settings",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            if (isDspModified) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(verticalGap))
            }
        }

        // Viscous Glue / Connected Button Group Transport Row
        ExpressiveTransportGroup(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            shuffleEnabled = shuffleEnabled,
            repeatOneEnabled = repeatOneEnabled,
            onToggleShuffle = onToggleShuffle,
            onSkipPrevious = onSkipPrevious,
            onTogglePlay = onTogglePlay,
            onSkipNext = onSkipNext,
            onToggleRepeatOne = onToggleRepeatOne,
            shuffleMode = shuffleMode
        )

        Spacer(modifier = Modifier.height(if (isUltraTallPortrait) 16.dp else 10.dp))

        // Bottom bar, plain icons that take the accent while their panel is open:
        // Bottom Left: Up Next Queue | Bottom Center: Synced Lyrics (Icon Only) | Bottom Right: Sleep Timer (Icon Only)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Bottom Left: Up Next Button
            val isQueueOpen = activeTab == NowPlayingSheetTab.QUEUE
            IconToggleButton(
                checked = isQueueOpen,
                onCheckedChange = { onSelectTab(if (isQueueOpen) NowPlayingSheetTab.COVER else NowPlayingSheetTab.QUEUE) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier
                    .size(width = 64.dp, height = 52.dp)
                    .testTag("now_playing_tab_queue")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Up Next Queue",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Bottom Center: Synced Lyrics Icon-Only Button (No word required)
            val isLyricsOpen = activeTab == NowPlayingSheetTab.LYRICS
            IconToggleButton(
                checked = isLyricsOpen,
                onCheckedChange = { onSelectTab(if (isLyricsOpen) NowPlayingSheetTab.COVER else NowPlayingSheetTab.LYRICS) },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier
                    .size(width = 64.dp, height = 52.dp)
                    .testTag("now_playing_tab_lyrics")
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = "Synced Lyrics",
                    modifier = Modifier.size(22.dp)
                )
            }

            download?.let { PlayerDownloadButton(it) }

            // Bottom Right: Sleep Timer Icon-Only Button
            Box(contentAlignment = Alignment.TopEnd) {
                IconToggleButton(
                    checked = sleepTimerState.isActive,
                    onCheckedChange = { onOpenSleepTimerSheet() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier
                        .size(width = 64.dp, height = 52.dp)
                        .testTag("open_sleep_timer_pill")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Sleep Timer",
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (sleepTimerState.isActive) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/**
 * Full-Area Blurred Artwork Left-Aligned Synced Lyrics Canvas with 120Hz Word-by-Word Tracking.
 * - Occupies the entire artwork stage area (`fillMaxSize()`), deeply blurring the track's cover art
 *   behind a high-contrast atmospheric scrim.
 * - Left-aligns every lyric line (`Alignment.Start`, `TextAlign.Start`).
 * - Tracks each word individually across the active lyric line with smooth color illumination,
 *   vertical lift, scale pulse, and glowing underline progression.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncedLyricsPane(
    lyrics: List<LyricLine>,
    lyricsResult: TrackLyricsResult,
    positionMs: Long,
    onSeekToLyric: (Long) -> Unit
) {
    // Line-level sync only: LRC timestamps mark when each line starts, nothing finer, so the whole
    // current line lights up. A small lead covers the engine's progress-tick latency.
    val activeIdx = lyrics.indexOfLast { it.timeMs <= positionMs + LyricLeadMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    // Scrolling the lyrics by hand stops them following the song until 3 s after letting go.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(dragged) {
        if (dragged) follow = false
        else if (!follow) {
            delay(3000)
            follow = true
        }
    }
    // One glide per line, with the same timing as the highlight, to a third of the way down.
    LaunchedEffect(activeIdx, lyrics, follow) {
        if (!follow || activeIdx !in lyrics.indices) return@LaunchedEffect
        val anchor = (listState.layoutInfo.viewportSize.height * 0.32f).toInt()
        val line = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIdx }
        if (line == null) listState.scrollToItem(activeIdx, -anchor)
        else listState.animateScrollBy((line.offset - anchor).toFloat(), LyricGlide)
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val glow = MaterialTheme.colorScheme.primary
    val standardStyle = LocalInterfaceStyle.current == InterfaceStyle.STANDARD
    val lineStyle = TextStyle(
        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
        fontWeight = if (standardStyle) FontWeight.Bold else FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.3).sp
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_area_blurred_lyrics_pane")
    ) {
        // Lines fade out at the top and bottom edges instead of hard-clipping
        val edgeFade = Modifier
            .weight(1f)
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.10f to Color.Black,
                        0.86f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }

        when {
            lyrics.isEmpty() && lyricsResult.plainTextLines.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = edgeFade.testTag("plain_unsynced_lyrics_list")
            ) {
                item {
                    Text(
                        text = "UNSYNCED LYRICS • ${lyricsResult.sourceLabel}",
                        style = monoLabelSmall(),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                itemsIndexed(lyricsResult.plainTextLines) { idx, plainLine ->
                    Text(
                        text = plainLine,
                        style = lineStyle.copy(fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
                        color = onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("plain_lyric_line_$idx")
                    )
                }
            }

            lyrics.isEmpty() -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .testTag("unavailable_lyrics_state"),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No lyrics for this track",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your server didn't return synced or plain lyrics, and none are embedded in the file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = edgeFade
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isCurrent = index == activeIdx
                    val emphasis by animateFloatAsState(
                        targetValue = when {
                            isCurrent -> 1f
                            index < activeIdx -> 0.40f
                            else -> 0.58f
                        },
                        animationSpec = LyricGlide,
                        label = "lyricEmphasis"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else 0.94f,
                        animationSpec = LyricGlide,
                        label = "lyricScale"
                    )
                    Text(
                        text = line.text.ifBlank { "♪" },
                        style = lineStyle.copy(
                            shadow = if (isCurrent) Shadow(glow.copy(alpha = 0.75f), Offset(0f, 2f), 22f) else null
                        ),
                        color = onSurface.copy(alpha = emphasis),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clip(cornerShape(16.dp))
                            .clickable { onSeekToLyric(line.timeMs) }
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                            .testTag("lyric_line_$index")
                    )
                }
            }
        }
    }
}

private const val LyricLeadMs = 200L
private val LyricGlide = tween<Float>(520, easing = FastOutSlowInEasing)

/**
 * Material 3 Expressive Partial Bottom Sheet for the "Play Next / Up Next" Queue.
 * Slides up from the bottom to ~62% of the screen height without covering the whole screen,
 * features a Material 3 drag handle pill, rounded 32.dp top corners, and swipe-down-to-dismiss.
 */
@Composable
private fun UpNextPartialBottomSheet(
    queue: List<SubsonicTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    shuffleMode: ShuffleMode,
    smartShuffleProfile: SmartShuffleProfile,
    onSelectTrack: (SubsonicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit = {},
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
    var sheetDragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedSheetOffsetPx by animateFloatAsState(
        targetValue = sheetDragOffsetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "upNextSheetDragOffset"
    )
    val latestDismiss by rememberUpdatedState(onDismiss)
    // Pulling down on the list once it is scrolled to the top drags the sheet itself, and
    // releasing far enough closes it, like a system bottom sheet.
    val listPullToDismiss = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (sheetDragOffsetPx > 0f && available.y < 0f) {
                    val used = available.y.coerceAtLeast(-sheetDragOffsetPx)
                    sheetDragOffsetPx += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    sheetDragOffsetPx += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetDragOffsetPx <= 0f) return Velocity.Zero
                if (sheetDragOffsetPx > 110f || available.y > 1800f) {
                    hapticManager.performSubtleTick()
                    latestDismiss()
                }
                sheetDragOffsetPx = 0f
                return available
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .fillMaxHeight(0.62f)
            .graphicsLayer {
                translationY = animatedSheetOffsetPx
            }
            .nestedScroll(listPullToDismiss)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0f || sheetDragOffsetPx > 0f) {
                            change.consume()
                            sheetDragOffsetPx = (sheetDragOffsetPx + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (sheetDragOffsetPx > 110f) {
                            hapticManager.performSubtleTick()
                            sheetDragOffsetPx = 0f
                            onDismiss()
                        } else {
                            sheetDragOffsetPx = 0f
                        }
                    },
                    onDragCancel = { sheetDragOffsetPx = 0f }
                )
            }
            .testTag("up_next_bottom_sheet"),
        shape = cornerShape(32.dp).copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
        ) {
            // Material 3 Standard Drag Handle Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                )
            }

            // Sheet Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Playing Next",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${queue.size} tracks in queue",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpressiveBounceIconButton(
                    onClick = onDismiss,
                    size = 48.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("close_up_next_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Playing Next Sheet",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Open at the playing track: what's above it has already played.
            val queueListState = rememberLazyListState(
                initialFirstVisibleItemIndex = queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0) +
                    if (shuffleMode != ShuffleMode.OFF) 1 else 0
            )
            LazyColumn(
                state = queueListState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (shuffleMode != ShuffleMode.OFF) {
                    item {
                        Surface(
                            shape = cornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (shuffleMode == ShuffleMode.SMART) {
                                    "Smart Shuffle (${smartShuffleProfile.label}) • ${smartShuffleProfile.description}"
                                } else {
                                    "Standard Shuffle • Uniform random order"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { index, item ->
                    val isCurrent = item.id == currentTrackId
                    SpringPressableSurface(
                        onClick = { onSelectTrack(item) },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "%02d".format(index + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            ExpressiveArtwork(
                                seedHex = item.paletteSeedHex,
                                accentHex = item.accentHex,
                                artStyleIndex = item.artStyleIndex,
                                coverArtUrl = item.coverArtUrl,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.artist} • ${item.playCount} plays",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                AnimatedAudioBarsIndicator(isPlaying = isPlaying)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (index > 0) {
                                        ExpressiveBounceIconButton(
                                            onClick = { onMoveTrack(index, index - 1) },
                                            size = 34.dp,
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("queue_move_up_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowUpward,
                                                contentDescription = "Move Up in Queue",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (index < queue.lastIndex) {
                                        ExpressiveBounceIconButton(
                                            onClick = { onMoveTrack(index, index + 1) },
                                            size = 34.dp,
                                            containerColor = Color.Transparent,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("queue_move_down_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDownward,
                                                contentDescription = "Move Down in Queue",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    ExpressiveBounceIconButton(
                                        onClick = { onRemoveTrack(item.id) },
                                        size = 34.dp,
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("queue_remove_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove from Queue",
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The current song's download in the player: its row (null if never downloaded) and actions. */
class PlayerDownload(
    val state: SubsonicDownloadEntity?,
    val onDownload: () -> Unit,
    val onCancel: () -> Unit,
    val onResume: () -> Unit,
    val onRemove: () -> Unit
)

/** The shared [DownloadButton] (cloud, spinner, progress ring, done) in the player's toggle shape. */
@Composable
private fun PlayerDownloadButton(download: PlayerDownload) {
    val status = remember(download.state) { downloadStatusOf(listOf(download.state)) }
    DownloadButton(
        status = status,
        onDownload = download.onDownload,
        onCancel = download.onCancel,
        onResume = download.onResume,
        onRemove = download.onRemove
    ) { onClick, content ->
        IconToggleButton(
            checked = status.phase == DownloadPhase.SAVED,
            onCheckedChange = { onClick() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier
                .size(width = 64.dp, height = 52.dp)
                .testTag("now_playing_download")
        ) { content() }
    }
}
