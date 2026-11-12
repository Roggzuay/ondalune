package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.lyrics.closePhrases
import io.github.roggzuay.ondalune.data.lyrics.isResting
import io.github.roggzuay.ondalune.data.lyrics.lineIndexAt
import io.github.roggzuay.ondalune.data.lyrics.parseLrc
import io.github.roggzuay.ondalune.data.lyrics.sungFraction
import io.github.roggzuay.ondalune.data.lyrics.timedLine
import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimingTest {
    /** Two sung phrases with a two-second instrumental between them, every word timed. */
    private val enhanced = parseLrc(
        """
        [00:10.00]<00:10.00>Hold <00:10.40>the <00:10.60>line<00:11.50>
        [00:13.50]<00:13.50>Here <00:14.00>we <00:14.20>go<00:15.00>
        """.trimIndent()
    )

    @Test
    fun `ordinary LRC gives line starts and nothing more`() {
        val lines = parseLrc("[00:10.00]Hold the line\n[00:13.50]Here we go")
        assertEquals(listOf(LyricLine(10_000, "Hold the line"), LyricLine(13_500, "Here we go")), lines)
        assertTrue("no word timing is made up", lines.all { it.words.isEmpty() })
        assertTrue("no phrase end is made up", lines.all { it.endMs == null })
        // The gap before the next line may be an instrumental or a held note: the file does not say.
        assertFalse(lines[0].isResting(13_000))
    }

    @Test
    fun `every timestamp spelling is read`() {
        val lines = parseLrc("[00:01]a\n[00:02.5]b\n[00:03.25]c\n[00:04.125]d\n[01:05:50]e\n[100:00.00]f")
        assertEquals(listOf(1_000L, 2_500L, 3_250L, 4_125L, 65_500L, 6_000_000L), lines.map { it.timeMs })
    }

    @Test
    fun `a line with several timestamps is sung at each of them, in order`() {
        val lines = parseLrc("[00:30.00][00:10.00]Chorus\n[00:20.00]Verse")
        assertEquals(listOf(10_000L to "Chorus", 20_000L to "Verse", 30_000L to "Chorus"), lines.map { it.timeMs to it.text })
    }

    @Test
    fun `tags that are not lyrics are skipped and the offset is applied`() {
        val lines = parseLrc("[ar:Someone]\n[ti:Song]\n[length:03:20]\n[offset:+500]\n[00:10.00]Sooner\n[00:00.20]Never before zero")
        assertEquals(listOf(0L to "Never before zero", 9_500L to "Sooner"), lines.map { it.timeMs to it.text })
        assertEquals(10_250L, parseLrc("[offset:-250]\n[00:10.00]Later").single().timeMs)
    }

    @Test
    fun `text that is not LRC gives no lines and never throws`() {
        assertEquals(emptyList<LyricLine>(), parseLrc(""))
        assertEquals(emptyList<LyricLine>(), parseLrc("Just some words\nover two lines"))
        assertEquals(listOf(LyricLine(5_000, "kept")), parseLrc("[xx:yy]bad\n[00:05.00]kept\n[00:0"))
    }

    @Test
    fun `enhanced LRC gives each word its own start and end`() {
        val first = enhanced[0]
        assertEquals("Hold the line", first.text)
        assertEquals(
            listOf(LyricWord(10_000, 10_400, "Hold "), LyricWord(10_400, 10_600, "the "), LyricWord(10_600, 11_500, "line")),
            first.words
        )
        assertEquals("the tag after the last word ends the phrase", 11_500L, first.endMs)
    }

    @Test
    fun `spaces around word tags never join or double words`() {
        fun textOf(lrc: String) = parseLrc(lrc).single().let { line -> line.text.also { assertEquals(it, line.words.joinToString("") { w -> w.text }) } }
        assertEquals("Hold the line", textOf("[00:10.00] <00:10.00> Hold <00:10.40> the <00:10.60> line <00:11.50>"))
        assertEquals("Hold the line", textOf("[00:10.00]<00:10.00>Hold<00:10.40> the<00:10.60> line"))
        assertEquals("Hello", textOf("[00:10.00]<00:10.00>Hel<00:10.40>lo"))
        assertEquals("Hold the line", textOf("[00:10.00]Hold <00:10.40>the <00:10.60>line"))
    }

    @Test
    fun `a last word with no closing tag gets no made-up ending`() {
        val line = parseLrc("[00:10.00]<00:10.00>Hold <00:10.40>on").single()
        assertEquals(LyricWord(10_400, 10_400, "on"), line.words.last())
        assertNull(line.endMs)
        assertEquals("it is lit when it starts, with no sweep", 1f, line.words.last().sungFraction(10_400))
    }

    @Test
    fun `word tags on a repeated line are dropped rather than guessed`() {
        val lines = parseLrc("[00:10.00][00:50.00]<00:10.00>Hold <00:10.40>on<00:11.00>")
        assertEquals(listOf(10_000L, 50_000L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "Hold on" && it.words.isEmpty() && it.endMs == null })
    }

    @Test
    fun `an empty timed line ends the phrase before it`() {
        val lines = parseLrc("[00:10.00]Hold the line\n[00:11.50]\n[00:13.50]Here we go\n[00:15.00]")
        assertEquals(listOf("Hold the line", "Here we go"), lines.map { it.text })
        assertEquals(listOf(11_500L, 15_000L), lines.map { it.endMs })
        assertTrue(lines[0].isResting(12_000))
    }

    @Test
    fun `server lines close phrases the same way`() {
        val lines = listOf(LyricLine(13_500, "Here we go"), LyricLine(11_500, ""), LyricLine(10_000, "Hold the line")).closePhrases()
        assertEquals(listOf(LyricLine(10_000, "Hold the line", endMs = 11_500), LyricLine(13_500, "Here we go")), lines)
    }

    @Test
    fun `words fill as they are sung`() {
        val (hold, the, line) = enhanced[0].words
        assertEquals(0f, hold.sungFraction(9_999))
        assertEquals(0.5f, hold.sungFraction(10_200))
        assertEquals(1f, hold.sungFraction(10_400))
        assertEquals("the word after is untouched", 0f, the.sungFraction(10_200))
        assertEquals(1f / 3f, line.sungFraction(10_900), 0.001f)
    }

    @Test
    fun `nothing moves through an instrumental pause`() {
        val pause = 11_500L until 13_500L
        for (positionMs in pause step 100) {
            assertEquals("the sung line is still the current one", 0, enhanced.lineIndexAt(positionMs))
            assertTrue(enhanced[0].isResting(positionMs))
            assertTrue("every word stays complete", enhanced[0].words.all { it.sungFraction(positionMs) == 1f })
            assertTrue("the next line has not begun", enhanced[1].words.all { it.sungFraction(positionMs) == 0f })
        }
        assertFalse("still singing just before the last word ends", enhanced[0].isResting(11_499))
        assertEquals("the next line begins at its own timestamp", 1, enhanced.lineIndexAt(13_500))
        assertFalse(enhanced[1].isResting(13_500))
    }

    @Test
    fun `a silence inside a line holds the words on both sides of it`() {
        val words = parseLrc("[00:10.00]<00:10.00>Wait <00:10.50> <00:12.00>now<00:12.40>").single().words
        assertEquals(listOf(LyricWord(10_000, 10_500, "Wait "), LyricWord(12_000, 12_400, "now")), words)
        assertEquals(listOf(1f, 0f), words.map { it.sungFraction(11_000) })
    }

    @Test
    fun `the state at a position does not depend on how playback got there`() {
        // Seeking, pausing and resuming only change the position asked about.
        val positions = listOf(14_100L, 0L, 10_500L, 12_000L, 10_500L, 99_000L, 13_499L)
        val states = positions.map { enhanced.lineIndexAt(it) to enhanced.getOrNull(enhanced.lineIndexAt(it))?.isResting(it) }
        assertEquals(listOf(1 to false, -1 to null, 0 to false, 0 to true, 0 to false, 1 to true, 0 to true), states)
    }

    @Test
    fun `before the first line nothing is current`() {
        assertEquals(-1, enhanced.lineIndexAt(0))
        assertEquals(-1, emptyList<LyricLine>().lineIndexAt(5_000))
        assertEquals(0, enhanced.lineIndexAt(10_000))
    }

    @Test
    fun `word timing passed through by a server reads as words, with pauses between them kept`() {
        // As Navidrome serves word-timed lyrics: a voice tag, then each word between its start and end.
        val line = timedLine("v1:<00:10.871>Sea <00:11.251><00:11.963>can <00:12.237><00:12.237>run hi<00:13.192><00:13.192>gher<00:13.900>", 10_871)
        assertEquals("Sea can run higher", line.text)
        assertEquals(13_900L, line.endMs)
        assertEquals(LyricWord(10_871, 11_251, "Sea "), line.words[0])
        assertEquals(LyricWord(11_963, 12_237, "can "), line.words[1])
        // Between "Sea" ending and "can" starting nothing is being sung.
        assertEquals(listOf(1f, 0f), line.words.take(2).map { it.sungFraction(11_600) })
    }

    @Test
    fun `background vocals join the line in brackets and the line ends when both have`() {
        val line = timedLine(
            "v1:<00:15.938>Keep <00:16.062><00:16.062>it <00:16.460><00:16.460>warm <00:19.659>\n" +
                "[bg:<00:17.597><00:17.597>Warm <00:17.931><00:17.931>now<00:19.712><00:19.712>]",
            15_938
        )
        assertEquals("Keep it warm (Warm now)", line.text)
        assertEquals(19_712L, line.endMs)
        assertEquals(LyricWord(17_597, 17_931, "(Warm "), line.words[3])
        // Both voices fill at once where they overlap.
        assertTrue(line.words[2].sungFraction(17_700) > 0f && line.words[3].sungFraction(17_700) > 0f)
    }

    @Test
    fun `a server line without word tags is its text`() {
        assertEquals(LyricLine(5_000, "Just a line"), timedLine("Just a line", 5_000))
        assertEquals(LyricLine(5_000, ""), timedLine("", 5_000))
    }
}
