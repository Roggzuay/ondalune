package io.github.roggzuay.ondalune

import android.app.Activity
import androidx.mediarouter.app.MediaRouteButton
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.cast.castDialogContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ondalune's own theme is `android:Theme.DeviceDefault.NoActionBar`, which has no AppCompat
 * `colorPrimary`. Everything in androidx.mediarouter reads that attribute and runs the result
 * through `ColorUtils.calculateContrast`, which refuses a transparent colour:
 *
 *     java.lang.IllegalArgumentException: background can not be translucent: #0
 *
 * That took the app down the moment the player drew a Cast button. These tests hold the line:
 * anything handed to mediarouter goes through [castDialogContext] first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CastDialogThemeTest {

    private fun activity(): Activity =
        Robolectric.buildActivity(android.app.Activity::class.java).setup().get()

    @Test
    fun `the app's own theme is the one that broke mediarouter`() {
        // If this ever stops throwing, Ondalune's theme has changed and the wrapper can go.
        assertThrows(IllegalArgumentException::class.java) {
            MediaRouteButton(ApplicationProvider.getApplicationContext())
        }
    }

    @Test
    fun `a context from castDialogContext is one mediarouter can work against`() {
        val themed = castDialogContext(activity())
        // Constructing the button is exactly what crashed; against this context it must not.
        assertNotNull(MediaRouteButton(themed))
    }

    @Test
    fun `the themed context keeps the activity underneath it`() {
        val activity = activity()
        val themed = castDialogContext(activity)
        var unwrapped: android.content.Context = themed
        while (unwrapped is android.content.ContextWrapper && unwrapped !is Activity) {
            unwrapped = unwrapped.baseContext
        }
        // The dialogs need a real activity under there to attach their window to.
        assertNotNull(unwrapped as? Activity)
    }
}
