package io.github.roggzuay.ondalune.ui.screens

import io.github.roggzuay.ondalune.ui.components.LocalHapticFeedbackManager
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.zIndex
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.LibraryMix
import io.github.roggzuay.ondalune.data.model.localEpochDay
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import io.github.roggzuay.ondalune.ui.components.MorphShape
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelves
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelfKind
import kotlin.math.sin

/** Cover art and the artist line shown on the Your Mix hero, from the listener's own shelves. */
private class MixArt(val covers: List<String>, val artists: String)

private fun mixArt(shelves: HomeShelves): MixArt {
    val seeds = shelves.favouriteTracks.take(20) + shelves.mostPlayed.take(20) + shelves.jumpBackIn.take(10)
    // One cover per album (each track has its own cover URL, so de-duplicating URLs let a single
    // album fill every shape), drawn round-robin from different shelves and rotated daily.
    val sources = listOf(
        shelves.favouriteTracks.map { it.albumId to it.coverArtUrl },
        shelves.mostPlayed.map { it.albumId to it.coverArtUrl },
        shelves.recentlyPlayed.map { it.id to it.coverArtUrl },
        shelves.jumpBackIn.map { it.albumId to it.coverArtUrl },
        shelves.recentlyAdded.map { it.id to it.coverArtUrl },
        shelves.rediscover.map { it.id to it.coverArtUrl }
    ).map { list -> list.filter { it.second.isNotBlank() }.distinctBy { it.first }.take(12) }
    val day = localEpochDay()
    val picked = LinkedHashMap<String, String>()
    for (round in 0 until 12) for (list in sources) {
        list.getOrNull(round)?.let { (album, url) -> picked.putIfAbsent(album, url) }
    }
    val covers = picked.values.take(10).shuffled(kotlin.random.Random(day)).take(5)
    val artists = seeds.groupingBy { it.artist }.eachCount().entries
        .sortedByDescending { it.value }
        .map { it.key }
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString(", ")
    return MixArt(covers, artists.ifBlank { "A smart blend of your library" })
}

/**
 * "Your Mix": a big playful hero. Library artwork floats in a cluster of Material shapes and a
 * large play button morphs as it is pressed; tapping either plays a fresh smart mix.
 *
 * Starting the mix launches into the player: the play button swells to fill the screen while the
 * artwork shapes and the title rush away from it and grow, as if the camera dived past them into
 * the button. Then [onOpenPlayer] takes over. Coming back to Home, the shapes pop back in.
 */
/**
 * The Your Mix launch, shared with the shell: the zoom's progress (0..1) and the play button's
 * centre on screen. Home's button stops at the edges of its list, so the shell draws the same
 * growing circle over its app bars too: the button swallows them as it reaches them.
 */
@Stable
class HeroLaunch {
    var progress by mutableFloatStateOf(0f)
    var centre by mutableStateOf(Offset.Unspecified)
    var maxZoom by mutableFloatStateOf(17f)
}

/** The play button's scale at launch progress [t]: still until 0.15, then swelling to [maxZoom]. */
fun heroZoom(t: Float, maxZoom: Float): Float =
    if (t <= 0.15f) 1f else 1f + ((t - 0.15f) / 0.85f).let { it * it } * (maxZoom - 1f)

