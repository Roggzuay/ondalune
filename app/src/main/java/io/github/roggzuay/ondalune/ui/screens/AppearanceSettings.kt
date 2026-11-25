package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.AppColorSource
import io.github.roggzuay.ondalune.data.model.AppFont
import io.github.roggzuay.ondalune.data.model.AppThemeMode
import io.github.roggzuay.ondalune.data.model.CornerStylePreset
import io.github.roggzuay.ondalune.data.model.DockStyle
import androidx.compose.ui.draw.clip
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.ui.components.BackdropPreview
import io.github.roggzuay.ondalune.ui.theme.LocalArtworkPalette
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.ui.components.SpringPressableSurface
import io.github.roggzuay.ondalune.ui.theme.LocalStyleSpec

/**
 * One collapsed row per group of Appearance settings. Only the group being changed is open, so the
 * screen is a short list of what is set rather than a long scroll of every control at once (the
 * Home layout alone is eleven rows). The summary is the setting's current value, so the list still
 * answers "what is this on?" without opening anything.
 *
 * Toggles are deliberately not wrapped in one of these: a switch is already its own answer, and
 * hiding it behind a row would cost a tap to read and a tap to set.
 */
@Composable
fun CompactSettingsGroup(
    title: String,
    summary: String,
    testTag: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "groupChevron")
    val container by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        label = "groupContainer"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(20.dp))
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("${testTag}_header"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

/** A selectable tile: the preview on top, its name under it, a tick when it is the one in use. */
/**
 * What moves behind the player. Every tile is drawn by the backdrop's own code in the playing
 * song's colours, so the choice is made on the thing itself rather than on a description of it.
 */
@Composable
private fun PlayerBackgroundTiles(
    selected: PlayerBackgroundAnimationStyle,
    onSelect: (PlayerBackgroundAnimationStyle) -> Unit
) {
    val palette = LocalArtworkPalette.current
    val styles = PlayerBackgroundAnimationStyle.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        styles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { style ->
                    ChoiceTile(
                        label = style.label,
                        selected = style == selected,
                        testTag = "player_background_${style.name}",
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(style) }
                    ) {
                        BackdropPreview(
                            style = style,
                            palette = palette,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp))
                        )
                    }
                }
                // A short last row keeps its tiles the same width as the ones above.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    label: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit
) {
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, label = "tileBorder")
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "tileBorderColor"
    )
    Column(
        modifier = modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .border(border, borderColor, RoundedCornerShape(16.dp))
                .padding(3.dp)
        ) {
            preview()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A miniature page: the ground, a heading bar and two list lines, in the theme's own colours. */
@Composable
private fun ThemeMiniature(background: Color, ink: Color, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background, RoundedCornerShape(13.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .height(7.dp)
                .background(accent, RoundedCornerShape(4.dp))
        )
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(4.dp)
                .background(ink.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(4.dp)
                .background(ink.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
        )
    }
}

/** The four light/dark choices, each drawn in the colours it actually produces. */
@Composable
private fun ThemeModeTiles(selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppThemeMode.entries.forEach { mode ->
            // Black is a true #000 ground, which is what makes it worth having on an OLED panel.
            val (bg, ink) = when (mode) {
                AppThemeMode.LIGHT -> Color(0xFFF7F7FA) to Color(0xFF14151A)
                AppThemeMode.DARK -> Color(0xFF16171C) to Color(0xFFECEDF2)
                AppThemeMode.AMOLED -> Color.Black to Color(0xFFECEDF2)
                AppThemeMode.SYSTEM -> scheme.surfaceContainerHighest to scheme.onSurface
            }
            ChoiceTile(
                label = mode.label,
                selected = selected == mode,
                testTag = "theme_mode_${mode.name.lowercase()}",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(mode) }
            ) {
                if (mode == AppThemeMode.SYSTEM) {
                    // Half light, half dark: it is whichever the phone is.
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            ThemeMiniature(Color(0xFFF7F7FA), Color(0xFF14151A), scheme.primary)
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            ThemeMiniature(Color(0xFF16171C), Color(0xFFECEDF2), scheme.primary)
                        }
                    }
                } else {
                    ThemeMiniature(bg, ink, scheme.primary)
                }
            }
        }
    }
}

