package io.github.roggzuay.ondalune.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth

/** Where a song (or every song of an album) stands on its way to the phone. */
enum class DownloadPhase { IDLE, QUEUED, DOWNLOADING, PAUSED, FAILED, SAVED }

data class DownloadStatus(val phase: DownloadPhase, val progress: Float = 0f) {
    val transferring get() = phase == DownloadPhase.QUEUED || phase == DownloadPhase.DOWNLOADING
}

/**
 * One status for a set of songs (a single track, or an album's tracks): any still moving wins,
 * then paused, then saved once every song is on the phone, then failed. Progress counts saved
 * songs as whole, so an album's ring fills song by song.
 */
fun downloadStatusOf(rows: List<SubsonicDownloadEntity?>): DownloadStatus {
    if (rows.isEmpty()) return DownloadStatus(DownloadPhase.IDLE)
    val states = rows.map { it?.stateEnum ?: OfflineDownloadState.NOT_DOWNLOADED }
    val progress = rows.sumOf { if (it?.isCompleted == true) 100 else it?.progressPercent ?: 0 } / (rows.size * 100f)
    return when {
        OfflineDownloadState.DOWNLOADING in states -> DownloadStatus(DownloadPhase.DOWNLOADING, progress)
        OfflineDownloadState.QUEUED in states ->
            // Some songs already done: show how far the album is, not an endless spinner.
            DownloadStatus(if (progress > 0f) DownloadPhase.DOWNLOADING else DownloadPhase.QUEUED, progress)
        OfflineDownloadState.PAUSED in states -> DownloadStatus(DownloadPhase.PAUSED, progress)
        states.all { it == OfflineDownloadState.COMPLETED } -> DownloadStatus(DownloadPhase.SAVED, 1f)
        OfflineDownloadState.FAILED in states -> DownloadStatus(DownloadPhase.FAILED, progress)
        else -> DownloadStatus(DownloadPhase.IDLE, progress)
    }
}

/**
 * Where each download sits in the downloads lists, so rows never trade places as they progress.
 * Albums keep the place they first appeared in (queue order); an album's songs follow its track
 * numbers. The database returns rows by last update, and sorting by that (or putting the running
 * songs first) swapped rows on every progress tick and whenever a waiting song started.
 */
class DownloadOrder {
    private val albums = HashMap<String, Int>()

    /** Gives albums seen for the first time the next places, oldest first. */
    fun place(rows: Collection<SubsonicDownloadEntity>) {
        rows.sortedBy { it.updatedAtMs }.forEach { albums.getOrPut(it.albumId) { albums.size } }
    }

    fun comparator(trackNumber: (trackId: String) -> Int): Comparator<SubsonicDownloadEntity> =
        compareBy({ albums[it.albumId] ?: Int.MAX_VALUE }, { trackNumber(it.trackId) }, { it.title })
}

/**
 * The download control on an album page, the player and anywhere a song or album can be saved.
 * Tapping the cloud turns it into a spinner at once (before the queue has written anything), then
 * a ring that fills as the songs arrive: tap the ring to cancel. Paused shows the ring with play
 * (tap to resume), failed shows the cloud in the error colour (tap to retry), and saved shows the
 * done cloud, which offers to remove the download.
 */