/** Radius of the Your Mix play button at rest. */
val HeroButtonRadius = 64.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHero(
    shelves: HomeShelves,
    onPlay: (startAfter: Job?) -> Job?,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    /** Mirrors the launch for the shell, which draws the zoom over its bars as well. */
    heroLaunch: HeroLaunch = remember { HeroLaunch() }
) {
    val art = remember(shelves) { mixArt(shelves) }
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedbackManager.current
    val scope = rememberCoroutineScope()
    // 0 = resting, 1 = shapes gone and the button covering the screen.
    val launch = remember { Animatable(0f) }
    // Home can leave mid-zoom (its scope is cancelled with it); never leave the bars covered.
    DisposableEffect(heroLaunch) { onDispose { heroLaunch.progress = 0f } }
    // Shapes pop in one after another the first time Home appears.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow))
    }
    val start: () -> Unit = start@{
        if (launch.isRunning) return@start
        haptics?.performSubtleTick()
        if (reduced) {
            onPlay(null)
            onOpenPlayer()
            return@start
        }
        // The songs are picked in the background while the shapes fly, but playback only starts
        // once the button covers the screen, and the player opens after it. Both are heavy
        // main-thread work, so they land behind a still colour instead of stuttering the zoom.
        val covered = Job()
        val mix = onPlay(covered)
        scope.launch {
            try {
                // Follows the animation rather than the clock, so a slow frame never starts the
                // mix before the button has grown.
                snapshotFlow { launch.value }.first { it >= 0.88f }
            } finally {
                // Never leave the mix waiting, even if Home goes away mid-zoom.
                covered.complete()
            }
            mix?.join()
            snapshotFlow { launch.value }.first { it >= 0.9f }
            onOpenPlayer()
        }
        scope.launch {
            // Written from the animation's own frame callback, so the shell's circle grows in the same frame.
            launch.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) { heroLaunch.progress = value }
            // Stay covered until the mix is playing, then reset under the player and let the
            // shapes pop back in for when Home shows again.
            mix?.join()
            delay(600)
            launch.snapTo(0f)
            heroLaunch.progress = 0f
            entrance.snapTo(0f)
            entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow))
        }
    }
    val launchValue = { launch.value }
    val entranceValue = { entrance.value }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Above the shelves below it, so the launch zoom swells over them rather than under.
            .zIndex(1f)
            .padding(horizontal = 20.dp)
            .testTag("home_your_mix")
    ) {
        // Phones stack the title over the collage. Wide screens (tablets, landscape, unfolded
        // foldables) put the title and play button on the left and the collage, kept at phone
        // size, beside it, so the shapes never stretch with the screen.
        val fullWidth = maxWidth
        val wide = fullWidth >= 600.dp
        val w = if (wide) minOf(fullWidth * 0.58f, 470.dp) else fullWidth
        val left = fullWidth - w
        val rise = if (wide) 200.dp else 0.dp
        // Centre of the play button: the point the launch zooms into.
        val focus = if (wide) DpOffset(64.dp, 264.dp) else DpOffset(fullWidth - 64.dp, 150.dp)
        Box(Modifier.fillMaxWidth().height(if (wide) 380.dp else 540.dp)) {
        Column(modifier = Modifier
            .padding(top = 8.dp)
            // Offscreen: the text is drawn once and the cached picture scales, instead of the
            // glyphs being re-rendered at a new size every frame.
            .flyOut(DpOffset(if (wide) 110.dp else fullWidth * 0.25f, 90.dp), focus, launchValue, CompositingStrategy.Offscreen)
        ) {
            Text(
                text = "Your\nMix",
                style = MaterialTheme.typography.displayLargeEmphasized.copy(
                    fontSize = 70.sp, lineHeight = 66.sp, letterSpacing = (-1.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = art.artists,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (wide) left - 24.dp else w * 0.55f)
            )
        }

        // The button is drawn last (below) so it swells over the shapes.

        // The collage. Positions are fractions of the width so it composes the same on any phone.
        val cover = { i: Int -> art.covers.getOrNull(i % art.covers.size.coerceAtLeast(1)) }
        val collage = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = start
        )
        MixPiece(cover(1), CircleShape, 104.dp, left + w * 0.0f, 250.dp - rise, 1.4f, entranceValue, 1, collage, launchValue, focus)
        MixPiece(cover(0), RoundedCornerShape(50), w * 0.78f, left + w * 0.14f, 300.dp - rise, 0f, entranceValue, 2, collage, launchValue, focus,
            height = 168.dp, rotation = -36f)
        MixPiece(cover(4), remember { io.github.roggzuay.ondalune.ui.theme.PolygonShape(MaterialShapes.Cookie6Sided) }, 70.dp,
            left + w * 0.66f, 236.dp - rise, 2.2f, entranceValue, 3, collage, launchValue, focus)
        MixPiece(cover(2), CircleShape, 132.dp, left + w - 136.dp, 404.dp - rise, 3.1f, entranceValue, 4, collage, launchValue, focus)
        MixPiece(cover(3), remember { io.github.roggzuay.ondalune.ui.theme.PolygonShape(MaterialShapes.Cookie9Sided) }, 96.dp, left + 4.dp, 420.dp - rise,
            4.4f, entranceValue, 5, collage, launchValue, focus)

        // Swell far enough to cover the whole screen, however wide it is.
        val maxZoom = maxOf(17f, fullWidth.value * 3.2f / 128f)
        SideEffect { heroLaunch.maxZoom = maxZoom }
        MixPlayButton(
            onClick = start,
            launch = launchValue,
            maxZoom = maxZoom,
            onCentre = { heroLaunch.centre = it },
            modifier = Modifier
                .align(if (wide) Alignment.TopStart else Alignment.TopEnd)
                .padding(top = if (wide) 200.dp else 86.dp)
                .resting(0.6f, entranceValue, 0)
        )
        }
    }
}