/** Where the app's colours come from: the playing cover, or one steady palette. */
@Composable
private fun ColorSourceChoice(selected: AppColorSource, onSelect: (AppColorSource) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppColorSource.entries.forEach { source ->
            val isSelected = selected == source
            SpringPressableSurface(
                onClick = { onSelect(source) },
                color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                cornerRadius = 18.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("color_source_${source.name.lowercase()}")
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .background(
                                if (source == AppColorSource.COVER) {
                                    // A cover's colours running across the page.
                                    Brush.horizontalGradient(
                                        listOf(scheme.primary, scheme.tertiary, scheme.secondary)
                                    )
                                } else {
                                    Brush.horizontalGradient(listOf(scheme.primary, scheme.primary))
                                },
                                RoundedCornerShape(9.dp)
                            )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}

/** The two bar styles, each drawn as the bar it produces rather than named in a box. */
@Composable
private fun DockStyleTiles(selected: DockStyle, onSelect: (DockStyle) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DockStyle.entries.forEach { style ->
            val isSelected = selected == style
            SpringPressableSurface(
                onClick = { onSelect(style) },
                color = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                cornerRadius = 18.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dock_style_${style.name.lowercase()}")
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A tiny page with its bar at the foot, drawn the way that style sits on it.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(scheme.surface, RoundedCornerShape(10.dp))
                            .padding(5.dp)
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(3.dp)
                                    .background(scheme.onSurface.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .background(scheme.onSurface.copy(alpha = 0.18f), RoundedCornerShape(2.dp))
                            )
                        }
                        if (style == DockStyle.FLOATING) {
                            // Two separate capsules lifted off the page.
                            Column(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.92f)
                                        .height(11.dp)
                                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.92f)
                                        .height(11.dp)
                                        .background(scheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                                )
                            }
                        } else {
                            // One blended slab running edge to edge, the page fading under it.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(25.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                scheme.surfaceContainer.copy(alpha = 0.35f),
                                                scheme.surfaceContainer.copy(alpha = 0.85f)
                                            )
                                        ),
                                        RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp)
                                    )
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = style.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.78f)
                    )
                }
            }
        }
    }
}

/** Corner roundness, each shown as the corner it gives rather than as a word. */
@Composable
private fun CornerTiles(selected: CornerStylePreset, onSelect: (CornerStylePreset) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CornerStylePreset.entries.forEach { preset ->
            // The preset's own multiplier, so each tile really is the shape it sets.
            val radius = (18.dp * preset.scaleMultiplier).coerceIn(0.dp, 22.dp)
            ChoiceTile(
                label = preset.label.split(" ").first(),
                selected = selected == preset,
                testTag = "corner_preset_${preset.name.lowercase()}",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(preset) }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(7.dp)
                        .background(scheme.primary.copy(alpha = 0.85f), RoundedCornerShape(radius))
                )
            }
        }
    }
}

/**
 * Home layout: the rows Home shows, in order, and the ones it does not. Visible and hidden are two
 * named lists rather than one list with a divider, so it is clear which side a row is on and what
 * the switch will do to it. The whole thing lives inside a [CompactSettingsGroup], which is what
 * keeps eleven rows off the screen until they are wanted.
 */
@Composable
private fun HomeLayoutGroup(
    settings: PlayerCustomizationSettings,
    onSetHomeSections: (List<HomeSection>) -> Unit,
    onResetHomeSections: () -> Unit
) {
    val defaults = LocalStyleSpec.current.defaultHome
    val sections = settings.homeSections.ifEmpty { defaults }
    HomeLayoutEditor(
        sections = sections,
        isCustom = settings.homeSections.isNotEmpty(),
        onChange = onSetHomeSections,
        onReset = onResetHomeSections
    )
}

