package io.github.roggzuay.ondalune.data.lyrics

import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException

/**
 * Which lyrics a song shows, best first:
 *  1. the server's own word-timed lyrics,
 *  2. word-timed lyrics found on LRCLIB,
 *  3. the server's line-timed lyrics,
 *  4. line-timed lyrics found on LRCLIB,
 *  5. plain lyrics, the server's first.
 * Lyrics the user picked win over all of these until they go back to the server's.
 */
fun pickLyrics(server: TrackLyricsResult, overlay: LyricsOverlay?): TrackLyricsResult {
    val candidate = overlay?.candidate ?: return server
    return when (overlay.state) {
        LyricsOverlay.State.CHOSEN -> candidate.toResult(provenance(candidate, overlay, chosen = true))
        LyricsOverlay.State.FOUND ->
            if (candidate.quality > server.quality()) candidate.toResult(provenance(candidate, overlay, chosen = false))
            else server
        else -> server
    }
}

private fun provenance(c: LyricsCandidate, o: LyricsOverlay, chosen: Boolean): String = listOf(
    "LRCLIB",
    c.quality.label + if (c.quality == LyricsQuality.WORDS) " by the source" else "",
    if (chosen) "chosen by you" else "found by match",
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(o.at))
).joinToString(" · ")

/** What the lyrics picker says about the playing song's lyrics. */
data class LyricsSource(
    /** Short, for the chip on the lyrics: "LRCLIB · Word synced". */
    val label: String = "",
    /** In full, with how it was picked and when. */
    val detail: String = "",
    val fromServer: Boolean = true,
    val serverQuality: LyricsQuality = LyricsQuality.NONE,
    /** An earlier choice can be brought back. */
    val canRestore: Boolean = false,
    /** Close matches were found online but none sure enough to use unasked. */
    val offers: Int = 0
)

/** The lyrics picker's search: what was asked, and what came back. */
data class LyricsSearch(
    val title: String = "",
    val artist: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<RankedCandidate> = emptyList(),
    val error: String? = null
)

/**
 * Finds, keeps and applies lyrics from LRCLIB for songs whose server has nothing as good, and
 * holds a copy of the server's lyrics for playing offline. Separate from playback: it only ever
 * reads song details.
 */
