package io.github.roggzuay.ondalune

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import io.github.roggzuay.ondalune.data.model.HomeSection
import io.github.roggzuay.ondalune.data.model.HomeSection.JUMP_BACK_IN
import io.github.roggzuay.ondalune.data.model.HomeSection.RECENTLY_ADDED
import io.github.roggzuay.ondalune.data.model.HomeSection.YOUR_MIX
import io.github.roggzuay.ondalune.ui.screens.HomeLayoutEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeLayoutEditorTest {
    @get:Rule val compose = createComposeRule()

    private fun switchOf(section: HomeSection) =
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("home_section_${section.name.lowercase()}"))).performScrollTo()

    private fun showEditor(initial: List<HomeSection>): () -> List<HomeSection> {
        var sections by mutableStateOf(initial)
        compose.setContent {
            MaterialTheme {
                // Scrolls like the real sheet, so rows below the small test window can be reached.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeLayoutEditor(sections = sections, isCustom = true, onChange = { sections = it }, onReset = {})
                }
            }
        }
        return { sections }
    }

    @Test
    fun `switching a section off keeps it off`() {
        val sections = showEditor(listOf(YOUR_MIX, JUMP_BACK_IN, RECENTLY_ADDED))
        switchOf(JUMP_BACK_IN).performClick()
        compose.waitForIdle()
        assertEquals(listOf(YOUR_MIX, RECENTLY_ADDED), sections())
    }

    @Test
    fun `switching sections off one after another keeps them all off`() {
        val sections = showEditor(listOf(YOUR_MIX, JUMP_BACK_IN, RECENTLY_ADDED))
        switchOf(YOUR_MIX).performClick()
        compose.waitForIdle()
        switchOf(RECENTLY_ADDED).performClick()
        compose.waitForIdle()
        assertEquals(listOf(JUMP_BACK_IN), sections())
    }

    @Test
    fun `hidden section switches back on at the end`() {
        val sections = showEditor(listOf(YOUR_MIX))
        switchOf(RECENTLY_ADDED).performClick()
        compose.waitForIdle()
        assertEquals(listOf(YOUR_MIX, RECENTLY_ADDED), sections())
    }

    @Test
    fun `last visible section cannot be switched off`() {
        showEditor(listOf(YOUR_MIX))
        switchOf(YOUR_MIX).assertIsNotEnabled()
    }
}
