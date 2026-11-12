package io.github.roggzuay.ondalune.data.lyrics

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** LRCLIB could not be reached or kept refusing; worth trying again later, unlike "not found". */
class LrcLibUnavailable(message: String) : IOException(message)

/**
 * The LRCLIB API (https://lrclib.net/docs). Only song details are sent: title, artist, album and
 * length, never audio. Requests go one at a time, at least a second apart, so a library-wide
 * search can't flood the service; a refusal or a dropped connection is retried twice with growing
 * waits (or the wait the server asks for) before giving up.
 */
class LrcLibClient(
    userAgent: String,
    private val base: HttpUrl = "https://lrclib.net/api/".toHttpUrl(),
    private val spacingMs: Long = 1_000
) {
    private val agent = userAgent
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private var lastRequestAt = 0L

    /** The record LRCLIB matches to these exact details (it allows ±2 s on length), or null. */
    suspend fun get(query: LyricsQuery): LyricsCandidate? {
        val url = base.newBuilder().addPathSegment("get")
            .addQueryParameter("track_name", query.title)
            .addQueryParameter("artist_name", query.artist)
            .apply { if (query.album.isNotBlank()) addQueryParameter("album_name", query.album) }
            .apply { if (query.durationSec > 0) addQueryParameter("duration", query.durationSec.toString()) }
            .build()
        return request(url)?.let { candidate(JSONObject(it)) }
    }

    /** Records whose title and artist look like these. Empty when nothing does. */
    suspend fun search(title: String, artist: String, album: String = ""): List<LyricsCandidate> {
        val url = base.newBuilder().addPathSegment("search")
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .apply { if (album.isNotBlank()) addQueryParameter("album_name", album) }
            .build()
        return request(url)?.let(::candidates).orEmpty()
    }

    /** Free-text search across title, artist and album. */
    suspend fun search(text: String): List<LyricsCandidate> {
        val url = base.newBuilder().addPathSegment("search").addQueryParameter("q", text).build()
        return request(url)?.let(::candidates).orEmpty()
    }

    /** The body, or null for "not found"; throws [LrcLibUnavailable] once retries run out. */
    private suspend fun request(url: HttpUrl): String? = gate.withLock {
        var lastProblem = "no answer"
        for (attempt in 0..2) {
            val wait = lastRequestAt + spacingMs - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
            val request = Request.Builder().url(url)
                .header("User-Agent", agent)
                .header("Lrclib-Client", agent)
                .build()
            var retryAfterMs: Long? = null
            try {
                http.newCall(request).await().use { response ->
                    when {
                        response.isSuccessful -> return@withLock response.body?.string().orEmpty()
                        response.code == 404 -> return@withLock null
                        response.code == 429 || response.code >= 500 -> {
                            lastProblem = "HTTP ${response.code}"
                            retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1000)
                        }
                        else -> throw LrcLibUnavailable("HTTP ${response.code}")
                    }
                }
            } catch (e: LrcLibUnavailable) {
                throw e
            } catch (e: IOException) {
                lastProblem = e.message ?: "connection failed"
            }
            if (attempt < 2) delay((retryAfterMs ?: (2_000L * (attempt * 2 + 1))).coerceAtMost(30_000))
        }
        throw LrcLibUnavailable(lastProblem)
    }

    private fun candidates(body: String): List<LyricsCandidate> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::candidate) }
    }

    private fun candidate(o: JSONObject) = LyricsCandidate(
        id = o.optLong("id"),
        title = o.optString("trackName", o.optString("name")),
        artist = o.optString("artistName"),
        album = o.optNullableString("albumName"),
        durationSec = o.optDouble("duration", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        instrumental = o.optBoolean("instrumental"),
        plain = o.optNullableString("plainLyrics"),
        synced = o.optNullableString("syncedLyrics"),
        lyricsfile = o.optNullableString("lyricsfile"),
        hasWordSync = o.optBoolean("hasWordSync")
    )
}

private fun JSONObject.optNullableString(key: String): String = if (isNull(key)) "" else optString(key)

/** Runs the call without holding a thread, and cancels it with the coroutine. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}
