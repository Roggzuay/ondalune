package io.github.roggzuay.ondalune.audio

import io.github.roggzuay.ondalune.data.model.ReverbPreset
import io.github.roggzuay.ondalune.data.model.ReverbState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hardware parameters mapped from [ReverbState] for Android's [android.media.audiofx.EnvironmentalReverb].
 */
data class EnvironmentalReverbHardwareParams(
    val enabled: Boolean,
    val roomLevelMillibels: Short,
    val roomHfLevelMillibels: Short,
    val decayTimeMs: Int,
    val decayHfRatioPermille: Short,
    val reflectionsLevelMillibels: Short,
    val reflectionsDelayMs: Int,
    val reverbLevelMillibels: Short,
    val reverbDelayMs: Int,
    val diffusionPermille: Short,
    val densityPermille: Short
)

/**
 * Real-Time Stereo Floating-Point Freeverb / Schroeder Reverberation DSP Processor.
 *
 * Architecture:
 * - 8 parallel low-pass-damped feedback comb filters per channel (with 23-sample stereo spread on right channel)
 * - 4 series Schroeder all-pass diffusers per channel
 * - Per-sample one-pole parameter smoothing (wet/dry, feedback, HF damping) to eliminate zipper noise,
 *   clicks, or pops during live slider interaction
 * - Pre-allocated primitive [FloatArray] delay buffers so the real-time audio loop performs zero heap allocations
 * - Soft-knee output protection ensuring finite, non-clipping output (`[-1.0f, 1.0f]`) under all input conditions
 */