/**
 * The launch zoom: as [launch] goes 0 → 1 the element rushes away from [focus] (the play button's
 * centre) and grows, as if the camera dived past it. Elements on different sides of the button
 * part in different directions. Read in the draw layer, so nothing recomposes or re-lays out.
 */
private fun Modifier.flyOut(
    centre: DpOffset,
    focus: DpOffset,
    launch: () -> Float,
    compositing: CompositingStrategy = CompositingStrategy.ModulateAlpha
): Modifier = graphicsLayer {
    val t = launch()
    if (t <= 0f) return@graphicsLayer
    val dx = (centre.x - focus.x).toPx()
    val dy = (centre.y - focus.y).toPx()
    val distance = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
    // Far elements travel further, and even the nearest one clears the button.
    val push = t * (1.2f + 160.dp.toPx() / distance)
    translationX = dx * push
    translationY = dy * push
    val s = 1f + 1.3f * t
    scaleX = s; scaleY = s
    alpha = 1f - ((t - 0.4f) / 0.45f).coerceIn(0f, 1f)
    compositingStrategy = compositing
}

/** Each piece's own small lift and tilt, and its pop-in; read in the draw layer, so no recomposition. */
private fun Modifier.resting(
    offset: Float,
    entrance: () -> Float,
    order: Int
): Modifier = graphicsLayer {
    translationY = sin(offset) * 7.dp.toPx()
    rotationZ = sin(offset) * 3f
    val local = ((entrance() * 1.6f) - order * 0.12f).coerceIn(0f, 1f)
    scaleX = 0.6f + 0.4f * local
    scaleY = 0.6f + 0.4f * local
    alpha = local
}

@Composable
private fun MixPiece(
    url: String?,
    pieceShape: Shape,
    width: Dp,
    x: Dp,
    y: Dp,
    offset: Float,
    entrance: () -> Float,
    order: Int,
    modifier: Modifier,
    launch: () -> Float,
    focus: DpOffset,
    height: Dp = width,
    rotation: Float = 0f
) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .flyOut(DpOffset(x + width / 2, y + height / 2), focus, launch)
            .resting(offset, entrance, order)
            .graphicsLayer { rotationZ += rotation }
            .clip(pieceShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(modifier)
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(300).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = if (rotation != 0f) {
                    // Upright artwork inside the tilted pill: a square as wide as the pill's
                    // diagonal always covers it, whatever the pill's length.
                    Modifier
                        .requiredSize(kotlin.math.hypot(width.value, height.value).dp)
                        .graphicsLayer { rotationZ = -rotation }
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }
    }
}

