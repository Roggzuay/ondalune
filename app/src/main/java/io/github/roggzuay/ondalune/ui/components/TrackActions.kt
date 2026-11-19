package io.github.roggzuay.ondalune.ui.components

import io.github.roggzuay.ondalune.data.model.countOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
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
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

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
    fun cancelDownloads(tracks: List<SubsonicTrack>)
    fun resumeDownloads(tracks: List<SubsonicTrack>)
    fun removeDownloads(tracks: List<SubsonicTrack>)
    fun addToPlaylist(tracks: List<SubsonicTrack>, playlist: SubsonicPlaylist)
    fun createPlaylist(name: String, tracks: List<SubsonicTrack>)
    fun openAlbum(track: SubsonicTrack)
    fun openArtist(track: SubsonicTrack)
    fun openArtist(artistId: String, name: String)
    /** [track] as its source has it, without edits made in the app. */
    fun originalOf(track: SubsonicTrack): SubsonicTrack
    /** Saves details edited in the app (kept on the phone, never sent to the server). */
    fun saveDetails(track: SubsonicTrack, title: String, artist: String, genre: String, year: Int)
    /** The songs a held album, artist or playlist stands for, in their own order. */
    fun tracksOf(target: HoldTarget): List<SubsonicTrack>
    fun play(target: HoldTarget, shuffle: Boolean)
    /** Whether the artist is a favourite; null when the library doesn't list the artist. */
    fun isFavouriteArtist(id: String, name: String): Boolean?
    fun toggleFavouriteArtist(id: String, name: String)
    fun deletePlaylist(playlist: SubsonicPlaylist)
}

val LocalTrackActions = staticCompositionLocalOf<TrackActions?> { null }

/** What a held card stands for. The shell shows one sheet for all of them. */
sealed interface HoldTarget {
    data class Song(val track: SubsonicTrack) : HoldTarget
    data class Album(val album: SubsonicAlbum) : HoldTarget
    data class Artist(val id: String, val name: String, val coverArtUrl: String) : HoldTarget
    data class Playlist(val playlist: SubsonicPlaylist) : HoldTarget
    /** A mix, genre or folder: a named set of songs. */
    data class Songs(val title: String, val subtitle: String, val coverArtUrl: String, val tracks: List<SubsonicTrack>) : HoldTarget
}

/** Opens the hold sheet for a card; null outside the shell (previews, tests). */
val LocalHold = staticCompositionLocalOf<((HoldTarget) -> Unit)?> { null }

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
    var newPlaylistDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
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
                        text = single?.title ?: "${countOf(tracks.size, "track")} selected",
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
                SheetItem(Icons.Filled.CloudDownload, if (single != null) "Download" else "Download ${countOf(tracks.size, "track")}", "option_download") {
                    run { actions.download(tracks) }
                }
            }
            PlaylistItems(actions, tracks, onNew = { newPlaylistDialog = true }, run = ::run)
            if (single != null) {
                SheetItem(Icons.Filled.Edit, "Edit details", "option_edit_details") { editDialog = true }
                SheetItem(Icons.Filled.Album, "Go to album", "option_go_album") { run { actions.openAlbum(single) } }
                SheetItem(Icons.Filled.Person, "Go to artist", "option_go_artist") { run { actions.openArtist(single) } }
            }
            if (onSelect != null) {
                SheetItem(Icons.Filled.SelectAll, "Select", "option_select") { run(onSelect) }
            }
        }
    }

    if (editDialog && single != null) {
        EditDetailsDialog(
            track = single,
            original = remember(single) { actions.originalOf(single) },
            onSave = { title, artist, genre, year ->
                editDialog = false
                run { actions.saveDetails(single, title, artist, genre, year) }
            },
            onDismiss = { editDialog = false }
        )
    }

    if (newPlaylistDialog) {
        NewPlaylistDialog("", onCreate = { name ->
            newPlaylistDialog = false
            run { actions.createPlaylist(name, tracks) }
        }, onDismiss = { newPlaylistDialog = false })
    }
}

