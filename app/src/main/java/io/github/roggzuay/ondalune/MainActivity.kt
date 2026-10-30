package io.github.roggzuay.ondalune

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.roggzuay.ondalune.ui.screens.OndaluneMainShell
import io.github.roggzuay.ondalune.ui.viewmodel.OndaluneViewModel
import kotlin.math.abs

/**
 * Value snapshot of an Android [Display.Mode] for deterministic, unit-testable refresh-rate selection
 * across 60Hz, 90Hz, 120Hz, and 144Hz panels.
 */
data class DisplayModeSnapshot(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

data class DisplayRefreshSelection(
    val selectedModeId: Int,
    val preferredRefreshRate: Float,
    val physicalWidth: Int,
    val physicalHeight: Int
)

/**
 * Pure, deterministic refresh-rate selector that:
 * 1. Filters supported modes to the active physical resolution (`physicalWidth` x `physicalHeight`)
 *    so switching refresh rates never triggers an unintended resolution downgrade.
 * 2. Selects the highest supported refresh-rate mode when [preferHighRefreshRate] is true, or
 *    the standard ~60Hz/lowest refresh-rate mode when false.
 * 3. Never requests a `preferredRefreshRate` higher than the selected `DisplayModeSnapshot.refreshRate`
 *    (eliminating the old `coerceAtLeast(120f)` defect).
 */
object DisplayRefreshRateSelector {

    fun selectDisplayMode(
        activeMode: DisplayModeSnapshot,
        supportedModes: List<DisplayModeSnapshot>,
        preferHighRefreshRate: Boolean
    ): DisplayRefreshSelection {
        val candidates = if (supportedModes.isEmpty()) {
            listOf(activeMode)
        } else {
            supportedModes
        }

        val sameResolutionModes = candidates.filter { mode ->
            mode.physicalWidth == activeMode.physicalWidth &&
                mode.physicalHeight == activeMode.physicalHeight
        }.ifEmpty { listOf(activeMode) }

        val targetMode = if (preferHighRefreshRate) {
            sameResolutionModes.maxByOrNull { it.refreshRate } ?: activeMode
        } else {
            // Prefer standard ~60Hz mode at the active resolution if available, otherwise lowest rate
            sameResolutionModes
                .filter { it.refreshRate in 58.0f..62.0f }
                .minByOrNull { abs(it.refreshRate - 60.0f) }
                ?: sameResolutionModes.minByOrNull { it.refreshRate }
                ?: activeMode
        }

        // Strictly match the selected supported mode's refreshRate; never exceed it.
        val safeRefreshRate = targetMode.refreshRate.coerceAtMost(
            sameResolutionModes.maxOf { it.refreshRate }
        )

        return DisplayRefreshSelection(
            selectedModeId = targetMode.modeId,
            preferredRefreshRate = safeRefreshRate,
            physicalWidth = targetMode.physicalWidth,
            physicalHeight = targetMode.physicalHeight
        )
    }

    fun shouldUpdateWindowAttributes(
        currentPreferredModeId: Int,
        currentPreferredRefreshRate: Float,
        selection: DisplayRefreshSelection
    ): Boolean {
        return currentPreferredModeId != selection.selectedModeId ||
            abs(currentPreferredRefreshRate - selection.preferredRefreshRate) > 0.05f
    }
}

class MainActivity : ComponentActivity() {
    private var preferMaxRefreshRateFlag: Boolean = true
    /** Bumped when the widget's lyric lines are tapped; the shell then opens the player on Lyrics. */
    private val openLyricsRequests = androidx.compose.runtime.mutableIntStateOf(0)
    private var displayManager: DisplayManager? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            applyHighRefreshRatePreference(preferMaxRefreshRate = preferMaxRefreshRateFlag)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dissolve the system splash into the in-app intro instead of hard-cutting to it.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                splash.animate().alpha(0f).setDuration(240L).withEndAction { splash.remove() }.start()
            }
        }
        enableEdgeToEdge()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        applyHighRefreshRatePreference(preferMaxRefreshRate = preferMaxRefreshRateFlag)
        consumeOpenLyrics(intent)

        setContent {
            val ondaluneViewModel: OndaluneViewModel = viewModel()
            val settings by ondaluneViewModel.customizationSettings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.highRefreshRate120Hz) {
                preferMaxRefreshRateFlag = settings.highRefreshRate120Hz
                applyHighRefreshRatePreference(preferMaxRefreshRate = settings.highRefreshRate120Hz)
            }

            val lyricsRequest = openLyricsRequests.intValue
            LaunchedEffect(lyricsRequest) {
                if (lyricsRequest > 0) {
                    ondaluneViewModel.selectNowPlayingTab(io.github.roggzuay.ondalune.ui.viewmodel.NowPlayingSheetTab.LYRICS)
                    ondaluneViewModel.setPlayerExpanded(true)
                }
            }

            OndaluneMainShell(viewModel = ondaluneViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenLyrics(intent)
    }

    private fun consumeOpenLyrics(intent: Intent?) {
        if (intent?.getBooleanExtra(io.github.roggzuay.ondalune.widget.OndaluneMediaBridge.EXTRA_OPEN_LYRICS, false) == true) {
            intent.removeExtra(io.github.roggzuay.ondalune.widget.OndaluneMediaBridge.EXTRA_OPEN_LYRICS)
            openLyricsRequests.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        applyHighRefreshRatePreference(preferMaxRefreshRate = preferMaxRefreshRateFlag)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyHighRefreshRatePreference(preferMaxRefreshRate = preferMaxRefreshRateFlag)
        }
    }

    override fun onDestroy() {
        displayManager?.unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    /**
     * Negotiates the highest supported hardware refresh rate at the active physical display
     * resolution (e.g., 60Hz, 90Hz, 120Hz LTPO, or 144Hz) without ever requesting a refresh rate
     * higher than the selected supported [Display.Mode].
     */
    private fun applyHighRefreshRatePreference(preferMaxRefreshRate: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val activeMode = currentDisplay?.mode ?: return
            val activeSnapshot = DisplayModeSnapshot(
                modeId = activeMode.modeId,
                physicalWidth = activeMode.physicalWidth,
                physicalHeight = activeMode.physicalHeight,
                refreshRate = activeMode.refreshRate
            )
            val supportedSnapshots = currentDisplay.supportedModes.map { mode ->
                DisplayModeSnapshot(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRate = mode.refreshRate
                )
            }

            val selection = DisplayRefreshRateSelector.selectDisplayMode(
                activeMode = activeSnapshot,
                supportedModes = supportedSnapshots,
                preferHighRefreshRate = preferMaxRefreshRate
            )

            val params: WindowManager.LayoutParams = window.attributes
            if (DisplayRefreshRateSelector.shouldUpdateWindowAttributes(
                    currentPreferredModeId = params.preferredDisplayModeId,
                    currentPreferredRefreshRate = params.preferredRefreshRate,
                    selection = selection
                )
            ) {
                params.preferredDisplayModeId = selection.selectedModeId
                params.preferredRefreshRate = selection.preferredRefreshRate
                window.attributes = params
            }
        } catch (_: Exception) {
        }
    }
}
