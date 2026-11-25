package io.github.roggzuay.ondalune.ui.viewmodel

import android.content.SharedPreferences
import io.github.roggzuay.ondalune.data.local.OndaluneCredentialSecurity
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.network.ListenBrainzClient
import io.github.roggzuay.ondalune.data.network.PendingListen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Scrobbling to ListenBrainz.
 *
 * Off until a token is put in, and the token is encrypted with the same hardware-backed key as
 * your server passwords. Nothing is sent before the play already counts as a play here, so a song
 * skipped after ten seconds never reaches anyone.
 *
 * A listen that could not be sent is kept and goes with the next one. The queue is small and
 * capped: a backup of your listening history is not Ondalune's job, and an unbounded queue in
 * preferences would be a slow leak.
 */
class ListenBrainzController(
    private val prefs: SharedPreferences,
    private val client: ListenBrainzClient,
    private val scope: CoroutineScope,
    private val showBanner: (String) -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val _state = MutableStateFlow(load())

    /** Whether it is on, whose account it is, and how many listens are waiting. */
    val state: StateFlow<ListenBrainzState> = _state.asStateFlow()

    val enabled: Boolean get() = _state.value.enabled

    private fun load(): ListenBrainzState {
        val token = storedToken()
        return ListenBrainzState(
            enabled = token.isNotEmpty() && prefs.getBoolean(KeyEnabled, true),
            userName = prefs.getString(KeyUser, "").orEmpty(),
            hasToken = token.isNotEmpty(),
            queued = queue().size
        )
    }

    private fun storedToken(): String {
        val stored = prefs.getString(KeyToken, "").orEmpty()
        if (stored.isEmpty()) return ""
        return runCatching { OndaluneCredentialSecurity.decryptCredential(stored) }.getOrDefault("")
    }

    /**
     * Checks a token with ListenBrainz before keeping it, so a typo is caught here rather than
     * silently swallowing every listen from now on.
     */
    fun signIn(token: String) {
        scope.launch {
            val user = client.validate(token)
            if (user == null) {
                showBanner("ListenBrainz didn't recognise that token")
                return@launch
            }
            prefs.edit()
                .putString(KeyToken, OndaluneCredentialSecurity.encryptCredential(token.trim()))
                .putString(KeyUser, user)
                .putBoolean(KeyEnabled, true)
                .apply()
            _state.value = _state.value.copy(enabled = true, hasToken = true, userName = user)
            showBanner("Scrobbling to ListenBrainz as $user")
            flush()
        }
    }

    /** Forgets the token and everything waiting with it. */
    fun signOut() {
        prefs.edit()
            .remove(KeyToken)
            .remove(KeyUser)
            .remove(KeyQueue)
            .putBoolean(KeyEnabled, false)
            .apply()
        _state.value = ListenBrainzState()
        showBanner("Signed out of ListenBrainz")
    }

    /** Keeps the token but stops sending. */
    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KeyEnabled, on).apply()
        _state.value = _state.value.copy(enabled = on && _state.value.hasToken)
        if (on) flush()
    }

    /** "Playing now". Sent and forgotten: ListenBrainz keeps it only until the next one. */
    fun nowPlaying(track: SubsonicTrack) {
        val token = tokenIfOn() ?: return
        scope.launch { client.playingNow(token, track) }
    }

    /**
     * A finished listen. Called from the same place that already counts the play, so what reaches
     * ListenBrainz is exactly what Ondalune itself counts.
     */
    fun submit(track: SubsonicTrack) {
        val token = tokenIfOn() ?: return
        val listen = PendingListen.of(track, listenedAt = now() / 1000L)
        scope.launch {
            val waiting = queue()
            val batch = waiting + listen
            if (client.submit(token, batch)) {
                saveQueue(emptyList())
            } else {
                saveQueue(batch.takeLast(MaxQueued))
            }
        }
    }

    /** Tries the waiting listens again, if there are any. */
    fun flush() {
        val token = tokenIfOn() ?: return
        val waiting = queue()
        if (waiting.isEmpty()) return
        scope.launch {
            if (client.submit(token, waiting)) saveQueue(emptyList())
        }
    }

    private fun tokenIfOn(): String? {
        if (!_state.value.enabled) return null
        return storedToken().takeIf { it.isNotEmpty() }
    }

    private fun queue(): List<PendingListen> =
        prefs.getString(KeyQueue, "").orEmpty()
            .split('\n')
            .mapNotNull { PendingListen.parse(it) }

    private fun saveQueue(listens: List<PendingListen>) {
        prefs.edit().putString(KeyQueue, listens.joinToString("\n") { it.serialized() }).apply()
        _state.value = _state.value.copy(queued = listens.size)
    }

    private companion object {
        const val KeyToken = "listenbrainz_token"
        const val KeyUser = "listenbrainz_user"
        const val KeyEnabled = "listenbrainz_on"
        const val KeyQueue = "listenbrainz_queue"

        /** Enough for a long flight; beyond that the oldest are let go rather than kept for ever. */
        const val MaxQueued = 200
    }
}

/** What the Settings row shows. */
data class ListenBrainzState(
    val enabled: Boolean = false,
    val userName: String = "",
    val hasToken: Boolean = false,
    /** Listens that could not be sent yet. */
    val queued: Int = 0
)
