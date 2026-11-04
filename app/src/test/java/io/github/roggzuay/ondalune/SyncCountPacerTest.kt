package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.ui.viewmodel.SyncCountPacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCountPacerTest {
    /** Pages of 500 songs every second, sampled every 32 ms like the banner. */
    private fun foundAt(ms: Long) = (ms / 1000L * 500L).toInt().coerceAtMost(2000)

    @Test
    fun `climbs steadily between pages instead of leaping and stopping`() {
        val pacer = SyncCountPacer(0L)
        var previous = 0
        var stalls = 0
        var biggestStep = 0
        var t = 0L
        while (t <= 4000L) {
            val shown = pacer.next(foundAt(t), t)
            assertTrue("never ahead of the songs received", shown <= foundAt(t))
            if (t > 1100L) {
                if (shown == previous) stalls++
                biggestStep = maxOf(biggestStep, shown - previous)
            }
            previous = shown
            t += 32L
        }
        // A page leaps 500; the pacer moves ~16 per 32 ms frame and keeps moving.
        assertTrue("stalled $stalls frames", stalls <= 2)
        assertTrue("jumped $biggestStep at once", biggestStep <= 20)
    }

    @Test
    fun `finishes the count soon after the sync ends`() {
        val pacer = SyncCountPacer(0L)
        var t = 0L
        while (t < 1500L) { pacer.next(foundAt(t), t); t += 32L }
        pacer.finish(800, t)
        var shown = 0
        val end = t + 640L
        while (t <= end) { shown = pacer.next(800, t); t += 32L }
        assertEquals(800, shown)
    }
}
