package io.github.roggzuay.ondalune.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.theme.ArtworkPalette
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The Now Playing backdrop. Every style is painted from the cover's own colours, taken from the
 * region of the cover they sit in (top-left colour drifts around the top-left of the screen and
 * so on), so the background reads as the artwork spilling out rather than a generic tint.
 *
 * Blurred cover (the default) is the cover itself, zoomed and blurred behind the whole player.
 *
 * Motion runs on a clock that only ticks while music plays (scaled by playback speed), and every
 * animated value is read in the draw phase, so the scene moves at full frame rate without
 * recomposing; pausing freezes it in place.
 */
@Composable
fun PlayerBackdrop(
    track: SubsonicTrack,
    palette: ArtworkPalette,
    style: PlayerBackgroundAnimationStyle,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
    /** Bass, middle and top of what is playing, each 0..1. Only read by the Reactive style. */
    readLevels: (FloatArray) -> Unit = {},
    /** Told while the Reactive style is on screen, so nothing is measured when nobody is looking. */
    onWantLevels: (Boolean) -> Unit = {}
) {
    val reducedMotion = LocalReducedMotion.current
    val clock = remember { mutableFloatStateOf(0f) }
    val coverBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Reactive is the blurred cover again, but it moves: it needs the frame loop even where the
    // other blurred-cover style holds still.
    val reactive = style == PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE && !reducedMotion
    // The blurred cover holds still, like the picture above it.
    val animate = isPlaying && !reducedMotion &&
        style != PlayerBackgroundAnimationStyle.MINIMAL_TONAL && (!coverBlur || reactive)

    // The three bands, smoothed once more for the eye. Written in the frame loop and read in the
    // draw and layer phases, so the picture moves without a single recomposition.
    val low = remember { mutableFloatStateOf(0f) }
    val mid = remember { mutableFloatStateOf(0f) }
    val high = remember { mutableFloatStateOf(0f) }
    val levels = remember { FloatArray(3) }

    DisposableEffect(reactive) {
        onWantLevels(reactive)
        onDispose { onWantLevels(false) }
    }

    LaunchedEffect(animate, playbackSpeed, reactive) {
        if (!animate) {
            // Coming to rest: the bands fall away over about a third of a second rather than
            // snapping flat, which would read as the picture being switched off.
            while (low.floatValue > 0.001f || mid.floatValue > 0.001f || high.floatValue > 0.001f) {
                withFrameNanos {
                    low.floatValue *= RestingFall
                    mid.floatValue *= RestingFall
                    high.floatValue *= RestingFall
                }
            }
            low.floatValue = 0f
            mid.floatValue = 0f
            high.floatValue = 0f
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = (now - last) / 1_000_000_000f
                // Drift carries on with the music; the reactive scene speeds up a little with it.
                val pace = if (reactive) 1f + mid.floatValue * 0.6f else 1f
                clock.floatValue += dt * playbackSpeed.coerceIn(0.5f, 2f) * pace
                last = now
                if (reactive) {
                    readLevels(levels)
                    low.floatValue += (levels[0] - low.floatValue) * EyeSmoothing
                    mid.floatValue += (levels[1] - mid.floatValue) * EyeSmoothing
                    high.floatValue += (levels[2] - high.floatValue) * EyeSmoothing
                }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val base = scheme.surface
    val dark = base.luminance() < 0.5f
    val source = palette.regions.ifEmpty { palette.swatches }
    fun pick(i: Int) = tune(source.getOrElse(i) { source.getOrElse(i % source.size.coerceAtLeast(1)) { palette.seed } }, dark)
    val c0 by animateColorAsState(pick(0), tween(1600), label = "bd0")
    val c1 by animateColorAsState(pick(1), tween(1600), label = "bd1")
    val c2 by animateColorAsState(pick(2), tween(1600), label = "bd2")
    val c3 by animateColorAsState(pick(3), tween(1600), label = "bd3")
    val c4 by animateColorAsState(pick(4), tween(1600), label = "bd4")
    val colors = Colors(c0, c1, c2, c3, c4)
    val grain = remember { grainTexture() }

    BoxWithConstraints(modifier.fillMaxSize().background(base), contentAlignment = Alignment.Center) {
        // The cover itself, zoomed past the screen's edges and blurred: the picture at the top of
        // the player carries on, out of focus, behind everything below it.
        val fill = max(maxWidth / CoverBox, maxHeight / CoverBox) * 1.08f
        // Reactive breathes the same picture: the cover swells on the bass and drifts on the mids,
        // read in the layer phase so it costs no recomposition.
        if (coverBlur) {
            if (reactive) BlurredCover(track, fill) { 1f + low.floatValue * ReactiveSwell to mid.floatValue }
            else BlurredCover(track, fill)
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.floatValue
            when (style) {
                // Before Android 12 there is no blur: the cover's colours stand in for it.
                PlayerBackgroundAnimationStyle.ARTWORK_GLOW -> if (!coverBlur) mesh(colors, t, alpha = 0.55f, amplitude = 0.16f)
                // The cover's own colours, lifted off it by the top of the music. On a phone with
                // no blur the mesh is the whole scene, so it carries the bass as well.
                PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE -> {
                    // The cover's own colours lifting off it. Wide enough to be seen: a scene
                    // that only moves a couple of per cent is a scene nobody notices.
                    mesh(
                        colors,
                        t,
                        alpha = (if (coverBlur) 0.20f else 0.50f) + high.floatValue * 0.45f,
                        amplitude = (if (coverBlur) 0.12f else 0.18f) + low.floatValue * 0.34f
                    )
                    sheen(t, colors.c4.copy(alpha = 0.3f + mid.floatValue * 0.7f))
                }
                PlayerBackgroundAnimationStyle.APPLE_GLITTER_GRAIN -> {
                    mesh(colors, t, alpha = 0.9f, amplitude = 0.22f)
                    sheen(t, colors.c4)
                    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.10f)
                }
                PlayerBackgroundAnimationStyle.LIQUID_AURORA -> aurora(colors, t)
                PlayerBackgroundAnimationStyle.VINYL_HALO_PULSE -> halo(colors, t, base)
                PlayerBackgroundAnimationStyle.MINIMAL_TONAL -> drawRect(
                    Brush.linearGradient(listOf(colors.c0.copy(alpha = 0.55f), colors.c3.copy(alpha = 0.35f), base))
                )
            }
            // Legibility: the cover lies over the top; from the title down the colour settles.
            // The blurred cover keeps more of itself, so it reads as the picture, not a tint.
            drawRect(
                if (coverBlur) Brush.verticalGradient(
                    0f to base.copy(alpha = 0.08f),
                    0.45f to base.copy(alpha = if (dark) 0.3f else 0.4f),
                    1f to base.copy(alpha = if (dark) 0.5f else 0.6f)
                ) else Brush.verticalGradient(
                    0f to base.copy(alpha = 0.30f),
                    0.45f to base.copy(alpha = 0.56f),
                    1f to base.copy(alpha = 0.78f)
                )
            )
        }
    }
}

private val CoverBox = 200.dp

/** How much of what is left falls away each frame once the music stops. */
private const val RestingFall = 0.90f

/** How much of a new level reading is taken each frame, so the picture glides instead of jumping. */
private const val EyeSmoothing = 0.18f

/** The blurred cover grows by at most this much on the bass. */
private const val ReactiveSwell = 0.16f

/** The furthest the blurred cover drifts, in density-independent pixels. */
private const val ReactiveDriftPx = 44f

private class Colors(val c0: Color, val c1: Color, val c2: Color, val c3: Color, val c4: Color)

/** Keep the cover's hue but pull its lightness into a range that glows on the page. */
private fun tune(color: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = if (dark) hsl[2].coerceIn(0.24f, 0.62f) else hsl[2].coerceIn(0.55f, 0.85f)
    hsl[1] = (hsl[1] * 1.12f).coerceAtMost(1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * The player's backdrop for a page: [coverArtUrl] zoomed past the edges, blurred, and melting into
 * the page's background at the bottom. Nothing before Android 12, which has no blur.
 */
@Composable
fun BlurredCoverHeader(
    seedHex: String,
    accentHex: String,
    artStyleIndex: Int,
    coverArtUrl: String,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || coverArtUrl.isBlank()) return
    val page = MaterialTheme.colorScheme.background
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val fill = max(maxWidth / CoverBox, maxHeight / CoverBox) * 1.08f
        ExpressiveArtwork(
            seedHex = seedHex,
            accentHex = accentHex,
            artStyleIndex = artStyleIndex,
            coverArtUrl = coverArtUrl,
            fullBleed = true,
            modifier = Modifier
                .requiredSize(CoverBox)
                .graphicsLayer {
                    scaleX = fill
                    scaleY = fill
                }
                .blur(14.dp, BlurredEdgeTreatment.Rectangle)
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        // Comes out of the page above without an edge.
                        0f to page,
                        0.14f to page.copy(alpha = 0.4f),
                        0.55f to page.copy(alpha = 0.55f),
                        1f to page
                    )
                )
        )
    }
}

