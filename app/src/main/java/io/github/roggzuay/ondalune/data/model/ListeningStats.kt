package io.github.roggzuay.ondalune.data.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Your year in Ondalune, and the smaller windows inside it.
 *
 * Everything here is worked out on this phone from the plays Ondalune wrote down itself. Nothing
 * is sent anywhere, nothing is fetched, and no account is involved.
 *
 * **It is honest about when it starts.** Ondalune only began writing down plays in 2.1, so a year
 * that began before that is a year this can only tell you part of. The page says so rather than
 * quietly showing a small number as though it were the whole story. The all-time counts your
 * server and Ondalune already kept are shown separately, and they do go further back.
 */

/**
 * How far back a page of statistics looks.
 *
 * Ondalune keeps the last [io.github.roggzuay.ondalune.data.model.PlayHistoryLimit] plays and no
 * more, so every window is bounded by that as well as by its own dates. The page says so: a
 * window that has run into the cap is not "all time" and must not call itself that.
 */
enum class StatsWindow(val label: String) {
    LAST_30("Last 30 days"),
    THIS_YEAR("This year"),
    ALL_TIME("As far back as it goes")
}

/** One row of a chart: a thing, how many times, and how long. */
data class StatsRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val plays: Int,
    val seconds: Long,
    val coverArtUrl: String = "",
    val seedHex: String = "#FF6B5B",
    val accentHex: String = "#70F5FF",
    val artStyleIndex: Int = 0
)

/** What a window of listening comes to. */
data class ListeningStats(
    val window: StatsWindow,
    val plays: Int = 0,
    val seconds: Long = 0,
    val songs: Int = 0,
    val artists: Int = 0,
    val albums: Int = 0,
    /** Plays where the speed, pitch or reverb had been changed. */
    val adjustedPlays: Int = 0,
    val topSongs: List<StatsRow> = emptyList(),
    val topArtists: List<StatsRow> = emptyList(),
    val topAlbums: List<StatsRow> = emptyList(),
    /** Plays by hour of the day, 0..23, in the phone's own time zone. */
    val byHour: List<Int> = List(24) { 0 },
    /** Plays by day of the week, Monday first. */
    val byWeekday: List<Int> = List(7) { 0 },
    /** The first play this page could see, so the page can say how far back it goes. */
    val firstPlayAt: Long = 0L,
    /**
     * The window ran into the cap on how many plays are kept, so what it shows is the newest
     * [PlayHistoryLimit] plays rather than everything in those dates. The page says so.
     */
    val reachedTheCap: Boolean = false
) {
    val isEmpty: Boolean get() = plays == 0

    /**
     * "14 hours, 20 minutes" — the number people actually want out of this.
     *
     * It adds up the length of the songs played, not how much of each was heard: a play counts
     * once it is past the point Ondalune counts a play, and nothing measures where you skipped.
     * The page says "of music" rather than claiming you sat through all of it.
     */
    val listeningTime: String get() = humanDuration(seconds)

    /** The hour the music is on most, as "21:00"; null when nothing has been played. */
    val busiestHour: String?
        get() {
            if (isEmpty) return null
            val hour = byHour.indices.maxByOrNull { byHour[it] } ?: return null
            if (byHour[hour] == 0) return null
            return String.format(Locale.US, "%02d:00", hour)
        }

    val busiestDay: String?
        get() {
            if (isEmpty) return null
            val day = byWeekday.indices.maxByOrNull { byWeekday[it] } ?: return null
            if (byWeekday[day] == 0) return null
            return WeekdayNames.getOrNull(day)
        }

    /** How much of the listening was a song bent into some other shape. */
    val adjustedShare: Int
        get() = if (plays == 0) 0 else ((adjustedPlays * 100f) / plays).toInt()
}

private val WeekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** "3 hours, 12 minutes", "45 minutes", "under a minute". */
fun humanDuration(seconds: Long): String {
    if (seconds < 60) return "under a minute"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val parts = mutableListOf<String>()
    if (hours > 0) parts += if (hours == 1L) "1 hour" else "$hours hours"
    if (minutes > 0) parts += if (minutes == 1L) "1 minute" else "$minutes minutes"
    return parts.joinToString(", ")
}

