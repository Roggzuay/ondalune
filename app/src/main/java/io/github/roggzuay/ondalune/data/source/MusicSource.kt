package io.github.roggzuay.ondalune.data.source

import android.content.Context
import io.github.roggzuay.ondalune.BuildConfig
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.RemotePlaylist
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.source
import io.github.roggzuay.ondalune.data.network.JellyfinClient
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient.LibrarySnapshot
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient.RemoteArtistMeta
import java.util.UUID

/**
 * A place songs come from: an OpenSubsonic or Jellyfin server, or this phone's storage. Each
 * library row ([SubsonicServerConfig]) names its kind in `sourceType`; [MusicSources.forServer]
 * picks the implementation. The library itself is kept in the app's database whatever the source.
 * What a source doesn't support does nothing: hearts and plays then stay in the app.
 */
interface MusicSource {
    suspend fun ping(server: SubsonicServerConfig): PingDiagnosticResult

    /** Every song, for a library sync; null for a source that fills the library another way (this phone). */
    suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit): LibrarySnapshot?

    /** Where [track] plays from: a stream address or a file path, worked out when it plays. Null when it can't play. */
    suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack): String?

    /** Where [track] downloads from for offline listening; null when there is nothing to download. */
    fun downloadUrl(server: SubsonicServerConfig, track: SubsonicTrack): String? = null

    suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack): TrackLyricsResult

    /** Saves a heart on a song or artist ([itemId]) on the server. */
    suspend fun setFavourite(server: SubsonicServerConfig, itemId: String, favourite: Boolean, isArtist: Boolean = false) {}

    /** Tells the server a song was played (play counts, scrobbling). */
    suspend fun reportPlayed(server: SubsonicServerConfig, track: SubsonicTrack) {}

    /** An artist's photo, biography and similar artists, when the source knows them. */
    suspend fun artistInfo(server: SubsonicServerConfig, artistId: String): RemoteArtistMeta? = null

    /** The server's playlists; null when it can't say (offline, refused, or no playlists here). */
    suspend fun playlists(server: SubsonicServerConfig): List<RemotePlaylist>? = null

    /** A server playlist's songs in order; null when they couldn't be read. */
    suspend fun playlistSongs(server: SubsonicServerConfig, playlistId: String): List<SubsonicTrack>? = null

    /** Adds songs to a server playlist the user owns; false when the server refused. */
    suspend fun addToPlaylist(server: SubsonicServerConfig, playlistId: String, trackIds: List<String>): Boolean = false

    /** Deletes a server playlist the user owns; false when the server refused. */
    suspend fun deletePlaylist(server: SubsonicServerConfig, playlistId: String): Boolean = false
}

/** A Navidrome or other OpenSubsonic server. */
class OpenSubsonicSource(private val client: OpenSubsonicClient) : MusicSource {
    override suspend fun ping(server: SubsonicServerConfig) = client.pingAndInspectServer(server)

    override suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit) =
        client.fetchFullLibrary(server, onProgress)

    override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) =
        client.buildStreamUrl(server, track.id)

    override fun downloadUrl(server: SubsonicServerConfig, track: SubsonicTrack) =
        client.buildDownloadUrl(server, track.id).ifBlank { client.buildStreamUrl(server, track.id) }

    override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) =
        client.fetchTrackLyrics(server, track)

    override suspend fun setFavourite(server: SubsonicServerConfig, itemId: String, favourite: Boolean, isArtist: Boolean) {
        client.setStarredRemote(server, itemId, favourite, idParam = if (isArtist) "artistId" else "id")
    }

    override suspend fun reportPlayed(server: SubsonicServerConfig, track: SubsonicTrack) {
        client.scrobbleRemote(server, track.id, submission = true)
    }

    override suspend fun artistInfo(server: SubsonicServerConfig, artistId: String) = client.fetchArtistInfo2(server, artistId)

    override suspend fun playlists(server: SubsonicServerConfig) = client.fetchPlaylists(server)

    override suspend fun playlistSongs(server: SubsonicServerConfig, playlistId: String) =
        client.fetchPlaylistSongs(server, playlistId)

    override suspend fun addToPlaylist(server: SubsonicServerConfig, playlistId: String, trackIds: List<String>) =
        client.addToRemotePlaylist(server, playlistId, trackIds)

    override suspend fun deletePlaylist(server: SubsonicServerConfig, playlistId: String) =
        client.deleteRemotePlaylist(server, playlistId)
}

/** A Jellyfin server. */
class JellyfinSource(private val client: JellyfinClient) : MusicSource {
    override suspend fun ping(server: SubsonicServerConfig) = client.ping(server)

    override suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit) = client.fetchLibrary(server, onProgress)

    override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) = client.streamUrl(server, track.id)

    override fun downloadUrl(server: SubsonicServerConfig, track: SubsonicTrack) = client.downloadUrl(server, track.id)

    override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) = client.lyrics(server, track.id)

    override suspend fun setFavourite(server: SubsonicServerConfig, itemId: String, favourite: Boolean, isArtist: Boolean) =
        client.setFavourite(server, itemId, favourite)

    override suspend fun reportPlayed(server: SubsonicServerConfig, track: SubsonicTrack) = client.markPlayed(server, track.id)

    override suspend fun artistInfo(server: SubsonicServerConfig, artistId: String) = client.artist(server, artistId)
}

object MusicSources {
    @Volatile private var jellyfin: JellyfinClient? = null

    /** One Jellyfin client per app, signed in as this install ([deviceId]). */
    fun jellyfin(context: Context): JellyfinClient = jellyfin ?: synchronized(this) {
        jellyfin ?: JellyfinClient(deviceId(context), BuildConfig.VERSION_NAME).also { jellyfin = it }
    }

    /** A random id for this install, which Jellyfin ties its sign-ins to. */
    private fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("ondalune_prefs", Context.MODE_PRIVATE)
        return prefs.getString("jellyfin_device_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("jellyfin_device_id", it).apply() }
    }

    fun forServer(context: Context, client: OpenSubsonicClient, server: SubsonicServerConfig): MusicSource = when (server.source) {
        SourceType.LOCAL -> LocalMusicSource(context.applicationContext, client::parseLrcString)
        SourceType.JELLYFIN -> JellyfinSource(jellyfin(context))
        SourceType.SUBSONIC -> OpenSubsonicSource(client)
    }
}
