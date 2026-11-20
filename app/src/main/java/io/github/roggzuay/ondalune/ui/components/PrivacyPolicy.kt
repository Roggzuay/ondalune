package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.BuildConfig

const val PrivacyPolicyUrl = "$SourceCodeUrl/blob/main/PRIVACY.md"

private class PolicySection(val title: String, val paragraphs: List<String>)

/**
 * The privacy policy as this build behaves: the GitHub release mentions its update check and
 * installer, store builds say the store delivers updates. Keep in step with PRIVACY.md.
 */
private fun policySections(): List<PolicySection> = listOfNotNull(
    PolicySection(
        "In short",
        listOf(
            "There is no Ondalune account and no Ondalune server. The developer receives none of your data.",
            "No ads, analytics, crash reporting or trackers.",
            "The app only connects to the music servers you add and to the public services below, each for one purpose."
        )
    ),
    PolicySection(
        "What stays on your phone",
        listOf(
            "Your servers and logins. Passwords are encrypted with a key in the Android Keystore; for Jellyfin only " +
                "the sign-in token is kept, encrypted the same way.",
            "Your library cache, favourites, play counts, queue, downloads, chosen lyrics, song details you edit and settings.",
            "Phone folders you add are read through Android's folder picker; no storage permission is asked. " +
                "Songs there are played where they are, never copied, changed or uploaded.",
            "Song analysis, only if you turn it on, keeps tempo and energy estimates on the phone; turning it off deletes them.",
            "Uninstalling the app deletes all of it. If Android backup is on, your settings can be included; " +
                "servers, the library database and downloads never are."
        )
    ),
    PolicySection(
        "Who the app talks to",
        listOfNotNull(
            "Your music servers: your login and the requests needed to browse, stream, download and sync. With " +
                "\"Report plays to your server\" on, the songs you play, for your server's play counts.",
            "Image hosts your server links to, such as Last.fm or Wikimedia for artist photos: an ordinary image request.",
            "LRCLIB (lrclib.net): a song's title, artist, album and length, when your server has no lyrics and " +
                "\"Find missing lyrics online\" is on, or when you search for lyrics. Never the audio.",
            "Wikipedia: an artist's name, when there is no biography for them yet.",
            if (BuildConfig.SELF_UPDATE) {
                "GitHub: once a day to check for a new version (you can turn this off), and to download an update you choose."
            } else null,
            if (BuildConfig.PLAY_STORE) "Google Play installs and updates the app, under Google's privacy policy." else null,
            "Each service sees your IP address, as any website does, and handles it under its own privacy policy. " +
                "Links you tap, like Ko-fi, open in your browser."
        )
    ),
    PolicySection(
        "Servers without HTTPS",
        listOf(
            "Allowed, because many home servers have no certificate. Anything sent to one, including your password, " +
                "can be read by others on the same network; the app says so when you enter one."
        )
    ),
    PolicySection(
        "Permissions",
        listOfNotNull(
            "Internet: your servers and the services above. Notifications: playback controls and download progress. " +
                "Media playback service and wake lock: music with the screen off. Vibrate: haptic feedback.",
            if (!BuildConfig.PLAY_STORE) "Battery optimisation: asked only if you agree, so battery savers don't stop music." else null,
            if (BuildConfig.SELF_UPDATE) {
                "Install and delete apps: to install an update you downloaded, or go back a version, each time " +
                    "with your confirmation in Android's installer."
            } else null
        )
    ),
    PolicySection(
        "Children, changes and contact",
        listOf(
            "Ondalune is not directed at children and collects no personal data from anyone.",
            "Changes are published, dated, in PRIVACY.md in the source repository. Questions: open an issue on GitHub."
        )
    )
)

/** Settings row that opens the privacy policy. */
@Composable
fun PrivacyPolicyRow(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SpringPressableSurface(
        onClick = { open = true },
        color = MaterialTheme.colorScheme.surfaceContainer,
        cornerRadius = 22.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("privacy_policy_button")
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No ads, analytics or trackers · what the app sends and where",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (open) PrivacySheet(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val sections = remember { policySections() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            item {
                Text("Privacy policy", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Last updated 21 November 2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sections) { section ->
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
                )
                section.paragraphs.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            item {
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(PrivacyPolicyUrl) } },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Read it online")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
