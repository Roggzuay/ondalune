package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.PcmSource
import io.github.roggzuay.ondalune.audio.QueuedSource
import io.github.roggzuay.ondalune.audio.TransitionMixer
import io.github.roggzuay.ondalune.audio.replayGainLinear
import io.github.roggzuay.ondalune.data.model.ReplayGainMode
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionMixerTest {

    /** A song of [frames] frames at 1 kHz whose every sample is [value], so 1 frame = 1 ms. */
    private class ConstantSource(private val frames: Long, private val value: Short) : PcmSource {
        override val sampleRate = 1000
        override val durationFrames = frames
        override var positionFrames = 0L
        var released = false
        override fun read(dst: ShortArray, offset: Int, frames: Int): Int {
            val n = minOf(frames.toLong(), durationFrames - positionFrames).toInt().coerceAtLeast(0)
            for (i in 0 until n * 2) dst[offset * 2 + i] = value
            positionFrames += n
            return n
        }
        override fun seekTo(ms: Long) { positionFrames = ms.coerceIn(0L, durationFrames) }
        override fun release() { released = true }
    }

    private fun track(id: String, album: String = "a", number: Int = 1) = SubsonicTrack(
        id = id, serverId = 1, title = id, artist = "x", artistId = "x", album = album, albumId = album,
        durationSec = 1, bitRate = 0, suffix = "WAV", trackNumber = number
    )

    private fun queued(id: String, frames: Long, value: Short) =
        QueuedSource(track(id), ConstantSource(frames, value), frames)

    /** Renders until the mixer ends, returning the left-channel samples and how often it advanced. */
    private fun renderAll(mixer: TransitionMixer): Pair<List<Short>, Int> {
        val out = ArrayList<Short>()
        val buf = ShortArray(256 * 2)
        val result = TransitionMixer.Result()
        var advances = 0
        repeat(10_000) {
            mixer.render(buf, 256, result)
            for (i in 0 until result.frames) out += buf[i * 2]
            if (result.advanced) advances++
            if (result.ended) return out to advances
        }
        error("mixer never ended")
    }

    @Test
    fun gaplessHandsOverOnTheNextSample() {
        val mixer = TransitionMixer(queued("one", 3000, 1000)).apply {
            next = queued("two", 2000, 2000)
            gapless = true
        }
        val (samples, advances) = renderAll(mixer)
        assertEquals(5000, samples.size)
        assertEquals(1, advances)
        assertTrue(samples.take(3000).all { it == 1000.toShort() })
        assertTrue(samples.drop(3000).all { it == 2000.toShort() })
        assertEquals("two", mixer.current.track.id)
    }

    @Test
    fun crossfadeOverlapsTheSongsWithEqualPower() {
        val mixer = TransitionMixer(queued("one", 3000, 1000)).apply {
            next = queued("two", 2000, 2000)
            crossfadeFrames = 1000
        }
        val (samples, advances) = renderAll(mixer)
        // The 1000-frame overlap shortens the total by exactly that much.
        assertEquals(4000, samples.size)
        assertEquals(1, advances)
        assertEquals(1000, samples[1999].toInt())
        // Halfway through the fade: 1000·cos(π/4) + 2000·sin(π/4) ≈ 2121.
        assertEquals(2121.0, samples[2500].toDouble(), 3.0)
        assertEquals(2000, samples.last().toInt())
    }

    @Test
    fun withoutGaplessOrCrossfadeTheSongJustEnds() {
        val mixer = TransitionMixer(queued("one", 1500, 1000)).apply {
            next = queued("two", 2000, 2000)
            gapless = false
        }
        val (samples, advances) = renderAll(mixer)
        assertEquals(1500, samples.size)
        assertEquals(0, advances)
        assertEquals("one", mixer.current.track.id)
    }

    @Test
    fun seekingBackCancelsAFadeAndRewindsTheNextSong() {
        val nextSource = ConstantSource(2000, 2000)
        val mixer = TransitionMixer(queued("one", 3000, 1000)).apply {
            next = QueuedSource(track("two"), nextSource, 2000)
            crossfadeFrames = 1000
        }
        val buf = ShortArray(256 * 2)
        val result = TransitionMixer.Result()
        while (!mixer.isFading || nextSource.positionFrames == 0L) mixer.render(buf, 256, result)
        mixer.current.source.seekTo(0)
        mixer.cancelFade()
        assertFalse(mixer.isFading)
        assertEquals(0L, nextSource.positionFrames)
    }

    @Test
    fun gainIsAppliedPerSong() {
        val mixer = TransitionMixer(queued("one", 500, 1000).apply { gain = 0.5f })
        val (samples, _) = renderAll(mixer)
        assertTrue(samples.all { it == 500.toShort() })
    }

    @Test
    fun replayGainFollowsTheModeAndNeverClips() {
        val t = track("t").copy(rgTrackGain = -6f, rgAlbumGain = 3f, rgTrackPeak = 0.9f, rgAlbumPeak = 0.95f)
        assertEquals(1f, replayGainLinear(t, ReplayGainMode.OFF), 0f)
        assertEquals(0.501f, replayGainLinear(t, ReplayGainMode.TRACK), 0.002f)
        // +3 dB would be ×1.41, but the album peak of 0.95 caps it at ×1.05.
        assertEquals(1f / 0.95f, replayGainLinear(t, ReplayGainMode.ALBUM), 0.001f)
        // No ReplayGain data: unchanged.
        assertEquals(1f, replayGainLinear(track("plain"), ReplayGainMode.ALBUM), 0f)
    }
}
