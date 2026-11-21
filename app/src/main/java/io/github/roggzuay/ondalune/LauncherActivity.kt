package io.github.roggzuay.ondalune

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What every launcher icon (an activity-alias) opens: it hands over to [MainActivity] at once and
 * closes. Android closes every screen in a task an alias started once that alias is turned off,
 * so this runs in a throwaway task and MainActivity lives in the app's own; switching icons then
 * no longer closes the open app or stops the music. Song files opened from other apps ("Open with")
 * and "open the music player" requests come in the same way, so they land in that task too.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(intent)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // This screen's own "hidden from Recents, no history" flags ride along in the copied
                // intent; left on, Ondalune vanishes from Recents the moment you leave it.
                .apply { removeFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY) }
                // An opened song file: pass the read access on, or it ends with this screen.
                .apply { if (data != null) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
