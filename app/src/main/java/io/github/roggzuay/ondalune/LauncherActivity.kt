package io.github.roggzuay.ondalune

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * What every launcher icon (an activity-alias) opens: it hands over to [MainActivity] at once and
 * closes. Android closes every screen in a task an alias started once that alias is turned off,
 * so this runs in a throwaway task and MainActivity lives in the app's own; switching icons then
 * no longer closes the open app or stops the music.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(intent)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
