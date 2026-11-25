package io.github.roggzuay.ondalune.data.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * A mix built to order, rather than from a fixed recipe.
 *
 * Your Mix already knows how to weigh taste, spread artists out and avoid what you just heard.
 * This puts three dials in front of that, so you can ask for the mix you actually want right now
 * instead of the one the app decided you wanted.
 *
 *  - **Energy** leans towards loud and driving, or quiet and still.
 *  - **Familiarity** leans towards what you play all the time, or what you never have.
 *  - **Era** leans towards a year you pick, or ignores the year entirely.
 *
 * A dial in the middle means "no opinion", so a mix with everything centred is Your Mix.
 *
 * Energy needs the on-device song analysis to have measured a song; without it the dial has
 * nothing to go on for that song, and [mixFromSeed] leaves it at no opinion rather than guessing.
 */
data class MixSeed(
    /** 0 still, 0.5 no opinion, 1 driving. */
    val energy: Float = 0.5f,
    /** 0 songs you never play, 0.5 no opinion, 1 the ones you always do. */
    val familiarity: Float = 0.5f,
    /** The year to lean towards; null for no opinion. */
    val year: Int? = null,
    /** Only songs by this artist and the artists you play alongside them; null for the lot. */
    val artist: String? = null,
    /** Only songs with this genre; null for the lot. */
    val genre: String? = null,
    val size: Int = 50
) {
    val isPlain: Boolean
        get() = abs(energy - 0.5f) < 0.02f && abs(familiarity - 0.5f) < 0.02f &&
            year == null && artist == null && genre == null

    /** What the mix is called once it is made: "Driving, and new to you". */
    val title: String
        get() {
            val parts = mutableListOf<String>()
            when {
                energy >= 0.75f -> parts += "Driving"
                energy <= 0.25f -> parts += "Still"
            }
            when {
                familiarity >= 0.75f -> parts += "the ones you always play"
                familiarity <= 0.25f -> parts += "new to you"
            }
            artist?.let { parts += "around $it" }
            genre?.let { parts += it }
            year?.let { parts += "around $it" }
            if (parts.isEmpty()) return "A mix for now"
            return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
        }
}

/**
 * Builds a mix from a seed. The draw is the same weighted one Your Mix uses, with the dials
 * added to each song's weight, so the result is still spread across artists and albums and still
 * avoids what you have just heard.
 */
fun mixFromSeed(
    library: List<SubsonicTrack>,
    seed: MixSeed,
    recentIds: List<String>,
    features: Map<String, SongFeatures>,
    random: Random = Random
): List<SubsonicTrack> {
    val filtered = library
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .filter { track ->
            (seed.artist == null || track.artist.equals(seed.artist, ignoreCase = true)) &&
                (seed.genre == null || track.genreTokens().any { it.equals(seed.genre, ignoreCase = true) })
        }
    if (filtered.isEmpty()) return emptyList()
    if (seed.isPlain) return yourMix(filtered, recentIds, seed.size, random)

    val plays = filtered.map { ln(1.0 + it.playCount) }
    val mostPlayed = plays.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val energyPull = (seed.energy - 0.5f) * 2f      // -1 still .. +1 driving
    val familiarPull = (seed.familiarity - 0.5f) * 2f

    val weighted = filtered.map { track ->
        var weight = 1.0

        // Energy: the measured loudness of the song, when it has been measured.
        val measured = features["${track.serverId}_${track.id}"]?.energy
        if (measured != null && abs(energyPull) > 0.02f) {
            val want = if (energyPull > 0) measured else 1f - measured
            weight *= 0.35 + 1.65 * want * abs(energyPull) + (1.0 - abs(energyPull))
        }

        // Familiarity: hearts and plays pull one way, never having played it pulls the other.
        if (abs(familiarPull) > 0.02f) {
            val known = (ln(1.0 + track.playCount) / mostPlayed).coerceIn(0.0, 1.0) +
                (if (track.starred) 0.35 else 0.0)
            val want = if (familiarPull > 0) known.coerceAtMost(1.0) else 1.0 - known.coerceAtMost(1.0)
            weight *= 0.3 + 1.7 * want * abs(familiarPull) + (1.0 - abs(familiarPull))
        }

        // Era: nearer the year you asked for, heavier. Ten years away is half the weight.
        seed.year?.let { wanted ->
            if (track.year > 0) {
                val distance = abs(track.year - wanted).toDouble()
                weight *= 0.25 + 1.75 * 0.5.pow(distance / 10.0)
            } else {
                weight *= 0.4
            }
        }

        track to weight.coerceAtLeast(0.01)
    }

    // The same weighted draw without replacement Your Mix uses, then its own spreading.
    val drawn = weighted
        .map { (track, weight) -> track to random.nextDouble().pow(1.0 / weight) }
        .sortedByDescending { it.second }
        .map { it.first }

    // Songs just played go to the back rather than out: a short library would otherwise empty.
    val recent = recentIds.take(30).toHashSet()
    val ordered = drawn.filterNot { it.id in recent } + drawn.filter { it.id in recent }
    // The dials are the mix's opinion; this only stops one artist or album running away with it.
    // Running the whole thing back through yourMix would re-weigh by taste and undo the dials.
    return spreadAcross(ordered, seed.size, random)
}
