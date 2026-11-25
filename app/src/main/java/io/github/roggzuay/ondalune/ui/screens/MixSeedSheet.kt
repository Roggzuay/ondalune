package io.github.roggzuay.ondalune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.MixSeed
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import kotlin.math.roundToInt

/**
 * Make a mix to order.
 *
 * Your Mix already knows how to weigh taste and spread a mix out; this puts the dials in front of
 * it, so you can ask for what you actually want right now instead of the recipe the app picked.
 * Everything in the middle is Your Mix, which is said on the button rather than left to be
 * discovered.
 */
@Composable
fun MixSeedSheet(
    /** The decades the library actually has songs from, so the era chips are never empty. */
    decades: List<Int>,
    genres: List<String>,
    /** On-device song analysis is off, so the energy dial has nothing measured to go on. */
    energyUnavailable: Boolean,
    onPlay: (MixSeed) -> Unit,
    onEnableSongAnalysis: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    var energy by remember { mutableStateOf(0.5f) }
    var familiarity by remember { mutableStateOf(0.5f) }
    var decade by remember { mutableStateOf<Int?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }

    val seed = MixSeed(
        energy = energy,
        familiarity = familiarity,
        year = decade?.plus(5),
        genre = genre
    )

    SwipeDownDismissModalContainer(
        onClose = onClose,
        testTag = "mix_seed_sheet",
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text("Make a mix", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Everything in the middle is Your Mix. Move a dial to lean it somewhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Dial(
                title = "Energy",
                left = "Still",
                right = "Driving",
                value = energy,
                onChange = { energy = it },
                tag = "mix_energy",
                note = if (energyUnavailable) {
                    "This needs the on-device song analysis to have listened to your music."
                } else {
                    null
                },
                onNoteAction = onEnableSongAnalysis.takeIf { energyUnavailable },
                noteAction = "Turn it on"
            )

            Dial(
                title = "Familiarity",
                left = "New to you",
                right = "The ones you always play",
                value = familiarity,
                onChange = { familiarity = it },
                tag = "mix_familiarity"
            )

            if (decades.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Era", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(decades) { from ->
                        FilterChip(
                            selected = decade == from,
                            onClick = { decade = if (decade == from) null else from },
                            label = { Text("${from}s") },
                            modifier = Modifier.testTag("mix_decade_$from")
                        )
                    }
                }
            }

            if (genres.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Genre", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { name ->
                        FilterChip(
                            selected = genre == name,
                            onClick = { genre = if (genre == name) null else name },
                            label = { Text(name) },
                            modifier = Modifier.testTag("mix_genre_$name")
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onPlay(seed) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("mix_play")
                ) { Text("Play “${seed.title}”") }
                TextButton(
                    onClick = {
                        energy = 0.5f
                        familiarity = 0.5f
                        decade = null
                        genre = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Reset") }
            }
        }
    }
}

@Composable
private fun Dial(
    title: String,
    left: String,
    right: String,
    value: Float,
    onChange: (Float) -> Unit,
    tag: String,
    note: String? = null,
    onNoteAction: (() -> Unit)? = null,
    noteAction: String = ""
) {
    Surface(
        shape = cornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when {
                        value >= 0.75f -> right
                        value <= 0.25f -> left
                        else -> "No opinion"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = { onChange((it * 100).roundToInt() / 100f) },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().testTag(tag)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(left, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(right, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onNoteAction != null) TextButton(
                    onClick = onNoteAction,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.testTag("${tag}_enable")
                ) { Text(noteAction) }
            }
        }
    }
}
