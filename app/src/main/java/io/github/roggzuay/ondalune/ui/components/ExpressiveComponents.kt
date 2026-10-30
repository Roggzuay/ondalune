@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.roggzuay.ondalune.ui.components

import io.github.roggzuay.ondalune.ui.theme.ArtworkPaletteCache
import android.os.Build
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ripple
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.roggzuay.ondalune.ui.theme.LocalCornerScale
import io.github.roggzuay.ondalune.ui.theme.LocalInterfaceStyle
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec
import io.github.roggzuay.ondalune.ui.theme.ArtShape
import io.github.roggzuay.ondalune.ui.theme.artShape
import io.github.roggzuay.ondalune.data.model.InterfaceStyle
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import androidx.compose.runtime.State
import io.github.roggzuay.ondalune.ui.theme.artworkCacheKey
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.Morph
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ButtonGroup
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.R
import io.github.roggzuay.ondalune.data.model.AppIconPreviewFrameShape
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.data.model.ArtworkShapeMode
import io.github.roggzuay.ondalune.data.model.DockStyle
import io.github.roggzuay.ondalune.data.model.ShuffleMode
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.parseHexColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Physics-driven Material 3 Expressive Play/Pause button for Mini-Player & Cards.
 * Uses viscous elastic expansion and corner morphing (no rotational tilt).
 */
@Composable
fun ExpressivePlayMorphButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 52.dp,
    testTagName: String = "expressive_play_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticManager.performGluePressStart(isPrimaryCenter = true)
        }
    }

    val scaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "playScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "playScaleY"
    )

    val cornerPercent by animateFloatAsState(
        targetValue = when {
            isPressed -> 24f
            isPlaying -> 28f
            else -> 45f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "playCorner"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        label = "playBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        label = "playFg"
    )

    Surface(
        modifier = modifier
            .size(buttonSize)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
            .clip(RoundedCornerShape(percent = cornerPercent.roundToInt().coerceIn(18, 50)))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticManager.performPlayPauseTransition(isStartingPlay = !isPlaying)
                    onClick()
                }
            )
            .testTag(testTagName),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (isPlaying) 8.dp else 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(buttonSize * 0.44f),
                    color = contentColor,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(buttonSize * 0.50f)
                )
            }
        }
    }
}

/**
 * Tactile Material 3 Expressive Boxed Icon Button with elastic glue stretch and corner morphing.
 */
@Composable
fun ExpressiveBounceIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 48.dp,
    containerColor: Color = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    },
    contentColor: Color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()

    val scaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.14f else if (active) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "iconScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (active) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "iconScaleY"
    )

    val cornerDp by animateDpAsState(
        targetValue = if (isPressed) 24.dp else if (active) 16.dp else 18.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconCorner"
    )

    val safeCornerDp = cornerDp.coerceAtLeast(0.dp)

    Surface(
        modifier = modifier
            .size(size)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
            .clip(RoundedCornerShape(safeCornerDp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    hapticManager.performSubtleTick()
                    onClick()
                }
            ),
        shape = RoundedCornerShape(safeCornerDp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Spring-animated interactive card/box with smooth elastic compression and corner morphing.
 */
@Composable
fun SpringPressableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    cornerRadius: Dp = 24.dp,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleX by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScaleX"
    )
    val scaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScaleY"
    )

    val corner = cornerRadius * LocalCornerScale.current
    val animatedCorner by animateDpAsState(
        targetValue = if (isPressed) (corner * 0.78f) else corner,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardCorner"
    )

    val safeAnimatedCorner = animatedCorner.coerceAtLeast(0.dp)

    val pressScale = Modifier.graphicsLayer {
        this.scaleX = scaleX
        this.scaleY = scaleY
    }
    val shape = RoundedCornerShape(safeAnimatedCorner)
    if (onLongClick == null) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier.then(pressScale),
            shape = shape,
            color = color
        ) {
            content()
        }
    } else {
        // Same press physics, plus long-press (Surface's onClick has no long-click variant).
        Surface(
            modifier = modifier
                .then(pressScale)
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = shape,
            color = color
        ) {
            content()
        }
    }
}

