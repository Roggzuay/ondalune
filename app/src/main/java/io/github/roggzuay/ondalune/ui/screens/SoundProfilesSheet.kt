package io.github.roggzuay.ondalune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.PlayedSound
import io.github.roggzuay.ondalune.data.model.SavedSound
import io.github.roggzuay.ondalune.ui.components.ExpressiveArtwork
import io.github.roggzuay.ondalune.ui.components.ExpressiveBounceIconButton
import io.github.roggzuay.ondalune.ui.components.SpringPressableSurface
import io.github.roggzuay.ondalune.ui.theme.cornerShape

/**
 * Sound Profiles: every sound you have saved, and every play Ondalune has written down with the
 * speed, pitch or reverb that was on at the time.
 *
 * A play is kept whether or not a profile was ever saved for it, so the version you liked once and
 * never saved is still one tap away.
 */
@Composable
fun SoundProfilesSheet(
    saved: List<SavedSound>,
    plays: List<PlayedSound>,
    autoApply: Boolean,
    keepHistory: Boolean,
    onSetAutoApply: (Boolean) -> Unit,
    onSetKeepHistory: (Boolean) -> Unit,
    onPlaySaved: (SavedSound) -> Unit,
    onForget: (SavedSound) -> Unit,
    onPlaySound: (PlayedSound) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "sound_profiles_sheet",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Sound Profiles", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Songs the way you like to hear them",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ExpressiveBounceIconButton(
                onClick = onClose,
                size = 48.dp,
                modifier = Modifier.testTag("close_sound_profiles")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    shape = cornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SoundProfileSwitchRow(
                            title = "Put saved sounds on by themselves",
                            subtitle = "Off, the player asks above the song title and nothing changes until you tap it",
                            checked = autoApply,
                            onCheckedChange = onSetAutoApply,
                            testTag = "toggle_auto_sound_profiles"
                        )
                        SoundProfileSwitchRow(
                            title = "Keep a record of how songs played",
                            subtitle = "What fills this list and the card on Home. Nothing leaves your phone.",
                            checked = keepHistory,
                            onCheckedChange = onSetKeepHistory,
                            testTag = "toggle_keep_sound_history"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Saved sounds",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (saved.isEmpty()) item {
                EmptySoundNote("Change the speed, pitch or reverb of a song and Ondalune offers to save it here.")
            }
            items(saved.size, key = { "saved_${saved[it].profile.scope}_${saved[it].profile.scopeId}" }) { index ->
                val row = saved[index]
                SoundRow(
                    title = row.title,
                    subtitle = if (row.isAlbum) "Whole album · ${row.subtitle}" else row.subtitle,
                    detail = row.snapshot.summary,
                    coverArtUrl = row.coverArtUrl,
                    seedHex = row.seedHex,
                    accentHex = row.accentHex,
                    artStyleIndex = row.artStyleIndex,
                    onClick = { onPlaySaved(row) },
                    testTag = "saved_sound_${row.profile.scopeId}",
                    trailing = {
                        ExpressiveBounceIconButton(
                            onClick = { onForget(row) },
                            size = 42.dp,
                            modifier = Modifier.testTag("forget_sound_${row.profile.scopeId}")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Forget this sound",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Played with changes", style = MaterialTheme.typography.titleMedium)
                    if (plays.isNotEmpty()) TextButton(
                        onClick = onClearHistory,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.testTag("clear_sound_history")
                    ) { Text("Clear") }
                }
            }
            if (plays.isEmpty()) item {
                EmptySoundNote("Play something with the speed, pitch or reverb changed and it shows up here.")
            }
            items(plays.size, key = { "play_${plays[it].track.id}_${plays[it].playedAt}" }) { index ->
                val play = plays[index]
                SoundRow(
                    title = play.track.title,
                    subtitle = play.track.artist,
                    detail = play.snapshot.summary,
                    coverArtUrl = play.track.coverArtUrl,
                    seedHex = play.track.paletteSeedHex,
                    accentHex = play.track.accentHex,
                    artStyleIndex = play.track.artStyleIndex,
                    onClick = { onPlaySound(play) },
                    testTag = "played_sound_${play.track.id}",
                    trailing = null
                )
            }
        }
    }
}

@Composable
private fun SoundProfileSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun EmptySoundNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun SoundRow(
    title: String,
    subtitle: String,
    detail: String,
    coverArtUrl: String,
    seedHex: String,
    accentHex: String,
    artStyleIndex: Int,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)?
) {
    SpringPressableSurface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ExpressiveArtwork(
                    seedHex = seedHex,
                    accentHex = accentHex,
                    artStyleIndex = artStyleIndex,
                    coverArtUrl = coverArtUrl,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}
