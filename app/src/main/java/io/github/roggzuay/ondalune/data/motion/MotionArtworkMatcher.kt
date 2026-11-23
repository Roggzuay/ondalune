package io.github.roggzuay.ondalune.data.motion

import java.text.Normalizer
import kotlin.math.abs

/** How sure Ondalune is that a candidate belongs to the song, and why. */
data class MotionMatch(val candidate: MotionCandidate, val score: Float, val reason: String) {
    val accepted: Boolean get() = score >= MotionArtworkMatcher.ACCEPT
}

/**
 * Decides whether artwork a provider offers belongs to the playing album. The strongest identifier
 * both sides know decides: a MusicBrainz release, the same album in Ondalune's library, an ISRC,
 * then album and artist names, then title and artist. Year and length only nudge the score. An
 * artist name alone never matches, and a tie between two different albums is turned down.
 */
object MotionArtworkMatcher {
    const val ACCEPT = 0.85f
    /** Two candidates for different albums this close together are too close to call. */
    private const val TIE = 0.05f

    fun score(want: MotionIdentity, candidate: MotionCandidate): MotionMatch {
        val got = candidate.claimed
        fun match(score: Float, reason: String) = MotionMatch(candidate, score.coerceIn(0f, 1f), reason)

        val wantMb = want.releaseMbid?.takeIf { it.isNotBlank() }
        val gotMb = got.releaseMbid?.takeIf { it.isNotBlank() }
        if (wantMb != null && gotMb != null) {
            return if (wantMb.equals(gotMb, ignoreCase = true)) match(1f, "Same MusicBrainz release")
            else match(0f, "A different MusicBrainz release")
        }
        if (want.albumId.isNotBlank() && got.serverId == want.serverId && got.albumId == want.albumId) {
            return match(0.98f, "The album's own artwork in your library")
        }

        var base: Float
        var reason: String
        val sameIsrc = !want.isrc.isNullOrBlank() && want.isrc.equals(got.isrc, ignoreCase = true)
        val artist = same(want.artist, got.artist)
        when {
            sameIsrc -> { base = 0.9f; reason = "Same recording (ISRC)" }
            artist && same(want.album, got.album) -> { base = 0.8f; reason = "Same album and artist" }
            artist && sameLoosely(want.album, got.album) -> { base = 0.66f; reason = "Same album, another edition" }
            artist && got.album.isBlank() && same(want.title, got.title) -> { base = 0.6f; reason = "Same song and artist" }
            else -> return match(0f, if (artist) "Only the artist matches" else "Nothing matches")
        }
        if (want.year > 0 && got.year > 0) {
            val gap = abs(want.year - got.year)
            if (gap == 0) base += 0.07f else if (gap > 1) { base -= 0.25f; reason += ", another year" }
        }
        if (want.durationSec > 0 && got.durationSec > 0 && got.album.isBlank()) {
            val gap = abs(want.durationSec - got.durationSec)
            if (gap <= 3) base += 0.05f else if (gap > 10) { base -= 0.2f; reason += ", another length" }
        }
        return match(base, reason)
    }

    /**
     * The candidates worth trying, best first: only those sure enough, and none at all when the
     * best two point at different albums with nearly the same score.
     */
    fun pick(want: MotionIdentity, candidates: List<MotionCandidate>): List<MotionMatch> {
        val sure = candidates.map { score(want, it) }.filter { it.accepted }
            .sortedWith(compareByDescending<MotionMatch> { it.score }.thenBy { it.candidate.format.ordinal })
        if (sure.size >= 2) {
            val (a, b) = sure
            if (a.score - b.score < TIE && !sameRelease(a.candidate.claimed, b.candidate.claimed)) return emptyList()
        }
        return sure
    }

    private fun sameRelease(a: MotionIdentity, b: MotionIdentity) =
        a.releaseKey == b.releaseKey || (same(a.album, b.album) && same(a.artist, b.artist) && a.year == b.year)

    /** Equal once case, accents, punctuation and spacing are set aside. */
    fun same(a: String, b: String): Boolean {
        val x = normalize(a)
        return x.isNotEmpty() && x == normalize(b)
    }

    /** Equal once edition notes such as "(Deluxe Edition)" or "[2011 Remaster]" are set aside too. */
    fun sameLoosely(a: String, b: String): Boolean {
        val x = normalize(stripEdition(a))
        return x.isNotEmpty() && x == normalize(stripEdition(b))
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private val edition = Regex(
        "\\s*[(\\[][^)\\]]*(edition|deluxe|remaster|expanded|anniversary|bonus|version|explicit|clean)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )

    private fun stripEdition(s: String) = s.replace(edition, "")
}
