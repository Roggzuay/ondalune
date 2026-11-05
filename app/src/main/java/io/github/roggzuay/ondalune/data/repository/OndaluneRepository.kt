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
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.isLocalSource
import io.github.roggzuay.ondalune.data.source.LocalMusicSource
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
    /** Reads this phone's music; null where there is no phone (tests). */
    private val phone: LocalMusicSource? = null,
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
        return merged.size
    }

    suspend fun toggleStarTrack(server: SubsonicServerConfig, track: SubsonicTrack) {
        val newStarred = !track.starred
        dao.updateTrackStarred(track.id, newStarred)
        sourceFor(server).setFavourite(server, track.id, newStarred)
    }

    suspend fun setTrackRating(server: SubsonicServerConfig, track: SubsonicTrack, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        dao.updateTrackRating(track.id, clamped)
        sourceFor(server).setRating(server, track, clamped)
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

    /**
     * Reads this phone's music into the library. Hearts, ratings and play counts given in the app
     * survive a rescan; songs no longer on the phone are removed. Returns how many songs the library has from the phone.
     */
    suspend fun scanPhoneMusic(source: SubsonicServerConfig): Int {
        // Without the permission nothing can be read: keep the songs rather than drop them all.
        val scanned = phone?.scan(source.id) ?: return dao.getTracksForServerOnce(source.id).size
        val existing = dao.getTracksForServerOnce(source.id).associateBy { it.id }
        val merged = scanned.map { fresh ->
            val old = existing[fresh.id] ?: return@map fresh
            fresh.copy(
                starred = old.starred,
                userRating = old.userRating,
                playCount = old.playCount,
                artistBio = old.artistBio,
                artistImageUrl = old.artistImageUrl
            )
        }
        merged.chunked(500).forEach { dao.insertTracks(it) }
        val live = merged.mapTo(HashSet()) { it.id }
        existing.keys.filterNot { it in live }.chunked(500).forEach { dao.deleteTracks(source.id, it) }
        return merged.size
    }

    /** Forgets this phone's music (the files stay on the phone). */
    suspend fun removePhoneSource(source: SubsonicServerConfig) {
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

