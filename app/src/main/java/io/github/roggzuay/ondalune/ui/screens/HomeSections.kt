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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.LocalTrackActions
import io.github.roggzuay.ondalune.ui.components.MorphShape
import io.github.roggzuay.ondalune.ui.theme.LocalReducedMotion
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelves
import io.github.roggzuay.ondalune.ui.viewmodel.HomeShelfKind
import kotlin.math.PI
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
    val day = java.time.LocalDate.now().toEpochDay()
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
 * Starting the mix launches into the player: the artwork shapes shatter outwards while the play
 * button swells to fill the screen, then [onOpenPlayer] takes over. Coming back to Home, the shapes
 * pop back in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHero(
    shelves: HomeShelves,
    onPlay: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val art = remember(shelves) { mixArt(shelves) }
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedbackManager.current
    val scope = rememberCoroutineScope()
    // 0 = resting, 1 = shapes gone and the button covering the screen.
    val launch = remember { Animatable(0f) }
    val drift = rememberInfiniteTransition(label = "mixDrift")
    val phase = if (reduced) null else drift.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "mixPhase"
    )
    // Shapes pop in one after another the first time Home appears.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow))
    }
    val start: () -> Unit = start@{
        if (launch.isRunning) return@start
        haptics?.performSubtleTick()
        if (reduced) {
            onPlay()
            onOpenPlayer()
            return@start
        }
        // The mix is built in the background while the shards fly; the player opens while the
        // button is still swelling, so the two motions overlap into one zoom.
        onPlay()
        scope.launch {
            // Follows the animation rather than the clock, so a slow frame never opens the
            // player before the button has grown.
            snapshotFlow { launch.value }.first { it >= 0.7f }
            onOpenPlayer()
        }
        scope.launch {
            launch.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
            // Reset under the player, then let the shapes pop back in for when Home shows again.
            delay(600)
            launch.snapTo(0f)
            entrance.snapTo(0f)
            entrance.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow))
        }
    }
    val launchValue = { launch.value }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(540.dp)
            // Above the shelves below it, so the launch zoom swells over them rather than under.
            .zIndex(1f)
            .padding(horizontal = 20.dp)
            .testTag("home_your_mix")
    ) {
        val w = maxWidth
        Column(modifier = Modifier
            .padding(top = 8.dp)
            .graphicsLayer {
                // The title rushes towards the viewer and fades as the hero zooms in.
                val t = launchValue()
                val s = 1f + t * 0.5f
                scaleX = s; scaleY = s
                alpha = 1f - (t * 1.8f).coerceAtMost(1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
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
                modifier = Modifier.width(w * 0.55f)
            )
        }

        // The button is drawn last (below) so it swells over the shards.

        // The collage. Positions are fractions of the width so it composes the same on any phone.
        val cover = { i: Int -> art.covers.getOrNull(i % art.covers.size.coerceAtLeast(1)) }
        val collage = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = start
        )
        MixPiece(cover(1), CircleShape, 104.dp, w * 0.0f, 250.dp, phase, 1.4f, entrance.value, 1, collage, launchValue)
        MixPiece(cover(0), RoundedCornerShape(50), w * 0.78f, w * 0.14f, 300.dp, phase, 0f, entrance.value, 2, collage, launchValue,
            height = 168.dp, rotation = -36f)
        MixPiece(cover(4), CircleShape, 70.dp, w * 0.66f, 236.dp, phase, 2.2f, entrance.value, 3, collage, launchValue,
            morph = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf) })
        MixPiece(cover(2), CircleShape, 132.dp, w - 136.dp, 404.dp, phase, 3.1f, entrance.value, 4, collage, launchValue)
        MixPiece(cover(3), remember { io.github.roggzuay.ondalune.ui.theme.PolygonShape(MaterialShapes.Cookie9Sided) }, 96.dp, 4.dp, 420.dp,
            phase, 4.4f, entrance.value, 5, collage, launchValue)

        MixPlayButton(
            onClick = start,
            launch = launchValue,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 86.dp)
                .floating(phase, 0.6f, entrance.value, 0)
        )
    }
}

/**
 * Breaks the drawn content into [ShardCount] jagged wedges around an off-centre point and flings
 * them outward as [progress] goes 0 → 1. The content is recorded once per frame into [layer] and
 * redrawn per shard, so the artwork is not loaded or laid out again.
 */
private const val ShardCount = 7

