package io.github.roggzuay.ondalune.data.network

import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A Jellyfin sign-in: the access token and the user it belongs to. */
data class JellyfinLogin(val token: String, val userId: String)

/**
 * A Jellyfin server's REST API: sign-in, the music library, streaming, hearts, plays, lyrics and
 * artists. Requests carry the access token from sign-in (never the password, which isn't kept);
 * stream and image addresses carry it as `api_key`, which logs redact.
 */
class JellyfinClient(private val deviceId: String, private val appVersion: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun authHeader(token: String?) = buildString {
        append("MediaBrowser Client=\"Ondalune\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"$appVersion\"")
        if (token != null) append(", Token=\"$token\"")
    }

    private fun base(server: SubsonicServerConfig): HttpUrl? = server.baseUrl.trim().trimEnd('/').toHttpUrlOrNull()

    private fun token(server: SubsonicServerConfig): String =
        OndaluneCredentialSecurity.tryDecryptCredential(server.passwordOrToken).getOrDefault("")

    private fun url(server: SubsonicServerConfig, path: String, query: Map<String, String> = emptyMap()): HttpUrl? {
        val builder = base(server)?.newBuilder() ?: return null
        path.trim('/').split('/').forEach { builder.addPathSegment(it) }
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun call(server: SubsonicServerConfig, path: String, query: Map<String, String> = emptyMap(), method: String = "GET"): String? {
        val target = url(server, path, query) ?: return null
        val body = if (method == "GET") null else ByteArray(0).toRequestBody(null)
        val request = Request.Builder().url(target).method(method, body).header("Authorization", authHeader(token(server))).build()
        return http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else null }
    }

    /** Signs in with a username and password; a failure carries the reason to show. */
    suspend fun login(baseUrl: String, username: String, password: String): Result<JellyfinLogin> = withContext(Dispatchers.IO) {
        runCatching {
            val root = baseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: error("That address doesn't look right")
            val target = root.newBuilder().addPathSegments("Users/AuthenticateByName").build()
            val body = JSONObject().put("Username", username).put("Pw", password).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(target).post(body).header("Authorization", authHeader(null)).build()
            http.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 -> error("Wrong username or password")
                    !resp.isSuccessful -> error("The server answered ${resp.code}")
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                JellyfinLogin(token = json.getString("AccessToken"), userId = json.getJSONObject("User").getString("Id"))
            }
        }
    }

    suspend fun ping(server: SubsonicServerConfig): PingDiagnosticResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        runCatching {
            val info = JSONObject(call(server, "System/Info/Public") ?: error("Can't reach the server"))
            // The public info answers anyone; the user's own record checks the sign-in still works.
            call(server, "Users/${server.remoteUserId}") ?: error("Signed out: add the server again")
            val version = info.optString("Version")
            PingDiagnosticResult(
                success = true,
                statusMessage = "Connected to ${info.optString("ServerName").ifBlank { "Jellyfin" }}",
                serverType = "Jellyfin $version".trim(),
                apiVersion = version,
                latencyMs = System.currentTimeMillis() - started
            )
        }.getOrElse { PingDiagnosticResult(success = false, statusMessage = it.message ?: "Can't reach the server") }
    }

    /** Every song in the user's libraries, 500 a page. Incomplete when a page fails. */
    suspend fun fetchLibrary(server: SubsonicServerConfig, onProgress: (Int) -> Unit): OpenSubsonicClient.LibrarySnapshot =
        withContext(Dispatchers.IO) {
            val songs = ArrayList<SubsonicTrack>()
            var start = 0
            var complete = true
            while (true) {
                val page = runCatching {
                    call(
                        server, "Users/${server.remoteUserId}/Items", mapOf(
                            "IncludeItemTypes" to "Audio",
                            "Recursive" to "true",
                            "Fields" to "Genres,MediaSources,Path,DateCreated",
                            "SortBy" to "AlbumArtist,Album,ParentIndexNumber,IndexNumber",
                            "StartIndex" to start.toString(),
                            "Limit" to "500"
                        )
                    )?.let(::JSONObject)
                }.getOrNull()
                if (page == null) { complete = false; break }
                val items = page.optJSONArray("Items") ?: JSONArray()
                for (i in 0 until items.length()) songs += jellyfinTrackFrom(items.getJSONObject(i), server)
                onProgress(songs.size)
                start += items.length()
                if (items.length() == 0 || start >= page.optInt("TotalRecordCount", start)) break
            }
            OpenSubsonicClient.LibrarySnapshot(songs, complete)
        }

    /**
     * Where a song streams from: the original file, or a conversion to the server row's format and
     * bitrate (MP3 or Opus, which the player decodes everywhere).
     */
    fun streamUrl(server: SubsonicServerConfig, itemId: String): String? {
        val auth = mapOf("api_key" to token(server), "DeviceId" to deviceId)
        if (server.transcodeFormat == "RAW" || server.maxBitRate <= 0) {
            return url(server, "Audio/$itemId/stream", auth + ("static" to "true"))?.toString()
        }
        val (codec, container) = if (server.transcodeFormat == "OPUS") "opus" to "ogg" else "mp3" to "mp3"
        return url(
            server, "Audio/$itemId/universal", auth + mapOf(
                "UserId" to server.remoteUserId,
                "MaxStreamingBitrate" to (server.maxBitRate * 1000).toString(),
                "Container" to "flac,mp3,ogg,opus,m4a,aac,wav",
                "AudioCodec" to codec,
                "TranscodingContainer" to container,
                "TranscodingProtocol" to "http"
            )
        )?.toString()
    }

    fun downloadUrl(server: SubsonicServerConfig, itemId: String): String? =
        url(server, "Items/$itemId/Download", mapOf("api_key" to token(server)))?.toString()

    suspend fun setFavourite(server: SubsonicServerConfig, itemId: String, favourite: Boolean) = withContext(Dispatchers.IO) {
        runCatching { call(server, "Users/${server.remoteUserId}/FavoriteItems/$itemId", method = if (favourite) "POST" else "DELETE") }
        Unit
    }

    suspend fun markPlayed(server: SubsonicServerConfig, itemId: String) = withContext(Dispatchers.IO) {
        runCatching { call(server, "Users/${server.remoteUserId}/PlayedItems/$itemId", method = "POST") }
        Unit
    }

    /** The server's lyrics for a song (Jellyfin 10.9 and later); synced when the lines carry start times. */
    suspend fun lyrics(server: SubsonicServerConfig, itemId: String): TrackLyricsResult = withContext(Dispatchers.IO) {
        val json = runCatching { call(server, "Audio/$itemId/Lyrics")?.let(::JSONObject) }.getOrNull()
            ?: return@withContext TrackLyricsResult()
        val lines = json.optJSONArray("Lyrics") ?: return@withContext TrackLyricsResult()
        val parsed = (0 until lines.length()).map { lines.getJSONObject(it) }
        if (parsed.isEmpty()) return@withContext TrackLyricsResult()
        if (parsed.all { it.has("Start") }) {
            TrackLyricsResult(
                LyricsAvailability.SYNCED,
                parsed.map { LyricLine(it.getLong("Start") / TICKS_PER_MS, it.optString("Text")) },
                sourceLabel = "Jellyfin"
            )
        } else {
            TrackLyricsResult(LyricsAvailability.PLAIN_UNSYNCED, plainLyrics = parsed.joinToString("\n") { it.optString("Text") }, sourceLabel = "Jellyfin")
        }
    }

    /** An artist's photo, biography and similar artists. */
    suspend fun artist(server: SubsonicServerConfig, artistId: String): OpenSubsonicClient.RemoteArtistMeta? = withContext(Dispatchers.IO) {
        runCatching {
            val item = JSONObject(call(server, "Users/${server.remoteUserId}/Items/$artistId") ?: return@runCatching null)
            val similar = call(server, "Items/$artistId/Similar", mapOf("UserId" to server.remoteUserId, "Limit" to "12"))
                ?.let { JSONObject(it).optJSONArray("Items") }
            OpenSubsonicClient.RemoteArtistMeta(
                id = artistId,
                name = item.optString("Name"),
                imageUrl = item.optJSONObject("ImageTags")?.optString("Primary")?.takeIf { it.isNotBlank() }
                    ?.let { jellyfinImageUrl(server.baseUrl, artistId, it) }.orEmpty(),
                bio = item.optString("Overview"),
                similarArtistIds = similar?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("Id") } }.orEmpty()
            )
        }.getOrNull()
    }

}

