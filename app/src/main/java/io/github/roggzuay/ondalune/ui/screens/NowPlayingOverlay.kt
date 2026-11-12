@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.data.lyrics.LyricsSearch
import io.github.roggzuay.ondalune.data.lyrics.LyricsSource
import io.github.roggzuay.ondalune.data.lyrics.RankedCandidate
import io.github.roggzuay.ondalune.ui.components.LyricsSourceSheet
import io.github.roggzuay.ondalune.ui.components.frosted
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import androidx.compose.foundation.shape.CornerSize
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.theme.monoLabelSmall
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import io.github.roggzuay.ondalune.ui.theme.LocalArtworkPalette
import io.github.roggzuay.ondalune.ui.components.PlayerBackdrop
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.LoadingIndicator
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import io.github.roggzuay.ondalune.data.model.AudioQuality
import io.github.roggzuay.ondalune.ui.components.AudioQualityChip
import io.github.roggzuay.ondalune.ui.components.AudioDetailsSheet
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.vector.ImageVector

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
 * The full-screen player. The cover is its backdrop: full width at the top, melting into the
 * background where the title and controls begin. Lyrics take the cover's place, over the same
 * picture blurred and dimmed, while everything below stays where it is.
 *
 * Pulling down anywhere closes it into the mini player; the Up Next queue slides up as a partial
 * bottom sheet.
 */