/** "Add to playlist", opening in place to "New playlist…" and the playlists you can change. */
@Composable
private fun PlaylistItems(actions: TrackActions, tracks: List<SubsonicTrack>, onNew: () -> Unit, run: (() -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SheetItem(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", "option_add_playlist") { open = !open }
    AnimatedVisibility(visible = open) {
        Column(modifier = Modifier.padding(start = 24.dp)) {
            SheetItem(Icons.Filled.Add, "New playlist…", "option_new_playlist", onNew)
            // Playlists shared with you by others are read-only.
            actions.playlists.filterNot { it.readOnly }.forEach { playlist ->
                SheetItem(Icons.AutoMirrored.Filled.QueueMusic, playlist.name, "option_playlist_${playlist.id}") {
                    run { actions.addToPlaylist(tracks, playlist) }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(suggested: String, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Hold sheet for an album, artist, playlist, mix, genre or folder: play or shuffle it, line it
 * up, download it, put it in a playlist, and the actions that belong to its kind.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionOptionsSheet(
    target: HoldTarget,
    downloads: Map<String, SubsonicDownloadEntity>,
    onDismiss: () -> Unit
) {
    val actions = LocalTrackActions.current ?: return
    val tracks = remember(target) { actions.tracksOf(target) }
    var newPlaylistDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun run(action: () -> Unit) {
        action()
        onDismiss()
    }
    val title: String
    val subtitle: String
    val cover: String
    when (target) {
        is HoldTarget.Album -> { title = target.album.name; subtitle = target.album.artist; cover = target.album.coverArtUrl }
        is HoldTarget.Artist -> { title = target.name; subtitle = countOf(tracks.size, "song"); cover = target.coverArtUrl }
        is HoldTarget.Playlist -> { title = target.playlist.name; subtitle = countOf(tracks.size, "song"); cover = target.playlist.coverArtUrl.ifBlank { tracks.firstOrNull()?.coverArtUrl.orEmpty() } }
        is HoldTarget.Songs -> { title = target.title; subtitle = target.subtitle; cover = target.coverArtUrl }
        is HoldTarget.Song -> { title = target.track.title; subtitle = target.track.artist; cover = target.track.coverArtUrl }
    }
    val round = target is HoldTarget.Artist

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveArtwork(
                    seedHex = "#7C4DFF",
                    accentHex = "#70F5FF",
                    artStyleIndex = title.length % 5,
                    coverArtUrl = cover,
                    monogramText = if (round) title else "",
                    modifier = Modifier.size(52.dp).then(if (round) Modifier.clip(CircleShape) else Modifier)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (tracks.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(onClick = { run { actions.play(target, shuffle = false) } }, modifier = Modifier.weight(1f).testTag("hold_play")) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = { run { actions.play(target, shuffle = true) } }, modifier = Modifier.weight(1f).testTag("hold_shuffle")) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }
                HorizontalDivider()
                SheetItem(Icons.Filled.PlaylistPlay, "Play next", "option_play_next") { run { actions.playNext(tracks) } }
                SheetItem(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", "option_add_queue") { run { actions.addToQueue(tracks) } }
            }
            if (target is HoldTarget.Artist) {
                actions.isFavouriteArtist(target.id, target.name)?.let { favourite ->
                    SheetItem(
                        if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (favourite) "Remove from favourites" else "Add to favourites",
                        "option_favourite"
                    ) { run { actions.toggleFavouriteArtist(target.id, target.name) } }
                }
            }
            if (tracks.isNotEmpty()) {
                val allDownloaded = tracks.all { downloads[it.id]?.isCompleted == true }
                if (allDownloaded) {
                    SheetItem(Icons.Filled.DeleteOutline, "Remove downloads", "option_remove_download") { run { actions.removeDownloads(tracks) } }
                } else {
                    SheetItem(Icons.Filled.CloudDownload, "Download ${countOf(tracks.size, "song")}", "option_download") { run { actions.download(tracks) } }
                }
                PlaylistItems(actions, tracks, onNew = { newPlaylistDialog = true }, run = ::run)
            }
            if (target is HoldTarget.Album) {
                SheetItem(Icons.Filled.Person, "Go to artist", "option_go_artist") {
                    run { actions.openArtist(target.album.artistId, target.album.artist) }
                }
            }
            if (target is HoldTarget.Playlist && !target.playlist.readOnly) {
                SheetItem(Icons.Filled.DeleteOutline, "Delete playlist", "option_delete_playlist") { confirmDelete = true }
            }
        }
    }

    if (newPlaylistDialog) {
        NewPlaylistDialog(if (target is HoldTarget.Playlist) "" else title, onCreate = { name ->
            newPlaylistDialog = false
            run { actions.createPlaylist(name, tracks) }
        }, onDismiss = { newPlaylistDialog = false })
    }
    if (confirmDelete && target is HoldTarget.Playlist) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${target.playlist.name}\"?") },
            text = { Text("The playlist goes; its songs stay in your library.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; run { actions.deletePlaylist(target.playlist) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Title, artist, genre and year of one song, changed for this app only: the server's library
 * stays as it is, and a sync never undoes the change. "Use original" puts the source's details back.
 */
@Composable
private fun EditDetailsDialog(
    track: SubsonicTrack,
    original: SubsonicTrack,
    onSave: (title: String, artist: String, genre: String, year: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var genre by remember { mutableStateOf(track.genre) }
    var year by remember { mutableStateOf(track.year.takeIf { it > 0 }?.toString().orEmpty()) }
    val edited = title != original.title || artist != original.artist || genre != original.genre ||
        (year.toIntOrNull() ?: 0) != original.year
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Changes stay in Ondalune on this phone; your music files and server are not changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Title") }, modifier = Modifier.testTag("edit_title"))
                OutlinedTextField(value = artist, onValueChange = { artist = it }, singleLine = true, label = { Text("Artist") })
                OutlinedTextField(value = genre, onValueChange = { genre = it }, singleLine = true, label = { Text("Genre") })
                OutlinedTextField(
                    value = year,
                    onValueChange = { v -> year = v.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (edited) {
                    TextButton(onClick = {
                        title = original.title
                        artist = original.artist
                        genre = original.genre
                        year = original.year.takeIf { it > 0 }?.toString().orEmpty()
                    }) { Text("Use original") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && artist.isNotBlank(),
                onClick = { onSave(title, artist, genre, year.toIntOrNull() ?: 0) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
