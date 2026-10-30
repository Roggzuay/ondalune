package io.github.roggzuay.ondalune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roggzuay.ondalune.BuildConfig

const val SourceCodeUrl = "https://github.com/Roggzuay/ondalune"

/** A bundled third-party component and the licence file (in assets/licenses) that covers it. */
private class Component(val name: String, val detail: String, val licence: String, val file: String)

private val Components = listOf(
    Component("AndroidX & Jetpack Compose", "Activity, Core, Lifecycle, Room, Compose UI, Material 3 and icons", "Apache-2.0", "Apache-2.0.txt"),
    Component("Kotlin & kotlinx.coroutines", "JetBrains", "Apache-2.0", "Apache-2.0.txt"),
    Component("Compose Multiplatform runtime", "JetBrains (used by MaterialKolor)", "Apache-2.0", "Apache-2.0.txt"),
    Component("OkHttp & Okio", "Square", "Apache-2.0", "Apache-2.0.txt"),
    Component("Coil", "Coil Contributors", "Apache-2.0", "Apache-2.0.txt"),
    Component("Haze", "Chris Banes", "Apache-2.0", "Apache-2.0.txt"),
    Component("Accompanist Drawable Painter", "Google", "Apache-2.0", "Apache-2.0.txt"),
    Component("Guava ListenableFuture", "Google", "Apache-2.0", "Apache-2.0.txt"),
    Component("JSpecify & JetBrains annotations", "JSpecify / JetBrains", "Apache-2.0", "Apache-2.0.txt"),
    Component("Poko", "Drew Hamilton", "Apache-2.0", "Apache-2.0.txt"),
    Component("MaterialKolor", "Copyright (c) Jordon de Hoog", "MIT", "MIT-MaterialKolor.txt"),
    Component("Colormath", "Copyright AJ Alt", "MIT", "MIT-colormath.txt"),
    Component(
        "Fonts",
        "Bricolage Grotesque, Figtree, Fraunces, Fredoka, JetBrains Mono, Roboto Flex, Space Grotesk, Syne, Unbounded",
        "OFL-1.1",
        "OFL-1.1.txt"
    )
)

/** Settings row that opens the About & licences sheet. */
@Composable
fun AboutLicencesRow(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SpringPressableSurface(
        onClick = { open = true },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        cornerRadius = 22.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("about_licences_button")
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("About & licences", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · open source under GPL-3.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (open) AboutSheet(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var shownFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            item {
                Text("Ondalune", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    "Ondalune is free software: you can redistribute it and/or modify it under the terms of " +
                        "the GNU General Public License, version 3 or later. It comes with no warranty.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Privacy: Ondalune talks only to the music servers you add, to Wikipedia for artist " +
                        "bios, and to Ko-fi if you open the support link. It has no ads, analytics or trackers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Subsonic, OpenSubsonic and Navidrome are trademarks of their owners. Ondalune is an " +
                        "independent client and is not affiliated with them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(SourceCodeUrl) } }) {
                        Text("Source code")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    TextButton(
                        onClick = { shownFile = "GNU GPL v3" to "GPL-3.0.txt" },
                        modifier = Modifier.testTag("about_view_gpl")
                    ) { Text("Read the licence") }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Open-source licences", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ondalune is built with these projects. Tap one to read its licence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(Components) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { shownFile = c.name to c.file }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.licence, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    shownFile?.let { (title, file) -> LicenceTextDialog(title, file) { shownFile = null } }
}

@Composable
private fun LicenceTextDialog(title: String, file: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(file) {
        runCatching { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
            .getOrDefault("Licence text not found.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
