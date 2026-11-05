package io.github.roggzuay.ondalune

import io.github.roggzuay.ondalune.data.model.SourceType
import io.github.roggzuay.ondalune.data.model.SubsonicServerConfig
import io.github.roggzuay.ondalune.data.network.JellyfinClient
import io.github.roggzuay.ondalune.data.network.jellyfinTrackFrom
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JellyfinClientTest {
    private val server = SubsonicServerConfig(
        id = 3, name = "Jelly", baseUrl = "https://jf.example/", username = "me", passwordOrToken = "tok123",
        sourceType = SourceType.JELLYFIN.name, remoteUserId = "u1"
    )
    private val client = JellyfinClient(deviceId = "dev", appVersion = "1.5.0")

    private val item = JSONObject(
        """
        {"Id":"a1","Name":"Teardrop","Album":"Mezzanine","AlbumId":"al9","AlbumPrimaryImageTag":"tagA",
         "Artists":["Massive Attack","Elizabeth Fraser"],"ArtistItems":[{"Name":"Massive Attack","Id":"ar5"}],
         "RunTimeTicks":3300000000,"IndexNumber":3,"ProductionYear":1998,"Genres":["Trip-Hop","Electronic"],
         "Container":"flac","Path":"/music/Massive Attack/Mezzanine/03 Teardrop.flac","NormalizationGain":-6.5,
         "UserData":{"IsFavorite":true,"PlayCount":12},
         "MediaSources":[{"Bitrate":1011000,"MediaStreams":[{"Type":"Audio","SampleRate":44100,"BitDepth":16}]}]}
        """
    )

    @Test
    fun `a Jellyfin song becomes a library track`() {
        val t = jellyfinTrackFrom(item, server)
        assertEquals("a1", t.id)
        assertEquals(3L, t.serverId)
        assertEquals("Teardrop", t.title)
        assertEquals("Massive Attack, Elizabeth Fraser", t.artist)
        assertEquals("ar5", t.artistId)
        assertEquals("al9", t.albumId)
        assertEquals(330, t.durationSec)
        assertEquals(1011, t.bitRate)
        assertEquals("FLAC", t.suffix)
        assertEquals(44100, t.samplingRate)
        assertEquals(16, t.bitDepth)
        assertEquals(3, t.trackNumber)
        assertEquals("Trip-Hop; Electronic", t.genre)
        assertEquals("/music/Massive Attack/Mezzanine", t.folderPath)
        assertTrue(t.starred)
        assertEquals(12, t.playCount)
        assertEquals(-6.5f, t.rgTrackGain)
        assertEquals("https://jf.example/Items/al9/Images/Primary?maxHeight=600&quality=90&tag=tagA", t.coverArtUrl)
    }

    @Test
    fun `missing details fall back without inventing any`() {
        val t = jellyfinTrackFrom(JSONObject("""{"Id":"b2","Name":"","RunTimeTicks":0}"""), server)
        assertEquals("Untitled", t.title)
        assertEquals("Unknown artist", t.artist)
        assertEquals(0, t.samplingRate)
        assertEquals(0, t.userRating)
        assertEquals("", t.coverArtUrl)
    }

    @Test
    fun `original quality streams the file as it is, other qualities convert`() {
        val raw = client.streamUrl(server.copy(transcodeFormat = "RAW"), "a1")!!.toHttpUrl()
        assertEquals("/Audio/a1/stream", raw.encodedPath)
        assertEquals("true", raw.queryParameter("static"))
        assertEquals("tok123", raw.queryParameter("api_key"))

        val opus = client.streamUrl(server.copy(transcodeFormat = "OPUS", maxBitRate = 192), "a1")!!.toHttpUrl()
        assertEquals("/Audio/a1/universal", opus.encodedPath)
        assertEquals("opus", opus.queryParameter("AudioCodec"))
        assertEquals("192000", opus.queryParameter("MaxStreamingBitrate"))
        assertEquals("u1", opus.queryParameter("UserId"))

        assertEquals("/Items/a1/Download", client.downloadUrl(server, "a1")!!.toHttpUrl().encodedPath)
    }
}
