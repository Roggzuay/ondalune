package io.github.roggzuay.ondalune.data.network

import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.model.AlbumListType
import io.github.roggzuay.ondalune.data.model.DownloadSpeedLimit
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.PingDiagnosticResult
import io.github.roggzuay.ondalune.data.model.SubsonicAlbum
import io.github.roggzuay.ondalune.data.model.SubsonicPlaylist
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real OpenSubsonic REST API v1.16.1 Client supporting Navidrome, Gonic, Airsonic, Ampache,
 * and Nextcloud Music servers.
 *
 * Implements standard Subsonic token authentication (`t = md5(password + salt)`, `s = salt`)
 * as well as legacy hex-encoded password authentication (`p = enc:<hex>`).
 */
class OpenSubsonicClient {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val palettePairs = listOf(
        "#FF6B5B" to "#70F5FF",
        "#B983FF" to "#5EEAD4",
        "#FFB443" to "#60A5FA",
        "#38BDF8" to "#F472B6",
        "#34D399" to "#A78BFA",
        "#FB7185" to "#FBBF24"
    )

    fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hexEncode(input: String): String {
        return input.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds an authenticated OpenSubsonic endpoint URL for `/rest/<endpoint>.view`.
     * Strictly verifies Android Keystore-protected `passwordOrToken` before signing; if the stored
     * ciphertext was tampered with or cannot be decrypted, returns `""` so corrupted ciphertext is
     * never transmitted as a password.
     */
    fun buildEndpointUrl(
        server: SubsonicServerConfig,
        endpoint: String,
        queryParams: Map<String, String> = emptyMap()
    ): String {
        val cleanBase = server.baseUrl.trim().trimEnd('/')
        val baseHttpUrl = "$cleanBase/rest/$endpoint.view".toHttpUrlOrNull()
            ?: return ""

        val plainSecret = OndaluneCredentialSecurity.tryDecryptCredential(server.passwordOrToken)
            .getOrElse { return "" }
        if (plainSecret.isBlank() && OndaluneCredentialSecurity.isEncrypted(server.passwordOrToken)) {
            return ""
        }
        val salt = UUID.randomUUID().toString().replace("-", "").take(10)
        val builder = baseHttpUrl.newBuilder()
            .addQueryParameter("u", server.username.trim())
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Ondalune")
            .addQueryParameter("f", "json")

        if (server.useTokenAuth) {
            val token = md5Hex(plainSecret + salt)
            builder.addQueryParameter("t", token)
            builder.addQueryParameter("s", salt)
        } else {
            builder.addQueryParameter("p", "enc:${hexEncode(plainSecret)}")
        }

        queryParams.forEach { (k, v) ->
            builder.addQueryParameter(k, v)
        }

        return builder.build().toString()
    }

    fun buildStreamUrl(server: SubsonicServerConfig, trackId: String, timeOffsetSec: Int = 0): String {
        if (server.isDemoServer) return "demo://$trackId"
        val params = mutableMapOf(
            "id" to trackId,
            "estimateContentLength" to "true"
        )
        if (server.maxBitRate > 0) {
            params["maxBitRate"] = server.maxBitRate.toString()
        }
        if (server.transcodeFormat.isNotBlank() && server.transcodeFormat != "RAW") {
            params["format"] = server.transcodeFormat.lowercase()
        } else {
            params["format"] = "raw"
        }
        if (timeOffsetSec > 0) {
            params["timeOffset"] = timeOffsetSec.toString()
        }
        return buildEndpointUrl(server, "stream", params)
    }

    fun buildDownloadUrl(server: SubsonicServerConfig, trackId: String): String {
        if (server.isDemoServer) return "demo://$trackId"
        return buildEndpointUrl(server, "download", mapOf("id" to trackId))
    }

    fun buildCoverArtUrl(server: SubsonicServerConfig, coverArtId: String, size: Int = 600): String {
        if (server.isDemoServer || coverArtId.isBlank()) return ""
        return buildEndpointUrl(
            server = server,
            endpoint = "getCoverArt",
            queryParams = mapOf("id" to coverArtId, "size" to size.toString())
        )
    }

    /**
     * Pings the OpenSubsonic server and queries `getOpenSubsonicExtensions.view`.
     */
    suspend fun pingAndInspectServer(server: SubsonicServerConfig): PingDiagnosticResult =
        withContext(Dispatchers.IO) {
            if (server.isDemoServer) {
                return@withContext PingDiagnosticResult(
                    success = true,
                    statusMessage = "Connected to Ondalune OpenSubsonic Sandbox (Local DSP Engine)",
                    serverType = "OpenSubsonic Reference 1.16.1",
                    apiVersion = "1.16.1",
                    openSubsonicExtensions = listOf(
                        "songLyrics (v1)",
                        "transcodeOffset (v1)",
                        "formPost (v1)",
                        "apiKeyAuthentication (v1)"
                    ),
                    latencyMs = 4L
                )
            }

            val startMs = System.currentTimeMillis()
            val pingUrl = buildEndpointUrl(server, "ping")
            if (pingUrl.isBlank()) {
                return@withContext PingDiagnosticResult(
                    success = false,
                    statusMessage = "Invalid Server URL format. Include http:// or https://"
                )
            }

            try {
                val request = Request.Builder().url(pingUrl).get().build()
                okHttpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startMs
                    if (!response.isSuccessful) {
                        return@withContext PingDiagnosticResult(
                            success = false,
                            statusMessage = "HTTP ${response.code}: ${OndaluneCredentialSecurity.redactSensitiveUrl(response.message)}",
                            latencyMs = latency
                        )
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    val root = JSONObject(bodyStr).optJSONObject("subsonic-response")
                        ?: return@withContext PingDiagnosticResult(
                            success = false,
                            statusMessage = "Unexpected response format (missing subsonic-response)"
                        )

                    val status = root.optString("status", "failed")
                    if (status != "ok") {
                        val err = root.optJSONObject("error")?.optString("message")
                            ?: "Authentication failed"
                        return@withContext PingDiagnosticResult(
                            success = false,
                            statusMessage = "Subsonic Error: ${OndaluneCredentialSecurity.redactSensitiveUrl(err)}",
                            latencyMs = latency
                        )
                    }

                    val serverType = root.optString("type", "OpenSubsonic Server")
                    val serverVersion = root.optString("serverVersion", root.optString("version", "1.16.1"))
                    val extensions = fetchExtensionsInternal(server)

                    PingDiagnosticResult(
                        success = true,
                        statusMessage = "Authenticated with $serverType ($serverVersion)",
                        serverType = "$serverType $serverVersion",
                        apiVersion = root.optString("version", "1.16.1"),
                        openSubsonicExtensions = extensions.ifEmpty { listOf("Standard Subsonic REST 1.16.1") },
                        latencyMs = latency
                    )
                }
            } catch (e: Exception) {
                val safeMsg = OndaluneCredentialSecurity.redactSensitiveUrl(
                    e.localizedMessage ?: e.javaClass.simpleName
                )
                PingDiagnosticResult(
                    success = false,
                    statusMessage = "Connection error: $safeMsg"
                )
            }
        }

    private fun fetchExtensionsInternal(server: SubsonicServerConfig): List<String> {
        return try {
            val extUrl = buildEndpointUrl(server, "getOpenSubsonicExtensions")
            val req = Request.Builder().url(extUrl).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("subsonic-response")
                    ?.optJSONArray("openSubsonicExtensions") ?: return emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val name = item.optString("name")
                    if (name.isNotBlank()) list.add(name)
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class RemoteArtistMeta(
        val id: String,
        val name: String,
        val imageUrl: String,
        val bio: String
    )

    /**
     * Queries `/rest/getArtists.view` to retrieve the OpenSubsonic server's artist directory,
     * including `artistImageUrl` or artist-level `coverArt` IDs for each artist.
     */
    suspend fun fetchArtistsIndexMap(server: SubsonicServerConfig): Map<String, RemoteArtistMeta> =
        withContext(Dispatchers.IO) {
            if (server.isDemoServer) return@withContext emptyMap()
            val url = buildEndpointUrl(server, "getArtists")
            if (url.isBlank()) return@withContext emptyMap()
            val result = mutableMapOf<String, RemoteArtistMeta>()
            try {
                val req = Request.Builder().url(url).get().build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyMap()
                    val indices = JSONObject(resp.body?.string().orEmpty())
                        .optJSONObject("subsonic-response")
                        ?.optJSONObject("artists")
                        ?.optJSONArray("index") ?: return@withContext emptyMap()

                    for (i in 0 until indices.length()) {
                        val idxObj = indices.optJSONObject(i) ?: continue
                        val artistsArr = idxObj.optJSONArray("artist") ?: continue
                        for (j in 0 until artistsArr.length()) {
                            val artObj = artistsArr.optJSONObject(j) ?: continue
                            val artId = artObj.optString("id")
                            val artName = artObj.optString("name")
                            val directUrl = artObj.optString("artistImageUrl")
                            val coverArtId = artObj.optString("coverArt", artId)
                            val resolvedImageUrl = if (directUrl.startsWith("http", ignoreCase = true)) {
                                directUrl
                            } else if (coverArtId.isNotBlank()) {
                                buildCoverArtUrl(server, coverArtId)
                            } else {
                                ""
                            }
                            val meta = RemoteArtistMeta(
                                id = artId,
                                name = artName,
                                imageUrl = resolvedImageUrl,
                                bio = ""
                            )
                            if (artId.isNotBlank()) result[artId] = meta
                            if (artName.isNotBlank()) result[artName.lowercase()] = meta
                        }
                    }
                }
            } catch (_: Exception) {
            }
            result
        }

    /**
     * Album ids from `/rest/getAlbumList2.view` in the server's own order: `newest` is by date
     * added, `recent` by last played, `frequent` by play count. Ids only, so it is cheap enough
     * to refresh every time Home opens.
     */
    suspend fun fetchAlbumListIds(
        server: SubsonicServerConfig,
        type: String,
        size: Int = 20
    ): List<String> = withContext(Dispatchers.IO) {
        if (server.isDemoServer) return@withContext emptyList()
        val url = buildEndpointUrl(
            server = server,
            endpoint = "getAlbumList2",
            queryParams = mapOf("type" to type, "size" to size.toString())
        )
        if (url.isBlank()) return@withContext emptyList()
        try {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val list = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("subsonic-response")
                    ?.optJSONObject("albumList2")
                    ?.optJSONArray("album") ?: return@withContext emptyList()
                (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fallback artist description from Wikipedia's REST summary when the server has no biography
     * (common on Navidrome without a Last.fm key). Tries the plain name first, then the usual
     * music-disambiguation titles, and only accepts pages whose summary reads like a musician.
     */
    suspend fun fetchWikipediaArtistSummary(artistName: String): RemoteArtistMeta? = withContext(Dispatchers.IO) {
        val name = artistName.trim()
        if (name.isBlank()) return@withContext null
        val musicHint = Regex("(?i)\\b(band|musician|singer|rapper|producer|DJ|duo|group|composer|songwriter|artist|record)\\b")
        for (title in listOf(name, "$name (band)", "$name (musician)", "$name (singer)", "$name (rapper)")) {
            try {
                val encoded = java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "_")
                val req = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                    .header("User-Agent", "Ondalune/1.0 (OpenSubsonic Android player)")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.optString("type") == "disambiguation") return@use
                    val extract = json.optString("extract").trim()
                    val description = json.optString("description")
                    if (extract.isBlank() || !(musicHint.containsMatchIn(description) || musicHint.containsMatchIn(extract))) return@use
                    val image = json.optJSONObject("originalimage")?.optString("source")
                        ?: json.optJSONObject("thumbnail")?.optString("source").orEmpty()
                    return@withContext RemoteArtistMeta(id = "", name = name, imageUrl = image.orEmpty(), bio = extract)
                }
            } catch (_: Exception) {
                // try the next candidate title
            }
        }
        null
    }

    /**
     * Queries `/rest/getArtistInfo2.view` (with fallback to `getArtistInfo.view`) for an artist's
     * high-resolution portrait (`largeImageUrl`, `mediumImageUrl`, `smallImageUrl`) and biography.
     */
    suspend fun fetchArtistInfo2(
        server: SubsonicServerConfig,
        artistId: String,
        fallbackCoverId: String = ""
    ): RemoteArtistMeta? = withContext(Dispatchers.IO) {
        if (server.isDemoServer || artistId.isBlank()) return@withContext null
        val url = buildEndpointUrl(
            server = server,
            endpoint = "getArtistInfo2",
            queryParams = mapOf("id" to artistId)
        )
        if (url.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val subResp = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("subsonic-response") ?: return@withContext null
                val infoObj = subResp.optJSONObject("artistInfo2")
                    ?: subResp.optJSONObject("artistInfo")
                    ?: return@withContext null

                val rawBio = infoObj.optString("biography", "")
                    .replace(Regex("<[^>]*>"), "") // strip HTML tags such as <a target="_blank" ...>
                    .trim()
                val largeImg = infoObj.optString("largeImageUrl")
                val mediumImg = infoObj.optString("mediumImageUrl")
                val smallImg = infoObj.optString("smallImageUrl")
                val bestImg = listOf(largeImg, mediumImg, smallImg)
                    .firstOrNull { it.startsWith("http", ignoreCase = true) }
                    ?: buildCoverArtUrl(server, fallbackCoverId.ifBlank { artistId })

                RemoteArtistMeta(
                    id = artistId,
                    name = "",
                    imageUrl = bestImg,
                    bio = rawBio
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Maps one Subsonic `child` song object (from getAlbum or search3) to a track. [album] fills
     * fields the song omits when it comes from getAlbum.
     */
    private fun parseSong(
        server: SubsonicServerConfig,
        s: JSONObject,
        album: JSONObject?,
        styleIndex: Int,
        fallbackTrackNumber: Int
    ): SubsonicTrack {
        val albumId = s.optString("albumId", album?.optString("id").orEmpty())
        val palette = palettePairs[styleIndex % palettePairs.size]
        val coverId = s.optString("coverArt", albumId)
        val trackArtistId = s.optString("artistId", album?.optString("artistId").orEmpty())
        return SubsonicTrack(
            id = s.optString("id"),
            serverId = server.id,
            title = s.optString("title", "Untitled Track"),
            artist = s.optString("artist", album?.optString("artist") ?: "Unknown Artist"),
            artistId = trackArtistId,
            album = s.optString("album", album?.optString("name") ?: "Unknown Album"),
            albumId = albumId,
            durationSec = s.optInt("duration", 210).coerceAtLeast(15),
            bitRate = s.optInt("bitRate", 960),
            suffix = s.optString("suffix", "FLAC").uppercase(),
            bitDepth = s.optInt("bitDepth", 24),
            samplingRate = s.optInt("samplingRate", 48000),
            starred = s.has("starred"),
            trackNumber = s.optInt("track", fallbackTrackNumber),
            year = s.optInt("year", album?.optInt("year", 2025) ?: 2025),
            genre = s.optJSONArray("genres")
                ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) } }
                ?.takeIf { it.isNotEmpty() }?.joinToString("; ")
                ?: s.optString("genre", album?.optString("genre").orEmpty()),
            folderPath = s.optString("path", "/Music/Lossless"),
            coverArtUrl = buildCoverArtUrl(server, coverId),
            artistImageUrl = if (trackArtistId.isNotBlank()) buildCoverArtUrl(server, trackArtistId) else "",
            artStyleIndex = styleIndex % 6,
            paletteSeedHex = palette.first,
            accentHex = palette.second,
            playCount = s.optInt("playCount", 0),
            userRating = s.optInt("userRating", if (s.has("starred")) 5 else 4)
        )
    }

    /** GET [endpoint] and return its `subsonic-response`, or null on HTTP/API failure. */
    private fun getResponse(server: SubsonicServerConfig, endpoint: String, params: Map<String, String>): JSONObject? {
        val url = buildEndpointUrl(server = server, endpoint = endpoint, queryParams = params)
        if (url.isBlank()) return null
        return okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            JSONObject(resp.body?.string().orEmpty()).optJSONObject("subsonic-response")
                ?.takeIf { it.optString("status") == "ok" }
        }
    }

    /** Result of a full library walk; [complete] is false if any page failed, so callers must not prune. */
    class LibrarySnapshot(val tracks: List<SubsonicTrack>, val complete: Boolean)

    /**
     * Every song on the server. Pages through `search3` with an empty query (500 per page; the
     * fast path on Navidrome), then walks every album from `getAlbumList2` and fetches any album
     * whose songs are missing or short, so servers that reject empty searches still sync fully.
     */
    suspend fun fetchFullLibrary(
        server: SubsonicServerConfig,
        onProgress: (Int) -> Unit = {}
    ): LibrarySnapshot = withContext(Dispatchers.IO) {
        val songs = LinkedHashMap<String, SubsonicTrack>()
        var complete = true
        val page = 500
        val songPage = 200 // smaller song pages so progress arrives often

        try {
            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val arr = getResponse(
                    server, "search3",
                    mapOf("query" to "", "songCount" to "$songPage", "songOffset" to "$offset", "albumCount" to "0", "artistCount" to "0")
                )?.optJSONObject("searchResult3")?.optJSONArray("song") ?: break
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val track = parseSong(server, obj, null, songs.size, i + 1)
                    if (track.id.isNotBlank()) songs[track.id] = track
                }
                onProgress(songs.size)
                if (arr.length() < songPage) break
                offset += songPage
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Empty search unsupported or interrupted: the album walk below covers it.
        }

        val albums = mutableListOf<Pair<String, Int>>()
        try {
            var offset = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val response = getResponse(
                    server, "getAlbumList2",
                    mapOf("type" to "alphabeticalByName", "size" to "$page", "offset" to "$offset")
                )
                if (response == null) { complete = false; break }
                val arr = response.optJSONObject("albumList2")?.optJSONArray("album") ?: break
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    albums += obj.optString("id") to obj.optInt("songCount", 0)
                }
                if (arr.length() < page) break
                offset += page
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            complete = false
        }

