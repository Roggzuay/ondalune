package io.github.roggzuay.ondalune.ui.viewmodel

import io.github.roggzuay.ondalune.data.model.LibrarySortOption
import kotlinx.coroutines.flow.first
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TabDisplayPreference
import kotlinx.coroutines.flow.map

/** Sorting and fuzzy search for the library lists (search box, tab sort options). */
object LibraryQueries {
    fun sortAlbums(albums: List<SubsonicAlbum>, pref: TabDisplayPreference): List<SubsonicAlbum> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> albums.sortedBy { it.name.lowercase() }
            LibrarySortOption.NAME_DESC -> albums.sortedByDescending { it.name.lowercase() }
            LibrarySortOption.ARTIST -> albums.sortedWith(
                compareBy<SubsonicAlbum> { it.artist.lowercase() }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.YEAR_NEWEST -> albums.sortedWith(
                compareBy<SubsonicAlbum> { it.year }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.TRACK_COUNT -> albums.sortedWith(
                compareBy<SubsonicAlbum> { it.songCount }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.DURATION -> albums.sortedWith(
                compareBy<SubsonicAlbum> { it.durationSec }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.RECENTLY_ADDED -> albums.sortedBy { it.id }
            else -> albums.sortedBy { it.name.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    fun sortArtists(artists: List<SubsonicArtist>, pref: TabDisplayPreference): List<SubsonicArtist> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> artists.sortedBy { it.name.lowercase() }
            LibrarySortOption.NAME_DESC -> artists.sortedByDescending { it.name.lowercase() }
            LibrarySortOption.ALBUM_COUNT -> artists.sortedWith(
                compareBy<SubsonicArtist> { it.albumCount }.thenBy { it.trackCount }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.TRACK_COUNT -> artists.sortedWith(
                compareBy<SubsonicArtist> { it.trackCount }.thenBy { it.albumCount }.thenBy { it.name.lowercase() }
            )
            else -> artists.sortedBy { it.name.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    fun sortGenres(genres: List<SubsonicGenre>, pref: TabDisplayPreference): List<SubsonicGenre> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> genres.sortedBy { it.name.lowercase() }
            LibrarySortOption.NAME_DESC -> genres.sortedByDescending { it.name.lowercase() }
            LibrarySortOption.TRACK_COUNT -> genres.sortedWith(
                compareBy<SubsonicGenre> { it.trackCount }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.ALBUM_COUNT -> genres.sortedWith(
                compareBy<SubsonicGenre> { it.albumCount }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.DURATION -> genres.sortedWith(
                compareBy<SubsonicGenre> { it.totalDurationSec }.thenBy { it.name.lowercase() }
            )
            else -> genres.sortedBy { it.name.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    fun sortFolders(folders: List<SubsonicFolder>, pref: TabDisplayPreference): List<SubsonicFolder> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> folders.sortedBy { it.displayName.lowercase() }
            LibrarySortOption.NAME_DESC -> folders.sortedByDescending { it.displayName.lowercase() }
            LibrarySortOption.TRACK_COUNT -> folders.sortedWith(
                compareBy<SubsonicFolder> { it.tracks.size }.thenBy { it.displayName.lowercase() }
            )
            else -> folders.sortedBy { it.displayName.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    fun sortTracks(tracks: List<SubsonicTrack>, pref: TabDisplayPreference): List<SubsonicTrack> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> tracks.sortedBy { it.title.lowercase() }
            LibrarySortOption.NAME_DESC -> tracks.sortedByDescending { it.title.lowercase() }
            LibrarySortOption.ARTIST -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
            )
            LibrarySortOption.ALBUM -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.album.lowercase() }.thenBy { it.trackNumber }
            )
            LibrarySortOption.DURATION -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.durationSec }.thenBy { it.title.lowercase() }
            )
            LibrarySortOption.TRACK_NUMBER -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.trackNumber }.thenBy { it.title.lowercase() }
            )
            LibrarySortOption.YEAR_NEWEST -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.year }.thenBy { it.title.lowercase() }
            )
            LibrarySortOption.MOST_PLAYED -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.playCount }.thenBy { it.starred }.thenBy { it.title.lowercase() }
            )
            LibrarySortOption.RECENTLY_FAVOURITED -> tracks.sortedWith(
                compareBy<SubsonicTrack> { it.starredAtMs }.thenByDescending { it.title.lowercase() }
            )
            else -> tracks.sortedBy { it.title.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    fun sortPlaylists(playlists: List<SubsonicPlaylist>, pref: TabDisplayPreference): List<SubsonicPlaylist> {
        val sorted = when (pref.sortOption) {
            LibrarySortOption.NAME_ASC -> playlists.sortedBy { it.name.lowercase() }
            LibrarySortOption.NAME_DESC -> playlists.sortedByDescending { it.name.lowercase() }
            LibrarySortOption.TRACK_COUNT -> playlists.sortedWith(
                compareBy<SubsonicPlaylist> { it.trackIds.size }.thenBy { it.name.lowercase() }
            )
            LibrarySortOption.DATE_MODIFIED -> playlists.sortedBy { it.updatedAtMs }
            else -> playlists.sortedBy { it.name.lowercase() }
        }
        return if (pref.sortOption == LibrarySortOption.NAME_DESC) {
            sorted
        } else if (pref.ascending) {
            sorted
        } else {
            sorted.reversed()
        }
    }

    /**
     * Smart, dynamic, multi-token & typo-tolerant relevance scorer across primary and secondary metadata fields.
     * Returns 0 if any query token fails to match, or a positive relevance score (> 0) when all tokens match.
     */
    fun smartMatchScore(
        query: String,
        primaryFields: List<String>,
        secondaryFields: List<String> = emptyList()
    ): Int {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return 100

        val tokens = cleanQuery.split(Regex("[\\s,•/\\-]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return 100

        val normPrimary = primaryFields.map { it.lowercase() }
        val normSecondary = secondaryFields.map { it.lowercase() }
        val primaryWords = normPrimary.flatMap { it.split(Regex("[^a-z0-9]+")) }.filter { it.isNotBlank() }

        var totalScore = 0

        // Full phrase bonus if the multi-word query appears contiguously in a primary field
        if (tokens.size > 1 && normPrimary.any { it.contains(cleanQuery) }) {
            totalScore += 65
        }

        for (token in tokens) {
            var bestTokenScore = 0
            for (field in normPrimary) {
                val s = when {
                    field == token -> 130
                    field.startsWith(token) -> 95
                    field.contains(token) -> 60
                    else -> 0
                }
                if (s > bestTokenScore) bestTokenScore = s
            }
            if (bestTokenScore < 80) {
                for (word in primaryWords) {
                    val s = when {
                        word == token -> 115
                        word.startsWith(token) -> 85
                        token.length >= 4 && isWithinOneEdit(word, token) -> 42
                        else -> 0
                    }
                    if (s > bestTokenScore) bestTokenScore = s
                }
            }
            if (bestTokenScore == 0) {
                for (field in normSecondary) {
                    if (field.contains(token)) {
                        bestTokenScore = 32
                        break
                    }
                }
            }
            if (bestTokenScore == 0) {
                return 0
            }
            totalScore += bestTokenScore
        }

        return totalScore
    }

    private fun isWithinOneEdit(a: String, b: String): Boolean {
        val lenDiff = kotlin.math.abs(a.length - b.length)
        if (lenDiff > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                edits++
                if (edits > 1) return false
                if (a.length > b.length) {
                    i++
                } else if (b.length > a.length) {
                    j++
                } else {
                    i++
                    j++
                }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits <= 1
    }

    fun searchTracks(tracks: List<SubsonicTrack>, query: String): List<SubsonicTrack> {
        if (query.isBlank()) return tracks
        return tracks.mapNotNull { track ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(track.title, track.artist, track.album, track.genre),
                secondaryFields = listOf(
                    track.suffix,
                    track.year.toString(),
                    track.folderPath,
                    track.musicalKey,
                    track.syncedLyricsRaw
                )
            )
            if (score > 0) track to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun searchAlbums(albums: List<SubsonicAlbum>, query: String): List<SubsonicAlbum> {
        if (query.isBlank()) return albums
        return albums.mapNotNull { album ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(album.name, album.artist, album.genre),
                secondaryFields = listOf(album.year.toString())
            )
            if (score > 0) album to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun searchArtists(artists: List<SubsonicArtist>, query: String): List<SubsonicArtist> {
        if (query.isBlank()) return artists
        return artists.mapNotNull { artist ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(artist.name, artist.genre),
                secondaryFields = listOf(artist.bio)
            )
            if (score > 0) artist to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun searchPlaylists(playlists: List<SubsonicPlaylist>, query: String): List<SubsonicPlaylist> {
        if (query.isBlank()) return playlists
        return playlists.mapNotNull { playlist ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(playlist.name),
                secondaryFields = listOf(playlist.comment)
            )
            if (score > 0) playlist to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun searchGenres(genres: List<SubsonicGenre>, query: String): List<SubsonicGenre> {
        if (query.isBlank()) return genres
        return genres.mapNotNull { genre ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(genre.name)
            )
            if (score > 0) genre to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun searchFolders(folders: List<SubsonicFolder>, query: String): List<SubsonicFolder> {
        if (query.isBlank()) return folders
        return folders.mapNotNull { folder ->
            val score = smartMatchScore(
                query = query,
                primaryFields = listOf(folder.displayName, folder.category),
                secondaryFields = listOf(folder.path) + folder.tracks.map { "${it.title} ${it.artist}" }
            )
            if (score > 0) folder to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }
}
