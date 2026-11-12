package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * How far the player is open, from 0 (the mini player) to 1 (full screen). Taps, Back and a finger
 * on either player all move this one value, so the full player grows out of the mini player and
 * the cover travels between the mini player's thumbnail and the middle of the screen.
 *
 * Read [fraction] and the values derived from it only in draw or layer blocks: it changes every
 * frame of a drag or settle.
 */
@Stable
class PlayerSheetState {
    var fraction by mutableFloatStateOf(0f)
        private set

    /** The mini player and its cover, in root coordinates; [Rect.Zero] while it isn't shown. */
    var miniCard by mutableStateOf(Rect.Zero)
    var miniArt by mutableStateOf(Rect.Zero)

    /**
     * The full player's cover frame, kept between openings so the cover can start from the
     * thumbnail on the first frame, and whether the player is showing its cover (not lyrics).
     */
    var fullArt by mutableStateOf(Rect.Zero)
    var coverInPlayer by mutableStateOf(false)

    /** The thumbnail hides once the player's own cover stands in its place. */
    val thumbnailHidden get() = fraction > 0f && coverInPlayer && fullArt != Rect.Zero && !fromHero

    /** Opened from the Your Mix hero: the player fades up out of its zoom rather than the mini player. */
    var fromHero by mutableStateOf(false)

    private var screen = Rect.Zero
    private var target = 0f
    private var settle: Job? = null

    /** How far the top edge moves between the two states, so it stays under the finger. */
    private val travel get() = (if (miniCard == Rect.Zero) screen.height else miniCard.top - screen.top).coerceAtLeast(1f)

    val backdropAlpha get() = if (fromHero) 1f else (fraction / 0.2f).coerceIn(0f, 1f)
    val contentAlpha get() = if (fromHero) 1f else ((fraction - 0.4f) / 0.6f).coerceIn(0f, 1f)

    fun dragBy(deltaY: Float) {
        settle?.cancel()
        fraction = (fraction - deltaY / travel).coerceIn(0f, 1f)
    }

    /**
     * Ends a drag: a fling decides, otherwise how far it got. Leaving the open player takes a
     * fifth of the way down; opening takes a fifth of the way up. Returns whether it ends open.
     */
    fun release(scope: CoroutineScope, velocityY: Float, startedOpen: Boolean): Boolean {
        val speed = -velocityY / travel
        val open = when {
            speed > 1.5f -> true
            speed < -1.5f -> false
            else -> fraction > if (startedOpen) 0.8f else 0.2f
        }
        settleTo(open, scope, velocityY)
        return open
    }

    fun settleTo(open: Boolean, scope: CoroutineScope, velocityY: Float = 0f) {
        val goal = if (open) 1f else 0f
        if (goal == target && settle?.isActive == true) return
        target = goal
        settle?.cancel()
        settle = scope.launch {
            animate(
                initialValue = fraction,
                targetValue = goal,
                initialVelocity = -velocityY / travel,
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
            ) { value, _ -> fraction = value }
            // Fully open, it closes into the mini player like any other way of opening it.
            if (open) fromHero = false
        }
    }

    /** The full player's frame: it grows from the mini player's card to the whole screen. */
    fun containerModifier(): Modifier = Modifier
        .onGloballyPositioned { screen = it.rootRect() }
        .graphicsLayer {
            val f = fraction
            if (fromHero) {
                alpha = f
                scaleX = lerp(0.9f, 1f, f)
                scaleY = scaleX
            } else if (f < 1f) {
                val from = if (miniCard == Rect.Zero) Rect(0f, size.height * 0.86f, size.width, size.height)
                else miniCard.translate(-screen.topLeft)
                clip = true
                shape = RoundedFrame(lerp(from, Rect(Offset.Zero, size), f), (1f - f) * 22.dp.toPx())
            }
        }

    /**
     * Places the full player's cover over the mini player's thumbnail and moves it into place as
     * the player opens. The cover's layer is a square centred on [fullArt] (as tall as the hero,
     * wider than it by the sides that are cropped away), so it scales evenly: at 0 the whole
     * square is the thumbnail, and on the way its clip opens from that square to the hero's
     * frame, [heroWidth] wide.
     */
    fun artTransform(heroWidth: Float): Modifier = Modifier.graphicsLayer {
        val f = fraction
        val full = fullArt
        // The square layer overhangs the hero's frame at the sides: in a window wider than it is
        // tall that would be the controls' pane, so the frame is always the clip at rest.
        val frame = Rect(Offset((size.width - heroWidth) / 2f, 0f), Size(heroWidth, size.height))
        clip = true
        shape = RoundedFrame(frame, 0f)
        if (f >= 1f || fromHero || full.isEmpty) return@graphicsLayer
        if (miniArt == Rect.Zero) {
            alpha = contentAlpha
            return@graphicsLayer
        }
        val scale = lerp(miniArt.width / size.width, 1f, f)
        scaleX = scale
        scaleY = scale
        translationX = (miniArt.center.x - full.center.x) * (1f - f)
        translationY = (miniArt.center.y - full.center.y) * (1f - f)
        shape = RoundedFrame(lerp(Rect(Offset.Zero, size), frame, f), 0f)
    }
}

fun LayoutCoordinates.rootRect() = Rect(positionInRoot(), size.toSize())

private data class RoundedFrame(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}
