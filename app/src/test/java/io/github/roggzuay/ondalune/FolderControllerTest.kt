package io.github.roggzuay.ondalune

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.data.repository.OndaluneRepository
import io.github.roggzuay.ondalune.ui.viewmodel.FolderController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The music-folders part on its own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val banners = mutableListOf<String>()
    private val folders = FolderController(
        app, OndaluneRepository.from(app, OpenSubsonicClient()), CoroutineScope(Dispatchers.Main)
    ) { banners += it }

    @Test
    fun startsWithNoFoldersAndNoScan() {
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(emptyList<Any>(), folders.localFolders.value)
        assertNull(folders.folderScan.value)
    }

    @Test
    fun scanningNothingDoesNothing() {
        folders.scanFolders(emptyList())
        folders.rescanAllMusicFolders()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(folders.folderScan.value)
        assertEquals(emptyList<String>(), banners)
    }
}