/** Jellyfin counts time in ticks of 100 ns. */
private const val TICKS_PER_MS = 10_000L

/** A picture of an item (album, song, artist); [tag] names the version, so caches stay correct. */
private fun jellyfinImageUrl(baseUrl: String, itemId: String, tag: String) =
    "${baseUrl.trim().trimEnd('/')}/Items/$itemId/Images/Primary?maxHeight=600&quality=90&tag=$tag"

/** The library track for one Jellyfin `Audio` item. */
fun jellyfinTrackFrom(item: JSONObject, server: SubsonicServerConfig): SubsonicTrack {
    fun JSONArray?.strings() = this?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
    val id = item.getString("Id")
    val artistItems = item.optJSONArray("ArtistItems")
    val firstArtist = artistItems?.optJSONObject(0)
    val artists = item.optJSONArray("Artists").strings()
    val source = item.optJSONArray("MediaSources")?.optJSONObject(0)
    val streams = source?.optJSONArray("MediaStreams")
    val audio = streams?.let { s -> (0 until s.length()).map { s.getJSONObject(it) }.firstOrNull { it.optString("Type") == "Audio" } }
    val albumId = item.optString("AlbumId")
    val albumTag = item.optString("AlbumPrimaryImageTag")
    val ownTag = item.optJSONObject("ImageTags")?.optString("Primary")
    val path = item.optString("Path")
    val userData = item.optJSONObject("UserData")
    return SubsonicTrack(
        id = id,
        serverId = server.id,
        title = item.optString("Name").ifBlank { "Untitled" },
        artist = artists.joinToString(", ").ifBlank { item.optString("AlbumArtist").ifBlank { "Unknown artist" } },
        artistId = firstArtist?.optString("Id").orEmpty(),
        album = item.optString("Album").ifBlank { "Unknown album" },
        albumId = albumId.ifBlank { "jf-album:${item.optString("Album")}" },
        durationSec = (item.optLong("RunTimeTicks") / 10_000_000L).toInt(),
        bitRate = (source?.optInt("Bitrate") ?: 0) / 1000,
        suffix = item.optString("Container").substringBefore(',').uppercase(),
        bitDepth = audio?.optInt("BitDepth") ?: 0,
        samplingRate = audio?.optInt("SampleRate") ?: 0,
        starred = userData?.optBoolean("IsFavorite") == true,
        starredAtMs = if (userData?.optBoolean("IsFavorite") == true) isoToMs(item.optString("DateCreated")) else 0L,
        trackNumber = item.optInt("IndexNumber", 1).coerceAtLeast(1),
        year = item.optInt("ProductionYear", 0),
        genre = item.optJSONArray("Genres").strings().joinToString("; "),
        folderPath = path.substringBeforeLast('/', ""),
        coverArtUrl = when {
            albumTag.isNotBlank() && albumId.isNotBlank() -> jellyfinImageUrl(server.baseUrl, albumId, albumTag)
            !ownTag.isNullOrBlank() -> jellyfinImageUrl(server.baseUrl, id, ownTag)
            else -> ""
        },
        playCount = userData?.optInt("PlayCount") ?: 0,
        userRating = 0,
        bpm = 0,
        musicalKey = "",
        rgTrackGain = item.optDouble("NormalizationGain").takeIf { !it.isNaN() }?.toFloat()
    )
}