class LyricsService(
    private val store: LyricsStore,
    private val client: LrcLibClient,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** The server's lyrics, or the copy kept from last time when the server can't be reached. */
    suspend fun fromServer(key: String, fetch: suspend () -> TrackLyricsResult): TrackLyricsResult {
        val fetched = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fetched != null && fetched.quality() != LyricsQuality.NONE) {
            store.saveServerCopy(key, fetched)
            return fetched
        }
        return store.serverCopy(key) ?: fetched ?: TrackLyricsResult()
    }

    fun current(key: String, server: TrackLyricsResult): TrackLyricsResult = pickLyrics(server, store.overlay(key))

    fun overlay(key: String): LyricsOverlay? = store.overlay(key)

    /**
     * Looks the song up when its server has no word timing, unless that was settled already.
     * Returns the lyrics to show if they changed. A song whose server has line timing only takes
     * an exact, confident match with word timing; one with less takes the best confident match.
     * Nothing confident is remembered, with how many near matches there were, and asked about
     * again after [RetryAfterMs]. When LRCLIB can't be reached nothing is remembered.
     */
    suspend fun discover(key: String, query: LyricsQuery, server: TrackLyricsResult): TrackLyricsResult? {
        val serverQuality = server.quality()
        if (serverQuality == LyricsQuality.WORDS || query.title.isBlank()) return null
        val known = store.overlay(key)
        if (known != null && (known.state != LyricsOverlay.State.NONE_FOUND || now() - known.at < RetryAfterMs)) return null
        val ranked = try {
            val exact = client.get(query)
            val exactRanked = rankCandidates(query, listOfNotNull(exact))
            if (serverQuality == LyricsQuality.SYNCED || exactRanked.firstOrNull()?.confident == true) exactRanked
            else rankCandidates(query, listOfNotNull(exact) + searchWithFallbacks(query))
        } catch (_: LrcLibUnavailable) {
            return null
        }
        // Only what beats the server's: word timing over line timing, any timing over plain text.
        val needed = when (serverQuality) {
            LyricsQuality.SYNCED -> LyricsQuality.WORDS
            LyricsQuality.PLAIN -> LyricsQuality.SYNCED
            else -> LyricsQuality.PLAIN
        }
        val best = ranked.firstOrNull { it.confident && trustworthy(it, query) }
        val overlay = when {
            best != null && best.candidate.quality >= needed ->
                LyricsOverlay(LyricsOverlay.State.FOUND, now(), best.candidate, best.score)
            else -> LyricsOverlay(LyricsOverlay.State.NONE_FOUND, now(), offers = ranked.count { it.score >= 0.6 && !it.candidate.instrumental })
        }
        // The user may have picked lyrics while LRCLIB was answering; their choice stands.
        if (store.overlay(key)?.state.let { it == LyricsOverlay.State.CHOSEN || it == LyricsOverlay.State.DISMISSED }) return null
        store.saveOverlay(key, overlay)
        return pickLyrics(server, overlay).takeIf { overlay.state == LyricsOverlay.State.FOUND }
    }

    /** For the lyrics picker: what LRCLIB has for these words, best match to the song first. */
    suspend fun search(song: LyricsQuery, title: String, artist: String): List<RankedCandidate> {
        val found = client.search(title, artist).ifEmpty { client.search("$artist $title".trim()) }
        return rankCandidates(song, found)
    }

    /** Uses [candidate] for the song from now on; what was there before can be restored. */
    fun choose(key: String, candidate: LyricsCandidate, confidence: Double?, server: TrackLyricsResult): TrackLyricsResult {
        val overlay = LyricsOverlay(LyricsOverlay.State.CHOSEN, now(), candidate, confidence, previous = store.overlay(key))
        store.saveOverlay(key, overlay)
        return pickLyrics(server, overlay)
    }

    /** Back to the server's lyrics, and no more looking up for this song. Can be undone. */
    fun useServer(key: String, server: TrackLyricsResult): TrackLyricsResult {
        store.saveOverlay(key, LyricsOverlay(LyricsOverlay.State.DISMISSED, now(), previous = store.overlay(key)))
        return server
    }

    /** Swaps back to what was there before the last change. */
    fun restorePrevious(key: String, server: TrackLyricsResult): TrackLyricsResult {
        val current = store.overlay(key) ?: return server
        val previous = current.previous ?: return pickLyrics(server, current)
        val restored = previous.copy(previous = current.copy(previous = null))
        store.saveOverlay(key, restored)
        return pickLyrics(server, restored)
    }

    /**
     * Title and artist as given, then without featured artists and release notes, then as one
     * line of text: at most three requests for a song.
     */
    private suspend fun searchWithFallbacks(query: LyricsQuery): List<LyricsCandidate> {
        client.search(query.title, query.artist).takeIf { it.isNotEmpty() }?.let { return it }
        val title = cleanTitle(query.title)
        val artist = primaryArtist(query.artist)
        if (title != query.title || artist != query.artist) {
            client.search(title, artist).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return client.search("$artist $title".trim())
    }

    /** A record whose times don't hold up (unreadable, past the end of the song) isn't used unasked. */
    private fun trustworthy(ranked: RankedCandidate, query: LyricsQuery): Boolean {
        val c = ranked.candidate
        if (c.instrumental) return false
        val durationMs = query.durationSec * 1000L
        val issues = if (c.quality == LyricsQuality.WORDS) checkLines(c.lines(), durationMs) else checkLrc(c.synced, durationMs)
        return issues.none {
            it.kind == LyricsIssue.Kind.MALFORMED_TIME || it.kind == LyricsIssue.Kind.PAST_END ||
                it.kind == LyricsIssue.Kind.OUT_OF_ORDER || it.kind == LyricsIssue.Kind.LONG_SILENCE
        }
    }

    companion object {
        const val RetryAfterMs = 30L * 24 * 60 * 60 * 1000
    }
}

/** The title without featured artists or remaster and edition notes; versions (Live, Remix) stay. */
fun cleanTitle(title: String): String = title
    .replace(Regex("""\s*[(\[][^)\]]*\b(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+(feat|ft|featuring)\b.*$""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s*[(\[][^)\]]*\b(remaster(ed)?|deluxe|anniversary|edition|bonus track|mono|stereo|explicit|clean)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""\s+-\s+[^-]*\b(remaster(ed)?|deluxe|anniversary|edition|bonus track|mono|stereo)\b.*$""", RegexOption.IGNORE_CASE), "")
    .trim()

/** The first credited artist. */
fun primaryArtist(artist: String): String =
    artist.split(Regex("""\s*(,|;|/|&|\bfeat\.?|\bft\.?|\bfeaturing\b|\swith\s|\sx\s)\s*""", RegexOption.IGNORE_CASE))
        .firstOrNull { it.isNotBlank() }?.trim() ?: artist
