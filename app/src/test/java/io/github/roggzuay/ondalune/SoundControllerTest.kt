package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.ui.viewmodel.SoundController
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sound settings part: what is set is kept for the next launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoundControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = app.getSharedPreferences("sound_controller_test", Context.MODE_PRIVATE)
    private val engine = OndalunePlaybackRuntime.getOrCreateEngine(app, OpenSubsonicClient())
    private val banners = mutableListOf<String>()
    private fun controller() = SoundController(prefs, engine) { banners += it }

    @Test
    fun speedReverbAndEqSurviveARestart() {
        prefs.edit().clear().commit()
        controller().apply {
            setPlaybackSpeed(1.25f)
            setReverbEnabled(true)
            selectEqualizerPreset(EqualizerPreset.entries.last())
        }
        val next = controller()
        assertEquals(1.25f, next.speedPitchState.value.speed, 0.001f)
        assertTrue(next.reverbState.value.enabled)
        assertEquals(EqualizerPreset.entries.last(), next.equalizerPreset.value)
    }

    @Test
    fun resettingReverbGoesDryAndSaysSo() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.setReverbEnabled(true)
        sound.resetReverbToDry()
        assertFalse(sound.reverbState.value.isActivelyProcessing)
        assertTrue(banners.last().contains("dry"))
    }
}