        val have = songs.values.groupingBy { it.albumId }.eachCount()
        val missing = albums.filter { (id, count) -> id.isNotBlank() && (have[id] ?: 0) < count }
        missing.chunked(8).forEach { batch ->
            val fetched = coroutineScope {
                batch.map { (id, _) -> async { fetchAlbumTracks(server, id, songs.size) } }.awaitAll()
            }
            fetched.forEach { albumTracks -> albumTracks.forEach { songs[it.id] = it } }
            if (fetched.any { it.isEmpty() }) complete = false
            onProgress(songs.size)
        }

        val artistsIndex = fetchArtistsIndexMap(server)
        val tracks = songs.values.map { trk ->
            val meta = artistsIndex[trk.artistId] ?: artistsIndex[trk.artist.lowercase()]
            if (meta?.imageUrl.isNullOrBlank()) trk else trk.copy(artistImageUrl = meta!!.imageUrl)
        }
        LibrarySnapshot(tracks, complete && (tracks.isNotEmpty() || albums.isEmpty()))
    }

    suspend fun fetchAlbumTracks(
        server: SubsonicServerConfig,
        albumId: String,
        styleSeed: Int = 0
    ): List<SubsonicTrack> = withContext(Dispatchers.IO) {
        val url = buildEndpointUrl(
            server = server,
            endpoint = "getAlbum",
            queryParams = mapOf("id" to albumId)
        )
        if (url.isBlank()) return@withContext emptyList()
        try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val albumObj = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("subsonic-response")
                    ?.optJSONObject("album") ?: return@withContext emptyList()

                val songArray = albumObj.optJSONArray("song") ?: return@withContext emptyList()
                val result = mutableListOf<SubsonicTrack>()
                for (i in 0 until songArray.length()) {
                    val s = songArray.optJSONObject(i) ?: continue
                    result.add(parseSong(server, s, albumObj, styleSeed + i, i + 1))
                }
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Queries `/rest/search3.view` on a live OpenSubsonic server.
     */
    suspend fun searchRemote(
        server: SubsonicServerConfig,
        query: String
    ): List<SubsonicTrack> = withContext(Dispatchers.IO) {
        if (server.isDemoServer || query.isBlank()) return@withContext emptyList()
        val url = buildEndpointUrl(
            server = server,
            endpoint = "search3",
            queryParams = mapOf("query" to query, "songCount" to "30")
        )
        if (url.isBlank()) return@withContext emptyList()
        try {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val songs = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("subsonic-response")
                    ?.optJSONObject("searchResult3")
                    ?.optJSONArray("song") ?: return@withContext emptyList()
                val list = mutableListOf<SubsonicTrack>()
                for (i in 0 until songs.length()) {
                    val s = songs.optJSONObject(i) ?: continue
                    val palette = palettePairs[i % palettePairs.size]
                    val coverId = s.optString("coverArt", s.optString("id"))
                    val trackArtistId = s.optString("artistId", "")
                    val coverArtUrl = buildCoverArtUrl(server, coverId)
                    list.add(
                        SubsonicTrack(
                            id = s.optString("id"),
                            serverId = server.id,
                            title = s.optString("title", "Track"),
                            artist = s.optString("artist", "Artist"),
                            artistId = trackArtistId,
                            album = s.optString("album", "Album"),
                            albumId = s.optString("albumId", ""),
                            durationSec = s.optInt("duration", 200).coerceAtLeast(15),
                            bitRate = s.optInt("bitRate", 850),
                            suffix = s.optString("suffix", "FLAC").uppercase(),
                            bitDepth = s.optInt("bitDepth", 16),
                            samplingRate = s.optInt("samplingRate", 44100),
                            starred = s.has("starred"),
                            trackNumber = s.optInt("track", i + 1),
                            year = s.optInt("year", 2024),
                            genre = s.optString("genre", "Stream"),
                            coverArtUrl = coverArtUrl,
                            artistImageUrl = if (trackArtistId.isNotBlank()) buildCoverArtUrl(server, trackArtistId) else coverArtUrl,
                            artStyleIndex = i % 6,
                            paletteSeedHex = palette.first,
                            accentHex = palette.second,
                            playCount = s.optInt("playCount", 0),
                            userRating = s.optInt("userRating", if (s.has("starred")) 5 else 4)
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Stars or unstars an item on the OpenSubsonic server (`/rest/star.view` or `/rest/unstar.view`).
     */
    suspend fun setStarredRemote(
        server: SubsonicServerConfig,
        id: String,
        starred: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (server.isDemoServer) return@withContext true
        val endpoint = if (starred) "star" else "unstar"
        val url = buildEndpointUrl(server, endpoint, mapOf("id" to id))
        if (url.isBlank()) return@withContext false
        try {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sets a 0..5 star rating on an OpenSubsonic track (`/rest/setRating.view`).
     */
    suspend fun setRatingRemote(
        server: SubsonicServerConfig,
        id: String,
        rating: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (server.isDemoServer) return@withContext true
        val safeRating = rating.coerceIn(0, 5)
        val url = buildEndpointUrl(
            server = server,
            endpoint = "setRating",
            queryParams = mapOf("id" to id, "rating" to safeRating.toString())
        )
        if (url.isBlank()) return@withContext false
        try {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends an OpenSubsonic scrobble event (`/rest/scrobble.view`).
     */
    suspend fun scrobbleRemote(
        server: SubsonicServerConfig,
        trackId: String,
        submission: Boolean
    ) = withContext(Dispatchers.IO) {
        if (server.isDemoServer) return@withContext
        val url = buildEndpointUrl(
            server = server,
            endpoint = "scrobble",
            queryParams = mapOf(
                "id" to trackId,
                "submission" to submission.toString()
            )
        )
        if (url.isBlank()) return@withContext
        try {
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().close()
        } catch (_: Exception) {
        }
    }

    /**
     * Resolves track lyrics honestly without fabricating time-synced lyrics for real tracks:
     * 1. Checks embedded LRC (`track.syncedLyricsRaw`). If valid LRC timestamps exist -> `SYNCED`;
     *    if plain text exists without timestamps -> `PLAIN_UNSYNCED`.
     * 2. On real OpenSubsonic servers (`!server.isDemoServer`):
     *    a) Queries `/rest/getLyricsBySongId.view` (OpenSubsonic `songLyrics` extension).
     *       Honors `synced` flag and line `start` attributes (`SYNCED` vs `PLAIN_UNSYNCED`).
     *    b) Falls back to standard Subsonic `/rest/getLyrics.view?artist=...&title=...`.
     * 3. If no lyrics are found, returns [LyricsAvailability.UNAVAILABLE].
     */
    suspend fun fetchTrackLyrics(
        server: SubsonicServerConfig,
        track: SubsonicTrack
    ): TrackLyricsResult = withContext(Dispatchers.IO) {
        if (track.syncedLyricsRaw.isNotBlank()) {
            val lrcLines = parseLrcString(track.syncedLyricsRaw)
            if (lrcLines.isNotEmpty()) {
                return@withContext TrackLyricsResult(
                    availability = LyricsAvailability.SYNCED,
                    syncedLines = lrcLines,
                    plainLyrics = lrcLines.joinToString("\n") { it.text },
                    sourceLabel = if (server.isDemoServer) "Embedded Studio LRC" else "Embedded LRC"
                )
            } else {
                return@withContext TrackLyricsResult(
                    availability = LyricsAvailability.PLAIN_UNSYNCED,
                    syncedLines = emptyList(),
                    plainLyrics = track.syncedLyricsRaw.trim(),
                    sourceLabel = "Embedded Unsynced Lyrics"
                )
            }
        }

        if (!server.isDemoServer) {
            // 1. Try OpenSubsonic structured lyrics (/rest/getLyricsBySongId.view)
            val structuredUrl = buildEndpointUrl(
                server = server,
                endpoint = "getLyricsBySongId",
                queryParams = mapOf("id" to track.id)
            )
            if (structuredUrl.isNotBlank()) {
                try {
                    val req = Request.Builder().url(structuredUrl).get().build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val structured = JSONObject(resp.body?.string().orEmpty())
                                .optJSONObject("subsonic-response")
                                ?.optJSONObject("lyricsList")
                                ?.optJSONArray("structuredLyrics")
                            if (structured != null && structured.length() > 0) {
                                val firstBlock = structured.optJSONObject(0)
                                val isSyncedBlock = firstBlock?.optBoolean("synced", true) ?: true
                                val linesArr = firstBlock?.optJSONArray("line")
                                if (linesArr != null && linesArr.length() > 0) {
                                    var hasExplicitStart = false
                                    val parsedSynced = mutableListOf<LyricLine>()
                                    val plainLines = mutableListOf<String>()
                                    for (i in 0 until linesArr.length()) {
                                        val lineObj = linesArr.optJSONObject(i) ?: continue
                                        val textVal = lineObj.optString("value", "").trim()
                                        if (textVal.isEmpty()) continue
                                        plainLines.add(textVal)
                                        if (lineObj.has("start")) {
                                            hasExplicitStart = true
                                            parsedSynced.add(
                                                LyricLine(
                                                    timeMs = lineObj.optLong("start", 0L).coerceAtLeast(0L),
                                                    text = textVal
                                                )
                                            )
                                        }
                                    }
                                    if (isSyncedBlock && hasExplicitStart && parsedSynced.isNotEmpty()) {
                                        return@withContext TrackLyricsResult(
                                            availability = LyricsAvailability.SYNCED,
                                            syncedLines = parsedSynced.sortedBy { it.timeMs },
                                            plainLyrics = plainLines.joinToString("\n"),
                                            sourceLabel = "OpenSubsonic Synced Lyrics"
                                        )
                                    } else if (plainLines.isNotEmpty()) {
                                        return@withContext TrackLyricsResult(
                                            availability = LyricsAvailability.PLAIN_UNSYNCED,
                                            syncedLines = emptyList(),
                                            plainLyrics = plainLines.joinToString("\n"),
                                            sourceLabel = "OpenSubsonic Plain Lyrics"
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }

            // 2. Fallback to standard Subsonic /rest/getLyrics.view
            val legacyUrl = buildEndpointUrl(
                server = server,
                endpoint = "getLyrics",
                queryParams = mapOf(
                    "artist" to track.artist,
                    "title" to track.title
                )
            )
            if (legacyUrl.isNotBlank()) {
                try {
                    val req = Request.Builder().url(legacyUrl).get().build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val rawValue = JSONObject(resp.body?.string().orEmpty())
                                .optJSONObject("subsonic-response")
                                ?.optJSONObject("lyrics")
                                ?.optString("value", "")
                                ?.trim()
                                .orEmpty()
                            if (rawValue.isNotBlank()) {
                                val maybeLrc = parseLrcString(rawValue)
                                if (maybeLrc.isNotEmpty()) {
                                    return@withContext TrackLyricsResult(
                                        availability = LyricsAvailability.SYNCED,
                                        syncedLines = maybeLrc,
                                        plainLyrics = maybeLrc.joinToString("\n") { it.text },
                                        sourceLabel = "Subsonic LRC Lyrics"
                                    )
                                } else {
                                    return@withContext TrackLyricsResult(
                                        availability = LyricsAvailability.PLAIN_UNSYNCED,
                                        syncedLines = emptyList(),
                                        plainLyrics = rawValue,
                                        sourceLabel = "Subsonic Plain Lyrics"
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        TrackLyricsResult(
            availability = LyricsAvailability.UNAVAILABLE,
            syncedLines = emptyList(),
            plainLyrics = "",
            sourceLabel = ""
        )
    }

    /**
     * Returns only time-synced lyrics when genuinely available; never fabricates timestamps.
     */
    suspend fun fetchSyncedLyrics(
        server: SubsonicServerConfig,
        track: SubsonicTrack
    ): List<LyricLine> {
        val result = fetchTrackLyrics(server, track)
        return if (result.availability == LyricsAvailability.SYNCED) {
            result.syncedLines
        } else {
            emptyList()
        }
    }

    /**
     * Streams a track from `/rest/download.view` (or `/rest/stream.view` fallback) into [targetFile]
     * while honoring [speedLimit] bandwidth throttling and reporting progress via [onProgress].
     */
    suspend fun downloadAudioStreamToFile(
        server: SubsonicServerConfig,
        track: SubsonicTrack,
        targetFile: File,
        speedLimit: DownloadSpeedLimit = DownloadSpeedLimit.UNLIMITED,
        onProgress: suspend (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val primaryUrl = buildDownloadUrl(server, track.id)
        val fallbackUrl = buildStreamUrl(server, track.id)
        val candidateUrl = primaryUrl.ifBlank { fallbackUrl }
        if (candidateUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL or credentials"))
        }

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        try {
            targetFile.parentFile?.mkdirs()
            val req = Request.Builder().url(candidateUrl).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${resp.code} while downloading ${track.title}")
                    )
                }
                val body = resp.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty response body from server")
                )
                val contentLength = body.contentLength().let { len ->
                    if (len > 0L) len else (track.durationSec.toLong() * track.bitRate.coerceAtLeast(320) * 125L)
                }

                val maxBytesPerSec = if (speedLimit.mbpsValue > 0) {
                    speedLimit.mbpsValue.toLong() * 1024L * 1024L
                } else {
                    0L
                }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var totalRead = 0L
                        var windowStartMs = System.currentTimeMillis()
                        var windowBytes = 0L
                        var lastProgressEmitMs = 0L

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            windowBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmitMs >= 120L) {
                                lastProgressEmitMs = now
                                onProgress(totalRead, contentLength.coerceAtLeast(totalRead))
                            }

                            if (maxBytesPerSec > 0L && windowBytes >= maxBytesPerSec / 4L) {
                                val elapsedMs = (now - windowStartMs).coerceAtLeast(1L)
                                val expectedMs = (windowBytes * 1000L) / maxBytesPerSec
                                if (expectedMs > elapsedMs) {
                                    delay(expectedMs - elapsedMs)
                                }
                                windowStartMs = System.currentTimeMillis()
                                windowBytes = 0L
                            }
                        }
                        output.flush()
                        onProgress(totalRead, totalRead.coerceAtLeast(1L))
                    }
                }
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(targetFile)
        } catch (ce: CancellationException) {
            tempFile.delete()
            throw ce
        } catch (t: Throwable) {
            tempFile.delete()
            Result.failure(t)
        }
    }

    fun parseLrcString(raw: String): List<LyricLine> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("[")) return@mapNotNull null
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return@mapNotNull null
            val timePart = trimmed.substring(1, closeIdx)
            val textPart = trimmed.substring(closeIdx + 1).trim()
            val parts = timePart.split(":")
            if (parts.size != 2) return@mapNotNull null
            val mins = parts[0].toLongOrNull() ?: return@mapNotNull null
            val secs = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val totalMs = (mins * 60_000L) + (secs * 1000.0).toLong()
            LyricLine(timeMs = totalMs, text = textPart)
        }
    }
}