@Composable
fun DownloadButton(
    status: DownloadStatus,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    container: @Composable (onClick: () -> Unit, content: @Composable () -> Unit) -> Unit
) {
    // Shown from the tap until the queue reports back, so the icon answers immediately.
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(status.phase, pending) {
        if (!pending) return@LaunchedEffect
        if (status.phase != DownloadPhase.IDLE && status.phase != DownloadPhase.FAILED) pending = false
        else { delay(4_000); pending = false } // nothing arrived (e.g. no songs): don't spin forever
    }
    var offerRemove by remember { mutableStateOf(false) }
    val shown = if (pending) DownloadStatus(DownloadPhase.QUEUED) else status
    Box(modifier) {
        container({
            when (shown.phase) {
                DownloadPhase.IDLE, DownloadPhase.FAILED -> { pending = true; onDownload() }
                DownloadPhase.QUEUED, DownloadPhase.DOWNLOADING -> onCancel()
                DownloadPhase.PAUSED -> { pending = true; onResume() }
                DownloadPhase.SAVED -> offerRemove = true
            }
        }) {
            DownloadGlyph(shown, iconSize)
        }
        DropdownMenu(expanded = offerRemove, onDismissRequest = { offerRemove = false }) {
            DropdownMenuItem(
                text = { Text("Remove download") },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    offerRemove = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
private fun DownloadGlyph(status: DownloadStatus, iconSize: Dp) {
    val ring = iconSize + 4.dp
    AnimatedContent(
        targetState = status.phase,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn())
                .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
        },
        contentAlignment = Alignment.Center,
        label = "downloadGlyph"
    ) { phase ->
        when (phase) {
            DownloadPhase.IDLE -> Icon(Icons.Filled.CloudDownload, contentDescription = "Download", modifier = Modifier.size(iconSize))
            DownloadPhase.FAILED -> Icon(
                Icons.Filled.CloudDownload,
                contentDescription = "Download failed, try again",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(iconSize)
            )
            DownloadPhase.SAVED -> Icon(Icons.Filled.CloudDone, contentDescription = "Downloaded", modifier = Modifier.size(iconSize))
            DownloadPhase.QUEUED -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.semantics { contentDescription = "Download starting, tap to cancel" }
            ) {
                CircularProgressIndicator(modifier = Modifier.size(ring), strokeWidth = 2.5.dp)
                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(iconSize * 0.5f))
            }
            DownloadPhase.DOWNLOADING, DownloadPhase.PAUSED -> Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.semantics {
                    contentDescription = if (phase == DownloadPhase.PAUSED) "Download paused, tap to resume"
                    else "Downloading, ${(status.progress * 100).toInt()} percent, tap to cancel"
                }
            ) {
                CircularProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.size(ring),
                    strokeWidth = 2.5.dp,
                    trackColor = LocalContentColor.current.copy(alpha = 0.18f)
                )
                Icon(
                    if (phase == DownloadPhase.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize * 0.5f)
                )
            }
        }
    }
}

/**
 * Bottom-right while songs are downloading and the screen isn't already showing them: a ring with
 * the overall progress around a download arrow. Stays (in the error colour) after a batch that had
 * failures, until the downloads sheet has been opened.
 */
@Composable
fun DownloadActivityButton(
    visible: Boolean,
    active: List<SubsonicDownloadEntity>,
    hasUnseenFailures: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        val failedOnly = active.isEmpty() && hasUnseenFailures
        FloatingActionButton(
            onClick = onClick,
            containerColor = if (failedOnly) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .testTag("download_activity_button")
                .semantics {
                    contentDescription = if (failedOnly) "Some downloads failed, open downloads"
                    else "${active.size} downloading, open downloads"
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!failedOnly) {
                    val status = downloadStatusOf(active)
                    if (status.phase == DownloadPhase.QUEUED) {
                        CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 2.5.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 2.5.dp,
                            trackColor = LocalContentColor.current.copy(alpha = 0.18f)
                        )
                    }
                }
                Icon(
                    if (failedOnly) Icons.Filled.ErrorOutline else Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(if (failedOnly) 24.dp else 18.dp)
                )
            }
        }
    }
}

/**
 * Shown once, after the first download finishes (or on Home for someone who already has
 * downloads): where downloaded music is found. [onShow] opens the filter; either button retires it.
 */
@Composable
fun DownloadsHint(onShow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("downloads_hint")
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Your downloads are ready! Find your downloaded music with the filter button at the top of Home.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.align(Alignment.End)) {
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Got it") }
                androidx.compose.material3.TextButton(onClick = onShow, modifier = Modifier.testTag("downloads_hint_show")) { Text("Show me") }
            }
        }
    }
}
