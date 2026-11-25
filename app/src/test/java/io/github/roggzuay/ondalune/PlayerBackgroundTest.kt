package io.github.roggzuay.ondalune

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.roggzuay.ondalune.data.model.PlayerBackgroundAnimationStyle
import io.github.roggzuay.ondalune.ui.viewmodel.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What moves behind the player.
 *
 * The setting was loaded from preferences and written back to them, and nothing in the whole app
 * ever set it — so every backdrop except the default was unreachable, however many the enum held.
 * A new one could be added, shipped and described in the changelog without once being visible.
 * These tests hold the setting to being settable, for every style there is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerBackgroundTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun clean() {
        prefs = app.getSharedPreferences("player_background_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    private fun store() = SettingsStore(prefs)

    @Test
    fun `every backdrop can be chosen and comes back after a restart`() {
        for (style in PlayerBackgroundAnimationStyle.entries) {
            val settings = store()
            settings.setPlayerBackgroundStyle(style)
            assertEquals(style, settings.customization.value.playerBackgroundStyle)
            assertEquals("$style did not survive a restart", style, store().customization.value.playerBackgroundStyle)
        }
    }

    @Test
    fun `the reactive backdrop is one of them`() {
        val settings = store()
        settings.setPlayerBackgroundStyle(PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE)
        assertEquals(
            PlayerBackgroundAnimationStyle.ARTWORK_REACTIVE,
            store().customization.value.playerBackgroundStyle
        )
    }

    @Test
    fun `every backdrop says what it is, so a picker can name it`() {
        for (style in PlayerBackgroundAnimationStyle.entries) {
            assertTrue(style.name, style.label.isNotBlank())
            assertTrue(style.name, style.description.isNotBlank())
        }
    }

    @Test
    fun `a stored name nobody recognises falls back instead of crashing`() {
        prefs.edit().putString("cfg_bg_style_v2", "SOMETHING_FROM_THE_FUTURE").commit()
        assertEquals(
            PlayerBackgroundAnimationStyle.ARTWORK_GLOW,
            store().customization.value.playerBackgroundStyle
        )
    }
}