class OndaluneReverbDspProcessor(
    val sampleRate: Int = 44100
) {
    companion object {
        private val BASE_COMB_TUNINGS_44K1 = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val BASE_ALLPASS_TUNINGS_44K1 = intArrayOf(556, 441, 341, 225)
        private const val STEREO_SPREAD_44K1 = 23
        private const val ALLPASS_FEEDBACK = 0.5f
        private const val FIXED_INPUT_GAIN = 0.018f
        private const val SCALE_DAMP = 0.45f
        private const val SCALE_ROOM = 0.28f
        private const val OFFSET_ROOM = 0.68f
        private const val SMOOTHING_COEFF = 0.005f

        /**
         * Maps high-level [ReverbState] parameters to Android's native [android.media.audiofx.EnvironmentalReverb]
         * millibel / millisecond / permille parameters for live hardware session processing.
         */
        fun toEnvironmentalReverbParams(rawState: ReverbState): EnvironmentalReverbHardwareParams {
            val state = rawState.sanitized()
            if (!state.isActivelyProcessing) {
                return EnvironmentalReverbHardwareParams(
                    enabled = false,
                    roomLevelMillibels = (-9000).toShort(),
                    roomHfLevelMillibels = 0.toShort(),
                    decayTimeMs = 100,
                    decayHfRatioPermille = 500.toShort(),
                    reflectionsLevelMillibels = (-9000).toShort(),
                    reflectionsDelayMs = 7,
                    reverbLevelMillibels = (-9000).toShort(),
                    reverbDelayMs = 11,
                    diffusionPermille = 500.toShort(),
                    densityPermille = 500.toShort()
                )
            }

            // Room level: -2200 mB (subtle) .. 0 mB (full)
            val roomMb = (-2200f + (state.roomSize * 2000f)).toInt().coerceIn(-9000, 0).toShort()
            // High-frequency damping: 0 mB (bright) .. -2400 mB (heavily damped warm tail)
            val roomHfMb = (-(state.damping * 2400f)).toInt().coerceIn(-9000, 0).toShort()
            // Decay time: 220 ms .. 7200 ms combining roomSize and decay
            val combinedDecay = ((state.decay * 0.72f) + (state.roomSize * 0.28f)).coerceIn(0.05f, 1.0f)
            val decayMs = (220f + (combinedDecay * 6980f)).toInt().coerceIn(100, 10000)
            // HF Decay Ratio: 1400 permille (bright) .. 250 permille (dark damped)
            val decayHfRatio = (1400f - (state.damping * 1150f)).toInt().coerceIn(100, 2000).toShort()
            // Wet Reverb Level: -3200 mB .. +400 mB scaled by wetDryMix
            val reverbMb = (-3200f + (state.wetDryMix * 3500f)).toInt().coerceIn(-9000, 1000).toShort()
            val reflectionsMb = (-2600f + (state.wetDryMix * 2200f)).toInt().coerceIn(-9000, 500).toShort()
            val reflectionsDelay = (5f + (state.roomSize * 35f)).toInt().coerceIn(0, 300)
            val reverbDelay = (10f + (state.roomSize * 55f)).toInt().coerceIn(0, 100)
            val diffusion = (550f + (state.roomSize * 450f)).toInt().coerceIn(0, 1000).toShort()
            val density = (500f + (state.decay * 500f)).toInt().coerceIn(0, 1000).toShort()

            return EnvironmentalReverbHardwareParams(
                enabled = true,
                roomLevelMillibels = roomMb,
                roomHfLevelMillibels = roomHfMb,
                decayTimeMs = decayMs,
                decayHfRatioPermille = decayHfRatio,
                reflectionsLevelMillibels = reflectionsMb,
                reflectionsDelayMs = reflectionsDelay,
                reverbLevelMillibels = reverbMb,
                reverbDelayMs = reverbDelay,
                diffusionPermille = diffusion,
                densityPermille = density
            )
        }
    }

    private val numCombs = BASE_COMB_TUNINGS_44K1.size
    private val numAllpasses = BASE_ALLPASS_TUNINGS_44K1.size
    private val rateScale = (sampleRate.coerceIn(8000, 192000).toFloat() / 44100f)
    private val stereoSpread = max(1, (STEREO_SPREAD_44K1 * rateScale).toInt())

    // Pre-allocated left/right comb delay lines and filter states (zero allocation in audio loop)
    private val combBuffersL = Array(numCombs) { idx ->
        FloatArray(max(64, (BASE_COMB_TUNINGS_44K1[idx] * rateScale).toInt()))
    }
    private val combBuffersR = Array(numCombs) { idx ->
        FloatArray(max(64, (BASE_COMB_TUNINGS_44K1[idx] * rateScale).toInt() + stereoSpread))
    }
    private val combIndexL = IntArray(numCombs)
    private val combIndexR = IntArray(numCombs)
    private val combFilterStoreL = FloatArray(numCombs)
    private val combFilterStoreR = FloatArray(numCombs)

    // Pre-allocated left/right all-pass delay lines
    private val allpassBuffersL = Array(numAllpasses) { idx ->
        FloatArray(max(32, (BASE_ALLPASS_TUNINGS_44K1[idx] * rateScale).toInt()))
    }
    private val allpassBuffersR = Array(numAllpasses) { idx ->
        FloatArray(max(32, (BASE_ALLPASS_TUNINGS_44K1[idx] * rateScale).toInt() + stereoSpread))
    }
    private val allpassIndexL = IntArray(numAllpasses)
    private val allpassIndexR = IntArray(numAllpasses)

    // Target and smoothed DSP parameters
    @Volatile
    private var currentState: ReverbState = ReverbState()

    @Volatile
    private var targetWetGain: Float = 0.0f

    @Volatile
    private var targetDryGain: Float = 1.0f

    @Volatile
    private var targetFeedback: Float = 0.72f

    @Volatile
    private var targetDamp1: Float = 0.2f

    private var smoothedWetGain: Float = 0.0f
    private var smoothedDryGain: Float = 1.0f
    private var smoothedFeedback: Float = 0.72f
    private var smoothedDamp1: Float = 0.2f

    init {
        updateParameters(ReverbState(), snapImmediately = true)
    }

    fun getReverbState(): ReverbState = currentState

    /**
     * Updates target DSP parameters from [ReverbState] with automatic bounds clamping.
     * When [snapImmediately] is false (default during live playback), parameters ramp smoothly
     * per sample to avoid clicks or discontinuities.
     */
    @Synchronized
    fun updateParameters(newState: ReverbState, snapImmediately: Boolean = false) {
        val safe = newState.sanitized()
        currentState = safe

        if (!safe.isActivelyProcessing) {
            targetWetGain = 0.0f
            targetDryGain = 1.0f
            val combinedRoom = ((safe.roomSize * 0.5f) + (safe.decay * 0.5f)).coerceIn(0f, 1f)
            targetFeedback = (OFFSET_ROOM + (combinedRoom * SCALE_ROOM)).coerceIn(0.65f, 0.965f)
            targetDamp1 = (safe.damping * SCALE_DAMP).coerceIn(0.02f, 0.48f)
        } else {
            val wet = safe.wetDryMix.coerceIn(0f, 1f)
            // Equal-power / constant-clarity wet/dry curve
            targetDryGain = sqrt((1.0f - (wet * 0.65f)).coerceIn(0.15f, 1.0f))
            targetWetGain = wet * 2.6f
            val combinedRoom = ((safe.roomSize * 0.48f) + (safe.decay * 0.52f)).coerceIn(0f, 1f)
            targetFeedback = (OFFSET_ROOM + (combinedRoom * SCALE_ROOM)).coerceIn(0.68f, 0.972f)
            targetDamp1 = (safe.damping * SCALE_DAMP).coerceIn(0.02f, 0.48f)
        }

        if (snapImmediately) {
            smoothedWetGain = targetWetGain
            smoothedDryGain = targetDryGain
            smoothedFeedback = targetFeedback
            smoothedDamp1 = targetDamp1
        }
    }

    fun updateState(newState: ReverbState, sampleRate: Int = this.sampleRate) {
        updateParameters(newState, snapImmediately = false)
    }

    /**
     * Applies a preset immediately or smoothly.
     */
    fun applyPreset(preset: ReverbPreset, snapImmediately: Boolean = false): ReverbState {
        val state = ReverbState.fromPreset(preset)
        updateParameters(state, snapImmediately)
        return currentState
    }

    /**
     * Disables reverb and returns to 100% dry signal path.
     */
    fun bypass(snapImmediately: Boolean = false): ReverbState {
        val next = currentState.copy(enabled = false)
        updateParameters(next, snapImmediately)
        return currentState
    }

    /**
     * Clears internal comb and all-pass delay lines (e.g., on track switch or explicit reset).
     */
    @Synchronized
    fun clearDelayLines() {
        for (i in 0 until numCombs) {
            combBuffersL[i].fill(0f)
            combBuffersR[i].fill(0f)
            combFilterStoreL[i] = 0f
            combFilterStoreR[i] = 0f
            combIndexL[i] = 0
            combIndexR[i] = 0
        }
        for (i in 0 until numAllpasses) {
            allpassBuffersL[i].fill(0f)
            allpassBuffersR[i].fill(0f)
            allpassIndexL[i] = 0
            allpassIndexR[i] = 0
        }
    }

    /**
     * Processes stereo interleaved floating-point samples `[L0, R0, L1, R1, ...]` in-place.
     * Guaranteed zero heap allocations, finite output, and soft-knee clipping protection.
     */
    @Synchronized
    fun processStereoInterleavedFloat(
        samples: FloatArray,
        frameCount: Int = samples.size / 2
    ) {
        val safeFrames = min(frameCount, samples.size / 2)
        if (safeFrames <= 0) return

        // Fast bypass check when completely dry and smoothed wet gain has decayed to zero
        if (!currentState.isActivelyProcessing && smoothedWetGain < 1e-5f && abs(smoothedDryGain - 1f) < 1e-4f) {
            for (f in 0 until safeFrames * 2) {
                val v = samples[f]
                samples[f] = if (v.isFinite()) v.coerceIn(-1f, 1f) else 0f
            }
            return
        }

        val tWet = targetWetGain
        val tDry = targetDryGain
        val tFb = targetFeedback
        val tDamp = targetDamp1

        for (frame in 0 until safeFrames) {
            val idxL = frame * 2
            val idxR = idxL + 1

            val rawL = samples[idxL]
            val rawR = samples[idxR]
            val inL = if (rawL.isFinite()) rawL.coerceIn(-1f, 1f) else 0f
            val inR = if (rawR.isFinite()) rawR.coerceIn(-1f, 1f) else 0f

            // Per-sample parameter smoothing
            smoothedWetGain += (tWet - smoothedWetGain) * SMOOTHING_COEFF
            smoothedDryGain += (tDry - smoothedDryGain) * SMOOTHING_COEFF
            smoothedFeedback += (tFb - smoothedFeedback) * SMOOTHING_COEFF
            smoothedDamp1 += (tDamp - smoothedDamp1) * SMOOTHING_COEFF
            val damp2 = 1.0f - smoothedDamp1

            val monoExciter = (inL + inR) * FIXED_INPUT_GAIN
            var wetL = 0f
            var wetR = 0f

            // 8 Parallel Low-Pass-Damped Feedback Comb Filters
            for (c in 0 until numCombs) {
                val bufL = combBuffersL[c]
                val posL = combIndexL[c]
                val delayedL = bufL[posL]
                val filteredL = (delayedL * damp2) + (combFilterStoreL[c] * smoothedDamp1)
                combFilterStoreL[c] = if (filteredL.isFinite()) filteredL else 0f
                val writeL = monoExciter + (combFilterStoreL[c] * smoothedFeedback)
                bufL[posL] = if (writeL.isFinite()) writeL.coerceIn(-2f, 2f) else 0f
                combIndexL[c] = if (posL + 1 >= bufL.size) 0 else posL + 1
                wetL += delayedL

                val bufR = combBuffersR[c]
                val posR = combIndexR[c]
                val delayedR = bufR[posR]
                val filteredR = (delayedR * damp2) + (combFilterStoreR[c] * smoothedDamp1)
                combFilterStoreR[c] = if (filteredR.isFinite()) filteredR else 0f
                val writeR = monoExciter + (combFilterStoreR[c] * smoothedFeedback)
                bufR[posR] = if (writeR.isFinite()) writeR.coerceIn(-2f, 2f) else 0f
                combIndexR[c] = if (posR + 1 >= bufR.size) 0 else posR + 1
                wetR += delayedR
            }

            // 4 Series Schroeder All-Pass Diffusers
            for (a in 0 until numAllpasses) {
                val apBufL = allpassBuffersL[a]
                val apPosL = allpassIndexL[a]
                val bufOutL = apBufL[apPosL]
                val newWriteL = wetL + (bufOutL * ALLPASS_FEEDBACK)
                apBufL[apPosL] = if (newWriteL.isFinite()) newWriteL.coerceIn(-2f, 2f) else 0f
                wetL = bufOutL - wetL
                allpassIndexL[a] = if (apPosL + 1 >= apBufL.size) 0 else apPosL + 1

                val apBufR = allpassBuffersR[a]
                val apPosR = allpassIndexR[a]
                val bufOutR = apBufR[apPosR]
                val newWriteR = wetR + (bufOutR * ALLPASS_FEEDBACK)
                apBufR[apPosR] = if (newWriteR.isFinite()) newWriteR.coerceIn(-2f, 2f) else 0f
                wetR = bufOutR - wetR
                allpassIndexR[a] = if (apPosR + 1 >= apBufR.size) 0 else apPosR + 1
            }

            // Stereo cross-matrixing for lush spaciousness
            val crossWetL = (wetL * 0.85f) + (wetR * 0.15f)
            val crossWetR = (wetR * 0.85f) + (wetL * 0.15f)

            val mixedL = (inL * smoothedDryGain) + (crossWetL * smoothedWetGain)
            val mixedR = (inR * smoothedDryGain) + (crossWetR * smoothedWetGain)

            samples[idxL] = softClipLimit(mixedL)
            samples[idxR] = softClipLimit(mixedR)
        }
    }

    /**
     * Processes 16-bit signed stereo PCM samples (`ShortArray`) in-place.
     */
    @Synchronized
    fun processStereoInterleavedPcm16(
        pcmSamples: ShortArray,
        frameCount: Int = pcmSamples.size / 2
    ) {
        val safeFrames = min(frameCount, pcmSamples.size / 2)
        if (safeFrames <= 0) return
        if (!currentState.isActivelyProcessing && smoothedWetGain < 1e-5f) return

        val floatWorkspace = FloatArray(safeFrames * 2)
        for (i in 0 until safeFrames * 2) {
            floatWorkspace[i] = (pcmSamples[i].toFloat() / 32768.0f).coerceIn(-1f, 1f)
        }
        processStereoInterleavedFloat(floatWorkspace, safeFrames)
        for (i in 0 until safeFrames * 2) {
            val scaled = (floatWorkspace[i] * 32767.0f).toInt().coerceIn(-32767, 32767)
            pcmSamples[i] = scaled.toShort()
        }
    }

    /**
     * Soft-knee studio limiter ensuring output never exceeds `[-1.0f, 1.0f]` and never returns NaN/Inf.
     */
    private fun softClipLimit(x: Float): Float {
        if (!x.isFinite()) return 0f
        val ax = abs(x)
        if (ax <= 0.85f) return x
        // Smooth asymptotic soft saturation above 0.85 threshold up to 1.0 ceiling
        val excess = ax - 0.85f
        val compressed = 0.85f + (0.15f * (excess / (excess + 0.15f)))
        return if (x >= 0f) compressed.coerceAtMost(1.0f) else (-compressed).coerceAtLeast(-1.0f)
    }
}
