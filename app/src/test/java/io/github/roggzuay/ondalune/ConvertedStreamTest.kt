package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.audio.convertingServers
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertedStreamTest {
    private val server = SubsonicServerConfig(id = 3, name = "Home", baseUrl = "https://music.example.com", username = "me", passwordOrToken = "x")

    @Test
    fun `a song the device can't decode is tried as AAC first, then MP3`() {
        assertEquals(listOf("AAC", "MP3"), convertingServers(server).map { it.transcodeFormat })
    }

    @Test
    fun `never Opus, which servers fail to make from 5_1 Dolby`() {
        assertFalse(convertingServers(server).any { it.transcodeFormat == "OPUS" })
    }

    @Test
    fun `conversion keeps the same server and asks for a real bitrate`() {
        convertingServers(server).forEach {
            assertEquals(server.id, it.id)
            assertEquals(server.baseUrl, it.baseUrl)
            assertEquals(320, it.maxBitRate)
        }
    }

    @Test
    fun `the stream address asks the server to convert`() {
        val client = OpenSubsonicClient()
        convertingServers(server).forEach { converting ->
            val url = client.buildStreamUrl(converting, "bfhKQ")
            assertTrue(url, url.contains("format=${converting.transcodeFormat.lowercase()}"))
            assertTrue(url, url.contains("maxBitRate=320"))
        }
    }
}
