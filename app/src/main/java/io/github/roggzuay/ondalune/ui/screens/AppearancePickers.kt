package io.github.roggzuay.ondalune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.roggzuay.ondalune.data.model.AppFont
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.roggzuay.ondalune.ui.theme.colors
import io.github.roggzuay.ondalune.ui.theme.ArtShape
import io.github.roggzuay.ondalune.data.model.AppIconPreviewFrameShape
import io.github.roggzuay.ondalune.data.model.AppIconVariant
import io.github.roggzuay.ondalune.ui.components.OndaluneBrandEmblem
import io.github.roggzuay.ondalune.ui.theme.appFontFamily
import io.github.roggzuay.ondalune.ui.theme.artShape

/** A row of "Aa" chips, each set in its own typeface. */
@Composable
fun FontPicker(title: String, selected: AppFont, display: Boolean, onSelect: (AppFont) -> Unit, tag: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(tag)) {
        items(AppFont.entries) { font ->
            val isSelected = font == selected
            val family = remember(font) { appFontFamily(font, display) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSelect(font) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag("${tag}_${font.name.lowercase()}")
            ) {
                Text(
                    text = if (font == AppFont.AUTO) "Auto" else "Aa",
                    style = TextStyle(fontFamily = family, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = font.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * App icon picker: the wave colourways and minimal tiles, previewed in the shape launchers
 * cut them to. "Match theme" keeps the icon in step with the app's colours automatically.
 */
@Composable
fun AppIconSettingsScreen(
    selectedVariant: AppIconVariant,
    onSelectVariant: (AppIconVariant) -> Unit,
    iconFollowsTheme: Boolean = false,
    onSetIconFollowsTheme: (Boolean) -> Unit = {},
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var previewShape by remember { mutableStateOf(AppIconPreviewFrameShape.SQUIRCLE) }
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_icon_settings_screen")
    ) {
        LazyColumn(
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("App icon", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(20.dp)
                ) {
                    OndaluneBrandEmblem(size = 120.dp, variant = selectedVariant, frameShape = previewShape)
                    Spacer(Modifier.height(12.dp))
                    Text(selectedVariant.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        selectedVariant.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIconPreviewFrameShape.entries.forEach { shape ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { previewShape = shape }
                                    .padding(6.dp)
                            ) {
                                OndaluneBrandEmblem(size = 40.dp, variant = selectedVariant, frameShape = shape)
                                Text(
                                    shape.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (shape == previewShape) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        "Your launcher decides the final shape; on Android 13+ themed icons use the note's monochrome layer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSetIconFollowsTheme(!iconFollowsTheme) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Match theme", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Switch to the icon closest to the app's colours whenever the theme changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = iconFollowsTheme, onCheckedChange = onSetIconFollowsTheme, modifier = Modifier.testTag("icon_follow_theme"))
                }
            }
            listOf("Shaded" to AppIconVariant.entries.filter { !it.isMinimal }, "Minimal" to AppIconVariant.entries.filter { it.isMinimal })
                .forEach { (group, variants) ->
                    item {
                        Text(group, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            variants.forEach { v ->
                                val isSelected = v == selectedVariant
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { onSelectVariant(v) }
                                        .padding(vertical = 10.dp)
                                        .testTag("app_icon_variant_${v.name.lowercase()}")
                                ) {
                                    OndaluneBrandEmblem(size = 60.dp, variant = v, frameShape = previewShape)
                                    Spacer(Modifier.height(6.dp))
                                    Text(v.shortLabel, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            repeat(4 - variants.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
            item {
                Text(
                    "Changing the icon can take a few seconds to show on the home screen, and some launchers move the shortcut.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
