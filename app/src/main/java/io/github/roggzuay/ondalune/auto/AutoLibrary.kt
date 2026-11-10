package io.github.roggzuay.ondalune.auto

import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.ui.viewmodel.LibraryQueries
import java.net.URLDecoder
import java.net.URLEncoder

/** One row Android Auto shows: a folder to open, or a song to play. */
data class AutoItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverArtUrl: String = "",
    val browsable: Boolean = false
)

/** A song to start, and the list it plays on from. */
data class AutoQueue(val start: SubsonicTrack, val tracks: List<SubsonicTrack>)

/**
 * The library as Android Auto browses it: four tabs, the folders under them, and their songs.
 * A song's id is its list's id, a bar, and the song's own id, so a tap plays on from that list.
 * Plain data, so it is tested without a car.
 */
class AutoLibrary(
    private val tracks: List<SubsonicTrack> = emptyList(),
    private val albums: List<SubsonicAlbum> = emptyList(),
    private val playlists: List<SubsonicPlaylist> = emptyList(),
    private val yourMix: List<SubsonicTrack> = emptyList(),
    private val recent: List<SubsonicTrack> = emptyList(),
    private val mostPlayed: List<SubsonicTrack> = emptyList(),
    private val downloadedIds: Set<String> = emptySet()
) {
    private val tracksById by lazy { tracks.associateBy { it.id } }

    val isEmpty: Boolean get() = tracks.isEmpty()

    /** What [parentId] holds, or null when there is no such folder. */
    fun children(parentId: String): List<AutoItem>? = when (parentId) {
        ROOT -> listOf(
            AutoItem(HOME, "Home", browsable = true),
            AutoItem(PLAYLISTS, "Playlists", browsable = true),
            AutoItem(ALBUMS, "Albums", browsable = true),
            AutoItem(DOWNLOADS, "Downloads", browsable = true)
        )
        HOME -> HomeFolders.mapNotNull { (id, title) -> folder(id, title, songs(id).orEmpty()) }
        PLAYLISTS -> playlists.mapNotNull { folder(PLAYLIST + encode(it.id), it.name, songsOf(it)) }
        ALBUMS -> albums.sortedBy { it.name.lowercase() }
            .map { AutoItem(ALBUM + encode(it.id), it.name, it.artist, it.coverArtUrl, browsable = true) }
        else -> songs(parentId)?.map { AutoItem("$parentId|${it.id}", it.title, it.artist, it.coverArtUrl) }
    }

    /** The song a tapped row stands for and the list it belongs to; a folder plays from its top. */
    fun queueFor(mediaId: String): AutoQueue? {
        val list = songs(mediaId.substringBefore('|'))?.ifEmpty { null } ?: return null
        if ('|' !in mediaId) return AutoQueue(list.first(), list)
        return list.find { it.id == mediaId.substringAfter('|') }?.let { AutoQueue(it, list) }
    }

    /** What "play [query]" means: a playlist, album or artist of that name, else the songs that match best. */
    fun search(query: String): AutoQueue? {
        val q = query.trim().ifEmpty { return null }
        val named = playlists.find { it.name.equals(q, ignoreCase = true) }?.let(::songsOf)?.ifEmpty { null }
            ?: albums.find { it.name.equals(q, ignoreCase = true) }?.let { album -> tracks.filter { it.albumId == album.id } }?.ifEmpty { null }
            ?: tracks.filter { it.artist.equals(q, ignoreCase = true) }.ifEmpty { null }
            ?: LibraryQueries.searchTracks(tracks, q).take(SEARCH_QUEUE)
        return named.firstOrNull()?.let { AutoQueue(it, named) }
    }

    /** The songs of a list (a Home folder, Downloads, a playlist or an album), or null for anything else. */
    private fun songs(listId: String): List<SubsonicTrack>? = when {
        listId == MIX -> yourMix
        listId == RECENT -> recent
        listId == FAVOURITES -> tracks.filter { it.starred }.sortedByDescending { it.starredAtMs }
        listId == MOST_PLAYED -> mostPlayed
        listId == DOWNLOADS -> tracks.filter { it.id in downloadedIds }
        listId.startsWith(PLAYLIST) -> decode(listId.removePrefix(PLAYLIST)).let { id -> playlists.find { it.id == id } }?.let(::songsOf)
        listId.startsWith(ALBUM) -> decode(listId.removePrefix(ALBUM)).let { id -> tracks.filter { it.albumId == id } }.ifEmpty { null }
        else -> null
    }

    private fun songsOf(playlist: SubsonicPlaylist) = playlist.trackIds.mapNotNull { tracksById[it] }

    /** A folder of [list] showing its first cover; empty folders are left out. */
    private fun folder(id: String, title: String, list: List<SubsonicTrack>): AutoItem? = list.firstOrNull()?.let {
        AutoItem(id, title, if (list.size == 1) "1 song" else "${list.size} songs", it.coverArtUrl, browsable = true)
    }

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val PLAYLISTS = "playlists"
        const val ALBUMS = "albums"
        const val DOWNLOADS = "downloads"
        const val MIX = "mix"
        private const val RECENT = "recent"
        private const val FAVOURITES = "favourites"
        private const val MOST_PLAYED = "most"
        private const val PLAYLIST = "playlist/"
        private const val ALBUM = "album/"
        private const val SEARCH_QUEUE = 50
        private val HomeFolders = listOf(
            MIX to "Your Mix", RECENT to "Recently played", FAVOURITES to "Favourites", MOST_PLAYED to "Most played"
        )

        // Ids are the user's own text; encoded, a bar in one cannot be mistaken for the separator.
        private fun encode(id: String) = URLEncoder.encode(id, "UTF-8")
        private fun decode(id: String) = URLDecoder.decode(id, "UTF-8")
    }
}
