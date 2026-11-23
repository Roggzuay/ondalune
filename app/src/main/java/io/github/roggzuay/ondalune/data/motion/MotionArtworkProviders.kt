package io.github.roggzuay.ondalune.data.motion

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.roggzuay.ondalune.data.model.LOCAL_TRACK_PREFIX
import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.source
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.source.FolderReader
import io.github.roggzuay.ondalune.data.source.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Moving artwork kept next to the songs in a music folder on this phone: a video (`cover.mp4`,
 * `cover.webm`), an animated `cover.webp` or `cover.gif`, or a file named for motion
 * (`motion.mp4`, `animated.webp`…). It is shown where it is. A folder that holds songs from more
 * than one album claims no album, so its artwork is never put on the wrong one.
 */
class FolderMotionProvider(
    private val context: Context,
    /** The picked folder (tree) a song file belongs to. */
    private val treeOf: suspend (SubsonicTrack) -> String?,
    /** The albums among the songs in the same folder as [SubsonicTrack]. */
    private val albumsBeside: suspend (SubsonicTrack) -> Set<String>
) : MotionArtworkProvider {
    override val id = "folder"
    override val name = "Your music folder"
    override val attribution: String? = null
    override val usesNetwork = false
    override val showsInPlace = true

    companion object {
        private val motionNames = listOf("motion", "animated", "cover-motion", "motion-cover", "animated-cover", "cover-animated")
        private val coverNames = listOf("cover", "folder", "front", "album", "albumart")

        /** How likely [fileName] is to be the folder's moving artwork, lower first; null when it isn't. */
        fun rank(fileName: String): Int? {
            val base = fileName.substringBeforeLast('.').lowercase()
            val format = formatOf(fileName) ?: return null
            val name = motionNames.indexOf(base).takeIf { it >= 0 }
                ?: coverNames.indexOf(base).takeIf { it >= 0 }?.plus(motionNames.size)
                ?: return null
            return name * MotionFormat.entries.size + format.ordinal
        }

        fun formatOf(fileName: String): MotionFormat? = when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> MotionFormat.MP4
            "webm" -> MotionFormat.WEBM
            "webp" -> MotionFormat.ANIMATED_WEBP
            "gif" -> MotionFormat.GIF
            else -> null
        }
    }

    override fun handles(track: SubsonicTrack, server: SubsonicServerConfig?) =
        track.id.startsWith(LOCAL_TRACK_PREFIX) && !track.id.startsWith(OpenedFile.PREFIX)

    override suspend fun find(identity: MotionIdentity, track: SubsonicTrack, server: SubsonicServerConfig?): List<MotionCandidate> =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeOf(track) ?: return@withContext emptyList())
            val dirId = track.id.removePrefix(LOCAL_TRACK_PREFIX).substringBeforeLast('/', "")
            if (dirId.isEmpty()) return@withContext emptyList()
            val files = FolderReader(context).filesIn(tree, dirId) ?: throw IOException("The folder can't be read")
            val oneAlbum = albumsBeside(track).let { it.size == 1 && track.albumId in it }
            files.mapNotNull { f -> rank(f.name)?.let { f to it } }
                .sortedBy { it.second }
                .mapNotNull { (f, _) ->
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, f.id)
                    val declared = formatOf(f.name) ?: return@mapNotNull null
                    // Still pictures share these names; only a file that moves counts.
                    val format = if (declared.isVideo) declared else sniff(uri) ?: return@mapNotNull null
                    MotionCandidate(
                        providerId = id,
                        source = uri.toString(),
                        format = format,
                        claimed = if (oneAlbum) identity else MotionIdentity(identity.serverId, "", "", ""),
                        sizeBytes = f.size
                    )
                }
        }

    override suspend fun open(candidate: MotionCandidate, fromByte: Long): MotionStream = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(candidate.source)) ?: throw IOException("Can't open ${candidate.source}")
        if (fromByte > 0) input.skip(fromByte)
        MotionStream(input, fromByte, candidate.sizeBytes)
    }

    private fun sniff(uri: Uri): MotionFormat? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(MotionArtworkValidator.HEADER_BYTES)
            val n = input.readNBytesCompat(head)
            MotionArtworkValidator.sniff(head.copyOf(n))
        }
    }.getOrNull()
}

/**
 * The album cover on an OpenSubsonic server (Navidrome and others), fetched as it is stored, with
 * no size asked so the server neither resizes nor re-encodes it. When the album folder's cover is
 * an animated WebP or GIF, that is moving artwork from the user's own library. Only the first 4 KB
 * are read to tell; the whole file is fetched only when it moves.
 */
class ServerCoverMotionProvider(private val client: OpenSubsonicClient) : MotionArtworkProvider {
    override val id = "server-cover"
    override val name = "Your server's album cover"
    override val attribution: String? = null
    override val usesNetwork = true

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    override fun handles(track: SubsonicTrack, server: SubsonicServerConfig?) =
        server != null && !server.isDemoServer && server.source == SourceType.SUBSONIC &&
            track.albumId.isNotBlank() && !track.id.startsWith(LOCAL_TRACK_PREFIX)

    override suspend fun find(identity: MotionIdentity, track: SubsonicTrack, server: SubsonicServerConfig?): List<MotionCandidate> =
        withContext(Dispatchers.IO) {
            val url = client.buildOriginalCoverArtUrl(server ?: return@withContext emptyList(), track.albumId)
            if (url.isBlank()) return@withContext emptyList()
            http.newCall(Request.Builder().url(url).header("Range", "bytes=0-${MotionArtworkValidator.HEADER_BYTES - 1}").build()).execute().use { r ->
                if (r.code == 404) return@withContext emptyList()
                if (!r.isSuccessful) throw IOException("Cover lookup failed: HTTP ${r.code}")
                // Not every server honours the range; read the start and hang up either way.
                val head = ByteArray(MotionArtworkValidator.HEADER_BYTES)
                val n = r.body?.byteStream()?.readNBytesCompat(head) ?: 0
                val format = MotionArtworkValidator.sniff(head.copyOf(n)) ?: return@withContext emptyList()
                val total = if (r.code == 206) r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                else r.body?.contentLength() ?: -1L
                listOf(MotionCandidate(id, url, format, identity, total))
            }
        }

    override suspend fun open(candidate: MotionCandidate, fromByte: Long): MotionStream = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(candidate.source)
            .apply { if (fromByte > 0) header("Range", "bytes=$fromByte-") }
            .build()
        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Download failed: HTTP ${response.code}")
        }
        val body = response.body ?: run { response.close(); throw IOException("Empty response") }
        val resumed = response.code == 206 && fromByte > 0
        MotionStream(body.byteStream(), if (resumed) fromByte else 0L, body.contentLength().let { if (it >= 0 && resumed) it + fromByte else it })
    }
}

/** Fills [buffer] as far as the stream goes; returns how much was read. */
internal fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val n = read(buffer, total, buffer.size - total)
        if (n < 0) break
        total += n
    }
    return total
}
