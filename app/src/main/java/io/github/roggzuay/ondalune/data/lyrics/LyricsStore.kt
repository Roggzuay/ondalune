package io.github.roggzuay.ondalune.data.lyrics

import android.content.Context
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * What Ondalune holds for one song beside its server's lyrics: lyrics found on or picked from
 * LRCLIB, or the note that nothing suitable was found, so the same song isn't asked about again.
 * The server's files are never changed; this is a local overlay. Whatever was there before a
 * change is kept in [previous], so a change can be undone.
 */
data class LyricsOverlay(
    val state: State,
    /** When LRCLIB was asked, or the choice made (epoch ms). */
    val at: Long,
    val candidate: LyricsCandidate? = null,
    /** How well [candidate] matched the song, 0..1. */
    val confidence: Double? = null,
    /** With [State.NONE_FOUND]: close matches that weren't sure enough to use unasked. */
    val offers: Int = 0,
    val previous: LyricsOverlay? = null
) {
    enum class State {
        /** Matched with confidence and used where better than the server's. */
        FOUND,
        /** Picked by the user; used whatever the server has. */
        CHOSEN,
        /** Asked, nothing sure enough. Asked again after a while. */
        NONE_FOUND,
        /** The user went back to the server's lyrics; nothing is looked up for the song again. */
        DISMISSED
    }
}

/** Files under the app's own storage, one per song: works offline and leaves no trace elsewhere. */
class LyricsStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "lyrics")

    fun overlay(key: String): LyricsOverlay? = read("$key.json")?.let { runCatching { overlayFrom(it) }.getOrNull() }

    fun saveOverlay(key: String, overlay: LyricsOverlay) = write("$key.json", overlayJson(overlay))

    /** The server's lyrics as last fetched, for playing offline. */
    fun serverCopy(key: String): TrackLyricsResult? = read("$key.server.json")?.let { runCatching { resultFrom(it) }.getOrNull() }

    fun saveServerCopy(key: String, result: TrackLyricsResult) = write("$key.server.json", resultJson(result))

    private fun read(name: String): JSONObject? =
        File(dir, name).takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    private fun write(name: String, json: JSONObject) {
        dir.mkdirs()
        val target = File(dir, name)
        val partial = File(dir, "$name.part")
        partial.writeText(json.toString())
        if (!partial.renameTo(target)) partial.delete()
    }
}

/** A file name for a song on a server; hashed, so ids with odd characters are safe. */
fun lyricsKey(serverId: Long, trackId: String): String =
    MessageDigest.getInstance("SHA-1").digest("$serverId:$trackId".toByteArray())
        .joinToString("") { "%02x".format(it) }

private fun overlayJson(o: LyricsOverlay): JSONObject = JSONObject()
    .put("state", o.state.name)
    .put("at", o.at)
    .put("confidence", o.confidence ?: JSONObject.NULL)
    .put("offers", o.offers)
    .put("candidate", o.candidate?.let(::candidateJson) ?: JSONObject.NULL)
    .put("previous", o.previous?.copy(previous = null)?.let(::overlayJson) ?: JSONObject.NULL)

private fun overlayFrom(j: JSONObject): LyricsOverlay = LyricsOverlay(
    state = LyricsOverlay.State.valueOf(j.getString("state")),
    at = j.optLong("at"),
    candidate = j.optJSONObject("candidate")?.let(::candidateFrom),
    confidence = if (j.isNull("confidence")) null else j.optDouble("confidence"),
    offers = j.optInt("offers"),
    previous = j.optJSONObject("previous")?.let(::overlayFrom)
)

private fun candidateJson(c: LyricsCandidate): JSONObject = JSONObject()
    .put("id", c.id).put("title", c.title).put("artist", c.artist).put("album", c.album)
    .put("duration", c.durationSec).put("instrumental", c.instrumental).put("plain", c.plain)
    .put("synced", c.synced).put("lyricsfile", c.lyricsfile).put("hasWordSync", c.hasWordSync)

private fun candidateFrom(j: JSONObject) = LyricsCandidate(
    id = j.optLong("id"), title = j.optString("title"), artist = j.optString("artist"), album = j.optString("album"),
    durationSec = j.optDouble("duration", 0.0), instrumental = j.optBoolean("instrumental"), plain = j.optString("plain"),
    synced = j.optString("synced"), lyricsfile = j.optString("lyricsfile"), hasWordSync = j.optBoolean("hasWordSync")
)

private fun resultJson(r: TrackLyricsResult): JSONObject = JSONObject()
    .put("availability", r.availability.name)
    .put("plain", r.plainLyrics)
    .put("source", r.sourceLabel)
    .put("lines", JSONArray().apply {
        r.syncedLines.forEach { line ->
            put(JSONObject().put("t", line.timeMs).put("x", line.text).put("e", line.endMs ?: JSONObject.NULL)
                .put("w", JSONArray().apply {
                    line.words.forEach { put(JSONArray().put(it.startMs).put(it.endMs).put(it.text)) }
                }))
        }
    })

private fun resultFrom(j: JSONObject): TrackLyricsResult {
    val lines = j.getJSONArray("lines")
    return TrackLyricsResult(
        availability = LyricsAvailability.valueOf(j.getString("availability")),
        syncedLines = (0 until lines.length()).map { i ->
            val l = lines.getJSONObject(i)
            val w = l.getJSONArray("w")
            LyricLine(
                timeMs = l.getLong("t"),
                text = l.getString("x"),
                endMs = if (l.isNull("e")) null else l.getLong("e"),
                words = (0 until w.length()).map { k -> w.getJSONArray(k).let { LyricWord(it.getLong(0), it.getLong(1), it.getString(2)) } }
            )
        },
        plainLyrics = j.optString("plain"),
        sourceLabel = j.optString("source")
    )
}
