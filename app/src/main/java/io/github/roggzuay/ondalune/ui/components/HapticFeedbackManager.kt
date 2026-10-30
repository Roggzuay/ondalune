package io.github.roggzuay.ondalune.ui.components

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/**
 * Centralized tactile feedback controller for Ondalune's Material 3 Expressive player.
 *
 * Delivers nuanced, subtle haptic patterns for:
 * - Play / Pause state transitions (rising double-pulse on play vs. soft damped tick on pause)
 * - Viscous-glue transport button press-down tension, track skips, and mode toggles
 * - Squiggly waveform scrubber grab, continuous vinyl-groove crest micro-ticks, boundary detents,
 *   and seek-commit release
 */
@Stable
class HapticFeedbackManager(
    private val view: View,
    context: Context,
    private val composeHaptics: HapticFeedback,
    var isEnabled: Boolean = true
) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    private var lastScrubFraction: Float = -1f
    private var lastScrubTickMs: Long = 0L
    private var lastScrubBoundaryState: Int = 0 // -1 = at 0%, 1 = at 100%, 0 = inside
    private var lastPlayPauseTransitionMs: Long = 0L

    /**
     * Play / Pause transition haptic pattern:
     * - Starting playback (`isStartingPlay = true`): Crisp rising engagement pulse
     * - Pausing playback (`isStartingPlay = false`): Soft, damped release tick
     */
    fun performPlayPauseTransition(isStartingPlay: Boolean) {
        if (!isEnabled) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayPauseTransitionMs < 140L) return
        lastPlayPauseTransitionMs = now
        if (isStartingPlay) {
            val performed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            if (!performed) {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            vibrateWaveformIfSupported(
                timings = longArrayOf(0L, 9L, 28L, 14L),
                amplitudes = intArrayOf(0, 95, 0, 165)
            )
        } else {
            val performed = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!performed) {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            vibrateOneShotIfSupported(durationMs = 10L, amplitude = 75)
        }
    }

    /**
     * Viscous-glue button press-down tension tick when the button begins stretching away
     * from its adjacent neighbor in the transport strip.
     */
    fun performGluePressStart(isPrimaryCenter: Boolean = false) {
        if (!isEnabled) return
        val constant = if (isPrimaryCenter) {
            HapticFeedbackConstants.CONTEXT_CLICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        if (!view.performHapticFeedback(constant)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Mechanical double-detent pulse when skipping to the previous or next track.
     */
    fun performTrackSkip(isNext: Boolean) {
        if (!isEnabled) return
        if (!view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        val amplitudes = if (isNext) {
            intArrayOf(0, 90, 0, 145)
        } else {
            intArrayOf(0, 140, 0, 85)
        }
        vibrateWaveformIfSupported(
            timings = longArrayOf(0L, 7L, 24L, 11L),
            amplitudes = amplitudes
        )
    }

    /**
     * Tactile toggle detent for Shuffle, Repeat-One, Synced Lyrics, Up Next Queue,
     * Star/Favourite, and Sleep Timer toggles.
     */
    fun performToggleOption(enabled: Boolean) {
        if (!isEnabled) return
        val constant = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                if (enabled) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (enabled) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
            }
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (!view.performHapticFeedback(constant)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Squiggly Waveform Scrubber: Initial finger touch / thumb grab.
     */
    fun performWaveformScrubStart(initialFraction: Float) {
        if (!isEnabled) return
        lastScrubFraction = initialFraction.coerceIn(0f, 1f)
        lastScrubTickMs = SystemClock.uptimeMillis()
        lastScrubBoundaryState = when {
            initialFraction <= 0.005f -> -1
            initialFraction >= 0.995f -> 1
            else -> 0
        }

        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        if (!view.performHapticFeedback(constant)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        vibrateOneShotIfSupported(durationMs = 8L, amplitude = 95)
    }

    /**
     * Squiggly Waveform Scrubber: Continuous drag across waveform crests.
     * Produces subtle vinyl-groove micro-ticks every ~1.8% of track progress (rate-limited to >= 22ms)
     * and a distinct boundary bump when hitting 0:00 or 100% track end.
     */
    fun performWaveformScrubMove(fraction: Float) {
        if (!isEnabled) return
        val clamped = fraction.coerceIn(0f, 1f)
        val now = SystemClock.uptimeMillis()

        val boundaryNow = when {
            clamped <= 0.004f -> -1
            clamped >= 0.996f -> 1
            else -> 0
        }

        // Distinct detent thud when hitting start (0:00) or end of track
        if (boundaryNow != 0 && boundaryNow != lastScrubBoundaryState) {
            lastScrubBoundaryState = boundaryNow
            lastScrubFraction = clamped
            lastScrubTickMs = now
            val rejectConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            if (!view.performHapticFeedback(rejectConst)) {
                composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            vibrateOneShotIfSupported(durationMs = 14L, amplitude = 150)
            return
        }
        lastScrubBoundaryState = boundaryNow

        // Micro-tick across squiggly wave crests (~55 fine detents across the track width)
        val delta = abs(clamped - lastScrubFraction)
        if (delta >= 0.018f && (now - lastScrubTickMs) >= 22L) {
            lastScrubFraction = clamped
            lastScrubTickMs = now
            val tickConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            if (!view.performHapticFeedback(tickConst)) {
                composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            vibratePredefinedTickIfSupported()
        }
    }

    /**
     * Squiggly Waveform Scrubber: Finger release / seek commit.
     */
    fun performWaveformScrubCommit() {
        if (!isEnabled) return
        lastScrubBoundaryState = 0
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        if (!view.performHapticFeedback(constant)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        vibrateOneShotIfSupported(durationMs = 9L, amplitude = 115)
    }

    /**
     * Subtle tick for speed/pitch step adjustments, navigation dock tabs, and secondary buttons.
     */
    fun performSubtleTick() {
        if (!isEnabled) return
        if (!view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    private fun vibratePredefinedTickIfSupported() {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            }
        } catch (_: Throwable) {
            // Ignore on emulators or devices without vibrator hardware
        }
    }

    private fun vibrateOneShotIfSupported(durationMs: Long, amplitude: Int) {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmp = if (vib.hasAmplitudeControl()) {
                    amplitude.coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                vib.vibrate(VibrationEffect.createOneShot(durationMs, safeAmp))
            }
        } catch (_: Throwable) {
            // Ignore on devices without vibrator support
        }
    }

    private fun vibrateWaveformIfSupported(timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Throwable) {
            // Ignore on devices without amplitude waveform support
        }
    }
}

val LocalHapticFeedbackManager = compositionLocalOf<HapticFeedbackManager?> { null }

@Composable
fun rememberHapticFeedbackManager(enabled: Boolean = true): HapticFeedbackManager {
    val view = LocalView.current
    val context = LocalContext.current
    val composeHaptics = LocalHapticFeedback.current
    val manager = remember(view, context, composeHaptics) {
        HapticFeedbackManager(
            view = view,
            context = context,
            composeHaptics = composeHaptics,
            isEnabled = enabled
        )
    }
    manager.isEnabled = enabled
    return manager
}