/**
 * Custom Material 3 Expressive Navigation Dock supporting both:
 * 1. FLOATING Dock (elevated capsule suspended with margins)
 * 2. STATIC Default Dock (full-width anchored dock)
 *
 * Uses plump, tall squircle-box active indicators that elastically expand and push neighbors
 * like connected glue cells when selected!
 */
data class ExpressiveNavItem<T>(
    val id: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun <T> ExpressiveNavigationDock(
    items: List<ExpressiveNavItem<T>>,
    selectedId: T,
    dockStyle: DockStyle,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
    if (dockStyle == DockStyle.STATIC) {
        // Standard is transparent: the bar sits on the page itself instead of a tinted band.
        NavigationBar(
            modifier = modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        hapticManager.performSubtleTick()
                        onSelect(item.id)
                    },
                    icon = { Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = null) },
                    label = {
                        Text(
                            text = item.label,
                            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag(item.testTag)
                )
            }
        }
        return
    }

    // Floating: a slim frosted pill that blurs whatever scrolls behind it.
    val pill = RoundedCornerShape(percent = 50)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(10.dp, pill, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .frosted(hazeState, pill)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), pill)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.id == selectedId
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 52.dp else 32.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "dockIndicator"
                )
                val indicator by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "dockIndicatorColor"
                )
                val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            hapticManager.performSubtleTick()
                            onSelect(item.id)
                        }
                        .padding(vertical = 5.dp)
                        .testTag(item.testTag)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = indicatorWidth, height = 28.dp)
                            .clip(pill)
                            .background(indicator),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Frosted glass: a live blur of the content behind (Android 12+) under a light tint of the
 * surface colour. Without blur support it falls back to a mostly opaque surface.
 */
@Composable
fun Modifier.frosted(hazeState: HazeState?, shape: Shape): Modifier {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    return if (hazeState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tint = HazeTint(surface.copy(alpha = 0.62f)),
                    blurRadius = 26.dp,
                    noiseFactor = 0.04f
                )
            )
    } else {
        this.clip(shape).background(surface.copy(alpha = 0.96f))
    }
}

/**
 * Native Google Android 15/16 Media Player style Squiggly Waveform Scrubber:
 * - 120Hz VSYNC frame-paced wave phase (`withFrameNanos`) with calm, liquid motion
 * - Dual-ended C2-continuous Quintic Smootherstep envelope so the wave emerges tangentially
 *   and seamlessly from the flat line on the left and merges back into the flat line before
 *   the playhead (never popping out of an invisible wall)
 * - Uncluttered 44.dp wavelength, 3.6.dp refined amplitude, and a sleek un-boxed pill playhead
 *   with a breathing gap before the inactive track.
 */
