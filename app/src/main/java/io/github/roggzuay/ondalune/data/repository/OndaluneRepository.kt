package io.github.roggzuay.ondalune.data.repository

import kotlinx.coroutines.flow.map
import io.github.roggzuay.ondalune.data.model.TrackOverride
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.local.OndaluneDao
import io.github.roggzuay.ondalune.data.model.AlbumListType
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.serverPlaylistKey
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.isLocalSource
import io.github.roggzuay.ondalune.data.model.LocalFileRecord
import kotlinx.coroutines.ensureActive
import io.github.roggzuay.ondalune.data.model.LocalFolder
import io.github.roggzuay.ondalune.data.source.FolderPermissions
import io.github.roggzuay.ondalune.data.source.FolderReader
import io.github.roggzuay.ondalune.data.source.folderLocation
import io.github.roggzuay.ondalune.data.source.folderTrackFrom
import io.github.roggzuay.ondalune.data.source.isInside
import io.github.roggzuay.ondalune.data.source.MusicSource
import io.github.roggzuay.ondalune.data.source.OpenSubsonicSource
import io.github.roggzuay.ondalune.data.network.JellyfinClient
import io.github.roggzuay.ondalune.data.model.source
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OndaluneRepository(
    private val dao: OndaluneDao,
    val client: OpenSubsonicClient,
    /** The source behind a library row; Subsonic for every row unless told otherwise (tests). */
    private val sourceFor: (SubsonicServerConfig) -> MusicSource = { OpenSubsonicSource(client) },
    /** Gives back a picked folder's read permission when it is removed; null in tests. */
    private val folderPermissions: FolderPermissions? = null,
    /** Signs in to Jellyfin servers; null in tests. */
    private val jellyfin: JellyfinClient? = null
) {
    val serversFlow: Flow<List<SubsonicServerConfig>> = dao.observeServers()

    fun observeTracksForServer(serverId: Long): Flow<List<SubsonicTrack>> =
        dao.observeTracksForServer(serverId)

    /** Song details edited in the app, by (server id, track id). */
    val overridesFlow: Flow<Map<Pair<Long, String>, TrackOverride>> =
        dao.observeOverrides().map { list -> list.associateBy { it.serverId to it.trackId } }

    /** Saves [override] for its song, or forgets the song's edits when [override] is null. */
    suspend fun setOverride(track: SubsonicTrack, override: TrackOverride?) {
        if (override == null) dao.deleteOverride(track.serverId, track.id) else dao.upsertOverride(override)
    }

    fun observePlaylistsForServer(serverId: Long): Flow<List<SubsonicPlaylist>> =
        dao.observePlaylistsForServer(serverId)

    suspend fun ensureSeeded(): SubsonicServerConfig {
        val existingServers = dao.getAllServersOnce()
        if (existingServers.isEmpty()) {
            val demoServer = DemoSubsonicCatalog.createDefaultDemoServer()
            val id = dao.insertServer(demoServer)
            val seededServer = demoServer.copy(id = id)
            dao.insertTracks(DemoSubsonicCatalog.seedTracks(id))
            DemoSubsonicCatalog.seedPlaylists(id).forEach { dao.insertPlaylist(it) }
            return seededServer
        }
        // This phone's music is never the active library; it joins whichever server is.
        val active = dao.getActiveServer()?.takeUnless { it.isLocalSource }
            ?: existingServers.first { !it.isLocalSource }.also {
            dao.setActiveServer(it.id)
        }
        val existingTracks = dao.getTracksForServerOnce(active.id)
        if (active.isDemoServer && existingTracks.isEmpty()) {
            dao.insertTracks(DemoSubsonicCatalog.seedTracks(active.id))
            DemoSubsonicCatalog.seedPlaylists(active.id).forEach { dao.insertPlaylist(it) }
        }
        return active
    }

    suspend fun pingAndInspect(server: SubsonicServerConfig): PingDiagnosticResult = sourceFor(server).ping(server)

    /**
     * Saves [server] as the active library and syncs it. A Jellyfin server signs in first with the
     * password in `passwordOrToken`, which is then replaced by the access token; if sign-in fails
     * nothing is saved and the returned server has id 0.
     */
    suspend fun saveAndActivateServer(
        server: SubsonicServerConfig,
        onSyncProgress: (Int) -> Unit = {}
    ): Pair<SubsonicServerConfig, PingDiagnosticResult> {
        @Suppress("NAME_SHADOWING")
        val server = if (server.source == SourceType.JELLYFIN) {
            val login = (jellyfin ?: return server to PingDiagnosticResult(false, "Jellyfin isn't available"))
                .login(server.baseUrl, server.username, server.passwordOrToken)
                .getOrElse { return server to PingDiagnosticResult(false, it.message ?: "Couldn't sign in") }
            server.copy(passwordOrToken = login.token, remoteUserId = login.userId)
        } else server
        val diag = sourceFor(server).ping(server)
        val updated = OndaluneCredentialSecurity.encryptServerConfig(
            server.copy(
                isActive = true,
                serverSoftware = if (diag.serverType.isNotBlank()) diag.serverType else server.serverSoftware,
                extensionsCsv = if (diag.openSubsonicExtensions.isNotEmpty()) {
                    diag.openSubsonicExtensions.joinToString(",")
                } else {
                    server.extensionsCsv
                },
                lastPingMs = System.currentTimeMillis()
            )
        )
        val savedId = if (updated.id == 0L) {
            dao.insertServer(updated)
        } else {
            dao.updateServer(updated)
            updated.id
        }
        dao.setActiveServer(savedId)
        val finalServer = updated.copy(id = savedId)

        if (!finalServer.isDemoServer && diag.success) {
            syncFullLibrary(finalServer, onSyncProgress)
        }
        return finalServer to diag
    }

    suspend fun switchActiveServer(server: SubsonicServerConfig): PingDiagnosticResult {
        dao.setActiveServer(server.id)
        val diag = sourceFor(server).ping(server)
        if (!server.isDemoServer && diag.success) {
            val existing = dao.getTracksForServerOnce(server.id)
            if (existing.isEmpty()) {
                syncFullLibrary(server)
            }
        }
        return diag
    }

    suspend fun syncServerCatalog(
        server: SubsonicServerConfig,
        onProgress: (Int) -> Unit = {}
    ): Int {
        if (server.isDemoServer) {
            val existing = dao.getTracksForServerOnce(server.id)
            if (existing.isEmpty()) {
                val demoTracks = DemoSubsonicCatalog.seedTracks(server.id)
                dao.insertTracks(demoTracks)
                return demoTracks.size
            }
            return existing.size
        }
        return syncFullLibrary(server, onProgress)
    }

    /**
     * Mirrors the server's whole library into Room. Artist bios/images found earlier (server or
     * Wikipedia) survive the refresh, and songs deleted on the server are pruned, but only when
     * every page of the walk succeeded.
     */
    private suspend fun syncFullLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit = {}): Int {
        val snapshot = sourceFor(server).fetchLibrary(server, onProgress) ?: return 0
        if (snapshot.tracks.isEmpty()) return 0
        val existing = dao.getTracksForServerOnce(server.id).associateBy { it.id }
        val merged = snapshot.tracks.map { fresh ->
            val old = existing[fresh.id] ?: return@map fresh
            fresh.copy(
                artistBio = fresh.artistBio.ifBlank { old.artistBio },
                // Keep enriched external images; server cover URLs are regenerated fresh.
                artistImageUrl = old.artistImageUrl.takeIf { it.isNotBlank() && "getCoverArt" !in it } ?: fresh.artistImageUrl
            )
        }
        merged.chunked(500).forEach { dao.insertTracks(it) }
        if (snapshot.complete) {
            val live = merged.mapTo(HashSet()) { it.id }
            existing.keys.filterNot { it in live }.chunked(500).forEach { dao.deleteTracks(server.id, it) }
        }
        syncServerPlaylists(server)
        return merged.size
    }

    /**
     * Brings the server's playlists in as their own rows (one per server playlist, keyed by its
     * server id, so syncing again never adds a copy). Changed ones are fetched again, unchanged
     * ones keep their songs, ones gone from the server are removed. Playlists made in Ondalune are
     * never touched. Returns false when the server couldn't list them; the cached ones then stay.
     */
    suspend fun syncServerPlaylists(server: SubsonicServerConfig): Boolean {
        val source = sourceFor(server)
        val remote = source.playlists(server) ?: return false
        val cached = dao.getPlaylistsForServerOnce(server.id).filter { it.isServerPlaylist }.associateBy { it.remoteId }
        var known: Set<String>? = null
        for (p in remote) {
            val old = cached[p.id]
            // The server's "changed" stamp; cleared when the songs couldn't be read, so the next
            // sync tries again.
            var stamp = p.changed
            val trackIds = if (old != null && p.changed.isNotBlank() && old.remoteChanged == p.changed) {
                old.trackIds
            } else {
                val songs = source.playlistSongs(server, p.id)
                if (songs != null) {
                    // A song the library sync hasn't seen yet still plays from the playlist.
                    val inLibrary = known ?: dao.getTrackIdsForServer(server.id).toHashSet().also { known = it }
                    songs.filter { it.id !in inLibrary }.distinctBy { it.id }.takeIf { it.isNotEmpty() }?.let { dao.insertTracks(it) }
                    songs.map { it.id }
                } else {
                    stamp = ""
                    old?.trackIds ?: continue
                }
            }
            dao.insertPlaylist(
                SubsonicPlaylist(
                    id = serverPlaylistKey(server.id, p.id),
                    serverId = server.id,
                    name = p.name,
                    comment = p.comment,
                    trackIdsCsv = trackIds.joinToString(","),
                    paletteSeedHex = old?.paletteSeedHex ?: "#FF6B5B",
                    accentHex = old?.accentHex ?: "#70F5FF",
                    updatedAtMs = old?.updatedAtMs ?: System.currentTimeMillis(),
                    remoteId = p.id,
                    owner = p.owner,
                    isPublic = p.isPublic,
                    readOnly = p.owner.isNotBlank() && !p.owner.equals(server.username.trim(), ignoreCase = true),
                    coverArtUrl = p.coverArtId.takeIf { it.isNotBlank() }?.let { client.buildCoverArtUrl(server, it) }.orEmpty(),
                    remoteChanged = stamp
                )
            )
        }
        val live = remote.mapTo(HashSet()) { it.id }
        cached.values.filter { it.remoteId !in live }.forEach { dao.deletePlaylist(it.id) }
        return true
    }

    suspend fun toggleStarTrack(server: SubsonicServerConfig, track: SubsonicTrack) {
        val newStarred = !track.starred
        dao.updateTrackStarred(track.id, newStarred, if (newStarred) System.currentTimeMillis() else 0L)
        sourceFor(server).setFavourite(server, track.id, newStarred)
    }

    suspend fun recordPlay(server: SubsonicServerConfig, track: SubsonicTrack) {
        dao.incrementPlayCount(track.id)
        sourceFor(server).reportPlayed(server, track)
    }

    suspend fun fetchLyricsResult(
        server: SubsonicServerConfig,
        track: SubsonicTrack
    ): io.github.roggzuay.ondalune.data.model.TrackLyricsResult {
        return sourceFor(server).lyrics(server, track)
    }

    /** The library row for this phone's music, created on first use. Never the active library. */
    suspend fun phoneSource(): SubsonicServerConfig {
        dao.getAllServersOnce().firstOrNull { it.isLocalSource }?.let { return it }
        val row = SubsonicServerConfig(
            name = "This phone",
            baseUrl = "",
            username = "",
            passwordOrToken = "",
            serverSoftware = "On-device music",
            extensionsCsv = "",
            sourceType = SourceType.LOCAL.name
        )
        return row.copy(id = dao.insertServer(row))
    }

    fun observeLocalFolders() = dao.observeLocalFolders()

    suspend fun localFolders() = dao.getLocalFoldersOnce()

    /** What adding a picked folder did. */
    enum class FolderAdd { ADDED, ALREADY_INCLUDED, REPLACED_INNER }

    /**
     * Adds a picked folder ([treeUri], already granted). A folder inside one already picked adds
     * nothing; one that holds picked folders takes their place, so no song is listed twice.
     */
    suspend fun addLocalFolder(treeUri: String, treeDocId: String, name: String): FolderAdd {
        val existing = dao.getLocalFoldersOnce()
        fun docId(f: LocalFolder) = runCatching { android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(f.treeUri)) }.getOrDefault("")
        if (existing.any { it.treeUri == treeUri || isInside(treeDocId, docId(it)) }) return FolderAdd.ALREADY_INCLUDED
        val inner = existing.filter { isInside(docId(it), treeDocId) }
        inner.forEach { removeLocalFolder(it, keepPermission = false) }
        dao.upsertLocalFolder(LocalFolder(treeUri = treeUri, name = name, location = folderLocation(treeDocId)))
        return if (inner.isEmpty()) FolderAdd.ADDED else FolderAdd.REPLACED_INNER
    }

    /** Forgets a folder and its songs (the files stay on the phone). */
    suspend fun removeLocalFolder(folder: LocalFolder, keepPermission: Boolean = false) {
        val source = dao.getAllServersOnce().firstOrNull { it.isLocalSource }
        val ids = dao.getLocalFiles(folder.treeUri).map { it.trackId }
        if (source != null) ids.chunked(500).forEach { dao.deleteTracks(source.id, it) }
        ids.chunked(500).forEach { dao.deleteLocalFiles(it) }
        dao.deleteLocalFolder(folder.treeUri)
        if (!keepPermission) folderPermissions?.release(folder.treeUri)
    }

    /**
     * Brings one picked folder's songs up to date. Unchanged files (same size and date) keep their
     * row without being read again; changed and new ones have their tags read; songs whose files
     * are gone are removed, but only after a complete walk of a folder that could be read. A folder
     * that can't be read keeps its songs and is marked inaccessible. Hearts, play counts and edits
     * given in the app survive. Returns the folder as it stands after the scan.
     */
    suspend fun scanLocalFolder(
        folder: LocalFolder,
        reader: FolderReader,
        onProgress: (found: Int, read: Int) -> Unit = { _, _ -> }
    ): LocalFolder {
        val source = phoneSource()
        val uri = android.net.Uri.parse(folder.treeUri)
        if (!reader.canRead(uri)) {
            return folder.copy(accessible = false).also { dao.upsertLocalFolder(it) }
        }
        val files = runCatching { reader.listMusic(uri) { onProgress(it, 0) } }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return folder.copy(accessible = false).also { dao.upsertLocalFolder(it) }
        }
        val records = dao.getLocalFiles(folder.treeUri).associateBy { it.trackId }
        val existing = dao.getTracksForServerOnce(source.id).associateBy { it.id }
        val fresh = ArrayList<SubsonicTrack>()
        val freshRecords = ArrayList<LocalFileRecord>()
        val albumCovers = HashMap<String, String>()
        var read = 0
        for (file in files) {
            kotlin.coroutines.coroutineContext.ensureActive()
            val id = LOCAL_TRACK_PREFIX + file.documentId
            val record = records[id]
            val old = existing[id]
            if (old != null && record != null && record.size == file.size && record.modifiedMs == file.modifiedMs) continue
            val (tags, picture) = reader.readTags(uri, file.documentId) ?: continue
            read++
            onProgress(files.size, read)
            var track = folderTrackFrom(file, tags, source.id, coverArtUrl = file.coverUri.orEmpty())
            if (track.coverArtUrl.isBlank()) {
                track = track.copy(coverArtUrl = albumCovers[track.albumId] ?: picture?.let { reader.saveCover(track.albumId, it) }.orEmpty())
                if (track.coverArtUrl.isNotBlank()) albumCovers[track.albumId] = track.coverArtUrl
            }
            fresh += if (old == null) track else track.copy(
                starred = old.starred,
                starredAtMs = old.starredAtMs,
                playCount = old.playCount,
                artistBio = old.artistBio,
                artistImageUrl = old.artistImageUrl
            )
            freshRecords += LocalFileRecord(id, folder.treeUri, file.size, file.modifiedMs)
            if (fresh.size >= 200) {
                dao.insertTracks(fresh); dao.upsertLocalFiles(freshRecords)
                fresh.clear(); freshRecords.clear()
            }
        }
        if (fresh.isNotEmpty()) {
            dao.insertTracks(fresh); dao.upsertLocalFiles(freshRecords)
        }
        // The walk was complete: songs whose files are gone go too.
        val live = files.mapTo(HashSet()) { LOCAL_TRACK_PREFIX + it.documentId }
        val gone = records.keys.filterNot { it in live }
        gone.chunked(500).forEach { dao.deleteTracks(source.id, it); dao.deleteLocalFiles(it) }
        return folder.copy(trackCount = live.size, lastScanMs = System.currentTimeMillis(), accessible = true)
            .also { dao.upsertLocalFolder(it) }
    }

    /**
     * Removes songs added by the old whole-phone scan (1.5.0), whose ids are MediaStore numbers:
     * music on this phone now comes only from folders picked in Settings. Returns how many went.
     */
    suspend fun removeWholePhoneScanSongs(): Int {
        val source = dao.getAllServersOnce().firstOrNull { it.isLocalSource } ?: return 0
        val legacy = dao.getTrackIdsForServer(source.id).filter { it.removePrefix(LOCAL_TRACK_PREFIX).all(Char::isDigit) }
        legacy.chunked(500).forEach { dao.deleteTracks(source.id, it) }
        return legacy.size
    }

    /** Forgets this phone's music and every picked folder (the files stay on the phone). */
    suspend fun removePhoneSource(source: SubsonicServerConfig) {
        dao.getLocalFoldersOnce().forEach { removeLocalFolder(it) }
        dao.getTracksForServerOnce(source.id).map { it.id }.chunked(500).forEach { dao.deleteTracks(source.id, it) }
        dao.deleteServer(source.id)
    }

    suspend fun createPlaylist(
        serverId: Long,
        name: String,
        comment: String,
        trackIds: List<String>,
        seedHex: String = "#FF6B5B",
        accentHex: String = "#70F5FF"
    ) {
        val playlist = SubsonicPlaylist(
            id = "pl_${UUID.randomUUID().toString().take(8)}",
            serverId = serverId,
            name = name.trim(),
            comment = comment.trim().ifBlank { "Custom OpenSubsonic playlist" },
            trackIdsCsv = trackIds.joinToString(","),
            paletteSeedHex = seedHex,
            accentHex = accentHex
        )
        dao.insertPlaylist(playlist)
    }

    /**
     * Adds songs to a playlist. A server playlist changes on the server first (only songs from that
     * server, only if it's yours), then here; returns false when the server refused, and nothing
     * changes.
     */
    suspend fun addTracksToPlaylist(playlist: SubsonicPlaylist, trackIds: List<String>): Boolean {
        val added = trackIds.filterNot { it in playlist.trackIds }.distinct()
        if (added.isEmpty()) return true
        if (playlist.isServerPlaylist) {
            val server = dao.getAllServersOnce().find { it.id == playlist.serverId } ?: return false
            if (playlist.readOnly || added.any { it.startsWith(LOCAL_TRACK_PREFIX) }) return false
            if (!sourceFor(server).addToPlaylist(server, playlist.remoteId, added)) return false
        }
        dao.insertPlaylist(
            playlist.copy(
                trackIdsCsv = (playlist.trackIds + added).joinToString(","),
                updatedAtMs = System.currentTimeMillis(),
                remoteChanged = "" // the server's stamp moved; read it again on the next sync
            )
        )
        return true
    }

    /** Deletes a playlist; a server playlist is deleted on the server (only if it's yours). */
    suspend fun deletePlaylist(playlist: SubsonicPlaylist): Boolean {
        if (playlist.isServerPlaylist) {
            val server = dao.getAllServersOnce().find { it.id == playlist.serverId } ?: return false
            if (playlist.readOnly || !sourceFor(server).deletePlaylist(server, playlist.remoteId)) return false
        }
        dao.deletePlaylist(playlist.id)
        return true
    }

    suspend fun deleteServer(serverId: Long) {
        dao.deleteServer(serverId)
    }

    fun deriveAlbums(tracks: List<SubsonicTrack>, category: AlbumListType): List<SubsonicAlbum> {
        val base = DemoSubsonicCatalog.deriveAlbumsFromTracks(tracks)
        return when (category) {
            AlbumListType.NEWEST -> base.sortedByDescending { it.year }
            AlbumListType.FREQUENT -> {
                val playByAlbum = tracks.groupBy { it.albumId }
                    .mapValues { (_, list) -> list.sumOf { it.playCount } }
                base.sortedByDescending { playByAlbum[it.id] ?: 0 }
            }
            AlbumListType.RECENT -> base.reversed()
            AlbumListType.STARRED -> base.filter { it.starred }.ifEmpty { base }
            AlbumListType.RANDOM -> base.shuffled()
        }
    }

    fun deriveArtists(
        tracks: List<SubsonicTrack>,
        fallbackToAlbumArt: Boolean = false
    ): List<SubsonicArtist> =
        DemoSubsonicCatalog.deriveArtistsFromTracks(tracks, fallbackToAlbumArt)

    suspend fun setArtistFavourite(server: SubsonicServerConfig, artistId: String, favourite: Boolean) =
        sourceFor(server).setFavourite(server, artistId, favourite, isArtist = true)

    /** Stores the artist's portrait and bio; returns the server's similar artists' ids. */
    suspend fun enrichArtistMetadata(
        server: SubsonicServerConfig,
        artistId: String,
        artistName: String
    ): List<String> {
        if (server.isDemoServer || artistId.isBlank()) return emptyList()
        val info2 = sourceFor(server).artistInfo(server, artistId)
        // Server bio first; Wikipedia only fills what the server left empty.
        val wiki = if (info2?.bio.isNullOrBlank()) client.fetchWikipediaArtistSummary(artistName) else null
        val image = info2?.imageUrl?.takeIf { it.isNotBlank() } ?: wiki?.imageUrl.orEmpty()
        val bio = info2?.bio?.takeIf { it.isNotBlank() } ?: wiki?.bio.orEmpty()
        if (image.isNotBlank() || bio.isNotBlank()) {
            dao.updateArtistMetadataForServer(
                serverId = server.id,
                artistId = artistId,
                artistName = artistName,
                artistImageUrl = image,
                artistBio = bio
            )
        }
        return info2?.similarArtistIds.orEmpty()
    }

    fun deriveGenres(tracks: List<SubsonicTrack>): List<SubsonicGenre> =
        DemoSubsonicCatalog.deriveGenresFromTracks(tracks)

    fun deriveFolders(tracks: List<SubsonicTrack>): List<SubsonicFolder> =
        DemoSubsonicCatalog.deriveFoldersFromTracks(tracks)
}

