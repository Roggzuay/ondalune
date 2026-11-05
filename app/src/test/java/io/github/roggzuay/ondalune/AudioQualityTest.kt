package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.OpenedStream
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.audioQualityOf
import io.github.roggzuay.ondalune.data.model.compactQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityTest {
    private fun song(suffix: String, depth: Int = 0, rate: Int = 0, kbps: Int = 0) = SubsonicTrack(
        id = "s", serverId = 1, title = "T", artist = "A", artistId = "a", album = "B", albumId = "b",
        durationSec = 200, bitRate = kbps, suffix = suffix, bitDepth = depth, samplingRate = rate
    )
    private fun opened(mime: String, rate: Int = 44100, channels: Int = 2, kbps: Int = 0) =
        OpenedStream(mime, rate, channels, kbps * 1000, 0, ownPipeline = true, outputSampleRate = rate)

    @Test
    fun `a FLAC file opened as raw PCM is still the FLAC file, not a conversion`() {
        val q = audioQualityOf(song("FLAC", 16, 44100), opened("audio/raw"), null, emptyList(), null)!!
        assertEquals("FLAC · 16-bit / 44.1 kHz", q.label)
        assertEquals("Lossless", q.tag)
        assertTrue(q.stream.contains("Codec" to "FLAC"))
    }

    @Test
    fun `untouched 16-bit FLAC is lossless`() {
        val q = audioQualityOf(song("FLAC", 16, 44100, 900), opened("audio/flac"), null, emptyList(), "Phone speaker")!!
        assertEquals("FLAC · 16-bit / 44.1 kHz", q.label)
        assertEquals("Lossless", q.tag)
        assertTrue(q.output.contains("Device" to "Phone speaker"))
    }

    @Test
    fun `a 24-bit source played as 16-bit, or processed, is a lossless source, not lossless output`() {
        val hiRes = audioQualityOf(song("FLAC", 24, 96000), opened("audio/flac", 96000), null, emptyList(), null)!!
        assertEquals("FLAC · 24-bit / 96 kHz", hiRes.label)
        assertEquals("Lossless source", hiRes.tag)
        assertTrue(hiRes.output.any { it.second == "This 24-bit song plays as 16-bit" })

        val processed = audioQualityOf(song("FLAC", 16, 44100), opened("audio/flac"), null, listOf("Reverb: Mastering Studio"), null)!!
        assertEquals("Lossless source", processed.tag)
    }

    @Test
    fun `a converted stream shows what actually plays`() {
        val q = audioQualityOf(song("FLAC", 24, 96000), opened("audio/opus", 48000, kbps = 192), "OPUS, up to 192 kbps", emptyList(), null)!!
        assertEquals("Opus · 192 kbps", q.label)
        assertEquals("Converted", q.tag)
        // Even without the server setting, the decoder gives a conversion away.
        assertEquals("Converted", audioQualityOf(song("MP3", kbps = 320), opened("audio/opus"), null, emptyList(), null)!!.tag)
    }

    @Test
    fun `lossy files show their bitrate and no lossless claim`() {
        val q = audioQualityOf(song("MP3", kbps = 320), opened("audio/mpeg", kbps = 320), null, emptyList(), null)!!
        assertEquals("MP3 · 320 kbps", q.label)
        assertNull(q.tag)
    }

    @Test
    fun `nothing is made up when nothing is known`() {
        assertNull(audioQualityOf(song(""), null, null, emptyList(), null))
        assertEquals("MP3", audioQualityOf(song("MP3"), null, null, emptyList(), null)!!.label)
    }

    @Test
    fun `surround audio is said to play as stereo`() {
        val q = audioQualityOf(song("M4A"), opened("audio/eac3", 48000, channels = 6), null, emptyList(), null)!!
        assertTrue(q.output.any { it.second == "5.1 audio plays as stereo" })
        assertTrue("never claims Atmos", q.label.contains("Atmos").not())
    }

    @Test
    fun `row badges never show a missing number`() {
        assertEquals("MP3 320k", song("MP3", rate = 44100, kbps = 320).compactQuality())
        assertEquals("FLAC 24/96k", song("FLAC", 24, 96000).compactQuality())
        assertEquals("OGG", song("OGG").compactQuality())
    }
}
