package io.github.roggzuay.ondalune.ui.screens

import androidx.compose.material3.LoadingIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.OfflineDownloadState
import io.github.roggzuay.ondalune.ui.theme.cornerShape
import io.github.roggzuay.ondalune.ui.viewmodel.LibraryFilters
import io.github.roggzuay.ondalune.data.model.OfflineSummary

/**
 * Minimal app bar: the tab's title on the left, icon actions on the right. Search
 * opens in place of the title, so no search field sits on screen until it's wanted.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OndaluneTopBar(
    title: String,
    searchQuery: String,
    searchOpen: Boolean,
    statusBanner: String?,
    filtersActive: Boolean,
    isSettingsActive: Boolean,
    bannerBusy: Boolean = false,
    onQueryChange: (String) -> Unit,
    onSearchOpenChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissBanner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
    ) {
        AnimatedContent(
            targetState = searchOpen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "topBarSearch"
        ) { searching ->
            if (searching) {
                SearchRow(
                    query = searchQuery,
                    onQueryChange = onQueryChange,
                    onClose = {
                        onQueryChange("")
                        onSearchOpenChange(false)
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onSearchOpenChange(true) },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.testTag("top_bar_search_button")
                    ) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    if (!isSettingsActive) {
                        IconButton(
                            onClick = onOpenFilters,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.testTag("top_bar_filter_button")
                        ) {
                            BadgedBox(badge = { if (filtersActive) Badge() }) {
                                Icon(Icons.Filled.FilterList, contentDescription = "Library filter")
                            }
                        }
                    }
                    if (isSettingsActive) {
                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.testTag("top_bar_settings_button")
                        ) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    } else {
                        IconButton(
                            onClick = onOpenSettings,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.testTag("top_bar_settings_button")
                        ) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                    }
                }
            }
        }

        AnimatedVisibility(visible = !statusBanner.isNullOrBlank()) {
            Surface(
                shape = cornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp, top = 4.dp)
                    .clickable { onDismissBanner() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bannerBusy) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Songs, albums, artists, genres", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("clear_search_query_icon_button")) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = cornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .testTag("opensubsonic_search_bar")
        )
    }
}

/**
 * Bottom sheet behind the top bar's filter icon: library-wide filters, the active server with a
 * sync action, and shortcuts to switch server or open settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    filters: LibraryFilters,
    /** What plays without a connection, for the Downloaded switch. */
    offline: OfflineSummary,
    serverName: String,
    trackCount: Int,
    isSyncing: Boolean,
    onChange: (LibraryFilters) -> Unit,
    onSync: () -> Unit,
    onSwitchServer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)) {
                Icon(Icons.Filled.FilterList, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text("Library filter", style = MaterialTheme.typography.headlineSmall)
            }

            Surface(shape = cornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    FilterSwitchRow(
                        "Downloaded",
                        offline.description?.let { "Plays without a connection: $it" } ?: "Nothing downloaded yet: tap the cloud on a song or album",
                        filters.downloadedOnly
                    ) {
                        onChange(filters.copy(downloadedOnly = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    FilterSwitchRow("Hide short tracks", "Skits, intros and interludes under a minute", filters.hideShortTracks) {
                        onChange(filters.copy(hideShortTracks = it))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)
                    FilterSwitchRow("Lossless only", "FLAC, ALAC, WAV and other lossless formats", filters.losslessOnly) {
                        onChange(filters.copy(losslessOnly = it))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(shape = cornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                    headlineContent = { Text(serverName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (isSyncing) "Syncing…" else "$trackCount tracks in library") },
                    trailingContent = {
                        TextButton(onClick = onSync, enabled = !isSyncing, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSwitchServer, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Switch server")
                }
                TextButton(onClick = onOpenDownloads, shapes = ButtonDefaults.shapes(), modifier = Modifier.testTag("filter_sheet_downloads")) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Downloads")
                }
                TextButton(onClick = onOpenSettings, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
private fun FilterSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }
    )
}