@Composable
private fun BlurredCover(
    track: SubsonicTrack,
    fill: Float,
    /** Reactive only: extra swell and the drift, both read in the layer phase, per frame. */
    motion: (() -> Pair<Float, Float>)? = null
) {
    // Blurring a small box and scaling the cached result is far cheaper than blurring the screen.
    // The scale wraps the crossfade so the box edge never shows on track change.
    Box(
        Modifier
            .requiredSize(CoverBox)
            .graphicsLayer {
                if (motion == null) {
                    scaleX = fill
                    scaleY = fill
                } else {
                    val (swell, drift) = motion()
                    scaleX = fill * swell
                    scaleY = fill * swell
                    // A hand's width of drift at the loudest, and never more than that.
                    translationX = drift * ReactiveDriftPx * density
                    translationY = drift * ReactiveDriftPx * 0.6f * density
                }
            }
    ) {
        AnimatedContent(
            targetState = track,
            contentKey = { it.coverArtUrl.ifBlank { it.id } },
            transitionSpec = { (fadeIn(tween(900)) togetherWith fadeOut(tween(900))).using(SizeTransform(clip = false)) },
            label = "glowCover"
        ) { shown ->
            ExpressiveArtwork(
                seedHex = shown.paletteSeedHex,
                accentHex = shown.accentHex,
                artStyleIndex = shown.artStyleIndex,
                coverArtUrl = shown.coverArtUrl,
                fullBleed = true,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(14.dp, BlurredEdgeTreatment.Rectangle)
            )
        }
    }
}

