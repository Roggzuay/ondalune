package io.github.roggzuay.ondalune

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.github.roggzuay.ondalune.data.local.DemoSubsonicCatalog
import io.github.roggzuay.ondalune.data.motion.MotionArtworkMode
import io.github.roggzuay.ondalune.data.motion.MotionArtworkPreferences
import io.github.roggzuay.ondalune.data.motion.MotionArtworkService
import io.github.roggzuay.ondalune.data.motion.MotionArtworkSettings
import io.github.roggzuay.ondalune.data.motion.MotionArtworkWorker
import io.github.roggzuay.ondalune.data.motion.MotionFetchScope
import io.github.roggzuay.ondalune.data.source.OpenedFile
import io.github.roggzuay.ondalune.ui.components.MotionLook
import io.github.roggzuay.ondalune.ui.components.motionLook
import io.github.roggzuay.ondalune.ui.components.motionRuns
import io.github.roggzuay.ondalune.ui.screens.limitLabel
import io.github.roggzuay.ondalune.ui.viewmodel.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MotionArtworkSettingsTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("motion-test", Context.MODE_PRIVATE)

    @Test
    fun `motion artwork starts on automatic, looking up as songs play, on wifi only`() {
        val s = SettingsStore(prefs).customization.value.motionArtwork
        assertEquals(MotionArtworkMode.AUTOMATIC, s.mode)
        assertEquals(MotionFetchScope.WHEN_PLAYED, s.fetchScope)
        assertTrue(s.autoRetrieve)
        assertTrue(s.wifiOnly)
        assertEquals(250, s.cacheLimitMb)
    }

    @Test
    fun `every motion setting is saved and read back`() {
        val picked = MotionArtworkSettings(MotionArtworkMode.DISABLED, autoRetrieve = false, wifiOnly = false, fetchScope = MotionFetchScope.WHOLE_LIBRARY, cacheLimitMb = 1000)
        SettingsStore(prefs).setMotionArtwork { picked }
        assertEquals(picked, SettingsStore(prefs).customization.value.motionArtwork)
        // The background run reads the same saved values without the app open.
        assertEquals(picked, MotionArtworkPreferences.read(prefs))
    }

    @Test
    fun `a custom storage limit is kept, and kept within bounds`() {
        val store = SettingsStore(prefs)
        store.setMotionArtwork { it.copy(cacheLimitMb = 3_500) }
        assertEquals(3_500, SettingsStore(prefs).customization.value.motionArtwork.cacheLimitMb)
        prefs.edit().putInt(MotionArtworkPreferences.KEY_CACHE_MB, 5).commit()
        assertEquals(MotionArtworkSettings.CUSTOM_LIMIT_MB.first, MotionArtworkPreferences.read(prefs).cacheLimitMb)
        prefs.edit().putInt(MotionArtworkPreferences.KEY_CACHE_MB, Int.MAX_VALUE).commit()
        assertEquals(MotionArtworkSettings.CUSTOM_LIMIT_MB.last, MotionArtworkPreferences.read(prefs).cacheLimitMb)
    }

    @Test
    fun `a custom number of next songs is kept, within bounds`() {
        val store = SettingsStore(prefs)
        store.setReadAheadBuffer(25)
        assertEquals(25, SettingsStore(prefs).customization.value.readAheadTrackBuffer)
        store.setReadAheadBuffer(0)
        assertEquals(1, store.customization.value.readAheadTrackBuffer)
        store.setReadAheadBuffer(10_000)
        assertEquals(SettingsStore.SAVE_AHEAD_RANGE.last, store.customization.value.readAheadTrackBuffer)
    }

    @Test
    fun `limits read the way people say them`() {
        assertEquals("250 MB", limitLabel(250))
        assertEquals("1 GB", limitLabel(1000))
        assertEquals("1.5 GB", limitLabel(1500))
    }

    @Test
    fun `an unknown saved value falls back to the default`() {
        prefs.edit().putString(MotionArtworkPreferences.KEY_MODE, "SPARKLES").apply()
        assertEquals(MotionArtworkMode.AUTOMATIC, MotionArtworkPreferences.read(prefs).mode)
    }

    @Test
    fun `the album's own moving artwork shows, otherwise the still cover`() {
        fun look(mode: MotionArtworkMode, original: Boolean) = motionLook(mode, original, reducedMotion = false, powerSave = false)
        assertEquals(MotionLook.ORIGINAL, look(MotionArtworkMode.AUTOMATIC, original = true))
        assertEquals(MotionLook.STILL, look(MotionArtworkMode.AUTOMATIC, original = false))
        assertEquals(MotionLook.STILL, look(MotionArtworkMode.DISABLED, original = true))
    }

    @Test
    fun `the removed Original only choice is read back as on`() {
        prefs.edit().putString(MotionArtworkPreferences.KEY_MODE, "ORIGINAL_ONLY").commit()
        assertEquals(MotionArtworkMode.AUTOMATIC, MotionArtworkPreferences.read(prefs).mode)
    }

    @Test
    fun `removed animations and battery saver keep the cover still`() {
        assertEquals(MotionLook.STILL, motionLook(MotionArtworkMode.AUTOMATIC, true, reducedMotion = true, powerSave = false))
        assertEquals(MotionLook.STILL, motionLook(MotionArtworkMode.AUTOMATIC, true, reducedMotion = false, powerSave = true))
    }

    @Test
    fun `artwork moves only while the player is open and Ondalune is on screen`() {
        assertTrue(motionRuns(true, Lifecycle.State.RESUMED))
        assertFalse(motionRuns(true, Lifecycle.State.STARTED))
        assertFalse(motionRuns(false, Lifecycle.State.RESUMED))
    }

    @Test
    fun `the library run happens only when asked for`() {
        val base = MotionArtworkSettings(fetchScope = MotionFetchScope.WHOLE_LIBRARY)
        assertTrue(MotionArtworkWorker.wanted(base))
        assertFalse(MotionArtworkWorker.wanted(base.copy(fetchScope = MotionFetchScope.WHEN_PLAYED)))
        assertFalse(MotionArtworkWorker.wanted(base.copy(autoRetrieve = false)))
        assertFalse(MotionArtworkWorker.wanted(base.copy(mode = MotionArtworkMode.DISABLED)))
    }

    @Test
    fun `the library run looks at one song per album and skips opened files`() {
        val tracks = DemoSubsonicCatalog.seedTracks(1L)
        val opened = tracks.first().copy(id = OpenedFile.PREFIX + "content://x/1", albumId = "opened-album")
        val picked = MotionArtworkWorker.albumsOf(tracks + opened)
        assertEquals(tracks.map { it.albumId }.distinct(), picked.map { it.albumId })
    }

    @Test
    fun `folder names are matched literally in the database`() {
        assertEquals("local:primary:Music/100\\% Hits\\_2/", MotionArtworkService.likeEscape("local:primary:Music/100% Hits_2/"))
    }
}