@Composable
fun NowPlayingStudioOverlay(
    sheet: PlayerSheetState,
    /** The quality chip under the seek bar; null hides it (setting off, or nothing known). */
    audioQuality: AudioQuality? = null,
    spatialAudio: () -> Boolean? = { null },
    dolbyDecoders: List<String> = emptyList(),
    track: SubsonicTrack,
    server: SubsonicServerConfig,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    /** The position being heard right now; the lyrics follow this, not [positionMs]. */
    audiblePositionMs: () -> Long,
    durationMs: Long,
    speedPitchState: SpeedPitchState,
    sleepTimerState: SleepTimerState,
    shuffleEnabled: Boolean,
    repeatOneEnabled: Boolean,
    animatedWaveEnabled: Boolean,
    activeTab: NowPlayingSheetTab,
    lyrics: List<LyricLine>,
    queue: List<SubsonicTrack>,
    eqPreset: EqualizerPreset,
    onCollapse: () -> Unit,
    onSelectTab: (NowPlayingSheetTab) -> Unit,
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
    lyricsSource: LyricsSource = LyricsSource(),
    lyricsSearch: LyricsSearch = LyricsSearch(),
    onSearchLyrics: (String, String) -> Unit = { _, _ -> },
    onChooseLyrics: (RankedCandidate) -> Unit = {},
    onUseServerLyrics: () -> Unit = {},
    onRestoreLyrics: () -> Unit = {},
) {
    val isUpNextBottomSheetOpen = activeTab == NowPlayingSheetTab.QUEUE
    var showAudioDetails by remember { mutableStateOf(false) }
    if (showAudioDetails && audioQuality != null) {
        AudioDetailsSheet(audioQuality, remember { spatialAudio() }, dolbyDecoders, onDismiss = { showAudioDetails = false })
    }
    var showLyricsSource by remember { mutableStateOf(false) }
    if (showLyricsSource) {
        LyricsSourceSheet(
            track = track,
            source = lyricsSource,
            search = lyricsSearch,
            onSearch = onSearchLyrics,
            onChoose = onChooseLyrics,
            onUseServer = onUseServerLyrics,
            onRestore = onRestoreLyrics,
            onDismiss = { showLyricsSource = false }
        )
    }
    BackHandler {
        if (activeTab != NowPlayingSheetTab.COVER) onSelectTab(NowPlayingSheetTab.COVER) else onCollapse()
    }

    val lyricsOpen = activeTab == NowPlayingSheetTab.LYRICS
    // The position ticks several times a second: nothing below reads it but the seek bar.
    val position = rememberUpdatedState(positionMs)
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
            val wide = availWidth > availHeight * 1.08f
            // The cover is a full-width square at the top, or the leading pane of a window that is
            // wider than it is tall.
            val heroWidth = if (wide) minOf(availHeight, availWidth * 0.58f) else availWidth
            val heroHeight = if (wide) availHeight else availWidth
            val sidePadding = if (availWidth < 380.dp) 16.dp else 22.dp
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            // Where the controls begin, in root pixels: the cover has faded out by this line.
            var controlsTop by remember { mutableFloatStateOf(Float.NaN) }
            // Read in layer and draw blocks only: it moves every frame of the change.
            val lyricsShown = animateFloatAsState(
                targetValue = if (lyricsOpen) 1f else 0f,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "lyricsShown"
            )

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

            PlayerHero(
                sheet = sheet,
                track = track,
                queue = queue,
                wide = wide,
                lyricsOpen = lyricsOpen,
                lyricsShown = { lyricsShown.value },
                controlsTop = { controlsTop },
                topBarHeight = insets.calculateTopPadding() + 60.dp,
                sizePx = with(LocalDensity.current) { max(heroWidth, heroHeight).roundToPx() },
                onSkipNext = onSkipNext,
                onSkipPrevious = onSwipePrevious,
                onSwipeDownDelta = onSwipeDownDelta,
                onSwipeDownFinish = onSwipeDownFinish,
                modifier = Modifier
                    .align(if (wide) Alignment.TopStart else Alignment.TopCenter)
                    .size(heroWidth, heroHeight)
            )

            val topBar = @Composable {
                Box(openingContent) {
                    PlayerTopBar(
                        server = server,
                        playingFromPlaylist = playingFromPlaylist,
                        soundShaped = eqPreset != EqualizerPreset.FLAT_STUDIO,
                        speedShaped = speedPitchState.speed != 1.0f || speedPitchState.effectivePitch != 1.0f || reverbState.isActivelyProcessing,
                        showSpeedResetHint = showSpeedResetHint,
                        onCollapse = onCollapse,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenSonicLab = onOpenSonicLab,
                        onOpenSpeedPitchSheet = onOpenSpeedPitchSheet,
                        onResetSpeedPitch = onResetSpeedPitch,
                        onSpeedResetHintShown = onSpeedResetHintShown
                    )
                }
            }
            // The lyrics come up where the cover is; nothing below them moves.
            val lyricsSlot = @Composable { slot: Modifier ->
                Box(slot) {
                    AnimatedVisibility(
                        visible = lyricsOpen,
                        enter = fadeIn(tween(320, delayMillis = 80)) +
                            slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 10 },
                        exit = fadeOut(tween(200))
                    ) {
                        SyncedLyricsPane(
                            lyrics = lyrics,
                            lyricsResult = lyricsResult,
                            audiblePositionMs = audiblePositionMs,
                            isPlaying = isPlaying,
                            onSeekToLyric = onSeek,
                            modifier = Modifier.graphicsLayer { alpha = sheet.contentAlpha },
                            source = lyricsSource,
                            onOpenSource = { showLyricsSource = true }
                        )
                    }
                }
            }
            val controls = @Composable { area: Modifier ->
                Box(
                    modifier = area.onGloballyPositioned { controlsTop = it.positionInRoot().y },
                    propagateMinConstraints = true
                ) {
                    PlayerControls(
                        track = track,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMs = { position.value },
                        durationMs = durationMs,
                        audioQuality = audioQuality,
                        sleepTimerActive = sleepTimerState.isActive,
                        shuffleEnabled = shuffleEnabled,
                        shuffleMode = shuffleMode,
                        repeatOneEnabled = repeatOneEnabled,
                        animatedWaveEnabled = animatedWaveEnabled,
                        showRemainingTime = showRemainingTime,
                        showStarButton = showPlayerStarButton,
                        activeTab = activeTab,
                        download = download,
                        onSelectTab = onSelectTab,
                        onTogglePlay = onTogglePlay,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeatOne = onToggleRepeatOne,
                        onToggleStar = onToggleStar,
                        onOpenSleepTimerSheet = onOpenSleepTimerSheet,
                        onOpenArtist = onOpenArtist,
                        onOpenAudioDetails = { showAudioDetails = true },
                        modifier = openingContent
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = sidePadding, vertical = 10.dp)
            ) {
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.width(heroWidth - sidePadding - insets.calculateStartPadding(LocalLayoutDirection.current))) {
                            topBar()
                            lyricsSlot(Modifier.weight(1f).fillMaxWidth())
                        }
                        Spacer(Modifier.width(sidePadding))
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            controls(Modifier.widthIn(max = 520.dp).heightIn(max = 460.dp).fillMaxHeight())
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        topBar()
                        lyricsSlot(Modifier.weight(1f).widthIn(max = 620.dp).fillMaxWidth())
                        // On a tall screen the controls start where the cover ends and spread out
                        // below it; on a short one they keep their own height and overlap its fade.
                        controls(
                            Modifier.widthIn(max = 620.dp).heightIn(
                                min = (availHeight - heroHeight - insets.calculateBottomPadding() + 10.dp).coerceIn(0.dp, 440.dp)
                            )
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

/**
 * The cover as the player's backdrop: full width at the top (the leading pane when [wide]),
 * melting into the background where the controls begin. With the lyrics open it stays, blurred
 * and dimmed, behind them.
 *
 * Dragging it sideways skips tracks; dragging it down closes the player.
 */
@Composable
private fun PlayerHero(
    sheet: PlayerSheetState,
    track: SubsonicTrack,
    queue: List<SubsonicTrack>,
    wide: Boolean,
    lyricsOpen: Boolean,
    lyricsShown: () -> Float,
    controlsTop: () -> Float,
    topBarHeight: Dp,
    sizePx: Int,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSwipeDownDelta: (Float) -> Unit,
    onSwipeDownFinish: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestNext by rememberUpdatedState(onSkipNext)
    val latestPrevious by rememberUpdatedState(onSkipPrevious)
    val latestSwipeDown by rememberUpdatedState(onSwipeDownDelta)
    val latestSwipeDownFinish by rememberUpdatedState(onSwipeDownFinish)
    val latestQueue by rememberUpdatedState(queue)
    val latestTrack by rememberUpdatedState(track)
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
    // Brings a cover that left by [side] back in from the other side. [onShown] runs once it is
    // on screen again.
    suspend fun slideBackIn(side: Float, onShown: () -> Unit = {}) {
        artDragX = -side * sheet.fullArt.width * 0.6f
        withFrameNanos { }
        onShown()
        animateArtTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
    }

    val context = LocalContext.current
    fun coverRequest(url: String) = artworkCacheKey(url).let { key ->
        ImageRequest.Builder(context).data(url).memoryCacheKey(key).diskCacheKey(key).size(sizePx).build()
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
    // the finger, by [swipedFrom] (-1 or 1), so it isn't shown leaving again: the new one slides
    // in from the other side.
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
        if (coverChanged) exitHidden[0] = side != 0f
        shownTrack = track
        if (side == 0f) return@LaunchedEffect
        swipedFrom = 0f
        artSettle[0] = swipeScope.launch { slideBackIn(side) }
    }

    // The mini player's thumbnail hands over to this cover only while it shows as the cover.
    DisposableEffect(sheet, lyricsOpen) {
        sheet.coverInPlayer = !lyricsOpen
        onDispose { sheet.coverInPlayer = false }
    }

    val surface = MaterialTheme.colorScheme.surface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val topScrim = with(LocalDensity.current) { (topBarHeight + 28.dp).toPx() }
    val overlap = with(LocalDensity.current) { 20.dp.toPx() }
    // Blur needs Android 12; before it the cover behind the lyrics is dimmed harder instead.
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val behindLyrics by remember(lyricsShown) { derivedStateOf { lyricsShown() > 0f } }

    // The outer box takes the gestures and stays put; the inner one moves, so finger positions are
    // never measured in a moving frame.
    Box(
        modifier = modifier
            .onGloballyPositioned { sheet.fullArt = it.rootRect() }
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
                                val side = direction.toFloat()
                                val skip = { if (direction < 0) latestNext() else latestPrevious() }
                                // The queue plays in order, so the cover that follows is known and
                                // already decoded: it is sliding in before the engine is asked to
                                // switch, which holds the main thread for a moment.
                                val at = latestQueue.indexOfFirst { it.id == latestTrack.id }
                                val coming = if (at < 0) null else latestQueue.getOrNull(at - direction)
                                if (coming != null) {
                                    exitHidden[0] = true
                                    shownTrack = coming
                                    slideBackIn(side, onShown = skip)
                                    return@launch
                                }
                                swipedFrom = side
                                skip()
                                // Nothing to skip to: the cover comes back.
                                delay(700)
                                if (swipedFrom != 0f) {
                                    swipedFrom = 0f
                                    slideBackIn(side)
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
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(sheet.artTransform())
                .graphicsLayer {
                    // Straight sideways, no tilt: rotating around the centre made the leading
                    // edge dip, as if the cover rode a wheel.
                    translationX = artDragX
                    val dragged = 1f - abs(artDragX / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.7f)
                    // Behind the lyrics it is no cover for the mini player to take over, so it
                    // fades with the rest of the player.
                    alpha = dragged * lerp(1f, sheet.contentAlpha, lyricsShown())
                    compositingStrategy = CompositingStrategy.Offscreen
                    if (sheet.fraction < 1f) {
                        clip = true
                        shape = RoundedCornerShape(percent = ((1f - sheet.fraction) * 22f).toInt())
                    }
                }
                .drawWithContent {
                    drawContent()
                    val shown = sheet.contentAlpha
                    val lyric = lyricsShown()
                    if (lyric > 0f) drawRect(surface, alpha = (if (blurs) 0.74f else 0.86f) * lyric)
                    // Keeps the top bar readable on a bright cover.
                    drawRect(
                        Brush.verticalGradient(0f to surface.copy(alpha = 0.6f * shown), 1f to Color.Transparent, endY = topScrim),
                        size = Size(size.width, topScrim)
                    )
                    // The cover melts into the background towards the controls. The fade comes in
                    // with the rest of the player, so the cover leaves the mini player whole.
                    fun kept(alpha: Float) = Color.Black.copy(alpha = lerp(1f, alpha, shown))
                    val stops = arrayOf(0f to kept(1f), 0.4f to kept(0.6f), 0.75f to kept(0.2f), 1f to kept(0f))
                    val fade = if (wide) {
                        val from = size.width * 0.6f
                        if (rtl) Brush.horizontalGradient(*stops, startX = size.width - from, endX = 0f)
                        else Brush.horizontalGradient(*stops, startX = from, endX = size.width)
                    } else {
                        val edge = controlsTop().let {
                            if (it.isNaN()) size.height else (it - sheet.fullArt.top + overlap).coerceIn(0f, size.height)
                        }
                        Brush.verticalGradient(*stops, startY = edge - size.height * 0.3f, endY = edge)
                    }
                    drawRect(fade, blendMode = BlendMode.DstIn)
                }
        ) {
            // With the lyrics up the picture eases back a little, behind them.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val zoom = 1f + 0.06f * lyricsShown()
                    scaleX = zoom
                    scaleY = zoom
                }
            ) {
                AnimatedContent(
                    targetState = shownTrack,
                    contentKey = { it.coverKey() },
                    transitionSpec = {
                        // The new cover comes in over the old one, which stays until it is covered:
                        // the background never shows through between two covers.
                        // After a swipe the slide is the transition.
                        if (exitHidden[0]) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(360)) + scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 1.06f) togetherWith
                            ExitTransition.KeepUntilTransitionsFinished
                    },
                    label = "coverChange"
                ) { shown ->
                    // A swiped-away cover is gone from its first leaving frame (an exit
                    // animation would still draw it once, back in the middle).
                    val hidden = exitHidden[0] && transition.targetState == EnterExitState.PostExit
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (hidden) 0f else 1f }) {
                        ExpressiveArtwork(
                            seedHex = shown.paletteSeedHex,
                            accentHex = shown.accentHex,
                            artStyleIndex = shown.artStyleIndex,
                            coverArtUrl = shown.coverArtUrl,
                            fullBleed = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Blurring a small copy and scaling it up is far cheaper than blurring
                        // the full-size cover.
                        if (blurs && behindLyrics) ExpressiveArtwork(
                            seedHex = shown.paletteSeedHex,
                            accentHex = shown.accentHex,
                            artStyleIndex = shown.artStyleIndex,
                            coverArtUrl = shown.coverArtUrl,
                            fullBleed = true,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .requiredSize(160.dp)
                                .graphicsLayer {
                                    scaleX = sheet.fullArt.width / size.width
                                    scaleY = sheet.fullArt.height / size.height
                                    alpha = lyricsShown()
                                }
                                .blur(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Identifies the picture a track shows, so songs from one album keep the same cover on screen. */
private fun SubsonicTrack.coverKey() = if (coverArtUrl.isBlank()) "track:$id" else artworkCacheKey(coverArtUrl)

/** Over the top of the cover: close, where the music comes from, and the two sound tools. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    server: SubsonicServerConfig,
    playingFromPlaylist: SubsonicPlaylist?,
    soundShaped: Boolean,
    speedShaped: Boolean,
    showSpeedResetHint: Boolean,
    onCollapse: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSonicLab: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    onResetSpeedPitch: () -> Unit,
    onSpeedResetHintShown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveBounceIconButton(
            onClick = onCollapse,
            size = 48.dp,
            modifier = Modifier.testTag("collapse_player_button")
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize Player")
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // It sits on the cover, so it brings its own backing.
            val backing = Modifier
                .clip(cornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
            if (playingFromPlaylist != null) {
                // Playing from a playlist: name it, and tapping it opens its page.
                Column(
                    modifier = backing
                        .clickable { onOpenPlaylist(playingFromPlaylist.id) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .testTag("now_playing_playlist_link")
                ) {
                    Text(
                        text = "PLAYING FROM PLAYLIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                Column(backing.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

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
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text("Hold to reset speed & pitch") } },
            state = resetHint,
            // Long-press belongs to the reset, not to showing the tooltip.
            enableUserInput = false
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                ExpressiveBounceIconButton(
                    onClick = onOpenSpeedPitchSheet,
                    onLongClick = onResetSpeedPitch,
                    active = speedShaped,
                    size = 48.dp,
                    containerColor = if (speedShaped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (speedShaped) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("open_speed_pitch_pill")
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = "Speed, Pitch & Live Reverb Settings", modifier = Modifier.size(22.dp))
                }
                if (speedShaped) {
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }

        ExpressiveBounceIconButton(
            onClick = onOpenSonicLab,
            active = soundShaped,
            size = 48.dp,
            containerColor = if (soundShaped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (soundShaped) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("open_sonic_lab_pill")
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

/** Everything under the cover: what is playing, where it is, and the controls. */
@Composable
private fun PlayerControls(
    track: SubsonicTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    audioQuality: AudioQuality?,
    sleepTimerActive: Boolean,
    shuffleEnabled: Boolean,
    shuffleMode: ShuffleMode,
    repeatOneEnabled: Boolean,
    animatedWaveEnabled: Boolean,
    showRemainingTime: Boolean,
    showStarButton: Boolean,
    activeTab: NowPlayingSheetTab,
    download: PlayerDownload?,
    onSelectTab: (NowPlayingSheetTab) -> Unit,
    onTogglePlay: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatOne: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAudioDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(
        // It scrolls only when it can't fit (a small window, a large font): a scrolling column
        // would take the pull down that closes the player.
        modifier = modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0),
        // Spare height goes between the rows; the spacers are the least they stand apart.
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                            .widthIn(max = 200.dp)
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
            }
            if (showStarButton) {
                Spacer(Modifier.width(12.dp))
                IconToggleButton(
                    checked = track.starred,
                    onCheckedChange = { onToggleStar() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(56.dp).testTag("now_playing_star_button")
                ) {
                    Icon(
                        imageVector = if (track.starred) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Star on OpenSubsonic Server"
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PlayerProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            animatedWaveEnabled = animatedWaveEnabled,
            showRemainingTime = showRemainingTime,
            audioQuality = audioQuality,
            onSeek = onSeek,
            onOpenAudioDetails = onOpenAudioDetails
        )

        Spacer(Modifier.height(10.dp))
        ExpressiveTransportGroup(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onSkipPrevious = onSkipPrevious,
            onTogglePlay = onTogglePlay,
            onSkipNext = onSkipNext
        )

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val haptics = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
            val lyricsOpen = activeTab == NowPlayingSheetTab.LYRICS
            val queueOpen = activeTab == NowPlayingSheetTab.QUEUE
            PlayerToggle(
                checked = lyricsOpen,
                onToggle = { onSelectTab(if (lyricsOpen) NowPlayingSheetTab.COVER else NowPlayingSheetTab.LYRICS) },
                icon = Icons.Filled.Lyrics,
                description = "Synced Lyrics",
                tag = "now_playing_tab_lyrics",
                modifier = Modifier.weight(1f)
            )
            PlayerToggle(
                checked = queueOpen,
                onToggle = { onSelectTab(if (queueOpen) NowPlayingSheetTab.COVER else NowPlayingSheetTab.QUEUE) },
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                description = "Up Next Queue",
                tag = "now_playing_tab_queue",
                modifier = Modifier.weight(1f)
            )
            // Shuffle and repeat are one joined pair.
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val leading = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                val trailing = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)
                val pressed = RoundedCornerShape(10.dp)
                PlayerToggle(
                    checked = shuffleEnabled,
                    onToggle = {
                        haptics.performToggleOption(!shuffleEnabled)
                        onToggleShuffle()
                    },
                    icon = if (shuffleMode == ShuffleMode.SMART) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
                    description = "Cycle Shuffle Mode (${shuffleMode.label})",
                    tag = "shuffle_button",
                    modifier = Modifier.weight(1f),
                    shapes = IconButtonDefaults.toggleableShapes(leading, pressed, leading)
                )
                PlayerToggle(
                    checked = repeatOneEnabled,
                    onToggle = {
                        haptics.performToggleOption(!repeatOneEnabled)
                        onToggleRepeatOne()
                    },
                    icon = Icons.Filled.RepeatOne,
                    description = "Toggle Repeat One",
                    tag = "repeat_button",
                    modifier = Modifier.weight(1f),
                    shapes = IconButtonDefaults.toggleableShapes(trailing, pressed, trailing)
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                PlayerToggle(
                    checked = sleepTimerActive,
                    onToggle = onOpenSleepTimerSheet,
                    icon = Icons.Filled.Bedtime,
                    description = "Sleep Timer",
                    tag = "open_sleep_timer_pill",
                    modifier = Modifier.fillMaxWidth()
                )
                if (sleepTimerActive) {
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            download?.let { PlayerDownloadButton(it, Modifier.weight(1f)) }
        }
    }
}

/** The seek bar with the audio quality between the two times. Only this follows the position. */
@Composable
private fun PlayerProgress(
    positionMs: () -> Long,
    durationMs: Long,
    isPlaying: Boolean,
    animatedWaveEnabled: Boolean,
    showRemainingTime: Boolean,
    audioQuality: AudioQuality?,
    onSeek: (Long) -> Unit,
    onOpenAudioDetails: () -> Unit
) {
    ExpressiveSquigglySlider(
        positionMs = positionMs(),
        durationMs = durationMs,
        isPlaying = isPlaying,
        animatedWaveEnabled = animatedWaveEnabled,
        showRemainingTime = showRemainingTime,
        onSeek = onSeek,
        activeColor = MaterialTheme.colorScheme.primary,
        accentColor = MaterialTheme.colorScheme.tertiary,
        centerLabel = audioQuality?.let { quality ->
            {
                // The chip keeps its small size here; touches near it still reach it.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    AudioQualityChip(quality, onClick = onOpenAudioDetails)
                }
            }
        }
    )
}

/** One of the tonal toggles in the player's bottom row. */
@Composable
private fun PlayerToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    description: String,
    tag: String,
    modifier: Modifier = Modifier,
    shapes: IconToggleButtonShapes = IconButtonDefaults.toggleableShapes()
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        shapes = shapes,
        modifier = modifier.height(48.dp).testTag(tag)
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp))
    }
}

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
private fun PlayerDownloadButton(download: PlayerDownload, modifier: Modifier = Modifier) {
    val status = remember(download.state) { downloadStatusOf(listOf(download.state)) }
    DownloadButton(
        status = status,
        onDownload = download.onDownload,
        onCancel = download.onCancel,
        onResume = download.onResume,
        onRemove = download.onRemove,
        modifier = modifier
    ) { onClick, content ->
        FilledTonalIconToggleButton(
            checked = status.phase == DownloadPhase.SAVED,
            onCheckedChange = { onClick() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("now_playing_download")
        ) { content() }
    }
}
