@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import io.github.roggzuay.ondalune.data.model.countOf
import io.github.roggzuay.ondalune.data.lyrics.LyricsSearch
import io.github.roggzuay.ondalune.data.lyrics.LyricsSource
import io.github.roggzuay.ondalune.data.lyrics.RankedCandidate
import io.github.roggzuay.ondalune.ui.components.LyricsSourceSheet
import io.github.roggzuay.ondalune.ui.components.frosted
import dev.chrisbanes.haze.HazeState
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
import io.github.roggzuay.ondalune.ui.theme.LocalSongScheme
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import io.github.roggzuay.ondalune.cast.CastButton
import io.github.roggzuay.ondalune.ui.viewmodel.SoundOffer
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.ui.components.AnimatedAudioBarsIndicator
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.MotionArtworkInfoSheet
import io.github.roggzuay.ondalune.ui.components.MotionBackdrop
import io.github.roggzuay.ondalune.ui.components.MotionCover
import androidx.compose.foundation.gestures.detectTapGestures
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.FavouriteHeartIcon
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
import kotlin.math.roundToInt
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import io.github.roggzuay.ondalune.data.model.AudioQuality
import io.github.roggzuay.ondalune.ui.components.AudioQualityChip
import io.github.roggzuay.ondalune.ui.components.AudioDetailsSheet
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import kotlin.math.min
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Slider
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import io.github.roggzuay.ondalune.data.model.RepeatMode

