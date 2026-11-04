package io.github.roggzuay.ondalune.data.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/** A mix built from the library: a name, a line saying where it comes from, and its songs. */
data class LibraryMix(val key: String, val title: String, val subtitle: String, val tracks: List<SubsonicTrack>) {
    /** Up to four different album covers, for the card's collage. */
    val covers: List<String>
        get() = tracks.asSequence().filter { it.coverArtUrl.isNotBlank() }.distinctBy { it.albumId }.take(4).map { it.coverArtUrl }.toList()
}

/** Today's day number in the phone's time zone; changes at local midnight (java.time needs Android 8). */
fun localEpochDay(nowMs: Long = System.currentTimeMillis()): Long =
    (nowMs + java.util.TimeZone.getDefault().getOffset(nowMs)) / 86_400_000L

private const val MIX_SIZE = 50
private const val MIN_MIX = 10

/** How much a song says about taste: plays, favourites and ratings. */
private fun SubsonicTrack.affinity(): Double = 1.0 + playCount + (if (starred) 5.0 else 0.0) + (if (userRating >= 4) 1.0 else 0.0)

/**
 * Mixes made only from the library and how it is played: the genres and the artist played most,
 * favourites, new arrivals, and much-played songs that haven't come up lately. [seed] fixes the
 * order for a day, so Home doesn't reshuffle while it is open. Mixes too small to be worth playing
 * are left out.
 */
fun libraryMixes(
    library: List<SubsonicTrack>,
    recentIds: List<String>,
    newestAlbumIds: List<String>,
    seed: Long,
    /** Song analysis results by track id; empty unless the user turned analysis on. */
    features: Map<String, SongFeatures> = emptyMap()
): List<LibraryMix> {
    if (library.size < MIN_MIX) return emptyList()
    val random = Random(seed)
    fun ordered(pool: List<SubsonicTrack>) =
        smartShuffle(pool, SmartShuffleProfile.BALANCED, recentIds, random = random).take(MIX_SIZE)
    val mixes = ArrayList<LibraryMix>()

    // Genres, ranked by how much they are played and loved, not by how many songs they hold.
    val byGenre = HashMap<String, MutableList<SubsonicTrack>>()
    library.forEach { t -> t.genreList().forEach { g -> byGenre.getOrPut(g.lowercase()) { ArrayList() } += t } }
    byGenre.entries
        .filter { it.value.size >= MIN_MIX }
        .sortedByDescending { (_, songs) -> songs.sumOf { it.affinity() } }
        .take(3)
        .forEach { (key, songs) ->
            val name = songs.first().genreList().first { it.lowercase() == key }
            val artists = songs.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
            mixes += LibraryMix("genre:$key", "$name mix", artists, ordered(songs))
        }

    // The artist played most, with songs that sound like theirs.
    library.groupBy { it.artist }
        .filter { it.value.size >= 5 && it.key.isNotBlank() }
        .maxByOrNull { (_, songs) -> songs.sumOf { it.playCount + if (it.starred) 5 else 0 } }
        ?.takeIf { (_, songs) -> songs.any { it.playCount > 0 || it.starred } }
        ?.let { (artist, songs) ->
            val own = ordered(songs).take(MIX_SIZE / 2)
            val similar = autoplayPicks(own.take(10), library, recentIds, own.mapTo(HashSet()) { it.id }, MIX_SIZE / 2, random)
            mixes += LibraryMix("artist:$artist", "$artist & similar", "$artist and artists like them", spreadOut((own + similar).shuffled(random)))
        }

    library.filter { it.starred }.takeIf { it.size >= MIN_MIX }?.let {
        mixes += LibraryMix("favourites", "Favourites mix", "Songs you hearted", ordered(it))
    }

    if (newestAlbumIds.isNotEmpty()) {
        val newest = newestAlbumIds.take(12).toSet()
        library.filter { it.albumId in newest }.takeIf { it.size >= MIN_MIX }?.let {
            mixes += LibraryMix("new", "New in your library", "From the albums added last", ordered(it))
        }
    }

    // Mood mixes need song analysis: lively and calm, from tempo and energy.
    if (features.isNotEmpty()) {
        library.filter { t -> features[t.id]?.let { it.energy >= 0.6f && it.bpm >= 112f || it.danceability >= 0.6f && it.bpm >= 108f } == true }
            .takeIf { it.size >= MIN_MIX }
            ?.let { mixes += LibraryMix("mood:upbeat", "Upbeat mix", "Faster, livelier songs, by song analysis", ordered(it)) }
        library.filter { t -> features[t.id]?.let { it.energy <= 0.45f && it.bpm <= 102f } == true }
            .takeIf { it.size >= MIN_MIX }
            ?.let { mixes += LibraryMix("mood:calm", "Calm mix", "Slower, softer songs, by song analysis", ordered(it)) }
    }

    // Played a lot, but not among the last plays: songs worth bringing back.
    val recent = recentIds.take(200).toHashSet()
    library.filter { it.playCount >= 2 && it.id !in recent }.takeIf { it.size >= MIN_MIX }?.let {
        mixes += LibraryMix("forgotten", "Forgotten favourites", "Played a lot, not lately", ordered(it))
    }
    return mixes
}