/** The first moment a window covers; 0 for all of it. */
fun windowStart(window: StatsWindow, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    when (window) {
        StatsWindow.ALL_TIME -> 0L
        StatsWindow.LAST_30 -> nowMs - 30L * 24 * 60 * 60 * 1000
        StatsWindow.THIS_YEAR -> Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

/**
 * Works out a window of listening from the plays Ondalune wrote down.
 *
 * A play of a song that has since left the library still counts towards the totals — it happened
 * — but it cannot appear in a chart, because there is nothing left to name or draw.
 */
fun listeningStats(
    history: List<PlayHistoryEntry>,
    tracks: List<SubsonicTrack>,
    window: StatsWindow,
    nowMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
    topCount: Int = 10
): ListeningStats {
    val from = windowStart(window, nowMs, zone)
    val inWindow = history.filter { it.playedAt in from..nowMs }
    if (inWindow.isEmpty()) return ListeningStats(window = window)

    val byId = tracks.associateBy { it.id }
    // One pass each, rather than a full library scan per row of every chart.
    val anyByArtist = HashMap<String, SubsonicTrack>()
    val anyByAlbum = HashMap<String, SubsonicTrack>()
    tracks.forEach { track ->
        if (track.artist.isNotBlank()) anyByArtist.putIfAbsent(track.artist, track)
        if (track.albumId.isNotBlank()) anyByAlbum.putIfAbsent(track.albumId, track)
    }
    val calendar = Calendar.getInstance(zone)
    val hours = IntArray(24)
    val weekdays = IntArray(7)

    var seconds = 0L
    var adjusted = 0
    val songPlays = LinkedHashMap<String, Int>()
    val songSeconds = LinkedHashMap<String, Long>()
    val artistPlays = LinkedHashMap<String, Int>()
    val artistSeconds = LinkedHashMap<String, Long>()
    val albumPlays = LinkedHashMap<String, Int>()
    val albumSeconds = LinkedHashMap<String, Long>()

    for (entry in inWindow) {
        calendar.timeInMillis = entry.playedAt
        hours[calendar.get(Calendar.HOUR_OF_DAY)]++
        // Calendar counts Sunday as 1; this list starts the week on Monday, as people do.
        weekdays[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7]++
        if (entry.adjusted) adjusted++

        val track = byId[entry.trackId] ?: continue
        val length = track.durationSec.toLong().coerceAtLeast(0L)
        seconds += length
        songPlays[track.id] = (songPlays[track.id] ?: 0) + 1
        songSeconds[track.id] = (songSeconds[track.id] ?: 0L) + length
        if (track.artist.isNotBlank()) {
            artistPlays[track.artist] = (artistPlays[track.artist] ?: 0) + 1
            artistSeconds[track.artist] = (artistSeconds[track.artist] ?: 0L) + length
        }
        if (track.albumId.isNotBlank()) {
            albumPlays[track.albumId] = (albumPlays[track.albumId] ?: 0) + 1
            albumSeconds[track.albumId] = (albumSeconds[track.albumId] ?: 0L) + length
        }
    }

    fun songRows(): List<StatsRow> = songPlays.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(topCount)
        .mapNotNull { (id, plays) ->
            val track = byId[id] ?: return@mapNotNull null
            StatsRow(
                key = id,
                title = track.title,
                subtitle = track.artist,
                plays = plays,
                seconds = songSeconds[id] ?: 0L,
                coverArtUrl = track.coverArtUrl,
                seedHex = track.paletteSeedHex,
                accentHex = track.accentHex,
                artStyleIndex = track.artStyleIndex
            )
        }

    fun artistRows(): List<StatsRow> = artistPlays.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(topCount)
        .map { (artist, plays) ->
            val any = anyByArtist[artist]
            StatsRow(
                key = artist,
                title = artist,
                subtitle = statsCountOf(plays, "play"),
                plays = plays,
                seconds = artistSeconds[artist] ?: 0L,
                coverArtUrl = any?.artistImageUrl?.takeIf { it.isNotBlank() } ?: any?.coverArtUrl.orEmpty(),
                seedHex = any?.paletteSeedHex ?: "#FF6B5B",
                accentHex = any?.accentHex ?: "#70F5FF",
                artStyleIndex = any?.artStyleIndex ?: 0
            )
        }

    fun albumRows(): List<StatsRow> = albumPlays.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(topCount)
        .mapNotNull { (albumId, plays) ->
            val any = anyByAlbum[albumId] ?: return@mapNotNull null
            StatsRow(
                key = albumId,
                title = any.album,
                subtitle = any.artist,
                plays = plays,
                seconds = albumSeconds[albumId] ?: 0L,
                coverArtUrl = any.coverArtUrl,
                seedHex = any.paletteSeedHex,
                accentHex = any.accentHex,
                artStyleIndex = any.artStyleIndex
            )
        }

    return ListeningStats(
        window = window,
        // Every window is also bounded by how many plays are kept at all.
        reachedTheCap = history.size >= PlayHistoryLimit && inWindow.size == history.size,
        plays = inWindow.size,
        seconds = seconds,
        songs = songPlays.size,
        artists = artistPlays.size,
        albums = albumPlays.size,
        adjustedPlays = adjusted,
        topSongs = songRows(),
        topArtists = artistRows(),
        topAlbums = albumRows(),
        byHour = hours.toList(),
        byWeekday = weekdays.toList(),
        firstPlayAt = inWindow.minOf { it.playedAt }
    )
}

private fun statsCountOf(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

/**
 * What the library itself already knows, which goes back further than Ondalune's own record: the
 * play counts kept on the songs, by this app and by your server before it.
 */
data class LibraryTotals(
    val countedPlays: Int = 0,
    val songsEverPlayed: Int = 0,
    val favourites: Int = 0,
    val librarySongs: Int = 0,
    val librarySeconds: Long = 0
) {
    val libraryLength: String get() = humanDuration(librarySeconds)
}

fun libraryTotals(tracks: List<SubsonicTrack>): LibraryTotals = LibraryTotals(
    countedPlays = tracks.sumOf { it.playCount },
    songsEverPlayed = tracks.count { it.playCount > 0 },
    favourites = tracks.count { it.starred },
    librarySongs = tracks.size,
    librarySeconds = tracks.sumOf { it.durationSec.toLong() }
)
