package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import io.github.roggzuay.ondalune.audio.OndaluneAudioEngine
import io.github.roggzuay.ondalune.data.model.EqualizerPreset
import io.github.roggzuay.ondalune.data.model.PitchCorrectionMode
import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
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

    private val _customEqBands = MutableStateFlow(loadPersistedBands(_equalizerPreset.value))
    val customEqBands: StateFlow<List<Float>> = _customEqBands.asStateFlow()

    // =========================================================================
    // Your own equalizer curves
    // =========================================================================

    private val _savedEqCurves = MutableStateFlow(loadSavedCurves())

    /** Curves you shaped and named yourself, newest last, at most [MaxSavedCurves]. */
    val savedEqCurves: StateFlow<List<EqCurve>> = _savedEqCurves.asStateFlow()

    /** Keeps the bands as they stand under a name of your choosing. */
    fun saveEqCurve(name: String) {
        val clean = EqCurve.cleanName(name)
        if (clean.isEmpty()) return
        val bands = _customEqBands.value.map { it.coerceIn(-10f, 10f) }
        val kept = _savedEqCurves.value.filterNot { it.name.equals(clean, ignoreCase = true) }
        val all = kept + EqCurve(clean, bands)
        val dropped = all.size - MaxSavedCurves
        _savedEqCurves.value = all.takeLast(MaxSavedCurves)
        persistSavedCurves()
        showBanner(
            if (dropped > 0) {
                "Saved the \"$clean\" curve; \"${all.first().name}\" was let go to make room"
            } else {
                "Saved the \"$clean\" curve"
            }
        )
    }

    fun applyEqCurve(curve: EqCurve) {
        _customEqBands.value = curve.bandsDb
        engine.setEqualizerBandsDb(curve.bandsDb)
        persistBands(curve.bandsDb)
        refreshPendingSave()
    }

    fun deleteEqCurve(name: String) {
        // Saving matches names without case, so forgetting must too, or a curve becomes undeletable.
        _savedEqCurves.value = _savedEqCurves.value.filterNot { it.name.equals(name, ignoreCase = true) }
        persistSavedCurves()
    }

    private fun loadSavedCurves(): List<EqCurve> =
        prefs.getString("eq_curves", "").orEmpty()
            .split('|')
            .mapNotNull { EqCurve.parse(it) }
            .takeLast(MaxSavedCurves)

    private fun persistSavedCurves() {
        prefs.edit()
            .putString("eq_curves", _savedEqCurves.value.joinToString("|") { it.serialized() })
            .apply()
    }

    private fun loadPersistedBands(preset: EqualizerPreset): List<Float> {
        val stored = prefs.getString("eq_bands", "").orEmpty()
            .split(',')
            .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(-10f, 10f) }
        return if (stored.size == preset.bandsDb.size) stored else preset.bandsDb
    }

    private fun persistBands(bands: List<Float>) {
        prefs.edit().putString("eq_bands", bands.joinToString(",")).apply()
    }

    // =========================================================================
    // Sound profiles: this song, the way you like to hear it
    // =========================================================================

    /**
     * The sound as the playing song started. Everything the song does to it is measured against
     * this, so turning a knob and turning it back doesn't count as a change.
     */
    private var baseline: SoundSnapshot = currentSnapshot()

    /** "Not now" on the save card holds until the next song starts. */
    private var saveDismissed = false

    private val _pendingSave = MutableStateFlow<SoundSnapshot?>(null)

    /** The sound to offer to save, or null when nothing has been changed for this song. */
    val pendingSave: StateFlow<SoundSnapshot?> = _pendingSave.asStateFlow()

    /** The sound exactly as it stands. */
    fun currentSnapshot(): SoundSnapshot = SoundSnapshot(
        speedPitch = _speedPitchState.value,
        reverb = _reverbState.value,
        eqPreset = _equalizerPreset.value,
        eqBandsDb = _customEqBands.value
    )

    /**
     * A song started. The sound carries over, as it always has, but it becomes the new baseline:
     * the save card is for what you do to *this* song.
     */
    fun onPlaybackStarted() {
        baseline = currentSnapshot()
        saveDismissed = false
        _pendingSave.value = null
    }

    /** Puts a saved sound back, in one go, without offering to save it again. */
    fun applySnapshot(snapshot: SoundSnapshot) {
        _speedPitchState.value = snapshot.speedPitch
        persistSpeedPitchState(snapshot.speedPitch)
        val reverb = snapshot.reverb.sanitized()
        _reverbState.value = reverb
        persistReverbState(reverb)
        _equalizerPreset.value = snapshot.eqPreset
        _customEqBands.value = snapshot.eqBandsDb
        prefs.edit().putString("eq_preset", snapshot.eqPreset.name).apply()
        persistBands(snapshot.eqBandsDb)
        engine.setSpeedAndPitch(snapshot.speedPitch.speed, snapshot.speedPitch.effectivePitch)
        engine.setReverbState(reverb)
        engine.setEqualizerBandsDb(snapshot.eqBandsDb)
        baseline = snapshot
        saveDismissed = false
        _pendingSave.value = null
    }

    /** Keeps the sound, drops the offer to save it, until the next song. */
    fun dismissPendingSave() {
        saveDismissed = true
        _pendingSave.value = null
    }

    /**
     * Called after every change to the sound. The card appears once the sound both differs from
     * where this song started and is actually doing something.
     */
    private fun refreshPendingSave() {
        if (saveDismissed) return
        val now = currentSnapshot()
        _pendingSave.value = if (now.differsFrom(baseline) && !now.isNeutral) now else null
    }

    // =========================================================================
    // Speed (0.80x..2.00x) & Dynamic Pitch Correction Engine
    // =========================================================================

    fun setPlaybackSpeed(speed: Float) {
        val rounded = ((speed.coerceIn(0.80f, 2.00f) * 100f).roundToInt() / 100f)
        val updated = _speedPitchState.value.copy(speed = rounded)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
        refreshPendingSave()
    }

    fun setPitchCorrectionMode(mode: PitchCorrectionMode) {
        val updated = _speedPitchState.value.copy(pitchMode = mode)
        _speedPitchState.value = updated
        persistSpeedPitchState(updated)
        engine.setSpeedAndPitch(updated.speed, updated.effectivePitch)
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        refreshPendingSave()
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
        persistBands(preset.bandsDb)
        engine.setEqualizerBandsDb(preset.bandsDb)
        refreshPendingSave()
    }

    fun updateCustomEqBand(bandIndex: Int, gainDb: Float) {
        val currentList = _customEqBands.value.toMutableList()
        if (bandIndex in currentList.indices) {
            currentList[bandIndex] = gainDb.coerceIn(-10f, 10f)
            _customEqBands.value = currentList
            engine.setEqualizerBandsDb(currentList)
            persistBands(currentList)
            refreshPendingSave()
        }
    }

    /** Hands the saved speed, pitch and reverb to the engine at start-up. */
    fun applyToEngine() {
        engine.setSpeedAndPitch(_speedPitchState.value.speed, _speedPitchState.value.effectivePitch)
        engine.setReverbState(_reverbState.value)
        engine.setEqualizerBandsDb(_customEqBands.value)
    }
}

/** How many of your own curves are kept. */
private const val MaxSavedCurves = 12

/** An equalizer curve you shaped and named: five bands, -10 dB to +10 dB. */
data class EqCurve(val name: String, val bandsDb: List<Float>) {
    fun serialized(): String = name + ":" + bandsDb.joinToString(",")

    companion object {
        /** Keeps the separators out of a name, so a curve called "Bass : heavy" still reads back. */
        fun cleanName(raw: String): String =
            raw.trim().replace('|', ' ').replace(':', ' ').trim().take(28)

        fun parse(raw: String): EqCurve? {
            val at = raw.indexOf(':')
            if (at <= 0) return null
            val name = raw.substring(0, at)
            val bands = raw.substring(at + 1)
                .split(',')
                .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(-10f, 10f) }
            return if (bands.size == 5) EqCurve(name, bands) else null
        }
    }
}
