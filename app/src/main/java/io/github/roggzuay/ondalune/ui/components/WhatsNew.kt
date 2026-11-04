package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import io.github.roggzuay.ondalune.BuildConfig
import io.github.roggzuay.ondalune.data.local.UpdateInstallState
import io.github.roggzuay.ondalune.data.network.AppUpdate
import io.github.roggzuay.ondalune.data.network.UpdateCheckStatus
import io.github.roggzuay.ondalune.ui.theme.LocalCornerScale
import java.io.File
import kotlin.math.roundToInt

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
    ),
    "1.2" to listOf(
        "Ondalune now tells you on Home when a new version is out",
        "Update from inside the app: the notes, download progress and install in one place",
        "Settings → Updates: check any time, or turn the daily check off"
    ),
    "1.3" to listOf(
        "Downloads page: what's downloading, waiting, failed or saved. Open it from the filter button",
        "Downloads retry on their own and carry on after you close the app",
        "Download the playing song from the player; a tick shows it's on your phone",
        "Fresher shuffle: fewer repeats of the same favourites",
        "Fixes & improvements, including two songs playing at once"
    ),
    "1.4" to listOf(
        "Artist pages: a full-size portrait, and the app takes on its colours",
        "Mixes for you on Home, made from your genres, artists and favourites",
        "Smarter autoplay when your queue ends; turn it off in Settings → Playback",
        "Optional song analysis for Upbeat and Calm mixes (off until you turn it on)",
        "Downloads: the cloud becomes a progress ring, with pause and resume",
        "Fixes & improvements, including Home sections that wouldn't switch off"
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
        text = { NoteBullets(notes.items) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

/** A quiet card at the top of Home while a newer release is out. */
@Composable
fun UpdateBanner(update: AppUpdate, onView: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().testTag("update_banner")
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                "Ondalune ${update.version} is available",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onLater) { Text("Later") }
            FilledTonalButton(onClick = onView) { Text("View") }
        }
    }
}

/**
 * The release's notes, then the in-app update below them: download progress, the one-time
 * "allow installs" step, or what went wrong. Without [inAppInstall] (preview and debug builds,
 * which a release can't update), Download opens the release page instead.
 */
@Composable
fun UpdateDialog(
    update: AppUpdate,
    install: UpdateInstallState,
    inAppInstall: Boolean,
    onUpdate: () -> Unit,
    onAllowInstalls: () -> Unit,
    onInstall: (File) -> Unit,
    onCancel: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_dialog"),
        title = { Text("Ondalune ${update.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NoteBullets(
                    update.notes.ifEmpty { listOf("Fixes & improvements") },
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                )
                when (install) {
                    UpdateInstallState.Idle -> Unit
                    is UpdateInstallState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(progress = { install.progress }, modifier = Modifier.fillMaxWidth())
                        StatusText("Downloading… ${(install.progress * 100).roundToInt()}%")
                    }
                    is UpdateInstallState.NeedsPermission ->
                        StatusText("To install it, allow Ondalune to install apps. You'll come straight back here.")
                    is UpdateInstallState.Ready ->
                        StatusText("Android will ask you to confirm. Ondalune closes while it updates; open it again afterwards.")
                    is UpdateInstallState.Failed -> StatusText(install.message, MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (install) {
                UpdateInstallState.Idle ->
                    if (inAppInstall) TextButton(onClick = onUpdate) { Text("Update") }
                    else TextButton(onClick = onOpenReleasePage) { Text("Download") }
                is UpdateInstallState.Downloading -> TextButton(onClick = onCancel) { Text("Cancel") }
                is UpdateInstallState.NeedsPermission -> TextButton(onClick = onAllowInstalls) { Text("Open settings") }
                is UpdateInstallState.Ready -> TextButton(onClick = { onInstall(install.apk) }) { Text("Install") }
                is UpdateInstallState.Failed -> TextButton(onClick = onUpdate) { Text("Try again") }
            }
        },
        dismissButton = {
            when (install) {
                is UpdateInstallState.Downloading -> Unit
                is UpdateInstallState.NeedsPermission -> TextButton(onClick = onCancel) { Text("Cancel") }
                is UpdateInstallState.Failed -> TextButton(onClick = onOpenReleasePage) { Text("Release page") }
                else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

/** Settings card above About: the installed version, Check now, and the daily check switch. */
@Composable
fun UpdatesCard(
    available: AppUpdate?,
    status: UpdateCheckStatus,
    autoCheck: Boolean,
    onCheckNow: () -> Unit,
    onView: () -> Unit,
    onSetAutoCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape((22.dp * LocalCornerScale.current).coerceAtMost(40.dp)),
        modifier = modifier.fillMaxWidth().testTag("updates_card")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Updates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            available != null -> "Ondalune ${available.version} is available"
                            status == UpdateCheckStatus.CHECKING -> "Checking…"
                            status == UpdateCheckStatus.UP_TO_DATE -> "Version ${BuildConfig.VERSION_NAME} · up to date"
                            status == UpdateCheckStatus.FAILED -> "Couldn't reach GitHub. Try again later"
                            else -> "Version ${BuildConfig.VERSION_NAME}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    available != null -> FilledTonalButton(onClick = onView) { Text("View") }
                    status == UpdateCheckStatus.CHECKING ->
                        CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(24.dp), strokeWidth = 3.dp)
                    else -> FilledTonalButton(onClick = onCheckNow, modifier = Modifier.testTag("check_updates_now")) {
                        Text("Check now")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Check automatically", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Once a day, asks GitHub for a newer version and lets you know on Home",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCheck,
                    onCheckedChange = onSetAutoCheck,
                    modifier = Modifier.testTag("toggle_check_updates")
                )
            }
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
private fun NoteBullets(items: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
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
}
