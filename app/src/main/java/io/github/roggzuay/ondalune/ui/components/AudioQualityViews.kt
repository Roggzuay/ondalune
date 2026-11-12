package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roggzuay.ondalune.data.model.AudioQuality

/** The compact quality line under the seek bar: "FLAC · 24-bit / 96 kHz". Tap for details. */
@Composable
fun AudioQualityChip(quality: AudioQuality, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = quality.label
    val spoken = listOfNotNull(quality.label, quality.tag).joinToString(", ")
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = modifier
            .testTag("audio_quality_chip")
            .semantics {
                role = Role.Button
                contentDescription = "Audio quality: $spoken. Show details"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The details behind the quality line: the source file, what Ondalune opened, how it goes out,
 * what changes the sound, spatial audio, and the phone's Dolby decoders. Each part says only what
 * Android or the source reported.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetailsSheet(quality: AudioQuality, spatialAudio: Boolean?, dolbyDecoders: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .testTag("audio_details_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Audio details", style = MaterialTheme.typography.headlineSmall)
            Section("The song's file", quality.source)
            Section("What plays", quality.stream)
            Section("How it goes out", quality.output)
            Section(
                "What changes the sound",
                if (quality.processing.isEmpty()) listOf("" to "Nothing: no speed, pitch, reverb, EQ or levelling is on")
                else quality.processing.map { "" to it }
            )
            Section(
                "Spatial audio",
                listOf(
                    "" to when (spatialAudio) {
                        true -> "On: Android is spatialising Ondalune's audio"
                        false -> "Off for Ondalune's audio"
                        null -> "Not available on this phone or output"
                    }
                )
            )
            Section(
                "Dolby",
                listOf(
                    "" to if (dolbyDecoders.isEmpty()) "No Dolby decoders on this phone" else "This phone can decode ${dolbyDecoders.joinToString(", ")}",
                    "" to "Ondalune plays every song through its own stereo engine, so Dolby Atmos and Dolby processing are not used, whatever the phone supports"
                )
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(title: String, rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        rows.forEach { (name, value) ->
            Row {
                if (name.isNotBlank()) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                }
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
            }
        }
    }
}
