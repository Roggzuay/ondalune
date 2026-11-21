package io.github.roggzuay.ondalune

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherActivityTest {
    private fun handOff(launch: Intent): Intent {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java, launch).create().get()
        return shadowOf(activity).nextStartedActivity
    }

    @Test
    fun `the app screen never inherits the trampoline's hidden-from-Recents flags`() {
        val started = handOff(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
        )
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    @Test
    fun `the app screen opens in its own task, and extras carry over`() {
        val started = handOff(Intent(Intent.ACTION_MAIN).putExtra("open", "lyrics"))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals("lyrics", started.getStringExtra("open"))
    }

    @Test
    fun `an opened song file arrives with read access and its address`() {
        val song = android.net.Uri.parse("content://media/external/audio/media/42")
        val started = handOff(Intent(Intent.ACTION_VIEW).setDataAndType(song, "audio/flac"))
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(song, started.data)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `opening the app normally asks for no file access`() {
        val started = handOff(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(0, started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
