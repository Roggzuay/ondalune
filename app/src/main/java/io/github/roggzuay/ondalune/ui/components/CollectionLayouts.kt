package io.github.roggzuay.ondalune.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import io.github.roggzuay.ondalune.data.model.LibraryViewMode
import io.github.roggzuay.ondalune.ui.theme.ArtShape
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec
import io.github.roggzuay.ondalune.ui.theme.cornerShape

/** One album, artist, playlist or track as any collection layout shows it. */
class CollectionEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val detail: String = "",
    val coverArtUrl: String = "",
    val seedHex: String = "#7C4DFF",
    val accentHex: String = "#70F5FF",
    val artStyleIndex: Int = 0,
    val monogram: String = "",
    /** Artists are round in every layout. */
    val round: Boolean = false,
    val highlighted: Boolean = false,
    /** Read during composition so multi-select changes redraw the entry. */
    val selected: () -> Boolean = { false },
    val onClick: () -> Unit,
    val onPlay: (() -> Unit)? = null,
    val onLongClick: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null
)

fun LibraryViewMode.icon(): ImageVector = when (this) {
    LibraryViewMode.COMPACT_LIST -> Icons.Filled.ViewHeadline
    LibraryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
    LibraryViewMode.GRID -> Icons.Filled.ViewModule
    LibraryViewMode.LARGE_GRID -> Icons.Filled.GridView
}

/**
 * Emits [entries] in [mode]:
 * compact = dense one-line rows; rows = large art with full detail and a play button;
 * tiles = [tileColumns] artwork tiles with a title; cards = two big cards with a play button.
 */
fun LazyListScope.collectionEntries(
    entries: List<CollectionEntry>,
    mode: LibraryViewMode,
    tileColumns: Int = 3
) {
    when (mode) {
        LibraryViewMode.COMPACT_LIST -> items(entries, key = { "c_${it.key}" }, contentType = { "entry_compact" }) { CompactEntryRow(it) }
        LibraryViewMode.LIST -> items(entries, key = { "r_${it.key}" }, contentType = { "entry_row" }) { DetailedEntryRow(it) }
        LibraryViewMode.GRID -> entryGrid(entries, tileColumns, "t") { EntryTile(it, Modifier.weight(1f)) }
        LibraryViewMode.LARGE_GRID -> entryGrid(entries, 2, "k") { EntryCard(it, Modifier.weight(1f)) }
    }
}

private fun LazyListScope.entryGrid(
    entries: List<CollectionEntry>,
    columns: Int,
    prefix: String,
    cell: @Composable androidx.compose.foundation.layout.RowScope.(CollectionEntry) -> Unit
) {
    items(entries.chunked(columns), key = { row -> "${prefix}_${row.first().key}" }, contentType = { "entry_grid_$prefix" }) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (columns > 2) 10.dp else 14.dp)) {
            row.forEach { cell(it) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun EntryArt(entry: CollectionEntry, modifier: Modifier) {
    val selected = entry.selected()
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "entrySelectScale")
    Box(modifier, contentAlignment = Alignment.TopStart) {
        ExpressiveArtwork(
            seedHex = entry.seedHex,
            accentHex = entry.accentHex,
            artStyleIndex = entry.artStyleIndex,
            coverArtUrl = entry.coverArtUrl,
            monogramText = entry.monogram,
            styleShape = if (entry.round) ArtShape.CIRCLE else null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }
        )
        if (selected) SelectedBadge(
            Modifier
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}

/** Container colour for an entry: selection wins over the now-playing highlight. */
@Composable
private fun CollectionEntry.container(highlight: Color, idle: Color): Color = when {
    selected() -> MaterialTheme.colorScheme.secondaryContainer
    highlighted -> highlight
    else -> idle
}

@Composable
private fun PlayChip(onPlay: () -> Unit, size: Int, filled: Boolean) {
    ExpressiveBounceIconButton(
        onClick = onPlay,
        size = size.dp,
        containerColor = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size((size * 0.5f).dp)) }
}

@Composable
private fun CompactEntryRow(entry: CollectionEntry) {
    SpringPressableSurface(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick,
        color = entry.container(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), Color.Transparent),
        cornerRadius = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("entry_compact_${entry.key}")
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            EntryArt(entry, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "  ·  ${entry.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            entry.trailing?.invoke()
        }
    }
}