@Composable
fun ExpressiveSquigglySlider(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    animatedWaveEnabled: Boolean = true,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showRemainingTime: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    accentColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val safeDuration = durationMs.coerceAtLeast(1000L)
    val hapticManager = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val rawTargetFraction = if (isDragging) {
        dragFraction
    } else {
        (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    // Smooth 120Hz interpolation of playback progress so the playhead glides without stepping
    val currentFraction by animateFloatAsState(
        targetValue = rawTargetFraction,
        animationSpec = if (isDragging) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "smoothProgressFraction"
    )

    val reducedMotion = LocalReducedMotion.current
    val effectiveAnimatedWave = animatedWaveEnabled && !reducedMotion

    val waveAmplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging && effectiveAnimatedWave) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessVeryLow
        ),
        label = "waveAmp"
    )

    val thumbExpandFactor by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbExpand"
    )

    // 120Hz VSYNC-locked phase progression (~3.2 seconds per full cycle — calm, liquid, uncluttered)
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, effectiveAnimatedWave) {
        if (isPlaying && effectiveAnimatedWave) {
            var lastNanos = 0L
            val twoPi = (2.0 * PI).toFloat()
            val radiansPerSec = 1.95f
            while (true) {
                withFrameNanos { frameNanos ->
                    if (lastNanos != 0L) {
                        val dtSec = ((frameNanos - lastNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
                        wavePhase = (wavePhase + dtSec * radiansPerSec) % twoPi
                    }
                    lastNanos = frameNanos
                }
            }
        }
    }

    val effectiveElapsedMs = if (isDragging) (dragFraction * safeDuration).toLong() else positionMs

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        hapticManager.performWaveformScrubStart(frac)
                        hapticManager.performWaveformScrubCommit()
                        onSeek((frac * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val startFrac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            isDragging = true
                            dragFraction = startFrac
                            hapticManager.performWaveformScrubStart(startFrac)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val newFrac = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                            dragFraction = newFrac
                            hapticManager.performWaveformScrubMove(newFrac)
                        },
                        onDragEnd = {
                            val target = (dragFraction * safeDuration).toLong()
                            isDragging = false
                            hapticManager.performWaveformScrubCommit()
                            onSeek(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            hapticManager.performSubtleTick()
                        }
                    )
                }
                .testTag("squiggly_progress_slider"),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padX = 10.dp.toPx()
                val rightEndX = (size.width - padX).coerceAtLeast(padX + 1f)
                val usableWidth = (rightEndX - padX).coerceAtLeast(1f)
                val centerY = size.height / 2f
                val activeX = padX + usableWidth * currentFraction
                val strokeWidthPx = 4.2.dp.toPx()
                val thumbGapPx = 6.dp.toPx()

                // 1. Inactive Track Line (starts after a clean breathing gap to the right of the playhead)
                val inactiveStartX = (activeX + thumbGapPx).coerceAtMost(rightEndX)
                if (rightEndX > inactiveStartX + 1f) {
                    drawLine(
                        color = inactiveColor,
                        start = Offset(inactiveStartX, centerY),
                        end = Offset(rightEndX, centerY),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }

                // 2. Active Wave Line (smoothly emerging from the flat line at padX and tapering back to flat before the playhead)
                val activeEndX = (activeX - thumbGapPx * 0.45f).coerceAtLeast(padX)
                val activeSpan = (activeEndX - padX).coerceAtLeast(0f)
                if (activeSpan > 0.5f) {
                    val spanRatio = (activeSpan / 64.dp.toPx()).coerceIn(0f, 1f)
                    val spanSmoothScale = spanRatio * spanRatio * (3f - 2f * spanRatio)

                    val maxAmpPx = 3.6.dp.toPx() * waveAmplitudeFactor * spanSmoothScale
                    val wavelengthPx = 44.dp.toPx()
                    val taperSpanPx = minOf(40.dp.toPx(), activeSpan * 0.46f).coerceAtLeast(1f)

                    val path = Path()
                    path.moveTo(padX, centerY)

                    if (maxAmpPx <= 0.15f) {
                        path.lineTo(activeEndX, centerY)
                    } else {
                        val step = 2.0f
                        var x = padX + step
                        while (x < activeEndX) {
                            val s = ((x - padX) / taperSpanPx).coerceIn(0f, 1f)
                            val e = ((activeEndX - x) / taperSpanPx).coerceIn(0f, 1f)
                            val startEnv = s * s * s * (s * (s * 6f - 15f) + 10f)
                            val endEnv = e * e * e * (e * (e * 6f - 15f) + 10f)
                            val localAmp = maxAmpPx * startEnv * endEnv

                            val angle = ((x - padX) / wavelengthPx) * (2f * PI.toFloat()) - wavePhase
                            val y = centerY + sin(angle) * localAmp
                            path.lineTo(x, y)
                            x += step
                        }
                        path.lineTo(activeEndX, centerY)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(activeColor, accentColor, activeColor),
                            startX = padX,
                            endX = activeEndX.coerceAtLeast(padX + 12f)
                        ),
                        style = Stroke(
                            width = strokeWidthPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 3. Sleek Un-Boxed Vertical Capsule Playhead Indicator
                val thumbW = (4.5.dp + 2.5.dp * thumbExpandFactor).toPx()
                val thumbH = (18.dp + 6.dp * thumbExpandFactor).toPx()
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(activeX - thumbW / 2f, centerY - thumbH / 2f),
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
                )
            }

            if (isDragging) {
                val previewMs = (dragFraction * safeDuration).toLong()
                val offsetPx = ((trackWidthPx - 64.dp.value) * dragFraction).roundToInt()
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(offsetPx.coerceAtLeast(0), -34) },
                    shape = cornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = formatDurationMs(previewMs),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(effectiveElapsedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (showRemainingTime) {
                    "-${formatDurationMs((safeDuration - effectiveElapsedMs).coerceAtLeast(0L))}"
                } else {
                    formatDurationMs(safeDuration)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Computes a clean 1–2 character monogram from an artist name for avatar badges when no dedicated
 * artist profile photo is present on the server.
 */
fun computeArtistMonogram(name: String): String {
    val words = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    if (words.isEmpty()) return "♪"
    return if (words.size >= 2) {
        "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
    } else {
        words[0].take(2).uppercase(Locale.US)
    }
}

/**
 * Runtime Android Activity Alias switcher for Ondalune's selectable launcher icons.
 */
object AppIconLauncherManager {
    /**
     * Launcher aliases from 1.0.0's icon set. They stay in the manifest (showing the current
     * default icon) so a home-screen shortcut or a chosen icon from 1.0.0 keeps working after an
     * update, and are switched off the next time an icon is picked.
     */
    private val LegacyAliases = listOf("Tide", "Amethyst", "Moss", "Cream", "Night", "Peach", "Wine")
        .map { "io.github.roggzuay.ondalune.LauncherAlias$it" }

    /**
     * Dynamically updates the Android launcher icon at runtime using `<activity-alias>` components
     * and [PackageManager.setComponentEnabledSetting] with [PackageManager.DONT_KILL_APP].
     */
    fun applyLauncherIconVariant(context: Context, selectedVariant: AppIconVariant): Boolean {
        return runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            AppIconVariant.entries.forEach { variant ->
                val component = ComponentName(pkg, variant.aliasClassName)
                val targetState = if (variant == selectedVariant) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    component,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            }
            LegacyAliases.forEach { alias ->
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Checks whether the `<activity-alias>` for [variant] is currently enabled in [PackageManager].
     */
    fun isAliasEnabled(context: Context, variant: AppIconVariant): Boolean {
        return runCatching {
            val pm = context.packageManager
            val component = ComponentName(context.packageName, variant.aliasClassName)
            when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // Never toggled at runtime: fall back to the manifest's android:enabled value.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                    pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * Returns the currently enabled [AppIconVariant] alias in [PackageManager], or [fallback].
     */
    fun getActiveAliasVariant(
        context: Context,
        fallback: AppIconVariant = AppIconVariant.MOONLIGHT
    ): AppIconVariant {
        return AppIconVariant.entries.firstOrNull { isAliasEnabled(context, it) } ?: fallback
    }
}

/**
 * Maps an [AppIconPreviewFrameShape] to a Compose [Shape] for the App Icon Settings Preview Gallery.
 */
fun appIconFrameShape(frameShape: AppIconPreviewFrameShape): Shape = when (frameShape) {
    AppIconPreviewFrameShape.SQUIRCLE -> RoundedCornerShape(percent = 24)
    AppIconPreviewFrameShape.CIRCLE_ROUND -> CircleShape
    AppIconPreviewFrameShape.ROUNDED_SQUARE -> RoundedCornerShape(percent = 14)
    AppIconPreviewFrameShape.TEARDROP -> RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 50,
        bottomStartPercent = 50,
        bottomEndPercent = 14
    )
}

/** Background and foreground layers of an icon variant's adaptive icon. */
fun appIconLayers(variant: AppIconVariant): Pair<Int, Int> = when (variant) {
    AppIconVariant.MOONLIGHT -> R.drawable.ic_launcher_bg_moonlight to R.drawable.ic_launcher_fg_moonlight
    AppIconVariant.PAPER -> R.drawable.ic_launcher_bg_paper to R.drawable.ic_launcher_fg_paper
    AppIconVariant.SLATE -> R.drawable.ic_launcher_bg_slate to R.drawable.ic_launcher_fg_slate
    AppIconVariant.GRAPHITE -> R.drawable.ic_launcher_bg_graphite to R.drawable.ic_launcher_fg_graphite
}

/**
 * The launcher icon as the home screen draws it: both adaptive layers scaled so the visible
 * 72dp window fills the frame, clipped to [frameShape]. [foregroundScale] and [foregroundAlpha]
 * let the intro animate the note separately from its wave background.
 */
@Composable
fun OndaluneBrandEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    variant: AppIconVariant = AppIconVariant.MOONLIGHT,
    frameShape: AppIconPreviewFrameShape = AppIconPreviewFrameShape.SQUIRCLE,
    foregroundScale: Float = 1f,
    foregroundAlpha: Float = 1f,
    backgroundShift: Float = 0f
) {
    val (bg, fg) = appIconLayers(variant)
    Box(
        modifier = modifier
            .size(size)
            .clip(appIconFrameShape(frameShape)),
        contentAlignment = Alignment.Center
    ) {
        // Adaptive layers are 108dp with the visible area in the middle 72dp: scale by 1.5.
        Image(
            painter = painterResource(bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f; translationX = backgroundShift * this.size.width * 0.12f }
        )
        Image(
            painter = painterResource(fg),
            contentDescription = "Ondalune icon (${variant.label})",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.5f * foregroundScale; scaleY = 1.5f * foregroundScale; alpha = foregroundAlpha }
        )
    }
}

/**
 * Renders generative Material 3 Expressive album art, Artist Monogram Avatar, OR remote OpenSubsonic
 * cover/artist art, with live shape morphing.
 */
@Composable
fun ExpressiveArtwork(
    seedHex: String,
    accentHex: String,
    artStyleIndex: Int,
    coverArtUrl: String = "",
    shapeMode: ArtworkShapeMode = ArtworkShapeMode.SQUIRCLE,
    isSpinning: Boolean = false,
    monogramText: String = "",
    modifier: Modifier = Modifier,
    /** Overrides the theme's artwork cut (track rows use the theme's smaller row shape). */
    styleShape: ArtShape? = null
) {
    // Border/placeholder tint: the cover's real colours once extracted (server hints are guesses,
    // and painted a colourful rim around black-and-white covers).
    val extracted = if (coverArtUrl.isNotBlank()) ArtworkPaletteCache.peek(coverArtUrl) else null
    val primaryColor = extracted?.seed ?: parseHexColor(seedHex)
    val secondaryColor = extracted?.swatches?.getOrNull(1) ?: extracted?.seed ?: parseHexColor(accentHex)

    val shouldSpin = isSpinning && shapeMode != ArtworkShapeMode.SQUIRCLE && !LocalReducedMotion.current
    // Rotation is read only inside graphicsLayer (draw phase), so spinning never recomposes.
    val spin: State<Float>? = if (shouldSpin) {
        rememberInfiniteTransition(label = "vinylSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinDeg"
        )
    } else {
        null
    }

    // Squircle <-> 12-sided cookie is a continuous MaterialShapes morph, not a hard swap.
    val cookieMorph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
    val cookieProgress by animateFloatAsState(
        targetValue = if (shapeMode == ArtworkShapeMode.COOKIE) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "artMorph"
    )
    val spec = LocalStyleSpec.current
    val themeShape = styleShape ?: spec.artShape
    val standard = !spec.artBorder
    val shape = when {
        shapeMode == ArtworkShapeMode.VINYL -> CircleShape
        // Only the squircle theme morphs into the playing cookie; other themes keep their cut.
        themeShape != ArtShape.SQUIRCLE || cookieProgress == 0f ->
            artShape(themeShape, key = if (coverArtUrl.isNotBlank()) artworkCacheKey(coverArtUrl).hashCode() else artStyleIndex + monogramText.hashCode())
        else -> remember(cookieProgress) { MorphShape(cookieMorph, cookieProgress) }
    }

    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = spin?.value ?: 0f }
            .clip(shape)
            .then(
                if (standard) Modifier else Modifier.border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.65f),
                            secondaryColor.copy(alpha = 0.35f)
                        )
                    ),
                    shape = shape
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Generated art only stands in for tracks that truly have no cover.
        if (coverArtUrl.isBlank()) Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF141122),
                        primaryColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.75f),
                        Color(0xFF0D0C16)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            when (artStyleIndex % 5) {
                0 -> {
                    for (i in 1..4) {
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.18f * i),
                            radius = (minOf(w, h) * 0.14f * i),
                            center = Offset(w * 0.62f, h * 0.38f),
                            style = Stroke(width = (3.5f * i))
                        )
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor, Color.Transparent),
                            center = Offset(w * 0.35f, h * 0.68f),
                            radius = minOf(w, h) * 0.45f
                        ),
                        center = Offset(w * 0.35f, h * 0.68f),
                        radius = minOf(w, h) * 0.45f
                    )
                }
                1 -> {
                    for (waveIdx in 0..5) {
                        val path = Path()
                        val yBase = h * (0.2f + waveIdx * 0.12f)
                        path.moveTo(0f, yBase)
                        var x = 0f
                        while (x <= w) {
                            val y = yBase + sin((x / w) * 2f * PI.toFloat() + waveIdx) * (h * 0.08f)
                            path.lineTo(x, y)
                            x += 8f
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.15f + waveIdx * 0.05f),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                2 -> {
                    drawCircle(
                        color = primaryColor,
                        radius = minOf(w, h) * 0.28f,
                        center = Offset(w * 0.5f, h * 0.42f)
                    )
                    drawRoundRect(
                        color = secondaryColor.copy(alpha = 0.8f),
                        topLeft = Offset(w * 0.18f, h * 0.56f),
                        size = Size(w * 0.64f, h * 0.22f),
                        cornerRadius = CornerRadius(24f, 24f)
                    )
                }
                3 -> {
                    for (ring in 1..6) {
                        drawRoundRect(
                            color = secondaryColor.copy(alpha = 0.16f + ring * 0.06f),
                            topLeft = Offset(w * (0.08f * ring), h * (0.08f * ring)),
                            size = Size(w * (1f - 0.16f * ring).coerceAtLeast(0.1f), h * (1f - 0.16f * ring).coerceAtLeast(0.1f)),
                            cornerRadius = CornerRadius(18f * ring, 18f * ring),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
                else -> {
                    val cx = w / 2f
                    val cy = h / 2f
                    for (ray in 0 until 16) {
                        val angle = (ray / 16f) * (2f * PI.toFloat())
                        drawLine(
                            color = Color.White.copy(alpha = 0.24f),
                            start = Offset(cx, cy),
                            end = Offset(
                                cx + cos(angle) * w * 0.48f,
                                cy + sin(angle) * h * 0.48f
                            ),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            if (shapeMode == ArtworkShapeMode.VINYL && monogramText.isBlank()) {
                val rMax = minOf(w, h) / 2f
                for (g in 1..5) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.28f),
                        radius = rMax * (0.45f + g * 0.09f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                drawCircle(
                    color = Color(0xFF14121D),
                    radius = rMax * 0.12f
                )
                drawCircle(
                    color = primaryColor,
                    radius = rMax * 0.12f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        if (coverArtUrl.isNotBlank()) {
            // When the cover changes (next song), the previous cover stays up as the placeholder and
            // cross-fades into the new one; the tonal tile is only for the very first load or a
            // failure. Without this the tile flashed for a frame on every track change.
            var shownKey by remember { mutableStateOf<String?>(null) }
            val key = remember(coverArtUrl) { artworkCacheKey(coverArtUrl) }
            if (shownKey == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (monogramText.isNotBlank()) {
                        Text(
                            text = remember(monogramText) { computeArtistMonogram(monogramText) },
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxSize(0.34f)
                        )
                    }
                }
            }
            val context = LocalContext.current
            val request = remember(coverArtUrl) {
                ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .placeholderMemoryCacheKey(shownKey)
                    .crossfade(220)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = if (monogramText.isNotBlank()) "$monogramText Profile Photo" else "Album Cover Art",
                contentScale = ContentScale.Crop,
                onSuccess = { shownKey = key },
                onError = { shownKey = null },
                modifier = Modifier.fillMaxSize()
            )
        } else if (monogramText.isNotBlank()) {
            val initials = remember(monogramText) { computeArtistMonogram(monogramText) }
            Box(
                modifier = Modifier
                    .fillMaxSize(0.68f)
                    .clip(CircleShape)
                    .background(Color(0x99100E1C))
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

private fun DrawScope.drawBars(color: Color, heights: List<Float>) {
    val w = 3.dp.toPx()
    val gap = 2.5.dp.toPx()
    heights.forEachIndexed { i, frac ->
        val h = size.height * frac
        drawRoundRect(
            color = color,
            topLeft = Offset(i * (w + gap), size.height - h),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2f, w / 2f)
        )
    }
}

@Composable
fun AnimatedAudioBarsIndicator(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    if (!isPlaying) {
        Canvas(modifier.size(20.dp)) { drawBars(color, listOf(0.3f, 0.5f, 0.35f, 0.25f)) }
        return
    }
    val transition = rememberInfiniteTransition(label = "eqBars")
    val bars = listOf(Triple(0.25f, 0.95f, 420), Triple(0.85f, 0.2f, 340), Triple(0.35f, 1.0f, 510), Triple(0.75f, 0.3f, 390))
        .mapIndexed { i, (from, to, ms) ->
            transition.animateFloat(from, to, infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b$i")
        }
    // Heights are read in the draw phase only, so the bars animate without recomposing or relayout.
    Canvas(modifier.size(20.dp)) { drawBars(color, bars.map { it.value }) }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

fun formatDurationSec(sec: Int): String {
    val clean = sec.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", clean / 60, clean % 60)
}

/**
 * Pull-to-sync feedback in the Material 3 Expressive style: a contained loading indicator drops
 * in under the top bar, its shape filling with the pull, then morphing continuously while syncing.
 */
@Composable
fun PullSyncIndicator(
    pullProgress: Float,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    val shown by animateFloatAsState(
        targetValue = if (isSyncing) 1f else pullProgress.coerceIn(0f, 1f),
        animationSpec = if (isSyncing || pullProgress == 0f) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "pullSync"
    )
    if (shown <= 0.01f) return
    Box(
        modifier = modifier.graphicsLayer {
            translationY = (shown - 1f) * 56.dp.toPx()
            alpha = shown
            scaleX = 0.7f + 0.3f * shown
            scaleY = 0.7f + 0.3f * shown
        }
    ) {
        if (isSyncing) {
            ContainedLoadingIndicator(modifier = Modifier.size(48.dp).testTag("pull_sync_indicator"))
        } else {
            ContainedLoadingIndicator(progress = { pullProgress }, modifier = Modifier.size(48.dp))
        }
    }
}

/**
 * M3 Expressive transport: a real [ButtonGroup] so the pressed button widens and its neighbours
 * yield. Play/pause is the hero — it morphs pill ↔ squircle with the playback state and swaps
 * to a [LoadingIndicator] while buffering. Shuffle/repeat are toggles whose corners square off
 * when checked.
 */
@Composable
fun ExpressiveTransportGroup(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatOneEnabled: Boolean,
    shuffleMode: ShuffleMode,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeatOne: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedbackManager.current ?: rememberHapticFeedbackManager()
    val sideHeight = 64.dp
    val shuffleSource = remember { MutableInteractionSource() }
    val prevSource = remember { MutableInteractionSource() }
    val playSource = remember { MutableInteractionSource() }
    val nextSource = remember { MutableInteractionSource() }
    val repeatSource = remember { MutableInteractionSource() }

    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 44.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playCorner"
    )
    val glyphSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier
            .fillMaxWidth()
            .testTag("wobble_transport_box"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconToggleButton(
                    checked = shuffleEnabled,
                    onCheckedChange = {
                        haptics.performToggleOption(!shuffleEnabled)
                        onToggleShuffle()
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    interactionSource = shuffleSource,
                    modifier = Modifier
                        .weight(1f)
                        .height(sideHeight)
                        .animateWidth(shuffleSource)
                        .testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = if (shuffleMode == ShuffleMode.SMART) Icons.Filled.AutoAwesome else Icons.Filled.Shuffle,
                        contentDescription = "Cycle Shuffle Mode (${shuffleMode.label})"
                    )
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performTrackSkip(isNext = false)
                        onSkipPrevious()
                    },
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = prevSource,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(sideHeight)
                        .animateWidth(prevSource)
                        .testTag("skip_previous_button")
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Track", modifier = Modifier.size(30.dp))
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                FilledIconButton(
                    onClick = {
                        haptics.performPlayPauseTransition(isStartingPlay = !isPlaying)
                        onTogglePlay()
                    },
                    shape = RoundedCornerShape(playCorner),
                    interactionSource = playSource,
                    modifier = Modifier
                        .weight(2f)
                        .height(88.dp)
                        .animateWidth(playSource)
                        .testTag("now_playing_play_button")
                ) {
                    AnimatedContent(
                        targetState = when {
                            isBuffering -> 0
                            isPlaying -> 1
                            else -> 2
                        },
                        transitionSpec = {
                            (scaleIn(glyphSpec) + fadeIn())
                                .togetherWith(scaleOut() + fadeOut())
                        },
                        label = "playGlyph"
                    ) { state ->
                        when (state) {
                            0 -> LoadingIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                            1 -> Icon(Icons.Filled.Pause, contentDescription = "Pause", modifier = Modifier.size(40.dp))
                            else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp))
                        }
                    }
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performTrackSkip(isNext = true)
                        onSkipNext()
                    },
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = nextSource,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(sideHeight)
                        .animateWidth(nextSource)
                        .testTag("skip_next_button")
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next Track", modifier = Modifier.size(30.dp))
                }
            },
            menuContent = {}
        )
        customItem(
            buttonGroupContent = {
                FilledTonalIconToggleButton(
                    checked = repeatOneEnabled,
                    onCheckedChange = {
                        haptics.performToggleOption(!repeatOneEnabled)
                        onToggleRepeatOne()
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    interactionSource = repeatSource,
                    modifier = Modifier
                        .weight(1f)
                        .height(sideHeight)
                        .animateWidth(repeatSource)
                        .testTag("repeat_button")
                ) {
                    Icon(Icons.Filled.RepeatOne, contentDescription = "Toggle Repeat One")
                }
            },
            menuContent = {}
        )
    }
}

/** Clips to a [Morph] between two normalized MaterialShapes polygons at [progress]. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = android.graphics.Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        matrix.setScale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}
