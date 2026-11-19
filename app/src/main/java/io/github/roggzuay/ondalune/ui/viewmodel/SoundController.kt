package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import io.github.roggzuay.ondalune.audio.OndaluneAudioEngine
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SpeedPitchState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sound settings: speed and pitch, live reverb and the equalizer, saved between launches and
 * handed to the audio engine. Only state and saving live here; the engine does the processing.
 */
class SoundController(
    private val prefs: SharedPreferences,
    private val engine: OndaluneAudioEngine,
    private val showBanner: (String) -> Unit,
) {
    private val _speedPitchState = MutableStateFlow(loadPersistedSpeedPitchState())
    val speedPitchState: StateFlow<SpeedPitchState> = _speedPitchState.asStateFlow()

    private val _reverbState = MutableStateFlow(loadPersistedReverbState())
    val reverbState: StateFlow<ReverbState> = _reverbState.asStateFlow()

    // Shown once per install, new or upgraded: the player's speed button resets on a long press.
    private val _showSpeedResetHint = MutableStateFlow(!prefs.getBoolean("hint_speed_hold_reset", false))
    val showSpeedResetHint: StateFlow<Boolean> = _showSpeedResetHint.asStateFlow()

    fun markSpeedResetHintShown() {
        _showSpeedResetHint.value = false
        prefs.edit().putBoolean("hint_speed_hold_reset", true).apply()
    }

    private val _equalizerPreset = MutableStateFlow(
        runCatching {
            EqualizerPreset.valueOf(prefs.getString("eq_preset", EqualizerPreset.FLAT_STUDIO.name)!!)
        }.getOrDefault(EqualizerPreset.FLAT_STUDIO)
    )
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    private val _customEqBands = MutableStateFlow(_equalizerPreset.value.bandsDb)
    val customEqBands: StateFlow<List<Float>> = _customEqBands.asStateFlow()

    // =========================================================================
    // Speed (0.80x..2.00x) & Dynamic Pitch Correction Engine
    // =========================================================================

    fun setPlaybackSpeed(speed: Float) {
        val rounded = ((speed.coerceIn(0.80f, 2.00f) * 100f).roundToInt() / 100f)
        val updated = _speedPitchState.value.copy(speed = rounded)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setPitchCorrectionMode(mode: PitchCorrectionMode) {
        val updated = _speedPitchState.value.copy(pitchMode = mode)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setIndependentSemitones(semitones: Float) {
        val ratio = SpeedPitchState.semitonesToRatio(semitones.coerceIn(-12f, 12f))
        val updated = _speedPitchState.value.copy(
            pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
            independentPitchRatio = ratio
        )
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun setIndependentPitchRatio(ratio: Float) {
        val clean = ratio.coerceIn(0.50f, 2.00f)
        val updated = _speedPitchState.value.copy(
            pitchMode = PitchCorrectionMode.INDEPENDENT_SEMITONES,
            independentPitchRatio = clean
        )
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
    }

    fun resetSpeedAndPitch() {
        val resetState = SpeedPitchState(
            speed = 1.0f,
            pitchMode = PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH,
            independentPitchRatio = 1.0f
        )
        _speedPitchState.value = resetState
        persistSpeedPitchState(resetState)
        engine.setSpeedAndPitch(1.0f, 1.0f)
    }

    // =========================================================================
    // Live Studio Reverb DSP Engine (Simultaneous with Speed & Dynamic Pitch)
    // =========================================================================

    fun setReverbEnabled(enabled: Boolean) {
        val current = _reverbState.value
        val nextPreset = if (enabled && current.preset == ReverbPreset.OFF) {
            ReverbPreset.STUDIO_ROOM
        } else {
            current.preset
        }
        val updated = if (enabled && current.preset == ReverbPreset.OFF) {
            ReverbState.fromPreset(ReverbPreset.STUDIO_ROOM).copy(enabled = true)
        } else {
            current.copy(enabled = enabled, preset = nextPreset)
        }
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
    }

    fun selectReverbPreset(preset: ReverbPreset) {
        val updated = ReverbState.fromPreset(preset).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
        showBanner(
            if (updated.isActivelyProcessing) {
                "Live Reverb: ${preset.label} (${(updated.wetDryMix * 100).roundToInt()}% Wet)"
            } else {
                "Live Reverb bypassed (Dry signal)"
            }
        )
    }

    fun setReverbWetDryMix(wetDryMix: Float) {
        val clean = wetDryMix.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = clean > 0.005f,
            preset = if (clean <= 0.005f) ReverbPreset.OFF else ReverbPreset.CUSTOM,
            wetDryMix = clean
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
    }

    fun setReverbRoomSize(roomSize: Float) {
        val clean = roomSize.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            roomSize = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
    }

    fun setReverbDecay(decay: Float) {
        val clean = decay.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            decay = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
    }

    fun setReverbDamping(damping: Float) {
        val clean = damping.coerceIn(0f, 1f)
        val current = _reverbState.value
        val updated = current.copy(
            enabled = true,
            preset = ReverbPreset.CUSTOM,
            damping = clean,
            wetDryMix = current.wetDryMix.coerceAtLeast(0.15f)
        ).sanitized()
        _reverbState.value = updated
        persistReverbState(updated)
        engine.setReverbState(updated)
    }

    fun resetReverbToDry() {
        val dry = ReverbState.fromPreset(ReverbPreset.OFF)
        _reverbState.value = dry
        persistReverbState(dry)
        engine.setReverbState(dry)
        showBanner("Live Reverb reset to clean dry studio signal")
    }

    fun processLiveDspStereoBlock(interleavedStereo: FloatArray): FloatArray {
        engine.processLiveDspStereoBlock(interleavedStereo)
        return interleavedStereo
    }

    // =========================================================================
    // SharedPreferences Persistence Helpers
    // =========================================================================

    private fun loadPersistedSpeedPitchState(): SpeedPitchState {
        val speed = prefs.getFloat("dsp_speed", 1.0f).coerceIn(0.80f, 2.00f)
        val modeName = prefs.getString("dsp_pitch_mode", PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH.name)
        val mode = PitchCorrectionMode.entries.find { it.name == modeName }
            ?: PitchCorrectionMode.TIME_STRETCH_PRESERVE_PITCH
        val ratio = prefs.getFloat("dsp_pitch_ratio", 1.0f).coerceIn(0.50f, 2.00f)
        return SpeedPitchState(speed = speed, pitchMode = mode, independentPitchRatio = ratio)
    }

    private fun persistSpeedPitchState(state: SpeedPitchState) {
        prefs.edit()
            .putFloat("dsp_speed", state.speed)
            .putString("dsp_pitch_mode", state.pitchMode.name)
            .putFloat("dsp_pitch_ratio", state.independentPitchRatio)
            .apply()
    }

    private fun loadPersistedReverbState(): ReverbState {
        val enabled = prefs.getBoolean("reverb_enabled", false)
        val presetName = prefs.getString("reverb_preset", ReverbPreset.OFF.name)
        val preset = ReverbPreset.entries.find { it.name == presetName } ?: ReverbPreset.OFF
        val wetDry = prefs.getFloat("reverb_wet_dry", preset.defaultWetDryMix)
        val roomSize = prefs.getFloat("reverb_room_size", preset.defaultRoomSize)
        val decay = prefs.getFloat("reverb_decay", preset.defaultDecay)
        val damping = prefs.getFloat("reverb_damping", preset.defaultDamping)
        return ReverbState(
            enabled = enabled,
            preset = preset,
            wetDryMix = wetDry,
            roomSize = roomSize,
            decay = decay,
            damping = damping
        ).sanitized()
    }

    private fun persistReverbState(state: ReverbState) {
        val clean = state.sanitized()
        prefs.edit()
            .putBoolean("reverb_enabled", clean.enabled)
            .putString("reverb_preset", clean.preset.name)
            .putFloat("reverb_wet_dry", clean.wetDryMix)
            .putFloat("reverb_room_size", clean.roomSize)
            .putFloat("reverb_decay", clean.decay)
            .putFloat("reverb_damping", clean.damping)
            .apply()
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        _equalizerPreset.value = preset
        _customEqBands.value = preset.bandsDb
        prefs.edit().putString("eq_preset", preset.name).apply()
        engine.setEqualizerBandsDb(preset.bandsDb)
    }

    fun updateCustomEqBand(bandIndex: Int, gainDb: Float) {
        val currentList = _customEqBands.value.toMutableList()
        if (bandIndex in currentList.indices) {
            currentList[bandIndex] = gainDb.coerceIn(-10f, 10f)
            _customEqBands.value = currentList
            engine.setEqualizerBandsDb(currentList)
        }
    }

    /** Hands the saved speed, pitch and reverb to the engine at start-up. */
    fun applyToEngine() {
        engine.setSpeedAndPitch(_speedPitchState.value.speed, _speedPitchState.value.effectivePitch)
        engine.setReverbState(_reverbState.value)
    }
}