private fun DrawScope.blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    val center = Offset(cx * size.width, cy * size.height)
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.55f to color.copy(alpha = alpha * 0.45f),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/**
 * Mesh gradient: five large blobs, each starting where its colour sits on the cover and wandering
 * on its own Lissajous path (periods of roughly 15–30 s) so the field keeps re-mixing.
 */
private fun DrawScope.mesh(c: Colors, t: Float, alpha: Float, amplitude: Float) {
    val r = size.maxDimension * 0.62f
    val a = amplitude
    blob(c.c0, 0.18f + a * sin(t * 0.33f), 0.16f + a * 0.8f * cos(t * 0.27f), r, alpha)
    blob(c.c1, 0.84f + a * cos(t * 0.29f + 1f), 0.20f + a * 0.9f * sin(t * 0.37f), r, alpha)
    blob(c.c2, 0.16f + a * cos(t * 0.24f + 2f), 0.78f + a * 0.7f * sin(t * 0.31f + 1f), r, alpha)
    blob(c.c3, 0.86f + a * sin(t * 0.21f + 3f), 0.82f + a * 0.8f * cos(t * 0.35f), r, alpha)
    blob(c.c4, 0.50f + a * 1.2f * sin(t * 0.19f + 4f), 0.46f + a * cos(t * 0.23f + 2f), r * 0.8f, alpha * 0.8f)
}

/** A soft diagonal band of light that sweeps across every ~12 s, like light on velvet. */
private fun DrawScope.sheen(t: Float, tint: Color) {
    val travel = ((t * 0.085f) % 1f) * 2.4f - 0.7f
    val x = travel * size.width
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.07f).compositeOver(tint.copy(alpha = 0.06f)),
            1f to Color.Transparent,
            start = Offset(x - size.width * 0.35f, 0f),
            end = Offset(x + size.width * 0.35f, size.height)
        )
    )
}

private fun Color.compositeOver(under: Color): Color = Color(
    red = red * alpha + under.red * (1 - alpha),
    green = green * alpha + under.green * (1 - alpha),
    blue = blue * alpha + under.blue * (1 - alpha),
    alpha = alpha + under.alpha * (1 - alpha)
)

