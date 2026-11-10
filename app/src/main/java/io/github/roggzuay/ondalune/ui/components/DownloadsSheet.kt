package io.github.roggzuay.ondalune.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec

/**
 * Every download on the active server, grouped: in progress (downloading, then waiting) with
 * pause and cancel, paused with resume, failed with the reason and Retry, and saved on the phone.
 * Tapping a song opens its album and plays it. Opened from the download activity button, the
 * filter sheet and Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: Collection<SubsonicDownloadEntity>,
    /** Fixed order for the rows (see [DownloadOrder]), so they don't swap as they progress. */
    order: Comparator<SubsonicDownloadEntity>,
    trackFor: (String) -> SubsonicTrack?,
    onCancel: (List<String>) -> Unit,
    onPause: (List<String>) -> Unit,
    onResume: (List<String>) -> Unit,
    onRetry: (List<String>) -> Unit,
    onRemove: (List<String>) -> Unit,
    onOpenTrack: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val byState = downloads.groupBy { it.stateEnum }
    val inProgress = (byState[OfflineDownloadState.DOWNLOADING].orEmpty() + byState[OfflineDownloadState.QUEUED].orEmpty())
        .sortedWith(order)
    val paused = byState[OfflineDownloadState.PAUSED].orEmpty().sortedWith(order)
    val failed = byState[OfflineDownloadState.FAILED].orEmpty().sortedWith(order)
    val saved = downloads.filter { it.isCompleted }.sortedByDescending { it.updatedAtMs }
    // Saved songs picked for removal. Long-press one to start; then a tap adds or drops a song.
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val selection = picked.intersect(saved.mapTo(HashSet()) { it.trackId })
    var confirmRemove by remember { mutableStateOf(false) }
    fun toggle(id: String) { picked = if (id in selection) selection - id else selection + id }

    // ModalBottomSheet rises from the bottom edge; skipPartiallyExpanded lets it open in one motion.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("downloads_sheet")
    ) {
        BackHandler(enabled = selection.isNotEmpty()) { picked = emptySet() }
        if (selection.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = { picked = emptySet() }) { Icon(Icons.Filled.Clear, contentDescription = "Stop selecting") }
                Text("${selection.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val all = selection.size == saved.size
                TextButton(onClick = { picked = if (all) emptySet() else saved.mapTo(HashSet()) { it.trackId } }) {
                    Text(if (all) "Select none" else "Select all")
                }
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove selected downloads")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 32.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text("Downloads", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${saved.size} ${if (saved.size == 1) "song" else "songs"} saved · ${formatBytes(saved.sumOf { it.totalBytes })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (downloads.none { it.stateEnum != OfflineDownloadState.CANCELLED }) {
                item {
                    Text(
                        "Nothing downloaded yet. Download a song, an album or a playlist to play it without a connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, end = 12.dp)
                    )
                }
            }
            section("In progress", inProgress, listOf(
                "Pause all" to { onPause(inProgress.map { it.trackId }) },
                "Cancel all" to { onCancel(inProgress.map { it.trackId }) }
            )) { d ->
                DownloadRow(
                    d, trackFor(d.trackId), { onOpenTrack(d.trackId) },
                    Icons.Filled.Pause to { onPause(listOf(d.trackId)) }, "Pause download",
                    Icons.Filled.Clear to { onCancel(listOf(d.trackId)) }, "Cancel download"
                ) {
                    if (d.stateEnum == OfflineDownloadState.DOWNLOADING) {
                        LinearProgressIndicator(
                            progress = { d.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    } else {
                        Detail(d.errorMessage.ifBlank { "Waiting" })
                    }
                }
            }
            section("Paused", paused, listOf("Resume all" to { onResume(paused.map { it.trackId }) })) { d ->
                DownloadRow(
                    d, trackFor(d.trackId), { onOpenTrack(d.trackId) },
                    Icons.Filled.PlayArrow to { onResume(listOf(d.trackId)) }, "Resume download",
                    Icons.Filled.Clear to { onCancel(listOf(d.trackId)) }, "Cancel download"
                ) { Detail(if (d.progressPercent > 0) "Paused at ${d.progressPercent}%" else "Paused") }
            }
            section("Failed", failed, listOf("Retry all" to { onRetry(failed.map { it.trackId }) })) { d ->
                DownloadRow(
                    d, trackFor(d.trackId), { onOpenTrack(d.trackId) },
                    Icons.Filled.Refresh to { onRetry(listOf(d.trackId)) }, "Retry download",
                    Icons.Filled.Clear to { onRemove(listOf(d.trackId)) }, "Remove from list"
                ) { Detail(d.errorMessage.ifBlank { "Download failed" }, MaterialTheme.colorScheme.error) }
            }
            section("Saved on this phone", saved) { d ->
                DownloadRow(
                    d, trackFor(d.trackId), { if (selection.isEmpty()) onOpenTrack(d.trackId) else toggle(d.trackId) },
                    Icons.Outlined.DeleteOutline to { onRemove(listOf(d.trackId)) }, "Remove download",
                    selected = d.trackId in selection,
                    selecting = selection.isNotEmpty(),
                    onLongClick = { toggle(d.trackId) }
                ) {
                    Detail("${d.artist} · ${formatBytes(d.totalBytes)}")
                }
            }
        }
    }
    if (confirmRemove) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (count == 1) "Remove 1 download?" else "Remove $count downloads?") },
            text = { Text("They come off this phone and stay in your library to stream.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(selection.toList())
                    picked = emptySet()
                    confirmRemove = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<SubsonicDownloadEntity>,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    row: @Composable (SubsonicDownloadEntity) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = "header_$title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.animateItem().fillMaxWidth().padding(top = 16.dp, end = 4.dp).height(40.dp)
        ) {
            Text(
                "$title · ${rows.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (rows.size > 1) {
                val haptics = LocalHapticFeedbackManager.current
                actions.forEach { (label, onAction) ->
                    TextButton(onClick = { haptics?.performSubtleTick(); onAction() }) { Text(label) }
                }
            }
        }
    }
    // Keyed by download, so a paused or resumed song glides into its new section instead of jumping.
    items(rows, key = { it.downloadKey }) { Box(Modifier.animateItem()) { row(it) } }
}

