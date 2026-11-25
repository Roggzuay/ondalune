package io.github.roggzuay.ondalune.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore, in Settings.
 *
 * The card says in as many words what is in the file and what is not, because a backup that looks
 * complete and quietly leaves your servers behind is worse than one that tells you.
 */
@Composable
fun BackupCard(
    /** When the last backup was written, in epoch millis; 0 when none has been. */
    lastBackupMs: Long,
    onWriteBackup: (Uri) -> Unit,
    onReadBackup: (Uri) -> Unit
) {
    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onWriteBackup) }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onReadBackup) }

    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("backup_card")
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "One file holding every setting, your equalizer curves, the sounds you have " +
                    "saved for songs and albums, and the song details you have corrected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Your servers and passwords are not in it. They are encrypted on this phone " +
                    "with a key that cannot leave it, and a backup file can be copied anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (lastBackupMs > 0) Text(
                text = "Last saved ${friendlyDate(lastBackupMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { save.launch(suggestedName()) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("backup_save")
                ) { Text("Save a backup") }
                FilledTonalButton(
                    onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("backup_restore")
                ) { Text("Put one back") }
            }
            Text(
                text = "Putting a backup back adds to what is here and replaces what it matches. " +
                    "Nothing is deleted.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "Ondalune-backup-2026-11-25.json" — the date is what you look for a year later. */
internal fun suggestedName(nowMs: Long = System.currentTimeMillis()): String =
    "Ondalune-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs)) + ".json"

internal fun friendlyDate(ms: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))
