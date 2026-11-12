package io.github.roggzuay.ondalune.audio

/**
 * Where the listener is in the track, as opposed to where the decoder is. The engine's position
 * is the decoder's read head; the audio already handed to the AudioTrack but not yet out of the
 * speaker is still ahead of the listener. Lyrics follow this clock.
 *
 * The playback loop reports what it wrote and where it seeked. Reading the position changes nothing.
 */
internal class AudibleClock {
    @Volatile private var writtenFrames = 0L
    /** Track position of the AudioTrack's frame zero, so the position is this plus the frames played. */
    @Volatile private var originMs = 0L
    @Volatile private var seekTargetMs = 0L
    /** First AudioTrack frame of the audio from the last seek; what is queued before it is from the old position. */
    @Volatile private var seekFrame = 0L

    /** A new AudioTrack starts counting frames from zero. */
    fun restart() {
        writtenFrames = 0L
        originMs = 0L
        seekTargetMs = 0L
        seekFrame = 0L
    }

    /** [frames] more went to the AudioTrack, and the decoder now stands at [decoderPositionMs]. */
    fun wrote(frames: Int, decoderPositionMs: Long, sampleRate: Int) {
        val total = writtenFrames + frames
        originMs = decoderPositionMs - total * 1000L / sampleRate
        writtenFrames = total
    }

    fun seekRequested(targetMs: Long) {
        seekTargetMs = targetMs
        seekFrame = Long.MAX_VALUE
    }

    /** The decoder has moved to the requested position; the next frames written come from there. */
    fun seekApplied(sampleRate: Int) {
        originMs = seekTargetMs - writtenFrames * 1000L / sampleRate
        seekFrame = writtenFrames
    }

    /**
     * The position being heard.
     *
     * [head] is the AudioTrack's playback head: frames it has taken from its buffer, as the 32-bit
     * counter Android reports. They still have the output path to cross, so while [playing] and when
     * the track has a timestamp ([stampFrames] presented [stampAgeNanos] ago, or -1 for none) the
     * frames presented are used instead, carried forward at [speed] and never past the head.
     */
    fun positionMs(sampleRate: Int, head: Int, playing: Boolean, stampFrames: Long = -1L, stampAgeNanos: Long = 0L, speed: Float = 1f): Long {
        val written = writtenFrames
        // The head counter wraps; the distance back from what was written is what the track still holds.
        val consumed = written - ((written - head) and 0xFFFFFFFFL)
        var played = consumed
        if (playing && stampFrames >= 0L) {
            val presented = stampFrames + (stampAgeNanos * sampleRate.toDouble() * speed / 1e9).toLong()
            played = presented.coerceIn((consumed - sampleRate).coerceAtLeast(0L), consumed)
        }
        if (played < seekFrame) return seekTargetMs
        return (originMs + played * 1000L / sampleRate).coerceAtLeast(0L)
    }
}
