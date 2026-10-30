package io.github.roggzuay.ondalune.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.roggzuay.ondalune.data.model.SubsonicDownloadEntity
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack

/**
 * Everything a track row (or the multi-select bar) can do to tracks. Provided once by the shell
 * through [LocalTrackActions], so rows on every screen share the same swipe / hold behaviour
 * without threading a dozen callbacks through each screen.
 */
interface TrackActions {
    val playlists: List<SubsonicPlaylist>
    fun playNext(tracks: List<SubsonicTrack>)
    fun addToQueue(tracks: List<SubsonicTrack>)
    fun toggleFavourite(track: SubsonicTrack)
    fun setFavourite(tracks: List<SubsonicTrack>, favourite: Boolean)
    fun download(tracks: List<SubsonicTrack>)
    fun removeDownload(track: SubsonicTrack)
    fun addToPlaylist(tracks: List<SubsonicTrack>, playlist: SubsonicPlaylist)
    fun createPlaylist(name: String, tracks: List<SubsonicTrack>)
    fun openAlbum(track: SubsonicTrack)
    fun openArtist(track: SubsonicTrack)
    fun openArtist(artistId: String, name: String)
}

val LocalTrackActions = staticCompositionLocalOf<TrackActions?> { null }

/** Download state per track id, so any row anywhere can show its progress bar. */
val LocalDownloads = compositionLocalOf<Map<String, SubsonicDownloadEntity>> { emptyMap() }

/** Multi-select state shared by all track lists; empty means selection mode is off. */
@Stable
class TrackSelection {
    private val selected = mutableStateMapOf<String, SubsonicTrack>()
    val active: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size
    val tracks: List<SubsonicTrack> get() = selected.values.toList()
    fun isSelected(id: String) = id in selected
    fun toggle(track: SubsonicTrack) {
        if (selected.remove(track.id) == null) selected[track.id] = track
    }
    fun clear() = selected.clear()

    /** Every track the visible screen lists, so "Select all" covers rows not yet scrolled into view. */
    var pool by mutableStateOf<List<SubsonicTrack>>(emptyList())
    val allSelected: Boolean get() = pool.isNotEmpty() && pool.all { it.id in selected }
    fun selectAll() = pool.forEach { selected[it.id] = it }
}

val LocalTrackSelection = staticCompositionLocalOf { TrackSelection() }

/** Registers [tracks] as the list "Select all" acts on while this screen is shown. */
@Composable
fun SelectableTracks(tracks: List<SubsonicTrack>) {
    val selection = LocalTrackSelection.current
    DisposableEffect(selection, tracks) {
        selection.pool = tracks
        // A screen animating out must not wipe the pool the incoming screen just set.
        onDispose { if (selection.pool === tracks) selection.pool = emptyList() }
    }
}

/**
 * Long-press sheet for one track or a multi-selection. Every entry maps to a real action; the
 * playlist entry expands in place to existing playlists plus "New playlist".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsSheet(
    tracks: List<SubsonicTrack>,
    downloads: Map<String, SubsonicDownloadEntity>,
    onSelect: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val actions = LocalTrackActions.current ?: return
    val single = tracks.singleOrNull()
    var showPlaylists by remember { mutableStateOf(false) }
    var newPlaylistDialog by remember { mutableStateOf(false) }
    val allFavourite = tracks.all { it.starred }
    fun run(action: () -> Unit) {
        action()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (single != null) {
                    ExpressiveArtwork(
                        seedHex = single.paletteSeedHex,
                        accentHex = single.accentHex,
                        artStyleIndex = single.artStyleIndex,
                        coverArtUrl = single.coverArtUrl,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = single?.title ?: "${tracks.size} tracks selected",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (single != null) {
                        Text(
                            text = "${single.artist} • ${single.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider()

            SheetItem(Icons.Filled.PlaylistPlay, "Play next", "option_play_next") { run { actions.playNext(tracks) } }
            SheetItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", "option_add_queue") { run { actions.addToQueue(tracks) } }
            SheetItem(
                if (allFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (allFavourite) "Remove from favourites" else "Add to favourites",
                "option_favourite"
            ) { run { actions.setFavourite(tracks, !allFavourite) } }
            val downloaded = single != null && downloads[single.id]?.isCompleted == true
            if (downloaded) {
                SheetItem(Icons.Filled.DeleteOutline, "Remove download", "option_remove_download") { run { actions.removeDownload(single!!) } }
            } else {
                SheetItem(Icons.Filled.CloudDownload, if (single != null) "Download" else "Download ${tracks.size} tracks", "option_download") {
                    run { actions.download(tracks) }
                }
            }
            SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", "option_add_playlist") { showPlaylists = !showPlaylists }
            AnimatedVisibility(visible = showPlaylists) {
                Column(modifier = Modifier.padding(start = 24.dp)) {
                    SheetItem(Icons.Filled.Add, "New playlist…", "option_new_playlist") { newPlaylistDialog = true }
                    actions.playlists.forEach { playlist ->
                        SheetItem(Icons.AutoMirrored.Filled.QueueMusic, playlist.name, "option_playlist_${playlist.id}") {
                            run { actions.addToPlaylist(tracks, playlist) }
                        }
                    }
                }
            }
            if (single != null) {
                SheetItem(Icons.Filled.Album, "Go to album", "option_go_album") { run { actions.openAlbum(single) } }
                SheetItem(Icons.Filled.Person, "Go to artist", "option_go_artist") { run { actions.openArtist(single) } }
            }
            if (onSelect != null) {
                SheetItem(Icons.Filled.SelectAll, "Select", "option_select") { run(onSelect) }
            }
        }
    }

    if (newPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPlaylistDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newPlaylistDialog = false
                    run { actions.createPlaylist(name.trim(), tracks) }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newPlaylistDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag)
    )
}

/** Replaces the top bar while tracks are selected. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionTopBar(selection: TrackSelection, onMore: () -> Unit) {
    val actions = LocalTrackActions.current ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(72.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = selection::clear, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
        }
        Text(
            text = "${selection.count} selected",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = { actions.playNext(selection.tracks); selection.clear() }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.PlaylistPlay, contentDescription = "Play next")
        }
        IconButton(onClick = { actions.setFavourite(selection.tracks, true); selection.clear() }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.FavoriteBorder, contentDescription = "Add to favourites")
        }
        IconButton(onClick = { actions.download(selection.tracks); selection.clear() }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Filled.CloudDownload, contentDescription = "Download")
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.testTag("selection_more")) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (selection.pool.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(if (selection.allSelected) "Deselect all" else "Select all (${selection.pool.size})") },
                        leadingIcon = { Icon(if (selection.allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            if (selection.allSelected) selection.clear() else selection.selectAll()
                        },
                        modifier = Modifier.testTag("selection_select_all")
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        actions.addToQueue(selection.tracks)
                        selection.clear()
                    }
                )
                DropdownMenuItem(
                    text = { Text("More options…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onMore()
                    },
                    modifier = Modifier.testTag("selection_more_options")
                )
            }
        }
    }
}

/** Check badge drawn over a selected row's artwork. */
@Composable
fun SelectedBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = "Selected",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(22.dp)
    )
}
