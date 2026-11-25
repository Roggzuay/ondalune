package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.viewmodel.ListenBrainzState

/**
 * ListenBrainz, in Settings: an open record of what you listen to that you can take with you.
 *
 * It says exactly what leaves the phone, because this is the only place other than your own
 * servers that Ondalune ever tells what is playing.
 */
@Composable
fun ListenBrainzCard(
    state: ListenBrainzState,
    onSignIn: (String) -> Unit,
    onSignOut: () -> Unit,
    onSetEnabled: (Boolean) -> Unit
) {
    var token by remember { mutableStateOf("") }

    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("listenbrainz_card")
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("ListenBrainz", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.hasToken) {
                            "Signed in as ${state.userName}"
                        } else {
                            "An open record of what you listen to, run by MetaBrainz"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.hasToken) Switch(
                    checked = state.enabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag("toggle_listenbrainz")
                )
            }

            if (!state.hasToken) {
                Text(
                    text = "Ondalune sends the song's title, artist, album and length, once a play " +
                        "already counts as a play here. Nothing about you goes with it beyond the " +
                        "token that says which account it is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Your ListenBrainz token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("listenbrainz_token")
                )
                Text(
                    text = "listenbrainz.org → Settings, where the token is shown. It is kept " +
                        "encrypted on this phone, like your server passwords.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        onSignIn(token)
                        token = ""
                    },
                    enabled = token.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("listenbrainz_sign_in")
                ) { Text("Sign in") }
            } else {
                if (state.queued > 0) Text(
                    text = if (state.queued == 1) {
                        "1 listen is waiting to be sent"
                    } else {
                        "${state.queued} listens are waiting to be sent"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = onSignOut,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("listenbrainz_sign_out")
                ) { Text("Sign out") }
            }
        }
    }
}