/** Aurora: three translucent curtains of cover colour rippling across the upper screen. */
private fun DrawScope.aurora(c: Colors, t: Float) {
    mesh(c, t * 0.6f, alpha = 0.35f, amplitude = 0.1f)
    val w = size.width
    val h = size.height
    listOf(c.c0, c.c4, c.c1).forEachIndexed { i, color ->
        val baseY = h * (0.16f + i * 0.17f)
        val thick = h * (0.20f - i * 0.03f)
        val path = Path()
        val steps = 28
        for (s in 0..steps) {
            val x = s / steps.toFloat() * w
            val y = baseY + sin(s * 0.42f + t * (0.55f + i * 0.17f) + i * 1.7f) * h * 0.05f +
                sin(s * 0.17f - t * 0.31f) * h * 0.03f
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        for (s in steps downTo 0) {
            val x = s / steps.toFloat() * w
            val y = baseY + thick + sin(s * 0.36f + t * (0.48f + i * 0.13f) + i) * h * 0.06f
            path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path,
            Brush.verticalGradient(
                0f to color.copy(alpha = 0f),
                0.35f to color.copy(alpha = 0.55f),
                1f to color.copy(alpha = 0f),
                startY = baseY - h * 0.05f,
                endY = baseY + thick + h * 0.08f
            )
        )
    }
}

/** Halo: a slowly turning ring of every cover colour behind the artwork, with rings pulsing out. */
private fun DrawScope.halo(c: Colors, t: Float, base: Color) {
    val center = Offset(size.width / 2f, size.height * 0.34f)
    val r = size.minDimension * 0.95f
    rotate(degrees = t * 14f, pivot = center) {
        drawCircle(
            Brush.sweepGradient(listOf(c.c0, c.c1, c.c3, c.c2, c.c4, c.c0), center = center),
            radius = r,
            center = center,
            alpha = 0.55f
        )
    }
    // Fade the ring's outer edge into the page.
    drawCircle(
        Brush.radialGradient(
            0f to base.copy(alpha = 0.25f),
            0.45f to Color.Transparent,
            1f to base,
            center = center,
            radius = r
        ),
        radius = r,
        center = center
    )
    for (i in 0 until 4) {
        val phase = ((t * 0.28f) + i / 4f) % 1f
        drawCircle(
            color = listOf(c.c0, c.c1, c.c2, c.c3)[i].copy(alpha = (1f - phase) * 0.35f),
            radius = size.minDimension * (0.28f + phase * 0.7f),
            center = center,
            style = Stroke(width = (2f + 6f * (1f - phase)) * density)
        )
    }
}

/** 96×96 tile of monochrome noise; drawn tiled at low alpha it reads as film grain. */
private fun grainTexture(): ImageBitmap {
    val n = 96
    val random = java.util.Random(7)
    val pixels = IntArray(n * n) {
        val v = random.nextInt(256)
        android.graphics.Color.argb(random.nextInt(140), v, v, v)
    }
    return android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * A still of what a backdrop does, for the picker in Appearance.
 *
 * It is drawn by the same code that draws the real thing, at a fixed moment, in the playing
 * song's own colours — so a tile shows the scene you would actually get rather than an artist's
 * impression of it. The two styles built on the blurred cover stand in with the cover's colours,
 * since a tile this size cannot show a blurred photograph meaningfully.
 */
@Composable
fun BackdropPreview(
    style: PlayerBackgroundAnimationStyle,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surface
    val dark = base.luminance() < 0.5f
    val source = palette.regions.ifEmpty { palette.swatches }
    fun pick(i: Int) = tune(source.getOrElse(i) { source.getOrElse(i % source.size.coerceAtLeast(1)) { palette.seed } }, dark)
    val colors = Colors(pick(0), pick(1), pick(2), pick(3), pick(4))
    val grain = remember { grainTexture() }

    Canvas(modifier.background(base)) {
        // A moment part-way through, so every scene is caught mid-movement rather than at rest.
        val t = PreviewMoment
        when (style) {
            PlayerBackgroundAnimationStyle.ARTWORK_GLOW ->
                mesh(colors, t, alpha = 0.85f, amplitude = 0.16f)
            PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE -> {
                mesh(colors, t, alpha = 0.85f, amplitude = 0.26f)
                sheen(t, colors.c4)
            }
            PlayerBackgroundAnimationStyle.APPLE_GLITTER_GRAIN -> {
                mesh(colors, t, alpha = 0.9f, amplitude = 0.22f)
                sheen(t, colors.c4)
                drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.12f)
            }
            PlayerBackgroundAnimationStyle.LIQUID_AURORA -> aurora(colors, t)
            PlayerBackgroundAnimationStyle.VINYL_HALO_PULSE -> halo(colors, t, base)
            PlayerBackgroundAnimationStyle.MINIMAL_TONAL -> drawRect(
                Brush.linearGradient(listOf(colors.c0.copy(alpha = 0.75f), colors.c3.copy(alpha = 0.5f), base))
            )
        }
    }
}

/** Far enough into the scene's clock that nothing is caught at its starting position. */
private const val PreviewMoment = 2.4f
