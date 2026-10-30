package io.github.roggzuay.ondalune.data.repository

import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.local.OndaluneDao
import io.github.roggzuay.ondalune.data.model.AlbumListType
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicArtist
import io.github.roggzuay.ondalune.data.model.SubsonicFolder
import io.github.roggzuay.ondalune.data.model.SubsonicGenre
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class OndaluneRepository(
    private val dao: OndaluneDao,
    val client: OpenSubsonicClient
) {
    val serversFlow: Flow<List<SubsonicServerConfig>> = dao.observeServers()

    fun observeTracksForServer(serverId: Long): Flow<List<SubsonicTrack>> =
        dao.observeTracksForServer(serverId)

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
        val active = dao.getActiveServer() ?: existingServers.first().also {
            dao.setActiveServer(it.id)
        }
        val existingTracks = dao.getTracksForServerOnce(active.id)
        if (active.isDemoServer && existingTracks.isEmpty()) {
            dao.insertTracks(DemoSubsonicCatalog.seedTracks(active.id))
            DemoSubsonicCatalog.seedPlaylists(active.id).forEach { dao.insertPlaylist(it) }
        }
        return active
    }

    suspend fun pingAndInspect(server: SubsonicServerConfig): PingDiagnosticResult {
        return client.pingAndInspectServer(server)
    }

    suspend fun saveAndActivateServer(
        server: SubsonicServerConfig,
        onSyncProgress: (Int) -> Unit = {}
    ): Pair<SubsonicServerConfig, PingDiagnosticResult> {
        val diag = client.pingAndInspectServer(server)
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
        val diag = client.pingAndInspectServer(server)
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
        val snapshot = client.fetchFullLibrary(server, onProgress)
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
        return merged.size
    }

    suspend fun toggleStarTrack(server: SubsonicServerConfig, track: SubsonicTrack) {
        val newStarred = !track.starred
        dao.updateTrackStarred(track.id, newStarred)
        client.setStarredRemote(server, track.id, newStarred)
    }

    suspend fun setTrackRating(server: SubsonicServerConfig, track: SubsonicTrack, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        dao.updateTrackRating(track.id, clamped)
        client.setRatingRemote(server, track.id, clamped)
    }

    suspend fun recordPlay(server: SubsonicServerConfig, track: SubsonicTrack) {
        dao.incrementPlayCount(track.id)
        client.scrobbleRemote(server, track.id, submission = true)
    }

    suspend fun fetchLyricsResult(
        server: SubsonicServerConfig,
        track: SubsonicTrack
    ): io.github.roggzuay.ondalune.data.model.TrackLyricsResult {
        return client.fetchTrackLyrics(server, track)
    }

    /**
     * Returns time-synced lyrics only when genuinely available from embedded LRC or OpenSubsonic
     * `structuredLyrics`. Never fabricates fake synchronized lyric lines for real tracks.
     */
    suspend fun fetchLyrics(server: SubsonicServerConfig, track: SubsonicTrack): List<LyricLine> {
        return client.fetchSyncedLyrics(server, track)
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

    suspend fun addTracksToPlaylist(playlist: SubsonicPlaylist, trackIds: List<String>) {
        val merged = (playlist.trackIds + trackIds).distinct()
        dao.insertPlaylist(playlist.copy(trackIdsCsv = merged.joinToString(","), updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
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

    suspend fun enrichArtistMetadata(
        server: SubsonicServerConfig,
        artistId: String,
        artistName: String
    ) {
        if (server.isDemoServer || artistId.isBlank()) return
        val info2 = client.fetchArtistInfo2(server, artistId)
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
    }

    fun deriveGenres(tracks: List<SubsonicTrack>): List<SubsonicGenre> =
        DemoSubsonicCatalog.deriveGenresFromTracks(tracks)

    fun deriveFolders(tracks: List<SubsonicTrack>): List<SubsonicFolder> =
        DemoSubsonicCatalog.deriveFoldersFromTracks(tracks)
}

