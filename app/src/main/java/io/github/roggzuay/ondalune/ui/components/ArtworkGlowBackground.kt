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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
    /** Bass, middle and top of what is playing, each 0..1. Only the two live scenes read it. */
    readLevels: (FloatArray) -> Unit = {},
    /** Told while a scene that listens is on screen, so nothing is measured for nobody. */
    onWantLevels: (Boolean) -> Unit = {}
) {
    val reducedMotion = LocalReducedMotion.current
    val clock = remember { mutableFloatStateOf(0f) }
    val coverBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Two scenes listen to the music; the rest move on their own clock. Blurred cover does not
    // move at all, which is the point of it.
    val listens = !reducedMotion && (
        style == PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE ||
            style == PlayerBackgroundAnimationStyle.RIPPLE ||
            style == PlayerBackgroundAnimationStyle.COMIC
        )
    val moves = style != PlayerBackgroundAnimationStyle.ARTWORK_GLOW
    val animate = isPlaying && !reducedMotion && (moves || !coverBlur)

    // The three bands, smoothed once more for the eye. Written in the frame loop and read in the
    // draw and layer phases, so the picture moves without a single recomposition.
    val low = remember { mutableFloatStateOf(0f) }
    val mid = remember { mutableFloatStateOf(0f) }
    val high = remember { mutableFloatStateOf(0f) }
    val levels = remember { FloatArray(3) }

    /**
     * How far the comic field has travelled. It is accumulated rather than worked out from the
     * clock, because the kick speeds it up: multiplying the clock would jump the whole field
     * forward the instant the bass arrived instead of surging it along.
     */
    val comicTravel = remember { mutableFloatStateOf(0f) }

    /** Ripples in flight: x, y, the moment each started, and how hard it was hit. */
    val ripples = remember { FloatArray(RippleSlots * 4) }
    val nextRipple = remember { intArrayOf(0) }
    val lastLow = remember { floatArrayOf(0f) }
    val lastSpawn = remember { floatArrayOf(-9f) }
    val wallSeconds = remember { floatArrayOf(0f) }
    val spawnRandom = remember { kotlin.random.Random(7) }

    DisposableEffect(listens) {
        onWantLevels(listens)
        onDispose { onWantLevels(false) }
    }

    LaunchedEffect(animate, playbackSpeed, listens) {
        if (!animate) {
            // Coming to rest over about a third of a second rather than snapping flat, which
            // would read as the picture being switched off. Timed, not counted in frames: the
            // same fade on a 60 Hz screen and a 120 Hz one.
            var last = withFrameNanos { it }
            while (low.floatValue > 0.004f || mid.floatValue > 0.004f || high.floatValue > 0.004f) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000.0).coerceIn(0.0, 0.1)
                    last = now
                    val keep = kotlin.math.exp(-dt / RestingSeconds).toFloat()
                    low.floatValue *= keep
                    mid.floatValue *= keep
                    high.floatValue *= keep
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
                val pace = if (listens) 1f + mid.floatValue * 0.6f else 1f
                clock.floatValue += dt * playbackSpeed.coerceIn(0.5f, 2f) * pace
                last = now
                if (listens) {
                    readLevels(levels)
                    low.floatValue += (levels[0] - low.floatValue) * EyeSmoothing
                    mid.floatValue += (levels[1] - mid.floatValue) * EyeSmoothing
                    high.floatValue += (levels[2] - high.floatValue) * EyeSmoothing
                } else {
                    // Switching to a scene that does not listen used to leave the bands frozen at
                    // whatever they held at that instant, so the glass swayed by a stuck amount.
                    val keep = kotlin.math.exp(-dt / RestingSeconds).toFloat()
                    low.floatValue *= keep
                    mid.floatValue *= keep
                    high.floatValue *= keep
                }
                val bass = low.floatValue
                // The comic field runs at a steady pace and surges on the kick.
                // Wrapped rather than left to grow: multiplied by the step over a long session
                // it reached a size where float resolution swallowed the per-frame increment.
                comicTravel.floatValue = (comicTravel.floatValue +
                    dt * (ComicDriftSteps + bass * ComicKickSteps)) % 1024f
                // A kick is the bass climbing quickly, not merely being loud: a sustained bass
                // note would otherwise spawn a ripple on every frame of itself.
                val rise = bass - lastLow[0]
                lastLow[0] = bass
                // Wall time, not the music clock: at double speed the music clock would let a
                // kick through twice as often as a listener could tell them apart.
                wallSeconds[0] += dt
                if (bass > 0.38f && rise > 0.055f && wallSeconds[0] - lastSpawn[0] > 0.11f) {
                    lastSpawn[0] = wallSeconds[0]
                    val slot = nextRipple[0] % RippleSlots
                    nextRipple[0] = slot + 1
                    ripples[slot * 4] = 0.12f + spawnRandom.nextFloat() * 0.76f
                    ripples[slot * 4 + 1] = 0.10f + spawnRandom.nextFloat() * 0.62f
                    ripples[slot * 4 + 2] = clock.floatValue
                    ripples[slot * 4 + 3] = bass
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
    // The drawn scenes get colours that stand clear of the page rather than glow under a photo.
    fun vividPick(i: Int) = vivid(source.getOrElse(i) { source.getOrElse(i % source.size.coerceAtLeast(1)) { palette.seed } }, dark)
    val v0 by animateColorAsState(vividPick(0), tween(1600), label = "bv0")
    val v1 by animateColorAsState(vividPick(1), tween(1600), label = "bv1")
    val v2 by animateColorAsState(vividPick(2), tween(1600), label = "bv2")
    val v3 by animateColorAsState(vividPick(3), tween(1600), label = "bv3")
    val v4 by animateColorAsState(vividPick(4), tween(1600), label = "bv4")
    val drawn = Colors(v0, v1, v2, v3, v4)

    BoxWithConstraints(modifier.fillMaxSize().background(base), contentAlignment = Alignment.Center) {
        // The cover itself, zoomed past the screen's edges and blurred: the picture at the top of
        // the player carries on, out of focus, behind everything below it.
        val fill = max(maxWidth / CoverBox, maxHeight / CoverBox) * 1.08f
        when (style) {
            // Comic paints its own ground edge to edge, so blurring a cover under it is work
            // nobody sees.
            PlayerBackgroundAnimationStyle.COMIC -> Unit
            // Mesh turns the cover, so it has to be wide enough that a corner never swings in.
            PlayerBackgroundAnimationStyle.MESH_SPIN -> if (coverBlur) {
                BlurredCover(track, fill * 1.42f) {
                    CoverMotion(scale = 1f, drift = 0f, rotation = clock.floatValue * MeshSpinPerSecond)
                }
            }
            // Night light breathes the same picture: it swells on the bass and drifts on the
            // mids, read in the layer phase so it costs no recomposition.
            PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE -> if (coverBlur) {
                BlurredCover(track, fill) {
                    CoverMotion(1f + low.floatValue * ReactiveSwell, mid.floatValue, 0f)
                }
            }
            else -> if (coverBlur) BlurredCover(track, fill)
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.floatValue
            when (style) {
                // Before Android 12 there is no blur: the cover's colours stand in for it.
                PlayerBackgroundAnimationStyle.ARTWORK_GLOW ->
                    if (!coverBlur) mesh(colors, t, alpha = 0.55f, amplitude = 0.16f)

                // A mesh of the cover's colours turning against the cover behind it. The two
                // opposite rotations are what stop it reading as a single drifting blur.
                PlayerBackgroundAnimationStyle.MESH_SPIN ->
                    texturedGlass(drawn, base, t, mid.floatValue, overCover = coverBlur)

                // Printed dots in the cover's colours, the whole field drifting steadily past as
                // though it were outside a window.
                PlayerBackgroundAnimationStyle.COMIC ->
                    comicDots(drawn, t, comicTravel.floatValue, low.floatValue)

                // Rings leaving the cover on the bass.
                PlayerBackgroundAnimationStyle.RIPPLE ->
                    ripple(drawn, base, t, low.floatValue, ripples, fullBleed = !coverBlur)

                PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE -> {
                    mesh(
                        colors,
                        t,
                        alpha = (if (coverBlur) 0.20f else 0.50f) + high.floatValue * 0.45f,
                        amplitude = (if (coverBlur) 0.12f else 0.18f) + low.floatValue * 0.34f
                    )
                    sheen(t, colors.c4.copy(alpha = 0.3f + mid.floatValue * 0.7f))
                }
            }
            // Legibility: the cover lies over the top; from the title down the colour settles.
            // A drawn scene is the picture rather than a wash under one, so it keeps far more of
            // itself --- scrimming Comic or Ripple as heavily as a photograph is what made the
            // old backdrops indistinguishable from each other.
            val drawnScene = style == PlayerBackgroundAnimationStyle.COMIC ||
                style == PlayerBackgroundAnimationStyle.RIPPLE ||
                style == PlayerBackgroundAnimationStyle.MESH_SPIN
            drawRect(
                when {
                    drawnScene -> Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to base.copy(alpha = 0.10f),
                        1f to base.copy(alpha = if (dark) 0.34f else 0.40f)
                    )
                    coverBlur -> Brush.verticalGradient(
                        0f to base.copy(alpha = 0.08f),
                        0.45f to base.copy(alpha = if (dark) 0.3f else 0.4f),
                        1f to base.copy(alpha = if (dark) 0.5f else 0.6f)
                    )
                    else -> Brush.verticalGradient(
                        0f to base.copy(alpha = 0.30f),
                        0.45f to base.copy(alpha = 0.56f),
                        1f to base.copy(alpha = 0.78f)
                    )
                }
            )
        }
    }
}

private val CoverBox = 200.dp

/** How long the bands take to fall away once the music stops. */
private const val RestingSeconds = 0.1

/** How much of a new level reading is taken each frame, so the picture glides instead of jumping. */
private const val EyeSmoothing = 0.18f

/** The blurred cover grows by at most this much on the bass. */
private const val ReactiveSwell = 0.16f

/** The furthest the blurred cover drifts, in density-independent pixels. */
private const val ReactiveDriftPx = 44f

/** Degrees a second the cover and the mesh turn, in opposite directions. */
private const val MeshSpinPerSecond = 2.6f

/** How many dot-widths the comic field travels each second at rest, and on a full kick. */
private const val ComicDriftSteps = 0.38f
private const val ComicKickSteps = 1.9f

private const val RippleRings = 5

/** How many kick-born ripples can be in the air at once, and how long each lives. */
private const val RippleSlots = 12
private const val RippleLifeSeconds = 1.9f

/** Panes across the narrow side of the glass: enough to read as a rough surface, not a window. */
private const val GlassPanes = 8.5f

private class Colors(val c0: Color, val c1: Color, val c2: Color, val c3: Color, val c4: Color)

/**
 * Keep the cover's hue but push it clear of the page, so a drawn scene reads against the surface
 * instead of dissolving into it. On a light theme that means darker; on a dark one, lighter.
 *
 * The glow tuning below is right for a wash lying under a photograph. It is wrong for a scene
 * that is itself the picture --- Comic, Ripple and Mesh were drawing pale shapes onto a pale page
 * and then having a scrim put over the top of them.
 */
private fun vivid(color: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    // A black-and-white sleeve has nothing to saturate, and squeezing its swatches into a narrow
    // band left every one of them the same grey, so the scenes drew in a single flat tone. Keep
    // the lightness spread wide when there is no colour to work with.
    val grey = hsl[1] < 0.12f
    hsl[2] = when {
        grey -> hsl[2].coerceIn(0.14f, 0.86f)
        dark -> hsl[2].coerceIn(0.44f, 0.72f)
        else -> hsl[2].coerceIn(0.26f, 0.52f)
    }
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

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

/** What a live scene does to the blurred cover, read fresh in the layer phase every frame. */
internal class CoverMotion(val scale: Float, val drift: Float, val rotation: Float)

@Composable
private fun BlurredCover(
    track: SubsonicTrack,
    fill: Float,
    /** Live scenes only: swell, drift and turn, all read in the layer phase, per frame. */
    motion: (() -> CoverMotion)? = null
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
                    val m = motion()
                    scaleX = fill * m.scale
                    scaleY = fill * m.scale
                    // A hand's width of drift at the loudest, and never more than that.
                    translationX = m.drift * ReactiveDriftPx * density
                    translationY = m.drift * ReactiveDriftPx * 0.6f * density
                    rotationZ = m.rotation
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


/**
 * Printed dots, the way a comic page is printed: one flat ground in the cover's colours, and a
 * grid of dots over it whose size and tint follow a slow field, so they read as bands of shade
 * rather than noise.
 *
 * The field travels steadily and surges on the kick. [travel] is accumulated by the caller rather
 * than taken from the clock, because speeding up a position computed from the clock would jump
 * the whole field forward the moment the bass arrived instead of pushing it along.
 */
private fun DrawScope.comicDots(c: Colors, t: Float, travel: Float, bass: Float) {
    drawRect(
        Brush.linearGradient(
            listOf(c.c0, c.c3, c.c2),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
    )
    val step = size.minDimension / 13f
    val run = travel * step
    val cols = (size.width / step).toInt() + 1
    val rows = (size.height / step).toInt() + 1
    val slideX = run * 0.45f % step
    val slideY = run % step
    for (row in -1..rows) {
        for (col in -1..cols) {
            val x = col * step + slideX
            val y = row * step + slideY
            // Two slow waves crossed: bands of larger and smaller dots, never a random field.
            val f = (sin(x / size.width * 3.1f + t * 0.30f) * 0.5f + 0.5f) *
                (cos(y / size.height * 2.3f - t * 0.18f) * 0.5f + 0.5f)
            val radius = step * (0.11f + 0.40f * f) * (1f + bass * 0.45f)
            drawCircle(
                color = lerp(c.c1, c.c4, f),
                radius = radius,
                center = Offset(x, y),
                alpha = 0.62f + 0.33f * f
            )
        }
    }
}

/**
 * Rings leaving the cover on the bass.
 *
 * One set keeps travelling out of the middle so the scene is alive in a quiet passage, and each
 * kick drops another ring somewhere else on the screen. The middle set is deliberately short —
 * it is the heartbeat under the rest, not the whole picture.
 *
 * The ring colour is chosen for contrast against the ground rather than taken from the palette as
 * it comes: a cover whose colours all sit near the surface used to draw rings nobody could see.
 */
private fun DrawScope.ripple(
    c: Colors,
    base: Color,
    t: Float,
    bass: Float,
    live: FloatArray,
    fullBleed: Boolean
) {
    val centre = Offset(size.width / 2f, size.height * 0.40f)
    // Without a blurred cover underneath there is nothing at the edges, so the wash covers all.
    if (fullBleed) drawRect(Brush.linearGradient(listOf(c.c0, c.c3, c.c1)))
    drawRect(
        Brush.radialGradient(
            listOf(c.c0.copy(alpha = 0.55f), c.c3.copy(alpha = 0.26f), Color.Transparent),
            center = centre,
            radius = size.maxDimension * 0.75f
        )
    )
    // The rings sit on that wash, not on the bare surface.
    val ink = againstGround(c, washed(base, c.c0, 0.55f))

    // The heartbeat in the middle, on a short radius.
    val middleReach = size.minDimension * 0.30f
    for (i in 0 until RippleRings) {
        val phase = ((t * (0.22f + bass * 0.30f)) + i.toFloat() / RippleRings) % 1f
        val fade = 1f - phase
        drawCircle(
            color = ink,
            radius = phase * middleReach,
            center = centre,
            // Squared fade put most of a ring's life under 20% alpha, which is why rings were
            // there but nobody could see them.
            alpha = fade * (0.42f + bass * 0.40f),
            style = Stroke(width = (2f + bass * 7f) * (0.4f + fade) * density)
        )
    }

    // One ring per kick, wherever it landed, spreading and fading out.
    val reach = size.minDimension * 0.58f
    // Walk what the caller actually gave: the picker's tile passes a single ring.
    for (slot in 0 until minOf(RippleSlots, live.size / 4)) {
        val born = live[slot * 4 + 2]
        if (born <= 0f) continue
        val age = (t - born) / RippleLifeSeconds
        if (age <= 0f || age >= 1f) continue
        val strength = live[slot * 4 + 3]
        val fade = 1f - age
        drawCircle(
            color = ink,
            radius = age * reach * (0.55f + strength * 0.65f),
            center = Offset(live[slot * 4] * size.width, live[slot * 4 + 1] * size.height),
            alpha = fade * (0.40f + strength * 0.50f),
            style = Stroke(width = (2.5f + strength * 9f) * (0.3f + fade) * density)
        )
    }
}

/**
 * The palette colour that stands furthest from what a scene has actually painted, so a drawn line
 * is visible whatever the cover happens to be.
 *
 * [ground] is the colour the lines will sit on — not the bare surface. Measuring against the
 * surface was wrong: Ripple paints its own wash over the surface first, and the brightest swatch
 * it then picked for the rings was often the very colour that wash is made of, so the rings were
 * drawn in the colour they were drawn onto.
 */
private fun againstGround(c: Colors, ground: Color): Color {
    val groundLight = ground.luminance()
    var best = c.c4
    var bestGap = -1f
    for (candidate in arrayOf(c.c0, c.c1, c.c2, c.c3, c.c4)) {
        val gap = kotlin.math.abs(candidate.luminance() - groundLight)
        if (gap > bestGap) { bestGap = gap; best = candidate }
    }
    if (bestGap > 0.22f) return best
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(best.toArgb(), hsl)
    hsl[2] = if (groundLight < 0.5f) 0.86f else 0.16f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** What a translucent wash of [over] actually leaves on [under], for measuring contrast against. */
private fun washed(under: Color, over: Color, amount: Float): Color = lerp(under, over, amount)

/**
 * Thick glass with a texture in it — the kind you cannot see through properly, where the surface
 * is broken into irregular facets that each bend the light a little differently.
 *
 * It is drawn as a jittered lattice: neighbouring panes share their corners, so there are no gaps
 * between them, and every corner drifts on its own slow wobble. That wobble is the movement —
 * turning a near-uniform blur a couple of degrees a second, which is what this was before, is a
 * change nobody can see.
 */
private fun DrawScope.texturedGlass(c: Colors, base: Color, t: Float, mids: Float, overCover: Boolean) {
    if (!overCover) {
        drawRect(Brush.linearGradient(listOf(c.c0, c.c3, c.c1)))
    }
    val cell = size.minDimension / GlassPanes
    val cols = (size.width / cell).toInt() + 3
    val rows = (size.height / cell).toInt() + 3
    val sway = cell * (0.16f + mids * 0.22f)
    // Over the cover the surface is whatever the photograph is; off it, the wash just painted.
    val edge = againstGround(c, if (overCover) base else washed(base, c.c3, 0.8f))
    // One stroke for the whole pass: a fresh Stroke per pane was a few hundred objects a frame.
    val thinEdge = Stroke(width = 1.2f * density)
    val thickEdge = Stroke(width = 2.8f * density)

    fun corner(i: Int, j: Int): Offset {
        // A stable scatter per lattice point, so the glass keeps its shape while it breathes.
        val h = fract(sin(i * 12.9898f + j * 78.233f) * 43758.547f)
        val g = fract(sin(i * 39.3468f + j * 11.135f) * 24634.633f)
        val wob = sin(t * (0.5f + h * 0.7f) + h * 6.283f) * sway
        val wobY = cos(t * (0.4f + g * 0.6f) + g * 6.283f) * sway
        return Offset(
            (i - 1) * cell + (h - 0.5f) * cell * 0.55f + wob,
            (j - 1) * cell + (g - 0.5f) * cell * 0.55f + wobY
        )
    }

    val pane = Path()
    for (j in 0 until rows) {
        for (i in 0 until cols) {
            val a = corner(i, j)
            val b = corner(i + 1, j)
            val d = corner(i + 1, j + 1)
            val e = corner(i, j + 1)
            pane.reset()
            pane.moveTo(a.x, a.y)
            pane.lineTo(b.x, b.y)
            pane.lineTo(d.x, d.y)
            pane.lineTo(e.x, e.y)
            pane.close()
            val shade = fract(sin(i * 4.1f + j * 7.7f) * 1371.37f)
            // Each pane sits at its own depth, so the surface reads as thick and uneven.
            // The fill is never the edge colour, or a pane's outline vanishes into its face.
            drawPath(
                pane,
                color = if (shade > 0.5f) c.c0 else c.c2,
                alpha = (if (overCover) 0.12f else 0.20f) + shade * 0.18f
            )
            drawPath(
                pane,
                color = edge,
                alpha = 0.18f + shade * 0.28f,
                style = if (shade > 0.5f) thickEdge else thinEdge
            )
        }
    }
}

private fun fract(v: Float): Float = v - kotlin.math.floor(v)

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
    fun vividPick(i: Int) = vivid(source.getOrElse(i) { source.getOrElse(i % source.size.coerceAtLeast(1)) { palette.seed } }, dark)
    val drawn = Colors(vividPick(0), vividPick(1), vividPick(2), vividPick(3), vividPick(4))

    Canvas(modifier.background(base)) {
        // A moment part-way through, so every scene is caught mid-movement rather than at rest.
        val t = PreviewMoment
        when (style) {
            // The plain one: a photograph out of focus, with no structure of its own. Drawing a
            // mesh here made it look like the two scenes that do have one.
            PlayerBackgroundAnimationStyle.ARTWORK_GLOW -> drawRect(
                Brush.linearGradient(
                    listOf(colors.c0, colors.c2, colors.c3),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )
            )
            // The glass itself, so the tile shows the facets rather than a wash.
            PlayerBackgroundAnimationStyle.MESH_SPIN -> {
                drawRect(Brush.linearGradient(listOf(colors.c0, colors.c3)))
                texturedGlass(drawn, base, t, mids = 0.5f, overCover = false)
            }
            PlayerBackgroundAnimationStyle.COMIC -> comicDots(drawn, t, travel = 1.4f, bass = 0.4f)
            // One off-centre ring in the tile, so it shows what the beat does.
            PlayerBackgroundAnimationStyle.RIPPLE -> ripple(
                drawn,
                base,
                t,
                bass = 0.55f,
                live = floatArrayOf(0.72f, 0.30f, t - 0.8f, 0.8f),
                fullBleed = true
            )
            // The blurred cover with the music in it: a soft ground, lit where the sound is.
            PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE -> {
                drawRect(Brush.verticalGradient(listOf(colors.c0, colors.c3)))
                mesh(colors, t, alpha = 0.62f, amplitude = 0.30f)
                sheen(t, colors.c4)
            }
        }
    }
}

/** Far enough into the scene's clock that nothing is caught at its starting position. */
private const val PreviewMoment = 2.4f
