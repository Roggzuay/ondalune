package io.github.roggzuay.ondalune

import android.net.Uri
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.model.isOnDevice
import io.github.roggzuay.ondalune.data.source.OpenedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenedFileTest {
    private val uri = "content://com.android.providers.downloads.documents/document/msf%3A42"

    @Test
    fun `titles come from the file name without its extension`() {
        assertEquals("Night Drive", OpenedFile.titleFrom("Night Drive.flac", uri))
        assertEquals("a.b.c", OpenedFile.titleFrom("a.b.c.mp3", uri))
        assertEquals(".hidden", OpenedFile.titleFrom(".hidden", uri))
    }

    @Test
    fun `without a file name the address's last part is the title`() {
        assertEquals("Song One", OpenedFile.titleFrom(null, "file:///sdcard/Music/Song%20One.ogg"))
        assertEquals("msf:42", OpenedFile.titleFrom("", uri))
    }

    @Test
    fun `an unreadable file still becomes a playable song that keeps its address`() {
        val track = OpenedFile.track(RuntimeEnvironment.getApplication(), Uri.parse(uri), serverId = 7)
        assertEquals(uri, OpenedFile.uriOf(track))
        assertEquals(7L, track.serverId)
        assertEquals("Unknown artist", track.artist)
        assertTrue("an opened file counts as on this phone", track.isOnDevice)
    }

    @Test
    fun `library songs are never mistaken for opened files`() {
        val libraryTrack = DemoSubsonicCatalog.seedTracks(1L).first()
        assertNull(OpenedFile.uriOf(libraryTrack))
        assertNull(OpenedFile.uriOf(libraryTrack.copy(id = "local:primary:Music/x.flac")))
    }
}
