package io.github.roggzuay.ondalune

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import io.github.roggzuay.ondalune.ui.viewmodel.PrimaryDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Back from a page opened by a jump (player names, "Go to album") returns to where you were. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NavigationJumpTest {
    private val vm = OndaluneViewModel(ApplicationProvider.getApplicationContext<Application>())

    /** Waits for the seeded demo library and returns one of its album ids. */
    private fun anAlbumId(): String {
        val watcher = CoroutineScope(Dispatchers.Unconfined).launch { vm.albums.collect {} }
        try {
            repeat(400) {
                shadowOf(Looper.getMainLooper()).idle()
                vm.albums.value.firstOrNull()?.let { return it.id }
                Thread.sleep(25)
            }
            error("The demo library never loaded")
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun `back from an album opened on another tab returns to that tab`() {
        val album = anAlbumId()
        vm.selectDestination(PrimaryDestination.PLAYLISTS)
        vm.openAlbumAnywhere(album)
        assertEquals(PrimaryDestination.LIBRARY, vm.currentDestination.value)
        assertEquals(album, vm.selectedAlbumDetailId.value)

        vm.openAlbumDetail(null)
        assertEquals(PrimaryDestination.PLAYLISTS, vm.currentDestination.value)
        assertNull(vm.selectedAlbumDetailId.value)
    }

    @Test
    fun `an album missing from the library opens nothing`() {
        anAlbumId()
        vm.selectDestination(PrimaryDestination.HOME)
        vm.openAlbumAnywhere("not-in-library")
        assertEquals(PrimaryDestination.HOME, vm.currentDestination.value)
        assertNull(vm.selectedAlbumDetailId.value)
    }

    @Test
    fun `back from a playlist returns to the page that was open, not just the tab`() {
        vm.selectDestination(PrimaryDestination.HOME)
        vm.openAlbumDetail("a1")
        vm.openPlaylistAnywhere("p1")
        assertEquals(PrimaryDestination.PLAYLISTS, vm.currentDestination.value)
        assertEquals("p1", vm.selectedPlaylistDetailId.value)
        assertNull(vm.selectedAlbumDetailId.value)

        vm.openPlaylistDetail(null)
        assertEquals(PrimaryDestination.HOME, vm.currentDestination.value)
        assertEquals("a1", vm.selectedAlbumDetailId.value)
        assertNull(vm.selectedPlaylistDetailId.value)
    }

    @Test
    fun `nested jumps unwind one page at a time`() {
        val album = anAlbumId()
        vm.selectDestination(PrimaryDestination.HOME)
        vm.openPlaylistAnywhere("p1")
        vm.openAlbumAnywhere(album)
        assertEquals(PrimaryDestination.LIBRARY, vm.currentDestination.value)

        vm.openAlbumDetail(null)
        assertEquals(PrimaryDestination.PLAYLISTS, vm.currentDestination.value)
        assertEquals("p1", vm.selectedPlaylistDetailId.value)

        vm.openPlaylistDetail(null)
        assertEquals(PrimaryDestination.HOME, vm.currentDestination.value)
        assertNull(vm.selectedPlaylistDetailId.value)
    }

    @Test
    fun `choosing a tab forgets earlier jumps`() {
        val album = anAlbumId()
        vm.selectDestination(PrimaryDestination.HOME)
        vm.openAlbumAnywhere(album)
        vm.selectDestination(PrimaryDestination.LIBRARY)
        vm.openAlbumDetail(album)

        vm.openAlbumDetail(null)
        assertEquals(PrimaryDestination.LIBRARY, vm.currentDestination.value)
        assertNull(vm.selectedAlbumDetailId.value)
    }
}
