package io.github.roggzuay.ondalune.data.source

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import java.io.File

/** One audio file as MediaStore describes it; the fields [localTrackFrom] needs. */
data class LocalAudioRow(
    val mediaId: Long,
    val title: String?,
    val artist: String?,
    val artistId: Long,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    /** MediaStore's track number, which may carry the disc as thousands (2003 = disc 2, track 3). */
    val track: Int,
    val year: Int,
    val path: String?,
    val genre: String?,
    /** Bits per second; 0 when MediaStore doesn't say (before Android 11). */
    val bitrate: Int,
    val sizeBytes: Long
)

/**
 * The music stored on this phone, read from MediaStore (Android's index of audio files). Nothing is
 * copied: songs play straight from their files, and album covers come from MediaStore's
 * thumbnails. Needs the "Music and audio" permission ([permission]). Hearts and ratings stay in the
 * app, and lyrics come from `.lrc` files beside the songs ([parseLrc] reads them).
 */
class LocalMusicSource(
    private val context: Context,
    private val parseLrc: (String) -> List<LyricLine>
) : MusicSource {
    override suspend fun ping(server: SubsonicServerConfig) = PingDiagnosticResult(
        success = hasPermission(context),
        statusMessage = if (hasPermission(context)) "Music on this phone" else "Allow \"Music and audio\" to play music on this phone"
    )

    /** The phone's library comes from [scan], not a sync. */
    override suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit) = null

    override suspend fun playablePath(server: SubsonicServerConfig, track: SubsonicTrack) = filePath(context, track)

    override suspend fun lyrics(server: SubsonicServerConfig, track: SubsonicTrack) =
        sidecarLyrics(filePath(context, track), parseLrc)

    companion object {
        /** The runtime permission that lets the app read audio files. */
        val permission: String
            get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        /** The file [track] plays from, or null when it is gone or unreadable. */
        fun filePath(context: Context, track: SubsonicTrack): String? {
            val mediaId = track.id.removePrefix(LOCAL_TRACK_PREFIX).toLongOrNull() ?: return null
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
            @Suppress("DEPRECATION") // DATA is still filled, and readable with the audio permission.
            val path = runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            return path?.takeIf { File(it).canRead() }
        }

        /** Lyrics from a `.lrc` file beside the song (same name), the usual way to keep them with local music. */
        fun sidecarLyrics(path: String?, parse: (String) -> List<LyricLine>): TrackLyricsResult {
            val lrc = path?.let { File(it.substringBeforeLast('.') + ".lrc") }?.takeIf { it.canRead() }
                ?: return TrackLyricsResult()
            val raw = runCatching { lrc.readText() }.getOrNull().orEmpty()
            val lines = parse(raw)
            return when {
                lines.isNotEmpty() -> TrackLyricsResult(LyricsAvailability.SYNCED, lines, sourceLabel = "Lyrics file")
                raw.isNotBlank() -> TrackLyricsResult(LyricsAvailability.PLAIN_UNSYNCED, plainLyrics = raw, sourceLabel = "Lyrics file")
                else -> TrackLyricsResult()
            }
        }
    }

    /** Every song MediaStore lists as music, as library tracks of the source [serverId]; null without the permission. */
    fun scan(serverId: Long): List<SubsonicTrack>? {
        if (!hasPermission(context)) return null
        val columns = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            @Suppress("DEPRECATION") add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= 30) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.BITRATE)
            }
        }.toTypedArray()
        val rows = mutableListOf<LocalAudioRow>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { c ->
            fun idx(name: String) = c.getColumnIndex(name)
            val genreIdx = if (Build.VERSION.SDK_INT >= 30) idx(MediaStore.Audio.Media.GENRE) else -1
            val bitrateIdx = if (Build.VERSION.SDK_INT >= 30) idx(MediaStore.Audio.Media.BITRATE) else -1
            @Suppress("DEPRECATION") val dataIdx = idx(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                rows += LocalAudioRow(
                    mediaId = c.getLong(idx(MediaStore.Audio.Media._ID)),
                    title = c.getString(idx(MediaStore.Audio.Media.TITLE)),
                    artist = c.getString(idx(MediaStore.Audio.Media.ARTIST)),
                    artistId = c.getLong(idx(MediaStore.Audio.Media.ARTIST_ID)),
                    album = c.getString(idx(MediaStore.Audio.Media.ALBUM)),
                    albumId = c.getLong(idx(MediaStore.Audio.Media.ALBUM_ID)),
                    durationMs = c.getLong(idx(MediaStore.Audio.Media.DURATION)),
                    track = c.getInt(idx(MediaStore.Audio.Media.TRACK)),
                    year = c.getInt(idx(MediaStore.Audio.Media.YEAR)),
                    path = c.getString(dataIdx),
                    genre = if (genreIdx >= 0) c.getString(genreIdx) else null,
                    bitrate = if (bitrateIdx >= 0) c.getInt(bitrateIdx) else 0,
                    sizeBytes = c.getLong(idx(MediaStore.Audio.Media.SIZE))
                )
            }
        } ?: return null
        return rows.map { localTrackFrom(it, serverId) }
    }
}

/** MediaStore's word for a missing tag. */
private const val UNKNOWN_TAG = "<unknown>"

/** The library track for one MediaStore [row]. Ids are prefixed so they never clash with a server's. */
fun localTrackFrom(row: LocalAudioRow, serverId: Long, sdkInt: Int = Build.VERSION.SDK_INT): SubsonicTrack {
    fun tag(value: String?) = value?.trim()?.takeIf { it.isNotEmpty() && it != UNKNOWN_TAG }
    val fileName = row.path?.substringAfterLast('/')
    val artist = tag(row.artist) ?: "Unknown artist"
    val bitrateKbps = when {
        row.bitrate > 0 -> row.bitrate / 1000
        row.durationMs > 0 && row.sizeBytes > 0 -> (row.sizeBytes * 8 / row.durationMs).toInt()
        else -> 0
    }
    // Android 10 and later read covers through the album's thumbnail; older versions through albumart.
    val cover = if (sdkInt >= 29) "content://media/external/audio/albums/${row.albumId}"
    else "content://media/external/audio/albumart/${row.albumId}"
    return SubsonicTrack(
        id = "$LOCAL_TRACK_PREFIX${row.mediaId}",
        serverId = serverId,
        title = tag(row.title) ?: fileName?.substringBeforeLast('.') ?: "Untitled",
        artist = artist,
        artistId = "local-artist:${row.artistId}",
        album = tag(row.album) ?: "Unknown album",
        albumId = "local-album:${row.albumId}",
        durationSec = (row.durationMs / 1000).toInt(),
        bitRate = bitrateKbps,
        suffix = fileName?.substringAfterLast('.', "")?.uppercase().orEmpty(),
        // MediaStore doesn't know these; 0 hides them rather than guessing.
        bitDepth = 0,
        samplingRate = 0,
        trackNumber = (row.track % 1000).coerceAtLeast(1),
        year = row.year.coerceAtLeast(0),
        genre = tag(row.genre).orEmpty(),
        folderPath = row.path?.substringBeforeLast('/', "")?.removePrefix("/storage/emulated/0").orEmpty(),
        coverArtUrl = cover,
        bpm = 0,
        musicalKey = "",
        userRating = 0
    )
}
