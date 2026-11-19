package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.ui.viewmodel.SettingsStore
import io.github.roggzuay.ondalune.ui.viewmodel.UpdateController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The update part on its own, outside the ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = app.getSharedPreferences("update_controller_test", Context.MODE_PRIVATE)
    private fun controller() = UpdateController(app, prefs, SettingsStore(prefs), CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun freshInstallHasNoWhatsNewAndRemembersTheVersion() {
        prefs.edit().clear().commit()
        assertNull(controller().whatsNew.value)
        assertNotNull(prefs.getString("whats_new_seen", null))
    }

    @Test
    fun dismissingWhatsNewIsRemembered() {
        val updates = controller()
        updates.dismissWhatsNew()
        assertNull(updates.whatsNew.value)
        assertNotNull(prefs.getString("whats_new_seen", null))
    }

    @Test
    fun detailsStayClosedWithoutAnUpdate() {
        val updates = controller()
        updates.clearAvailableUpdate()
        updates.showUpdateDetails()
        assertFalse(updates.updateDetailsOpen.value)
    }
}
