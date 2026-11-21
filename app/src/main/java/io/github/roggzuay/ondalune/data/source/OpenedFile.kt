package io.github.roggzuay.ondalune.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import java.io.File

/**
 * A song file another app opened with Ondalune ("Open with" in a file manager or a chat). It plays
 * as it is, from the address the app handed over, and never joins the library.
 */
object OpenedFile {
    const val PREFIX = LOCAL_TRACK_PREFIX + "opened:"

    /** The address [track] plays from, or null when it isn't an opened file. */
    fun uriOf(track: SubsonicTrack): String? = track.id.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    /** "Night Drive.flac" → "Night Drive"; a bare address keeps its last part. */
    fun titleFrom(displayName: String?, uri: String): String {
        val name = displayName?.takeIf { it.isNotBlank() } ?: Uri.decode(uri).substringAfterLast('/')
        return name.substringBeforeLast('.').ifBlank { name }
    }

    /** Whether [uri] can still be opened: access to a shared file ends with the app that shared it. */
    fun canRead(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    /** The song at [uri], with whatever its tags say. Reads the file, so call it off the main thread. */
    fun track(context: Context, uri: Uri, serverId: Long): SubsonicTrack {
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        val retriever = MediaMetadataRetriever()
        try {
            val tags = runCatching { retriever.setDataSource(context, uri) }.isSuccess
            fun key(k: Int) = if (tags) runCatching { retriever.extractMetadata(k) }.getOrNull()?.takeIf { it.isNotBlank() } else null
            val title = key(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: titleFrom(displayName, uri.toString())
            val artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: "Unknown artist"
            val album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: title
            val id = PREFIX + uri
            val cover = if (tags) runCatching { retriever.embeddedPicture }.getOrNull()?.let { bytes ->
                runCatching {
                    File(context.cacheDir, "opened-covers").apply { mkdirs() }
                        .resolve("${id.hashCode().toUInt()}.jpg").apply { writeBytes(bytes) }.absolutePath
                }.getOrNull()
            } else null
            return SubsonicTrack(
                id = id,
                serverId = serverId,
                title = title,
                artist = artist,
                artistId = "",
                album = album,
                albumId = "",
                durationSec = ((key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L).toInt(),
                bitRate = (key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000,
                suffix = (displayName ?: uri.toString()).substringAfterLast('.', "").takeIf { it.length in 2..5 }?.uppercase() ?: "",
                samplingRate = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0 else 0,
                bitDepth = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0 else 0,
                year = key(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "",
                folderPath = "",
                coverArtUrl = cover ?: ""
            )
        } finally {
            runCatching { retriever.release() }
        }
    }
}
