package io.github.roggzuay.ondalune.data.model

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** How many songs ahead [spreadOut] looks for one that doesn't clash. */
private const val SPREAD_LOOKAHEAD = 12

/**
 * Smart shuffle order (weighted random, `u^(1 / weight)`). Favourites, high ratings and much-played
 * songs still come up sooner, but only gently (the square root of their weight), and songs among
 * the last plays ([recentIds], most recent first) move towards the back. Without that, every
 * shuffle opened with the same few favourites, whose play counts then grew and made it worse.
 */
fun smartShuffle(
    tracks: List<SubsonicTrack>,
    profile: SmartShuffleProfile,
    recentIds: List<String>,
    after: SubsonicTrack? = null,
    random: Random = Random
): List<SubsonicTrack> {
    // At most half the pool counts as recent, so a short album isn't simply reversed.
    val window = minOf(recentIds.size, 200, tracks.size / 2)
    val recency = recentIds.take(window).withIndex().associate { (i, id) -> id to i }
    val keyed = tracks.map { track ->
        var weight = sqrt(track.smartShuffleWeight(profile))
        recency[track.id]?.let { i -> weight *= 0.05 + 0.95 * (i.toDouble() / window).pow(2) }
        track to random.nextDouble().pow(1.0 / weight)
    }
    return spreadOut(keyed.sortedByDescending { it.second }.map { it.first }, after)
}

/**
 * Keeps the order of [tracks] but avoids the same artist within two songs and the same album
 * twice in a row: each slot takes the first of the next few songs that doesn't clash, or simply
 * the next one when they all do (a single album, say). [after] is the song playing before them.
 */
fun spreadOut(tracks: List<SubsonicTrack>, after: SubsonicTrack? = null): List<SubsonicTrack> {
    val pending = ArrayDeque(tracks)
    val out = ArrayList<SubsonicTrack>(tracks.size)
    while (pending.isNotEmpty()) {
        val last = out.lastOrNull() ?: after
        val beforeLast = if (out.size >= 2) out[out.size - 2] else if (out.size == 1) after else null
        fun clashes(t: SubsonicTrack) =
            (last != null && (last.artist.equals(t.artist, ignoreCase = true) || last.albumId == t.albumId)) ||
                (beforeLast != null && beforeLast.artist.equals(t.artist, ignoreCase = true))
        val pick = (0 until minOf(pending.size, SPREAD_LOOKAHEAD)).firstOrNull { !clashes(pending[it]) } ?: 0
        out += pending.removeAt(pick)
    }
    return out
}