/**
 * Songs to play when the queue runs out, like the [seeds] (the last songs of the session): shared
 * genres first, then the same artists and nearby years, nudged towards favourites and songs that
 * are played often, and away from the last plays ([recentIds], most recent first). Nothing in
 * [exclude] (the queue) comes back, the same artist never plays twice within two songs, and the
 * same album never twice in a row.
 */
fun autoplayPicks(
    seeds: List<SubsonicTrack>,
    library: List<SubsonicTrack>,
    recentIds: List<String>,
    exclude: Set<String>,
    count: Int,
    random: Random = Random,
    /** Song analysis results by track id: when known, songs near the seeds' tempo and energy rank higher. */
    features: Map<String, SongFeatures> = emptyMap()
): List<SubsonicTrack> {
    if (seeds.isEmpty()) return emptyList()
    val seedFeatures = seeds.mapNotNull { features[it.id] }
    val seedBpm = seedFeatures.map { it.bpm }.average().takeIf { !it.isNaN() }
    val seedEnergy = seedFeatures.map { it.energy }.average().takeIf { !it.isNaN() }
    val seedGenres = seeds.flatMap { it.genreTokens() }.toSet()
    val seedArtists = seeds.map { it.artist.lowercase() }.toSet()
    val seedYear = seeds.map { it.year }.filter { it > 0 }.average().takeIf { !it.isNaN() }
    val window = minOf(recentIds.size, 200)
    val recency = recentIds.take(window).withIndex().associate { (i, id) -> id to i }
    val scored = library.asSequence()
        .filter { it.id !in exclude }
        .mapNotNull { t ->
            var score = t.genreTokens().count { it in seedGenres } * 3.0
            if (t.artist.lowercase() in seedArtists) score += 2.0
            if (seedYear != null && t.year > 0 && kotlin.math.abs(t.year - seedYear) <= 5) score += 1.0
            if (score <= 0.0) return@mapNotNull null
            val f = features[t.id]
            if (f != null && seedBpm != null && seedEnergy != null) {
                score += (1.0 - kotlin.math.abs(f.bpm - seedBpm) / 30.0).coerceAtLeast(0.0)
                score += 0.5 * (1.0 - kotlin.math.abs(f.energy - seedEnergy) * 2).coerceAtLeast(0.0)
            }
            if (t.starred) score += 1.5
            score += ln(1.0 + t.playCount) * 0.5
            // Just heard: pushed back hard, fading over the last 200 plays.
            recency[t.id]?.let { i -> score *= 0.1 + 0.9 * (i.toDouble() / window).pow(2) }
            // Jitter drawn once per song, so the order varies without breaking the sort.
            t to score + random.nextDouble() * 2.5
        }
        .toList()
    // A few spare picks: spreading pushes unavoidable clashes to the end, where they are cut.
    val picks = scored.sortedByDescending { it.second }
        .distinctBy { it.first.albumId to it.first.title.lowercase() }
        .take(count + 8)
        .map { it.first }
    return spreadOut(picks, after = seeds.last()).take(count)
}
