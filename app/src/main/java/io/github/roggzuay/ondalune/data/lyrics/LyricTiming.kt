package io.github.roggzuay.ondalune.data.lyrics

import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord

private val LineTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val WordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
private val OffsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
private val VoiceTag = Regex("""^v\d+:""")

/**
 * LRC text into timed lines, sorted by start.
 *
 * Reads `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and `[mm:ss:xx]`, several timestamps in front of one
 * line, and `[offset:±ms]` (a positive offset makes lyrics appear sooner). Other `[tag:value]` lines
 * and lines with no timestamp are skipped.
 *
 * Enhanced LRC puts a `<mm:ss.xx>` tag in front of each word. A word ends where the next tag
 * stands; a tag with nothing after it ends the last word and so the phrase. A last word with no
 * closing tag has no known end, and gets none.
 */
fun parseLrc(raw: String): List<LyricLine> {
    val offsetMs = OffsetTag.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val timed = mutableListOf<LyricLine>()
    for (source in raw.lineSequence()) {
        var rest = source.trim()
        val starts = mutableListOf<Long>()
        while (true) {
            val tag = LineTag.matchAt(rest, 0) ?: break
            starts += (tag.timeMs() - offsetMs).coerceAtLeast(0L)
            rest = rest.substring(tag.value.length).trimStart()
        }
        if (starts.isEmpty()) continue
        rest = rest.replace(VoiceTag, "")
        // Word tags are absolute times, so they can only belong to a line that is sung once.
        val line = if (starts.size == 1) timedWords(rest, starts[0], offsetMs) else null
        if (line != null) timed += line
        else {
            val text = WordTag.replace(rest, "").collapseSpaces()
            starts.forEach { timed += LyricLine(it, text) }
        }
    }
    return timed.closePhrases()
}

/**
 * One line as a server hands it over, starting at [startMs] (offset already applied). Word-timed
 * lines come as `v1:<start>word <end><start>word <end>…`, the voice tag optional; a background
 * part may follow on its own row as `[bg:<start>word <end>…]`. Background words join the line in
 * brackets, each with its own timing, and the line ends when both parts have. A line with no word
 * tags is its text.
 */
fun timedLine(value: String, startMs: Long, offsetMs: Long = 0L): LyricLine {
    val rows = value.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val main = rows.firstOrNull { !it.startsWith("[bg:") }.orEmpty().replace(VoiceTag, "")
    val line = timedWords(main, startMs, offsetMs) ?: LyricLine(startMs, WordTag.replace(main, "").collapseSpaces())
    val background = rows.filter { it.startsWith("[bg:") }
        .mapNotNull { timedWords(it.removePrefix("[bg:").removeSuffix("]"), startMs, offsetMs) }
        .filter { it.words.isNotEmpty() }
    if (line.words.isEmpty() || background.isEmpty()) return line
    val words = line.words.toMutableList()
    var endMs = line.endMs
    for (part in background) {
        val extra = part.words.toMutableList()
        if (!part.text.startsWith("(")) {
            extra[0] = extra[0].copy(text = "(" + extra[0].text)
            extra[extra.lastIndex] = extra.last().copy(text = extra.last().text + ")")
        }
        words[words.lastIndex] = words.last().copy(text = words.last().text + " ")
        words += extra
        // Unknown if either part's end is unknown.
        endMs = if (endMs == null || part.endMs == null) null else maxOf(endMs, part.endMs)
    }
    return LyricLine(startMs, words.joinToString("") { it.text }, endMs, words)
}

/**
 * Sorts [this] by start and turns each empty timed line into the end of the line before it, which
 * is what an empty line means: the singer has stopped. The empty lines themselves are dropped.
 */
fun List<LyricLine>.closePhrases(): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    for (entry in sortedBy { it.timeMs }) {
        if (entry.text.isNotBlank()) lines += entry
        else lines.lastOrNull()?.takeIf { it.endMs == null && entry.timeMs > it.timeMs }
            ?.let { lines[lines.lastIndex] = it.copy(endMs = entry.timeMs) }
    }
    return lines
}

/** The line being sung, or last sung, at [positionMs]; -1 before the first line starts. */
fun List<LyricLine>.lineIndexAt(positionMs: Long): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].timeMs <= positionMs) low = mid + 1 else high = mid - 1
    }
    return high
}

/** True between the end of this line's vocal and the next line: sung, and nothing is being sung. */
fun LyricLine.isResting(positionMs: Long): Boolean = endMs != null && positionMs >= endMs

/** How much of this word has been sung at [positionMs]: 0 before it starts, 1 from where it ends. */
fun LyricWord.sungFraction(positionMs: Long): Float = when {
    positionMs < startMs -> 0f
    positionMs >= endMs -> 1f
    else -> (positionMs - startMs).toFloat() / (endMs - startMs)
}

private fun timedWords(text: String, lineStartMs: Long, offsetMs: Long): LyricLine? {
    val tags = WordTag.findAll(text).toList()
    if (tags.isEmpty()) return null
    val words = mutableListOf<LyricWord>()
    // A word keeps the space after it, so the words put back together read as the line.
    fun add(startMs: Long, endMs: Long, segment: String) {
        var word = segment.replace(Spaces, " ")
        val previous = words.lastOrNull()
        if (word.startsWith(" ")) {
            if (previous != null && !previous.text.endsWith(" ")) words[words.lastIndex] = previous.copy(text = previous.text + " ")
            word = word.trimStart()
        }
        words += LyricWord(startMs, endMs, word)
    }
    val tagTimes = tags.map { (it.timeMs() - offsetMs).coerceAtLeast(0L) }
    text.substring(0, tags[0].range.first).takeIf { it.isNotBlank() }?.let { add(lineStartMs, tagTimes[0], it) }
    var phraseEndMs: Long? = null
    tags.forEachIndexed { i, tag ->
        val last = i == tags.lastIndex
        val segment = text.substring(tag.range.last + 1, if (last) text.length else tags[i + 1].range.first)
        when {
            // With no tag after it, where the word ends is unknown: it starts and that is all.
            segment.isNotBlank() -> add(tagTimes[i], if (last) tagTimes[i] else tagTimes[i + 1].coerceAtLeast(tagTimes[i]), segment)
            last -> phraseEndMs = tagTimes[i]
        }
    }
    if (words.isEmpty()) return LyricLine(lineStartMs, "")
    words[words.lastIndex] = words.last().let { it.copy(text = it.text.trimEnd()) }
    return LyricLine(lineStartMs, words.joinToString("") { it.text }, phraseEndMs, words)
}

private fun MatchResult.timeMs(): Long {
    val (minutes, seconds, fraction) = destructured
    return minutes.toLong() * 60_000L + seconds.toLong() * 1000L + fraction.padEnd(3, '0').toLong()
}

private val Spaces = Regex("""\s+""")

private fun String.collapseSpaces(): String = trim().replace(Spaces, " ")