private fun Modifier.shatter(layer: androidx.compose.ui.graphics.layer.GraphicsLayer, seed: Int, progress: () -> Float): Modifier =
    drawWithContent {
        val t = progress()
        if (t <= 0f) {
            drawContent()
            return@drawWithContent
        }
        layer.record { this@drawWithContent.drawContent() }
        val random = kotlin.random.Random(seed)
        val w = size.width
        val h = size.height
        val centre = Offset(w * (0.38f + random.nextFloat() * 0.24f), h * (0.38f + random.nextFloat() * 0.24f))
        val reach = maxOf(w, h) * 1.5f
        val angles = List(ShardCount) { i -> (i + random.nextFloat() * 0.6f) / ShardCount * 2f * PI.toFloat() }
        val eased = t * t
        layer.alpha = (1f - t * 1.25f).coerceIn(0f, 1f)
        for (i in angles.indices) {
            val a0 = angles[i]
            val a1 = if (i == angles.lastIndex) angles[0] + 2f * PI.toFloat() else angles[i + 1]
            val mid = (a0 + a1) / 2f
            val shard = Path().apply {
                moveTo(centre.x, centre.y)
                lineTo(centre.x + cos(a0) * reach, centre.y + sin(a0) * reach)
                lineTo(centre.x + cos(mid) * reach * 1.1f, centre.y + sin(mid) * reach * 1.1f)
                lineTo(centre.x + cos(a1) * reach, centre.y + sin(a1) * reach)
                close()
            }
            val distance = eased * (220.dp.toPx() + random.nextFloat() * 160.dp.toPx())
            val spin = (random.nextFloat() - 0.5f) * 140f * eased
            val pivot = Offset(centre.x + cos(mid) * w * 0.3f, centre.y + sin(mid) * h * 0.3f)
            withTransform({
                translate(cos(mid) * distance, sin(mid) * distance)
                rotate(spin, pivot)
                scale(1f - 0.35f * eased, 1f - 0.35f * eased, pivot)
            }) {
                clipPath(shard) { drawLayer(layer) }
            }
        }
    }

/** Gentle bob and sway driven by the shared [phase]; read in the draw layer, so no recomposition. */
private fun Modifier.floating(
    phase: androidx.compose.runtime.State<Float>?,
    offset: Float,
    entrance: Float,
    order: Int
): Modifier = graphicsLayer {
    val p = phase?.value ?: 0f
    translationY = sin(p + offset) * 7.dp.toPx()
    rotationZ = sin(p * 0.5f + offset) * 3f
    val local = ((entrance * 1.6f) - order * 0.12f).coerceIn(0f, 1f)
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
    phase: androidx.compose.runtime.State<Float>?,
    offset: Float,
    entrance: Float,
    order: Int,
    modifier: Modifier,
    launch: () -> Float,
    height: Dp = width,
    rotation: Float = 0f,
    morph: Morph? = null
) {
    val shardLayer = rememberGraphicsLayer()
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .floating(phase, offset, entrance, order)
            .graphicsLayer { rotationZ += rotation }
            .shatter(shardLayer, seed = order * 31) { launch() }
            .then(
                // A morphing piece re-cuts its outline in the draw layer each frame (no recomposition).
                if (morph != null) Modifier.graphicsLayer {
                    clip = true
                    shape = MorphShape(morph, (sin(phase?.value ?: 0f) + 1f) / 2f)
                } else Modifier.clip(pieceShape)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(modifier)
    ) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(300).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Counter-rotate inside the tilted pill so the artwork stays upright.
                    .graphicsLayer { rotationZ = -rotation; scaleX = if (rotation != 0f) 1.5f else 1f; scaleY = scaleX }
            )
        }
    }
}

/** Big round play button that squishes into a cookie while pressed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MixPlayButton(onClick: () -> Unit, launch: () -> Float, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "mixPress"
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val shape = if (progress == 0f) CircleShape else MorphShape(morph, progress)
    Box(
        modifier = modifier
            .size(128.dp)
            .graphicsLayer {
                // On launch the button swells past the screen edges: the zoom into the player.
                val t = launch()
                val zoom = if (t <= 0.15f) 1f else 1f + ((t - 0.15f) / 0.85f).let { it * it } * 16f
                val s = (1f - 0.06f * progress) * zoom
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
        QuickAction("History", Icons.Filled.History, scheme.primaryContainer, scheme.onPrimaryContainer) { onOpenShelf(HomeShelfKind.JUMP_BACK_IN) },
        QuickAction("Last added", Icons.Filled.LibraryAdd, scheme.tertiaryContainer, scheme.onTertiaryContainer) { onOpenShelf(HomeShelfKind.RECENTLY_ADDED) },
        QuickAction("Most played", Icons.AutoMirrored.Filled.TrendingUp, scheme.secondaryContainer, scheme.onSecondaryContainer) { onOpenShelf(HomeShelfKind.MOST_PLAYED) },
        QuickAction("Shuffle", Icons.Filled.Shuffle, scheme.inversePrimary, scheme.onSurface, onShuffle)
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
            items(top, key = { it.artist }) { track: SubsonicTrack ->
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
            dragging = null
            dragOffset = 0f
            onChange(order)
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
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (drag != null) Modifier.pointerInput(section) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { drag.onStart(section) },
                        onDragEnd = drag.onEnd,
                        onDragCancel = drag.onEnd
                    ) { change, amount -> change.consume(); drag.onDrag(amount.y) }
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
                            onDragStart = { drag.onStart(section) },
                            onDragEnd = drag.onEnd,
                            onDragCancel = drag.onEnd
                        ) { change, amount -> change.consume(); drag.onDrag(amount) }
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
        Switch(checked = visible, onCheckedChange = { onToggle() })
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
