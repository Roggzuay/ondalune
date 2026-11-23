package io.github.roggzuay.ondalune

import android.content.Context
import io.github.roggzuay.ondalune.ui.viewmodel.RecentSearches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentSearchesTest {
    private val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("recent-test", Context.MODE_PRIVATE)

    @Test
    fun `the newest search comes first and is kept between launches`() {
        val recent = RecentSearches(prefs)
        recent.add("sza")
        recent.add("frank ocean")
        assertEquals(listOf("frank ocean", "sza"), recent.items.value)
        assertEquals(listOf("frank ocean", "sza"), RecentSearches(prefs).items.value)
    }

    @Test
    fun `searching again moves it to the top instead of adding it twice`() {
        assertEquals(listOf("SZA", "adele"), RecentSearches.added(listOf("adele", "sza"), "SZA"))
    }

    @Test
    fun `spaces are tidied and blank searches are not kept`() {
        assertEquals(listOf("joey valence"), RecentSearches.added(emptyList(), "  joey   valence "))
        assertEquals(listOf("a"), RecentSearches.added(listOf("a"), "   "))
    }

    @Test
    fun `only the latest dozen are kept`() {
        val many = (1..20).fold(emptyList<String>()) { list, i -> RecentSearches.added(list, "q$i") }
        assertEquals(RecentSearches.MAX, many.size)
        assertEquals("q20", many.first())
        assertEquals("q9", many.last())
    }

    @Test
    fun `one search can be removed, or all of them`() {
        val recent = RecentSearches(prefs)
        listOf("a", "b", "c").forEach(recent::add)
        recent.remove("b")
        assertEquals(listOf("c", "a"), recent.items.value)
        recent.clear()
        assertTrue(recent.items.value.isEmpty())
        assertTrue(RecentSearches(prefs).items.value.isEmpty())
    }
}
