package io.github.roggzuay.ondalune.data.lyrics

import io.github.roggzuay.ondalune.data.model.LyricLine
import io.github.roggzuay.ondalune.data.model.LyricWord

/**
 * LRCLIB's Lyricsfile, the YAML that carries its word timing: under `lines:` each line has `text`,
 * `start_ms`, `end_ms` and optionally `words:`, each word with `text`, `start_ms` and optionally
 * `end_ms`. A word with no end of its own lasts until the next word starts, and the last one until
 * its line ends: that is what the file says. Where the line's end is missing too, the last word
 * gets none.
 *
 * Only the part of YAML these files use is read: block lists and maps, one `key: value` per row,
 * plain, 'single' and "double" quoted values. Anything else is skipped rather than guessed at.
 */
fun parseLyricsfile(yaml: String): List<LyricLine> {
    val lines = mutableListOf<RawLine>()
    var inLines = false
    var line: RawLine? = null
    var word: RawWord? = null
    // Column of the current line's `words:` key; -1 outside a word list.
    var wordsKey = -1
    for (row in yaml.lines()) {
        val trimmed = row.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val indent = row.length - trimmed.length
        val dash = trimmed == "-" || trimmed.startsWith("- ")
        if (indent == 0 && !dash) {
            inLines = trimmed.substringBefore(':').trim() == "lines"
            line = null
            word = null
            wordsKey = -1
            continue
        }
        if (!inLines) continue
        var body = trimmed
        if (dash) {
            body = trimmed.removePrefix("-").trimStart()
            // A word list may sit at its key's own column, so a dash there is still a word.
            if (wordsKey >= 0 && indent >= wordsKey) {
                word = RawWord().also { line?.words?.add(it) }
            } else {
                line = RawLine().also { lines += it }
                word = null
                wordsKey = -1
            }
            if (body.isEmpty()) continue
        } else if (wordsKey >= 0 && indent <= wordsKey) {
            // Back at the line's own keys: the word list is over.
            word = null
            wordsKey = -1
        }
        val key = body.substringBefore(':').trim()
        val value = body.substringAfter(':', "").let(::scalar)
        val keyColumn = if (dash) indent + (trimmed.length - body.length) else indent
        val target = word
        when {
            key == "words" && target == null -> wordsKey = keyColumn
            target != null -> when (key) {
                "text" -> target.text = value
                "start_ms" -> target.startMs = value.toLongOrNull()
                "end_ms" -> target.endMs = value.toLongOrNull()
            }
            line != null -> when (key) {
                "text" -> line.text = value
                "start_ms" -> line.startMs = value.toLongOrNull()
                "end_ms" -> line.endMs = value.toLongOrNull()
            }
        }
    }
    return lines.mapNotNull { it.toLyricLine() }.closePhrases()
}

private class RawWord(var text: String = "", var startMs: Long? = null, var endMs: Long? = null)

private class RawLine(
    var text: String = "",
    var startMs: Long? = null,
    var endMs: Long? = null,
    val words: MutableList<RawWord> = mutableListOf()
) {
    fun toLyricLine(): LyricLine? {
        val timed = words.filter { it.startMs != null && it.text.isNotEmpty() }
        val start = startMs ?: timed.firstOrNull()?.startMs ?: return null
        if (timed.isEmpty()) return LyricLine(start, text.trim(), endMs)
        val lyricWords = timed.mapIndexed { i, w ->
            val wordStart = w.startMs!!
            val end = w.endMs ?: timed.getOrNull(i + 1)?.startMs ?: endMs ?: wordStart
            LyricWord(wordStart, end.coerceAtLeast(wordStart), w.text)
        }.toMutableList()
        lyricWords[lyricWords.lastIndex] = lyricWords.last().let { it.copy(text = it.text.trimEnd()) }
        return LyricLine(start, lyricWords.joinToString("") { it.text }, endMs, lyricWords)
    }
}

private fun scalar(raw: String): String {
    val s = raw.trim()
    return when {
        s.length >= 2 && s.startsWith('\'') && s.endsWith('\'') -> s.substring(1, s.length - 1).replace("''", "'")
        s.length >= 2 && s.startsWith('"') && s.endsWith('"') -> s.substring(1, s.length - 1)
            .replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
        else -> s
    }
}
