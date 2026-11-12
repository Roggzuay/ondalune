package io.github.roggzuay.ondalune.data.lyrics

import io.github.roggzuay.ondalune.data.model.LyricLine

/** Something wrong with a set of timed lyrics, worded for a person. */
data class LyricsIssue(val kind: Kind, val detail: String) {
    enum class Kind { MALFORMED_TIME, OUT_OF_ORDER, DUPLICATE, MISSING_TIME, PAST_END, LONG_SILENCE, LENGTH_MISMATCH }
}

private val LooksTimed = Regex("""^\s*\[\s*\d+\s*[:.]""")
private val WellTimed = Regex("""^\s*(\[\d{1,3}:[0-5]?\d(?:[.:]\d{1,3})?])+""")
private val MetaTag = Regex("""^\s*\[[a-zA-Z#]+\s*:.*]\s*$""")
private val TimeOf = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/**
 * Checks LRC text before it is trusted: timestamps that can't be read, lines out of order or
 * repeated at the same time, words with no time in an otherwise timed file, lines after the song
 * ends, and a length on record that differs from the song's. [durationMs] 0 when unknown;
 * [recordDurationSec] is the length the lyrics were made for, if known.
 */
fun checkLrc(raw: String, durationMs: Long, recordDurationSec: Double? = null): List<LyricsIssue> {
    val issues = mutableListOf<LyricsIssue>()
    var previous = -1L
    var timedRows = 0
    var untimed = 0
    val seen = HashSet<Pair<Long, String>>()
    for ((index, row) in raw.lines().withIndex()) {
        val text = row.trim()
        if (text.isEmpty() || MetaTag.matches(text)) continue
        if (LooksTimed.containsMatchIn(text) && !WellTimed.containsMatchIn(text)) {
            issues += LyricsIssue(LyricsIssue.Kind.MALFORMED_TIME, "Line ${index + 1}: unreadable time \"${text.take(16)}\"")
            continue
        }
        val times = TimeOf.findAll(WellTimed.find(text)?.value.orEmpty()).map { it.toMs() }.toList()
        if (times.isEmpty()) {
            untimed++
            continue
        }
        timedRows++
        val words = TimeOf.replace(text, "").replace(Regex("""<[^>]*>"""), "").trim()
        for (t in times) {
            if (times.size == 1 && t < previous) {
                issues += LyricsIssue(LyricsIssue.Kind.OUT_OF_ORDER, "Line ${index + 1} comes before the one above it")
            }
            if (words.isNotEmpty() && !seen.add(t to words)) {
                issues += LyricsIssue(LyricsIssue.Kind.DUPLICATE, "Line ${index + 1} repeats \"${words.take(24)}\"")
            }
            if (durationMs > 0 && t > durationMs) {
                issues += LyricsIssue(LyricsIssue.Kind.PAST_END, "Line ${index + 1} starts after the song ends")
            }
        }
        if (times.size == 1) previous = times[0]
    }
    if (timedRows > 0 && untimed > 0) {
        issues += LyricsIssue(LyricsIssue.Kind.MISSING_TIME, "$untimed line${if (untimed == 1) "" else "s"} without a time")
    }
    if (durationMs > 0 && recordDurationSec != null && recordDurationSec > 0 &&
        kotlin.math.abs(recordDurationSec * 1000 - durationMs) > 5_000
    ) {
        issues += LyricsIssue(
            LyricsIssue.Kind.LENGTH_MISMATCH,
            "Made for a ${formatClock((recordDurationSec * 1000).toLong())} version; this one is ${formatClock(durationMs)}"
        )
    }
    return issues
}

/** Word-timed lines have no LRC text to check; their times can still run past the end or stall. */
fun checkLines(lines: List<LyricLine>, durationMs: Long): List<LyricsIssue> {
    val issues = mutableListOf<LyricsIssue>()
    if (durationMs > 0 && lines.any { it.timeMs > durationMs }) {
        issues += LyricsIssue(LyricsIssue.Kind.PAST_END, "Some lines start after the song ends")
    }
    // A silence longer than five minutes between lines is almost always a wrong time.
    lines.zipWithNext().firstOrNull { (a, b) -> b.timeMs - a.timeMs > 300_000 }?.let { (a, _) ->
        issues += LyricsIssue(LyricsIssue.Kind.LONG_SILENCE, "Over five minutes with no words after ${formatClock(a.timeMs)}")
    }
    return issues
}

private fun MatchResult.toMs(): Long {
    val (m, s, f) = destructured
    return m.toLong() * 60_000 + s.toLong() * 1000 + f.padEnd(3, '0').take(3).ifEmpty { "0" }.toLong()
}

private fun formatClock(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)
