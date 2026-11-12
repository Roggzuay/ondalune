package io.github.roggzuay.ondalune.data.lyrics

import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import java.text.Normalizer
import kotlin.math.abs

/** How much timing a set of lyrics carries, worst to best. */
enum class LyricsQuality(val label: String) {
    NONE("No lyrics"),
    PLAIN("Plain"),
    SYNCED("Line synced"),
    WORDS("Word synced")
}

fun TrackLyricsResult.quality(): LyricsQuality = when {
    syncedLines.any { it.words.isNotEmpty() } -> LyricsQuality.WORDS
    availability == LyricsAvailability.SYNCED && syncedLines.isNotEmpty() -> LyricsQuality.SYNCED
    plainLyrics.isNotBlank() -> LyricsQuality.PLAIN
    else -> LyricsQuality.NONE
}

/** One LRCLIB record. [lyricsfile] carries word timing when [hasWordSync]. */
data class LyricsCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Double,
    val instrumental: Boolean,
    val plain: String,
    val synced: String,
    val lyricsfile: String,
    val hasWordSync: Boolean
) {
    val quality: LyricsQuality
        get() = when {
            hasWordSync && lyricsfile.isNotBlank() -> LyricsQuality.WORDS
            synced.isNotBlank() -> LyricsQuality.SYNCED
            plain.isNotBlank() -> LyricsQuality.PLAIN
            else -> LyricsQuality.NONE
        }

    /** The timed lines, word timed where the record has word timing. */
    fun lines(): List<LyricLine> = when (quality) {
        LyricsQuality.WORDS -> parseLyricsfile(lyricsfile).ifEmpty { parseLrc(synced) }
        LyricsQuality.SYNCED -> parseLrc(synced)
        else -> emptyList()
    }

    fun toResult(sourceLabel: String): TrackLyricsResult {
        val timed = lines()
        val plainText = plain.ifBlank { timed.joinToString("\n") { it.text } }
        return TrackLyricsResult(
            availability = when {
                timed.isNotEmpty() -> LyricsAvailability.SYNCED
                plainText.isNotBlank() -> LyricsAvailability.PLAIN_UNSYNCED
                else -> LyricsAvailability.UNAVAILABLE
            },
            syncedLines = timed,
            plainLyrics = plainText,
            sourceLabel = sourceLabel
        )
    }
}

/** What is known about the song being matched. [durationSec] 0 when unknown. */
data class LyricsQuery(val title: String, val artist: String, val album: String, val durationSec: Int)

/** A candidate with how well it matches, 0..1, and the parts that went into that. */
data class RankedCandidate(
    val candidate: LyricsCandidate,
    val score: Double,
    val titleMatch: Double,
    val artistMatch: Double,
    val durationOffSec: Double?,
    val versionMismatch: Boolean
) {
    /**
     * Sure enough to use without asking: the same song by the same artist, the same version,
     * and the same length to within two seconds.
     */
    val confident: Boolean
        get() = score >= 0.85 && titleMatch >= 0.9 && artistMatch >= 0.8 && !versionMismatch &&
            durationOffSec != null && durationOffSec <= 2.0
}

/**
 * Orders [candidates] by how well they match [query]: title, artist, album and length, with
 * versions told apart (a live take, a remix, an acoustic or instrumental version is a different
 * recording). Records with no lyrics at all drop out unless they say the song is instrumental.
 * Between close matches the one with more timing goes first.
 */
