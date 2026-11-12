package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.lyrics.LyricsQuality
import io.github.roggzuay.ondalune.data.lyrics.LyricsSearch
import io.github.roggzuay.ondalune.data.lyrics.LyricsSource
import io.github.roggzuay.ondalune.data.lyrics.RankedCandidate
import io.github.roggzuay.ondalune.data.lyrics.checkLines
import io.github.roggzuay.ondalune.data.lyrics.checkLrc
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlin.math.roundToInt

/**
 * Where the playing song's lyrics come from, and the way to change that: search LRCLIB, look at a
 * record's first lines and any timing problems before using it, go back to the server's lyrics, or
 * undo the last change. Only the title and artist typed here are sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSourceSheet(
    track: SubsonicTrack,
    source: LyricsSource,
    search: LyricsSearch,
    onSearch: (title: String, artist: String) -> Unit,
    onChoose: (RankedCandidate) -> Unit,
    onUseServer: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(track.id) { mutableStateOf(search.title.ifEmpty { track.title }) }
    var artist by rememberSaveable(track.id) { mutableStateOf(search.artist.ifEmpty { track.artist }) }
    var open by remember(search.results) { mutableLongStateOf(search.results.firstOrNull()?.candidate?.id ?: -1L) }
    // Searching straight away saves a tap when nothing good was found on the way in.
    LaunchedEffect(track.id) {
        if (!search.searched && !search.searching && (source.offers > 0 || source.serverQuality == LyricsQuality.NONE)) onSearch(title, artist)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().testTag("lyrics_source_sheet"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Lyrics", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Now: ${source.detail.ifEmpty { "none" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!source.fromServer || source.canRestore) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!source.fromServer) OutlinedButton(onClick = onUseServer, modifier = Modifier.testTag("lyrics_use_server")) {
                        Text(if (source.serverQuality == LyricsQuality.NONE) "Remove these lyrics" else "Use your server's lyrics")
                    }
                    if (source.canRestore) TextButton(onClick = onRestore, modifier = Modifier.testTag("lyrics_restore")) {
                        Text("Undo last change")
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_search_title")
                    )
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(title, artist) }),
                        trailingIcon = {
                            IconButton(onClick = { onSearch(title, artist) }, enabled = title.isNotBlank() && !search.searching) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search LRCLIB")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_search_artist")
                    )
                    Text(
                        text = "Searches send the title and artist to lrclib.net, never the audio. Lyrics you pick are kept on this phone; your server's files aren't changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                search.searching -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Searching LRCLIB…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                search.error != null -> item {
                    Text(search.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                search.searched && search.results.isEmpty() -> item {
                    Text("Nothing on LRCLIB for that. Try fewer words, or the title as written on the album.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(search.results, key = { it.candidate.id }) { ranked ->
                CandidateRow(
                    ranked = ranked,
                    songDurationSec = track.durationSec,
                    expanded = open == ranked.candidate.id,
                    onToggle = { open = if (open == ranked.candidate.id) -1L else ranked.candidate.id },
                    onUse = { onChoose(ranked); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun CandidateRow(ranked: RankedCandidate, songDurationSec: Int, expanded: Boolean, onToggle: () -> Unit, onUse: () -> Unit) {
    val c = ranked.candidate
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("lyrics_candidate_${c.id}")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(c.artist, c.album, clock(c.durationSec)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(if (c.instrumental && c.quality == LyricsQuality.NONE) "Instrumental" else c.quality.label, strong = c.quality >= LyricsQuality.SYNCED)
                Badge("${(ranked.score * 100).roundToInt()}% match", strong = ranked.confident)
                ranked.durationOffSec?.takeIf { it > 2 }?.let { off ->
                    Badge(if (c.durationSec > songDurationSec) "${off.roundToInt()} s longer" else "${off.roundToInt()} s shorter", strong = false)
                }
                if (ranked.versionMismatch) Badge("Different version", strong = false)
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val lines = remember(c.id) { c.lines() }
                val preview = lines.map { it.text }.ifEmpty { c.plain.lines().filter { it.isNotBlank() } }.take(6)
                preview.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (preview.isEmpty()) Text("No words: LRCLIB marks this song instrumental.", style = MaterialTheme.typography.bodyMedium)
                val issues = remember(c.id) {
                    if (c.quality == LyricsQuality.WORDS) checkLines(lines, songDurationSec * 1000L)
                    else checkLrc(c.synced, songDurationSec * 1000L, c.durationSec)
                }
                issues.take(3).forEach {
                    Text("⚠ ${it.detail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (c.quality != LyricsQuality.NONE) Button(onClick = onUse, modifier = Modifier.testTag("lyrics_use_candidate")) {
                    Text("Use these lyrics")
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, strong: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (strong) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (strong) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun clock(seconds: Double): String =
    if (seconds <= 0) "" else (seconds.roundToInt()).let { "%d:%02d".format(it / 60, it % 60) }
