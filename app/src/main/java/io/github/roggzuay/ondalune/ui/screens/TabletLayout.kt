package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.viewmodel.PrimaryDestination

/**
 * Tablet width (a tablet in either orientation, a foldable opened flat): the app gets a labelled
 * sidebar, Settings shows its list beside the open section, and the player and artist pages use
 * layouts made for the room instead of the phone's stretched across it.
 */
@Composable
fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= TabletMinWidthDp

const val TabletMinWidthDp = 840

/**
 * Tablet sidebar: the five tabs with their names, the current one held in a filled pill, and
 * Settings apart at the bottom. Replaces the phone's bottom dock and the narrow icon rail.
 * Swiping it left (or the button at its top) folds it to icons only, giving the page more room;
 * swiping right opens it again. The tabs never disappear.
 */
@Composable
internal fun TabletSidebar(
    destinations: List<PrimaryDestination>,
    current: PrimaryDestination,
    icon: (PrimaryDestination, Boolean) -> ImageVector,
    onOpen: (PrimaryDestination) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Room for a top bar that is hidden right now, so the tabs never move. */
    topGap: () -> Dp = { 0.dp }
) {
    val width by animateDpAsState(if (expanded) 232.dp else 88.dp, label = "sidebarWidth")
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val setExpanded by rememberUpdatedState(onExpandedChange)
    val isExpanded by rememberUpdatedState(expanded)
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // Laid out under the top bar, so only the side and bottom insets apply.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Bottom))
            .layout { measurable, constraints ->
                val gap = topGap().roundToPx()
                val placeable = measurable.measure(constraints.offset(vertical = -gap))
                layout(placeable.width, placeable.height + gap) { placeable.place(0, gap) }
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged < -threshold && isExpanded) setExpanded(false)
                        if (dragged > threshold && !isExpanded) setExpanded(true)
                    }
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            }
            .padding(horizontal = 12.dp)
            .testTag("tablet_sidebar"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.padding(start = 4.dp).testTag("sidebar_toggle")
        ) {
            Icon(
                if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                contentDescription = if (expanded) "Hide sidebar" else "Show sidebar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        destinations.forEach { dest ->
            SidebarRow(dest.label, icon(dest, dest == current), dest == current, expanded, "sidebar_${dest.route}") { onOpen(dest) }
        }
        Spacer(Modifier.weight(1f))
        val settings = PrimaryDestination.SETTINGS
        SidebarRow(settings.label, icon(settings, current == settings), current == settings, expanded, "sidebar_settings") { onOpen(settings) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SidebarRow(label: String, icon: ImageVector, selected: Boolean, showLabel: Boolean, tag: String, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "sidebarRow"
    )
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = cornerShape(28.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { this.selected = selected; role = Role.Tab; contentDescription = label }
            .testTag(tag)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content)
            AnimatedVisibility(showLabel, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) content else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

/** The server's cover at 1200 px instead of 600: a tablet shows it big enough to need it. */
private fun sharperCoverUrl(url: String): String =
    if ("getCoverArt" in url) url.replace(Regex("([?&]size=)\\d+")) { it.groupValues[1] + "1200" } else url

/**
 * Tablet player cover: a framed card at a size the picture can hold, never stretched across the
 * screen. The usual cover shows first and the sharper copy fades in over it once loaded.
 * Swiping it sideways changes song, as on the phone.
 */
@Composable
internal fun TabletCoverCard(
    track: SubsonicTrack,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val shape = cornerShape(22.dp)
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val next by rememberUpdatedState(onSkipNext)
    val previous by rememberUpdatedState(onSkipPrevious)
    Crossfade(
        targetState = track,
        animationSpec = tween(300),
        modifier = modifier
            .aspectRatio(1f)
            .shadow(28.dp, shape)
            .clip(shape)
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged < -threshold -> next()
                            dragged > threshold -> previous()
                        }
                    }
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            }
            .testTag("tablet_cover_card"),
        label = "tabletCover"
    ) { shown ->
        Box(Modifier.fillMaxSize()) {
            ExpressiveArtwork(
                seedHex = shown.paletteSeedHex,
                accentHex = shown.accentHex,
                artStyleIndex = shown.artStyleIndex,
                coverArtUrl = shown.coverArtUrl,
                fullBleed = true,
                modifier = Modifier.fillMaxSize()
            )
            val sharp = sharperCoverUrl(shown.coverArtUrl)
            if (sharp != shown.coverArtUrl) {
                AsyncImage(
                    model = remember(sharp) { ImageRequest.Builder(context).data(sharp).crossfade(250).build() },
                    contentDescription = "${shown.album} cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
