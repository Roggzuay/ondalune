package io.github.roggzuay.ondalune.data.source

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest

/**
 * The music formats Ondalune reads from picked folders: the ones Android decodes on every version
 * it runs on. Anything else in a folder (videos, playlists, other audio formats) is left out.
 */
object SupportedAudio {
    /** File extension → the label the app shows. M4A holds AAC or ALAC. */
    val extensions = linkedMapOf(
        "flac" to "FLAC", "mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC",
        "ogg" to "OGG", "oga" to "OGG", "opus" to "OPUS", "wav" to "WAV"
    )

    /** For people: "FLAC, MP3, AAC and ALAC (.m4a), Ogg Vorbis, Opus, WAV". */
    const val DESCRIPTION = "FLAC, MP3, AAC and ALAC (.m4a), Ogg Vorbis, Opus and WAV"

    /**
     * Folders that hold sounds rather than music, by Android's conventions (Environment's
     * notification, ringtone, alarm and recording folders, voice notes, app data and caches).
     * Skipped inside a picked folder; a folder picked on purpose is always read.
     */
    private val notMusic = setOf(
        "notifications", "ringtones", "alarms", "recordings", "voice recorder", "sound recordings",
        "call recordings", "callrecordings", "voice notes", "whatsapp voice notes", "android",
        "cache", ".cache", "tmp", "temp", ".thumbnails", "lost.dir"
    )

    fun isMusicFile(name: String): Boolean =
        !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in extensions

    fun skipsFolder(name: String): Boolean = name.startsWith(".") || name.lowercase() in notMusic

    /** Pictures a folder may hold as its album cover, best first. */
    val coverNames = listOf("cover", "folder", "front", "album", "albumart")
}

/** One song file found in a picked folder, before its tags are read. */
data class FolderFile(
    val documentId: String,
    val name: String,
    val size: Long,
    val modifiedMs: Long,
    /** The folder's cover picture, if it has one. */
    val coverUri: String?,
    /** The text of a `.lrc` file with the same name, if any. */
    val lyrics: String?
)

/** The tags read from a song file; null fields weren't there. */
data class SongTags(
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val durationMs: Long,
    val track: String?,
    val disc: String?,
    val year: String?,
    val genre: String?,
    val bitrate: Int,
    val sampleRate: Int,
    val bitsPerSample: Int
)

/**
 * The library track for a song [file] in a picked folder. The id is the file's document id, so
 * the same file is one song however often it is scanned. Nothing is guessed: unknown details stay
 * blank or 0.
 */
fun folderTrackFrom(file: FolderFile, tags: SongTags, sourceId: Long, coverArtUrl: String): SubsonicTrack {
    fun tag(v: String?) = v?.trim()?.takeIf { it.isNotEmpty() }
    val extension = file.name.substringAfterLast('.', "").lowercase()
    val artist = tag(tags.artist) ?: tag(tags.albumArtist) ?: "Unknown artist"
    val albumArtist = tag(tags.albumArtist) ?: artist
    val album = tag(tags.album) ?: "Unknown album"
    val bitrateKbps = when {
        tags.bitrate > 0 -> tags.bitrate / 1000
        tags.durationMs > 0 && file.size > 0 -> (file.size * 8 / tags.durationMs).toInt()
        else -> 0
    }
    val relative = file.documentId.substringAfter(':', file.documentId)
    return SubsonicTrack(
        id = LOCAL_TRACK_PREFIX + file.documentId,
        serverId = sourceId,
        title = tag(tags.title) ?: file.name.substringBeforeLast('.'),
        artist = artist,
        artistId = "local-artist:" + shortHash(artist.lowercase()),
        album = album,
        albumId = "local-album:" + shortHash("${albumArtist.lowercase()}|${album.lowercase()}"),
        durationSec = (tags.durationMs / 1000).toInt(),
        bitRate = bitrateKbps,
        suffix = SupportedAudio.extensions[extension] ?: extension.uppercase(),
        bitDepth = tags.bitsPerSample.coerceAtLeast(0),
        samplingRate = tags.sampleRate.coerceAtLeast(0),
        trackNumber = tag(tags.track)?.substringBefore('/')?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        year = tag(tags.year)?.take(4)?.toIntOrNull() ?: 0,
        genre = tag(tags.genre).orEmpty(),
        folderPath = "/" + relative.substringBeforeLast('/', ""),
        coverArtUrl = coverArtUrl,
        syncedLyricsRaw = file.lyrics.orEmpty(),
        bpm = 0,
        musicalKey = "",
        userRating = 0
    )
}

