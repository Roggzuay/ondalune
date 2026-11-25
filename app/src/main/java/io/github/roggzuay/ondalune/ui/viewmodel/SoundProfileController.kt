package io.github.roggzuay.ondalune.ui.viewmodel

import io.github.roggzuay.ondalune.data.local.OndaluneDao
import io.github.roggzuay.ondalune.data.model.PlayHistoryEntry
import io.github.roggzuay.ondalune.data.model.PlayHistoryLimit
import io.github.roggzuay.ondalune.data.model.SoundProfile
import io.github.roggzuay.ondalune.data.model.SoundProfileScope
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.snapshot
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Sound profiles: a song, or an album, the way you like to hear it.
 *
 * Nothing here applies itself unless you have asked it to. When a song with a saved sound starts,
 * the player offers it on a quiet strip above the title and waits; the sound only changes when you
 * tap it. Every play is written down with whatever the sound was doing, saved or not, so the
 * version you liked but never saved is still there on Home.
 *
 * No timers, no background work: the history row rides on the play that was already being counted.
 */
class SoundProfileController(
    private val dao: OndaluneDao,
    private val sound: SoundController,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    val profiles: StateFlow<List<SoundProfile>> = dao.observeSoundProfiles()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<PlayHistoryEntry>> = dao.observePlayHistory(PlayHistoryLimit)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _offer = MutableStateFlow<SoundOffer?>(null)

    /** The saved sound waiting for a tap, or the note that one was just put on. */
    val offer: StateFlow<SoundOffer?> = _offer.asStateFlow()

    /** What the playing song's sound was when it started, for the history row. */
    private var playing: SubsonicTrack? = null

    /**
     * A sound asked for by the Home card, waiting for its song to start. Tapping a past play means
     * "that version", so it wins over the song's own saved profile for that one play.
     */
    private var pendingApply: Pair<String, SoundSnapshot>? = null

    /** The Home card asked for this song, played the way it was played before. */
    fun playWith(trackId: String, snapshot: SoundSnapshot) {
        pendingApply = trackId to snapshot
    }

    /**
     * The saved sound for a song: its own first, then its album's. Null when neither is saved.
     *
     * This asks the database rather than the list [profiles] happens to be holding. That list is
     * fed by a Room flow, which arrives a moment after a profile is written and a moment after
     * the app starts — and a song can easily begin inside that moment. Reading through means a
     * sound you just saved is found the very next time the song plays, which is exactly when
     * someone tries it.
     */
    suspend fun profileFor(track: SubsonicTrack?): SoundProfile? {
        if (track == null) return null
        dao.findSoundProfile(track.serverId, SoundProfileScope.TRACK.name, track.id)?.let { return it }
        if (track.albumId.isEmpty()) return null
        return dao.findSoundProfile(track.serverId, SoundProfileScope.ALBUM.name, track.albumId)
    }

    /**
     * A song started. The sound carries over as it always has; if this song has one saved, it is
     * offered, or put on straight away when [autoApply] is set.
     */
    fun onTrackStarted(track: SubsonicTrack?, autoApply: Boolean) {
        playing = track
        sound.onPlaybackStarted()
        val wanted = pendingApply
        pendingApply = null
        if (track != null && wanted != null && wanted.first == track.id) {
            sound.applySnapshot(wanted.second)
            _offer.value = SoundOffer(track.id, wanted.second, applied = true)
            return
        }
        _offer.value = null
        if (track == null) return
        scope.launch {
            val saved = profileFor(track) ?: return@launch
            // The song may have moved on while the row was being read.
            if (playing?.id != track.id) return@launch
            val snapshot = saved.snapshot
            if (autoApply) sound.applySnapshot(snapshot)
            _offer.value = SoundOffer(track.id, snapshot, applied = autoApply)
        }
    }

    /** Puts the offered sound on. */
    fun applyOffer() {
        val offered = _offer.value ?: return
        sound.applySnapshot(offered.snapshot)
        _offer.value = offered.copy(applied = true)
    }

    /** Clears the strip without changing the sound. */
    fun dismissOffer() {
        _offer.value = null
    }

    /** Saves the sound as it stands for this song, or for its whole album. */
    fun save(track: SubsonicTrack, profileScope: SoundProfileScope, snapshot: SoundSnapshot) {
        val scopeId = when (profileScope) {
            SoundProfileScope.TRACK -> track.id
            SoundProfileScope.ALBUM -> track.albumId
        }
        if (scopeId.isEmpty()) return
        scope.launch {
            dao.upsertSoundProfile(
                soundProfileOf(
                    serverId = track.serverId,
                    scope = profileScope,
                    scopeId = scopeId,
                    snapshot = snapshot,
                    updatedAt = now()
                )
            )
        }
        sound.dismissPendingSave()
        _offer.value = SoundOffer(track.id, snapshot, applied = true)
    }

    /** Forgets a saved sound. */
    fun forget(serverId: Long, profileScope: SoundProfileScope, scopeId: String) {
        scope.launch { dao.deleteSoundProfile(serverId, profileScope.name, scopeId) }
        if (_offer.value?.trackId != null && scopeId == playing?.id) _offer.value = null
    }

    /**
     * Writes down a play, with the sound that was on while it played. Called from the same place
     * that already counts the play, so this costs one insert and nothing else.
     */
    suspend fun recordPlay(track: SubsonicTrack) {
        dao.recordPlay(
            PlayHistoryEntry(
                serverId = track.serverId,
                trackId = track.id,
                playedAt = now(),
                sound = sound.currentSnapshot().stored(),
                hadProfile = profileFor(track) != null
            )
        )
    }

    suspend fun clearHistory() = dao.clearPlayHistory()
}

/** A saved sound waiting above the song title, or the note that one has just been put on. */
data class SoundOffer(
    val trackId: String,
    val snapshot: SoundSnapshot,
    /** False: the strip asks. True: the strip says the saved sound is playing, then fades. */
    val applied: Boolean
)
