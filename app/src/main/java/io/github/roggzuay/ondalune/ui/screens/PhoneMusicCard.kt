package io.github.roggzuay.ondalune.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.source.LocalMusicSource

/** What Settings shows about this phone's music. */
data class PhoneMusicState(val enabled: Boolean = false, val songCount: Int = 0, val scanning: Boolean = false)

/**
 * "Music on this phone": a switch that adds the songs stored on the phone to the library. Turning
 * it on asks for the "Music and audio" permission first; if Android won't ask again, it offers the
 * app's system settings instead.
 */
@Composable
fun PhoneMusicCard(
    state: PhoneMusicState,
    onSetEnabled: (Boolean) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) onSetEnabled(true)
    }
    val permitted = LocalMusicSource.hasPermission(context)
    fun toggle(on: Boolean) {
        when {
            !on -> onSetEnabled(false)
            permitted -> onSetEnabled(true)
            else -> askPermission.launch(LocalMusicSource.permission)
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (state.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag("phone_music_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { toggle(!state.enabled) }
            ) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Music on this phone", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            state.scanning -> "Looking for music…"
                            state.enabled -> "${state.songCount} songs, in your library with your server's"
                            else -> "Play songs stored on this phone alongside your server's, even offline"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.scanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Switch(checked = state.enabled, onCheckedChange = ::toggle, modifier = Modifier.testTag("phone_music_switch"))
            }
            when {
                state.enabled && !permitted -> PermissionHint(
                    "Ondalune can no longer read your music. Allow \"Music and audio\" to play these songs.",
                    "Allow"
                ) { askPermission.launch(LocalMusicSource.permission) }
                denied -> PermissionHint(
                    "Allow \"Music and audio\" for Ondalune in Android's settings to add your music.",
                    "Open settings"
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                state.enabled -> TextButton(onClick = onRescan, enabled = !state.scanning) { Text("Look for new music") }
            }
        }
    }
}

@Composable
private fun PermissionHint(text: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}
