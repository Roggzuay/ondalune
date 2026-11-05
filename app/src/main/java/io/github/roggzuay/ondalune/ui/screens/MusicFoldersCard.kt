package io.github.roggzuay.ondalune.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.LocalFolder
import io.github.roggzuay.ondalune.data.source.SupportedAudio
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel.FolderScan

/** What Settings shows about the picked music folders, and what it can do with them. */
class MusicFolders(
    val folders: List<LocalFolder> = emptyList(),
    val scan: FolderScan? = null,
    val rescanOnLaunch: Boolean = true,
    val onAdd: (Uri) -> Unit = {},
    val onRemove: (LocalFolder) -> Unit = {},
    val onRescan: (LocalFolder) -> Unit = {},
    val onRescanAll: () -> Unit = {},
    val onCancelScan: () -> Unit = {},
    val onSetRescanOnLaunch: (Boolean) -> Unit = {}
)

/**
 * "Music folders": the folders on this phone whose music joins the library. Each is picked in
 * Android's folder picker, which gives Ondalune lasting read access to that folder only; nothing
 * else on the phone is read, and no storage permission is asked for.
 */
@Composable
fun MusicFoldersCard(state: MusicFolders, modifier: Modifier = Modifier) {
    var pendingRemove by remember { mutableStateOf<LocalFolder?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(state.onAdd) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag("music_folders_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Music folders on this phone", style = MaterialTheme.typography.titleSmall)
            Text(
                "Only the folders you add here join your library, and they play offline. " +
                    "Ringtones, recordings and other sounds elsewhere stay out. Formats: ${SupportedAudio.DESCRIPTION}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    scanning = state.scan?.folderName == folder.name,
                    onAllowAgain = { pick.launch(Uri.parse(folder.treeUri)) },
                    onRescan = { state.onRescan(folder) },
                    onRemove = { pendingRemove = folder }
                )
            }
            state.scan?.let { scan ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (scan.read > 0) "Reading ${scan.folderName}: ${scan.read} of ${scan.found} new or changed songs"
                            else "Looking in ${scan.folderName}: ${scan.found} songs found",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.size(4.dp))
                        if (scan.read > 0 && scan.found > 0) LinearProgressIndicator(progress = { scan.read / scan.found.toFloat() }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = state.onCancelScan, modifier = Modifier.testTag("music_folders_stop")) { Text("Stop") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { pick.launch(null) }, modifier = Modifier.testTag("music_folders_add")) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add music folder")
                }
                if (state.folders.size > 1 && state.scan == null) {
                    TextButton(onClick = state.onRescanAll) { Text("Scan all") }
                }
            }
            if (state.folders.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Look for changes when Ondalune opens",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.rescanOnLaunch, onCheckedChange = state.onSetRescanOnLaunch)
                }
            }
        }
    }

    pendingRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove \"${folder.name}\"?") },
            text = { Text("Its songs leave your library. The files stay on your phone.") },
            confirmButton = { TextButton(onClick = { state.onRemove(folder); pendingRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FolderRow(folder: LocalFolder, scanning: Boolean, onAllowAgain: () -> Unit, onRescan: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("music_folder_row")) {
        Icon(
            if (folder.accessible) Icons.Filled.Folder else Icons.Filled.FolderOff,
            contentDescription = null,
            tint = if (folder.accessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !folder.accessible -> "Can't be opened. Its songs stay until it can"
                    scanning -> "Scanning…"
                    folder.lastScanMs == 0L -> "Not scanned yet"
                    else -> "${folder.trackCount} songs"
                } + " • ${folder.location}",
                style = MaterialTheme.typography.bodySmall,
                color = if (folder.accessible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!folder.accessible) TextButton(onClick = onAllowAgain) { Text("Allow again") }
        else IconButton(onClick = onRescan, enabled = !scanning) {
            Icon(Icons.Filled.Refresh, contentDescription = "Scan ${folder.name} again")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove ${folder.name}")
        }
    }
}
