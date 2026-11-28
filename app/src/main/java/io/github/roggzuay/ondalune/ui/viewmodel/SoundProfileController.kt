package io.github.roggzuay.ondalune.ui.viewmodel

import io.github.roggzuay.ondalune.data.local.OndaluneDao
import io.github.roggzuay.ondalune.data.model.PlayHistoryEntry
import io.github.roggzuay.ondalune.data.model.PlayHistoryLimit
import io.github.roggzuay.ondalune.data.model.SoundProfile
import io.github.roggzuay.ondalune.data.model.SoundProfileScope
import io.github.roggzuay.ondalune.data.model.SoundSnapshot
import io.github.roggzuay.ondalune.data.model.SubsonicTrack
import io.github.roggzuay.ondalune.data.model.blendTo
import io.github.roggzuay.ondalune.data.model.snapshot
import io.github.roggzuay.ondalune.data.model.soundGlideEase
import io.github.roggzuay.ondalune.data.model.soundProfileOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sound profiles: a song, or an album, the way you like to hear it.
 *
 * Nothing here applies itself unless you have asked it to. When a song with a saved sound starts,
 * the player offers it on a quiet strip above the title and waits; the sound only changes when you
 * tap it. Every play is written down with whatever the sound was doing, saved or not, so the
 * version you liked but never saved is still there on Home.
 *
 * No timers: the history row is opened and corrected by the sound moving, not by a clock.
 */