@Composable
private fun DetailedEntryRow(entry: CollectionEntry) {
    val cards = LocalStyleSpec.current.cardRows
    SpringPressableSurface(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick,
        color = entry.container(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            if (cards) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
        ),
        cornerRadius = 24.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("entry_row_${entry.key}")
    ) {
        Row(Modifier.padding(if (cards) 14.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
            EntryArt(entry, Modifier.size(68.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.detail.isNotBlank()) Text(
                    entry.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            entry.trailing?.invoke()
            entry.onPlay?.let { PlayChip(it, 44, filled = false) }
        }
    }
}

@Composable
private fun EntryTile(entry: CollectionEntry, modifier: Modifier) {
    SpringPressableSurface(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick,
        color = entry.container(Color.Transparent, Color.Transparent),
        cornerRadius = 16.dp,
        modifier = modifier.testTag("entry_tile_${entry.key}")
    ) {
        // Bottom padding keeps the title clear of the tile's rounded press-clip corners.
        Column(
            horizontalAlignment = if (entry.round) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            EntryArt(entry, Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(6.dp))
            Text(
                entry.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun EntryCard(entry: CollectionEntry, modifier: Modifier) {
    SpringPressableSurface(
        onClick = entry.onClick,
        onLongClick = entry.onLongClick,
        color = entry.container(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer),
        cornerRadius = 26.dp,
        modifier = modifier.testTag("entry_card_${entry.key}")
    ) {
        Column(Modifier.padding(10.dp)) {
            Box(contentAlignment = Alignment.BottomEnd) {
                EntryArt(entry, Modifier.fillMaxWidth().aspectRatio(1f))
                entry.onPlay?.let { Box(Modifier.padding(8.dp)) { PlayChip(it, 46, filled = true) } }
            }
            Spacer(Modifier.height(10.dp))
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.detail.isNotBlank()) Text(
                entry.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The ⋮ button with the layout strip (Compact / Rows / Tiles / Cards) and, when [sortOptions]
 * is not empty, the sort list.
 */
@Composable
fun ViewModeMenuButton(
    viewMode: LibraryViewMode,
    onSetViewMode: (LibraryViewMode) -> Unit,
    sortOptions: List<LibrarySortOption> = emptyList(),
    sortOption: LibrarySortOption? = null,
    ascending: Boolean = true,
    onSetSortOption: (LibrarySortOption) -> Unit = {},
    onToggleSortDirection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ExpressiveBounceIconButton(
            onClick = { open = true },
            active = open,
            size = 48.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("library_view_mode_menu_button")
        ) { Icon(Icons.Filled.MoreVert, contentDescription = "Layout and sort", modifier = Modifier.size(22.dp)) }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = cornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .width(272.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Layout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LibraryViewMode.entries.forEach { mode ->
                        val selected = viewMode == mode
                        SpringPressableSurface(
                            onClick = { onSetViewMode(mode); open = false },
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            cornerRadius = 16.dp,
                            modifier = Modifier
                                .size(width = 58.dp, height = 56.dp)
                                .testTag("library_view_${mode.name.lowercase()}_button")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(mode.icon(), contentDescription = mode.label, tint = tint, modifier = Modifier.size(20.dp))
                                Text(mode.shortLabel, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                            }
                        }
                    }
                }
                Text(
                    viewMode.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (sortOptions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        SpringPressableSurface(
                            onClick = onToggleSortDirection,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            cornerRadius = 12.dp,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("library_sort_direction_button")
                        ) {
                            Row(
                                Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                    contentDescription = if (ascending) "Ascending" else "Descending",
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(if (ascending) "Asc" else "Desc", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sortOptions.forEach { option ->
                            val selected = option == sortOption
                            SpringPressableSurface(
                                onClick = { onSetSortOption(option); open = false },
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                cornerRadius = 12.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .testTag("library_sort_${option.name.lowercase()}")
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    if (selected) Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(16.dp)
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