/** Big round play button that squishes into a cookie while pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MixPlayButton(
    onClick: () -> Unit,
    launch: () -> Float,
    maxZoom: Float,
    onCentre: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "mixPress"
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    // A morphed outline is a path clip, costly once the button has swelled to many times its
    // size, so the launch always zooms the plain circle.
    val launching by remember { derivedStateOf { launch() > 0f } }
    val shape = if (progress == 0f || launching) CircleShape else MorphShape(morph, progress)
    Box(
        modifier = modifier
            .size(HeroButtonRadius * 2)
            // Unclipped centre in window coordinates, for the shell's copy of the zoom.
            .onGloballyPositioned { onCentre(it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f))) }
            .graphicsLayer {
                // On launch the button swells past the screen edges: the zoom into the player.
                val t = launch()
                val s = (1f - 0.06f * progress) * heroZoom(t, maxZoom)
                scaleX = s; scaleY = s
                rotationZ = progress * 20f + t * 40f
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag("home_your_mix_play"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play your mix",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    rotationZ = -progress * 20f
                    alpha = 1f - (launch() * 4f).coerceAtMost(1f)
                }
        )
    }
}

/** Four round shortcuts: history, newest, top tracks and a fresh mix. */
@Composable
fun HomeQuickActions(onOpenShelf: (HomeShelfKind) -> Unit, onShuffle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val actions = listOf(
        // One neutral tone for all four: colour belongs to the music, not the shortcuts.
        QuickAction("History", Icons.Filled.History, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) { onOpenShelf(HomeShelfKind.JUMP_BACK_IN) },
        QuickAction("Last added", Icons.Filled.LibraryAdd, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) { onOpenShelf(HomeShelfKind.RECENTLY_ADDED) },
        QuickAction("Most played", Icons.AutoMirrored.Filled.TrendingUp, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) { onOpenShelf(HomeShelfKind.MOST_PLAYED) },
        QuickAction("Shuffle", Icons.Filled.Shuffle, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, onShuffle)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("home_quick_actions"),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        actions.forEach { a ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = a.onClick)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(a.container),
                    contentAlignment = Alignment.Center
                ) { Icon(a.icon, contentDescription = null, tint = a.content) }
                Spacer(Modifier.height(6.dp))
                Text(a.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private class QuickAction(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit
)

/** Artists ranked by plays across top and favourite tracks; each opens the artist page. */
@Composable
fun TopArtistsShelf(shelves: HomeShelves, header: @Composable () -> Unit) {
    val top = remember(shelves) {
        (shelves.mostPlayed + shelves.favouriteTracks).distinctBy { it.id }
            .groupBy { it.artist }
            .filterKeys { it.isNotBlank() }
            .map { (_, tracks) -> tracks.maxBy { it.playCount } to tracks.sumOf { it.playCount + if (it.starred) 3 else 0 } }
            .sortedByDescending { it.second }
            .take(15)
            .map { it.first }
    }
    if (top.isEmpty()) return
    val actions = LocalTrackActions.current
    Column {
        header()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unkeyed like the shelves, so a newly top artist shows at the start.
            items(top) { track: SubsonicTrack ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { actions?.openArtist(track) }
                        .testTag("top_artist_${track.artist}")
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(track.coverArtUrl).crossfade(200).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Opens the Home layout editor from the bottom of Home. */
@Composable
fun CustomizeHomeButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick, modifier = Modifier.testTag("home_customize_button")) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Customize Home")
        }
    }
}

/**
 * Reorder and show/hide Home sections. Drag a row by its handle (or long-press anywhere on it)
 * to move it; the order is saved when the finger lifts. [sections] is the visible order;
 * hidden sections are listed after it.
 */
@Composable
fun HomeLayoutEditor(
    sections: List<HomeSection>,
    isCustom: Boolean,
    onChange: (List<HomeSection>) -> Unit,
    onReset: () -> Unit
) {
    var order by remember(sections) { mutableStateOf(sections) }
    var dragging by remember { mutableStateOf<HomeSection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(1f) }
    val haptics = LocalHapticFeedbackManager.current
    val hidden = HomeSection.entries.filter { it !in order }

    val drag = DragCallbacks(
        onStart = { section -> dragging = section; dragOffset = 0f; haptics?.performSubtleTick() },
        onDrag = { delta ->
            val section = dragging
            if (section != null) {
                dragOffset += delta
                val index = order.indexOf(section)
                // Swap with a neighbour once the row passes half of it.
                if (dragOffset > rowHeight / 2 && index < order.lastIndex) {
                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                    dragOffset -= rowHeight
                    haptics?.performSubtleTick()
                } else if (dragOffset < -rowHeight / 2 && index > 0) {
                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                    dragOffset += rowHeight
                    haptics?.performSubtleTick()
                }
            }
        },
        onEnd = {
            // Only a real drag commits. The detectors also end when a row leaves the list (its
            // switch was turned off), and committing then put the section straight back.
            if (dragging != null) {
                dragging = null
                dragOffset = 0f
                onChange(order)
            }
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        order.forEach { section ->
            key(section) {
                val lifted = dragging == section
                val elevation by animateFloatAsState(if (lifted) 1f else 0f, label = "lift")
                HomeSectionRow(
                    section = section,
                    visible = true,
                    drag = drag,
                    // Keep one section, so Home never ends up empty.
                    canToggle = order.size > 1,
                    onToggle = { onChange(order - section) },
                    modifier = Modifier
                        .zIndex(if (lifted) 1f else 0f)
                        .onSizeChanged { rowHeight = it.height.toFloat() }
                        .graphicsLayer {
                            translationY = if (lifted) dragOffset else 0f
                            scaleX = 1f + 0.03f * elevation
                            scaleY = scaleX
                            shadowElevation = 12.dp.toPx() * elevation
                            shape = RoundedCornerShape(16.dp)
                            clip = elevation > 0f
                        }
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = elevation))
                )
            }
        }
        if (hidden.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Hidden",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            hidden.forEach { section ->
                HomeSectionRow(section, visible = false, drag = null, onToggle = { onChange(order + section) })
            }
        }
        TextButton(onClick = onReset, enabled = isCustom, modifier = Modifier.align(Alignment.End)) {
            Text("Use the theme's layout")
        }
    }
}

