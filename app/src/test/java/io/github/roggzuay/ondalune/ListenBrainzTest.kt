package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.ListenBrainzClient
import io.github.roggzuay.ondalune.data.network.PendingListen
import io.github.roggzuay.ondalune.ui.viewmodel.ListenBrainzController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrobbling to ListenBrainz: what is sent, what is kept when it cannot be sent, and what never
 * leaves the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ListenBrainzTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer
    private lateinit var prefs: android.content.SharedPreferences
    private val banners = mutableListOf<String>()

    private val track = SubsonicTrack(
        id = "s1",
        serverId = 1L,
        title = "Tangerine",
        artist = "Led Zeppelin",
        artistId = "ar1",
        album = "III",
        albumId = "al1",
        durationSec = 180,
        bitRate = 1000,
        suffix = "FLAC",
        trackNumber = 4
    )

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        prefs = app.getSharedPreferences("listenbrainz_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        banners.clear()
    }

    @After
    fun stop() = server.shutdown()

    /** The client runs its call on the test's own dispatcher, so advanceUntilIdle really means it. */
    private fun TestScope.client() = ListenBrainzClient(
        baseUrl = server.url("/").toString().trimEnd('/'),
        http = OkHttpClient(),
        io = StandardTestDispatcher(testScheduler)
    )

    private fun TestScope.controller(now: Long = 1_700_000_000_000L) =
        ListenBrainzController(prefs, client(), this, { banners += it }, now = { now })

    private fun ok(body: String = "{}") = MockResponse().setResponseCode(200).setBody(body)

    // ---- Signing in ----

    @Test
    fun `a good token is kept, and the name comes back with it`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc-123")
        advanceUntilIdle()
        assertTrue(lb.state.value.hasToken)
        assertTrue(lb.state.value.enabled)
        assertEquals("rogg", lb.state.value.userName)
        assertTrue(banners.last(), banners.last().contains("rogg"))
    }

    @Test
    fun `a typo is caught here rather than swallowing every listen from now on`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":false}"""))
        val lb = controller()
        lb.signIn("nope")
        advanceUntilIdle()
        assertFalse(lb.state.value.hasToken)
        assertTrue(banners.last(), banners.last().contains("didn't recognise"))
    }

    @Test
    fun `the token is never kept in the clear`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        controller().signIn("super-secret-token")
        advanceUntilIdle()
        val everything = prefs.all.values.joinToString(" ")
        assertFalse(everything, everything.contains("super-secret-token"))
    }

    @Test
    fun `signing out forgets the token and anything waiting with it`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc")
        advanceUntilIdle()
        lb.signOut()
        assertFalse(lb.state.value.hasToken)
        assertNull(prefs.getString("listenbrainz_token", null))
        assertNull(prefs.getString("listenbrainz_queue", null))
    }

    // ---- What is sent ----

    @Test
    fun `nothing at all is sent before a token is put in`() = runTest(StandardTestDispatcher()) {
        val lb = controller()
        lb.nowPlaying(track)
        lb.submit(track)
        advanceUntilIdle()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a finished listen carries the song and the time, and nothing else about you`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller(now = 1_700_000_000_000L)
        lb.signIn("abc")
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        server.enqueue(ok())
        lb.submit(track)
        advanceUntilIdle()

        val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("Token abc", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("single", body.getString("listen_type"))
        val listen = body.getJSONArray("payload").getJSONObject(0)
        assertEquals(1_700_000_000L, listen.getLong("listened_at"))
        val meta = listen.getJSONObject("track_metadata")
        assertEquals("Tangerine", meta.getString("track_name"))
        assertEquals("Led Zeppelin", meta.getString("artist_name"))
        assertEquals("III", meta.getString("release_name"))
        assertEquals(180_000L, meta.getJSONObject("additional_info").getLong("duration_ms"))
    }

    @Test
    fun `playing now carries no time, because it is only true until the next song`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc")
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        server.enqueue(ok())
        lb.nowPlaying(track)
        advanceUntilIdle()

        val body = JSONObject(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("playing_now", body.getString("listen_type"))
        assertFalse(body.getJSONArray("payload").getJSONObject(0).has("listened_at"))
    }

    @Test
    fun `turning it off keeps the token but stops sending`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc")
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        val before = server.requestCount
        lb.setEnabled(false)
        lb.submit(track)
        advanceUntilIdle()
        assertEquals("nothing more should have been sent", before, server.requestCount)
        assertTrue(lb.state.value.hasToken)
    }

    // ---- What happens when it cannot be sent ----

    @Test
    fun `a listen that could not be sent is kept`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc")
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        server.enqueue(MockResponse().setResponseCode(503))
        lb.submit(track)
        advanceUntilIdle()
        assertEquals(1, lb.state.value.queued)
    }

    @Test
    fun `a backlog goes with the next listen, in one request`() = runTest(StandardTestDispatcher()) {
        server.enqueue(ok("""{"valid":true,"user_name":"rogg"}"""))
        val lb = controller()
        lb.signIn("abc")
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        server.enqueue(MockResponse().setResponseCode(503))
        lb.submit(track)
        advanceUntilIdle()
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!

        server.enqueue(ok())
        lb.submit(track.copy(id = "s2", title = "Friends"))
        advanceUntilIdle()

        val body = JSONObject(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("import", body.getString("listen_type"))
        assertEquals(2, body.getJSONArray("payload").length())
        assertEquals(0, lb.state.value.queued)
    }

    // ---- The small queue that holds them ----

    @Test
    fun `a listen survives being written down and read back`() {
        val listen = PendingListen.of(track, listenedAt = 99L)
        val back = PendingListen.parse(listen.serialized())!!
        assertEquals(listen, back)
    }

    @Test
    fun `a song with a separator in its name still reads back`() {
        val odd = track.copy(title = "A|B", artist = "C\\D", album = "E|F\\G")
        val back = PendingListen.parse(PendingListen.of(odd, 1L).serialized())!!
        assertEquals("A|B", back.title)
        assertEquals("C\\D", back.artist)
        assertEquals("E|F\\G", back.album)
    }

    @Test
    fun `nonsense in the queue is dropped rather than crashing the next listen`() {
        assertNull(PendingListen.parse(""))
        assertNull(PendingListen.parse("not|a|listen"))
        assertNull(PendingListen.parse("abc|Title|Artist|Album|0|0"))
    }
}
