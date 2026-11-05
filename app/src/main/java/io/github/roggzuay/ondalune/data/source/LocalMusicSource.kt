package io.github.roggzuay.ondalune.data.source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.roggzuay.ondalune.data.local.OndaluneDatabase
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult

/**
 * The music in folders picked in Settings → Music sources ([FolderReader] finds it). Nothing is
 * copied: songs play straight from their documents through the folder's kept permission, so no
 * storage permission is needed. Hearts stay in the app, and lyrics come from `.lrc` files beside
 * the songs, read during the scan ([parseLrc] reads them).
 */
class LocalMusicSource(
    private val context: Context,
    private val parseLrc: (String) -> List<LyricLine>
) : MusicSource {
    override suspend fun ping(server: SubsonicServerConfig) = PingDiagnosticResult(success = true, statusMessage = "Music folders on this phone")

    /** The library comes from scanning picked folders, not a sync. */
    override suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit) = null

    override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) = documentUri(context, track)

    override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) = lyricsFrom(track.syncedLyricsRaw, parseLrc)

    companion object {
        /** The `content://` document [track] plays from, or null when its folder is no longer picked. */
        suspend fun documentUri(context: Context, track: SubsonicTrack): String? {
            val folder = OndaluneDatabase.getInstance(context).dao().folderOfTrack(track.id) ?: return null
            return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(folder), track.id.removePrefix(LOCAL_TRACK_PREFIX)).toString()
        }

        /** Lyrics from a `.lrc` file's text: synced when it has timestamps, plain otherwise. */
        fun lyricsFrom(raw: String, parse: (String) -> List<LyricLine>): TrackLyricsResult {
            if (raw.isBlank()) return TrackLyricsResult()
            val lines = parse(raw)
            return if (lines.isNotEmpty()) TrackLyricsResult(LyricsAvailability.SYNCED, lines, sourceLabel = "Lyrics file")
            else TrackLyricsResult(LyricsAvailability.PLAIN_UNSYNCED, plainLyrics = raw, sourceLabel = "Lyrics file")
        }
    }
}
