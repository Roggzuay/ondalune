package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** What a major or minor release (1.1, 2.0) tells people the first time they open it. */
data class ReleaseNotes(val version: String, val items: List<String>)

/**
 * Notes per major.minor version. Patch releases (1.1.1) have none and never interrupt anyone.
 * Written for people, not for the changelog: features by name, everything else as fixes &
 * improvements.
 */
private val releaseNotes = mapOf(
    "1.1" to listOf(
        "A calmer look: colour now comes from your music, not the buttons",
        "Hold the speed button in the player to reset speed, pitch and reverb",
        "Hold the tab bar and slide to glide between tabs",
        "Fixes & improvements, including a crash while the library syncs"
    )
)

/** "1.1.0-preview" → "1.1". */
fun majorMinor(versionName: String): String = versionName.split('.', '-').take(2).joinToString(".")

/**
 * The notes to show on this launch, or null. Fresh installs see nothing (onboarding covers them);
 * an update shows its version's notes once, until [lastSeen] records that version.
 */
fun whatsNewToShow(versionName: String, lastSeen: String?, isUpdate: Boolean): ReleaseNotes? {
    val version = majorMinor(versionName)
    if (version == lastSeen || (lastSeen == null && !isUpdate)) return null
    return releaseNotes[version]?.let { ReleaseNotes(version, it) }
}

@Composable
fun WhatsNewDialog(notes: ReleaseNotes, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("whats_new_dialog"),
        title = { Text("What's new in ${notes.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                notes.items.forEach { item ->
                    Row {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
