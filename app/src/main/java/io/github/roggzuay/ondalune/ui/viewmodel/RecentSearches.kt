package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Searches that led somewhere (a result opened or played, or the keyboard's search key), newest
 * first, kept between launches. Shown while the search field is still empty.
 */
class RecentSearches(private val prefs: SharedPreferences) {
    private val _items = MutableStateFlow(prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(query: String) = save(added(_items.value, query))

    fun remove(query: String) = save(_items.value - query)

    fun clear() = save(emptyList())

    private fun save(list: List<String>) {
        if (list == _items.value) return
        _items.value = list
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
    }

    companion object {
        const val KEY = "recent_searches"
        const val MAX = 12

        /** [query] tidied and put first; the same words in another case count as the same search. */
        fun added(list: List<String>, query: String): List<String> {
            val q = query.trim().replace(Regex("\\s+"), " ")
            if (q.isEmpty()) return list
            return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        }
    }
}