@Composable
private fun DownloadRow(
    download: SubsonicDownloadEntity,
    track: SubsonicTrack?,
    onOpen: () -> Unit,
    first: Pair<ImageVector, () -> Unit>,
    firstLabel: String,
    second: Pair<ImageVector, () -> Unit>? = null,
    secondLabel: String = "",
    selected: Boolean = false,
    /** Picking songs to remove: a tap picks, and the row's own buttons step aside. */
    selecting: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    detail: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedbackManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClickLabel = if (selecting) "Select" else "Open album and play",
                onLongClickLabel = onLongClick?.let { "Select" },
                onLongClick = onLongClick?.let { { haptics?.performSubtleTick(); it() } },
                onClick = onOpen
            )
            .padding(vertical = 6.dp)
            .testTag("download_row_${download.trackId}")
    ) {
        if (track != null) {
            Box(contentAlignment = Alignment.Center) {
                ExpressiveArtwork(
                    seedHex = track.paletteSeedHex,
                    accentHex = track.accentHex,
                    artStyleIndex = track.artStyleIndex,
                    coverArtUrl = track.coverArtUrl,
                    styleShape = LocalStyleSpec.current.rowArtShape,
                    modifier = Modifier.size(44.dp)
                )
                if (selected) SelectedBadge()
            }
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(download.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail()
        }
        Spacer(Modifier.size(4.dp))
        if (selecting) return@Row
        IconButton(onClick = { haptics?.performSubtleTick(); first.second() }) {
            Icon(first.first, contentDescription = firstLabel, modifier = Modifier.size(20.dp))
        }
        second?.let { (icon, onClick) ->
            IconButton(onClick = { haptics?.performSubtleTick(); onClick() }) {
                Icon(icon, contentDescription = secondLabel, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Detail(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}