private class DragCallbacks(
    val onStart: (HomeSection) -> Unit,
    val onDrag: (Float) -> Unit,
    val onEnd: () -> Unit
)

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    visible: Boolean,
    drag: DragCallbacks?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    canToggle: Boolean = true
) {
    // The detectors below outlive recompositions; read the latest callbacks, whose `order` is
    // current, rather than the ones captured when the row first appeared.
    val currentDrag by rememberUpdatedState(drag)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (drag != null) Modifier.pointerInput(section) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentDrag?.onStart(section) },
                        onDragEnd = { currentDrag?.onEnd() },
                        onDragCancel = { currentDrag?.onEnd() }
                    ) { change, amount -> change.consume(); currentDrag?.onDrag(amount.y) }
                } else Modifier
            )
            .padding(vertical = 4.dp)
            .testTag("home_section_${section.name.lowercase()}")
    ) {
        if (drag != null) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder ${section.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(44.dp)
                    .pointerInput(section) {
                        detectVerticalDragGestures(
                            onDragStart = { currentDrag?.onStart(section) },
                            onDragEnd = { currentDrag?.onEnd() },
                            onDragCancel = { currentDrag?.onEnd() }
                        ) { change, amount -> change.consume(); currentDrag?.onDrag(amount) }
                    }
                    .padding(10.dp)
                    .testTag("home_section_handle_${section.name.lowercase()}")
            )
        } else {
            Spacer(Modifier.width(44.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                section.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (visible) FontWeight.SemiBold else FontWeight.Normal,
                color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                section.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = visible, enabled = canToggle, onCheckedChange = { onToggle() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutSheet(
    customSections: List<HomeSection>,
    onChange: (List<HomeSection>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sections = customSections.ifEmpty { LocalStyleSpec.current.defaultHome }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Customize Home", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Drag the handles to reorder, switch off what you don't use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            HomeLayoutEditor(sections, customSections.isNotEmpty(), onChange, onReset)
        }
    }
}

/**
 * "Mixes for you": mixes made from the library (genres, the most played artist, favourites, new
 * arrivals, forgotten favourites), each a card with a collage of its covers. Tapping plays it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MixesShelf(mixes: List<LibraryMix>, onPlay: (LibraryMix) -> Unit) {
    Column {
        ShelfHeader("Mixes for you", onMore = null)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(mixes, key = { it.key }) { mix ->
                Column(
                    modifier = Modifier
                        .width(156.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClickLabel = "Play ${mix.title}") { onPlay(mix) }
                        // Room inside the rounded corner, so the clip never touches the text.
                        .padding(bottom = 12.dp)
                        .testTag("mix_${mix.key}")
                ) {
                    MixCollage(mix.covers, Modifier.size(156.dp))
                    Text(
                        mix.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp)
                    )
                    Text(
                        mix.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp)
                    )
                }
            }
        }
    }
}

/** Four covers in a rounded square (fewer fill what they can), on the theme's container colour. */
@Composable
internal fun MixCollage(covers: List<String>, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val cells = covers.take(4)
        if (cells.size < 4) {
            cells.firstOrNull()?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        } else {
            Column(Modifier.fillMaxSize()) {
                cells.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { url ->
                            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}
