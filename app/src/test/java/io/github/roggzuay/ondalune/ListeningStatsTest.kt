package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.PlayHistoryEntry
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.model.StatsWindow
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.humanDuration
import io.github.roggzuay.ondalune.data.model.libraryTotals
import io.github.roggzuay.ondalune.data.model.listeningStats
import io.github.roggzuay.ondalune.data.model.windowStart
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Your listening, worked out on the phone. The numbers have to be right, and the page has to be
 * honest about the window it can actually see.
 */
class ListeningStatsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** 15 June 2026, 21:30 UTC — a Monday. */
    private val now = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.JUNE, 15, 21, 30, 0)
    }.timeInMillis

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis

    private fun track(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist A",
        albumId: String = "al1",
        album: String = "Album One",
        seconds: Int = 180,
        plays: Int = 0,
        starred: Boolean = false
    ) = SubsonicTrack(
        id = id,
        serverId = 1L,
        title = title,
        artist = artist,
        artistId = "ar-$artist",
        album = album,
        albumId = albumId,
        durationSec = seconds,
        bitRate = 320,
        suffix = "FLAC",
        playCount = plays,
        starred = starred
    )

    private val slowed = SoundSnapshot(
        speedPitch = SpeedPitchState(speed = 0.80f),
        reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT).copy(enabled = true).sanitized()
    )

    private fun play(trackId: String, at: Long, sound: SoundSnapshot = SoundSnapshot()) =
        PlayHistoryEntry(serverId = 1L, trackId = trackId, playedAt = at, sound = sound.stored())

    // ---- The windows ----

    @Test
    fun `all time starts at the beginning`() {
        assertEquals(0L, windowStart(StatsWindow.ALL_TIME, now, utc))
    }

    @Test
    fun `this year starts on the first of January`() {
        assertEquals(at(2026, Calendar.JANUARY, 1, hour = 0), windowStart(StatsWindow.THIS_YEAR, now, utc))
    }

    @Test
    fun `the last thirty days is thirty days back`() {
        assertEquals(now - 30L * 24 * 3600 * 1000, windowStart(StatsWindow.LAST_30, now, utc))
    }

    @Test
    fun `a play from last year is not in this year`() {
        val stats = listeningStats(
            history = listOf(play("s1", at(2025, Calendar.DECEMBER, 20))),
            tracks = listOf(track("s1")),
            window = StatsWindow.THIS_YEAR,
            nowMs = now,
            zone = utc
        )
        assertTrue(stats.isEmpty)
    }

    @Test
    fun `a play that has not happened yet is not counted`() {
        val stats = listeningStats(
            history = listOf(play("s1", now + 86_400_000L)),
            tracks = listOf(track("s1")),
            window = StatsWindow.ALL_TIME,
            nowMs = now,
            zone = utc
        )
        assertTrue(stats.isEmpty)
    }

    // ---- The totals ----

    @Test
    fun `plays, time and the three counts add up`() {
        val tracks = listOf(
            track("s1", seconds = 200, artist = "Artist A", albumId = "al1"),
            track("s2", seconds = 100, artist = "Artist B", albumId = "al2")
        )
        val stats = listeningStats(
            history = listOf(
                play("s1", at(2026, Calendar.MARCH, 1)),
                play("s1", at(2026, Calendar.MARCH, 2)),
                play("s2", at(2026, Calendar.MARCH, 3))
            ),
            tracks = tracks,
            window = StatsWindow.THIS_YEAR,
            nowMs = now,
            zone = utc
        )
        assertEquals(3, stats.plays)
        assertEquals(500L, stats.seconds)
        assertEquals(2, stats.songs)
        assertEquals(2, stats.artists)
        assertEquals(2, stats.albums)
    }

    @Test
    fun `a play of a song the library no longer holds still counts as a play`() {
        val stats = listeningStats(
            history = listOf(play("gone", at(2026, Calendar.MARCH, 1))),
            tracks = listOf(track("s1")),
            window = StatsWindow.THIS_YEAR,
            nowMs = now,
            zone = utc
        )
        assertEquals(1, stats.plays)
        assertEquals(0L, stats.seconds)
        assertTrue(stats.topSongs.isEmpty())
    }

    @Test
    fun `the charts are ordered by plays, with ties settled the same way every time`() {
        val tracks = listOf(track("b"), track("a"), track("c"))
        val stats = listeningStats(
            history = listOf(
                play("c", at(2026, Calendar.MARCH, 1)),
                play("c", at(2026, Calendar.MARCH, 2)),
                play("c", at(2026, Calendar.MARCH, 3)),
                play("b", at(2026, Calendar.MARCH, 4)),
                play("a", at(2026, Calendar.MARCH, 5))
            ),
            tracks = tracks,
            window = StatsWindow.THIS_YEAR,
            nowMs = now,
            zone = utc
        )
        assertEquals(listOf("c", "a", "b"), stats.topSongs.map { it.key })
        assertEquals(3, stats.topSongs.first().plays)
    }

    @Test
    fun `the charts stop at the count they are given`() {
        val tracks = (1..20).map { track("s$it") }
        val history = (1..20).map { play("s$it", at(2026, Calendar.MARCH, 1)) }
        val stats = listeningStats(history, tracks, StatsWindow.THIS_YEAR, now, utc, topCount = 5)
        assertEquals(5, stats.topSongs.size)
    }

    // ---- When the music is on ----

    @Test
    fun `plays land in the right hour and the right day`() {
        val stats = listeningStats(
            history = listOf(
                // 15 June 2026 is a Monday.
                play("s1", at(2026, Calendar.JUNE, 15, hour = 21)),
                play("s1", at(2026, Calendar.JUNE, 15, hour = 21)),
                play("s1", at(2026, Calendar.JUNE, 16, hour = 9))
            ),
            tracks = listOf(track("s1")),
            window = StatsWindow.THIS_YEAR,
            nowMs = at(2026, Calendar.JUNE, 17),
            zone = utc
        )
        assertEquals(2, stats.byHour[21])
        assertEquals(1, stats.byHour[9])
        assertEquals("21:00", stats.busiestHour)
        assertEquals("Monday", stats.busiestDay)
        assertEquals(2, stats.byWeekday[0])
        assertEquals(1, stats.byWeekday[1])
    }

    @Test
    fun `with nothing played there is no busiest anything`() {
        val stats = listeningStats(emptyList(), listOf(track("s1")), StatsWindow.ALL_TIME, now, utc)
        assertNull(stats.busiestHour)
        assertNull(stats.busiestDay)
        assertTrue(stats.isEmpty)
    }

    // ---- The part that ties back to Sound Profiles ----

    @Test
    fun `plays with the sound changed are counted and turned into a share`() {
        val stats = listeningStats(
            history = listOf(
                play("s1", at(2026, Calendar.MARCH, 1), slowed),
                play("s1", at(2026, Calendar.MARCH, 2)),
                play("s1", at(2026, Calendar.MARCH, 3)),
                play("s1", at(2026, Calendar.MARCH, 4))
            ),
            tracks = listOf(track("s1")),
            window = StatsWindow.THIS_YEAR,
            nowMs = now,
            zone = utc
        )
        assertEquals(1, stats.adjustedPlays)
        assertEquals(25, stats.adjustedShare)
    }

    @Test
    fun `a share of nothing is nothing, not a crash`() {
        assertEquals(0, listeningStats(emptyList(), emptyList(), StatsWindow.ALL_TIME, now, utc).adjustedShare)
    }

    // ---- How long it reads as ----

    @Test
    fun `time is said the way a person would say it`() {
        assertEquals("under a minute", humanDuration(30))
        assertEquals("1 minute", humanDuration(60))
        assertEquals("45 minutes", humanDuration(45 * 60))
        assertEquals("1 hour", humanDuration(3600))
        assertEquals("3 hours, 12 minutes", humanDuration(3 * 3600 + 12 * 60))
        assertEquals("1 hour, 1 minute", humanDuration(3660))
    }

    // ---- What the library already knew ----

    @Test
    fun `library totals come from the songs themselves`() {
        val totals = libraryTotals(
            listOf(
                track("s1", seconds = 200, plays = 5, starred = true),
                track("s2", seconds = 100, plays = 0),
                track("s3", seconds = 300, plays = 2, starred = true)
            )
        )
        assertEquals(7, totals.countedPlays)
        assertEquals(2, totals.songsEverPlayed)
        assertEquals(2, totals.favourites)
        assertEquals(3, totals.librarySongs)
        assertEquals(600L, totals.librarySeconds)
        assertEquals("10 minutes", totals.libraryLength)
    }

    @Test
    fun `an empty library totals to nothing`() {
        val totals = libraryTotals(emptyList())
        assertEquals(0, totals.countedPlays)
        assertEquals(0, totals.librarySongs)
    }
}