/**
 * The Appearance settings, as a short list of groups: theme, fonts, Home layout, the navigation
 * bar and corner roundness. Each group is collapsed to its current value and opens on a tap; the
 * one switch stays out in the open, where a switch belongs.
 *
 * Every setting here is the same state and the same callback as before: this is how they are
 * presented, not what they do.
 */
@Composable
fun AppearanceSettingsContent(
    settings: PlayerCustomizationSettings,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetAppColorSource: (AppColorSource) -> Unit,
    onSetHeaderFont: (AppFont) -> Unit,
    onSetBodyFont: (AppFont) -> Unit,
    onSetHomeSections: (List<HomeSection>) -> Unit,
    onResetHomeSections: () -> Unit,
    onSetDockStyle: (DockStyle) -> Unit,
    onSetHideMiniPlayerOnScroll: (Boolean) -> Unit,
    onSetCornerStylePreset: (CornerStylePreset) -> Unit,
    onSetPlayerBackgroundStyle: (PlayerBackgroundAnimationStyle) -> Unit = {}
) {
    val defaults = LocalStyleSpec.current.defaultHome
    val shownCount = settings.homeSections.ifEmpty { defaults }.size
    // Both on AUTO is the common case, and "Theme • Theme" says nothing: name it once instead.
    val fontSummary = if (settings.headerFont == AppFont.AUTO && settings.bodyFont == AppFont.AUTO) {
        "Follows the theme"
    } else {
        "${settings.headerFont.label} headings • ${settings.bodyFont.label} text"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactSettingsGroup(
            title = "Theme",
            summary = "${settings.themeMode.label} • ${settings.appColorSource.label}",
            testTag = "group_theme"
        ) {
            ThemeModeTiles(selected = settings.themeMode, onSelect = onSetThemeMode)
            Text(
                text = "Where the colours come from",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            ColorSourceChoice(selected = settings.appColorSource, onSelect = onSetAppColorSource)
        }

        CompactSettingsGroup(
            title = "Fonts",
            summary = fontSummary,
            testTag = "group_fonts"
        ) {
            Text(
                text = "Headings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            FontPicker("Headings", settings.headerFont, display = true, onSelect = onSetHeaderFont, tag = "font_header")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Text(
                text = "Songs & text",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            FontPicker("Songs & text", settings.bodyFont, display = false, onSelect = onSetBodyFont, tag = "font_body")
        }

        CompactSettingsGroup(
            title = "Player background",
            summary = settings.playerBackgroundStyle.label,
            testTag = "group_player_background"
        ) {
            PlayerBackgroundTiles(
                selected = settings.playerBackgroundStyle,
                onSelect = onSetPlayerBackgroundStyle
            )
            Text(
                text = settings.playerBackgroundStyle.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CompactSettingsGroup(
            title = "Home layout",
            summary = "$shownCount of ${HomeSection.entries.size} rows shown",
            testTag = "group_home_layout"
        ) {
            HomeLayoutGroup(
                settings = settings,
                onSetHomeSections = onSetHomeSections,
                onResetHomeSections = onResetHomeSections
            )
        }

        CompactSettingsGroup(
            title = "Navigation bar",
            summary = settings.dockStyle.label,
            testTag = "group_navigation_bar"
        ) {
            DockStyleTiles(selected = settings.dockStyle, onSelect = onSetDockStyle)
        }

        // A switch answers itself: it stays in the open rather than behind a row to tap.
        SettingsToggleRow(
            title = "Hide mini player while scrolling",
            subtitle = "Slides away as you scroll down a list and comes back as soon as you scroll up",
            checked = settings.hideMiniPlayerOnScroll,
            onCheckedChange = onSetHideMiniPlayerOnScroll,
            testTag = "toggle_hide_mini_player"
        )

        CompactSettingsGroup(
            title = "Corner roundness",
            summary = settings.cornerStylePreset.label,
            testTag = "group_corner_roundness"
        ) {
            CornerTiles(selected = settings.cornerStylePreset, onSelect = onSetCornerStylePreset)
        }
    }
}
