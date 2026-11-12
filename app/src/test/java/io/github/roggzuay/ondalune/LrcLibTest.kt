package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.lyrics.LyricsCandidate
import io.github.roggzuay.ondalune.data.lyrics.LyricsIssue
import io.github.roggzuay.ondalune.data.lyrics.LyricsOverlay
import io.github.roggzuay.ondalune.data.lyrics.LyricsQuality
import io.github.roggzuay.ondalune.data.lyrics.LyricsQuery
import io.github.roggzuay.ondalune.data.lyrics.checkLrc
import io.github.roggzuay.ondalune.data.lyrics.cleanTitle
import io.github.roggzuay.ondalune.data.lyrics.parseLyricsfile
import io.github.roggzuay.ondalune.data.lyrics.pickLyrics
import io.github.roggzuay.ondalune.data.lyrics.primaryArtist
import io.github.roggzuay.ondalune.data.lyrics.quality
import io.github.roggzuay.ondalune.data.lyrics.rankCandidates
import io.github.roggzuay.ondalune.data.lyrics.sungFraction
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord
import io.github.roggzuay.ondalune.data.model.LyricsAvailability
import io.github.roggzuay.ondalune.data.model.TrackLyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibTest {
    private fun record(
        id: Long, title: String, artist: String = "Lumen Vale", album: String = "Neon Rain", duration: Double = 200.0,
        synced: String = "[00:10.00]First line\n[00:14.00]Second line", plain: String = "First line\nSecond line",
        lyricsfile: String = "", instrumental: Boolean = false
    ) = LyricsCandidate(id, title, artist, album, duration, instrumental, plain, synced, lyricsfile, lyricsfile.isNotEmpty())

    private val song = LyricsQuery("Hold the Line", "Lumen Vale", "Neon Rain", 200)

    // Both layouts LRCLIB writes: lists at their key's column, and indented under it.
    private val compactFile = """
        version: '1.0'
        metadata:
          title: Hold the Line
        lines:
        - text: It's cold out
          start_ms: 10000
          end_ms: 12500
          words:
          - text: 'It''s '
            start_ms: 10000
          - text: 'cold '
            start_ms: 10400
          - text: out
            start_ms: 11000
        - text: ''
          start_ms: 12500
        - text: "Say: hello"
          start_ms: 20000
          end_ms: 21000
          words:
          - text: 'Say: '
            start_ms: 20000
            end_ms: 20300
          - text: hello
            start_ms: 20600
        plain: |
          It's cold out
          Say: hello
    """.trimIndent()

    @Test
    fun `a Lyricsfile gives words that end where the next starts and the line where it says`() {
        val lines = parseLyricsfile(compactFile)
        assertEquals(2, lines.size)
        assertEquals("It's cold out", lines[0].text)
        assertEquals(listOf(LyricWord(10_000, 10_400, "It's "), LyricWord(10_400, 11_000, "cold "), LyricWord(11_000, 12_500, "out")), lines[0].words)
        assertEquals(12_500L, lines[0].endMs)
        // A word's own end is kept, and the pause after it stays a pause.
        assertEquals(LyricWord(20_000, 20_300, "Say: "), lines[1].words[0])
        assertEquals(0f, lines[1].words[1].sungFraction(20_450))
        assertEquals(21_000L, lines[1].words[1].endMs)
    }

    @Test
    fun `an indented Lyricsfile reads the same`() {
        val indented = """
            lines:
              - text: It's cold out
                words:
                  - text: "It's "
                    start_ms: 10000
                  - text: "cold "
                    start_ms: 10400
                  - text: out
                    start_ms: 11000
                start_ms: 10000
                end_ms: 12500
        """.trimIndent()
        assertEquals(parseLyricsfile(compactFile).first(), parseLyricsfile(indented).single())
    }

    @Test
    fun `the exact song is matched with confidence`() {
        val ranked = rankCandidates(song, listOf(record(2, "Hold On", duration = 230.0), record(1, "Hold the Line", duration = 201.0)))
        assertEquals(1L, ranked.first().candidate.id)
        assertTrue(ranked.first().confident)
        assertFalse(ranked[1].confident)
    }

    @Test
    fun `a remaster note or a featured artist doesn't stop a match, a live take does`() {
        val remaster = rankCandidates(song, listOf(record(1, "Hold the Line (2011 Remaster)", artist = "Lumen Vale feat. Ora Kessler"))).single()
        assertTrue(remaster.confident)
        val live = rankCandidates(song, listOf(record(2, "Hold the Line (Live)"))).single()
        assertTrue(live.versionMismatch)
        assertFalse(live.confident)
        val liveSong = song.copy(title = "Hold the Line - Live at the Harbor")
        assertFalse(rankCandidates(liveSong, listOf(record(3, "Hold the Line (Live)"))).single().versionMismatch)
    }

    @Test
    fun `the right words with the wrong length are offered, not used`() {
        val ranked = rankCandidates(song, listOf(record(1, "Hold the Line", duration = 236.0))).single()
        assertFalse(ranked.confident)
        assertTrue(ranked.score > 0.6)
    }

    @Test
    fun `between equal matches the one with word timing comes first`() {
        val words = record(2, "Hold the Line", lyricsfile = compactFile)
        val ranked = rankCandidates(song, listOf(record(1, "Hold the Line"), words, record(3, "Hold the Line", synced = "")))
        assertEquals(listOf(LyricsQuality.WORDS, LyricsQuality.SYNCED, LyricsQuality.PLAIN), ranked.map { it.candidate.quality })
    }

    @Test
    fun `records without lyrics drop out unless they say the song is instrumental`() {
        val empty = record(1, "Hold the Line", synced = "", plain = "")
        val instrumental = empty.copy(id = 2, instrumental = true)
        assertEquals(listOf(2L), rankCandidates(song, listOf(empty, instrumental)).map { it.candidate.id })
    }

    @Test
    fun `different spellings of the same title still match`() {
        val query = LyricsQuery("Don’t Stop (Café Version)", "Beyoncé & Ora", "", 0)
        val ranked = rankCandidates(query, listOf(record(1, "Dont Stop (Cafe Version)", artist = "Beyonce"))).single()
        assertEquals(1.0, ranked.titleMatch, 0.0)
        assertTrue(ranked.artistMatch >= 0.99)
    }

    @Test
    fun `lyrics found never push out better lyrics from the server, lyrics picked by the user do`() {
        val serverWords = TrackLyricsResult(LyricsAvailability.SYNCED, listOf(LyricLine(1_000, "Hi", 1_500, listOf(LyricWord(1_000, 1_500, "Hi")))), "Hi", "server")
        val serverLines = TrackLyricsResult(LyricsAvailability.SYNCED, listOf(LyricLine(1_000, "Hi")), "Hi", "server")
        val plain = TrackLyricsResult(LyricsAvailability.PLAIN_UNSYNCED, emptyList(), "Hi", "server")
        val lines = LyricsOverlay(LyricsOverlay.State.FOUND, 0, record(1, "Hold the Line"))
        val words = LyricsOverlay(LyricsOverlay.State.FOUND, 0, record(2, "Hold the Line", lyricsfile = compactFile))
        assertEquals("server", pickLyrics(serverLines, lines).sourceLabel)
        assertEquals(LyricsQuality.WORDS, pickLyrics(serverLines, words).quality())
        assertEquals(LyricsQuality.SYNCED, pickLyrics(plain, lines).quality())
        assertEquals("server", pickLyrics(serverWords, words).sourceLabel)
        assertEquals(LyricsQuality.SYNCED, pickLyrics(serverWords, lines.copy(state = LyricsOverlay.State.CHOSEN)).quality())
        assertEquals("server", pickLyrics(serverLines, words.copy(state = LyricsOverlay.State.DISMISSED)).sourceLabel)
    }

    @Test
    fun `broken LRC is caught before it is trusted`() {
        val raw = "[ar:Lumen Vale]\n[00:10.00]One\n[00:61.00]Bad seconds\n[00:08.00]Too early\n[00:08.00]Too early\nNo time here\n[05:00.00]After the end"
        val kinds = checkLrc(raw, durationMs = 200_000, recordDurationSec = 230.0).map { it.kind }.toSet()
        assertEquals(
            setOf(
                LyricsIssue.Kind.MALFORMED_TIME, LyricsIssue.Kind.OUT_OF_ORDER, LyricsIssue.Kind.DUPLICATE,
                LyricsIssue.Kind.MISSING_TIME, LyricsIssue.Kind.PAST_END, LyricsIssue.Kind.LENGTH_MISMATCH
            ),
            kinds
        )
        assertTrue(checkLrc("[00:10.00]One\n[00:12.00]\n[00:14.50]Two", 200_000, 200.0).isEmpty())
    }

    @Test
    fun `search fallbacks drop credits and release notes but keep the version`() {
        assertEquals("Hold the Line (Live)", cleanTitle("Hold the Line (Live) [feat. Ora] - 2011 Remastered"))
        assertEquals("Hold the Line", cleanTitle("Hold the Line (Deluxe Edition)"))
        assertEquals("Lumen Vale", primaryArtist("Lumen Vale, Ora Kessler & The Tidewalkers"))
        assertNull(rankCandidates(song, emptyList()).firstOrNull())
    }
}
