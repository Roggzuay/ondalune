package io.github.roggzuay.ondalune

import android.app.Activity
import android.app.SearchManager
import android.os.Bundle
import io.github.roggzuay.ondalune.auto.AutoLibrary
import io.github.roggzuay.ondalune.widget.OndaluneMediaBridge

/**
 * "Play … on Ondalune" from the phone's voice assistant. Plays what was asked for through the
 * shared player and closes at once, so the request neither opens nor stacks another screen.
 */
class VoiceSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            OndaluneMediaBridge.playWithoutScreen(this, AutoLibrary.spokenQuery(intent.getStringExtra(SearchManager.QUERY), intent.extras))
        }
        finish()
    }
}
