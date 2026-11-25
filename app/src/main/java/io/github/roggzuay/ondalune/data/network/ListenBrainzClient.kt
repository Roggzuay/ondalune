package io.github.roggzuay.ondalune.data.network

import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ListenBrainz: an open record of what you listen to, run by MetaBrainz, that you can take with
 * you and nobody sells.
 *
 * It is off until you put a token in, and it is listed in PRIVACY.md as the only place other than
 * your own servers that Ondalune ever tells what is playing. What is sent is the song's title,
 * artist, album and length, and nothing about you beyond the token that says which account it is.
 *
 * A listen that could not be sent is kept and goes with the next one, so a train tunnel does not
 * cost you an afternoon's listening.
 */
class ListenBrainzClient(
    private val baseUrl: String = DefaultBaseUrl,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    /** Where the blocking call runs. Tests hand it their own, so the waiting is theirs to control. */
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    /** Checks a token and returns the name it belongs to, or null when it is not a good one. */
    suspend fun validate(token: String): String? = withContext(io) {
        if (token.isBlank()) return@withContext null
        val request = Request.Builder()
            .url("$baseUrl/1/validate-token")
            .header("Authorization", "Token ${token.trim()}")
            .get()
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = JSONObject(response.body?.string().orEmpty())
                if (!body.optBoolean("valid", false)) null
                else body.optString("user_name").takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    /**
     * Says a song is playing now. ListenBrainz keeps this only until the next one, so it is sent
     * and forgotten: a failure here is never queued and never worth telling anyone about.
     */
    suspend fun playingNow(token: String, track: SubsonicTrack): Boolean =
        post(token, body = JSONObject().apply {
            put("listen_type", "playing_now")
            put("payload", JSONArray().put(listenOf(track, listenedAt = null)))
        })

    /**
     * Submits one or more finished listens. All of them go in one request, so a backlog clears in
     * one go rather than one call per song.
     */
    suspend fun submit(token: String, listens: List<PendingListen>): Boolean {
        if (listens.isEmpty()) return true
        val payload = JSONArray()
        listens.forEach { payload.put(it.toJson()) }
        return post(token, JSONObject().apply {
            put("listen_type", if (listens.size == 1) "single" else "import")
            put("payload", payload)
        })
    }

    private suspend fun post(token: String, body: JSONObject): Boolean = withContext(io) {
        if (token.isBlank()) return@withContext false
        val request = Request.Builder()
            .url("$baseUrl/1/submit-listens")
            .header("Authorization", "Token ${token.trim()}")
            .post(body.toString().toRequestBody(Json))
            .build()
        runCatching {
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun listenOf(track: SubsonicTrack, listenedAt: Long?): JSONObject =
        PendingListen.of(track, listenedAt ?: 0L).toJson(includeTime = listenedAt != null)

    companion object {
        const val DefaultBaseUrl = "https://api.listenbrainz.org"
        private val Json = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * One listen, waiting to be sent. Kept as its own small record rather than a whole track, so a
 * song that leaves the library still reaches ListenBrainz.
 */
data class PendingListen(
    /** Seconds since the epoch, which is what ListenBrainz counts in. */
    val listenedAt: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int
) {
    fun toJson(includeTime: Boolean = true): JSONObject = JSONObject().apply {
        if (includeTime) put("listened_at", listenedAt)
        put(
            "track_metadata",
            JSONObject().apply {
                put("track_name", title)
                put("artist_name", artist)
                if (album.isNotBlank()) put("release_name", album)
                put(
                    "additional_info",
                    JSONObject().apply {
                        put("media_player", Client)
                        put("submission_client", Client)
                        if (durationMs > 0) put("duration_ms", durationMs)
                        if (trackNumber > 0) put("tracknumber", trackNumber)
                    }
                )
            }
        )
    }

    /** "1699999999|Title|Artist|Album|200000|3", for the small queue kept in preferences. */
    fun serialized(): String = listOf(
        listenedAt.toString(),
        title.escaped(),
        artist.escaped(),
        album.escaped(),
        durationMs.toString(),
        trackNumber.toString()
    ).joinToString("|")

    private fun String.escaped() = replace("\\", "\\\\").replace("|", "\\p").replace("\n", " ")

    companion object {
        private const val Client = "Ondalune"

        fun of(track: SubsonicTrack, listenedAt: Long) = PendingListen(
            listenedAt = listenedAt,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationSec * 1000L,
            trackNumber = track.trackNumber
        )

        fun parse(raw: String): PendingListen? {
            val parts = raw.splitEscaped()
            if (parts.size != 6) return null
            val at = parts[0].toLongOrNull() ?: return null
            val title = parts[1].takeIf { it.isNotEmpty() } ?: return null
            return PendingListen(
                listenedAt = at,
                title = title,
                artist = parts[2],
                album = parts[3],
                durationMs = parts[4].toLongOrNull() ?: 0L,
                trackNumber = parts[5].toIntOrNull() ?: 0
            )
        }

        private fun String.splitEscaped(): List<String> {
            val out = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < length) {
                val c = this[i]
                when {
                    c == '\\' && i + 1 < length && this[i + 1] == 'p' -> { current.append('|'); i++ }
                    c == '\\' && i + 1 < length && this[i + 1] == '\\' -> { current.append('\\'); i++ }
                    c == '|' -> { out += current.toString(); current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            out += current.toString()
            return out
        }
    }
}