/** True for a folder already inside [parent] (both document ids of the same tree provider). */
fun isInside(childDocId: String, parentDocId: String): Boolean =
    childDocId != parentDocId && childDocId.startsWith(parentDocId.trimEnd('/') + "/") ||
        (parentDocId.endsWith(":") && childDocId.startsWith(parentDocId) && childDocId != parentDocId)

/** "primary:Music/Albums" → "Internal storage › Music › Albums"; other volumes keep their id. */
fun folderLocation(treeDocId: String): String {
    val volume = treeDocId.substringBefore(':')
    val path = treeDocId.substringAfter(':', "")
    val root = if (volume == "primary") "Internal storage" else "Storage $volume"
    return (listOf(root) + path.split('/').filter { it.isNotBlank() }).joinToString(" › ")
}

private fun shortHash(s: String): String =
    MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

/**
 * Walks a picked folder ([treeUri]) through the Storage Access Framework. Only this folder and the
 * folders inside it are read, with the permission the person granted when picking it.
 */
class FolderReader(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun treeDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    /** Whether the folder can still be read (permission kept, storage present). */
    fun canRead(treeUri: Uri): Boolean = runCatching {
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission } &&
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId(treeUri)), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
    }.getOrDefault(false)

    /**
     * Every music file under [treeUri], with its folder's cover and its `.lrc` lyrics. Throws when
     * the folder can't be read, so callers keep what they had rather than treat it as empty.
     */
    suspend fun listMusic(treeUri: Uri, onFound: (Int) -> Unit = {}): List<FolderFile> {
        val found = ArrayList<FolderFile>()
        val pending = ArrayDeque(listOf(treeDocumentId(treeUri)))
        var first = true
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dirId = pending.removeFirst()
            val children = children(treeUri, dirId) ?: if (first) error("Can't read $treeUri") else continue
            first = false
            // Android's own rule: a folder with a .nomedia file has no media for apps to list.
            if (dirId != treeDocumentId(treeUri) && children.any { it.name == ".nomedia" }) continue
            val cover = children.filter { !it.isDir && it.mime.startsWith("image/") }
                .minByOrNull { c -> SupportedAudio.coverNames.indexOf(c.name.substringBeforeLast('.').lowercase()).let { if (it < 0) 99 else it } }
                ?.takeIf { SupportedAudio.coverNames.contains(it.name.substringBeforeLast('.').lowercase()) }
            val lrcs = children.filter { it.name.endsWith(".lrc", ignoreCase = true) }.associateBy { it.name.substringBeforeLast('.') }
            for (c in children) {
                when {
                    c.isDir -> if (!SupportedAudio.skipsFolder(c.name)) pending += c.id
                    SupportedAudio.isMusicFile(c.name) -> {
                        found += FolderFile(
                            documentId = c.id,
                            name = c.name,
                            size = c.size,
                            modifiedMs = c.modified,
                            coverUri = cover?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id).toString() },
                            lyrics = lrcs[c.name.substringBeforeLast('.')]?.let { readText(treeUri, it.id) }
                        )
                        onFound(found.size)
                    }
                }
            }
        }
        return found
    }

    /** The tags of one song file; null when it can't be read as audio. */
    fun readTags(treeUri: Uri, documentId: String): Pair<SongTags, ByteArray?>? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId))
            fun key(k: Int) = retriever.extractMetadata(k)
            val duration = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            SongTags(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = duration,
                track = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                disc = key(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                year = key(MediaMetadataRetriever.METADATA_KEY_YEAR),
                genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE),
                bitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                sampleRate = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0 else 0,
                bitsPerSample = if (Build.VERSION.SDK_INT >= 31) key(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0 else 0
            ) to retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Saves an embedded cover once per album, in the app's private storage; returns its path. */
    fun saveCover(albumId: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "folder-covers").apply { mkdirs() }
        val file = File(dir, albumId.substringAfter(':') + ".jpg")
        if (!file.exists()) runCatching { file.writeBytes(bytes) }
        return file.absolutePath
    }

    private class Child(val id: String, val name: String, val mime: String, val size: Long, val modified: Long) {
        val isDir get() = mime == Document.MIME_TYPE_DIR
    }

    private fun children(treeUri: Uri, dirId: String): List<Child>? = runCatching {
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
            null, null, null
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Child(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3), c.getLong(4)))
                }
            }
        }
    }.getOrNull()

    private fun readText(treeUri: Uri, documentId: String): String? = runCatching {
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId))?.use { input ->
            input.readBytes().takeIf { it.size <= 512 * 1024 }?.toString(Charsets.UTF_8)
        }
    }.getOrNull()
}

/** Keeps or gives back read access to picked folders across restarts. */
class FolderPermissions(private val context: Context) {
    fun take(treeUri: Uri) = context.contentResolver.takePersistableUriPermission(treeUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun release(treeUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
