package io.github.roggzuay.ondalune.ui.viewmodel

/**
 * Paces the song counter shown while a library syncs. The server answers in pages, so the real
 * count leaps by hundreds and then waits for the next page; eased toward each new total, the
 * number ran up and stopped, page after page. This climbs steadily at the rate songs have been
 * arriving, so it reaches each total about when the next page lands, and only hurries once the
 * sync has its final count.
 */
class SyncCountPacer(private val startMs: Long) {
    private var shown = 0.0
    private var lastMs = startMs
    private var finishPerMs = -1.0

    /** The sync is done at [found] songs: count out the rest within [withinMs]. */
    fun finish(found: Int, nowMs: Long, withinMs: Long = 600) {
        finishPerMs = maxOf(rate(found, nowMs), (found - shown) / withinMs)
    }

    /** The number to show at [nowMs], given [found] songs received so far. */
    fun next(found: Int, nowMs: Long): Int {
        val dt = (nowMs - lastMs).coerceAtLeast(0)
        lastMs = nowMs
        val perMs = if (finishPerMs >= 0) finishPerMs else rate(found, nowMs)
        shown = minOf(found.toDouble(), shown + perMs * dt)
        return shown.toInt()
    }

    private fun rate(found: Int, nowMs: Long) = found.toDouble() / (nowMs - startMs).coerceAtLeast(1)
}
