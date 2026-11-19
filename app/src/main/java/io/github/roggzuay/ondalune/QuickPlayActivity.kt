package io.github.roggzuay.ondalune

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.roggzuay.ondalune.auto.AutoLibrary
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge

/**
 * Plays without opening a screen, then closes at once: "Play … on Ondalune" from a voice
 * assistant, and the shortcuts on the app icon (hold the icon).
 */
class QuickPlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)
            when {
                shortcut != null -> OndaluneMediaBridge.playWithoutScreen(this) { it.playShortcut(shortcut) }
                intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                    val query = AutoLibrary.spokenQuery(intent.getStringExtra(SearchManager.QUERY), intent.extras)
                    OndaluneMediaBridge.playWithoutScreen(this) { it.playFromSearch(query) }
                }
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_SHORTCUT = "io.github.roggzuay.ondalune.extra.SHORTCUT"
        const val SHORTCUT_MIX = "mix"
        const val SHORTCUT_FAVOURITES = "favourites"
        const val SHORTCUT_ALL = "all"

        /** The shortcuts shown when the app icon is held. Replaces whatever was published before. */
        fun publishShortcuts(context: Context) {
            fun shortcut(id: String, label: String, icon: Int) = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(
                    Intent(context, QuickPlayActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_SHORTCUT, id)
                )
                .build()
            runCatching {
                ShortcutManagerCompat.setDynamicShortcuts(
                    context,
                    listOf(
                        shortcut(SHORTCUT_MIX, "Play Your Mix", R.drawable.ic_shortcut_mix),
                        shortcut(SHORTCUT_FAVOURITES, "Shuffle favourites", R.drawable.ic_shortcut_favourites),
                        shortcut(SHORTCUT_ALL, "Shuffle all", R.drawable.ic_shortcut_shuffle)
                    )
                )
            }
        }
    }
}