class SoundProfileController(
    private val dao: OndaluneDao,
    private val sound: SoundController,
    private val scope: CoroutineScope,
    /**
     * Whether plays may be written down at all: "Keep a history of sounds" in Settings. Read
     * afresh each time, so turning it off stops the next write instead of needing a restart.
     */
    private val keepHistory: () -> Boolean = { true },
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    val profiles: StateFlow<List<SoundProfile>> = dao.observeSoundProfiles()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<PlayHistoryEntry>> = dao.observePlayHistory(PlayHistoryLimit)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _offer = MutableStateFlow<SoundOffer?>(null)

    /** The saved sound waiting for a tap, or the note that one was just put on. */
    val offer: StateFlow<SoundOffer?> = _offer.asStateFlow()

    private val _glide = MutableStateFlow(0f)

    /**
     * How far along the slow slide into a saved sound we are, 0 to 1, and 0 when none is running.
     *
     * The player draws this as a line filling under the strip, so the one thing the app does
     * slowly on purpose does not look like the app being slow.
     */
    val glide: StateFlow<Float> = _glide.asStateFlow()

    private var glideJob: Job? = null

    /** Where the running slide is headed, so calling it off can still land it there. */
    private var glideTarget: SoundSnapshot? = null

    /** What the playing song's sound was when it started, for the history row. */
    private var playing: SubsonicTrack? = null

    /**
     * The history row for the play being counted right now, and the sound last written to it.
     *
     * A play used to be written down once, thirty seconds in — and reaching for the speed and the
     * reverb is something you do *after* a song has got going, so that row was almost always
     * written before the interesting part happened and said "as mastered" forever. Worse, a song
     * tuned and then skipped was never written down at all. Keeping the row's id means it can be
     * opened the moment the sound moves and corrected to whatever the play ends up sounding like,
     * which is the whole point of a history of sounds nobody saved.
     */
    private var playRow: PlayHistoryEntry? = null

    /**
     * Set by clearing the history, so the song still playing does not write itself straight back
     * in. Clearing means "forget these", and a row reappearing under your thumb reads as a bug.
     * The next song starts recording again.
     */
    private var historyClosedForPlay = false

    /**
     * A sound asked for by the Home card, waiting for its song to start. Tapping a past play means
     * "that version", so it wins over the song's own saved profile for that one play.
     */
    private var pendingApply: Pair<String, SoundSnapshot>? = null

    /**
     * The sound you had before a saved one went on, kept so it can be put back.
     *
     * A saved sound belongs to its song. Without this it became the sound of everything that
     * followed: play one song slowed and reverbed from the Sound Profiles list, skip on, and the
     * whole queue stayed slowed. Null means no saved sound is in force and the sound is simply
     * yours, carrying from song to song as it always has.
     */
    private var soundBeforeProfile: SoundSnapshot? = null

    /** The sound as the playing song began, before anything was done to it for this song. */
    private var soundAtStart: SoundSnapshot = SoundSnapshot()

    init {
        // The sound you end a song with is the sound that play is remembered by. Watching the four
        // flows the snapshot is made of costs nothing while nothing moves, and a settle window
        // means dragging a slider across its whole range is one write, not a hundred.
        scope.launch {
            combine(
                sound.speedPitchState,
                sound.reverbState,
                sound.equalizerPreset,
                sound.customEqBands
            ) { _, _, _, _ -> sound.currentSnapshot() }.collectLatest { live ->
                delay(SoundSettleMs)
                rememberSound(live)
            }
        }
    }

    /**
     * Writes down the sound this play is to be remembered by, as it now stands.
     *
     * Two jobs, because the row can be needed before the play has been counted. A row already
     * written is corrected. No row yet means the song has not been heard for its thirty seconds —
     * but reaching for the speed or the reverb is a deliberate act, and a sound somebody went to
     * the trouble of making is the whole point of this history, so that opens the row there and
     * then. The thirty second mark finds it open and corrects it rather than adding a second one.
     */
    private suspend fun rememberSound(live: SoundSnapshot) {
        if (playRow != null) correctPlayRow(live) else openPlayRow(live)
    }

    /** Opens a row mid-play, for a sound changed before the play was counted. */
    private suspend fun openPlayRow(live: SoundSnapshot) {
        val track = playing ?: return
        if (!keepHistory() || historyClosedForPlay) return
        // Nothing done to the sound is nothing to remember, and a sound merely carried in from the
        // last song was not chosen for this one.
        if (live.isNeutral || !live.differsFrom(soundAtStart)) return
        val entry = PlayHistoryEntry(
            serverId = track.serverId,
            trackId = track.id,
            playedAt = now(),
            sound = live.stored(),
            hadProfile = profileFor(track) != null
        )
        val id = runCatching { dao.recordPlay(entry) }.getOrNull() ?: return
        playRow = entry.copy(id = id)
    }

    /**
     * Rewrites the current play's row with the sound as it now stands.
     *
     * Silent about everything it cannot do: no row yet, the row belongs to a song that has since
     * changed, the sound has not meaningfully moved, or trimming has already dropped the row. None
     * of those is worth telling anyone about, and a play history is not worth a crash.
     */
    private suspend fun correctPlayRow(live: SoundSnapshot) {
        val row = playRow ?: return
        if (playing?.id != row.trackId) return
        if (!live.differsFrom(row.snapshot)) return
        val corrected = row.copy(sound = live.stored())
        val rows = runCatching { dao.updatePlay(corrected) }.getOrDefault(0)
        // Zero means the row is gone, so stop trying to correct it.
        playRow = if (rows > 0) corrected else null
    }

    /** The Home card asked for this song, played the way it was played before. */
    fun playWith(trackId: String, snapshot: SoundSnapshot) {
        pendingApply = trackId to snapshot
    }

    /**
     * Puts a saved sound on, remembering what it replaced so the next song without one can have
     * it back. A second saved sound in a row replaces the first and leaves the memory alone: what
     * is being kept is your sound, not the last song's.
     */
    private fun putProfileOn(snapshot: SoundSnapshot) {
        if (soundBeforeProfile == null) soundBeforeProfile = soundAtStart
        sound.applySnapshot(snapshot)
        // This song now starts from the saved sound. Leaving the old mark standing made the guard
        // in openPlayRow read the app's own doing as yours, and wrote a history row for every
        // song you skipped through on an album with a sound saved to it.
        soundAtStart = sound.currentSnapshot()
    }

    /** A song with no saved sound started: give back the sound you had before one went on. */
    private fun takeProfileOff() {
        val yours = soundBeforeProfile ?: return
        soundBeforeProfile = null
        sound.applySnapshot(yours)
        // Same reason as putProfileOn: giving your sound back is the app's doing, not a choice
        // made for this song.
        soundAtStart = sound.currentSnapshot()
    }

    /**
     * You moved a sound control while a slide was running. The slide is dropped where it stands
     * rather than landed, because the caller has already written the value you asked for and
     * landing would put the saved sound straight back over it.
     *
     * The controls you did not touch stay where the slide had got to, which the sliders show.
     */
    fun onSoundChangedByHand() {
        stopGliding(land = false)
    }

    /**
     * You reset the sound yourself while a saved one was on. That is the sound you want now, so
     * the one from before it is forgotten rather than coming back on the next song.
     */
    fun onSoundResetByHand() {
        // Resetting during a slide means you changed your mind about it, so the slide is dropped
        // rather than landed: the reset is the sound you asked for and it has to be the one left
        // standing. Called before the reset itself, so no frame of the slide can land on top.
        stopGliding(land = false)
        soundBeforeProfile = null
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
        // A slide belongs to the song it was bending. Land it before anything else so the sound
        // this song starts from is a settled one.
        stopGliding()
        playing = track
        // The row from the song before is finished with: correcting it now would write this
        // song's sound onto that play.
        playRow = null
        historyClosedForPlay = false
        soundAtStart = sound.currentSnapshot()
        sound.onPlaybackStarted()
        val wanted = pendingApply
        pendingApply = null
        if (track != null && wanted != null && wanted.first == track.id) {
            putProfileOn(wanted.second)
            _offer.value = SoundOffer(track.id, wanted.second, applied = true)
            return
        }
        _offer.value = null
        if (track == null) {
            takeProfileOff()
            return
        }
        scope.launch {
            val saved = profileFor(track)
            // The song may have moved on while the row was being read.
            if (playing?.id != track.id) return@launch
            if (saved == null) {
                takeProfileOff()
                return@launch
            }
            val snapshot = saved.snapshot
            if (autoApply) putProfileOn(snapshot)
            _offer.value = SoundOffer(track.id, snapshot, applied = autoApply)
        }
    }

    /**
     * You tapped the strip, so the song leans into the saved sound instead of switching to it.
     *
     * This is the one place a sound arrives slowly. Everywhere else — a profile played from the
     * Sound Profiles list, auto-apply, the sound coming back off a song — lands at once, because
     * there you asked for a song and the sound is a property of it. Here you asked for the change
     * itself, which makes the change worth hearing: over [GlideMs] the speed bends, the pitch
     * slides and the room opens up around the song while it keeps playing.
     *
     * Tapping again while one is running does nothing; the slide is already on its way.
     */
    fun applyOffer() {
        val offered = _offer.value ?: return
        if (glideJob?.isActive == true) return
        // Marked applied straight away: the tap has been taken, and the strip switches to showing
        // the slide rather than still asking a question that has been answered.
        _offer.value = offered.copy(applied = true)
        // Claimed here rather than at the end so that a song change mid-slide still gives your own
        // sound back, and so a slide that is cut short is not remembered as your sound.
        if (soundBeforeProfile == null) soundBeforeProfile = soundAtStart
        glideInto(offered.snapshot)
    }

    private fun glideInto(target: SoundSnapshot) {
        val from = sound.currentSnapshot()
        stopGliding()
        glideTarget = target
        // Published here rather than after the first delay. The strip reads the slide from this,
        // and a frame of it sitting at zero showed "Playing your saved sound" for a blink before
        // "Easing into your sound" — a flicker at the one moment the tap is being watched.
        _glide.value = GlideFrameMs.toFloat() / GlideMs
        glideJob = scope.launch {
            var elapsed = 0L
            while (elapsed < GlideMs) {
                delay(GlideFrameMs)
                // delay throws when cancelled, so this only guards the race where cancellation
                // arrives in the instant after it resumes. Writing a frame after the slide has
                // been called off would leave the engine somewhere nothing has written down.
                if (!isActive) return@launch
                elapsed += GlideFrameMs
                val fraction = (elapsed.toFloat() / GlideMs).coerceAtMost(1f)
                _glide.value = fraction
                sound.previewBlend(from.blendTo(target, soundGlideEase(fraction)))
            }
            // Lands on the saved sound exactly, and this is the one write to disk.
            sound.applySnapshot(target)
            glideTarget = null
            glideJob = null
            _glide.value = 0f
        }
    }

    /**
     * Calls off a running slide.
     *
     * [land] decides what the song is left on, and both answers are needed. A slide cut off and
     * simply dropped leaves the song at some sound between two others that nothing has written
     * down — a song stuck at 0.93x with no setting that explains it — so a song change lands it,
     * and whatever happens next starts from a sound that exists. But when the caller is about to
     * set the sound itself, landing it would overwrite what you just asked for, so there [land] is
     * false and the caller has the last word.
     */
    private fun stopGliding(land: Boolean = true) {
        val job = glideJob ?: return
        glideJob = null
        job.cancel()
        _glide.value = 0f
        if (land) glideTarget?.let { sound.applySnapshot(it) }
        glideTarget = null
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
        // Saving the sound you are listening to does not change it, but from now on it is this
        // song's. What comes back on the next song is the sound this one started with, not the
        // one just saved.
        if (soundBeforeProfile == null) soundBeforeProfile = soundAtStart
        _offer.value = SoundOffer(track.id, snapshot, applied = true)
    }

    /** Forgets a saved sound. */
    fun forget(serverId: Long, profileScope: SoundProfileScope, scopeId: String) {
        scope.launch { dao.deleteSoundProfile(serverId, profileScope.name, scopeId) }
        // The offer on screen is for the playing song. An album's sound is keyed by the album, so
        // comparing it with the track id left a strip offering a sound that no longer existed.
        val playingThis = playing?.let { track ->
            when (profileScope) {
                SoundProfileScope.TRACK -> track.id == scopeId
                SoundProfileScope.ALBUM -> track.albumId == scopeId
            } && track.serverId == serverId
        } ?: false
        if (playingThis) _offer.value = null
    }

    /**
     * Writes down a play, with the sound that was on while it played. Called from the same place
     * that already counts the play, so this costs one insert and nothing else.
     */
    suspend fun recordPlay(track: SubsonicTrack) {
        // Both ways into the history answer to the same two gates, not just the one the sound
        // moving takes. Without this, a play counted after you cleared the list put the song
        // still playing straight back into it, under your thumb.
        if (!keepHistory() || historyClosedForPlay) return
        // The row may already be open: changing the sound writes the play down there and then
        // rather than waiting for it to be counted. One play, one row.
        val open = playRow
        if (open != null && open.trackId == track.id && playing?.id == track.id) {
            correctPlayRow(sound.currentSnapshot())
            return
        }
        val entry = PlayHistoryEntry(
            serverId = track.serverId,
            trackId = track.id,
            playedAt = now(),
            sound = sound.currentSnapshot().stored(),
            hadProfile = profileFor(track) != null
        )
        val id = dao.recordPlay(entry)
        // Held so the rest of this play can still change what it is remembered by. Only for the
        // song actually playing: a play counted just as the song changed belongs to the song that
        // was heard, not to the one that is being heard now. Left alone rather than cleared in
        // that case, because the row it would clear belongs to the song now playing, and losing
        // it means the next sound move opens a second row for the same play.
        if (playing?.id == track.id) playRow = entry.copy(id = id)
    }

    suspend fun clearHistory() {
        playRow = null
        historyClosedForPlay = true
        dao.clearPlayHistory()
    }

    companion object {
        /**
         * How long the song takes to lean into a tapped sound. Long enough to be heard happening
         * rather than noticed afterwards, short enough that it is over before you wonder.
         */
        const val GlideMs = 3_400L

        /** A frame of the slide. Three engine calls apiece, at about twenty a second. */
        const val GlideFrameMs = 50L

        /**
         * How long the sound must hold still before the play it belongs to is rewritten. Covers a
         * slider being dragged across its range, or a run of taps on the semitone buttons.
         */
        const val SoundSettleMs = 900L
    }
}

/** A saved sound waiting above the song title, or the note that one has just been put on. */
data class SoundOffer(
    val trackId: String,
    val snapshot: SoundSnapshot,
    /** False: the strip asks. True: the strip says the saved sound is playing, then fades. */
    val applied: Boolean
)
