package io.github.roggzuay.ondalune.data.source

import android.content.Context
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import io.github.roggzuay.ondalune.data.model.isLocalSource
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient

/** What a source can do beyond listing and playing songs; the app offers only what it supports. */
enum class SourceCapability {
    /** Hearts and ratings are saved on the server too, not only in the app. */
    SYNC_FAVOURITES,
    /** Plays are reported to the server (play counts, scrobbling). */
    SCROBBLE,
    /** The server knows artists' photos, biographies and similar artists. */
    ARTIST_INFO
}

/**
 * A place songs come from: a Subsonic server, or this phone's storage. Each library row
 * ([SubsonicServerConfig]) names its kind in `sourceType`; [MusicSources.forServer] picks the
 * implementation. The library itself is kept in the app's database whatever the source.
 */
interface MusicSource {
    val capabilities: Set<SourceCapability>

    /** Where [track] plays from: a stream address or a file path, worked out when it plays. Null when it can't play. */
    suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack): String?

    suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack): TrackLyricsResult
}

/** A Navidrome or other OpenSubsonic server. */
class OpenSubsonicSource(private val client: OpenSubsonicClient) : MusicSource {
    override val capabilities = SourceCapability.entries.toSet()

    override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) =
        client.buildStreamUrl(server, track.id)

    override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) =
        client.fetchTrackLyrics(server, track)
}

object MusicSources {
    fun forServer(context: Context, client: OpenSubsonicClient, server: SubsonicServerConfig): MusicSource =
        if (server.isLocalSource) LocalMusicSource(context.applicationContext, client::parseLrcString) else OpenSubsonicSource(client)
}