fun rankCandidates(query: LyricsQuery, candidates: List<LyricsCandidate>): List<RankedCandidate> {
    val title = normalizeTitle(query.title)
    val tags = versionTags(query.title) + versionTags(query.album)
    return candidates
        .filter { it.quality != LyricsQuality.NONE || it.instrumental }
        .distinctBy { it.id }
        .map { c ->
            val titleMatch = similarity(title, normalizeTitle(c.title))
            val artistMatch = artistSimilarity(query.artist, c.artist)
            val albumMatch = if (query.album.isBlank() || c.album.isBlank()) null
            else similarity(normalizeTitle(query.album), normalizeTitle(c.album))
            val off = if (query.durationSec > 0 && c.durationSec > 0) abs(query.durationSec - c.durationSec) else null
            val durationMatch = when {
                off == null -> 0.5
                off <= 2.0 -> 1.0
                off <= 5.0 -> 0.7
                off <= 10.0 -> 0.3
                else -> 0.0
            }
            val mismatch = (versionTags(c.title) + versionTags(c.album)) != tags
            var score = if (albumMatch == null) (0.45 * titleMatch + 0.33 * artistMatch + 0.22 * durationMatch)
            else 0.4 * titleMatch + 0.3 * artistMatch + 0.1 * albumMatch + 0.2 * durationMatch
            if (mismatch) score *= 0.6
            RankedCandidate(c, score, titleMatch, artistMatch, off, mismatch)
        }
        .sortedWith(
            compareByDescending<RankedCandidate> { (it.score * 20).toInt() }
                .thenByDescending { it.candidate.quality }
                .thenByDescending { it.score }
        )
}

/** Words that make a recording a different version of a song. */
private val VersionWords = setOf("live", "acoustic", "remix", "instrumental", "demo", "unplugged", "karaoke", "a cappella", "acapella")

fun versionTags(text: String): Set<String> {
    val plain = fold(text)
    return VersionWords.filterTo(mutableSetOf()) { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(plain) }
        .mapTo(mutableSetOf()) { if (it == "acapella") "a cappella" else it }
}

/** Parts that never tell two recordings apart: who else is featured, remasters, editions. */
private val Noise = listOf(
    Regex("""[(\[][^)\]]*\b(feat|ft|featuring|with)\b[^)\]]*[)\]]"""),
    Regex("""\s(feat|ft|featuring)\b.*$"""),
    Regex("""[(\[][^)\]]*\b(remaster(ed)?|deluxe|expanded|anniversary|edition|bonus track|mono|stereo|explicit|clean)\b[^)\]]*[)\]]"""),
    Regex("""\s-\s[^-]*\b(remaster(ed)?|deluxe|anniversary|edition|bonus track|mono|stereo)\b.*$""")
)

/** A title as compared: folded, featured artists and remaster notes dropped, punctuation gone. */
fun normalizeTitle(text: String): String {
    var s = fold(text)
    Noise.forEach { s = it.replace(s, " ") }
    return s.replace("&", " and ").replace(Regex("""['’`]"""), "").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
}

/** Lower case, accents off, compatibility forms unified (full-width letters, ligatures). */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("""\p{M}+"""), "").lowercase()

private val ArtistSplit = Regex("""\s*(,|;|/|&|\+|\bfeat\.?|\bft\.?|\bfeaturing\b|\bwith\b|\sx\s)\s*""")

/** The better of the whole credit compared and the best pair of individual artists. */
fun artistSimilarity(a: String, b: String): Double {
    val whole = similarity(normalizeTitle(a), normalizeTitle(b))
    val partsA = fold(a).split(ArtistSplit).map { normalizeTitle(it) }.filter { it.isNotEmpty() }
    val partsB = fold(b).split(ArtistSplit).map { normalizeTitle(it) }.filter { it.isNotEmpty() }
    val best = partsA.maxOfOrNull { x -> partsB.maxOfOrNull { y -> similarity(x, y) } ?: 0.0 } ?: 0.0
    return maxOf(whole, best)
}

/**
 * 1 for the same text, otherwise the better of shared words and shared letter pairs, so word
 * order, a missing word and scripts written without spaces all still compare.
 */
fun similarity(a: String, b: String): Double {
    if (a == b) return if (a.isEmpty()) 0.0 else 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val wordsA = a.split(' ').toSet()
    val wordsB = b.split(' ').toSet()
    val words = 2.0 * (wordsA intersect wordsB).size / (wordsA.size + wordsB.size)
    return maxOf(words, pairs(a.replace(" ", ""), b.replace(" ", "")))
}

private fun pairs(a: String, b: String): Double {
    if (a.length < 2 || b.length < 2) return if (a == b) 1.0 else 0.0
    val countsA = a.windowed(2).groupingBy { it }.eachCount()
    val countsB = b.windowed(2).groupingBy { it }.eachCount()
    val shared = countsA.entries.sumOf { (pair, n) -> minOf(n, countsB[pair] ?: 0) }
    return 2.0 * shared / (a.length - 1 + b.length - 1)
}
