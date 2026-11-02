package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Arrangement
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

/**
 * Every download on the active server, grouped: in progress (downloading, then waiting), failed
 * with the reason and Retry, and saved on the phone. Opened from the top bar's download ring and
 * from Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: Collection<SubsonicDownloadEntity>,
    onCancel: (List<String>) -> Unit,
    onRetry: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val byState = downloads.groupBy { it.stateEnum }
    val inProgress = byState[OfflineDownloadState.DOWNLOADING].orEmpty() +
        byState[OfflineDownloadState.QUEUED].orEmpty().sortedBy { it.updatedAtMs }
    val failed = byState[OfflineDownloadState.FAILED].orEmpty().sortedByDescending { it.updatedAtMs }
    val saved = downloads.filter { it.isCompleted }.sortedByDescending { it.updatedAtMs }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("downloads_sheet")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 12.dp, bottom = 32.dp)
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
            section("In progress", inProgress, "Cancel all", { onCancel(inProgress.map { it.trackId }) }) { d ->
                DownloadRow(d, Icons.Filled.Clear, "Cancel download", { onCancel(listOf(d.trackId)) }) {
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
            section("Failed", failed, "Retry all", { onRetry(failed.map { it.trackId }) }) { d ->
                DownloadRow(
                    d, Icons.Filled.Refresh, "Retry download", { onRetry(listOf(d.trackId)) },
                    secondIcon = Icons.Filled.Clear to { onRemove(d.trackId) }
                ) { Detail(d.errorMessage.ifBlank { "Download failed" }, MaterialTheme.colorScheme.error) }
            }
            section("Saved on this phone", saved) { d ->
                DownloadRow(d, Icons.Outlined.DeleteOutline, "Remove download", { onRemove(d.trackId) }) {
                    Detail("${d.artist} · ${formatBytes(d.totalBytes)}")
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    rows: List<SubsonicDownloadEntity>,
    action: String? = null,
    onAction: () -> Unit = {},
    row: @Composable (SubsonicDownloadEntity) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = "header_$title") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(40.dp)
        ) {
            Text(
                "$title · ${rows.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (action != null && rows.size > 1) TextButton(onClick = onAction) { Text(action) }
        }
    }
    items(rows, key = { it.downloadKey }) { row(it) }
}

@Composable
private fun DownloadRow(
    download: SubsonicDownloadEntity,
    icon: ImageVector,
    iconLabel: String,
    onIcon: () -> Unit,
    secondIcon: Pair<ImageVector, () -> Unit>? = null,
    detail: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(download.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail()
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = onIcon) { Icon(icon, contentDescription = iconLabel, modifier = Modifier.size(20.dp)) }
            secondIcon?.let { (secondIconImage, onSecond) ->
                IconButton(onClick = onSecond) { Icon(secondIconImage, contentDescription = "Remove from list", modifier = Modifier.size(20.dp)) }
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
