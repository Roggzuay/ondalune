package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.AudibleClock
import org.junit.Assert.assertEquals
import org.junit.Test

class AudibleClockTest {
    private val rate = 44_100
    private val second = 1_000_000_000L

    /** A clock two seconds into a track, with one second of that still queued in the AudioTrack. */
    private fun twoSecondsIn() = AudibleClock().apply { wrote(2 * rate, decoderPositionMs = 2_000, sampleRate = rate) }

    @Test
    fun `the position heard trails the decoder by what is still queued`() {
        assertEquals(1_000, twoSecondsIn().positionMs(rate, head = rate, playing = true))
        assertEquals("nothing queued, nothing to trail", 2_000, twoSecondsIn().positionMs(rate, head = 2 * rate, playing = true))
    }

    @Test
    fun `a timestamp moves it back by the output delay and forward by the time since`() {
        val clock = twoSecondsIn()
        // Half a second presented a tenth of a second ago; the head is a full second in.
        assertEquals(600, clock.positionMs(rate, head = rate, playing = true, stampFrames = rate / 2L, stampAgeNanos = second / 10))
    }

    @Test
    fun `slowed playback covers less of the track in the same time`() {
        val clock = twoSecondsIn()
        assertEquals(580, clock.positionMs(rate, head = rate, playing = true, stampFrames = rate / 2L, stampAgeNanos = second / 10, speed = 0.8f))
    }

    @Test
    fun `a paused track stands still however old its timestamp is`() {
        val clock = twoSecondsIn()
        val whilePaused = listOf(0L, second, 60 * second).map {
            clock.positionMs(rate, head = rate, playing = false, stampFrames = rate / 2L, stampAgeNanos = it)
        }
        assertEquals(listOf(1_000L, 1_000L, 1_000L), whilePaused)
    }

    @Test
    fun `a stale timestamp never runs past the playback head`() {
        // Buffering, or the first moments after resuming: the old timestamp says far more than was played.
        assertEquals(1_000, twoSecondsIn().positionMs(rate, head = rate, playing = true, stampFrames = rate / 2L, stampAgeNanos = 30 * second))
    }

    @Test
    fun `a seek shows its target while the old audio drains, then moves on from it`() {
        val clock = twoSecondsIn()
        clock.seekRequested(60_000)
        assertEquals("at once, before the decoder has moved", 60_000, clock.positionMs(rate, head = rate, playing = true))
        clock.seekApplied(rate)
        clock.wrote(rate, decoderPositionMs = 61_000, sampleRate = rate)
        assertEquals("old audio still playing", 60_000, clock.positionMs(rate, head = rate + rate / 2, playing = true))
        assertEquals("the audio from the target starts", 60_000, clock.positionMs(rate, head = 2 * rate, playing = true))
        assertEquals(60_500, clock.positionMs(rate, head = 2 * rate + rate / 2, playing = true))
    }

    @Test
    fun `a seek while paused holds the target`() {
        val clock = twoSecondsIn()
        clock.seekRequested(10_000)
        clock.seekApplied(rate)
        assertEquals(10_000, clock.positionMs(rate, head = rate, playing = false))
        assertEquals("even with nothing left queued", 10_000, clock.positionMs(rate, head = 2 * rate, playing = false))
    }

    @Test
    fun `a new track starts from zero`() {
        val clock = twoSecondsIn()
        clock.seekRequested(60_000)
        clock.restart()
        assertEquals(0, clock.positionMs(rate, head = 0, playing = true))
        clock.wrote(rate, decoderPositionMs = 1_000, sampleRate = rate)
        assertEquals(500, clock.positionMs(rate, head = rate / 2, playing = true))
    }

    @Test
    fun `the next song in a gapless run counts from its own start`() {
        val clock = twoSecondsIn()
        // The decoder is half a second into the next song; the last second written is still queued.
        clock.wrote(rate, decoderPositionMs = 500, sampleRate = rate)
        assertEquals("the old song's tail is still playing", 0, clock.positionMs(rate, head = 2 * rate, playing = true))
        assertEquals(250, clock.positionMs(rate, head = 3 * rate - rate / 4, playing = true))
    }

    @Test
    fun `the playback head wrapping after many hours changes nothing`() {
        val clock = AudibleClock()
        val frames = (1L shl 32) + rate          // a little over 27 hours of audio
        repeat(4) { clock.wrote((frames / 4).toInt(), decoderPositionMs = 0, sampleRate = rate) }
        clock.wrote((frames % 4).toInt() + rate, decoderPositionMs = 100_000, sampleRate = rate)
        // The head is one second behind what was written, and has wrapped past zero.
        val head = (frames and 0xFFFFFFFFL).toInt()
        assertEquals(99_000, clock.positionMs(rate, head = head, playing = true))
    }
}
