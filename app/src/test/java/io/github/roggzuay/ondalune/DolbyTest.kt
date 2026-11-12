package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.PlayPath
import io.github.roggzuay.ondalune.audio.playPathFor
import io.github.roggzuay.ondalune.data.model.OpenedStream
import io.github.roggzuay.ondalune.data.model.PlayerCustomizationSettings
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.audioQualityOf
import io.github.roggzuay.ondalune.data.model.dolbyCodecOfContentType
import io.github.roggzuay.ondalune.data.model.dolbyMime
import io.github.roggzuay.ondalune.data.model.mayHoldDolby
import io.github.roggzuay.ondalune.data.model.streamQualityFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyTest {
    private fun song(suffix: String, codec: String = "", channels: Int = 0, kbps: Int = 0) = SubsonicTrack(
        id = "s", serverId = 1, title = "T", artist = "A", artistId = "a", album = "B", albumId = "b",
        durationSec = 200, bitRate = kbps, suffix = suffix, channelCount = channels, codec = codec
    )
    private fun decoded(mime: String, channels: Int = 6, kbps: Int = 640) =
        OpenedStream(mime, 48000, channels, kbps * 1000, 0, ownPipeline = true, outputSampleRate = 48000)
    private fun dolbyEngine(mime: String, channels: Int = 6) =
        OpenedStream(mime, 48000, channels, 768_000, 0, ownPipeline = false, outputSampleRate = 48000, dolbyEngine = true)

    @Test
    fun `only Dolby goes another way, and only when the output and the switch allow it`() {
        assertEquals(PlayPath.DECODE, playPathFor("audio/flac", passthroughOn = true, outputTakesIt = true, phoneDecodes = true))
        assertEquals(PlayPath.DOLBY_ENGINE, playPathFor("audio/eac3-joc", passthroughOn = true, outputTakesIt = true, phoneDecodes = false))
        assertEquals(PlayPath.DECODE, playPathFor("audio/eac3", passthroughOn = false, outputTakesIt = true, phoneDecodes = true))
        assertEquals(PlayPath.DECODE, playPathFor("audio/ac3", passthroughOn = true, outputTakesIt = false, phoneDecodes = true))
        assertEquals(PlayPath.NO_DECODER, playPathFor("audio/eac3", passthroughOn = true, outputTakesIt = false, phoneDecodes = false))
        assertEquals(PlayPath.NO_DECODER, playPathFor("audio/ac4", passthroughOn = false, outputTakesIt = true, phoneDecodes = false))
    }

    @Test
    fun `the server's codec, content type or extension tells a Dolby song`() {
        assertEquals("audio/eac3-joc", song("M4A", codec = "eac3-joc").dolbyMime())
        assertEquals("audio/eac3", song("MKA", codec = "eac3").dolbyMime())
        assertEquals("audio/ac3", song("AC3").dolbyMime())
        assertNull(song("M4A").dolbyMime())
        assertNull(song("FLAC", codec = "flac", channels = 6).dolbyMime())
        assertEquals("eac3", dolbyCodecOfContentType("audio/eac3"))
        assertEquals("ac3", dolbyCodecOfContentType("audio/x-ac3; charset=binary"))
        assertEquals("", dolbyCodecOfContentType("audio/mp4"))
    }

    @Test
    fun `Dolby songs stream as the original file only while the Dolby engine is on`() {
        val settings = PlayerCustomizationSettings(mobileTranscodeFormat = "OPUS", mobileMaxBitRate = 128, dolbyPassthrough = true)
        assertEquals("RAW", settings.streamQualityFor(song("M4A", codec = "eac3"), mobile = true).format)
        // A multichannel MP4 may hold Dolby when the server doesn't say; a known AAC one doesn't.
        assertTrue(song("M4A", channels = 6).mayHoldDolby())
        assertFalse(song("M4A", codec = "aac", channels = 6).mayHoldDolby())
        assertEquals("OPUS", settings.streamQualityFor(song("M4A", codec = "aac", channels = 6), mobile = true).format)
        assertEquals("OPUS", settings.streamQualityFor(song("FLAC", channels = 6), mobile = true).format)
        assertEquals("OPUS", settings.copy(dolbyPassthrough = false).streamQualityFor(song("M4A", codec = "eac3"), mobile = true).format)
    }

    @Test
    fun `an untouched Dolby file is named by its stream and is not a conversion`() {
        val q = audioQualityOf(song("M4A", kbps = 640), decoded("audio/eac3"), null, emptyList(), null)!!
        assertEquals("Dolby Digital Plus · 5.1 · 640 kbps", q.label)
        assertNull(q.tag)
        assertTrue(q.stream.contains("How it plays" to "Decoded by this phone's Dolby decoder, mixed down to stereo"))
        assertEquals("Dolby Digital", audioQualityOf(song("MKA"), decoded("audio/ac3"), null, emptyList(), null)!!.label.substringBefore(" ·"))
        assertEquals("Dolby AC-4", audioQualityOf(song("MP4"), decoded("audio/ac4"), null, emptyList(), null)!!.label.substringBefore(" ·"))
    }

    @Test
    fun `the phone's Dolby engine path says Ondalune's processing is off`() {
        val q = audioQualityOf(song("M4A"), dolbyEngine("audio/eac3-joc"), null, listOf("EQ: Warm"), null)!!
        assertTrue(q.label.startsWith("Dolby Digital Plus with Atmos · 5.1"))
        assertTrue(q.stream.contains("How it plays" to "Played by this phone's Dolby engine: EQ and effects don't apply"))
        assertTrue(q.output.contains("Path" to "This phone's Dolby engine"))
        assertTrue(q.processing.none { it.startsWith("EQ") })
    }

    @Test
    fun `a Dolby song this phone can't decode says so before it opens`() {
        val none = audioQualityOf(song("EC3", codec = "eac3"), null, null, emptyList(), null, phoneDecodes = emptySet())!!
        assertEquals(listOf("Playing" to "This phone has no Dolby Digital Plus decoder"), none.stream)
        val some = audioQualityOf(song("EC3", codec = "eac3"), null, null, emptyList(), null, phoneDecodes = setOf("audio/eac3"))!!
        assertEquals(listOf("Playing" to "Not opened yet"), some.stream)
        val unknown = audioQualityOf(song("EC3", codec = "eac3"), null, null, emptyList(), null)!!
        assertEquals(listOf("Playing" to "Not opened yet"), unknown.stream)
    }

    @Test
    fun `a 5_1 FLAC says it is mixed down, without any Dolby wording`() {
        val q = audioQualityOf(song("FLAC", channels = 6), decoded("audio/raw", kbps = 0), null, emptyList(), null)!!
        assertTrue(q.source.contains("Channels" to "5.1"))
        assertTrue(q.output.any { it.second.startsWith("5.1 audio is mixed down to stereo") })
        assertTrue(q.stream.none { it.first == "How it plays" })
        assertFalse(q.label.contains("Dolby"))
    }
}
