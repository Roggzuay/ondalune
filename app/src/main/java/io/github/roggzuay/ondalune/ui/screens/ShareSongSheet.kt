package io.github.roggzuay.ondalune.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.components.NowPlayingCard
import io.github.roggzuay.ondalune.ui.components.writeShareableCard
import io.github.roggzuay.ondalune.ui.theme.ArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share what is playing, as a picture in Ondalune's own colours.
 *
 * The card is the real thing, drawn by the same code that draws the player, and captured as it
 * appears here — so what is shared is what was on the preview, not an approximation of it.
 *
 * Nothing leaves the phone by itself. The picture is written into the app's cache and handed to
 * Android's share sheet; where it goes from there is whatever you pick.
 */
@Composable
fun ShareSongSheet(
    track: SubsonicTrack,
    palette: ArtworkPalette,
    /** The line that was showing in the lyrics, when they were up. */
    lyricLine: String,
    onClose: () -> Unit,
    onFailed: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var includeLyric by remember { mutableStateOf(lyricLine.isNotBlank()) }
    var busy by remember { mutableStateOf(false) }

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "share_song_sheet",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Share this song", style = MaterialTheme.typography.headlineSmall)

            // Recorded as it is drawn, so the picture and the preview can never disagree.
            NowPlayingCard(
                track = track,
                palette = palette,
                lyricLine = if (includeLyric) lyricLine else "",
                modifier = Modifier
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .testTag("share_card_preview")
            )

            if (lyricLine.isNotBlank()) Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Include the line that was playing", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = lyricLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = includeLyric,
                    onCheckedChange = { includeLyric = it },
                    modifier = Modifier.testTag("share_include_lyric")
                )
            }

            Text(
                text = "The picture is made on this phone and handed to Android's share sheet. " +
                    "Ondalune sends it nowhere by itself.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        scope.launch {
                            val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
                            // Compressing a PNG and clearing a folder belong off the thread that draws.
                            val uri = bitmap?.let {
                                withContext(Dispatchers.IO) {
                                    writeShareableCard(context, it, "${track.artist} - ${track.title}")
                                }
                            }
                            busy = false
                            if (uri == null) {
                                onFailed("Couldn't make the picture")
                                return@launch
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share this song"))
                            }.onFailure { onFailed("Nothing on this phone can share a picture") }
                            onClose()
                        }
                    },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("share_song_confirm")
                ) { Text(if (busy) "Making it…" else "Share") }
                TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text("Not now") }
            }
        }
    }
}