/**
 * Floating Pill/Squircle Mini-Player docked above the bottom navigation dock.
 * Clean, uncluttered layout without a redundant like button, centered on wide tablets.
 * Wears the playing song's colours (LocalSongScheme), a hint of the cover on every page.
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
    /** Standard dock: the bar sits flush on the dock's own blurred ground, with no card of its own. */
    blended: Boolean = false,
    sheet: PlayerSheetState
) = MaterialTheme(colorScheme = LocalSongScheme.current ?: MaterialTheme.colorScheme) {
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
                .then(
                    if (blended) Modifier.padding(horizontal = 4.dp)
                    else Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                .onGloballyPositioned { sheet.miniCard = it.rootRect() }
                .graphicsLayer {
                    translationX = dragX.value
                    // The opening player covers the bar from where it stands, so the bar fades out under it.
                    val opening = 1f - (sheet.fraction / 0.2f).coerceIn(0f, 1f)
                    alpha = opening * (1f - (kotlin.math.abs(dragX.value) / (swipeThreshold * 4f)).coerceIn(0f, 0.35f))
                }
                .pointerInput(sheet) {
                    var horizontal: Boolean? = null
                    // Finger travel; the bar follows it 1:1 up to the skip point, then resists
                    // (rubber band) so it's clear the skip is armed and the bar can't be flung off.
                    var rawX = 0f
                    fun banded(x: Float): Float {
                        val excess = kotlin.math.abs(x) - swipeThreshold
                        if (excess <= 0f) return x
                        return kotlin.math.sign(x) * (swipeThreshold + excess / (1f + excess / swipeThreshold))
                    }
                    val tracker = VelocityTracker()
                    fun open(velocityY: Float) {
                        if (sheet.release(swipeScope, velocityY, startedOpen = false)) latestExpand()
                    }
                    detectDragGestures(
                        onDragStart = {
                            horizontal = null
                            rawX = dragX.value
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            if (horizontal == true) {
                                val offset = dragX.value
                                when {
                                    offset < -swipeThreshold -> latestNext()
                                    offset > swipeThreshold -> latestPrevious()
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
                            val before = kotlin.math.abs(rawX) > swipeThreshold
                            rawX += amount.x
                            // Tick the moment the skip arms or disarms, while the finger is still down.
                            if (before != kotlin.math.abs(rawX) > swipeThreshold) swipeHaptics?.performSubtleTick()
                            val target = banded(rawX)
                            swipeScope.launch { dragX.snapTo(target) }
                        } else {
                            tracker.addPointerInputChange(change)
                            sheet.dragBy(amount.y)
                        }
                    }
                }
                // Blended: the dock's own blur is already behind it, so a second frosted card
                // here would read as a box on a box.
                .then(if (blended) Modifier else Modifier.frosted(hazeState, cornerShape(22.dp)))
                .testTag("floating_mini_player"),
            color = Color.Transparent,
            cornerRadius = if (blended) 0.dp else 22.dp
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
                        onClick = {
                            swipeHaptics?.performPlayPauseTransition(isStartingPlay = !isPlaying)
                            onTogglePlay()
                        },
                        shape = RoundedCornerShape(playCorner),
                        modifier = Modifier
                            .size(width = 48.dp, height = 40.dp)
                            .testTag("mini_player_play_button")
                    ) {
                        // Tapped often, so a quick crossfade rather than a swap or a flourish.
                        Crossfade(
                            targetState = if (isBuffering) 0 else if (isPlaying) 1 else 2,
                            animationSpec = tween(120),
                            label = "miniPlayIcon"
                        ) { state ->
                            when (state) {
                                0 -> LoadingIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                                1 -> Icon(Icons.Filled.Pause, contentDescription = "Pause")
                                else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                            }
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
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    /** The position being heard right now; the lyrics follow this, not [positionMs]. */
    audiblePositionMs: () -> Long,
    durationMs: Long,
    speedPitchState: SpeedPitchState,
    sleepTimerState: SleepTimerState,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
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
    onCycleRepeat: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    /** Long-press on the speed button: back to 1×, original pitch, no reverb. */
    onResetSpeedPitch: () -> Unit = {},
    showSpeedResetHint: Boolean = false,
    onSpeedResetHintShown: () -> Unit = {},
    onOpenSleepTimerSheet: () -> Unit,
    /** Make a picture of this song to share. */
    onShareSong: () -> Unit = {},
    /** Reads the three bands of what is playing, for the Reactive backdrop. */
    readAudioLevels: (FloatArray) -> Unit = {},
    onWantAudioLevels: (Boolean) -> Unit = {},
    /** This build can cast, and where the music is coming out if it is. */
    castSupported: Boolean = false,
    castState: io.github.roggzuay.ondalune.cast.CastState = io.github.roggzuay.ondalune.cast.CastState(),
    /** The saved sound for this song, offered above the title. */
    soundOffer: SoundOffer? = null,
    onApplySoundOffer: () -> Unit = {},
    onDismissSoundOffer: () -> Unit = {},
    onOpenArtist: () -> Unit = {},
    /** The playlist the queue came from, while the current track is in it; named in the header. */
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
    /** Fill the room under the cover with its own colour, giving way to the surface by the title. */
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
            // Tablets get a framed cover instead of one filling the screen (a phone-sized picture
            // spread over a tablet came out soft and split the screen in two).
            val tablet = minOf(availWidth, availHeight) >= 600.dp
            val lyricsBeside = tablet && availWidth > availHeight
            val sidePadding = if (availWidth < 380.dp) 16.dp else 22.dp
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            // The controls' height and where their title row ends inside them, in pixels. Both are
            // measured within the controls, not on screen, so they hold still while the player's
            // own layer scales during an opening.
            var controlsHeight by remember { mutableIntStateOf(0) }
            var titleRowBottom by remember { mutableIntStateOf(0) }
            // The cover runs from the top edge, under the status bar and the top bar, fills the
            // width and reaches down behind the title and artist, fading out just above the
            // progress bar: the picture is the room the controls sit in rather than a square set
            // above them. Its height follows the layout, down to the title row's end and a little
            // past it. On a short window the controls take more of the height and it stays a wide
            // band; in a window wider than it is tall it is the leading pane.
            val topInset = insets.calculateTopPadding()
            val heroWidth = if (wide) minOf(availHeight, availWidth * 0.58f) else availWidth
            val heroHeight = when {
                wide -> availHeight
                controlsHeight == 0 -> availWidth + topInset
                else -> with(LocalDensity.current) {
                    (availHeight - insets.calculateBottomPadding() - (controlsHeight - titleRowBottom).toDp())
                        .coerceIn(availWidth * 0.7f, availHeight)
                }
            }
            // Read in layer and draw blocks only: it moves every frame of the change.
            val lyricsShown = animateFloatAsState(
                targetValue = if (lyricsOpen) 1f else 0f,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
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
                    readLevels = readAudioLevels,
                    onWantLevels = onWantAudioLevels
                )
            }

            // An album with moving artwork plays it over the whole player, unblurred; the still
            // cover above the title steps aside once it is in. Others keep the cover and its blur.
            val motionFull = remember { mutableFloatStateOf(0f) }
            // A tap on the moving artwork leaves it and the play buttons alone on screen; another
            // tap brings the rest back. Anything that takes the artwork away brings it back too.
            var bare by remember { mutableStateOf(false) }
            val motionShowing by remember { derivedStateOf { motionFull.floatValue > 0.5f } }
            if (bare && (!motionShowing || lyricsOpen || isUpNextBottomSheetOpen)) SideEffect { bare = false }
            val bareShown = animateFloatAsState(if (bare) 1f else 0f, tween(420, easing = FastOutSlowInEasing), label = "bare")
            if (!wide && !tablet) {
                val fullyOpen by remember(lyricsOpen) { derivedStateOf { sheet.fraction >= 0.999f && !lyricsOpen } }
                MotionBackdrop(
                    track = track,
                    playerOpen = fullyOpen,
                    shown = motionFull,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = sheet.backdropAlpha }
                        .pointerInput(Unit) { detectTapGestures(onTap = { if (motionShowing) bare = !bare }) },
                    shade = { 1f - bareShown.value }
                )
            } else SideEffect { motionFull.floatValue = 0f }

            if (!tablet) PlayerHero(
                sheet = sheet,
                motionFull = { motionFull.floatValue },
                onTapArt = { if (motionShowing) bare = !bare },
                track = track,
                queue = queue,
                wide = wide,
                lyricsOpen = lyricsOpen,
                lyricsShown = { lyricsShown.value },
                topBarHeight = topInset + 60.dp,
                heroWidth = heroWidth,
                heroHeight = heroHeight,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSwipePrevious,
                onSwipeDownDelta = onSwipeDownDelta,
                onSwipeDownFinish = onSwipeDownFinish,
                modifier = Modifier
                    .align(if (wide) Alignment.TopStart else Alignment.TopCenter)
                    .size(heroWidth, heroHeight)
            )

            val topIconColor by animateColorAsState(
                targetValue = if (lyricsOpen || tablet) MaterialTheme.colorScheme.onSurface else Color.White,
                animationSpec = tween(320),
                label = "topIconColor"
            )
            val topBar = @Composable {
                AnimatedVisibility(visible = !bare, enter = fadeIn(tween(320)), exit = fadeOut(tween(240))) {
                Box(openingContent) {
                    PlayerTopBar(
                        iconColor = topIconColor,
                        soundShaped = eqPreset != EqualizerPreset.FLAT_STUDIO,
                        speedShaped = speedPitchState.speed != 1.0f || speedPitchState.effectivePitch != 1.0f || reverbState.isActivelyProcessing,
                        showSpeedResetHint = showSpeedResetHint,
                        castSupported = castSupported,
                        onCollapse = onCollapse,
                        onOpenSonicLab = onOpenSonicLab,
                        onOpenSpeedPitchSheet = onOpenSpeedPitchSheet,
                        onResetSpeedPitch = onResetSpeedPitch,
                        onSpeedResetHintShown = onSpeedResetHintShown
                    )
                }
                }
            }
            // The lyrics come up where the cover is; nothing below them moves.
            val lyricsSlot = @Composable { slot: Modifier ->
                Box(slot) {
                    AnimatedVisibility(
                        visible = lyricsOpen,
                        enter = fadeIn(tween(320, delayMillis = 80)) +
                            slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 10 },
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
                    // Measured with everything showing only: the cover keeps its size while the
                    // lyrics fold the controls away and back.
                    modifier = area.onSizeChanged { if (!lyricsOpen && lyricsShown.value == 0f && bareShown.value == 0f) controlsHeight = it.height },
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
                        repeatMode = repeatMode,
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
                        onCycleRepeat = onCycleRepeat,
                        onToggleStar = onToggleStar,
                        onOpenSleepTimerSheet = onOpenSleepTimerSheet,
                        onShareSong = onShareSong,
                        castState = castState,
                        soundOffer = soundOffer,
                        onApplySoundOffer = onApplySoundOffer,
                        onDismissSoundOffer = onDismissSoundOffer,
                        onOpenArtist = onOpenArtist,
                        onOpenAudioDetails = { showAudioDetails = true },
                        onTitleRowBottom = { if (!lyricsOpen && lyricsShown.value == 0f && bareShown.value == 0f) titleRowBottom = it },
                        keepDetails = lyricsBeside,
                        bare = bare,
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
                if (tablet) {
                    val cover = @Composable { area: Modifier ->
                        BoxWithConstraints(area, contentAlignment = Alignment.Center) {
                            TabletCoverCard(
                                track = track,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSwipePrevious,
                                modifier = Modifier.size(minOf(maxWidth, maxHeight) - 8.dp)
                            )
                        }
                    }
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        topBar()
                        if (lyricsBeside) {
                            // Landscape: cover and controls in one centred column, lyrics beside it.
                            Row(
                                Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(56.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier.width(minOf(availWidth * 0.42f, 520.dp)).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    cover(Modifier.weight(1f).fillMaxWidth())
                                    Spacer(Modifier.height(12.dp))
                                    controls(Modifier.fillMaxWidth())
                                }
                                AnimatedVisibility(
                                    visible = lyricsOpen,
                                    enter = fadeIn(tween(320)) + expandHorizontally(tween(320)),
                                    exit = fadeOut(tween(200)) + shrinkHorizontally(tween(320)),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    lyricsSlot(Modifier.fillMaxSize())
                                }
                            }
                        } else {
                            // Portrait: the phone's order, at a width the picture can hold.
                            Column(
                                Modifier.weight(1f).widthIn(max = 580.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    if (!lyricsOpen) cover(Modifier.fillMaxSize())
                                    lyricsSlot(Modifier.fillMaxSize())
                                }
                                controls(Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else if (wide) {
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
                        // The controls keep their own height at the bottom; the cover's height
                        // follows them, never the other way round.
                        controls(Modifier.widthIn(max = 620.dp))
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
 * melting into the background behind the title, or towards the controls beside it. With the
 * lyrics open it stays, blurred and dimmed, behind them.
 *
 * The picture is drawn as a square of the hero's longer side, centred, so it is cropped rather
 * than squeezed: the same square the mini player's thumbnail shows, which is what lets the one
 * grow out of the other.
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
    topBarHeight: Dp,
    heroWidth: Dp,
    /** How far the full-screen moving artwork has come in over the player. */
    motionFull: () -> Float,
    onTapArt: () -> Unit,
    heroHeight: Dp,
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
    val latestTapArt by rememberUpdatedState(onTapArt)
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
    // The picture's square takes the hero's longer side, so the hero is a window onto it.
    val artSide = max(heroWidth, heroHeight)
    val sizePx = with(LocalDensity.current) { artSide.roundToPx() }
    val frame = with(LocalDensity.current) { Size(heroWidth.toPx(), heroHeight.toPx()) }
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
    val topFade = with(LocalDensity.current) { topBarHeight.toPx() }
    // Blur needs Android 12; before it the cover behind the lyrics is dimmed harder instead.
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val behindLyrics by remember(lyricsShown) { derivedStateOf { lyricsShown() > 0f } }

    // The outer box takes the gestures and stays put; the inner one moves, so finger positions are
    // never measured in a moving frame.
    val coverMoves by remember(lyricsOpen) { derivedStateOf { sheet.fraction >= 0.999f && !lyricsOpen } }
    var artworkInfo by remember { mutableStateOf(false) }
    if (artworkInfo) MotionArtworkInfoSheet(track, onDismiss = { artworkInfo = false })
    Box(
        modifier = modifier
            .onGloballyPositioned { sheet.fullArt = it.rootRect() }
            // Holding the cover tells where its artwork comes from.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { latestTapArt() },
                    onLongPress = { if (sheet.fraction >= 0.999f) artworkInfo = true }
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
                .requiredSize(artSide)
                .then(sheet.artTransform(frame))
                .graphicsLayer {
                    // Straight sideways, no tilt: rotating around the centre made the leading
                    // edge dip, as if the cover rode a wheel.
                    translationX = artDragX
                    val dragged = 1f - abs(artDragX / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.7f)
                    // Behind the lyrics it is no cover for the mini player to take over, so it
                    // fades with the rest of the player.
                    alpha = dragged * lerp(1f, sheet.contentAlpha, lyricsShown()) * (1f - motionFull())
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
                    // The hero is the middle of this square; everything below is placed from its
                    // frame, not the square's edges.
                    val left = (size.width - frame.width) / 2f
                    val top = (size.height - frame.height) / 2f
                    val bottom = top + frame.height
                    // The cover melts into the background over its last three tenths: behind the
                    // title and artist, which sit on the tail of the fade, or towards the controls
                    // beside it. The fade comes in with the rest of the player, so the cover
                    // leaves the mini player whole.
                    fun kept(alpha: Float) = Color.Black.copy(alpha = lerp(1f, alpha, shown))
                    val stops = arrayOf(0f to kept(1f), 0.4f to kept(0.7f), 0.75f to kept(0.22f), 1f to kept(0f))
                    val span = frame.height * 0.3f
                    // As it thins out under the title the picture leans towards the surface colour
                    // too, so the text keeps its contrast on a bright cover in the dark theme and
                    // on a dark one in the light theme.
                    if (!wide) drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to surface.copy(alpha = 0.38f * shown),
                            startY = bottom - span,
                            endY = bottom
                        ),
                        topLeft = Offset(0f, bottom - span),
                        size = Size(size.width, span)
                    )
                    // A light shade under the status bar and the top bar keeps their white icons
                    // readable on a bright picture; it thins out to nothing, so it leaves no edge.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.34f * shown * (1f - lyric)),
                            0.6f to Color.Black.copy(alpha = 0.14f * shown * (1f - lyric)),
                            1f to Color.Transparent,
                            startY = top,
                            endY = top + topFade
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, topFade)
                    )
                    val fade = if (wide) {
                        // From inside the pane to its far edge.
                        if (rtl) Brush.horizontalGradient(*stops, startX = size.width - left - frame.width * 0.6f, endX = left)
                        else Brush.horizontalGradient(*stops, startX = left + frame.width * 0.6f, endX = size.width - left)
                    } else {
                        Brush.verticalGradient(*stops, startY = bottom - span, endY = bottom)
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
                        else fadeIn(tween(220)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 1.06f) togetherWith
                            ExitTransition.KeepUntilTransitionsFinished
                    },
                    label = "coverChange"
                ) { shown ->
                    // A swiped-away cover is gone from its first leaving frame (an exit
                    // animation would still draw it once, back in the middle).
                    val hidden = exitHidden[0] && transition.targetState == EnterExitState.PostExit
                    // The whole cover, as wide as the hero, at its top; while the player opens it
                    // starts as the whole square (the mini player's thumbnail) and settles there.
                    // Its lower edge melts into the blurred cover behind the player.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (hidden) 0f else 1f
                                val f = sheet.fraction.coerceIn(0f, 1f)
                                val side = min(frame.width, frame.height)
                                val scale = lerp(1f, side / size.width, f)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                translationY = (size.height - frame.height) / 2f * f
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val melt = sheet.fraction.coerceIn(0f, 1f)
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Black,
                                        0.45f to Color.Black.copy(alpha = lerp(1f, 0.6f, melt)),
                                        1f to Color.Black.copy(alpha = 1f - melt),
                                        startY = size.height * 0.72f,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(0f, size.height * 0.72f),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        // The album's moving artwork, or the cover drifting gently, where there is
                        // some; only while the whole player is open and the lyrics are down.
                        MotionCover(shown, playerOpen = coverMoves, modifier = Modifier.fillMaxSize(), original = wide) {
                            ExpressiveArtwork(
                                seedHex = shown.paletteSeedHex,
                                accentHex = shown.accentHex,
                                artStyleIndex = shown.artStyleIndex,
                                coverArtUrl = shown.coverArtUrl,
                                fullBleed = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                                    // A square over a square: no stretch.
                                    scaleX = sizePx / size.width
                                    scaleY = scaleX
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
    soundShaped: Boolean,
    speedShaped: Boolean,
    /** White over the picture; the theme's text colour over the lyrics' dimmed backdrop. */
    iconColor: Color,
    showSpeedResetHint: Boolean,
    /** This build can cast: the route button goes in the top bar beside the sound tools. */
    castSupported: Boolean,
    onCollapse: () -> Unit,
    onOpenSonicLab: () -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    onResetSpeedPitch: () -> Unit,
    onSpeedResetHintShown: () -> Unit
) {
    // A soft dark backing keeps white icons readable over light covers.
    val backing = if (iconColor.luminance() > 0.95f) Color.Black.copy(alpha = 0.28f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveBounceIconButton(
            onClick = onCollapse,
            size = 48.dp,
            containerColor = backing,
            contentColor = iconColor,
            modifier = Modifier.testTag("collapse_player_button")
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player")
        }

        Spacer(Modifier.weight(1f))

        // Google's own route button: it draws nothing until there is a device on the network.
        if (castSupported) CastButton(
            modifier = Modifier
                .size(44.dp)
                .padding(end = 2.dp)
                .testTag("cast_button")
        )

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
            ExpressiveBounceIconButton(
                onClick = onOpenSpeedPitchSheet,
                onLongClick = onResetSpeedPitch,
                active = speedShaped,
                size = 48.dp,
                containerColor = backing,
                contentColor = if (speedShaped) MaterialTheme.colorScheme.primaryFixedDim else iconColor,
                modifier = Modifier.testTag("open_speed_pitch_pill")
            ) {
                Icon(Icons.Filled.Speed, contentDescription = "Speed & pitch", modifier = Modifier.size(22.dp))
            }
        }

        ExpressiveBounceIconButton(
            onClick = onOpenSonicLab,
            active = soundShaped,
            size = 48.dp,
            containerColor = backing,
            contentColor = if (soundShaped) MaterialTheme.colorScheme.primaryFixedDim else iconColor,
            modifier = Modifier.testTag("open_sonic_lab_pill")
        ) {
            Icon(
                imageVector = Icons.Filled.Equalizer,
                contentDescription = "Equalizer",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Everything under the cover: what is playing, where it is, and the controls. */
/**
 * The saved sound, offered above the song title: no card, no scrim, just a line over whatever the
 * backdrop is doing. Tapping it puts the sound on; it fades by itself after a few seconds either
 * way, and nothing changes unless it is tapped.
 */
@Composable
private fun SavedSoundStrip(
    offer: SoundOffer?,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    // Both states go away on their own: the question after eight seconds, the confirmation after
    // three, so the strip never becomes furniture.
    LaunchedEffect(offer?.trackId, offer?.applied) {
        if (offer == null) return@LaunchedEffect
        delay(if (offer.applied) 3_000L else 8_000L)
        onDismiss()
    }
    AnimatedVisibility(
        visible = offer != null,
        enter = fadeIn(tween(420)) + expandVertically(tween(320, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(320)) + shrinkVertically(tween(260))
    ) {
        val applied = offer?.applied == true
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .clip(cornerShape(12.dp))
                .then(if (applied) Modifier else Modifier.clickable(onClick = onApply))
                .padding(vertical = 4.dp)
                .testTag("saved_sound_strip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (applied) "Playing your saved sound" else "Apply last audio profile?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1
            )
            Text(
                text = offer?.snapshot?.summary.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Where the music is coming out, when it is not this phone, and what that costs.
 *
 * The device fetches the song from your server and plays it itself, so Ondalune's sound engine is
 * not in the path at all. Saying so here is the difference between a feature and a bug report.
 */
@Composable
private fun CastingStrip(state: io.github.roggzuay.ondalune.cast.CastState) {
    AnimatedVisibility(
        visible = state.connected || state.connecting,
        enter = fadeIn(tween(300)) + expandVertically(tween(260, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(220)) + shrinkVertically(tween(200))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .testTag("casting_strip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Cast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Column {
                Text(
                    text = if (state.connecting && !state.connected) {
                        "Connecting to ${state.deviceName.ifBlank { "the other device" }}…"
                    } else {
                        "Playing on ${state.deviceName.ifBlank { "another device" }}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                if (state.connected) Text(
                    text = "Speed, pitch, reverb and the equalizer stay on this phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

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
    repeatMode: RepeatMode,
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
    onCycleRepeat: () -> Unit,
    onToggleStar: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    /** Make a picture of this song to share. */
    onShareSong: () -> Unit = {},
    onOpenArtist: () -> Unit,
    onOpenAudioDetails: () -> Unit,
    onTitleRowBottom: (Int) -> Unit,
    /** Where the music is coming out, when it is not this phone. */
    castState: io.github.roggzuay.ondalune.cast.CastState = io.github.roggzuay.ondalune.cast.CastState(),
    /** The saved sound for this song, waiting above the title; null when there is none. */
    soundOffer: SoundOffer? = null,
    onApplySoundOffer: () -> Unit = {},
    onDismissSoundOffer: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Lyrics open beside the cover (tablet landscape): keep the title and transport. */
    keepDetails: Boolean = false,
    /** Only the play buttons stay, settled at the bottom, over the moving artwork. */
    bare: Boolean = false
) {
    val scroll = rememberScrollState()
    // The title and artist sit on the tail of the cover's fade: a soft halo in the surface colour
    // keeps them readable whatever the picture.
    val coverShadow = Shadow(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), blurRadius = 12f)
    Column(
        // It scrolls only when it can't fit (a small window, a large font): a scrolling column
        // would take the pull down that closes the player.
        modifier = modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0),
        // Packed at the bottom: the height above goes to the picture, or to the lyrics.
        verticalArrangement = Arrangement.Bottom
    ) {
        // With the lyrics up only the progress bar and the row of buttons stay; the title and the
        // transport slip away downwards so the lyrics get the height, and come back when closed.
        val lyricsOpen = activeTab == NowPlayingSheetTab.LYRICS && !keepDetails
        Column {
            // The cover reaches down behind this row; it needs to know where the row ends.
            AnimatedVisibility(
                visible = !lyricsOpen && !bare,
                enter = expandVertically(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(320, delayMillis = 100)),
                exit = shrinkVertically(tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(200))
            ) {
            Column {
            CastingStrip(castState)
            SavedSoundStrip(
                offer = soundOffer?.takeIf { it.trackId == track.id },
                onApply = onApplySoundOffer,
                onDismiss = onDismissSoundOffer
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onTitleRowBottom((it.positionInParent().y + it.size.height).roundToInt()) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized.copy(shadow = coverShadow),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(initialDelayMillis = 1800)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.titleMediumEmphasized.copy(shadow = coverShadow),
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
                            style = MaterialTheme.typography.titleMedium.copy(shadow = coverShadow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val trackActions = LocalTrackActions.current
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = coverShadow),
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
                val heartHaptics = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
                if (showStarButton) {
                    Spacer(Modifier.width(12.dp))
                    IconToggleButton(
                        checked = track.starred,
                        onCheckedChange = { starred ->
                            heartHaptics.performToggleOption(starred)
                            onToggleStar()
                        },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        modifier = Modifier.size(56.dp).testTag("now_playing_star_button")
                    ) {
                        FavouriteHeartIcon(
                            starred = track.starred,
                            contentDescription = "Star on OpenSubsonic Server"
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            }
            }
            AnimatedVisibility(visible = !bare, enter = bareEnter, exit = bareExit) {
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
            }
        }

        AnimatedVisibility(
            visible = !lyricsOpen,
            enter = expandVertically(tween(320, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(320, delayMillis = 100)),
            exit = shrinkVertically(tween(320, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            ExpressiveTransportGroup(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onSkipPrevious = onSkipPrevious,
                onTogglePlay = onTogglePlay,
                onSkipNext = onSkipNext,
                modifier = Modifier.padding(top = 18.dp)
            )
        }

        // Volume sits between the transport and the row of toggles, as the foot of the controls.
        AnimatedVisibility(visible = !bare && !lyricsOpen, enter = bareEnter, exit = bareExit) {
            PlayerVolumeRow(modifier = Modifier.padding(top = 14.dp))
        }

        // Folding away below the transport is what carries the play buttons down to the bottom.
        AnimatedVisibility(visible = !bare, enter = bareEnter, exit = bareExit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val haptics = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
            val queueOpen = activeTab == NowPlayingSheetTab.QUEUE
            // The transport folds away with the lyrics up; play/pause stays reachable here.
            AnimatedVisibility(
                visible = lyricsOpen,
                enter = expandHorizontally(tween(320)) + fadeIn(tween(240, delayMillis = 80)),
                exit = shrinkHorizontally(tween(320)) + fadeOut(tween(160)),
                modifier = Modifier.weight(1f)
            ) {
                FilledIconButton(onClick = onTogglePlay, modifier = Modifier.testTag("lyrics_play_pause")) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
            // The tab itself, not lyricsOpen: with lyrics beside the cover the controls stay but it still closes.
            val lyricsTab = activeTab == NowPlayingSheetTab.LYRICS
            PlayerToggle(
                checked = lyricsTab,
                onToggle = { onSelectTab(if (lyricsTab) NowPlayingSheetTab.COVER else NowPlayingSheetTab.LYRICS) },
                icon = Icons.Filled.Lyrics,
                description = "Lyrics",
                tag = "now_playing_tab_lyrics",
                modifier = Modifier.weight(1f)
            )
            PlayerToggle(
                checked = queueOpen,
                onToggle = { onSelectTab(if (queueOpen) NowPlayingSheetTab.COVER else NowPlayingSheetTab.QUEUE) },
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                description = "Queue",
                tag = "now_playing_tab_queue",
                modifier = Modifier.weight(1f)
            )
            Row(Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PlayerToggle(
                    checked = shuffleEnabled,
                    onToggle = {
                        haptics.performToggleOption(!shuffleEnabled)
                        onToggleShuffle()
                    },
                    icon = if (shuffleMode == ShuffleMode.SMART) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
                    description = "Shuffle: ${shuffleMode.label}",
                    tag = "shuffle_button",
                    modifier = Modifier.weight(1f)
                )
                // Three states: the loop dimmed for off, lit for the whole queue, and the "1" loop
                // for this song. Off and all share an icon, told apart by the lit state.
                PlayerToggle(
                    checked = repeatMode != RepeatMode.OFF,
                    onToggle = {
                        haptics.performToggleOption(repeatMode == RepeatMode.OFF)
                        onCycleRepeat()
                    },
                    icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    description = repeatMode.label,
                    tag = "repeat_button",
                    modifier = Modifier.weight(1f)
                )
            }
            PlayerToggle(
                checked = sleepTimerActive,
                onToggle = onOpenSleepTimerSheet,
                icon = Icons.Filled.Bedtime,
                description = "Sleep timer",
                tag = "open_sleep_timer_pill",
                modifier = Modifier.weight(1f)
            )
            PlayerToggle(
                checked = false,
                onToggle = onShareSong,
                icon = Icons.Filled.IosShare,
                description = "Share this song",
                tag = "share_song_pill",
                modifier = Modifier.weight(1f)
            )
            download?.let { PlayerDownloadButton(it, Modifier.weight(1f)) }
        }
        }
    }
}

/**
 * The system music volume, under the transport. It fills the band that used to sit empty between
 * the buttons and the foot of the screen, so the cover above runs to its natural height instead of
 * being stretched to cover it. The slider wears the song's own colours like everything else here.
 *
 * Hardware keys change the same stream, so the row listens for that and follows.
 */
@Composable
private fun PlayerVolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(android.media.AudioManager::class.java) }
    val stream = android.media.AudioManager.STREAM_MUSIC
    val maxVolume = remember(audio) { (audio?.getStreamMaxVolume(stream) ?: 15).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf((audio?.getStreamVolume(stream) ?: 0).toFloat()) }
    // The buttons on the side of the phone move the same stream: follow them while the player is up.
    DisposableEffect(audio) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val observer = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                audio?.getStreamVolume(stream)?.let { volume = it.toFloat() }
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI, true, observer
            )
        }
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                runCatching { audio?.setStreamVolume(stream, it.roundToInt(), 0) }
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = (maxVolume - 1).coerceAtLeast(0),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Volume" }
                .testTag("player_volume_slider")
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val bareEnter = expandVertically(tween(420, easing = FastOutSlowInEasing)) + fadeIn(tween(320, delayMillis = 120))
private val bareExit = shrinkVertically(tween(420, easing = FastOutSlowInEasing)) + fadeOut(tween(180))

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

/** Just the icon, with no backing: the theme colour when on, quiet when off. */
@Composable
private fun playerToggleColors() = IconButtonDefaults.iconToggleButtonColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
)

/** One of the icon toggles in the player's bottom row. */
@Composable
private fun PlayerToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    description: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        colors = playerToggleColors(),
        shapes = IconButtonDefaults.toggleableShapes(),
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
                            text = "${countOf(queue.size, "track")} in queue",
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

/** The shared [DownloadButton] (cloud, spinner, progress ring, done) as a bare icon toggle. */
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
        IconToggleButton(
            checked = status.phase == DownloadPhase.SAVED,
            onCheckedChange = { onClick() },
            colors = playerToggleColors(),
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("now_playing_download")
        ) { content() }
    }
}
