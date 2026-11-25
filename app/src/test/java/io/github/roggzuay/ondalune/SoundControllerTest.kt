package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import io.github.roggzuay.ondalune.data.network.OpenSubsonicClient
import io.github.roggzuay.ondalune.ui.viewmodel.SoundController
import io.github.roggzuay.ondalune.widget.OndalunePlaybackRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ---- Sound profiles: offering to save what you did to a song ----

    @Test
    fun `nothing is offered until the sound is actually changed`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        assertNull(sound.pendingSave.value)
    }

    @Test
    fun `slowing a song down offers to save it`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        sound.setPlaybackSpeed(0.80f)
        assertNotNull(sound.pendingSave.value)
        assertEquals(0.80f, sound.pendingSave.value!!.speedPitch.speed, 0.001f)
    }

    @Test
    fun `putting the speed back takes the offer away again`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        sound.setPlaybackSpeed(0.80f)
        sound.setPlaybackSpeed(1.00f)
        assertNull(sound.pendingSave.value)
    }

    @Test
    fun `not now holds until the next song starts`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        sound.setPlaybackSpeed(0.80f)
        sound.dismissPendingSave()
        assertNull(sound.pendingSave.value)

        sound.setReverbEnabled(true)
        assertNull("dismissed for this song", sound.pendingSave.value)

        sound.onPlaybackStarted()
        sound.setPlaybackSpeed(1.50f)
        assertNotNull(sound.pendingSave.value)
    }

    @Test
    fun `a song inherits the sound without being asked to save it again`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        sound.setPlaybackSpeed(0.80f)
        sound.onPlaybackStarted()
        assertNull(sound.pendingSave.value)
        assertEquals(0.80f, sound.speedPitchState.value.speed, 0.001f)
    }

    @Test
    fun `applying a saved sound sets everything at once and offers nothing`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.onPlaybackStarted()
        val saved = SoundSnapshot(
            speedPitch = SpeedPitchState(speed = 0.85f, pitchMode = PitchCorrectionMode.VARISPEED_NATURAL_LINKED),
            reverb = ReverbState.fromPreset(ReverbPreset.AMBIENT).copy(enabled = true).sanitized(),
            eqPreset = EqualizerPreset.WARM_ANALOG,
            eqBandsDb = EqualizerPreset.WARM_ANALOG.bandsDb
        )
        sound.applySnapshot(saved)
        assertEquals(0.85f, sound.speedPitchState.value.speed, 0.001f)
        assertEquals(PitchCorrectionMode.VARISPEED_NATURAL_LINKED, sound.speedPitchState.value.pitchMode)
        assertEquals(ReverbPreset.AMBIENT, sound.reverbState.value.preset)
        assertEquals(EqualizerPreset.WARM_ANALOG, sound.equalizerPreset.value)
        assertNull(sound.pendingSave.value)
    }

    @Test
    fun `a saved sound survives a restart because it is the live sound`() {
        prefs.edit().clear().commit()
        controller().applySnapshot(
            SoundSnapshot(speedPitch = SpeedPitchState(speed = 0.90f), eqPreset = EqualizerPreset.VOCAL_PRISM)
        )
        val next = controller()
        assertEquals(0.90f, next.speedPitchState.value.speed, 0.001f)
        assertEquals(EqualizerPreset.VOCAL_PRISM, next.equalizerPreset.value)
    }

    // ---- Your own equalizer curves ----

    @Test
    fun `a curve you shape and name comes back after a restart`() {
        prefs.edit().clear().commit()
        controller().apply {
            updateCustomEqBand(0, 4.5f)
            updateCustomEqBand(4, -3f)
            saveEqCurve("Late night")
        }
        val next = controller()
        val curve = next.savedEqCurves.value.single()
        assertEquals("Late night", curve.name)
        assertEquals(4.5f, curve.bandsDb[0], 0.001f)
        assertEquals(-3f, curve.bandsDb[4], 0.001f)
    }

    @Test
    fun `applying a curve sets the bands`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.updateCustomEqBand(2, 6f)
        sound.saveEqCurve("Vocals up")
        sound.selectEqualizerPreset(EqualizerPreset.FLAT_STUDIO)
        assertEquals(0f, sound.customEqBands.value[2], 0.001f)
        sound.applyEqCurve(sound.savedEqCurves.value.single())
        assertEquals(6f, sound.customEqBands.value[2], 0.001f)
    }

    @Test
    fun `saving the same name twice replaces it instead of piling up`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.updateCustomEqBand(0, 2f)
        sound.saveEqCurve("Mine")
        sound.updateCustomEqBand(0, 7f)
        sound.saveEqCurve("Mine")
        assertEquals(1, sound.savedEqCurves.value.size)
        assertEquals(7f, sound.savedEqCurves.value.single().bandsDb[0], 0.001f)
    }

    @Test
    fun `a curve named with the separators still reads back`() {
        prefs.edit().clear().commit()
        controller().apply {
            updateCustomEqBand(1, 3f)
            saveEqCurve("Bass|heavy:night")
        }
        val curve = controller().savedEqCurves.value.single()
        assertEquals("Bass heavy night", curve.name)
        assertEquals(3f, curve.bandsDb[1], 0.001f)
    }

    @Test
    fun `an empty name saves nothing`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.updateCustomEqBand(0, 2f)
        sound.saveEqCurve("   ")
        assertTrue(sound.savedEqCurves.value.isEmpty())
    }

    @Test
    fun `a deleted curve is gone for good`() {
        prefs.edit().clear().commit()
        val sound = controller()
        sound.updateCustomEqBand(0, 2f)
        sound.saveEqCurve("Gone")
        sound.deleteEqCurve("Gone")
        assertTrue(sound.savedEqCurves.value.isEmpty())
        assertTrue(controller().savedEqCurves.value.isEmpty())
    }

    @Test
    fun `the bands themselves survive a restart`() {
        prefs.edit().clear().commit()
        controller().updateCustomEqBand(3, -5.5f)
        assertEquals(-5.5f, controller().customEqBands.value[3], 0.001f)
    }
}
